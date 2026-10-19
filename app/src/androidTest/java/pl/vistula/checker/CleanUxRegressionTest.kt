package pl.vistula.checker

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.TaskTools
import pl.vistula.checker.ui.GradesScreenController
import pl.vistula.checker.ui.ScheduleScreenController
import pl.vistula.checker.ui.TasksScreenController
import pl.vistula.checker.ui.UiHelper

@RunWith(AndroidJUnit4::class)
class CleanUxRegressionTest {
    @JvmField
    @Rule
    val rule = ActivityTestRule(MainActivity::class.java)

    @Before
    fun isolatedOnly() {
        Assume.assumeTrue(rule.activity.packageName.endsWith(".qa"))
    }

    private fun main(action: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    @Test
    fun homeIsCleanAndSettingsOwnAllPreferences() {
        main {
            val activity = rule.activity
            activity.setDemo(true)
            activity.navigate(0)
            var content = activity.findViewById<View>(R.id.main_content)
            for (label in intArrayOf(
                R.string.hero_heading,
                R.string.monitoring_title,
                R.string.home_week_title,
                R.string.academic_calendar_title,
                R.string.quick_refresh,
                R.string.btn_simulate_changes
            )) {
                assertFalse(activity.getString(label), hasText(content, activity.getString(label)))
            }
            activity.navigate(6)
            content = activity.findViewById(R.id.main_content)
            assertFalse(hasText(content, activity.getString(R.string.appearance_title)))
            assertFalse(hasText(content, activity.getString(R.string.about_title)))
            activity.navigate(4)
            content = activity.findViewById(R.id.main_content)
            for (label in intArrayOf(
                R.string.appearance_title,
                R.string.language_title,
                R.string.account_notices_heading,
                R.string.account_widgets_heading,
                R.string.academic_calendar_title,
                R.string.about_title
            )) {
                assertTrue(activity.getString(label), hasText(content, activity.getString(label)))
            }
            assertTrue(hasText(content, activity.getString(R.string.nav_account)))
        }
    }

    @Test
    fun timetableOptionsAreRightOfDateNavigation() {
        main {
            val activity = rule.activity
            activity.setDemo(true)
            activity.navigate(5)
            val menu = activity.findViewById<View>(R.id.schedule_more)
            assertNotNull(menu)
            val row = menu.parent as ViewGroup
            assertSame(menu, row.getChildAt(3))
            assertEquals(activity.getString(R.string.schedule_options), menu.contentDescription)
            assertTrue(activity.findViewById<View>(R.id.main_root) is LinearLayout)
            assertNotNull(activity.findViewById(R.id.nav_item_more))
        }
    }

    @Test
    fun screenNamesStayHiddenAndTextUsesNeutralColors() {
        main {
            val activity = rule.activity
            for (tab in intArrayOf(0, 1, 2, 4, 5, 6)) {
                activity.navigate(tab)
                assertTrue(activity.findViewById<View>(R.id.main_root) is LinearLayout)
            }
            assertEquals(activity.getColor(R.color.app_ink), UiHelper.foreground(activity, UiHelper.GREEN))
            val time = activity.layoutInflater.inflate(R.layout.item_lesson_card, null).findViewById<TextView>(R.id.lesson_item_time)
            assertEquals(activity.getColor(R.color.app_muted), time.currentTextColor)
        }
    }

    @Test
    fun timetableTechnicalFieldsAreAvailableOnlyAfterRequestingFullData() {
        main {
            try {
                val lesson = Record(
                    "lesson",
                    "plan",
                    "Finanse",
                    "09:00 – 10:30\nSala 201",
                    "",
                    "2026-10-18",
                    "2026Z",
                    "",
                    "",
                    Double.NaN,
                    JSONObject().put("room", "201").put("unitId", "unit-fixture")
                )
                ScheduleScreenController.showLessonDetails(
                    rule.activity,
                    LessonTime.of(lesson),
                    object : ScheduleScreenController.ScheduleListener {
                        override fun onOpenTask(title: String) {}
                        override fun onAbsenceRecorded() {}
                    }
                )
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }
        onView(withText("Sala 201")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(containsString("unit-fixture"))).inRoot(isDialog())
            .check(matches(not(isDisplayed())))
        onView(withText(R.string.grades_full_data)).inRoot(isDialog()).perform(click())
        onView(withText(containsString("unit-fixture"))).inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText(R.string.btn_close)).inRoot(isDialog()).perform(click())
    }

    @Test
    fun compactGradeRowShowsValuesAndKeepsSupportingDataInDetails() {
        main {
            try {
                val activity = rule.activity
                val snapshot = Snapshot("", "")
                snapshot.add(
                    Record(
                        "grade",
                        "ocena",
                        "Finanse",
                        "5",
                        "",
                        "",
                        "2026Z",
                        "",
                        "",
                        5.0,
                        JSONObject().put("courseId", "FIN").put("gradeScope", "course").put("comment", "Komentarz testowy")
                    )
                )
                val content = LinearLayout(activity)
                GradesScreenController.render(
                    activity,
                    content,
                    JSONObject().put("snapshot", snapshot.json()),
                    JSONObject(),
                    false,
                    "",
                    "all",
                    "name",
                    "",
                    false,
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    object : GradesScreenController.GradesListener {
                        override fun onFilterChanged() {}
                        override fun onOpenTask(title: String) {}
                    }
                )
                assertNotNull(content.findViewById(R.id.grades_more))
                assertFalse(hasText(content, activity.getString(R.string.grades_summary_title)))
                assertFalse(hasText(content, "Komentarz testowy"))
                val grade = content.findViewById<TextView>(R.id.grade_item_value_badge)
                assertEquals("5", grade.text.toString())
                assertNull(grade.background)
                val source = content.findViewById<TextView>(R.id.grade_item_course_meta)
                assertEquals(View.VISIBLE, source.visibility)
                assertEquals(activity.getString(R.string.grades_scope_course), source.text.toString())
                val row = grade.parent.parent as View
                assertTrue(row.isClickable)
                assertTrue(row.isFocusable)
                row.performClick()
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }
        onView(withText("Komentarz testowy")).inRoot(isDialog()).perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText(R.string.btn_close)).inRoot(isDialog()).perform(click())
    }

    @Test
    fun taskEditorKeepsPrivateDraftsOutOfViewStateAndAutofill() {
        main {
            val editor = LayoutInflater.from(rule.activity).inflate(R.layout.dialog_task_input, null)
            for (id in intArrayOf(R.id.task_input_title, R.id.task_input_notes)) {
                val field = editor.findViewById<EditText>(id)
                assertFalse(field.isSaveEnabled)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, field.importantForAutofill)
                assertNotEquals(0, field.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
            }
            val title = editor.findViewById<TextInputLayout>(R.id.task_title_layout)
            val notes = editor.findViewById<TextInputLayout>(R.id.task_notes_layout)
            assertTrue(title.isCounterEnabled)
            assertEquals(200, title.counterMaxLength)
            assertTrue(notes.isCounterEnabled)
            assertEquals(2000, notes.counterMaxLength)
        }
    }

    @Test
    fun sharedControlsExposeHeadingsAndDisabledState() {
        main {
            val activity = rule.activity
            val content = LinearLayout(activity)
            UiHelper.title(activity, content, "Title", "Description")
            assertTrue(ViewCompat.isAccessibilityHeading(content.getChildAt(0)))
            val tile = UiHelper.toolTile(activity, content, "*", "Tasks", "Manage deadlines") {}
            assertEquals("Tasks. Manage deadlines", tile.contentDescription)
            val button = UiHelper.button(activity, "Save", {}, true)
            val enabled = button.currentTextColor
            button.isEnabled = false
            assertNotEquals(enabled, button.currentTextColor)
            val search = UiHelper.searchField(activity, content, "Search", "", "Search", null)
            assertNotEquals(0, search.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        }
    }

    @Test
    fun invalidTaskStaysOpenWithoutSaving() {
        val saves = AtomicInteger()
        main {
            TasksScreenController.showTaskDialog(
                rule.activity,
                null,
                "",
                0,
                object : TasksScreenController.TaskCallback {
                    override fun onSaveTask(task: JSONObject) {
                        saves.incrementAndGet()
                    }

                    override fun onMutateTask(id: String, remove: Boolean) {}
                }
            )
        }
        onView(withText(R.string.btn_save)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.task_input_title)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            assertNotNull((view as EditText).error)
            assertTrue(view.hasFocus())
        }
        assertEquals(0, saves.get())
        onView(withText(R.string.btn_cancel)).inRoot(isDialog()).perform(click())
    }

    @Test
    fun taskActionsIdentifyTheirTaskAndWeekCountMatchesFilter() {
        val task = JSONObject()
            .put("id", "fixture")
            .put("title", "Projekt testowy")
            .put("notes", "Example notes")
            .put("due", LocalDate.now(UsosClient.ZONE).plusDays(7).toString())
        main {
            try {
                val activity = rule.activity
                val content = LinearLayout(activity)
                TasksScreenController.render(
                    activity,
                    content,
                    JSONArray().put(task),
                    "all",
                    "due",
                    "",
                    {},
                    {},
                    {},
                    object : TasksScreenController.TaskCallback {
                        override fun onSaveTask(value: JSONObject) {}
                        override fun onMutateTask(id: String, remove: Boolean) {}
                    }
                )
                for (id in intArrayOf(R.id.task_item_more, R.id.task_item_btn_toggle)) {
                    assertTrue(content.findViewById<View>(id).contentDescription.toString().contains("Projekt testowy"))
                }
                assertFalse(TaskTools.matches(task, "week", "", LocalDate.now(UsosClient.ZONE)))
            } catch (e: JSONException) {
                throw AssertionError(e)
            }
        }
    }

    private fun hasText(view: View, text: String): Boolean {
        if (view is TextView && text.contentEquals(view.text)) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (hasText(view.getChildAt(i), text)) return true
            }
        }
        return false
    }

    @Test
    fun taskActionsFitNarrowScreenWithLargeFonts() {
        main {
            val config = Configuration(rule.activity.resources.configuration)
            config.fontScale = 2f
            val context = ContextThemeWrapper(rule.activity.createConfigurationContext(config), R.style.AppTheme)
            val card = LayoutInflater.from(context).inflate(R.layout.item_task_card, null)
            card.measure(
                View.MeasureSpec.makeMeasureSpec(UiHelper.dp(context, 280), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            for (id in intArrayOf(R.id.task_item_more, R.id.task_item_btn_toggle)) {
                val button = card.findViewById<View>(id)
                assertTrue(button.measuredHeight >= UiHelper.dp(context, 48))
                assertTrue(button.measuredWidth >= UiHelper.dp(context, 48))
            }
        }
    }

    @Test
    fun captureExampleEditorForVisualReview() {
        main {
            TasksScreenController.showTaskDialog(
                rule.activity,
                null,
                "Przygotować projekt",
                0,
                object : TasksScreenController.TaskCallback {
                    override fun onSaveTask(value: JSONObject) {}
                    override fun onMutateTask(id: String, remove: Boolean) {}
                }
            )
        }
        onView(withId(R.id.task_input_title)).inRoot(isDialog()).check(matches(isDisplayed()))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Espresso's displayed check can complete while the platform dialog is still fading in.
        SystemClock.sleep(500)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        val output = File(rule.activity.getExternalFilesDir(null), "ux-task-editor.png")
        FileOutputStream(output).use { stream -> screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream) }
        screenshot.recycle()
        onView(withText(R.string.btn_cancel)).inRoot(isDialog()).perform(click())
    }
}
