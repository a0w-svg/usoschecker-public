package pl.vistula.checker;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.ui.Language;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ApiLanguageRegressionTest {
    @Test public void cachedSnapshotHistoryAndProfileFollowLanguageWithoutChangingStoredBaseline() throws Exception {
        Snapshot canonical = new Snapshot("2026-10-01", "2026-11-01");
        Record polish = new Record("g", "ocena", "Analiza", "5", "", "");
        canonical.add(polish);
        Snapshot english = new Snapshot(canonical.start, canonical.end);
        Record translated = new Record("g", "ocena", "Analysis", "5", "", "");
        english.add(translated); canonical.localized.put("en", english);
        JSONObject change = new Change(null, polish, 1).json().put("localized",
            new JSONObject().put("en", new Change(null, translated, 1).json()));
        JSONObject stored = new JSONObject().put("snapshot", canonical.json()).put("history", new JSONArray().put(change))
            .put("programme", "Informatyka").put("profileTranslations", new JSONObject().put("en", new JSONObject().put("programme", "Computer science")));
        JSONObject en = Repository.localizedView(stored, "en");
        assertEquals("Analysis", en.getJSONObject("snapshot").getJSONArray("records").getJSONObject(0).getString("title"));
        assertEquals("Analysis", en.getJSONArray("history").getJSONObject(0).getString("title"));
        assertEquals("Computer science", en.getString("programme"));
        JSONObject pl = Repository.localizedView(stored, "pl");
        assertEquals("Analiza", pl.getJSONArray("history").getJSONObject(0).getString("title"));
        assertEquals("Informatyka", pl.getString("programme"));
        assertEquals("Analiza", stored.getJSONObject("snapshot").getJSONArray("records").getJSONObject(0).getString("title"));
    }
    @Test public void backgroundContextUsesSelectedLanguage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getPackageName().endsWith(".qa"));
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.app.LocaleManager manager = context.getSystemService(android.app.LocaleManager.class);
            android.os.LocaleList previous = manager.getApplicationLocales();
            try {
                for (String language : new String[]{"en", "uk", "pl"}) {
                    manager.setApplicationLocales(android.os.LocaleList.forLanguageTags(language));
                    assertEquals(language, Language.currentLocale(context).getLanguage());
                }
            } finally { manager.setApplicationLocales(previous); }
        } else {
            android.content.SharedPreferences prefs = context.getSharedPreferences("api_language", Context.MODE_PRIVATE);
            String previous = prefs.getString("tag", "");
            try {
                prefs.edit().putString("tag", "uk").commit();
                assertEquals("uk", Language.currentLocale(context).getLanguage());
            } finally { prefs.edit().putString("tag", previous).commit(); }
        }
    }
}
