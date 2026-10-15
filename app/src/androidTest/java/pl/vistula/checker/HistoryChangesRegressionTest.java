package pl.vistula.checker;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.ui.HistoryScreenController;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HistoryChangesRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);

    @Test public void scheduleCardShowsHoursAndRoomWithoutAGradeLabel() throws Exception {
        Change change = new Change(lesson("09:00", "10:00", "204"), lesson("10:00", "11:00", "315"), 1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MainActivity activity = rule.getActivity();
            LinearLayout content = new LinearLayout(activity);
            HistoryScreenController.renderChangeCard(activity, content, change);
            String text = text(content);
            assertTrue(text.contains("09:00–10:00 → 10:00–11:00"));
            assertTrue(text.contains("204 → 315"));
            assertFalse(text.contains(activity.getString(R.string.change_grade) + ":"));
        });
    }

    @Test public void planFilterRepairsOldHistoryAndExcludesActualGradeEntries() throws Exception {
        JSONObject old = new Change(lesson("09:00", "10:00", "204"), lesson("10:00", "11:00", "315"), 1).json();
        old.getJSONObject("metadata").put("diffGrade", "09:00 → 10:00");
        Change grade = new Change(new Record("grade", "ocena", "Grade-only subject", "4", "", ""),
            new Record("grade", "ocena", "Grade-only subject", "5", "", ""), 2);
        JSONObject state = new JSONObject().put("history", new JSONArray().put(old).put(grade.json()));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MainActivity activity = rule.getActivity();
            LinearLayout content = new LinearLayout(activity);
            HistoryScreenController.render(activity, content, state, "plan", "", false, ignored -> {}, ignored -> {}, () -> {});
            String text = text(content);
            assertTrue(text.contains("Schedule-only subject"));
            assertFalse(text.contains("Grade-only subject"));
            assertFalse(text.contains(activity.getString(R.string.change_grade) + ":"));
        });
    }

    @Test public void gradeCardStillShowsItsGradeChange() throws Exception {
        Change grade = new Change(new Record("grade", "ocena", "Grade-only subject", "4", "", ""),
            new Record("grade", "ocena", "Grade-only subject", "5", "", ""), 2);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MainActivity activity = rule.getActivity();
            LinearLayout content = new LinearLayout(activity);
            HistoryScreenController.renderChangeCard(activity, content, grade);
            assertTrue(text(content).contains(activity.getString(R.string.change_grade) + ": 4 → 5"));
        });
    }

    private Record lesson(String start, String end, String room) throws Exception {
        return new Record("lesson", "plan", "Schedule-only subject", start + "–" + end + " · " + room, "", "2026-10-15", "",
            "2026-10-15 " + start + ":00", "2026-10-15 " + end + ":00", Double.NaN, new JSONObject().put("room", room));
    }
    private String text(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            text.append(text(((ViewGroup) view).getChildAt(i)));
        return text.toString();
    }
}
