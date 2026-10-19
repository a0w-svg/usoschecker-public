package pl.vistula.checker

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.ui.Language

@RunWith(AndroidJUnit4::class)
class ApiLanguageRegressionTest {
    @Test
    fun cachedSnapshotHistoryAndProfileFollowLanguageWithoutChangingStoredBaseline() {
        val canonical = Snapshot("2026-10-01", "2026-11-01")
        val polish = Record("g", "ocena", "Analiza", "5", "", "")
        canonical.add(polish)
        val english = Snapshot(canonical.start, canonical.end)
        val translated = Record("g", "ocena", "Analysis", "5", "", "")
        english.add(translated)
        canonical.localized["en"] = english
        val change = Change(null, polish, 1).json().put(
            "localized",
            JSONObject().put("en", Change(null, translated, 1).json())
        )
        val stored = JSONObject()
            .put("snapshot", canonical.json())
            .put("history", JSONArray().put(change))
            .put("programme", "Informatyka")
            .put("profileTranslations", JSONObject().put("en", JSONObject().put("programme", "Computer science")))
        val en = Repository.localizedView(stored, "en")
        assertEquals("Analysis", en.getJSONObject("snapshot").getJSONArray("records").getJSONObject(0).getString("title"))
        assertEquals("Analysis", en.getJSONArray("history").getJSONObject(0).getString("title"))
        assertEquals("Computer science", en.getString("programme"))
        val pl = Repository.localizedView(stored, "pl")
        assertEquals("Analiza", pl.getJSONArray("history").getJSONObject(0).getString("title"))
        assertEquals("Informatyka", pl.getString("programme"))
        assertEquals("Analiza", stored.getJSONObject("snapshot").getJSONArray("records").getJSONObject(0).getString("title"))
    }

    @Test
    fun backgroundContextUsesSelectedLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".qa"))
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java)
            val previous = manager.applicationLocales
            try {
                for (language in arrayOf("en", "uk", "pl")) {
                    manager.applicationLocales = LocaleList.forLanguageTags(language)
                    assertEquals(language, Language.currentLocale(context).language)
                }
            } finally {
                manager.applicationLocales = previous
            }
        } else {
            val prefs = context.getSharedPreferences("api_language", Context.MODE_PRIVATE)
            val previous = prefs.getString("tag", "")
            try {
                prefs.edit().putString("tag", "uk").commit()
                assertEquals("uk", Language.currentLocale(context).language)
            } finally {
                prefs.edit().putString("tag", previous).commit()
            }
        }
    }
}
