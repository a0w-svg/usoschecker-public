package pl.vistula.checker

import org.json.JSONObject
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.Snapshot

/** Platform-agnostic encrypted state storage (Android Keystore on Android, AES-GCM file store on Desktop). */
interface VaultStore {
    val storePath: String

    @Throws(Exception::class)
    fun read(): JSONObject

    @Throws(Exception::class)
    fun write(state: JSONObject)

    fun clear()
}

/** Platform-agnostic key-value preferences store (SharedPreferences on Android, JSON file on Desktop). */
interface KeyValueStore {
    fun getString(key: String, defaultValue: String = ""): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getLong(key: String, defaultValue: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun clear()
}

/** Platform-agnostic notification publisher for detected USOS grade/timetable changes. */
fun interface ChangeNotifier {
    fun notifyChanges(changes: List<Change>)
}

/** Platform-agnostic background sync and class reminder scheduler. */
interface SyncScheduler {
    fun schedulePeriodicSync(enabled: Boolean)
    fun scheduleNextReminder(snapshot: Snapshot?, enabled: Boolean, leadMinutes: Int)
}

/** Common interface implemented by both Android Repository and KMP SharedRepository. */
interface StateRepository {
    fun interface Update {
        @Throws(Exception::class)
        fun apply(state: JSONObject)
    }

    @Throws(Exception::class)
    fun read(): JSONObject

    @Throws(Exception::class)
    fun update(operation: Update)
}

