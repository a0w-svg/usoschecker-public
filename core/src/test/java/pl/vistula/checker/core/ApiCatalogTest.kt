package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiCatalogTest {
    @Test
    fun catalogSearchUsesPolishAliasesAndRespectsModuleSelection() {
        val cards = JSONObject().put("name", "services/cards/mcard2").put("brief_description", "Student card")
        assertTrue(ApiCatalog.matches(cards, "", "legitymacje"))
        assertFalse(ApiCatalog.matches(cards, "grades", "legitymacje"))
        assertEquals("examrep2", ApiCatalog.module("services/examrep2/exam/grades"))
        assertEquals(1, ApiCatalog.modules(JSONArray().put(cards))["cards"]!!.toInt())
    }

    @Test
    fun restoredDraftsExcludeCredentialsAndActivationData() {
        val draft = JSONObject().put("order_id", "42").put("token", "secret").put("distribution_pass", "secret")
            .put("code", "secret").put("key", "secret").put("fields", "id|status").put("__transport", 1)
        val safe = ApiCatalog.restorableDrafts(JSONObject().put("services/cards/mcard_order2", draft))
        assertFalse(safe.toString().contains("secret"))
        assertTrue(safe.toString().contains("42"))
        assertTrue(safe.toString().contains("__transport"))
        assertTrue(ApiCatalog.sensitiveParameter("key"))
    }

    @Test
    fun permissionCheckUsesWholeScopeNames() {
        val metadata = JSONObject().put("auth_options", JSONObject().put("scopes", JSONArray().put("cards").put("grades")))
        assertEquals(listOf("cards"), ApiCatalog.missingScopes(metadata, "grades|old_cards"))
        assertTrue(ApiCatalog.missingScopes(metadata, "grades|cards").isEmpty())
    }

    @Test
    fun credentialResponsesIncludeLegacyAndCurrentMobileActivationEndpoints() {
        for (method in listOf(
            "services/cards/mcard", "services/cards/mcard2", "services/cards/mcard_order",
            "services/cards/mcard_order2", "services/oauth/access_token", "services/oauth2/impersonated_token"
        )) {
            assertTrue(method, ApiCatalog.credentialResponse(method))
        }
        assertFalse(ApiCatalog.credentialResponse("services/cards/user"))
        assertFalse(ApiCatalog.credentialResponse("services/apisrv/installation"))
    }
}
