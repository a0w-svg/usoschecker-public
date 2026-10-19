package pl.vistula.checker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.work.WorkerParameters
import java.lang.reflect.Modifier
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionReadinessTest {
    @Test
    fun releaseWorkerRemainsConstructibleAfterOptimization() {
        val constructor = Class.forName("pl.vistula.checker.ReleaseUpdateWorker")
            .getConstructor(Context::class.java, WorkerParameters::class.java)
        assertTrue(Modifier.isPublic(constructor.modifiers))
    }

    @JvmField
    @Rule
    val rule = ActivityTestRule(MainActivity::class.java)

    @Before
    fun qaOnly() {
        Assume.assumeTrue(rule.activity.packageName.endsWith(".qa"))
        try {
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(cancel, true)
            cancel.recycle()
        } catch (ignored: Throwable) {}
    }

    @Test
    fun testInstallationContainsNoDeveloperCredentials() {
        assertTrue("QA must not embed a developer key", BuildConfig.USOS_KEY.isEmpty())
        assertTrue("QA must not embed a developer secret", BuildConfig.USOS_SECRET.isEmpty())
    }

    @Test
    fun platformBackReturnsFromGradesToHomeOnAndroid16() {
        onView(withId(R.id.nav_item_grades)).perform(click())
        onView(withId(R.id.nav_item_grades)).check(matches(isSelected()))
        systemBack()
        waitForHomeSelected(3000)
        onView(withId(R.id.nav_item_home)).check(matches(isSelected()))
    }

    private fun waitForHomeSelected(timeoutMs: Long) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val selected = booleanArrayOf(false)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val home = rule.activity?.findViewById<View>(R.id.nav_item_home)
                if (home != null && home.isSelected) selected[0] = true
            }
            if (selected[0]) return
            try {
                Thread.sleep(50)
            } catch (ignored: InterruptedException) {}
        }
    }

    @Test
    fun toolsReturnToMoreWithBackAndNavigationReselect() {
        onView(withId(R.id.nav_item_more)).perform(click())
        InstrumentationRegistry.getInstrumentation().runOnMainSync { rule.activity.navigate(7) }
        onView(withId(R.id.tasks_more)).check(matches(isDisplayed()))
        systemBack()
        onView(withId(R.id.nav_item_more)).check(matches(isSelected()))
        onView(withId(R.id.tasks_more)).check(doesNotExist())
        InstrumentationRegistry.getInstrumentation().runOnMainSync { rule.activity.navigate(7) }
        onView(withId(R.id.nav_item_more)).perform(click())
        onView(withId(R.id.tasks_more)).check(doesNotExist())
    }

    @Test
    fun moreContainsEverySecondaryDestination() {
        val labels = intArrayOf(
            R.string.cards_title,
            R.string.documents_title,
            R.string.tool_gpa_title,
            R.string.tool_attendance_title,
            R.string.tool_tasks_title,
            R.string.tool_payments_title,
            R.string.tool_news_title,
            R.string.tool_campus_title,
            R.string.tool_account_title,
            R.string.tool_api_title
        )
        for (label in labels) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                rule.activity.navigate(6)
                val content = rule.activity.findViewById<LinearLayout>(R.id.main_content)
                val name = rule.activity.getString(label)
                var destination: View? = null
                for (i in 0 until content.childCount) {
                    val candidate = content.getChildAt(i)
                    if (candidate.contentDescription != null && candidate.contentDescription.toString().startsWith("$name. ")) {
                        destination = candidate
                    }
                }
                assertNotNull("Missing destination: $name", destination)
                assertTrue(destination!!.performClick())
                assertNull("Destination did not open: $name", destination.parent)
            }
        }
    }

    private fun systemBack() {
        // API 36 no longer dispatches synthetic KEYCODE_BACK to the app's views.
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        assertTrue(ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        try {
            ui.waitForIdle(200, 5000)
        } catch (e: TimeoutException) {
            throw AssertionError("System back did not settle", e)
        }
    }

    @Test
    fun manifestPreservesTransportAndStorageSafety() {
        val info = rule.activity.applicationInfo
        assertTrue(info.targetSdkVersion >= 36)
        assertEquals(BuildConfig.DEBUG, (info.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertEquals(0, info.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)
        val pkg = rule.activity.packageManager.getPackageInfo(info.packageName, PackageManager.GET_PROVIDERS)
        val providers = pkg.providers
        if (!BuildConfig.DEBUG && providers != null) {
            for (provider in providers) {
                assertFalse(provider.authority != null && provider.authority.contains("debugexports"))
            }
        }
    }
}
