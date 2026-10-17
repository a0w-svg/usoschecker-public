package pl.vistula.checker;

import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.AttendanceTracker;
import pl.vistula.checker.ui.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AttendanceAndLoginUiTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    private SharedPreferences prefs;
    private java.util.Map<String, ?> original;
    @Before public void setUp() {
        Assume.assumeTrue("Use -PisolatedTests", rule.getActivity().getPackageName().endsWith(".qa"));
        prefs = rule.getActivity().getSharedPreferences("attendance_tracker", Context.MODE_PRIVATE);
        original = prefs.getAll();
        prefs.edit().clear().commit();
    }
    @After public void restore() {
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (String key : original.keySet()) {
            Object value = original.get(key);
            if (value instanceof String) editor.putString(key, (String) value);
        }
        editor.commit();
    }
    private interface Action { void run() throws Exception; }
    private void main(Action action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { action.run(); } catch (Exception e) { throw new AssertionError(e); }
        });
    }
    private View find(View root, String text) {
        if (root instanceof TextView && ((TextView) root).getText().toString().equals(text)) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = find(((ViewGroup) root).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
    private boolean visible(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        return !(view.getParent() instanceof View) || visible((View) view.getParent());
    }
    private LinearLayout attendance(String filter) {
        LinearLayout content = new LinearLayout(rule.getActivity());
        AttendanceScreenController.render(rule.getActivity(), content, new JSONObject(), filter, value -> {}, () -> {});
        return content;
    }
    @Test public void semesterAndClassTypeFiltersDoNotMixTotals() {
        main(() -> {
            JSONObject rows = new JSONObject()
                .put("one", AttendanceTracker.item("Wykład bieżący", "MAT", "now", "Wykład").put("absences", 2))
                .put("two", AttendanceTracker.item("Ćwiczenia bieżące", "MAT", "now", "Ćwiczenia").put("absences", 1))
                .put("old", AttendanceTracker.item("Wykład archiwalny", "MAT", "old", "Wykład").put("absences", 4));
            prefs.edit().putString("data", rows.toString()).putString("term", "now").putString("classType", "Wykład").commit();
            LinearLayout content = attendance("all");
            assertNotNull(find(content, "Wykład bieżący"));
            assertNull(find(content, "Ćwiczenia bieżące"));
            assertNull(find(content, "Wykład archiwalny"));
            assertNotNull(find(content, rule.getActivity().getString(R.string.attendance_stats_summary, 1, 2)));
            prefs.edit().putString("term", "old").commit();
            assertNotNull(find(attendance("exceeded"), "Wykład archiwalny"));
        });
    }
    @Test public void thirdAbsenceIsWarningAndFourthIsExceeded() {
        main(() -> {
            JSONObject rows = new JSONObject().put("three", AttendanceTracker.item("Trzy", "A", "now", "Wykład").put("absences", 3))
                .put("four", AttendanceTracker.item("Cztery", "B", "now", "Wykład").put("absences", 4));
            prefs.edit().putString("data", rows.toString()).putString("term", "now").commit();
            assertNotNull(find(attendance("warning"), "Trzy"));
            assertNull(find(attendance("exceeded"), "Trzy"));
            assertNotNull(find(attendance("exceeded"), "Cztery"));
        });
    }
    private LinearLayout account(JSONObject state) {
        LinearLayout content = new LinearLayout(rule.getActivity());
        AccountScreenController.render(rule.getActivity(), content, new JSONObject(), state, false, "", new AccountScreenController.AccountListener() {
            public void onBeginOAuth(String key, String secret, String scopes) {}
            public void onConfirmPin(String pin) {}
            public void onToggleEnabled(boolean enabled) {}
            public void onUpdateInterval(long interval) {}
            public void onDisconnect() {}
            public void onStartDemo() {}
            public void onEndDemo() {}
            public void onRequestNotifications() {}
        });
        return content;
    }
    @Test public void pendingLoginShowsPinAndHidesRestartUntilSettingsAreOpened() {
        main(() -> {
            LinearLayout content = account(new JSONObject().put("key", "test-key").put("secret", "test-secret").put("requestToken", "pending"));
            assertTrue(visible(find(content, rule.getActivity().getString(R.string.btn_confirm_pin))));
            View restart = find(content, rule.getActivity().getString(R.string.btn_login_browser));
            assertFalse(visible(restart));
            find(content, rule.getActivity().getString(R.string.login_settings)).performClick();
            assertTrue(visible(restart));
        });
    }
}
