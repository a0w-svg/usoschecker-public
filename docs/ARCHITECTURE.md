# Architecture guide

A short map for new contributors. Release and security rules live in [RELEASE.md](../RELEASE.md) and [SECURITY.md](../SECURITY.md).

## Modules

| Module | Role | Depends on |
|---|---|---|
| `core` | Gradle module that runs the pure-JVM unit tests for the logic in `shared/src/jvmCommonMain/.../core` (parsers, diffing, GPA, reminders, input parsing). | `shared` |
| `shared` | Kotlin Multiplatform code used by Android and desktop. `jvmCommonMain` holds the USOS client, repository, services and the Compose UI in `ui/compose`. | none |
| `app` | Android app: `MainActivity`, View-based screen controllers, workers, widgets, notifications. | `shared` |
| `desktopApp` | Desktop launcher for the shared Compose UI. | `shared` |

Rule of thumb: put logic that needs no Android classes in `shared/.../core` so it can be tested with plain JUnit.

## Android app structure

`MainActivity` is the single activity. It owns the data (`state`, `repository`), runs background work through `task(...)`, and renders one screen at a time into `content` by switching on the current tab (see `Tabs`).

Screen flows that need dialogs, toasts and background work are kept out of it. `PlatonFlow` (sign-in, sync, courses, messages, sign-out) and `TasksFlow` (add, edit, complete, delete with undo; demo and real tasks) talk to the Activity only through the small `FlowHost` interface. `StudentPrefs` is the typed wrapper for the `student_tools` SharedPreferences file; add new keys to `StudentPrefs.Keys` instead of calling `getSharedPreferences` directly.

- **Screen controllers** (`app/.../ui/*ScreenController.kt`) are stateless objects with a `render(...)` function. They receive everything they need as parameters and report user actions through callbacks.
- **UI state holders** keep what a screen remembers between renders and across configuration changes:
  `GradesUiState`, `TasksUiState`, `ScheduleUiState`, `HistoryUiState`, `ApiExplorerState`.
  Each has `save(Bundle)` / `restore(Bundle)` that use the same keys as before, so saved state stays compatible.
- **`Tabs`** names the screen ids. Values are persisted in saved state and external intents, so never renumber them; only append.
- **`Diagnostics.swallowed(tag, e)`** is the one way to deliberately ignore an exception. It logs the exception type only, never the message.

### Adding a screen

1. Add a constant to `Tabs`.
2. Create `XScreenController.render(...)` in `app/.../ui/`.
3. Add a branch in `MainActivity.render()` and, if it has filters or a search box, a `XUiState` class with `save` / `restore`.
4. Add strings to `values/`, `values-en/` and `values-uk/`.

## Platon integration

Platon has no public API, so the app reads its HTML with the session of an in-app WebView.

- `PlatonLoginActivity` signs the student in (CAS/Microsoft or local account) and closes itself when a signed-in page appears. `PlatonSession` renews an expired session silently in a hidden WebView through the Microsoft session.
- `PlatonClient` fetches pages (and POSTs list forms) with the WebView cookie, only over HTTPS to `platon.vistula.edu.pl`; off-site redirects count as an expired session.
- `PlatonCrawler`, `PlatonTaskParser` and `PlatonSync` (`shared/.../core`) turn pages into tasks, courses and messages and merge them into the task list without losing the student's edits. They are pure JVM and covered by `PlatonSyncTest`.
- `PlatonFlow` (app) owns the screens: message list and detail (an unread message asks first because Platon marks it read when opened), course list and detail, settings and the semester picker. Chosen semesters are stored in `StudentPrefs.platonSemesters`; the crawler (`PlatonCrawler.crawlAll(..., semesters)`) reads only those and reports every semester it saw.
- Refresh speed: `UsosClient` runs independent endpoints (ECTS, accounts, payments, theses, tests, news, timetable) ahead on a small executor behind a 4-request gate, with a circuit breaker that stops new requests after an authorization, rate-limit or network failure (`withParallelism(1)` restores strict sequence). `PlatonCrawler` reads the inbox, course pages and assignment pages through `Parallel.map` (5 at a time) and skips the detail page of a submitted assignment whose deadline is in `PlatonState.knownDeadlines`.
- `PlatonRunner` (app) applies a crawl to the state, writes `platonCourses`, `platonMessages` and Changes-feed entries (kind `platon`), and sends notifications. `SyncWorker` calls it in the background; `MainActivity` calls it for manual and automatic syncs.

