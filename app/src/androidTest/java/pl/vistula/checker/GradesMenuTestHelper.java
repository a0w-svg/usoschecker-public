package pl.vistula.checker;

import android.app.Activity;
import android.widget.EditText;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.platform.app.InstrumentationRegistry;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.*;

final class GradesMenuTestHelper {
    static boolean select(Activity activity, String text) {
        boolean[] available = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> available[0] = activity.findViewById(R.id.grades_more) != null);
        if (!available[0]) return false;
        int submenu = 0;
        for (int id : new int[]{R.string.grades_chip_all, R.string.grades_type_labs_label, R.string.grades_type_exercises_label,
                R.string.grades_chip_numeric, R.string.grades_chip_other, R.string.grades_chip_retakes})
            if (text.equals(activity.getString(id))) submenu = R.string.grades_filter_type;
        for (int id : new int[]{R.string.grades_sort_subject, R.string.grades_sort_recent, R.string.grades_sort_value})
            if (text.equals(activity.getString(id))) submenu = R.string.grades_sort_title;
        boolean direct = false;
        for (int id : new int[]{R.string.grades_search_action, R.string.grades_summary_title, R.string.grades_calc_button,
                R.string.grades_refresh, R.string.grades_filter_reset, R.string.grades_data_status})
            if (text.equals(activity.getString(id))) direct = true;
        if (submenu == 0 && !direct) return false;
        TestKeyboard.hide(activity);
        onView(withId(R.id.grades_more)).perform(click());
        if (submenu != 0) onView(withText(submenu)).inRoot(isPlatformPopup()).perform(click());
        onView(withText(text)).inRoot(isPlatformPopup()).perform(click());
        return true;
    }
    static void revealSearch(Activity activity) {
        boolean[] hidden = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            EditText input = input(activity.getWindow().getDecorView());
            hidden[0] = activity.findViewById(R.id.grades_more) != null && input != null && !input.isShown();
        });
        if (hidden[0]) { select(activity, activity.getString(R.string.grades_search_action)); TestKeyboard.hide(activity); }
    }
    static void selectSemester(Activity activity, int index) {
        TestKeyboard.hide(activity); onView(withId(R.id.grades_semester)).perform(click());
        onData(org.hamcrest.Matchers.anything()).inRoot(isDialog()).atPosition(index).perform(click());
    }
    private static EditText input(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            EditText result = input(((ViewGroup) view).getChildAt(i)); if (result != null) return result;
        }
        return null;
    }
}
