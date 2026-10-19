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
        String[] modes = context.getResources().getStringArray(R.array.appearance_modes);
        try {
            context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .putInt("nightMode", AppCompatDelegate.MODE_NIGHT_NO).commit();
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> a.navigate(4));
                onView(withText(R.string.appearance_title)).perform(scrollTo(), click());
                onView(withText(modes[2])).perform(click());
                scenario.onActivity(this::assertOled);
                scenario.recreate();
                scenario.onActivity(a -> {
                    assertOled(a);
                    assertPalette(a);
                    a.setDemo(true);
                    a.navigate(0);
                    // Check that screen rendering works after changing the theme.
                    for (int tab : new int[]{0, 2, 5, 7, 4, 6}) a.navigate(tab);
                    assertOled(a);
                    a.navigate(2);
                    assertGradeBadges(a.findViewById(R.id.main_content));
                    a.navigate(0);
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
                onView(withText(modes[1])).perform(click());
                scenario.onActivity(a -> {
                    assertFalse(UiHelper.isDark(a));
                    assertEquals(AppCompatDelegate.MODE_NIGHT_NO, Appearance.mode(a));
                    assertNotEquals(Color.BLACK, ((ColorDrawable) a.findViewById(R.id.main_root).getBackground()).getColor());
                    assertPalette(a);
                    a.setDemo(true);
                    a.navigate(2);
                    assertGradeBadges(a.findViewById(R.id.main_content));
                    a.navigate(0);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                android.graphics.Bitmap lightScreenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                assertNotNull(lightScreenshot);
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                        new java.io.File(context.getExternalFilesDir(null), "light-preview.png"))) {
                    assertTrue(lightScreenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
                } finally { lightScreenshot.recycle(); }
                scenario.onActivity(Appearance::choose);
                onView(withText(modes[0])).perform(click());
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

        for (int color : new int[]{UiHelper.INK, UiHelper.MUTED, UiHelper.GREEN, Color.rgb(160, 48, 40)})
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(
                UiHelper.foreground(activity, color), Color.rgb(24, 24, 24)) >= 4.5);
    }

    private void assertPalette(MainActivity activity) {
        android.widget.Button primary = UiHelper.button(activity, "Primary", () -> {}, true);
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(primary.getCurrentTextColor(),
            primary.getBackgroundTintList().getDefaultColor()) >= 4.5);
        android.widget.Button secondary = UiHelper.button(activity, "Secondary", () -> {}, false);
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(secondary.getCurrentTextColor(),
            secondary.getBackgroundTintList().getDefaultColor()) >= 4.5);
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setMessage("Palette check").setPositiveButton(android.R.string.ok, null).show();
        assertEquals(activity.getColor(R.color.app_ink), dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getCurrentTextColor());
        dialog.dismiss();
        for (int layout : new int[]{R.layout.item_grade_card, R.layout.item_grade_component, R.layout.item_lesson_card}) {
            android.view.View view = activity.getLayoutInflater().inflate(layout, null);
            int id = layout == R.layout.item_grade_card ? R.id.grade_item_value_badge
                : layout == R.layout.item_grade_component ? R.id.grade_comp_value : R.id.lesson_item_conflict_badge;
            android.widget.TextView badge = view.findViewById(id);
            assertTrue("Badge must be readable in either theme", androidx.core.graphics.ColorUtils.calculateContrast(
                badge.getCurrentTextColor(), activity.getColor(R.color.app_secondary_container)) >= 4.5);
        }
        assertNotEquals(activity.getColor(R.color.app_background), activity.getColor(R.color.app_card));
        assertEquals(activity.getColor(R.color.app_ink), UiHelper.foreground(activity, UiHelper.GREEN));
    }

    private void assertGradeBadges(android.view.View view) {
        if (view instanceof android.widget.TextView && (view.getId() == R.id.grade_item_value_badge
                || view.getId() == R.id.grade_comp_value)) {
            android.graphics.drawable.Drawable background = view.getBackground();
            int surface = background instanceof android.graphics.drawable.GradientDrawable
                ? ((android.graphics.drawable.GradientDrawable) background).getColor().getDefaultColor()
                : view.getContext().getColor(R.color.app_background);
            assertTrue("Rendered grade text contrast", androidx.core.graphics.ColorUtils.calculateContrast(
                ((android.widget.TextView) view).getCurrentTextColor(), surface) >= 4.5);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) assertGradeBadges(parent.getChildAt(i));
        }
    }
}
