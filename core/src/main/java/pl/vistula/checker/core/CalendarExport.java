package pl.vistula.checker.core;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/** RFC 5545 snapshot export; UTC timestamps retain Warsaw daylight-saving offsets. */
public final class CalendarExport {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final Pattern HOURS = Pattern.compile("^(?:\\d{4}-\\d{2}-\\d{2} )?(\\d{2}:\\d{2})\\s*[–-]\\s*(\\d{2}:\\d{2})");
    private CalendarExport() {}
    public static String create(Collection<Record> rows, Instant exportedAt) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR"); line(out, "VERSION:2.0"); line(out, "PRODID:-//Vistula Checker//Plan//PL");
        line(out, "CALSCALE:GREGORIAN");
        for (Record row : rows) {
            if (!row.kind.equals("plan")) continue;
            LessonTime lesson = LessonTime.of(row); ZonedDateTime start = lesson.start, end = lesson.end;
            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + UUID.nameUUIDFromBytes(row.id.getBytes(StandardCharsets.UTF_8)) + "@vistula-checker");
            line(out, "DTSTAMP:" + UTC.format(exportedAt));
            line(out, "DTSTART:" + UTC.format(start)); line(out, "DTEND:" + UTC.format(end));
            line(out, "SUMMARY:" + escape(row.title));
            line(out, "DESCRIPTION:" + escape(row.value + "\nZapis planu z aplikacji Vistula. Plik nie aktualizuje się automatycznie."));
            for (String part : row.value.split("\\r?\\n")) if (part.startsWith("Sala ")) { line(out, "LOCATION:" + escape(part)); break; }
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n")
            .replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,");
    }
    private static void line(StringBuilder out, String value) {
        int octets = 0;
        for (int i = 0; i < value.length();) {
            int code = value.codePointAt(i); String unit = new String(Character.toChars(code));
            int length = unit.getBytes(StandardCharsets.UTF_8).length;
            if (octets + length > 75) { out.append("\r\n "); octets = 1; }
            out.append(unit); octets += length; i += Character.charCount(code);
        }
        out.append("\r\n");
    }
}