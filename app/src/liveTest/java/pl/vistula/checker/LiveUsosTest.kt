package pl.vistula.checker

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly enabled with -PliveApiTests; never part of offline release verification. */
@RunWith(AndroidJUnit4::class)
class LiveUsosTest {
    @Test
    fun publicInstallationWorksWithoutCredentials() {
        val method = JSONObject(
            "{\"name\":\"services/apisrv/installation\",\"auth_options\":{\"consumer\":\"ignored\",\"token\":\"ignored\",\"administrative_only\":false}}"
        )
        val response = UsosClient(JSONObject()).explore(method, emptyMap(), "GET", null, null)
        val result = JSONObject(response.text())
        assertEquals(UsosClient.BASE, result.getString("base_url"))
        assertTrue(result.getJSONObject("institution_name").getString("pl").contains("Vistula"))
    }
}
