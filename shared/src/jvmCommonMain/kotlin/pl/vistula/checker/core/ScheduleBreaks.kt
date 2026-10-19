package pl.vistula.checker.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** Free intervals between the union of all meetings within one Warsaw calendar day. */
object ScheduleBreaks {
    class Break internal constructor(start: Instant, end: Instant) {
        @JvmField val start: ZonedDateTime = start.atZone(LessonTime.ZONE)
        @JvmField val end: ZonedDateTime = end.atZone(LessonTime.ZONE)
        @JvmField val minutes: Long = Duration.between(start, end).toMinutes()
    }

    @JvmStatic
    fun inDay(lessons: Collection<LessonTime>, day: LocalDate): List<Break> {
        val dayStart = day.atStartOfDay(LessonTime.ZONE).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(LessonTime.ZONE).toInstant()
        val intervals = ArrayList<Array<Instant>>()
        for (lesson in lessons) {
            if (lesson.intersects(day)) {
                val s = if (lesson.start.toInstant().isBefore(dayStart)) dayStart else lesson.start.toInstant()
                val e = if (lesson.end.toInstant().isAfter(dayEnd)) dayEnd else lesson.end.toInstant()
                intervals.add(arrayOf(s, e))
            }
        }
        intervals.sortWith(Comparator.comparing { it[0] })
        val result = ArrayList<Break>()
        var occupiedUntil: Instant? = null
        for (interval in intervals) {
            if (occupiedUntil != null && interval[0].isAfter(occupiedUntil)) {
                val gap = Break(occupiedUntil, interval[0])
                if (gap.minutes > 0) result.add(gap)
            }
            if (occupiedUntil == null || interval[1].isAfter(occupiedUntil)) {
                occupiedUntil = interval[1]
            }
        }
        return result
    }
}

