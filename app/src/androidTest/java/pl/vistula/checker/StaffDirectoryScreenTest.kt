package pl.vistula.checker

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
class StaffDirectoryScreenTest {
    @JvmField
    @Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    private fun main(action: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun content() = rule.activity.findViewById<LinearLayout>(R.id.main_content)

    @Test
    fun demoShowsTeachersAndSearchValidatesTheQuery() {
        assertTrue(rule.activity.packageName.endsWith(".qa"))
        main { rule.activity.setDemo(true); rule.activity.navigate(Tabs.STAFF_DIRECTORY) }
        main {
            val names = content().findViewById<LinearLayout>(R.id.staff_results)
            assertTrue("demo should list at least one teacher", names.childCount >= 1)
            assertEquals(View.GONE, names.getChildAt(0).findViewById<View>(R.id.staff_divider).visibility)
            assertTrue(names.getChildAt(0).findViewById<TextView>(R.id.staff_name).text.isNotEmpty())
            assertEquals(View.GONE, content().findViewById<View>(R.id.staff_empty).visibility)

            content().findViewById<TextInputEditText>(R.id.staff_search_input).setText("a")
            content().findViewById<View>(R.id.staff_search_button).performClick()
            assertNotNull(content().findViewById<TextInputLayout>(R.id.staff_search_layout).error)
        }
        SystemClock.sleep(400)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val file = File(rule.activity.getExternalFilesDir(null), "staff-xml.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cp " + file.absolutePath + " /data/local/tmp/usos-staff-xml.png").use { }
    }
}
