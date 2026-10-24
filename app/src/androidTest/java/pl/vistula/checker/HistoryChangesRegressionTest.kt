package pl.vistula.checker

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.Record
import pl.vistula.checker.ui.HistoryScreenController

@RunWith(AndroidJUnit4::class)
class HistoryChangesRegressionTest {
    @JvmField
    @Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun scheduleCardShowsHoursAndRoomWithoutAGradeLabel() {
        val change = Change(lesson("09:00", "10:00", "204"), lesson("10:00", "11:00", "315"), 1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = rule.activity
            val content = LinearLayout(activity)
            HistoryScreenController.renderChangeCard(activity, content, change)
            val text = text(content)
            assertTrue(text.contains("09:00–10:00 → 10:00–11:00"))
            assertTrue(text.contains("204 → 315"))
            assertFalse(text.contains(activity.getString(R.string.change_grade) + ":"))
        }
    }

    @Test
    fun planFilterRepairsOldHistoryAndExcludesActualGradeEntries() {
        val old = Change(lesson("09:00", "10:00", "204"), lesson("10:00", "11:00", "315"), 1).json()
        old.getJSONObject("metadata").put("diffGrade", "09:00 → 10:00")
        val grade = Change(
            Record("grade", "ocena", "Grade-only subject", "4", "", ""),
            Record("grade", "ocena", "Grade-only subject", "5", "", ""),
            2
        )
        val state = JSONObject().put("history", JSONArray().put(old).put(grade.json()))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = rule.activity
            val content = LinearLayout(activity)
            HistoryScreenController.render(activity, content, state, "plan", "", false, {}, {}) {}
            val text = text(content)
            assertTrue(text.contains("Schedule-only subject"))
            assertFalse(text.contains("Grade-only subject"))
            assertFalse(text.contains(activity.getString(R.string.change_grade) + ":"))
        }
    }

    @Test
    fun gradeCardStillShowsItsGradeChange() {
        val grade = Change(
            Record("grade", "ocena", "Grade-only subject", "4", "", ""),
            Record("grade", "ocena", "Grade-only subject", "5", "", ""),
            2
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = rule.activity
            val content = LinearLayout(activity)
            HistoryScreenController.renderChangeCard(activity, content, grade)
            assertTrue(text(content).contains(activity.getString(R.string.change_grade) + ": 4 → 5"))
        }
    }

    @Test
    fun minimalHistoryKeepsSearchInMenuAndOpensFullDetails() {
        val deadline = SystemClock.uptimeMillis() + 10000
        val ready = booleanArrayOf(false)
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ready[0] = rule.activity.findViewById<View>(R.id.progress_text).visibility == View.GONE
            }
            if (!ready[0]) SystemClock.sleep(50)
        }
        assertTrue("Startup must finish before replacing test content", ready[0])
        val change = Change(lesson("09:00", "10:00", "204"), lesson("10:00", "11:00", "315"), 1)
        val state = JSONObject().put("history", JSONArray().put(change.json()))
        val root = arrayOfNulls<LinearLayout>(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = rule.activity
            root[0] = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            activity.setContentView(root[0])
            HistoryScreenController.render(activity, root[0]!!, state, "wszystkie", "", false, {}, {}, null)
            assertFalse(findSearch(root[0]!!)!!.isShown)
            root[0]!!.findViewById<View>(R.id.history_more).performClick()
        }
        Espresso.onView(withText(R.string.history_search_action)).perform(click())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val search = findSearch(root[0]!!)!!
            assertTrue(search.isShown)
            search.setText("no matching subject")
            assertFalse(text(root[0]!!).contains("Schedule-only subject"))
            search.setText("")
            assertTrue(text(root[0]!!).contains("Schedule-only subject"))
        }
        Espresso.closeSoftKeyboard()
        Espresso.onView(withText("Schedule-only subject")).perform(click())
        Espresso.onView(withText(R.string.history_details_title)).check(matches(isDisplayed()))
    }

    private fun findSearch(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findSearch(view.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    private fun lesson(start: String, end: String, room: String): Record {
        return Record(
            "lesson",
            "plan",
            "Schedule-only subject",
            "$start–$end · $room",
            "",
            "2026-10-15",
            "",
            "2026-10-15 $start:00",
            "2026-10-15 $end:00",
            Double.NaN,
            JSONObject().put("room", room)
        )
    }

    private fun text(view: View): String {
        val text = StringBuilder()
        if (view is TextView) text.append(view.text).append('\n')
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                text.append(text(view.getChildAt(i)))
            }
        }
        return text.toString()
    }
}
