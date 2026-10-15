package pl.vistula.checker.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ApiCatalogTest {
    @Test public void catalogSearchUsesPolishAliasesAndRespectsModuleSelection() {
        JSONObject cards = new JSONObject().put("name", "services/cards/mcard2").put("brief_description", "Student card");
        assertTrue(ApiCatalog.matches(cards, "", "legitymacje"));
        assertFalse(ApiCatalog.matches(cards, "grades", "legitymacje"));
        assertEquals("examrep2", ApiCatalog.module("services/examrep2/exam/grades"));
        assertEquals(1, (int) ApiCatalog.modules(new JSONArray().put(cards)).get("cards"));
    }
    @Test public void restoredDraftsExcludeCredentialsAndActivationData() throws Exception {
        JSONObject draft = new JSONObject().put("order_id", "42").put("token", "secret").put("distribution_pass", "secret")
            .put("code", "secret").put("fields", "id|status").put("__transport", 1);
        JSONObject safe = ApiCatalog.restorableDrafts(new JSONObject().put("services/cards/mcard_order2", draft));
        assertFalse(safe.toString().contains("secret")); assertTrue(safe.toString().contains("42"));
        assertTrue(safe.toString().contains("__transport"));
    }
    @Test public void permissionCheckUsesWholeScopeNames() {
        JSONObject metadata = new JSONObject().put("auth_options", new JSONObject().put("scopes", new JSONArray().put("cards").put("grades")));
        assertEquals(java.util.List.of("cards"), ApiCatalog.missingScopes(metadata, "grades|old_cards"));
        assertTrue(ApiCatalog.missingScopes(metadata, "grades|cards").isEmpty());
    }
    @Test public void credentialResponsesIncludeLegacyAndCurrentMobileActivationEndpoints() {
        for (String method : java.util.List.of("services/cards/mcard", "services/cards/mcard2", "services/cards/mcard_order",
                "services/cards/mcard_order2", "services/oauth/access_token", "services/oauth2/impersonated_token"))
            assertTrue(method, ApiCatalog.credentialResponse(method));
        assertFalse(ApiCatalog.credentialResponse("services/cards/user"));
        assertFalse(ApiCatalog.credentialResponse("services/apisrv/installation"));
    }
}
