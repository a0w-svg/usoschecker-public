"""Keep the three shipped Android translations complete and format-compatible."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

RES = Path(__file__).resolve().parents[1] / "app/src/main/res"


def resources(folder):
    result = {}
    for path in (RES / folder).glob("*.xml"):
        for item in ET.parse(path).getroot():
            if item.tag in {"string", "string-array", "plurals"} and item.get("translatable") != "false":
                key = (item.tag, item.attrib["name"])
                if key in result:
                    raise AssertionError(f"Duplicate localized resource: {folder}/{key}")
                result[key] = item
    return result


class LocalizationTest(unittest.TestCase):
    def test_all_resources_have_english_and_ukrainian_translations(self):
        base = resources("values")
        for language in ("values-en", "values-uk"):
            self.assertFalse(set(base) - set(resources(language)), language)

    def test_format_arguments_match(self):
        base = resources("values")
        pattern = re.compile(r"%(?:(\d+)\$)?[\d.]*([sdf])")
        for language in ("values-en", "values-uk"):
            translated = resources(language)
            for key, original in base.items():
                if key[0] != "string" or original.get("formatted") == "false":
                    continue
                expected = sorted(pattern.findall("".join(original.itertext())))
                actual = sorted(pattern.findall("".join(translated[key].itertext())))
                self.assertEqual(expected, actual, f"{language}/{key}")

    def test_selection_arrays_preserve_all_choices(self):
        base = resources("values")
        for language in ("values-en", "values-uk"):
            translated = resources(language)
            for key, original in base.items():
                if key[0] == "string-array":
                    self.assertEqual(len(original), len(translated[key]), f"{language}/{key}")

    def test_plural_forms_preserve_format_arguments(self):
        pattern = re.compile(r"%(?:(\d+)\$)?[\d.]*([sdf])")
        base = resources("values")
        for language in ("values", "values-en", "values-uk"):
            translated = resources(language)
            for key, original in base.items():
                if key[0] != "plurals":
                    continue
                expected = sorted(pattern.findall(original.find("item[@quantity='other']").text))
                forms = translated[key]
                required = {"one", "other"} if language == "values-en" else {"one", "few", "many", "other"}
                self.assertTrue(required.issubset({item.get("quantity") for item in forms}), f"{language}/{key}")
                for item in forms:
                    self.assertEqual(expected, sorted(pattern.findall(item.text)), f"{language}/{key}/{item.get('quantity')}")


if __name__ == "__main__":
    unittest.main()