## Compose UI (shared)

Files in `shared/.../ui/compose` are split by section. Text goes through `val tr = Translator(language)` and is called as `tr("pl", "en", "uk")`. Larger screens live in their own file (for example `LecturerWriteCards.kt`, `StaffDirectorySection.kt`, `AcademicExtrasSection.kt`).

## Stored state and schema version

Account data is one JSON object in the encrypted vault. Shared key names are in `StateKeys`; the shape version is
`StateSchema.CURRENT`. When you change the shape of existing data, raise `CURRENT` and add an idempotent step to
`StateSchema.migrate` (with a test). Reads shown to the UI are migrated in memory, and every write persists the
migrated state. Pure state logic lives in `shared/.../core` (for example `PlatonState`, `PlatonSync`, `TaskTools`) so it
runs in plain JUnit and on desktop. The Android vault caches the decrypted state, validated by the file's stamp
(`StateCache`), so repeated reads do not decrypt again.

Versions used by more than one module are in `gradle/libs.versions.toml`.

## Failure handling

- `VaultUnreadableException` (a subtype of `IOException`) means the stored state exists but cannot be decrypted or
  parsed. It is never retried and never replaced silently; `MainActivity.offerVaultReset` and the desktop dialog in
  `VistulaApp` clear the store only after a confirmation.
- `SyncWorker` runs the USOS sync first and then every independent step (task deadlines, Platon, class reminders,
  widgets) even when the sync failed. `SyncWorker.shouldRetry` decides which failures are worth a retry.
- An OAuth return link waits in the intent until `stateLoaded` is true and no task is running
  (`MainActivity.handleOAuthCallback`).

## Requests and field selectors

USOS answers a request that names an unknown result field with HTTP 400, and optional sections swallow that
error and stay empty. `scripts/check_field_selectors.py` compares every `"fields" to "…"` selector next to a
`services/…` call with the result fields in the bundled API metadata (`app/src/main/assets/api`); list methods are
checked through the method that defines their elements. It runs with the script tests. When adding a request,
write the parser test from the declared fields (see `RealShapeServicesTest`), not from guessed ones.

## Data flow

1. `SyncWorker` or a user refresh calls `UsosClient` (OAuth1 over HTTPS).
2. `UsosParser` turns responses into `Snapshot` / `Record` objects; `Change` diffs the old and new snapshot.
3. `Repository` persists state as JSON; screens read it through `MainActivity.display()` (real or demo data).

## Tests

| Command | Covers |
|---|---|
| `./gradlew :core:test :shared:desktopTest :app:testDebugUnitTest` | pure logic, UI state holders, formatters, HTTP client with fakes, repository concurrency and edit-session rules |
| `./gradlew :app:connectedDebugAndroidTest -PisolatedTests` | device UI regression tests; needs an emulator and installs the separate QA app |

Always pass `-PisolatedTests` for device tests: they refuse to run against the real app's data.

## Security notes for contributors

- The launcher activity is exported. Treat every intent extra as untrusted; use `Tabs.fromExternal` for tab ids.
- Never log exception messages, URLs or response bodies (`Diagnostics` follows this).
- Never write tokens, keys or the signing key into the repository or release artifacts.

Platon parsers are tested against anonymized page samples in `core/src/test/resources/platon`. When Platon changes its markup, replace the sample with a new anonymized capture and `PlatonFixturesTest` shows what broke.
