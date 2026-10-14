package pl.vistula.checker.core;

import java.util.Map;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NullableGradesTest {
    @Test public void explicitNullGradeCollectionsAreEmptyWhileValidGradesRemain() {
        Snapshot snapshot = new Snapshot("2026-10-12", "2026-12-07");
        JSONObject response = new JSONObject("{\"2026Z\":{\"EMPTY\":{\"course_grades\":null,\"course_units_grades\":null},\"UNIT\":{\"course_grades\":[],\"course_units_grades\":{\"1\":null}},\"VALID\":{\"course_grades\":[{\"1\":{\"exam_id\":1,\"exam_session_number\":1,\"value_symbol\":\"5\",\"date_modified\":null,\"comment\":null}}],\"course_units_grades\":{}}}}");
        UsosParser.grades(snapshot, response, Map.of());
        assertEquals(1, snapshot.records.size());
        assertEquals("", snapshot.records.get("grade:1:1").sourceTime);
        assertFalse(snapshot.records.get("grade:1:1").value.contains("null"));
    }
    @Test public void nullableStringsBehaveTheSameWithAndroidJsonSemantics() {
        JSONObject grade = new JSONObject() {
            @Override public String optString(String name, String fallback) { return isNull(name) ? "null" : super.optString(name, fallback); }
        };
        grade.put("exam_id", 1).put("exam_session_number", 1).put("value_symbol", "5").put("date_modified", JSONObject.NULL).put("comment", JSONObject.NULL);
        JSONObject edition = new JSONObject().put("course_grades", new JSONArray().put(new JSONObject().put("1", grade))).put("course_units_grades", new JSONObject());
        Snapshot snapshot = new Snapshot("2026-10-12", "2026-12-07");
        UsosParser.grades(snapshot, new JSONObject().put("2026Z", new JSONObject().put("FIN", edition)), Map.of());
        assertEquals("", snapshot.records.get("grade:1:1").sourceTime);
        assertFalse(snapshot.records.get("grade:1:1").value.contains("null"));
    }
    @Test(expected = JSONException.class) public void missingFieldsStillRejectIncompleteResponses() {
        UsosParser.grades(new Snapshot("2026-10-12", "2026-12-07"), new JSONObject("{\"2026Z\":{\"FIN\":{\"course_grades\":null}}}"), Map.of());
    }
    @Test(expected = JSONException.class) public void wrongCollectionTypesStillFailValidation() {
        UsosParser.grades(new Snapshot("2026-10-12", "2026-12-07"), new JSONObject("{\"2026Z\":{\"FIN\":{\"course_grades\":\"bad\",\"course_units_grades\":{}}}}"), Map.of());
    }
}
