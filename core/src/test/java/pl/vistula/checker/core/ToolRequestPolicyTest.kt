package pl.vistula.checker.core

import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class ToolRequestPolicyTest {
    @Test fun featureChoicesPreserveCustomAccessAndRequireFreshConsent() {
        val changed = FeatureAccess.change("grades|studies|custom|email", "documents", true)
        assertTrue(StudentServices.hasScope(changed, "adm_documents"))
        assertTrue(StudentServices.hasScope(changed, "custom"))
        assertTrue(StudentServices.hasScope(changed, "email"))
        assertFalse(FeatureAccess.selected(changed, "profile"))
        assertFalse(FeatureAccess.selected(FeatureAccess.change(changed, "documents", false), "documents"))
        assertTrue(StudentServices.hasScope(FeatureAccess.change(changed, "profile", false), "offline_access"))
    }

    @Test fun stalledAndSlowContinuousResponsesShareABoundedBudget() {
        var time = 0L
        val budget = RequestBudget { time }
        assertEquals(20_000, budget.readTimeout())
        time = 44_000_000_000L
        assertEquals(1000, budget.readTimeout())
        time = 45_000_000_000L
        assertThrows(SocketTimeoutException::class.java) { budget.readTimeout() }
    }

    @Test fun interruptedRequestsDoNotContinueReading() {
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedIOException::class.java) { RequestBudget().readTimeout() }
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun absentServerCooldownUsesASafeFallbackWithoutAutomaticReplay() {
        assertEquals(31_000L, ApiFailure(429, "{}", null, 1000).retryAt)
        assertEquals(6_000L, ApiFailure(503, "{}", null, 1000).retryAt)
        assertEquals(0L, ApiFailure(403, "{}", null, 1000).retryAt)
    }
}
