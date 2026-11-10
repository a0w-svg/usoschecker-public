package pl.vistula.checker

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The background sync retries only failures that can heal within minutes. */
class SyncRetryTest {
    @Test
    fun networkFailuresAndRetryableAnswersAreRetriedUpToThreeTimes() {
        assertTrue(SyncWorker.shouldRetry(SocketTimeoutException(), 0))
        assertTrue(SyncWorker.shouldRetry(IOException("offline"), 2))
        assertTrue(SyncWorker.shouldRetry(UsosClient.ApiException(503, "server"), 1))
        assertFalse(SyncWorker.shouldRetry(IOException("offline"), 3))
    }

    @Test
    fun failuresThatRepeatAreLeftForTheNextPeriod() {
        // Cooldown and missing scope are reported as IllegalStateException by the repository and client.
        assertFalse(SyncWorker.shouldRetry(IllegalStateException("cooldown"), 0))
        assertFalse(SyncWorker.shouldRetry(UsosClient.ApiException(403, "forbidden"), 0))
        assertFalse(SyncWorker.shouldRetry(UsosClient.ApiException(401, "token_expired"), 0))
        assertFalse(SyncWorker.shouldRetry(VaultUnreadableException(), 0))
        assertFalse(SyncWorker.shouldRetry(org.json.JSONException("shape"), 0))
    }

    @Test
    fun emailShapeCheckAcceptsOrdinaryAddressesOnly() {
        assertTrue(KeyRegistration.plausibleEmail("student@vistula.edu.pl"))
        assertFalse(KeyRegistration.plausibleEmail("student"))
        assertFalse(KeyRegistration.plausibleEmail("a b@example.com"))
        assertFalse(KeyRegistration.plausibleEmail("a@b"))
        assertFalse(KeyRegistration.plausibleEmail("a@@example.com"))
    }
}
