package pl.vistula.checker;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import pl.vistula.checker.ui.Appearance;
import pl.vistula.checker.ui.UiHelper;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OledThemeTest {
    @Test public void oledSwitchPersistsAcrossRecreationAndRestartAndCanReturnToLight() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        org.junit.Assume.assumeTrue(context.getPackageName().endsWith(".qa"));
        int previous = Appearance.mode(context);
        try {
            context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putInt("nightMode", AppCompatDelegate.MODE_NIGHT_NO).commit();
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> a.navigate(6));
                onView(withText(R.string.appearance_title)).perform(scrollTo(), click());
                onView(withText("Ciemny OLED")).perform(click());
                scenario.onActivity(this::assertOled);
                scenario.recreate();
                scenario.onActivity(a -> {
                    assertOled(a);
                    // Check that screen rendering works after changing the theme.
                    for (int tab : new int[]{0, 2, 5, 7, 4, 6}) a.navigate(tab);
                    assertOled(a);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                android.graphics.Bitmap screenshot = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().takeScreenshot();
                assertNotNull(screenshot);
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                        new java.io.File(context.getExternalFilesDir(null), "oled-preview.png"))) {
                    assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
                } finally { screenshot.recycle(); }
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> { assertOled(a); Appearance.choose(a); });
                onView(withText("Jasny")).perform(click());
                scenario.onActivity(a -> {
                    assertFalse(UiHelper.isDark(a));
                    assertEquals(AppCompatDelegate.MODE_NIGHT_NO, Appearance.mode(a));
                    assertNotEquals(Color.BLACK, ((ColorDrawable) a.findViewById(R.id.main_root).getBackground()).getColor());
                    Appearance.choose(a);
                });
                onView(withText("Systemowy")).perform(click());
                scenario.onActivity(a -> assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, Appearance.mode(a)));
            }
        } finally {
            context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putInt("nightMode", previous).commit();
        }
    }

    private void assertOled(MainActivity activity) {
        assertTrue(UiHelper.isDark(activity));
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, Appearance.mode(activity));
        assertEquals(Color.BLACK, ((ColorDrawable) activity.findViewById(R.id.main_root).getBackground()).getColor());
        android.graphics.drawable.Drawable toolbarBackground = activity.findViewById(R.id.toolbar).getBackground();
        int toolbarColor = toolbarBackground instanceof ColorDrawable ? ((ColorDrawable) toolbarBackground).getColor()
            : ((com.google.android.material.shape.MaterialShapeDrawable) toolbarBackground).getFillColor().getDefaultColor();
        assertEquals(Color.BLACK, toolbarColor);
        for (int color : new int[]{UiHelper.INK, UiHelper.MUTED, UiHelper.GREEN, Color.rgb(160, 48, 40)})
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(
                UiHelper.foreground(activity, color), Color.rgb(24, 24, 24)) >= 4.5);
    }
}
