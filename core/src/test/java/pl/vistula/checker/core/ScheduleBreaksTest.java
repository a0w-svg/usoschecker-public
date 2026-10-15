package pl.vistula.checker.core;

import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScheduleBreaksTest {
    private final LocalDate day = LocalDate.of(2026, 10, 15);
    private LessonTime lesson(String id, String date, String time) {
        return LessonTime.of(new Record(id, "plan", id, time, "", date));
    }
    private LessonTime lesson(String id, String time) { return lesson(id, day.toString(), time); }
    @Test public void calculatesExactBreaksInUnsortedPlan() {
        List<ScheduleBreaks.Break> gaps = ScheduleBreaks.inDay(List.of(lesson("b", "11:15 – 12:00"), lesson("a", "09:00 – 10:00")), day);
        assertEquals(1, gaps.size()); assertEquals(75, gaps.get(0).minutes);
        assertEquals(LocalTime.of(10, 0), gaps.get(0).start.toLocalTime());
        assertEquals(LocalTime.of(11, 15), gaps.get(0).end.toLocalTime());
    }
    @Test public void overlappingNestedAndTouchingMeetingsDoNotCreateFalseBreaks() {
        List<ScheduleBreaks.Break> gaps = ScheduleBreaks.inDay(List.of(lesson("a", "09:00 – 12:00"),
            lesson("b", "10:00 – 11:00"), lesson("c", "12:00 – 13:00"), lesson("d", "14:00 – 15:00")), day);
        assertEquals(1, gaps.size()); assertEquals(60, gaps.get(0).minutes);
        assertEquals(LocalTime.of(13, 0), gaps.get(0).start.toLocalTime());
    }
    @Test public void doesNotCountNightsOrFreeTimeBeforeAndAfterClasses() {
        List<LessonTime> rows = List.of(lesson("a", "2026-10-14", "09:00 – 10:00"), lesson("b", "11:00 – 12:00"));
        assertTrue(ScheduleBreaks.inDay(rows, day).isEmpty());
        assertTrue(ScheduleBreaks.inDay(List.of(), day).isEmpty());
    }
    @Test public void includesIncomingOvernightMeetingsWithoutCrossingMidnight() {
        List<ScheduleBreaks.Break> gaps = ScheduleBreaks.inDay(List.of(lesson("a", "2026-10-14", "23:00 – 01:00"),
            lesson("b", "02:00 – 03:00")), day);
        assertEquals(1, gaps.size()); assertEquals(60, gaps.get(0).minutes);
        assertEquals(day, gaps.get(0).start.toLocalDate());
    }
    @Test public void hiddenOrTentativeMeetingsStillOccupyTime() {
        List<ScheduleBreaks.Break> gaps = ScheduleBreaks.inDay(List.of(lesson("a", "09:00 – 10:00"),
            lesson("hidden", "10:00 – 11:00\nTermin orientacyjny"), lesson("b", "11:15 – 12:00")), day);
        assertEquals(1, gaps.size()); assertEquals(15, gaps.get(0).minutes);
    }
}
