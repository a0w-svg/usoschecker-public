package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Typed parsers and privacy-minimized cache serializers for lecturer services
 * and read-only student service gaps documented in the bundled USOS API catalog.
 */
object LecturerServices {
    const val MAX_PARTICIPANTS = 200
    const val MAX_STAFF_RESULTS = 20
    const val MAX_NODES = 100

    class ClassGroup(source: JSONObject, language: String? = ApiLanguage.current()) {
        @JvmField val courseUnitId: String = StudentServices.text(source, "course_unit_id")
        @JvmField val groupNumber: String = StudentServices.text(source, "group_number")
        @JvmField val courseId: String = StudentServices.text(source, "course_id")
        @JvmField val courseName: String = StudentServices.localized(source.opt("course_name"), language)
        @JvmField val classType: String = StudentServices.localized(source.opt("class_type"), language)
        @JvmField val termId: String = StudentServices.text(source, "term_id")
        @JvmField val groupUrl: String = StudentServices.text(source, "group_url")
            .takeIf { WebLinks.isWebUrl(it) }.orEmpty()

        @Throws(JSONException::class)
        fun json(): JSONObject = JSONObject()
            .put("course_unit_id", courseUnitId)
            .put("group_number", groupNumber)
            .put("course_id", courseId)
            .put("course_name", courseName)
            .put("class_type", classType)
            .put("term_id", termId)
            .put("group_url", groupUrl)
    }

    /** Minimal participant record: only ID, name, and student number may enter the encrypted cache. */
    class Participant(source: JSONObject) {
        @JvmField val id: String = StudentServices.text(source, "id")
        @JvmField val firstName: String = StudentServices.text(source, "first_name").trim()
        @JvmField val lastName: String = StudentServices.text(source, "last_name").trim()
        @JvmField val studentNumber: String = StudentServices.text(source, "student_number").trim()

