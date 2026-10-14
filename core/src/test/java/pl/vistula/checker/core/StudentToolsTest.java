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
    @Test public void medianHandlesOddAndEvenCounts() {
        GradeSummary odd = GradeSummary.of(List.of(
            new Record("1", "ocena", "A", "3", "", ""), new Record("2", "ocena", "A", "5", "", ""), new Record("3", "ocena", "A", "4", "", "")));
        assertEquals(4, odd.median, 0);
        GradeSummary even = GradeSummary.of(List.of(
            new Record("1", "ocena", "A", "3", "", ""), new Record("2", "ocena", "A", "5", "", "")));
        assertEquals(4, even.median, 0);
    }
    @Test public void requiredAverageReachesTarget() {
        GradeSummary s = GradeSummary.of(List.of(
            new Record("1", "ocena", "A", "3", "", ""), new Record("2", "ocena", "A", "4", "", "")));
        assertEquals(5, s.requiredAverage(4, 1), 0.0001);
        assertEquals(4.5, s.requiredAverage(4, 2), 0.0001);
        assertTrue(Double.isNaN(s.requiredAverage(4, 0)));
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
    @Test public void ectsWeightedAverageCalculatesAndAvoidsDoubleCountingSessions() {
        Record course1Session1 = new Record("1", "ocena", "Bazy danych", "3,0 · termin 1", "", "", "2026Z", "", "", 5.0);
        Record course1Session2 = new Record("2", "ocena", "Bazy danych", "5,0 · termin 2", "", "", "2026Z", "", "", 5.0);
        Record course2 = new Record("3", "ocena", "Algorytmy", "4,0 · termin 1", "", "", "2026Z", "", "", 3.0);
        Record nonEcts = new Record("4", "ocena", "Bhp", "ZAL · termin 1", "", "", "2026Z");

        GradeSummary s = GradeSummary.of(List.of(course1Session1, course1Session2, course2, nonEcts));
        // Highest grade for Bazy danych is 5.0 (5 ECTS), Algorytmy is 4.0 (3 ECTS)
        // Weighted sum = 5.0 * 5 + 4.0 * 3 = 25 + 12 = 37
        // Total ECTS = 5 + 3 = 8
        // Weighted average = 37 / 8 = 4.625
        assertEquals(8.0, s.totalEcts, 0.0001);
        assertEquals(4.625, s.ectsAverage, 0.0001);
        // Simple average counts all 3 numeric entries: (3 + 5 + 4) / 3 = 4.0
        assertEquals(4.0, s.average, 0.0001);

        // Required ECTS grade to reach 4.75 with an upcoming 4 ECTS course:
        // (4.75 * (8 + 4) - 37) / 4 = (57 - 37) / 4 = 5.0
        assertEquals(5.0, s.requiredEctsGrade(4.75, 4.0), 0.0001);
    }
    @Test public void semesterBreakdownGroupsAndCalculatesPerTerm() {
        Record termZ1 = new Record("1", "ocena", "A", "5,0 · termin 1", "", "", "2026Z", "", "", 4.0);
        Record termZ2 = new Record("2", "ocena", "B", "3,0 · termin 1", "", "", "2026Z", "", "", 6.0);
        Record termL1 = new Record("3", "ocena", "C", "4,5 · termin 1", "", "", "2025L", "", "", 5.0);

        java.util.Map<String, GradeSummary> breakdown = GradeSummary.bySemester(List.of(termZ1, termZ2, termL1));
        assertEquals(2, breakdown.size());
        assertTrue(breakdown.containsKey("2026Z"));
        assertTrue(breakdown.containsKey("2025L"));

        GradeSummary z = breakdown.get("2026Z");
        assertEquals(2, z.numeric);
        assertEquals(4.0, z.average, 0.0001);
        // ECTS: (5*4 + 3*6) / 10 = (20 + 18) / 10 = 3.8
        assertEquals(3.8, z.ectsAverage, 0.0001);
        assertEquals(10.0, z.totalEcts, 0.0001);

        GradeSummary l = breakdown.get("2025L");
        assertEquals(1, l.numeric);
        assertEquals(4.5, l.average, 0.0001);
        assertEquals(4.5, l.ectsAverage, 0.0001);
    }
    @Test public void ectsPointsAndUserProfileParsingFromUsosResponse() throws Exception {
        org.json.JSONObject ectsJson = new org.json.JSONObject("""
            {
                "2026Z": { "CS101": "5,0", "MATH": 6.5, "NULL_CRS": null },
                "2025L": { "ENG": "2" }
            }
            """);
        java.util.Map<String, Double> ects = UsosParser.ectsPoints(ectsJson);
        assertEquals(5.0, ects.get("2026Z\0CS101"), 0.0001);
        assertEquals(6.5, ects.get("2026Z\0MATH"), 0.0001);
        assertEquals(2.0, ects.get("ENG"), 0.0001);
        assertNull(ects.get("NULL_CRS"));

        org.json.JSONObject userJson = new org.json.JSONObject("""
            {
                "first_name": "Jan",
                "last_name": "Kowalski",
                "student_number": "123456",
                "email": "j.kowalski@vistula.edu.pl",
                "student_status": 2,
                "student_programmes": [
                    {
                        "programme": { "name": { "pl": "Informatyka", "en": "Computer Science" } },
                        "status": "active",
                        "admission_date": "2024-10-01",
                        "stages": [ { "stage_id": "Semestr 3" } ]
                    }
                ],
                "photo_urls": { "200x200": "https://usosapps.vistula.edu.pl/photo.jpg" }
            }
            """);
        UsosParser.UserProfile profile = UsosParser.userProfile(userJson);
        assertEquals("Jan Kowalski", profile.name);
        assertEquals("123456", profile.studentNumber);
        assertEquals("j.kowalski@vistula.edu.pl", profile.email);
        assertEquals("Informatyka", profile.programme);
        assertEquals("Aktywny student", profile.studentStatus);
        assertEquals("Semestr 3", profile.stage);
        assertEquals("2024-10-01", profile.admissionDate);
        assertEquals("https://usosapps.vistula.edu.pl/photo.jpg", profile.photoUrl);
    }
}