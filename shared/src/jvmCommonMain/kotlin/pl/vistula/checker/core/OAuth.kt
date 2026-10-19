package pl.vistula.checker.core

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.TreeMap
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** OAuth 1.0a, RFC 5849. Parameters are transmitted in the query, never logged. */
object OAuth {
    @JvmStatic
    fun encode(value: String): String {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
                .replace("*", "%2A").replace("%7E", "~")
        } catch (e: java.io.UnsupportedEncodingException) {
            throw AssertionError(e)
        }
    }

    @JvmStatic
    fun query(params: Map<String, String>): String {
        val pairs = ArrayList<String>(params.size)
        params.forEach { (k, v) -> pairs.add("${encode(k)}=${encode(v)}") }
        pairs.sort()
        return pairs.joinToString("&")
    }

    @JvmStatic
    @Throws(Exception::class)
    fun signature(
        method: String,
        url: String,
        params: Map<String, String>,
        consumerSecret: String,
        tokenSecret: String,
    ): String {
        val unsigned = TreeMap(params)
        unsigned.remove("oauth_signature")
        val base = "${method.uppercase(Locale.ROOT)}&${encode(url)}&${encode(query(unsigned))}"
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(
            SecretKeySpec(
                "${encode(consumerSecret)}&${encode(tokenSecret)}".toByteArray(StandardCharsets.UTF_8),
                "HmacSHA1",
            ),
        )
        return Base64.getEncoder().encodeToString(mac.doFinal(base.toByteArray(StandardCharsets.UTF_8)))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun sign(
        url: String,
        params: Map<String, String>,
        key: String,
        secret: String,
        token: String,
        tokenSecret: String,
    ): Map<String, String> = sign("GET", url, params, key, secret, token, tokenSecret)

    @JvmStatic
    @Throws(Exception::class)
    fun sign(
        method: String,
        url: String,
        params: Map<String, String>,
        key: String,
        secret: String,
        token: String,
        tokenSecret: String,
    ): Map<String, String> {
        val signed = TreeMap(params)
        signed["oauth_consumer_key"] = key
        signed["oauth_signature_method"] = "HMAC-SHA1"
        signed["oauth_timestamp"] = (System.currentTimeMillis() / 1000).toString()
        signed["oauth_nonce"] = UUID.randomUUID().toString()
        signed["oauth_version"] = "1.0"
        if (token.isNotEmpty()) signed["oauth_token"] = token
        signed["oauth_signature"] = signature(method, url, signed, secret, tokenSecret)
        return signed
    }

    @JvmStatic
    fun form(body: String): Map<String, String> {
        val result = HashMap<String, String>()
        for (part in body.split("&")) {
            val p = part.split("=", limit = 2)
            if (p.size == 2) {
                try {
                    result[URLDecoder.decode(p[0], "UTF-8")] = URLDecoder.decode(p[1], "UTF-8")
                } catch (e: java.io.UnsupportedEncodingException) {
                    throw AssertionError(e)
                }
            }
        }
        val token = result["oauth_token"]
        val tokenSecret = result["oauth_token_secret"]
        if (token.isNullOrEmpty() || tokenSecret.isNullOrEmpty())
            throw IllegalArgumentException("Niepełna odpowiedź autoryzacji USOS.")
        return result
    }
}

