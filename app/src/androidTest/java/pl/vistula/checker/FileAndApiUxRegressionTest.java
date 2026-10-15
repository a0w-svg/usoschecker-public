package pl.vistula.checker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
public class FileAndApiUxRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);

    @Test public void forgedFilePickerResultCannotOverwriteAPrivateFile() throws Exception {
        File victim = new File(rule.getActivity().getCacheDir(), "private-export-guard.txt");
        Files.write(victim.toPath(), "private-original".getBytes(StandardCharsets.UTF_8));
        androidx.test.espresso.intent.Intents.init();
        try {
            interceptPicker(Uri.fromFile(victim));
            main(() -> rule.getActivity().saveCalendar("attacker-overwrite"));
            onView(withText(R.string.error_invalid_document_uri)).inRoot(isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.matches(
                androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            assertEquals("private-original", new String(Files.readAllBytes(victim.toPath()), StandardCharsets.UTF_8));
        } finally { androidx.test.espresso.intent.Intents.release(); victim.delete(); }
    }

    @Test public void contentProviderExportTruncatesThePreviousDocument() throws Exception {
        MainActivity activity = rule.getActivity();
        File directory = new File(activity.getCacheDir(), "test-exports"); directory.mkdirs();
        File document = new File(directory, "truncate-test.ics");
        Files.write(document.toPath(), "old-private-document-that-is-much-longer".getBytes(StandardCharsets.UTF_8));
        Uri uri = androidx.core.content.FileProvider.getUriForFile(activity, activity.getPackageName() + ".debugexports", document);
        androidx.test.espresso.intent.Intents.init();
        try {
            interceptPicker(uri);
            main(() -> activity.saveCalendar("short"));
            long until = System.currentTimeMillis() + 5000;
            while (document.length() != 5 && System.currentTimeMillis() < until) Thread.sleep(50);
            assertEquals("short", new String(Files.readAllBytes(document.toPath()), StandardCharsets.UTF_8));
        } finally { androidx.test.espresso.intent.Intents.release(); document.delete(); }
    }

    @Test public void invalidAndCanceledApiRequestsPreserveTheLastSuccessfulResponse() throws Exception {
        MainActivity activity = rule.getActivity();
        JSONObject metadata = new JSONObject().put("name", "services/apisrv/installation")
            .put("auth_options", new JSONObject().put("consumer", "ignored").put("token", "ignored"))
            .put("arguments", new JSONArray().put(new JSONObject().put("name", "id").put("is_required", true)));
        LinearLayout[] content = {null};
        AtomicInteger requests = new AtomicInteger();
        main(() -> {
            content[0] = new LinearLayout(activity);
            ScrollView scroll = new ScrollView(activity); scroll.addView(content[0]);
            try {
                activity.apiDraft(metadata.getString("name")).put("id", "1");
                ApiExplorerController.renderMethodDetails(activity, content[0], scroll, metadata, Collections.emptySet(), null, () -> {},
                    new ApiExplorerController.ApiCallback() {
                        public void onSelectUploadFile(String method) {}
                        public void onSaveResponse(UsosClient.Response response) {}
                        public void onExecuteRequest(JSONObject meta, Map<String,String> values, String method, byte[] file,
                                java.util.function.Consumer<UsosClient.Response> success, java.util.function.Consumer<String> failure) {
                            requests.incrementAndGet();
                            success.accept(new UsosClient.Response("{\"result\":1}".getBytes(StandardCharsets.UTF_8), "application/json"));
                        }
                    });
            } catch (Exception e) { throw new AssertionError(e); }
            find(content[0], activity.getString(R.string.api_execute_btn)).performClick();
        });
        onView(withText(R.string.api_btn_execute)).inRoot(isDialog()).perform(click());
        main(() -> {
            assertTrue(find(content[0], activity.getString(R.string.api_export_response)).isEnabled());
            EditText input = input(content[0]); input.setText("");
            find(content[0], activity.getString(R.string.api_execute_btn)).performClick();
            assertNotNull(input.getError());
            assertTrue(find(content[0], activity.getString(R.string.api_export_response)).isEnabled());
            assertTrue(find(content[0], activity.getString(R.string.api_copy_response)).isEnabled());
            input.setText("1"); find(content[0], activity.getString(R.string.api_execute_btn)).performClick();
        });
        onView(withText(R.string.btn_cancel)).inRoot(isDialog()).perform(click());
        main(() -> assertTrue(find(content[0], activity.getString(R.string.api_export_response)).isEnabled()));
        assertEquals(1, requests.get());
    }

    private void interceptPicker(Uri uri) {
        androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(new android.app.Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent().setData(uri)));
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
    private EditText input(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            EditText found = input(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
}
