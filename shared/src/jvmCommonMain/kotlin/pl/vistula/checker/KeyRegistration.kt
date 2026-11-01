package pl.vistula.checker

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

/**
 * Registers the user's own USOSapps consumer key through the same public endpoint the developers form posts to,
 * so the key and secret no longer have to be copied between browser and app. Nothing is stored here.
 */
object KeyRegistration {
    class Keys(@JvmField val key: String, @JvmField val secret: String)

    /** The server refused the registration; [message] is its own explanation, meant for the user. */
    class Rejected(message: String) : IOException(message)

    private const val ENDPOINT = "developers/submit"
    private const val PAGE = "developers/"

    /** The developers page hands out a CSRF cookie that its own form script echoes back as a header. */
    @Throws(IOException::class)
    private fun csrfToken(connections: UsosClient.ConnectionSource): String {
        val connection = connections.open(UsosClient.BASE + PAGE)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.responseCode
            val cookies = connection.headerFields?.entries
                ?.filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                ?.flatMap { it.value.orEmpty() }
                .orEmpty()
            return cookies.firstNotNullOfOrNull { Regex("""csrftoken=([^;\s]+)""").find(it)?.groupValues?.get(1) }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun register(
        appName: String,
        appUrl: String,
        email: String,
        connections: UsosClient.ConnectionSource = UsosClient.ConnectionSource { url ->
            java.net.URI.create(url).toURL().openConnection() as java.net.HttpURLConnection
        }
    ): Keys {
        val form = listOf("appname" to appName, "appurl" to appUrl, "email" to email)
            .joinToString("&") { (name, value) -> name + "=" + URLEncoder.encode(value, "UTF-8") }
        val token = csrfToken(connections)
        val connection = connections.open(UsosClient.BASE + ENDPOINT)
        try {
            if (token.isNotEmpty()) {
                connection.setRequestProperty("Cookie", "csrftoken=$token")
                connection.setRequestProperty("X-CSRFToken", token)
                connection.setRequestProperty("Referer", UsosClient.BASE + PAGE)
            }
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(form.toByteArray(StandardCharsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { String(it.readBytes(), StandardCharsets.UTF_8) }.orEmpty()
            return parse(body)
        } finally {
            connection.disconnect()
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun parse(body: String): Keys {
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw IOException("Unexpected response from USOSapps", e)
        }
        if (json.optString("status") != "success") {
            throw Rejected(json.optString("message").ifBlank { "USOSapps refused the registration" })
        }
        val key = json.optString("consumer_key").trim()
        val secret = json.optString("consumer_secret").trim()
        if (key.isEmpty() || secret.isEmpty()) throw IOException("USOSapps returned no key")
        return Keys(key, secret)
    }
}
