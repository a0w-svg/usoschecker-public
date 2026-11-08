package pl.vistula.checker

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Typed access to the `student_tools` SharedPreferences file. Key names are unchanged (the file already exists on
 * installed apps) and are collected in [Keys] so a typo cannot silently create a second setting.
 * Preferences are opened lazily, so an instance may be created while an Activity is still being constructed.
 */
class StudentPrefs(private val context: Context) {
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences(NAME, Context.MODE_PRIVATE) }

    /** Page the Platon sync starts from; empty or null means Platon is not linked. */
    var platonSource: String?
        get() = prefs.getString(Keys.PLATON_SOURCE, null)?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit { if (value.isNullOrEmpty()) remove(Keys.PLATON_SOURCE) else putString(Keys.PLATON_SOURCE, value) }

    val platonLinked: Boolean get() = platonSource != null

    var platonSyncedAt: Long
        get() = prefs.getLong(Keys.PLATON_SYNCED_AT, 0L)
        set(value) = prefs.edit { putLong(Keys.PLATON_SYNCED_AT, value) }

    var platonOffered: Boolean
        get() = prefs.getBoolean(Keys.PLATON_OFFERED, false)
        set(value) = prefs.edit { putBoolean(Keys.PLATON_OFFERED, value) }

    /** Set by the background refresh when the Platon session could not be renewed silently. */
    var platonExpired: Boolean
        get() = prefs.getBoolean(Keys.PLATON_EXPIRED, false)
        set(value) = prefs.edit { putBoolean(Keys.PLATON_EXPIRED, value) }

    /** Exception type of the last failed Platon sync (never a message), or null. */
    var platonLastError: String?
        get() = prefs.getString(Keys.PLATON_LAST_ERROR, null)?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit { if (value.isNullOrEmpty()) remove(Keys.PLATON_LAST_ERROR) else putString(Keys.PLATON_LAST_ERROR, value) }

    var taskOrder: String?
        get() = prefs.getString(Keys.TASK_ORDER, null)
        set(value) = prefs.edit { putString(Keys.TASK_ORDER, value) }

    var gradeOrder: String?
        get() = prefs.getString(Keys.GRADE_ORDER, null)
        set(value) = prefs.edit { putString(Keys.GRADE_ORDER, value) }

    var calendarMode: String
        get() = prefs.getString(Keys.CALENDAR_MODE, "list") ?: "list"
        set(value) = prefs.edit { putString(Keys.CALENDAR_MODE, value) }

    /** Day (ISO date) on which the deadline reminder was last shown. */
    var taskReminderDay: String?
        get() = prefs.getString(Keys.TASK_REMINDER_DAY, null)
        set(value) = prefs.edit { putString(Keys.TASK_REMINDER_DAY, value) }

    /** Forgets everything about the Platon link (cached data lives in the account state, not here). */
    fun forgetPlaton() = prefs.edit {
        remove(Keys.PLATON_SOURCE)
        remove(Keys.PLATON_SYNCED_AT)
        remove(Keys.PLATON_OFFERED)
        remove(Keys.PLATON_EXPIRED)
        remove(Keys.PLATON_LAST_ERROR)
    }

    object Keys {
        const val PLATON_SOURCE = "platonSource"
        const val PLATON_SYNCED_AT = "platonSyncedAt"
        const val PLATON_OFFERED = "platonOffered"
        const val PLATON_EXPIRED = "platonExpired"
        const val PLATON_LAST_ERROR = "platonLastError"
        const val TASK_ORDER = "taskOrder"
        const val GRADE_ORDER = "gradeOrder"
        const val CALENDAR_MODE = "calendarMode"
        const val TASK_REMINDER_DAY = "taskReminderDay"
    }

    companion object {
        const val NAME = "student_tools"
    }
}
