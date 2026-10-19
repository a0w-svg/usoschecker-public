package pl.vistula.checker.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.NavigableSet
import java.util.TreeSet

object PlanBrowser {
    @JvmStatic
    fun daysWithLessons(lessons: List<LessonTime>, query: String): NavigableSet<LocalDate> {
        val days = TreeSet<LocalDate>()
        for (lesson in lessons) {
            if (!StudentSearch.matches(query, lesson.record.title, lesson.record.value, lesson.record.date)) continue
            val last = lesson.end.minusNanos(1).toLocalDate()
            var day = lesson.start.toLocalDate()
            while (!day.isAfter(last)) {
                days.add(day)
                day = day.plusDays(1)
            }
        }
        return days
    }

    @JvmStatic
    fun nearestDay(days: NavigableSet<LocalDate>, requested: LocalDate): LocalDate {
        val next = days.ceiling(requested)
        return next ?: if (days.isEmpty()) requested else days.last()
    }

    @JvmStatic
    fun adjacentDay(days: NavigableSet<LocalDate>, active: LocalDate, weekly: Boolean, direction: Int): LocalDate? {
        val monday = active.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return if (direction > 0) {
            days.higher(if (weekly) monday.plusDays(6) else active)
        } else {
            days.lower(if (weekly) monday else active)
        }
    }

    @JvmStatic
    fun select(lessons: List<LessonTime>, day: LocalDate, mode: String, query: String): List<LessonTime> {
        val week = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val result = ArrayList<LessonTime>()
        for (lesson in lessons) {
            if (!StudentSearch.matches(query, lesson.record.title, lesson.record.value, lesson.record.date)) continue
            val inRange = mode == "agenda" || (if (mode == "week") {
                lesson.start.toInstant().isBefore(week.plusDays(7).atStartOfDay(LessonTime.ZONE).toInstant()) &&
                    lesson.end.toInstant().isAfter(week.atStartOfDay(LessonTime.ZONE).toInstant())
            } else {
                lesson.intersects(day)
            })
            if (inRange) result.add(lesson)
        }
        return result
    }

    @JvmStatic
    fun nextDay(lessons: List<LessonTime>, day: LocalDate, end: LocalDate, query: String): LocalDate? {
        var result: LocalDate? = null
        for (lesson in lessons) {
            val candidate = if (lesson.start.toLocalDate().isAfter(day)) lesson.start.toLocalDate() else day.plusDays(1)
            if (candidate.isAfter(end) || !lesson.intersects(candidate) || (result != null && !candidate.isBefore(result))) continue
            if (StudentSearch.matches(query, lesson.record.title, lesson.record.value, lesson.record.date)) {
                result = candidate
            }
        }
        return result
    }
}

