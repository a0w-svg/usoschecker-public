package pl.vistula.checker

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import java.util.regex.Pattern
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.ui.ScheduleScreenController

@RunWith(AndroidJUnit4::class)
class ScheduleBreaksUiTest {
    @JvmField
    @Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    private fun lesson(id: String, time: String): Record = Record(id, "plan", id, time, "", "2026-10-15")

    private fun render(mode: String, query: String, vararg lessons: Record): String {
        val snapshot = Snapshot("2026-10-12", "2026-10-19")
        for (lesson in lessons) snapshot.add(lesson)
        val data = JSONObject().put("snapshot", snapshot.json())
        val output = arrayOfNulls<String>(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val activity = rule.activity
                activity.setCalendarQuery(query)
                val content = LinearLayout(activity)
                ScheduleScreenController.render(
                    activity,
                    content,
                    data,
                    JSONObject(),
                    false,
                    "2026-10-15",
                    mode,
                    7,
                    {},
                    {},
                    {},
                    object : ScheduleScreenController.ScheduleListener {
                        override fun onOpenTask(title: String) {}
                        override fun onAbsenceRecorded() {}
                    }
                )
                output[0] = text(content)
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }
        return output[0]!!
    }

    @Test
    fun dayWeekAndAgendaShowBreakHoursAndDurationOnlyOnce() {
        val break30 = rule.activity.getString(R.string.schedule_break_title, 30)
        val breakTime = rule.activity.getString(R.string.schedule_break_time, "10:00", "10:30")
        for (mode in arrayOf("day", "week", "agenda")) {
            val text = render(mode, "", lesson("a", "09:00 – 10:00"), lesson("b", "10:30 – 11:30"), lesson("c", "10:30 – 12:00"))
            assertEquals(1, text.split(Regex(Pattern.quote(break30))).size - 1)
            assertTrue(text.contains(breakTime))
        }
    }

    @Test
    fun searchDoesNotTreatHiddenMeetingAsFreeTime() {
        val break15 = rule.activity.getString(R.string.schedule_break_title, 15)
        val break75 = rule.activity.getString(R.string.schedule_break_title, 75)
        val text = render(
            "day",
            "visible",
            lesson("first", "09:00 – 10:00"),
            lesson("hidden", "10:00 – 11:00"),
            lesson("visible", "11:15 – 12:00")
        )
        assertTrue(text.contains(break15))
        assertFalse(text.contains(break75))
    }

    @Test
    fun overlappingOrTouchingLessonsHaveNoBreakCard() {
        val breakPrefix = rule.activity.getString(R.string.schedule_break_title, 30).split("·")[0].trim() + " ·"
        assertFalse(
            render(
                "day",
                "",
                lesson("a", "09:00 – 12:00"),
                lesson("b", "10:00 – 11:00"),
                lesson("c", "12:00 – 13:00")
            ).contains(breakPrefix)
        )
    }

    private fun text(view: View): String {
        val result = StringBuilder()
        if (view is TextView) result.append(view.text).append('\n')
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                result.append(text(view.getChildAt(i)))
            }
        }
        return result.toString()
    }
}
