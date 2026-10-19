package pl.vistula.checker

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.AttendanceTracker
import pl.vistula.checker.ui.AccountScreenController
import pl.vistula.checker.ui.AttendanceScreenController

@RunWith(AndroidJUnit4::class)
class AttendanceAndLoginUiTest {
    @JvmField
    @Rule
    val rule = ActivityTestRule(MainActivity::class.java)
    private var prefs: SharedPreferences? = null
    private lateinit var original: Map<String, *>

    @Before
    fun setUp() {
        Assume.assumeTrue("Use -PisolatedTests", rule.activity.packageName.endsWith(".qa"))
        val p = rule.activity.getSharedPreferences("attendance_tracker", Context.MODE_PRIVATE)
        prefs = p
        original = p.all
        p.edit().clear().commit()
    }

    @After
    fun restore() {
        val p = prefs ?: return
        val editor = p.edit().clear()
        for ((key, value) in original) {
            if (value is String) editor.putString(key, value)
        }
        editor.commit()
    }

    private fun main(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                action()
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }
    }

    private fun find(root: View, text: String): View? {
        if (root is TextView && root.text.toString() == text) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = find(root.getChildAt(i), text)
                if (found != null) return found
            }
        }
        return null
    }

    private fun visible(view: View?): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        val parent = view.parent
        return parent !is View || visible(parent)
    }

    private fun attendance(filter: String): LinearLayout {
        val content = LinearLayout(rule.activity)
        AttendanceScreenController.render(rule.activity, content, JSONObject(), filter, {}) {}
        return content
    }

    @Test
    fun semesterAndClassTypeFiltersDoNotMixTotals() {
        main {
            val rows = JSONObject()
                .put("one", AttendanceTracker.item("Wykład bieżący", "MAT", "now", "Wykład").put("absences", 2))
                .put("two", AttendanceTracker.item("Ćwiczenia bieżące", "MAT", "now", "Ćwiczenia").put("absences", 1))
                .put("old", AttendanceTracker.item("Wykład archiwalny", "MAT", "old", "Wykład").put("absences", 4))
            prefs!!.edit().putString("data", rows.toString()).putString("term", "now").putString("classType", "Wykład").commit()
            val content = attendance("all")
            assertNotNull(find(content, "Wykład bieżący"))
            assertNull(find(content, "Ćwiczenia bieżące"))
            assertNull(find(content, "Wykład archiwalny"))
            assertNotNull(find(content, rule.activity.getString(R.string.attendance_stats_summary, 1, 2)))
            prefs!!.edit().putString("term", "old").commit()
            assertNotNull(find(attendance("exceeded"), "Wykład archiwalny"))
        }
    }

    @Test
    fun thirdAbsenceIsWarningAndFourthIsExceeded() {
        main {
            val rows = JSONObject()
                .put("three", AttendanceTracker.item("Trzy", "A", "now", "Wykład").put("absences", 3))
                .put("four", AttendanceTracker.item("Cztery", "B", "now", "Wykład").put("absences", 4))
            prefs!!.edit().putString("data", rows.toString()).putString("term", "now").commit()
            assertNotNull(find(attendance("warning"), "Trzy"))
            assertNull(find(attendance("exceeded"), "Trzy"))
            assertNotNull(find(attendance("exceeded"), "Cztery"))
        }
    }

    @Test
    fun demoAttendanceDoesNotReadOrOverwritePersonalAttendance() {
        main {
            val activity = rule.activity
            val personal = JSONObject()
                .put("private", AttendanceTracker.item("Prywatny przedmiot", "A", "now", "Wykład").put("absences", 2))
                .toString()
            prefs!!.edit().putString("data", personal).commit()
            try {
                activity.setDemo(true)
                activity.attendancePreferences().edit().clear().commit()
                assertNull(find(attendance("all"), "Prywatny przedmiot"))
                activity.attendancePreferences().edit().putString("data", "{}").commit()
                assertEquals(personal, prefs!!.getString("data", ""))
                activity.setDemo(false)
                assertSame(prefs, activity.attendancePreferences())
            } finally {
                activity.getSharedPreferences("attendance_tracker_demo", Context.MODE_PRIVATE).edit().clear().commit()
                activity.setDemo(false)
            }
        }
    }

    private fun account(state: JSONObject): LinearLayout {
        val content = LinearLayout(rule.activity)
        AccountScreenController.render(
            rule.activity,
            content,
            JSONObject(),
            state,
            false,
            "",
            object : AccountScreenController.AccountListener {
                override fun onBeginOAuth(key: String, secret: String, scopes: String) {}
                override fun onConfirmPin(pin: String) {}
                override fun onToggleEnabled(enabled: Boolean) {}
                override fun onUpdateInterval(interval: Long) {}
                override fun onDisconnect() {}
                override fun onStartDemo() {}
                override fun onEndDemo() {}
                override fun onRequestNotifications() {}
            }
        )
        return content
    }

    @Test
    fun pendingLoginShowsPinAndHidesRestartUntilSettingsAreOpened() {
        main {
            val content = account(JSONObject().put("key", "test-key").put("secret", "test-secret").put("requestToken", "pending"))
            assertTrue(visible(find(content, rule.activity.getString(R.string.btn_confirm_pin))))
            val restart = find(content, rule.activity.getString(R.string.btn_login_browser))
            assertFalse(visible(restart))
            find(content, rule.activity.getString(R.string.login_settings))!!.performClick()
            assertTrue(visible(restart))
        }
    }
}
