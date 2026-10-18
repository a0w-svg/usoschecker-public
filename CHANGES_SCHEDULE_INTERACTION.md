# Schedule interaction fixes — 2026-10-18

- Direction is resolved by the timetable's scroll container before pull-to-refresh or vertical scrolling can intercept a horizontal gesture. Both swipe directions work across the viewport, including the date row and empty space. A deliberate vertical pull still refreshes; ordinary scrolling and taps retain their behavior.
- The plan toolbar is 48 dp, without an account subtitle or extra demo paragraph. Demo mode remains labeled in its title. The date has larger 20 sp text. Date-row controls have at least 64 dp height; the arrow and menu targets are 56 dp wide with larger symbols.
- Room numbers appear in a separate bold 20 sp label under the subject name, including older stored timetables. They are not repeated in the smaller secondary description.
- Confirmed ongoing classes have a separate “TRWAJĄ TERAZ” badge, remaining minutes, a contrasting border and an accessibility state. Tentative meetings are not marked as currently running. The existing minute refresh updates these states.
- Default navigation into the plan selects today when a matching ongoing class exists, then scrolls to its card after layout. Auto-scroll runs only on entry, so ordinary renders and manual navigation do not pull the user back. Search remains respected. Explicit date links from the dashboard still open their requested day.
- Regression coverage includes both diagonal swipe directions without refresh, deliberate vertical refresh, large date controls, ongoing-class visibility after an earlier list of classes, preservation of manual scrolling, and explicit date navigation.

Release: 1.0.6, versionCode 7. Verification follows `RELEASE.md` and `tools/production-check.ps1 -DeviceTests`.
