package pl.vistula.checker.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Duration counts the union of visible meetings clipped to a Warsaw calendar day. */
class DaySummary private constructor(
    @JvmField val meetings: Int,
    @JvmField val tentative: Int,
    @JvmField val conflicts: Int,
    @JvmField val busyMinutes: Long,
    @JvmField val gapMinutes: Long
) {
    companion object {
        @JvmStatic
        fun of(rows: Collection<LessonTime>, day: LocalDate): DaySummary {
            val dayStart = day.atStartOfDay(LessonTime.ZONE).toInstant()
            val dayEnd = day.plusDays(1).atStartOfDay(LessonTime.ZONE).toInstant()
            val intervals = ArrayList<Array<Instant>>()
            var tentative = 0
            for (row in rows) {
                if (row.intersects(day)) {
                    if (row.tentative()) tentative++
                    val s = if (row.start.toInstant().isBefore(dayStart)) dayStart else row.start.toInstant()
                    val e = if (row.end.toInstant().isAfter(dayEnd)) dayEnd else row.end.toInstant()
                    intervals.add(arrayOf(s, e))
                }
            }
            intervals.sortWith(Comparator.comparing { it[0] })
            if (intervals.isEmpty()) return DaySummary(0, 0, 0, 0, 0)
            var conflicts = 0
            for (i in intervals.indices) {
                for (j in i + 1 until intervals.size) {
                    if (!intervals[j][0].isBefore(intervals[i][1])) break
                    conflicts++
                }
            }
            var begin = intervals[0][0]
            var end = intervals[0][1]
            var seconds = 0L
            var gapSeconds = 0L
            for (i in 1 until intervals.size) {
                val next = intervals[i]
                if (next[0].isAfter(end)) {
                    seconds += Duration.between(begin, end).seconds
                    gapSeconds += Duration.between(end, next[0]).seconds
                    begin = next[0]
                    end = next[1]
                } else if (next[1].isAfter(end)) {
                    end = next[1]
                }
            }
            seconds += Duration.between(begin, end).seconds
            return DaySummary(intervals.size, tentative, conflicts, seconds / 60, gapSeconds / 60)
        }
    }
}

