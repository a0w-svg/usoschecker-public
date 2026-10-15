package pl.vistula.checker;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudentServiceLoaderTest {
    @Test public void failedRefreshKeepsPreviouslyFetchedDataAndTime() throws Exception {
        JSONObject store = new JSONObject().put("cards", new JSONObject().put("data", new JSONArray().put("old"))
            .put("fetchedAt", 123L).put("error", ""));
        StudentServiceLoader.fetch(store, "cards", () -> { throw new java.io.IOException("Offline"); });
        assertEquals("old", store.getJSONObject("cards").getJSONArray("data").getString(0));
        assertEquals(123L, store.getJSONObject("cards").getLong("fetchedAt"));
        assertFalse(store.getJSONObject("cards").getString("error").isEmpty());
    }
    @Test public void successfulRefreshReplacesOnlySelectedServiceAndClearsError() throws Exception {
        JSONObject store = new JSONObject().put("cards", new JSONObject().put("error", "Offline"))
            .put("documents", new JSONObject().put("data", "untouched"));
        StudentServiceLoader.fetch(store, "cards", () -> new JSONArray());
        assertEquals("", store.getJSONObject("cards").getString("error"));
        assertTrue(store.getJSONObject("cards").getLong("fetchedAt") > 0);
        assertEquals("untouched", store.getJSONObject("documents").getString("data"));
    }
}
