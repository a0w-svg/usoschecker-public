"""API references must never control the path used for a local write."""
import unittest
from unittest.mock import patch
import refresh_api_catalog


class CatalogPathsTest(unittest.TestCase):
    def test_invalid_remote_names_fail_before_network_or_filesystem_access(self):
        for name in ('../outside', 'services/a/../../outside', 'services/a/..\\outside',
                     '/tmp/file', 'services/a/b?query', 'services/a/b#fragment', None):
            with self.subTest(name=name), patch.object(refresh_api_catalog, 'read') as read:
                with self.assertRaises(ValueError):
                    refresh_api_catalog.method({'name': name})
                read.assert_not_called()
