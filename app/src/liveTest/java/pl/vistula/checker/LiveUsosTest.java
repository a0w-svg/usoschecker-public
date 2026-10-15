package pl.vistula.checker;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Explicitly enabled with -PliveApiTests; never part of offline release verification. */
@RunWith(AndroidJUnit4.class)
public class LiveUsosTest {
    @Test public void publicInstallationWorksWithoutCredentials() throws Exception {
        JSONObject method = new JSONObject("{\"name\":\"services/apisrv/installation\",\"auth_options\":{\"consumer\":\"ignored\",\"token\":\"ignored\",\"administrative_only\":false}}");
        UsosClient.Response response = new UsosClient(new JSONObject()).explore(method, java.util.Map.of(), "GET", null, null);
        JSONObject result = new JSONObject(response.text());
        assertEquals(UsosClient.BASE, result.getString("base_url"));
        assertTrue(result.getJSONObject("institution_name").getString("pl").contains("Vistula"));
    }
}
