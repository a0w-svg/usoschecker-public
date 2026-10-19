package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StudentServicesTest {
    @Test
    fun scopeNormalizationPreservesOnlyDistinctNonEmptyScopes() {
        assertEquals("grades|studies|offline_access", StudentServices.normalizeScopes(" grades | | studies||grades|offline_access| "))
        assertEquals("", StudentServices.normalizeScopes(null))
        assertFalse(StudentServices.hasScope(null, "payments"))
        assertFalse(StudentServices.hasScope("other_payments", "payments"))
    }

    @Test
    fun nullableFieldsAndLanguageDictionariesRemainReadable() {
        assertEquals("", StudentServices.text(JSONObject().put("id", JSONObject.NULL), "id"))
        assertEquals("Decyzja", StudentServices.localized(JSONObject().put("pl", "Decyzja").put("en", "Decision")))
        assertEquals("Decision", StudentServices.localized(JSONObject().put("pl", JSONObject.NULL).put("en", "Decision")))
        assertEquals("", StudentServices.localized(JSONObject.NULL))
        assertTrue(StudentServices.hasScope("grades|cards|offline_access", "cards"))
        assertFalse(StudentServices.hasScope("grades|student_cards", "cards"))
    }

    @Test
    fun expirationIncludesLastDayAndWarnsThirtyDaysAhead() {
        val today = LocalDate.of(2026, 10, 14)
        val todayCard = StudentServices.Card(JSONObject().put("expiration_date", today.toString()))
        assertFalse(todayCard.expired(today))
        assertTrue(todayCard.expiresSoon(today))
        assertTrue(StudentServices.Card(JSONObject().put("expiration_date", "2026-10-13")).expired(today))
        assertFalse(StudentServices.Card(JSONObject().put("expiration_date", "bad")).expired(today))
        assertFalse(StudentServices.Card(JSONObject().put("expiration_date", "2026-11-14")).expiresSoon(today))
    }

    @Test
    fun cacheDoesNotKeepActivationOrCardWriteSecrets() {
        val order = JSONObject().put("id", "42").put("status", "ready_to_receive").put("token", "SECRET")
            .put("distribution_pass", "PASSWORD").put("expiration_date", "2027-03-31")
        val result = StudentServices.safeMobileCard(
            JSONObject().put("latest_order", order)
                .put("orders", JSONArray().put(order)).put("token", "SECRET")
        )
        assertEquals("42", result.getJSONObject("latest_order").getString("id"))
        assertFalse(result.toString().contains("SECRET"))
        assertFalse(result.toString().contains("PASSWORD"))
        assertFalse(result.optBoolean("orderEligibilityKnown"))
        assertTrue(StudentServices.safeMobileCard(JSONObject().put("why_cannot_order", JSONObject.NULL)).optBoolean("orderEligibilityKnown"))
        val cards = StudentServices.safeCards(JSONArray().put(JSONObject().put("id", "1").put("card_key", "SECRET")))
        assertFalse(cards.toString().contains("SECRET"))
    }

    @Test
    fun documentActionsAreAuthoritativeAndSearchAcceptsPolishWithoutAccents() {
        val doc = StudentServices.Document(
            JSONObject().put("name", JSONObject().put("pl", "Zaświadczenie o studiowaniu"))
                .put("id_blobbox", "blob").put("status", "Można pobrać").put("actions", JSONArray())
        )
        assertFalse(doc.downloadable())
        assertTrue(doc.matches("zaswiadczenie"))
        val allowed = StudentServices.Document(
            JSONObject().put("id_blobbox", "blob")
                .put("actions", JSONArray().put("GET_DOCUMENT"))
        )
        assertTrue(allowed.downloadable())
    }
}
