# API support, security, More view, and Settings improvement plan

Date: 2026-10-19

Implementation record: `CHANGES_API_CAMPUS_SETTINGS.md`. Settings uses focused subpages; campus detection and filtering were added after the original plan. Candidate new services in section 5 remain a future backlog unless covered by that implementation record.

Scope: implementation plan for the Android app and its shared JVM data layer. This document does not implement changes or constitute a security audit. “More” means the Więcej student tools hub and the service screens opened from it.

Expanded scope: close gaps in usable API support and reorganize the current Account/settings screen into a friendly Settings destination. Implementation must preserve existing accounts, preferences, navigation, and signing continuity.

User clarification: improve every destination under More primarily through a simpler appearance and clearer presentation, rather than adding more information to each screen. API coverage work remains in scope, but newly supported data should appear in focused details instead of crowding the main views.

## Current baseline

- `MoreScreenController.kt` renders grouped navigation tiles with static descriptions. It does not currently present service data or freshness summaries.
- `StudentServiceLoader.kt` loads cards and documents on demand, checks scopes, preserves cached data on failure, records attempt/success timestamps, and rejects writes after an account change.
- `PaymentsScreenController.kt` reads payments from the main snapshot and displays its overall `successAt`, rather than an independent payment fetch timestamp.
- `UsosClient.kt` already disables redirects and HTTP caching, sets connection/read timeouts, limits responses and uploads to 8 MB, and disconnects connections in `finally`.
- `ApiFailure.kt` already classifies authorization failures, retryable HTTP failures, and Retry-After. `SharedRepository.kt` coordinates account identity and synchronization cooldowns.
- `ApiExplorerController.kt` exposes a broad API catalog, parameter entry, request confirmation, response copying, and export. These paths need targeted review as privileged diagnostic features.
- `SECURITY.md`, `RELEASE.md`, and the Android manifest already establish encrypted storage, no embedded release credentials, disabled backup, HTTPS, and release verification requirements. Preserve these protections.
- `AccountScreenController.kt` currently combines student profile, academic information, appearance/language, login, scope strings, background sync, notifications, reminders, and widgets. Scope presets exist, but users still encounter technical permission strings.
- `CHANGES_STUDENT_SERVICES.md` records a previous catalog comparison of 571 methods across 52 modules. That historical result is not a current capability check: catalog metadata, successful execution, and a dedicated usable student screen are different levels of support.

## 1. P0 — Establish API contracts and security boundaries

1. Inventory the endpoints used by cards, documents, payments, news, profile, and API Explorer. Record required scopes, supported response shapes, read/write semantics, cache eligibility, and sensitive fields. Verify capabilities against the deployed university API metadata; catalog presence alone does not establish availability or user permission.
2. Define shared result/error models with explicit states: not requested, loading, available, empty after successful fetch, stale with cached data, missing scope, authorization expired, unsupported service, rate limited, offline, and malformed response.
3. Keep structured error codes separate from translated UI text. Preserve safe status/endpoint identifiers without retaining raw error bodies, signed URLs, OAuth parameters, or student data in diagnostics.
4. Review endpoint normalization, trusted HTTPS origin checks, OAuth parameter handling, and browser/document links. Add adversarial coverage for absolute URLs, path traversal, encoded separators, hostile redirects, and malformed content. Keep OAuth behavior compatible with USOS; verify server support before changing credential placement.
5. Trace credentials and personal data through storage, saved instance state, API drafts, clipboard, exported files, logs, and crash messages. Extend existing filtering where gaps are demonstrated. Store only necessary service fields; keep activation tokens/passwords transient and clear them on dismissal, logout, or account change.
6. Verify that mutating operations are recognized by endpoint semantics, including operations transported over GET. Require a concrete action summary, prevent duplicate submissions, and never automatically replay an action whose outcome is uncertain.

Acceptance: documented endpoint/capability matrix; regression cases cover trust boundaries and credential redaction; no new credential persistence or release secrets.

## 2. P1 — Unify request execution and section caching

