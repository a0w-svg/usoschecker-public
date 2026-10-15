package pl.vistula.checker;

import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.*;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.ui.GradesScreenController;
import java.time.LocalDate;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GradeSemestersUiTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    private Snapshot fixture(boolean currentGrade) {
        LocalDate now = LocalDate.now(UsosClient.ZONE);
        Snapshot s = new Snapshot(now.toString(), now.plusDays(1).toString());
        s.terms.put("now", new AcademicTerm("now", "Semestr aktualny", now.minusDays(10).toString(), now.plusDays(100).toString(), ""));
        s.terms.put("old", new AcademicTerm("old", "Semestr archiwalny", now.minusYears(1).toString(), now.minusMonths(6).toString(), ""));
        if (currentGrade) s.add(new Record("new", "ocena", "Aktualny przedmiot", "5", "", "", "now"));
        s.add(new Record("old", "ocena", "Archiwalny przedmiot", "3", "", "", "old"));
        s.add(new Record("unknown", "ocena", "Nieprzypisany przedmiot", "4", "", "", ""));
        return s;
    }
    private LinearLayout render(Snapshot snapshot, String query) throws Exception {
        MainActivity a = rule.getActivity(); LinearLayout content = new LinearLayout(a);
        GradesScreenController.render(a, content, new JSONObject().put("snapshot", snapshot.json()), new JSONObject(), false,
            "", "all", "name", query, false, value -> {}, value -> {}, value -> {}, value -> {}, value -> {}, () -> {}, () -> {},
            new GradesScreenController.GradesListener() {
                public void onFilterChanged() {}
                public void onOpenTask(String title) {}
            });
        return content;
    }
    private void main(TestAction action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { rule.getActivity().gradeSemesterExpansion().clear(); action.run(); }
            catch (Exception e) { throw new AssertionError(e); }
        });
    }
    private interface TestAction { void run() throws Exception; }
    @Test public void allSemestersAreExpandedByDefault() {
        main(() -> {
            LinearLayout content = render(fixture(true), "");
            assertTrue(visible(find(content, "Aktualny przedmiot", false)));
            assertTrue(visible(find(content, "Archiwalny przedmiot", false)));
            assertTrue(visible(find(content, "Nieprzypisany przedmiot", false)));
        });
    }
    @Test public void archiveCanBeExpandedCollapsedAndKeepsItsStateAfterRender() {
        main(() -> {
            Snapshot snapshot = fixture(true); LinearLayout content = render(snapshot, "");
            View toggle = find(content, "Semestr archiwalny", true); assertNotNull(toggle);
            toggle.performClick(); assertFalse(visible(find(content, "Archiwalny przedmiot", false)));
            content = render(snapshot, ""); assertFalse(visible(find(content, "Archiwalny przedmiot", false)));
            find(content, "Semestr archiwalny", true).performClick();
            assertTrue(visible(find(content, "Archiwalny przedmiot", false)));
        });
    }
    @Test public void currentSemesterWithoutGradesStillShowsAllSemesters() {
        main(() -> {
            LinearLayout content = render(fixture(false), "");
            assertTrue(visible(find(content, rule.getActivity().getString(R.string.grades_semester_no_grades), false)));
            assertTrue(visible(find(content, "Archiwalny przedmiot", false)));
        });
    }
    @Test public void searchingAnArchivedSubjectOpensItsSection() {
        main(() -> {
            LinearLayout content = render(fixture(true), "Archiwalny przedmiot");
            assertTrue(visible(find(content, "Archiwalny przedmiot", false)));
        });
    }
    @Test public void semesterWithoutGradesIsListedAndCanBeExpanded() {
        main(() -> {
            Snapshot snapshot = fixture(true);
            snapshot.terms.put("empty", new AcademicTerm("empty", "Semestr bez ocen", "2024-10-01", "2025-02-01", ""));
            LinearLayout content = render(snapshot, "");
            View header = find(content, "Semestr bez ocen", true);
            assertNotNull(header); assertTrue(visible(header));
            assertTrue(visible(find(content, rule.getActivity().getString(R.string.grades_semester_no_grades), false)));
            header.performClick();
            assertFalse(visible(find(content, rule.getActivity().getString(R.string.grades_semester_no_grades), false)));
        });
    }
    @Test public void ambiguousTestsStayCollapsedInsteadOfAppearingInCurrentSemester() {
        main(() -> {
            Snapshot snapshot = fixture(true);
            snapshot.add(new Record("repeated-old", "ocena", "Repeated course", "3", "", "", "old"));
            snapshot.add(new Record("repeated-new", "ocena", "Repeated course", "5", "", "", "now"));
            snapshot.tests.add(new CourseTestRecord("ambiguous", "", "Repeated course", "Ambiguous test", 3, 5, "", ""));
            LinearLayout content = render(snapshot, "");
            assertFalse(visible(find(content, "Ambiguous test", false)));
            find(content, rule.getActivity().getString(R.string.grades_unassigned_tests), true).performClick();
            assertTrue(visible(find(content, "Ambiguous test", false)));
        });
    }
    @Test public void enrolledCourseWithoutGradeShowsPlaceholderAndDisappearsAfterGradeArrives() {
        main(() -> {
            Snapshot snapshot = fixture(true);
            snapshot.courses.add(new Record("pending", "przedmiot", "Nowy przedmiot", "", "", "", "now", "", "", 5,
                new JSONObject().put("courseId", "NEW")));
            LinearLayout content = render(snapshot, "");
            assertTrue(visible(find(content, "Nowy przedmiot", false)));
            assertTrue(visible(find(content, rule.getActivity().getString(R.string.grades_no_grade), false)));
            snapshot.add(new Record("grade-new", "ocena", "Nowy przedmiot", "4", "", "", "now", "", "", 5,
                new JSONObject().put("courseId", "NEW")));
            content = render(snapshot, "");
            assertTrue(visible(find(content, "Nowy przedmiot", false)));
            assertNull(find(content, rule.getActivity().getString(R.string.grades_no_grade), false));
        });
    }
    private View find(View view, String text, boolean button) {
        if (view instanceof TextView && (!button || view instanceof Button)
            && (button ? ((TextView) view).getText().toString().contains(text) : text.contentEquals(((TextView) view).getText()))) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View result = find(((ViewGroup) view).getChildAt(i), text, button); if (result != null) return result;
        }
        return null;
    }
    private boolean visible(View view) {
        assertNotNull(view);
        for (View current = view; current != null; current = current.getParent() instanceof View ? (View) current.getParent() : null)
            if (current.getVisibility() != View.VISIBLE) return false;
        return true;
    }
}
