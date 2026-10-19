package pl.vistula.checker.core

/** Feature choices preserve custom scopes and never pretend to change an existing token. */
object FeatureAccess {
    val optional = linkedMapOf(
        "payments" to setOf("payments"),
        "cards" to setOf("cards"),
        "documents" to setOf("adm_documents"),
        "tests" to setOf("crstests"),
        "profile" to setOf("photo", "email")
    )

    fun selected(scopes: String, feature: String): Boolean =
        optional.getValue(feature).all { StudentServices.hasScope(scopes, it) }

    fun change(scopes: String, feature: String, enabled: Boolean): String {
        val result = StudentServices.normalizeScopes(scopes).split('|').filter { it.isNotEmpty() }.toMutableSet()
        val values = optional.getValue(feature)
        if (enabled) result.addAll(values) else result.removeAll(values)
        result.addAll(listOf("grades", "studies", "offline_access"))
        return result.joinToString("|")
    }
}
