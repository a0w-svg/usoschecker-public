package pl.vistula.checker.ui.compose

import pl.vistula.checker.core.ApiLanguage

internal fun toolText(language: String, polish: String, english: String, ukrainian: String): String =
    when (ApiLanguage.normalize(language)) { "en" -> english; "uk" -> ukrainian; else -> polish }

/** Picks the Polish, English or Ukrainian variant for [language]; call as `tr("pl", "en", "uk")`. */
internal class Translator(private val language: String) {
    operator fun invoke(pl: String, en: String, uk: String): String = toolText(language, pl, en, uk)
}
