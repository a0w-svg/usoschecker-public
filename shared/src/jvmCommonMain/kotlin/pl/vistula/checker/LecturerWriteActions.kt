package pl.vistula.checker

import java.io.IOException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.core.AccountRole
import pl.vistula.checker.core.LecturerServices
import pl.vistula.checker.core.StudentServices

/**
 * Experimental lecturer write actions for USOS course tests, gradebooks, and attendance lists.
 *
 * Safeguards:
 * 1. Strictly gated on verified lecturer role (`AccountRole.lecturer(state)`).
 * 2. Requires experimental feature toggle (`PREF_EXPERIMENTAL_WRITES`, default true) AND
 *    explicit one-time user acknowledgment (`PREF_EXPERIMENTAL_WRITES_ACK`).
 * 3. Pre-checks target entity via `services/crstests/node2` or `services/attendance/attendance_list` before mutating.
 * 4. Sends a single POST (never GET), never retries on uncertain network/5xx/timeout errors,
 *    and re-verifies account identity before and after.
 * 5. Performs an authoritative re-read after each write.
 * 6. Keeps a minimal audit log of node/target IDs only (never student names or grade values).
 */
object LecturerWriteActions {
    const val PREF_EXPERIMENTAL_WRITES = "experimental_lecturer_writes"
    const val PREF_EXPERIMENTAL_WRITES_ACK = "experimental_lecturer_writes_ack"
    const val PREF_AUDIT_LOG = "lecturer_write_audit"
    private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    private val STUDENT_ID_PATTERN = Regex("[0-9]{1,20}")
    val ATTENDANCE_MODES = setOf("intramural", "remote", "justified_absence", "absence")
    val LIST_MODES = setOf("intramural", "remote", "hybrid")

    class Uncertain(cause: Exception) : IOException("Lecturer write result unknown", cause)
    class ConfirmedRefreshFailed(cause: Exception) : IOException("Lecturer write confirmed but authoritative re-read failed", cause)

    @JvmStatic
    fun isEnabled(preferences: KeyValueStore): Boolean =
        preferences.getBoolean(PREF_EXPERIMENTAL_WRITES, true)

    @JvmStatic
    fun setEnabled(preferences: KeyValueStore, enabled: Boolean) {
        preferences.putBoolean(PREF_EXPERIMENTAL_WRITES, enabled)
    }

    @JvmStatic
    fun isAcknowledged(preferences: KeyValueStore): Boolean =
        preferences.getBoolean(PREF_EXPERIMENTAL_WRITES_ACK, false)

    @JvmStatic
    fun acknowledge(preferences: KeyValueStore) {
        preferences.putBoolean(PREF_EXPERIMENTAL_WRITES_ACK, true)
    }

    private fun checkPrerequisites(state: JSONObject, preferences: KeyValueStore?, requireCrstestsScope: Boolean = true) {
        check(AccountRole.lecturer(state)) { "Lecturer role required for write actions" }
        if (preferences != null) {
            check(isEnabled(preferences)) { "Experimental lecturer writes are disabled in settings" }
            check(isAcknowledged(preferences)) { "Experimental lecturer writes require user acknowledgment" }
        }
        val token = state.optString("token")
        check(token.isNotBlank() && token != "demo" && !state.optBoolean("authorizationError") &&
            state.optLong("retryAt") <= System.currentTimeMillis()) { "Lecturer authorization unavailable" }
        val scopes = state.optString("scopes")
        check(StudentServices.hasScope(scopes, "staff_perspective")) {
            "Missing required scope: staff_perspective"
        }
        if (requireCrstestsScope) {
            check(StudentServices.hasScope(scopes, "crstests")) {
                "Missing required scopes: staff_perspective|crstests"
            }
        }
    }

