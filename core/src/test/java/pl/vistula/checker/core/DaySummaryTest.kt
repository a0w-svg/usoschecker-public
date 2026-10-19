package pl.vistula.checker.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DaySummaryTest {
    private fun lesson(id: String, day: String, value: String): LessonTime =
        LessonTime.of(Record(id, "plan", id, value, "", day))

    @Test
    fun overlappingMeetingsAreCountedOnceInTotalDuration() {
        val summary = DaySummary.of(
            listOf(
                lesson("a", "2026-10-13", "09:00 – 10:30"),
                lesson("b", "2026-10-13", "10:00 – 11:00"),
                lesson("c", "2026-10-13", "12:00 – 13:00")
            ),
            LocalDate.of(2026, 10, 13)
        )
        assertEquals(3, summary.meetings)
        assertEquals(180L, summary.busyMinutes)
        assertEquals(60L, summary.gapMinutes)
        assertEquals(1, summary.conflicts)
    }

    @Test
    fun touchingMeetingsHaveNoConflictOrGap() {
        val summary = DaySummary.of(
            listOf(lesson("a", "2026-10-13", "09:00 – 10:00"), lesson("b", "2026-10-13", "10:00 – 11:00")),
            LocalDate.of(2026, 10, 13)
        )
        assertEquals(120L, summary.busyMinutes)
        assertEquals(0, summary.conflicts)
        assertEquals(0L, summary.gapMinutes)
    }

    @Test
    fun overnightMeetingsAreClippedToEachDay() {
        val rows = listOf(lesson("a", "2026-10-13", "23:00 – 01:00"))
        assertEquals(60L, DaySummary.of(rows, LocalDate.of(2026, 10, 13)).busyMinutes)
        assertEquals(60L, DaySummary.of(rows, LocalDate.of(2026, 10, 14)).busyMinutes)
        assertEquals(0, DaySummary.of(rows, LocalDate.of(2026, 10, 15)).meetings)
    }

    @Test
    fun tentativeAndMultipleOverlapsRemainExplicit() {
        val summary = DaySummary.of(
            listOf(
                lesson("a", "2026-10-13", "09:00 – 12:00"),
                lesson("b", "2026-10-13", "10:00 – 11:00\nTermin orientacyjny"),
                lesson("c", "2026-10-13", "10:30 – 11:30")
            ),
            LocalDate.of(2026, 10, 13)
        )
        assertEquals(3, summary.conflicts)
        assertEquals(1, summary.tentative)
        assertEquals(180L, summary.busyMinutes)
    }
}
