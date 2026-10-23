package pl.vistula.checker

import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.ui.Appearance
import pl.vistula.checker.ui.UiHelper

@RunWith(AndroidJUnit4::class)
class OledThemeTest {
    @Test
    fun oledSwitchPersistsAcrossRecreationAndRestartAndCanReturnToLight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue(context.packageName.endsWith(".qa"))
        val previous = Appearance.mode(context)
        val modes = context.resources.getStringArray(R.array.appearance_modes)
        try {
            context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putInt("nightMode", AppCompatDelegate.MODE_NIGHT_NO).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { a ->
                    a.navigate(4)
                    a.openAccountPage("settings.appearance")
                }
                onView(withText(R.string.appearance_title)).perform(scrollTo(), click())
                onView(withText(modes[2])).perform(click())
                scenario.onActivity(::assertOled)
                scenario.recreate()
                scenario.onActivity { a ->
                    assertOled(a)
                    assertPalette(a)
                    a.setDemo(true)
                    a.navigate(0)
                    // Check that screen rendering works after changing the theme.
                    for (tab in intArrayOf(0, 2, 5, 7, 4, 6)) a.navigate(tab)
                    assertOled(a)
                    a.navigate(2)
                    assertGradeBadges(a.findViewById(R.id.main_content))
                    a.navigate(0)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                assertNotNull(screenshot)
                try {
                    FileOutputStream(File(context.getExternalFilesDir(null), "oled-preview.png")).use { output ->
                        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                } finally {
                    screenshot.recycle()
                }
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { a ->
                    assertOled(a)
                    Appearance.choose(a)
                }
                onView(withText(modes[1])).perform(click())
                scenario.onActivity { a ->
                    assertFalse(UiHelper.isDark(a))
                    assertEquals(AppCompatDelegate.MODE_NIGHT_NO, Appearance.mode(a))
                    assertNotEquals(Color.BLACK, (a.findViewById<View>(R.id.main_root).background as ColorDrawable).color)
                    assertPalette(a)
                    a.setDemo(true)
                    a.navigate(2)
                    assertGradeBadges(a.findViewById(R.id.main_content))
                    a.navigate(0)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val lightScreenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                assertNotNull(lightScreenshot)
                try {
                    FileOutputStream(File(context.getExternalFilesDir(null), "light-preview.png")).use { output ->
                        assertTrue(lightScreenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                } finally {
                    lightScreenshot.recycle()
                }
                scenario.onActivity { Appearance.choose(it) }
                onView(withText(modes[0])).perform(click())
                scenario.onActivity { a -> assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, Appearance.mode(a)) }
            }
        } finally {
            context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putInt("nightMode", previous).commit()
        }
    }

    private fun assertOled(activity: MainActivity) {
        assertTrue(UiHelper.isDark(activity))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, Appearance.mode(activity))
        assertEquals(Color.BLACK, (activity.findViewById<View>(R.id.main_root).background as ColorDrawable).color)

        for (color in intArrayOf(UiHelper.INK, UiHelper.MUTED, UiHelper.GREEN, Color.rgb(160, 48, 40))) {
            assertTrue(ColorUtils.calculateContrast(UiHelper.foreground(activity, color), Color.rgb(24, 24, 24)) >= 4.5)
        }
    }

    private fun assertPalette(activity: MainActivity) {
        val primary = UiHelper.button(activity, "Primary", {}, true)
        assertTrue(
            ColorUtils.calculateContrast(
                primary.currentTextColor,
                primary.backgroundTintList!!.defaultColor
            ) >= 4.5
        )
        val secondary = UiHelper.button(activity, "Secondary", {}, false)
        assertTrue(
            ColorUtils.calculateContrast(
                secondary.currentTextColor,
                secondary.backgroundTintList!!.defaultColor
            ) >= 4.5
        )
        val dialog = MaterialAlertDialogBuilder(activity)
            .setMessage("Palette check")
            .setPositiveButton(android.R.string.ok, null)
            .show()
        assertEquals(activity.getColor(R.color.app_ink), dialog.getButton(DialogInterface.BUTTON_POSITIVE).currentTextColor)
        dialog.dismiss()
        for (layout in intArrayOf(R.layout.item_grade_card, R.layout.item_grade_component, R.layout.item_lesson_card)) {
            val view = activity.layoutInflater.inflate(layout, null)
            val id = when (layout) {
                R.layout.item_grade_card -> R.id.grade_item_value_badge
                R.layout.item_grade_component -> R.id.grade_comp_value
                else -> R.id.lesson_item_conflict_badge
            }
            val badge = view.findViewById<TextView>(id)
            assertTrue(
                "Badge must be readable in either theme",
                ColorUtils.calculateContrast(
                    badge.currentTextColor,
                    activity.getColor(R.color.app_secondary_container)
                ) >= 4.5
            )
        }
        assertNotEquals(activity.getColor(R.color.app_background), activity.getColor(R.color.app_card))
        assertEquals(activity.getColor(R.color.app_accent), UiHelper.foreground(activity, UiHelper.GREEN))
    }

    private fun assertGradeBadges(view: View) {
        if (view is TextView && (view.id == R.id.grade_item_value_badge || view.id == R.id.grade_comp_value)) {
            val background = view.background
            val surface = if (background is GradientDrawable) {
                background.color!!.defaultColor
            } else {
                view.context.getColor(R.color.app_background)
            }
            assertTrue(
                "Rendered grade text contrast",
                ColorUtils.calculateContrast(view.currentTextColor, surface) >= 4.5
            )
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) assertGradeBadges(view.getChildAt(i))
        }
    }
}