        fun displayName(): String = listOf(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
            .ifEmpty { if (id.isNotEmpty()) "ID: $id" else "—" }

        @Throws(JSONException::class)
        fun json(): JSONObject = JSONObject()
            .put("id", id)
            .put("first_name", firstName)
            .put("last_name", lastName)
            .put("student_number", studentNumber)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeGroups(source: JSONObject): JSONArray {
        val result = JSONArray()
        val groupsObj = source.optJSONObject("groups") ?: source
        val terms = groupsObj.keys().asSequence().toList().sortedDescending()
        for (term in terms) {
            val arr = groupsObj.optJSONArray(term) ?: continue
            for (i in 0 until arr.length()) {
                val raw = arr.optJSONObject(i) ?: continue
                val url = StudentServices.text(raw, "group_url").takeIf { WebLinks.isWebUrl(it) }.orEmpty()
                result.put(
                    JSONObject()
                        .put("course_unit_id", StudentServices.text(raw, "course_unit_id"))
                        .put("group_number", StudentServices.text(raw, "group_number"))
                        .put("course_id", StudentServices.text(raw, "course_id"))
                        .put("course_name", ApiLanguage.safe(raw.opt("course_name")))
                        .put("class_type", ApiLanguage.safe(raw.opt("class_type")))
                        .put("term_id", StudentServices.text(raw, "term_id").ifEmpty { term })
                        .put("group_url", url)
                )
            }
        }
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeCoordinatedCourses(source: JSONObject): JSONArray {
        val result = JSONArray()
        val editions = source.optJSONObject("course_editions") ?: source
        val terms = editions.keys().asSequence().toList().sortedDescending()
        for (term in terms) {
            val arr = editions.optJSONArray(term) ?: continue
            for (i in 0 until arr.length()) {
                val raw = arr.optJSONObject(i) ?: continue
                val course = raw.optJSONObject("course")
                val termObj = raw.optJSONObject("term")
                result.put(
                    JSONObject()
                        .put("course_id", StudentServices.text(course, "id").ifEmpty { StudentServices.text(raw, "course_id") })
                        .put("course_name", ApiLanguage.safe(course?.opt("name") ?: raw.opt("course_name")))
                        .put("term_id", StudentServices.text(termObj, "id").ifEmpty { term })
                        .put("term_name", ApiLanguage.safe(termObj?.opt("name")))
                )
            }
        }
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeGroupDetail(source: JSONObject): JSONObject {
        val participantsRaw = source.optJSONArray("participants") ?: JSONArray()
        val participants = JSONArray()
        val total = participantsRaw.length()
        val limit = minOf(total, MAX_PARTICIPANTS)
        for (i in 0 until limit) {
            val p = participantsRaw.optJSONObject(i) ?: continue
            participants.put(Participant(p).json())
        }
        val lecturersRaw = source.optJSONArray("lecturers") ?: JSONArray()
        val lecturers = JSONArray()
        for (i in 0 until minOf(lecturersRaw.length(), 50)) {
            val l = lecturersRaw.optJSONObject(i) ?: continue
            lecturers.put(
                JSONObject()
                    .put("id", StudentServices.text(l, "id"))
                    .put("first_name", StudentServices.text(l, "first_name").trim())
                    .put("last_name", StudentServices.text(l, "last_name").trim())
            )
        }
        return JSONObject()
            .put("course_unit_id", StudentServices.text(source, "course_unit_id"))
            .put("group_number", StudentServices.text(source, "group_number"))
            .put("course_id", StudentServices.text(source, "course_id"))
            .put("course_name", ApiLanguage.safe(source.opt("course_name")))
            .put("class_type", ApiLanguage.safe(source.opt("class_type")))
            .put("term_id", StudentServices.text(source, "term_id"))
            .put("participants", participants)
            .put("participantsTotal", total)
            .put("participantsTruncated", total > limit)
            .put("lecturers", lecturers)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeLecturerTests(source: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until minOf(source.length(), MAX_NODES)) {
            val row = source.optJSONObject(i) ?: continue
            val edition = row.optJSONObject("course_edition")
            val course = edition?.optJSONObject("course")
            val term = edition?.optJSONObject("term")
            val root = row.optJSONObject("root")
            val subnodes = JSONArray()
            val deep = root?.optJSONArray("subnodes_deep") ?: root?.optJSONArray("subnodes") ?: JSONArray()
            for (j in 0 until minOf(deep.length(), MAX_NODES)) {
                val node = deep.optJSONObject(j) ?: continue
                subnodes.put(
                    JSONObject()
                        .put("id", StudentServices.text(node, "id"))
                        .put("parent_id", StudentServices.text(node, "parent_id"))
                        .put("order", node.optInt("order", j))
                        .put("name", ApiLanguage.safe(node.opt("name")))
                        .put("type", StudentServices.text(node, "type"))
                        .put("visible_for_students", node.optBoolean("visible_for_students", false))
                )
            }
            result.put(
                JSONObject()
                    .put("course_id", StudentServices.text(course, "id"))
                    .put("course_name", ApiLanguage.safe(course?.opt("name")))
                    .put("term_id", StudentServices.text(term, "id"))
                    .put("term_name", ApiLanguage.safe(term?.opt("name")))
                    .put("root_id", StudentServices.text(root, "id"))
                    .put("root_name", ApiLanguage.safe(root?.opt("name")))
                    .put("nodes", subnodes)
                    .put("nodesTruncated", deep.length() > MAX_NODES)
            )
        }
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeNodeDetail(source: JSONObject, statsSource: JSONObject? = null): JSONObject {
        val taskDetails = source.optJSONObject("task_node_details")
        val gradeDetails = source.optJSONObject("grade_node_details")
        val permissions = source.optJSONObject("my_permissions")
        val safePerms = JSONObject()
        if (permissions != null) {
            for (k in arrayOf("can_view", "can_edit", "can_Give_points", "can_give_points", "can_give_grades")) {
                if (permissions.has(k)) safePerms.put(k.lowercase(), permissions.optBoolean(k, false))
            }
        }
        val statsObj = statsSource ?: source.optJSONObject("stats")
        val histogram = JSONArray()
        val rawHist = statsObj?.optJSONArray("histogram")
        if (rawHist != null) {
            for (i in 0 until minOf(rawHist.length(), 50)) {
                val item = rawHist.optJSONObject(i) ?: continue
                histogram.put(
                    JSONObject()
                        .put("label", StudentServices.text(item, "value").ifEmpty { StudentServices.text(item, "label") })
                        .put("count", item.optInt("number", item.optInt("count", 0)))
                )
            }
        }
        return JSONObject()
            .put("id", StudentServices.text(source, "id"))
            .put("parent_id", StudentServices.text(source, "parent_id"))
            .put("name", ApiLanguage.safe(source.opt("name")))
            .put("description", ApiLanguage.safe(source.opt("description")))
            .put("type", StudentServices.text(source, "type"))
            .put("visible_for_students", source.optBoolean("visible_for_students", false))
            .put("points_min", taskDetails?.optDouble("points_min", 0.0) ?: 0.0)
            .put("points_max", taskDetails?.optDouble("points_max", 0.0) ?: 0.0)
            .put("points_precision", taskDetails?.optInt("points_precision", 0) ?: 0)
            .put("grade_type_id", StudentServices.text(gradeDetails?.optJSONObject("grade_type"), "id"))
            .put("permissions", safePerms)
            .put("histogram", histogram)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeMeetings(meetings: JSONArray, datesByMeeting: Map<String, JSONArray>): JSONArray {
        val result = JSONArray()
        for (i in 0 until minOf(meetings.length(), 50)) {
            val m = meetings.optJSONObject(i) ?: continue
            val id = StudentServices.text(m, "id")
            val rawDates = datesByMeeting[id] ?: JSONArray()
            val safeDates = JSONArray()
            for (j in 0 until minOf(rawDates.length(), 50)) {
                val d = rawDates.optJSONObject(j) ?: continue
                safeDates.put(
                    JSONObject()
                        .put("id", StudentServices.text(d, "id"))
                        .put("start_time", StudentServices.text(d, "start_time"))
                        .put("end_time", StudentServices.text(d, "end_time"))
                        .put("seats_limit", d.optInt("seats_limit", 0))
                        .put("reserve_seats_limit", d.optInt("reserve_seats_limit", 0))
                        .put("comment", ApiLanguage.safe(d.opt("comment")))
                )
            }
            result.put(
                JSONObject()
                    .put("id", id)
                    .put("name", ApiLanguage.safe(m.opt("name")))
                    .put("comment", ApiLanguage.safe(m.opt("comment")))
                    .put("open", m.optBoolean("open", false))
                    .put("dates", safeDates)
            )
        }
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeStaffDirectory(items: JSONArray, nextPage: Boolean = false, total: Int = items.length()): JSONObject {
        val users = JSONArray()
        val limit = minOf(items.length(), MAX_STAFF_RESULTS)
        for (i in 0 until limit) {
            val raw = items.optJSONObject(i) ?: continue
            val user = raw.optJSONObject("user") ?: raw
            val titles = user.optJSONObject("titles")
            val prefix = StudentServices.text(titles, "before")
            val suffix = StudentServices.text(titles, "after")
            users.put(
                JSONObject()
                    .put("id", StudentServices.text(user, "id").ifEmpty { StudentServices.text(raw, "user_id") })
                    .put("first_name", StudentServices.text(user, "first_name").trim().ifEmpty {
                        StudentServices.text(raw, "match").replace(Regex("</?b>", RegexOption.IGNORE_CASE), "").trim()
                    })
                    .put("last_name", StudentServices.text(user, "last_name").trim())
                    .put("title_before", prefix)
                    .put("title_after", suffix)
                    .put("office_hours", ApiLanguage.safe(user.opt("office_hours")))
                    .put("room", StudentServices.text(user, "room"))
            )
        }
        return JSONObject()
            .put("users", users)
            .put("hasMore", nextPage || items.length() > limit || total > limit)
            .put("total", maxOf(total, items.length()))
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeRegistrations(registrations: JSONArray, cart: JSONArray): JSONObject {
        val regs = JSONArray()
        for (i in 0 until minOf(registrations.length(), 50)) {
            val r = registrations.optJSONObject(i) ?: continue
            regs.put(
                JSONObject()
                    .put("id", StudentServices.text(r, "id"))
                    .put("description", ApiLanguage.safe(r.opt("description")))
                    .put("message", ApiLanguage.safe(r.opt("message")))
                    .put("type", StudentServices.text(r, "type"))
                    .put("status", StudentServices.text(r, "status"))
                    .put("is_linkage_required", r.optBoolean("is_linkage_required", false))
            )
        }
        val cartItems = JSONArray()
        for (i in 0 until minOf(cart.length(), 100)) {
            val c = cart.optJSONObject(i) ?: continue
            val course = c.optJSONObject("course")
            val term = c.optJSONObject("term")
            cartItems.put(
                JSONObject()
                    .put("course_id", StudentServices.text(course, "id"))
                    .put("course_name", ApiLanguage.safe(course?.opt("name")))
                    .put("term_id", StudentServices.text(term, "id"))
                    .put("user_registration_status", StudentServices.text(c, "user_registration_status"))
                    .put("registration_status", StudentServices.text(c, "registration_status"))
                    .put("is_registration_valid", c.optBoolean("is_registration_valid", false))
                    .put("registrations_count", c.optInt("registrations_count", 0))
                    .put("limits", c.optInt("limits", 0))
            )
        }
        return JSONObject().put("registrations", regs).put("cart", cartItems)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeStudentExams(exams: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until minOf(exams.length(), 50)) {
            val e = exams.optJSONObject(i) ?: continue
            val course = e.optJSONObject("course")
            val term = e.optJSONObject("term")
            result.put(
                JSONObject()
                    .put("id", StudentServices.text(e, "id"))
                    .put("code", StudentServices.text(e, "code"))
                    .put("course_id", StudentServices.text(course, "id"))
                    .put("course_name", ApiLanguage.safe(course?.opt("name")))
                    .put("term_id", StudentServices.text(term, "id"))
                    .put("registration_start", StudentServices.text(e, "registration_start"))
                    .put("registration_end", StudentServices.text(e, "registration_end"))
            )
        }
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeCalendarEvents(events: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until minOf(events.length(), 100)) {
            val ev = events.optJSONObject(i) ?: continue
            result.put(
                JSONObject()
                    .put("id", StudentServices.text(ev, "id"))
                    .put("name", ApiLanguage.safe(ev.opt("name")))
                    .put("start_date", StudentServices.text(ev, "start_date"))
                    .put("end_date", StudentServices.text(ev, "end_date"))
                    .put("type", StudentServices.text(ev, "type"))
                    .put("is_day_off", ev.optBoolean("is_day_off", false))
            )
        }
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeProgrammeStages(programmes: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until minOf(programmes.length(), 20)) {
            val p = programmes.optJSONObject(i) ?: continue
            val prog = p.optJSONObject("programme")
            result.put(
                JSONObject()
                    .put("id", StudentServices.text(p, "id"))
                    .put("status", StudentServices.text(p, "status"))
                    .put("admission_date", StudentServices.text(p, "admission_date"))
                    .put("programme_id", StudentServices.text(prog, "id"))
                    .put("programme_name", ApiLanguage.safe(prog?.opt("name") ?: prog?.opt("description")))
            )
        }
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeAttendanceLists(lists: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until minOf(lists.length(), 100)) {
            val row = lists.optJSONObject(i) ?: continue
            val unitObj = row.optJSONObject("course_unit")
            val ownerObj = row.optJSONObject("owner")
            val ownerName = if (ownerObj != null) {
                listOf(StudentServices.text(ownerObj, "first_name").trim(), StudentServices.text(ownerObj, "last_name").trim())
                    .filter { it.isNotEmpty() }.joinToString(" ")
            } else {
                StudentServices.text(row, "owner_name")
            }
            result.put(
                JSONObject()
                    .put("id", StudentServices.text(row, "id"))
                    .put("course_unit_id", StudentServices.text(unitObj, "id").ifEmpty { StudentServices.text(row, "course_unit_id") })
                    .put("course_name", ApiLanguage.safe(row.opt("course_name") ?: unitObj?.opt("course_name")))
                    .put("group_number", StudentServices.text(row, "group_number"))
                    .put("date", StudentServices.text(row, "date"))
                    .put("mode", StudentServices.text(row, "mode"))
                    .put("owner_id", StudentServices.text(ownerObj, "id").ifEmpty { StudentServices.text(row, "owner_id") })
                    .put("owner_name", ownerName)
            )
        }
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeAttendanceEntries(listMeta: JSONObject?, entries: JSONArray): JSONObject {
        val safeEntries = JSONArray()
        val total = entries.length()
        val limit = minOf(total, MAX_PARTICIPANTS)
        for (i in 0 until limit) {
            val row = entries.optJSONObject(i) ?: continue
            val st = row.optJSONObject("student")
            val studentId = StudentServices.text(st, "id").ifEmpty { StudentServices.text(row, "student_id") }
            val firstName = StudentServices.text(st, "first_name").ifEmpty { StudentServices.text(row, "first_name") }.trim()
            val lastName = StudentServices.text(st, "last_name").ifEmpty { StudentServices.text(row, "last_name") }.trim()
            val studentNum = StudentServices.text(st, "student_number").ifEmpty { StudentServices.text(row, "student_number") }.trim()
            safeEntries.put(
                JSONObject()
                    .put("student_id", studentId)
                    .put("first_name", firstName)
                    .put("last_name", lastName)
                    .put("student_number", studentNum)
                    .put("attendance_mode", StudentServices.text(row, "attendance_mode"))
                    .put("comment", StudentServices.text(row, "comment").take(200))
            )
        }
        val unitObj = listMeta?.optJSONObject("course_unit")
        return JSONObject()
            .put("id", StudentServices.text(listMeta, "id"))
            .put("course_unit_id", StudentServices.text(unitObj, "id").ifEmpty { StudentServices.text(listMeta, "course_unit_id") })
            .put("group_number", StudentServices.text(listMeta, "group_number"))
            .put("date", StudentServices.text(listMeta, "date"))
            .put("mode", StudentServices.text(listMeta, "mode"))
            .put("entries", safeEntries)
            .put("entriesTotal", total)
            .put("entriesTruncated", total > limit)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeLecturerReports(surveys: JSONArray, signableTheses: JSONArray): JSONObject {
        val safeSurveys = JSONArray()
        for (i in 0 until minOf(surveys.length(), 50)) {
            val s = surveys.optJSONObject(i) ?: continue
            val grp = s.optJSONObject("group")
            safeSurveys.put(
                JSONObject()
                    .put("id", StudentServices.text(s, "id"))
                    .put("name", ApiLanguage.safe(s.opt("name")))
                    .put("headline", ApiLanguage.safe(s.opt("headline_html")))
                    .put("start_date", StudentServices.text(s, "start_date"))
                    .put("end_date", StudentServices.text(s, "end_date"))
                    .put("number_of_answers", s.optInt("number_of_answers", 0))
                    .put("course_unit_id", StudentServices.text(grp, "course_unit_id").ifEmpty { StudentServices.text(s, "course_unit_id") })
                    .put("group_number", StudentServices.text(grp, "group_number").ifEmpty { StudentServices.text(s, "group_number") })
                    .put("course_name", ApiLanguage.safe(grp?.opt("course_name") ?: s.opt("course_name")))
            )
        }
        val safeTheses = JSONArray()
        for (i in 0 until minOf(signableTheses.length(), 50)) {
            val t = signableTheses.optJSONObject(i) ?: continue
            safeTheses.put(
                JSONObject()
                    .put("id", StudentServices.text(t, "id"))
                    .put("type", StudentServices.text(t, "type"))
                    .put("title", ApiLanguage.safe(t.opt("title") ?: t.opt("titles")))
                    .put("status", StudentServices.text(t, "status"))
            )
        }
        return JSONObject()
            .put("surveys", safeSurveys)
            .put("signableTheses", safeTheses)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeStudyGroupsAndCredits(usedEcts: String, primaryGroups: JSONArray, customGroups: JSONArray): JSONObject {
        val safePrimary = JSONArray()
        for (i in 0 until minOf(primaryGroups.length(), 50)) {
            val g = primaryGroups.optJSONObject(i) ?: continue
            val prog = g.optJSONObject("programme")
            val stage = g.optJSONObject("stage")
            val term = g.optJSONObject("term")
            safePrimary.put(
                JSONObject()
                    .put("id", StudentServices.text(g, "id"))
                    .put("number", StudentServices.text(g, "number"))
                    .put("description", ApiLanguage.safe(g.opt("description")))
                    .put("programme_id", StudentServices.text(prog, "id").ifEmpty { StudentServices.text(g, "programme_id") })
                    .put("programme_name", ApiLanguage.safe(prog?.opt("name") ?: prog?.opt("description")))
                    .put("stage_id", StudentServices.text(stage, "id").ifEmpty { StudentServices.text(g, "stage_id") })
                    .put("term_id", StudentServices.text(term, "id").ifEmpty { StudentServices.text(g, "term_id") })
            )
        }
        val safeCustom = JSONArray()
        for (i in 0 until minOf(customGroups.length(), 50)) {
            val g = customGroups.optJSONObject(i) ?: continue
            safeCustom.put(
                JSONObject()
                    .put("id", StudentServices.text(g, "id"))
                    .put("name", StudentServices.text(g, "name"))
                    .put("description", StudentServices.text(g, "description").take(200))
                    .put("expiration_date", StudentServices.text(g, "expiration_date"))
            )
        }
        return JSONObject()
            .put("usedEcts", usedEcts)
            .put("primaryGroups", safePrimary)
            .put("customGroups", safeCustom)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeMobilities(items: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until minOf(items.length(), 50)) {
            val m = items.optJSONObject(i) ?: continue
            val cat = m.optJSONObject("mobility_category")
            val prog = m.optJSONObject("programme")
            val inst = m.optJSONObject("host_institution") ?: m.optJSONObject("institution")
            val state = m.optJSONObject("state")
            result.put(
                JSONObject()
                    .put("id", StudentServices.text(m, "id"))
                    .put("category", ApiLanguage.safe(cat?.opt("name") ?: m.opt("category")))
                    .put("programme", ApiLanguage.safe(prog?.opt("name") ?: m.opt("programme")))
                    .put("institution", ApiLanguage.safe(inst?.opt("name") ?: m.opt("institution")))
                    .put("start_date", StudentServices.text(m, "start_date").ifEmpty { StudentServices.text(m, "planned_start_date") })
                    .put("end_date", StudentServices.text(m, "end_date").ifEmpty { StudentServices.text(m, "planned_end_date") })
                    .put("state", ApiLanguage.safe(state?.opt("name") ?: m.opt("state")))
            )
        }
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safePlacementAndSlips(tests: JSONArray, slips: JSONArray, slipRoles: JSONArray): JSONObject {
        val safeTests = JSONArray()
        for (i in 0 until minOf(tests.length(), 50)) {
            val row = tests.optJSONObject(i) ?: continue
            val t = row.optJSONObject("test") ?: row
            val res = row.optJSONObject("result")
            safeTests.put(
                JSONObject()
                    .put("id", StudentServices.text(t, "id"))
                    .put("name", ApiLanguage.safe(t.opt("name")))
                    .put("description", ApiLanguage.safe(t.opt("description")))
                    .put("max_score", StudentServices.text(t, "max_score"))
                    .put("score", StudentServices.text(res, "score").ifEmpty { StudentServices.text(row, "score") })
                    .put("level", StudentServices.text(res, "level").ifEmpty { StudentServices.text(row, "level") })
            )
        }
        val safeSlips = JSONArray()
        for (i in 0 until minOf(slips.length(), 50)) {
            val s = slips.optJSONObject(i) ?: continue
            val q = s.optJSONObject("question")
            val slipObj = s.optJSONObject("slip")
            safeSlips.put(
                JSONObject()
                    .put("id", StudentServices.text(s, "id"))
                    .put("slip_id", StudentServices.text(slipObj, "id").ifEmpty { StudentServices.text(s, "slip_id") })
                    .put("question_id", StudentServices.text(q, "id").ifEmpty { StudentServices.text(s, "question_id") })
                    .put("question", StudentServices.text(q, "question").ifEmpty { StudentServices.text(s, "question") }.take(300))
            )
        }
        val safeRoles = JSONArray()
        for (i in 0 until minOf(slipRoles.length(), 20)) {
            val r = slipRoles.opt(i)?.toString()?.trim().orEmpty()
            if (r.isNotEmpty()) safeRoles.put(r)
        }
        return JSONObject()
            .put("placementTests", safeTests)
            .put("slipsToAnswer", safeSlips)
            .put("slipRoles", safeRoles)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeCampusGuide(chapters: JSONArray, dorms: JSONArray, buildings: JSONArray, allocPeriods: JSONArray = JSONArray()): JSONObject {
        val safeChapters = JSONArray()
        for (i in 0 until minOf(chapters.length(), 30)) {
            val ch = chapters.optJSONObject(i) ?: continue
            val pagesRaw = ch.optJSONArray("pages") ?: JSONArray()
            val pages = JSONArray()
            for (j in 0 until minOf(pagesRaw.length(), 30)) {
                val p = pagesRaw.optJSONObject(j) ?: continue
                val entriesRaw = p.optJSONArray("entries") ?: JSONArray()
                val entries = JSONArray()
                for (k in 0 until minOf(entriesRaw.length(), 20)) {
                    val e = entriesRaw.optJSONObject(k) ?: continue
                    entries.put(
                        JSONObject()
                            .put("id", StudentServices.text(e, "id"))
                            .put("title", ApiLanguage.safe(e.opt("title")))
                    )
                }
                pages.put(
                    JSONObject()
                        .put("id", StudentServices.text(p, "id"))
                        .put("title", ApiLanguage.safe(p.opt("title")))
                        .put("entries", entries)
                )
            }
            safeChapters.put(
                JSONObject()
                    .put("id", StudentServices.text(ch, "id"))
                    .put("title", ApiLanguage.safe(ch.opt("title")))
                    .put("pages", pages)
            )
        }
        val safeDorms = JSONArray()
        for (i in 0 until minOf(dorms.length(), 30)) {
            val d = dorms.optJSONObject(i) ?: continue
            safeDorms.put(
                JSONObject()
                    .put("id", StudentServices.text(d, "id"))
                    .put("name", ApiLanguage.safe(d.opt("name")))
                    .put("short_name", ApiLanguage.safe(d.opt("short_name")))
                    .put("address", StudentServices.text(d, "address"))
                    .put("description", ApiLanguage.safe(d.opt("description")))
            )
        }
        val safeBuildings = JSONArray()
        for (i in 0 until minOf(buildings.length(), 40)) {
            val b = buildings.optJSONObject(i) ?: continue
            safeBuildings.put(
                JSONObject()
                    .put("id", StudentServices.text(b, "id"))
                    .put("name", ApiLanguage.safe(b.opt("name")))
                    .put("postal_address", StudentServices.text(b, "postal_address"))
                    .put("campus_name", ApiLanguage.safe(b.opt("campus_name")))
            )
        }
        val safePeriods = JSONArray()
        for (i in 0 until minOf(allocPeriods.length(), 20)) {
            val p = allocPeriods.optJSONObject(i) ?: continue
            safePeriods.put(
                JSONObject()
                    .put("id", StudentServices.text(p, "id"))
                    .put("name", ApiLanguage.safe(p.opt("name")))
                    .put("description", ApiLanguage.safe(p.opt("description")))
            )
        }
        return JSONObject()
            .put("guideChapters", safeChapters)
            .put("dormitories", safeDorms)
            .put("buildings", safeBuildings)
            .put("allocationPeriods", safePeriods)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeCommunications(
        statements: JSONArray,
        notifications: JSONArray,
        messages: JSONArray,
        caseCategories: JSONObject,
        surveys: JSONArray = JSONArray()
    ): JSONObject {
        val safeStatements = JSONArray()
        for (i in 0 until minOf(statements.length(), 30)) {
            val s = statements.optJSONObject(i) ?: continue
            safeStatements.put(
                JSONObject()
                    .put("id", StudentServices.text(s, "id"))
                    .put("subject", StudentServices.text(s, "subject").ifEmpty { ApiLanguage.text(s.opt("title")) }.take(200))
                    .put("content", StudentServices.text(s, "content").take(300))
                    .put("expiration_date", StudentServices.text(s, "expiration_date"))
            )
        }
        val safeNotifications = JSONArray()
        for (i in 0 until minOf(notifications.length(), 30)) {
            val n = notifications.optJSONObject(i) ?: continue
            safeNotifications.put(
                JSONObject()
                    .put("id", StudentServices.text(n, "id"))
                    .put("type", StudentServices.text(n, "type"))
                    .put("created", StudentServices.text(n, "created"))
                    .put("summary", ApiLanguage.safe(n.opt("summary") ?: n.opt("title") ?: n.opt("message")))
            )
        }
        val safeMessages = JSONArray()
        for (i in 0 until minOf(messages.length(), 30)) {
            val m = messages.optJSONObject(i) ?: continue
            safeMessages.put(
                JSONObject()
                    .put("id", StudentServices.text(m, "id"))
                    .put("subject", StudentServices.text(m, "subject").take(200))
                    .put("status", StudentServices.text(m, "status"))
                    .put("date", StudentServices.text(m, "date").ifEmpty { StudentServices.text(m, "created") })
            )
        }
        val safeCategories = JSONArray()
        val catKeys = caseCategories.keys().asSequence().toList().sorted()
        for (key in catKeys.take(30)) {
            val c = caseCategories.optJSONObject(key)
            if (c != null) {
                safeCategories.put(
                    JSONObject()
                        .put("id", StudentServices.text(c, "id").ifEmpty { key })
                        .put("name", ApiLanguage.safe(c.opt("name") ?: c.opt("description")))
                )
            } else {
                safeCategories.put(
                    JSONObject()
                        .put("id", key)
                        .put("name", ApiLanguage.safe(caseCategories.opt(key)))
                )
            }
        }
        return JSONObject()
            .put("statements", safeStatements)
            .put("notifications", safeNotifications)
            .put("messages", safeMessages)
            .put("caseCategories", safeCategories)
            .put("surveys", StudentExtras.safeSurveys(surveys))
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeCourseSearchResults(items: JSONArray, nextPage: Boolean = false): JSONObject {
        val courses = JSONArray()
        val limit = minOf(items.length(), 25)
        for (i in 0 until limit) {
            val row = items.optJSONObject(i) ?: continue
            val c = row.optJSONObject("course") ?: row
            val fac = c.optJSONObject("fac_id") ?: c.optJSONObject("faculty")
            val matchRaw = StudentServices.text(row, "match").replace(Regex("<[^>]*>"), "").trim()
            val url = StudentServices.text(c, "profile_url").takeIf { WebLinks.isWebUrl(it) }.orEmpty()
            courses.put(
                JSONObject()
                    .put("id", StudentServices.text(c, "id").ifEmpty { StudentServices.text(row, "course_id") })
                    .put("name", ApiLanguage.safe(c.opt("name") ?: matchRaw))
                    .put("match", matchRaw)
                    .put("ects", StudentServices.text(c, "ects_credits_simplified").ifEmpty { StudentServices.text(c, "ects") })
                    .put("fac_id", StudentServices.text(fac, "id").ifEmpty { StudentServices.text(c, "fac_id") })
                    .put("is_currently_conducted", c.optBoolean("is_currently_conducted", false))
                    .put("profile_url", url)
            )
        }
        return JSONObject()
            .put("courses", courses)
            .put("hasMore", nextPage || items.length() > limit)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun safeRoomSchedule(roomMeta: JSONObject?, timetable: JSONArray): JSONObject {
        val building = roomMeta?.optJSONObject("building")
        val lessons = JSONArray()
        for (i in 0 until minOf(timetable.length(), 60)) {
            val l = timetable.optJSONObject(i) ?: continue
            lessons.put(
                JSONObject()
                    .put("start_time", StudentServices.text(l, "start_time"))
                    .put("end_time", StudentServices.text(l, "end_time"))
                    .put("name", ApiLanguage.safe(l.opt("name")))
                    .put("classtype_name", ApiLanguage.safe(l.opt("classtype_name")))
            )
        }
        return JSONObject()
            .put("id", StudentServices.text(roomMeta, "id"))
            .put("number", StudentServices.text(roomMeta, "number"))
            .put("type", StudentServices.text(roomMeta, "type"))
            .put("capacity", roomMeta?.optInt("capacity", 0) ?: 0)
            .put("building_id", StudentServices.text(building, "id").ifEmpty { StudentServices.text(roomMeta, "building_id") })
            .put("building_name", ApiLanguage.safe(building?.opt("name") ?: roomMeta?.opt("building_name")))
            .put("lessons", lessons)
    }
}
