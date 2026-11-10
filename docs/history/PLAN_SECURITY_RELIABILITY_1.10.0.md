# Security, reliability and unfinished features — plan for 1.10.0

Date: 2026-11-10. Based on a read of the code at `6c2d487` (1.9.0). Each item names the defect that was
found, not a general wish. Items that could not be verified without a live account are listed at the end
and are deliberately left out.

## 1. Security

| # | Finding | Change |
|---|---|---|
| S1 | `KeyRegistration.revoke` puts the Consumer Secret in the URL query of a GET. URLs end up in server and proxy logs. | Send the pair in a POST form body (the endpoint accepts it; checked with a non-existent pair). |
| S2 | `KeyRegistration` follows redirects, may use the HTTP cache and reads the answer without a size limit. Every other transport in the app refuses redirects and caps the body. | Refuse redirects, disable caching, cap the answer at 64 KB. |
| S3 | Key creation is not serialized between `Vault` instances, so two first writes can each generate a key and one state becomes unreadable. | Create the key under a lock. (Deleting the alias on disconnect was tried and dropped: one alias serves every `Vault` directory, so clearing one store would break another.) |
| S4 | The two Platon WebViews leave geolocation at its default and do not handle a renderer crash (the app process is killed with it). | Turn geolocation off; handle `onRenderProcessGone` (close the login / give up the silent renewal). |

## 2. Reliability

| # | Finding | Change |
|---|---|---|
| R1 | The OAuth return link (`usoschecker://oauth`) is dropped when the process was killed while the browser was open: `onResume` handles it before the saved state is read, sees no pending request token, shows "ignored" and clears the link. The same happens when the link arrives during a running task. | Keep the link until the state is loaded and no task is running, then handle it once. |
| R2 | When the encrypted state cannot be decrypted (lost Keystore key, damaged file) every action fails with the same message, including "connect", so the only way out is clearing app data in system settings. | Typed `VaultUnreadableException`; the app offers a confirmed "reset local data" that runs the normal disconnect cleanup. |
| R3 | `ReleaseUpdates.check` does `release!!` when the 12-hour throttle applies but the cached release cannot be parsed: NullPointerException in the worker and the manual check. | Fetch again when there is no usable cache. |
| R4 | `SyncWorker` skips Platon, task-deadline reminders, class reminders and widgets whenever the USOS sync throws, and retries failures that cannot succeed (cooldown, missing scope). | Run the independent steps regardless; retry only network and retryable API failures. |
| R5 | `DesktopSyncScheduler.scheduleNextReminder` reads the vault unguarded on the caller's thread. | Treat an unreadable vault as "no reminder". |

| R6 | Found while checking the features reported as "not working at all": the Java runtime bundled with the desktop app does not trust "Certum Trusted Root CA", the root of every `*.vistula.edu.pl` certificate, so each request to USOS, the news site and key registration failed the TLS handshake on Windows and Linux. Confirmed live: sign-in start fails without the root and succeeds with it. | Bundle that one public root with a pinned fingerprint: added to the default anchors on desktop, and scoped to `vistula.edu.pl` on Android for system versions that may not ship it. |

| R7 | Eleven requests behind the More screens asked for result fields the methods do not have (for example `address` of a dormitory, `number_of_answers` of a survey, `host_institution` of a mobility). USOS answers such a request with HTTP 400; the error was swallowed and the section stayed empty for everyone. Two were confirmed live, the rest against the bundled API metadata. Two more answers are dictionaries that were parsed as lists. | Correct the selectors and parsers (lecturer surveys and exam reports, primary and custom groups, mobility, placement tests, questionnaires, dormitories, allocation periods, statements, course unit). `scripts/check_field_selectors.py` now compares every selector with the metadata and runs with the script tests in CI. |

## 3. Unfinished features

| # | Gap | Change |
|---|---|---|
| F1 | Android registers the USOSapps key from an e-mail address (1.6.0). Desktop still asks the student to copy Consumer Key and Secret by hand. | Same one-field flow in the desktop Account section; manual entry stays available. |
| F2 | Android offers to revoke the API key when disconnecting; desktop does not. | Optional "also revoke the API key" in the desktop disconnect dialog. |
| F3 | Desktop change notifications are Polish only. | Localize title and body (pl/en/uk) through the repository's language. |
| F4 | README still describes copying keys by hand as the first-run flow, the English README has no Platon section, RELEASE.md quotes version 1.6.0. | Update the three documents and SECURITY.md for S1–S4/R2. |

## 4. Verification and release

- New JVM tests for S1/S2, R3, R4 (decision logic), F3 and the vault failure type; existing suites stay green.
- `:core:test :shared:desktopTest :desktopApp:compileKotlin :app:testDebugUnitTest :app:lintRelease
  :app:verifyReleaseConfiguration :app:assembleRelease`, source and history secret scans, script tests.
- Version 1.10.0 (versionCode 26), What's New in three languages, PR with required CI, tag and release
  signed with the existing key.

## 5. Looked at and left alone

- Restricting POST navigations in the Platon WebView to the host allowlist: cannot be tested without a
  live Microsoft sign-in, and a wrong guess breaks login for everyone.
- Experimental lecturer writes stay experimental: they need a lecturer account to verify.
- Platon on desktop: needs an embedded browser the desktop app does not have.
