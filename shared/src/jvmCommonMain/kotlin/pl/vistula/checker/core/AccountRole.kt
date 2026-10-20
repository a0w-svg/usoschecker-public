package pl.vistula.checker.core

import org.json.JSONObject

/** Roles come from the authenticated USOS profile, never from missing student data. */
object AccountRole {
    fun lecturer(state: JSONObject): Boolean = state.optInt("staffStatus", -1) == 2
    fun lecturerOnly(state: JSONObject): Boolean = lecturer(state) && state.optInt("studentStatusCode", -1) == 0
}
