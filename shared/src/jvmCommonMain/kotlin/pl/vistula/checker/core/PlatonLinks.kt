package pl.vistula.checker.core

import java.net.URI
import java.util.Locale

/** Links to Vistula's Platon only; credentials and session parameters are never task links. */
object PlatonLinks {
    const val HOME = "https://platon.vistula.edu.pl/"
    private val SENSITIVE = Regex("(?s).*(?:token|password|secret|ticket|session|code|sesskey|sid|sessionid|authorization)=.*")

    @JvmStatic
    fun normalize(address: String?): String {
        if (address == null || address.trim().isEmpty()) return ""
        val value = address.trim()
        try {
            val uri = URI(value)
            if (!"https".equals(uri.scheme, ignoreCase = true) || !"platon.vistula.edu.pl".equals(uri.host, ignoreCase = true) ||
                uri.rawUserInfo != null || (uri.port != -1 && uri.port != 443) ||
                value.length > 2048 || uri.rawPath.contains("\\")) throw IllegalArgumentException()
            val query = uri.rawQuery
            val parameters = "${uri.query}&${uri.fragment}"
            if (parameters.lowercase(Locale.ROOT).matches(SENSITIVE)) throw IllegalArgumentException()
            return "https://platon.vistula.edu.pl" + (if (uri.rawPath.isEmpty()) "/" else uri.rawPath) +
                (if (query == null) "" else "?$query") + (if (uri.rawFragment == null) "" else "#" + uri.rawFragment)
        } catch (e: Exception) {
            throw IllegalArgumentException("Wklej link HTTPS do zadania na platon.vistula.edu.pl, bez danych logowania.")
        }
    }

    @JvmStatic
    fun isTaskLink(address: String?): Boolean = try {
        normalize(address).isNotEmpty()
    } catch (e: IllegalArgumentException) {
        false
    }
}

