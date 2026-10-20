package pl.vistula.checker

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.PaymentServices
import pl.vistula.checker.core.CampusAffiliation
import pl.vistula.checker.core.ApiLanguage
import pl.vistula.checker.core.UsosParser

/** Independent, localized read models; only parsed fields enter the encrypted service cache. */
object ToolServiceData {
    fun fetch(client: UsosClient, section: String, campus: String = "", state: JSONObject = JSONObject()): JSONObject {
        val result = JSONObject()
        when (section) {
            "affiliation" -> {
                val facultyFields = "id|name|postal_address|homepage_url|email|phone_numbers|path[id|name]"
                val programmes = if (pl.vistula.checker.core.AccountRole.lecturerOnly(state)) JSONArray()
                    else JSONArray(client.get("services/progs/student", mapOf(
                        "active_only" to "true", "old_programs" to "false",
                        "fields" to "id|status|programme[id|faculty[$facultyFields]]"
                    )))
                if (pl.vistula.checker.core.AccountRole.lecturer(state)) {
                    val functions = JSONArray(client.get("services/users/employment_positions", mapOf(
                        "fields" to "faculty"
                    )))
                    val ids = (0 until functions.length()).mapNotNull {
                        functions.optJSONObject(it)?.optJSONObject("faculty")?.optString("id")?.takeIf { id -> id.isNotBlank() }
                    }.distinct()
                    for (id in ids) programmes.put(JSONObject().put("programme", JSONObject().put("faculty",
                        JSONObject(client.get("services/fac/faculty", mapOf("fac_id" to id, "fields" to facultyFields))))))
                }
                val detected = CampusAffiliation.detect(programmes)
                val selected = campus.ifEmpty { detected.optString("detected") }
                if (selected in CampusAffiliation.campuses) {
                    val faculty = JSONObject(client.get("services/fac/faculty", mapOf("fac_id" to selected, "fields" to facultyFields)))
                    val contacts = CampusAffiliation.detect(JSONArray().put(JSONObject().put("programme", JSONObject().put("faculty", faculty))))
                    val units = detected.getJSONArray("units")
                    val extra = contacts.getJSONArray("units")
                    for (i in 0 until extra.length()) {
                        val unit = extra.getJSONObject(i)
                        if ((0 until units.length()).none { units.getJSONObject(it).optString("id") == unit.optString("id") }) units.put(unit)
                    }
                }
                return detected
            }
            "teaching" -> {
                check(pl.vistula.checker.core.AccountRole.lecturer(state)) { "Lecturer account required" }
                val user = JSONObject(client.get("services/users/user", mapOf("fields" to "course_editions_conducted")))
                val courses = user.getJSONArray("course_editions_conducted")
                val safe = JSONArray()
                for (i in 0 until courses.length()) {
                    val course = courses.getJSONObject(i)
                    safe.put(JSONObject().put("id", StudentServices.text(course, "id"))
                        .put("name", ApiLanguage.safe(course.optJSONObject("course")?.opt("name")))
                        .put("term", ApiLanguage.safe(course.optJSONObject("term")?.opt("name"))))
                }
                return JSONObject().put("courses", safe)
            }
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
