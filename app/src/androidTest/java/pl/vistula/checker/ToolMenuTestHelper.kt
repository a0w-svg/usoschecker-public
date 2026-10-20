package pl.vistula.checker

import android.app.Activity
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry

internal object ToolMenuTestHelper {
    @JvmStatic
    fun select(activity: Activity, text: String): Boolean {
        val available = booleanArrayOf(false)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            available[0] = activity.findViewById<View>(R.id.tool_screen_more) != null
        }
        if (!available[0]) return false
        var root = false
        var document = false
        for (id in intArrayOf(
            R.string.api_all_methods,
            R.string.api_fav_filter,
            R.string.cards_refresh,
            R.string.documents_refresh,
            R.string.documents_all_methods,
            R.string.news_refresh_btn,
            R.string.payments_refresh_btn,
            R.string.tool_api_methods,
            R.string.attendance_btn_add_course
        )) {
            if (activity.getString(id) == text) root = true
        }
        for (id in intArrayOf(
            R.string.documents_details,
            R.string.documents_download,
            R.string.documents_receive,
            R.string.documents_get_code,
            R.string.documents_upo,
            R.string.documents_upd
        )) {
            if (activity.getString(id) == text) document = true
        }
        if (!root && !document) return false
        TestKeyboard.hide(activity)
        if (document) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                activity.findViewById<View>(R.id.document_more).performClick()
            }
        } else {
            onView(withId(R.id.tool_screen_more)).perform(click())
        }
        onView(withText(text)).inRoot(isPlatformPopup()).perform(click())
        return true
    }
}
