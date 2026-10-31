package pl.vistula.checker.ui

import android.content.Context
import android.graphics.Color
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.LecturerWriteActions
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.ToolServiceData
import pl.vistula.checker.UsosClient
import pl.vistula.checker.DemoStudentServices
import pl.vistula.checker.core.AccountRole
import pl.vistula.checker.core.CampusAffiliation
import pl.vistula.checker.core.StudentServices

object LecturerScreenController {
    fun render(activity: MainActivity, content: LinearLayout, state: JSONObject, servicesOverride: JSONObject? = null) {
                if (!AccountRole.lecturer(state)) {
            ToolScreenUi.header(activity, content, R.string.lecturer_title, null)
            val box = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, box, activity.getString(R.string.lecturer_title), 17, UiHelper.INK, true)
            UiHelper.note(activity, box, activity.getString(R.string.lecturer_role_required), UiHelper.MUTED)
            box.addView(UiHelper.button(activity, activity.getString(R.string.nav_home), { activity.navigate(0) }, true))
            return
        }
        val language = Language.currentLocale(activity).language
        val services = servicesOverride ?: state.optJSONObject("studentServices") ?: JSONObject()
        ToolScreenUi.header(activity, content, R.string.lecturer_title) { menu ->
            ToolScreenUi.action(menu, R.string.btn_refresh) {
                activity.refreshStudentServices("teaching", "lecturerGroups", "lecturerTests", "lecturerMeetings", "lecturerAttendance", "lecturerReports")
            }
        }
        UiHelper.note(activity, content, activity.getString(R.string.lecturer_description), UiHelper.MUTED)
        content.addView(UiHelper.button(activity, activity.getString(R.string.lecturer_schedule), { activity.navigate(5) }, true))

