package pl.vistula.checker.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GradeSemestersTest {
    private val today = LocalDate.of(2026, 10, 15)
    private fun snapshot(): Snapshot = Snapshot("2026-10-01", "2026-11-01")
    private fun grade(id: String, term: String): Record = Record(id, "ocena", id, "4", "", "", term)

    @Test
    fun currentSemesterUsesDatesEvenWhenThereAreNoGradesYet() {
        val s = snapshot()
        s.terms["current"] = AcademicTerm("current", "Current", "2026-10-01", "2027-02-01", "")
        s.add(grade("old", "ZZZ"))
        assertEquals("current", GradeSemesters.current(s, today))
        assertEquals(listOf("current", "ZZZ"), GradeSemesters.ordered(s, today))
    }

    @Test
    fun sectionsUseDatesForOrderingAndKeepUnassignedGradesLast() {
        val s = snapshot()
        s.add(grade("a", "AAA"))
        s.add(grade("b", "ZZZ"))
        s.add(grade("c", ""))
        s.terms["AAA"] = AcademicTerm("AAA", "", "2026-02-01", "2026-06-30", "")
        s.terms["ZZZ"] = AcademicTerm("ZZZ", "", "2025-02-01", "2025-06-30", "")
        assertEquals(listOf("AAA", "ZZZ", ""), GradeSemesters.ordered(s, today))
        assertNull(GradeSemesters.current(s, today))
    }

    @Test
    fun invalidDatesDoNotInventACurrentSemester() {
        val s = snapshot()
        s.terms["2026Z"] = AcademicTerm("2026Z", "", "invalid", "2027-02-01", "")
        s.add(grade("a", "2026Z"))
        assertNull(GradeSemesters.current(s, today))
        assertEquals(listOf("2026Z"), GradeSemesters.ordered(s, today))
    }

    @Test
    fun overlappingCalendarPeriodsPreferTheMostRecentlyStartedTerm() {
        val s = snapshot()
        s.terms["year"] = AcademicTerm("year", "", "2026-09-01", "2027-08-31", "")
        s.terms["winter"] = AcademicTerm("winter", "", "2026-10-01", "2027-02-01", "")
        assertEquals("winter", GradeSemesters.current(s, today))
    }

    @Test
    fun unassignedGradesAreSeparatedWithoutIncludingOtherTerms() {
        val rows = listOf(grade("a", ""), grade("b", "old"))
        assertEquals("a", GradeSemesters.inTerm(rows, "")[0].id)
        assertEquals(1, GradeSemesters.inTerm(rows, "old").size)
        assertEquals(2, rows.size)
    }

    @Test
    fun testsForCoursesRepeatedAcrossSemestersAreNotAssignedArbitrarily() {
        val s = snapshot()
        s.add(Record("a", "ocena", "Course", "5", "", "", "old"))
        val test = CourseTestRecord("test", "", "Course", "Test", 5.0, 10.0, "", "")
        assertEquals("old", GradeSemesters.testTerm(s, test))
        s.add(Record("b", "ocena", "Course", "4", "", "", "current"))
        assertNull(GradeSemesters.testTerm(s, test))
    }

    @Test
    fun includesPastAndFutureSemestersEvenWithoutAnyGrades() {
        val s = snapshot()
        s.terms["old"] = AcademicTerm("old", "", "2026-02-01", "2026-06-30", "")
        s.terms["current"] = AcademicTerm("current", "", "2026-10-01", "2027-02-01", "")
        s.terms["future"] = AcademicTerm("future", "", "2027-02-15", "2027-06-30", "")
        assertEquals(listOf("current", "future", "old"), GradeSemesters.ordered(s, today))
    }

    @Test
    fun includesSemesterKnownOnlyFromTimetableWithoutAnEmptyPlaceholder() {
        val s = snapshot()
        s.add(Record("plan", "plan", "Course", "", "", "", "planned"))
        s.terms[""] = AcademicTerm("", "", "", "", "")
        assertEquals(listOf("planned"), GradeSemesters.ordered(s, today))
    }
}
