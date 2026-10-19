package pl.vistula.checker.shared

/** Prototype expect/actual pair proving that shared code builds for every target. */
expect fun platformName(): String

fun greeting(): String = "Vistula Checker on ${platformName()}"

