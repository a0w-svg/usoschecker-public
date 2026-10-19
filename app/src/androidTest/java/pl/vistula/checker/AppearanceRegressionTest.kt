package pl.vistula.checker

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.ui.Appearance
import pl.vistula.checker.ui.UiHelper

@RunWith(AndroidJUnit4::class)
class AppearanceRegressionTest {
    private lateinit var context: Context
    private var prefs: SharedPreferences? = null
    private var previous: Any? = null
    private var phoneMode: Int = 0

    @Before
    fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue("Use isolated QA", context.packageName.endsWith(".qa"))
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        prefs = p
        previous = p.all["nightMode"]
        phoneMode = context.getSystemService(UiModeManager::class.java).nightMode
        Assume.assumeTrue(phoneMode in 0..2)
    }

    @After
    fun restore() {
        val p = prefs ?: return
        val editor = p.edit().remove("nightMode")
        when (val prev = previous) {
            is Int -> editor.putInt("nightMode", prev)
            is String -> editor.putString("nightMode", prev)
        }
        editor.commit()
        val modeArg = when (phoneMode) {
            UiModeManager.MODE_NIGHT_AUTO -> "auto"
            UiModeManager.MODE_NIGHT_YES -> "yes"
            else -> "no"
        }
        shell("cmd uimode night $modeArg")
        Appearance.apply(context)
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { pipe ->
            FileInputStream(pipe.fileDescriptor).use { stream ->
                val bytes = ByteArray(1024)
                while (stream.read(bytes) != -1) {
                    /* Drain shell completion. */
                }
            }
        }
    }

    private fun phone(dark: Boolean) {
        shell("cmd uimode night " + if (dark) "yes" else "no")
    }

    private fun awaitTheme(scenario: ActivityScenario<MainActivity>, dark: Boolean) {
        val matched = AtomicBoolean()
        val deadline = SystemClock.uptimeMillis() + 10000
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity -> matched.set(UiHelper.isDark(activity) == dark) }
            if (matched.get()) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Expected " + (if (dark) "dark" else "light") + " theme after configuration change")
    }

    @Test
    fun defaultFollowsPhoneAndReactsWhileActivityIsOpen() {
        prefs!!.edit().remove("nightMode").commit()
        Appearance.apply(context)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, Appearance.mode(context))
        phone(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTheme(scenario, false)
            phone(true)
            awaitTheme(scenario, true)
            phone(false)
            awaitTheme(scenario, false)
            scenario.recreate()
            awaitTheme(scenario, false)
        }
    }

    @Test
    fun manualOverridePersistsAndSystemOptionRestoresAutomaticChanges() {
        phone(true)
        Appearance.setMode(context, AppCompatDelegate.MODE_NIGHT_NO)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitTheme(scenario, false)
            scenario.recreate()
            awaitTheme(scenario, false)
            Appearance.setMode(context, AppCompatDelegate.MODE_NIGHT_YES)
            awaitTheme(scenario, true)
            phone(false)
            awaitTheme(scenario, true)
            Appearance.setMode(context, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            awaitTheme(scenario, false)
            phone(true)
            awaitTheme(scenario, true)
        }
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, Appearance.mode(context))
    }

    @Test
    fun invalidLegacyPreferencesFallBackToPhoneWithoutCrashing() {
        for (value in arrayOf<Any>("dark", 99, AppCompatDelegate.MODE_NIGHT_UNSPECIFIED)) {
            val editor = prefs!!.edit()
            if (value is String) editor.putString("nightMode", value)
            else editor.putInt("nightMode", value as Int)
            editor.commit()
            assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, Appearance.mode(context))
        }
    }
}
