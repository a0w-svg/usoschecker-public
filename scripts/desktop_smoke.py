#!/usr/bin/env python3
"""Run under xvfb-run on Linux; all account data is a disposable fixture in work/."""
import argparse
import os
import re
import shutil
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--app', type=Path, default=ROOT / 'desktopApp/build/compose/binaries/main/app/VistulaChecker')
    parser.add_argument('--theme', choices=['light', 'dark'], default='light')
    args = parser.parse_args()
    work = ROOT / 'work/desktop-smoke'
    work.mkdir(parents=True, exist_ok=True)
    profile = Path(tempfile.mkdtemp(prefix='fixture-', dir=work))
    java_home = Path(os.environ['JAVA_HOME'])
    classpath = str(args.app.resolve() / 'lib/app/*')
    subprocess.run([str(java_home / 'bin/javac'), '--release', '17', '-cp', classpath, '-d', str(profile),
                    str(ROOT / 'scripts/DesktopSmoke.java')], check=True, timeout=60, cwd=ROOT)
    classpath = str(profile) + os.pathsep + classpath
    driver = [str(java_home / 'bin/java'), '-cp', classpath, 'DesktopSmoke']
    subprocess.run(driver + ['seed', str(profile), args.theme], check=True, timeout=30, cwd=ROOT)
    environment = os.environ.copy()
    environment['XDG_DATA_HOME'] = str(profile / 'profile')
    environment['XDG_CACHE_HOME'] = str(profile / 'cache')
    environment['SKIKO_RENDER_API'] = 'SOFTWARE'
    with (profile / 'application.log').open('w') as log:
        process = subprocess.Popen([str(args.app.resolve() / 'bin/VistulaChecker')], env=environment, stdout=log, stderr=log, cwd=ROOT)
        try:
            subprocess.run(driver + ['exercise', str(profile)], check=True, timeout=50, cwd=ROOT)
            if process.poll() is not None:
                raise RuntimeError('Packaged desktop application exited during the smoke test')
            if not shutil.which('tesseract'):
                raise RuntimeError('The desktop smoke test requires tesseract for visible-state assertions')
            def visible_text(name):
                # The prototype has independent columns and a small account header.
                # Sparse-text segmentation retains that header instead of treating it as noise.
                result = subprocess.run(['tesseract', str(profile / name), 'stdout', '--psm', '11'], check=True,
                                        capture_output=True, text=True, timeout=20)
                return ' '.join(result.stdout.lower().split())
            before_text = visible_text('personal-before-header.png')
            if 'personal desktop fixture' not in before_text:
                raise RuntimeError('The fixture account did not load in the packaged application: ' + before_text)
            if 'demo' not in visible_text('demo.png') or 'not connected to usos' in visible_text('demo.png'):
                raise RuntimeError('Demo was not recognized as an isolated demo account')
            after_text = visible_text('personal-after-header.png')
            if 'personal desktop fixture' not in after_text:
                raise RuntimeError('Leaving demo did not restore the saved account: ' + after_text)
            schedule_text = re.sub(r'[^a-z]', '', visible_text('schedule-repeat.png'))
            if not any(label in schedule_text for label in ('chooseaday', 'noclassesonthisday', 'thisweek', 'backtothisweek')):
                raise RuntimeError('Repeating the timetable shortcut did not navigate back to the timetable')
            home_text = re.sub(r'[^a-z]', '', visible_text('home-between.png'))
            if not any(label in home_text for label in ('nextclass', 'ongoingclass', 'thisweek')):
                raise RuntimeError('The home shortcut did not leave the timetable')
            previews = ROOT / 'work/design-previews'
            previews.mkdir(parents=True, exist_ok=True)
            shutil.copy2(profile / 'home-between.png', previews / f'desktop-{args.theme}.png')

        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
    print(f'Fixture screenshots and logs: {profile.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
