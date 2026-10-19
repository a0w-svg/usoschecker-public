package pl.vistula.checker

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

object DemoStudentServices {
    @JvmStatic
    @Throws(Exception::class)
    fun data(): JSONObject {
        val today = LocalDate.now(UsosClient.ZONE)
        val card = JSONObject()
            .put("id", "demo-card").put("type", "student").put("student_number", "123456")
            .put("barcode_number", "DEMO-123456").put("date_of_issue", today.minusMonths(6).toString())
            .put("expiration_date", today.plusDays(24).toString())
        val order = JSONObject()
            .put("id", "demo-order").put("status", "ready_to_receive")
            .put("student_number", "123456").put("order_date", "${today.minusDays(2)} 10:00:00")
            .put("expiration_date", today.plusMonths(6).toString())
        val mobile = JSONObject()
            .put("latest_order", order).put("orders", JSONArray().put(order))
            .put("why_cannot_order", "Masz już zamówienie mLegitymacji.").put("why_cannot_revoke", "")
            .put("expected_expiration_date", today.plusMonths(6).toString())
            .put("orderEligibilityKnown", true).put("revokeEligibilityKnown", true)
        val documents = JSONArray()
            .put(
                JSONObject()
                    .put("id", "demo-doc-1").put("name", "Zaświadczenie o studiowaniu")
                    .put("status", "Udostępniony").put("type_name", "Zaświadczenie")
                    .put("published_date", today.minusDays(3).toString())
                    .put("id_blobbox", "demo-blob")
                    .put("actions", JSONArray().put("GET_DOCUMENT").put("GET_UPO"))
            )
            .put(
                JSONObject()
                    .put("id", "demo-doc-2").put("name", "Decyzja stypendialna")
                    .put("status", "Do odbioru").put("type_name", "Decyzja")
                    .put("published_date", today.minusDays(1).toString())
                    .put("actions", JSONArray().put("RECEIVE_DOCUMENT"))
            )
        val now = System.currentTimeMillis()
        return JSONObject()
            .put("cards", JSONObject().put("data", JSONArray().put(card)).put("fetchedAt", now))
            .put("mcard", JSONObject().put("data", mobile).put("fetchedAt", now))
            .put("documents", JSONObject().put("data", documents).put("fetchedAt", now))
    }
}

