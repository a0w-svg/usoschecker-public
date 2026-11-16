package pl.vistula.checker.ui.compose

import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.core.ApiLanguage
import pl.vistula.checker.core.CampusAffiliation
import pl.vistula.checker.core.NewsArticle
import pl.vistula.checker.core.PaymentInfo
import pl.vistula.checker.core.Snapshot

internal object ToolReadModels {
    fun payments(state: JSONObject, snapshot: Snapshot?, language: String): PaymentInfo? {
        val entry = state.optJSONObject("studentServices")?.optJSONObject("payments")
        val data = entry?.optJSONObject("data")?.optJSONObject(ApiLanguage.normalize(language))
        return if (data != null && entry.optLong("fetchedAt") >= state.optLong("successAt")) PaymentInfo.from(data)
            else snapshot?.payments
    }

    fun news(state: JSONObject, snapshot: Snapshot?, language: String): List<NewsArticle> {
        val service = state.optJSONObject("studentServices")?.optJSONObject("news")
        if (service != null && (service.has("data") || service.has("error"))) {
            val data = service.optJSONObject("data")?.takeIf { CampusAffiliation.newsMatches(it, state) }
            val list = if (data?.optString("source") == "university") {
                data.optJSONArray(ApiLanguage.normalize(language)) ?: data.optJSONArray("pl") ?: JSONArray()
            } else {
                data?.optJSONArray("articles") ?: JSONArray()
            }
            val result = ArrayList<NewsArticle>(list.length())
            for (i in 0 until list.length()) {
                val obj = list.optJSONObject(i) ?: continue
                result.add(NewsArticle.from(obj))
            }
            return result
        }
        return snapshot?.news ?: emptyList()
    }
}
