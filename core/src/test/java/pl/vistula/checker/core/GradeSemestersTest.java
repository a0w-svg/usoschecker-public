package pl.vistula.checker.core;

import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GradeSemestersTest {
    private final LocalDate today = LocalDate.of(2026, 10, 15);
    private Snapshot snapshot() { return new Snapshot("2026-10-01", "2026-11-01"); }
    private Record grade(String id, String term) { return new Record(id, "ocena", id, "4", "", "", term); }
    @Test public void currentSemesterUsesDatesEvenWhenThereAreNoGradesYet() {
        Snapshot s = snapshot();
        s.terms.put("current", new AcademicTerm("current", "Current", "2026-10-01", "2027-02-01", ""));
        s.add(grade("old", "ZZZ"));
        assertEquals("current", GradeSemesters.current(s, today));
        assertEquals(List.of("current", "ZZZ"), GradeSemesters.ordered(s, today));
    }
    @Test public void sectionsUseDatesForOrderingAndKeepUnassignedGradesLast() {
        Snapshot s = snapshot();
        s.add(grade("a", "AAA")); s.add(grade("b", "ZZZ")); s.add(grade("c", ""));
        s.terms.put("AAA", new AcademicTerm("AAA", "", "2026-02-01", "2026-06-30", ""));
        s.terms.put("ZZZ", new AcademicTerm("ZZZ", "", "2025-02-01", "2025-06-30", ""));
        assertEquals(List.of("AAA", "ZZZ", ""), GradeSemesters.ordered(s, today));
        assertNull(GradeSemesters.current(s, today));
    }
    @Test public void invalidDatesDoNotInventACurrentSemester() {
        Snapshot s = snapshot();
        s.terms.put("2026Z", new AcademicTerm("2026Z", "", "invalid", "2027-02-01", ""));
        s.add(grade("a", "2026Z"));
        assertNull(GradeSemesters.current(s, today));
        assertEquals(List.of("2026Z"), GradeSemesters.ordered(s, today));
    }
    @Test public void overlappingCalendarPeriodsPreferTheMostRecentlyStartedTerm() {
        Snapshot s = snapshot();
        s.terms.put("year", new AcademicTerm("year", "", "2026-09-01", "2027-08-31", ""));
        s.terms.put("winter", new AcademicTerm("winter", "", "2026-10-01", "2027-02-01", ""));
        assertEquals("winter", GradeSemesters.current(s, today));
    }
    @Test public void unassignedGradesAreSeparatedWithoutIncludingOtherTerms() {
        List<Record> rows = List.of(grade("a", ""), grade("b", "old"));
        assertEquals("a", GradeSemesters.inTerm(rows, "").get(0).id);
        assertEquals(1, GradeSemesters.inTerm(rows, "old").size());
        assertEquals(2, rows.size());
    }
    @Test public void testsForCoursesRepeatedAcrossSemestersAreNotAssignedArbitrarily() {
        Snapshot s = snapshot();
        s.add(new Record("a", "ocena", "Course", "5", "", "", "old"));
        CourseTestRecord test = new CourseTestRecord("test", "", "Course", "Test", 5, 10, "", "");
        assertEquals("old", GradeSemesters.testTerm(s, test));
        s.add(new Record("b", "ocena", "Course", "4", "", "", "current"));
        assertNull(GradeSemesters.testTerm(s, test));
    }
    @Test public void includesPastAndFutureSemestersEvenWithoutAnyGrades() {
        Snapshot s = snapshot();
        s.terms.put("old", new AcademicTerm("old", "", "2026-02-01", "2026-06-30", ""));
        s.terms.put("current", new AcademicTerm("current", "", "2026-10-01", "2027-02-01", ""));
        s.terms.put("future", new AcademicTerm("future", "", "2027-02-15", "2027-06-30", ""));
        assertEquals(List.of("current", "future", "old"), GradeSemesters.ordered(s, today));
    }
    @Test public void includesSemesterKnownOnlyFromTimetableWithoutAnEmptyPlaceholder() {
        Snapshot s = snapshot();
        s.add(new Record("plan", "plan", "Course", "", "", "", "planned"));
        s.terms.put("", new AcademicTerm("", "", "", "", ""));
        assertEquals(List.of("planned"), GradeSemesters.ordered(s, today));
    }
}
