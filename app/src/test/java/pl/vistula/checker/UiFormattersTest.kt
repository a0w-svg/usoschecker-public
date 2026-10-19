package pl.vistula.checker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.Record
import pl.vistula.checker.ui.HistoryScreenController
import pl.vistula.checker.ui.PaymentsScreenController
import pl.vistula.checker.ui.ScheduleScreenController
import pl.vistula.checker.ui.UiHelper
import java.util.Locale

class UiFormattersTest {
    @Test
    fun lessonRoomPrefersStructuredRoomAndReadsLegacyLabels() {
        val legacy = Record(
            "room", "plan", "Course",
            "09:00 – 10:00\nSala A-204 · Budynek B\nProwadzący: Jan Nowak", "", "2026-10-18"
        )
        assertEquals("A-204", ScheduleScreenController.lessonRoom(legacy))
        legacy.metadata.put("room", "315")
        assertEquals("315", ScheduleScreenController.lessonRoom(legacy))
        assertEquals(
            "",
            ScheduleScreenController.lessonRoom(
                Record("online", "plan", "Course", "09:00 – 10:00\nOnline", "", "2026-10-18")
            )
        )
    }

    @Test
    fun scheduleCardsKeepEssentialsWithoutRepeatedTimesOrTechnicalFields() {
        val meta = JSONObject().put("room", "201").put("building", "Budynek A")
            .put("classType", "Ćwiczenia").put("lecturers", "Jan Nowak").put("unitId", "private-id")
        val record = Record(
            "lesson", "plan", "Finanse",
            "09:00 – 10:30\nĆwiczenia\nSala 201 · Budynek A\nProwadzący: Jan Nowak\nTermin orientacyjny",
            "", "2026-10-18", "", "", "", Double.NaN, meta
        )
        assertEquals(
            "Ćwiczenia\nSala 201\nProwadzący: Jan Nowak\nTermin orientacyjny",
            ScheduleScreenController.lessonSummary(LessonTime.of(record))
        )
        assertTrue(record.value.contains("Budynek A")) // Details and exports retain the original data.
    }

    @Test
    fun scheduleCardsKeepLegacyAndPartiallyStructuredDescriptions() {
        for (meta in arrayOf(JSONObject(), JSONObject().put("room", "201"))) {
            val record = Record(
                "lesson", "plan", "Finanse",
                "2026-10-18 09:00 – 10:30\nĆwiczenia\nSala 201\nProwadzący: Jan Nowak", "", "2026-10-18", "",
                "", "", Double.NaN, meta
            )
            assertEquals(
                "Ćwiczenia\nSala 201\nProwadzący: Jan Nowak",
                ScheduleScreenController.lessonSummary(LessonTime.of(record))
            )
        }
    }

    @Test
    fun testFormatIbanStandardPolish() {
        val raw = "12345678901234567890123456"
        val formatted = PaymentsScreenController.formatIban(raw)
        assertEquals("12 3456 7890 1234 5678 9012 3456", formatted)
    }

    @Test
    fun testFormatIbanWithCountryCode() {
        val raw = "PL12345678901234567890123456"
        val formatted = PaymentsScreenController.formatIban(raw)
        assertEquals("PL 12 3456 7890 1234 5678 9012 3456", formatted)
    }

    @Test
    fun testFormatIbanShortOrNull() {
        assertEquals("", PaymentsScreenController.formatIban(null))
        assertEquals("123", PaymentsScreenController.formatIban("123"))
    }

    @Test
    fun testFormatFullDateIso() {
        val iso = "2026-10-15T14:30:00"
        val formatted = HistoryScreenController.formatFullDate(Locale.forLanguageTag("pl"), iso)
        assertTrue(formatted.contains("2026"))
        assertTrue(formatted.contains("14:30"))
        assertTrue(formatted.contains("październik"))
    }

    @Test
    fun testFormatFullDateSimple() {
        val date = "2026-10-15"
        val formatted = HistoryScreenController.formatFullDate(Locale.forLanguageTag("pl"), date)
        assertTrue(formatted.contains("2026"))
        assertTrue(formatted.contains("październik"))
    }

    @Test
    fun datesWithoutContextFollowASupportedSystemLanguage() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertTrue(HistoryScreenController.formatFullDate("2026-10-15").contains("October"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun testFormatFullDateLocales() {
        val iso = "2026-10-15T14:30:00"
        val formattedEn = HistoryScreenController.formatFullDate(Locale.ENGLISH, iso)
        assertTrue(formattedEn.contains("2026"))
        assertTrue(formattedEn.contains("14:30"))
        assertTrue(formattedEn.lowercase(Locale.ENGLISH).contains("october"))

        val formattedUk = HistoryScreenController.formatFullDate(Locale.forLanguageTag("uk"), iso)
        assertTrue(formattedUk.contains("2026"))
        assertTrue(formattedUk.contains("14:30"))
        assertTrue(formattedUk.lowercase(Locale.forLanguageTag("uk")).contains("жовт"))
    }

    @Test
    fun testUiHelperInitials() {
        assertEquals("JN", UiHelper.initials("Jan Nowak"))
        assertEquals("AN", UiHelper.initials("Anna"))
        assertEquals("US", UiHelper.initials(""))
        assertEquals("US", UiHelper.initials(null))
    }
}
