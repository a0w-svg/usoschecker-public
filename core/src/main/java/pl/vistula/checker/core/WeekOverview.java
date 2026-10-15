package pl.vistula.checker.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Seven Warsaw calendar days starting today, based only on the saved timetable. */
public final class WeekOverview {
    public static final class Day {
        public final LocalDate date;
        public final DaySummary schedule;
        public final int tasks;
        private Day(LocalDate date, DaySummary schedule, int tasks) {
            this.date = date; this.schedule = schedule; this.tasks = tasks;
        }
    }
    public final List<Day> days;
    public final int dueTasks, overdueTasks, lessonCount;
    public final long busyMinutes;

    private WeekOverview(List<Day> days, int dueTasks, int overdueTasks, int lessonCount, long busyMinutes) {
        this.days = Collections.unmodifiableList(days);
        this.dueTasks = dueTasks; this.overdueTasks = overdueTasks;
        this.lessonCount = lessonCount; this.busyMinutes = busyMinutes;
    }

    public static WeekOverview of(Collection<Record> records, JSONArray tasks, LocalDate today) {
        List<LessonTime> lessons = LessonTime.sorted(records);
        int[] dueCounts = new int[7];
        int overdue = 0, dueTotal = 0, lessonCount = 0;
        if (tasks != null) for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null || task.optBoolean("done")) continue;
            LocalDate due = TaskTools.dueDate(task);
            if (due == null) continue;
            if (due.isBefore(today)) overdue++;
            else if (due.isBefore(today.plusDays(7))) {
                dueCounts[(int) java.time.temporal.ChronoUnit.DAYS.between(today, due)]++;
                dueTotal++;
            }
        }
        // An overnight meeting contributes to both days' duration but is counted once.
        for (LessonTime lesson : lessons) {
            if (lesson.start.toInstant().isBefore(today.plusDays(7).atStartOfDay(LessonTime.ZONE).toInstant())
                    && lesson.end.toInstant().isAfter(today.atStartOfDay(LessonTime.ZONE).toInstant())) lessonCount++;
        }
        List<Day> days = new ArrayList<>();
        long minutes = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            DaySummary summary = DaySummary.of(lessons, date);
            minutes += summary.busyMinutes;
            days.add(new Day(date, summary, dueCounts[i]));
        }
        return new WeekOverview(days, dueTotal, overdue, lessonCount, minutes);
    }
}
