"""The app must not request result fields the bundled USOS API metadata does not declare."""
import unittest

import check_field_selectors as check


class FieldSelectorTest(unittest.TestCase):
    def test_top_level_names_ignore_nested_selectors(self):
        self.assertEqual(["a", "b", "e"], check.top_level("a|b[c|d[x|y]]|e"))

    def test_every_selector_names_declared_fields(self):
        problems = []
        checked = 0
        for file, line, method, selector in check.calls():
            fields = check.declared(method)
            self.assertIsNotNone(fields, f"{file}:{line}: {method} is not in the bundled catalog")
            if not fields:
                continue
            checked += 1
            unknown = [name for name in check.top_level(selector) if name not in fields]
            if unknown:
                problems.append(f"{file}:{line}: {method} has no field {', '.join(unknown)}")
        self.assertEqual([], problems)
        self.assertGreater(checked, 50, "The scan must keep finding the app's API calls")

    def test_list_methods_are_checked_through_their_element_method(self):
        self.assertIn("status_name", check.declared("services/housing/allocation_period_index"))
        self.assertNotIn("name", check.declared("services/housing/allocation_period_index"))


if __name__ == "__main__":
    unittest.main()