    private fun precheckNode(client: UsosClient, nodeId: String, expectedType: String?): JSONObject {
        require(ID_PATTERN.matches(nodeId)) { "Invalid node_id" }
        val current = JSONObject(client.get("services/crstests/node2", mapOf(
            "node_id" to nodeId,
            "fields" to "id|type|visible_for_students|my_permissions|task_node_details|grade_node_details"
        )))
        check(StudentServices.text(current, "id") == nodeId) { "Node verification failed" }
        if (expectedType != null) {
            val actualType = StudentServices.text(current, "type")
            check(actualType.isEmpty() || actualType == expectedType || actualType == "${expectedType}_node") {
                "Unexpected node type: $actualType"
            }
        }
        return current
    }

    private fun precheckAttendanceList(client: UsosClient, listId: String): JSONObject {
        require(ID_PATTERN.matches(listId)) { "Invalid list_id" }
        val current = JSONObject(client.get("services/attendance/attendance_list", mapOf(
            "id" to listId,
            "fields" to "id|course_unit|group_number|date|mode"
        )))
        check(StudentServices.text(current, "id") == listId) { "Attendance list verification failed" }
        return current
    }

    private fun authoritativeReread(client: UsosClient, nodeId: String): JSONObject {
        try {
            val updated = JSONObject(client.get("services/crstests/node2", mapOf(
                "node_id" to nodeId,
                "fields" to "id|parent_id|order|name|description|visible_for_students|type|my_permissions|stats|grade_node_details|task_node_details"
            )))
            check(StudentServices.text(updated, "id") == nodeId) { "Authoritative re-read id mismatch" }
            return LecturerServices.safeNodeDetail(updated)
        } catch (e: Exception) {
            throw ConfirmedRefreshFailed(e)
        }
    }

    private fun authoritativeAttendanceReread(client: UsosClient, listId: String): JSONObject {
        try {
            val meta = JSONObject(client.get("services/attendance/attendance_list", mapOf(
                "id" to listId,
                "fields" to "id|course_unit|group_number|date|mode"
            )))
            check(StudentServices.text(meta, "id") == listId) { "Authoritative attendance list id mismatch" }
            val entriesRaw = JSONArray(client.get("services/attendance/attendance", mapOf(
                "list_id" to listId,
                "fields" to "student|attendance_mode|comment"
            )))
            return LecturerServices.safeAttendanceEntries(meta, entriesRaw)
        } catch (e: Exception) {
            throw ConfirmedRefreshFailed(e)
        }
    }

    private fun executeSinglePost(
        client: UsosClient,
        state: JSONObject,
        endpoint: String,
        params: Map<String, String>,
        checkAccount: () -> Unit,
        allowEmptySuccessObject: Boolean = false
    ) {
        checkAccount()
        val response = try {
            JSONObject(client.request("POST", endpoint, params, true, state.optString("token"), state.optString("tokenSecret")))
        } catch (e: UsosClient.ApiException) {
            if (e.status == 408 || e.status >= 500) throw Uncertain(e) else throw e
        } catch (e: Exception) {
            throw Uncertain(e)
        }
        when {
            response.opt("success") == true -> Unit
            response.opt("success") == false -> error("USOS rejected lecturer write action")
            allowEmptySuccessObject && response.length() == 0 -> Unit
            else -> throw Uncertain(IllegalStateException("Unexpected lecturer write response"))
        }
        checkAccount()
    }

