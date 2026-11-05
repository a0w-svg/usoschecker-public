# Screen improvements

- Grades search updates immediately, includes course codes, and supports later exam attempts. Different course IDs with the same name remain separate. Reset clears sorting as well as filters. Unknown exam sessions are labeled explicitly. Unrelated test results no longer appear under a search or semester/type filter.
- Timetable offers day/week/agenda ranges, previous/next week navigation, a day picker and persistent search. The daily summary counts overlapping time once and includes breaks. Ongoing confirmed classes are highlighted. Conflict badges use record IDs so legacy records cannot mark unrelated lessons. Overnight classes show their end-day status.
- Calendar export saves the visible, searched range through Android's document picker. It no longer depends on an unconfigured file provider. Sharing includes dates for multi-day plans. Empty results disable export and sharing.
- Change history searches live, accepts Polish text without accents, groups results by date, and filters added/modified/deleted entries. Before and after values have separate panels. Details and copied text include changed metadata and dates.
- Dashboard prioritizes class/task information, uses larger shortcuts, and includes a seven-day workload preview with links to each day and deadline filters. Tasks support a seven-day filter and live accent-insensitive search. Invalid task dates do not crash the task list or dashboard.

Data is based on the saved USOS snapshot. Summary durations include tentative meetings and count overlapping intervals once; they do not measure actual attendance. No new network scopes or dependencies are required.

Verification includes core/app unit tests, debug build, lint, and isolated emulator regression tests in `ScreenRegressionTest` (search, navigation, export picker and task date boundaries). Tests use demo data; live USOS synchronization is not part of those device checks.
