package pl.vistula.checker.core

import org.json.JSONObject

/** Roles come from the authenticated USOS profile, never from missing student data. */
object AccountRole {
    enum class Mode { STUDENT, LECTURER, BOTH }

    @JvmStatic
    fun lecturer(state: JSONObject): Boolean = state.optInt("staffStatus", -1) == 2

    @JvmStatic
    fun lecturerOnly(state: JSONObject): Boolean = lecturer(state) && state.optInt("studentStatusCode", -1) == 0

    @JvmStatic
    fun mode(state: JSONObject): Mode = when {
        lecturerOnly(state) -> Mode.LECTURER
        lecturer(state) -> Mode.BOTH
        else -> Mode.STUDENT
    }

    @JvmStatic
    fun hasTeaching(state: JSONObject): Boolean {
        if (!lecturer(state)) return false
        if (state.optBoolean("isLecturer", false)) return true
        val teaching = state.optJSONObject("studentServices")?.optJSONObject("teaching")?.optJSONObject("data")
        val groups = state.optJSONObject("studentServices")?.optJSONObject("lecturerGroups")?.optJSONObject("data")
        return (teaching?.optJSONArray("courses")?.length() ?: 0) > 0 ||
            (groups?.optJSONArray("groups")?.length() ?: 0) > 0
    }

    @JvmStatic
    fun coordinator(state: JSONObject): Boolean {
        if (!lecturer(state)) return false
        if (state.optBoolean("isCoordinator", false)) return true
        val groups = state.optJSONObject("studentServices")?.optJSONObject("lecturerGroups")?.optJSONObject("data")
        return (groups?.optJSONArray("coordinated")?.length() ?: 0) > 0
    }
}
