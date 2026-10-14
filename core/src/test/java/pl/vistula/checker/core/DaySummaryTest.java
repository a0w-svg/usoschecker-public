package pl.vistula.checker.core;

import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DaySummaryTest {
    private LessonTime lesson(String id, String day, String value) { return LessonTime.of(new Record(id, "plan", id, value, "", day)); }
    @Test public void overlappingMeetingsAreCountedOnceInTotalDuration() {
        DaySummary summary = DaySummary.of(List.of(lesson("a", "2026-10-13", "09:00 – 10:30"), lesson("b", "2026-10-13", "10:00 – 11:00"), lesson("c", "2026-10-13", "12:00 – 13:00")), LocalDate.of(2026,10,13));
        assertEquals(3, summary.meetings); assertEquals(180, summary.busyMinutes); assertEquals(60, summary.gapMinutes); assertEquals(1, summary.conflicts);
    }
    @Test public void touchingMeetingsHaveNoConflictOrGap() {
        DaySummary summary = DaySummary.of(List.of(lesson("a", "2026-10-13", "09:00 – 10:00"), lesson("b", "2026-10-13", "10:00 – 11:00")), LocalDate.of(2026,10,13));
        assertEquals(120, summary.busyMinutes); assertEquals(0, summary.conflicts); assertEquals(0, summary.gapMinutes);
    }
    @Test public void overnightMeetingsAreClippedToEachDay() {
        List<LessonTime> rows = List.of(lesson("a", "2026-10-13", "23:00 – 01:00"));
        assertEquals(60, DaySummary.of(rows, LocalDate.of(2026,10,13)).busyMinutes);
        assertEquals(60, DaySummary.of(rows, LocalDate.of(2026,10,14)).busyMinutes);
        assertEquals(0, DaySummary.of(rows, LocalDate.of(2026,10,15)).meetings);
    }
    @Test public void tentativeAndMultipleOverlapsRemainExplicit() {
        DaySummary summary = DaySummary.of(List.of(lesson("a", "2026-10-13", "09:00 – 12:00"), lesson("b", "2026-10-13", "10:00 – 11:00\nTermin orientacyjny"), lesson("c", "2026-10-13", "10:30 – 11:30")), LocalDate.of(2026,10,13));
        assertEquals(3, summary.conflicts); assertEquals(1, summary.tentative); assertEquals(180, summary.busyMinutes);
    }
}
