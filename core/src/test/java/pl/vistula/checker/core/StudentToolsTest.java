package pl.vistula.checker.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class StudentToolsTest {
    private Record lesson(String id, String day, String title, String value) {
        return new Record(id, "plan", title, value, "", day);
    }
    @Test public void numericSummaryExcludesTextGradesAndCountsExamEntries() {
        GradeSummary s = GradeSummary.of(List.of(
            new Record("1", "ocena", "A", "4,5 · termin 1", "", ""),
            new Record("2", "ocena", "A", "5.0 · termin 2", "", ""),
            new Record("3", "ocena", "B", "ZAL · termin 1", "", ""),
            new Record("4", "ocena", "C", "4+ · termin 1", "", ""),
            lesson("5", "2026-10-13", "D", "09:00 – 10:00")));
        assertEquals(2, s.numeric); assertEquals(2, s.other);
        assertEquals(4.75, s.average, 0.0001); assertEquals(4.5, s.min, 0); assertEquals(5, s.max, 0);
    }
    @Test public void emptyNumericSummaryDoesNotInventAnAverage() {
        GradeSummary s = GradeSummary.of(List.of(new Record("1", "ocena", "A", "ZAL", "", "")));
        assertEquals(0, s.numeric); assertTrue(Double.isNaN(s.average));
    }
    @Test public void calendarConvertsWarsawSummerAndWinterTimesToUtc() {
        String calendar = CalendarExport.create(List.of(
            lesson("summer", "2026-07-01", "A", "09:00 – 10:30\nSala 204"),
            lesson("winter", "2026-12-01", "B", "2026-12-01 09:00 – 10:30")), Instant.EPOCH);
        assertTrue(calendar.contains("DTSTART:20260701T070000Z\r\n"));
        assertTrue(calendar.contains("DTSTART:20261201T080000Z\r\n"));
        assertTrue(calendar.contains("DTEND:20261201T093000Z\r\n"));
        assertTrue(calendar.contains("LOCATION:Sala 204\r\n"));
        assertEquals(2, calendar.split("BEGIN:VEVENT", -1).length - 1);
    }
    @Test public void overnightClassEndsOnFollowingDay() {
        String calendar = CalendarExport.create(List.of(lesson("1", "2026-12-01", "A", "23:00 – 01:00")), Instant.EPOCH);
        assertTrue(calendar.contains("DTSTART:20261201T220000Z"));
        assertTrue(calendar.contains("DTEND:20261202T000000Z"));
    }
    @Test public void calendarEscapesPropertyInjectionAndFoldsUtf8ByOctets() {
        String title = "Ą😀".repeat(40) + ",x;\r\nBEGIN:VEVENT";
        Record row = lesson("1", "2026-07-01", title, "09:00 – 10:00\nSala A,B");
        String calendar = CalendarExport.create(List.of(row), Instant.EPOCH);
        for (String line : calendar.split("\r\n")) assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75);
        String unfolded = calendar.replace("\r\n ", "");
        assertTrue(unfolded.contains("SUMMARY:" + "Ą😀".repeat(40) + "\\,x\\;\\nBEGIN:VEVENT"));
        assertEquals(1, calendar.split("\r\nBEGIN:VEVENT\r\n", -1).length - 1);
        assertFalse(calendar.contains("\uFFFD"));
    }
    @Test public void stableMeetingIdProducesStableCalendarUid() {
        String first = CalendarExport.create(List.of(lesson("meeting:99", "2026-07-01", "A", "09:00 – 10:00")), Instant.EPOCH);
        String second = CalendarExport.create(List.of(lesson("meeting:99", "2026-07-02", "A", "11:00 – 12:00")), Instant.now());
        assertEquals(first.lines().filter(l -> l.startsWith("UID:")).findFirst().get(), second.lines().filter(l -> l.startsWith("UID:")).findFirst().get());
    }
    @Test(expected = IllegalArgumentException.class) public void invalidTimesCannotExportInventedEvents() {
        CalendarExport.create(List.of(lesson("1", "2026-07-01", "A", "Brak godzin")), Instant.EPOCH);
    }
}