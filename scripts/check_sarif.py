"""Fail CI for unsuppressed warning/error findings in retained SARIF reports."""
import json
from pathlib import Path
import sys


def check(directory):
    reports = list(Path(directory).rglob('*.sarif'))
    if not reports:
        raise RuntimeError('No SARIF reports were produced')
    findings = []
    for report in reports:
        data = json.loads(report.read_text(encoding='utf-8'))
        for run in data.get('runs', []):
            for result in run.get('results', []):
                suppressed = any(s.get('status') == 'accepted' for s in result.get('suppressions', []))
                if not suppressed and result.get('level', 'warning') in ('warning', 'error'):
                    findings.append(result.get('ruleId', 'unknown rule'))
    print(f'SARIF: {len(reports)} reports; {len(findings)} unsuppressed warning/error findings.')
    for rule in findings:
        print(rule)
    return 1 if findings else 0


if __name__ == '__main__':
    raise SystemExit(check(sys.argv[1]))
