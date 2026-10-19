package pl.vistula.checker.core

import java.util.Locale
import org.json.JSONException
import org.json.JSONObject

/** Language selection for API dictionaries; scoped parsing never changes the process locale. */
object ApiLanguage {
    @JvmField val SUPPORTED: Array<String> = arrayOf("pl", "en", "uk")
    private val PARSING = ThreadLocal<String>()

    fun interface Read<T> {
        @Throws(Exception::class)
        fun get(): T
    }

    @JvmStatic
    @Throws(Exception::class)
    fun <T> `in`(language: String?, read: Read<T>): T {
        val previous = PARSING.get()
        PARSING.set(normalize(language))
        return try {
            read.get()
        } finally {
            if (previous == null) PARSING.remove() else PARSING.set(previous)
        }
    }

    @JvmStatic
    fun label(polish: String, english: String, ukrainian: String): String {
        val cur = current()
        return if ("uk" == cur) ukrainian else if ("en" == cur) english else polish
    }

    @JvmStatic
    fun current(): String = PARSING.get() ?: "pl"

    @JvmStatic
    fun normalize(tag: String?): String {
        val language = Locale.forLanguageTag(tag?.replace('_', '-') ?: "").language
        return if ("en" == language || "uk" == language) language else "pl"
    }

    @JvmStatic
    @JvmOverloads
    fun text(value: Any?, tag: String? = current()): String {
        if (value is String) return value
        if (value !is JSONObject) return ""
        val requested = normalize(tag)
        // Ukrainian is often unavailable in USOS; prefer English before Polish in that case.
        val order = if ("pl" == requested) arrayOf("pl", "en", "uk") else arrayOf(requested, "en", "pl")
        for (key in order) {
            val candidate = value.opt(key)
            if (candidate is String && candidate.trim().isNotEmpty()) return candidate
        }
        for (key in UsosParser.sortedKeys(value)) {
            val candidate = value.opt(key)
            if (candidate is String && candidate.trim().isNotEmpty()) return candidate
        }
        return ""
    }

    /** Keep only text translations, without retaining unrelated fields from an API object. */
    @JvmStatic
    @Throws(JSONException::class)
    fun safe(value: Any?): Any {
        if (value is String) return value
        val safe = JSONObject()
        if (value is JSONObject) {
            for (key in UsosParser.sortedKeys(value)) {
                val text = value.opt(key)
                if (key.matches(Regex("[a-z]{2}(?:-[A-Za-z]{2})?")) && text is String) {
                    safe.put(key, text)
                }
            }
        }
        return safe
    }
}

