package pl.vistula.checker

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Collections
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.UsosParser
import pl.vistula.checker.ui.UiHelper

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @JvmField
    @Rule
    val rule = ActivityScenarioRule<MainActivity>(
        Intent(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )

    @Before
    fun resetNavigation() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        if (!target.packageName.endsWith(".qa")) {
            Assume.assumeTrue(
                "Use -PisolatedTests when the app already has local data.",
                Repository(target).read().length() == 0
            )
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { rule.activity.navigate(0) }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun startDemo() {
        click("Więcej")
        click(activity().getString(R.string.tool_account_title))
        click(activity().getString(R.string.btn_start_demo))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { rule.activity.selectGradeSemester("") }
    }

    private fun activity(): Activity = rule.activity

    private fun localized(text: String?): String? {
        if (text == null) return null
        val a = rule.activity
        return when (text) {
            "Więcej" -> a.getString(R.string.nav_more)
            "Start" -> a.getString(R.string.nav_home)
            "Oceny" -> a.getString(R.string.nav_grades)
            "Plan" -> a.getString(R.string.nav_plan)
            "Zmiany" -> a.getString(R.string.nav_history)
            "Dzisiaj" -> a.getString(R.string.schedule_btn_today)
            "Symuluj zmiany" -> a.getString(R.string.btn_simulate_changes)
            "Zakończ tryb demonstracyjny" -> a.getString(R.string.btn_end_demo)
            "Połącz z USOS" -> a.getString(R.string.btn_login_browser)
            "Parametry żądania" -> a.getString(R.string.api_params_heading)
            "← Wróć do katalogu" -> a.getString(R.string.api_btn_back_catalog)
            "Dodaj do ulubionych" -> a.getString(R.string.api_btn_fav_add)
            "Usuń z ulubionych" -> a.getString(R.string.api_btn_fav_remove)
            "Brak pasujących metod. Spróbuj innej nazwy lub opisu." -> a.getString(R.string.api_count_none)
            "Pokaż kolejne metody" -> a.getString(R.string.api_btn_show_more)
            "Brak pasujących danych" -> a.getString(R.string.grades_no_match)
            "Filtry i sortowanie" -> a.getString(R.string.grades_filter_expand)
            "Ukryj filtry i sortowanie" -> a.getString(R.string.grades_filter_collapse)
            "Liczbowe" -> a.getString(R.string.grades_chip_numeric)
            "Pozostałe" -> a.getString(R.string.grades_chip_other)
            "Wyczyść filtry ocen" -> a.getString(R.string.grades_filter_reset)
            "Podsumowanie ocen" -> a.getString(R.string.grades_summary_title)
            "Ostatnia zmiana" -> a.getString(R.string.grades_sort_recent)
            "Brak pasujących zmian" -> a.getString(R.string.history_no_match_title)
            "Najbliższe zajęcia" -> a.getString(R.string.classes_upcoming)
            "Dodaj zadanie" -> a.getString(R.string.btn_add_task)
            "Oznacz jako ukończone" -> a.getString(R.string.tasks_btn_mark_done)
            "Edytuj zadanie" -> a.getString(R.string.tasks_btn_edit)
            "Usuń zadanie" -> a.getString(R.string.tasks_btn_delete)
            "Cofnij" -> a.getString(R.string.undo)
            "Najbliższe terminy" -> a.getString(R.string.deadlines_upcoming)
            "Otwórz zadania" -> a.getString(R.string.home_open_tasks)
            "Na dzisiaj" -> a.getString(R.string.tasks_chip_today)
            "Ukończone" -> a.getString(R.string.tasks_chip_done)
            "Otwarte" -> a.getString(R.string.tasks_chip_open)
            "Brak pasujących zadań" -> a.getString(R.string.tasks_no_match_title)
            "Wysoki priorytet" -> a.getString(R.string.tasks_priority_high)
            "Spokojna głowa" -> a.getString(R.string.tasks_empty_title)
            else -> {
                if (text.startsWith("Wyświetlono ") && text.endsWith(" pasujących metod.")) {
                    val parts = text.split(" ")
                    try {
                        val count = parts[1].toInt()
                        val total = parts[3].toInt()
                        return a.getString(R.string.api_count_match, count, total)
                    } catch (ignored: Exception) {}
                }
                if (text.startsWith("Do zrobienia: ") && text.contains(" · ukończone: ")) {
                    val parts = text.split(" · ")
                    try {
                        val open = parts[0].replace("Do zrobienia: ", "").trim().toInt()
                        val done = parts[1].replace("ukończone: ", "").trim().toInt()
                        return a.getString(R.string.tasks_summary, open, done)
                    } catch (ignored: Exception) {}
                }
                text
            }
        }
    }

    private fun find(root: View, text: String, button: Boolean): View? {
        val loc = localized(text) ?: text
        if (root is TextView && (!button || root is Button)) {
            val label = root.text.toString()
            if (label == text || label == loc || (button && (label.matches(Regex(Pattern.quote(text) + " \\(\\d+\\)")) || label.matches(Regex(Pattern.quote(loc) + " \\(\\d+\\)"))))) {
                return root
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val result = find(root.getChildAt(i), text, button)
                if (result != null) return result
            }
        }
        return null
    }

    private fun click(text: String) {
        val locText = localized(text) ?: text
        if (ToolMenuTestHelper.select(activity(), locText)) return
        if (TasksMenuTestHelper.select(activity(), locText)) return
        if (GradesMenuTestHelper.select(activity(), locText)) return
        if (PlanMenuTestHelper.select(activity(), text) || PlanMenuTestHelper.select(activity(), locText)) return
        val until = System.currentTimeMillis() + 10000
        val done = booleanArrayOf(false)
        while (!done[0] && System.currentTimeMillis() < until) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val tabs = arrayOf("Start", "Oceny", "Plan", "Zmiany", "Więcej")
                val labelIds = intArrayOf(R.string.nav_home, R.string.nav_grades, R.string.nav_plan, R.string.nav_history, R.string.nav_more)
                val ids = intArrayOf(R.id.nav_item_home, R.id.nav_item_grades, R.id.nav_item_plan, R.id.nav_item_history, R.id.nav_item_more)
                val nav = findNavigation(activity().window.decorView)
                for (i in tabs.indices) {
                    if ((text == tabs[i] || text == activity().getString(labelIds[i]) || text == localized(tabs[i])) && nav != null) {
                        val item = (nav as BottomNavigationView).menu.findItem(ids[i])
                        if (item.isEnabled) {
                            nav.findViewById<View>(ids[i]).performClick()
                            done[0] = true
                        }
                        return@runOnMainSync
                    }
                }
                var v = find(activity().window.decorView, text, true)
                if (v == null) {
                    v = find(activity().window.decorView, text, false)
                    while (v != null && !v.isClickable) {
                        v = v.parent as? View
                    }
                }
                if (v != null && v.isEnabled) {
                    v.performClick()
                    done[0] = true
                }
            }
            if (!done[0]) Thread.sleep(100)
        }
        assertTrue("Button not available: $text", done[0])
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun has(text: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertNotNull("Missing text: $text", find(activity().window.decorView, text, false))
        }
    }

    private fun hasContaining(text: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue("Missing text: $text", containsText(activity().window.decorView, text))
        }
    }

    @Test
    fun demoSimulatesChangesAndRemainsSeparateFromLiveAccount() {
        click("Więcej")
        click(activity().getString(R.string.tool_account_title))
        click(activity().getString(R.string.btn_start_demo))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { rule.activity.selectGradeSemester("") }
        click("Więcej")
        click(activity().getString(R.string.tool_account_title))
        click("Symuluj zmiany")
        has("Zmiany")
        has("Podstawy finansów")
        has("Statystyka opisowa")
        click("Oceny")
        has("Podstawy finansów")
        click("Plan")
        click(activity().getString(R.string.schedule_mode_agenda))
        has("Statystyka opisowa")
        click("Więcej")
        click(activity().getString(R.string.tool_account_title))
        click("Zakończ tryb demonstracyjny")
        has("Połącz z USOS")
        assertTrue(Repository(activity()).read().optString("token").isEmpty())
    }

    @Test
    fun everyCatalogEntryHasMatchingOfflineMetadata() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val index = JSONArray(read(c, "api/index.json"))
        assertEquals(571, index.length())
        for (i in 0 until index.length()) {
            val name = index.getJSONObject(i).getString("name")
            val meta = JSONObject(read(c, "api/" + name.replace("/", "__") + ".json"))
            assertEquals(name, meta.getString("name"))
            assertNotNull(meta.getJSONArray("arguments"))
            assertNotNull(meta.getJSONObject("auth_options"))
        }
    }

    @Test
    fun apiSearchOpensOfflineMethodAndRequiredParameters() {
        click("Więcej")
        click(activity().getString(R.string.tool_api_title))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = activity().window.decorView as ViewGroup
            val input = firstInput(root)
            assertNotNull(input)
            input!!.setText("grades/latest")
        }
        click("grades/latest\nGet user's latest grades")
        val until = System.currentTimeMillis() + 5000
        val found = booleanArrayOf(false)
        while (!found[0] && System.currentTimeMillis() < until) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                found[0] = find(activity().window.decorView, "Parametry żądania", false) != null
            }
            if (!found[0]) Thread.sleep(100)
        }
        assertTrue(found[0])
        has(activity().getString(R.string.api_execute_btn))
        has(activity().getString(R.string.api_btn_online_docs))
        click("← Wróć do katalogu")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals("grades/latest", firstInput(activity().window.decorView as ViewGroup)!!.text.toString())
        }
    }

    @Test
    fun apiFavoritesPersistAndFilterTheCatalog() {
        val preferences = activity().getSharedPreferences("api_catalog", Context.MODE_PRIVATE)
        val previous = HashSet(preferences.getStringSet("favorites", Collections.emptySet())!!)
        try {
            click("Więcej")
            click(activity().getString(R.string.tool_api_title))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                firstInput(activity().window.decorView as ViewGroup)!!.setText("grades/latest")
            }
            click("grades/latest\nGet user's latest grades")
            if (previous.contains("services/grades/latest")) click("Usuń z ulubionych")
            click("Dodaj do ulubionych")
            has("Usuń z ulubionych")
            click("← Wróć do katalogu")
            click(activity().getString(R.string.api_fav_filter))
            has("grades/latest\nGet user's latest grades")
            click("grades/latest\nGet user's latest grades")
            click("Usuń z ulubionych")
            click("← Wróć do katalogu")
            has("Brak pasujących metod. Spróbuj innej nazwy lub opisu.")
            assertFalse(preferences.getStringSet("favorites", Collections.emptySet())!!.contains("services/grades/latest"))
        } finally {
            preferences.edit().putStringSet("favorites", previous).commit()
        }
    }

    @Test
    fun catalogCanShowMoreThanFirstPage() {
        click("Więcej")
        click(activity().getString(R.string.tool_api_title))
        has("Wyświetlono 60 z 571 pasujących metod.")
        click("Pokaż kolejne metody")
        has("Wyświetlono 120 z 571 pasujących metod.")
        for (i in 0 until 8) click("Pokaż kolejne metody")
        has("Wyświetlono 571 z 571 pasujących metod.")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertNull(find(activity().window.decorView, "Pokaż kolejne metody", true))
        }
    }

    private fun assertGradeCounts(numeric: Int, other: Int) {
        GradesMenuTestHelper.select(activity(), activity().getString(R.string.grades_summary_title))
        Espresso.onView(ViewMatchers.withText(activity().getString(R.string.grades_card_counts, numeric, other)))
            .inRoot(RootMatchers.isDialog())
            .check(ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
        Espresso.onView(ViewMatchers.withText(R.string.btn_close))
            .inRoot(RootMatchers.isDialog())
            .perform(ViewActions.click())
    }

    private fun searchFor(query: String) {
        PlanMenuTestHelper.revealSearch(activity())
        GradesMenuTestHelper.revealSearch(activity())
        TasksMenuTestHelper.revealSearch(activity())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val input = firstInput(activity().window.decorView as ViewGroup)
            assertNotNull(input)
            input!!.setText(query)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun studentDataSearchAndTimetableDateFilters() {
        startDemo()
        click("Oceny")
        searchFor("FINANSÓW")
        has("Podstawy finansów")
        searchFor("nieistniejący przedmiot")
        has("Brak pasujących danych")
        searchFor("")
        click("Plan")
        click("Dzisiaj")
        has("Ekonomia")
        hasContaining(activity().getString(R.string.classes_ongoing))
        click(activity().getString(R.string.schedule_mode_agenda))
        has("Statystyka opisowa")
        searchFor("204")
        has("Statystyka opisowa")
        searchFor("315")
        has(activity().getString(R.string.schedule_no_search_results))
        click("Start")
        click("Oceny")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals("", firstInput(activity().window.decorView as ViewGroup)!!.text.toString())
        }
    }

    @Test
    fun gradeFiltersDetailsAndTimetableSearchStayIndependent() {
        startDemo()
        click("Oceny")
        click("Liczbowe")
        assertGradeCounts(4, 0)
        searchFor("finansów")
        capturePreview("grades-refined-preview.png")
        click("Podstawy finansów")
        Espresso.onView(ViewMatchers.withText(R.string.grades_btn_add_task))
            .inRoot(RootMatchers.isDialog())
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.task_input_title))
            .check { view, missing ->
                assertNull(missing)
                assertEquals("Podstawy finansów", (view as EditText).text.toString())
            }
        Espresso.onView(ViewMatchers.withText(R.string.btn_cancel))
            .inRoot(RootMatchers.isDialog())
            .perform(ViewActions.click())
        click("Plan")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals("", firstInput(activity().window.decorView as ViewGroup)!!.text.toString())
        }
        click(activity().getString(R.string.schedule_mode_week))
        has("Ekonomia")
        capturePreview("timetable-week-preview.png")
        searchFor("Kowalska")
        click("Oceny")
        has("Podstawy finansów")
        assertGradeCounts(2, 0)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals("finansów", firstInput(activity().window.decorView as ViewGroup)!!.text.toString())
        }
        click("Pozostałe")
        has("Brak pasujących danych")
        click("Wyczyść filtry ocen")
        has("Język angielski")
    }

    @Test
    fun friendlySearchAndDailyNavigationRespectVisibleResults() {
        startDemo()
        click("Oceny")
        searchFor("finansow")
        has("Podstawy finansów")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val decor = activity().window.decorView
            assertNull(find(decor, "Podsumowanie ocen", false))
            assertNotNull(decor.findViewById(R.id.grades_more))
            firstInput(decor as ViewGroup)!!.requestFocus()
        }
        Espresso.onView(ViewMatchers.withContentDescription(activity().getString(R.string.btn_clear_search)))
            .perform(ViewActions.click())
        has("Język angielski")
        capturePreview("friendly-grades.png")
        click("Plan")
        click(activity().getString(R.string.schedule_btn_today))
        searchFor("Kowalska")
        has(activity().getString(R.string.schedule_no_search_results))
        click(activity().getString(R.string.schedule_btn_next_class) + " (" + UiHelper.shortDate(LocalDate.now(UsosClient.ZONE).plusDays(1)) + ")")
        has("Statystyka opisowa")
        capturePreview("friendly-plan.png")
        searchFor("nieistniejace")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val decor = activity().window.decorView
            assertNull(find(decor, activity().getString(R.string.schedule_btn_next_class), true))
        }
        PlanMenuTestHelper.assertExportDisabled(activity())
    }

    @Test
    fun weeklyViewExportsOnlyTheSelectedWeek() {
        val exportDirectory = File(activity().filesDir, "test-exports")
        exportDirectory.mkdirs()
        val output = File(exportDirectory, "week-test.ics")
        Files.write(output.toPath(), ByteArray(0))
        Intents.init()
        try {
            val result = Intent().setData(FileProvider.getUriForFile(activity(), activity().packageName + ".debugexports", output))
            Intents.intending(IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, result))
            startDemo()
            click("Plan")
            click(activity().getString(R.string.schedule_mode_week))
            click(activity().getString(R.string.schedule_export_ics))
            val until = System.currentTimeMillis() + 10000
            while (output.length() == 0L && System.currentTimeMillis() < until) Thread.sleep(100)
            val calendar = String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8)
            val expected = if (LocalDate.now(UsosClient.ZONE).dayOfWeek == DayOfWeek.SUNDAY) 1 else 2
            assertEquals(expected, calendar.split("BEGIN:VEVENT").size - 1)
            assertTrue(calendar.contains("SUMMARY:Ekonomia"))
            assertEquals(expected == 2, calendar.contains("SUMMARY:Statystyka opisowa"))
        } finally {
            Intents.release()
            output.delete()
        }
    }

    @Test
    fun gradeFiltersSurviveActivityRecreation() {
        startDemo()
        click("Oceny")
        click("Liczbowe")
        click("Ostatnia zmiana")
        searchFor("finansów")
        val previous = activity()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { previous.recreate() }
        val until = System.currentTimeMillis() + 10000
        val resumed = arrayOfNulls<Activity>(1)
        while (resumed[0] == null && System.currentTimeMillis() < until) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                for (candidate in ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (candidate is MainActivity && candidate !== previous && firstInput(candidate.window.decorView as ViewGroup) != null) {
                        resumed[0] = candidate
                    }
                }
            }
            if (resumed[0] == null) Thread.sleep(100)
        }
        assertNotNull(resumed[0])
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val decor = resumed[0]!!.window.decorView
            assertEquals("finansów", firstInput(decor as ViewGroup)!!.text.toString())
            val active = decor.findViewById<TextView>(R.id.grades_active_filters)
            assertNotNull(active)
            assertTrue(active.isSelected)
            assertTrue(active.text.toString().contains(activity().getString(R.string.grades_chip_numeric)))
            assertTrue(active.text.toString().contains(activity().getString(R.string.grades_sort_recent)))
            assertNotNull(find(decor, "Podstawy finansów", false))
        }
    }

    @Test
    fun historySearchMatchesBeforeAndAfterValues() {
        click("Więcej")
        click(activity().getString(R.string.tool_account_title))
        click(activity().getString(R.string.btn_start_demo))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { rule.activity.selectGradeSemester("") }
        click("Więcej")
        click(activity().getString(R.string.tool_account_title))
        click("Symuluj zmiany")
        searchFor("315")
        has("Statystyka opisowa")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertNull(find(activity().window.decorView, "Podstawy finansów", false))
        }
        searchFor("finansów")
        has("Podstawy finansów")
        searchFor("nieistniejąca zmiana")
        has("Brak pasujących zmian")
    }

    @Test
    fun materialNavigationSeparatesGradesPlanAndTools() {
        startDemo()
        has("Ekonomia")
        hasContaining(activity().getString(R.string.classes_ongoing))
        click("Oceny")
        has(activity().getString(R.string.nav_grades))
        has("Podstawy finansów")
        click("Plan")
        has("Plan")
        has("Ekonomia")
        hasContaining(activity().getString(R.string.classes_ongoing))
        click("Zmiany")
        has("Zmiany")
        click("Więcej")
        click(activity().getString(R.string.tool_tasks_title))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertNotNull(activity().findViewById(R.id.tasks_add))
        }
    }

    @Test
    fun demoTasksCanBeAddedAndCompletedWithoutWritingLiveData() {
        val before = Repository(activity()).read().optJSONArray("tasks")?.toString() ?: "[]"
        startDemo()
        click("Więcej")
        click(activity().getString(R.string.tool_tasks_title))
        click("Dodaj zadanie")
        Espresso.onView(ViewMatchers.withId(R.id.task_input_title))
            .perform(ViewActions.replaceText("Przygotować projekt"), ViewActions.closeSoftKeyboard())
        Espresso.onView(ViewMatchers.withText(R.string.btn_save))
            .perform(ViewActions.click())
        has("Przygotować projekt")
        click("Oznacz jako ukończone")
        has("Do zrobienia: 0 · ukończone: 1")
        val after = Repository(activity()).read().optJSONArray("tasks")?.toString() ?: "[]"
        assertEquals(before, after)
    }

    @Test
    fun semesterFilterAndSummaryTrackVisibleGrades() {
        startDemo()
        click("Oceny")
        assertGradeCounts(4, 0)
        assertGradeCounts(1, 1)
        GradesMenuTestHelper.selectSemester(activity(), 1)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        has("Podstawy finansów")
        assertGradeCounts(4, 0)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertNull(find(activity().window.decorView, "Język angielski", false))
        }
        GradesMenuTestHelper.selectSemester(activity(), 2)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        has("Język angielski")
        assertGradeCounts(1, 1)
    }

    @Test
    fun taskEditingFiltersAndDashboardKeepTheSameTask() {
        startDemo()
        click("Więcej")
        click(activity().getString(R.string.tool_tasks_title))
        click("Dodaj zadanie")
        Espresso.onView(ViewMatchers.withId(R.id.task_input_title))
            .perform(ViewActions.replaceText("Projekt"), ViewActions.closeSoftKeyboard())
        Espresso.onView(ViewMatchers.withText(R.string.btn_save))
            .perform(ViewActions.click())
        click("Edytuj zadanie")
        Espresso.onView(ViewMatchers.withId(R.id.task_input_title))
            .perform(ViewActions.replaceText("Projekt poprawiony"), ViewActions.closeSoftKeyboard())
        Espresso.onView(ViewMatchers.withText(R.string.btn_save))
            .perform(ViewActions.click())
        has("Projekt poprawiony")
        has("Do zrobienia: 1 · ukończone: 0")
        click("Start")
        has("Najbliższe terminy")
        has("Projekt poprawiony")
        click("Otwórz zadania")
        click("Na dzisiaj")
        has("Projekt poprawiony")
        click("Oznacz jako ukończone")
        has("Brak pasujących zadań")
        click("Ukończone")
        has("Projekt poprawiony")
        click("Otwarte")
        has("Brak pasujących zadań")
    }

    @Test
    fun taskNotesPrioritySearchAndDeletionUndoStayInDemo() {
        val original = Repository(activity()).read().toString()
        startDemo()
        click("Więcej")
        click(activity().getString(R.string.tool_tasks_title))
        click("Dodaj zadanie")
        Espresso.onView(ViewMatchers.withId(R.id.task_input_title))
            .perform(ViewActions.replaceText("Przygotuj prezentację"), ViewActions.closeSoftKeyboard())
        Espresso.onView(ViewMatchers.withId(R.id.task_input_notes))
            .perform(ViewActions.replaceText("Materiały w bibliotece"), ViewActions.closeSoftKeyboard())
        Espresso.onView(ViewMatchers.withId(R.id.task_input_priority))
            .check { view, missing ->
                assertNull(missing)
                (view as Spinner).setSelection(2)
            }
        Espresso.onView(ViewMatchers.withText(R.string.btn_save)).perform(ViewActions.click())
        hasContaining(activity().getString(R.string.tasks_priority_high))
        capturePreview("task-priority-preview.png")
        searchFor("bibliotece")
        has("Przygotuj prezentację")
        click("Edytuj zadanie")
        Espresso.onView(ViewMatchers.withId(R.id.task_input_notes))
            .check { view, missing ->
                assertNull(missing)
                assertEquals("Materiały w bibliotece", (view as EditText).text.toString())
            }
        Espresso.onView(ViewMatchers.withId(R.id.task_input_priority))
            .check { view, missing ->
                assertNull(missing)
                assertEquals(2, (view as Spinner).selectedItemPosition)
            }
        capturePreview("task-editor-preview.png")
        Espresso.onView(ViewMatchers.withText(R.string.btn_save)).perform(ViewActions.click())
        click("Usuń zadanie")
        Espresso.onView(ViewMatchers.withText(R.string.tasks_btn_delete))
            .inRoot(RootMatchers.isDialog())
            .perform(ViewActions.click())
        has("Spokojna głowa")
        click("Cofnij")
        has("Przygotuj prezentację")
        hasContaining(activity().getString(R.string.tasks_priority_high))
        assertEquals(original, Repository(activity()).read().toString())
    }

    @Test
    fun androidJsonNullGradesDoNotFailParsing() {
        val snapshot = Snapshot("2026-10-12", "2026-12-07")
        val response = JSONObject("{\"2026Z\":{\"EMPTY\":{\"course_grades\":null,\"course_units_grades\":null},\"FIN\":{\"course_grades\":[{\"1\":{\"exam_id\":1,\"exam_session_number\":1,\"value_symbol\":\"5\",\"date_modified\":null,\"comment\":null}}],\"course_units_grades\":{}}}}")
        UsosParser.grades(snapshot, response, emptyMap())
        assertEquals(1, snapshot.records.size)
        assertEquals("", snapshot.records["grade:1:1"]!!.sourceTime)
        assertFalse(snapshot.records["grade:1:1"]!!.value.contains("null"))
    }

    private fun capturePreview(filename: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(400, 3000)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        try {
            FileOutputStream(File(activity().getExternalFilesDir(null), filename)).use { output ->
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            screenshot.recycle()
        }
    }

    @Test
    fun timetableExportWritesVisibleEventsToSelectedDocument() {
        val exportDirectory = File(activity().filesDir, "test-exports")
        exportDirectory.mkdirs()
        val output = File(exportDirectory, "calendar-test.ics")
        Files.write(output.toPath(), ByteArray(0))
        Intents.init()
        try {
            val result = Intent().setData(FileProvider.getUriForFile(activity(), activity().packageName + ".debugexports", output))
            Intents.intending(IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, result))
            startDemo()
            click("Plan")
            click(activity().getString(R.string.schedule_mode_agenda))
            searchFor("204")
            click(activity().getString(R.string.schedule_export_ics))
            val until = System.currentTimeMillis() + 10000
            while (output.length() == 0L && System.currentTimeMillis() < until) Thread.sleep(100)
            val calendar = String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8)
            assertTrue(calendar.contains("BEGIN:VCALENDAR"))
            assertTrue(calendar.contains("SUMMARY:Statystyka opisowa"))
            assertTrue(calendar.contains("LOCATION:" + activity().getString(R.string.notification_class_room, "204")))
            assertTrue(calendar.replace("\r\n ", "").contains(activity().getString(R.string.notification_diff_3) + " Anna Kowalska"))
            assertEquals(8, calendar.split("BEGIN:VEVENT").size - 1)
        } finally {
            Intents.release()
            output.delete()
        }
    }

    @Test
    fun lecturerDirectoryBatchesUniqueIdsAcrossWeeks() {
        val lecturers = JSONArray()
        for (i in 1..61) lecturers.put(i)
        val week = JSONArray().put(JSONObject().put("lecturer_ids", lecturers))
        val sizes = ArrayList<Int>()
        val names = UsosClient.resolveLecturers(listOf(week, week)) { ids ->
            sizes.add(ids.size)
            val result = JSONObject()
            for (id in ids) result.put(id, JSONObject().put("first_name", "Anna").put("last_name", "Kowalska $id"))
            result
        }
        assertEquals(listOf(50, 11), sizes)
        assertEquals(61, names.size)
        assertEquals("Anna Kowalska 61", names["61"])
        assertTrue(
            UsosClient.resolveLecturers(listOf(JSONArray())) {
                fail("Unexpected lookup")
                JSONObject()
            }.isEmpty()
        )
    }

    @Test(expected = IOException::class)
    fun incompleteLecturerDirectoryRejectsDownload() {
        val week = JSONArray().put(JSONObject().put("lecturer_ids", JSONArray().put(1)))
        UsosClient.resolveLecturers(listOf(week)) { JSONObject() }
    }

    @Test
    fun conciseDashboardKeepsUpcomingLessonAndLecturerIsSearchableInTimetable() {
        startDemo()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(containsText(activity().window.decorView, "Statystyka opisowa"))
        }
        click("Plan")
        click(activity().getString(R.string.schedule_mode_agenda))
        searchFor("Kowalska")
        has("Statystyka opisowa")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(containsText(activity().window.decorView, activity().getString(R.string.notification_diff_3) + " Anna Kowalska"))
        }
    }

    private fun containsText(root: View, part: String): Boolean {
        val loc = localized(part) ?: part
        if (root is TextView && (root.text.toString().contains(part) || root.text.toString().contains(loc))) return true
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                if (containsText(root.getChildAt(i), part)) return true
            }
        }
        return false
    }

    @Test
    fun calendarShowsCurrentLectureAndSavedAgendaRange() {
        startDemo()
        hasContaining(activity().getString(R.string.classes_ongoing))
        has("Ekonomia")
        click("Plan")
        hasContaining(activity().getString(R.string.classes_ongoing))
        click(activity().getString(R.string.schedule_mode_agenda))
        has("Statystyka opisowa")
        click(activity().getString(R.string.schedule_info))
        val range = Demo.state(false).getJSONObject("snapshot").getString("start") + " – " +
            Demo.state(false).getJSONObject("snapshot").getString("end")
        Espresso.onView(ViewMatchers.withSubstring(range))
            .inRoot(RootMatchers.isDialog())
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withText(R.string.btn_close))
            .inRoot(RootMatchers.isDialog())
            .perform(ViewActions.click())
        click("Dzisiaj")
        has("Ekonomia")
        hasContaining(activity().getString(R.string.classes_ongoing))
    }

    @Test
    fun calendarBlocksFollowDurationAndSeparateOverlaps() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val day = LocalDate.of(2026, 10, 13)
            val rows = listOf(
                Record("a", "plan", "A", "09:00 – 10:00", "", "2026-10-13"),
                Record("b", "plan", "B", "09:30 – 10:30", "", "2026-10-13"),
                Record("c", "plan", "C", "10:00 – 11:00", "", "2026-10-13")
            )
            val grid = DayTimelineView(activity(), day, LessonTime.sorted(rows)) {}
            val height = grid.layoutParams.height
            grid.measure(
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            grid.layout(0, 0, 600, height)
            val a = grid.getChildAt(1)
            val b = grid.getChildAt(2)
            val c = grid.getChildAt(3)
            assertTrue(a.height < height / 4)
            assertTrue(a.width < 600 / 2)
            assertEquals(a.left, c.left)
            assertNotEquals(a.left, b.left)
            assertTrue(a.bottom <= c.top)
        }
    }

    private fun findNavigation(root: View): View? {
        if (root is BottomNavigationView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val result = findNavigation(root.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    private fun firstInput(root: ViewGroup): EditText? {
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            if (v is EditText) return v
            if (v is ViewGroup) {
                val result = firstInput(v)
                if (result != null) return result
            }
        }
        return null
    }

    @Test
    fun encryptedVaultRoundTripsAndDoesNotWriteTokensAsPlaintext() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val r = Repository(c)
        val marker = "sensitive_token_test_6d8b1c"
        r.update { s -> s.put("testMarker", marker) }
        assertEquals(marker, r.read().getString("testMarker"))
        val bytes = Files.readAllBytes(File(c.noBackupFilesDir, "state.bin").toPath())
        assertFalse(String(bytes, StandardCharsets.ISO_8859_1).contains(marker))
        r.update { s -> s.remove("testMarker") }
    }

    @Test
    fun failedSyncPreservesSnapshotAndHistoryAndRetryDoesNotDuplicateChanges() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val r = Repository(c)
        r.disconnect()
        val day = LocalDate.now(UsosClient.ZONE).toString()
        val initial = Snapshot(day, LocalDate.now(UsosClient.ZONE).plusDays(28).toString())
        val updated = Snapshot(initial.start, initial.end)
        initial.add(Record("grade:test", "ocena", "Test", "4", "$day 10:00:00", ""))
        updated.add(Record("grade:test", "ocena", "Test", "5", "$day 10:30:00", ""))
        try {
            r.update { s -> s.put("token", "test-only-token").put("enabled", true) }
            assertTrue(r.sync(true) { initial }.isEmpty())
            assertEquals(1, r.sync(true) { updated }.size)
            val before = r.read()
            try {
                r.sync(true) { throw IOException("simulated unavailable source") }
                fail("Expected failure")
            } catch (expected: IOException) {}
            val after = r.read()
            assertEquals(before.getJSONObject("snapshot").toString(), after.getJSONObject("snapshot").toString())
            assertEquals(before.getJSONArray("history").toString(), after.getJSONArray("history").toString())
            assertEquals(before.getLong("successAt"), after.getLong("successAt"))
            assertFalse(after.optString("error").isEmpty())
            assertTrue(r.sync(true) { updated }.isEmpty())
            assertEquals(1, r.read().getJSONArray("history").length())
        } finally {
            r.disconnect()
        }
    }

    private fun read(c: Context, path: String): String {
        c.assets.open(path).use { input ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                var n: Int
                while (input.read(buffer).also { n = it } != -1) {
                    output.write(buffer, 0, n)
                }
                return output.toString("UTF-8")
            }
        }
    }
}
