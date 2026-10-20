package pl.vistula.checker.ui.compose

import pl.vistula.checker.core.ApiLanguage

internal fun toolText(language: String, polish: String, english: String, ukrainian: String): String =
    when (ApiLanguage.normalize(language)) { "en" -> english; "uk" -> ukrainian; else -> polish }
