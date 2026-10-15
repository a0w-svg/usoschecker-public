package pl.vistula.checker.core;

import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class GradeDetailsTest {
    @Test public void changedPassFlagsAreDetectedWithoutTreatingTypeEnrichmentAsGradeChanges() {
        Record before = new Record("g", "ocena", "A", "4", "", "", "", "", "", Double.NaN,
            new JSONObject().put("passes", true).put("countsIntoAverage", true));
        Record after = new Record("g", "ocena", "A", "4", "", "", "", "", "", Double.NaN,
            new JSONObject().put("passes", false).put("countsIntoAverage", true));
        assertFalse(before.same(after));
        Record enriched = new Record("g", "ocena", "A", "4", "", "", "", "", "", Double.NaN,
            new JSONObject().put("passes", true).put("countsIntoAverage", true).put("classType", "Laboratorium").put("classTypeSource", "unit_api"));
        assertTrue(before.same(enriched));
    }
    @Test public void weightedEstimatePrefersCourseProtocolsAndRespectsExclusions() {
        Record course = new Record("course", "ocena", "Same name", "3", "", "", "2026Z", "", "", 5,
            new JSONObject().put("courseId", "A").put("gradeScope", "course").put("countsIntoAverage", true));
        Record unit = new Record("unit", "ocena", "Same name", "5", "", "", "2026Z", "", "", 5,
            new JSONObject().put("courseId", "A").put("gradeScope", "unit").put("countsIntoAverage", true));
        Record excluded = new Record("excluded", "ocena", "Excluded", "5", "", "", "2026Z", "", "", 10,
            new JSONObject().put("countsIntoAverage", false));
        Record otherCourse = new Record("other", "ocena", "Same name", "4", "", "", "2026Z", "", "", 5,
            new JSONObject().put("courseId", "B").put("gradeScope", "course"));
        GradeSummary summary = GradeSummary.of(java.util.List.of(course, unit, excluded, otherCourse));
        assertEquals(3.5, summary.ectsAverage, .001); assertEquals(10, summary.totalEcts, .001);
        assertEquals(4, summary.numeric);
    }
    @Test public void gradingScaleAndCommentsNeverDetermineClassType() throws Exception {
        JSONObject grade = new JSONObject().put("exam_id", "1").put("exam_session_number", 2).put("value_symbol", "A")
            .put("grade_type_id", "LAB").put("comment", "Egzamin po laboratorium").put("unknown_api_field", "preserved")
            .put("date_acquisition", JSONObject.NULL).put("passes", false);
        JSONObject response = new JSONObject().put("2026Z", new JSONObject().put("COURSE", new JSONObject()
            .put("course_grades", new JSONArray().put(new JSONObject().put("2", grade))).put("course_units_grades", new JSONObject())));
        Snapshot snapshot = new Snapshot("2026-10-12", "2026-11-12");
        UsosParser.grades(snapshot, response, Map.of());
        Record record = snapshot.records.get("grade:1:2");
        assertEquals("Ocena z protokołu przedmiotu", GradeBrowser.classType(record, snapshot));
        assertEquals("preserved", record.metadata.getJSONObject("rawGrade").getString("unknown_api_field"));
        assertTrue(record.metadata.getJSONObject("rawGrade").isNull("date_acquisition"));
        assertEquals("LAB", RecordDetails.fields(record, snapshot).get("ID systemu oceniania"));
        assertEquals("Nie", RecordDetails.fields(record, snapshot).get("Zaliczenie według USOS"));
    }
    @Test public void unknownUnitsAndPreviouslyGuessedLabelsDoNotBecomeLecturesOrLabs() {
        Record unit = new Record("g", "ocena", "A", "5", "", "", "2026Z", "", "", Double.NaN,
            new JSONObject().put("unitId", "unit-1").put("gradeScope", "unit").put("classType", "Laboratorium / Ćwiczenia"));
        assertEquals("Ocena z protokołu zajęć", GradeBrowser.classType(unit));
        assertTrue(GradeBrowser.select(java.util.List.of(unit), "", "labs", "name", "").isEmpty());
        Record course = new Record("g2", "ocena", "A", "5", "", "", "2026Z", "", "", Double.NaN,
            new JSONObject().put("gradeScope", "course").put("classType", "Wykład"));
        assertEquals("Ocena z protokołu przedmiotu", GradeBrowser.classType(course));
    }
    @Test public void extraStoredMetadataIsNotOmittedFromDetails() {
        Record record = new Record("g", "ocena", "A", "4", "", "", "", "", "", Double.NaN,
            new JSONObject().put("future_field", "new-value").put("countsIntoAverage", JSONObject.NULL));
        assertEquals("new-value", RecordDetails.fields(record, null).get("future_field"));
        assertEquals("Nie podano", RecordDetails.fields(record, null).get("Wliczanie do średniej według USOS"));
    }
}
