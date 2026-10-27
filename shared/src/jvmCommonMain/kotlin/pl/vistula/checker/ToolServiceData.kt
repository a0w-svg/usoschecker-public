package pl.vistula.checker

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import pl.vistula.checker.core.AccountRole
import pl.vistula.checker.core.ApiLanguage
import pl.vistula.checker.core.CampusAffiliation
import pl.vistula.checker.core.LecturerServices
import pl.vistula.checker.core.PaymentServices
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.UsosParser

/** Independent, localized read models; only parsed fields enter the encrypted service cache. */
object ToolServiceData {
    private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

    fun fetch(client: UsosClient, section: String, campus: String = "", state: JSONObject = JSONObject()): JSONObject {
        val result = JSONObject()
        when (section) {
            "affiliation" -> {
                val facultyFields = "id|name|postal_address|homepage_url|email|phone_numbers|path[id|name]"
                val programmes = if (AccountRole.lecturerOnly(state)) JSONArray()
                    else JSONArray(client.get("services/progs/student", mapOf(
                        "active_only" to "true", "old_programs" to "false",
                        "fields" to "id|status|programme[id|faculty[$facultyFields]]"
                    )))
                if (AccountRole.lecturer(state)) {
                    val functions = JSONArray(client.get("services/users/employment_positions", mapOf(
                        "fields" to "faculty"
                    )))
                    val ids = (0 until functions.length()).mapNotNull {
                        functions.optJSONObject(it)?.optJSONObject("faculty")?.optString("id")?.takeIf { id -> id.isNotBlank() }
                    }.distinct()
                    for (id in ids) programmes.put(JSONObject().put("programme", JSONObject().put("faculty",
                        JSONObject(client.get("services/fac/faculty", mapOf("fac_id" to id, "fields" to facultyFields))))))
                }
                val detected = CampusAffiliation.detect(programmes)
                val selected = campus.ifEmpty { detected.optString("detected") }
                if (selected in CampusAffiliation.campuses) {
                    val faculty = JSONObject(client.get("services/fac/faculty", mapOf("fac_id" to selected, "fields" to facultyFields)))
                    val contacts = CampusAffiliation.detect(JSONArray().put(JSONObject().put("programme", JSONObject().put("faculty", faculty))))
                    val units = detected.getJSONArray("units")
                    val extra = contacts.getJSONArray("units")
                    for (i in 0 until extra.length()) {
                        val unit = extra.getJSONObject(i)
                        if ((0 until units.length()).none { units.getJSONObject(it).optString("id") == unit.optString("id") }) units.put(unit)
                    }
                }
                return detected
            }
            "teaching" -> {
                check(AccountRole.lecturer(state)) { "Lecturer account required" }
                val user = JSONObject(client.get("services/users/user", mapOf("fields" to "course_editions_conducted")))
                val courses = user.getJSONArray("course_editions_conducted")
                val safe = JSONArray()
                for (i in 0 until courses.length()) {
                    val course = courses.getJSONObject(i)
                    safe.put(JSONObject().put("id", StudentServices.text(course, "id"))
                        .put("name", ApiLanguage.safe(course.optJSONObject("course")?.opt("name")))
                        .put("term", ApiLanguage.safe(course.optJSONObject("term")?.opt("name"))))
                }
                return JSONObject().put("courses", safe)
            }
            "lecturerGroups" -> {
                check(AccountRole.lecturer(state)) { "Lecturer account required" }
                val groupsRaw = JSONObject(client.get("services/groups/lecturer", mapOf(
                    "active_terms" to "false",
                    "fields" to "course_unit_id|group_number|class_type|course_id|course_name|term_id|group_url"
                )))
                val coordinated = try {
                    val coordRaw = JSONObject(client.get("services/courses/coordinator", mapOf(
                        "active_terms_only" to "false",
                        "fields" to "course|term"
                    )))
                    LecturerServices.safeCoordinatedCourses(coordRaw)
                } catch (_: Exception) {
                    JSONArray()
                }
                return JSONObject()
                    .put("groups", LecturerServices.safeGroups(groupsRaw))
                    .put("coordinated", coordinated)
            }
            "lecturerTests" -> {
                check(AccountRole.lecturer(state)) { "Lecturer account required" }
                val raw = JSONArray(client.get("services/crstests/lecturer2", mapOf(
                    "fields" to "course_edition[course[id|name]|term[id|name]]|is_limited_to_groups|root[id|name|description|visible_for_students|type|subnodes_deep[id|parent_id|order|name|type|visible_for_students]]"
                )))
                return JSONObject().put("trees", LecturerServices.safeLecturerTests(raw))
            }
            "lecturerMeetings" -> {
                check(AccountRole.lecturer(state)) { "Lecturer account required" }
                val meetings = JSONArray(client.get("services/meetings/lecturer", mapOf(
                    "fields" to "id|name|comment|open"
                )))
                val datesByMeeting = LinkedHashMap<String, JSONArray>()
                for (i in 0 until minOf(meetings.length(), 10)) {
                    val m = meetings.optJSONObject(i) ?: continue
                    val id = StudentServices.text(m, "id")
                    if (id.isNotEmpty() && ID_PATTERN.matches(id)) {
                        try {
                            datesByMeeting[id] = JSONArray(client.get("services/meetings/lecturer_meeting_dates", mapOf(
                                "meeting_id" to id,
                                "fields" to "id|comment|start_time|end_time|registration_start_time|registration_end_time|seats_limit|reserve_seats_limit"
                            )))
                        } catch (_: Exception) {
                        }
                    }
                }
                return JSONObject().put("meetings", LecturerServices.safeMeetings(meetings, datesByMeeting))
            }
            "staffDirectory" -> {
                val fac = campus.ifEmpty { "0000" }
                val raw = JSONObject(client.get("services/users/staff_index", mapOf(
                    "fac_ids" to fac,
                    "teachers_only" to "true",
                    "num" to LecturerServices.MAX_STAFF_RESULTS.toString(),
                    "start" to "0",
                    "fields" to "users[id|first_name|last_name|titles]|next_page|total"
                )))
                val users = raw.optJSONArray("users") ?: JSONArray()
                return LecturerServices.safeStaffDirectory(users, raw.optBoolean("next_page", false), raw.optInt("total", users.length()))
            }
            "registrations" -> {
                val regs = JSONArray(client.get("services/registrations/user_registrations", mapOf(
                    "active_only" to "false",
                    "fields" to "id|description|message|type|status|is_linkage_required"
                )))
                val cart = try {
                    JSONArray(client.get("services/registrations/courses_cart", mapOf(
                        "fields" to "course[id|name]|term[id|name]|user_registration_status|is_registration_valid|limits|registrations_count|registration_status"
                    )))
                } catch (_: Exception) {
                    JSONArray()
                }
                return LecturerServices.safeRegistrations(regs, cart)
            }
            "exams" -> {
                val exams = JSONArray(client.get("services/exams/student_exams", mapOf(
                    "fields" to "id|examination_session_id|term[id|name]|course[id|name]|registration_start|registration_end|examiners_visible|code"
                )))
                return JSONObject().put("exams", LecturerServices.safeStudentExams(exams))
            }
            "academicCalendar" -> {
                val today = LocalDate.now(UsosClient.ZONE)
                val fac = campus.ifEmpty { "0000" }
                val events = JSONArray(client.get("services/calendar/search", mapOf(
                    "faculty_id" to fac,
                    "start_date" to today.toString(),
                    "end_date" to today.plusDays(28).toString(),
                    "fields" to "id|name|start_date|end_date|type|is_day_off"
                )))
                val progs = if (!AccountRole.lecturerOnly(state) && StudentServices.hasScope(state.optString("scopes"), "studies")) {
                    try {
                        val rawProgs = JSONArray(client.get("services/progs/student", mapOf(
                            "active_only" to "false",
                            "old_programs" to "false",
                            "fields" to "id|status|admission_date|programme[id|name]"
                        )))
                        LecturerServices.safeProgrammeStages(rawProgs)
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                return JSONObject()
                    .put("events", LecturerServices.safeCalendarEvents(events))
                    .put("programmes", progs)
            }
            "payments" -> {
                val accounts = collection(client.get("services/payments/user_accounts", emptyMap()))
                val payments = JSONArray(client.get("services/payments/user_payments", mapOf(
                    "fields" to PaymentServices.FIELDS
                )))
                for (language in ApiLanguage.SUPPORTED) {
                    result.put(language, ApiLanguage.`in`(language) { UsosParser.payments(accounts, payments).json() })
                }
                result.put("details", PaymentServices.details(payments))
            }
            "remittances" -> return JSONObject().put("items", PaymentServices.remittances(JSONArray(
                client.get("services/payments/user_remittances", mapOf("fields" to "id|amount|remaining|description|status|is_settled|date|currency"))
            )))
            "news" -> {
                val parameters = linkedMapOf("num" to "50", "fields" to "items[article[id|title|publication_date|author|headline_html|content_html]]|next_page|total")
                if (campus.isNotEmpty()) parameters["facpattern"] = if (campus == "0000") "=0000" else "+$campus"
                val raw = JSONObject(client.get("services/news/search", parameters))
                if (raw.optJSONArray("items") == null) throw JSONException("Missing news items")
                for (language in ApiLanguage.SUPPORTED) {
                    result.put(language, ApiLanguage.`in`(language) {
                        JSONArray().apply { UsosParser.newsArticles(raw).forEach { put(it.json()) } }
                    })
                }
                result.put("source", "usos").put("hasMore", raw.optBoolean("next_page")).put("campus", campus)
            }
            else -> throw IllegalArgumentException("Nieznana usługa.")
        }
        return result
    }

    @JvmStatic
    fun fetchGroupDetail(client: UsosClient, state: JSONObject, courseUnitId: String, groupNumber: String): JSONObject {
        check(AccountRole.lecturer(state)) { "Lecturer account required" }
        require(ID_PATTERN.matches(courseUnitId)) { "Invalid course_unit_id" }
        require(groupNumber.matches(Regex("[0-9]{1,6}"))) { "Invalid group_number" }
        val raw = JSONObject(client.get("services/groups/group", mapOf(
            "course_unit_id" to courseUnitId,
            "group_number" to groupNumber,
            "fields" to "course_unit_id|group_number|class_type|course_id|course_name|term_id|lecturers|participants"
        )))
        return LecturerServices.safeGroupDetail(raw)
    }

    @JvmStatic
    fun fetchNodeDetail(client: UsosClient, state: JSONObject, nodeId: String): JSONObject {
        check(AccountRole.lecturer(state)) { "Lecturer account required" }
        require(ID_PATTERN.matches(nodeId)) { "Invalid node_id" }
        val node = JSONObject(client.get("services/crstests/node2", mapOf(
            "node_id" to nodeId,
            "fields" to "id|parent_id|order|name|description|visible_for_students|type|my_permissions|stats|grade_node_details|task_node_details"
        )))
        val stats = try {
            JSONObject(client.get("services/crstests/node_stats", mapOf("node_id" to nodeId)))
        } catch (_: Exception) {
            null
        }
        return LecturerServices.safeNodeDetail(node, stats)
    }

    @JvmStatic
    fun searchTeachers(client: UsosClient, query: String): JSONObject {
        val clean = query.trim()
        require(clean.length in 2..80) { "Search query must be 2..80 characters" }
        val raw = JSONObject(client.get("services/users/search_current_teachers", mapOf(
            "name" to clean,
            "num" to LecturerServices.MAX_STAFF_RESULTS.toString(),
            "start" to "0",
            "fields" to "items[user[id|first_name|last_name|titles]]|next_page"
        )))
        val items = raw.optJSONArray("items") ?: JSONArray()
        return LecturerServices.safeStaffDirectory(items, raw.optBoolean("next_page", false), items.length())
    }

    @JvmStatic
    fun fetchStaffSchedule(client: UsosClient, userId: String, startDate: String): JSONArray {
        require(ID_PATTERN.matches(userId)) { "Invalid user_id" }
        require(runCatching { LocalDate.parse(startDate) }.isSuccess) { "Invalid start date" }
        val rows = JSONArray(client.get("services/tt/staff", mapOf(
            "user_id" to userId,
            "start" to startDate,
            "days" to "7",
            "fields" to "start_time|end_time|name|room_number|building_name|classtype_name"
        )))
        val safe = JSONArray()
        for (i in 0 until minOf(rows.length(), 100)) {
            val row = rows.optJSONObject(i) ?: continue
            safe.put(
                JSONObject()
                    .put("start_time", StudentServices.text(row, "start_time"))
                    .put("end_time", StudentServices.text(row, "end_time"))
                    .put("name", ApiLanguage.safe(row.opt("name")))
                    .put("room_number", StudentServices.text(row, "room_number"))
                    .put("building_name", ApiLanguage.safe(row.opt("building_name")))
                    .put("classtype_name", ApiLanguage.safe(row.opt("classtype_name")))
            )
        }
        return safe
    }

    private fun collection(text: String): Any {
        val value = JSONTokener(text).nextValue()
        if (value !is JSONObject && value !is JSONArray) throw JSONException("Expected collection")
        return value
    }
}
