package pl.vistula.checker

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import pl.vistula.checker.core.PaymentServices
import pl.vistula.checker.core.CampusAffiliation
import pl.vistula.checker.core.ApiLanguage
import pl.vistula.checker.core.UsosParser

/** Independent, localized read models; only parsed fields enter the encrypted service cache. */
object ToolServiceData {
    fun fetch(client: UsosClient, section: String, campus: String = ""): JSONObject {
        val result = JSONObject()
        when (section) {
            "affiliation" -> return CampusAffiliation.detect(JSONArray(client.get("services/progs/student", mapOf(
                "active_only" to "true", "old_programs" to "false",
                "fields" to "id|status|programme[id|faculty[id|name|postal_address|homepage_url|email|path[id|name]]]"
            ))))
            "payments" -> {
                val accounts = collection(client.get("services/payments/user_accounts", emptyMap()))
                val payments = JSONArray(client.get("services/payments/user_payments", mapOf(
                    "fields" to PaymentServices.FIELDS
                )))
                for (language in ApiLanguage.SUPPORTED) {
                    result.put(language, ApiLanguage.`in`(language) { UsosParser.payments(accounts, payments).json() })
                }
                result.put("details", PaymentServices.details(payments))
            }
            "remittances" -> return JSONObject().put("items", PaymentServices.remittances(JSONArray(
                client.get("services/payments/user_remittances", mapOf("fields" to "id|amount|remaining|description|status|is_settled|date|currency"))
            )))
            "news" -> {
                val parameters = linkedMapOf("num" to "50", "fields" to "items[article[id|title|publication_date|author|headline_html|content_html]]|next_page|total")
                if (campus.isNotEmpty()) parameters["facpattern"] = if (campus == "0000") "=0000" else "+$campus"
                val raw = JSONObject(client.get("services/news/search", parameters))
                if (raw.optJSONArray("items") == null) throw JSONException("Missing news items")
                for (language in ApiLanguage.SUPPORTED) {
                    result.put(language, ApiLanguage.`in`(language) {
                        JSONArray().apply { UsosParser.newsArticles(raw).forEach { put(it.json()) } }
                    })
                }
                result.put("source", "usos").put("hasMore", raw.optBoolean("next_page")).put("campus", campus)
            }
            else -> throw IllegalArgumentException("Nieznana usługa.")
        }
        return result
    }

    private fun collection(text: String): Any {
        val value = JSONTokener(text).nextValue()
        if (value !is JSONObject && value !is JSONArray) throw JSONException("Expected collection")
        return value
    }
}
