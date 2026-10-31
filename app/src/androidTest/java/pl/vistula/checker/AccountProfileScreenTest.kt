package pl.vistula.checker

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.ui.Tabs

@RunWith(AndroidJUnit4::class)
class AccountProfileScreenTest {
    @JvmField
    @Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    private fun main(action: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun content() = rule.activity.findViewById<LinearLayout>(R.id.main_content)

    @Test
    fun demoProfileCardShowsTheStudentAndHidesEmptyLines() {
        assertTrue(rule.activity.packageName.endsWith(".qa"))
        main {
            rule.activity.setDemo(true)
            rule.activity.navigate(Tabs.ACCOUNT)
            rule.activity.openAccountPage("settings.profile")
        }
        main {
            assertEquals("Jan Kowalski", content().findViewById<TextView>(R.id.profile_name).text.toString())
            assertEquals("JK", content().findViewById<TextView>(R.id.profile_avatar).text.toString())
            assertEquals(View.VISIBLE, content().findViewById<View>(R.id.profile_email_row).visibility)
            assertTrue(content().findViewById<TextView>(R.id.profile_student_number).text.contains("34589"))
            assertEquals(View.GONE, content().findViewById<View>(R.id.profile_thesis_unavailable).visibility)
        }
        SystemClock.sleep(400)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val file = File(rule.activity.getExternalFilesDir(null), "account-profile.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cp " + file.absolutePath + " /data/local/tmp/usos-account-profile.png").use { }
    }

    @Test
    fun signInSetupShowsCredentialFieldsScopesAndValidatesBeforeLogin() {
        main {
            rule.activity.setDemo(false)
            rule.activity.navigate(Tabs.ACCOUNT)
            rule.activity.openAccountPage("settings.connection")
        }
        main {
            val keyHost = content().findViewById<LinearLayout>(R.id.connection_key_host)
            val secretHost = content().findViewById<LinearLayout>(R.id.connection_secret_host)
            assertEquals(1, keyHost.childCount)
            assertEquals(1, secretHost.childCount)
            assertTrue(content().findViewById<LinearLayout>(R.id.connection_choices).childCount >= 6)
            assertEquals(View.GONE, content().findViewById<View>(R.id.connection_pin_section).visibility)
            if (BuildConfig.USOS_KEY.isBlank()) {
                content().findViewById<View>(R.id.connection_login).performClick()
                val keyLayout = keyHost.getChildAt(0) as com.google.android.material.textfield.TextInputLayout
                assertNotNull(keyLayout.error)
            }
        }
        SystemClock.sleep(400)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val file = File(rule.activity.getExternalFilesDir(null), "account-connection.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cp " + file.absolutePath + " /data/local/tmp/usos-account-connection.png").use { }
    }

    @Test
    fun reminderSwitchRevealsLeadTimeChipsWithoutLeavingThePage() {
        val prefs = rule.activity.getSharedPreferences("student_tools", android.content.Context.MODE_PRIVATE)
        main {
            prefs.edit().putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, false).putInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, 15).commit()
            rule.activity.setDemo(true)
            rule.activity.navigate(Tabs.ACCOUNT)
            rule.activity.openAccountPage("settings.notifications")
        }
        main {
            assertEquals(View.GONE, content().findViewById<View>(R.id.reminders_detail).visibility)
            content().findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.reminders_switch).isChecked = true
        }
        main {
            assertEquals(View.VISIBLE, content().findViewById<View>(R.id.reminders_detail).visibility)
            content().findViewById<com.google.android.material.chip.Chip>(R.id.lead_30).performClick()
            assertEquals(30, prefs.getInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, 0))
            content().findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.reminders_switch).isChecked = false
            prefs.edit().putInt(ClassReminderReceiver.PREF_REMINDER_MINUTES, 15).commit()
        }
    }
}
