"""New user-visible Polish text belongs in string resources, not in Kotlin code.

The Android screens must be translatable (pl, en, uk). This guard scans the app sources for string literals
containing Polish letters and fails on any that are not in the reviewed baseline below. The baseline holds
stable data keys and parsers (action names stored in history, labels matched in API responses, internal
exception messages); do not add user-facing sentences to it - add a string resource instead.
"""
from pathlib import Path
import re
import unittest

APP = Path(__file__).resolve().parents[1] / "app/src/main/java"
LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
POLISH = re.compile("[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]")

# Reviewed: persisted identifiers, API label parsers, internal messages and map links.
ALLOWED = {
    "Usunięto", "Dodano", "Zmieniono", "ocena", "plan",
    "Wykład", "Ćwiczenia", "Laboratorium", "Projekt", "Seminarium", "Lektorat", "Konwersatorium", "Inne",
    "Ocena z protokołu przedmiotu", "Ocena z protokołu zajęć", "Typ zajęć niepodany",
    "niedopłata", "do zapłaty", "przed chwilą", "Prowadzący: ", "Prowadzący:",
    "Finanse i Rachunkowość", "Nieprawidłowy tryb wyglądu.",
    "Sesja Platona wygasła.", "Zbyt wiele przekierowań Platona.", "Odpowiedź Platona jest zbyt duża.",
    "geo:52.158145,21.034326?q=Akademia+Finansów+i+Biznesu+Vistula,+ul.+Stokłosy+3,+Warszawa",
}


class UiLiteralsTest(unittest.TestCase):
    def test_no_new_hard_coded_polish_text(self):
        offenders = []
        for path in sorted(APP.rglob("*.kt")):
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if line.lstrip().startswith(("//", "*", "/*")):
                    continue
                for match in LITERAL.finditer(line):
                    text = match.group(1)
                    if POLISH.search(text) and text not in ALLOWED:
                        offenders.append(f"{path.relative_to(APP)}:{number}: {text[:60]}")
        self.assertEqual([], offenders, "Move user-visible text to string resources (values, values-en, values-uk).")


if __name__ == "__main__":
    unittest.main()
