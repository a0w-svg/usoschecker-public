package pl.vistula.checker

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

internal object PlanMenuTestHelper {
    @JvmStatic
    fun select(activity: Activity, text: String): Boolean {
        val available = booleanArrayOf(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (activity.findViewById<View>(R.id.schedule_more) == null) return@runOnMainSync
            for (label in intArrayOf(
                R.string.schedule_day_label,
                R.string.schedule_mode_week,
                R.string.schedule_mode_agenda,
                R.string.schedule_btn_today,
                R.string.schedule_search_action,
                R.string.schedule_export_ics,
                R.string.schedule_share_plan,
                R.string.schedule_info,
                R.string.schedule_info_title,
                R.string.btn_refresh
            )) {
                if (text == activity.getString(label)) available[0] = true
            }
        }
        if (!available[0]) return false
        val label = if (text == activity.getString(R.string.schedule_info)) {
            activity.getString(R.string.schedule_info_title)
        } else {
            text
        }
        onView(withId(R.id.schedule_more)).perform(click())
        onView(withText(label)).inRoot(isPlatformPopup()).perform(click())
        return true
    }

    @JvmStatic
    fun revealSearch(activity: Activity) {
        val hidden = booleanArrayOf(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val input = input(activity.window.decorView)
            hidden[0] = activity.findViewById<View>(R.id.schedule_more) != null && input != null && !input.isShown
        }
        if (hidden[0]) {
            select(activity, activity.getString(R.string.schedule_search_action))
            closeSoftKeyboard()
        }
    }

    private fun input(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = input(view.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    @JvmStatic
    fun assertExportDisabled(activity: Activity) {
        onView(withId(R.id.schedule_more)).perform(click())
        try {
            onView(withText(R.string.schedule_export_ics)).inRoot(isPlatformPopup()).check { view, error ->
                if (error != null) throw error
                var disabled = false
                // Native menus disable the row; the title TextView itself remains enabled.
                var row: View? = view
                while (row != null) {
                    disabled = disabled or !row.isEnabled
                    row = row.parent as? View
                }
                assertTrue("Export menu row must be disabled", disabled)
            }
        } finally {
            pressBack()
        }
    }
}
