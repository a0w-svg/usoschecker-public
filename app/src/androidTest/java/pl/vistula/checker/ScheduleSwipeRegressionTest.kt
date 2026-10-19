package pl.vistula.checker

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.NavigableSet
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.PlanBrowser
import pl.vistula.checker.core.Snapshot

@RunWith(AndroidJUnit4::class)
class ScheduleSwipeRegressionTest {
    @JvmField
    @Rule
    val rule = ActivityTestRule(MainActivity::class.java)

    private fun main(action: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Before
    fun setup() {
        assertTrue(rule.activity.packageName.endsWith(".qa"))
        main {
            rule.activity.setDemo(true)
            rule.activity.setCalendarDate(LocalDate.now(UsosClient.ZONE).toString())
            rule.activity.setCalendarMode("day")
            rule.activity.navigate(5)
        }
    }

    private fun swipe(left: Boolean, vertical: Boolean) {
        val area = Rect()
        main {
            val results = rule.activity.findViewById<View>(R.id.schedule_swipe_content)
            assertNotNull(results)
            assertTrue(results.getGlobalVisibleRect(area))
        }
        val startX = area.left + area.width() * (if (left) .85f else .15f)
        var endX = area.left + area.width() * (if (left) .15f else .85f)
        val startY = area.top + min(80f, area.height() * .25f)
        val endY = if (vertical) startY + min(180f, area.height() * .5f) else startY
        if (vertical) endX = startX
        val start = SystemClock.uptimeMillis()
        for (i in 0..12) {
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                12 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val progress = i / 12f
            val event = MotionEvent.obtain(
                start,
                SystemClock.uptimeMillis(),
                action,
                startX + (endX - startX) * progress,
                startY + (endY - startY) * progress,
                0
            )
            InstrumentationRegistry.getInstrumentation().sendPointerSync(event)
            event.recycle()
            SystemClock.sleep(16)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun days(query: String): NavigableSet<LocalDate> {
        try {
            return PlanBrowser.daysWithLessons(
                LessonTime.sorted(Snapshot.from(Demo.state(false).getJSONObject("snapshot")).records.values),
                query
            )
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }

    @Test
    fun daySwipesSkipEmptyDaysAndKeepSearch() {
        main {
            rule.activity.setCalendarQuery("Kowalska")
            rule.activity.navigate(5)
        }
        val initial = rule.activity.getCalendarDate()
        val next = days("Kowalska").higher(LocalDate.parse(initial))
        assertNotNull(next)
        swipe(left = true, vertical = false)
        assertEquals(next.toString(), rule.activity.getCalendarDate())
        assertEquals("Kowalska", rule.activity.getCalendarQuery())
        swipe(left = false, vertical = false)
        assertEquals(initial, rule.activity.getCalendarDate())
    }

    @Test
    fun weekSwipeSkipsEmptyWeeksAndVerticalScrollKeepsDate() {
        main {
            rule.activity.setCalendarMode("week")
            rule.activity.navigate(5)
        }
        val initial = LocalDate.parse(rule.activity.getCalendarDate())
        val expected = PlanBrowser.adjacentDay(days(""), initial, true, 1)
        assertNotNull(expected)
        swipe(left = true, vertical = false)
        assertEquals(expected.toString(), rule.activity.getCalendarDate())
        swipe(left = false, vertical = false)
        val previous = rule.activity.getCalendarDate()
        assertEquals(initial.with(DayOfWeek.MONDAY), LocalDate.parse(previous).with(DayOfWeek.MONDAY))
        swipe(left = true, vertical = true)
        assertEquals(previous, rule.activity.getCalendarDate())
    }

    @Test
    fun minimalHeaderKeepsSecondaryActionsInMenu() {
        main {
            rule.activity.setCalendarDate(LocalDate.now(UsosClient.ZONE).toString())
            rule.activity.navigate(5)
            val more = rule.activity.findViewById<View>(R.id.schedule_more)
            assertEquals(4, (more.parent as ViewGroup).childCount)
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(400, 3000)
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(image)
        try {
            FileOutputStream(File(rule.activity.getExternalFilesDir(null), "minimal-plan.png")).use { stream ->
                image.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        } finally {
            image.recycle()
        }
        Espresso.onView(ViewMatchers.withText(R.string.schedule_btn_today))
            .check(ViewAssertions.doesNotExist())
        PlanMenuTestHelper.select(rule.activity, rule.activity.getString(R.string.schedule_mode_week))
        assertEquals("week", rule.activity.getCalendarMode())
        PlanMenuTestHelper.select(rule.activity, rule.activity.getString(R.string.schedule_search_action))
        Espresso.closeSoftKeyboard()
        Espresso.onView(ViewMatchers.isAssignableFrom(EditText::class.java))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
}
