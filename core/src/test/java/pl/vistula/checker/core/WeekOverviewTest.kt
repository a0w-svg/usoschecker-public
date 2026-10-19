package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeekOverviewTest {
    private val today = LocalDate.of(2026, 10, 14)
    private fun task(due: String, done: Boolean): JSONObject =
        JSONObject().put("due", due).put("done", done)

    @Test
    fun sevenDaysExcludeCompletedOverdueAndFollowingWeekDeadlines() {
        val tasks = JSONArray().put(task("2026-10-13", false)).put(task("2026-10-14", false))
            .put(task("2026-10-20", false)).put(task("2026-10-21", false)).put(task("2026-10-15", true))
            .put(task("invalid", false))
        val overview = WeekOverview.of(emptyList(), tasks, today)
        assertEquals(7, overview.days.size)
        assertEquals(2, overview.dueTasks)
        assertEquals(1, overview.overdueTasks)
        assertEquals(1, overview.days[0].tasks)
        assertEquals(1, overview.days[6].tasks)
        assertTrue(TaskTools.matches(tasks.getJSONObject(1), "week", "", today))
        assertTrue(TaskTools.matches(tasks.getJSONObject(2), "week", "", today))
        assertFalse(TaskTools.matches(tasks.getJSONObject(3), "week", "", today))
        assertFalse(TaskTools.matches(tasks.getJSONObject(4), "week", "", today))
        assertFalse(TaskTools.matches(tasks.getJSONObject(5), "week", "", today))
    }

    @Test
    fun overlappingAndOvernightLessonsCountDurationOnce() {
        val records = listOf(
            Record("a", "plan", "A", "09:00 – 11:00", "", today.toString()),
            Record("b", "plan", "B", "10:00 – 12:00", "", today.toString()),
            Record("c", "plan", "C", "23:00 – 01:00", "", today.toString()),
            Record("grade", "ocena", "Grade", "5", "", "")
        )
        val overview = WeekOverview.of(records, null, today)
        assertEquals(3, overview.lessonCount)
        assertEquals(300L, overview.busyMinutes)
        assertEquals(240L, overview.days[0].schedule.busyMinutes)
        assertEquals(60L, overview.days[1].schedule.busyMinutes)
    }

    @Test
    fun taskSearchUsesCurrentQueryAndNotes() {
        val task = task(today.toString(), false).put("title", "Presentation").put("notes", "Library research")
        assertTrue(TaskTools.matches(task, "all", "LIBRARY", today))
        assertFalse(TaskTools.matches(task, "all", "absent", today))
        assertTrue(TaskTools.matches(task, "all", "", today))
        assertFalse(TaskTools.matches(task, "done", "", today))
    }
}
