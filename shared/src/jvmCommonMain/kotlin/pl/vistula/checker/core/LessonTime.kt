package pl.vistula.checker.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.regex.Pattern

class LessonTime private constructor(
    @JvmField val record: Record,
    start: LocalDateTime,
    end: LocalDateTime
) {
    @JvmField val start: ZonedDateTime = start.atZone(ZONE)
    @JvmField val end: ZonedDateTime = end.atZone(ZONE)

    init {
        require(this.end.isAfter(this.start)) { "Nieprawidłowy czas zajęć." }
    }

    fun current(now: Instant): Boolean = !now.isBefore(start.toInstant()) && now.isBefore(end.toInstant())
    fun contains(now: Instant): Boolean = current(now)

    fun tentative(): Boolean {
        val frequency = record.optMeta("frequency")
        return "other" == frequency || "once" == frequency ||
            record.value.contains("Termin orientacyjny") ||
            record.value.contains("Tentative date") ||
            record.value.contains("Орієнтовна дата")
    }

    fun intersects(date: LocalDate): Boolean =
        start.toInstant().isBefore(date.plusDays(1).atStartOfDay(ZONE).toInstant()) &&
            end.toInstant().isAfter(date.atStartOfDay(ZONE).toInstant())

    companion object {
        @JvmField val ZONE: ZoneId = ZoneId.of("Europe/Warsaw")
        private val HOURS: Pattern = Pattern.compile("^(?:\\d{4}-\\d{2}-\\d{2} )?(\\d{2}:\\d{2})\\s*[–-]\\s*(\\d{2}:\\d{2})")

        @JvmStatic
        fun of(r: Record): LessonTime {
            if (r.startTime.isNotEmpty() && r.endTime.isNotEmpty()) {
                return LessonTime(
                    r,
                    LocalDateTime.parse(r.startTime, UsosParser.TIME),
                    LocalDateTime.parse(r.endTime, UsosParser.TIME)
                )
            }
            val match = HOURS.matcher(r.value)
            if (!match.find()) throw IllegalArgumentException("Brak godzin zajęć: " + r.title)
            val day = LocalDate.parse(r.date)
            val start = LocalTime.parse(match.group(1))
            val end = LocalTime.parse(match.group(2))
            return LessonTime(r, day.atTime(start), (if (end.isBefore(start)) day.plusDays(1) else day).atTime(end))
        }

        @JvmStatic
        fun sorted(rows: Collection<Record>): List<LessonTime> {
            val result = ArrayList<LessonTime>()
            for (r in rows) if (r.kind == "plan") result.add(of(r))
            result.sortWith(Comparator.comparing<LessonTime, Instant> { it.start.toInstant() }.thenComparing { it.record.id })
            return result
        }

        @JvmStatic
        fun current(rows: Collection<Record>, now: Instant): List<LessonTime> {
            val result = ArrayList<LessonTime>()
            for (t in sorted(rows)) if (!t.tentative() && t.current(now)) result.add(t)
            return result
        }
    }
}

