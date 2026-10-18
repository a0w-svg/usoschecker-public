package pl.vistula.checker;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;
import pl.vistula.checker.ui.UiHelper;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ScheduleInteractionRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    private void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Before public void isolated() {
        assertTrue(rule.getActivity().getPackageName().endsWith(".qa"));
        long deadline = SystemClock.uptimeMillis() + 10000;
        boolean[] ready = {false};
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            main(() -> ready[0] = rule.getActivity().findViewById(R.id.progress_text).getVisibility() == View.GONE);
            if (!ready[0]) SystemClock.sleep(50);
        }
        assertTrue("Startup must finish before changing test state", ready[0]);
    }
    private void gesture(boolean left, boolean vertical) {
        Rect area = new Rect();
        main(() -> {
            ScrollView scroll = rule.getActivity().findViewById(R.id.main_scroll);
            scroll.scrollTo(0, 0);
            assertTrue(scroll.getGlobalVisibleRect(area));
        });
        float startX = area.left + area.width() * (left ? .85f : .15f);
        float endX = area.left + area.width() * (left ? .15f : .85f);
        float startY = area.top + UiHelper.dp(rule.getActivity(), 12);
        float endY = startY + UiHelper.dp(rule.getActivity(), vertical ? 200 : 60);
        if (vertical) endX = startX;
        long start = SystemClock.uptimeMillis();
        for (int i = 0; i <= 18; i++) {
            MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), i == 0 ? MotionEvent.ACTION_DOWN
                : i == 18 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE,
                startX + (endX - startX) * i / 18f, startY + (endY - startY) * i / 18f, 0);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(event);
            event.recycle(); SystemClock.sleep(16);
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Test public void diagonalSwipesAcrossHeaderWorkBothWaysWithoutRefreshing() {
        AtomicInteger refreshes = new AtomicInteger();
        main(() -> {
            MainActivity a = rule.getActivity(); a.setDemo(true); a.setCalendarMode("day");
            a.setCalendarQuery("Kowalska"); a.navigate(5);
            ((SwipeRefreshLayout) a.findViewById(R.id.swipe_refresh)).setOnRefreshListener(refreshes::incrementAndGet);
        });
        String first = rule.getActivity().getCalendarDate();
        gesture(true, false);
        assertTrue(LocalDate.parse(rule.getActivity().getCalendarDate()).isAfter(LocalDate.parse(first)));
        gesture(false, false);
        assertEquals(first, rule.getActivity().getCalendarDate());
        SystemClock.sleep(500);
        main(() -> assertFalse(((SwipeRefreshLayout) rule.getActivity().findViewById(R.id.swipe_refresh)).isRefreshing()));
        assertEquals(0, refreshes.get());
    }
    @Test public void deliberateVerticalPullStillRefreshes() {
        AtomicInteger refreshes = new AtomicInteger();
        main(() -> {
            MainActivity a = rule.getActivity(); a.setDemo(true); a.navigate(5);
            ((SwipeRefreshLayout) a.findViewById(R.id.swipe_refresh)).setOnRefreshListener(refreshes::incrementAndGet);
        });
        String date = rule.getActivity().getCalendarDate();
        gesture(true, true);
        SystemClock.sleep(600);
        assertEquals(1, refreshes.get());
        assertEquals(date, rule.getActivity().getCalendarDate());
        main(() -> ((SwipeRefreshLayout) rule.getActivity().findViewById(R.id.swipe_refresh)).setRefreshing(false));
    }
    @Test public void enteringPlanFocusesCurrentClassOnceAndKeepsManualScroll() throws Exception {
        LocalDateTime now = LocalDateTime.now(UsosClient.ZONE).withNano(0);
        Snapshot snapshot = new Snapshot(now.minusDays(2).toLocalDate().toString(), now.plusDays(2).toLocalDate().toString());
        for (int i = 40; i >= 1; i--) {
            LocalDateTime start = now.minusMinutes(30 + i * 10L);
            snapshot.add(lesson("past-" + i, start, start.plusMinutes(5)));
        }
        snapshot.add(lesson("current", now.minusMinutes(10), now.plusMinutes(30)));
        snapshot.add(lesson("future", now.plusDays(1), now.plusDays(1).plusHours(1)));
        main(() -> {
            try {
                MainActivity a = rule.getActivity(); a.setDemo(false);
                a.display().put("snapshot", snapshot.json()); a.navigate(0);
                a.setCalendarMode("agenda"); a.setCalendarQuery("");
                a.setCalendarDate(now.plusDays(1).toLocalDate().toString()); a.navigate(5);
            } catch (Exception e) { throw new AssertionError(e); }
        });
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(300, 3000);
        main(() -> {
            MainActivity a = rule.getActivity(); ScrollView scroll = a.findViewById(R.id.main_scroll);
            assertEquals(now.toLocalDate().toString(), a.getCalendarDate());
            View current = a.findViewById(R.id.schedule_current_lesson); assertNotNull(current);
            assertTrue(scroll.getScrollY() > 0);
            Rect visible = new Rect(); assertTrue(current.getGlobalVisibleRect(visible));
            assertEquals(View.VISIBLE, current.findViewById(R.id.lesson_item_current_badge).getVisibility());
            assertTrue(((TextView) current.findViewById(R.id.lesson_item_current_badge)).getText().toString().contains(a.getString(R.string.classes_ongoing)));
            scroll.scrollTo(0, 0); a.render();
        });
        main(() -> assertEquals(0, ((ScrollView) rule.getActivity().findViewById(R.id.main_scroll)).getScrollY()));
    }
    private Record lesson(String id, LocalDateTime start, LocalDateTime end) {
        return new Record(id, "plan", id, start.toLocalTime() + " – " + end.toLocalTime(), "", start.toLocalDate().toString(), "",
            start.format(pl.vistula.checker.core.UsosParser.TIME), end.format(pl.vistula.checker.core.UsosParser.TIME), Double.NaN, new JSONObject());
    }
    @Test public void toolbarIsCompactAndDateHasLargerTouchArea() {
        main(() -> { rule.getActivity().setDemo(true); rule.getActivity().navigate(5); });
        main(() -> {
            MainActivity a = rule.getActivity();
            assertEquals(UiHelper.dp(a, 48), a.findViewById(R.id.toolbar).getHeight());
            assertNull(((com.google.android.material.appbar.MaterialToolbar) a.findViewById(R.id.toolbar)).getSubtitle());
        });
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(
            org.hamcrest.Matchers.startsWith(rule.getActivity().getString(R.string.schedule_btn_choose_date) + ":")))
            .check((view, error) -> {
                if (error != null) throw error;
                assertTrue(view.getHeight() >= UiHelper.dp(rule.getActivity(), 64));
                assertEquals(20 * rule.getActivity().getResources().getDisplayMetrics().scaledDensity, ((TextView) view).getTextSize(), .5f);
            });
    }
    @Test public void explicitDateLinksStayOnTheirDayButDefaultEntryReturnsToCurrentClass() {
        LocalDate today = LocalDate.now(UsosClient.ZONE);
        main(() -> {
            MainActivity a = rule.getActivity(); a.setDemo(true); a.setCalendarMode("day");
            a.openScheduleDate(today.plusDays(1).toString());
            assertEquals(today.plusDays(1).toString(), a.getCalendarDate());
            a.navigate(0); a.navigate(5);
            assertEquals(today.toString(), a.getCalendarDate());
            assertNotNull(a.findViewById(R.id.schedule_current_lesson));
        });
    }
    @Test public void roomIsProminentAndNotRepeatedInSecondaryText() {
        main(() -> { rule.getActivity().setDemo(true); rule.getActivity().navigate(5); });
        main(() -> {
            MainActivity a = rule.getActivity();
            View card = a.findViewById(R.id.schedule_current_lesson); assertNotNull(card);
            TextView room = card.findViewById(R.id.lesson_item_room);
            assertEquals(View.VISIBLE, room.getVisibility());
            assertEquals("Sala 101", room.getText().toString());
            assertTrue(room.getTypeface().isBold());
            assertEquals(20 * a.getResources().getDisplayMetrics().scaledDensity, room.getTextSize(), .5f);
            assertFalse(((TextView) card.findViewById(R.id.lesson_item_details)).getText().toString().contains("Sala "));
            android.view.ViewGroup bar = (android.view.ViewGroup) a.findViewById(R.id.schedule_more).getParent();
            for (int i = 0; i < bar.getChildCount(); i++) {
                assertTrue(bar.getChildAt(i).getWidth() >= UiHelper.dp(a, 56));
                assertTrue(bar.getChildAt(i).getHeight() >= UiHelper.dp(a, 64));
            }
        });
    }
}
