package pl.vistula.checker

import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.ui.Appearance
import pl.vistula.checker.ui.UiHelper

/** Opt-in -PupgradeTests fixture, after installing a previous QA APK and replacing it with adb install -r. */
@RunWith(AndroidJUnit4::class)
class ThemeUpgradeRegressionTest {
    @Test
    fun savedModeSurvivesInPlaceApkUpgrade() {
        val expected = InstrumentationRegistry.getArguments().getString("upgradeTheme")
        Assume.assumeTrue("Explicit upgrade fixture only", expected != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".qa"))
        val mode = when (expected) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        assertEquals("APK update must preserve appearance preferences", mode, Appearance.mode(context))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(expected != "light", UiHelper.isDark(activity))
            }
        }
    }
}
