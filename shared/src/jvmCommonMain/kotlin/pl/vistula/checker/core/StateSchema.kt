package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject

/** Names of the top-level keys of the account state JSON that more than one class reads or writes. */
object StateKeys {
    const val SCHEMA_VERSION = "schemaVersion"
    const val TASKS = "tasks"
    const val HISTORY = "history"
    const val SNAPSHOT = "snapshot"
    const val PLATON_COURSES = "platonCourses"
    const val PLATON_MESSAGES = "platonMessages"
    const val PLATON_SEMESTERS = "platonSemesters"
}

/**
 * Version of the stored account state. Every change to the shape of existing data adds a numbered step to [migrate]
 * and raises [CURRENT]; steps must be idempotent, tolerate garbage, and never touch credentials.
 *
 * - 0: states written before versioning (no [StateKeys.SCHEMA_VERSION]).
 * - 1: a completed task always shows 100% progress (older builds could leave a stale value after "done").
 */
object StateSchema {
    const val CURRENT = 1

    /** Brings [state] up to [CURRENT] in place and returns true when anything changed. */
    @JvmStatic
    fun migrate(state: JSONObject): Boolean {
        if (state.length() == 0) return false // signed out: nothing stored, nothing to stamp
        val before = state.optInt(StateKeys.SCHEMA_VERSION, 0)
        if (before >= CURRENT) return false
        if (before < 1) completedTasksAreFullyProgressed(state.optJSONArray(StateKeys.TASKS))
        state.put(StateKeys.SCHEMA_VERSION, CURRENT)
        return true
    }

    private fun completedTasksAreFullyProgressed(tasks: JSONArray?) {
        if (tasks == null) return
        for (i in 0 until tasks.length()) {
            val task = tasks.optJSONObject(i) ?: continue
            if (task.optBoolean("done") && task.optInt("progress", 0) != 100) task.put("progress", 100)
        }
    }
}
