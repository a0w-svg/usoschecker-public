package pl.vistula.checker

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDashboardRegressionTest {
    @JvmField @Rule val rule = ActivityTestRule(MainActivity::class.java)
    private fun main(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    @Before fun ready() {
        assertTrue(rule.activity.packageName.endsWith(".qa"))
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until) {
            var busy = true
            main { busy = rule.activity.findViewById<View>(R.id.progress_text).visibility != View.GONE }
            if (!busy) break
            Thread.sleep(50)
        }
        main { rule.activity.setDemo(true); rule.activity.navigate(0) }
    }
    @Test fun dayShortcutClearsOldFiltersAndOpensDayMode() {
        main {
            rule.activity.setCalendarQuery("missing-course")
            rule.activity.setCalendarMode("week")
            assertTrue(rule.activity.findViewById<View>(R.id.home_open_schedule).performClick())
            assertEquals("", rule.activity.getCalendarQuery())
            assertEquals("day", rule.activity.getCalendarMode())
            assertTrue(rule.activity.findViewById<View>(R.id.nav_item_plan).isSelected)
        }
    }
    @Test fun gradeAndTaskShortcutsReachTheirFullScreens() {
        main {
            assertTrue(rule.activity.findViewById<View>(R.id.home_open_grades).performClick())
            assertTrue(rule.activity.findViewById<View>(R.id.nav_item_grades).isSelected)
            rule.activity.navigate(0)
            assertTrue(rule.activity.findViewById<View>(R.id.home_open_tasks).performClick())
            assertNotNull(rule.activity.findViewById<View>(R.id.tasks_add))
        }
    }
    @Test fun dashboardPreviewAndShortcutsNeverWriteTheLiveAccount() {
        val original = Repository(rule.activity).read().toString()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 3000)
        val shot = instrumentation.uiAutomation.takeScreenshot()
        val file = File(rule.activity.getExternalFilesDir(null), "dashboard.png")
        try { FileOutputStream(file).use { assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { shot.recycle() }
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/usos-home-dashboard.png").use {
            FileInputStream(it.fileDescriptor).use { input -> while (input.read() != -1) { } }
        }
        main { assertTrue(rule.activity.findViewById<View>(R.id.home_open_news).performClick()) }
        assertEquals(original, Repository(rule.activity).read().toString())
    }
    @Test fun installmentPreviewIncludesParentCurrencyWhenPlanHasNoUnit() {
        main {
            val a = rule.activity
            a.setDemo(false)
            val language = a.resources.configuration.locales[0].language
            val plan = JSONObject().put("id", "fixture-plan").put("installments",
                JSONArray().put(JSONObject().put("amount", 1250).put("payment_date", "2027-01-15")))
            val payment = JSONObject().put("currency", "PLN").put("description", "Czesne")
                .put("plans", JSONArray().put(plan))
            val data = JSONObject().put(language, pl.vistula.checker.core.PaymentInfo(emptyList(), emptyList()).json())
                .put("details", JSONArray().put(payment))
            val state = JSONObject().put("studentServices", JSONObject().put("payments", JSONObject().put("data", data)))
            val content = LinearLayout(a)
            pl.vistula.checker.ui.PaymentsScreenController.render(a, content, JSONObject(), state, false, {})
            val expected = pl.vistula.checker.core.PaymentAmounts.format(1250, "PLN", language)
            fun includes(view: View): Boolean = (view is TextView && view.text.contains(expected)) ||
                (view is ViewGroup && (0 until view.childCount).any { includes(view.getChildAt(it)) })
            assertTrue("Installment must include its currency: $expected", includes(content))
        }
    }

    @Test fun concurrentClassesRemainVisibleInTheNewFocusLayout() {
        main {
            val now = java.time.ZonedDateTime.now(pl.vistula.checker.UsosClient.ZONE)
            val format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val snapshot = pl.vistula.checker.core.Snapshot(now.toLocalDate().toString(), now.toLocalDate().plusDays(2).toString())
            repeat(2) { index -> snapshot.add(pl.vistula.checker.core.Record(
                "focus-$index", "plan", "Concurrent class $index", "", "", now.toLocalDate().toString(), "",
                now.minusMinutes(10).format(format), now.plusMinutes(50).format(format))) }
            val content = LinearLayout(rule.activity)
            pl.vistula.checker.ui.HomeScreenController.render(rule.activity, content,
                JSONObject().put("snapshot", snapshot.json()), JSONObject(), false, null) { }
            fun texts(view: View): List<String> = (if (view is TextView) listOf(view.text.toString()) else emptyList()) +
                (if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList())
            assertTrue(texts(content).containsAll(listOf("Concurrent class 0", "Concurrent class 1")))
        }
    }

    @Test fun tentativeClassHasAnHonestLabelInsteadOfACountdown() {
        main {
            val now = java.time.ZonedDateTime.now(pl.vistula.checker.UsosClient.ZONE)
            val format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val snapshot = pl.vistula.checker.core.Snapshot(now.toLocalDate().toString(), now.toLocalDate().plusDays(3).toString())
            snapshot.add(pl.vistula.checker.core.Record("tentative", "plan", "Tentative fixture", "", "", now.toLocalDate().toString(), "",
                now.plusMinutes(30).format(format), now.plusMinutes(90).format(format), Double.NaN, JSONObject().put("frequency", "other")))
            val content = LinearLayout(rule.activity)
            pl.vistula.checker.ui.HomeScreenController.render(rule.activity, content,
                JSONObject().put("snapshot", snapshot.json()), JSONObject(), false, null) { }
            fun has(view: View): Boolean = (view is TextView && view.text.toString() == rule.activity.getString(R.string.design_tentative)) ||
                (view is ViewGroup && (0 until view.childCount).any { has(view.getChildAt(it)) })
            assertTrue(has(content))
        }
    }
}
