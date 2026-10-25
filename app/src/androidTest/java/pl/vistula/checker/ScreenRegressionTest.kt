package pl.vistula.checker

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenRegressionTest {
    @JvmField
    @Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    private fun main(action: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun find(root: View, text: String): View? {
        if (root is TextView && root.text.toString() == text) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val result = find(root.getChildAt(i), text)
                if (result != null) return result
            }
        }
        return null
    }

    private fun input(root: View): EditText? {
        if (root is EditText) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val result = input(root.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    private fun decor(): View = rule.activity.window.decorView

    private fun has(text: String) {
        main { assertNotNull(text, find(decor(), text)) }
    }

    private fun absent(text: String) {
        main { assertNull(text, find(decor(), text)) }
    }

    private fun search(query: String) {
        GradesMenuTestHelper.revealSearch(rule.activity)
        TasksMenuTestHelper.revealSearch(rule.activity)
        PlanMenuTestHelper.revealSearch(rule.activity)
        main {
            val inp = input(decor())
            assertNotNull(inp)
            inp!!.setText(query)
        }
    }

    private fun click(text: String) {
        if (ToolMenuTestHelper.select(rule.activity, text)) return
        if (TasksMenuTestHelper.select(rule.activity, text)) return
        if (PlanMenuTestHelper.select(rule.activity, text)) return
        main {
            var view = find(decor(), text)
            assertNotNull(text, view)
            while (view != null && !view.isClickable) {
                view = view.parent as? View
            }
            assertNotNull("Clickable row for $text", view)
            assertTrue(view!!.performClick())
        }
    }

    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(400, 3000)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        val file = File(rule.activity.getExternalFilesDir(null), "$name.png")
        try {
            FileOutputStream(file).use { output ->
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } finally {
            screenshot.recycle()
        }
        if (name == "grades-improved" || name == "tasks-improved") {
            val targetName = if (name == "grades-improved") "grades" else "tasks"
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("cp ${file.absolutePath} /data/local/tmp/usos-$targetName-ux.png").use { result ->
                    FileInputStream(result.fileDescriptor).use { copied ->
                        while (copied.read() != -1) {
                            /* Wait until the shell copy finishes. */
                        }
                    }
                }
        }
    }

    @Before
    fun demo() {
        assertTrue("Regression tests must use isolated app data", rule.activity.packageName.endsWith(".qa"))
        val deadline = System.currentTimeMillis() + 10000
        val ready = booleanArrayOf(false)
        while (!ready[0] && System.currentTimeMillis() < deadline) {
            main {
                rule.activity.navigate(4)
                ready[0] = find(decor(), rule.activity.getString(R.string.btn_start_demo)) != null
            }
            if (!ready[0]) Thread.sleep(100)
        }
        assertTrue(ready[0])
        main { rule.activity.setDemo(true); rule.activity.selectGradeSemester("") }
    }

    @Test
    fun gradesSearchUpdatesImmediatelyAndClears() {
        main { rule.activity.navigate(2) }
        capture("grades-improved")
        search("finansów")
        has("Podstawy finansów")
        absent("Programowanie obiektowe")
        search("no-such-grade")
        has(rule.activity.getString(R.string.grades_no_match))
        absent("Podstawy finansów")
        search("")
        has("Programowanie obiektowe")
    }

    @Test
    fun historySearchAndActionFilterUpdateImmediately() {
        main { rule.activity.sync() }
        capture("changes-improved")
        Espresso.onView(ViewMatchers.withId(R.id.history_more))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.history_search_action))
            .inRoot(RootMatchers.isPlatformPopup())
            .perform(ViewActions.click())
        Espresso.closeSoftKeyboard()
        search("315")
        has("Statystyka opisowa")
        absent("Podstawy finansów")
        search("no-such-change")
        has(rule.activity.getString(R.string.history_no_match_title))
        search("")
        Espresso.onView(ViewMatchers.withId(R.id.history_more))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(rule.activity.getString(R.string.history_chip_modified, 2)))
            .inRoot(RootMatchers.isPlatformPopup())
            .perform(ViewActions.click())
        has("Podstawy finansów")
        has("Statystyka opisowa")
    }

    @Test
    fun timetableWeekSearchPersistsAcrossNavigation() {
        // The demo statistics class is tomorrow, which belongs to the next week on Sundays.
        main {
            rule.activity.navigate(5)
            rule.activity.setCalendarDate(LocalDate.now(UsosClient.ZONE).plusDays(1).toString())
            rule.activity.render()
        }
        click(rule.activity.getString(R.string.schedule_mode_week))
        capture("timetable-improved")
        search("Kowalska")
        has("Statystyka opisowa")
        absent("Programowanie obiektowe")
        main {
            rule.activity.navigate(2)
            rule.activity.navigate(5)
        }
        main { assertEquals("Kowalska", input(decor())!!.text.toString()) }
        search("no-such-lesson")
        absent("Statystyka opisowa")
        PlanMenuTestHelper.assertExportDisabled(rule.activity)
    }

    @Test
    fun weeklyTasksExcludeOverdueAndCompletedEntries() {
        val today = LocalDate.now(UsosClient.ZONE)
        val due = JSONObject()
            .put("id", "week-due")
            .put("title", "Due this week")
            .put("due", today.plusDays(6).toString())
            .put("done", false)
        val overdue = JSONObject()
            .put("id", "week-late")
            .put("title", "Overdue task")
            .put("due", today.minusDays(1).toString())
            .put("done", false)
        main {
            rule.activity.savePersonalTask(due)
            rule.activity.savePersonalTask(overdue)
            rule.activity.navigate(0)
        }
        capture("dashboard-improved")
        main { rule.activity.navigate(7) }
        capture("tasks-improved")
        main { rule.activity.openTasks("week") }
        has("Due this week")
        absent("Overdue task")
        main { rule.activity.openTasks("overdue") }
        has("Overdue task")
        absent("Due this week")
    }

    @Test
    fun calendarExportOpensSystemDocumentPicker() {
        main { rule.activity.navigate(5) }
        click(rule.activity.getString(R.string.schedule_mode_agenda))
        Intents.init()
        try {
            Intents.intending(IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            click(rule.activity.getString(R.string.schedule_export_ics))
            Intents.intended(IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
        } finally {
            Intents.release()
        }
    }

    private fun awaitText(text: String) {
        val until = System.currentTimeMillis() + 10000
        val found = booleanArrayOf(false)
        while (!found[0] && System.currentTimeMillis() < until) {
            main { found[0] = find(decor(), text) != null }
            if (!found[0]) Thread.sleep(100)
        }
        assertTrue("Missing $text", found[0])
    }

    private fun dialogHas(text: String) {
        Espresso.onView(ViewMatchers.isRoot()).inRoot(RootMatchers.isDialog()).check { view, _ ->
            assertNotNull(text, find(view, text))
        }
    }

    @Test
    fun cardDemoSupportsActivationWithoutPhysicalCardAndDoesNotUseLiveAccount() {
        main { rule.activity.navigate(13) }
        has(rule.activity.getString(R.string.mcard_ready))
        has(rule.activity.getString(R.string.cards_activation))
        absent(rule.activity.getString(R.string.cards_physical_title))
        capture("student-cards")
        main { rule.activity.mobileCardAction(false, "demo-order") }
        has(rule.activity.getString(R.string.mcard_ready))
        assertFalse(Repository(rule.activity).read().toString().contains("demo-order"))
    }

    @Test
    fun documentSearchAndReadableDetailsUseTheSelectedDocument() {
        main { rule.activity.navigate(14) }
        has("Zaświadczenie o studiowaniu")
        has("Decyzja stypendialna")
        capture("student-documents")
        search("zaswiadczenie")
        has("Zaświadczenie o studiowaniu")
        absent("Decyzja stypendialna")
        click(rule.activity.getString(R.string.documents_details))
        Espresso.onView(ViewMatchers.withId(R.id.student_document_details)).inRoot(RootMatchers.isDialog())
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withText(R.string.btn_close)).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
        main { rule.activity.navigate(14) }
        main { assertEquals("zaswiadczenie", input(decor())!!.text.toString()) }
    }

    @Test
    fun apiModuleShortcutsAndPolishSearchReachEveryModule() {
        main { rule.activity.openApiModule("cards") }
        has("cards/mcard2\nGet information about student card and related mcard (mLegitymacje)")
        main { rule.activity.openApiModule("") }
        search("legitymacje")
        has("cards/mcard2\nGet information about student card and related mcard (mLegitymacje)")
        absent("adm_documents/document\nGet information on administration document by id.")
        main { assertEquals("legitymacje", input(decor())!!.text.toString()) }
        capture("api-modules")
        main { rule.activity.openApiMethod("services/cards/mcard_order2", mapOf("order_id" to "42")) }
        awaitText("cards/mcard_order2")
        has("42")
        assertTrue(rule.activity.apiDraft("services/cards/mcard_order2").getString("order_id") == "42")
    }

    @Test
    fun gradeSubjectDetailsIncludeAllComponentsAndCompleteStoredData() {
        main { rule.activity.navigate(2) }
        search("finansów")
        main {
            val values = rule.activity.findViewById<TextView>(R.id.grade_item_value_badge)
            var course = values.parent as View
            while (!course.isClickable) course = course.parent as View
            assertTrue(course.contentDescription.toString().contains("4,0"))
            assertTrue(course.contentDescription.toString().contains("5,0"))
        }
        click("Podstawy finansów")
        dialogHas("4,0")
        dialogHas("5,0")
        dialogHas("Kod przedmiotu")
        dialogHas("ID autora modyfikacji")
        dialogHas(rule.activity.getString(R.string.grades_full_data))
        Espresso.onView(ViewMatchers.withText(R.string.btn_close))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        capture("grade-details-complete")
    }

    @Test
    fun noAccountShowsScopeGuidanceWithoutPretendingDataExists() {
        main {
            rule.activity.setDemo(false)
            rule.activity.navigate(13)
        }
        has(rule.activity.getString(R.string.services_scope_required, "cards"))
        has(rule.activity.getString(R.string.cards_mobile_empty))
        absent(rule.activity.getString(R.string.cards_physical_title))
        main { rule.activity.navigate(14) }
        has(rule.activity.getString(R.string.services_scope_required, "adm_documents"))
    }
}
