package pl.vistula.checker.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.TreeSet

class PlanBrowserTest {
    @Test
    fun availableDaysSkipGapsAndRespectSearchAcrossMonthBoundary() {
        val rows = listOf(
            lesson("A", "2026-10-30", "09:00 – 10:00\nSala 204"),
            lesson("B", "2026-11-03", "09:00 – 10:00\nSala 204"),
            lesson("C", "2026-11-04", "09:00 – 10:00\nSala 101")
        )
        val days = PlanBrowser.daysWithLessons(rows, "204")
        assertEquals(TreeSet(listOf(LocalDate.parse("2026-10-30"), LocalDate.parse("2026-11-03"))), days)
        assertEquals(LocalDate.parse("2026-11-03"), PlanBrowser.adjacentDay(days, LocalDate.parse("2026-10-30"), false, 1))
        assertEquals(LocalDate.parse("2026-10-30"), PlanBrowser.adjacentDay(days, LocalDate.parse("2026-11-03"), false, -1))
        assertEquals(LocalDate.parse("2026-11-03"), PlanBrowser.nearestDay(days, LocalDate.parse("2026-10-31")))
        assertNull(PlanBrowser.adjacentDay(days, LocalDate.parse("2026-11-03"), false, 1))
        assertTrue(PlanBrowser.daysWithLessons(rows, "missing").isEmpty())
    }

    @Test
    fun availableDaysIncludeOvernightPartButNotMidnightEndAndSkipEmptyWeeks() {
        val days = PlanBrowser.daysWithLessons(
            listOf(
                lesson("A", "2026-10-30", "23:00 – 01:00"),
                lesson("B", "2026-11-03", "22:00 – 00:00"),
                lesson("C", "2026-11-20", "09:00 – 10:00")
            ),
            ""
        )
        assertTrue(days.contains(LocalDate.parse("2026-10-31")))
        assertFalse(days.contains(LocalDate.parse("2026-11-04")))
        assertEquals(LocalDate.parse("2026-11-20"), PlanBrowser.adjacentDay(days, LocalDate.parse("2026-11-03"), true, 1))
        assertEquals(LocalDate.parse("2026-11-03"), PlanBrowser.adjacentDay(days, LocalDate.parse("2026-11-20"), true, -1))
    }

    private fun lesson(id: String, date: String, value: String): LessonTime =
        LessonTime.of(Record(id, "plan", id, value, "", date))

    @Test
    fun nextDayRespectsTheActiveSearchAndSavedRange() {
        val rows = listOf(
            lesson("A", "2026-10-14", "09:00 – 10:00\nSala 101"),
            lesson("B", "2026-10-16", "09:00 – 10:00\nSala 204")
        )
        assertEquals(LocalDate.of(2026, 10, 16), PlanBrowser.nextDay(rows, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 20), "204"))
        assertNull(PlanBrowser.nextDay(rows, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 15), "204"))
        assertNull(PlanBrowser.nextDay(rows, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 20), "315"))
        assertEquals(
            LocalDate.of(2026, 10, 14),
            PlanBrowser.nextDay(listOf(rows[1], rows[0]), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 20), "")
        )
    }

    @Test
    fun dailyListsIncludeIncomingOvernightClassesAndExcludeEndBoundaries() {
        val rows = listOf(lesson("A", "2026-10-13", "23:00 – 01:00"), lesson("B", "2026-10-13", "22:00 – 00:00"))
        val visible = PlanBrowser.select(rows, LocalDate.of(2026, 10, 14), "list", "")
        assertEquals(1, visible.size)
        assertEquals("A", visible[0].record.id)
        assertEquals(LocalDate.of(2026, 10, 14), PlanBrowser.nextDay(rows, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 15), ""))
    }

    @Test
    fun weeklyAndAgendaModesPreserveTheirDistinctRanges() {
        val rows = listOf(lesson("A", "2026-10-14", "09:00 – 10:00\nĆwiczenia"), lesson("B", "2026-10-21", "09:00 – 10:00"))
        assertEquals(1, PlanBrowser.select(rows, LocalDate.of(2026, 10, 14), "week", "").size)
        assertEquals(2, PlanBrowser.select(rows, LocalDate.of(2026, 10, 14), "agenda", "").size)
        assertEquals(1, PlanBrowser.select(rows, LocalDate.of(2026, 10, 14), "list", "cwiczenia").size)
    }
}
