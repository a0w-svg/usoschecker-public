package pl.vistula.checker

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.anything

internal object GradesMenuTestHelper {
    @JvmStatic
    fun select(activity: Activity, text: String): Boolean {
        val available = booleanArrayOf(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            available[0] = activity.findViewById<View>(R.id.grades_more) != null
        }
        if (!available[0]) return false
        var submenu = 0
        for (id in intArrayOf(
            R.string.grades_chip_all,
            R.string.grades_type_labs_label,
            R.string.grades_type_exercises_label,
            R.string.grades_chip_numeric,
            R.string.grades_chip_other,
            R.string.grades_chip_retakes
        )) {
            if (text == activity.getString(id)) submenu = R.string.grades_filter_type
        }
        for (id in intArrayOf(
            R.string.grades_sort_subject,
            R.string.grades_sort_recent,
            R.string.grades_sort_value
        )) {
            if (text == activity.getString(id)) submenu = R.string.grades_sort_title
        }
        var direct = false
        for (id in intArrayOf(
            R.string.grades_search_action,
            R.string.grades_summary_title,
            R.string.grades_calc_button,
            R.string.grades_refresh,
            R.string.grades_filter_reset,
            R.string.grades_data_status
        )) {
            if (text == activity.getString(id)) direct = true
        }
        if (submenu == 0 && !direct) return false
        TestKeyboard.hide(activity)
        onView(withId(R.id.grades_more)).perform(click())
        if (submenu != 0) onView(withText(submenu)).inRoot(isPlatformPopup()).perform(click())
        onView(withText(text)).inRoot(isPlatformPopup()).perform(click())
        return true
    }

    @JvmStatic
    fun revealSearch(activity: Activity) {
        val hidden = booleanArrayOf(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val input = input(activity.window.decorView)
            hidden[0] = activity.findViewById<View>(R.id.grades_more) != null && input != null && !input.isShown
        }
        if (hidden[0]) {
            select(activity, activity.getString(R.string.grades_search_action))
            TestKeyboard.hide(activity)
        }
    }

    @JvmStatic
    fun selectSemester(activity: Activity, index: Int) {
        TestKeyboard.hide(activity)
        onView(withId(R.id.grades_semester)).perform(click())
        onData(anything()).inRoot(isDialog()).atPosition(index).perform(click())
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
}
