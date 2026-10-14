package pl.vistula.checker.core;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CalendarTimelineTest {
    private Record event(String id, String start, String end) {
        return new Record(id, "plan", id, start.substring(11, 16) + " – " + end.substring(11, 16), "",
            start.substring(0, 10), "", start, end);
    }
    @Test public void currentLectureIncludesStartAndExcludesEndAndFutureClasses() {
        Record active = event("active", "2026-10-13 09:00:00", "2026-10-13 10:30:00");
        Instant start = LocalDateTime.of(2026,10,13,9,0).atZone(LessonTime.ZONE).toInstant();
        assertEquals(1, LessonTime.current(List.of(active), start).size());
        assertEquals(0, LessonTime.current(List.of(active), start.minusSeconds(1)).size());
        assertEquals(0, LessonTime.current(List.of(active), start.plusSeconds(90 * 60)).size());
    }
    @Test public void overnightLecturesIntersectBothDaysAndOldSnapshotsStillWork() {
        LessonTime time = LessonTime.of(event("overnight", "2026-10-13 23:00:00", "2026-10-14 01:00:00"));
        assertTrue(time.intersects(LocalDate.of(2026,10,13))); assertTrue(time.intersects(LocalDate.of(2026,10,14)));
        assertFalse(time.intersects(LocalDate.of(2026,10,15)));
        assertEquals(time.start, LessonTime.of(new Record("legacy", "plan", "", "23:00 – 01:00", "", "2026-10-13")).start);
    }
    @Test public void approximateEntriesAreNotPresentedAsCurrentLectures() {
        Record tentative = new Record("1", "plan", "A", "09:00 – 10:00\nTermin orientacyjny", "", "2026-10-13");
        assertTrue(LessonTime.current(List.of(tentative), Instant.parse("2026-10-13T07:30:00Z")).isEmpty());
    }
    @Test public void movingLectureToDifferentDayIsDetectedAndExactTimesRoundTrip() throws Exception {
        Record first = event("meeting", "2026-10-13 09:00:00", "2026-10-13 10:30:00");
        Record moved = event("meeting", "2026-10-14 09:00:00", "2026-10-14 10:30:00");
        assertFalse(first.same(moved)); assertEquals(first.endTime, Record.from(first.json()).endTime);
        assertEquals("", Record.from(new JSONObject().put("id","1").put("kind","plan").put("title","A").put("value","09:00 – 10:00").put("sourceTime","").put("date","2026-10-13")).startTime);
    }
    @Test public void loaderFetchesAllEightDistinctWeeksNotJustTheFirst() throws Exception {
        LocalDate start = LocalDate.of(2026,10,12); Set<LocalDate> starts = Collections.synchronizedSet(new HashSet<>());
        List<String> progress = new ArrayList<>();
        List<JSONArray> weeks = TimetableLoader.load(start, 56, (date, days) -> {
            assertEquals(7, days); starts.add(date);
            return new JSONArray().put(new JSONObject().put("start_time", date + " 09:00:00"));
        }, progress::add);
        assertEquals(8, weeks.size()); assertEquals(8, starts.size()); assertTrue(starts.contains(start.plusDays(49)));
        assertTrue(progress.get(progress.size()-1).contains("8/8"));
        for (int i=0; i<8; i++) assertEquals(start.plusDays(i*7L) + " 09:00:00", weeks.get(i).getJSONObject(0).getString("start_time"));
    }
    @Test public void extendedRangeLoadsSixteenWeeks() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        assertEquals(16, TimetableLoader.load(LocalDate.of(2026,10,12),112,(date,days)->{ requests.incrementAndGet(); return new JSONArray(); },s->{}).size());
        assertEquals(16, requests.get());
    }
    @Test(expected=java.io.IOException.class) public void failedLaterWeekNeverReturnsPartialData() throws Exception {
        LocalDate start = LocalDate.of(2026,10,12);
        TimetableLoader.load(start,56,(date,days)->{ if(date.equals(start.plusDays(21))) throw new java.io.IOException("week failure"); return new JSONArray(); },s->{});
    }
    @Test(expected=JSONException.class) public void repeatedFirstWeekResponseIsRejectedInsteadOfSilentlyTruncating() throws Exception {
        TimetableLoader.load(LocalDate.of(2026,10,12),56,(date,days)->new JSONArray().put(new JSONObject().put("start_time","2026-10-12 09:00:00")),s->{});
    }
}