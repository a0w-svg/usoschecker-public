package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object PaymentServices {
    const val FIELDS = "id|description|type|state|account_number|payment_deadline|total_amount|saldo_amount|currency|interest|bonus_amount|bonus_deadline|has_bonus|default_choice_date|chosen_installment_plan[id|currency|installments[amount|payment_date|description|number]]|available_installment_plans[id|is_default|currency|installments[amount|payment_date|description|number]]"

    fun details(source: JSONArray): JSONArray = JSONArray().apply {
        for (i in 0 until source.length()) {
            val item = source.getJSONObject(i)
            val safe = JSONObject()
            for (key in listOf("id", "type", "state", "total_amount", "saldo_amount", "currency", "interest",
                "bonus_amount", "bonus_deadline", "has_bonus", "default_choice_date", "payment_deadline")) {
                val value = item.opt(key)
                if (value is String || value is Number || value is Boolean) safe.put(key, value)
            }
            safe.put("description", ApiLanguage.safe(item.opt("description")))
            safe.put("plans", plans(item.optJSONArray("available_installment_plans") ?: JSONArray()))
            safe.put("chosenPlan", StudentServices.text(item.optJSONObject("chosen_installment_plan"), "id"))
            put(safe)
        }
    }

    fun plans(source: JSONArray): JSONArray = JSONArray().apply {
        for (i in 0 until source.length()) {
            val item = source.getJSONObject(i)
            val safe = JSONObject().put("id", StudentServices.text(item, "id"))
                .put("currency", StudentServices.text(item, "currency"))
                .put("is_default", item.optBoolean("is_default"))
            val installments = JSONArray()
            val rows = item.optJSONArray("installments") ?: throw JSONException("Missing installments")
            for (j in 0 until rows.length()) {
                val row = rows.getJSONObject(j)
                val amount = row.optDouble("amount", Double.NaN)
                if (!amount.isFinite()) throw JSONException("Missing installment amount")
                installments.put(JSONObject().put("amount", amount).put("payment_date", StudentServices.text(row, "payment_date"))
                    .put("number", row.optInt("number", j + 1)).put("description", ApiLanguage.safe(row.opt("description"))))
            }
            put(safe.put("installments", installments))
        }
    }

    fun remittances(source: JSONArray): JSONArray = JSONArray().apply {
        for (i in 0 until source.length()) {
            val item = source.getJSONObject(i)
            val safe = JSONObject()
            for (key in listOf("id", "amount", "remaining", "date", "currency", "status", "is_settled")) {
                val value = item.opt(key)
                if (value is String || value is Number || value is Boolean) safe.put(key, value)
            }
            put(safe.put("description", ApiLanguage.safe(item.opt("description"))))
        }
    }
}