1. Extend the existing shared repository/loader architecture with a service gateway that accepts the existing injectable client factory, clock, and cancellation hooks. Keep Android Views and existing executors; avoid a framework migration.
2. Consolidate preflight checks for account session, scopes, authorization state, and server cooldown. Recheck identity before committing data or updating UI after asynchronous work.
3. Deduplicate concurrent refreshes for the same account and section. Tie callbacks to their screen owner; stop abandoned read requests where possible and reject obsolete completions after navigation, rotation, logout, or account switching.
4. Add a bounded overall operation deadline alongside existing connection/read timeouts. Retain byte limits and inspect content types before parsing; handle invalid JSON and unexpected success payloads explicitly. Evaluate streaming document downloads to avoid repeated in-memory copies.
5. Use bounded exponential backoff with jitter only for explicitly safe read operations and retryable failures. Honor valid server Retry-After, use a bounded fallback for absent/invalid headers, and surface long valid cooldowns instead of retrying early. Do not retry authorization, missing-scope, validation, unsupported-service, or parsing failures automatically.
6. Give each remote section its own last successful fetch, last attempt, error, and cache schema version. Preserve good cached data on errors; a failed request must never become a successful empty result. Keep global authorization/cooldown rules where applicable.
7. Decouple payment/news refresh from a complete grades/timetable sync where practical. Introduce incremental cache migration, preserve account isolation and language variants, and keep offline upgrade behavior compatible with existing snapshots.

Acceptance: a failure in an optional section leaves other cached sections usable; repeated taps do not duplicate requests; stale/account-mismatched results never overwrite current data; tests verify retry limits and deadlines deterministically.

## 3. P1 — Display useful data in More

Retain the existing Studies, University, and App grouping and add compact summaries to the existing destinations. Render cached content immediately. Opening More should not fan out into a request for every API module.

| Destination | Proposed summary | Required behavior |
| --- | --- | --- |
| Cards | Physical/mobile card status and nearest expiry | Use cached allowed fields; show activation secrets only in the explicit activation flow |
| Documents | Available document count and latest publication date | Call a document “new” only after implementing a local seen marker; respect server-provided action availability |
| Payments | Outstanding amount, currency, and next due date where provided | Keep currencies separate; distinguish missing access, unavailable data, and a verified zero balance |
| News | Latest headline and publication date | Render safe text, validate links, and label cached content |
| Tasks / attendance / GPA | Existing local task count or calculated summary | Label local/calculated data clearly and avoid implying USOS verification |
| Account | Connection state and an actionable access warning | Link missing-scope/expired-session states to the account screen |

1. Use a reusable summary model with title, concise value, freshness, state, and destination. Keep raw JSON and endpoint names inside API Explorer.
2. Display section-specific timestamps and plain states such as “Saved data”, “Not loaded”, or “Reconnect account”. Never use the last overall sync as proof that every section is fresh.
3. Preserve cached values during refresh and show progress beside the affected item. Provide section retry; disable retry during server cooldown and show when it becomes available.
4. On service detail screens, distinguish successful empty results from search with no matches, offline cache, unsupported endpoints, missing permission, and expired authorization. Provide the appropriate recovery action in each case.
5. Preserve scroll position, search filters, focus, and navigation state after refresh. Use explicit text alongside status colors, Android touch targets of at least 48 dp, TalkBack labels, and layouts that support large fonts and light/dark themes.
6. Add all user-facing strings to Polish, English, and Ukrainian resources. Keep demo data separate and clearly identified. Do not introduce new background polling or notifications for summaries.

Acceptance: More provides useful cached summaries offline, each summary opens the matching screen, loading is scoped, unavailable data is never presented as zero, and opening the hub triggers no blanket network refresh.

## 4. P1 — Harden API Explorer and document handling

