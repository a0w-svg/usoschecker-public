package pl.vistula.checker.core

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/** Safe API diagnostics: never include raw server bodies or request credentials. */
class ApiFailure(status: Int, body: String, retryAfter: String?, now: Long) {
    @JvmField val code: String = errorCode(body)
    @JvmField val authorization: Boolean = code == "token_expired" || code == "token_rejected" ||
        code == "invalid_token" || (status == 401 && code == "api_error")
    @JvmField val retryable: Boolean = !authorization && (status == 408 || status == 429 || status >= 500)
    @JvmField val retryAt: Long = if (retryable) {
        val serverTime = retryTime(retryAfter, now)
        if (serverTime > 0) serverTime else now + if (status == 429) 30_000L else 5_000L
    } else 0

    companion object {
        private val CODE_PATTERN = Regex("[a-zA-Z0-9_]{1,80}")

        private fun errorCode(body: String): String {
            var value = "api_error"
            try {
                val json = JSONObject(body)
                value = json.optString("code", json.optString("oauth_problem", value))
            } catch (ignored: Exception) {
                for (part in body.split("&")) {
                    val pair = part.split("=", limit = 2)
                    if (pair.size == 2 && (pair[0] == "oauth_problem" || pair[0] == "code")) {
                        value = try {
                            URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name())
                        } catch (malformed: Exception) {
                            "api_error"
                        }
                        break
                    }
                }
            }
            return if (value.matches(CODE_PATTERN)) value else "api_error"
        }

        private fun retryTime(header: String?, now: Long): Long {
            if (header == null) return 0
            return try {
                val seconds = header.trim().toLong()
                if (seconds < 0) 0 else Math.addExact(now, Math.multiplyExact(seconds, 1000))
            } catch (ignored: Exception) {
                try {
                    val date = ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
                    maxOf(now, date)
                } catch (malformed: Exception) {
                    0
                }
            }
        }
    }
}

