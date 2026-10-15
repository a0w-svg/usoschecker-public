#!/usr/bin/env python3
"""Package only project sources and documentation, excluding local IDE files and secrets."""
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist'

def main():
    DIST.mkdir(exist_ok=True)
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    if apk.exists():
        shutil.copy2(apk, DIST / 'vistula-checker-debug.apk')
    release_apk = ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk'
    if release_apk.exists():
        shutil.copy2(release_apk, DIST / 'vistula-checker-release-unsigned.apk')
    release_aab = ROOT / 'app/build/outputs/bundle/release/app-release.aab'
    if release_aab.exists():
        shutil.copy2(release_aab, DIST / 'vistula-checker-release.aab')
    mapping = ROOT / 'app/build/outputs/mapping/release/mapping.txt'
    if mapping.exists():
        shutil.copy2(mapping, DIST / 'mapping.txt')

    roots = ['app/src', 'core/src', 'gradle/wrapper', '.run', 'scripts', 'tools']
    files = [ROOT / name for name in [
        'settings.gradle', 'build.gradle', 'gradle.properties',
        'gradlew', 'gradlew.bat', '.gitignore',
        'app/build.gradle', 'core/build.gradle',
        'app/proguard-rules.pro', 'app/optimized-qa-rules.pro', 'app/test-rules.pro',
        'README.md', 'ANDROID_STUDIO.md', 'VALIDATION.md', 'RELEASE.md'
    ]]
    for directory in roots:
        dir_path = ROOT / directory
        if dir_path.exists():
            files.extend(p for p in dir_path.rglob('*') if p.is_file() and '__pycache__' not in p.parts)
    with zipfile.ZipFile(DIST / 'VistulaChecker-AndroidStudio.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(set(files)):
            if path.exists():
                archive.write(path, 'VistulaChecker/' + path.relative_to(ROOT).as_posix())
    print('Created dist/VistulaChecker-AndroidStudio.zip and release/debug artifacts if built.')

if __name__ == '__main__':
    main()
