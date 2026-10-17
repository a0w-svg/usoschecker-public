package pl.vistula.checker.core;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AttendanceTrackerTest {
    private Record lesson(String id, String term, String type, String date) throws Exception {
        return new Record(id, "plan", "Matematyka", "", "", date, term, date + " 10:00:00", date + " 11:30:00",
            Double.NaN, new JSONObject().put("courseId", "MAT").put("classType", type));
    }
    @Test public void separatesSemestersAndClassFormatsAndCountsLessonOnlyOnce() throws Exception {
        Snapshot snapshot = new Snapshot("", "");
        JSONObject saved = new JSONObject();
        Record lecture = lesson("w1", "2026Z", "Wykład", "2026-10-17");
        String lectureId = AttendanceTracker.record(saved, snapshot, lecture);
        AttendanceTracker.record(saved, snapshot, lecture);
        String exerciseId = AttendanceTracker.record(saved, snapshot, lesson("c1", "2026Z", "Ćwiczenia", "2026-10-17"));
        String nextTermId = AttendanceTracker.record(saved, snapshot, lesson("w2", "2027L", "Wykład", "2027-03-17"));
        assertEquals(3, saved.length());
        assertEquals(1, saved.getJSONObject(lectureId).getInt("absences"));
        assertNotEquals(lectureId, exerciseId);
        assertNotEquals(lectureId, nextTermId);
    }
    @Test public void infersSemesterFromUniversityDatesRatherThanCurrentSemester() throws Exception {
        Snapshot snapshot = new Snapshot("", "");
        snapshot.terms.put("winter", new AcademicTerm("winter", "Zimowy", "2026-10-01", "2027-02-15", ""));
        snapshot.terms.put("summer", new AcademicTerm("summer", "Letni", "2027-03-01", "2027-06-30", ""));
        assertEquals("winter", AttendanceTracker.term(snapshot, lesson("1", "", "Wykład", "2026-10-17")));
        assertEquals("summer", AttendanceTracker.term(snapshot, lesson("2", "", "Wykład", "2027-03-17")));
        assertEquals("", AttendanceTracker.term(snapshot, lesson("3", "", "Wykład", "2027-08-17")));
    }
    @Test public void preservesLegacyCountsAndNotesWithoutAssigningThemToEveryClass() throws Exception {
        JSONObject legacy = new JSONObject().put("MAT", new JSONObject().put("name", "Matematyka")
            .put("absences", 2).put("limit", 2).put("notes", "Usprawiedliwienie"));
        Snapshot snapshot = new Snapshot("", "");
        snapshot.add(lesson("1", "2026Z", "Wykład", "2026-10-17"));
        snapshot.add(lesson("2", "2026Z", "Ćwiczenia", "2026-10-17"));
        JSONObject migrated = AttendanceTracker.reconcile(legacy, snapshot);
        assertEquals(3, migrated.length());
        assertEquals(2, migrated.getJSONObject("MAT").getInt("absences"));
        assertEquals("", migrated.getJSONObject("MAT").getString("term"));
        assertEquals("Usprawiedliwienie", migrated.getJSONObject("MAT").getString("notes"));
        assertEquals(0, migrated.getJSONObject(AttendanceTracker.key("2026Z", "MAT", "Wykład")).getInt("absences"));
        assertEquals(migrated.toString(), AttendanceTracker.reconcile(migrated, snapshot).toString());
        assertEquals(2, legacy.getJSONObject("MAT").getInt("limit"));
    }
    @Test public void threeAbsencesReachLimitAndFourthExceedsIt() throws Exception {
        JSONObject saved = new JSONObject();
        String id = "";
        for (int i = 1; i <= 4; i++) {
            id = AttendanceTracker.record(saved, new Snapshot("", ""), lesson("lesson" + i, "2026Z", "Wykład", "2026-10-17"));
            assertEquals(i, saved.getJSONObject(id).getInt("absences"));
            assertEquals(i > 3, AttendanceTracker.exceeded(i));
        }
        assertTrue(AttendanceTracker.warning(2));
        assertTrue(AttendanceTracker.warning(3));
        assertFalse(AttendanceTracker.warning(4));
        assertEquals(3, saved.getJSONObject(id).getInt("limit"));
    }
    @Test public void classTypeAliasesAndCompositeKeysRemainDistinct() throws Exception {
        assertEquals("Wykład", AttendanceTracker.type(lesson("1", "", "lecture", "2026-10-17")));
        assertEquals("Ćwiczenia", AttendanceTracker.type(lesson("1", "", "CW", "2026-10-17")));
        assertEquals("Laboratorium", AttendanceTracker.type(lesson("1", "", "Laboratory", "2026-10-17")));
        assertNotEquals(AttendanceTracker.key("a|b", "c", "d"), AttendanceTracker.key("a", "b|c", "d"));
    }
}
