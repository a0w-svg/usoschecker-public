# Validation — 2026-10-25

## Prototype completion and lint cleanup — 1.5.1 (17)

- Regression coverage: 186 core, 23 shared desktop and 67 Android JVM tests; 165 full debug QA tests and 6 optimized QA tests. The separate QA AVD uses synthetic data.
- Navigation checks cover all five destinations, rapid consecutive swipes, reselecting More, translated label bounds, active-pill centring, parent clipping and clearance above the system navigation area.
- Additional layout runs execute the three design regressions at 200% system text and in landscape; original emulator settings are restored afterward. Grade checks retain full course names and every displayed assessment value.
- Release and debug lint have zero findings; warnings are treated as errors. Notification plurals and localized widget labels have format/parity checks. Fifteen Python script tests cover source security, localization, the API catalog and audit handling.
- Release verification includes APK/AAB builds, the offline-language configuration guard, source/history secret scans, Linux and Windows package launch checks in required PR CI, signer continuity, version/package/alignment checks and decompressed artifact scans.
- The final tested commit/tree, report counts and artifact SHA-256 values are recorded in the published release's `release-info.json`. Publication follows the required PR checks.
- Limits: fixture-based account actions on Android 16; Android 12, macOS and a live student account are not manually covered. AGP 9.4.1 still emits its own Gradle deprecation notice; project Kotlin compilation and lint are clean.

---

# Validation — 2026-10-23

## Design, first-run guide and bilingual documentation — 1.5.0 (16)

