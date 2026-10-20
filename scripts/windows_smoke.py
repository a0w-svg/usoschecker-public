#!/usr/bin/env python3
"""Verify the extracted Windows installer with a disposable account, never a personal profile."""
import os
from pathlib import Path
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]


def main():
    if os.name != 'nt':
        raise RuntimeError('Run the Windows package test on Windows')
    work = ROOT / 'work/desktop-smoke'
    work.mkdir(parents=True, exist_ok=True)
    profile = Path(tempfile.mkdtemp(prefix='fixture-windows-', dir=work))
    installers = list((ROOT / 'desktopApp/build/windows-publication').glob('*.msi'))
    if len(installers) != 1:
        raise RuntimeError('Expected exactly one freshly built MSI')
    extracted = profile / 'installer'
    # Administrative extraction verifies MSI contents without installing into the user's profile.
    result = subprocess.run(['msiexec.exe', '/a', str(installers[0]), '/qn',
                             'TARGETDIR=' + str(extracted), '/l*v', str(profile / 'msi.log')], timeout=120)
    if result.returncode != 0:
        raise RuntimeError('MSI administrative extraction failed: ' + str(result.returncode))
    launchers = list(extracted.rglob('VistulaChecker.exe'))
    if len(launchers) != 1:
        raise RuntimeError('Missing or ambiguous installed Windows launcher')
    app = launchers[0].parent
    java_home = Path(os.environ['JAVA_HOME'])
    classpath = str(app / 'app/*')
    subprocess.run([str(java_home / 'bin/javac.exe'), '--release', '17', '-cp', classpath,
                    '-d', str(profile), str(ROOT / 'scripts/DesktopSmoke.java')], check=True, timeout=60, cwd=ROOT)
    driver = [str(java_home / 'bin/java.exe'), '-cp', str(profile) + os.pathsep + classpath, 'DesktopSmoke']
    subprocess.run(driver + ['seed', str(profile)], check=True, timeout=30, cwd=ROOT)
    environment = os.environ.copy()
    environment['LOCALAPPDATA'] = str(profile / 'profile')
    environment['SKIKO_RENDER_API'] = 'SOFTWARE'
    with (profile / 'application.log').open('w') as log:
        process = subprocess.Popen([str(launchers[0])], env=environment, stdout=log, stderr=log, cwd=ROOT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            time.sleep(12)
            if process.poll() is not None:
                raise RuntimeError('Installed Windows app exited during launch')
            subprocess.run(driver + ['probe', str(profile)], check=True, timeout=30, cwd=ROOT)
        finally:
            process.terminate()
            process.wait(timeout=20)
    print('Windows MSI extraction, native app launch, account lock and fixture preservation passed.')


if __name__ == '__main__':
    main()
