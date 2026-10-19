package pl.vistula.checker.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object IcsExporter {
    private val ICS_DATE_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    @JvmStatic
    @JvmOverloads
    fun export(records: Collection<Record>, calendarTitle: String? = "Plan zajęć Vistula"): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//Vistula Checker//USOS Timetable//PL\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")
        sb.append("X-WR-CALNAME:").append(escape(calendarTitle)).append("\r\n")
        sb.append("X-WR-TIMEZONE:Europe/Warsaw\r\n")

        val lessons = LessonTime.sorted(records)
        val dtstamp = ICS_DATE_TIME.format(Instant.now())

        for (lt in lessons) {
            val r = lt.record
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:").append(escape(r.id)).append("@vistula.checker\r\n")
            sb.append("DTSTAMP:").append(dtstamp).append("\r\n")
            // UTC avoids relying on a missing VTIMEZONE definition in importing calendars.
            sb.append("DTSTART:").append(ICS_DATE_TIME.format(lt.start)).append("\r\n")
            sb.append("DTEND:").append(ICS_DATE_TIME.format(lt.end)).append("\r\n")

            val classType = r.optMeta("classType")
            val title = r.title + (if (classType.isNotEmpty()) " ($classType)" else "")
            sb.append("SUMMARY:").append(escape(title)).append("\r\n")

            val room = r.optMeta("room")
            val building = r.optMeta("building")
            val location = (if (room.isNotEmpty()) "Sala $room" else "") +
                (if (building.isNotEmpty()) (if (room.isNotEmpty()) ", " else "") + building else "") +
                ", Akademia Finansów i Biznesu Vistula, ul. Stokłosy 3, Warszawa"
            sb.append("LOCATION:").append(escape(location)).append("\r\n")

            val desc = StringBuilder()
            val lecturers = r.optMeta("lecturers")
            if (lecturers.isNotEmpty()) desc.append("Prowadzący: ").append(lecturers).append("\n")
            val group = r.optMeta("groupNumber")
            if (group.isNotEmpty()) desc.append("Grupa: ").append(group).append("\n")
            val courseId = r.optMeta("courseId")
            if (courseId.isNotEmpty()) desc.append("Kod przedmiotu: ").append(courseId).append("\n")
            if (r.term.isNotEmpty()) desc.append("Semestr: ").append(r.term).append("\n")
            if (desc.isEmpty()) desc.append(r.value)

            sb.append("DESCRIPTION:").append(escape(desc.toString())).append("\r\n")
            sb.append(if (lt.tentative()) "STATUS:TENTATIVE\r\n" else "STATUS:CONFIRMED\r\n")
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        val folded = StringBuilder()
        for (line in sb.toString().split("\r\n").dropLastWhile { it.isEmpty() }) {
            CalendarExport.line(folded, line)
        }
        return folded.toString()
    }

    private fun escape(s: String?): String {
        if (s == null) return ""
        return s.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
    }
}

