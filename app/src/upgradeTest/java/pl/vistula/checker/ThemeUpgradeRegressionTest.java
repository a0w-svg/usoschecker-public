package pl.vistula.checker;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import pl.vistula.checker.ui.Appearance;
import pl.vistula.checker.ui.UiHelper;
import static org.junit.Assert.*;

/** Opt-in -PupgradeTests fixture, after installing a previous QA APK and replacing it with adb install -r. */
@RunWith(AndroidJUnit4.class)
public class ThemeUpgradeRegressionTest {
    @Test public void savedModeSurvivesInPlaceApkUpgrade() {
        String expected = InstrumentationRegistry.getArguments().getString("upgradeTheme");
        Assume.assumeTrue("Explicit upgrade fixture only", expected != null);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getPackageName().endsWith(".qa"));
        int mode = "light".equals(expected) ? AppCompatDelegate.MODE_NIGHT_NO
            : "dark".equals(expected) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        assertEquals("APK update must preserve appearance preferences", mode, Appearance.mode(context));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertEquals(!"light".equals(expected), UiHelper.isDark(activity)));
        }
    }
}
