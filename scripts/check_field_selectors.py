"""Compare the `fields` selectors the app sends with the result fields the bundled USOS API metadata declares.

A selector naming a field the method does not have is answered with HTTP 400, so the feature behind it never
loads. The live server is the authority; this check catches the mistake offline for every method whose metadata
lists its result fields. Methods whose metadata lists none (most *_index and list methods) are reported as
unchecked instead of passed.

Usage: python scripts/check_field_selectors.py [--verbose]
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "app/src/main/assets/api"
SOURCES = [ROOT / "shared/src/jvmCommonMain/kotlin", ROOT / "app/src/main/java", ROOT / "shared/src/desktopMain/kotlin"]
METHOD = re.compile(r'"(services/(?:[a-z0-9_]+/)+[a-z0-9_]+)"')
FIELDS = re.compile(r'"fields"\s+to\s+"([^"$]*)"')
CONST_FIELDS = re.compile(r'"fields"\s+to\s+([A-Za-z_][A-Za-z0-9_.]*)')
STRING_CONST = re.compile(r'(?:const\s+)?val\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*String)?\s*=\s*"([^"$]*)"')


# List and index methods return objects "as defined in" another method; their own metadata lists no fields.
ELEMENT_METHOD = {
    "services/adm_documents/user_documents": "services/adm_documents/document",
    "services/attendance/user": "services/attendance/attendance_list",
    "services/calendar/search": "services/calendar/calendar_event",
    "services/courses/units": "services/courses/unit",
    "services/csgroups/user": "services/csgroups/custom_group",
    "services/events2/personalized_statements": "services/events2/statement",
    "services/exams/student_exams": "services/exams/exam",
    "services/geo/building_index": "services/geo/building2",
    "services/groups/lecturer": "services/groups/group",
    "services/groups/participant": "services/groups/group",
    "services/groups/user": "services/groups/group",
    "services/guide/guide": "services/guide/chapter",
    "services/housing/allocation_period_index": "services/housing/allocation_period",
    "services/housing/dormitory_index": "services/housing/dormitory",
    "services/mailclient/attachments": "services/mailclient/attachment",
    "services/mailclient/messages": "services/mailclient/message",
    "services/mailclient/user": "services/mailclient/message",
    "services/meetings/lecturer": "services/meetings/meeting",
    "services/meetings/lecturer_meeting_dates": "services/meetings/meeting_date",
    "services/mobility/coordinator": "services/mobility/mobility",
    "services/mobility/student": "services/mobility/mobility",
    "services/payments/user_payments": "services/payments/payment",
    "services/payments/user_remittances": "services/payments/remittance",
    "services/plctests/user": "services/plctests/test_result",
    "services/prgroups/user": "services/prgroups/primary_group",
    "services/progs/student": "services/progs/student_programme",
    "services/registrations/course_registration_rounds": "services/registrations/registration_round",
    "services/registrations/user_registrations": "services/registrations/registration",
    "services/registrations/user_token_registrations": "services/registrations/token_registration",
    "services/surveys/surveys_about_me2": "services/surveys/survey2",
    "services/surveys/surveys_to_fill2": "services/surveys/survey2",
    "services/terms/terms": "services/terms/term",
    "services/theses/signable_reports": "services/theses/exam_report",
    "services/tt/room": "services/tt/classgroup",
    "services/tt/staff": "services/tt/classgroup",
    "services/tt/student": "services/tt/classgroup",
    "services/tt/user": "services/tt/classgroup",
    "services/users/users": "services/users/user",
}
# The selector of grades/terms2 names fields of the nested grade objects, not of the method's own result.
NESTED_SELECTOR = {"services/grades/terms2"}


def top_level(selector):
    """Field names at the top level of a selector such as `a|b[c|d]|e`."""
    names, depth, current = [], 0, ""
    for char in selector:
        if char == "[":
            depth += 1
        elif char == "]":
            depth -= 1
        elif char == "|" and depth == 0:
            names.append(current)
            current = ""
            continue
        if depth == 0 and char not in "[]":
            current += char
    names.append(current)
    return [name for name in names if name]


def declared(method):
    path = API / (method.replace("/", "__") + ".json")
    if not path.exists():
        return None
    fields = {field["name"] for field in json.loads(path.read_text(encoding="utf-8")).get("result_fields") or []}
    if method in NESTED_SELECTOR:
        return set()
    if not fields and method in ELEMENT_METHOD:
        return declared(ELEMENT_METHOD[method]) or set()
    return fields


def calls():
    for base in SOURCES:
        for file in sorted(base.rglob("*.kt")):
            text = file.read_text(encoding="utf-8")
            constants = dict(STRING_CONST.findall(text))
            lines = text.splitlines()
            for index, line in enumerate(lines):
                for method in METHOD.findall(line):
                    window = "\n".join(lines[index:index + 8])
                    # Stop at the next API call so its selector is not attributed to this one.
                    following = METHOD.search(window, window.find(method) + len(method))
                    if following:
                        window = window[:following.start()]
                    match = FIELDS.search(window)
                    selector = match.group(1) if match else None
                    if selector is None:
                        named = CONST_FIELDS.search(window)
                        if named:
                            selector = constants.get(named.group(1).split(".")[-1])
                    if selector:
                        yield file.relative_to(ROOT).as_posix(), index + 1, method, selector


def main():
    verbose = "--verbose" in sys.argv
    problems, unchecked, checked = [], set(), 0
    for file, line, method, selector in calls():
        fields = declared(method)
        if fields is None:
            problems.append(f"{file}:{line}: {method} is not in the bundled catalog")
        elif not fields:
            unchecked.add(method)
        else:
            checked += 1
            unknown = [name for name in top_level(selector) if name not in fields]
            if unknown:
                problems.append(f"{file}:{line}: {method} has no field {', '.join(unknown)}")
            elif verbose:
                print(f"ok {file}:{line} {method}")
    for problem in problems:
        print(problem)
    print(f"Field selectors: {checked} checked, {len(problems)} problems, {len(unchecked)} methods without field metadata.")
    if verbose:
        print("Unchecked: " + ", ".join(sorted(unchecked)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
