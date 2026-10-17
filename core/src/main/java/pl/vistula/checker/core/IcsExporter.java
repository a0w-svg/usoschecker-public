package pl.vistula.checker.core;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class IcsExporter {
    private static final DateTimeFormatter ICS_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private IcsExporter() {}

    public static String export(Collection<Record> records) {
        return export(records, "Plan zajęć Vistula");
    }

    public static String export(Collection<Record> records, String calendarTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Vistula Checker//USOS Timetable//PL\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:").append(escape(calendarTitle)).append("\r\n");
        sb.append("X-WR-TIMEZONE:Europe/Warsaw\r\n");

        List<LessonTime> lessons = LessonTime.sorted(records);
        String dtstamp = ICS_DATE_TIME.format(Instant.now());

        for (LessonTime lt : lessons) {
            Record r = lt.record;
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(escape(r.id)).append("@vistula.checker\r\n");
            sb.append("DTSTAMP:").append(dtstamp).append("\r\n");
            // UTC avoids relying on a missing VTIMEZONE definition in importing calendars.
            sb.append("DTSTART:").append(ICS_DATE_TIME.format(lt.start)).append("\r\n");
            sb.append("DTEND:").append(ICS_DATE_TIME.format(lt.end)).append("\r\n");

            String classType = r.optMeta("classType");
            String title = r.title + (!classType.isEmpty() ? " (" + classType + ")" : "");
            sb.append("SUMMARY:").append(escape(title)).append("\r\n");

            String room = r.optMeta("room");
            String building = r.optMeta("building");
            String location = (!room.isEmpty() ? "Sala " + room : "") +
                (!building.isEmpty() ? (!room.isEmpty() ? ", " : "") + building : "") +
                ", Akademia Finansów i Biznesu Vistula, ul. Stokłosy 3, Warszawa";
            sb.append("LOCATION:").append(escape(location)).append("\r\n");

            StringBuilder desc = new StringBuilder();
            String lecturers = r.optMeta("lecturers");
            if (!lecturers.isEmpty()) desc.append("Prowadzący: ").append(lecturers).append("\n");
            String group = r.optMeta("groupNumber");
            if (!group.isEmpty()) desc.append("Grupa: ").append(group).append("\n");
            String courseId = r.optMeta("courseId");
            if (!courseId.isEmpty()) desc.append("Kod przedmiotu: ").append(courseId).append("\n");
            if (!r.term.isEmpty()) desc.append("Semestr: ").append(r.term).append("\n");
            if (desc.length() == 0) desc.append(r.value);

            sb.append("DESCRIPTION:").append(escape(desc.toString())).append("\r\n");
            sb.append(lt.tentative() ? "STATUS:TENTATIVE\r\n" : "STATUS:CONFIRMED\r\n");
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        StringBuilder folded = new StringBuilder();
        for (String line : sb.toString().split("\r\n")) CalendarExport.line(folded, line);
        return folded.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }
}
