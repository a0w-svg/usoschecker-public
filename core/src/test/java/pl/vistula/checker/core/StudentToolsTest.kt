package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.Instant

class StudentToolsTest {
    private fun lesson(id: String, day: String, title: String, value: String): Record =
        Record(id, "plan", title, value, "", day)

    @Test
    fun numericSummaryExcludesTextGradesAndCountsExamEntries() {
        val s = GradeSummary.of(
            listOf(
                Record("1", "ocena", "A", "4,5 · termin 1", "", ""),
                Record("2", "ocena", "A", "5.0 · termin 2", "", ""),
                Record("3", "ocena", "B", "ZAL · termin 1", "", ""),
                Record("4", "ocena", "C", "4+ · termin 1", "", ""),
                lesson("5", "2026-10-13", "D", "09:00 – 10:00")
            )
        )
        assertEquals(2, s.numeric)
        assertEquals(2, s.other)
        assertEquals(4.75, s.average, 0.0001)
        assertEquals(4.5, s.min, 0.0)
        assertEquals(5.0, s.max, 0.0)
    }

    @Test
    fun medianHandlesOddAndEvenCounts() {
        val odd = GradeSummary.of(
            listOf(
                Record("1", "ocena", "A", "3", "", ""),
                Record("2", "ocena", "A", "5", "", ""),
                Record("3", "ocena", "A", "4", "", "")
            )
        )
        assertEquals(4.0, odd.median, 0.0)
        val even = GradeSummary.of(
            listOf(
                Record("1", "ocena", "A", "3", "", ""),
                Record("2", "ocena", "A", "5", "", "")
            )
        )
        assertEquals(4.0, even.median, 0.0)
    }

    @Test
    fun requiredAverageReachesTarget() {
        val s = GradeSummary.of(
            listOf(
                Record("1", "ocena", "A", "3", "", ""),
                Record("2", "ocena", "A", "4", "", "")
            )
        )
        assertEquals(5.0, s.requiredAverage(4.0, 1), 0.0001)
        assertEquals(4.5, s.requiredAverage(4.0, 2), 0.0001)
        assertTrue(java.lang.Double.isNaN(s.requiredAverage(4.0, 0)))
    }

    @Test
    fun emptyNumericSummaryDoesNotInventAnAverage() {
        val s = GradeSummary.of(listOf(Record("1", "ocena", "A", "ZAL", "", "")))
        assertEquals(0, s.numeric)
        assertTrue(java.lang.Double.isNaN(s.average))
    }

    @Test
    fun calendarConvertsWarsawSummerAndWinterTimesToUtc() {
        val calendar = CalendarExport.create(
            listOf(
                lesson("summer", "2026-07-01", "A", "09:00 – 10:30\nSala 204"),
                lesson("winter", "2026-12-01", "B", "2026-12-01 09:00 – 10:30")
            ),
            Instant.EPOCH
        )
        assertTrue(calendar.contains("DTSTART:20260701T070000Z\r\n"))
        assertTrue(calendar.contains("DTSTART:20261201T080000Z\r\n"))
        assertTrue(calendar.contains("DTEND:20261201T093000Z\r\n"))
        assertTrue(calendar.contains("LOCATION:Sala 204\r\n"))
        assertEquals(2, calendar.split("BEGIN:VEVENT").size - 1)
    }

    @Test
    fun overnightClassEndsOnFollowingDay() {
        val calendar = CalendarExport.create(listOf(lesson("1", "2026-12-01", "A", "23:00 – 01:00")), Instant.EPOCH)
        assertTrue(calendar.contains("DTSTART:20261201T220000Z"))
        assertTrue(calendar.contains("DTEND:20261202T000000Z"))
    }

    @Test
    fun calendarEscapesPropertyInjectionAndFoldsUtf8ByOctets() {
        val title = "Ą😀".repeat(40) + ",x;\r\nBEGIN:VEVENT"
        val row = lesson("1", "2026-07-01", title, "09:00 – 10:00\nSala A,B")
        val calendar = CalendarExport.create(listOf(row), Instant.EPOCH)
        for (line in calendar.split("\r\n")) {
            assertTrue(line.toByteArray(StandardCharsets.UTF_8).size <= 75)
        }
        val unfolded = calendar.replace("\r\n ", "")
        assertTrue(unfolded.contains("SUMMARY:" + "Ą😀".repeat(40) + "\\,x\\;\\nBEGIN:VEVENT"))
        assertEquals(1, calendar.split("\r\nBEGIN:VEVENT\r\n").size - 1)
        assertFalse(calendar.contains("\uFFFD"))
    }

