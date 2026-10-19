package pl.vistula.checker.core

import java.text.Normalizer
import java.util.Locale

/** Word search accepts accents or plain keyboard equivalents. */
object StudentSearch {
    private val MARKS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("\\s+")

    private fun normalize(text: String): String =
        MARKS.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "").replace('ł', 'l')

    @JvmStatic
    fun matches(query: String, vararg values: String): Boolean {
        val text = normalize(values.joinToString(" "))
        for (word in normalize(query).trim().split(WHITESPACE)) if (!text.contains(word)) return false
        return true
    }
}

