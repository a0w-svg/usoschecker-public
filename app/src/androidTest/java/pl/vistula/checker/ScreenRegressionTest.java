package pl.vistula.checker;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ScreenRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    private void main(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private View find(View root, String text) {
        if (root instanceof TextView && ((TextView) root).getText().toString().equals(text)) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View result = find(((ViewGroup) root).getChildAt(i), text); if (result != null) return result;
        }
        return null;
    }
    private EditText input(View root) {
        if (root instanceof EditText) return (EditText) root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            EditText result = input(((ViewGroup) root).getChildAt(i)); if (result != null) return result;
        }
        return null;
    }
    private View decor() { return rule.getActivity().getWindow().getDecorView(); }
    private void has(String text) { main(() -> assertNotNull(text, find(decor(), text))); }
    private void absent(String text) { main(() -> assertNull(text, find(decor(), text))); }
    private void search(String query) { PlanMenuTestHelper.revealSearch(rule.getActivity()); main(() -> { assertNotNull(input(decor())); input(decor()).setText(query); }); }
    private void click(String text) {
        if (PlanMenuTestHelper.select(rule.getActivity(), text)) return;
        main(() -> { View view = find(decor(), text); assertNotNull(text, view); assertTrue(view.performClick()); });
    }
    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(400, 3000);
        android.graphics.Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        java.io.File file = new java.io.File(rule.getActivity().getExternalFilesDir(null), name + ".png");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        } finally { screenshot.recycle(); }
    }
    @Before public void demo() throws Exception {
        assertTrue("Regression tests must use isolated app data", rule.getActivity().getPackageName().endsWith(".qa"));
        long deadline = System.currentTimeMillis() + 10000;
        boolean[] ready = {false};
        while (!ready[0] && System.currentTimeMillis() < deadline) {
            main(() -> { rule.getActivity().navigate(4); ready[0] = find(decor(), rule.getActivity().getString(R.string.btn_connect)) != null; });
            if (!ready[0]) Thread.sleep(100);
        }
        assertTrue(ready[0]);
        main(() -> rule.getActivity().setDemo(true));
    }
    @Test public void gradesSearchUpdatesImmediatelyAndClears() throws Exception {
        main(() -> rule.getActivity().navigate(2));
        capture("grades-improved");
        search("finansów"); has("Podstawy finansów"); absent("Programowanie obiektowe");
        search("no-such-grade"); has(rule.getActivity().getString(R.string.grades_no_match)); absent("Podstawy finansów");
        search(""); has("Programowanie obiektowe");
    }
    @Test public void historySearchAndActionFilterUpdateImmediately() throws Exception {
        main(() -> rule.getActivity().sync());
        capture("changes-improved");
        search("315"); has("Statystyka opisowa"); absent("Podstawy finansów");
        search("no-such-change"); has(rule.getActivity().getString(R.string.history_no_match_title));
        search(""); click(rule.getActivity().getString(R.string.history_chip_modified, 2));
        has("Podstawy finansów"); has("Statystyka opisowa");
    }
    @Test public void timetableWeekSearchPersistsAcrossNavigation() throws Exception {
        // The demo statistics class is tomorrow, which belongs to the next week on Sundays.
        main(() -> {
            rule.getActivity().navigate(5);
            rule.getActivity().setCalendarDate(java.time.LocalDate.now(UsosClient.ZONE).plusDays(1).toString());
            rule.getActivity().render();
        });
        click(rule.getActivity().getString(R.string.schedule_mode_week));
        capture("timetable-improved");
        search("Kowalska"); has("Statystyka opisowa"); absent("Programowanie obiektowe");
        main(() -> { rule.getActivity().navigate(2); rule.getActivity().navigate(5); });
        main(() -> assertEquals("Kowalska", input(decor()).getText().toString()));
        search("no-such-lesson"); absent("Statystyka opisowa");
        PlanMenuTestHelper.assertExportDisabled(rule.getActivity());
    }
    @Test public void weeklyTasksExcludeOverdueAndCompletedEntries() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now(UsosClient.ZONE);
        org.json.JSONObject due = new org.json.JSONObject().put("id", "week-due").put("title", "Due this week")
            .put("due", today.plusDays(6).toString()).put("done", false);
        org.json.JSONObject overdue = new org.json.JSONObject().put("id", "week-late").put("title", "Overdue task")
            .put("due", today.minusDays(1).toString()).put("done", false);
        main(() -> { rule.getActivity().savePersonalTask(due); rule.getActivity().savePersonalTask(overdue); rule.getActivity().navigate(0); });
        capture("dashboard-improved");
        main(() -> rule.getActivity().openTasks("week"));
        has("Due this week"); absent("Overdue task");
        main(() -> rule.getActivity().openTasks("overdue"));
        has("Overdue task"); absent("Due this week");
    }
    @Test public void calendarExportOpensSystemDocumentPicker() {
        main(() -> rule.getActivity().navigate(5));
        click(rule.getActivity().getString(R.string.schedule_mode_agenda));
        androidx.test.espresso.intent.Intents.init();
        try {
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
                .respondWith(new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null));
            click(rule.getActivity().getString(R.string.schedule_export_ics));
            androidx.test.espresso.intent.Intents.intended(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT));
        } finally { androidx.test.espresso.intent.Intents.release(); }
    }
    private void awaitText(String text) throws Exception {
        long until = System.currentTimeMillis() + 10000; boolean[] found = {false};
        while (!found[0] && System.currentTimeMillis() < until) {
            main(() -> found[0] = find(decor(), text) != null);
            if (!found[0]) Thread.sleep(100);
        }
        assertTrue("Missing " + text, found[0]);
    }
    private void dialogHas(String text) {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot()).check((view, error) ->
            assertNotNull(text, find(view, text)));
    }
    @Test public void cardDemoHasPhysicalAndMobileStatusAndDoesNotUseLiveAccount() throws Exception {
        main(() -> rule.getActivity().navigate(13));
        has("Aleksandra Nowak · DEMO"); has(rule.getActivity().getString(R.string.mcard_ready));
        capture("student-cards");
        main(() -> rule.getActivity().showMobileActivation("demo-order"));
        has(rule.getActivity().getString(R.string.mcard_ready));
        assertFalse(new Repository(rule.getActivity()).read().toString().contains("demo-order"));
    }
    @Test public void documentSearchAndPrefilledDetailsUseTheSelectedDocument() throws Exception {
        main(() -> rule.getActivity().navigate(14));
        has("Zaświadczenie o studiowaniu"); has("Decyzja stypendialna");
        capture("student-documents");
        search("zaswiadczenie"); has("Zaświadczenie o studiowaniu"); absent("Decyzja stypendialna");
        click(rule.getActivity().getString(R.string.documents_details));
        awaitText("adm_documents/document");
        has("demo-doc-1");
        main(() -> { rule.getActivity().navigate(14); });
        main(() -> assertEquals("zaswiadczenie", input(decor()).getText().toString()));
    }
    @Test public void apiModuleShortcutsAndPolishSearchReachEveryModule() throws Exception {
        main(() -> rule.getActivity().openApiModule("cards"));
        has("cards/mcard2\nGet information about student card and related mcard (mLegitymacje)");
        main(() -> rule.getActivity().openApiModule(""));
        search("legitymacje");
        has("cards/mcard2\nGet information about student card and related mcard (mLegitymacje)");
        absent("adm_documents/document\nGet information on administration document by id.");
        main(() -> assertEquals("legitymacje", input(decor()).getText().toString()));
        capture("api-modules");
        main(() -> rule.getActivity().openApiMethod("services/cards/mcard_order2", java.util.Map.of("order_id", "42")));
        awaitText("cards/mcard_order2"); has("42");
        assertTrue(rule.getActivity().apiDraft("services/cards/mcard_order2").getString("order_id").equals("42"));
    }
    @Test public void gradeSubjectDetailsIncludeAllComponentsAndCompleteStoredData() throws Exception {
        main(() -> rule.getActivity().navigate(2)); search("finansów");
        has(rule.getActivity().getString(R.string.grades_group_entry_count, 2));
        click(rule.getActivity().getString(R.string.subject_view_more));
        click(rule.getActivity().getString(R.string.grades_subject_details));
        dialogHas("4,0"); dialogHas("5,0"); dialogHas("Kod przedmiotu"); dialogHas("ID autora modyfikacji");
        dialogHas(rule.getActivity().getString(R.string.grades_full_data));
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.btn_close))
            .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
        capture("grade-details-complete");
    }
    @Test public void noAccountShowsScopeGuidanceWithoutPretendingDataExists() throws Exception {
        main(() -> { rule.getActivity().setDemo(false); rule.getActivity().navigate(13); });
        has(rule.getActivity().getString(R.string.services_scope_required, "cards"));
        has(rule.getActivity().getString(R.string.cards_empty));
        main(() -> rule.getActivity().navigate(14));
        has(rule.getActivity().getString(R.string.services_scope_required, "adm_documents"));
    }
}
