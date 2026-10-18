package pl.vistula.checker;

import android.content.Context;
import android.app.Activity;
import android.widget.*;
import android.view.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppSmokeTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<MainActivity>(MainActivity.class) {
        @Override protected android.content.Intent getActivityIntent() {
            return new android.content.Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
    };
    @Before public void resetNavigation() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (!target.getPackageName().endsWith(".qa")) org.junit.Assume.assumeTrue(
            "Use -PisolatedTests when the app already has local data.", new Repository(target).read().length() == 0);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> rule.getActivity().navigate(0));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void startDemo() throws Exception {
        click("Więcej"); click(activity().getString(R.string.tool_account_title));
        click(activity().getString(R.string.btn_start_demo));
    }
    private Activity activity() { return rule.getActivity(); }
    private View find(View root, String text, boolean button) {
        if (root instanceof TextView && (!button || root instanceof Button)) {
            String label = ((TextView) root).getText().toString();
            if (label.equals(text) || (button && label.matches(java.util.regex.Pattern.quote(text) + " \\(\\d+\\)"))) return root;
        }
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View result = find(((ViewGroup) root).getChildAt(i), text, button); if (result != null) return result;
        }
        return null;
    }
    private void click(String text) throws Exception {
        if (PlanMenuTestHelper.select(activity(), text)) return;
        long until = System.currentTimeMillis() + 10000;
        boolean[] done = {false};
        while (!done[0] && System.currentTimeMillis() < until) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                String[] tabs = {"Start", "Oceny", "Plan", "Zmiany", "Więcej"};
                int[] ids = {R.id.nav_item_home, R.id.nav_item_grades, R.id.nav_item_plan, R.id.nav_item_history, R.id.nav_item_more};
                View nav = findNavigation(activity().getWindow().getDecorView());
                for (int i = 0; i < tabs.length; i++) if (text.equals(tabs[i]) && nav != null) {
                    android.view.MenuItem item = ((com.google.android.material.bottomnavigation.BottomNavigationView) nav).getMenu().findItem(ids[i]);
                    if (item.isEnabled()) { nav.findViewById(ids[i]).performClick(); done[0] = true; }
                    return;
                }
                View v = find(activity().getWindow().getDecorView(), text, true);
                if (v == null) {
                    v = find(activity().getWindow().getDecorView(), text, false);
                    while (v != null && !v.isClickable()) v = v.getParent() instanceof View ? (View) v.getParent() : null;
                }
                if (v != null && v.isEnabled()) { v.performClick(); done[0] = true; }
            });
            if (!done[0]) Thread.sleep(100);
        }
        assertTrue("Button not available: " + text, done[0]);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void has(String text) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            assertNotNull("Missing text: " + text, find(activity().getWindow().getDecorView(), text, false)));
    }
    private void hasContaining(String text) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            assertTrue("Missing text: " + text, containsText(activity().getWindow().getDecorView(), text)));
    }
    @Test public void demoSimulatesChangesAndRemainsSeparateFromLiveAccount() throws Exception {
        click("Więcej"); click(activity().getString(R.string.tool_account_title));
        click(activity().getString(R.string.btn_start_demo));
        click("Więcej"); click(activity().getString(R.string.tool_account_title)); click("Symuluj zmiany");
        has("Zmiany"); has("Podstawy finansów"); has("Statystyka opisowa");
        click("Oceny"); has("Podstawy finansów");
        click("Plan"); click(activity().getString(R.string.schedule_mode_agenda)); has("Statystyka opisowa");
        click("Więcej"); click(activity().getString(R.string.tool_account_title)); click("Zakończ tryb demonstracyjny");
        has("Połącz z USOS"); assertTrue(new Repository(activity()).read().optString("token").isEmpty());
    }
    @Test public void everyCatalogEntryHasMatchingOfflineMetadata() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        JSONArray index = new JSONArray(read(c, "api/index.json"));
        assertEquals(571, index.length());
        for (int i = 0; i < index.length(); i++) {
            String name = index.getJSONObject(i).getString("name");
            JSONObject meta = new JSONObject(read(c, "api/" + name.replace("/", "__") + ".json"));
            assertEquals(name, meta.getString("name")); assertNotNull(meta.getJSONArray("arguments")); assertNotNull(meta.getJSONObject("auth_options"));
        }
    }
    @Test public void apiSearchOpensOfflineMethodAndRequiredParameters() throws Exception {
        click("Więcej"); click(activity().getString(R.string.tool_api_title));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ViewGroup root = (ViewGroup) activity().getWindow().getDecorView();
            EditText input = firstInput(root); assertNotNull(input); input.setText("grades/latest");
        });
        click("grades/latest\nGet user's latest grades");
        long until = System.currentTimeMillis() + 5000; boolean[] found = {false};
        while (!found[0] && System.currentTimeMillis() < until) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> found[0] = find(activity().getWindow().getDecorView(), "Parametry żądania", false) != null);
            if (!found[0]) Thread.sleep(100);
        }
        assertTrue(found[0]); has(activity().getString(R.string.api_execute_btn)); has(activity().getString(R.string.api_btn_online_docs));
        click("← Wróć do katalogu");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            assertEquals("grades/latest", firstInput((ViewGroup) activity().getWindow().getDecorView()).getText().toString()));
    }
    @Test public void apiFavoritesPersistAndFilterTheCatalog() throws Exception {
        android.content.SharedPreferences preferences = activity().getSharedPreferences("api_catalog", Context.MODE_PRIVATE);
        java.util.Set<String> previous = new java.util.HashSet<>(preferences.getStringSet("favorites", java.util.Collections.emptySet()));
        try {
            click("Więcej"); click(activity().getString(R.string.tool_api_title));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                firstInput((ViewGroup) activity().getWindow().getDecorView()).setText("grades/latest"));
            click("grades/latest\nGet user's latest grades");
            if (previous.contains("services/grades/latest")) click("Usuń z ulubionych");
            click("Dodaj do ulubionych");
            has("Usuń z ulubionych");
            click("← Wróć do katalogu"); click(activity().getString(R.string.api_fav_filter));
            has("grades/latest\nGet user's latest grades");
            click("grades/latest\nGet user's latest grades"); click("Usuń z ulubionych");
            click("← Wróć do katalogu");
            has("Brak pasujących metod. Spróbuj innej nazwy lub opisu.");
            assertFalse(preferences.getStringSet("favorites", java.util.Collections.emptySet()).contains("services/grades/latest"));
        } finally {
            preferences.edit().putStringSet("favorites", previous).commit();
        }
    }
    @Test public void catalogCanShowMoreThanFirstPage() throws Exception {
        click("Więcej"); click(activity().getString(R.string.tool_api_title));
        has("Wyświetlono 60 z 571 pasujących metod.");
        click("Pokaż kolejne metody");
        has("Wyświetlono 120 z 571 pasujących metod.");
        for (int i = 0; i < 8; i++) click("Pokaż kolejne metody");
        has("Wyświetlono 571 z 571 pasujących metod.");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            assertNull(find(activity().getWindow().getDecorView(), "Pokaż kolejne metody", true)));
    }
    private void searchFor(String query) {
        PlanMenuTestHelper.revealSearch(activity());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            EditText input = firstInput((ViewGroup) activity().getWindow().getDecorView());
            assertNotNull(input); input.setText(query);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Test public void studentDataSearchAndTimetableDateFilters() throws Exception {
        startDemo(); click("Oceny");
        searchFor("FINANSÓW"); has("Podstawy finansów");
        searchFor("nieistniejący przedmiot"); has("Brak pasujących danych");
        searchFor(""); click("Plan"); click("Dzisiaj"); has("Ekonomia"); hasContaining(activity().getString(R.string.classes_ongoing));
        click(activity().getString(R.string.schedule_mode_agenda)); has("Statystyka opisowa");
        searchFor("204"); has("Statystyka opisowa");
        searchFor("315"); has(activity().getString(R.string.schedule_no_search_results));
        click("Start"); click("Oceny");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            assertEquals("", firstInput((ViewGroup) activity().getWindow().getDecorView()).getText().toString()));
    }
    @Test public void gradeFiltersDetailsAndTimetableSearchStayIndependent() throws Exception {
        startDemo(); click("Oceny"); click("Filtry i sortowanie"); click("Liczbowe");
        has(activity().getString(R.string.grades_card_counts, 4, 0)); searchFor("finansów");
        click("Ukryj filtry i sortowanie"); capturePreview("grades-refined-preview.png");
        click(activity().getString(R.string.grades_subject_details));
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.grades_btn_add_task))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click());
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.task_input_title))
            .check((view, missing) -> { assertNull(missing); assertEquals("Podstawy finansów", ((EditText) view).getText().toString()); });
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Anuluj"))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(androidx.test.espresso.action.ViewActions.click());
        click("Plan");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertEquals("", firstInput((ViewGroup) activity().getWindow().getDecorView()).getText().toString()));
        click(activity().getString(R.string.schedule_mode_week)); has("Ekonomia"); capturePreview("timetable-week-preview.png");
        searchFor("Kowalska"); click("Oceny"); has("Podstawy finansów"); has(activity().getString(R.string.grades_card_counts, 2, 0));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertEquals("finansów", firstInput((ViewGroup) activity().getWindow().getDecorView()).getText().toString()));
        click("Filtry i sortowanie"); click("Pozostałe"); has("Brak pasujących danych"); click("Wyczyść filtry ocen"); has("Język angielski");
    }
    @Test public void friendlySearchAndDailyNavigationRespectVisibleResults() throws Exception {
        startDemo(); click("Oceny"); searchFor("finansow"); has("Podstawy finansów");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View decor = activity().getWindow().getDecorView(); int[] grade = new int[2], summary = new int[2];
            find(decor,"Podstawy finansów",false).getLocationOnScreen(grade); find(decor,"Podsumowanie ocen",false).getLocationOnScreen(summary);
            assertTrue(summary[1] < grade[1]); firstInput((ViewGroup) decor).requestFocus();
        });
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Wyczyść wyszukiwanie"))
            .perform(androidx.test.espresso.action.ViewActions.click());
        has("Język angielski"); capturePreview("friendly-grades.png");
        click("Plan"); click(activity().getString(R.string.schedule_btn_today)); searchFor("Kowalska"); has(activity().getString(R.string.schedule_no_search_results));
        String tomorrow = java.time.LocalDate.now(UsosClient.ZONE).plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        click(activity().getString(R.string.schedule_btn_next_class) + " (" + pl.vistula.checker.ui.UiHelper.shortDate(java.time.LocalDate.now(UsosClient.ZONE).plusDays(1)) + ")"); has("Statystyka opisowa"); capturePreview("friendly-plan.png");
        searchFor("nieistniejace");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View decor = activity().getWindow().getDecorView();
            assertNull(find(decor, activity().getString(R.string.schedule_btn_next_class), true));
        });
        PlanMenuTestHelper.assertExportDisabled(activity());
    }
    @Test public void weeklyViewExportsOnlyTheSelectedWeek() throws Exception {
        java.io.File exportDirectory = new java.io.File(activity().getCacheDir(), "test-exports");
        exportDirectory.mkdirs();
        java.io.File output = new java.io.File(exportDirectory, "week-test.ics");
        java.nio.file.Files.write(output.toPath(), new byte[0]);
        androidx.test.espresso.intent.Intents.init();
        try {
            android.content.Intent result = new android.content.Intent().setData(androidx.core.content.FileProvider.getUriForFile(activity(), activity().getPackageName() + ".debugexports", output));
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.content.Intent.ACTION_CREATE_DOCUMENT))
                .respondWith(new android.app.Instrumentation.ActivityResult(Activity.RESULT_OK, result));
            startDemo(); click("Plan"); click(activity().getString(R.string.schedule_mode_week));
            click(activity().getString(R.string.schedule_export_ics));
            long until = System.currentTimeMillis() + 10000;
            while (output.length() == 0 && System.currentTimeMillis() < until) Thread.sleep(100);
            String calendar = new String(java.nio.file.Files.readAllBytes(output.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            int expected = java.time.LocalDate.now(UsosClient.ZONE).getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? 1 : 2;
            assertEquals(expected, calendar.split("BEGIN:VEVENT", -1).length - 1);
            assertTrue(calendar.contains("SUMMARY:Ekonomia"));
            assertEquals(expected == 2, calendar.contains("SUMMARY:Statystyka opisowa"));
        } finally { androidx.test.espresso.intent.Intents.release(); output.delete(); }
    }
    @Test public void gradeFiltersSurviveActivityRecreation() throws Exception {
        startDemo(); click("Oceny"); click("Filtry i sortowanie"); click("Liczbowe"); click("Ostatnia zmiana"); searchFor("finansów");
        Activity previous = activity(); InstrumentationRegistry.getInstrumentation().runOnMainSync(previous::recreate);
        long until = System.currentTimeMillis() + 10000; Activity[] resumed = {null};
        while (resumed[0] == null && System.currentTimeMillis() < until) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity candidate : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))
                    if (candidate instanceof MainActivity && candidate != previous && firstInput((ViewGroup) candidate.getWindow().getDecorView()) != null) resumed[0] = candidate;
            });
            if (resumed[0] == null) Thread.sleep(100);
        }
        assertNotNull(resumed[0]);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View decor = resumed[0].getWindow().getDecorView();
            assertEquals("finansów", firstInput((ViewGroup) decor).getText().toString());
            assertTrue(find(decor, "Liczbowe", true).isSelected()); assertTrue(find(decor, "Ostatnia zmiana", true).isSelected());
            assertNotNull(find(decor, "Podstawy finansów", false));
        });
    }
    @Test public void historySearchMatchesBeforeAndAfterValues() throws Exception {
        click("Więcej"); click(activity().getString(R.string.tool_account_title));
        click(activity().getString(R.string.btn_start_demo));
        click("Więcej"); click(activity().getString(R.string.tool_account_title)); click("Symuluj zmiany");
        searchFor("315"); has("Statystyka opisowa");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            assertNull(find(activity().getWindow().getDecorView(), "Podstawy finansów", false)));
        searchFor("finansów"); has("Podstawy finansów");
        searchFor("nieistniejąca zmiana"); has("Brak pasujących zmian");
    }
    @Test public void materialNavigationSeparatesGradesPlanAndTools() throws Exception {
        startDemo();
        has("Najbliższe zajęcia");
        click("Oceny"); has(activity().getString(R.string.nav_grades)); has("Podstawy finansów");
        click("Plan"); has("Plan"); has("Ekonomia"); hasContaining(activity().getString(R.string.classes_ongoing));
        click("Zmiany"); has("Zmiany");
        click("Więcej"); click(activity().getString(R.string.tool_tasks_title)); has("Dodaj zadanie");
    }
    @Test public void demoTasksCanBeAddedAndCompletedWithoutWritingLiveData() throws Exception {
        String before = new Repository(activity()).read().optJSONArray("tasks") == null ? "[]" : new Repository(activity()).read().getJSONArray("tasks").toString();
        startDemo(); click("Więcej"); click(activity().getString(R.string.tool_tasks_title)); click("Dodaj zadanie");
androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.task_input_title))
            .perform(androidx.test.espresso.action.ViewActions.replaceText("Przygotować projekt"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.btn_save))
            .perform(androidx.test.espresso.action.ViewActions.click());
        has("Przygotować projekt"); click("Oznacz jako ukończone"); has("Do zrobienia: 0 · ukończone: 1");
        String after = new Repository(activity()).read().optJSONArray("tasks") == null ? "[]" : new Repository(activity()).read().getJSONArray("tasks").toString();
        assertEquals(before, after);
    }
    @Test public void semesterFilterAndSummaryTrackVisibleGrades() throws Exception {
        startDemo(); click("Oceny");
        click("Filtry i sortowanie");
        has("Podsumowanie ocen"); has(activity().getString(R.string.grades_card_counts, 4, 0)); has(activity().getString(R.string.grades_card_counts, 1, 1));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> firstSpinner((ViewGroup) activity().getWindow().getDecorView()).setSelection(1));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        has("Podstawy finansów"); has(activity().getString(R.string.grades_card_counts, 4, 0));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            assertNull(find(activity().getWindow().getDecorView(), "Język angielski", false)));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> firstSpinner((ViewGroup) activity().getWindow().getDecorView()).setSelection(2));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        has("Język angielski"); has(activity().getString(R.string.grades_card_counts, 1, 1));
    }
    @Test public void taskEditingFiltersAndDashboardKeepTheSameTask() throws Exception {
        startDemo(); click("Więcej"); click(activity().getString(R.string.tool_tasks_title)); click("Dodaj zadanie");
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.task_input_title))
            .perform(androidx.test.espresso.action.ViewActions.replaceText("Projekt"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.btn_save))
            .perform(androidx.test.espresso.action.ViewActions.click());
        click("Edytuj zadanie");
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.task_input_title))
            .perform(androidx.test.espresso.action.ViewActions.replaceText("Projekt poprawiony"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Zapisz"))
            .perform(androidx.test.espresso.action.ViewActions.click());
        has("Projekt poprawiony"); has("Do zrobienia: 1 · ukończone: 0");
        click("Start"); has("Najbliższe terminy"); has("Projekt poprawiony");
        click("Otwórz zadania"); click("Na dzisiaj"); has("Projekt poprawiony");
        click("Oznacz jako ukończone"); has("Brak pasujących zadań");
        click("Ukończone"); has("Projekt poprawiony");
        click("Otwarte"); has("Brak pasujących zadań");
    }
    @Test public void taskNotesPrioritySearchAndDeletionUndoStayInDemo() throws Exception {
        String original = new Repository(activity()).read().toString();
        startDemo(); click("Więcej"); click(activity().getString(R.string.tool_tasks_title)); click("Dodaj zadanie");
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.task_input_title))
            .perform(androidx.test.espresso.action.ViewActions.replaceText("Przygotuj prezentację"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.task_input_notes))
            .perform(androidx.test.espresso.action.ViewActions.replaceText("Materiały w bibliotece"), androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.task_input_priority))
            .check((view, missing) -> { assertNull(missing); ((Spinner) view).setSelection(2); });
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.btn_save)).perform(androidx.test.espresso.action.ViewActions.click());
        has("Wysoki priorytet"); has("Materiały w bibliotece");
        capturePreview("task-priority-preview.png");
        searchFor("bibliotece"); has("Przygotuj prezentację");
        click("Edytuj zadanie");
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.task_input_notes))
            .check((view, missing) -> { assertNull(missing); assertEquals("Materiały w bibliotece", ((EditText) view).getText().toString()); });
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.task_input_priority))
            .check((view, missing) -> { assertNull(missing); assertEquals(2, ((Spinner) view).getSelectedItemPosition()); });
        capturePreview("task-editor-preview.png");
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Zapisz")).perform(androidx.test.espresso.action.ViewActions.click());
        click("Usuń zadanie");
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.tasks_btn_delete))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
        has("Spokojna głowa"); click("Cofnij");
        has("Przygotuj prezentację"); has("Wysoki priorytet");
        assertEquals(original, new Repository(activity()).read().toString());
    }
    @Test public void androidJsonNullGradesDoNotFailParsing() throws Exception {
        pl.vistula.checker.core.Snapshot snapshot = new pl.vistula.checker.core.Snapshot("2026-10-12", "2026-12-07");
        JSONObject response = new JSONObject("{\"2026Z\":{\"EMPTY\":{\"course_grades\":null,\"course_units_grades\":null},\"FIN\":{\"course_grades\":[{\"1\":{\"exam_id\":1,\"exam_session_number\":1,\"value_symbol\":\"5\",\"date_modified\":null,\"comment\":null}}],\"course_units_grades\":{}}}}");
        pl.vistula.checker.core.UsosParser.grades(snapshot, response, java.util.Collections.emptyMap());
        assertEquals(1, snapshot.records.size());
        assertEquals("", snapshot.records.get("grade:1:1").sourceTime);
        assertFalse(snapshot.records.get("grade:1:1").value.contains("null"));
    }
    private void capturePreview(String filename) throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(400, 3000);
        android.graphics.Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(activity().getExternalFilesDir(null), filename))) {
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } finally { screenshot.recycle(); }
    }
    @Test public void timetableExportWritesVisibleEventsToSelectedDocument() throws Exception {
        java.io.File exportDirectory = new java.io.File(activity().getCacheDir(), "test-exports");
        exportDirectory.mkdirs();
        java.io.File output = new java.io.File(exportDirectory, "calendar-test.ics");
        java.nio.file.Files.write(output.toPath(), new byte[0]);
        androidx.test.espresso.intent.Intents.init();
        try {
            android.content.Intent result = new android.content.Intent().setData(androidx.core.content.FileProvider.getUriForFile(activity(), activity().getPackageName() + ".debugexports", output));
            androidx.test.espresso.intent.Intents.intending(
                androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.content.Intent.ACTION_CREATE_DOCUMENT))
                .respondWith(new android.app.Instrumentation.ActivityResult(Activity.RESULT_OK, result));
            startDemo(); click("Plan"); click(activity().getString(R.string.schedule_mode_agenda)); searchFor("204");
            click(activity().getString(R.string.schedule_export_ics));
            long until = System.currentTimeMillis() + 10000;
            while (output.length() == 0 && System.currentTimeMillis() < until) Thread.sleep(100);
            String calendar = new String(java.nio.file.Files.readAllBytes(output.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(calendar.contains("BEGIN:VCALENDAR")); assertTrue(calendar.contains("SUMMARY:Statystyka opisowa"));
            assertTrue(calendar.contains("LOCATION:Sala 204"));
            assertTrue(calendar.replace("\r\n ", "").contains("Prowadzący: Anna Kowalska"));
            assertEquals(8, calendar.split("BEGIN:VEVENT", -1).length - 1);
        } finally { androidx.test.espresso.intent.Intents.release(); output.delete(); }
    }
    private Spinner firstSpinner(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i); if (v instanceof Spinner) return (Spinner) v;
            if (v instanceof ViewGroup) { Spinner result = firstSpinner((ViewGroup) v); if (result != null) return result; }
        }
        return null;
    }
    @Test public void lecturerDirectoryBatchesUniqueIdsAcrossWeeks() throws Exception {
        JSONArray lecturers = new JSONArray();
        for (int i = 1; i <= 61; i++) lecturers.put(i);
        JSONArray week = new JSONArray().put(new JSONObject().put("lecturer_ids", lecturers));
        java.util.List<Integer> sizes = new java.util.ArrayList<>();
        java.util.Map<String, String> names = UsosClient.resolveLecturers(java.util.List.of(week, week), ids -> {
            sizes.add(ids.size()); JSONObject result = new JSONObject();
            for (String id : ids) result.put(id, new JSONObject().put("first_name", "Anna").put("last_name", "Kowalska " + id));
            return result;
        });
        assertEquals(java.util.List.of(50, 11), sizes); assertEquals(61, names.size());
        assertEquals("Anna Kowalska 61", names.get("61"));
        assertTrue(UsosClient.resolveLecturers(java.util.List.of(new JSONArray()), ids -> { fail("Unexpected lookup"); return null; }).isEmpty());
    }
    @Test(expected = java.io.IOException.class) public void incompleteLecturerDirectoryRejectsDownload() throws Exception {
        JSONArray week = new JSONArray().put(new JSONObject().put("lecturer_ids", new JSONArray().put(1)));
        UsosClient.resolveLecturers(java.util.List.of(week), ids -> new JSONObject());
    }
    @Test public void conciseDashboardKeepsUpcomingLessonAndLecturerIsSearchableInTimetable() throws Exception {
        startDemo();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            assertTrue(containsText(activity().getWindow().getDecorView(), "Statystyka opisowa")));
        click("Plan"); click(activity().getString(R.string.schedule_mode_agenda)); searchFor("Kowalska"); has("Statystyka opisowa");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            assertTrue(containsText(activity().getWindow().getDecorView(), "Prowadzący: Anna Kowalska")));
    }
    private boolean containsText(View root, String part) {
        if (root instanceof TextView && ((TextView) root).getText().toString().contains(part)) return true;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++)
            if (containsText(((ViewGroup) root).getChildAt(i), part)) return true;
        return false;
    }
    @Test public void calendarShowsCurrentLectureAndSavedAgendaRange() throws Exception {
        startDemo(); hasContaining(activity().getString(R.string.classes_ongoing)); has("Ekonomia");
        click("Plan"); hasContaining(activity().getString(R.string.classes_ongoing));
        click(activity().getString(R.string.schedule_mode_agenda));
        has("Statystyka opisowa");
        click(activity().getString(R.string.schedule_info));
        String range = Demo.state(false).getJSONObject("snapshot").getString("start") + " – "
            + Demo.state(false).getJSONObject("snapshot").getString("end");
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withSubstring(range))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.btn_close))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
        click("Dzisiaj"); has("Ekonomia"); hasContaining(activity().getString(R.string.classes_ongoing));
    }
    @Test public void calendarBlocksFollowDurationAndSeparateOverlaps() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            java.time.LocalDate day = java.time.LocalDate.of(2026,10,13);
            java.util.List<pl.vistula.checker.core.Record> rows = java.util.List.of(
                new pl.vistula.checker.core.Record("a","plan","A","09:00 – 10:00","","2026-10-13"),
                new pl.vistula.checker.core.Record("b","plan","B","09:30 – 10:30","","2026-10-13"),
                new pl.vistula.checker.core.Record("c","plan","C","10:00 – 11:00","","2026-10-13"));
            DayTimelineView grid = new DayTimelineView(activity(), day, pl.vistula.checker.core.LessonTime.sorted(rows), lesson -> {});
            int height = grid.getLayoutParams().height;
            grid.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            grid.layout(0,0,600,height);
            View a = grid.getChildAt(1), b = grid.getChildAt(2), c = grid.getChildAt(3);
            assertTrue(a.getHeight() < height / 4); assertTrue(a.getWidth() < 600 / 2);
            assertEquals(a.getLeft(), c.getLeft()); assertNotEquals(a.getLeft(), b.getLeft());
            assertTrue(a.getBottom() <= c.getTop());
        });
    }
    private View findNavigation(View root) {
        if (root instanceof com.google.android.material.bottomnavigation.BottomNavigationView) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View result = findNavigation(((ViewGroup) root).getChildAt(i)); if (result != null) return result;
        }
        return null;
    }
    private EditText firstInput(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i); if (v instanceof EditText) return (EditText) v;
            if (v instanceof ViewGroup) { EditText result = firstInput((ViewGroup) v); if (result != null) return result; }
        }
        return null;
    }
    @Test public void encryptedVaultRoundTripsAndDoesNotWriteTokensAsPlaintext() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Repository r = new Repository(c); String marker = "sensitive_token_test_6d8b1c";
        r.update(s -> s.put("testMarker", marker)); assertEquals(marker, r.read().getString("testMarker"));
        byte[] bytes = java.nio.file.Files.readAllBytes(new java.io.File(c.getNoBackupFilesDir(), "state.bin").toPath());
        assertFalse(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains(marker));
        r.update(s -> s.remove("testMarker"));
    }
    @Test public void failedSyncPreservesSnapshotAndHistoryAndRetryDoesNotDuplicateChanges() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Repository r = new Repository(c); r.disconnect();
        String day = java.time.LocalDate.now(UsosClient.ZONE).toString();
        pl.vistula.checker.core.Snapshot initial = new pl.vistula.checker.core.Snapshot(day, java.time.LocalDate.now(UsosClient.ZONE).plusDays(28).toString());
        pl.vistula.checker.core.Snapshot updated = new pl.vistula.checker.core.Snapshot(initial.start, initial.end);
        initial.add(new pl.vistula.checker.core.Record("grade:test", "ocena", "Test", "4", day + " 10:00:00", ""));
        updated.add(new pl.vistula.checker.core.Record("grade:test", "ocena", "Test", "5", day + " 10:30:00", ""));
        try {
            r.update(s -> s.put("token", "test-only-token").put("enabled", true));
            assertTrue(r.sync(true, s -> initial).isEmpty());
            assertEquals(1, r.sync(true, s -> updated).size());
            JSONObject before = r.read();
            try { r.sync(true, s -> { throw new java.io.IOException("simulated unavailable source"); }); fail("Expected failure"); }
            catch (java.io.IOException expected) {}
            JSONObject after = r.read();
            assertEquals(before.getJSONObject("snapshot").toString(), after.getJSONObject("snapshot").toString());
            assertEquals(before.getJSONArray("history").toString(), after.getJSONArray("history").toString());
            assertEquals(before.getLong("successAt"), after.getLong("successAt"));
            assertFalse(after.optString("error").isEmpty());
            assertTrue(r.sync(true, s -> updated).isEmpty()); assertEquals(1, r.read().getJSONArray("history").length());
        } finally { r.disconnect(); }
    }
    private String read(Context c, String path) throws Exception {
        try (java.io.InputStream input = c.getAssets().open(path); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n); return output.toString("UTF-8");
        }
    }
}
