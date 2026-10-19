package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CampusAffiliationTest {
    private fun programme(faculty: String, branch: String, status: String = "active") = JSONObject()
        .put("status", status).put("programme", JSONObject().put("faculty", JSONObject().put("id", faculty)
            .put("name", JSONObject().put("pl", "Faculty")).put("path", JSONArray()
                .put(JSONObject().put("id", "0000")).put(JSONObject().put("id", branch)))))

    @Test fun childFacultyResolvesItsExactBranchWithoutUsingCourseNames() {
        val result = CampusAffiliation.detect(JSONArray().put(programme("O1234", "O0000")))
        assertEquals("O0000", result.getString("detected"))
        assertEquals("O0000", result.getJSONArray("units").getJSONObject(0).getString("campus"))
    }

    @Test fun multipleActiveBranchesRequireAChoice() {
        val result = CampusAffiliation.detect(JSONArray().put(programme("A", "O0000")).put(programme("B", "G0000")))
        assertEquals("", result.getString("detected"))
        assertEquals(2, result.getJSONArray("candidates").length())
    }

    @Test fun missingAndInactiveAffiliationsNeverDefaultToWarsaw() {
        assertEquals("", CampusAffiliation.detect(JSONArray()).getString("detected"))
        assertEquals("", CampusAffiliation.detect(JSONArray().put(programme("A", "O0000", "cancelled"))).getString("detected"))
        assertEquals("", CampusAffiliation.detect(JSONArray().put(programme("UNKNOWN", "NEW_BRANCH"))).getString("detected"))
    }

    @Test fun manualChoiceOverridesDetectionAndCanBeRemoved() {
        val state = JSONObject().put("studentServices", JSONObject().put("affiliation", JSONObject().put("data",
            JSONObject().put("detected", "O0000"))))
        assertEquals("O0000", CampusAffiliation.selected(state))
        state.put("campusOverride", "G0000")
        assertEquals("G0000", CampusAffiliation.selected(state))
        state.remove("campusOverride")
        assertEquals("O0000", CampusAffiliation.selected(state))
    }

    @Test fun branchChangesHidePreviouslyCachedNewsAndCentralPublicFeed() {
        val state = JSONObject().put("campusOverride", "O0000")
        assertFalse(CampusAffiliation.newsMatches(JSONObject().put("campus", "0000"), state))
        assertFalse(CampusAffiliation.canShowPublicNews(state))
        assertTrue(CampusAffiliation.newsMatches(JSONObject().put("campus", "O0000"), state))
        assertFalse(CampusAffiliation.newsMatches(JSONObject(), state))
    }
}
