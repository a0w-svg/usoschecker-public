package pl.vistula.checker.core

import java.net.URI
import java.net.URISyntaxException

/** Limits server-supplied browser links to ordinary web addresses. */
object WebLinks {
    private val API_METHOD = Regex("services/(?:[a-z0-9_]+/)+[a-z0-9_]+")

    @JvmStatic
    fun apiDocumentation(method: String?): String {
        if (method == null || !method.matches(API_METHOD))
            return "https://usosapps.vistula.edu.pl/developers/api/"
        val slash = method.lastIndexOf('/')
        return "https://usosapps.vistula.edu.pl/developers/api/" + method.substring(0, slash) + "/#" + method.substring(slash + 1)
    }

    @JvmStatic
    fun isWebUrl(address: String?): Boolean {
        if (address == null) return false
        return try {
            val uri = URI(address)
            ("https".equals(uri.scheme, ignoreCase = true) || "http".equals(uri.scheme, ignoreCase = true)) &&
                uri.host != null && uri.host.isNotEmpty() && uri.rawUserInfo == null
        } catch (e: URISyntaxException) {
            false
        }
    }
}

