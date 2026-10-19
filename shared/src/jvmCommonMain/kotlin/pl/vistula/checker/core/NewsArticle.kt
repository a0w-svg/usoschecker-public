package pl.vistula.checker.core

import org.json.JSONException
import org.json.JSONObject

class NewsArticle @JvmOverloads constructor(
    id: String?,
    title: String?,
    publicationDate: String?,
    author: String?,
    headline: String?,
    url: String?,
    content: String? = "",
) {
    @JvmField val id: String = id ?: ""
    @JvmField val title: String = title ?: ""
    @JvmField val publicationDate: String = publicationDate ?: ""
    @JvmField val author: String = author ?: ""
    @JvmField val headline: String = headline ?: ""
    @JvmField val url: String = url ?: ""
    @JvmField val content: String = content ?: ""

    @Throws(JSONException::class)
    fun json(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("publicationDate", publicationDate)
        .put("author", author)
        .put("headline", headline)
        .put("url", url).put("content", content)

    companion object {
        @JvmStatic
        fun from(o: JSONObject?): NewsArticle {
            if (o == null) return NewsArticle("", "", "", "", "", "")
            return NewsArticle(
                o.optString("id", ""),
                o.optString("title", ""),
                o.optString("publicationDate", ""),
                o.optString("author", ""),
                o.optString("headline", ""),
                o.optString("url", ""), o.optString("content", ""),
            )
        }
    }
}

