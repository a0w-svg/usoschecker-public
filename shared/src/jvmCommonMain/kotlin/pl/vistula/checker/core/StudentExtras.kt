package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Privacy-minimized read models for student surveys (`surveys/surveys_to_fill2`, `survey2`)
 * and USOSmail messages (`mailclient/message`, `attachment`). Untrusted HTML from the API is
 * reduced to plain text before it can reach the cache or any screen.
 */
object StudentExtras {
    const val MAX_SURVEYS = 50
    const val MAX_QUESTIONS = 100
    const val MAX_ANSWERS = 20
    const val MAX_ATTACHMENTS = 20
    const val MAX_MESSAGE_CHARS = 8000

    @JvmStatic
    fun plainText(html: String?): String = (html ?: "")
        .replace(Regex("(?s)<(script|style)[^>]*>.*?</\\1>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</li>|</div>|</tr>"), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        .replace(Regex("[ \\t]+\n"), "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun putBoolean(target: JSONObject, source: JSONObject, key: String) {
        if (source.opt(key) is Boolean) target.put(key, source.optBoolean(key))
    }

    private fun putCount(target: JSONObject, source: JSONObject, key: String) {
        val value = source.opt(key)
        if (value is Number) target.put(key, value.toInt())
    }

    private fun personName(user: JSONObject?): String =
        listOf(StudentServices.text(user, "first_name"), StudentServices.text(user, "last_name"))
            .filter { it.isNotBlank() }.joinToString(" ")

    @JvmStatic
    @Throws(JSONException::class)
    fun safeSurveys(rows: JSONArray): JSONArray = JSONArray().apply {
        for (i in 0 until minOf(rows.length(), MAX_SURVEYS)) {
            val s = rows.optJSONObject(i) ?: continue
            val id = StudentServices.text(s, "id")
            if (id.isEmpty()) continue
            val group = s.optJSONObject("group")
            val item = JSONObject()
                .put("id", id)
                .put("survey_type", StudentServices.text(s, "survey_type"))
                .put("name", ApiLanguage.safe(s.opt("name")))
                .put("headline", plainText(StudentServices.text(s, "headline_html")).take(500))
                .put("start_date", StudentServices.text(s, "start_date"))
                .put("end_date", StudentServices.text(s, "end_date"))
                .put("course_name", ApiLanguage.safe(group?.opt("course_name")))
                .put("group_number", StudentServices.text(group, "group_number"))
            putBoolean(item, s, "can_i_fill_out")
            putBoolean(item, s, "did_i_fill_out")
            put(item)
        }
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeSurveyDetail(source: JSONObject): JSONObject {
        val questions = JSONArray()
        val rows = source.optJSONArray("questions") ?: JSONArray()
        for (i in 0 until minOf(rows.length(), MAX_QUESTIONS)) {
            val q = rows.optJSONObject(i) ?: continue
            val answers = JSONArray()
            val possible = q.optJSONArray("possible_answers") ?: JSONArray()
            for (j in 0 until minOf(possible.length(), MAX_ANSWERS)) {
                val text = plainText(StudentServices.text(possible.optJSONObject(j), "display_text_html"))
                if (text.isNotEmpty()) answers.put(text.take(300))
            }
            val item = JSONObject()
                .put("number", StudentServices.text(q, "number"))
                .put("level", q.optInt("level", 0).coerceIn(0, 10))
                .put("text", plainText(StudentServices.text(q, "display_text_html")).take(1000))
                .put("answers", answers)
            putBoolean(item, q, "allow_comment")
            questions.put(item)
        }
        val result = JSONObject()
            .put("id", StudentServices.text(source, "id"))
            .put("survey_type", StudentServices.text(source, "survey_type"))
            .put("name", ApiLanguage.safe(source.opt("name")))
            .put("headline", plainText(StudentServices.text(source, "headline_html")).take(1000))
            .put("start_date", StudentServices.text(source, "start_date"))
            .put("end_date", StudentServices.text(source, "end_date"))
            .put("lecturer", personName(source.optJSONObject("lecturer")))
            .put("faculty_name", ApiLanguage.safe(source.optJSONObject("faculty")?.opt("name")))
            .put("programme_name", ApiLanguage.safe(source.optJSONObject("programme")?.opt("name")))
            .put("course_name", ApiLanguage.safe(source.optJSONObject("group")?.opt("course_name")))
            .put("questions", questions)
            .put("questions_truncated", rows.length() > MAX_QUESTIONS)
        for (key in listOf("can_i_fill_out", "did_i_fill_out", "has_final_comment", "can_i_view_results")) putBoolean(result, source, key)
        for (key in listOf("filled_out_count", "entitled_count")) putCount(result, source, key)
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeAttachments(source: JSONArray?): JSONArray = JSONArray().apply {
        val rows = source ?: return@apply
        for (i in 0 until minOf(rows.length(), MAX_ATTACHMENTS)) {
            val a = rows.optJSONObject(i) ?: continue
            val item = JSONObject()
                .put("id", StudentServices.text(a, "id"))
                .put("filename", StudentServices.text(a, "filename").take(200))
                .put("description", StudentServices.text(a, "description").take(300))
                .put("url", StudentServices.text(a, "url").takeIf { WebLinks.isWebUrl(it) }.orEmpty())
            putCount(item, a, "size")
            if (item.getString("filename").isNotEmpty() || item.getString("id").isNotEmpty()) put(item)
        }
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeMailMessage(source: JSONObject): JSONObject {
        val raw = StudentServices.text(source, "content")
        val html = StudentServices.text(source, "content_format").equals("html", ignoreCase = true)
        val body = (if (html) plainText(raw) else raw.trim())
        val result = JSONObject()
            .put("id", StudentServices.text(source, "id"))
            .put("subject", StudentServices.text(source, "subject").take(300))
            .put("status", StudentServices.text(source, "status"))
            .put("date", StudentServices.text(source, "date"))
            .put("content", body.take(MAX_MESSAGE_CHARS))
            .put("content_truncated", body.length > MAX_MESSAGE_CHARS)
            .put("attachments", safeAttachments(source.optJSONArray("attachments")))
        putCount(result, source, "recipients_count")
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeMailMessages(source: JSONObject): JSONArray = JSONArray().apply {
        for (key in source.keys()) {
            val msgObj = source.optJSONObject(key) ?: continue
            put(safeMailMessage(msgObj))
        }
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeMailAttachmentsMap(source: JSONObject): JSONArray = JSONArray().apply {
        for (key in source.keys()) {
            val a = source.optJSONObject(key) ?: continue
            val item = JSONObject()
                .put("id", StudentServices.text(a, "id").ifEmpty { key })
                .put("filename", StudentServices.text(a, "filename").take(200))
                .put("description", StudentServices.text(a, "description").take(300))
                .put("url", StudentServices.text(a, "url").takeIf { WebLinks.isWebUrl(it) }.orEmpty())
            putCount(item, a, "size")
            if (item.getString("filename").isNotEmpty() || item.getString("id").isNotEmpty()) put(item)
        }
    }
}