- Release gate: `tools/production-check.ps1 -DeviceTests` passed on a separate Android 16 AVD (`CodexDesignQA`) with synthetic QA data.
- JVM: **186 core + 20 shared desktop + 67 Android = 273 passed**, no failures or skips.
- Android: **162 full debug QA tests + 6 optimized QA tests passed**, no failures or skips. First-run tests cover skip/relaunch, demo isolation, saved-key upgrade bypass, masked credentials and activity recreation. Dashboard tests cover concurrent classes, tentative times and shortcuts.
- `lintRelease`: **0 errors**; existing warnings remain. Release configuration, APK/AAB builds and source/history secret scans passed. **14 Python security/localization/catalog/audit tests passed**.
- UI checks: light/OLED Android previews, 200% system text and landscape; desktop light/dark smoke checks and synthetic previews in GitHub CI. The wide dashboard uses three columns. `docs/screenshots` contains only synthetic demo/first-run views.
- Readability: larger body/caption text, stronger muted-text contrast and full-size navigation labels; at large system font sizes the tab bar scrolls horizontally.
- Dependencies: direct stable versions verified; Gradle 9.8.2 and Compose 1.12.2; seven build-classpath advisories fixed, OSV 363 selected Maven versions with zero affected components. Full inventory: [DEPENDENCIES.md](DEPENDENCIES.md).
- README is available in Polish and English, with mobile/desktop galleries and API setup instructions. Design co-author: [Kaydjen](https://github.com/Kaydjen).
- The original AVD was short of storage. Tests used a new, separate profile without deleting original emulator data. Device animations were disabled for deterministic Espresso menu interaction; no production animation behavior was changed.
- Public release requires the protected PR checks, the same signing certificate as earlier releases, version/package/alignment checks, decompressed artifact scans and SHA-256 files. The checked commit/tree, exact tests and artifact hashes are recorded in the release's `release-info.json`.
- Limits: tests use fixtures rather than a live student account; Android 12 and macOS were not manually exercised. Default CodeQL setup remains enabled. Additional manual-build analysis retains SARIF and checks findings without duplicate uploads.

---

# Validation — 2026-10-15

## Production release preparation and automated QA verification — 2026-10-15

- Complete release quality gate executed via `.\tools\production-check.ps1 -DeviceTests`:
  - `:core:test`: 44 JVM tests passed (0 failures, 0 skipped).
  - `:app:testDebugUnitTest`: 7 JVM tests passed (0 failures, 0 skipped).
  - Total JVM tests: **51 passed**, 0 failures.
  - `:app:lintRelease`: **0 errors** (all checks clean).
  - `:app:verifyReleaseConfiguration`: verified release build type is non-debuggable, `minifyEnabled` and `shrinkResources` are true, `USOS_KEY` and `USOS_SECRET` BuildConfig fields are empty, and release does not use debug signing key.
  - `:app:assembleRelease` & `:app:bundleRelease`: generated release APK and AAB.
  - `:app:connectedDebugAndroidTest` (`-PisolatedTests`): all **42 device tests** passed on `Medium_Phone_API_36.0` (Android 16, API 36).
  - `:app:connectedOptimizedQaAndroidTest` (`-PoptimizedQa`): all **4 device tests** in `ProductionReadinessTest` passed against the R8-optimized, non-debuggable build (`testInstallationContainsNoDeveloperCredentials`, `platformBackReturnsFromGradesToHomeOnAndroid16`, `platformBackClosesDrawerWithoutLeavingHome`, `manifestPreservesTransportAndStorageSafety`).
- PowerShell script encoding fixed for seamless execution under Windows PowerShell 5.1 and modern PowerShell.
- `package_android_studio.py` updated to package `RELEASE.md`, `tools/`, Proguard/R8 rules, and copy release APK, AAB, and R8 mapping to `dist/`.
- Release deliverables:
  - `dist/vistula-checker-release-unsigned.apk` (2 786 736 B)
    SHA-256: `d15cbdf7b6b1668d90054b3a21311767f24adc979b44be4e12ad0ef6ae21814b`
  - `dist/vistula-checker-release.aab` (3 395 199 B)
    SHA-256: `7dfeac456b86780a5eb8e298bc0d0b0bc58473d079cb5fc1c7c9815881741780`
  - `dist/mapping.txt` (12 972 045 B)
    SHA-256: `cd09db64c23c11b5743f11766af7500c8459b401ccd0a52e619c808eec42bf7b`
  - `dist/VistulaChecker-AndroidStudio.zip` (1 058 980 B)
    SHA-256: `b85f14d8b0338c5e0f04229cfe451169ed716175f45e4397f6680df024807d59`



## Task tools, class actions and nullable sync responses — 2026-10-13

- Task priorities, notes, note search, persistent sorting, completion progress and
  deletion undo. Legacy tasks default to normal priority. Undo preserves existing
  entries and checks both account identity and the 200-task limit.
- Scrollable task editor and filter rows with 48 dp minimum button height;
  class details offer task creation and a system share chooser. Dashboard opens
  the actual date of the next lesson.
- Explicit JSON null grade collections and optional date/comment fields are handled
  without rejecting valid responses. Missing fields and wrong types still fail.
  Android JSONObject null-string behavior has its own regression test.
- Sync failures include their stage; HTTP failures also identify the method.
  Wrapped errors retain authorization/retry classification. Error descriptions
  can be copied without exposing response bodies or tokens.
- All 51 JVM tests passed (44 core + 7 app transport/snapshot tests).
- All 22 Android instrumentation tests passed on Android 16 with `-PisolatedTests`.
  The initial run encountered an existing account and failed demo fixture assumptions;
  a separate `.qa` installation was then used. Non-QA tests now skip populated stores.
- Final task flow was additionally rerun after UI/preference changes.
- APK and Android lint built successfully; no lint errors.
- Deliverable: dist/usoschecker-improved-debug.apk (standard package pl.vistula.checker).
- Visually inspected task list/editor previews in build/task-priority-preview.png
  and build/task-editor-preview.png; dialog surface colors now follow the green palette.
- Private-account sync was not exercised. Null parsing is a concrete compatibility
  fix for the reported incomplete-response message, but its exact cause on the
  user's account requires a refresh using the updated app and its stage diagnostics.

## API handling and catalog UX — 2026-10-13

- JSON/form OAuth error classification, exact expired-token detection, nonretryable
  authorization errors, HTTP 408/429/5xx retry classification and Retry-After parsing.
- Sync preserves saved data and records server cooldowns; account recovery guidance
  and last-attempt timestamps are visible on Start.
- Explorer honors ignored-token metadata, rejects injected OAuth parameters and
  unsupported multipart/method requests, normalizes MIME types and clears stale exports.
- Persistent offline API favorites/filter, inline required-field errors and retained
  parameters with an explicit response-panel error after a failed request.
- All 40 JVM tests passed: 36 core plus 4 transport tests using simulated HTTP connections.
  Transport tests cover form POST, ignored tokens, retry headers, redacted failures,
  local validation and connection cleanup. No private API requests were made.
- Debug APK, Android test APK and lint built successfully. Android lint has no errors.
- Added favorites instrumentation coverage; device tests were not run because adb
  reported no connected device. UI rendering and real account OAuth remain unverified.
- APK: app/build/outputs/apk/debug/app-debug.apk.

## Calendar timeline and complete multi-week loading — 2026-10-13

- Day time grid with week/day selection, full-range agenda, overlap lanes, event details,
  current-time marker, jump-to-now control and current-lecture cards with remaining minutes.
- Exact meeting start/end timestamps are stored; legacy snapshots remain readable.
- Default range: eight weeks from the current Monday; load-more extends to twelve/sixteen weeks.
- All seven-day pages are fetched, with at most three concurrent requests and week progress.
  Unexpected repeated/out-of-range pages fail validation. Any failed page retains the previous snapshot.
- Routine cache reads are silent; current indicators update on minute changes without resetting scroll.
- Debug build and all 32 core tests passed. Tests include all 8/16 pages, later-page failure,
  repeated first-week rejection, overnight/current-time boundaries and moved meeting dates.
- The clean Android regression run passed all 18 cases on a fresh CodexCalendar AVD.
  Layout geometry and affected calendar/search flows were additionally tested after visual sizing repair.
- Android lint: 0 errors, 12 advisory warnings.
- Final artifact: dist/usoschecker-calendar-debug.apk.
- Screenshots: build/calendar-header-preview.png and build/calendar-timeline-preview.png.
- Private account pagination was not verified against a real USOS account. The API can return
  empty future weeks; loaded-range labels indicate requested/saved coverage, not a guarantee of classes.

## Lecturer names — 2026-10-13

- Timetable sync collects unique lecturer IDs from all four weeks and resolves first_name
  and last_name through services/users/users in batches of at most 50.
- Names appear in the timetable, upcoming-class dashboard, search and calendar description.
- Unavailable/null user records retain an explicit ID fallback; incomplete responses fail
  the sync so the previous snapshot is retained.
- Debug APK and instrumentation APK built; all 24 JVM tests passed, including 3 new
  tests for name parsing, lecturer-ID deduplication, null values and order-independent display.
- Android lint: 0 errors, 11 advisory warnings.
- Added Android coverage for batch resolution, incomplete directory response, dashboard/search,
  and lecturer names in calendar exports. These updated Android tests compiled but were not run
  in this change: the existing AVD was already in use and disallowed a simultaneous read-only
  instance. The existing emulator session was left unchanged.
- Actual lecturer lookup against a private USOS account requires a connected account and was
  not exercised here. Existing snapshots gain names after the next successful refresh.

## Student tools expansion — 2026-10-13

- Semester filtering and summaries of visible numeric grades (unweighted, non-official).
- Visible timetable export to .ics via the system document picker, with stable event IDs,
  escaped/folded UTF-8 text, Warsaw-to-UTC conversion, and saved pending export during recreation.
- Task editing, text search, open/completed/today/overdue filters, and dashboard deadline preview.
- Debug APK and instrumentation APK built; all 21 core tests passed (14 existing + 7 student tools).
- All 14 emulator tests passed on Android 16, including semester summaries, task editing/filtering,
  dashboard deadlines, and a real calendar file write with an intercepted document-picker result.
- Android lint: 0 errors, 11 advisory warnings about target SDK and dependency versions.
- Tests now start with a fresh activity task to avoid carrying saved demo navigation between cases.
- Calendar export is a static snapshot; importing/updating it in third-party calendar apps was not tested.
- Private account/OAuth operations still require validation with real user credentials.

## Material redesign — verified on Android emulator

- Material 3 toolbar, icon bottom navigation (Start / Oceny / Plan / Zmiany / Więcej),
  side drawer, outlined text inputs, Material buttons/dialogs and settings switch.
- Separate grades and timetable destinations; dashboard statistics and upcoming class.
- Personal tasks: due date, completion/reopening, deletion, encrypted local storage;
  demo tasks remain separate from real data.
- Debug build and all 14 core tests passed.
- All 11 Android instrumentation tests passed on Medium_Phone_API_36.0 (Android 16),
  including actual bottom-navigation clicks, catalog pagination, search/date filters,
  task creation/completion, demo isolation, encryption, failed-sync preservation,
  bundled metadata, and anonymous public API request.
- Android lint: 0 errors, 10 warnings (target SDK and dependency update advisories).
- Visually inspected the rendered dashboard, timetable and drawer on the emulator.
  Screenshots: build/material-home.png, build/material-plan.png, build/material-drawer.png.
- The earlier sections below are historical; the current device tests were executed,
  unlike the original build's compile-only verification.
- Private OAuth/account requests and real notification delivery still need account validation.

## Current workspace improvements — 2026-10-13

- API catalog now exposes all results with "show more" and preserves search and filters.
- Student data and history have case-insensitive search with result counts and empty states.
- Timetable supports today / next seven days / full snapshot; dates use Europe/Warsaw.
- Grade cards show semester IDs when available; data screen shows last successful fetch time.
- Routine reloads skip active text input, avoid rebuilding unchanged data, and retain scroll position.
- Compatibility overloads preserve the extended record/parser APIs; malformed grades and duplicate meetings are rejected.
- Verified with the current Gradle wrapper: core tests (14, zero failures), debug APK,
  instrumentation APK compilation, and Android lint all passed.
- Added instrumentation coverage for catalog pagination, retained search, student data search,
  timetable ranges, and history before/after search. Device tests were not executed:
  adb reports no connected device or emulator.
- Earlier validation details below describe the original build and its toolchain/hash,
  not the APK rebuilt from the current workspace.

## Passed

- Gradle 8.11.1 / JDK 17 build: `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`.
- 14 JVM tests, 0 failures, 0 skipped: OAuth signing/encoding, response parsing,
  initial baseline, grade and timetable differences, rolling schedule windows, serialization,
  and rejection of incomplete or duplicated responses.
- Android Lint: **0 errors**, 6 advisory warnings about newer dependency/Gradle versions.
  Versions are deliberately pinned to the documented AGP 8.10.1 toolchain.
- Debug application APK and instrumentation-test APK both built.
- `apksigner verify`: APK signature scheme v2 verified.
- Offline catalog: all **571/571** indexed methods have matching JSON descriptors,
  argument lists and authorization requirements.
- Live anonymous request to `services/apisrv/installation` confirmed that the configured
  server belongs to Akademia Finansów i Biznesu Vistula z siedzibą w Warszawie.

## Not verified

- Device/emulator tests were compiled but not executed. No KVM device is available on this
  host. Android Emulator 37.2.12 with the Android 35 x86_64 system image crashed before
  Android booted, both with SwiftShader and with Vulkan disabled/GPU off. This is an emulator
  host failure, not a successful application runtime test.
- The Android Studio GUI itself was not launched here. The project has standard Android
  Gradle modules, the complete wrapper and a shared Android App run configuration.
- OAuth login and private grade/timetable requests require a registered key and user consent.
  No real university account or credentials were used, and no key was registered.
- Actual background timing, notification delivery, file upload/download and administrative
  permissions need device/account validation. No write operation was sent to the university.

Run the supplied `AppSmokeTest` on a phone or working emulator from Android Studio, or use:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The six instrumentation tests cover demo navigation, searching and opening API documentation,
all bundled descriptors, encrypted storage, preservation of data after failed sync, and an
anonymous live installation request. Compilation of those tests does not establish their runtime result.

Debug APK SHA-256:

```text
a9c303a0a73616d7a2772c7a5e6f22ff6aca59c3e3e43f28b1720453d355f423
```
