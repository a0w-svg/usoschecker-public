package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NullableGradesTest {
    @Test
    fun explicitNullGradeCollectionsAreEmptyWhileValidGradesRemain() {
        val snapshot = Snapshot("2026-10-12", "2026-12-07")
        val response = JSONObject(
            "{\"2026Z\":{\"EMPTY\":{\"course_grades\":null,\"course_units_grades\":null},\"UNIT\":{\"course_grades\":[],\"course_units_grades\":{\"1\":null}},\"VALID\":{\"course_grades\":[{\"1\":{\"exam_id\":1,\"exam_session_number\":1,\"value_symbol\":\"5\",\"date_modified\":null,\"comment\":null}}],\"course_units_grades\":{}}}}"
        )
        UsosParser.grades(snapshot, response, emptyMap())
        assertEquals(1, snapshot.records.size)
        assertEquals("", snapshot.records["grade:1:1"]!!.sourceTime)
        assertFalse(snapshot.records["grade:1:1"]!!.value.contains("null"))
    }

    @Test
    fun nullableStringsBehaveTheSameWithAndroidJsonSemantics() {
        val grade = object : JSONObject() {
            override fun optString(name: String, fallback: String): String =
                if (isNull(name)) "null" else super.optString(name, fallback)
        }
        grade.put("exam_id", 1).put("exam_session_number", 1).put("value_symbol", "5")
            .put("date_modified", JSONObject.NULL).put("comment", JSONObject.NULL)
        val edition = JSONObject()
            .put("course_grades", JSONArray().put(JSONObject().put("1", grade)))
            .put("course_units_grades", JSONObject())
        val snapshot = Snapshot("2026-10-12", "2026-12-07")
        UsosParser.grades(snapshot, JSONObject().put("2026Z", JSONObject().put("FIN", edition)), emptyMap())
        assertEquals("", snapshot.records["grade:1:1"]!!.sourceTime)
        assertFalse(snapshot.records["grade:1:1"]!!.value.contains("null"))
    }

    @Test(expected = JSONException::class)
    fun missingFieldsStillRejectIncompleteResponses() {
        UsosParser.grades(
            Snapshot("2026-10-12", "2026-12-07"),
            JSONObject("{\"2026Z\":{\"FIN\":{\"course_grades\":null}}}"),
            emptyMap()
        )
    }

    @Test(expected = JSONException::class)
    fun wrongCollectionTypesStillFailValidation() {
        UsosParser.grades(
            Snapshot("2026-10-12", "2026-12-07"),
            JSONObject("{\"2026Z\":{\"FIN\":{\"course_grades\":\"bad\",\"course_units_grades\":{}}}}"),
            emptyMap()
        )
    }
}
