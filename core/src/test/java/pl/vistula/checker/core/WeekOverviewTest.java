package pl.vistula.checker.core;

import java.time.LocalDate;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeekOverviewTest {
    private final LocalDate today = LocalDate.of(2026, 10, 14);
    private JSONObject task(String due, boolean done) throws Exception {
        return new JSONObject().put("due", due).put("done", done);
    }
    @Test public void sevenDaysExcludeCompletedOverdueAndFollowingWeekDeadlines() throws Exception {
        JSONArray tasks = new JSONArray().put(task("2026-10-13", false)).put(task("2026-10-14", false))
            .put(task("2026-10-20", false)).put(task("2026-10-21", false)).put(task("2026-10-15", true))
            .put(task("invalid", false));
        WeekOverview overview = WeekOverview.of(List.of(), tasks, today);
        assertEquals(7, overview.days.size()); assertEquals(2, overview.dueTasks); assertEquals(1, overview.overdueTasks);
        assertEquals(1, overview.days.get(0).tasks); assertEquals(1, overview.days.get(6).tasks);
        assertTrue(TaskTools.matches(tasks.getJSONObject(1), "week", "", today));
        assertTrue(TaskTools.matches(tasks.getJSONObject(2), "week", "", today));
        assertFalse(TaskTools.matches(tasks.getJSONObject(3), "week", "", today));
        assertFalse(TaskTools.matches(tasks.getJSONObject(4), "week", "", today));
        assertFalse(TaskTools.matches(tasks.getJSONObject(5), "week", "", today));
    }
    @Test public void overlappingAndOvernightLessonsCountDurationOnce() {
        List<Record> records = List.of(new Record("a", "plan", "A", "09:00 – 11:00", "", today.toString()),
            new Record("b", "plan", "B", "10:00 – 12:00", "", today.toString()),
            new Record("c", "plan", "C", "23:00 – 01:00", "", today.toString()),
            new Record("grade", "ocena", "Grade", "5", "", ""));
        WeekOverview overview = WeekOverview.of(records, null, today);
        assertEquals(3, overview.lessonCount); assertEquals(300, overview.busyMinutes);
        assertEquals(240, overview.days.get(0).schedule.busyMinutes);
        assertEquals(60, overview.days.get(1).schedule.busyMinutes);
    }
    @Test public void taskSearchUsesCurrentQueryAndNotes() throws Exception {
        JSONObject task = task(today.toString(), false).put("title", "Presentation").put("notes", "Library research");
        assertTrue(TaskTools.matches(task, "all", "LIBRARY", today));
        assertFalse(TaskTools.matches(task, "all", "absent", today));
        assertTrue(TaskTools.matches(task, "all", "", today));
        assertFalse(TaskTools.matches(task, "done", "", today));
    }
}
