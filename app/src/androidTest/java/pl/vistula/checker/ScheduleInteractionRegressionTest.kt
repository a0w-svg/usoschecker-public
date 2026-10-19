package pl.vistula.checker

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import org.hamcrest.Matchers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.UsosParser
import pl.vistula.checker.ui.UiHelper

@RunWith(AndroidJUnit4::class)
class ScheduleInteractionRegressionTest {
    @JvmField
    @Rule
    val rule = ActivityTestRule(MainActivity::class.java)

    private fun main(action: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Before
    fun isolated() {
        assertTrue(rule.activity.packageName.endsWith(".qa"))
        val deadline = SystemClock.uptimeMillis() + 10000
        val ready = booleanArrayOf(false)
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            main { ready[0] = rule.activity.findViewById<View>(R.id.progress_text).visibility == View.GONE }
            if (!ready[0]) SystemClock.sleep(50)
        }
        assertTrue("Startup must finish before changing test state", ready[0])
    }

    private fun gesture(left: Boolean, vertical: Boolean) {
        val area = Rect()
        main {
            val scroll = rule.activity.findViewById<ScrollView>(R.id.main_scroll)
            scroll.scrollTo(0, 0)
            assertTrue(scroll.getGlobalVisibleRect(area))
        }
        val startX = area.left + area.width() * (if (left) .85f else .15f)
        var endX = area.left + area.width() * (if (left) .15f else .85f)
        val startY = area.top + UiHelper.dp(rule.activity, 12).toFloat()
        val endY = startY + UiHelper.dp(rule.activity, if (vertical) 200 else 60).toFloat()
        if (vertical) endX = startX
        val start = SystemClock.uptimeMillis()
        for (i in 0..18) {
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                18 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val event = MotionEvent.obtain(
                start,
                SystemClock.uptimeMillis(),
                action,
                startX + (endX - startX) * i / 18f,
                startY + (endY - startY) * i / 18f,
                0
            )
            InstrumentationRegistry.getInstrumentation().sendPointerSync(event)
            event.recycle()
            SystemClock.sleep(16)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun diagonalSwipesAcrossHeaderWorkBothWaysWithoutRefreshing() {
        val refreshes = AtomicInteger()
        main {
            val a = rule.activity
            a.setDemo(true)
            a.setCalendarMode("day")
            a.setCalendarQuery("Kowalska")
            a.navigate(5)
            a.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).setOnRefreshListener { refreshes.incrementAndGet() }
        }
        val first = rule.activity.getCalendarDate()
        gesture(left = true, vertical = false)
        assertTrue(LocalDate.parse(rule.activity.getCalendarDate()).isAfter(LocalDate.parse(first)))
        gesture(left = false, vertical = false)
        assertEquals(first, rule.activity.getCalendarDate())
        SystemClock.sleep(500)
        main { assertFalse(rule.activity.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing) }
        assertEquals(0, refreshes.get())
    }

    @Test
    fun deliberateVerticalPullStillRefreshes() {
        val refreshes = AtomicInteger()
        main {
            val a = rule.activity
            a.setDemo(true)
            a.navigate(5)
            a.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).setOnRefreshListener { refreshes.incrementAndGet() }
        }
        val date = rule.activity.getCalendarDate()
        gesture(left = true, vertical = true)
        SystemClock.sleep(600)
        assertEquals(1, refreshes.get())
        assertEquals(date, rule.activity.getCalendarDate())
        main { rule.activity.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing = false }
    }

