package pl.vistula.checker.ui

/** Reads a Consumer Key and Secret out of text copied from the USOSapps registration result. */
object KeyPaste {
    private val LABELLED_KEY = Regex("(?i)consumer\\s*key\\W{1,5}([A-Za-z0-9]{16,64})")
    private val LABELLED_SECRET = Regex("(?i)consumer\\s*secret\\W{1,5}([A-Za-z0-9]{16,64})")
    private val BARE_TOKEN = Regex("^[A-Za-z0-9]{16,64}$")

    /** Returns key to secret, or null when the text does not clearly contain both. */
    @JvmStatic
    fun parse(text: String?): Pair<String, String>? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty() || value.length > 400) return null
        val key = LABELLED_KEY.find(value)?.groupValues?.get(1)
        val secret = LABELLED_SECRET.find(value)?.groupValues?.get(1)
        if (key != null && secret != null) return key to secret
        val parts = value.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size == 2 && parts.all { BARE_TOKEN.matches(it) } && parts[0] != parts[1]) return parts[0] to parts[1]
        return null
    }
}
