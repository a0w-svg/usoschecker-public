package pl.vistula.checker.core;

import java.time.*;
import java.util.*;
import java.util.regex.*;

public final class LessonTime {
    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final Pattern HOURS = Pattern.compile("^(?:\\d{4}-\\d{2}-\\d{2} )?(\\d{2}:\\d{2})\\s*[–-]\\s*(\\d{2}:\\d{2})");
    public final Record record;
    public final ZonedDateTime start, end;
    private LessonTime(Record record, LocalDateTime start, LocalDateTime end) {
        this.record = record; this.start = start.atZone(ZONE); this.end = end.atZone(ZONE);
        if (!this.end.isAfter(this.start)) throw new IllegalArgumentException("Nieprawidłowy czas zajęć.");
    }
    public static LessonTime of(Record r) {
        if (!r.startTime.isEmpty() && !r.endTime.isEmpty())
            return new LessonTime(r, LocalDateTime.parse(r.startTime, UsosParser.TIME), LocalDateTime.parse(r.endTime, UsosParser.TIME));
        Matcher match = HOURS.matcher(r.value);
        if (!match.find()) throw new IllegalArgumentException("Brak godzin zajęć: " + r.title);
        LocalDate day = LocalDate.parse(r.date);
        LocalTime start = LocalTime.parse(match.group(1)), end = LocalTime.parse(match.group(2));
        return new LessonTime(r, day.atTime(start), (end.isBefore(start) ? day.plusDays(1) : day).atTime(end));
    }
    public boolean current(Instant now) { return !now.isBefore(start.toInstant()) && now.isBefore(end.toInstant()); }
    public boolean contains(Instant now) { return current(now); }
    public boolean tentative() { return record.value.contains("Termin orientacyjny"); }
    public boolean intersects(LocalDate date) {
        return start.toInstant().isBefore(date.plusDays(1).atStartOfDay(ZONE).toInstant())
            && end.toInstant().isAfter(date.atStartOfDay(ZONE).toInstant());
    }
    public static List<LessonTime> sorted(Collection<Record> rows) {
        List<LessonTime> result = new ArrayList<>();
        for (Record r : rows) if (r.kind.equals("plan")) result.add(of(r));
        result.sort(Comparator.comparing((LessonTime t) -> t.start.toInstant()).thenComparing(t -> t.record.id));
        return result;
    }
    public static List<LessonTime> current(Collection<Record> rows, Instant now) {
        List<LessonTime> result = new ArrayList<>();
        for (LessonTime t : sorted(rows)) if (!t.tentative() && t.current(now)) result.add(t);
        return result;
    }
}