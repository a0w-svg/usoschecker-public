package pl.vistula.checker;

import org.junit.Test;
import static org.junit.Assert.*;

import pl.vistula.checker.ui.HistoryScreenController;
import pl.vistula.checker.ui.PaymentsScreenController;
import pl.vistula.checker.ui.UiHelper;

public class UiFormattersTest {
    @Test public void lessonRoomPrefersStructuredRoomAndReadsLegacyLabels() throws Exception {
        pl.vistula.checker.core.Record legacy = new pl.vistula.checker.core.Record("room", "plan", "Course",
            "09:00 – 10:00\nSala A-204 · Budynek B\nProwadzący: Jan Nowak", "", "2026-10-18");
        assertEquals("A-204", pl.vistula.checker.ui.ScheduleScreenController.lessonRoom(legacy));
        legacy.metadata.put("room", "315");
        assertEquals("315", pl.vistula.checker.ui.ScheduleScreenController.lessonRoom(legacy));
        assertEquals("", pl.vistula.checker.ui.ScheduleScreenController.lessonRoom(
            new pl.vistula.checker.core.Record("online", "plan", "Course", "09:00 – 10:00\nOnline", "", "2026-10-18")));
    }

    @Test public void scheduleCardsKeepEssentialsWithoutRepeatedTimesOrTechnicalFields() throws Exception {
        org.json.JSONObject meta = new org.json.JSONObject().put("room", "201").put("building", "Budynek A")
            .put("classType", "Ćwiczenia").put("lecturers", "Jan Nowak").put("unitId", "private-id");
        pl.vistula.checker.core.Record record = new pl.vistula.checker.core.Record("lesson", "plan", "Finanse",
            "09:00 – 10:30\nĆwiczenia\nSala 201 · Budynek A\nProwadzący: Jan Nowak\nTermin orientacyjny",
            "", "2026-10-18", "", "", "", Double.NaN, meta);
        assertEquals("Ćwiczenia\nSala 201\nProwadzący: Jan Nowak\nTermin orientacyjny",
            pl.vistula.checker.ui.ScheduleScreenController.lessonSummary(pl.vistula.checker.core.LessonTime.of(record)));
        assertTrue(record.value.contains("Budynek A")); // Details and exports retain the original data.
    }

    @Test public void scheduleCardsKeepLegacyAndPartiallyStructuredDescriptions() throws Exception {
        for (org.json.JSONObject meta : new org.json.JSONObject[]{new org.json.JSONObject(), new org.json.JSONObject().put("room", "201")}) {
            pl.vistula.checker.core.Record record = new pl.vistula.checker.core.Record("lesson", "plan", "Finanse",
                "2026-10-18 09:00 – 10:30\nĆwiczenia\nSala 201\nProwadzący: Jan Nowak", "", "2026-10-18", "",
                "", "", Double.NaN, meta);
            assertEquals("Ćwiczenia\nSala 201\nProwadzący: Jan Nowak",
                pl.vistula.checker.ui.ScheduleScreenController.lessonSummary(pl.vistula.checker.core.LessonTime.of(record)));
        }
    }

    @Test
    public void testFormatIbanStandardPolish() {
        String raw = "12345678901234567890123456";
        String formatted = PaymentsScreenController.formatIban(raw);
        assertEquals("12 3456 7890 1234 5678 9012 3456", formatted);
    }

    @Test
    public void testFormatIbanWithCountryCode() {
        String raw = "PL12345678901234567890123456";
        String formatted = PaymentsScreenController.formatIban(raw);
        assertEquals("PL 12 3456 7890 1234 5678 9012 3456", formatted);
    }

    @Test
    public void testFormatIbanShortOrNull() {
        assertEquals("", PaymentsScreenController.formatIban(null));
        assertEquals("123", PaymentsScreenController.formatIban("123"));
    }

    @Test
    public void testFormatFullDateIso() {
        String iso = "2026-10-15T14:30:00";
        String formatted = HistoryScreenController.formatFullDate(iso);
        assertTrue(formatted.contains("2026"));
        assertTrue(formatted.contains("14:30"));
        assertTrue(formatted.contains("październik"));
    }

    @Test
    public void testFormatFullDateSimple() {
        String date = "2026-10-15";
        String formatted = HistoryScreenController.formatFullDate(date);
        assertTrue(formatted.contains("2026"));
        assertTrue(formatted.contains("październik"));
    }

    @Test
    public void testUiHelperInitials() {
        assertEquals("JN", UiHelper.initials("Jan Nowak"));
        assertEquals("AN", UiHelper.initials("Anna"));
        assertEquals("US", UiHelper.initials(""));
        assertEquals("US", UiHelper.initials(null));
    }
}
