package pl.vistula.checker

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class StudentServiceLoaderTest {
    @Test
    fun failedRefreshKeepsPreviouslyFetchedDataAndTime() {
        val store = JSONObject().put(
            "cards",
            JSONObject().put("data", JSONArray().put("old")).put("fetchedAt", 123L).put("error", "")
        )
        StudentServiceLoader.fetch(store, "cards") { throw IOException("Offline") }
        assertEquals("old", store.getJSONObject("cards").getJSONArray("data").getString(0))
        assertEquals(123L, store.getJSONObject("cards").getLong("fetchedAt"))
        assertFalse(store.getJSONObject("cards").getString("error").isEmpty())
    }

    @Test
    fun successfulRefreshReplacesOnlySelectedServiceAndClearsError() {
        val store = JSONObject().put("cards", JSONObject().put("error", "Offline"))
            .put("documents", JSONObject().put("data", "untouched"))
        StudentServiceLoader.fetch(store, "cards") { JSONArray() }
        assertEquals("", store.getJSONObject("cards").getString("error"))
        assertTrue(store.getJSONObject("cards").getLong("fetchedAt") > 0)
        assertEquals("untouched", store.getJSONObject("documents").getString("data"))
    }

    @Test
    fun mergingCardsPreservesDocumentsFetchedByAnotherRequest() {
        val state = account().put(
            "studentServices",
            JSONObject().put("documents", entry("new documents", 20)).put("cards", entry("old cards", 1))
        )
        StudentServiceLoader.applyUpdates(state, account(), JSONObject().put("cards", entry("new cards", 10)))
        val services = state.getJSONObject("studentServices")
        assertEquals("new documents", services.getJSONObject("documents").getString("data"))
        assertEquals("new cards", services.getJSONObject("cards").getString("data"))
    }

    @Test
    fun unrelatedCachedErrorsDoNotDisableSyncAgain() {
        val state = account().put("enabled", true).put(
            "studentServices",
            JSONObject().put("mcard", entry("cached", 1).put("authorizationError", true).put("retryAt", Long.MAX_VALUE))
        )
        StudentServiceLoader.applyUpdates(state, account(), JSONObject().put("documents", entry("new", 10)))
        assertTrue(state.getBoolean("enabled"))
        assertFalse(state.optBoolean("authorizationError"))
        assertEquals(0L, state.optLong("retryAt"))
    }

    @Test
    fun lateOlderResponseCannotReplaceNewerServiceDataOrRestoreItsError() {
        val state = account().put("enabled", true).put("studentServices", JSONObject().put("cards", entry("fresh", 20)))
        val old = entry("stale", 10).put("authorizationError", true).put("retryAt", Long.MAX_VALUE)
        StudentServiceLoader.applyUpdates(state, account(), JSONObject().put("cards", old))
        assertEquals("fresh", state.getJSONObject("studentServices").getJSONObject("cards").getString("data"))
        assertTrue(state.getBoolean("enabled"))
        assertFalse(state.optBoolean("authorizationError"))
    }

    @Test
    fun credentialChangesRejectCachedResultsBeforeMutatingState() {
        for (key in arrayOf("token", "tokenSecret", "key", "secret", "accountSession")) {
            val state = account().put(key, "changed")
            val before = state.toString()
            try {
                StudentServiceLoader.applyUpdates(state, account(), JSONObject().put("cards", entry("private", 1)))
                fail()
            } catch (expected: IllegalStateException) {
            }
            assertEquals(before, state.toString())
        }
    }

    @Test
    fun retryableErrorsStopFurtherCallsEvenWithoutRetryAfter() {
        for (status in intArrayOf(401, 429, 503)) {
            val store = JSONObject()
            val proceed = StudentServiceLoader.fetch(store, "cards") { throw UsosClient.ApiException(status, "api_error") }
            assertFalse("Must stop after HTTP $status", proceed)
            if (status == 401) assertEquals(0L, store.getJSONObject("cards").getLong("retryAt"))
            else assertTrue(store.getJSONObject("cards").getLong("retryAt") > System.currentTimeMillis())
        }
        assertFalse(StudentServiceLoader.fetch(JSONObject(), "cards") { throw SocketTimeoutException() })
        assertTrue(StudentServiceLoader.fetch(JSONObject(), "cards") { throw UsosClient.ApiException(403, "api_error") })
    }

    @Test
    fun freshTransportFailureDoesNotReuseAuthorizationFlagsFromAnOlderAttempt() {
        val store = JSONObject().put("cards", entry("cached", 1).put("authorizationError", true).put("retryAt", Long.MAX_VALUE))
        StudentServiceLoader.fetch(store, "cards") { throw IOException() }
        val result = store.getJSONObject("cards")
        assertEquals("cached", result.getString("data"))
        assertFalse(result.getBoolean("authorizationError"))
        assertEquals(0L, result.getLong("retryAt"))
    }

    private fun account(): JSONObject =
        JSONObject().put("token", "test-token").put("tokenSecret", "test-token-secret")
            .put("key", "test-key").put("secret", "test-secret")

    private fun entry(data: String, attemptAt: Long): JSONObject =
        JSONObject().put("data", data).put("attemptAt", attemptAt)
}