    @JvmStatic
    @JvmOverloads
    fun updatePoints(
        client: UsosClient,
        state: JSONObject,
        nodeId: String,
        studentId: String,
        points: Double,
        publicComment: String = "",
        lastChanged: String? = null,
        preferences: KeyValueStore? = null,
        checkAccount: () -> Unit = {}
    ): JSONObject {
        checkPrerequisites(state, preferences)
        require(STUDENT_ID_PATTERN.matches(studentId)) { "Invalid student_id" }
        require(points.isFinite()) { "Invalid points value" }
        require(publicComment.length <= 300) { "Public comment exceeds 300 characters" }

        val existing = precheckNode(client, nodeId, "task")
        val taskDetails = existing.optJSONObject("task_node_details")
        if (taskDetails != null && taskDetails.has("points_min") && taskDetails.has("points_max")) {
            val min = taskDetails.optDouble("points_min", 0.0)
            val max = taskDetails.optDouble("points_max", 0.0)
            if (max > min) {
                require(points in min..max) { "Points $points outside node range [$min, $max]" }
            }
        }

        val entry = JSONObject()
            .put("points", points)
            .put("public_comment", publicComment)
            .put("last_changed", if (lastChanged.isNullOrBlank()) JSONObject.NULL else lastChanged)
        val payload = JSONObject().put(studentId, entry)

        executeSinglePost(
            client = client,
            state = state,
            endpoint = "services/crstests/update_points",
            params = mapOf("node_id" to nodeId, "new_points" to payload.toString()),
            checkAccount = checkAccount
        )
        recordAudit(preferences, "update_points", nodeId, studentId)
        return authoritativeReread(client, nodeId)
    }

    @JvmStatic
    @JvmOverloads
    fun updateGrade(
        client: UsosClient,
        state: JSONObject,
        nodeId: String,
        studentId: String,
        order: Int,
        publicComment: String = "",
        privateComment: String = "",
        lastChanged: String? = null,
        preferences: KeyValueStore? = null,
        checkAccount: () -> Unit = {}
    ): JSONObject {
        checkPrerequisites(state, preferences)
        require(STUDENT_ID_PATTERN.matches(studentId)) { "Invalid student_id" }
        require(order >= -1) { "Invalid grade order" }
        require(publicComment.length <= 300 && privateComment.length <= 300) { "Comment exceeds 300 characters" }

        precheckNode(client, nodeId, "grade")

        val entry = JSONObject()
            .put("order", order)
            .put("public_comment", publicComment)
            .put("private_comment", privateComment)
            .put("last_changed", if (lastChanged.isNullOrBlank()) JSONObject.NULL else lastChanged)
        val payload = JSONObject().put(studentId, entry)

        executeSinglePost(
            client = client,
            state = state,
            endpoint = "services/crstests/update_grades",
            params = mapOf("node_id" to nodeId, "new_grades" to payload.toString()),
            checkAccount = checkAccount
        )
        recordAudit(preferences, "update_grades", nodeId, studentId)
        return authoritativeReread(client, nodeId)
    }

    @JvmStatic
    @JvmOverloads
    fun changeGradeNodeProperties(
        client: UsosClient,
        state: JSONObject,
        nodeId: String,
        namePl: String,
        nameEn: String,
        visibleForStudents: Boolean,
        preferences: KeyValueStore? = null,
        checkAccount: () -> Unit = {}
    ): JSONObject {
        checkPrerequisites(state, preferences)
        require(namePl.isNotBlank() && namePl.length <= 200) { "Invalid Polish node name" }
        require(nameEn.isNotBlank() && nameEn.length <= 200) { "Invalid English node name" }

        precheckNode(client, nodeId, "grade")

        executeSinglePost(
            client = client,
            state = state,
            endpoint = "services/crstests/change_grade_node_properties",
            params = mapOf(
                "node_id" to nodeId,
                "name_pl" to namePl.trim(),
                "name_en" to nameEn.trim(),
                "visible_for_students" to visibleForStudents.toString()
            ),
            checkAccount = checkAccount
        )
        recordAudit(preferences, "change_grade_node_properties", nodeId, "")
        return authoritativeReread(client, nodeId)
    }

