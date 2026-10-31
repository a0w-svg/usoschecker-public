package pl.vistula.checker.ui

/**
 * Screen ids understood by [pl.vistula.checker.MainActivity.navigate].
 *
 * The numeric values are stored in saved instance state and in launcher shortcuts / widget intents,
 * so existing values must never change; only append new ones.
 */
object Tabs {
    const val HOME = 0
    const val HISTORY = 1
    const val GRADES = 2
    const val API = 3
    const val ACCOUNT = 4
    const val SCHEDULE = 5
    const val MORE = 6
    const val TASKS = 7
    const val PAYMENTS = 8
    const val GPA_SIMULATOR = 9
    const val CAMPUS_GUIDE = 10
    const val NEWS = 11
    const val ATTENDANCE = 12
    const val CARDS = 13
    const val DOCUMENTS = 14
    const val LECTURER = 15
    const val ACADEMIC_EXTRAS = 16
    const val STAFF_DIRECTORY = 17

    /**
     * Maps a tab id received from another app (the launcher activity is exported) to a known screen,
     * falling back to [HOME] so a crafted intent cannot request a nonexistent screen.
     */
    @JvmStatic
    fun fromExternal(value: Int): Int = if (value in HOME..STAFF_DIRECTORY) value else HOME
}
