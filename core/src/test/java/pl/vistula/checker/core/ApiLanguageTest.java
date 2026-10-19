package pl.vistula.checker.core;

import org.json.*;
import org.junit.Test;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class ApiLanguageTest {
    private JSONObject names() { return new JSONObject().put("pl", "Analiza").put("en", "Analysis").put("uk", "Аналіз"); }
    @Test public void languageFallbackIgnoresNullBlankAndNonTextValues() {
        assertEquals("Analysis", ApiLanguage.text(names(), "en-GB"));
        assertEquals("Аналіз", ApiLanguage.text(names(), "uk-UA"));
        assertEquals("Analysis", ApiLanguage.text(names().put("uk", "  "), "uk"));
        assertEquals("Analiza", ApiLanguage.text(names().put("uk", JSONObject.NULL).put("en", new JSONObject()), "uk"));
        assertEquals("", ApiLanguage.text(JSONObject.NULL, "en"));
        assertEquals("plain", ApiLanguage.text("plain", "uk"));
    }
    @Test public void parsingScopeRestoresAfterFailureAndDoesNotLeakAcrossThreads() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> en = pool.submit(() -> ApiLanguage.in("en", () -> UsosParser.language(names())));
            Future<String> uk = pool.submit(() -> ApiLanguage.in("uk", () -> UsosParser.language(names())));
            assertEquals("Analysis", en.get()); assertEquals("Аналіз", uk.get());
            try { ApiLanguage.in("uk", () -> { throw new IllegalStateException(); }); fail(); }
            catch (IllegalStateException expected) { assertEquals("Analiza", UsosParser.language(names())); }
        } finally { pool.shutdownNow(); }
    }
    @Test public void documentCachePreservesTranslationsAndStillDropsUnapprovedFields() throws Exception {
        JSONObject raw = new JSONObject().put("id", "doc").put("name", names()).put("status", names())
            .put("type_name", names()).put("activation_token", "private-value");
        JSONObject safe = StudentServices.safeDocuments(new JSONArray().put(raw)).getJSONObject(0);
        assertEquals("Analysis", new StudentServices.Document(safe, "en").name);
        assertEquals("Аналіз", new StudentServices.Document(safe, "uk").name);
        assertFalse(safe.has("activation_token"));
        JSONObject card = StudentServices.safeMobileCard(new JSONObject().put("why_cannot_order", names()));
        assertEquals("Analysis", StudentServices.localized(card.opt("why_cannot_order"), "en"));
    }
    private Snapshot snapshot(String english, String value) {
        Snapshot root = new Snapshot("2026-10-01", "2026-11-01");
        root.add(new Record("g", "ocena", "Analiza", value, "", ""));
        Snapshot en = new Snapshot(root.start, root.end);
        en.add(new Record("g", "ocena", english, value, "", ""));
        root.localized.put("en", en);
        return root;
    }
    @Test public void offlineRoundTripAndDiffUseStableDataAndLocalizedChangeText() throws Exception {
        Snapshot previous = Snapshot.from(snapshot("Analysis", "4").json());
        Snapshot renamed = Snapshot.from(snapshot("Mathematical analysis", "4").json());
        assertEquals("Analysis", previous.forLanguage("en").records.get("g").title);
        assertTrue(Diff.between(previous, renamed, 1, "2026-10-01", "en").isEmpty());
        Snapshot updated = snapshot("Mathematical analysis", "5");
        java.util.List<Change> changes = Diff.between(previous, updated, 1, "2026-10-01", "en");
        assertEquals(1, changes.size()); assertEquals("Mathematical analysis", changes.get(0).title);
        assertEquals("4", changes.get(0).before); assertEquals("5", changes.get(0).after);
    }
    @Test public void translatedLabelsDoNotChangeAttendanceFiltersTentativeLessonsOrPaymentState() throws Exception {
        Record exercise = new Record("g", "ocena", "Analysis", "5", "", "", "2026Z", "", "", Double.NaN,
            new JSONObject().put("classType", "Практичні заняття").put("classTypeCanonical", "Ćwiczenia"));
        assertEquals("Ćwiczenia", AttendanceTracker.type(exercise));
        assertEquals(1, GradeBrowser.select(java.util.List.of(exercise), "2026Z", "exercises", "recent", "").size());
        Record tentative = new Record("t", "plan", "Analysis", "09:00 – 10:00", "", "2026-10-18", "",
            "2026-10-18 09:00:00", "2026-10-18 10:00:00", Double.NaN, new JSONObject().put("frequency", "once"));
        assertTrue(LessonTime.of(tentative).tentative());
        PaymentInfo.Installment paid = new PaymentInfo.Installment("p", "Платіж", 100, "PLN", "2020-01-01", "Сплачено", "");
        assertTrue(paid.isPaid()); assertFalse(paid.isOverdue());
    }
    @Test public void timetableIdsRemainTheSameAcrossLanguages() throws Exception {
        JSONObject raw = new JSONObject().put("type", "custom").put("name", names()).put("start_time", "2026-10-18 09:00:00")
            .put("end_time", "2026-10-18 10:00:00");
        Snapshot pl = new Snapshot("2026-10-01", "2026-11-01"), en = new Snapshot(pl.start, pl.end);
        UsosParser.timetable(pl, new JSONArray().put(raw));
        ApiLanguage.in("en", () -> { UsosParser.timetable(en, new JSONArray().put(raw)); return null; });
        assertEquals(pl.records.keySet(), en.records.keySet());
        assertEquals("Analysis", en.records.values().iterator().next().title);
    }
}
