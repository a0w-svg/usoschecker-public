package pl.vistula.checker.core

import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun onlyGetRequestsAreRetriedAndOnlyTwice() {
        assertTrue(RetryPolicy.allows("GET", 0))
        assertTrue(RetryPolicy.allows("GET", 1))
        assertFalse(RetryPolicy.allows("GET", 2))
        assertFalse(RetryPolicy.allows("POST", 0))
    }

    @Test
    fun timeoutsAndDroppedConnectionsAreTransientButOfflineAndProtocolErrorsAreNot() {
        assertTrue(RetryPolicy.retryableNetworkError(SocketTimeoutException()))
        assertTrue(RetryPolicy.retryableNetworkError(ConnectException()))
        assertTrue(RetryPolicy.retryableNetworkError(SocketException("Connection reset")))
        assertFalse(RetryPolicy.retryableNetworkError(UnknownHostException("offline")))
        assertFalse(RetryPolicy.retryableNetworkError(IOException("Odpowiedź API przekracza limit 8 MB.")))
        assertFalse(RetryPolicy.retryableNetworkError(IllegalStateException()))
    }

    @Test
    fun backoffGrowsAndJitterIsBounded() {
        assertEquals(400L, RetryPolicy.delayMillis(0))
        assertEquals(1_200L, RetryPolicy.delayMillis(1))
        assertEquals(1_200L, RetryPolicy.delayMillis(7))
        assertEquals(550L, RetryPolicy.delayMillis(0, 1.0))
        assertEquals(550L, RetryPolicy.delayMillis(0, 9.0))
    }
}
