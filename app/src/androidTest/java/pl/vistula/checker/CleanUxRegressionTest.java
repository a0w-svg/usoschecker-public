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
    @Test public void homeIsCleanAndSettingsOwnAllPreferences() {
        main(() -> {
            MainActivity activity = rule.getActivity();
            activity.setDemo(true);
            activity.navigate(0);
            View content = activity.findViewById(R.id.main_content);
            for (int label : new int[]{R.string.hero_heading, R.string.monitoring_title,
                    R.string.home_week_title, R.string.academic_calendar_title,
                    R.string.quick_refresh, R.string.btn_simulate_changes}) {
                assertFalse(activity.getString(label), hasText(content, activity.getString(label)));
            }
            activity.navigate(6);
            content = activity.findViewById(R.id.main_content);
            assertFalse(hasText(content, activity.getString(R.string.appearance_title)));
            assertFalse(hasText(content, activity.getString(R.string.about_title)));
            activity.navigate(4);
            content = activity.findViewById(R.id.main_content);
            for (int label : new int[]{R.string.appearance_title, R.string.account_notices_heading,
                    R.string.account_widgets_heading, R.string.academic_calendar_title, R.string.about_title}) {
                assertTrue(activity.getString(label), hasText(content, activity.getString(label)));
            }
            // The title is supplied once by the main toolbar, not repeated in the content.
            assertFalse(hasText(content, activity.getString(R.string.nav_account)));
        });
    }
    @Test public void timetableOptionsAreLeftOfDateNavigation() {
        main(() -> {
            MainActivity activity = rule.getActivity();
            activity.setDemo(true);
            activity.navigate(5);
            View menu = activity.findViewById(R.id.schedule_more);
            assertNotNull(menu);
            android.view.ViewGroup row = (android.view.ViewGroup) menu.getParent();
            assertSame(menu, row.getChildAt(0));
            assertEquals(activity.getString(R.string.schedule_options), menu.getContentDescription());
        });
    }
    @Test public void timetableTechnicalFieldsAreAvailableOnlyAfterRequestingFullData() {
        main(() -> {
            try {
                pl.vistula.checker.core.Record lesson = new pl.vistula.checker.core.Record("lesson", "plan", "Finanse",
                    "09:00 – 10:30\nSala 201", "", "2026-10-18", "2026Z", "", "", Double.NaN,
                    new JSONObject().put("room", "201").put("unitId", "unit-fixture"));
                pl.vistula.checker.ui.ScheduleScreenController.showLessonDetails(rule.getActivity(),
                    pl.vistula.checker.core.LessonTime.of(lesson), new pl.vistula.checker.ui.ScheduleScreenController.ScheduleListener() {
                        public void onOpenTask(String title) {}
                        public void onAbsenceRecorded() {}
                    });
            } catch (Exception e) { throw new AssertionError(e); }
        });
        onView(withText("Sala 201")).inRoot(isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
        onView(withText(org.hamcrest.Matchers.containsString("unit-fixture"))).inRoot(isDialog())
            .check(androidx.test.espresso.assertion.ViewAssertions.matches(org.hamcrest.Matchers.not(isDisplayed())));
        onView(withText(R.string.grades_full_data)).inRoot(isDialog()).perform(click());
        onView(withText(org.hamcrest.Matchers.containsString("unit-fixture"))).inRoot(isDialog())
            .check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
        onView(withText(R.string.btn_close)).inRoot(isDialog()).perform(click());
    }
    @Test public void subjectDetailsExpandAndCollapseWithoutHidingTheGradeOverview() {
        main(() -> {
            try {
                MainActivity activity = rule.getActivity();
                pl.vistula.checker.core.Snapshot snapshot = new pl.vistula.checker.core.Snapshot("", "");
                snapshot.add(new pl.vistula.checker.core.Record("grade", "ocena", "Finanse", "5", "", "", "2026Z",
                    "", "", 5, new JSONObject().put("courseId", "FIN").put("comment", "Komentarz testowy")));
                LinearLayout content = new LinearLayout(activity);
                pl.vistula.checker.ui.GradesScreenController.render(activity, content,
                    new JSONObject().put("snapshot", snapshot.json()), new JSONObject(), false,
                    "", "all", "name", "", false, value -> {}, value -> {}, value -> {}, value -> {}, value -> {},
                    () -> {}, () -> {}, new pl.vistula.checker.ui.GradesScreenController.GradesListener() {
                        public void onFilterChanged() {}
                        public void onOpenTask(String title) {}
                    });
                View components = content.findViewById(R.id.grade_item_components_container);
                View comment = content.findViewById(R.id.grade_item_comment);
                Button more = content.findViewById(R.id.subject_view_more);
                assertEquals(View.VISIBLE, content.findViewById(R.id.grade_item_value_badge).getVisibility());
                assertEquals(View.GONE, components.getVisibility());
                assertEquals(View.GONE, comment.getVisibility());
                more.performClick();
                assertEquals(View.VISIBLE, components.getVisibility());
                assertEquals(View.VISIBLE, comment.getVisibility());
                assertEquals(activity.getString(R.string.subject_view_less), more.getText().toString());
                more.performClick();
                assertEquals(View.GONE, components.getVisibility());
                assertEquals(View.GONE, comment.getVisibility());
                assertEquals(activity.getString(R.string.subject_view_more), more.getText().toString());
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }
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