- Validate required parameters and declared value types before execution; distinguish public, authenticated, unsupported, and administrative methods.
- Make write effects explicit in confirmations. Disable repeated execution while pending and explain uncertain outcomes without silently retrying.
- Review saved drafts and process restoration against sensitive-field rules. Clear in-memory responses/uploads on account changes and prevent stale results from appearing in another session.
- Keep exports user initiated through the existing system file flow. Validate filenames, size, content type, link origin, and temporary-file cleanup. Explain when a chosen export or copy contains personal data; mark sensitive clipboard content using the supported Android mechanism.
- Use safe response previews with bounded rendering for large JSON/binary responses. Keep raw-response access explicit within the diagnostic tool.

Acceptance: synthetic secrets do not appear in restored drafts, logs, or unintended exports; download/export cancellation and hostile filenames are covered; mutating methods cannot be replayed by the retry layer.

## 5. P1/P2 — Close gaps in usable API support

1. Produce a coverage matrix from the bundled catalog, client calls, models, and screens. For every student-facing capability, record endpoint, server availability, scopes, pagination, parsed fields, cache, user interface, write actions, and fixture coverage. Mark support as catalog-only, callable in Explorer, partially integrated, fully integrated, or unavailable to this account. Do not claim that every catalog method should become a student feature.
2. Run `scripts/Test-ApiCatalog.ps1` offline and with `-CheckRemote` during implementation. Review changes to argument defaults, fields, auth requirements, and return/error schemas as well as method names; the existing remote comparison checks names only. Update metadata through the established refresh script and review the diff without account credentials.
3. Prioritize completing existing features before expanding the hub. Verify full payment/account/installment details, all permitted document actions, card eligibility/statuses, and complete course/test-result details. Identify exactly which fields and actions are missing before committing implementation scope.
4. Assess candidate student features currently lacking dedicated flows: course registration and available places, exam/session information, academic calendar events, programme/progress and ECTS details, thesis details, and staff/contact information. Some data already exists in snapshots or profile views; reuse it. Verify actual endpoints, university support, and access before promising a new screen.
5. Implement the highest-value verified gaps incrementally with typed parsers, pagination/bounded batching, localized values, detail views, section caching, and explicit unavailable states. Never silently truncate results or interpret an unsupported method as an empty account.
6. Treat registration, document receipt, and other consequential writes as separate flows with permission/eligibility checks, confirmation, duplicate prevention, and refreshed authoritative status afterward. Exclude administrative-only capabilities from normal student actions.
7. Let an unavailable dedicated feature explain the limitation and offer a verified official university link when one exists. API Explorer remains an advanced tool; a student must not need to enter endpoint parameters to complete a supported everyday task.

Acceptance: publish a checked coverage matrix with concrete gaps and implementation priorities; each shipped feature has an end-to-end flow and fixtures; no claim of complete API support based only on catalog counts. P1 completes existing features; P2 adds verified new capabilities.

## 6. P1 — Make Settings understandable and easy to navigate

Use the existing Account screen as the starting point. Make Settings a clearly labeled destination reachable from More and existing account navigation. Keep a concise account/connection summary at the top; place lengthy profile, thesis, and academic details in a separate Student profile destination.

| Group | Everyday controls | Supporting detail |
| --- | --- | --- |
| Account and access | Connect/reconnect, profile, enabled student services | Explain expired login and missing permissions in feature terms |
| Synchronization | Background checks, interval, check now | Last successful check, cooldown, approximate background scheduling |
| Notifications and reminders | Grade alerts, class reminders, reminder lead time | Actual system permission/channel state and a direct recovery action |
| Appearance and language | Follow phone / Light / Dark or existing OLED option; language | Show current selection and preserve existing preference semantics |
| Privacy and storage | Explain stored data, clear cached API data | Keep cache clearing distinct from disconnecting and deleting account data |
| Widgets | Update widgets and usage help | Explain what the action updates and report success/failure |
| About and help | Installed version, update check, release notes, support | Reuse verified release links and existing update behavior |
| Advanced | Consumer credentials, raw scopes, API Explorer | Preserve required user-supplied credentials with guided setup |

