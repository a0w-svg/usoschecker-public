package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class IcsExporterTest {
    @Test
    fun timestampsUseUtcAcrossSummerAndWinter() {
        val summer = Record(
            "summer", "plan", "Latem", "", "", "2026-07-01", "",
            "2026-07-01 09:00:00", "2026-07-01 10:00:00", Double.NaN, JSONObject()
        )
        val winter = Record(
            "winter", "plan", "Zimą", "", "", "2026-12-01", "",
            "2026-12-01 09:00:00", "2026-12-01 10:00:00", Double.NaN, JSONObject()
        )
        val ics = IcsExporter.export(listOf(summer, winter))
        assertTrue(ics.contains("DTSTART:20260701T070000Z\r\n"))
        assertTrue(ics.contains("DTSTART:20261201T080000Z\r\n"))
        assertTrue(ics.matches(Regex("(?s).*DTSTAMP:\\d{8}T\\d{6}Z\\r\\n.*")))
    }

    @Test
    fun foldsLongUnicodeLinesWithoutLosingTextAndMarksTentativeLessons() {
        val title = "Długie zajęcia 🧪 ".repeat(15)
        val row = Record(
            "long", "plan", title, "Termin orientacyjny", "", "2026-10-17", "",
            "2026-10-17 09:00:00", "2026-10-17 10:00:00", Double.NaN, JSONObject()
        )
        val ics = IcsExporter.export(listOf(row))
        for (line in ics.split("\r\n")) {
            assertTrue(line.toByteArray(StandardCharsets.UTF_8).size <= 75)
        }
        assertTrue(ics.replace("\r\n ", "").contains("SUMMARY:$title\r\n"))
        assertTrue(ics.contains("STATUS:TENTATIVE\r\n"))
    }

    @Test
    fun exportProducesValidIcsFormat() {
        val meta = JSONObject()
            .put("room", "204")
            .put("building", "Budynek Główny")
            .put("classType", "Ćwiczenia")
            .put("lecturers", "Dr Anna Kowalska")
            .put("groupNumber", "1")
            .put("courseId", "MAT-101")

        val r = Record(
            "plan:1", "plan", "Matematyka Dyskretna", "09:00 - 10:30", "", "2026-10-15", "2026Z",
            "2026-10-15 09:00:00", "2026-10-15 10:30:00", 5.0, meta
        )

        val ics = IcsExporter.export(listOf(r), "Mój Plan").replace("\r\n ", "")
        assertNotNull(ics)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.contains("X-WR-CALNAME:Mój Plan\r\n"))
        assertTrue(ics.contains("BEGIN:VEVENT\r\n"))
        assertTrue(ics.contains("SUMMARY:Matematyka Dyskretna (Ćwiczenia)\r\n"))
        assertTrue(ics.contains("DTSTART:20261015T070000Z\r\n"))
        assertTrue(ics.contains("DTEND:20261015T083000Z\r\n"))
        assertTrue(ics.contains("LOCATION:Sala 204\\, Budynek Główny\\, Akademia Finansów i Biznesu Vistula"))
        assertTrue(ics.contains("Prowadzący: Dr Anna Kowalska"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
    }
}
