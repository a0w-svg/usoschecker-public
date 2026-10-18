# Compact subject cards and timetable navigation — 2026-10-18

- Subject cards show the overview first. “Zobacz więcej” expands components, comments and test results; “Zobacz mniej” collapses them. Ungraded subjects can also reveal their course code and ECTS.
- Swipe left/right over the timetable to move to the next/previous day, or seven days in week mode. Search stays active. Vertical scrolling and card taps remain available. Arrow buttons provide the same navigation without gestures; in Agenda, navigation opens the adjacent day.
- The timetable header combines arrows, a tappable date and “Dzisiaj”. View selection and the weekday strip remain available. The separate heading, timezone paragraph, date-picker row, week header and large conflict panel no longer precede classes.
- “Informacje o planie” shows the saved range, last successful synchronization, timezone and conflict information. Individual conflicting lessons retain their warning badges.
- Day summaries follow lessons. Timetable cards avoid repeating times and buildings; ordinary details show the meeting time, class type, room, lecturer, building and group when supplied. Technical fields remain accessible through the existing full-data disclosure. Stored data, search and exports retain the complete records.

Regression coverage includes subject expansion/collapse, technical-data disclosure, legacy and partially structured timetable records, day/week swipes, month boundaries, preserved search, and vertical scrolling. Release validation follows `RELEASE.md` and `tools/production-check.ps1 -DeviceTests`.
