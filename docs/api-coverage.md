# USOS API Capability & Coverage Matrix

Generated from bundled USOS API metadata (`app/src/main/assets/api/index.json` and 571 method definitions) and application source code.

## Summary

- **Total USOS API methods indexed**: 571
- **Dedicated UI**: 129
- **Experimental Write UI**: 6
- **Catalog Explorer (Read)**: 260
- **Admin-Only / Blocked**: 97
- **Mutating / Blocked in Catalog**: 79

## Method Matrix

| Method | Module | Auth | Scopes | Admin Only | Parsed in App | UI Screen | Cache | Write Action | Fixture / Test | Status |
| :--- | :--- | :--- | :--- | :---: | :---: | :--- | :---: | :--- | :---: | :--- |
| `services/adm_documents/document` | `adm_documents` | consumer:required, token:optional | `adm_documents` | No | Yes | Documents (Details & Verification) | Yes | No | Yes | Dedicated UI |
| `services/adm_documents/generate_code` | `adm_documents` | consumer:required, token:optional | `adm_documents` | No | Yes | Documents | No | Student Write | Yes | Dedicated UI |
| `services/adm_documents/get_upd` | `adm_documents` | consumer:required, token:required | `adm_documents` | No | Yes | Documents | Yes | No | Yes | Dedicated UI |
| `services/adm_documents/get_upo` | `adm_documents` | consumer:required, token:required | `adm_documents` | No | Yes | Documents | Yes | No | Yes | Dedicated UI |
| `services/adm_documents/receive` | `adm_documents` | consumer:required, token:optional | `adm_documents` | No | Yes | Documents | No | Student Write | Yes | Dedicated UI |
| `services/adm_documents/user_documents` | `adm_documents` | consumer:required, token:optional | `adm_documents` | No | Yes | Documents | Yes | No | Yes | Dedicated UI |
| `services/apiref/method` | `apiref` | consumer:optional, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/apiref/method_index` | `apiref` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/apiref/module` | `apiref` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/apiref/scopes` | `apiref` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/apisrv/consumer` | `apisrv` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/apisrv/installation` | `apisrv` | consumer:ignored, token:ignored | `none` | No | Yes | Account / API Explorer (Installation) | Yes | No | Yes | Dedicated UI |
| `services/apisrv/installations` | `apisrv` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/apisrv/now` | `apisrv` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/apistlog/formatted` | `apistlog` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/apistlog/raw` | `apistlog` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/attendance/attendance` | `attendance` | consumer:required, token:required | `staff_perspective` | No | Yes | Lecturer Attendance | Yes | No | Yes | Dedicated UI |
| `services/attendance/attendance_list` | `attendance` | consumer:required, token:required | `staff_perspective` | No | Yes | Lecturer Attendance | Yes | No | Yes | Dedicated UI |
| `services/attendance/attendance_lists` | `attendance` | consumer:required, token:required | `staff_perspective` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/attendance/change_list_mode` | `attendance` | consumer:required, token:required | `staff_perspective` | No | Yes | Lecturer Attendance (Experimental Write) | No | Experimental Lecturer Write | Yes | Experimental Write UI |
| `services/attendance/create_attendance_list` | `attendance` | consumer:required, token:required | `staff_perspective` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/attendance/create_from_tt` | `attendance` | consumer:required, token:required | `staff_perspective` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/attendance/delete_list` | `attendance` | consumer:required, token:required | `staff_perspective` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/attendance/group` | `attendance` | consumer:required, token:required | `staff_perspective` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/attendance/update_attendance` | `attendance` | consumer:required, token:required | `staff_perspective` | No | Yes | Lecturer Attendance (Experimental Write) | No | Experimental Lecturer Write | Yes | Experimental Write UI |
| `services/attendance/user` | `attendance` | consumer:required, token:required | `staff_perspective` | No | Yes | Lecturer Attendance | Yes | No | Yes | Dedicated UI |
| `services/blobbox/blob` | `blobbox` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/blobbox/blobs` | `blobbox` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/blobbox/chmod` | `blobbox` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/blobbox/consumer` | `blobbox` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/blobbox/delete` | `blobbox` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/blobbox/get` | `blobbox` | consumer:required, token:ignored | `none` | No | Yes | Documents | Yes | No | Yes | Dedicated UI |
| `services/blobbox/inspect_blob` | `blobbox` | consumer:optional, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/blobbox/put` | `blobbox` | consumer:required, token:optional | `none` | Yes | Yes | None (Admin-only) | Yes | No | Yes | Dedicated UI |
| `services/blobbox/put_base64` | `blobbox` | consumer:required, token:optional | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/blobbox/put_sha1` | `blobbox` | consumer:required, token:optional | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/blobbox/put_text` | `blobbox` | consumer:required, token:optional | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/blobbox/put_unsigned` | `blobbox` | consumer:ignored, token:ignored | `none` | No | Yes | API Explorer | Yes | No | Yes | Dedicated UI |
| `services/blobbox/user` | `blobbox` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/blobbox/users` | `blobbox` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/blobbox/users_all` | `blobbox` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/calendar/calendar_event` | `calendar` | consumer:required, token:optional | `none` | No | Yes | Academic Calendar (Event Details) | Yes | No | Yes | Dedicated UI |
| `services/calendar/search` | `calendar` | consumer:required, token:optional | `none` | No | Yes | Academic Calendar | Yes | No | Yes | Dedicated UI |
| `services/cards/card` | `cards` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/cards/check_orders_status` | `cards` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/cards/make_mcard_order` | `cards` | consumer:required, token:required | `cards` | No | Yes | Cards (mLegitymacja Order) | Yes | No | Yes | Dedicated UI |
| `services/cards/mark_student_card_prolonged` | `cards` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/cards/mcard` | `cards` | consumer:required, token:required | `cards` | No | Yes | Cards (mLegitymacja) | Yes | No | Yes | Dedicated UI |
| `services/cards/mcard2` | `cards` | consumer:required, token:required | `cards` | No | Yes | Cards (mLegitymacja) | Yes | No | Yes | Dedicated UI |
| `services/cards/mcard_order` | `cards` | consumer:required, token:required | `cards` | No | Yes | Cards (mLegitymacja Order Status) | Yes | No | Yes | Dedicated UI |
| `services/cards/mcard_order2` | `cards` | consumer:required, token:required | `cards` | No | Yes | Cards (mLegitymacja Order Status) | Yes | No | Yes | Dedicated UI |
| `services/cards/revoke_mcard` | `cards` | consumer:required, token:required | `cards` | No | Yes | Cards (mLegitymacja Revoke) | Yes | No | Yes | Dedicated UI |
| `services/cards/student_card_data` | `cards` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/cards/user` | `cards` | consumer:required, token:required | `cards` | No | Yes | Cards | Yes | No | Yes | Dedicated UI |
| `services/cards/user_cards` | `cards` | consumer:required, token:required | `cards` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/courses/classtypes_index` | `courses` | consumer:ignored, token:ignored | `none` | No | Yes | Schedule / Grades (Class Types) | Yes | No | Yes | Dedicated UI |
| `services/courses/coordinator` | `courses` | consumer:optional, token:optional | `none` | No | Yes | Lecturer Groups | Yes | No | Yes | Dedicated UI |
| `services/courses/course` | `courses` | consumer:ignored, token:ignored | `none` | No | Yes | Campus Guide (Course Details) | Yes | No | Yes | Dedicated UI |
| `services/courses/course2` | `courses` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/courses/course_edition` | `courses` | consumer:optional, token:optional | `none` | No | Yes | Lecturer Panel / Course Tests | Yes | No | Yes | Dedicated UI |
| `services/courses/course_edition2` | `courses` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/courses/course_unit` | `courses` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/courses/courses` | `courses` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/courses/is_coordinator` | `courses` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/courses/is_lecturer` | `courses` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/courses/is_participant` | `courses` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/courses/search` | `courses` | consumer:ignored, token:ignored | `none` | No | Yes | Campus Guide (Course Search) | Yes | No | Yes | Dedicated UI |
| `services/courses/unit` | `courses` | consumer:optional, token:optional | `none` | No | Yes | Schedule (Course Unit) | Yes | No | Yes | Dedicated UI |
| `services/courses/units` | `courses` | consumer:optional, token:optional | `none` | No | Yes | Schedule (Course Units) | Yes | No | Yes | Dedicated UI |
| `services/courses/user` | `courses` | consumer:required, token:required | `none` | No | Yes | Grades / Lecturer Panel | Yes | No | Yes | Dedicated UI |
| `services/courses/user_ects_points` | `courses` | consumer:required, token:required | `none` | No | Yes | Grades / Academic Calendar (ECTS) | Yes | No | Yes | Dedicated UI |
| `services/credits/student_programme` | `credits` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/credits/used_sum` | `credits` | consumer:required, token:required | `studies` | No | Yes | Academic Calendar & ECTS | Yes | No | Yes | Dedicated UI |
| `services/crstests/all_user_grades` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/all_user_points` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/change_folder_node_permissions` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/crstests/change_folder_node_properties` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/crstests/change_grade_node_permissions` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/crstests/change_grade_node_properties` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | Yes | Lecturer Course Tests (Experimental Write) | No | Experimental Lecturer Write | Yes | Experimental Write UI |
| `services/crstests/change_root_node_permissions` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/crstests/change_root_node_properties` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/crstests/change_task_node_permissions` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/crstests/change_task_node_properties` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | Yes | Lecturer Course Tests (Experimental Write) | No | Experimental Lecturer Write | Yes | Experimental Write UI |
| `services/crstests/connect_template_to_course_edition` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/course_test` | `crstests` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/create_folder_node` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/create_grade_node` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | Yes | None (Admin-only) | Yes | No | Yes | Dedicated UI |
| `services/crstests/create_root_node` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/create_task_node` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/delete_subtree` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/duplicate_subtree` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/duplicate_tree` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/examinee` | `crstests` | consumer:required, token:required | `crstests, staff_perspective` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/folder_node` | `crstests` | consumer:required, token:required | `crstests` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/folder_node_details` | `crstests` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/grade_node` | `crstests` | consumer:required, token:required | `crstests, grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/grade_node_details` | `crstests` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/groups` | `crstests` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/lecturer` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | Yes | Lecturer Course Tests | Yes | No | Yes | Dedicated UI |
| `services/crstests/lecturer2` | `crstests` | consumer:required, token:required | `crstests` | No | Yes | Lecturer Course Tests | Yes | No | Yes | Dedicated UI |
| `services/crstests/move_node` | `crstests` | consumer:required, token:required | `adm_documents, cards, change_all_preferences, crstests, dorm_admin, edit_user_attrs, email, events, grades, grades_write, mailclient, mobile_numbers, offline_access, other_emails, payments, personal, photo, placement_tests, session_debugging_perms, slips, slips_admin, staff_perspective, student_exams, student_exams_write, studies, surveys_filling, surveys_reports, theses_protocols_write` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/node` | `crstests` | consumer:required, token:optional | `none` | No | Yes | Lecturer Course Tests | Yes | No | Yes | Dedicated UI |
| `services/crstests/node2` | `crstests` | consumer:optional, token:optional | `none` | No | Yes | Lecturer Course Tests | Yes | No | Yes | Dedicated UI |
| `services/crstests/node_grades` | `crstests` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/node_points` | `crstests` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/node_stats` | `crstests` | consumer:required, token:required | `crstests` | No | Yes | Lecturer Course Tests | Yes | No | Yes | Dedicated UI |
| `services/crstests/participant` | `crstests` | consumer:required, token:required | `crstests` | No | Yes | Grades / Course Tests | Yes | No | Yes | Dedicated UI |
| `services/crstests/participant2` | `crstests` | consumer:required, token:required | `crstests` | No | Yes | Grades / Course Tests | Yes | No | Yes | Dedicated UI |
| `services/crstests/public_templates` | `crstests` | consumer:required, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/root_node` | `crstests` | consumer:required, token:required | `crstests` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/root_nodes` | `crstests` | consumer:required, token:required | `crstests` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/student_grade` | `crstests` | consumer:required, token:required | `crstests` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/student_point` | `crstests` | consumer:required, token:required | `crstests` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/subscribe` | `crstests` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/task_node` | `crstests` | consumer:required, token:required | `crstests` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/task_node_details` | `crstests` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/unsubscribe` | `crstests` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/update_grades` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | Yes | Lecturer Course Tests (Experimental Write) | No | Experimental Lecturer Write | Yes | Experimental Write UI |
| `services/crstests/update_points` | `crstests` | consumer:required, token:required | `staff_perspective, crstests` | No | Yes | Lecturer Course Tests (Experimental Write) | No | Experimental Lecturer Write | Yes | Experimental Write UI |
| `services/crstests/user_grade` | `crstests` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/user_grade_modified` | `crstests` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/user_grades` | `crstests` | consumer:required, token:required | `crstests, grades` | No | Yes | Grades / Course Tests | Yes | No | Yes | Dedicated UI |
| `services/crstests/user_grades_modified` | `crstests` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/user_point` | `crstests` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/crstests/user_point_modified` | `crstests` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/crstests/user_points` | `crstests` | consumer:required, token:required | `crstests` | No | Yes | Grades / Course Tests | Yes | No | Yes | Dedicated UI |
| `services/crstests/user_points_modified` | `crstests` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/csgroups/create` | `csgroups` | consumer:required, token:required | `studies, mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/csgroups/custom_group` | `csgroups` | consumer:required, token:required | `studies, mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/csgroups/custom_groups` | `csgroups` | consumer:required, token:required | `studies, mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/csgroups/delete` | `csgroups` | consumer:required, token:required | `studies, mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/csgroups/update` | `csgroups` | consumer:required, token:required | `studies, mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/csgroups/user` | `csgroups` | consumer:required, token:required | `studies, mailclient` | No | Yes | Academic Calendar & Groups | Yes | No | Yes | Dedicated UI |
| `services/emrex/delete_elmo` | `emrex` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/emrex/get_elmo` | `emrex` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/emrex/put_elmo` | `emrex` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/emrex/save_as_document` | `emrex` | consumer:required, token:required | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/events/notifier_status` | `events` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events/register_fcm_token` | `events` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events/registered_fcm_tokens` | `events` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events/subscribe_event` | `events` | consumer:required, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events/subscriptions` | `events` | consumer:required, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events/test_my_fcm` | `events` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events/unsubscribe` | `events` | consumer:required, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/add_recipient` | `events2` | consumer:required, token:required | `events` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events2/delete_recipient` | `events2` | consumer:required, token:required | `events` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events2/delete_statement` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events2/distribution_channel` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/event_channel` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/event_channel_preference` | `events2` | consumer:required, token:required | `events` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/event_preference` | `events2` | consumer:required, token:required | `events` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/event_type` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/event_type_schedule` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/event_types_index` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/generate_one_time_code` | `events2` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events2/generate_one_time_code2` | `events2` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events2/latest_notifications` | `events2` | consumer:required, token:required | `events` | No | Yes | Statements & USOSmail | Yes | No | Yes | Dedicated UI |
| `services/events2/notification_channels_index` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/notifications_schedule` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/notifications_schedule_index` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/personalized_statements` | `events2` | consumer:optional, token:optional | `none` | No | Yes | Statements & USOSmail | Yes | No | Yes | Dedicated UI |
| `services/events2/search_statements` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/send_notification` | `events2` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/events2/statement` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/update_event_channel` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events2/update_event_schedule` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events2/update_event_type` | `events2` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events2/update_statement` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events2/update_user_preference` | `events2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/events2/user_preferences` | `events2` | consumer:required, token:required | `events` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/events2/validate_one_time_code` | `events2` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/examrep/course_edition` | `examrep` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep/course_edition2` | `examrep` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep/exam` | `examrep` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep/exam_session` | `examrep` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep/user` | `examrep` | consumer:required, token:required | `none` | No | Yes | Grades | Yes | No | Yes | Dedicated UI |
| `services/examrep/user2` | `examrep` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/class_group` | `examrep2` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/examinee` | `examrep2` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/examrep` | `examrep2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/examrep_session` | `examrep2` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/examrep_session_access` | `examrep2` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/examrep_type` | `examrep2` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/grade_type` | `examrep2` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/grade_value` | `examrep2` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/grader` | `examrep2` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/student_grade` | `examrep2` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/student_grades` | `examrep2` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/examrep2/update_student_grade` | `examrep2` | consumer:required, token:required | `grades_write` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/examrep2/user_grade` | `examrep2` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/exams/active_examination_sessions` | `exams` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/exams/administrator` | `exams` | consumer:required, token:required | `staff_perspective` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/exams/batch_register_exam` | `exams` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/exams/batch_registration` | `exams` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/exams/batch_unregister_exam` | `exams` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/exams/condition` | `exams` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/exams/exam` | `exams` | consumer:optional, token:optional | `none` | No | Yes | Exams (Details) | Yes | No | Yes | Dedicated UI |
| `services/exams/exam_group` | `exams` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/exams/examination_session` | `exams` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/exams/examination_sessions` | `exams` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/exams/exams` | `exams` | consumer:optional, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/exams/faculty_examination_sessions` | `exams` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/exams/group` | `exams` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/exams/search_exams_registrations` | `exams` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/exams/slot` | `exams` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/exams/student_exams` | `exams` | consumer:required, token:required | `studies` | No | Yes | Academic Extras (Exams) | Yes | No | Yes | Dedicated UI |
| `services/exams/user` | `exams` | consumer:required, token:required | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/fac/factsheet_get` | `fac` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/fac/faculties` | `fac` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/fac/faculty` | `fac` | consumer:optional, token:optional | `none` | No | Yes | Campus / News Affiliation | Yes | No | Yes | Dedicated UI |
| `services/fac/resolve_facpattern` | `fac` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/fac/search` | `fac` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/fac/subfaculties_deep` | `fac` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/facperms/delete` | `facperms` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/facperms/effective_fac_ids` | `facperms` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/facperms/fpclass` | `facperms` | consumer:required, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/facperms/fpclass_index` | `facperms` | consumer:required, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/facperms/fpclasses` | `facperms` | consumer:required, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/facperms/has_facperm` | `facperms` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/facperms/replace` | `facperms` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/facperms/select` | `facperms` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/feedback/report_db` | `feedback` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/feedback/status` | `feedback` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/fileshare/create_link` | `fileshare` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/fileshare/parse_link` | `fileshare` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/fileshare/set_404` | `fileshare` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/geo/building` | `geo` | consumer:ignored, token:ignored | `none` | No | Yes | Campus Guide (Buildings) | Yes | No | Yes | Dedicated UI |
| `services/geo/building2` | `geo` | consumer:ignored, token:ignored | `none` | No | Yes | Campus Guide (Building Rooms) | Yes | No | Yes | Dedicated UI |
| `services/geo/building_index` | `geo` | consumer:ignored, token:ignored | `none` | No | Yes | Campus Guide (Buildings) | Yes | No | Yes | Dedicated UI |
| `services/geo/buildings` | `geo` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/geo/buildings2` | `geo` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/geo/room` | `geo` | consumer:optional, token:optional | `none` | No | Yes | Campus Guide (Room Schedule) | Yes | No | Yes | Dedicated UI |
| `services/geo/rooms` | `geo` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/geo/staticmap` | `geo` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/grades/course_edition` | `grades` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/grades/course_edition2` | `grades` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/grades/exam` | `grades` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/grades/grade` | `grades` | consumer:required, token:required | `grades` | No | Yes | Grades | Yes | No | Yes | Dedicated UI |
| `services/grades/grade_type` | `grades` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/grades/grade_type_index` | `grades` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/grades/grade_types` | `grades` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/grades/latest` | `grades` | consumer:required, token:required | `grades` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/grades/terms` | `grades` | consumer:required, token:required | `grades` | No | Yes | Grades | Yes | No | Yes | Dedicated UI |
| `services/grades/terms2` | `grades` | consumer:required, token:required | `grades` | No | Yes | Grades | Yes | No | Yes | Dedicated UI |
| `services/grades/update_grade` | `grades` | consumer:required, token:required | `none` | Yes | Yes | None (Admin-only) | Yes | No | Yes | Dedicated UI |
| `services/groups/class_group` | `groups` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/groups/common_groups` | `groups` | consumer:required, token:required | `studies` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/groups/group` | `groups` | consumer:optional, token:optional | `none` | No | Yes | Lecturer Groups | Yes | No | Yes | Dedicated UI |
| `services/groups/groups` | `groups` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/groups/is_lecturer` | `groups` | consumer:optional, token:optional | `none` | No | Yes | Lecturer Groups | Yes | No | Yes | Dedicated UI |
| `services/groups/is_participant` | `groups` | consumer:required, token:required | `none` | No | Yes | Lecturer Groups | Yes | No | Yes | Dedicated UI |
| `services/groups/lecturer` | `groups` | consumer:optional, token:optional | `none` | No | Yes | Lecturer Groups | Yes | No | Yes | Dedicated UI |
| `services/groups/participant` | `groups` | consumer:required, token:required | `studies` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/groups/user` | `groups` | consumer:optional, token:optional | `none` | No | Yes | Lecturer / Student Groups | Yes | No | Yes | Dedicated UI |
| `services/guide/chapter` | `guide` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/guide/chapters` | `guide` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/guide/entries` | `guide` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/guide/entry` | `guide` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/guide/guide` | `guide` | consumer:required, token:optional | `none` | No | Yes | Campus Guide | Yes | No | Yes | Dedicated UI |
| `services/guide/page` | `guide` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/guide/pages` | `guide` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/allocation_period` | `housing` | consumer:ignored, token:ignored | `none` | No | Yes | Campus Guide (Dormitories) | Yes | No | Yes | Dedicated UI |
| `services/housing/allocation_period_index` | `housing` | consumer:ignored, token:ignored | `none` | No | Yes | Campus Guide (Dormitories) | Yes | No | Yes | Dedicated UI |
| `services/housing/allocation_periods` | `housing` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/assignment` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/assignment_extend` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/assignments` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/check_in` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/check_out` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/dormitories` | `housing` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/dormitory` | `housing` | consumer:optional, token:optional | `none` | No | Yes | Campus Guide (Dormitories) | Yes | No | Yes | Dedicated UI |
| `services/housing/dormitory_index` | `housing` | consumer:optional, token:optional | `none` | No | Yes | Campus Guide (Dormitories) | Yes | No | Yes | Dedicated UI |
| `services/housing/dormitory_user` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/dormitory_users` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/dormroom` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/dormroom_type` | `housing` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/dormroom_type_index` | `housing` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/dormroom_types` | `housing` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/dormrooms` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/residence` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/residences` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/resident_type` | `housing` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/resident_type_index` | `housing` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/resident_types` | `housing` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/housing/unused_assignment_ids` | `housing` | consumer:required, token:required | `dorm_admin` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/instaddr/all` | `instaddr` | consumer:required, token:optional | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/instaddr/create` | `instaddr` | consumer:required, token:optional | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/instaddr/delete` | `instaddr` | consumer:required, token:optional | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/instaddr/inst_address` | `instaddr` | consumer:required, token:optional | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/instaddr/inst_addresses` | `instaddr` | consumer:required, token:optional | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/instaddr/update` | `instaddr` | consumer:required, token:optional | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/instaddr/update_user` | `instaddr` | consumer:required, token:optional | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/instaddr/user` | `instaddr` | consumer:required, token:required | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/internships/create_company` | `internships` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/internships/update_company` | `internships` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/mailclient/attachment` | `mailclient` | consumer:required, token:required | `mailclient` | No | Yes | Statements & USOSmail (Message Attachments) | Yes | No | Yes | Dedicated UI |
| `services/mailclient/attachments` | `mailclient` | consumer:required, token:required | `mailclient` | No | Yes | Statements & USOSmail (Attachments) | Yes | No | Yes | Dedicated UI |
| `services/mailclient/case_category` | `mailclient` | consumer:required, token:optional | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mailclient/cases_categories` | `mailclient` | consumer:required, token:optional | `mailclient` | No | Yes | Statements & USOSmail | Yes | No | Yes | Dedicated UI |
| `services/mailclient/copy_message` | `mailclient` | consumer:required, token:required | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mailclient/create_and_send_message` | `mailclient` | consumer:required, token:optional | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mailclient/create_message` | `mailclient` | consumer:required, token:required | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mailclient/delete_attachments` | `mailclient` | consumer:required, token:required | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mailclient/delete_messages` | `mailclient` | consumer:required, token:required | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mailclient/message` | `mailclient` | consumer:required, token:optional | `mailclient` | No | Yes | Statements & USOSmail (Message Details) | Yes | No | Yes | Dedicated UI |
| `services/mailclient/message_address_limit` | `mailclient` | consumer:required, token:optional | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mailclient/messages` | `mailclient` | consumer:required, token:required | `mailclient` | No | Yes | Statements & USOSmail (Messages) | Yes | No | Yes | Dedicated UI |
| `services/mailclient/put_attachment` | `mailclient` | consumer:required, token:required | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mailclient/recipients` | `mailclient` | consumer:required, token:optional | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mailclient/recipients_group` | `mailclient` | consumer:required, token:required | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mailclient/refresh_recipients` | `mailclient` | consumer:required, token:optional | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mailclient/retry_send_message` | `mailclient` | consumer:required, token:required | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mailclient/send_message` | `mailclient` | consumer:required, token:optional | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mailclient/update_attachment` | `mailclient` | consumer:required, token:required | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mailclient/update_message` | `mailclient` | consumer:required, token:required | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mailclient/update_recipients_group` | `mailclient` | consumer:required, token:optional | `mailclient` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mailclient/user` | `mailclient` | consumer:required, token:required | `mailclient` | No | Yes | Statements & USOSmail | Yes | No | Yes | Dedicated UI |
| `services/mailing/check_message_status` | `mailing` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/mailing/check_messages_status` | `mailing` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/mailing/send_simple_by_template` | `mailing` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/mailing/send_simple_message` | `mailing` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/mailing/send_simple_messages` | `mailing` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/mailing/system_status` | `mailing` | consumer:optional, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/accept_participant` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/available_meetings` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/cart` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/course_meetings` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/courses_with_meetings` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/delete_note` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/meetings/generate_meeting_dates` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/meetings/get_meeting_substantiations` | `meetings` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/get_note` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/get_rejection_substantiations` | `meetings` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/lecturer` | `meetings` | consumer:required, token:required | `none` | No | Yes | Lecturer Consultations | Yes | No | Yes | Dedicated UI |
| `services/meetings/lecturer_meeting_dates` | `meetings` | consumer:required, token:required | `none` | No | Yes | Lecturer Consultations | Yes | No | Yes | Dedicated UI |
| `services/meetings/meeting` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/meeting_all_dates` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/meeting_date` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/meeting_date_participants` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/meeting_dates` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/meetings` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/note` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/open_meeting_dates` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/participant` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/participants` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/put_note` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/meetings/reject_participant` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/meetings/search_meeting_dates` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/sign_off` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/meetings/sign_up` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/meetings/sign_up_non_usos_person` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/meetings/sign_up_specific_user` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/meetings/substantiation` | `meetings` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/meetings/user_calendar` | `meetings` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/approval` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/approve` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/cancel_approvals` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/cancel_version` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/change_reason` | `mobility` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/change_reasons` | `mobility` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/component` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/coordinator` | `mobility` | consumer:required, token:required | `none` | No | Yes | Mobility & Erasmus+ (Lecturer) | Yes | No | Yes | Dedicated UI |
| `services/mobility/create_cnr` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/create_component` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/create_draft` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/create_external_user` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/create_la` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/decision` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/decision_points` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/decisions_course` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/delete_component` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/delete_decisions_course` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/delete_decisions_points` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/delete_external_course` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/delete_la` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/delete_version` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/external_course` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/external_user` | `mobility` | consumer:required, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/institution` | `mobility` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/la` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/la_to_decision` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/la_version` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/mobility` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/points_type` | `mobility` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/points_type_index` | `mobility` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/search_course` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/mobility/student` | `mobility` | consumer:required, token:required | `none` | No | Yes | Mobility & Erasmus+ | Yes | No | Yes | Dedicated UI |
| `services/mobility/update_component` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/update_decision` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/update_decision_course` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/update_decision_points` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/update_external_course` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/update_external_user` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/update_la` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/update_la_status` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/update_mobility` | `mobility` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/mobility/update_status` | `mobility` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/news/article` | `news` | consumer:required, token:optional | `none` | No | Yes | News | Yes | No | Yes | Dedicated UI |
| `services/news/articles` | `news` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/news/category_index` | `news` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/news/search` | `news` | consumer:required, token:optional | `none` | No | Yes | News | Yes | No | Yes | Dedicated UI |
| `services/oauth/access_token` | `oauth` | consumer:required, token:required | `none` | No | Yes | Account (OAuth) | No | No | Yes | Dedicated UI |
| `services/oauth/authorize` | `oauth` | consumer:ignored, token:ignored | `none` | No | Yes | Account (OAuth) | No | No | Yes | Dedicated UI |
| `services/oauth/proxy` | `oauth` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/oauth/request_token` | `oauth` | consumer:required, token:ignored | `none` | No | Yes | Account (OAuth) | No | No | Yes | Dedicated UI |
| `services/oauth/revoke_consumer_key` | `oauth` | consumer:optional, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/oauth/revoke_token` | `oauth` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/oauth2/impersonated_token` | `oauth2` | consumer:required, token:optional | `none` | Yes | Yes | None (Admin-only) | Yes | No | Yes | Dedicated UI |
| `services/payments/choose_installment_plan` | `payments` | consumer:required, token:required | `payments` | No | Yes | Payments | No | Student Write | Yes | Dedicated UI |
| `services/payments/installment` | `payments` | consumer:required, token:required | `payments` | No | Yes | Payments (Installments, shown with user_payments) | Yes | No | Yes | Dedicated UI |
| `services/payments/installment_plan` | `payments` | consumer:required, token:required | `payments` | No | Yes | Payments (Installment Plans, shown with user_payments) | Yes | No | Yes | Dedicated UI |
| `services/payments/payment` | `payments` | consumer:required, token:required | `payments` | No | Yes | Payments (Plan Details & Selection) | Yes | No | Yes | Dedicated UI |
| `services/payments/remittance` | `payments` | consumer:required, token:required | `payments` | No | Yes | Payments (Remittance Details) | Yes | No | Yes | Dedicated UI |
| `services/payments/user_accounts` | `payments` | consumer:required, token:required | `payments` | No | Yes | Payments | Yes | No | Yes | Dedicated UI |
| `services/payments/user_payments` | `payments` | consumer:required, token:required | `payments` | No | Yes | Payments | Yes | No | Yes | Dedicated UI |
| `services/payments/user_remittances` | `payments` | consumer:required, token:required | `payments` | No | Yes | Payments | Yes | No | Yes | Dedicated UI |
| `services/phones/phone_number` | `phones` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/phones/phone_type` | `phones` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/phones/replace` | `phones` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/phones/select` | `phones` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/photos/can_see_photo` | `photos` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/photos/delete_photo` | `photos` | consumer:required, token:required | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/photos/my_photo_visibility` | `photos` | consumer:required, token:required | `photo` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/photos/photo` | `photos` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/photos/settings` | `photos` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/photos/update_photo` | `photos` | consumer:required, token:required | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/pit/fetch_pdf` | `pit` | consumer:required, token:required | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/pit/pit` | `pit` | consumer:required, token:required | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/pit/user` | `pit` | consumer:required, token:required | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/plctests/test` | `plctests` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/plctests/test_result` | `plctests` | consumer:required, token:required | `placement_tests` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/plctests/tests_index` | `plctests` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/plctests/update_result` | `plctests` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/plctests/update_results` | `plctests` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/plctests/user` | `plctests` | consumer:required, token:required | `placement_tests` | No | Yes | Placement Tests & Slips | Yes | No | Yes | Dedicated UI |
| `services/prgroups/admin` | `prgroups` | consumer:required, token:required | `studies` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/prgroups/auto_assign_users` | `prgroups` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/prgroups/create_descriptor` | `prgroups` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/prgroups/delete_descriptors` | `prgroups` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/prgroups/descriptor` | `prgroups` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/prgroups/descriptors` | `prgroups` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/prgroups/descriptors_all` | `prgroups` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/prgroups/primary_group` | `prgroups` | consumer:required, token:required | `studies` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/prgroups/primary_groups` | `prgroups` | consumer:required, token:required | `studies` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/prgroups/schema` | `prgroups` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/prgroups/update_descriptor` | `prgroups` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/prgroups/update_user` | `prgroups` | consumer:required, token:optional | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/prgroups/user` | `prgroups` | consumer:required, token:required | `studies` | No | Yes | Academic Calendar & Groups | Yes | No | Yes | Dedicated UI |
| `services/progs/programme` | `progs` | consumer:optional, token:ignored | `none` | No | Yes | Academic Calendar (Programme Details) | Yes | No | Yes | Dedicated UI |
| `services/progs/programmes` | `progs` | consumer:optional, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/progs/search` | `progs` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/progs/search_history_affect` | `progs` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/progs/stage` | `progs` | consumer:ignored, token:ignored | `none` | No | Yes | Academic Calendar (Study Stage Details) | Yes | No | Yes | Dedicated UI |
| `services/progs/student` | `progs` | consumer:required, token:optional | `none` | No | Yes | Account / Academic Extras | Yes | No | Yes | Dedicated UI |
| `services/progs/student_programme` | `progs` | consumer:required, token:optional | `none` | No | Yes | Academic Calendar (Study Stages) | Yes | No | Yes | Dedicated UI |
| `services/progs/student_programmes` | `progs` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/course_registration_rounds` | `registrations` | consumer:required, token:optional | `none` | No | Yes | Registrations | Yes | No | Yes | Dedicated UI |
| `services/registrations/courses_cart` | `registrations` | consumer:required, token:optional | `none` | No | Yes | Registrations | Yes | No | Yes | Dedicated UI |
| `services/registrations/faculty_registrations` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/faculty_token_registrations` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/ranking` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/register` | `registrations` | consumer:required, token:optional | `studies` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/registrations/registration` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/registration_course` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/registration_round` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/registration_round_courses` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/search_rounds` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/search_token_rounds` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/token_registration` | `registrations` | consumer:required, token:optional | `none` | No | Yes | Token Registration Details | Yes | No | Yes | Dedicated UI |
| `services/registrations/token_registration_course` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/token_registration_round` | `registrations` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/registrations/unregister` | `registrations` | consumer:required, token:optional | `studies` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/registrations/user_registrations` | `registrations` | consumer:required, token:required | `studies` | No | Yes | Registrations | Yes | No | Yes | Dedicated UI |
| `services/registrations/user_token_registrations` | `registrations` | consumer:required, token:required | `studies` | No | Yes | Registrations | Yes | No | Yes | Dedicated UI |
| `services/slips/add_question` | `slips` | consumer:required, token:required | `slips_admin` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/slips/create_template` | `slips` | consumer:required, token:required | `slips_admin` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/slips/delete_question` | `slips` | consumer:required, token:required | `slips_admin` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/slips/delete_slip` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/slips/delete_template` | `slips` | consumer:required, token:required | `slips_admin` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/slips/issue_slip` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/slips/question` | `slips` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/questions` | `slips` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/search_answerer` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/search_issuer` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/search_slips` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/search_templates` | `slips` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/slip` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/slipquestion` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/slipquestions` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/slips` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/template` | `slips` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/templates` | `slips` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/to_be_answered` | `slips` | consumer:required, token:required | `slips` | No | Yes | Placement Tests & Slips | Yes | No | Yes | Dedicated UI |
| `services/slips/trigger` | `slips` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/trigger_index` | `slips` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/triggers` | `slips` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/slips/update_question` | `slips` | consumer:required, token:required | `slips_admin` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/slips/update_slip` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/slips/update_slipquestion` | `slips` | consumer:required, token:required | `slips` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/slips/update_template` | `slips` | consumer:required, token:required | `slips_admin` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/slips/user_roles` | `slips` | consumer:required, token:required | `none` | No | Yes | Placement Tests & Slips | Yes | No | Yes | Dedicated UI |
| `services/statements/awaiting_statements` | `statements` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/statements/statement` | `statements` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/statements/statement_declaration` | `statements` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/statements/statement_declarations` | `statements` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/statements/statements` | `statements` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/statements/user_statements` | `statements` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/statements/user_status` | `statements` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/surveys/fill_out` | `surveys` | consumer:required, token:required | `surveys_filling` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/surveys/fill_out2` | `surveys` | consumer:required, token:required | `surveys_filling` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/surveys/survey` | `surveys` | consumer:required, token:required | `none` | No | Yes | Statements & USOSmail (Survey Details) | Yes | No | Yes | Dedicated UI |
| `services/surveys/survey2` | `surveys` | consumer:required, token:required | `none` | No | Yes | Statements & USOSmail (Survey Details) | Yes | No | Yes | Dedicated UI |
| `services/surveys/surveys` | `surveys` | consumer:required, token:required | `none` | No | Yes | Statements & USOSmail (Surveys) | Yes | No | Yes | Dedicated UI |
| `services/surveys/surveys2` | `surveys` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/surveys/surveys_about_me` | `surveys` | consumer:required, token:required | `surveys_reports` | No | Yes | Lecturer Surveys & Thesis Reports | Yes | No | Yes | Dedicated UI |
| `services/surveys/surveys_about_me2` | `surveys` | consumer:required, token:required | `surveys_reports` | No | Yes | Lecturer Surveys & Thesis Reports | Yes | No | Yes | Dedicated UI |
| `services/surveys/surveys_to_fill` | `surveys` | consumer:required, token:required | `surveys_filling` | No | Yes | Statements & USOSmail (Surveys) | Yes | No | Yes | Dedicated UI |
| `services/surveys/surveys_to_fill2` | `surveys` | consumer:required, token:required | `surveys_filling` | No | Yes | Statements & USOSmail (Surveys) | Yes | No | Yes | Dedicated UI |
| `services/terms/search` | `terms` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/terms/term` | `terms` | consumer:ignored, token:ignored | `none` | No | Yes | Grades / Schedule | Yes | No | Yes | Dedicated UI |
| `services/terms/terms` | `terms` | consumer:ignored, token:ignored | `none` | No | Yes | Grades / Schedule (Terms) | Yes | No | Yes | Dedicated UI |
| `services/terms/terms_index` | `terms` | consumer:ignored, token:ignored | `none` | No | Yes | Grades / Schedule | Yes | No | Yes | Dedicated UI |
| `services/theses/authorship_preference` | `theses` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/theses/exam_report` | `theses` | consumer:required, token:required | `staff_perspective` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/theses/exam_reports` | `theses` | consumer:required, token:required | `staff_perspective` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/theses/search` | `theses` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/theses/search_history_affect` | `theses` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/theses/sign_report` | `theses` | consumer:required, token:required | `theses_protocols_write, staff_perspective` | No | Yes | Lecturer Surveys & Thesis Reports | Yes | No | Yes | Dedicated UI |
| `services/theses/signable_reports` | `theses` | consumer:required, token:required | `staff_perspective` | No | Yes | Lecturer Surveys & Thesis Reports | Yes | No | Yes | Dedicated UI |
| `services/theses/theses` | `theses` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/theses/thesis` | `theses` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/theses/user` | `theses` | consumer:required, token:optional | `none` | No | Yes | Services / Profile | Yes | No | Yes | Dedicated UI |
| `services/theses/users` | `theses` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/tt/classgroup` | `tt` | consumer:ignored, token:ignored | `none` | No | Yes | Schedule | Yes | No | Yes | Dedicated UI |
| `services/tt/classgroup_dates` | `tt` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/tt/classgroup_dates2` | `tt` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/tt/classgroups` | `tt` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/tt/course_edition` | `tt` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/tt/course_editions` | `tt` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/tt/room` | `tt` | consumer:required, token:ignored | `none` | No | Yes | Campus Guide (Room Schedule) | Yes | No | Yes | Dedicated UI |
| `services/tt/staff` | `tt` | consumer:optional, token:optional | `none` | No | Yes | Schedule / Lecturer Panel / Staff Directory | Yes | No | Yes | Dedicated UI |
| `services/tt/student` | `tt` | consumer:required, token:required | `studies` | No | Yes | Schedule / Home | Yes | No | Yes | Dedicated UI |
| `services/tt/upcoming_ical` | `tt` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/tt/upcoming_share` | `tt` | consumer:required, token:required | `studies` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/tt/user` | `tt` | consumer:required, token:required | `studies` | No | Yes | Schedule / Home (Lecturer) | Yes | No | Yes | Dedicated UI |
| `services/uprefs/change_preference` | `uprefs` | consumer:required, token:required | `change_all_preferences` | No | No | API Explorer | No | No | Catalog Test | Mutating / Blocked in Catalog |
| `services/uprefs/preference` | `uprefs` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/uprefs/preferences` | `uprefs` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/uprefs/values` | `uprefs` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/users/change` | `users` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/employment_functions` | `users` | consumer:optional, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/employment_group` | `users` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/employment_groups_index` | `users` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/employment_positions` | `users` | consumer:optional, token:optional | `none` | No | Yes | Account / Affiliation | Yes | No | Yes | Dedicated UI |
| `services/users/pesel` | `users` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/users/photo` | `users` | consumer:required, token:required | `photo` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/position` | `users` | consumer:ignored, token:ignored | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/search` | `users` | consumer:required, token:optional | `none` | No | Yes | Staff Directory | Yes | No | Yes | Dedicated UI |
| `services/users/search2` | `users` | consumer:required, token:optional | `none` | No | Yes | Staff Directory | Yes | No | Yes | Dedicated UI |
| `services/users/search_by_email` | `users` | consumer:required, token:ignored | `none` | Yes | No | None (Admin-only) | No | No | Catalog Test | Admin-Only / Blocked |
| `services/users/search_current_students` | `users` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/search_current_teachers` | `users` | consumer:optional, token:optional | `none` | No | Yes | Staff Directory | Yes | No | Yes | Dedicated UI |
| `services/users/search_history_affect` | `users` | consumer:required, token:required | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/search_staff` | `users` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/search_students` | `users` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/staff_index` | `users` | consumer:required, token:optional | `none` | No | Yes | Staff Directory | Yes | No | Yes | Dedicated UI |
| `services/users/student_index` | `users` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/student_programmes` | `users` | consumer:required, token:required | `studies` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/user` | `users` | consumer:required, token:optional | `none` | No | Yes | Account / Profile / Lecturer Panel / Staff Directory | Yes | No | Yes | Dedicated UI |
| `services/users/user2` | `users` | consumer:required, token:optional | `none` | No | No | API Explorer | No | No | Catalog Test | Catalog Explorer (Read) |
| `services/users/users` | `users` | consumer:required, token:optional | `none` | No | Yes | Schedule / Grades (Lecturers) | Yes | No | Yes | Dedicated UI |
