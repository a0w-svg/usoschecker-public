import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

if __package__:
    from . import audit_dependencies, check_sarif
else:
    import audit_dependencies
    import check_sarif


class DependencyAuditTest(unittest.TestCase):
    def test_empty_export_cannot_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder)
            (out / 'components.json').write_text('[]')
            with patch.object(audit_dependencies, 'OUT', out):
                with self.assertRaises(RuntimeError):
                    audit_dependencies.audit(False)

    def test_known_advisory_blocks_audit(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder)
            (out / 'components.json').write_text(json.dumps([{'name': 'example:library', 'version': '1.0'}]))
            response = io.BytesIO(json.dumps({'results': [{'vulns': [{'id': 'GHSA-example'}]}]}).encode())
            with patch.object(audit_dependencies, 'OUT', out), patch('urllib.request.urlopen', return_value=response):
                self.assertEqual(1, audit_dependencies.audit(False))

    def test_incomplete_osv_response_cannot_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder)
            (out / 'components.json').write_text(json.dumps([{'name': 'example:library', 'version': '1.0'}]))
            with patch.object(audit_dependencies, 'OUT', out), patch('urllib.request.urlopen', return_value=io.BytesIO(b'{"results": []}')):
                with self.assertRaises(RuntimeError):
                    audit_dependencies.audit(False)

    def test_sarif_findings_block_and_missing_report_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(RuntimeError):
                check_sarif.check(folder)
            report = Path(folder) / 'test.sarif'
            report.write_text(json.dumps({'runs': [{'results': [{'ruleId': 'example', 'level': 'error'}]}]}))
            self.assertEqual(1, check_sarif.check(folder))
            report.write_text(json.dumps({'runs': [{'results': []}]}))
            self.assertEqual(0, check_sarif.check(folder))


if __name__ == '__main__':
    unittest.main()
