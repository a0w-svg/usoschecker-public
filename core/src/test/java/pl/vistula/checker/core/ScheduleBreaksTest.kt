package pl.vistula.checker.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleBreaksTest {
    private val day = LocalDate.of(2026, 10, 15)
    private fun lesson(id: String, date: String, time: String): LessonTime =
        LessonTime.of(Record(id, "plan", id, time, "", date))

    private fun lesson(id: String, time: String): LessonTime = lesson(id, day.toString(), time)

    @Test
    fun calculatesExactBreaksInUnsortedPlan() {
        val gaps = ScheduleBreaks.inDay(listOf(lesson("b", "11:15 – 12:00"), lesson("a", "09:00 – 10:00")), day)
        assertEquals(1, gaps.size)
        assertEquals(75L, gaps[0].minutes)
        assertEquals(LocalTime.of(10, 0), gaps[0].start.toLocalTime())
        assertEquals(LocalTime.of(11, 15), gaps[0].end.toLocalTime())
    }

    @Test
    fun overlappingNestedAndTouchingMeetingsDoNotCreateFalseBreaks() {
        val gaps = ScheduleBreaks.inDay(
            listOf(
                lesson("a", "09:00 – 12:00"),
                lesson("b", "10:00 – 11:00"),
                lesson("c", "12:00 – 13:00"),
                lesson("d", "14:00 – 15:00")
            ),
            day
        )
        assertEquals(1, gaps.size)
        assertEquals(60L, gaps[0].minutes)
        assertEquals(LocalTime.of(13, 0), gaps[0].start.toLocalTime())
    }

    @Test
    fun doesNotCountNightsOrFreeTimeBeforeAndAfterClasses() {
        val rows = listOf(lesson("a", "2026-10-14", "09:00 – 10:00"), lesson("b", "11:00 – 12:00"))
        assertTrue(ScheduleBreaks.inDay(rows, day).isEmpty())
        assertTrue(ScheduleBreaks.inDay(emptyList(), day).isEmpty())
    }

    @Test
    fun includesIncomingOvernightMeetingsWithoutCrossingMidnight() {
        val gaps = ScheduleBreaks.inDay(
            listOf(lesson("a", "2026-10-14", "23:00 – 01:00"), lesson("b", "02:00 – 03:00")),
            day
        )
        assertEquals(1, gaps.size)
        assertEquals(60L, gaps[0].minutes)
        assertEquals(day, gaps[0].start.toLocalDate())
    }

    @Test
    fun hiddenOrTentativeMeetingsStillOccupyTime() {
        val gaps = ScheduleBreaks.inDay(
            listOf(
                lesson("a", "09:00 – 10:00"),
                lesson("hidden", "10:00 – 11:00\nTermin orientacyjny"),
                lesson("b", "11:15 – 12:00")
            ),
            day
        )
        assertEquals(1, gaps.size)
        assertEquals(15L, gaps[0].minutes)
    }
}
