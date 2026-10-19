package pl.vistula.checker

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.core.NewsArticle
import pl.vistula.checker.core.RequestBudget

/** Public university news. This transport never receives USOS credentials or account state. */
object UniversityNews {
    const val HOST = "vistula.edu.pl"
    fun load(connections: UsosClient.ConnectionSource = UsosClient.ConnectionSource {
        URI(it).toURL().openConnection() as HttpURLConnection
    }): JSONObject {
        val result = JSONObject().put("source", "university")
        for (language in listOf("pl", "en")) {
            val connection = connections.open("https://$HOST/wp-json/wp/v2/posts?per_page=20&lang=$language&_fields=id,date,link,title,excerpt,content")
            val budget = RequestBudget()
            try {
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = 15_000
                connection.readTimeout = budget.readTimeout()
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "VistulaChecker (public university news)")
                if (connection.responseCode != 200) throw IOException("University news unavailable")
                if (!connection.contentType.orEmpty().contains("json", ignoreCase = true)) throw IOException("Unexpected news format")
                val bytes = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val chunk = ByteArray(8192)
                    while (true) {
                        connection.readTimeout = budget.readTimeout()
                        val count = input.read(chunk)
                        if (count < 0) break
                        if (bytes.size() + count > 4 * 1024 * 1024) throw IOException("University news too large")
                        bytes.write(chunk, 0, count)
                    }
                }
                result.put(language, parse(JSONArray(bytes.toString("UTF-8"))))
            } finally {
                connection.disconnect()
            }
        }
        // The official API supplies Polish/English posts; never claim machine translation.
        return result.put("uk", JSONArray(result.getJSONArray("en").toString()))
    }

    fun parse(posts: JSONArray): JSONArray = JSONArray().apply {
        for (i in 0 until posts.length()) {
            val post = posts.getJSONObject(i)
            val url = post.optString("link")
            val uri = try { URI(url) } catch (_: Exception) { continue }
            if (uri.scheme != "https" || uri.host != HOST || uri.userInfo != null || uri.port !in listOf(-1, 443)) continue
            val title = post.optJSONObject("title")?.optString("rendered").orEmpty()
            if (title.isBlank()) continue
            put(NewsArticle("university:" + post.get("id"), title, post.optString("date").take(10),
                "Vistula", post.optJSONObject("excerpt")?.optString("rendered").orEmpty(), url,
                post.optJSONObject("content")?.optString("rendered").orEmpty()).json())
        }
    }
}
