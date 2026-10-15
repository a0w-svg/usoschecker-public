package pl.vistula.checker.core;

import java.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudentServicesTest {
    @Test public void nullableFieldsAndLanguageDictionariesRemainReadable() {
        assertEquals("", StudentServices.text(new JSONObject().put("id", JSONObject.NULL), "id"));
        assertEquals("Decyzja", StudentServices.localized(new JSONObject().put("pl", "Decyzja").put("en", "Decision")));
        assertEquals("Decision", StudentServices.localized(new JSONObject().put("pl", JSONObject.NULL).put("en", "Decision")));
        assertEquals("", StudentServices.localized(JSONObject.NULL));
        assertTrue(StudentServices.hasScope("grades|cards|offline_access", "cards"));
        assertFalse(StudentServices.hasScope("grades|student_cards", "cards"));
    }
    @Test public void expirationIncludesLastDayAndWarnsThirtyDaysAhead() {
        LocalDate today = LocalDate.of(2026, 10, 14);
        StudentServices.Card todayCard = new StudentServices.Card(new JSONObject().put("expiration_date", today.toString()));
        assertFalse(todayCard.expired(today)); assertTrue(todayCard.expiresSoon(today));
        assertTrue(new StudentServices.Card(new JSONObject().put("expiration_date", "2026-10-13")).expired(today));
        assertFalse(new StudentServices.Card(new JSONObject().put("expiration_date", "bad")).expired(today));
        assertFalse(new StudentServices.Card(new JSONObject().put("expiration_date", "2026-11-14")).expiresSoon(today));
    }
    @Test public void cacheDoesNotKeepActivationOrCardWriteSecrets() throws Exception {
        JSONObject order = new JSONObject().put("id", "42").put("status", "ready_to_receive").put("token", "SECRET")
            .put("distribution_pass", "PASSWORD").put("expiration_date", "2027-03-31");
        JSONObject result = StudentServices.safeMobileCard(new JSONObject().put("latest_order", order)
            .put("orders", new JSONArray().put(order)).put("token", "SECRET"));
        assertEquals("42", result.getJSONObject("latest_order").getString("id"));
        assertFalse(result.toString().contains("SECRET")); assertFalse(result.toString().contains("PASSWORD"));
        assertFalse(result.optBoolean("orderEligibilityKnown"));
        assertTrue(StudentServices.safeMobileCard(new JSONObject().put("why_cannot_order", JSONObject.NULL)).optBoolean("orderEligibilityKnown"));
        JSONArray cards = StudentServices.safeCards(new JSONArray().put(new JSONObject().put("id", "1").put("card_key", "SECRET")));
        assertFalse(cards.toString().contains("SECRET"));
    }
    @Test public void documentActionsAreAuthoritativeAndSearchAcceptsPolishWithoutAccents() {
        StudentServices.Document doc = new StudentServices.Document(new JSONObject().put("name", new JSONObject().put("pl", "Zaświadczenie o studiowaniu"))
            .put("id_blobbox", "blob").put("status", "Można pobrać").put("actions", new JSONArray()));
        assertFalse(doc.downloadable()); assertTrue(doc.matches("zaswiadczenie"));
        StudentServices.Document allowed = new StudentServices.Document(new JSONObject().put("id_blobbox", "blob")
            .put("actions", new JSONArray().put("GET_DOCUMENT")));
        assertTrue(allowed.downloadable());
    }
}
