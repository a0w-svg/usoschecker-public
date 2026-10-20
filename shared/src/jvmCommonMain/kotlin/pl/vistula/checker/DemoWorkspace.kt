package pl.vistula.checker

import java.util.UUID
import org.json.JSONObject

/** A demo is an isolated workspace; entering or leaving it never writes the user's vault. */
class DemoWorkspace {
    private val vault = object : VaultStore {
        override val storePath = "memory-demo:${UUID.randomUUID()}"
        private var state = JSONObject()
        @Synchronized override fun read() = JSONObject(state.toString())
        @Synchronized override fun write(state: JSONObject) { this.state = JSONObject(state.toString()) }
        @Synchronized override fun clear() { state = JSONObject() }
    }
    val repository = SharedRepository(vault)
    val preferences: KeyValueStore = object : KeyValueStore {
        private val values = JSONObject()
        @Synchronized override fun getString(key: String, defaultValue: String) = values.optString(key, defaultValue)
        @Synchronized override fun putString(key: String, value: String) { values.put(key, value) }
        @Synchronized override fun getBoolean(key: String, defaultValue: Boolean) = values.optBoolean(key, defaultValue)
        @Synchronized override fun putBoolean(key: String, value: Boolean) { values.put(key, value) }
        @Synchronized override fun getLong(key: String, defaultValue: Long) = values.optLong(key, defaultValue)
        @Synchronized override fun putLong(key: String, value: Long) { values.put(key, value) }
        @Synchronized override fun clear() { values.keys().asSequence().toList().forEach(values::remove) }
    }
    init { reset(false) }
    fun reset(changed: Boolean) {
        val data = Demo.state(changed).put("token", "demo").put("enabled", false).put("accountSession", vault.storePath)
            .put("studentServices", DemoStudentServices.data()).put("campusOverride", "0000")
        vault.write(data)
    }
}

/** Claim a foreground operation before launching its coroutine, including repeated keyboard events. */
class OperationGate {
    private val running = java.util.concurrent.atomic.AtomicBoolean()
    fun tryStart() = running.compareAndSet(false, true)
    fun finish() { running.set(false) }
    fun isRunning() = running.get()
}
