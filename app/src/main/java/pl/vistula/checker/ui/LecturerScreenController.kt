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
import pl.vistula.checker.core.AccountRole
import pl.vistula.checker.core.StudentServices

object LecturerScreenController {
    fun render(activity: MainActivity, content: LinearLayout, state: JSONObject, servicesOverride: JSONObject? = null) {
        if (!AccountRole.lecturer(state)) return
        val language = Language.currentLocale(activity).language
        val services = servicesOverride ?: state.optJSONObject("studentServices") ?: JSONObject()
        ToolScreenUi.header(activity, content, R.string.lecturer_title) { menu ->
            ToolScreenUi.action(menu, R.string.btn_refresh) {
                activity.refreshStudentServices("teaching")
                activity.refreshStudentServices("lecturerGroups")
                activity.refreshStudentServices("lecturerTests")
                activity.refreshStudentServices("lecturerMeetings")
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
        val coordinated = groupsData?.optJSONArray("coordinatedCourses") ?: JSONArray()
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
        val testCourses = testsData?.optJSONArray("courses") ?: JSONArray()
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
                activity.refreshStudentServices("registrations")
                activity.refreshStudentServices("exams")
                activity.refreshStudentServices("academicCalendar")
            }
        }
        val regs = services.optJSONObject("registrations")?.optJSONObject("data")?.optJSONArray("registrations") ?: JSONArray()
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
                tray.addView(box)
            }
        }
        val events = services.optJSONObject("academicCalendar")?.optJSONObject("data")?.optJSONArray("events") ?: JSONArray()
        if (events.length() > 0) {
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until events.length()) {
                val ev = events.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val title = StudentServices.localized(ev.opt("name"), language).ifEmpty { StudentServices.text(ev, "id") }
                UiHelper.add(activity, box, title, 15, UiHelper.INK, true)
                UiHelper.note(activity, box, StudentServices.text(ev, "start_date"), UiHelper.MUTED)
                tray.addView(box)
            }
        }
    }

    fun renderStaffDirectory(activity: MainActivity, content: LinearLayout, services: JSONObject) {
        val language = Language.currentLocale(activity).language
        ToolScreenUi.header(activity, content, R.string.staff_directory_title) { menu ->
            ToolScreenUi.action(menu, R.string.btn_refresh) {
                activity.refreshStudentServices("staffDirectory")
            }
        }
        UiHelper.note(activity, content, activity.getString(R.string.staff_directory_desc), UiHelper.MUTED)
        val staff = services.optJSONObject("staffDirectory")?.optJSONObject("data")?.optJSONArray("users") ?: JSONArray()
        if (staff.length() > 0) {
            val tray = UiHelper.tray(activity, content)
            for (i in 0 until staff.length()) {
                val s = staff.optJSONObject(i) ?: continue
                if (i > 0) UiHelper.trayDivider(activity, tray)
                val box = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                }
                val fullName = listOf(
                    StudentServices.text(s, "title_before"),
                    StudentServices.text(s, "first_name"),
                    StudentServices.text(s, "last_name"),
                    StudentServices.text(s, "title_after")
                ).filter { it.isNotEmpty() }.joinToString(" ")
                UiHelper.add(activity, box, fullName, 15, UiHelper.INK, true)
                val sub = listOfNotNull(
                    StudentServices.text(s, "room").takeIf { it.isNotEmpty() },
                    StudentServices.localized(s.opt("office_hours"), language).takeIf { it.isNotEmpty() }
                ).joinToString(" · ")
                if (sub.isNotEmpty()) UiHelper.note(activity, box, sub, UiHelper.MUTED)
                tray.addView(box)
            }
        }
    }
}
