package pl.vistula.checker;

import java.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;

final class DemoStudentServices {
    private DemoStudentServices() {}
    static JSONObject data() throws Exception {
        LocalDate today = LocalDate.now(UsosClient.ZONE);
        JSONObject card = new JSONObject().put("id", "demo-card").put("type", "student").put("student_number", "123456")
            .put("barcode_number", "DEMO-123456").put("date_of_issue", today.minusMonths(6).toString()).put("expiration_date", today.plusDays(24).toString());
        JSONObject order = new JSONObject().put("id", "demo-order").put("status", "ready_to_receive")
            .put("student_number", "123456").put("order_date", today.minusDays(2) + " 10:00:00").put("expiration_date", today.plusMonths(6).toString());
        JSONObject mobile = new JSONObject().put("latest_order", order).put("orders", new JSONArray().put(order))
            .put("why_cannot_order", "Masz już zamówienie mLegitymacji.").put("why_cannot_revoke", "")
            .put("expected_expiration_date", today.plusMonths(6).toString()).put("orderEligibilityKnown", true).put("revokeEligibilityKnown", true);
        JSONArray documents = new JSONArray().put(new JSONObject().put("id", "demo-doc-1").put("name", "Zaświadczenie o studiowaniu")
            .put("status", "Udostępniony").put("type_name", "Zaświadczenie").put("published_date", today.minusDays(3).toString())
            .put("id_blobbox", "demo-blob").put("actions", new JSONArray().put("GET_DOCUMENT").put("GET_UPO")))
            .put(new JSONObject().put("id", "demo-doc-2").put("name", "Decyzja stypendialna").put("status", "Do odbioru")
                .put("type_name", "Decyzja").put("published_date", today.minusDays(1).toString())
                .put("actions", new JSONArray().put("RECEIVE_DOCUMENT")));
        long now = System.currentTimeMillis();
        return new JSONObject().put("cards", new JSONObject().put("data", new JSONArray().put(card)).put("fetchedAt", now))
            .put("mcard", new JSONObject().put("data", mobile).put("fetchedAt", now))
            .put("documents", new JSONObject().put("data", documents).put("fetchedAt", now));
    }
}
