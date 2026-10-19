package pl.vistula.checker;

import android.app.Activity;
import androidx.test.platform.app.InstrumentationRegistry;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;

final class ToolMenuTestHelper {
    static boolean select(Activity activity, String text) {
        boolean[] available = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> available[0] = activity.findViewById(R.id.tool_screen_more) != null);
        if (!available[0]) return false;
        boolean root = false, document = false;
        for (int id : new int[]{R.string.api_all_methods, R.string.api_fav_filter, R.string.cards_refresh, R.string.cards_all_methods,
                R.string.documents_refresh, R.string.documents_all_methods, R.string.news_refresh_btn, R.string.payments_refresh_btn,
                R.string.tool_api_methods, R.string.attendance_btn_add_course}) if (activity.getString(id).equals(text)) root = true;
        for (int id : new int[]{R.string.documents_details, R.string.documents_download, R.string.documents_receive,
                R.string.documents_get_code, R.string.documents_upo, R.string.documents_upd}) if (activity.getString(id).equals(text)) document = true;
        if (!root && !document) return false;
        TestKeyboard.hide(activity);
        if (document) InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> activity.findViewById(R.id.document_more).performClick());
        else onView(withId(R.id.tool_screen_more)).perform(click());
        onView(withText(text)).inRoot(isPlatformPopup()).perform(click());
        return true;
    }
}
