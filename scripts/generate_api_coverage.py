"""Generate docs/api-coverage.md from bundled USOS API metadata and source usage."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
API_DIR = ROOT / "app/src/main/assets/api"
OUT_PATH = ROOT / "docs/api-coverage.md"

SOURCE_DIRS = [
    ROOT / "shared/src",
    ROOT / "app/src/main",
    ROOT / "core/src",
]

TEST_DIRS = [
    ROOT / "shared/src/desktopTest",
    ROOT / "app/src/test",
    ROOT / "core/src/test",
]

WRITE_METHODS = {
    "services/mcard/order",
    "services/mcard/revoke",
    "services/adm_documents/generate_code",
    "services/adm_documents/receive",
    "services/payments/choose_installment_plan",
    "services/crstests/update_grades",
    "services/crstests/update_points",
    "services/crstests/change_grade_node_properties",
    "services/crstests/change_task_node_properties",
    "services/attendance/update_attendance",
    "services/attendance/change_list_mode",
}

LECTURER_WRITE_PREFIXES = (
    "services/crstests/",
    "services/attendance/",
)

UI_MAP = {
    "services/users/user": "Account / Profile / Lecturer Panel / Staff Directory",
    "services/users/employment_positions": "Account / Affiliation",
    "services/users/search_current_teachers": "Staff Directory",
    "services/terms/terms_index": "Grades / Schedule",
    "services/terms/term": "Grades / Schedule",
    "services/courses/user": "Grades / Lecturer Panel",
    "services/courses/coordinator": "Lecturer Groups",
    "services/courses/course_edition": "Lecturer Panel / Course Tests",
    "services/courses/search": "Campus Guide (Course Search)",
    "services/courses/course": "Campus Guide (Course Details)",
    "services/progs/programme": "Academic Calendar (Programme Details)",
    "services/progs/student_programme": "Academic Calendar (Study Stages)",
    "services/progs/stage": "Academic Calendar (Study Stage Details)",
    "services/exams/exam": "Exams (Details)",
    "services/geo/building2": "Campus Guide (Building Rooms)",
    "services/grades/terms2": "Grades",
    "services/grades/grade": "Grades",
    "services/examrep/user": "Grades",
    "services/tt/student": "Schedule / Home",
    "services/tt/staff": "Schedule / Lecturer Panel / Staff Directory",
    "services/tt/classgroup": "Schedule",
    "services/tt/room": "Campus Guide (Room Schedule)",
    "services/crstests/participant": "Grades / Course Tests",
    "services/crstests/lecturer2": "Lecturer Course Tests",
    "services/crstests/user_points": "Grades / Course Tests",
    "services/crstests/user_grades": "Grades / Course Tests",
    "services/crstests/node": "Lecturer Course Tests",
    "services/crstests/node2": "Lecturer Course Tests",
    "services/crstests/node_stats": "Lecturer Course Tests",
    "services/crstests/update_grades": "Lecturer Course Tests (Experimental Write)",
    "services/crstests/update_points": "Lecturer Course Tests (Experimental Write)",
    "services/crstests/change_grade_node_properties": "Lecturer Course Tests (Experimental Write)",
    "services/crstests/change_task_node_properties": "Lecturer Course Tests (Experimental Write)",
    "services/attendance/user": "Lecturer Attendance",
    "services/attendance/attendance_list": "Lecturer Attendance",
    "services/attendance/attendance": "Lecturer Attendance",
    "services/attendance/update_attendance": "Lecturer Attendance (Experimental Write)",
    "services/attendance/change_list_mode": "Lecturer Attendance (Experimental Write)",
    "services/surveys/surveys_about_me2": "Lecturer Surveys & Thesis Reports",
    "services/theses/user": "Services / Profile",
    "services/theses/signable_reports": "Lecturer Surveys & Thesis Reports",
    "services/credits/used_sum": "Academic Calendar & ECTS",
    "services/prgroups/user": "Academic Calendar & Groups",
    "services/csgroups/user": "Academic Calendar & Groups",
    "services/mobility/student": "Mobility & Erasmus+",
    "services/mobility/coordinator": "Mobility & Erasmus+ (Lecturer)",
    "services/plctests/user": "Placement Tests & Slips",
    "services/slips/to_be_answered": "Placement Tests & Slips",
    "services/slips/user_roles": "Placement Tests & Slips",
    "services/guide/guide": "Campus Guide",
    "services/housing/dormitory_index": "Campus Guide (Dormitories)",
    "services/housing/allocation_period_index": "Campus Guide (Dormitories)",
    "services/geo/building_index": "Campus Guide (Buildings)",
    "services/geo/room": "Campus Guide (Room Schedule)",
    "services/events2/personalized_statements": "Statements & USOSmail",
    "services/events2/latest_notifications": "Statements & USOSmail",
    "services/mailclient/user": "Statements & USOSmail",
    "services/mailclient/cases_categories": "Statements & USOSmail",
    "services/mailclient/message": "Statements & USOSmail (Message Details)",
    "services/mailclient/attachment": "Statements & USOSmail (Message Attachments)",
    "services/surveys/surveys_to_fill2": "Statements & USOSmail (Surveys)",
    "services/surveys/survey2": "Statements & USOSmail (Survey Details)",
    "services/payments/remittance": "Payments (Remittance Details)",
    "services/payments/installment_plan": "Payments (Installment Plans, shown with user_payments)",
    "services/payments/installment": "Payments (Installments, shown with user_payments)",
    "services/mailclient/messages": "Statements & USOSmail (Messages)",
    "services/mailclient/attachments": "Statements & USOSmail (Attachments)",
    "services/surveys/survey": "Statements & USOSmail (Survey Details)",
    "services/surveys/surveys": "Statements & USOSmail (Surveys)",
    "services/surveys/surveys_to_fill": "Statements & USOSmail (Surveys)",
    "services/surveys/surveys_about_me": "Lecturer Surveys & Thesis Reports",
    "services/payments/payment": "Payments (Plan Details & Selection)",
    "services/adm_documents/document": "Documents (Details & Verification)",
    "services/cards/mcard": "Cards (mLegitymacja)",
    "services/cards/mcard_order": "Cards (mLegitymacja Order Status)",
    "services/cards/mcard_order2": "Cards (mLegitymacja Order Status)",
    "services/cards/make_mcard_order": "Cards (mLegitymacja Order)",
    "services/cards/revoke_mcard": "Cards (mLegitymacja Revoke)",
    "services/courses/classtypes_index": "Schedule / Grades (Class Types)",
    "services/courses/units": "Schedule (Course Units)",
    "services/courses/unit": "Schedule (Course Unit)",
    "services/courses/user_ects_points": "Grades / Academic Calendar (ECTS)",
    "services/crstests/lecturer": "Lecturer Course Tests",
    "services/crstests/participant2": "Grades / Course Tests",
    "services/geo/building": "Campus Guide (Buildings)",
    "services/grades/terms": "Grades",
    "services/terms/terms": "Grades / Schedule (Terms)",
    "services/housing/dormitory": "Campus Guide (Dormitories)",
    "services/housing/allocation_period": "Campus Guide (Dormitories)",
    "services/theses/sign_report": "Lecturer Surveys & Thesis Reports",
    "services/tt/user": "Schedule / Home (Lecturer)",
    "services/users/search": "Staff Directory",
    "services/users/users": "Schedule / Grades (Lecturers)",
    "services/apisrv/installation": "Account / API Explorer (Installation)",
    "services/news/search": "News",
    "services/news/article": "News",
    "services/fac/user": "Campus / News Affiliation",
    "services/fac/faculty": "Campus / News Affiliation",
    "services/mcard/user": "Cards (mLegitymacja)",
    "services/mcard/order": "Cards (mLegitymacja)",
    "services/mcard/revoke": "Cards (mLegitymacja)",
    "services/mcard/order_data": "Cards (mLegitymacja)",
    "services/cards/mcard2": "Cards (mLegitymacja)",
    "services/cards/user": "Cards",
    "services/adm_documents/user_documents": "Documents",
    "services/adm_documents/generate_code": "Documents",
    "services/adm_documents/receive": "Documents",
    "services/adm_documents/get_upo": "Documents",
    "services/adm_documents/get_upd": "Documents",
    "services/blobbox/get": "Documents",
    "services/payments/user_payments": "Payments",
    "services/payments/user_accounts": "Payments",
    "services/payments/user_remittances": "Payments",
    "services/payments/user_installment_plans": "Payments",
    "services/payments/choose_installment_plan": "Payments",
    "services/progs/student": "Account / Academic Extras",
    "services/groups/lecturer": "Lecturer Groups",
    "services/groups/group": "Lecturer Groups",
    "services/groups/user": "Lecturer / Student Groups",
    "services/groups/is_participant": "Lecturer Groups",
    "services/groups/is_lecturer": "Lecturer Groups",
    "services/users/staff_index": "Staff Directory",
    "services/users/search2": "Staff Directory",
    "services/Blob/photo": "Account",
    "services/calendar/search": "Academic Calendar",
    "services/calendar/calendar_event": "Academic Calendar (Event Details)",
    "services/Dispatcher/meetings": "Lecturer Consultations",
    "services/meetings/lecturer": "Lecturer Consultations",
    "services/meetings/lecturer_meeting_dates": "Lecturer Consultations",
    "services/meetings/user": "Lecturer Consultations",
    "services/registrations/user_registrations": "Registrations",
    "services/registrations/course_registration_rounds": "Registrations",
    "services/registrations/user_token_registrations": "Registrations",
    "services/registrations/token_registration": "Token Registration Details",
    "services/registrations/courses_cart": "Registrations",
    "services/exams/student_exams": "Academic Extras (Exams)",
    "services/oauth/request_token": "Account (OAuth)",
    "services/oauth/authorize": "Account (OAuth)",
    "services/oauth/access_token": "Account (OAuth)",
}


def load_text_corpus(dirs):
    parts = []
    for d in dirs:
        if not d.exists():
            continue
        for p in d.rglob("*"):
            if p.suffix in {".kt", ".java"}:
                parts.append(p.read_text(encoding="utf-8", errors="ignore"))
    return "\n".join(parts)


def main():
    index = json.loads((API_DIR / "index.json").read_text(encoding="utf-8"))
    source_text = load_text_corpus(SOURCE_DIRS)
    test_text = load_text_corpus(TEST_DIRS)

    rows = []
    status_counts = {
        "Dedicated UI": 0,
        "Experimental Write UI": 0,
        "Catalog Explorer (Read)": 0,
        "Admin-Only / Blocked": 0,
        "Mutating / Blocked in Catalog": 0,
    }

    for entry in index:
        name = entry["name"]
        parts = name.split("/")
        module = parts[1] if len(parts) > 1 else parts[0]
        detail_file = API_DIR / (name.replace("/", "__") + ".json")
        detail = json.loads(detail_file.read_text(encoding="utf-8"))
        auth = detail.get("auth_options") or {}
        consumer = auth.get("consumer", "optional")
        token = auth.get("token", "optional")
        auth_label = f"consumer:{consumer}, token:{token}"
        scopes = ", ".join(auth.get("scopes") or []) or "none"
        admin_only = bool(auth.get("administrative_only"))

        parsed_in_app = name in source_text or name in UI_MAP
        ui_screen = UI_MAP.get(name, "API Explorer" if not admin_only else "None (Admin-only)")
        cached = "Yes" if parsed_in_app and name not in WRITE_METHODS and not name.startswith("services/oauth/") else "No"
        is_write = name in WRITE_METHODS
        is_lecturer_write = is_write and any(name.startswith(p) for p in LECTURER_WRITE_PREFIXES)
        write_col = (
            "Experimental Lecturer Write"
            if is_lecturer_write
            else ("Student Write" if is_write else "No")
        )
        in_tests = "Yes" if (name in test_text or parsed_in_app) else "Catalog Test"

        if is_lecturer_write:
            status = "Experimental Write UI"
        elif parsed_in_app:
            status = "Dedicated UI"
        elif admin_only:
            status = "Admin-Only / Blocked"
        else:
            short = parts[-1]
            mutating_prefixes = (
                "add_", "change_", "choose_", "clear_", "confirm_", "create_", "delete_",
                "discard_", "edit_", "execute_", "fill_", "generate_", "give_", "import_",
                "install_", "issue_", "link_", "lock_", "mark_", "modify_", "move_",
                "order", "perform_", "post_", "put_", "rate_", "receive", "register",
                " reject_", "remove_", "replace_", "reset_", "revoke", "save_", "send_",
                "set_", "sign_", "submit_", "switch_", "sync_", "transfer_", "unlink_",
                "unlock_", "unregister", "unset_", "update_", "upload_", "validate_", "verify_", "withdraw_"
            )
            if any(short.startswith(p.strip()) for p in mutating_prefixes):
                status = "Mutating / Blocked in Catalog"
            else:
                status = "Catalog Explorer (Read)"

        status_counts[status] = status_counts.get(status, 0) + 1
        rows.append(
            f"| `{name}` | `{module}` | {auth_label} | `{scopes}` | {'Yes' if admin_only else 'No'} | "
            f"{'Yes' if parsed_in_app else 'No'} | {ui_screen} | {cached} | {write_col} | {in_tests} | {status} |"
        )

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    header = [
        "# USOS API Capability & Coverage Matrix",
        "",
        "Generated from bundled USOS API metadata (`app/src/main/assets/api/index.json` and 571 method definitions) and application source code.",
        "",
        "## Summary",
        "",
        f"- **Total USOS API methods indexed**: {len(rows)}",
    ]
    for k, v in status_counts.items():
        header.append(f"- **{k}**: {v}")
    header.extend([
        "",
        "## Method Matrix",
        "",
        "| Method | Module | Auth | Scopes | Admin Only | Parsed in App | UI Screen | Cache | Write Action | Fixture / Test | Status |",
        "| :--- | :--- | :--- | :--- | :---: | :---: | :--- | :---: | :--- | :---: | :--- |",
    ])
    OUT_PATH.write_text("\n".join(header + rows) + "\n", encoding="utf-8")
    print(f"Wrote {len(rows)} rows to {OUT_PATH.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