    @JvmStatic
    @JvmOverloads
    fun changeTaskNodeProperties(
        client: UsosClient,
        state: JSONObject,
        nodeId: String,
        namePl: String,
        nameEn: String,
        visibleForStudents: Boolean,
        pointsMin: Double,
        pointsMax: Double,
        pointsPrecision: Int,
        preferences: KeyValueStore? = null,
        checkAccount: () -> Unit = {}
    ): JSONObject {
        checkPrerequisites(state, preferences)
        require(namePl.isNotBlank() && namePl.length <= 200) { "Invalid Polish node name" }
        require(nameEn.isNotBlank() && nameEn.length <= 200) { "Invalid English node name" }
        require(pointsMin <= 0.0 && pointsMax > 0.0 && pointsMin < pointsMax) { "Invalid task points range" }
        require(pointsPrecision in 0..3) { "points_precision must be in 0..3" }

        precheckNode(client, nodeId, "task")

        executeSinglePost(
            client = client,
            state = state,
            endpoint = "services/crstests/change_task_node_properties",
            params = mapOf(
                "node_id" to nodeId,
                "name_pl" to namePl.trim(),
                "name_en" to nameEn.trim(),
                "visible_for_students" to visibleForStudents.toString(),
                "points_min" to String.format(Locale.US, "%.2f", pointsMin),
                "points_max" to String.format(Locale.US, "%.2f", pointsMax),
                "points_precision" to pointsPrecision.toString()
            ),
            checkAccount = checkAccount
        )
        recordAudit(preferences, "change_task_node_properties", nodeId, "")
        return authoritativeReread(client, nodeId)
    }

    @JvmStatic
    @JvmOverloads
    fun updateAttendance(
        client: UsosClient,
        state: JSONObject,
        listId: String,
        studentId: String,
        attendanceMode: String,
        comment: String = "",
        preferences: KeyValueStore? = null,
        checkAccount: () -> Unit = {}
    ): JSONObject {
        checkPrerequisites(state, preferences, requireCrstestsScope = false)
        require(STUDENT_ID_PATTERN.matches(studentId)) { "Invalid student_id" }
        val normalizedMode = attendanceMode.trim()
        require(normalizedMode in ATTENDANCE_MODES) { "Invalid attendance_mode: $normalizedMode" }
        require(comment.length <= 200) { "Comment exceeds 200 characters" }

        precheckAttendanceList(client, listId)

        executeSinglePost(
            client = client,
            state = state,
            endpoint = "services/attendance/update_attendance",
            params = mapOf(
                "list_id" to listId,
                "student_id" to studentId,
                "attendance_mode" to normalizedMode,
                "comment" to comment.trim()
            ),
            checkAccount = checkAccount,
            allowEmptySuccessObject = true
        )
        recordAudit(preferences, "update_attendance", listId, studentId)
        return authoritativeAttendanceReread(client, listId)
    }

    @JvmStatic
    @JvmOverloads
    fun changeAttendanceListMode(
        client: UsosClient,
        state: JSONObject,
        listId: String,
        mode: String,
        preferences: KeyValueStore? = null,
        checkAccount: () -> Unit = {}
    ): JSONObject {
        checkPrerequisites(state, preferences, requireCrstestsScope = false)
        val normalizedMode = mode.trim()
        require(normalizedMode in LIST_MODES) { "Invalid attendance list mode: $normalizedMode" }

        precheckAttendanceList(client, listId)

        executeSinglePost(
            client = client,
            state = state,
            endpoint = "services/attendance/change_list_mode",
            params = mapOf(
                "list_id" to listId,
                "mode" to normalizedMode
            ),
            checkAccount = checkAccount,
            allowEmptySuccessObject = true
        )
        recordAudit(preferences, "change_list_mode", listId, "")
        return authoritativeAttendanceReread(client, listId)
    }

    private fun recordAudit(preferences: KeyValueStore?, action: String, nodeId: String, targetId: String) {
        if (preferences == null) return
        val existing = try { JSONArray(preferences.getString(PREF_AUDIT_LOG, "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray()
        next.put(
            JSONObject()
                .put("at", System.currentTimeMillis())
                .put("action", action)
                .put("nodeId", nodeId)
                .put("targetId", targetId)
        )
        for (i in 0 until minOf(existing.length(), 49)) {
            existing.optJSONObject(i)?.let { next.put(it) }
        }
        preferences.putString(PREF_AUDIT_LOG, next.toString())
    }
}
