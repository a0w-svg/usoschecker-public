package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AttendanceTrackerTest {
    private fun lesson(id: String, term: String, type: String, date: String): Record =
        Record(
            id, "plan", "Matematyka", "", "", date, term, "$date 10:00:00", "$date 11:30:00",
            Double.NaN, JSONObject().put("courseId", "MAT").put("classType", type)
        )

    @Test
    fun separatesSemestersAndClassFormatsAndCountsLessonOnlyOnce() {
        val snapshot = Snapshot("", "")
        val saved = JSONObject()
        val lecture = lesson("w1", "2026Z", "Wykład", "2026-10-17")
        val lectureId = AttendanceTracker.record(saved, snapshot, lecture)
        AttendanceTracker.record(saved, snapshot, lecture)
        val exerciseId = AttendanceTracker.record(saved, snapshot, lesson("c1", "2026Z", "Ćwiczenia", "2026-10-17"))
        val nextTermId = AttendanceTracker.record(saved, snapshot, lesson("w2", "2027L", "Wykład", "2027-03-17"))
        assertEquals(3, saved.length())
        assertEquals(1, saved.getJSONObject(lectureId).getInt("absences"))
        assertNotEquals(lectureId, exerciseId)
        assertNotEquals(lectureId, nextTermId)
    }

    @Test
    fun infersSemesterFromUniversityDatesRatherThanCurrentSemester() {
        val snapshot = Snapshot("", "")
        snapshot.terms["winter"] = AcademicTerm("winter", "Zimowy", "2026-10-01", "2027-02-15", "")
        snapshot.terms["summer"] = AcademicTerm("summer", "Letni", "2027-03-01", "2027-06-30", "")
        assertEquals("winter", AttendanceTracker.term(snapshot, lesson("1", "", "Wykład", "2026-10-17")))
        assertEquals("summer", AttendanceTracker.term(snapshot, lesson("2", "", "Wykład", "2027-03-17")))
        assertEquals("", AttendanceTracker.term(snapshot, lesson("3", "", "Wykład", "2027-08-17")))
    }

    @Test
    fun preservesLegacyCountsAndNotesWithoutAssigningThemToEveryClass() {
        val legacy = JSONObject().put(
            "MAT",
            JSONObject().put("name", "Matematyka").put("absences", 2).put("limit", 2).put("notes", "Usprawiedliwienie")
        )
        val snapshot = Snapshot("", "")
        snapshot.add(lesson("1", "2026Z", "Wykład", "2026-10-17"))
        snapshot.add(lesson("2", "2026Z", "Ćwiczenia", "2026-10-17"))
        val migrated = AttendanceTracker.reconcile(legacy, snapshot)
        assertEquals(3, migrated.length())
        assertEquals(2, migrated.getJSONObject("MAT").getInt("absences"))
        assertEquals("", migrated.getJSONObject("MAT").getString("term"))
        assertEquals("Usprawiedliwienie", migrated.getJSONObject("MAT").getString("notes"))
        assertEquals(0, migrated.getJSONObject(AttendanceTracker.key("2026Z", "MAT", "Wykład")).getInt("absences"))
        assertEquals(migrated.toString(), AttendanceTracker.reconcile(migrated, snapshot).toString())
        assertEquals(2, legacy.getJSONObject("MAT").getInt("limit"))
    }

    @Test
    fun threeAbsencesReachLimitAndFourthExceedsIt() {
        val saved = JSONObject()
        var id = ""
        for (i in 1..4) {
            id = AttendanceTracker.record(saved, Snapshot("", ""), lesson("lesson$i", "2026Z", "Wykład", "2026-10-17"))
            assertEquals(i, saved.getJSONObject(id).getInt("absences"))
            assertEquals(i > 3, AttendanceTracker.exceeded(i))
        }
        assertTrue(AttendanceTracker.warning(2))
        assertTrue(AttendanceTracker.warning(3))
        assertFalse(AttendanceTracker.warning(4))
        assertEquals(3, saved.getJSONObject(id).getInt("limit"))
    }

    @Test
    fun classTypeAliasesAndCompositeKeysRemainDistinct() {
        assertEquals("Wykład", AttendanceTracker.type(lesson("1", "", "lecture", "2026-10-17")))
        assertEquals("Ćwiczenia", AttendanceTracker.type(lesson("1", "", "CW", "2026-10-17")))
        assertEquals("Laboratorium", AttendanceTracker.type(lesson("1", "", "Laboratory", "2026-10-17")))
        assertNotEquals(AttendanceTracker.key("a|b", "c", "d"), AttendanceTracker.key("a", "b|c", "d"))
    }
}
