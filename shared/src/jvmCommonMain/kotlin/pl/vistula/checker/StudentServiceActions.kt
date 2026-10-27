package pl.vistula.checker

import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.PaymentAmounts

/** Foreground actions: recheck eligibility, send once, never retry an uncertain write. */
object StudentServiceActions {
    fun document(client: UsosClient, id: String): JSONObject {
        require(id.isNotBlank())
        val result = JSONObject(client.get("services/adm_documents/document", mapOf("id" to id,
            "fields" to "id|published_date|delivered_date|id_blobbox|actions|name|status|type_name")))
        require(StudentServices.text(result, "id") == id)
        return result
    }

    private fun receivable(client: UsosClient, id: String) {
        check(StudentServices.Document(document(client, id)).actions.contains("RECEIVE_DOCUMENT")) {
            "Document action unavailable"
        }
    }

    fun generateCode(client: UsosClient, state: JSONObject, id: String, language: String): JSONObject {
        receivable(client, id)
        val response = post(client, state, "services/adm_documents/generate_code", mapOf("id" to id,
            "lang" to if (language == "pl") "pl" else "en"))
        check(StudentServices.text(response, "email").isNotEmpty() && StudentServices.text(response, "date").isNotEmpty()) {
            "Unexpected document response"
        }
        return response
    }

    fun receive(client: UsosClient, state: JSONObject, id: String, code: String): JSONObject {
        require(code.trim().isNotEmpty())
        receivable(client, id)
        return post(client, state, "services/adm_documents/receive", mapOf("id" to id, "code" to code.trim())).also {
            check(it.length() == 0 || it.optString("status") == "Err") { "Unexpected document response" }
        }
    }

    fun choosePlan(client: UsosClient, state: JSONObject, paymentId: String, type: String, planId: String, expected: JSONObject, expectedCurrency: String = StudentServices.text(expected, "currency")) {
        require(paymentId.isNotBlank() && planId.isNotBlank() && type.isNotBlank())
        val payment = JSONObject(client.get("services/payments/payment", mapOf("id" to paymentId, "type" to type,
            "fields" to "id|currency|available_installment_plans[id|currency|installments[amount|payment_date|description|number]]")))
        check(StudentServices.text(payment, "id") == paymentId)
        val plans = payment.optJSONArray("available_installment_plans") ?: JSONArray()
        check((0 until plans.length()).any { StudentServices.text(plans.optJSONObject(it), "id") == planId }) {
            "Payment plan unavailable"
        }
        val current = (0 until plans.length()).map { plans.getJSONObject(it) }.first { StudentServices.text(it, "id") == planId }
        val rows = current.getJSONArray("installments")
        val shown = expected.getJSONArray("installments")
        check(PaymentAmounts.currency(current, payment) == expectedCurrency && rows.length() == shown.length())
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val previous = shown.getJSONObject(i)
            check(row.getDouble("amount") == previous.getDouble("amount") &&
                StudentServices.text(row, "payment_date") == StudentServices.text(previous, "payment_date") &&
                row.optInt("number", i + 1) == previous.optInt("number", i + 1)) { "Payment plan unavailable" }
        }
        check(post(client, state, "services/payments/choose_installment_plan", mapOf("payment_id" to paymentId,
            "plan_id" to planId)).length() == 0) { "Unexpected payment response" }
    }

    private fun post(client: UsosClient, state: JSONObject, endpoint: String, values: Map<String, String>): JSONObject =
        JSONObject(client.request("POST", endpoint, values, true, state.optString("token"), state.optString("tokenSecret")))
}
