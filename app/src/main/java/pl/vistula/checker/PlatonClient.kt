package pl.vistula.checker

import android.webkit.CookieManager
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import pl.vistula.checker.core.OAuth
import pl.vistula.checker.core.PlatonLinks
import pl.vistula.checker.core.PlatonSync

/**
 * Reads one Platon page with the session the student created in [PlatonLoginActivity].
 * Platon has no public API and does not accept USOS OAuth, so the only credential is the
 * WebView session cookie, which stays inside the WebView store and is never logged or saved by the app.
 */
object PlatonClient {
    class SessionExpiredException : Exception("Sesja Platona wygasła.")

    class Page(val url: String, val html: String)

    private const val MAX_BYTES = 2_000_000
    private const val MAX_REDIRECTS = 4

    /**
     * Next address to follow after a redirect. Anything that leaves HTTPS `platon.vistula.edu.pl`
     * (Microsoft, CAS, another host, user-info tricks) or lands on a sign-in page means the session is gone.
     */
    @Throws(SessionExpiredException::class)
    internal fun nextHop(current: String, location: String?): String {
        if (location.isNullOrEmpty()) throw SessionExpiredException()
        val next = try {
            PlatonLinks.normalize(URI(current).resolve(location).toString())
        } catch (e: Exception) {
            throw SessionExpiredException()
        }
        if (PlatonSync.isLoginPage(next, null)) throw SessionExpiredException()
        return next
    }

    @Throws(Exception::class)
    fun fetch(address: String, form: Map<String, String>? = null): Page {
        var current = PlatonLinks.normalize(address)
        repeat(MAX_REDIRECTS + 1) {
            val cookie = CookieManager.getInstance().getCookie(current)
            if (cookie.isNullOrEmpty()) throw SessionExpiredException()
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Cookie", cookie)
                connection.setRequestProperty("Accept", "text/html")
                connection.setRequestProperty("User-Agent", "VistulaChecker/" + BuildConfig.VERSION_NAME)
                if (form != null && current == PlatonLinks.normalize(address)) {
                    // Platon list pages are loaded with a form POST; the body is never logged.
                    val body = OAuth.query(form).toByteArray(Charsets.UTF_8)
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                }
                val code = connection.responseCode
                if (code in 300..399) {
                    current = nextHop(current, connection.getHeaderField("Location"))
                    return@repeat
                }
                if (code == 401 || code == 403) throw SessionExpiredException()
                if (code != 200) throw java.io.IOException("Platon HTTP $code")
                val html = read(connection)
                if (PlatonSync.isLoginPage(current, html)) throw SessionExpiredException()
                return Page(current, html)
            } finally {
                connection.disconnect()
            }
        }
        throw java.io.IOException("Zbyt wiele przekierowań Platona.")
    }

    private fun read(connection: HttpURLConnection): String {
        val out = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size() + n > MAX_BYTES) throw java.io.IOException("Odpowiedź Platona jest zbyt duża.")
                out.write(buffer, 0, n)
            }
        }
        val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(connection.contentType ?: "")?.groupValues?.get(1)
        return String(out.toByteArray(), try { charset(charset ?: "UTF-8") } catch (e: Exception) { Charsets.UTF_8 })
    }
}
