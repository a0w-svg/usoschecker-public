package pl.vistula.checker

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.startsWith

internal object TasksMenuTestHelper {
    @JvmStatic
    fun select(activity: Activity, text: String): Boolean {
        val available = booleanArrayOf(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            available[0] = activity.findViewById<View>(R.id.tasks_more) != null
        }
        if (!available[0]) return false
        if (text == activity.getString(R.string.btn_add_task)) {
            onView(withId(R.id.tasks_add)).perform(click())
            return true
        }
        var submenu = 0
        for (id in intArrayOf(
            R.string.tasks_chip_all,
            R.string.tasks_chip_open,
            R.string.tasks_chip_done,
            R.string.tasks_platon_title,
            R.string.tasks_chip_today,
            R.string.tasks_chip_week,
            R.string.tasks_chip_overdue
        )) {
            if (text == activity.getString(id)) submenu = R.string.tasks_filter_title
        }
        for (id in intArrayOf(
            R.string.tasks_sort_due,
            R.string.tasks_sort_priority,
            R.string.tasks_sort_title
        )) {
            if (text == activity.getString(id)) submenu = R.string.tasks_sort_note
        }
        val direct = text == activity.getString(R.string.tasks_search_action) ||
            text == activity.getString(R.string.tasks_platon_open) ||
            text == activity.getString(R.string.tasks_platon_add)
        var rowAction = false
        for (id in intArrayOf(
            R.string.tasks_btn_edit,
            R.string.tasks_btn_delete,
            R.string.tasks_btn_mark_done,
            R.string.tasks_btn_mark_open
        )) {
            if (text == activity.getString(id)) rowAction = true
        }
        if (submenu == 0 && !direct && !rowAction) return false
        if (rowAction) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                activity.findViewById<View>(R.id.task_item_more).performClick()
            }
            onView(withText(text)).inRoot(isPlatformPopup()).perform(click())
            return true
        }
        TestKeyboard.hide(activity)
        onView(withId(R.id.tasks_more)).perform(click())
        if (submenu != 0) onView(withText(submenu)).inRoot(isPlatformPopup()).perform(click())
        onView(withText(startsWith(text))).inRoot(isPlatformPopup()).perform(click())
        return true
    }

    @JvmStatic
    fun revealSearch(activity: Activity) {
        val hidden = booleanArrayOf(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val input = input(activity.window.decorView)
            hidden[0] = activity.findViewById<View>(R.id.tasks_more) != null && input != null && !input.isShown
        }
        if (hidden[0]) {
            select(activity, activity.getString(R.string.tasks_search_action))
            TestKeyboard.hide(activity)
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
}
