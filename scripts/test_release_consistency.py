"""Cheap release hygiene: version fields are well formed and every language ships release notes."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
GRADLE = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
RAW = ROOT / "app/src/main/res"


class ReleaseConsistencyTest(unittest.TestCase):
    def test_version_fields_are_well_formed(self):
        name = re.search(r"versionName\s*=\s*'(\d+)\.(\d+)\.(\d+)'", GRADLE)
        code = re.search(r"versionCode\s*=\s*(\d+)", GRADLE)
        self.assertIsNotNone(name, "versionName must be MAJOR.MINOR.PATCH")
        self.assertIsNotNone(code, "versionCode must be an integer")
        self.assertGreater(int(code.group(1)), 0)

    def test_release_notes_exist_in_every_language(self):
        for folder in ("raw", "raw-en", "raw-uk"):
            notes = (RAW / folder / "whats_new.txt").read_text(encoding="utf-8").strip()
            self.assertTrue(notes, f"{folder}/whats_new.txt must not be empty")
            self.assertTrue(notes.startswith("•"), f"{folder}/whats_new.txt must start with a bullet")

    def test_notes_have_the_same_number_of_leading_bullets(self):
        counts = {f: (RAW / f / "whats_new.txt").read_text(encoding="utf-8").count("•") for f in ("raw", "raw-en", "raw-uk")}
        self.assertEqual(1, len(set(counts.values())), f"whats_new bullet counts differ between languages: {counts}")


if __name__ == "__main__":
    unittest.main()