1. Replace the routine raw scope editor with labeled feature choices such as Grades and studies, Payments, Cards, and Documents. Show why access is needed and the minimum scope mapping. Keep raw scope editing behind Advanced; distinguish requested scopes from permissions confirmed for the current session.
2. Clearly state that changing feature access requires USOS reauthorization. Show a review of requested access before starting login; do not imply that toggling a preference grants server permission. Keep the existing account usable until replacement login succeeds where supported by the current session model.
3. Guide first-time setup through required consumer credentials, browser authorization, and PIN confirmation. Use persistent field labels, short help, inline validation, masked secret input, explicit paste, and recovery for cancellation or expiration. Advanced placement must not conceal credentials that are required to connect.
4. Show the current value beneath each settings row and open a focused chooser/detail screen when necessary. Apply simple preferences immediately; use an explicit Save action for grouped forms only, with success/error feedback and preserved input on failure.
5. Separate an enabled preference from effective system state: background checking enabled does not guarantee an exact schedule, and reminders enabled does not mean notifications are permitted. Show the exact next step when Android blocks delivery.
6. Keep disconnect/data removal in a clearly labeled account action area. Explain which tokens, cached data, local tasks, preferences, and scheduled jobs are affected before confirmation. Cache clearing must not erase credentials or unrelated local data.
7. Preserve settings after rotation, process recreation, and app updates. Return from Android system settings with refreshed permission state. Keep keyboard focus stable and ensure all choices work with TalkBack, large fonts, three supported languages, and both themes.
8. Validate discovery with concrete tasks: change language, enable a class reminder, resolve blocked notifications, connect cards access, and find the installed version. Each task should be reachable within three taps from Settings, excluding external authorization and system screens.

Acceptance: everyday preferences are understandable without OAuth/API knowledge; setup still supports the required credential model; settings accurately reflect saved and effective states; no preference resets or accidental account-data deletion.

## 7. P1 — Simplify every screen reached from More

Apply this pass to all ten current destinations: Cards, Documents, GPA simulator, Attendance, Tasks, Payments, News, Campus guide, Account/Settings, and API Explorer. The goal is quick comprehension and fewer competing controls. Preserve useful functionality and make secondary information discoverable.

### Shared presentation rules

1. Give each screen one clear title, a short summary only when useful, and one visually prominent primary action. Place rare actions in a consistently labeled overflow menu.
2. Use a simple vertical structure: essential status → main content → optional details. Avoid nested cards, repeated headings, decorative badges, and explanatory text that repeats a button label.
3. Keep the More hub as grouped, predictable navigation rows. Use at most one short secondary line per row; add a status only when it changes a user's next action. Do not turn the hub into a dense dashboard of counts and timestamps.
4. Use readable body text, consistent spacing, aligned labels/values, and quiet separators. Reserve emphasis and warning colors for meaningful states; pair color with plain text. Wrap long labels and avoid horizontal scrolling for routine controls.
5. Show one concise freshness/offline message per relevant section, with technical diagnostics in details. Keep loading and error feedback close to the affected content without obscuring cached information.
6. Expand details on demand and preserve expansion, filter, focus, and scroll state during refresh/navigation where appropriate. Give every hidden secondary action a recognizable entry point.

### Screen-by-screen changes

| Screen | Initial view | Secondary content and actions |
| --- | --- | --- |
| Cards | Current card status, validity, and relevant next action | Printed identifiers, order history, eligibility explanations, and API links in details; activation stays explicit and protected |
| Documents | Searchable rows with document name, date, and readable status | Open a document detail view for permitted downloads, receipts, and technical identifiers |
| GPA simulator | Current calculated average and a clear way to try a change | Calculation assumptions, excluded entries, weighting, and detailed breakdown behind an explanation; retain the unofficial-estimate label |
| Attendance | Course list with a concise attendance summary and clear recording action | Per-class history and calculation rules in course details; distinguish unknown attendance from absence |
| Tasks | Task title, due date, and completion control; one Add task action | Priority, notes, editing, sorting, and less-used filters in focused controls; preserve an easy way to find completed tasks |
| Payments | Outstanding amount by currency and nearest due date, when available | Installments, account numbers, and payment history in grouped details; explicit copy controls and missing-access state |
| News | Headline, publication date, and short preview | Full article on opening; source and validated external link in article details |
| Campus guide | Search or clear categories leading to locations and contacts | Address, opening hours, contact actions, and directions in location details; show source/freshness where known |
| Account/Settings | Connection summary and concise settings groups from section 6 | Student profile, advanced credentials/scopes, help, and destructive actions in their respective focused screens |
| API Explorer | Search, module selection, and readable method list | Technical documentation, full parameters, transport choice, and raw response in method details; keep access and write-effect warnings visible before execution |

