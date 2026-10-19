package pl.vistula.checker.core

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** RFC 5545 snapshot export; UTC timestamps retain Warsaw daylight-saving offsets. */
object CalendarExport {
    private val UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    @JvmStatic
    fun create(rows: Collection<Record>, exportedAt: Instant): String {
        val out = StringBuilder()
        line(out, "BEGIN:VCALENDAR")
        line(out, "VERSION:2.0")
        line(out, "PRODID:-//Vistula Checker//Plan//PL")
        line(out, "CALSCALE:GREGORIAN")
        for (row in rows) {
            if (row.kind != "plan") continue
            val lesson = LessonTime.of(row)
            val start = lesson.start
            val end = lesson.end
            line(out, "BEGIN:VEVENT")
            line(out, "UID:" + UUID.nameUUIDFromBytes(row.id.toByteArray(StandardCharsets.UTF_8)) + "@vistula-checker")
            line(out, "DTSTAMP:" + UTC.format(exportedAt))
            line(out, "DTSTART:" + UTC.format(start))
            line(out, "DTEND:" + UTC.format(end))
            line(out, "SUMMARY:" + escape(row.title))
            line(out, "DESCRIPTION:" + escape(row.value + "\nZapis planu z aplikacji Vistula. Plik nie aktualizuje się automatycznie."))
            for (part in row.value.split(Regex("\\r?\\n"))) {
                if (part.startsWith("Sala ")) {
                    line(out, "LOCATION:" + escape(part))
                    break
                }
            }
            line(out, "END:VEVENT")
        }
        line(out, "END:VCALENDAR")
        return out.toString()
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")

    @JvmStatic
    fun line(out: StringBuilder, value: String) {
        var octets = 0
        var i = 0
        while (i < value.length) {
            val code = value.codePointAt(i)
            val unit = String(Character.toChars(code))
            val length = unit.toByteArray(StandardCharsets.UTF_8).size
            if (octets + length > 75) {
                out.append("\r\n ")
                octets = 1
            }
            out.append(unit)
            octets += length
            i += Character.charCount(code)
        }
        out.append("\r\n")
    }
}

