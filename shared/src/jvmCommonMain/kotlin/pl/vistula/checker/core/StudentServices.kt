package pl.vistula.checker.core

import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Read models for the documented cards and administration-document endpoints. */
object StudentServices {
    @JvmStatic
    fun text(obj: JSONObject?, key: String): String {
        if (obj == null || obj.isNull(key)) return ""
        val value = obj.opt(key)
        return if (value is String || value is Number) value.toString() else ""
    }

    @JvmStatic
    fun localized(value: Any?): String = ApiLanguage.text(value)

    @JvmStatic
    fun localized(value: Any?, language: String?): String = ApiLanguage.text(value, language)

    @JvmStatic
    fun date(value: String): LocalDate? = try {
        LocalDate.parse(if (value.length >= 10) value.substring(0, 10) else value)
    } catch (e: DateTimeParseException) {
        null
    }

    @JvmStatic
    fun hasScope(scopes: String?, scope: String): Boolean {
        if (scopes == null) return false
        for (value in scopes.split("|")) {
            if (scope == value.trim()) return true
        }
        return false
    }

    @JvmStatic
    fun normalizeScopes(scopes: String?): String {
        val values = LinkedHashSet<String>()
        if (scopes != null) {
            for (value in scopes.split("|")) {
                if (value.trim().isNotEmpty()) values.add(value.trim())
            }
        }
        return values.joinToString("|")
    }

    class Card(value: JSONObject?) {
        @JvmField val id: String = text(value, "id")
        @JvmField val type: String = text(value, "type")
        @JvmField val studentNumber: String = text(value, "student_number")
        @JvmField val barcode: String = text(value, "barcode_number")
        @JvmField val issued: String = text(value, "date_of_issue")
        @JvmField val expires: String = text(value, "expiration_date")

        fun expired(today: LocalDate): Boolean {
            val until = date(expires)
            return until != null && until.isBefore(today)
        }

        fun expiresSoon(today: LocalDate): Boolean {
            val until = date(expires)
            return until != null && !until.isBefore(today) && !until.isAfter(today.plusDays(30))
        }

        @Throws(JSONException::class)
        fun json(): JSONObject = JSONObject()
            .put("id", id)
            .put("type", type)
            .put("student_number", studentNumber)
            .put("barcode_number", barcode)
            .put("date_of_issue", issued)
            .put("expiration_date", expires)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun cards(rows: JSONArray): List<Card> {
        val cards = ArrayList<Card>()
        for (i in 0 until rows.length()) cards.add(Card(rows.getJSONObject(i)))
        return cards
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeCards(rows: JSONArray): JSONArray {
        val result = JSONArray()
        for (card in cards(rows)) result.put(card.json())
        return result
    }

    /** Cache only status information, never mobile activation tokens or passwords. */
    @JvmStatic
    @Throws(JSONException::class)
    fun safeMobileCard(source: JSONObject): JSONObject {
        val result = JSONObject()
            .put("why_cannot_order", ApiLanguage.safe(source.opt("why_cannot_order")))
            .put("why_cannot_revoke", ApiLanguage.safe(source.opt("why_cannot_revoke")))
            .put("orderEligibilityKnown", source.has("why_cannot_order"))
            .put("revokeEligibilityKnown", source.has("why_cannot_revoke"))
            .put("expected_expiration_date", text(source, "expected_expiration_date"))
        val latest = source.optJSONObject("latest_order")
        if (latest != null) result.put("latest_order", safeOrder(latest))
        val orders = source.optJSONArray("orders")
        val safe = JSONArray()
        if (orders != null) {
            for (i in 0 until orders.length()) safe.put(safeOrder(orders.getJSONObject(i)))
        }
        result.put("orders", safe)
        return result
    }

    @Throws(JSONException::class)
    private fun safeOrder(source: JSONObject): JSONObject {
        val result = JSONObject()
        for (key in arrayOf("id", "order_date", "status", "student_number", "expiration_date")) {
            result.put(key, text(source, key))
        }
        return result
    }

    class Document @JvmOverloads constructor(
        source: JSONObject,
        language: String? = ApiLanguage.current()
    ) {
        @JvmField val id: String = text(source, "id")
        @JvmField val name: String = localized(source.opt("name"), language)
        @JvmField val status: String = localized(source.opt("status"), language)
        @JvmField val typeName: String = localized(source.opt("type_name"), language)
        @JvmField val published: String = text(source, "published_date")
        @JvmField val delivered: String = text(source, "delivered_date")
        @JvmField val blobId: String = text(source, "id_blobbox")
        @JvmField val actions: MutableList<String> = ArrayList()

        init {
            val values = source.optJSONArray("actions")
            if (values != null) {
                for (i in 0 until values.length()) {
                    val value = values.opt(i)
                    if (value is String) actions.add(value)
                }
            }
        }

        fun downloadable(): Boolean = actions.contains("GET_DOCUMENT") && blobId.isNotEmpty()
        fun matches(query: String): Boolean = StudentSearch.matches(query, name, status, typeName, published, id)

        @Throws(JSONException::class)
        fun json(): JSONObject = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("status", status)
            .put("type_name", typeName)
            .put("published_date", published)
            .put("delivered_date", delivered)
            .put("id_blobbox", blobId)
            .put("actions", JSONArray(actions))
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeDocuments(source: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until source.length()) {
            val raw = source.getJSONObject(i)
            val safe = Document(raw).json()
            for (key in arrayOf("name", "status", "type_name")) {
                safe.put(key, ApiLanguage.safe(raw.opt(key)))
            }
            result.put(safe)
        }
        return result
    }
}

