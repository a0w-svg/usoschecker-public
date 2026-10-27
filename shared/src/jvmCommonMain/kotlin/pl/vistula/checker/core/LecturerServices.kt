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
                    .put("id", StudentServices.text(user, "id"))
                    .put("first_name", StudentServices.text(user, "first_name").trim())
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
}

