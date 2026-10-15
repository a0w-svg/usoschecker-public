package pl.vistula.checker;

import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;
import pl.vistula.checker.ui.ScheduleScreenController;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ScheduleBreaksUiTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    private Record lesson(String id, String time) { return new Record(id, "plan", id, time, "", "2026-10-15"); }
    private String render(String mode, String query, Record... lessons) throws Exception {
        Snapshot snapshot = new Snapshot("2026-10-12", "2026-10-19");
        for (Record lesson : lessons) snapshot.add(lesson);
        JSONObject data = new JSONObject().put("snapshot", snapshot.json());
        String[] output = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                MainActivity activity = rule.getActivity();
                activity.setCalendarQuery(query);
                LinearLayout content = new LinearLayout(activity);
                ScheduleScreenController.render(activity, content, data, new JSONObject(), false,
                    "2026-10-15", mode, 7, value -> {}, value -> {}, () -> {},
                    new ScheduleScreenController.ScheduleListener() {
                        public void onOpenTask(String title) {}
                        public void onAbsenceRecorded() {}
                    });
                output[0] = text(content);
            } catch (Exception e) { throw new AssertionError(e); }
        });
        return output[0];
    }
    @Test public void dayWeekAndAgendaShowBreakHoursAndDurationOnlyOnce() throws Exception {
        for (String mode : new String[]{"day", "week", "agenda"}) {
            String text = render(mode, "", lesson("a", "09:00 – 10:00"), lesson("b", "10:30 – 11:30"), lesson("c", "10:30 – 12:00"));
            assertEquals(1, text.split("Przerwa · 30 min", -1).length - 1);
            assertTrue(text.contains("10:00 – 10:30 · Czas wolny między zajęciami"));
        }
    }
    @Test public void searchDoesNotTreatHiddenMeetingAsFreeTime() throws Exception {
        String text = render("day", "visible", lesson("first", "09:00 – 10:00"),
            lesson("hidden", "10:00 – 11:00"), lesson("visible", "11:15 – 12:00"));
        assertTrue(text.contains("Przerwa · 15 min"));
        assertFalse(text.contains("Przerwa · 75 min"));
    }
    @Test public void overlappingOrTouchingLessonsHaveNoBreakCard() throws Exception {
        assertFalse(render("day", "", lesson("a", "09:00 – 12:00"), lesson("b", "10:00 – 11:00"),
            lesson("c", "12:00 – 13:00")).contains("Przerwa ·"));
    }
    private String text(View view) {
        StringBuilder result = new StringBuilder();
        if (view instanceof TextView) result.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            result.append(text(((ViewGroup) view).getChildAt(i)));
        return result.toString();
    }
}
