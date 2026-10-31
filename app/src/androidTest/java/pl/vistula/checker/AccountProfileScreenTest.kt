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
}
