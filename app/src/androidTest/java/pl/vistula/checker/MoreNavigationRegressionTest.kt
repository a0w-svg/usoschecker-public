package pl.vistula.checker

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.get
import androidx.core.view.size
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreNavigationRegressionTest {
    @JvmField
    @Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    private fun main(action: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Before
    fun ready() {
        assertTrue(rule.activity.packageName.endsWith(".qa"))
        val deadline = SystemClock.uptimeMillis() + 10000
        val ready = booleanArrayOf(false)
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            main { ready[0] = rule.activity.findViewById<View>(R.id.progress_text).visibility == View.GONE }
            if (!ready[0]) SystemClock.sleep(50)
        }
        assertTrue(ready[0])
        main {
            rule.activity.getSharedPreferences("tool_layout", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            rule.activity.setDemo(true)
            rule.activity.selectCampus("0000")
            rule.activity.navigate(0)
        }
    }

    @Test
    fun barSwipesFollowTabOrderAndStopAtBothEnds() {
        for (id in intArrayOf(
            R.id.nav_item_plan,
            R.id.nav_item_grades,
            R.id.nav_item_history,
            R.id.nav_item_more,
            R.id.nav_item_more
        )) {
            onView(withId(R.id.bottom_navigation)).perform(swipeLeft())
            main { assertNavigation(id) }
            onView(withId(id)).check(matches(isSelected()))
        }
        for (id in intArrayOf(
            R.id.nav_item_history,
            R.id.nav_item_grades,
            R.id.nav_item_plan,
            R.id.nav_item_home,
            R.id.nav_item_home
        )) {
            onView(withId(R.id.bottom_navigation)).perform(swipeRight())
            main { assertNavigation(id) }
            onView(withId(id)).check(matches(isSelected()))
        }
    }

    private fun assertNavigation(expected: Int) {
        val bar = rule.activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        val states = (0 until bar.menu.size).joinToString { index ->
            val item = bar.menu[index]
            "${item.title}:checked=${item.isChecked},selected=${bar.findViewById<View>(item.itemId).isSelected}"
        }
        assertEquals("Navigation state after gesture: $states; width=${bar.width}, viewport=${(bar.parent as View).width}", expected, bar.selectedItemId)
    }

    @Test
    fun scheduleContentStillChangesDateWhileBarChangesTabs() {
        val today = LocalDate.now(UsosClient.ZONE)
        main {
            rule.activity.setCalendarQuery("")
            rule.activity.setCalendarMode("day")
            rule.activity.navigate(5)
            rule.activity.setCalendarDate(today.toString())
            rule.activity.render()
        }
        onView(withId(R.id.main_scroll)).perform(swipeLeft())
        main { assertEquals(today.plusDays(1).toString(), rule.activity.getCalendarDate()) }
        onView(withId(R.id.nav_item_plan)).check(matches(isSelected()))
        onView(withId(R.id.bottom_navigation)).perform(swipeRight())
        onView(withId(R.id.nav_item_home)).check(matches(isSelected()))
    }

    @Test
    fun cancelledShortAndVerticalMotionsDoNotSelectTheTouchedTab() {
        main {
            val bar = rule.activity.findViewById<View>(R.id.bottom_navigation)
            val x = bar.width / 2f
            val y = bar.height / 2f
            gesture(bar, x, y, x, y + bar.height / 3f, MotionEvent.ACTION_UP)
            gesture(bar, x, y, x - bar.width / 3f, y, MotionEvent.ACTION_CANCEL)
            gesture(bar, x, y, x - 25, y, MotionEvent.ACTION_UP)
            assertTrue(rule.activity.findViewById<View>(R.id.nav_item_home).isSelected)
        }
    }

    private fun gesture(view: View, x: Float, y: Float, endX: Float, endY: Float, endAction: Int) {
        val now = SystemClock.uptimeMillis()
        val actions = intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, endAction)
        for (i in actions.indices) {
            val event = MotionEvent.obtain(now, now + i * 100, actions[i], if (i == 0) x else endX, if (i == 0) y else endY, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    @Test
    fun toolsBelongToMoreWhenSwipingAndTapsRemainAvailable() {
        main { rule.activity.navigate(7) }
        onView(withId(R.id.bottom_navigation)).perform(swipeRight())
        onView(withId(R.id.nav_item_history)).check(matches(isSelected()))
        onView(withId(R.id.nav_item_more)).perform(click())
        onView(withText(R.string.section_studies)).check(matches(isDisplayed()))
    }

    @Test
    fun newsSearchUsesCurrentTextAndClearsImmediately() {
        main { rule.activity.navigate(11) }
        onView(isAssignableFrom(EditText::class.java)).perform(replaceText("biuro"))
        TestKeyboard.hide(rule.activity)
        onView(withText("Godziny rektorskie w dniu 31 października")).check(matches(isDisplayed()))
        onView(withText("Harmonogram składania wniosków o stypendium rektora")).check(doesNotExist())
        onView(isAssignableFrom(EditText::class.java)).perform(replaceText(""))
        TestKeyboard.hide(rule.activity)
        onView(withText("Harmonogram składania wniosków o stypendium rektora")).check(matches(isDisplayed()))
    }

    @Test
    fun moreUsesConsistentIconsAndAllToolsRenderWithoutLosingActions() {
        main {
            rule.activity.navigate(6)
            val content = rule.activity.findViewById<LinearLayout>(R.id.main_content)
            var count = 0
            fun verifyRows(view: View) {
                if (view.id == R.id.more_entry_root) {
                    count++
                    assertTrue(view.isClickable && view.isFocusable)
                }
                if (view is ViewGroup) repeat(view.childCount) { verifyRows(view.getChildAt(it)) }
            }
            verifyRows(content)
            val expected = pl.vistula.checker.ui.MoreScreenController
                .layout(pl.vistula.checker.core.AccountRole.mode(rule.activity.serviceAccountState()))
                .sumOf { it.entries.size }
            assertEquals(expected, count)
        }
        screenshot("more")
        val tabs = intArrayOf(7, 8, 9, 10, 11, 12, 13, 14, 3, 4)
        val titles = intArrayOf(
            R.string.tool_tasks_title,
            R.string.tool_payments_title,
            R.string.tool_gpa_title,
            R.string.tool_campus_title,
            R.string.tool_news_title,
            R.string.tool_attendance_title,
            R.string.cards_title,
            R.string.documents_title,
            R.string.tool_api_title,
            R.string.nav_account
        )
        for (i in tabs.indices) {
            val tab = tabs[i]
            val title = titles[i]
            main {
                rule.activity.navigate(tab)
                assertTrue(contains(rule.activity.findViewById(R.id.main_content), rule.activity.getString(title)))
            }
            screenshot("tool-$tab")
        }
    }

    @Test
    fun settingsPagesKeepTheSelectedSectionAndReturnToTheOverview() {
        main {
            val activity = rule.activity
            activity.navigate(4)
            var content = activity.findViewById<LinearLayout>(R.id.main_content)
            fun find(view: View, text: String): TextView? {
                if (view is TextView && text.contentEquals(view.text)) return view
                if (view is ViewGroup) for (i in 0 until view.childCount) {
                    val found = find(view.getChildAt(i), text)
                    if (found != null) return found
                }
                return null
            }
            val appearance = activity.getString(R.string.appearance_title)
            assertTrue(find(content, appearance)?.visibility == View.VISIBLE)
            assertTrue(find(content, appearance)?.isShown == false)
            find(content, activity.getString(R.string.settings_personalization))!!.performClick()
            assertTrue(find(content, appearance)!!.isShown)
            activity.render()
            content = activity.findViewById(R.id.main_content)
            assertTrue(find(content, appearance)!!.isShown)
            find(content, activity.getString(R.string.settings_back))!!.performClick()
            assertTrue(!find(content, appearance)!!.isShown)
        }
    }

    @Test
    fun branchPreviewDoesNotShowWarsawAddressOrContacts() {
        main {
            val activity = rule.activity
            activity.selectCampus("O0000")
            activity.navigate(10)
            val content = activity.findViewById<LinearLayout>(R.id.main_content)
            assertTrue(contains(content, "Olsztyn"))
            assertTrue(!contains(content, activity.getString(R.string.campus_address)))
            assertTrue(!contains(content, activity.getString(R.string.campus_dziekanat_call)))
            activity.selectCampus("0000")
        }
    }

    private fun contains(view: View, text: String): Boolean {
        if (view is TextView && text.contentEquals(view.text)) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (contains(view.getChildAt(i), text)) return true
            }
        }
        return false
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(150)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val file = File(rule.activity.getExternalFilesDir(null), "$name.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cp " + file.absolutePath + " /data/local/tmp/usos-" + name + ".png").use { fd ->
                FileInputStream(fd.fileDescriptor).use { result ->
                    while (result.read() != -1) {}
                }
            }
    }

    @Test
    fun courseTestsShortcutOpensAnExistingApiDefinition() {
        main { rule.activity.navigate(3) }
        onView(withId(R.id.tool_screen_more)).perform(click())
        onView(withText(R.string.api_key_services_heading)).inRoot(isPlatformPopup()).perform(click())
        onView(withText(R.string.api_quick_tests)).inRoot(isPlatformPopup()).perform(click())
        onView(withText("crstests/participant2")).check(matches(isDisplayed()))
    }
}
