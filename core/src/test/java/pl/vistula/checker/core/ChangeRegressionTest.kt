package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChangeRegressionTest {
    private fun lesson(start: String, end: String, room: String, lecturer: String): Record =
        Record(
            "lesson", "plan", "Matematyka", "$start–$end · $room", "", "2026-10-15", "",
            "2026-10-15 $start:00", "2026-10-15 $end:00", Double.NaN,
            JSONObject().put("room", room).put("lecturers", lecturer)
        )

    @Test
    fun scheduleValueChangesNeverProduceGradeChanges() {
        val change = Change(lesson("09:00", "10:00", "204", "Anna"), lesson("10:00", "11:00", "315", "Jan"), 1)
        assertEquals("plan", change.kind)
        assertEquals("", change.optMeta("diffGrade"))
        assertEquals("09:00–10:00 → 10:00–11:00", change.optMeta("diffTime"))
        assertEquals("204 → 315", change.optMeta("diffRoom"))
    }

    @Test
    fun oldHistoryDropsInvalidGradeDiffWithoutMutatingSavedInput() {
        val saved = Change(null, lesson("09:00", "10:00", "204", "Anna"), 1).json()
        saved.getJSONObject("metadata").put("diffGrade", "09:00 → 10:00").put("diffRoom", "204 → 315")
        val restored = Change.from(saved)
        assertEquals("", restored.optMeta("diffGrade"))
        assertEquals("204 → 315", restored.optMeta("diffRoom"))
        assertFalse(restored.json().getJSONObject("metadata").has("diffGrade"))
        assertTrue(saved.getJSONObject("metadata").has("diffGrade"))
    }

    @Test
    fun actualGradeChangesStillIncludeTheGradeDiff() {
        val change = Change(
            Record("grade", "ocena", "Matematyka", "4 · dobry", "", ""),
            Record("grade", "ocena", "Matematyka", "5 · bardzo dobry", "", ""),
            1
        )
        assertEquals("4 → 5", change.optMeta("diffGrade"))
        assertEquals("4 → 5", Change.from(change.json()).optMeta("diffGrade"))
    }

    @Test
    fun changingOnlyTheEndTimeIsReported() {
        val change = Change(lesson("09:00", "10:00", "204", "Anna"), lesson("09:00", "10:30", "204", "Anna"), 1)
        assertEquals("09:00–10:00 → 09:00–10:30", change.optMeta("diffTime"))
    }

    @Test
    fun removingAndAddingScheduleDetailsIsReported() {
        val populated = lesson("09:00", "10:00", "204", "Anna")
        val empty = lesson("09:00", "10:00", "", "")
        val removed = Change(populated, empty, 1)
        val added = Change(empty, populated, 2)
        assertEquals("204 → (brak)", removed.optMeta("diffRoom"))
        assertEquals("Anna → (brak)", removed.optMeta("diffLecturers"))
        assertEquals("(brak) → 204", added.optMeta("diffRoom"))
        assertEquals("(brak) → Anna", added.optMeta("diffLecturers"))
    }
}
