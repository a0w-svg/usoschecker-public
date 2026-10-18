package pl.vistula.checker;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import pl.vistula.checker.ui.ApiExplorerController;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;

@RunWith(AndroidJUnit4.class)
public class CredentialsAndNavigationRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);

    @Test public void activationResponsesAreNeitherDisplayedNorAvailableToCopyOrExport() throws Exception {
        MainActivity activity = rule.getActivity();
        JSONObject metadata = new JSONObject().put("name", "services/cards/mcard_order2")
            .put("auth_options", new JSONObject().put("consumer", "required").put("token", "required"))
            .put("arguments", new JSONArray());
        LinearLayout[] content = {null};
        for (String mime : new String[]{"application/json", "text/plain", "application/octet-stream"}) {
            main(() -> {
                content[0] = new LinearLayout(activity);
                ScrollView scroll = new ScrollView(activity); scroll.addView(content[0]);
                try {
                    ApiExplorerController.renderMethodDetails(activity, content[0], scroll, metadata, Collections.emptySet(), null, () -> {},
                        new ApiExplorerController.ApiCallback() {
                            public void onSelectUploadFile(String method) {}
                            public void onSaveResponse(UsosClient.Response response) { fail("Secret response exported"); }
                            public void onExecuteRequest(JSONObject meta, Map<String,String> values, String method, byte[] file,
                                    java.util.function.Consumer<UsosClient.Response> success, java.util.function.Consumer<String> failure) {
                                success.accept(new UsosClient.Response("{\"token\":\"private-activation-token\",\"distribution_pass\":\"private-password\"}"
                                    .getBytes(StandardCharsets.UTF_8), mime));
                            }
                        });
                } catch (Exception e) { throw new AssertionError(e); }
                find(content[0], activity.getString(R.string.api_execute_btn)).performClick();
            });
            onView(withText(R.string.api_btn_execute)).inRoot(isDialog()).perform(click());
            main(() -> {
                assertNotNull(find(content[0], activity.getString(R.string.api_activation_hidden)));
                assertFalse(allText(content[0]).contains("private-activation-token"));
                assertFalse(allText(content[0]).contains("private-password"));
                assertFalse(find(content[0], activity.getString(R.string.api_export_response)).isEnabled());
                assertFalse(find(content[0], activity.getString(R.string.api_copy_response)).isEnabled());
            });
        }
    }

    @Test public void historyDeepLinksSelectTheCategoryAndClearAnOldSearch() {
        main(() -> {
            MainActivity activity = rule.getActivity();
            activity.navigate(1);
            EditText search = input(activity.getWindow().getDecorView());
            assertNotNull(search); search.setText("old unrelated search");
            activity.onNewIntent(new Intent(activity, MainActivity.class).putExtra("showHistory", true).putExtra("historyKind", "ocena"));
            assertEquals("", input(activity.getWindow().getDecorView()).getText().toString());
            assertTrue(selectedLabel(activity.getWindow().getDecorView()).startsWith("Oceny ("));
            activity.onNewIntent(new Intent(activity, MainActivity.class).putExtra("showHistory", true).putExtra("historyKind", "plan"));
            assertTrue(selectedLabel(activity.getWindow().getDecorView()).startsWith("Plan ("));
        });
    }

    @Test public void gradeAndScheduleDestinationsOpenTheirOwnScreens() {
        main(() -> {
            MainActivity activity = rule.getActivity();
            activity.onNewIntent(new Intent(activity, MainActivity.class).putExtra("tab", 2));
            assertEquals(R.id.nav_item_grades, ((com.google.android.material.bottomnavigation.BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).getSelectedItemId());
            activity.onNewIntent(new Intent(activity, MainActivity.class).putExtra("tab", 5));
            assertEquals(R.id.nav_item_plan, ((com.google.android.material.bottomnavigation.BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).getSelectedItemId());
        });
    }

    private void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private View find(View view, String label) {
        if (view instanceof TextView && label.equals(((TextView) view).getText().toString())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), label); if (found != null) return found;
        }
        return null;
    }
    private String allText(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(((TextView) view).getText());
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) text.append(allText(((ViewGroup) view).getChildAt(i)));
        return text.toString();
    }
    private EditText input(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            EditText found = input(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private String selectedLabel(View view) {
        if (view instanceof TextView && view.isSelected() && (((TextView) view).getText().toString().startsWith("Oceny (")
                || ((TextView) view).getText().toString().startsWith("Plan ("))) return ((TextView) view).getText().toString();
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            String found = selectedLabel(((ViewGroup) view).getChildAt(i)); if (!found.isEmpty()) return found;
        }
        return "";
    }
}
