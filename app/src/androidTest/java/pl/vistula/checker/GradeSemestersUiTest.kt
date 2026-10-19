package pl.vistula.checker

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.AcademicTerm
import pl.vistula.checker.core.CourseTestRecord
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.ui.GradesScreenController

@RunWith(AndroidJUnit4::class)
class GradeSemestersUiTest {
    @JvmField
    @Rule
    val rule = ActivityTestRule(MainActivity::class.java)

    private fun fixture(currentGrade: Boolean): Snapshot {
        val now = LocalDate.now(UsosClient.ZONE)
        val s = Snapshot(now.toString(), now.plusDays(1).toString())
        s.terms["now"] = AcademicTerm("now", "Semestr aktualny", now.minusDays(10).toString(), now.plusDays(100).toString(), "")
        s.terms["old"] = AcademicTerm("old", "Semestr archiwalny", now.minusYears(1).toString(), now.minusMonths(6).toString(), "")
        if (currentGrade) s.add(Record("new", "ocena", "Aktualny przedmiot", "5", "", "", "now"))
        s.add(Record("old", "ocena", "Archiwalny przedmiot", "3", "", "", "old"))
        s.add(Record("unknown", "ocena", "Nieprzypisany przedmiot", "4", "", "", ""))
        return s
    }

    private fun render(snapshot: Snapshot, query: String): LinearLayout {
        val a = rule.activity
        val content = LinearLayout(a)
        GradesScreenController.render(
            a,
            content,
            JSONObject().put("snapshot", snapshot.json()),
            JSONObject(),
            false,
            "",
            "all",
            "name",
            query,
            false,
            {},
            {},
            {},
            {},
            {},
            {},
            {},
            object : GradesScreenController.GradesListener {
                override fun onFilterChanged() {}
                override fun onOpenTask(title: String) {}
            }
        )
        return content
    }

    private fun main(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                rule.activity.gradeSemesterExpansion().clear()
                action()
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }
    }

    @Test
    fun allSemestersAreExpandedByDefault() {
        main {
            val content = render(fixture(true), "")
            assertTrue(visible(find(content, "Aktualny przedmiot", false)))
            assertTrue(visible(find(content, "Archiwalny przedmiot", false)))
            assertTrue(visible(find(content, "Nieprzypisany przedmiot", false)))
        }
    }

    @Test
    fun archiveCanBeExpandedCollapsedAndKeepsItsStateAfterRender() {
        main {
            val snapshot = fixture(true)
            var content = render(snapshot, "")
            val toggle = find(content, "Semestr archiwalny", true)
            assertNotNull(toggle)
            toggle!!.performClick()
            assertFalse(visible(find(content, "Archiwalny przedmiot", false)))
            content = render(snapshot, "")
            assertFalse(visible(find(content, "Archiwalny przedmiot", false)))
            find(content, "Semestr archiwalny", true)!!.performClick()
            assertTrue(visible(find(content, "Archiwalny przedmiot", false)))
        }
    }

    @Test
    fun currentSemesterWithoutGradesStillShowsAllSemesters() {
        main {
            val content = render(fixture(false), "")
            assertTrue(visible(find(content, rule.activity.getString(R.string.grades_semester_no_grades), false)))
            assertTrue(visible(find(content, "Archiwalny przedmiot", false)))
        }
    }

    @Test
    fun searchingAnArchivedSubjectOpensItsSection() {
        main {
            val content = render(fixture(true), "Archiwalny przedmiot")
            assertTrue(visible(find(content, "Archiwalny przedmiot", false)))
        }
    }

    @Test
    fun semesterWithoutGradesIsListedAndCanBeExpanded() {
        main {
            val snapshot = fixture(true)
            snapshot.terms["empty"] = AcademicTerm("empty", "Semestr bez ocen", "2024-10-01", "2025-02-01", "")
            val content = render(snapshot, "")
            val header = find(content, "Semestr bez ocen", true)
            assertNotNull(header)
            assertTrue(visible(header))
            assertTrue(visible(find(content, rule.activity.getString(R.string.grades_semester_no_grades), false)))
            header!!.performClick()
            assertFalse(visible(find(content, rule.activity.getString(R.string.grades_semester_no_grades), false)))
        }
    }

    @Test
    fun ambiguousTestsStayCollapsedInsteadOfAppearingInCurrentSemester() {
        main {
            val snapshot = fixture(true)
            snapshot.add(Record("repeated-old", "ocena", "Repeated course", "3", "", "", "old"))
            snapshot.add(Record("repeated-new", "ocena", "Repeated course", "5", "", "", "now"))
            snapshot.tests.add(CourseTestRecord("ambiguous", "", "Repeated course", "Ambiguous test", 3.0, 5.0, "", ""))
            val content = render(snapshot, "")
            assertFalse(visible(find(content, "Ambiguous test", false)))
            find(content, rule.activity.getString(R.string.grades_unassigned_tests), true)!!.performClick()
            assertTrue(visible(find(content, "Ambiguous test", false)))
        }
    }

    @Test
    fun enrolledCourseWithoutGradeShowsPlaceholderAndDisappearsAfterGradeArrives() {
        main {
            val snapshot = fixture(true)
            snapshot.courses.add(
                Record(
                    "pending",
                    "przedmiot",
                    "Nowy przedmiot",
                    "",
                    "",
                    "",
                    "now",
                    "",
                    "",
                    5.0,
                    JSONObject().put("courseId", "NEW")
                )
            )
            var content = render(snapshot, "")
            assertTrue(visible(find(content, "Nowy przedmiot", false)))
            assertTrue(visible(find(content, rule.activity.getString(R.string.grades_no_grade), false)))
            snapshot.add(
                Record(
                    "grade-new",
                    "ocena",
                    "Nowy przedmiot",
                    "4",
                    "",
                    "",
                    "now",
                    "",
                    "",
                    5.0,
                    JSONObject().put("courseId", "NEW")
                )
            )
            content = render(snapshot, "")
            assertTrue(visible(find(content, "Nowy przedmiot", false)))
            assertNull(find(content, rule.activity.getString(R.string.grades_no_grade), false))
        }
    }

    private fun find(view: View, text: String, button: Boolean): View? {
        if (view is TextView && (!button || view is Button) &&
            (if (button) view.text.toString().contains(text) else text.contentEquals(view.text))
        ) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = find(view.getChildAt(i), text, button)
                if (result != null) return result
            }
        }
        return null
    }

    private fun visible(view: View?): Boolean {
        assertNotNull(view)
        var current = view
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return true
    }
}
