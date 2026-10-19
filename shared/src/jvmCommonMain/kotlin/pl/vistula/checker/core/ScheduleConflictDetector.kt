package pl.vistula.checker.core

import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

object ScheduleConflictDetector {
    class Conflict(
        @JvmField val first: Record,
        @JvmField val second: Record,
        @JvmField val overlapMinutes: Long,
    ) {
        fun description(): String = String.format(
            Locale.forLanguageTag("pl-PL"),
            "Kolizja w planie zajęć: %s oraz %s nakładają się na siebie przez %d min (%s)",
            first.title, second.title, overlapMinutes, first.date,
        )
    }

    @JvmStatic
    fun findConflicts(records: Collection<Record>?): List<Conflict> {
        if (records == null || records.size < 2) return emptyList()

        val lessons = ArrayList<Record>()
        for (r in records) {
            if ("plan" == r.kind && r.startTime.isNotEmpty() && r.endTime.isNotEmpty()) {
                lessons.add(r)
            }
        }

        val byDate = HashMap<String, MutableList<Record>>()
        for (l in lessons) {
            byDate.computeIfAbsent(l.date) { ArrayList() }.add(l)
        }

        val conflicts = ArrayList<Conflict>()
        for (dayLessons in byDate.values) {
            if (dayLessons.size < 2) continue
            for (i in dayLessons.indices) {
                val a = dayLessons[i]
                val startA: LocalDateTime
                val endA: LocalDateTime
                try {
                    startA = LocalDateTime.parse(a.startTime.replace(" ", "T"))
                    endA = LocalDateTime.parse(a.endTime.replace(" ", "T"))
                } catch (ignored: Exception) {
                    continue
                }

                for (j in i + 1 until dayLessons.size) {
                    val b = dayLessons[j]
                    val startB: LocalDateTime
                    val endB: LocalDateTime
                    try {
                        startB = LocalDateTime.parse(b.startTime.replace(" ", "T"))
                        endB = LocalDateTime.parse(b.endTime.replace(" ", "T"))
                    } catch (ignored: Exception) {
                        continue
                    }

                    if (startA.isBefore(endB) && startB.isBefore(endA)) {
                        val overlapStart = if (startA.isAfter(startB)) startA else startB
                        val overlapEnd = if (endA.isBefore(endB)) endA else endB
                        val minutes = Duration.between(overlapStart, overlapEnd).toMinutes()
                        if (minutes > 0) {
                            conflicts.add(Conflict(a, b, minutes))
                        }
                    }
                }
            }
        }
        return conflicts
    }

    @JvmStatic
    fun hasConflicts(records: Collection<Record>?): Boolean = findConflicts(records).isNotEmpty()
}

