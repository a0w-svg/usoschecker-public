package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ApiLanguageTest {
    private fun names(): JSONObject =
        JSONObject().put("pl", "Analiza").put("en", "Analysis").put("uk", "Аналіз")

    @Test
    fun languageFallbackIgnoresNullBlankAndNonTextValues() {
        assertEquals("Analysis", ApiLanguage.text(names(), "en-GB"))
        assertEquals("Аналіз", ApiLanguage.text(names(), "uk-UA"))
        assertEquals("Analysis", ApiLanguage.text(names().put("uk", "  "), "uk"))
        assertEquals("Analiza", ApiLanguage.text(names().put("uk", JSONObject.NULL).put("en", JSONObject()), "uk"))
        assertEquals("", ApiLanguage.text(JSONObject.NULL, "en"))
        assertEquals("plain", ApiLanguage.text("plain", "uk"))
    }

    @Test
    fun parsingScopeRestoresAfterFailureAndDoesNotLeakAcrossThreads() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val en = pool.submit(Callable { ApiLanguage.`in`("en") { UsosParser.language(names()) } })
            val uk = pool.submit(Callable { ApiLanguage.`in`("uk") { UsosParser.language(names()) } })
            assertEquals("Analysis", en.get())
            assertEquals("Аналіз", uk.get())
            try {
                ApiLanguage.`in`<Unit>("uk") { throw IllegalStateException() }
                fail()
            } catch (expected: IllegalStateException) {
                assertEquals("Analiza", UsosParser.language(names()))
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun documentCachePreservesTranslationsAndStillDropsUnapprovedFields() {
        val raw = JSONObject().put("id", "doc").put("name", names()).put("status", names())
            .put("type_name", names()).put("activation_token", "private-value")
        val safe = StudentServices.safeDocuments(JSONArray().put(raw)).getJSONObject(0)
        assertEquals("Analysis", StudentServices.Document(safe, "en").name)
        assertEquals("Аналіз", StudentServices.Document(safe, "uk").name)
        assertFalse(safe.has("activation_token"))
        val card = StudentServices.safeMobileCard(JSONObject().put("why_cannot_order", names()))
        assertEquals("Analysis", StudentServices.localized(card.opt("why_cannot_order"), "en"))
    }

    private fun snapshot(english: String, value: String): Snapshot {
        val root = Snapshot("2026-10-01", "2026-11-01")
        root.add(Record("g", "ocena", "Analiza", value, "", ""))
        val en = Snapshot(root.start, root.end)
        en.add(Record("g", "ocena", english, value, "", ""))
        root.localized["en"] = en
        return root
    }

    @Test
    fun offlineRoundTripAndDiffUseStableDataAndLocalizedChangeText() {
        val previous = Snapshot.from(snapshot("Analysis", "4").json())
        val renamed = Snapshot.from(snapshot("Mathematical analysis", "4").json())
        assertEquals("Analysis", previous.forLanguage("en").records["g"]!!.title)
        assertTrue(Diff.between(previous, renamed, 1, "2026-10-01", "en").isEmpty())
        val updated = snapshot("Mathematical analysis", "5")
        val changes = Diff.between(previous, updated, 1, "2026-10-01", "en")
        assertEquals(1, changes.size)
        assertEquals("Mathematical analysis", changes[0].title)
        assertEquals("4", changes[0].before)
        assertEquals("5", changes[0].after)
    }

    @Test
    fun translatedLabelsDoNotChangeAttendanceFiltersTentativeLessonsOrPaymentState() {
        val exercise = Record(
            "g", "ocena", "Analysis", "5", "", "", "2026Z", "", "", Double.NaN,
            JSONObject().put("classType", "Практичні заняття").put("classTypeCanonical", "Ćwiczenia")
        )
        assertEquals("Ćwiczenia", AttendanceTracker.type(exercise))
        assertEquals(1, GradeBrowser.select(listOf(exercise), "2026Z", "exercises", "recent", "").size)
        val tentative = Record(
            "t", "plan", "Analysis", "09:00 – 10:00", "", "2026-10-18", "",
            "2026-10-18 09:00:00", "2026-10-18 10:00:00", Double.NaN, JSONObject().put("frequency", "once")
        )
        assertTrue(LessonTime.of(tentative).tentative())
        val paid = PaymentInfo.Installment("p", "Платіж", 100.0, "PLN", "2020-01-01", "Сплачено", "")
        assertTrue(paid.isPaid())
        assertFalse(paid.isOverdue())
    }

    @Test
    fun timetableIdsRemainTheSameAcrossLanguages() {
        val raw = JSONObject().put("type", "custom").put("name", names()).put("start_time", "2026-10-18 09:00:00")
            .put("end_time", "2026-10-18 10:00:00")
        val pl = Snapshot("2026-10-01", "2026-11-01")
        val en = Snapshot(pl.start, pl.end)
        UsosParser.timetable(pl, JSONArray().put(raw))
        ApiLanguage.`in`("en") {
            UsosParser.timetable(en, JSONArray().put(raw))
            null
        }
        assertEquals(pl.records.keys, en.records.keys)
        assertEquals("Analysis", en.records.values.iterator().next().title)
    }
}