These are presentation targets, not claims that every listed field is currently supplied by the university. Omit unavailable optional values and preserve explicit unknown/error states for essential information.

Acceptance: all ten destinations have a reviewed simplified layout; everyday tasks remain easy to discover; users can identify the screen's purpose, essential status, and main action without reading technical descriptions. Verify small screens, large fonts, TalkBack, Polish/English/Ukrainian, both themes, empty/error/offline states, and long real-shaped fixture values. Review before/after screenshots for each destination; do not accept reduced functionality or clipped content as simplification.

## 8. Verification and delivery

Implement in reviewable increments: coverage inventory and contracts/security tests → request/cache behavior → existing API support gaps → simplified More hub and all ten destination layouts → Settings organization and access setup → Explorer/export hardening → verified new student capabilities. Each increment should keep the app usable and its cache backward compatible. Apply section 7's simplicity rules to the summaries proposed in section 3; do not display every possible summary at once.

- JVM tests: extend `ApiFailureTest`, `StudentServicesTest`, `StudentToolsTest`, and `ApiCatalogTest`; add deterministic gateway/cache tests for malformed data, partial failures, Retry-After, deduplication, account changes, cancellation, and migration. Use synthetic fixtures and injected connections.
- Android regression: extend `MoreNavigationRegressionTest`, `FileAndApiUxRegressionTest`, and `ApiLanguageRegressionTest`; cover offline navigation, recovery actions, duplicate refresh taps, rotation, logout during fetch, large fonts, and language changes in isolated QA.
- API coverage tests: verify pagination, unknown/missing fields, optional scopes, unavailable methods, localized payloads, and account-safe caching for each newly integrated capability. Expand catalog checks to detect relevant metadata/schema drift.
- Settings regression: extend `AppearanceRegressionTest` and add focused settings/access tests for persisted selections, feature-to-scope mapping, canceled reauthorization, notification denial and return from system settings, cache clearing, and confirmation behavior. Exercise upgrades with existing preferences and encrypted account data using the established upgrade fixture.
- Security validation: run source/history secret scans and script unit tests; inspect release configuration, manifest, packaged resources, and final publication artifacts without exposing secret values.
- Full release gate: run `.\tools\production-check.ps1 -DeviceTests`, including optimized QA checks, plus the required CI checks. Report unavailable device/live-account checks explicitly; fixture success does not prove production USOS access.
- After a substantial implementation, follow `RELEASE.md`: increase `versionName` and `versionCode`, update What's New, merge through a PR after required CI, and publish a new tag/release from the verified commit in `a0w-svg/usoschecker-public`.
- Attach an APK signed with the existing release key, sanitized source archive, SHA-256 checksums, and changes/validation notes. Verify signature continuity and version before upload. Keep previous releases and private R8 mappings; if the existing signing key is unavailable, report the publication blocker and do not generate a replacement.

This planning-only change does not require an app version bump or public release.

## References

- Repository requirements: `AGENTS.md`, `SECURITY.md`, `RELEASE.md`.
- [Android security best practices](https://developer.android.com/privacy-and-security/security-best-practices): guidance for network trust, private storage, and safe sharing.
- [Android privacy checklist](https://developer.android.com/privacy-and-security/about): guidance for minimizing sensitive data and permissions.

These references guide the proposed review; they do not establish that the existing app has a particular vulnerability.
