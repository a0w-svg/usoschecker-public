package pl.vistula.checker;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.view.ViewCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import pl.vistula.checker.ui.TasksScreenController;
import pl.vistula.checker.ui.UiHelper;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;

@RunWith(AndroidJUnit4.class)
public class CleanUxRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    @Before public void isolatedOnly() {
        Assume.assumeTrue(rule.getActivity().getPackageName().endsWith(".qa"));
    }
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    @Test public void taskEditorKeepsPrivateDraftsOutOfViewStateAndAutofill() {
        main(() -> {
            View editor = LayoutInflater.from(rule.getActivity()).inflate(R.layout.dialog_task_input, null);
            for (int id : new int[]{R.id.task_input_title, R.id.task_input_notes}) {
                EditText field = editor.findViewById(id);
                assertFalse(field.isSaveEnabled());
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, field.getImportantForAutofill());
                assertNotEquals(0, field.getImeOptions() & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            }
            TextInputLayout title = editor.findViewById(R.id.task_title_layout);
            TextInputLayout notes = editor.findViewById(R.id.task_notes_layout);
            assertTrue(title.isCounterEnabled()); assertEquals(200, title.getCounterMaxLength());
            assertTrue(notes.isCounterEnabled()); assertEquals(2000, notes.getCounterMaxLength());
        });
    }
    @Test public void sharedControlsExposeHeadingsAndDisabledState() {
        main(() -> {
            MainActivity activity = rule.getActivity();
            LinearLayout content = new LinearLayout(activity);
            UiHelper.title(activity, content, "Title", "Description");
            assertTrue(ViewCompat.isAccessibilityHeading(content.getChildAt(0)));
            View tile = UiHelper.toolTile(activity, content, "*", "Tasks", "Manage deadlines", () -> {});
            assertEquals("Tasks. Manage deadlines", tile.getContentDescription());
            Button button = UiHelper.button(activity, "Save", () -> {}, true);
            int enabled = button.getCurrentTextColor();
            button.setEnabled(false);
            assertNotEquals(enabled, button.getCurrentTextColor());
            EditText search = UiHelper.searchField(activity, content, "Search", "", "Search", null);
            assertNotEquals(0, search.getImeOptions() & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        });
    }
    @Test public void invalidTaskStaysOpenWithoutSaving() {
        AtomicInteger saves = new AtomicInteger();
        main(() -> TasksScreenController.showTaskDialog(rule.getActivity(), null, "", 0, new TasksScreenController.TaskCallback() {
            public void onSaveTask(JSONObject task) { saves.incrementAndGet(); }
            public void onMutateTask(String id, boolean remove) {}
        }));
        onView(withText(R.string.btn_save)).inRoot(isDialog()).perform(click());
        onView(withId(R.id.task_input_title)).inRoot(isDialog()).check((view, error) -> {
            if (error != null) throw error;
            assertNotNull(((EditText) view).getError()); assertTrue(view.hasFocus());
        });
        assertEquals(0, saves.get());
        onView(withText(R.string.btn_cancel)).inRoot(isDialog()).perform(click());
    }
    @Test public void taskActionsIdentifyTheirTaskAndWeekCountMatchesFilter() throws Exception {
        JSONObject task = new JSONObject().put("id", "fixture").put("title", "Projekt testowy")
            .put("notes", "Example notes").put("due", java.time.LocalDate.now(UsosClient.ZONE).plusDays(7).toString());
        main(() -> {
            try {
                MainActivity activity = rule.getActivity(); LinearLayout content = new LinearLayout(activity);
                TasksScreenController.render(activity, content, new JSONArray().put(task), "all", "due", "",
                    value -> {}, value -> {}, value -> {}, new TasksScreenController.TaskCallback() {
                        public void onSaveTask(JSONObject value) {}
                        public void onMutateTask(String id, boolean remove) {}
                    });
                for (int id : new int[]{R.id.task_item_btn_edit, R.id.task_item_btn_toggle, R.id.task_item_btn_delete})
                    assertTrue(content.findViewById(id).getContentDescription().toString().contains("Projekt testowy"));
                assertEquals(3, ((TextView) content.findViewById(R.id.task_item_notes)).getMaxLines());
                assertTrue(hasText(content, activity.getString(R.string.tasks_chip_week) + " (0)"));
            } catch (JSONException e) { throw new AssertionError(e); }
        });
    }
    private boolean hasText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), text)) return true;
        }
        return false;
    }
    @Test public void taskActionsFitNarrowScreenWithLargeFonts() {
        main(() -> {
            Configuration config = new Configuration(rule.getActivity().getResources().getConfiguration()); config.fontScale = 2f;
            Context context = new ContextThemeWrapper(rule.getActivity().createConfigurationContext(config), R.style.AppTheme);
            View card = LayoutInflater.from(context).inflate(R.layout.item_task_card, null);
            card.measure(View.MeasureSpec.makeMeasureSpec(UiHelper.dp(context, 280), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());
            for (int id : new int[]{R.id.task_item_btn_edit, R.id.task_item_btn_delete, R.id.task_item_btn_toggle}) {
                TextView button = card.findViewById(id);
                assertTrue(button.getMeasuredHeight() >= UiHelper.dp(context, 48));
                assertEquals(0, button.getLayout().getEllipsisCount(button.getLayout().getLineCount() - 1));
            }
        });
    }
    @Test public void captureExampleEditorForVisualReview() throws Exception {
        main(() -> TasksScreenController.showTaskDialog(rule.getActivity(), null, "Przygotować projekt", 0,
            new TasksScreenController.TaskCallback() {
                public void onSaveTask(JSONObject value) {}
                public void onMutateTask(String id, boolean remove) {}
            }));
        onView(withId(R.id.task_input_title)).inRoot(isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // Espresso's displayed check can complete while the platform dialog is still fading in.
        android.os.SystemClock.sleep(500);
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        File output = new File(rule.getActivity().getExternalFilesDir(null), "ux-task-editor.png");
        try (FileOutputStream stream = new FileOutputStream(output)) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        screenshot.recycle();
        onView(withText(R.string.btn_cancel)).inRoot(isDialog()).perform(click());
    }
}
