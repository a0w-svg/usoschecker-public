#!/usr/bin/env python3
"""Package only project sources and documentation, excluding local IDE files and secrets."""
from pathlib import Path
import zipfile
from security_check import check, git

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist' / 'publication'

def main():
    if not check() or not check(history=True):
        raise SystemExit('Publication blocked by security check.')
    DIST.mkdir(parents=True, exist_ok=True)

    names = set(git('ls-files', '--cached', '--others', '--exclude-standard', '-z').split(b'\0')) - {b''}
    files = [ROOT / raw.decode('utf-8') for raw in names]
    with zipfile.ZipFile(DIST / 'VistulaChecker-AndroidStudio.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(set(files)):
            if path.exists():
                archive.write(path, 'VistulaChecker/' + path.relative_to(ROOT).as_posix())
    print('Created dist/publication/VistulaChecker-AndroidStudio.zip (sources only; no APK/AAB/mapping).')

if __name__ == '__main__':
    main()