    @Test
    fun stableMeetingIdProducesStableCalendarUid() {
        val first = CalendarExport.create(listOf(lesson("meeting:99", "2026-07-01", "A", "09:00 – 10:00")), Instant.EPOCH)
        val second = CalendarExport.create(listOf(lesson("meeting:99", "2026-07-02", "A", "11:00 – 12:00")), Instant.now())
        assertEquals(
            first.lines().first { it.startsWith("UID:") },
            second.lines().first { it.startsWith("UID:") }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTimesCannotExportInventedEvents() {
        CalendarExport.create(listOf(lesson("1", "2026-07-01", "A", "Brak godzin")), Instant.EPOCH)
    }

    @Test
    fun ectsWeightedAverageCalculatesAndAvoidsDoubleCountingSessions() {
        val course1Session1 = Record("1", "ocena", "Bazy danych", "3,0 · termin 1", "", "", "2026Z", "", "", 5.0)
        val course1Session2 = Record("2", "ocena", "Bazy danych", "5,0 · termin 2", "", "", "2026Z", "", "", 5.0)
        val course2 = Record("3", "ocena", "Algorytmy", "4,0 · termin 1", "", "", "2026Z", "", "", 3.0)
        val nonEcts = Record("4", "ocena", "Bhp", "ZAL · termin 1", "", "", "2026Z")

        val s = GradeSummary.of(listOf(course1Session1, course1Session2, course2, nonEcts))
        assertEquals(8.0, s.totalEcts, 0.0001)
        assertEquals(4.625, s.ectsAverage, 0.0001)
        assertEquals(4.0, s.average, 0.0001)
        assertEquals(5.0, s.requiredEctsGrade(4.75, 4.0), 0.0001)
    }

    @Test
    fun semesterBreakdownGroupsAndCalculatesPerTerm() {
        val termZ1 = Record("1", "ocena", "A", "5,0 · termin 1", "", "", "2026Z", "", "", 4.0)
        val termZ2 = Record("2", "ocena", "B", "3,0 · termin 1", "", "", "2026Z", "", "", 6.0)
        val termL1 = Record("3", "ocena", "C", "4,5 · termin 1", "", "", "2025L", "", "", 5.0)

        val breakdown = GradeSummary.bySemester(listOf(termZ1, termZ2, termL1))
        assertEquals(2, breakdown.size)
        assertTrue(breakdown.containsKey("2026Z"))
        assertTrue(breakdown.containsKey("2025L"))

        val z = breakdown["2026Z"]!!
        assertEquals(2, z.numeric)
        assertEquals(4.0, z.average, 0.0001)
        assertEquals(3.8, z.ectsAverage, 0.0001)
        assertEquals(10.0, z.totalEcts, 0.0001)

        val l = breakdown["2025L"]!!
        assertEquals(1, l.numeric)
        assertEquals(4.5, l.average, 0.0001)
        assertEquals(4.5, l.ectsAverage, 0.0001)
    }

    @Test
    fun ectsPointsAndUserProfileParsingFromUsosResponse() {
        val ectsJson = JSONObject(
            """
            {
                "2026Z": { "CS101": "5,0", "MATH": 6.5, "NULL_CRS": null },
                "2025L": { "ENG": "2" }
            }
            """.trimIndent()
        )
        val ects = UsosParser.ectsPoints(ectsJson)
        assertEquals(5.0, ects["2026Z\u0000CS101"]!!, 0.0001)
        assertEquals(6.5, ects["2026Z\u0000MATH"]!!, 0.0001)
        assertEquals(2.0, ects["ENG"]!!, 0.0001)
        assertNull(ects["NULL_CRS"])

        val userJson = JSONObject(
            """
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
            """.trimIndent()
        )
        val profile = UsosParser.userProfile(userJson)
        assertEquals("Jan Kowalski", profile.name)
        assertEquals("123456", profile.studentNumber)
        assertEquals("j.kowalski@vistula.edu.pl", profile.email)
        assertEquals("Informatyka", profile.programme)
        assertEquals("Aktywny student", profile.studentStatus)
        assertEquals("Semestr 3", profile.stage)
        assertEquals("2024-10-01", profile.admissionDate)
        assertEquals("https://usosapps.vistula.edu.pl/photo.jpg", profile.photoUrl)
    }
}
