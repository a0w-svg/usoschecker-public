package pl.vistula.checker.core;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class IcsExporterTest {
    @Test
    public void exportProducesValidIcsFormat() {
        JSONObject meta = new JSONObject()
            .put("room", "204")
            .put("building", "Budynek Główny")
            .put("classType", "Ćwiczenia")
            .put("lecturers", "Dr Anna Kowalska")
            .put("groupNumber", "1")
            .put("courseId", "MAT-101");

        Record r = new Record("plan:1", "plan", "Matematyka Dyskretna", "09:00 - 10:30", "", "2026-10-15", "2026Z", "2026-10-15 09:00:00", "2026-10-15 10:30:00", 5.0, meta);

        String ics = IcsExporter.export(List.of(r), "Mój Plan");
        assertNotNull(ics);
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.contains("X-WR-CALNAME:Mój Plan\r\n"));
        assertTrue(ics.contains("BEGIN:VEVENT\r\n"));
        assertTrue(ics.contains("SUMMARY:Matematyka Dyskretna (Ćwiczenia)\r\n"));
        assertTrue(ics.contains("DTSTART;TZID=Europe/Warsaw:20261015T090000\r\n"));
        assertTrue(ics.contains("DTEND;TZID=Europe/Warsaw:20261015T103000\r\n"));
        assertTrue(ics.contains("LOCATION:Sala 204\\, Budynek Główny\\, Akademia Finansów i Biznesu Vistula"));
        assertTrue(ics.contains("Prowadzący: Dr Anna Kowalska"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
    }
}
