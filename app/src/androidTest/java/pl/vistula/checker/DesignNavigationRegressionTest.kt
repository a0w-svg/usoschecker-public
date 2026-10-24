package pl.vistula.checker

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignNavigationRegressionTest {
    @JvmField @Rule val rule = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun main(action: () -> Unit) {
        instrumentation.runOnMainSync(action)
        instrumentation.waitForIdleSync()
    }
    private fun capture(name: String) {
        val shot = instrumentation.uiAutomation.takeScreenshot()
        val file = File(rule.activity.getExternalFilesDir(null), "design-$name.png")
        try { file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { shot.recycle() }
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/usos-design-$name.png").use {
            FileInputStream(it.fileDescriptor).use { input -> while (input.read() != -1) {} }
        }
    }
    @Before fun ready() {
        assertTrue(rule.activity.packageName.endsWith(".qa"))
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var busy = true
            main { busy = rule.activity.findViewById<View>(R.id.progress_text).visibility != View.GONE }
            if (!busy) break
            SystemClock.sleep(50)
        }
        main { rule.activity.finishWelcome(false, false); rule.activity.setDemo(true); rule.activity.navigate(0) }
    }

    @Test fun allNavigationLabelsFitAndContentStopsAboveTheBar() {
        val destinations = listOf(0 to "today", 5 to "plan", 2 to "grades", 1 to "changes", 6 to "more")
        for ((destination, name) in destinations) {
            main { rule.activity.navigate(destination) }
            instrumentation.uiAutomation.waitForIdle(300, 3000)
            capture(name)
            main {
                val bar = rule.activity.findViewById<ViewGroup>(R.id.bottom_navigation)
                val viewport = bar.parent as View
                fun labels(view: View) {
                    if (view is TextView && view.isShown && view.text.isNotEmpty()) {
                        assertEquals("Full navigation label: ${view.text}", 1, view.lineCount)
                        assertEquals("Clipped navigation label: ${view.text}", 0, view.layout.getEllipsisCount(0))
                        assertTrue("Label wider than its slot", view.layout.getLineWidth(0) <= view.width - view.paddingLeft - view.paddingRight + 1)
                    }
                    if (view is ViewGroup) repeat(view.childCount) { labels(view.getChildAt(it)) }
                }
                labels(bar)
                val selectedId = when (destination) {
                    0 -> R.id.nav_item_home; 5 -> R.id.nav_item_plan; 2 -> R.id.nav_item_grades
                    1 -> R.id.nav_item_history; else -> R.id.nav_item_more
                }
                val selected = bar.findViewById<ViewGroup>(selectedId)
                val indicator = selected.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_active_indicator_view)
                val pillBounds = Rect(); assertTrue(indicator.getGlobalVisibleRect(pillBounds))
                fun insidePill(view: View) {
                    if (view is TextView && view.isShown && view.text.isNotEmpty()) {
                        val position = IntArray(2); view.getLocationOnScreen(position)
                        val textLeft = position[0] + view.compoundPaddingLeft + view.layout.getLineLeft(0)
                        val textRight = textLeft + view.layout.getLineWidth(0)
                        assertTrue("Active pill must contain its label", textLeft >= pillBounds.left && textRight <= pillBounds.right)
                        assertTrue("Active pill must centre ${view.text}: text=$textLeft..$textRight, pill=$pillBounds", kotlin.math.abs((textLeft + textRight) / 2 - pillBounds.exactCenterX()) <= 3 * rule.activity.resources.displayMetrics.density)
                    }
                    if (view is ViewGroup) repeat(view.childCount) { insidePill(view.getChildAt(it)) }
                }
                insidePill(selected)
                val scroll = rule.activity.findViewById<View>(R.id.main_scroll)
                val scrollBounds = Rect(); val navigationBounds = Rect()
                assertTrue(scroll.getGlobalVisibleRect(scrollBounds)); assertTrue(viewport.getGlobalVisibleRect(navigationBounds))
                assertTrue("Content must stop above the navigation", scrollBounds.bottom <= navigationBounds.top)
            }
        }
    }

    @Test fun weekStripOpensTheSelectedDayWithoutChangingTheTab() {
        main { rule.activity.setCalendarMode("day"); rule.activity.openScheduleDate(LocalDate.now(UsosClient.ZONE).toString()) }
        val monday = LocalDate.now(UsosClient.ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        main {
            val viewport = rule.activity.findViewById<HorizontalScrollView>(R.id.schedule_week_strip)
            val days = viewport.getChildAt(0) as LinearLayout
            assertEquals(7, days.childCount)
            assertTrue(days.getChildAt(2).performClick())
            assertEquals(monday.plusDays(2).toString(), rule.activity.getCalendarDate())
            assertTrue(rule.activity.findViewById<View>(R.id.nav_item_plan).isSelected)
        }
    }

    @Test fun gradeNamesAndAssessmentValuesHaveNoClippedLines() {
        main { rule.activity.navigate(2) }
        instrumentation.uiAutomation.waitForIdle(300, 3000)
        var courses = 0
        main {
            fun inspect(view: View) {
                if (view.id == R.id.grade_item_course_name) courses++
                if (view is TextView && view.isShown && view.text.isNotEmpty() && view.layout != null) {
                    repeat(view.layout.lineCount) { line ->
                        assertEquals("Clipped grade text: ${view.text}", 0, view.layout.getEllipsisCount(line))
                        assertTrue("Grade text wider than its container: ${view.text}", view.layout.getLineWidth(line) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1)
                    }
                }
                if (view is ViewGroup) repeat(view.childCount) { inspect(view.getChildAt(it)) }
            }
            inspect(rule.activity.findViewById(R.id.main_content))
            assertTrue("Fixture courses must be rendered", courses >= 2)
        }
        capture("grades-fit")
    }
}
