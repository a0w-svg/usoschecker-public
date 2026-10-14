package pl.vistula.checker.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ApiFailureTest {
    @Test public void expiredTokensAreNotRetriedEvenOnServerErrors() {
        ApiFailure failure = new ApiFailure(503, "oauth_problem=token_expired&oauth_token=secret", "60", 1000);
        assertTrue(failure.authorization);
        assertFalse(failure.retryable);
        assertEquals(0, failure.retryAt);
        assertEquals("token_expired", failure.code);
    }
    @Test public void signatureErrorsDoNotDeclareTheSessionExpired() {
        ApiFailure failure = new ApiFailure(401, "oauth_problem=signature_invalid", null, 1000);
        assertFalse(failure.authorization);
        assertFalse(failure.retryable);
        assertTrue(new ApiFailure(401, "<html>unauthorized</html>", null, 1000).authorization);
    }
    @Test public void serverRetryDelaySupportsSecondsAndHttpDates() {
        assertEquals(61000, new ApiFailure(429, "{}", "60", 1000).retryAt);
        assertEquals(60000, new ApiFailure(503, "{}", "Thu, 1 Jan 1970 00:01:00 GMT", 1000).retryAt);
        assertEquals(1000, new ApiFailure(503, "{}", "Thu, 1 Jan 1970 00:00:00 GMT", 1000).retryAt);
        assertTrue(new ApiFailure(408, "{}", null, 1000).retryable);
        assertFalse(new ApiFailure(403, "{}", "60", 1000).retryable);
    }
    @Test public void malformedResponsesAndRetryHeadersAreSafe() {
        for (String header : new String[]{"garbage", "-1", "9223372036854775807"})
            assertEquals(0, new ApiFailure(429, "{}", header, 1000).retryAt);
        for (String body : new String[]{"secret text", "oauth_problem=%ZZ", "{\"code\":\"token=secret\"}", "oauth_problem=token_rejected_extra"}) {
            ApiFailure failure = new ApiFailure(400, body, null, 1000);
            assertFalse(failure.authorization);
            assertFalse(failure.code.contains("secret"));
        }
        assertTrue(new ApiFailure(400, "{\"code\":\"invalid_token\"}", null, 1000).authorization);
    }
}
