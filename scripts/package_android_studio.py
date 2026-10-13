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
    roots = ['app/src', 'core/src', 'gradle/wrapper', '.run', 'scripts']
    files = [ROOT / name for name in ['settings.gradle', 'build.gradle', 'gradle.properties',
        'gradlew', 'gradlew.bat', '.gitignore', 'app/build.gradle', 'core/build.gradle',
        'README.md', 'ANDROID_STUDIO.md', 'VALIDATION.md']]
    for directory in roots:
        files.extend(p for p in (ROOT / directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
    with zipfile.ZipFile(DIST / 'VistulaChecker-AndroidStudio.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(set(files)):
            archive.write(path, 'VistulaChecker/' + path.relative_to(ROOT).as_posix())
    print('Created dist/VistulaChecker-AndroidStudio.zip and debug APK if built.')

if __name__ == '__main__':
    main()
