"""Resolve shipped/test/build Maven components and query OSV for known advisories.

Only public package names and versions are sent to api.osv.dev. Run from any folder;
reports stay in ignored work/dependency-audit. A failed query is never a clean scan.
"""
import argparse
import json
from pathlib import Path
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'work' / 'dependency-audit'
INIT = '''
import groovy.json.JsonOutput
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
gradle.projectsEvaluated {
    gradle.rootProject.tasks.register('dependencyAuditExport') {
        doLast {
            def records = []
            rootProject.allprojects.each { p ->
                def selected = p.configurations.findAll { c ->
                    c.canBeResolved && c.name in ['releaseRuntimeClasspath',
                        'debugUnitTestRuntimeClasspath', 'debugAndroidTestRuntimeClasspath',
                        'runtimeClasspath', 'testRuntimeClasspath', 'desktopRuntimeClasspath',
                        'desktopTestRuntimeClasspath', 'coreLibraryDesugaring']
                }
                selected += p.buildscript.configurations.findAll { it.name == 'classpath' && it.canBeResolved }
                selected.each { c ->
                    def result = c.incoming.resolutionResult
                    result.allDependencies.each { d ->
                        if (d instanceof org.gradle.api.artifacts.result.UnresolvedDependencyResult)
                            throw new GradleException('Unresolved audit dependency: ' + d.attempted.displayName, d.failure)
                    }
                    result.allComponents.each { component ->
                        def id = component.id
                        if (id instanceof ModuleComponentIdentifier) {
                            records << [scope: p.path + ':' + c.name, name: id.group + ':' + id.module, version: id.version]
                        }
                    }
                }
            }
            def target = new File(rootProject.projectDir, 'work/dependency-audit/components.json')
            target.parentFile.mkdirs()
            target.text = JsonOutput.prettyPrint(JsonOutput.toJson(records.unique()))
            println('Exported ' + records.size() + ' scoped Maven components')
        }
    }
}
'''


def audit(resolve=True):
    OUT.mkdir(parents=True, exist_ok=True)
    if resolve:
        init = OUT / 'export.init.gradle'
        init.write_text(INIT, encoding='utf-8')
        wrapper = 'gradlew.bat' if __import__('os').name == 'nt' else './gradlew'
        subprocess.run([str(ROOT / wrapper), '-I', str(init), 'dependencyAuditExport',
                        '-PisolatedTests', '--console=plain'], cwd=ROOT, check=True)
    components = json.loads((OUT / 'components.json').read_text(encoding='utf-8'))
    packages = sorted({(c['name'], c['version']) for c in components})
    if not packages:
        raise RuntimeError('No resolved components; refusing an empty audit')
    results = []
    for offset in range(0, len(packages), 100):
        batch = packages[offset:offset + 100]
        query = {'queries': [{'package': {'ecosystem': 'Maven', 'name': name},
                              'version': version} for name, version in batch]}
        request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
            data=json.dumps(query).encode(), headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=90) as response:
            answers = json.load(response)['results']
        if len(answers) != len(batch):
            raise RuntimeError('Incomplete OSV response')
        for (name, version), answer in zip(batch, answers):
            results.append({'name': name, 'version': version,
                            'advisories': [v['id'] for v in answer.get('vulns', [])]})
    report = {'source': 'https://api.osv.dev/v1/querybatch',
              'checked_at': __import__('datetime').datetime.now(__import__('datetime').timezone.utc).isoformat(),
              'components': len(packages), 'results': results}
    (OUT / 'osv-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    findings = [r for r in results if r['advisories']]
    print(f'OSV: {len(packages)} unique Maven versions checked; {len(findings)} affected components.')
    for finding in findings:
        print(finding['name'], finding['version'], ', '.join(finding['advisories']))
    return 1 if findings else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-resolve', action='store_true', help='Query an existing component export')
    raise SystemExit(audit(resolve=not parser.parse_args().no_resolve))
