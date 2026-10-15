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
    @Test public void mergingCardsPreservesDocumentsFetchedByAnotherRequest() throws Exception {
        JSONObject state = account().put("studentServices", new JSONObject()
            .put("documents", entry("new documents", 20)).put("cards", entry("old cards", 1)));
        StudentServiceLoader.applyUpdates(state, account(), new JSONObject().put("cards", entry("new cards", 10)));
        JSONObject services = state.getJSONObject("studentServices");
        assertEquals("new documents", services.getJSONObject("documents").getString("data"));
        assertEquals("new cards", services.getJSONObject("cards").getString("data"));
    }
    @Test public void unrelatedCachedErrorsDoNotDisableSyncAgain() throws Exception {
        JSONObject state = account().put("enabled", true).put("studentServices", new JSONObject()
            .put("mcard", entry("cached", 1).put("authorizationError", true).put("retryAt", Long.MAX_VALUE)));
        StudentServiceLoader.applyUpdates(state, account(), new JSONObject().put("documents", entry("new", 10)));
        assertTrue(state.getBoolean("enabled"));
        assertFalse(state.optBoolean("authorizationError"));
        assertEquals(0, state.optLong("retryAt"));
    }
    @Test public void lateOlderResponseCannotReplaceNewerServiceDataOrRestoreItsError() throws Exception {
        JSONObject state = account().put("enabled", true).put("studentServices", new JSONObject().put("cards", entry("fresh", 20)));
        JSONObject old = entry("stale", 10).put("authorizationError", true).put("retryAt", Long.MAX_VALUE);
        StudentServiceLoader.applyUpdates(state, account(), new JSONObject().put("cards", old));
        assertEquals("fresh", state.getJSONObject("studentServices").getJSONObject("cards").getString("data"));
        assertTrue(state.getBoolean("enabled"));
        assertFalse(state.optBoolean("authorizationError"));
    }
    @Test public void credentialChangesRejectCachedResultsBeforeMutatingState() throws Exception {
        for (String key : new String[]{"token", "tokenSecret", "key", "secret", "accountSession"}) {
            JSONObject state = account().put(key, "changed");
            String before = state.toString();
            try { StudentServiceLoader.applyUpdates(state, account(), new JSONObject().put("cards", entry("private", 1))); fail(); }
            catch (IllegalStateException expected) {}
            assertEquals(before, state.toString());
        }
    }
    @Test public void retryableErrorsStopFurtherCallsEvenWithoutRetryAfter() throws Exception {
        for (int status : new int[]{401, 429, 503}) {
            JSONObject store = new JSONObject();
            boolean proceed = StudentServiceLoader.fetch(store, "cards", () -> { throw new UsosClient.ApiException(status, "api_error"); });
            assertFalse("Must stop after HTTP " + status, proceed);
            assertEquals(0, store.getJSONObject("cards").getLong("retryAt"));
        }
        assertFalse(StudentServiceLoader.fetch(new JSONObject(), "cards", () -> { throw new java.net.SocketTimeoutException(); }));
        assertTrue(StudentServiceLoader.fetch(new JSONObject(), "cards", () -> { throw new UsosClient.ApiException(403, "api_error"); }));
    }
    @Test public void freshTransportFailureDoesNotReuseAuthorizationFlagsFromAnOlderAttempt() throws Exception {
        JSONObject store = new JSONObject().put("cards", entry("cached", 1).put("authorizationError", true).put("retryAt", Long.MAX_VALUE));
        StudentServiceLoader.fetch(store, "cards", () -> { throw new java.io.IOException(); });
        JSONObject result = store.getJSONObject("cards");
        assertEquals("cached", result.getString("data"));
        assertFalse(result.getBoolean("authorizationError"));
        assertEquals(0, result.getLong("retryAt"));
    }
    private JSONObject account() throws Exception {
        return new JSONObject().put("token", "test-token").put("tokenSecret", "test-token-secret")
            .put("key", "test-key").put("secret", "test-secret");
    }
    private JSONObject entry(String data, long attemptAt) throws Exception {
        return new JSONObject().put("data", data).put("attemptAt", attemptAt);
    }
}
