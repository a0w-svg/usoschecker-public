package pl.vistula.checker;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ScheduleSwipeRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    private void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Before public void setup() {
        assertTrue(rule.getActivity().getPackageName().endsWith(".qa"));
        main(() -> {
            rule.getActivity().setDemo(true);
            rule.getActivity().setCalendarDate("2026-10-31");
            rule.getActivity().setCalendarMode("day");
            rule.getActivity().navigate(5);
        });
    }
    private void swipe(boolean left, boolean vertical) {
        Rect area = new Rect();
        main(() -> {
            View results = rule.getActivity().findViewById(R.id.schedule_swipe_content);
            assertNotNull(results);
            assertTrue(results.getGlobalVisibleRect(area));
        });
        float startX = area.left + area.width() * (left ? .85f : .15f);
        float endX = area.left + area.width() * (left ? .15f : .85f);
        float startY = area.top + Math.min(80, area.height() * .25f);
        float endY = vertical ? startY + Math.min(180, area.height() * .5f) : startY;
        if (vertical) endX = startX;
        long start = SystemClock.uptimeMillis();
        for (int i = 0; i <= 12; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == 12 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            float progress = i / 12f;
            MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                startX + (endX - startX) * progress, startY + (endY - startY) * progress, 0);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(event);
            event.recycle();
            SystemClock.sleep(16);
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Test public void daySwipesCrossMonthBoundaryAndKeepSearch() {
        main(() -> { rule.getActivity().setCalendarQuery("finanse"); rule.getActivity().navigate(5); });
        swipe(true, false);
        assertEquals("2026-11-01", rule.getActivity().getCalendarDate());
        assertEquals("finanse", rule.getActivity().getCalendarQuery());
        swipe(false, false);
        assertEquals("2026-10-31", rule.getActivity().getCalendarDate());
    }
    @Test public void weekSwipeMovesSevenDaysAndVerticalScrollKeepsDate() {
        main(() -> { rule.getActivity().setCalendarMode("week"); rule.getActivity().navigate(5); });
        swipe(true, false);
        assertEquals("2026-11-07", rule.getActivity().getCalendarDate());
        swipe(false, false);
        assertEquals("2026-10-31", rule.getActivity().getCalendarDate());
        swipe(true, true);
        assertEquals("2026-10-31", rule.getActivity().getCalendarDate());
    }
    @Test public void minimalHeaderKeepsSecondaryActionsInMenu() throws Exception {
        main(() -> {
            rule.getActivity().setCalendarDate(java.time.LocalDate.now(UsosClient.ZONE).toString());
            rule.getActivity().navigate(5);
            View more = rule.getActivity().findViewById(R.id.schedule_more);
            assertEquals(4, ((android.view.ViewGroup) more.getParent()).getChildCount());
        });
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(400, 3000);
        android.graphics.Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(new java.io.File(
                rule.getActivity().getExternalFilesDir(null), "minimal-plan.png"))) {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        } finally { image.recycle(); }
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.schedule_btn_today))
            .check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
        PlanMenuTestHelper.select(rule.getActivity(), rule.getActivity().getString(R.string.schedule_mode_week));
        assertEquals("week", rule.getActivity().getCalendarMode());
        PlanMenuTestHelper.select(rule.getActivity(), rule.getActivity().getString(R.string.schedule_search_action));
        androidx.test.espresso.Espresso.closeSoftKeyboard();
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.EditText.class))
            .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
    }
}
