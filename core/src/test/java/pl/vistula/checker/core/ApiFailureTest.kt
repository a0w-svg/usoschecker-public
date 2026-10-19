package pl.vistula.checker.core

import org.junit.Assert.*
import org.junit.Test

class ApiFailureTest {
    @Test
    fun expiredTokensAreNotRetriedEvenOnServerErrors() {
        val failure = ApiFailure(503, "oauth_problem=token_expired&oauth_token=secret", "60", 1000)
        assertTrue(failure.authorization)
        assertFalse(failure.retryable)
        assertEquals(0L, failure.retryAt)
        assertEquals("token_expired", failure.code)
    }

    @Test
    fun signatureErrorsDoNotDeclareTheSessionExpired() {
        val failure = ApiFailure(401, "oauth_problem=signature_invalid", null, 1000)
        assertFalse(failure.authorization)
        assertFalse(failure.retryable)
        assertTrue(ApiFailure(401, "<html>unauthorized</html>", null, 1000).authorization)
    }

    @Test
    fun serverRetryDelaySupportsSecondsAndHttpDates() {
        assertEquals(61000L, ApiFailure(429, "{}", "60", 1000).retryAt)
        assertEquals(60000L, ApiFailure(503, "{}", "Thu, 1 Jan 1970 00:01:00 GMT", 1000).retryAt)
        assertEquals(1000L, ApiFailure(503, "{}", "Thu, 1 Jan 1970 00:00:00 GMT", 1000).retryAt)
        assertTrue(ApiFailure(408, "{}", null, 1000).retryable)
        assertFalse(ApiFailure(403, "{}", "60", 1000).retryable)
    }

    @Test
    fun malformedResponsesAndRetryHeadersAreSafe() {
        for (header in arrayOf("garbage", "-1", "9223372036854775807")) {
            assertEquals(0L, ApiFailure(429, "{}", header, 1000).retryAt)
        }
        for (body in arrayOf("secret text", "oauth_problem=%ZZ", "{\"code\":\"token=secret\"}", "oauth_problem=token_rejected_extra")) {
            val failure = ApiFailure(400, body, null, 1000)
            assertFalse(failure.authorization)
            assertFalse(failure.code.contains("secret"))
        }
        assertTrue(ApiFailure(400, "{\"code\":\"invalid_token\"}", null, 1000).authorization)
    }
}
