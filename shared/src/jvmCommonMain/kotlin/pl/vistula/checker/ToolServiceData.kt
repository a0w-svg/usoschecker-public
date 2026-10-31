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
import pl.vistula.checker.core.StudentExtras
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
            "lecturerAttendance" -> {
                check(AccountRole.lecturer(state)) { "Lecturer account required" }
                val lists = JSONArray(client.get("services/attendance/user", mapOf(
                    "fields" to "id|course_unit|group_number|owner|date|mode"
                )))
                return JSONObject().put("lists", LecturerServices.safeAttendanceLists(lists))
            }
            "lecturerReports" -> {
                check(AccountRole.lecturer(state)) { "Lecturer account required" }
                val scopes = state.optString("scopes")
                val surveys = if (StudentServices.hasScope(scopes, "surveys_reports")) {
                    try {
                        JSONArray(client.get("services/surveys/surveys_about_me2", mapOf(
                            "fields" to "id|name|headline_html|start_date|end_date|number_of_answers|group"
                        )))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                val signable = if (StudentServices.hasScope(scopes, "staff_perspective")) {
                    try {
                        JSONArray(client.get("services/theses/signable_reports", mapOf(
                            "fields" to "id|type|title"
                        )))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                return LecturerServices.safeLecturerReports(surveys, signable)
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
                val cart = JSONArray(client.get("services/registrations/courses_cart", mapOf(
                        "fields" to "course[id|name]|term[id|name]|user_registration_status|is_registration_valid|limits|registrations_count|registration_status"
                    )))
                return LecturerServices.safeRegistrations(regs, cart)
            }
            "tokenRegistrations" -> {
                val rows = JSONArray(client.get("services/registrations/user_token_registrations", mapOf(
                    "active_only" to "false",
                    "fields" to "id|description|message|status|is_linkage_required"
                )))
                return LecturerServices.safeRegistrations(rows, JSONArray())
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
                val hasStudies = !AccountRole.lecturerOnly(state) && StudentServices.hasScope(state.optString("scopes"), "studies")
                val progs = if (hasStudies) {
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
                val usedEcts = if (hasStudies) {
                    try {
                        client.get("services/credits/used_sum", emptyMap()).trim().removeSurrounding("\"")
                    } catch (_: Exception) {
                        ""
                    }
                } else ""
                val primaryGroups = if (hasStudies) {
                    try {
                        JSONArray(client.get("services/prgroups/user", mapOf(
                            "fields" to "id|number|description|programme|stage|term"
                        )))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                val customGroups = if (hasStudies) {
                    try {
                        JSONArray(client.get("services/csgroups/user", mapOf(
                            "fields" to "id|name|description|expiration_date"
                        )))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                val groupsAndCredits = LecturerServices.safeStudyGroupsAndCredits(usedEcts, primaryGroups, customGroups)
                return JSONObject()
                    .put("events", LecturerServices.safeCalendarEvents(events))
                    .put("programmes", progs)
                    .put("usedEcts", groupsAndCredits.optString("usedEcts"))
                    .put("primaryGroups", groupsAndCredits.optJSONArray("primaryGroups") ?: JSONArray())
                    .put("customGroups", groupsAndCredits.optJSONArray("customGroups") ?: JSONArray())
            }
            "mobility" -> {
                val scopes = state.optString("scopes")
                val studentMobs = if (!AccountRole.lecturerOnly(state)) {
                    try {
                        JSONArray(client.get("services/mobility/student", mapOf(
                            "fields" to "id|mobility_category|programme|host_institution|start_date|end_date|state"
                        )))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                val coordMobs = if (AccountRole.lecturer(state) && StudentServices.hasScope(scopes, "staff_perspective")) {
                    try {
                        JSONArray(client.get("services/mobility/coordinator", mapOf(
                            "fields" to "id|mobility_category|programme|host_institution|start_date|end_date|state"
                        )))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                return JSONObject()
                    .put("studentMobilities", LecturerServices.safeMobilities(studentMobs))
                    .put("coordinatedMobilities", LecturerServices.safeMobilities(coordMobs))
            }
            "placementAndSlips" -> {
                val scopes = state.optString("scopes")
                val tests = if (StudentServices.hasScope(scopes, "placement_tests")) {
                    try {
                        JSONArray(client.get("services/plctests/user", mapOf(
                            "fields" to "test[id|name|description|max_score]|result"
                        )))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                val slips = if (StudentServices.hasScope(scopes, "slips")) {
                    try {
                        JSONArray(client.get("services/slips/to_be_answered", mapOf(
                            "langpref" to if (ApiLanguage.current() == "pl") "pl" else "en",
                            "num" to "20",
                            "fields" to "id|slip|question",
                            "question_fields" to "id|question",
                            "slip_fields" to "id|client"
                        )))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                val roles = try {
                    JSONArray(client.get("services/slips/user_roles", emptyMap()))
                } catch (_: Exception) {
                    JSONArray()
                }
                return LecturerServices.safePlacementAndSlips(tests, slips, roles)
            }
            "campusGuide" -> {
                val guideLang = if (ApiLanguage.current() == "pl") "pl" else "en"
                val chapters = try {
                    JSONArray(client.get("services/guide/guide", mapOf(
                        "lang" to guideLang,
                        "fields" to "id|title|pages[id|title|entries[id|title]]"
                    )))
                } catch (_: Exception) {
                    JSONArray()
                }
                val dorms = try {
                    JSONArray(client.get("services/housing/dormitory_index", mapOf(
                        "fields" to "id|name|short_name|address|description"
                    )))
                } catch (_: Exception) {
                    JSONArray()
                }
                val periods = try {
                    JSONArray(client.get("services/housing/allocation_period_index", mapOf(
                        "fields" to "id|name|description"
                    )))
                } catch (_: Exception) {
                    JSONArray()
                }
                val buildings = try {
                    JSONArray(client.get("services/geo/building_index", mapOf(
                        "fields" to "id|name|postal_address|campus_name"
                    )))
                } catch (_: Exception) {
                    JSONArray()
                }
                return LecturerServices.safeCampusGuide(chapters, dorms, buildings, periods)
            }
            "communications" -> {
                val scopes = state.optString("scopes")
                val statements = try {
                    JSONArray(client.get("services/events2/personalized_statements", mapOf(
                        "fields" to "id|subject|content|expiration_date"
                    )))
                } catch (_: Exception) {
                    JSONArray()
                }
                val notifications = if (StudentServices.hasScope(scopes, "events")) {
                    try {
                        JSONArray(client.get("services/events2/latest_notifications", emptyMap()))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                val messages = if (StudentServices.hasScope(scopes, "mailclient")) {
                    try {
                        JSONArray(client.get("services/mailclient/user", mapOf(
                            "fields" to "id|subject|status|date"
                        )))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                val categories = if (StudentServices.hasScope(scopes, "mailclient")) {
                    try {
                        JSONObject(client.get("services/mailclient/cases_categories", emptyMap()))
                    } catch (_: Exception) {
                        JSONObject()
                    }
                } else JSONObject()
                val surveys = if (StudentServices.hasScope(scopes, "surveys_filling")) {
                    try {
                        JSONArray(client.get("services/surveys/surveys_to_fill2", mapOf(
                            "include_filled_out" to "true",
                            "fields" to "id|survey_type|name|headline_html|start_date|end_date|can_i_fill_out|did_i_fill_out|group[course_name|group_number]"
                        )))
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else JSONArray()
                return LecturerServices.safeCommunications(statements, notifications, messages, categories, surveys)
            }
                        "theses" -> {
                val studentNumber = state.optString("studentNumber")
                val userId = state.optString("userId").ifEmpty {
                    try {
                        JSONObject(client.get("services/users/user", mapOf("fields" to "id"))).optString("id")
                    } catch (_: Exception) { "" }
                }
                val authoredTheses = if (userId.isNotEmpty()) {
                    try {
                        val raw = JSONObject(client.get("services/theses/user", mapOf(
                            "user_id" to userId,
                            "fields" to "authored_theses[id|type|title|titles|authors[id|first_name|last_name]|supervisors[id|first_name|last_name]|faculty[id|name]]"
                        )))
                        LecturerServices.safeTheses(raw.optJSONArray("authored_theses") ?: JSONArray())
                    } catch (_: Exception) { JSONArray() }
                } else JSONArray()
                return JSONObject().put("theses", authoredTheses)
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
    fun fetchSurveyDetail(client: UsosClient, surveyId: String): JSONObject {
        require(ID_PATTERN.matches(surveyId)) { "Invalid survey_id" }
        val survey = JSONObject(client.get("services/surveys/survey2", mapOf(
            "survey_id" to surveyId,
            "fields" to "id|survey_type|name|headline_html|start_date|end_date|can_i_fill_out|did_i_fill_out|has_final_comment|can_i_view_results|filled_out_count|entitled_count|" +
                "group[course_name]|lecturer[first_name|last_name]|faculty[name]|programme[name]|" +
                "questions[number|level|display_text_html|possible_answers[display_text_html]|allow_comment]"
        )))
        return StudentExtras.safeSurveyDetail(survey)
    }

    @JvmStatic
    fun fetchMailMessage(client: UsosClient, messageId: String): JSONObject {
        require(ID_PATTERN.matches(messageId)) { "Invalid message_id" }
        val message = JSONObject(client.get("services/mailclient/message", mapOf(
            "message_id" to messageId,
            "fields" to "id|subject|status|date|content|content_format|recipients_count|attachments[id|filename|size|description|url]"
        )))
        return StudentExtras.safeMailMessage(message)
    }

    @JvmStatic
    fun fetchMailMessages(client: UsosClient, messageIds: List<String>): JSONArray {
        require(messageIds.isNotEmpty() && messageIds.size <= 20) { "Must request 1..20 message IDs" }
        require(messageIds.all { ID_PATTERN.matches(it) }) { "Invalid message_ids" }
        val raw = JSONObject(client.get("services/mailclient/messages", mapOf(
            "message_ids" to messageIds.joinToString("|"),
            "fields" to "id|subject|status|date|content|content_format|recipients_count|attachments[id|filename|size|description|url]"
        )))
        return StudentExtras.safeMailMessages(raw)
    }

    @JvmStatic
    fun fetchMailAttachments(client: UsosClient, attachmentIds: List<String>): JSONArray {
        require(attachmentIds.isNotEmpty() && attachmentIds.size <= 20) { "Must request 1..20 attachment IDs" }
        require(attachmentIds.all { ID_PATTERN.matches(it) }) { "Invalid attachment_ids" }
        val raw = JSONObject(client.get("services/mailclient/attachments", mapOf(
            "attachment_ids" to attachmentIds.joinToString("|"),
            "fields" to "id|filename|size|description|url"
        )))
        return StudentExtras.safeMailAttachmentsMap(raw)
    }

    @JvmStatic
    fun fetchRemittanceDetail(client: UsosClient, remittanceId: String): JSONObject {
        require(ID_PATTERN.matches(remittanceId)) { "Invalid remittance_id" }
        val item = JSONObject(client.get("services/payments/remittance", mapOf(
            "id" to remittanceId,
            "fields" to PaymentServices.REMITTANCE_DETAIL_FIELDS
        )))
        return PaymentServices.remittanceDetail(item)
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
    fun fetchAttendanceDetail(client: UsosClient, state: JSONObject, listId: String): JSONObject {
        check(AccountRole.lecturer(state)) { "Lecturer account required" }
        require(ID_PATTERN.matches(listId)) { "Invalid list_id" }
        val meta = JSONObject(client.get("services/attendance/attendance_list", mapOf(
            "id" to listId,
            "fields" to "id|course_unit|group_number|date|mode"
        )))
        val entries = JSONArray(client.get("services/attendance/attendance", mapOf(
            "list_id" to listId,
            "fields" to "student|attendance_mode|comment"
        )))
        return LecturerServices.safeAttendanceEntries(meta, entries)
    }

    @JvmStatic
    fun searchTeachers(client: UsosClient, query: String): JSONObject {
        val clean = query.trim()
        require(clean.length in 2..80) { "Search query must be 2..80 characters" }
        val raw = JSONObject(client.get("services/users/search_current_teachers", mapOf(
            "name" to clean,
            "num" to LecturerServices.MAX_STAFF_RESULTS.toString(),
            "start" to "0"
        )))
        val items = raw.optJSONArray("items") ?: JSONArray()
        return LecturerServices.safeStaffDirectory(items, raw.optBoolean("next_page", false), items.length())
    }

    @JvmStatic
    fun fetchStaffProfile(client: UsosClient, userId: String): JSONObject {
        require(ID_PATTERN.matches(userId)) { "Invalid user_id" }
        val user = JSONObject(client.get("services/users/user", mapOf(
            "user_id" to userId,
            "fields" to "id|first_name|last_name|titles|office_hours|room"
        )))
        return LecturerServices.safeStaffDirectory(JSONArray().put(user)).getJSONArray("users").getJSONObject(0)
    }

    @JvmStatic
    fun fetchRegistrationRounds(client: UsosClient, registrationId: String): JSONArray {
        require(ID_PATTERN.matches(registrationId)) { "Invalid registration_id" }
        val rows = JSONArray(client.get("services/registrations/course_registration_rounds", mapOf(
            "registration_id" to registrationId,
            "fields" to "id|name|status|registration_mode|start_date|end_date"
        )))
        val result = JSONArray()
        for (i in 0 until minOf(rows.length(), 100)) {
            val row = rows.optJSONObject(i) ?: continue
            result.put(JSONObject()
                .put("id", StudentServices.text(row, "id"))
                .put("name", ApiLanguage.safe(row.opt("name")))
                .put("status", StudentServices.text(row, "status"))
                .put("registration_mode", StudentServices.text(row, "registration_mode"))
                .put("start_date", StudentServices.text(row, "start_date"))
                .put("end_date", StudentServices.text(row, "end_date")))
        }
        return result
    }

    @JvmStatic
    fun fetchTokenRegistrationDetails(client: UsosClient, registrationId: String): JSONObject {
        require(registrationId.isNotBlank() && registrationId.length <= 128 && registrationId.none { it.isISOControl() }) { "Invalid registration_id" }
        val registration = JSONObject(client.get("services/registrations/token_registration", mapOf(
            "id" to registrationId,
            "fields" to "id|description|message|rounds[id|name|start_date|end_date|selection_limit|is_prioritized|is_only_entitled]"
        )))
        val rows = registration.optJSONArray("rounds") ?: JSONArray()
        val safe = JSONArray()
        for (i in 0 until minOf(rows.length(), 100)) {
            val row = rows.optJSONObject(i) ?: continue
            val item = JSONObject().put("id", StudentServices.text(row, "id"))
                .put("name", ApiLanguage.safe(row.opt("name")))
                .put("start_date", StudentServices.text(row, "start_date"))
                .put("end_date", StudentServices.text(row, "end_date"))
            if (row.opt("selection_limit") is Number) item.put("selection_limit", row.optInt("selection_limit"))
            if (row.opt("is_prioritized") is Boolean) item.put("is_prioritized", row.optBoolean("is_prioritized"))
            if (row.opt("is_only_entitled") is Boolean) item.put("is_only_entitled", row.optBoolean("is_only_entitled"))
            safe.put(item)
        }
        return JSONObject().put("id", StudentServices.text(registration, "id"))
            .put("description", ApiLanguage.safe(registration.opt("description")))
            .put("message", ApiLanguage.safe(registration.opt("message")))
            .put("rounds", safe).put("rounds_available", registration.opt("rounds") is JSONArray)
            .put("rounds_truncated", rows.length() > 100)
    }

    @JvmStatic
    fun fetchExamDetails(client: UsosClient, examId: String): JSONObject {
        require(ID_PATTERN.matches(examId)) { "Invalid exam_id" }
        val exam = JSONObject(client.get("services/exams/exam", mapOf(
            "id" to examId,
            "fields" to "id|name|description|registration_start|registration_end|groups[number|exam_start|exam_end|room[id|number]]"
        )))
        val groups = JSONArray()
        val rows = exam.optJSONArray("groups") ?: JSONArray()
        for (i in 0 until minOf(rows.length(), 100)) {
            val row = rows.optJSONObject(i) ?: continue
            val room = row.optJSONObject("room")
            groups.put(JSONObject()
                .put("number", StudentServices.text(row, "number"))
                .put("exam_start", StudentServices.text(row, "exam_start"))
                .put("exam_end", StudentServices.text(row, "exam_end"))
                .put("room_number", StudentServices.text(room, "number")))
        }
        return JSONObject()
            .put("id", StudentServices.text(exam, "id"))
            .put("name", ApiLanguage.safe(exam.opt("name")))
            .put("description", ApiLanguage.safe(exam.opt("description")))
            .put("registration_start", StudentServices.text(exam, "registration_start"))
            .put("registration_end", StudentServices.text(exam, "registration_end"))
            .put("groups", groups)
            .put("groups_truncated", rows.length() > 100)
    }

    @JvmStatic
    fun searchCourses(client: UsosClient, query: String): JSONObject {
        val clean = query.trim()
        require(clean.length in 2..80) { "Search query must be 2..80 characters" }
        val lang = if (ApiLanguage.current() == "pl") "pl" else "en"
        val raw = JSONObject(client.get("services/courses/search", mapOf(
            "lang" to lang,
            "name" to clean,
            "num" to "10",
            "fields" to "id|name|ects_credits_simplified|fac_id|is_currently_conducted|profile_url|match"
        )))
        val items = raw.optJSONArray("items") ?: JSONArray()
        return LecturerServices.safeCourseSearchResults(items, raw.optBoolean("next_page", false))
    }

    @JvmStatic
    fun fetchCourseDetails(client: UsosClient, courseId: String): JSONObject {
        require(courseId.isNotBlank() && courseId.length <= 128 && courseId.none { it.isISOControl() }) { "Invalid course_id" }
        val course = JSONObject(client.get("services/courses/course", mapOf(
            "course_id" to courseId,
            "fields" to "id|name|lang_id|ects_credits_simplified|description|learning_outcomes|assessment_criteria"
        )))
        return JSONObject().put("id", StudentServices.text(course, "id"))
            .put("name", ApiLanguage.safe(course.opt("name")))
            .put("lang_id", StudentServices.text(course, "lang_id"))
            .put("ects", StudentServices.text(course, "ects_credits_simplified"))
            .put("description", ApiLanguage.safe(course.opt("description")))
            .put("learning_outcomes", ApiLanguage.safe(course.opt("learning_outcomes")))
            .put("assessment_criteria", ApiLanguage.safe(course.opt("assessment_criteria")))
    }

    @JvmStatic
    fun fetchProgrammeDetails(client: UsosClient, programmeId: String): JSONObject {
        require(programmeId.isNotBlank() && programmeId.length <= 128 && programmeId.none { it.isISOControl() }) { "Invalid programme_id" }
        val programme = JSONObject(client.get("services/progs/programme", mapOf(
            "programme_id" to programmeId,
            "fields" to "id|name|faculty[id|name]|mode_of_studies|level_of_studies|duration|professional_status"
        )))
        val faculty = programme.optJSONObject("faculty")
        return JSONObject().put("id", StudentServices.text(programme, "id"))
            .put("name", ApiLanguage.safe(programme.opt("name")))
            .put("faculty_name", ApiLanguage.safe(faculty?.opt("name")))
            .put("mode_of_studies", ApiLanguage.safe(programme.opt("mode_of_studies")))
            .put("level_of_studies", ApiLanguage.safe(programme.opt("level_of_studies")))
            .put("duration", ApiLanguage.safe(programme.opt("duration")))
            .put("professional_status", ApiLanguage.safe(programme.opt("professional_status")))
    }

    @JvmStatic
    fun fetchStudentProgrammeStages(client: UsosClient, studentProgrammeId: String): JSONObject {
        require(studentProgrammeId.isNotBlank() && studentProgrammeId.length <= 128 && studentProgrammeId.none { it.isISOControl() }) { "Invalid student_programme_id" }
        val programme = JSONObject(client.get("services/progs/student_programme", mapOf(
            "student_programme_id" to studentProgrammeId, "fields" to "id|stages"
        )))
        val rows = programme.optJSONArray("stages")
        val stages = JSONArray()
        if (rows != null) for (i in 0 until minOf(rows.length(), 100)) {
            val id = when (val item = rows.opt(i)) {
                is JSONObject -> StudentServices.text(item, "stage_id")
                is String -> item
                else -> ""
            }
            if (id.isNotBlank()) stages.put(JSONObject().put("id", id))
        }
        return JSONObject().put("stages", stages).put("stages_available", rows != null)
            .put("stages_unrecognized", rows != null && stages.length() != minOf(rows.length(), 100))
            .put("stages_truncated", rows != null && rows.length() > 100)
    }

    @JvmStatic
    fun fetchStudyStage(client: UsosClient, stageId: String): JSONObject {
        require(stageId.isNotBlank() && stageId.length <= 128 && stageId.none { it.isISOControl() }) { "Invalid stage_id" }
        val stage = JSONObject(client.get("services/progs/stage", mapOf("id" to stageId, "fields" to "id|name")))
        return JSONObject().put("id", StudentServices.text(stage, "id")).put("name", ApiLanguage.safe(stage.opt("name")))
    }

    @JvmStatic
    fun validCalendarRange(start: String, end: String): Boolean = runCatching {
        val from = LocalDate.parse(start)
        val until = LocalDate.parse(end)
        until.isAfter(from) && !until.isAfter(from.plusMonths(1))
    }.getOrDefault(false)

    @JvmStatic
    fun searchCalendar(client: UsosClient, facultyId: String, start: String, end: String): JSONObject {
        require(facultyId.isNotBlank() && facultyId.length <= 128 && facultyId.none { it.isISOControl() }) { "Invalid faculty_id" }
        require(validCalendarRange(start, end)) { "Invalid calendar date range" }
        val rows = JSONArray(client.get("services/calendar/search", mapOf(
            "faculty_id" to facultyId, "start_date" to start, "end_date" to end,
            "fields" to "id|name|start_date|end_date|type|is_day_off"
        )))
        return JSONObject().put("events", LecturerServices.safeCalendarEvents(rows)).put("truncated", rows.length() > 100)
    }

    @JvmStatic
    fun fetchCalendarEvent(client: UsosClient, eventId: String): JSONObject {
        require(eventId.isNotBlank() && eventId.length <= 128 && eventId.none { it.isISOControl() }) { "Invalid event_id" }
        val event = JSONObject(client.get("services/calendar/calendar_event", mapOf(
            "id" to eventId, "fields" to "id|name|start_date|end_date|type|is_day_off|faculty[id|name]"
        )))
        val result = JSONObject().put("id", StudentServices.text(event, "id"))
            .put("name", ApiLanguage.safe(event.opt("name")))
            .put("start_date", StudentServices.text(event, "start_date"))
            .put("end_date", StudentServices.text(event, "end_date"))
            .put("type", StudentServices.text(event, "type"))
            .put("faculty_name", ApiLanguage.safe(event.optJSONObject("faculty")?.opt("name")))
        if (event.opt("is_day_off") is Boolean) result.put("is_day_off", event.optBoolean("is_day_off"))
        return result
    }

    @JvmStatic
    fun courseText(value: Any?, language: String): String = StudentServices.localized(value, language)
        .replace(Regex("(?i)<br\\s*/?>|</p>|</li>"), "\n")
        .replace(Regex("</?[A-Za-z][^>]*>"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&").trim()

    @JvmStatic
    fun fetchBuildingRooms(client: UsosClient, buildingId: String): JSONArray {
        require(ID_PATTERN.matches(buildingId)) { "Invalid building_id" }
        val building = JSONObject(client.get("services/geo/building2", mapOf(
            "building_id" to buildingId, "fields" to "id|rooms[id|number|type|capacity]"
        )))
        val rows = building.optJSONArray("rooms") ?: JSONArray()
        val result = JSONArray()
        for (i in 0 until minOf(rows.length(), 100)) {
            val room = rows.optJSONObject(i) ?: continue
            result.put(JSONObject().put("id", StudentServices.text(room, "id"))
                .put("number", StudentServices.text(room, "number"))
                .put("type", StudentServices.text(room, "type"))
                .put("capacity", room.optInt("capacity", 0)))
        }
        return result
    }

    @JvmStatic
    fun fetchRoomSchedule(client: UsosClient, roomId: String, startDate: String): JSONObject {
        val cleanId = roomId.trim()
        require(ID_PATTERN.matches(cleanId)) { "Invalid room_id" }
        require(runCatching { LocalDate.parse(startDate) }.isSuccess) { "Invalid start date" }
        val roomMeta = JSONObject(client.get("services/geo/room", mapOf(
            "room_id" to cleanId,
            "fields" to "id|number|type|capacity|building[id|name]"
        )))
        val lessons = JSONArray(client.get("services/tt/room", mapOf(
            "room_id" to cleanId,
            "start" to startDate,
            "days" to "7",
            "fields" to "start_time|end_time|name|classtype_name"
        )))
        return LecturerServices.safeRoomSchedule(roomMeta, lessons)
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

    @JvmStatic
    fun fetchThesisDetail(client: UsosClient, thesisId: String): JSONObject {
        require(ID_PATTERN.matches(thesisId)) { "Invalid thesis_id" }
        val raw = JSONObject(client.get("services/theses/thesis", mapOf(
            "ths_id" to thesisId,
            "fields" to "id|type|title|titles|authors[id|first_name|last_name]|supervisors[id|first_name|last_name]|faculty[id|name]"
        )))
        return LecturerServices.safeThesisDetail(raw)
    }

    @JvmStatic
    fun fetchDormitoryDetail(client: UsosClient, dormitoryId: String): JSONObject {
        require(ID_PATTERN.matches(dormitoryId)) { "Invalid dormitory_id" }
        val raw = JSONObject(client.get("services/housing/dormitory", mapOf(
            "dormitory_id" to dormitoryId,
            "fields" to "id|name|short_name"
        )))
        return LecturerServices.safeDormitoryDetail(raw)
    }

    @JvmStatic
    fun fetchAllocationPeriodDetail(client: UsosClient, periodId: String): JSONObject {
        require(ID_PATTERN.matches(periodId)) { "Invalid allocation_period_id" }
        val raw = JSONObject(client.get("services/housing/allocation_period", mapOf(
            "allocation_period_id" to periodId,
            "fields" to "id|term_id|status_name|status_desc|start_date|end_date"
        )))
        return LecturerServices.safeAllocationPeriodDetail(raw)
    }

    @JvmStatic
    fun fetchGuidePage(client: UsosClient, pageId: String): JSONObject {
        require(ID_PATTERN.matches(pageId)) { "Invalid page_id" }
        val lang = if (ApiLanguage.current() == "pl") "pl" else "en"
        val raw = JSONObject(client.get("services/guide/page", mapOf(
            "page_id" to pageId,
            "lang" to lang,
            "fields" to "id|title|entries[id|title|content]"
        )))
        return LecturerServices.safeGuidePage(raw)
    }

    @JvmStatic
    fun fetchCourseEdition(client: UsosClient, courseId: String, termId: String): JSONObject {
        require(ID_PATTERN.matches(courseId)) { "Invalid course_id" }
        require(ID_PATTERN.matches(termId)) { "Invalid term_id" }
        val raw = JSONObject(client.get("services/courses/course_edition", mapOf(
            "course_id" to courseId,
            "term_id" to termId,
            "fields" to "coordinators[id|first_name|last_name]|lecturers[id|first_name|last_name]"
        )))
        raw.put("course_id", courseId).put("term_id", termId)
        return LecturerServices.safeCourseEdition(raw)
    }

    @JvmStatic
    fun fetchCourseUnit(client: UsosClient, unitId: String): JSONObject {
        require(ID_PATTERN.matches(unitId)) { "Invalid unit_id" }
        val raw = JSONObject(client.get("services/courses/unit", mapOf(
            "unit_id" to unitId,
            "fields" to "id|classtype_id|classtype_name"
        )))
        return LecturerServices.safeCourseUnit(raw)
    }

}
