package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class CalendarTimelineTest {
    private fun event(id: String, start: String, end: String): Record =
        Record(
            id, "plan", id, start.substring(11, 16) + " – " + end.substring(11, 16), "",
            start.substring(0, 10), "", start, end
        )

    @Test
    fun currentLectureIncludesStartAndExcludesEndAndFutureClasses() {
        val active = event("active", "2026-10-13 09:00:00", "2026-10-13 10:30:00")
        val start = LocalDateTime.of(2026, 10, 13, 9, 0).atZone(LessonTime.ZONE).toInstant()
        assertEquals(1, LessonTime.current(listOf(active), start).size)
        assertEquals(0, LessonTime.current(listOf(active), start.minusSeconds(1)).size)
        assertEquals(0, LessonTime.current(listOf(active), start.plusSeconds(90 * 60)).size)
    }

    @Test
    fun overnightLecturesIntersectBothDaysAndOldSnapshotsStillWork() {
        val time = LessonTime.of(event("overnight", "2026-10-13 23:00:00", "2026-10-14 01:00:00"))
        assertTrue(time.intersects(LocalDate.of(2026, 10, 13)))
        assertTrue(time.intersects(LocalDate.of(2026, 10, 14)))
        assertFalse(time.intersects(LocalDate.of(2026, 10, 15)))
        assertEquals(time.start, LessonTime.of(Record("legacy", "plan", "", "23:00 – 01:00", "", "2026-10-13")).start)
    }

    @Test
    fun approximateEntriesAreNotPresentedAsCurrentLectures() {
        val tentative = Record("1", "plan", "A", "09:00 – 10:00\nTermin orientacyjny", "", "2026-10-13")
        assertTrue(LessonTime.current(listOf(tentative), Instant.parse("2026-10-13T07:30:00Z")).isEmpty())
    }

    @Test
    fun movingLectureToDifferentDayIsDetectedAndExactTimesRoundTrip() {
        val first = event("meeting", "2026-10-13 09:00:00", "2026-10-13 10:30:00")
        val moved = event("meeting", "2026-10-14 09:00:00", "2026-10-14 10:30:00")
        assertFalse(first.same(moved))
        assertEquals(first.endTime, Record.from(first.json()).endTime)
        assertEquals(
            "",
            Record.from(
                JSONObject().put("id", "1").put("kind", "plan").put("title", "A")
                    .put("value", "09:00 – 10:00").put("sourceTime", "").put("date", "2026-10-13")
            ).startTime
        )
    }

    @Test
    fun loaderFetchesAllEightDistinctWeeksNotJustTheFirst() {
        val start = LocalDate.of(2026, 10, 12)
        val starts = Collections.synchronizedSet(HashSet<LocalDate>())
        val progress = ArrayList<String>()
        val weeks = TimetableLoader.load(start, 56, { date, days ->
            assertEquals(7, days)
            starts.add(date)
            JSONArray().put(JSONObject().put("start_time", "$date 09:00:00"))
        }, { progress.add(it) })
        assertEquals(8, weeks.size)
        assertEquals(8, starts.size)
        assertTrue(starts.contains(start.plusDays(49)))
        assertTrue(progress[progress.size - 1].contains("8/8"))
        for (i in 0 until 8) {
            assertEquals("${start.plusDays(i * 7L)} 09:00:00", weeks[i].getJSONObject(0).getString("start_time"))
        }
    }

    @Test
    fun extendedRangeLoadsSixteenWeeks() {
        val requests = AtomicInteger()
        assertEquals(
            16,
            TimetableLoader.load(LocalDate.of(2026, 10, 12), 112, { _, _ ->
                requests.incrementAndGet()
                JSONArray()
            }, {}).size
        )
        assertEquals(16, requests.get())
    }

    @Test(expected = IOException::class)
    fun failedLaterWeekNeverReturnsPartialData() {
        val start = LocalDate.of(2026, 10, 12)
        TimetableLoader.load(start, 56, { date, _ ->
            if (date == start.plusDays(21)) throw IOException("week failure")
            JSONArray()
        }, {})
    }

    @Test(expected = JSONException::class)
    fun repeatedFirstWeekResponseIsRejectedInsteadOfSilentlyTruncating() {
        TimetableLoader.load(
            LocalDate.of(2026, 10, 12),
            56,
            { _, _ -> JSONArray().put(JSONObject().put("start_time", "2026-10-12 09:00:00")) },
            {}
        )
    }
}
