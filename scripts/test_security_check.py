"""Regression tests using disposable Git repositories and synthetic credentials."""
from contextlib import redirect_stdout
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
if __package__:
    from . import security_check as guard
else:
    import security_check as guard


class SecurityCheckTest(unittest.TestCase):
    def setUp(self):
        # Fixtures must not inherit signing, hooks, excludes, or a caller's Git
        # worktree/index overrides. This also keeps every Git operation in temp.
        environment = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
        environment.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
        isolated = patch.dict(os.environ, environment, clear=True)
        isolated.start()
        self.addCleanup(isolated.stop)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.mock = patch.object(guard, 'ROOT', self.root)
        self.mock.start()
        self.addCleanup(self.mock.stop)
        self.git('init', '-q', '--template=')
        self.git('config', 'user.name', 'Security test')
        self.git('config', 'user.email', 'test@example.invalid')

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, stderr=subprocess.PIPE)

    def write(self, name, value):
        (self.root / name).write_text(value, encoding='utf-8')

    def run_check(self, **kwargs):
        output = io.StringIO()
        with redirect_stdout(output):
            result = guard.check(**kwargs)
        return result, output.getvalue()

    def test_empty_example_and_ordinary_sources_pass(self):
        self.write('local.properties.example', 'usos.consumer.key=\nusos.consumer.secret=\n')
        self.assertTrue(self.run_check()[0])

    def test_private_file_blocked_even_if_it_contains_no_known_secret(self):
        self.write('signing.properties', 'placeholder')
        self.assertFalse(self.run_check()[0])

    def test_staged_version_is_scanned_even_after_worktree_is_cleaned(self):
        secret = 'ghp_' + 'Q' * 36
        self.write('sample.txt', secret)
        self.git('add', 'sample.txt')
        self.write('sample.txt', 'safe')
        self.assertTrue(self.run_check()[0])
        passed, output = self.run_check(staged=True)
        self.assertFalse(passed)
        self.assertNotIn(secret, output)

    def test_history_detects_deleted_secret(self):
        secret = 'ghp_' + 'R' * 36
        self.write('sample.txt', secret)
        self.git('add', '.')
        self.git('commit', '-qm', 'fixture')
        self.git('rm', 'sample.txt')
        self.git('commit', '-qm', 'remove')
        passed, output = self.run_check(history=True)
        self.assertFalse(passed)
        self.assertNotIn(secret, output)

    def test_local_credentials_detected_in_binary_without_echo(self):
        secret = 'synthetic' + '-local-value'
        self.write('.gitignore', 'local.properties\n')
        self.write('local.properties', 'usos.consumer.secret=' + secret)
        (self.root / 'image.png').write_bytes(b'\0\xff' + secret.encode())
        passed, output = self.run_check()
        self.assertFalse(passed)
        self.assertIn('local-credential-match', output)
        self.assertNotIn(secret, output)

    def test_credential_assignment_is_detected(self):
        payload = b'consumer_secret = "' + b'Z' * 32 + b'"'
        self.assertIn('credential-literal', guard.inspect(payload, []))


if __name__ == '__main__':
    unittest.main()
