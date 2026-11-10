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
    private const val REVOKE = "services/oauth/revoke_consumer_key"
    private const val MAX_BYTES = 64 * 1024

    /** Same transport rules as the API client: no redirects (they could carry the secret elsewhere), no cache. */
    private fun prepare(connection: java.net.HttpURLConnection, method: String) {
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Cache-Control", "no-store")
    }

    /** Status of the answer; a redirect is refused instead of followed. */
    @Throws(IOException::class)
    private fun status(connection: java.net.HttpURLConnection): Int {
        val status = connection.responseCode
        if (status in 300..399) throw IOException("Unexpected redirect from USOSapps")
        return status
    }

    /** The answer body, limited to [MAX_BYTES]; these answers are a few hundred bytes. */
    @Throws(IOException::class)
    private fun body(connection: java.net.HttpURLConnection, ok: Boolean): String {
        val stream = (if (ok) connection.inputStream else connection.errorStream) ?: return ""
        stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > MAX_BYTES) throw IOException("USOSapps answer is too large")
                out.write(buffer, 0, count)
            }
            return String(out.toByteArray(), StandardCharsets.UTF_8)
        }
    }

    private fun form(vararg fields: Pair<String, String>): ByteArray =
        fields.joinToString("&") { (name, value) -> name + "=" + URLEncoder.encode(value, "UTF-8") }
            .toByteArray(StandardCharsets.UTF_8)

    /** The developers page hands out a CSRF cookie that its own form script echoes back as a header. */
    @Throws(IOException::class)
    private fun csrfToken(connections: UsosClient.ConnectionSource): String {
        val connection = connections.open(UsosClient.BASE + PAGE)
        try {
            prepare(connection, "GET")
            // Without the cookie the form is posted as before; a redirect simply yields no token.
            if (connection.responseCode !in 200..299) return ""
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
        val form = form("appname" to appName, "appurl" to appUrl, "email" to email)
        val token = csrfToken(connections)
        val connection = connections.open(UsosClient.BASE + ENDPOINT)
        try {
            if (token.isNotEmpty()) {
                connection.setRequestProperty("Cookie", "csrftoken=$token")
                connection.setRequestProperty("X-CSRFToken", token)
                connection.setRequestProperty("Referer", UsosClient.BASE + PAGE)
            }
            prepare(connection, "POST")
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(form) }
            return parse(body(connection, status(connection) in 200..299))
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Asks USOSapps to revoke the key pair. The server then emails the owner to confirm, so a `true` answer means
     * "check your mailbox", not "already revoked". `false` means the pair is unknown (for example already revoked).
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun revoke(
        key: String,
        secret: String,
        connections: UsosClient.ConnectionSource = UsosClient.ConnectionSource { url ->
            java.net.URI.create(url).toURL().openConnection() as java.net.HttpURLConnection
        }
    ): Boolean {
        // The pair travels in the POST body, never in the address, so the secret stays out of server and proxy logs.
        val connection = connections.open(UsosClient.BASE + REVOKE)
        try {
            prepare(connection, "POST")
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.outputStream.use { it.write(form("consumer_key" to key, "consumer_secret" to secret)) }
            val ok = status(connection) in 200..299
            val body = body(connection, ok).trim()
            if (ok) return body == "true"
            val message = try { JSONObject(body).optString("message") } catch (e: JSONException) { "" }
            // The server answers "not found" with a 400, which is the same outcome as `false`.
            if (message.contains("not found", ignoreCase = true)) return false
            throw Rejected(message.ifBlank { "USOSapps refused the request" })
        } finally {
            connection.disconnect()
        }
    }

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    /** A cheap shape check before asking the server; USOSapps does the real validation. */
    @JvmStatic
    fun plausibleEmail(address: String): Boolean = address.length <= 254 && EMAIL.matches(address)

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
