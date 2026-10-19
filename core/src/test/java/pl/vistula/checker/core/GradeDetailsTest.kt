package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GradeDetailsTest {
    @Test
    fun changedPassFlagsAreDetectedWithoutTreatingTypeEnrichmentAsGradeChanges() {
        val before = Record(
            "g", "ocena", "A", "4", "", "", "", "", "", Double.NaN,
            JSONObject().put("passes", true).put("countsIntoAverage", true)
        )
        val after = Record(
            "g", "ocena", "A", "4", "", "", "", "", "", Double.NaN,
            JSONObject().put("passes", false).put("countsIntoAverage", true)
        )
        assertFalse(before.same(after))
        val enriched = Record(
            "g", "ocena", "A", "4", "", "", "", "", "", Double.NaN,
            JSONObject().put("passes", true).put("countsIntoAverage", true).put("classType", "Laboratorium").put("classTypeSource", "unit_api")
        )
        assertTrue(before.same(enriched))
    }

    @Test
    fun weightedEstimatePrefersCourseProtocolsAndRespectsExclusions() {
        val course = Record(
            "course", "ocena", "Same name", "3", "", "", "2026Z", "", "", 5.0,
            JSONObject().put("courseId", "A").put("gradeScope", "course").put("countsIntoAverage", true)
        )
        val unit = Record(
            "unit", "ocena", "Same name", "5", "", "", "2026Z", "", "", 5.0,
            JSONObject().put("courseId", "A").put("gradeScope", "unit").put("countsIntoAverage", true)
        )
        val excluded = Record(
            "excluded", "ocena", "Excluded", "5", "", "", "2026Z", "", "", 10.0,
            JSONObject().put("countsIntoAverage", false)
        )
        val otherCourse = Record(
            "other", "ocena", "Same name", "4", "", "", "2026Z", "", "", 5.0,
            JSONObject().put("courseId", "B").put("gradeScope", "course")
        )
        val summary = GradeSummary.of(listOf(course, unit, excluded, otherCourse))
        assertEquals(3.5, summary.ectsAverage, 0.001)
        assertEquals(10.0, summary.totalEcts, 0.001)
        assertEquals(4, summary.numeric)
    }

    @Test
    fun gradingScaleAndCommentsNeverDetermineClassType() {
        val grade = JSONObject().put("exam_id", "1").put("exam_session_number", 2).put("value_symbol", "A")
            .put("grade_type_id", "LAB").put("comment", "Egzamin po laboratorium").put("unknown_api_field", "preserved")
            .put("date_acquisition", JSONObject.NULL).put("passes", false)
        val response = JSONObject().put(
            "2026Z",
            JSONObject().put(
                "COURSE",
                JSONObject()
                    .put("course_grades", JSONArray().put(JSONObject().put("2", grade)))
                    .put("course_units_grades", JSONObject())
            )
        )
        val snapshot = Snapshot("2026-10-12", "2026-11-12")
        UsosParser.grades(snapshot, response, emptyMap())
        val record = snapshot.records["grade:1:2"]!!
        assertEquals("Ocena z protokołu przedmiotu", GradeBrowser.classType(record, snapshot))
        assertEquals("preserved", record.metadata.getJSONObject("rawGrade").getString("unknown_api_field"))
        assertTrue(record.metadata.getJSONObject("rawGrade").isNull("date_acquisition"))
        assertEquals("LAB", RecordDetails.fields(record, snapshot)["ID systemu oceniania"])
        assertEquals("Nie", RecordDetails.fields(record, snapshot)["Zaliczenie według USOS"])
    }

    @Test
    fun unknownUnitsAndPreviouslyGuessedLabelsDoNotBecomeLecturesOrLabs() {
        val unit = Record(
            "g", "ocena", "A", "5", "", "", "2026Z", "", "", Double.NaN,
            JSONObject().put("unitId", "unit-1").put("gradeScope", "unit").put("classType", "Laboratorium / Ćwiczenia")
        )
        assertEquals("Ocena z protokołu zajęć", GradeBrowser.classType(unit))
        assertTrue(GradeBrowser.select(listOf(unit), "", "labs", "name", "").isEmpty())
        val course = Record(
            "g2", "ocena", "A", "5", "", "", "2026Z", "", "", Double.NaN,
            JSONObject().put("gradeScope", "course").put("classType", "Wykład")
        )
        assertEquals("Ocena z protokołu przedmiotu", GradeBrowser.classType(course))
    }

    @Test
    fun extraStoredMetadataIsNotOmittedFromDetails() {
        val record = Record(
            "g", "ocena", "A", "4", "", "", "", "", "", Double.NaN,
            JSONObject().put("future_field", "new-value").put("countsIntoAverage", JSONObject.NULL)
        )
        assertEquals("new-value", RecordDetails.fields(record, null)["future_field"])
        assertEquals("Nie podano", RecordDetails.fields(record, null)["Wliczanie do średniej według USOS"])
    }
}
