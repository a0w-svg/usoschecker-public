package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ScheduleConflictDetectorTest {
    private fun createLesson(id: String, title: String, date: String, start: String, end: String): Record =
        Record(
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
            JSONObject()
        )

    @Test
    fun testNoConflictsWhenEmptyOrSingle() {
        assertTrue(ScheduleConflictDetector.findConflicts(emptyList()).isEmpty())
        assertFalse(ScheduleConflictDetector.hasConflicts(emptyList()))

        val r1 = createLesson("1", "Programowanie", "2026-10-15", "2026-10-15 08:00:00", "2026-10-15 09:30:00")
        assertTrue(ScheduleConflictDetector.findConflicts(listOf(r1)).isEmpty())
    }

    @Test
    fun testSequentialLessonsNoConflict() {
        val r1 = createLesson("1", "Programowanie", "2026-10-15", "2026-10-15 08:00:00", "2026-10-15 09:30:00")
        val r2 = createLesson("2", "Matematyka", "2026-10-15", "2026-10-15 09:45:00", "2026-10-15 11:15:00")

        val conflicts = ScheduleConflictDetector.findConflicts(listOf(r1, r2))
        assertTrue(conflicts.isEmpty())
        assertFalse(ScheduleConflictDetector.hasConflicts(listOf(r1, r2)))
    }

    @Test
    fun testBackToBackNoConflict() {
        val r1 = createLesson("1", "Bazy Danych", "2026-10-15", "2026-10-15 10:00:00", "2026-10-15 11:30:00")
        val r2 = createLesson("2", "Sieci Komputerowe", "2026-10-15", "2026-10-15 11:30:00", "2026-10-15 13:00:00")

        val conflicts = ScheduleConflictDetector.findConflicts(listOf(r1, r2))
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun testOverlappingLessonsDetected() {
        val r1 = createLesson("1", "Bazy Danych (W)", "2026-10-15", "2026-10-15 10:00:00", "2026-10-15 11:30:00")
        val r2 = createLesson("2", "Fizyka (Ć)", "2026-10-15", "2026-10-15 11:00:00", "2026-10-15 12:30:00")

        val conflicts = ScheduleConflictDetector.findConflicts(listOf(r1, r2))
        assertEquals(1, conflicts.size)
        assertTrue(ScheduleConflictDetector.hasConflicts(listOf(r1, r2)))

        val c = conflicts[0]
        assertEquals(30L, c.overlapMinutes)
        assertTrue(c.description().contains("Bazy Danych (W)"))
        assertTrue(c.description().contains("Fizyka (Ć)"))
        assertTrue(c.description().contains("30 min"))
    }

    @Test
    fun testLessonsOnDifferentDaysNoConflict() {
        val r1 = createLesson("1", "Bazy Danych", "2026-10-15", "2026-10-15 10:00:00", "2026-10-15 11:30:00")
        val r2 = createLesson("2", "Fizyka", "2026-10-16", "2026-10-16 10:00:00", "2026-10-16 11:30:00")

        val conflicts = ScheduleConflictDetector.findConflicts(listOf(r1, r2))
        assertTrue(conflicts.isEmpty())
    }
}
