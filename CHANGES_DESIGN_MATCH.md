# Prototype design completion — 1.5.1

The Android and desktop interfaces follow the structure and tokens of
[Kaydjen's prototype](https://usoscheckerdesign.vercel.app/), checked against the
provided HTML source as well as the rendered reference.

- Today: a centred current/next class, quiet grouped rows and circular week dates.
- Plan: a week selector, compact lesson rows and details; existing search, modes,
  lecturer matching and export remain available.
- Grades: current-semester selection, aligned assessment columns, full course
  names and all attempts. Narrow/large-text Android layouts put assessments below
  the course title instead of clipping or requiring sideways reading.
- Changes and More: grouped trays and consistent typography, with all existing
  tools retained.
- Navigation: text-only pills in the reference order, measured translated labels,
  correctly centred active text, selected-state accessibility and safe-area spacing.

Android's pending document data now lives in a ViewModel; upload/export/calendar
results use registered Activity Result contracts. Test fixtures use ActivityScenario.
Gradle configuration uses the current DSL without changing the pinned library versions.

Verification includes the full production gate, secret scans, device regressions,
large-text/layout checks and protected PR CI. Exact results and remaining platform
limitations are recorded in VALIDATION.md and release-info.json before publication.
