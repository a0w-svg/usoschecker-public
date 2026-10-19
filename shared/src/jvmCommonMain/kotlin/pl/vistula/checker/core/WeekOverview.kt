package pl.vistula.checker.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Collections
import org.json.JSONArray

/** Seven Warsaw calendar days starting today, based only on the saved timetable. */
class WeekOverview private constructor(
    days: List<Day>,
    @JvmField val dueTasks: Int,
    @JvmField val overdueTasks: Int,
    @JvmField val lessonCount: Int,
    @JvmField val busyMinutes: Long
) {
    class Day internal constructor(
        @JvmField val date: LocalDate,
        @JvmField val schedule: DaySummary,
        @JvmField val tasks: Int
    )

    @JvmField val days: List<Day> = Collections.unmodifiableList(days)

    companion object {
        @JvmStatic
        fun of(records: Collection<Record>, tasks: JSONArray?, today: LocalDate): WeekOverview {
            val lessons = LessonTime.sorted(records)
            val dueCounts = IntArray(7)
            var overdue = 0
            var dueTotal = 0
            var lessonCount = 0
            if (tasks != null) {
                for (i in 0 until tasks.length()) {
                    val task = tasks.optJSONObject(i)
                    if (task == null || task.optBoolean("done")) continue
                    val due = TaskTools.dueDate(task) ?: continue
                    if (due.isBefore(today)) {
                        overdue++
                    } else if (due.isBefore(today.plusDays(7))) {
                        dueCounts[ChronoUnit.DAYS.between(today, due).toInt()]++
                        dueTotal++
                    }
                }
            }
            // An overnight meeting contributes to both days' duration but is counted once.
            for (lesson in lessons) {
                if (lesson.start.toInstant().isBefore(today.plusDays(7).atStartOfDay(LessonTime.ZONE).toInstant()) &&
                    lesson.end.toInstant().isAfter(today.atStartOfDay(LessonTime.ZONE).toInstant())
                ) {
                    lessonCount++
                }
            }
            val days = ArrayList<Day>()
            var minutes = 0L
            for (i in 0 until 7) {
                val date = today.plusDays(i.toLong())
                val summary = DaySummary.of(lessons, date)
                minutes += summary.busyMinutes
                days.add(Day(date, summary, dueCounts[i]))
            }
            return WeekOverview(days, dueTotal, overdue, lessonCount, minutes)
        }
    }
}

