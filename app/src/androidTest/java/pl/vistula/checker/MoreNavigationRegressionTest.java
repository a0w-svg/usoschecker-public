package pl.vistula.checker;

import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;

@RunWith(AndroidJUnit4.class)
public class MoreNavigationRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    private void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Before public void ready() {
        assertTrue(rule.getActivity().getPackageName().endsWith(".qa"));
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        boolean[] ready = {false};
        while (!ready[0] && android.os.SystemClock.uptimeMillis() < deadline) {
            main(() -> ready[0] = rule.getActivity().findViewById(R.id.progress_text).getVisibility() == View.GONE);
            if (!ready[0]) android.os.SystemClock.sleep(50);
        }
        assertTrue(ready[0]); main(() -> { rule.getActivity().setDemo(true); rule.getActivity().navigate(0); });
    }
    @Test public void barSwipesFollowTabOrderAndStopAtBothEnds() {
        for (int id : new int[]{R.id.nav_item_grades, R.id.nav_item_plan, R.id.nav_item_history, R.id.nav_item_more, R.id.nav_item_more}) {
            onView(withId(R.id.bottom_navigation)).perform(swipeLeft());
            onView(withId(id)).check(matches(isSelected()));
        }
        for (int id : new int[]{R.id.nav_item_history, R.id.nav_item_plan, R.id.nav_item_grades, R.id.nav_item_home, R.id.nav_item_home}) {
            onView(withId(R.id.bottom_navigation)).perform(swipeRight());
            onView(withId(id)).check(matches(isSelected()));
        }
    }
    @Test public void scheduleContentStillChangesDateWhileBarChangesTabs() {
        main(() -> { rule.getActivity().navigate(5); rule.getActivity().setCalendarDate("2026-10-19"); rule.getActivity().render(); });
        onView(withId(R.id.main_scroll)).perform(swipeLeft());
        main(() -> assertEquals("2026-10-20", rule.getActivity().getCalendarDate()));
        onView(withId(R.id.nav_item_plan)).check(matches(isSelected()));
        onView(withId(R.id.bottom_navigation)).perform(swipeRight());
        onView(withId(R.id.nav_item_grades)).check(matches(isSelected()));
    }
    @Test public void cancelledShortAndVerticalMotionsDoNotSelectTheTouchedTab() {
        main(() -> {
            View bar = rule.getActivity().findViewById(R.id.bottom_navigation);
            float x = bar.getWidth() / 2f, y = bar.getHeight() / 2f;
            gesture(bar, x, y, x, y + bar.getHeight() / 3f, MotionEvent.ACTION_UP);
            gesture(bar, x, y, x - bar.getWidth() / 3f, y, MotionEvent.ACTION_CANCEL);
            gesture(bar, x, y, x - 25, y, MotionEvent.ACTION_UP);
            assertTrue(rule.getActivity().findViewById(R.id.nav_item_home).isSelected());
        });
    }
    private void gesture(View view, float x, float y, float endX, float endY, int endAction) {
        long now = android.os.SystemClock.uptimeMillis();
        int[] actions = {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, endAction};
        for (int i = 0; i < actions.length; i++) {
            MotionEvent event = MotionEvent.obtain(now, now + i * 100, actions[i], i == 0 ? x : endX, i == 0 ? y : endY, 0);
            view.dispatchTouchEvent(event); event.recycle();
        }
    }
    @Test public void toolsBelongToMoreWhenSwipingAndTapsRemainAvailable() {
        main(() -> rule.getActivity().navigate(7));
        onView(withId(R.id.bottom_navigation)).perform(swipeRight());
        onView(withId(R.id.nav_item_history)).check(matches(isSelected()));
        onView(withId(R.id.nav_item_more)).perform(click());
        onView(withText(R.string.section_studies)).check(matches(isDisplayed()));
    }
    @Test public void newsSearchUsesCurrentTextAndClearsImmediately() {
        main(() -> rule.getActivity().navigate(11));
        onView(isAssignableFrom(EditText.class)).perform(replaceText("biuro"));
        TestKeyboard.hide(rule.getActivity());
        onView(withText("Godziny rektorskie w dniu 31 października")).check(matches(isDisplayed()));
        onView(withText("Harmonogram składania wniosków o stypendium rektora")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
        onView(isAssignableFrom(EditText.class)).perform(replaceText(""));
        TestKeyboard.hide(rule.getActivity());
        onView(withText("Harmonogram składania wniosków o stypendium rektora")).check(matches(isDisplayed()));
    }
    @Test public void moreUsesConsistentIconsAndAllToolsRenderWithoutLosingActions() throws Exception {
        main(() -> {
            rule.getActivity().navigate(6);
            android.widget.LinearLayout content = rule.getActivity().findViewById(R.id.main_content);
            int count = 0;
            for (int i = 0; i < content.getChildCount(); i++) {
                TextView icon = content.getChildAt(i).findViewById(R.id.tool_tile_icon);
                if (icon != null) { count++; assertEquals("", icon.getText().toString()); assertNotNull(icon.getCompoundDrawables()[0]); }
            }
            assertEquals(10, count);
        });
        screenshot("more");
        int[] tabs = {8, 9, 10, 11, 12, 13, 14, 3, 4};
        int[] titles = {R.string.tool_payments_title, R.string.tool_gpa_title, R.string.tool_campus_title, R.string.tool_news_title,
            R.string.tool_attendance_title, R.string.cards_title, R.string.documents_title, R.string.tool_api_title, R.string.nav_account};
        for (int i = 0; i < tabs.length; i++) {
            final int tab = tabs[i], title = titles[i];
            main(() -> { rule.getActivity().navigate(tab); assertTrue(contains(rule.getActivity().findViewById(R.id.main_content), rule.getActivity().getString(title))); });
            screenshot("tool-" + tab);
        }
    }
    private boolean contains(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (contains(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
    private void screenshot(String name) throws Exception {
        android.os.SystemClock.sleep(150);
        android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        java.io.File file = new java.io.File(rule.getActivity().getExternalFilesDir(null), name + ".png");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); }
        bitmap.recycle();
        try (android.os.ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("cp " + file.getAbsolutePath() + " /data/local/tmp/usos-" + name + ".png");
             java.io.FileInputStream result = new java.io.FileInputStream(fd.getFileDescriptor())) { while (result.read() != -1) {} }
    }
    @Test public void courseTestsShortcutOpensAnExistingApiDefinition() {
        main(() -> rule.getActivity().navigate(3));
        onView(withId(R.id.tool_screen_more)).perform(click());
        onView(withText(R.string.api_key_services_heading)).inRoot(isPlatformPopup()).perform(click());
        onView(withText(R.string.api_quick_tests)).inRoot(isPlatformPopup()).perform(click());
        onView(withText("crstests/participant2")).check(matches(isDisplayed()));
    }
}