        // 1. Conducted Courses
        val teachingEntry = services.optJSONObject("teaching") ?: JSONObject()
        StudentCardsController.status(activity, content, teachingEntry)
        val courses = teachingEntry.optJSONObject("data")?.optJSONArray("courses")
        if (courses == null || courses.length() == 0) {
            UiHelper.note(activity, content, activity.getString(if (teachingEntry.has("fetchedAt")) R.string.lecturer_empty else R.string.services_load_hint), UiHelper.MUTED)
        } else {
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until courses.length()) {
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val course = courses.getJSONObject(i)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                UiHelper.add(activity, box, StudentServices.localized(course.opt("name"), language), 16, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.localized(course.opt("term"), language), UiHelper.MUTED)
                tray.addView(box)
            }
        }

        // 2. Class Groups & Coordinated Courses
        UiHelper.sectionLabel(activity, content, activity.getString(R.string.lecturer_groups_title))
        content.addView(UiHelper.button(activity, activity.getString(R.string.btn_refresh) + " · " + activity.getString(R.string.lecturer_groups_title), {
            activity.refreshStudentServices("lecturerGroups")
        }, false))
        val groupsData = services.optJSONObject("lecturerGroups")?.optJSONObject("data")
        val groups = groupsData?.optJSONArray("groups") ?: JSONArray()
        val coordinated = groupsData?.optJSONArray("coordinated") ?: groupsData?.optJSONArray("coordinatedCourses") ?: JSONArray()
        if (groups.length() == 0 && coordinated.length() == 0) {
            UiHelper.note(activity, content, activity.getString(R.string.lecturer_groups_empty), UiHelper.MUTED)
        } else {
            val tray = UiHelper.tray(activity, content)
            var index = 0
            for (i in 0 until groups.length()) {
                val g = groups.optJSONObject(i) ?: continue
                if (index++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(g.opt("course_name"), language).ifEmpty { StudentServices.text(g, "course_id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                val sub = listOfNotNull(
                    StudentServices.localized(g.opt("class_type"), language).takeIf { it.isNotEmpty() },
                    StudentServices.text(g, "group_number").takeIf { it.isNotEmpty() }?.let { "#$it" },
                    StudentServices.text(g, "term_id").takeIf { it.isNotEmpty() }
                ).joinToString(" · ")
                if (sub.isNotEmpty()) UiHelper.note(activity, box, sub, UiHelper.MUTED)
                val unitId = StudentServices.text(g, "course_unit_id")
                val groupNr = StudentServices.text(g, "group_number")
                if (unitId.isNotEmpty() && groupNr.isNotEmpty()) {
                    box.isClickable = true
                    box.isFocusable = true
                    UiHelper.note(activity, box, activity.getString(R.string.lecturer_group_click_hint), UiHelper.MUTED)
                    box.setOnClickListener {
                        if (activity.isDemo()) {
                            showGroupDetails(activity, title, DemoStudentServices.demoGroupDetail(unitId, groupNr), language)
                        } else {
                            activity.universityRead({ client ->
                                ToolServiceData.fetchGroupDetail(client, state, unitId, groupNr)
                            }) { detail ->
                                showGroupDetails(activity, title, detail, language)
                            }
                        }
                    }
                }
                tray.addView(box)
            }
            for (i in 0 until coordinated.length()) {
                val c = coordinated.optJSONObject(i) ?: continue
                if (index++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(c.opt("course_name"), language).ifEmpty { StudentServices.text(c, "course_id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(c, "term_id"), UiHelper.MUTED)
                tray.addView(box)
            }
        }

        // 3. Course Tests & Experimental Grading
        UiHelper.sectionLabel(activity, content, activity.getString(R.string.lecturer_tests_title) + " · " + activity.getString(R.string.lecturer_experimental_badge))
        content.addView(UiHelper.button(activity, activity.getString(R.string.btn_refresh) + " · " + activity.getString(R.string.lecturer_tests_title), {
            activity.refreshStudentServices("lecturerTests")
        }, false))
        val testsData = services.optJSONObject("lecturerTests")?.optJSONObject("data")
        val testCourses = testsData?.optJSONArray("trees") ?: testsData?.optJSONArray("courses") ?: JSONArray()
        var firstNodeId = ""
        if (testCourses.length() == 0) {
            UiHelper.note(activity, content, activity.getString(R.string.lecturer_tests_empty), UiHelper.MUTED)
        } else {
            val tray = UiHelper.tray(activity, content)
            var nodeIndex = 0
            for (i in 0 until testCourses.length()) {
                val tc = testCourses.optJSONObject(i) ?: continue
                val courseName = StudentServices.localized(tc.opt("course_name"), language)
                val nodes = tc.optJSONArray("nodes") ?: JSONArray()
                for (j in 0 until nodes.length()) {
                    val n = nodes.optJSONObject(j) ?: continue
                    val nodeId = StudentServices.text(n, "id")
                    if (firstNodeId.isEmpty() && nodeId.isNotEmpty()) firstNodeId = nodeId
                    if (nodeIndex++ > 0) UiHelper.trayDivider(activity, tray)
                    val box = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                    }
                    val nodeName = StudentServices.localized(n.opt("name"), language).ifEmpty { nodeId }
                    UiHelper.add(activity, box, "$nodeName (#$nodeId)", 15, UiHelper.INK, true)
                    val sub = listOfNotNull(
                        courseName.takeIf { it.isNotEmpty() },
                        StudentServices.text(n, "type").uppercase(),
                        if (n.optBoolean("visible_for_students", false)) "Visible" else "Hidden"
                    ).joinToString(" · ")
                    UiHelper.note(activity, box, sub, UiHelper.MUTED)
                    if (nodeId.isNotEmpty()) {
                        box.isClickable = true
                        box.isFocusable = true
                        UiHelper.note(activity, box, activity.getString(R.string.lecturer_node_click_hint), UiHelper.MUTED)
                        box.setOnClickListener {
                            if (activity.isDemo()) {
                                showNodeDetails(activity, nodeName, DemoStudentServices.demoNodeDetail(nodeId), language)
                            } else {
                                activity.universityRead({ client ->
                                    ToolServiceData.fetchNodeDetail(client, state, nodeId)
                                }) { detail ->
                                    showNodeDetails(activity, nodeName, detail, language)
                                }
                            }
                        }
                    }
                    tray.addView(box)
                }
            }
        }

        val prefs = activity.getSharedPreferences("student_tools", Context.MODE_PRIVATE)
        val expEnabled = prefs.getBoolean(LecturerWriteActions.PREF_EXPERIMENTAL_WRITES, true) &&
            prefs.getBoolean(LecturerWriteActions.PREF_EXPERIMENTAL_WRITES_ACK, false)
        val writeBox = UiHelper.section(activity, content, Color.WHITE)
        UiHelper.add(activity, writeBox, activity.getString(R.string.lecturer_experimental_setting), 15, UiHelper.GREEN, true)
        UiHelper.note(activity, writeBox, activity.getString(R.string.lecturer_experimental_notice_body), UiHelper.MUTED)
        if (expEnabled) {
            val nodeField = UiHelper.field(activity, writeBox, "Node ID", firstNodeId, false)
            val studentField = UiHelper.field(activity, writeBox, "Student ID", "", false)
            val valueField = UiHelper.field(activity, writeBox, "Points / Grade", "", false)
            val commentField = UiHelper.field(activity, writeBox, "Comment", "", false)
            writeBox.addView(UiHelper.button(activity, activity.getString(R.string.lecturer_write_points), {
                val nodeId = nodeField.text.toString().trim()
                val studentId = studentField.text.toString().trim()
                val value = valueField.text.toString().trim()
                val comment = commentField.text.toString().trim()
                if (nodeId.isNotEmpty() && studentId.isNotEmpty() && value.isNotEmpty()) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.lecturer_write_confirm_title)
                        .setMessage(activity.getString(R.string.lecturer_write_confirm_body, nodeId, studentId, value))
                        .setNegativeButton(R.string.btn_cancel, null)
                        .setPositiveButton(R.string.lecturer_write_points) { _, _ ->
                            activity.performLecturerWrite(true, nodeId, studentId, value, comment)
                        }
                        .show()
                }
            }, true))
            writeBox.addView(UiHelper.button(activity, activity.getString(R.string.lecturer_write_grade), {
                val nodeId = nodeField.text.toString().trim()
                val studentId = studentField.text.toString().trim()
                val value = valueField.text.toString().trim()
                val comment = commentField.text.toString().trim()
                if (nodeId.isNotEmpty() && studentId.isNotEmpty() && value.isNotEmpty()) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.lecturer_write_confirm_title)
                        .setMessage(activity.getString(R.string.lecturer_write_confirm_body, nodeId, studentId, value))
                        .setNegativeButton(R.string.btn_cancel, null)
                        .setPositiveButton(R.string.lecturer_write_grade) { _, _ ->
                            activity.performLecturerWrite(false, nodeId, studentId, value, comment)
                        }
                        .show()
                }
            }, false))
        }

        // 4. Consultations & Meetings
        UiHelper.sectionLabel(activity, content, activity.getString(R.string.lecturer_meetings_title))
        content.addView(UiHelper.button(activity, activity.getString(R.string.btn_refresh) + " · " + activity.getString(R.string.lecturer_meetings_title), {
            activity.refreshStudentServices("lecturerMeetings")
        }, false))
        val meetings = services.optJSONObject("lecturerMeetings")?.optJSONObject("data")?.optJSONArray("meetings") ?: JSONArray()
        if (meetings.length() == 0) {
            UiHelper.note(activity, content, activity.getString(R.string.lecturer_meetings_empty), UiHelper.MUTED)
        } else {
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until meetings.length()) {
                val m = meetings.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(m.opt("name"), language).ifEmpty { StudentServices.text(m, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                val comment = StudentServices.localized(m.opt("comment"), language)
                if (comment.isNotEmpty()) UiHelper.note(activity, box, comment, UiHelper.MUTED)
                val dates = m.optJSONArray("dates") ?: JSONArray()
                if (dates.length() > 0) {
                    val slots = (0 until dates.length()).mapNotNull { dIdx ->
                        val d = dates.optJSONObject(dIdx) ?: return@mapNotNull null
                        val time = listOf(StudentServices.text(d, "start_time"), StudentServices.text(d, "end_time")).filter { it.isNotEmpty() }.joinToString(" – ")
                        val seats = d.optInt("seats_limit", 0).takeIf { it > 0 }?.let { "Limit: $it" }.orEmpty()
                        listOf(time, seats).filter { it.isNotEmpty() }.joinToString(" · ")
                    }.joinToString("\n")
                    if (slots.isNotEmpty()) UiHelper.note(activity, box, slots, UiHelper.GREEN)
                }
                tray.addView(box)
            }
        }

        // 5. Attendance Lists
        UiHelper.sectionLabel(activity, content, activity.getString(R.string.lecturer_attendance_title))
        content.addView(UiHelper.button(activity, activity.getString(R.string.btn_refresh) + " · " + activity.getString(R.string.lecturer_attendance_title), {
            activity.refreshStudentServices("lecturerAttendance")
        }, false))
        val attLists = services.optJSONObject("lecturerAttendance")?.optJSONObject("data")?.optJSONArray("lists") ?: JSONArray()
        if (attLists.length() == 0) {
            UiHelper.note(activity, content, activity.getString(R.string.lecturer_attendance_empty), UiHelper.MUTED)
        } else {
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until attLists.length()) {
                val l = attLists.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(l.opt("course_name"), language).ifEmpty { "#${StudentServices.text(l, "id")}" }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                val sub = listOfNotNull(
                    StudentServices.text(l, "date").takeIf { it.isNotEmpty() },
                    StudentServices.text(l, "group_number").takeIf { it.isNotEmpty() }?.let { "#$it" },
                    StudentServices.text(l, "mode").takeIf { it.isNotEmpty() }
                ).joinToString(" · ")
                if (sub.isNotEmpty()) UiHelper.note(activity, box, sub, UiHelper.MUTED)
                val listId = StudentServices.text(l, "id")
                if (listId.isNotEmpty()) {
                    box.isClickable = true
                    box.isFocusable = true
                    UiHelper.note(activity, box, activity.getString(R.string.lecturer_attendance_click_hint), UiHelper.MUTED)
                    box.setOnClickListener {
                        if (activity.isDemo()) {
                            showAttendanceDetails(activity, title, DemoStudentServices.demoAttendanceDetail(listId), language)
                        } else {
                            activity.universityRead({ client ->
                                ToolServiceData.fetchAttendanceDetail(client, state, listId)
                            }) { detail ->
                                showAttendanceDetails(activity, title, detail, language)
                            }
                        }
                    }
                }
                tray.addView(box)
            }
        }

        // 6. Surveys & Signable Thesis Reports
        UiHelper.sectionLabel(activity, content, activity.getString(R.string.lecturer_reports_title))
        content.addView(UiHelper.button(activity, activity.getString(R.string.btn_refresh) + " · " + activity.getString(R.string.lecturer_reports_title), {
            activity.refreshStudentServices("lecturerReports")
        }, false))
        val repData = services.optJSONObject("lecturerReports")?.optJSONObject("data")
        val surveys = repData?.optJSONArray("surveys") ?: JSONArray()
        val theses = repData?.optJSONArray("signableTheses") ?: JSONArray()
        if (surveys.length() == 0 && theses.length() == 0) {
            UiHelper.note(activity, content, activity.getString(R.string.lecturer_reports_empty), UiHelper.MUTED)
        } else {
            val tray = UiHelper.tray(activity, content)
            var idx = 0
            for (i in 0 until surveys.length()) {
                val s = surveys.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(s.opt("name"), language).ifEmpty { StudentServices.text(s, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, "${s.optInt("number_of_answers", 0)} · ${StudentServices.text(s, "start_date")}", UiHelper.MUTED)
                tray.addView(box)
            }
            for (i in 0 until theses.length()) {
                val t = theses.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(t.opt("title"), language).ifEmpty { StudentServices.text(t, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(t, "type"), UiHelper.MUTED)
                tray.addView(box)
            }
        }

        content.addView(UiHelper.actionRow(activity, activity.getString(R.string.lecturer_usosweb), {
            UiHelper.browser(activity, "https://usosweb.vistula.edu.pl/kontroler.php?_action=dla_prac")
        }, false))
    }

    fun renderAcademicExtras(activity: MainActivity, content: LinearLayout, services: JSONObject) {
        val language = Language.currentLocale(activity).language
        ToolScreenUi.header(activity, content, R.string.academic_extras_title) { menu ->
            ToolScreenUi.action(menu, R.string.btn_refresh) {
                activity.refreshStudentServices("registrations", "tokenRegistrations", "exams", "academicCalendar", "mobility", "placementAndSlips", "campusGuide", "communications", "theses")
            }
        }
        UiHelper.note(activity, content, activity.getString(R.string.academic_extras_desc), UiHelper.MUTED)
        val statusEntry = listOf("academicCalendar", "registrations", "exams", "mobility", "placementAndSlips", "campusGuide", "communications", "theses")
            .mapNotNull { services.optJSONObject(it) }
            .maxByOrNull { it.optLong("attemptAt", it.optLong("fetchedAt", 0L)) } ?: JSONObject()
        StudentCardsController.status(activity, content, statusEntry)
        content.addView(UiHelper.button(activity, activity.getString(R.string.btn_refresh), {
            activity.refreshStudentServices("registrations", "tokenRegistrations", "exams", "academicCalendar", "mobility", "placementAndSlips", "campusGuide", "communications", "theses")
        }, false))
        val regs = services.optJSONObject("registrations")?.optJSONObject("data")?.optJSONArray("registrations") ?: JSONArray()
        val cart = services.optJSONObject("registrations")?.optJSONObject("data")?.optJSONArray("cart") ?: JSONArray()
        if (regs.length() > 0) {
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until regs.length()) {
                val r = regs.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val desc = StudentServices.localized(r.opt("description"), language).ifEmpty { StudentServices.text(r, "id") }
                UiHelper.add(activity, box, desc, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(r, "status"), UiHelper.MUTED)
                val registrationId = StudentServices.text(r, "id")
                if (registrationId.isNotEmpty()) {
                    box.isClickable = true
                    box.isFocusable = true
                    box.setOnClickListener {
                        activity.universityRead({ client -> ToolServiceData.fetchRegistrationRounds(client, registrationId) }) { rounds ->
                            val lines = (0 until rounds.length()).mapNotNull { index ->
                                val round = rounds.optJSONObject(index) ?: return@mapNotNull null
                                listOf(StudentServices.localized(round.opt("name"), language), StudentServices.text(round, "status"),
                                    StudentServices.text(round, "start_date"), StudentServices.text(round, "end_date"))
                                    .filter { it.isNotEmpty() }.joinToString(" · ")
                            }
                            MaterialAlertDialogBuilder(activity).setTitle(desc)
                                .setMessage(lines.ifEmpty { listOf(activity.getString(R.string.registration_rounds_empty)) }.joinToString("\n\n"))
                                .setPositiveButton(android.R.string.ok, null).show()
                        }
                    }
                }
                tray.addView(box)
            }
        }
        if (cart.length() > 0) {
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until cart.length()) {
                val item = cart.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                UiHelper.add(activity, box, StudentServices.localized(item.opt("course_name"), language).ifEmpty { StudentServices.text(item, "course_id") }, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, listOf(StudentServices.text(item, "term_id"), StudentServices.text(item, "user_registration_status")).filter { it.isNotEmpty() }.joinToString(" · "), UiHelper.MUTED)
                tray.addView(box)
            }
        }
        val tokenEntry = services.optJSONObject("tokenRegistrations")
        val tokens = tokenEntry?.optJSONObject("data")?.optJSONArray("registrations") ?: JSONArray()
        UiHelper.sectionLabel(activity, content, activity.getString(R.string.token_registrations_title))
        if (tokenEntry != null) StudentCardsController.status(activity, content, tokenEntry)
        if (tokens.length() == 0 && (tokenEntry?.optLong("fetchedAt", 0L) ?: 0L) > 0L) {
            UiHelper.note(activity, content, activity.getString(R.string.token_registrations_empty), UiHelper.MUTED)
        }
        if (tokens.length() > 0) {
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until tokens.length()) {
                val item = tokens.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(item.opt("description"), language).ifEmpty { StudentServices.text(item, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(item, "status"), UiHelper.MUTED)
                val id = StudentServices.text(item, "id")
                if (id.isNotEmpty()) {
                    UiHelper.note(activity, box, activity.getString(R.string.token_details_hint), UiHelper.MUTED)
                    box.isClickable = true
                    box.isFocusable = true
                    box.setOnClickListener {
                        if (activity.isDemo()) showTokenDetails(activity, title, DemoStudentServices.demoTokenRegistrationDetails(id), language)
                        else activity.universityRead({ client -> ToolServiceData.fetchTokenRegistrationDetails(client, id) }) { data ->
                            showTokenDetails(activity, title, data, language)
                        }
                    }
                }
                tray.addView(box)
            }
        }
        val exams = services.optJSONObject("exams")?.optJSONObject("data")?.optJSONArray("exams") ?: JSONArray()
        if (exams.length() > 0) {
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until exams.length()) {
                val e = exams.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(e.opt("course_name"), language).ifEmpty { StudentServices.text(e, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(e, "term_id"), UiHelper.MUTED)
                val examId = StudentServices.text(e, "id")
                if (examId.isNotEmpty()) {
                    UiHelper.note(activity, box, activity.getString(R.string.exam_details_hint), UiHelper.MUTED)
                    box.isClickable = true
                    box.isFocusable = true
                    box.setOnClickListener {
                        if (activity.isDemo()) showExamDetails(activity, title, DemoStudentServices.demoExamDetails(examId), language)
                        else activity.universityRead({ client -> ToolServiceData.fetchExamDetails(client, examId) }) { data ->
                            showExamDetails(activity, title, data, language)
                        }
                    }
                }
                tray.addView(box)
            }
        }
        val thesisEntry = services.optJSONObject("theses")
        val theses = thesisEntry?.optJSONObject("data")?.optJSONArray("theses") ?: JSONArray()
        if (theses.length() > 0) {
            UiHelper.sectionLabel(activity, content, activity.getString(R.string.theses_title))
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until theses.length()) {
                val t = theses.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val thId = StudentServices.text(t, "id")
                val title = StudentServices.localized(t.opt("title") ?: t.opt("titles"), language).ifEmpty { thId }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, listOf(StudentServices.text(t, "type"), StudentServices.text(t, "status")).filter { it.isNotEmpty() }.joinToString(" · "), UiHelper.MUTED)
                if (thId.isNotEmpty()) {
                    box.isClickable = true
                    box.isFocusable = true
                    UiHelper.note(activity, box, activity.getString(R.string.theses_details_hint), UiHelper.MUTED)
                    box.setOnClickListener {
                        if (activity.isDemo()) {
                            showThesisDetails(activity, title, DemoStudentServices.demoThesisDetail(thId), language)
                        } else {
                            activity.universityRead({ client ->
                                ToolServiceData.fetchThesisDetail(client, thId)
                            }) { detail ->
                                showThesisDetails(activity, title, detail, language)
                            }
                        }
                    }
                }
                tray.addView(box)
            }
        }
        val calData = services.optJSONObject("academicCalendar")?.optJSONObject("data")
        val programmes = calData?.optJSONArray("programmes") ?: JSONArray()
        val usedEcts = calData?.optString("usedEcts").orEmpty()
        val events = calData?.optJSONArray("events") ?: JSONArray()
        val primaryGroups = calData?.optJSONArray("primaryGroups") ?: JSONArray()
        if (programmes.length() > 0) {
            UiHelper.sectionLabel(activity, content, activity.getString(R.string.programmes_title))
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until programmes.length()) {
                val programme = programmes.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val id = StudentServices.text(programme, "programme_id")
                val title = StudentServices.localized(programme.opt("programme_name"), language).ifEmpty { id }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, listOf(StudentServices.text(programme, "status"), StudentServices.text(programme, "admission_date")).filter { it.isNotEmpty() }.joinToString(" · "), UiHelper.MUTED)
                if (id.isNotEmpty()) {
                    UiHelper.note(activity, box, activity.getString(R.string.programme_details_hint), UiHelper.MUTED)
                    box.isClickable = true
                    box.isFocusable = true
                    box.setOnClickListener {
                        val studentProgrammeId = StudentServices.text(programme, "id")
                        val choices = if (studentProgrammeId.isNotEmpty()) arrayOf(activity.getString(R.string.programme_details_action), activity.getString(R.string.programme_stages_action))
                            else arrayOf(activity.getString(R.string.programme_details_action))
                        MaterialAlertDialogBuilder(activity).setTitle(title).setItems(choices) { _, selected ->
                            if (selected == 0) {
                                if (activity.isDemo()) showProgrammeDetails(activity, title, DemoStudentServices.demoProgrammeDetails(id), language)
                                else activity.universityRead({ client -> ToolServiceData.fetchProgrammeDetails(client, id) }) { detail ->
                                    showProgrammeDetails(activity, title, detail, language)
                                }
                            } else {
                                if (activity.isDemo()) showProgrammeStages(activity, title, DemoStudentServices.demoStudentProgrammeStages(), language)
                                else activity.universityRead({ client -> ToolServiceData.fetchStudentProgrammeStages(client, studentProgrammeId) }) { detail ->
                                    showProgrammeStages(activity, title, detail, language)
                                }
                            }
                        }.setNegativeButton(android.R.string.cancel, null).show()
                    }
                }
                tray.addView(box)
            }
        }
        if (usedEcts.isNotEmpty() || events.length() > 0 || primaryGroups.length() > 0) {
            val tray = UiHelper.tray(activity, content)
            var idx = 0
            if (usedEcts.isNotEmpty()) {
                idx++
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                UiHelper.add(activity, box, "ECTS: $usedEcts", 15, UiHelper.INK, true)
                tray.addView(box)
            }
            for (i in 0 until events.length()) {
                val ev = events.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(ev.opt("name"), language).ifEmpty { StudentServices.text(ev, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(ev, "start_date"), UiHelper.MUTED)
                val id = StudentServices.text(ev, "id")
                if (id.isNotEmpty()) {
                    UiHelper.note(activity, box, activity.getString(R.string.calendar_event_hint), UiHelper.MUTED)
                    box.isClickable = true
                    box.isFocusable = true
                    box.setOnClickListener { showCalendarEvent(activity, id, title, language) }
                }
                tray.addView(box)
            }
            for (i in 0 until primaryGroups.length()) {
                val g = primaryGroups.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(g.opt("description"), language).ifEmpty { StudentServices.text(g, "number") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(g, "term_id"), UiHelper.MUTED)
                tray.addView(box)
            }
        }
        UiHelper.sectionLabel(activity, content, activity.getString(R.string.calendar_range_title))
        val today = java.time.LocalDate.now(UsosClient.ZONE)
        val calendarStart = UiHelper.field(activity, content, activity.getString(R.string.calendar_range_start), today.toString(), false)
        val calendarEnd = UiHelper.field(activity, content, activity.getString(R.string.calendar_range_end), today.plusDays(28).toString(), false)
        val calendarResults = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(UiHelper.button(activity, activity.getString(R.string.calendar_range_search), {
            val start = calendarStart.text.toString().trim()
            val end = calendarEnd.text.toString().trim()
            calendarEnd.error = null
            if (!ToolServiceData.validCalendarRange(start, end)) {
                calendarEnd.error = activity.getString(R.string.calendar_range_invalid)
            } else if (activity.isDemo()) {
                showCalendarResults(activity, calendarResults, DemoStudentServices.demoCalendarSearch(start, end), language)
            } else {
                val faculty = CampusAffiliation.selected(activity.serviceAccountState())
                if (faculty.isEmpty()) calendarEnd.error = activity.getString(R.string.calendar_select_campus)
                else activity.universityRead({ client ->
                    val result = ToolServiceData.searchCalendar(client, faculty, start, end)
                    check(activity.currentServiceCampus() == faculty)
                    result
                }) { result ->
                    if (calendarResults.isAttachedToWindow) showCalendarResults(activity, calendarResults, result, language)
                }
            }
        }, false))
        content.addView(calendarResults)
        val mobData = services.optJSONObject("mobility")?.optJSONObject("data")
        val studentMobs = mobData?.optJSONArray("studentMobilities") ?: JSONArray()
        if (studentMobs.length() > 0) {
            UiHelper.sectionLabel(activity, content, activity.getString(R.string.mobility_title))
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until studentMobs.length()) {
                val m = studentMobs.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(m.opt("institution"), language).ifEmpty { StudentServices.text(m, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.localized(m.opt("programme"), language), UiHelper.MUTED)
                tray.addView(box)
            }
        }
        val guideData = services.optJSONObject("campusGuide")?.optJSONObject("data")
        val dorms = guideData?.optJSONArray("dormitories") ?: JSONArray()
        val buildings = guideData?.optJSONArray("buildings") ?: JSONArray()
        if (dorms.length() > 0 || buildings.length() > 0) {
            UiHelper.sectionLabel(activity, content, activity.getString(R.string.campus_guide_title))
            val tray = UiHelper.tray(activity, content)
            var idx = 0
            for (i in 0 until dorms.length()) {
                val d = dorms.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(d.opt("name"), language).ifEmpty { StudentServices.text(d, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(d, "address"), UiHelper.MUTED)
                val dormId = StudentServices.text(d, "id")
                if (dormId.isNotEmpty()) {
                    box.isClickable = true
                    box.isFocusable = true
                    UiHelper.note(activity, box, activity.getString(R.string.dormitory_details_hint), UiHelper.MUTED)
                    box.setOnClickListener {
                        if (activity.isDemo()) {
                            showDormitoryDetails(activity, title, DemoStudentServices.demoDormitoryDetail(dormId), language)
                        } else {
                            activity.universityRead({ client ->
                                ToolServiceData.fetchDormitoryDetail(client, dormId)
                            }) { detail ->
                                showDormitoryDetails(activity, title, detail, language)
                            }
                        }
                    }
                }
                tray.addView(box)
            }
            for (i in 0 until buildings.length()) {
                val b = buildings.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(b.opt("name"), language).ifEmpty { StudentServices.text(b, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(b, "postal_address"), UiHelper.MUTED)
                val buildingId = StudentServices.text(b, "id")
                if (buildingId.isNotEmpty() && !activity.isDemo()) {
                    box.isClickable = true
                    box.isFocusable = true
                    UiHelper.note(activity, box, activity.getString(R.string.campus_building_rooms_hint), UiHelper.MUTED)
                    box.setOnClickListener {
                        activity.universityRead({ client -> ToolServiceData.fetchBuildingRooms(client, buildingId) }) { rooms ->
                            if (rooms.length() == 0) {
                                MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(R.string.campus_rooms_empty).setPositiveButton(android.R.string.ok, null).show()
                            } else {
                                val labels = (0 until rooms.length()).map { index ->
                                    val room = rooms.getJSONObject(index)
                                    StudentServices.text(room, "number").ifEmpty { StudentServices.text(room, "id") }
                                }.toTypedArray()
                                val dialog = MaterialAlertDialogBuilder(activity).setTitle(title).setItems(labels) { _, index ->
                                    val roomId = StudentServices.text(rooms.optJSONObject(index), "id")
                                    if (roomId.isNotEmpty()) showRoomSchedule(activity, roomId, language)
                                }.setNegativeButton(android.R.string.cancel, null)
                                if (rooms.length() == 100) dialog.setTitle("$title · ${activity.getString(R.string.campus_rooms_limited)}")
                                dialog.show()
                            }
                        }
                    }
                }
                tray.addView(box)
            }
        }
        val periods = guideData?.optJSONArray("allocationPeriods") ?: JSONArray()
        if (periods.length() > 0) {
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until periods.length()) {
                val ap = periods.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val apId = StudentServices.text(ap, "id")
                val title = StudentServices.localized(ap.opt("name") ?: ap.opt("status_name"), language).ifEmpty { apId }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                val desc = StudentServices.localized(ap.opt("description") ?: ap.opt("status_desc"), language)
                if (desc.isNotEmpty()) UiHelper.note(activity, box, desc, UiHelper.MUTED)
                if (apId.isNotEmpty()) {
                    box.isClickable = true
                    box.isFocusable = true
                    UiHelper.note(activity, box, activity.getString(R.string.allocation_period_hint), UiHelper.MUTED)
                    box.setOnClickListener {
                        if (activity.isDemo()) {
                            showAllocationPeriodDetails(activity, title, DemoStudentServices.demoAllocationPeriodDetail(apId), language)
                        } else {
                            activity.universityRead({ client ->
                                ToolServiceData.fetchAllocationPeriodDetail(client, apId)
                            }) { detail ->
                                showAllocationPeriodDetails(activity, title, detail, language)
                            }
                        }
                    }
                }
                tray.addView(box)
            }
        }
        val chapters = guideData?.optJSONArray("guideChapters") ?: JSONArray()
        if (chapters.length() > 0) {
            val tray = UiHelper.tray(activity, content)
            var cIdx = 0
            for (i in 0 until chapters.length()) {
                val ch = chapters.optJSONObject(i) ?: continue
                val chTitle = StudentServices.localized(ch.opt("title"), language).ifEmpty { StudentServices.text(ch, "id") }
                val pages = ch.optJSONArray("pages") ?: JSONArray()
                for (j in 0 until pages.length()) {
                    val pg = pages.optJSONObject(j) ?: continue
                    if (cIdx++ > 0) UiHelper.trayDivider(activity, tray)
                    val box = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                    }
                    val pgId = StudentServices.text(pg, "id")
                    val pgTitle = StudentServices.localized(pg.opt("title"), language).ifEmpty { pgId }
                    UiHelper.add(activity, box, pgTitle, 15, UiHelper.INK, true)
                    UiHelper.note(activity, box, activity.getString(R.string.guide_chapter_title, chTitle), UiHelper.MUTED)
                    if (pgId.isNotEmpty()) {
                        box.isClickable = true
                        box.isFocusable = true
                        UiHelper.note(activity, box, activity.getString(R.string.guide_page_hint), UiHelper.MUTED)
                        box.setOnClickListener {
                            if (activity.isDemo()) {
                                showGuidePageDetails(activity, pgTitle, DemoStudentServices.demoGuidePage(pgId), language)
                            } else {
                                activity.universityRead({ client ->
                                    ToolServiceData.fetchGuidePage(client, pgId)
                                }) { detail ->
                                    showGuidePageDetails(activity, pgTitle, detail, language)
                                }
                            }
                        }
                    }
                    tray.addView(box)
                }
            }
        }
        UiHelper.sectionLabel(activity, content, activity.getString(R.string.campus_guide_title))
        val courseQuery = UiHelper.searchField(activity, content, activity.getString(R.string.campus_course_search_hint), "", activity.getString(R.string.campus_course_search_hint), null)
        val courseResults = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(UiHelper.button(activity, activity.getString(R.string.staff_search_button), {
            val query = courseQuery.text.toString().trim()
            if (query.length !in 2..80) courseQuery.error = activity.getString(R.string.staff_search_invalid)
            else if (activity.isDemo()) showCourseResults(activity, courseResults, DemoStudentServices.demoCourseSearch(query), language)
            else activity.universityRead({ client -> ToolServiceData.searchCourses(client, query) }) { result ->
                if (courseResults.isAttachedToWindow) showCourseResults(activity, courseResults, result, language)
            }
        }, false))
        content.addView(courseResults)
        val roomQuery = UiHelper.searchField(activity, content, activity.getString(R.string.campus_room_id_hint), "", activity.getString(R.string.campus_room_id_hint), null)
        content.addView(UiHelper.button(activity, activity.getString(R.string.campus_room_schedule), {
            val id = roomQuery.text.toString().trim()
            if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) roomQuery.error = activity.getString(R.string.campus_room_id_invalid)
            else showRoomSchedule(activity, id, language)
        }, false))
        val commData = services.optJSONObject("communications")?.optJSONObject("data")
        val statements = commData?.optJSONArray("statements") ?: JSONArray()
        val notifications = commData?.optJSONArray("notifications") ?: JSONArray()
        val surveys = commData?.optJSONArray("surveys") ?: JSONArray()
        val messages = commData?.optJSONArray("messages") ?: JSONArray()
        if (statements.length() > 0 || notifications.length() > 0 || surveys.length() > 0 || messages.length() > 0) {
            UiHelper.sectionLabel(activity, content, activity.getString(R.string.communications_title))
            val tray = UiHelper.tray(activity, content)
            var idx = 0
            for (i in 0 until statements.length()) {
                val s = statements.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                UiHelper.add(activity, box, StudentServices.text(s, "subject"), 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(s, "content"), UiHelper.MUTED)
                tray.addView(box)
            }
            for (i in 0 until surveys.length()) {
                val s = surveys.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val id = StudentServices.text(s, "id")
                val title = StudentServices.localized(s.opt("name"), language).ifEmpty { id }
                UiHelper.add(activity, box, activity.getString(R.string.survey_item_title, title), 15, UiHelper.INK, true)
                val status = when {
                    s.optBoolean("did_i_fill_out") -> activity.getString(R.string.survey_completed)
                    s.has("can_i_fill_out") && !s.optBoolean("can_i_fill_out") -> activity.getString(R.string.survey_unavailable)
                    s.has("can_i_fill_out") -> activity.getString(R.string.survey_to_fill)
                    else -> ""
                }
                UiHelper.note(activity, box, listOf(status, StudentServices.text(s, "end_date")).filter { it.isNotEmpty() }.joinToString(" · "), UiHelper.MUTED)
                if (id.isNotEmpty()) {
                    UiHelper.note(activity, box, activity.getString(R.string.survey_details_hint), UiHelper.MUTED)
                    box.isClickable = true
                    box.isFocusable = true
                    box.setOnClickListener {
                        if (activity.isDemo()) showSurveyDetails(activity, title, DemoStudentServices.demoSurveyDetail(id), language)
                        else activity.universityRead({ client -> ToolServiceData.fetchSurveyDetail(client, id) }) { data ->
                            showSurveyDetails(activity, title, data, language)
                        }
                    }
                }
                tray.addView(box)
            }
            for (i in 0 until messages.length()) {
                val m = messages.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val id = StudentServices.text(m, "id")
                val title = StudentServices.text(m, "subject").ifEmpty { id }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, listOf(StudentServices.text(m, "status"), StudentServices.text(m, "date")).filter { it.isNotEmpty() }.joinToString(" · "), UiHelper.MUTED)
                if (id.isNotEmpty()) {
                    UiHelper.note(activity, box, activity.getString(R.string.mail_details_hint), UiHelper.MUTED)
                    box.isClickable = true
                    box.isFocusable = true
                    box.setOnClickListener {
                        if (activity.isDemo()) showMailMessage(activity, title, DemoStudentServices.demoMailMessage(id))
                        else activity.universityRead({ client -> ToolServiceData.fetchMailMessage(client, id) }) { data ->
                            showMailMessage(activity, title, data)
                        }
                    }
                }
                tray.addView(box)
            }
            for (i in 0 until notifications.length()) {
                val n = notifications.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                UiHelper.add(activity, box, StudentServices.localized(n.opt("summary"), language).ifEmpty { StudentServices.text(n, "type") }, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(n, "created"), UiHelper.MUTED)
                tray.addView(box)
            }
        }
        val plcData = services.optJSONObject("placementAndSlips")?.optJSONObject("data")
        val plcTests = plcData?.optJSONArray("placementTests") ?: JSONArray()
        val slips = plcData?.optJSONArray("slipsToAnswer") ?: JSONArray()
        if (plcTests.length() > 0 || slips.length() > 0) {
            UiHelper.sectionLabel(activity, content, activity.getString(R.string.placement_slips_title))
            val tray = UiHelper.tray(activity, content)
            var idx = 0
            for (i in 0 until plcTests.length()) {
                val pt = plcTests.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(pt.opt("name"), language).ifEmpty { StudentServices.text(pt, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                val score = listOfNotNull(
                    StudentServices.text(pt, "points").takeIf { it.isNotEmpty() },
                    StudentServices.text(pt, "max_score").takeIf { it.isNotEmpty() }?.let { "/ $it" },
                    StudentServices.localized(pt.opt("level"), language).takeIf { it.isNotEmpty() }
                ).joinToString(" ")
                if (score.isNotEmpty()) UiHelper.note(activity, box, score, UiHelper.MUTED)
                tray.addView(box)
            }
            for (i in 0 until slips.length()) {
                val sl = slips.optJSONObject(i) ?: continue
                if (idx++ > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                UiHelper.add(activity, box, StudentServices.localized(sl.opt("question"), language).ifEmpty { StudentServices.text(sl, "id") }, 15, UiHelper.INK, true)
                tray.addView(box)
            }
        }
        val anyRendered = regs.length() > 0 || cart.length() > 0 || tokens.length() > 0 || exams.length() > 0 || theses.length() > 0 || programmes.length() > 0 || usedEcts.isNotEmpty() || events.length() > 0 ||
            primaryGroups.length() > 0 || studentMobs.length() > 0 || dorms.length() > 0 || buildings.length() > 0 ||
            statements.length() > 0 || notifications.length() > 0 || surveys.length() > 0 || messages.length() > 0 || plcTests.length() > 0 || slips.length() > 0
        if (!anyRendered) {
            UiHelper.note(activity, content, activity.getString(if (statusEntry.has("fetchedAt")) R.string.lecturer_empty else R.string.services_load_hint), UiHelper.MUTED)
        }
    }

    private fun showCourseResults(activity: MainActivity, content: LinearLayout, data: JSONObject, language: String) {
        content.removeAllViews()
        val rows = data.optJSONArray("courses") ?: JSONArray()
        if (rows.length() == 0) {
            UiHelper.note(activity, content, activity.getString(R.string.campus_courses_empty), UiHelper.MUTED)
            return
        }
        val tray = UiHelper.tray(activity, content)
        for (i in 0 until rows.length()) {
            val course = rows.optJSONObject(i) ?: continue
            if (i > 0) UiHelper.trayDivider(activity, tray)
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
            }
            val title = StudentServices.localized(course.opt("name"), language).ifEmpty { StudentServices.text(course, "match") }
            UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
            val ects = StudentServices.text(course, "ects").takeIf { it.isNotEmpty() }?.let { activity.getString(R.string.campus_ects_approx, it) }.orEmpty()
            UiHelper.note(activity, box, listOf(StudentServices.text(course, "id"), ects).filter { it.isNotEmpty() }.joinToString(" · "), UiHelper.MUTED)
            val courseId = StudentServices.text(course, "id")
            if (courseId.isNotEmpty()) {
                UiHelper.note(activity, box, activity.getString(R.string.course_details_hint), UiHelper.MUTED)
                box.isClickable = true
                box.isFocusable = true
                box.setOnClickListener {
                    if (activity.isDemo()) showCourseDetails(activity, title, DemoStudentServices.demoCourseDetails(courseId), language)
                    else activity.universityRead({ client -> ToolServiceData.fetchCourseDetails(client, courseId) }) { result ->
                        showCourseDetails(activity, title, result, language)
                    }
                }
            }
            tray.addView(box)
        }
        if (data.optBoolean("hasMore")) UiHelper.note(activity, content, activity.getString(R.string.staff_search_more), UiHelper.MUTED)
    }

    private fun showCourseDetails(activity: MainActivity, fallback: String, data: JSONObject, language: String) {
        val parts = mutableListOf<String>()
        StudentServices.text(data, "lang_id").takeIf { it.isNotBlank() }?.let { parts.add(activity.getString(R.string.course_language, it)) }
        StudentServices.text(data, "ects").takeIf { it.isNotBlank() }?.let { parts.add(activity.getString(R.string.campus_ects_approx, it)) }
        for ((key, label) in listOf("description" to R.string.course_description, "learning_outcomes" to R.string.course_learning_outcomes, "assessment_criteria" to R.string.course_assessment)) {
            val text = ToolServiceData.courseText(data.opt(key), language)
            if (text.isNotBlank()) parts.add(activity.getString(label) + "\n" + text)
        }
        if (parts.isEmpty()) parts.add(activity.getString(R.string.course_details_empty))
        MaterialAlertDialogBuilder(activity)
            .setTitle(StudentServices.localized(data.opt("name"), language).ifEmpty { fallback })
            .setMessage(parts.joinToString("\n\n"))
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showProgrammeDetails(activity: MainActivity, fallback: String, data: JSONObject, language: String) {
        val fields = listOf("faculty_name" to R.string.programme_faculty, "mode_of_studies" to R.string.programme_mode,
            "level_of_studies" to R.string.programme_level, "duration" to R.string.programme_duration,
            "professional_status" to R.string.programme_qualifications)
        val lines = fields.mapNotNull { (key, label) ->
            ToolServiceData.courseText(data.opt(key), language).takeIf { it.isNotBlank() }?.let { activity.getString(label) + ": " + it }
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(StudentServices.localized(data.opt("name"), language).ifEmpty { fallback })
            .setMessage(lines.ifEmpty { listOf(activity.getString(R.string.programme_details_empty)) }.joinToString("\n\n"))
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showProgrammeStages(activity: MainActivity, title: String, data: JSONObject, language: String) {
        val stages = data.optJSONArray("stages") ?: JSONArray()
        if (!data.optBoolean("stages_available") || stages.length() == 0) {
            val message = if (!data.optBoolean("stages_available") || data.optBoolean("stages_unrecognized")) R.string.programme_stages_unavailable else R.string.programme_stages_empty
            MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val ids = (0 until stages.length()).map { StudentServices.text(stages.optJSONObject(it), "id") }.toTypedArray()
        val heading = if (data.optBoolean("stages_truncated") || data.optBoolean("stages_unrecognized"))
            "$title · ${activity.getString(R.string.programme_stages_partial)}" else title
        MaterialAlertDialogBuilder(activity).setTitle(heading).setItems(ids) { _, index ->
            val id = ids[index]
            fun show(stage: JSONObject) {
                val name = StudentServices.localized(stage.opt("name"), language).ifEmpty { id }
                MaterialAlertDialogBuilder(activity).setTitle(id).setMessage(name).setPositiveButton(android.R.string.ok, null).show()
            }
            if (activity.isDemo()) show(DemoStudentServices.demoStudyStage(id))
            else activity.universityRead({ client -> ToolServiceData.fetchStudyStage(client, id) }, ::show)
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showTokenDetails(activity: MainActivity, fallback: String, data: JSONObject, language: String) {
        val lines = mutableListOf<String>()
        StudentServices.localized(data.opt("message"), language).takeIf { it.isNotBlank() }?.let { lines.add(it) }
        val rounds = data.optJSONArray("rounds") ?: JSONArray()
        if (!data.optBoolean("rounds_available")) lines.add(activity.getString(R.string.token_rounds_unavailable))
        else if (rounds.length() == 0) lines.add(activity.getString(R.string.registration_rounds_empty))
        for (i in 0 until rounds.length()) {
            val round = rounds.optJSONObject(i) ?: continue
            val parts = mutableListOf<String>()
            StudentServices.localized(round.opt("name"), language).ifEmpty { StudentServices.text(round, "id") }.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
            val start = StudentServices.text(round, "start_date")
            val end = StudentServices.text(round, "end_date")
            if (start.isNotEmpty() || end.isNotEmpty()) parts.add("${start.ifEmpty { "—" }} – ${end.ifEmpty { "—" }}")
            if (round.has("selection_limit")) parts.add(activity.getString(R.string.token_selection_limit, round.optInt("selection_limit")))
            if (round.optBoolean("is_prioritized")) parts.add(activity.getString(R.string.token_prioritized))
            if (round.optBoolean("is_only_entitled")) parts.add(activity.getString(R.string.token_entitled_only))
            if (parts.isNotEmpty()) lines.add(parts.joinToString(" · "))
        }
        if (data.optBoolean("rounds_truncated")) lines.add(activity.getString(R.string.token_rounds_limited))
        MaterialAlertDialogBuilder(activity).setTitle(StudentServices.localized(data.opt("description"), language).ifEmpty { fallback })
            .setMessage(lines.joinToString("\n\n")).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showRoomSchedule(activity: MainActivity, roomId: String, language: String) {
        fun show(data: JSONObject) {
            val lessons = data.optJSONArray("lessons") ?: JSONArray()
            val rows = (0 until lessons.length()).mapNotNull { index ->
                val lesson = lessons.optJSONObject(index) ?: return@mapNotNull null
                listOf(StudentServices.text(lesson, "start_time"), StudentServices.text(lesson, "end_time"), StudentServices.localized(lesson.opt("name"), language))
                    .filter { it.isNotEmpty() }.joinToString(" · ")
            }
            MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.campus_room_title, StudentServices.text(data, "number").ifEmpty { roomId }))
                .setMessage(rows.ifEmpty { listOf(activity.getString(R.string.campus_room_schedule_empty)) }.joinToString("\n\n"))
                .setPositiveButton(android.R.string.ok, null).show()
        }
        if (activity.isDemo()) show(DemoStudentServices.demoRoomSchedule(roomId))
        else activity.universityRead({ client -> ToolServiceData.fetchRoomSchedule(client, roomId, java.time.LocalDate.now(UsosClient.ZONE).toString()) }, ::show)
    }

    private fun showCalendarResults(activity: MainActivity, content: LinearLayout, data: JSONObject, language: String) {
        content.removeAllViews()
        val events = data.optJSONArray("events") ?: JSONArray()
        if (events.length() == 0) {
            UiHelper.note(activity, content, activity.getString(R.string.calendar_events_empty), UiHelper.MUTED)
            return
        }
        val tray = UiHelper.tray(activity, content)
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            if (i > 0) UiHelper.trayDivider(activity, tray)
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
            }
            val id = StudentServices.text(event, "id")
            val title = StudentServices.localized(event.opt("name"), language).ifEmpty { id }
            UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
            UiHelper.note(activity, box, listOf(StudentServices.text(event, "start_date"), StudentServices.text(event, "end_date")).filter { it.isNotEmpty() }.joinToString(" – "), UiHelper.MUTED)
            if (id.isNotEmpty()) {
                UiHelper.note(activity, box, activity.getString(R.string.calendar_event_hint), UiHelper.MUTED)
                box.isClickable = true; box.isFocusable = true
                box.setOnClickListener { showCalendarEvent(activity, id, title, language) }
            }
            tray.addView(box)
        }
        if (data.optBoolean("truncated")) UiHelper.note(activity, content, activity.getString(R.string.calendar_events_limited), UiHelper.MUTED)
    }

    private fun showCalendarEvent(activity: MainActivity, id: String, fallback: String, language: String) {
        fun show(event: JSONObject) {
            val lines = mutableListOf<String>()
            val dates = listOf(StudentServices.text(event, "start_date"), StudentServices.text(event, "end_date")).filter { it.isNotEmpty() }
            if (dates.isNotEmpty()) lines.add(dates.joinToString(" – "))
            StudentServices.localized(event.opt("faculty_name"), language).takeIf { it.isNotBlank() }?.let { lines.add(activity.getString(R.string.calendar_event_faculty, it)) }
            StudentServices.text(event, "type").takeIf { it.isNotBlank() }?.let { lines.add(activity.getString(R.string.calendar_event_type, it)) }
            if (event.has("is_day_off")) lines.add(activity.getString(if (event.optBoolean("is_day_off")) R.string.calendar_day_off else R.string.calendar_not_day_off))
            if (lines.isEmpty()) lines.add(activity.getString(R.string.calendar_event_empty))
            MaterialAlertDialogBuilder(activity).setTitle(StudentServices.localized(event.opt("name"), language).ifEmpty { fallback })
                .setMessage(lines.joinToString("\n\n")).setPositiveButton(android.R.string.ok, null).show()
        }
        if (activity.isDemo()) show(DemoStudentServices.demoCalendarEvent(id))
        else activity.universityRead({ client -> ToolServiceData.fetchCalendarEvent(client, id) }, ::show)
    }

    private fun showSurveyDetails(activity: MainActivity, fallbackTitle: String, data: JSONObject, language: String) {
        val lines = mutableListOf<String>()
        StudentServices.text(data, "headline").takeIf { it.isNotBlank() }?.let { lines.add(it) }
        listOf(
            StudentServices.localized(data.opt("course_name"), language), StudentServices.text(data, "lecturer"),
            StudentServices.localized(data.opt("faculty_name"), language),
            listOf(StudentServices.text(data, "start_date"), StudentServices.text(data, "end_date")).filter { it.isNotEmpty() }.joinToString(" – ")
        ).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { lines.add(it.joinToString(" · ")) }
        if (data.has("filled_out_count") && data.has("entitled_count")) {
            lines.add(activity.getString(R.string.survey_responses, data.optInt("filled_out_count"), data.optInt("entitled_count")))
        }
        val questions = data.optJSONArray("questions") ?: JSONArray()
        if (questions.length() == 0) lines.add(activity.getString(R.string.survey_questions_empty))
        for (i in 0 until questions.length()) {
            val q = questions.optJSONObject(i) ?: continue
            val answers = q.optJSONArray("answers") ?: JSONArray()
            val answerText = (0 until answers.length()).joinToString("\n") { "• " + answers.optString(it) }
            lines.add(listOf("${StudentServices.text(q, "number")} ${StudentServices.text(q, "text")}".trim(), answerText).filter { it.isNotEmpty() }.joinToString("\n"))
        }
        if (data.optBoolean("questions_truncated")) lines.add(activity.getString(R.string.survey_questions_limited))
        val title = StudentServices.localized(data.opt("name"), language).ifEmpty { fallbackTitle }
        MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(lines.joinToString("\n\n"))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.survey_open_usosweb) { _, _ ->
                UiHelper.browser(activity, "https://usosweb.vistula.edu.pl/")
            }.show()
    }

    private fun showMailMessage(activity: MainActivity, fallbackTitle: String, data: JSONObject) {
        val lines = mutableListOf<String>()
        val meta = listOf(StudentServices.text(data, "status"), StudentServices.text(data, "date"),
            if (data.has("recipients_count")) activity.getString(R.string.mail_recipients, data.optInt("recipients_count")) else "")
            .filter { it.isNotEmpty() }.joinToString(" · ")
        if (meta.isNotEmpty()) lines.add(meta)
        lines.add(StudentServices.text(data, "content").ifEmpty { activity.getString(R.string.mail_content_empty) })
        if (data.optBoolean("content_truncated")) lines.add(activity.getString(R.string.mail_content_limited))
        val attachments = data.optJSONArray("attachments") ?: JSONArray()
        val names = (0 until attachments.length()).mapNotNull { index ->
            val a = attachments.optJSONObject(index) ?: return@mapNotNull null
            val size = if (a.has("size")) "${(a.optInt("size") + 1023) / 1024} KB" else ""
            listOf(StudentServices.text(a, "filename").ifEmpty { StudentServices.text(a, "id") }, size).filter { it.isNotEmpty() }.joinToString(" · ")
        }
        if (names.isNotEmpty()) lines.add(activity.getString(R.string.mail_attachments) + "\n" + names.joinToString("\n") { "📎 $it" })
        val openable = (0 until attachments.length()).mapNotNull { attachments.optJSONObject(it) }
            .filter { StudentServices.text(it, "url").isNotEmpty() }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(StudentServices.text(data, "subject").ifEmpty { fallbackTitle })
            .setMessage(lines.joinToString("\n\n"))
            .setPositiveButton(android.R.string.ok, null)
        if (openable.isNotEmpty()) {
            dialog.setNeutralButton(R.string.mail_attachment_open) { _, _ ->
                val labels = openable.map { StudentServices.text(it, "filename").ifEmpty { StudentServices.text(it, "id") } }.toTypedArray()
                MaterialAlertDialogBuilder(activity).setTitle(R.string.mail_attachments).setItems(labels) { _, index ->
                    UiHelper.browser(activity, StudentServices.text(openable[index], "url"))
                }.setNegativeButton(android.R.string.cancel, null).show()
            }
        }
        dialog.show()
    }

    private fun showExamDetails(activity: MainActivity, fallbackTitle: String, data: JSONObject, language: String) {
        val lines = mutableListOf<String>()
        StudentServices.localized(data.opt("description"), language).takeIf { it.isNotBlank() }?.let { lines.add(it) }
        val start = StudentServices.text(data, "registration_start")
        val end = StudentServices.text(data, "registration_end")
        if (start.isNotEmpty() || end.isNotEmpty()) lines.add(activity.getString(R.string.exam_registration_window, start.ifEmpty { "—" }, end.ifEmpty { "—" }))
        val groups = data.optJSONArray("groups") ?: JSONArray()
        if (groups.length() == 0) lines.add(activity.getString(R.string.exam_groups_empty))
        for (i in 0 until groups.length()) {
            val group = groups.optJSONObject(i) ?: continue
            val parts = listOf(
                StudentServices.text(group, "number").takeIf { it.isNotEmpty() }?.let { activity.getString(R.string.exam_group_number, it) }.orEmpty(),
                StudentServices.text(group, "exam_start"), StudentServices.text(group, "exam_end"),
                StudentServices.text(group, "room_number").takeIf { it.isNotEmpty() }?.let { activity.getString(R.string.campus_room_title, it) }.orEmpty()
            ).filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) lines.add(parts.joinToString(" · "))
        }
        if (data.optBoolean("groups_truncated")) lines.add(activity.getString(R.string.exam_groups_limited))
        val title = StudentServices.localized(data.opt("name"), language).ifEmpty { fallbackTitle }
        MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(lines.joinToString("\n\n"))
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showStaffScheduleDialog(activity: MainActivity, fullName: String, profile: JSONObject, lessons: JSONArray, language: String) {
        val lines = mutableListOf<String>()
        StudentServices.text(profile, "room").takeIf { it.isNotEmpty() }?.let { lines.add(activity.getString(R.string.staff_office, it)) }
        StudentServices.localized(profile.opt("office_hours"), language).takeIf { it.isNotEmpty() }?.let { lines.add(it) }
        lines.add(activity.getString(R.string.staff_schedule_title))
        if (lessons.length() == 0) lines.add(activity.getString(R.string.staff_schedule_empty))
        for (n in 0 until lessons.length()) {
            val lesson = lessons.optJSONObject(n) ?: continue
            lines.add(listOf(StudentServices.text(lesson, "start_time"), StudentServices.localized(lesson.opt("name"), language), StudentServices.text(lesson, "room_number")).filter { it.isNotEmpty() }.joinToString(" · "))
        }
        MaterialAlertDialogBuilder(activity).setTitle(fullName).setMessage(lines.joinToString("\n\n")).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showGroupDetails(activity: MainActivity, title: String, data: JSONObject, language: String) {
        val lines = mutableListOf<String>()
        val participants = data.optJSONArray("participants") ?: JSONArray()
        val lecturers = data.optJSONArray("lecturers") ?: JSONArray()
        if (lecturers.length() > 0) {
            lines.add("Prowadzący / Lecturers:")
            for (i in 0 until lecturers.length()) {
                val l = lecturers.optJSONObject(i) ?: continue
                lines.add("• " + listOf(StudentServices.text(l, "first_name"), StudentServices.text(l, "last_name")).filter { it.isNotBlank() }.joinToString(" "))
            }
        }
        lines.add("Uczestnicy / Participants (${data.optInt("participantsTotal", participants.length())}):")
        if (participants.length() == 0) lines.add("Brak zapisanych uczestników.")
        for (i in 0 until participants.length()) {
            val p = participants.optJSONObject(i) ?: continue
            val name = listOf(StudentServices.text(p, "first_name"), StudentServices.text(p, "last_name")).filter { it.isNotBlank() }.joinToString(" ")
            val num = StudentServices.text(p, "student_number").takeIf { it.isNotEmpty() }?.let { "($it)" }.orEmpty()
            lines.add("• $name $num".trim())
        }
        MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(lines.joinToString("\n")).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showNodeDetails(activity: MainActivity, fallback: String, data: JSONObject, language: String) {
        val lines = mutableListOf<String>()
        val name = StudentServices.localized(data.opt("name"), language).ifEmpty { fallback }
        val type = StudentServices.text(data, "type")
        if (type.isNotEmpty()) lines.add("Typ / Type: $type")
        val desc = StudentServices.localized(data.opt("description"), language)
        if (desc.isNotEmpty()) lines.add(desc)
        if (data.has("points_min") || data.has("points_max")) {
            lines.add("Punkty: ${data.optDouble("points_min", 0.0)} – ${data.optDouble("points_max", 0.0)}")
        }
        val hist = data.optJSONArray("histogram") ?: JSONArray()
        if (hist.length() > 0) {
            lines.add("\nRozkład wyników / Distribution:")
            for (i in 0 until hist.length()) {
                val h = hist.optJSONObject(i) ?: continue
                lines.add("${StudentServices.text(h, "label")}: ${h.optInt("count", 0)}")
            }
        }
        MaterialAlertDialogBuilder(activity).setTitle(name).setMessage(lines.joinToString("\n")).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showAttendanceDetails(activity: MainActivity, title: String, data: JSONObject, language: String) {
        val lines = mutableListOf<String>()
        val entries = data.optJSONArray("entries") ?: JSONArray()
        lines.add("Data: ${StudentServices.text(data, "date")} · Tryb: ${StudentServices.text(data, "mode")}")
        lines.add("\nStudenci / Students (${data.optInt("entriesTotal", entries.length())}):")
        if (entries.length() == 0) lines.add("Brak wpisów.")
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val name = listOf(StudentServices.text(e, "first_name"), StudentServices.text(e, "last_name")).filter { it.isNotBlank() }.joinToString(" ")
            val num = StudentServices.text(e, "student_number").takeIf { it.isNotEmpty() }?.let { "($it)" }.orEmpty()
            val mode = StudentServices.text(e, "attendance_mode")
            val comment = StudentServices.text(e, "comment").takeIf { it.isNotEmpty() }?.let { " - $it" }.orEmpty()
            lines.add("• $name $num: $mode$comment")
        }
        MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(lines.joinToString("\n")).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showThesisDetails(activity: MainActivity, fallback: String, data: JSONObject, language: String) {
        val lines = mutableListOf<String>()
        val title = StudentServices.localized(data.opt("title") ?: data.opt("titles"), language).ifEmpty { fallback }
        val type = StudentServices.text(data, "type")
        if (type.isNotEmpty()) lines.add("Typ / Type: $type")
        val status = StudentServices.text(data, "status")
        if (status.isNotEmpty()) lines.add("Status: $status")
        val sups = data.optJSONArray("supervisors") ?: JSONArray()
        if (sups.length() > 0) {
            val names = (0 until sups.length()).map { sups.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
            lines.add(activity.getString(R.string.thesis_supervisors, names))
        }
        val authors = data.optJSONArray("authors") ?: JSONArray()
        if (authors.length() > 0) {
            val names = (0 until authors.length()).map { authors.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
            lines.add(activity.getString(R.string.thesis_authors, names))
        }
        val fac = StudentServices.localized(data.opt("faculty_name"), language)
        if (fac.isNotEmpty()) lines.add("Wydział: $fac")
        MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(lines.joinToString("\n\n")).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showDormitoryDetails(activity: MainActivity, fallback: String, data: JSONObject, language: String) {
        val lines = mutableListOf<String>()
        val name = StudentServices.localized(data.opt("name"), language).ifEmpty { fallback }
        val addr = StudentServices.text(data, "address")
        if (addr.isNotEmpty()) lines.add("Adres: $addr")
        val desc = StudentServices.localized(data.opt("description"), language)
        if (desc.isNotEmpty()) lines.add("Opis: $desc")
        MaterialAlertDialogBuilder(activity).setTitle(name).setMessage(lines.ifEmpty { listOf("Brak dodatkowych informacji.") }.joinToString("\n\n")).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showAllocationPeriodDetails(activity: MainActivity, fallback: String, data: JSONObject, language: String) {
        val lines = mutableListOf<String>()
        val name = StudentServices.localized(data.opt("status_name"), language).ifEmpty { fallback }
        val desc = StudentServices.localized(data.opt("status_desc"), language)
        if (desc.isNotEmpty()) lines.add(desc)
        val start = StudentServices.text(data, "start_date")
        val end = StudentServices.text(data, "end_date")
        if (start.isNotEmpty() || end.isNotEmpty()) lines.add("Termin: $start – $end")
        MaterialAlertDialogBuilder(activity).setTitle(name).setMessage(lines.joinToString("\n\n")).setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showGuidePageDetails(activity: MainActivity, fallback: String, data: JSONObject, language: String) {
        val lines = mutableListOf<String>()
        val title = StudentServices.localized(data.opt("title"), language).ifEmpty { fallback }
        val entries = data.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val eTitle = StudentServices.localized(e.opt("title"), language)
            val eContent = StudentServices.text(e, "content")
            if (eTitle.isNotEmpty()) lines.add("• $eTitle")
            if (eContent.isNotEmpty()) lines.add(eContent)
            lines.add("")
        }
        MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(lines.ifEmpty { listOf("Brak treści strony.") }.joinToString("\n")).setPositiveButton(android.R.string.ok, null).show()
    }

}
