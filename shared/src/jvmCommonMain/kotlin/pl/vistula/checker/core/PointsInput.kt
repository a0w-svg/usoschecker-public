package pl.vistula.checker.core

/** Parses user-typed point values, accepting both "4.5" and the Polish "4,5". */
object PointsInput {
    @JvmStatic
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