    @Test
    fun enteringPlanFocusesCurrentClassOnceAndKeepsManualScroll() {
        val now = LocalDateTime.now(UsosClient.ZONE).withNano(0)
        val snapshot = Snapshot(now.minusDays(2).toLocalDate().toString(), now.plusDays(2).toLocalDate().toString())
        for (i in 40 downTo 1) {
            val start = now.minusMinutes(30 + i * 10L)
            snapshot.add(lesson("past-$i", start, start.plusMinutes(5)))
        }
        snapshot.add(lesson("current", now.minusMinutes(10), now.plusMinutes(30)))
        snapshot.add(lesson("future", now.plusDays(1), now.plusDays(1).plusHours(1)))
        main {
            try {
                val a = rule.activity
                a.setDemo(false)
                a.display().put("snapshot", snapshot.json())
                a.navigate(0)
                a.setCalendarMode("agenda")
                a.setCalendarQuery("")
                a.setCalendarDate(now.plusDays(1).toLocalDate().toString())
                a.navigate(5)
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(300, 3000)
        main {
            val a = rule.activity
            val scroll = a.findViewById<ScrollView>(R.id.main_scroll)
            assertEquals(now.toLocalDate().toString(), a.getCalendarDate())
            val current = a.findViewById<View>(R.id.schedule_current_lesson)
            assertNotNull(current)
            assertTrue(scroll.scrollY > 0)
            val visible = Rect()
            assertTrue(current.getGlobalVisibleRect(visible))
            assertEquals(View.VISIBLE, current.findViewById<View>(R.id.lesson_item_current_badge).visibility)
            assertTrue(current.findViewById<TextView>(R.id.lesson_item_current_badge).text.toString().contains(a.getString(R.string.classes_ongoing)))
            scroll.scrollTo(0, 0)
            a.render()
        }
        main { assertEquals(0, rule.activity.findViewById<ScrollView>(R.id.main_scroll).scrollY) }
    }

    private fun lesson(id: String, start: LocalDateTime, end: LocalDateTime): Record {
        return Record(
            id,
            "plan",
            id,
            "${start.toLocalTime()} – ${end.toLocalTime()}",
            "",
            start.toLocalDate().toString(),
            "",
            start.format(UsosParser.TIME),
            end.format(UsosParser.TIME),
            Double.NaN,
            JSONObject()
        )
    }

    @Test
    fun toolbarIsCompactAndDateHasLargerTouchArea() {
        main {
            rule.activity.setDemo(true)
            rule.activity.navigate(5)
        }
        main {
            val a = rule.activity
            assertTrue(a.findViewById<View>(R.id.main_root) is LinearLayout)
        }
        Espresso.onView(
            ViewMatchers.withContentDescription(
                Matchers.startsWith(rule.activity.getString(R.string.schedule_btn_choose_date) + ":")
            )
        ).check { view, error ->
            if (error != null) throw error
            assertTrue(view.height >= UiHelper.dp(rule.activity, 64))
            assertEquals(20 * rule.activity.resources.displayMetrics.scaledDensity, (view as TextView).textSize, .5f)
        }
    }

    @Test
    fun explicitDateLinksStayOnTheirDayButDefaultEntryReturnsToCurrentClass() {
        val today = LocalDate.now(UsosClient.ZONE)
        main {
            val a = rule.activity
            a.setDemo(true)
            a.setCalendarMode("day")
            a.openScheduleDate(today.plusDays(1).toString())
            assertEquals(today.plusDays(1).toString(), a.getCalendarDate())
            a.navigate(0)
            a.navigate(5)
            assertEquals(today.toString(), a.getCalendarDate())
            assertNotNull(a.findViewById(R.id.schedule_current_lesson))
        }
    }

    @Test
    fun roomIsProminentAndNotRepeatedInSecondaryText() {
        main {
            rule.activity.setDemo(true)
            rule.activity.navigate(5)
        }
        main {
            val a = rule.activity
            val card = a.findViewById<View>(R.id.schedule_current_lesson)
            assertNotNull(card)
            val room = card.findViewById<TextView>(R.id.lesson_item_room)
            assertEquals(View.VISIBLE, room.visibility)
            assertEquals(a.getString(R.string.lesson_room_label, "101"), room.text.toString())
            assertTrue(room.typeface.isBold)
            assertEquals(18 * a.resources.displayMetrics.scaledDensity, room.textSize, .5f)
            assertFalse(card.findViewById<TextView>(R.id.lesson_item_details).text.toString().contains(a.getString(R.string.lesson_room_label, "101")))
            val bar = a.findViewById<View>(R.id.schedule_more).parent as ViewGroup
            for (i in 0 until bar.childCount) {
                assertTrue(bar.getChildAt(i).width >= UiHelper.dp(a, 56))
                assertTrue(bar.getChildAt(i).height >= UiHelper.dp(a, 64))
            }
        }
    }
}
