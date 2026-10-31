package pl.vistula.checker

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.ui.platform.ComposeView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.ui.Tabs

/** Screens implemented once in the shared Compose UI must render inside the View-based Android app. */
@RunWith(AndroidJUnit4::class)
class SharedComposeSectionTest {
    @JvmField
    @Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    private fun main(action: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun findCompose(view: View): ComposeView? {
        if (view is ComposeView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findCompose(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test
    fun staffDirectoryRendersFromSharedComposeInDemoMode() {
        assertTrue(rule.activity.packageName.endsWith(".qa"))
        main { rule.activity.setDemo(true); rule.activity.navigate(Tabs.STAFF_DIRECTORY) }
        val deadline = SystemClock.uptimeMillis() + 10_000
        var height = 0
        while (height == 0 && SystemClock.uptimeMillis() < deadline) {
            main { height = findCompose(rule.activity.findViewById<LinearLayout>(R.id.main_content))?.height ?: 0 }
            if (height == 0) SystemClock.sleep(100)
        }
        assertTrue("shared Compose section has no height", height > 0)
        SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        FileOutputStream(File(rule.activity.getExternalFilesDir(null), "staff-shared-compose.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cp " + File(rule.activity.getExternalFilesDir(null), "staff-shared-compose.png").absolutePath + " /data/local/tmp/usos-staff-shared-compose.png").use { }
    }
}
