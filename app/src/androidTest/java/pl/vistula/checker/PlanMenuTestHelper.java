package pl.vistula.checker;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.test.platform.app.InstrumentationRegistry;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;

final class PlanMenuTestHelper {
    static boolean select(Activity activity, String text) {
        boolean[] available = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            if (activity.findViewById(R.id.schedule_more) == null) return;
            for (int label : new int[]{R.string.schedule_day_label, R.string.schedule_mode_week, R.string.schedule_mode_agenda,
                R.string.schedule_btn_today, R.string.schedule_search_action, R.string.schedule_export_ics,
                R.string.schedule_share_plan, R.string.schedule_info, R.string.schedule_info_title, R.string.btn_refresh})
                if (text.equals(activity.getString(label))) available[0] = true;
        });
        if (!available[0]) return false;
        String label = text.equals(activity.getString(R.string.schedule_info)) ? activity.getString(R.string.schedule_info_title) : text;
        onView(withId(R.id.schedule_more)).perform(click());
        onView(withText(label)).inRoot(isPlatformPopup()).perform(click());
        return true;
    }
    static void revealSearch(Activity activity) {
        boolean[] hidden = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            EditText input = input(activity.getWindow().getDecorView());
            hidden[0] = activity.findViewById(R.id.schedule_more) != null && input != null && !input.isShown();
        });
        if (hidden[0]) { select(activity, activity.getString(R.string.schedule_search_action)); closeSoftKeyboard(); }
    }
    private static EditText input(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            EditText result = input(((ViewGroup) view).getChildAt(i)); if (result != null) return result;
        }
        return null;
    }
    static void assertExportDisabled(Activity activity) {
        onView(withId(R.id.schedule_more)).perform(click());
        try {
            onView(withText(R.string.schedule_export_ics)).inRoot(isPlatformPopup()).check((view, error) -> {
                if (error != null) throw error;
                boolean disabled = false;
                // Native menus disable the row; the title TextView itself remains enabled.
                for (View row = view; row != null; row = row.getParent() instanceof View ? (View) row.getParent() : null)
                    disabled |= !row.isEnabled();
                org.junit.Assert.assertTrue("Export menu row must be disabled", disabled);
            });
        } finally { pressBack(); }
    }
}
