package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.vistula.checker.ToolServiceData

/**
 * Answers shaped like the fields the USOS methods really declare (see scripts/check_field_selectors.py).
 * The earlier tests fed these parsers invented fields, so requests for fields that do not exist went unnoticed.
 */
class RealShapeServicesTest {
    private fun text(value: Any?): String = ApiLanguage.text(value, "pl")

    @Test
    fun surveyAndExamReportFieldsReachTheLecturerReports() {
        val reports = LecturerServices.safeLecturerReports(
            JSONArray().put(JSONObject().put("id", "s1").put("name", JSONObject().put("pl", "Ankieta")).put("filled_out_count", 12)),
            JSONArray().put(JSONObject().put("id", "r1").put("name", JSONObject().put("pl", "Egzamin dyplomowy")).put("status", "in_progress"))
        )
        assertEquals(12, reports.getJSONArray("surveys").getJSONObject(0).getInt("number_of_answers"))
        val report = reports.getJSONArray("signableTheses").getJSONObject(0)
        assertEquals("Egzamin dyplomowy", text(report.opt("title")))
        assertEquals("in_progress", report.getString("status"))
    }

    @Test
    fun mobilityUsesReceivingInstitutionAndStudentProgramme() {
        val row = LecturerServices.safeMobilities(JSONArray().put(JSONObject()
            .put("id", "m1").put("role", "student").put("state", "active")
            .put("student_programme", JSONObject().put("id", "9").put("programme", JSONObject().put("id", "P1").put("description", JSONObject().put("pl", "Informatyka"))))
            .put("receiving_institution", JSONObject().put("id", "77").put("name", JSONObject().put("pl", "Universidad de Sevilla")))
            .put("start_date", "2027-02-01").put("end_date", "2027-06-30"))).getJSONObject(0)
        assertEquals("Informatyka", text(row.opt("programme")))
        assertEquals("Universidad de Sevilla", text(row.opt("institution")))
        assertEquals("student", text(row.opt("category")))
        assertEquals("active", text(row.opt("state")))
        assertEquals("2027-02-01", row.getString("start_date"))
    }

    @Test
    fun placementResultsAndSlipsArriveAsDictionaries() {
        // plctests/user: a dictionary keyed by test ID; slips/to_be_answered: rows under "items".
        val tests = ToolServiceData.rows("""{"7": {"user_id": "1", "test": {"id": "7", "name": {"pl": "Angielski"}}, "value": 62}, "8": null}""")
        val slips = ToolServiceData.rows("""{"items": [{"id": "c1", "slip": {"id": "s1"}, "question": {"id": "q1", "question": "BHP"}}], "next_page": false}""")
        assertEquals(1, tests.length())
        val parsed = LecturerServices.safePlacementAndSlips(tests, slips, JSONArray())
        val test = parsed.getJSONArray("placementTests").getJSONObject(0)
        assertEquals("Angielski", text(test.opt("name")))
        assertEquals("62", test.getString("score"))
        assertEquals("BHP", parsed.getJSONArray("slipsToAnswer").getJSONObject(0).getString("question"))
        assertEquals(2, ToolServiceData.rows("""[{"id": 1}, {"id": 2}]""").length())
        assertEquals(0, ToolServiceData.rows("null").length())
    }

    @Test
    fun allocationPeriodIsNamedByItsStatusAndDormitoryNeedsNoAddress() {
        val guide = LecturerServices.safeCampusGuide(
            JSONArray(),
            JSONArray().put(JSONObject().put("id", "d1").put("name", JSONObject().put("pl", "Dom Studenta")).put("short_name", JSONObject().put("pl", "DS1"))),
            JSONArray(),
            JSONArray().put(JSONObject().put("id", "a1").put("term_id", "2026Z").put("status_name", JSONObject().put("pl", "Aktywna"))
                .put("status_desc", JSONObject().put("pl", "Trwa przydział miejsc")).put("start_date", "2026-09-01 00:00:00").put("end_date", "2026-09-30 00:00:00"))
        )
        assertEquals("Dom Studenta", text(guide.getJSONArray("dormitories").getJSONObject(0).opt("name")))
        val period = guide.getJSONArray("allocationPeriods").getJSONObject(0)
        assertEquals("Aktywna", text(period.opt("name")))
        assertEquals("Trwa przydział miejsc", text(period.opt("description")))
        assertEquals("2026Z", period.getString("term_id"))
    }

    @Test
    fun statementTitleAndContentAreLanguageDictionaries() {
        val comms = LecturerServices.safeCommunications(
            JSONArray().put(JSONObject().put("id", "st1").put("title", JSONObject().put("pl", "Godziny dziekańskie"))
                .put("content", JSONObject().put("pl", "<p>Zajęcia <b>odwołane</b></p>")).put("end", "2026-11-12 16:00:00")),
            JSONArray(), JSONArray(), JSONObject()
        )
        val statement = comms.getJSONArray("statements").getJSONObject(0)
        assertEquals("Godziny dziekańskie", statement.getString("subject"))
        assertEquals("Zajęcia odwołane", statement.getString("content").trim().replace(Regex("\\s+"), " "))
        assertEquals("2026-11-12", statement.getString("expiration_date"))
    }
}
