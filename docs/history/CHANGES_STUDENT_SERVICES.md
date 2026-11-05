# Student services and complete detail views

## Legitymacje

New **Więcej → Legitymacje** and drawer shortcut:

- Physical cards from `services/cards/user`: owner, student number, issue/expiration dates, printed card number and expiry reminders.
- Current mLegitymacja order, translated status, expected expiration and reasons USOS cannot order/revoke.
- Ordering/revoking asks for confirmation and rechecks eligibility in USOS before sending POST. The response must explicitly confirm `success`.
- Activation data is fetched on demand from `mcard_order2`, shown in a protected dialog, and excluded from encrypted offline status caches.
- The screen is an informational USOS view; the official electronic credential is presented in mObywatel.

`cards` consent is required. Existing consent is not expanded silently. The account form includes a cards/documents scope shortcut; reauthorization remains necessary for an existing account.

## Documents

New **Więcej → Dokumenty i decyzje**:

- Searchable list from `adm_documents/user_documents`, including translated names/statuses and publication/delivery dates.
- Download document, UPO or UPD using the system file picker only when the corresponding action is granted by USOS.
- Selected document IDs are prefilled in detailed API forms, including code generation and receipt.
- Separate encrypted cache preserves the last successful result after a failed refresh. Downloads and card/order actions are explicit foreground operations.

`adm_documents` consent is required. Demo data remains separate and its card actions and downloads do not call a real account.

## API coverage and usability

The public Vistula index and bundled catalog were compared: all 571 method names match. All modules are discovered dynamically. Polish search aliases make services easier to find. The explorer includes access/scope guidance, documented defaults, output-field/error documentation, selectable response preview, copying and full file/response export.

Method/form state survives rotation; secret/token/password/code parameters are excluded from saved-instance drafts. Uploads use the current selected file and validate multipart POST. OAuth response exports remain hidden. Large response previews are bounded while exports keep full bytes (existing 8 MB limit).

Validate the catalog offline with `powershell -File scripts/Test-ApiCatalog.ps1`; add `-CheckRemote` to compare public method names. Server roles, administrative keys and OAuth permissions still determine which methods a given account can execute. Catalog coverage does not turn administrative methods into student permissions.

## Grade correctness and details

- `grade_type_id` is a grading-system ID, never a lecture/laboratory label. Class labels use explicit class-type data, course-unit lookup or timetable data; unknown types have explicit protocol/unknown labels. Previously guessed cache labels are ignored.
- Exam session numbers do not automatically claim an attempt is a retake. Pass/fail badge colors follow the API's `passes` flag.
- Subjects with multiple grade entries show an entry count instead of presenting an arbitrary unit/session grade as a final subject grade.
- Course details include all grades in the same course edition, even when the list has an active class-type filter. Individual component details remain focused on that entry.
- Details expose all stored metadata, related test records and selectable complete JSON. Newly received grade and lesson responses are preserved as `rawGrade` / `rawLesson` for fields that do not yet have dedicated labels.
- Course-unit metadata and modification-author names are resolved in bounded batches. Unavailable optional enrichment leaves grades intact and clearly unknown.
- ECTS estimates separate course IDs, skip grades explicitly excluded from averages, prefer course protocols, and document the highest-visible-entry estimate. They are not an official GPA.
- Changes in pass/average flags are detectable without treating newly added class-type metadata as a grade change.

Verification covers core/app unit tests, debug build, lint, and isolated Android emulator flows. Live account permissions, card order/revocation and activation were not exercised against a real USOS account.

Final checks: 116 core/app unit tests passed; all 10 screen-regression cases passed, with the subject-label/detail case rechecked after the final badge change. Lint completed without errors (existing warnings remain). The public-index audit reports 571 methods / 52 modules with complete local metadata. Detail-dialog screenshots were reviewed and their close controls verified visible.
