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

## Compose UI (shared)

Files in `shared/.../ui/compose` are split by section. Text goes through `val tr = Translator(language)` and is called as `tr("pl", "en", "uk")`. Larger screens live in their own file (for example `LecturerWriteCards.kt`, `StaffDirectorySection.kt`, `AcademicExtrasSection.kt`).

## Data flow

1. `SyncWorker` or a user refresh calls `UsosClient` (OAuth1 over HTTPS).
2. `UsosParser` turns responses into `Snapshot` / `Record` objects; `Change` diffs the old and new snapshot.
3. `Repository` persists state as JSON; screens read it through `MainActivity.display()` (real or demo data).

## Tests

| Command | Covers |
|---|---|
| `./gradlew :core:test :shared:desktopTest :app:testDebugUnitTest` | pure logic, UI state holders, formatters, HTTP client with fakes |
| `./gradlew :app:connectedDebugAndroidTest -PisolatedTests` | device UI regression tests; needs an emulator and installs the separate QA app |

Always pass `-PisolatedTests` for device tests: they refuse to run against the real app's data.

## Security notes for contributors

- The launcher activity is exported. Treat every intent extra as untrusted; use `Tabs.fromExternal` for tab ids.
- Never log exception messages, URLs or response bodies (`Diagnostics` follows this).
- Never write tokens, keys or the signing key into the repository or release artifacts.
