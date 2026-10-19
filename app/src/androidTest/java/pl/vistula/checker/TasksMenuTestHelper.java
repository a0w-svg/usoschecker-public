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

final class TasksMenuTestHelper {
    static boolean select(Activity activity, String text) {
        boolean[] available = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> available[0] = activity.findViewById(R.id.tasks_more) != null);
        if (!available[0]) return false;
        if (text.equals(activity.getString(R.string.btn_add_task))) {
            onView(withId(R.id.tasks_add)).perform(click()); return true;
        }
        int submenu = 0;
        for (int id : new int[]{R.string.tasks_chip_all, R.string.tasks_chip_open, R.string.tasks_chip_done,
                R.string.tasks_platon_title, R.string.tasks_chip_today, R.string.tasks_chip_week, R.string.tasks_chip_overdue})
            if (text.equals(activity.getString(id))) submenu = R.string.tasks_filter_title;
        for (int id : new int[]{R.string.tasks_sort_due, R.string.tasks_sort_priority, R.string.tasks_sort_title})
            if (text.equals(activity.getString(id))) submenu = R.string.tasks_sort_note;
        boolean direct = text.equals(activity.getString(R.string.tasks_search_action))
            || text.equals(activity.getString(R.string.tasks_platon_open)) || text.equals(activity.getString(R.string.tasks_platon_add));
        boolean rowAction = false;
        for (int id : new int[]{R.string.tasks_btn_edit, R.string.tasks_btn_delete, R.string.tasks_btn_mark_done, R.string.tasks_btn_mark_open})
            if (text.equals(activity.getString(id))) rowAction = true;
        if (submenu == 0 && !direct && !rowAction) return false;
        if (rowAction) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> activity.findViewById(R.id.task_item_more).performClick());
            onView(withText(text)).inRoot(isPlatformPopup()).perform(click()); return true;
        }
        TestKeyboard.hide(activity);
        onView(withId(R.id.tasks_more)).perform(click());
        if (submenu != 0) onView(withText(submenu)).inRoot(isPlatformPopup()).perform(click());
        onView(withText(org.hamcrest.Matchers.startsWith(text))).inRoot(isPlatformPopup()).perform(click());
        return true;
    }
    static void revealSearch(Activity activity) {
        boolean[] hidden = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            EditText input = input(activity.getWindow().getDecorView());
            hidden[0] = activity.findViewById(R.id.tasks_more) != null && input != null && !input.isShown();
        });
        if (hidden[0]) { select(activity, activity.getString(R.string.tasks_search_action)); TestKeyboard.hide(activity); }
    }
    private static EditText input(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            EditText result = input(((ViewGroup) view).getChildAt(i)); if (result != null) return result;
        }
        return null;
    }
}
