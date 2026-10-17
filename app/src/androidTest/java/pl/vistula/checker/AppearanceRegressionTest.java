package pl.vistula.checker;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.FileInputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;
import pl.vistula.checker.ui.Appearance;
import pl.vistula.checker.ui.UiHelper;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppearanceRegressionTest {
    private Context context;
    private SharedPreferences prefs;
    private Object previous;
    private int phoneMode;

    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue("Use isolated QA", context.getPackageName().endsWith(".qa"));
        prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE);
        previous = prefs.getAll().get("nightMode");
        phoneMode = context.getSystemService(UiModeManager.class).getNightMode();
        Assume.assumeTrue(phoneMode >= 0 && phoneMode <= 2);
    }

    @After public void restore() throws Exception {
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit().remove("nightMode");
        if (previous instanceof Integer) editor.putInt("nightMode", (Integer) previous);
        if (previous instanceof String) editor.putString("nightMode", (String) previous);
        editor.commit();
        shell("cmd uimode night " + (phoneMode == UiModeManager.MODE_NIGHT_AUTO ? "auto"
            : phoneMode == UiModeManager.MODE_NIGHT_YES ? "yes" : "no"));
        Appearance.apply(context);
    }

    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor pipe = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
             FileInputStream stream = new FileInputStream(pipe.getFileDescriptor())) {
            byte[] bytes = new byte[1024];
            while (stream.read(bytes) != -1) { /* Drain shell completion. */ }
        }
    }

    private void phone(boolean dark) throws Exception { shell("cmd uimode night " + (dark ? "yes" : "no")); }

    private void awaitTheme(ActivityScenario<MainActivity> scenario, boolean dark) {
        AtomicBoolean matched = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> matched.set(UiHelper.isDark(activity) == dark));
            if (matched.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Expected " + (dark ? "dark" : "light") + " theme after configuration change");
    }

    @Test public void defaultFollowsPhoneAndReactsWhileActivityIsOpen() throws Exception {
        prefs.edit().remove("nightMode").commit();
        Appearance.apply(context);
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, Appearance.mode(context));
        phone(false);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitTheme(scenario, false);
            phone(true);
            awaitTheme(scenario, true);
            phone(false);
            awaitTheme(scenario, false);
            scenario.recreate();
            awaitTheme(scenario, false);
        }
    }

    @Test public void manualOverridePersistsAndSystemOptionRestoresAutomaticChanges() throws Exception {
        phone(true);
        Appearance.setMode(context, AppCompatDelegate.MODE_NIGHT_NO);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitTheme(scenario, false);
            scenario.recreate();
            awaitTheme(scenario, false);
            Appearance.setMode(context, AppCompatDelegate.MODE_NIGHT_YES);
            awaitTheme(scenario, true);
            phone(false);
            awaitTheme(scenario, true);
            Appearance.setMode(context, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            awaitTheme(scenario, false);
            phone(true);
            awaitTheme(scenario, true);
        }
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, Appearance.mode(context));
    }

    @Test public void invalidLegacyPreferencesFallBackToPhoneWithoutCrashing() {
        for (Object value : new Object[]{"dark", 99, AppCompatDelegate.MODE_NIGHT_UNSPECIFIED}) {
            SharedPreferences.Editor editor = prefs.edit();
            if (value instanceof String) editor.putString("nightMode", (String) value);
            else editor.putInt("nightMode", (Integer) value);
            editor.commit();
            assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, Appearance.mode(context));
        }
    }
}
