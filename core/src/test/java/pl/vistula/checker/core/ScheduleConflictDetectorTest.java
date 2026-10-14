package pl.vistula.checker.core;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ScheduleConflictDetectorTest {

    private Record createLesson(String id, String title, String date, String start, String end) {
        return new Record(
            id,
            "plan",
            title,
            start.substring(11, 16) + " - " + end.substring(11, 16),
            "Sala 101",
            date,
            "2026Z",
            start,
            end,
            Double.NaN,
            new JSONObject()
        );
    }

    @Test
    public void testNoConflictsWhenEmptyOrSingle() {
        assertTrue(ScheduleConflictDetector.findConflicts(Collections.emptyList()).isEmpty());
        assertFalse(ScheduleConflictDetector.hasConflicts(Collections.emptyList()));

        Record r1 = createLesson("1", "Programowanie", "2026-10-15", "2026-10-15 08:00:00", "2026-10-15 09:30:00");
        assertTrue(ScheduleConflictDetector.findConflicts(Collections.singletonList(r1)).isEmpty());
    }

    @Test
    public void testSequentialLessonsNoConflict() {
        Record r1 = createLesson("1", "Programowanie", "2026-10-15", "2026-10-15 08:00:00", "2026-10-15 09:30:00");
        Record r2 = createLesson("2", "Matematyka", "2026-10-15", "2026-10-15 09:45:00", "2026-10-15 11:15:00");

        List<ScheduleConflictDetector.Conflict> conflicts = ScheduleConflictDetector.findConflicts(Arrays.asList(r1, r2));
        assertTrue(conflicts.isEmpty());
        assertFalse(ScheduleConflictDetector.hasConflicts(Arrays.asList(r1, r2)));
    }

    @Test
    public void testBackToBackNoConflict() {
        Record r1 = createLesson("1", "Bazy Danych", "2026-10-15", "2026-10-15 10:00:00", "2026-10-15 11:30:00");
        Record r2 = createLesson("2", "Sieci Komputerowe", "2026-10-15", "2026-10-15 11:30:00", "2026-10-15 13:00:00");

        List<ScheduleConflictDetector.Conflict> conflicts = ScheduleConflictDetector.findConflicts(Arrays.asList(r1, r2));
        assertTrue(conflicts.isEmpty());
    }

    @Test
    public void testOverlappingLessonsDetected() {
        Record r1 = createLesson("1", "Bazy Danych (W)", "2026-10-15", "2026-10-15 10:00:00", "2026-10-15 11:30:00");
        Record r2 = createLesson("2", "Fizyka (Ć)", "2026-10-15", "2026-10-15 11:00:00", "2026-10-15 12:30:00");

        List<ScheduleConflictDetector.Conflict> conflicts = ScheduleConflictDetector.findConflicts(Arrays.asList(r1, r2));
        assertEquals(1, conflicts.size());
        assertTrue(ScheduleConflictDetector.hasConflicts(Arrays.asList(r1, r2)));

        ScheduleConflictDetector.Conflict c = conflicts.get(0);
        assertEquals(30, c.overlapMinutes);
        assertTrue(c.description().contains("Bazy Danych (W)"));
        assertTrue(c.description().contains("Fizyka (Ć)"));
        assertTrue(c.description().contains("30 min"));
    }

    @Test
    public void testLessonsOnDifferentDaysNoConflict() {
        Record r1 = createLesson("1", "Bazy Danych", "2026-10-15", "2026-10-15 10:00:00", "2026-10-15 11:30:00");
        Record r2 = createLesson("2", "Fizyka", "2026-10-16", "2026-10-16 10:00:00", "2026-10-16 11:30:00");

        List<ScheduleConflictDetector.Conflict> conflicts = ScheduleConflictDetector.findConflicts(Arrays.asList(r1, r2));
        assertTrue(conflicts.isEmpty());
    }
}
