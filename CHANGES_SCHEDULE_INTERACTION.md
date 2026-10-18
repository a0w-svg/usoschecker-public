# Schedule interaction fixes — 2026-10-18

- Direction is resolved by the timetable's scroll container before pull-to-refresh or vertical scrolling can intercept a horizontal gesture. Both swipe directions work across the viewport, including the date row and empty space. A deliberate vertical pull still refreshes; ordinary scrolling and taps retain their behavior.
- The plan toolbar is 48 dp, without an account subtitle or extra demo paragraph. Demo mode remains labeled in its title. The date has larger 20 sp text. Date-row controls have at least 64 dp height; the arrow and menu targets are 56 dp wide with larger symbols.
- Cards use the existing surface, 20 dp corner radius and spacing. Course names are 20 sp, room labels 18 sp bold, secondary details 14 sp and status labels 13 sp. Room numbers remain separate and are not repeated in the smaller description. Arrow/menu icons share 28 dp vector artwork instead of different text glyphs.
- Confirmed ongoing classes have a small “TRWAJĄ TERAZ” badge, remaining minutes, an accent outline on the same card surface and an accessibility state. Tentative meetings are not marked as currently running. The existing minute refresh updates these states.
- Default navigation into the plan selects today when a matching ongoing class exists, then scrolls to its card after layout. Auto-scroll runs only on entry, so ordinary renders and manual navigation do not pull the user back. Search remains respected. Explicit date links from the dashboard still open their requested day.
- Regression coverage includes both diagonal swipe directions without refresh, deliberate vertical refresh, large date controls, ongoing-class visibility after an earlier list of classes, preservation of manual scrolling, and explicit date navigation.

Release: 1.0.6, versionCode 7. Verification follows `RELEASE.md` and `tools/production-check.ps1 -DeviceTests`.
