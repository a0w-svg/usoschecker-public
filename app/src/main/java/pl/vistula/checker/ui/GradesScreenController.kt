package pl.vistula.checker.ui

import androidx.core.view.isGone
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.inputmethod.InputMethodManager
import pl.vistula.checker.ui.compose.GradeTable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.function.Consumer
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.CourseTestRecord
import pl.vistula.checker.core.GradeBrowser
import pl.vistula.checker.core.GradeSemesters
import pl.vistula.checker.core.GradeSummary
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.RecordDetails
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.StudentSearch

/**
 * Controller for rendering the Grades screen, filters, course items, tests and simulator.
 */
class GradesScreenController private constructor() {

    interface GradesListener {
        fun onFilterChanged()
        fun onOpenTask(title: String)
    }

    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject,
            state: JSONObject,
            demo: Boolean,
            semester: String,
            gradeType: String,
            gradeOrder: String,
            gradeQuery: String,
            filtersExpanded: Boolean,
            onExpandToggled: Consumer<Boolean>,
            onSemesterChanged: Consumer<String>,
            onTypeChanged: Consumer<String>,
            onOrderChanged: Consumer<String>,
            onQueryChanged: Consumer<String>,
            onResetFilters: Runnable,
            onRefresh: Runnable,
            listener: GradesListener
        ) {
            if (!demo && state.optString("error").isNotEmpty()) {
                UiHelper.note(activity, content, ApiErrorUi.cached(activity, state), Tone.ERROR)
            }

            val raw = d.optJSONObject("snapshot")
            if (raw == null) {
                UiHelper.empty(
                    activity,
                    content,
                    activity.getString(R.string.grades_no_data),
                    activity.getString(R.string.grades_no_match)
                )
                val connected = state.optString("token").isNotEmpty()
                content.addView(
                    UiHelper.button(
                        activity,
                        if (connected) activity.getString(R.string.grades_refresh) else activity.getString(R.string.btn_connect),
                        { if (connected) onRefresh.run() else activity.navigate(Tabs.ACCOUNT) },
                        true
                    )
                )
                return
            }

            val snapshot = Snapshot.from(raw)
            val today = LocalDate.now(UsosClient.ZONE)
            val terms = GradeSemesters.ordered(snapshot, today)
            val types = GradesUiState.TYPES
            val typeLabels = intArrayOf(
                R.string.grades_chip_all,
                R.string.grades_type_labs_label,
                R.string.grades_type_exercises_label,
                R.string.grades_chip_numeric,
                R.string.grades_chip_other,
                R.string.grades_chip_retakes
            )
            val orders = GradesUiState.ORDERS
            val orderLabels = intArrayOf(
                R.string.grades_sort_subject,
                R.string.grades_sort_recent,
                R.string.grades_sort_value
            )
            val screen = UiHelper.inflate(activity, R.layout.screen_grades, content)
            content.addView(screen)
            val semesterLabel = when {
                semester.isEmpty() -> activity.getString(R.string.grades_all_terms)
                snapshot.terms.containsKey(semester) && snapshot.terms[semester]!!.name.isNotEmpty() -> snapshot.terms[semester]!!.name
                else -> semester
            }
            screen.findViewById<MaterialButton>(R.id.grades_semester).apply {
                text = activity.getString(R.string.grades_semester_picker, semesterLabel)
                contentDescription = "${activity.getString(R.string.grade_picker_semester)}: $semesterLabel"
                setOnClickListener { showSemesterPicker(activity, snapshot, semester, onSemesterChanged) }
            }
            val searchAction = screen.findViewById<View>(R.id.grades_search_action)
            val more = screen.findViewById<View>(R.id.grades_more)
            if (snapshot.serviceErrors.containsKey("crstests")) {
                val reason = snapshot.serviceErrors["crstests"]
                screen.findViewById<TextView>(R.id.grades_tests_note).apply {
                    text = if ("crstests" == reason) activity.getString(R.string.services_scope_required, "crstests")
                        else activity.getString(R.string.grades_tests_unavailable)
                    visibility = View.VISIBLE
                }
            }
            if (gradeType != GradesUiState.TYPE_ALL || gradeOrder != GradesUiState.ORDER_NAME) {
                val active = ArrayList<String>()
                for (i in types.indices) {
                    if (gradeType != GradesUiState.TYPE_ALL && types[i] == gradeType) active.add(activity.getString(typeLabels[i]))
                }
                for (i in orders.indices) {
                    if (gradeOrder != GradesUiState.ORDER_NAME && orders[i] == gradeOrder) active.add(activity.getString(orderLabels[i]))
                }
                val summary = active.joinToString(" · ")
                screen.findViewById<MaterialButton>(R.id.grades_active_filters).apply {
                    text = activity.getString(R.string.tasks_filter_active, summary)
                    contentDescription = "$summary. ${activity.getString(R.string.grades_filter_reset)}"
                    isSelected = true
                    visibility = View.VISIBLE
                    setOnClickListener { onResetFilters.run() }
                }
            }
            val searchBox = screen.findViewById<TextInputLayout>(R.id.grades_search)
            searchBox.hint = activity.getString(R.string.grades_search_hint)
            val search = searchBox.findViewById<TextInputEditText>(R.id.field_input)
            search.setText(gradeQuery)
            searchBox.visibility = if (gradeQuery.isEmpty()) View.GONE else View.VISIBLE
            searchBox.setEndIconOnClickListener {
                search.setText("")
                onQueryChanged.accept("")
                activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(searchBox.windowToken, 0)
                searchBox.clearFocus()
                searchBox.visibility = View.GONE
            }
            search.setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
                activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
                search.clearFocus()
                true
            }
            searchAction.setOnClickListener {
                searchBox.visibility = View.VISIBLE
                search.requestFocus()
                search.post { activity.getSystemService(InputMethodManager::class.java)?.showSoftInput(search, 0) }
            }
            more.setOnClickListener {
                val menu = PopupMenu(activity, more)
                menu.menu.add(0, 1, 0, R.string.grades_search_action)
                val filterMenu = menu.menu.addSubMenu(R.string.grades_filter_type)
                for (i in types.indices) {
                    filterMenu.add(1, 10 + i, i, typeLabels[i]).setCheckable(true).isChecked = types[i] == gradeType
                }
                filterMenu.setGroupCheckable(1, true, true)
                val sortMenu = menu.menu.addSubMenu(R.string.grades_sort_title)
                for (i in orders.indices) {
                    sortMenu.add(2, 20 + i, i, orderLabels[i]).setCheckable(true).isChecked = orders[i] == gradeOrder
                }
                sortMenu.setGroupCheckable(2, true, true)
                menu.menu.add(0, 7, 7, R.string.grades_tests_heading).setCheckable(true).isChecked = filtersExpanded
                val visible = GradeBrowser.select(snapshot.records.values, semester, gradeType, gradeOrder, search.text.toString(), snapshot)
                menu.menu.add(0, 2, 3, R.string.grades_summary_title).isEnabled = visible.isNotEmpty()
                menu.menu.add(0, 3, 4, R.string.grades_calc_button)
                menu.menu.add(0, 4, 5, R.string.grades_refresh)
                menu.menu.add(0, 5, 6, R.string.grades_filter_reset).isEnabled = semester.isNotEmpty() ||
                    gradeType != GradesUiState.TYPE_ALL || gradeOrder != GradesUiState.ORDER_NAME || search.text.toString().trim().isNotEmpty()
                menu.menu.add(0, 6, 7, R.string.grades_data_status)
                menu.setOnMenuItemClickListener { item ->
                    val id = item.itemId
                    when {
                        id >= 10 && id < 10 + types.size -> onTypeChanged.accept(types[id - 10])
                        id >= 20 && id < 20 + orders.size -> onOrderChanged.accept(orders[id - 20])
                        id == 1 -> {
                            searchBox.visibility = View.VISIBLE
                            search.requestFocus()
                            search.post {
                                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                    .showSoftInput(search, 0)
                            }
                        }
                        id == 2 -> {
                            val body = LinearLayout(activity)
                            body.orientation = LinearLayout.VERTICAL
                            for (term in terms) {
                                val rows = GradeSemesters.inTerm(visible, term)
                                if (rows.isEmpty()) continue
                                val label = if (snapshot.terms.containsKey(term) && snapshot.terms[term]!!.name.isNotEmpty()) {
                                    snapshot.terms[term]!!.name
                                } else {
                                    term
                                }
                                UiHelper.heading(
                                    activity,
                                    body,
                                    if (label.isEmpty()) activity.getString(R.string.grades_semester_unknown) else label
                                )
                                renderGradeSummary(activity, body, rows, state, term)
                            }
                            MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.grades_summary_title)
                                .setView(UiHelper.dialogScroll(activity, body))
                                .setPositiveButton(R.string.btn_close, null)
                                .show()
                        }
                        id == 7 -> onExpandToggled.accept(!filtersExpanded)
                        id == 3 -> activity.navigate(Tabs.GPA_SIMULATOR)
                        id == 4 -> onRefresh.run()
                        id == 5 -> onResetFilters.run()
                        id == 6 -> MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.grades_data_status)
                            .setMessage(activity.getString(R.string.payments_data_freshness, UiHelper.timestamp(d.optLong("successAt"))))
                            .setPositiveButton(R.string.btn_close, null)
                            .show()
                    }
                    id != 0
                }
                menu.show()
            }

            val list = screen.findViewById<LinearLayout>(R.id.grades_list)

            val populate = Runnable {
                list.removeAllViews()
                val currentQuery = search.text.toString()
                val visible = GradeBrowser.select(snapshot.records.values, semester, gradeType, gradeOrder, currentQuery, snapshot)
                val renderedTestIds = HashSet<String>()
                var pendingCount = 0
                var extraTestCount = 0
                for (term in terms) {
                    if (semester.isNotEmpty() && semester != term) continue
                    val termRows = GradeSemesters.inTerm(visible, term)
                    val pendingCourses = GradeBrowser.ungraded(snapshot, term, gradeType, currentQuery)
                    pendingCount += pendingCourses.size
                    if (termRows.isEmpty() && pendingCourses.isEmpty() && (currentQuery.trim().isNotEmpty() || gradeType != GradesUiState.TYPE_ALL)) continue
                    val label = when {
                        term.isEmpty() -> activity.getString(R.string.grades_semester_unknown)
                        snapshot.terms.containsKey(term) && snapshot.terms[term]!!.name.isNotEmpty() -> snapshot.terms[term]!!.name
                        else -> term
                    }
                    val section = if (semester.isEmpty()) {
                        semesterSection(activity, list, term, label, termRows.size + pendingCourses.size, true)
                    } else {
                        LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; list.addView(this) }
                    }
                    val columns = GradeTable.columns(termRows, snapshot)
                    val scale = activity.resources.configuration.fontScale
                    val columnPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 18f, activity.resources.displayMetrics)
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    val courseGroups = GradeBrowser.groups(termRows)
                    val widths = columns.associateWith { column ->
                        val labels = listOf(GradeTable.fullLabel(column, Language.currentLocale(activity).language)) + courseGroups.map { GradeTable.values(it, column, snapshot) }
                        maxOf(UiHelper.dp(activity, 48), labels.maxOf { columnPaint.measureText(it) }.toInt() + UiHelper.dp(activity, 16))
                    }
                    val available = activity.resources.displayMetrics.widthPixels - UiHelper.dp(activity, 72)
                    val stacked = activity.resources.configuration.screenWidthDp < 600 || scale > 1.3f || widths.values.sum() + UiHelper.dp(activity, 220) > available
                    val table = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    if (!stacked && termRows.isNotEmpty()) {
                        val headings = LinearLayout(activity).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 8), UiHelper.dp(activity, 16), UiHelper.dp(activity, 8))
                        }
                        headings.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
                        columns.forEach { column ->
                            headings.addView(UiHelper.text(activity, GradeTable.fullLabel(column, Language.currentLocale(activity).language), 13, Tone.MUTED, false).apply {
                                gravity = Gravity.CENTER
                            }, LinearLayout.LayoutParams(widths.getValue(column), -2))
                        }
                        section.addView(headings)
                    }
                    UiHelper.tray(activity, section).addView(table, LinearLayout.LayoutParams(-1, -2))
                    for (group in courseGroups) {
                        renderCourseGroup(activity, table, group, snapshot, renderedTestIds, listener, columns, widths, stacked)
                    }
                    for (course in pendingCourses) {
                        renderUngradedCourse(activity, section, course)
                    }
                    if (termRows.isEmpty() && pendingCourses.isEmpty()) {
                        val hasGrades = snapshot.records.values.any { row -> row.kind == "ocena" && row.term == term }
                        UiHelper.note(
                            activity,
                            section,
                            activity.getString(if (hasGrades) R.string.grades_semester_empty else R.string.grades_semester_no_grades),
                            Tone.MUTED
                        )
                    }
                }

                if (filtersExpanded && snapshot.tests.isNotEmpty()) {
                    val unassigned = ArrayList<CourseTestRecord>()
                    for (ctr in snapshot.tests) {
                        val courseVisible = visible.any { r ->
                            (ctr.courseId.isNotEmpty() && ctr.courseId == r.optMeta("courseId")) ||
                                (ctr.courseName.isNotEmpty() && ctr.courseName == r.title)
                        }
                        val matchesTest = StudentSearch.matches(currentQuery, ctr.courseName, ctr.testName, ctr.grade, ctr.date)
                        if (!renderedTestIds.contains(ctr.id) &&
                            (semester.isEmpty() || semester == GradeSemesters.testTerm(snapshot, ctr)) &&
                            (courseVisible || (gradeType == GradesUiState.TYPE_ALL && matchesTest))
                        ) {
                            unassigned.add(ctr)
                            extraTestCount++
                        }
                    }
                    if (unassigned.isNotEmpty()) {
                        val grouped = LinkedHashMap<String, MutableList<CourseTestRecord>>()
                        for (test in unassigned) {
                            val term = GradeSemesters.testTerm(snapshot, test)
                            grouped.getOrPut(term ?: "") { ArrayList() }.add(test)
                        }
                        for ((term, groupValues) in grouped) {
                            val heading = if (term.isEmpty()) {
                                activity.getString(R.string.grades_unassigned_tests)
                            } else {
                                activity.getString(R.string.grades_tests_heading) + " · " +
                                    (if (snapshot.terms.containsKey(term)) snapshot.terms[term]!!.name else term)
                            }
                            val testsSection = semesterSection(activity, list, "\u0000tests$term", heading, groupValues.size, term.isNotEmpty())
                            for (ctr in groupValues) {
                                val testBox = UiHelper.section(activity, testsSection)
                                UiHelper.add(activity, testBox, ctr.testName, 17, Tone.INK, true)
                                UiHelper.add(activity, testBox, ctr.courseName, 13, Tone.INK, true)
                                if (ctr.maxPoints > 0) {
                                    UiHelper.add(
                                        activity,
                                        testBox,
                                        String.format(
                                            Language.currentLocale(activity),
                                            activity.getString(R.string.grades_points_format),
                                            ctr.points,
                                            ctr.maxPoints,
                                            ctr.percentage()
                                        ),
                                        15,
                                        Tone.INK,
                                        false
                                    )
                                } else if (!ctr.points.isNaN() && ctr.points > 0) {
                                    UiHelper.add(
                                        activity,
                                        testBox,
                                        String.format(
                                            Language.currentLocale(activity),
                                            activity.getString(R.string.grades_points_simple),
                                            ctr.points
                                        ),
                                        15,
                                        Tone.INK,
                                        false
                                    )
                                }
                                if (ctr.grade.isNotEmpty()) {
                                    UiHelper.add(activity, testBox, activity.getString(R.string.grades_test_grade, ctr.grade), 14, Tone.INK, true)
                                }
                                if (ctr.date.isNotEmpty()) {
                                    UiHelper.add(activity, testBox, activity.getString(R.string.grades_test_date, ctr.date), 12, Tone.MUTED, false)
                                }
                            }
                        }
                    }
                }

                if (visible.isEmpty() && pendingCount == 0 && extraTestCount == 0 && (terms.isEmpty() || currentQuery.trim().isNotEmpty())) {
                    UiHelper.empty(
                        activity, list,
                        activity.getString(if (currentQuery.trim().isEmpty()) R.string.grades_empty_title else R.string.grades_no_match),
                        activity.getString(R.string.grades_no_match_hint)
                    )
                }
            }

            UiHelper.watchSearch(search) { value ->
                onQueryChanged.accept(value)
                populate.run()
            }
            populate.run()
        }

        private fun renderUngradedCourse(activity: MainActivity, parent: LinearLayout, course: Record) {
            val row = LayoutInflater.from(activity).inflate(R.layout.item_grade_card, parent, false)
            row.findViewById<TextView>(R.id.grade_item_course_name).text = course.title
            val value = row.findViewById<TextView>(R.id.grade_item_value_badge)
            value.text = "—"
            value.setTone(Tone.MUTED)
            row.findViewById<TextView>(R.id.grade_item_course_meta).setText(R.string.grades_no_grade)
            row.setOnClickListener {
                val body = LinearLayout(activity)
                body.orientation = LinearLayout.VERTICAL
                body.setPadding(UiHelper.dp(activity, 24), 0, UiHelper.dp(activity, 24), UiHelper.dp(activity, 16))
                UiHelper.add(activity, body, activity.getString(R.string.grades_no_grade), 15, Tone.MUTED, false)
                if (course.optMeta("courseId").isNotEmpty()) {
                    UiHelper.add(activity, body, course.optMeta("courseId"), 14, Tone.INK, false)
                }
                if (course.ects.isFinite()) {
                    UiHelper.add(
                        activity,
                        body,
                        activity.getString(
                            R.string.grade_ects_points,
                            String.format(Language.currentLocale(activity), "%s", course.ects)
                        ),
                        14,
                        Tone.INK,
                        false
                    )
                }
                MaterialAlertDialogBuilder(activity)
                    .setTitle(course.title)
                    .setView(body)
                    .setPositiveButton(R.string.btn_close, null)
                    .show()
            }
            ViewCompat.setScreenReaderFocusable(row, true)
            parent.addView(row)
        }

        private fun semesterSection(
            activity: MainActivity,
            parent: LinearLayout,
            key: String,
            title: String,
            count: Int,
            defaultExpanded: Boolean
        ): LinearLayout {
            val section = LinearLayout(activity)
            section.orientation = LinearLayout.VERTICAL
            section.setBackgroundResource(R.drawable.bg_card_white)
            section.setPadding(UiHelper.dp(activity, 8), 0, UiHelper.dp(activity, 8), 0)
            var expanded = activity.gradeSemesterExpansion().getOrDefault(key, defaultExpanded)
            val header = arrayOfNulls<Button>(1)
            val update = Runnable {
                section.visibility = if (expanded) View.VISIBLE else View.GONE
                header[0]!!.text = activity.getString(R.string.design_toggle, title, if (expanded) "▾" else "▸")
                header[0]!!.contentDescription = activity.getString(
                    R.string.grades_semester_toggle,
                    title,
                    count,
                    activity.getString(if (expanded) R.string.grades_semester_collapse else R.string.grades_semester_expand)
                )
            }
            header[0] = UiHelper.button(
                activity,
                "",
                {
                    expanded = !expanded
                    activity.gradeSemesterExpansion()[key] = expanded
                    update.run()
                },
                false
            )
            header[0]!!.backgroundTintList = activity.getColorStateList(android.R.color.transparent)
            header[0]!!.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            header[0]!!.textSize = 13f
            header[0]!!.setTone(Tone.MUTED)
            header[0]!!.setPadding(UiHelper.dp(activity, 8), 0, UiHelper.dp(activity, 8), 0)
            header[0]!!.maxLines = Int.MAX_VALUE
            ViewCompat.setAccessibilityHeading(header[0]!!, true)
            parent.addView(header[0])
            parent.addView(section)
            update.run()
            return section
        }

        private fun showSemesterPicker(
            activity: MainActivity,
            snapshot: Snapshot,
            selected: String,
            onSelected: Consumer<String>
        ) {
            val values = ArrayList<String>()
            values.add("")
            for (term in GradeSemesters.ordered(snapshot, LocalDate.now(UsosClient.ZONE))) {
                if (term.isNotEmpty()) values.add(term)
            }
            val labels = Array(values.size) { i ->
                val term = values[i]
                when {
                    term.isEmpty() -> activity.getString(R.string.grades_all_terms)
                    snapshot.terms.containsKey(term) && snapshot.terms[term]!!.name.isNotEmpty() -> snapshot.terms[term]!!.name
                    else -> term
                }
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.grade_picker_semester)
                .setSingleChoiceItems(labels, values.indexOf(selected)) { dialog, index ->
                    dialog.dismiss()
                    onSelected.accept(values[index])
                }
                .setNegativeButton(R.string.btn_close, null)
                .show()
        }

        private fun renderCourseGroup(
            activity: MainActivity,
            parent: LinearLayout,
            rows: List<Record>,
            snapshot: Snapshot,
            renderedTestIds: MutableSet<String>,
            listener: GradesListener,
            columns: List<String>,
            widths: Map<String, Int>,
            stacked: Boolean
        ) {
            val first = rows[0]
            val row = LayoutInflater.from(activity).inflate(R.layout.item_grade_card, parent, false)
            row.findViewById<TextView>(R.id.grade_item_course_name).apply {
                text = first.title
                textSize = 17f
                setLineSpacing(UiHelper.dp(activity, 3).toFloat(), 1f)
            }
            val values = row.findViewById<TextView>(R.id.grade_item_value_badge)
            val horizontal = values.parent as LinearLayout
            horizontal.setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 16), UiHelper.dp(activity, 16), UiHelper.dp(activity, 16))
            val valueIndex = horizontal.indexOfChild(values)
            horizontal.removeView(values)
            // The chevron is redundant: the complete course row is the detail action.
            while (horizontal.childCount > valueIndex) horizontal.removeViewAt(valueIndex)
            val firstValueColumn = columns.firstOrNull { GradeTable.values(rows, it, snapshot) != "—" }
            fun cell(column: String) = UiHelper.text(activity, GradeTable.values(rows, column, snapshot), if (stacked) 20 else 18, Tone.INK, true).apply {
                if (column == firstValueColumn) id = R.id.grade_item_value_badge
                gravity = Gravity.CENTER
                contentDescription = GradeTable.fullLabel(column, Language.currentLocale(activity).language) + ": " + text
            }
            if (stacked) {
                horizontal.orientation = LinearLayout.VERTICAL
                horizontal.getChildAt(0).layoutParams = LinearLayout.LayoutParams(-1, -2)
                val cellsPerRow = if (activity.resources.configuration.fontScale > 1.3f || activity.resources.configuration.screenWidthDp < 360) 2 else 3
                columns.chunked(cellsPerRow).forEach { chunk ->
                    val assessments = LinearLayout(activity).apply { setPadding(0, UiHelper.dp(activity, 12), 0, 0) }
                    chunk.forEach { column ->
                        val assessment = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                            setPadding(UiHelper.dp(activity, 6), UiHelper.dp(activity, 4), UiHelper.dp(activity, 6), UiHelper.dp(activity, 4))
                        }
                        assessment.addView(UiHelper.text(activity, GradeTable.fullLabel(column, Language.currentLocale(activity).language), 13, Tone.MUTED, false).apply {
                            gravity = Gravity.CENTER
                            minLines = 2
                            setPadding(0, 0, 0, UiHelper.dp(activity, 4))
                        })
                        assessment.addView(cell(column), LinearLayout.LayoutParams(-1, -2))
                        assessments.addView(assessment, LinearLayout.LayoutParams(0, -2, 1f))
                    }
                    repeat(cellsPerRow - chunk.size) { assessments.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f)) }
                    horizontal.addView(assessments, LinearLayout.LayoutParams(-1, -2))
                }
            } else columns.forEach { column ->
                horizontal.addView(cell(column), LinearLayout.LayoutParams(widths.getValue(column), -2))
            }
            val meta = row.findViewById<TextView>(R.id.grade_item_course_meta)
            val forms = rows.map { gradeFormLabel(activity, it, snapshot) }.distinct()
            meta.text = forms.joinToString(" · ")
            meta.visibility = View.GONE
            row.contentDescription = first.title + ". " + rows.joinToString(", ") { gradeFormLabel(activity, it, snapshot) + ": " + GradeBrowser.symbol(it) }
            row.setOnClickListener {
                val all = ArrayList<Record>()
                for (candidate in snapshot.records.values) {
                    if (candidate.kind == "ocena" && candidate.term == first.term &&
                        (if (first.optMeta("courseId").isNotEmpty()) {
                            first.optMeta("courseId") == candidate.optMeta("courseId")
                        } else {
                            first.title == candidate.title
                        })
                    ) {
                        all.add(candidate)
                    }
                }
                showGradeGroupDetails(activity, all, snapshot, listener)
            }
            ViewCompat.setScreenReaderFocusable(row, true)
            for (test in snapshot.tests) {
                if (first.term == GradeSemesters.testTerm(snapshot, test) &&
                    ((first.optMeta("courseId").isNotEmpty() && first.optMeta("courseId") == test.courseId) ||
                        first.title == test.courseName)
                ) {
                    renderedTestIds.add(test.id)
                }
            }
            parent.addView(row)
        }

        private fun gradeFormLabel(activity: MainActivity, row: Record, snapshot: Snapshot): String {
            val label = GradeBrowser.classType(row, snapshot)
            if (label == "Ocena z protokołu przedmiotu") return activity.getString(R.string.grades_scope_course)
            if (label == "Ocena z protokołu zajęć") return activity.getString(R.string.grades_scope_unit)
            if (label == "Typ zajęć niepodany") return activity.getString(R.string.grades_type_unknown)
            return label
        }

        private fun showGradeGroupDetails(
            activity: MainActivity,
            rows: List<Record>,
            snapshot: Snapshot,
            listener: GradesListener
        ) {
            val body = LinearLayout(activity)
            body.orientation = LinearLayout.VERTICAL
            body.setPadding(UiHelper.dp(activity, 20), UiHelper.dp(activity, 8), UiHelper.dp(activity, 20), UiHelper.dp(activity, 8))
            val copied = StringBuilder()
            for (row in rows) {
                UiHelper.heading(activity, body, "${GradeBrowser.symbol(row)} · ${GradeBrowser.classType(row, snapshot)}")
                for ((key, value) in RecordDetails.fields(row, snapshot)) {
                    UiHelper.detailField(activity, body, key, value)
                    copied.append(key).append(": ").append(value).append("\n")
                }
                try {
                    val raw = row.json().toString(2)
                    copied.append(raw).append("\n\n")
                    val rawView = UiHelper.text(activity, raw, 12, Tone.INK, false)
                    rawView.typeface = Typeface.MONOSPACE
                    rawView.setTextIsSelectable(true)
                    rawView.visibility = View.GONE
                    body.addView(
                        UiHelper.button(
                            activity,
                            activity.getString(R.string.grades_full_data),
                            { rawView.visibility = if (rawView.isGone) View.VISIBLE else View.GONE },
                            false
                        )
                    )
                    body.addView(rawView)
                } catch (e: Exception) {
                    pl.vistula.checker.Diagnostics.swallowed("GradesScreenController", e)
                }
            }
            val courseIds = HashSet<String>()
            for (row in rows) {
                if (row.optMeta("courseId").isNotEmpty()) courseIds.add(row.optMeta("courseId"))
            }
            for (test in snapshot.tests) {
                if ((courseIds.contains(test.courseId) || rows[0].title == test.courseName) &&
                    rows[0].term == GradeSemesters.testTerm(snapshot, test)
                ) {
                    UiHelper.heading(activity, body, test.testName)
                    try {
                        if (test.maxPoints > 0) {
                            UiHelper.add(
                                activity,
                                body,
                                String.format(
                                    Language.currentLocale(activity),
                                    activity.getString(R.string.grades_points_format),
                                    test.points,
                                    test.maxPoints,
                                    test.percentage()
                                ),
                                15,
                                Tone.INK,
                                false
                            )
                        } else if (test.hasPoints) {
                            UiHelper.add(activity, body, activity.getString(R.string.grades_points_without_max, test.points), 15, Tone.INK, false)
                        }
                        if (test.grade.isNotEmpty()) {
                            UiHelper.add(activity, body, activity.getString(R.string.grades_test_grade, test.grade), 15, Tone.INK, true)
                        }
                        if (test.date.isNotEmpty()) {
                            UiHelper.note(activity, body, activity.getString(R.string.grades_test_date, test.date), Tone.MUTED)
                        }
                        val text = UiHelper.text(activity, test.json().toString(2), 13, Tone.INK, false)
                        text.setTextIsSelectable(true)
                        text.visibility = View.GONE
                        body.addView(
                            UiHelper.button(
                                activity,
                                activity.getString(R.string.grades_full_data),
                                { text.visibility = if (text.isGone) View.VISIBLE else View.GONE },
                                false
                            )
                        )
                        body.addView(text)
                        copied.append(test.json().toString(2)).append("\n")
                    } catch (e: Exception) {
                        pl.vistula.checker.Diagnostics.swallowed("GradesScreenController", e)
                    }
                }
            }
            val detailDialog = arrayOfNulls<AlertDialog>(1)
            val copy = UiHelper.button(
                activity,
                activity.getString(R.string.services_copy),
                { UiHelper.copyToClipboard(activity, rows[0].title, copied.toString()) },
                false
            )
            body.addView(copy, 0)
            val task = UiHelper.button(
                activity,
                activity.getString(R.string.grades_btn_add_task),
                {
                    detailDialog[0]?.dismiss()
                    listener.onOpenTask(rows[0].title)
                },
                false
            )
            body.addView(task, 1)
            detailDialog[0] = MaterialAlertDialogBuilder(activity)
                .setTitle(rows[0].title)
                .setView(UiHelper.dialogScroll(activity, body))
                .setPositiveButton(R.string.btn_close, null)
                .create()
            detailDialog[0]!!.show()
        }

        private fun renderGradeSummary(
            activity: MainActivity,
            parent: LinearLayout,
            rows: List<Record>,
            state: JSONObject,
            currentSemester: String
        ) {
            if (rows.isEmpty()) return
            val summary = GradeSummary.of(rows)
            val box = UiHelper.inflate(activity, R.layout.view_grades_summary, parent)
            box.backgroundTintList = ColorStateList.valueOf(activity.getColor(Surface.SUCCESS.color))
            val locale = Language.currentLocale(activity)

            box.findViewById<View>(R.id.grades_summary_help).setOnClickListener {
                var statistics = if (summary.numeric == 0) {
                    activity.getString(R.string.grades_no_numeric_stats)
                } else {
                    String.format(locale, activity.getString(R.string.grades_stats_simple), summary.average, summary.min, summary.max)
                }
                if (!summary.ectsAverage.isNaN()) {
                    statistics += String.format(locale, activity.getString(R.string.grades_stats_weighted), summary.ectsAverage, summary.totalEcts)
                }
                MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.grades_summary_title))
                    .setMessage(statistics + activity.getString(R.string.grades_stats_disclaimer))
                    .setPositiveButton(activity.getString(R.string.btn_understand), null)
                    .show()
            }
            box.findViewById<TextView>(R.id.grades_summary_average).text =
                if (summary.numeric == 0) "—" else String.format(locale, "%.2f", summary.average)
            box.findViewById<TextView>(R.id.grades_summary_kind).setText(
                if (summary.numeric == 0) R.string.grades_card_no_numeric else R.string.grades_card_simple_avg
            )
            if (!summary.ectsAverage.isNaN()) {
                box.findViewById<TextView>(R.id.grades_summary_weighted).apply {
                    text = String.format(locale, activity.getString(R.string.grades_card_weighted_avg), summary.ectsAverage, summary.totalEcts)
                    visibility = View.VISIBLE
                }
            }
            box.findViewById<TextView>(R.id.grades_summary_counts).text =
                activity.getString(R.string.grades_card_counts, summary.numeric, summary.other)
            box.findViewById<View>(R.id.grades_summary_calc).setOnClickListener { showGradeSimulatorDialog(activity, summary) }
            box.findViewById<View>(R.id.grades_summary_share).setOnClickListener {
                shareGradesReport(activity, rows, summary, state, currentSemester)
            }
            parent.addView(box)
        }

        private fun shareGradesReport(
            activity: MainActivity,
            rows: List<Record>?,
            summary: GradeSummary,
            state: JSONObject,
            currentSemester: String
        ) {
            if (rows.isNullOrEmpty()) return
            val sb = StringBuilder()
            sb.append(activity.getString(R.string.grades_share_header))
            val studentNr = state.optString("studentNumber", "")
            if (studentNr.isNotEmpty()) {
                sb.append(activity.getString(R.string.account_album_number, studentNr)).append("\n")
            }
            sb.append(
                activity.getString(
                    R.string.grades_share_term,
                    if (currentSemester.isEmpty()) activity.getString(R.string.grades_all_terms) else currentSemester
                )
            ).append("\n")
            if (!summary.ectsAverage.isNaN()) {
                sb.append(
                    String.format(
                        Language.currentLocale(activity),
                        activity.getString(R.string.grades_share_weighted),
                        summary.ectsAverage,
                        summary.totalEcts
                    )
                )
            }
            if (summary.numeric > 0) {
                sb.append(
                    String.format(
                        Language.currentLocale(activity),
                        activity.getString(R.string.grades_share_arithmetic),
                        summary.average
                    )
                )
            }
            sb.append(activity.getString(R.string.grades_share_courses_header))
            for (group in GradeBrowser.groups(rows)) {
                val first = group[0]
                sb.append("• ").append(first.title).append(":\n")
                for (r in group) {
                    sb.append("   - ").append(GradeBrowser.classType(r, null)).append(": ")
                        .append(GradeBrowser.symbol(r)).append("\n")
                }
            }
            sb.append("\n").append(activity.getString(R.string.share_generated_by))

            val share = Intent(Intent.ACTION_SEND).setType("text/plain")
            share.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.grades_share_subject))
            share.putExtra(Intent.EXTRA_TEXT, sb.toString())
            try {
                activity.startActivity(Intent.createChooser(share, activity.getString(R.string.grades_share_title)))
            } catch (_: ActivityNotFoundException) {
                UiHelper.copyToClipboard(activity, activity.getString(R.string.grades_clipboard_label), sb.toString())
            }
        }

        private fun showGradeSimulatorDialog(activity: MainActivity, summary: GradeSummary) {
            val dialogLayout = UiHelper.inflate(activity, R.layout.dialog_grade_simulator, LinearLayout(activity)) as LinearLayout

            val currentAvg = if (summary.ectsAverage.isNaN()) summary.average else summary.ectsAverage
            val currentAvgStr = if (currentAvg.isNaN()) {
                activity.getString(R.string.grades_sim_current_none)
            } else {
                String.format(Language.currentLocale(activity), "%.2f", currentAvg)
            }
            dialogLayout.findViewById<TextView>(R.id.sim_current).text = activity.getString(R.string.grades_sim_current, currentAvgStr)

            val fields = dialogLayout.findViewById<LinearLayout>(R.id.sim_fields)
            val targetField = UiHelper.field(activity, fields, activity.getString(R.string.grades_sim_target_hint), "4.50", false)
            val countField = UiHelper.field(activity, fields, activity.getString(R.string.grades_sim_count_hint), "2", false)
            countField.inputType = InputType.TYPE_CLASS_NUMBER
            val ectsField = UiHelper.field(activity, fields, activity.getString(R.string.grades_sim_ects_hint), "6", false)
            ectsField.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

            val resultView = dialogLayout.findViewById<TextView>(R.id.sim_result)
            resultView.setText(R.string.grades_sim_intro)

            val compute = Runnable {
                try {
                    val target = targetField.text.toString().replace(',', '.').toDouble()
                    val count = countField.text.toString().toInt()
                    val reqSimple = summary.requiredAverage(target, count)

                    val ectsPoints = ectsField.text.toString().replace(',', '.').toDouble()
                    val reqEcts = summary.requiredEctsGrade(target, ectsPoints)

                    val sb = StringBuilder()
                    if (!reqSimple.isNaN()) {
                        sb.append(
                            String.format(
                                Language.currentLocale(activity),
                                activity.getString(R.string.grades_sim_req_simple),
                                count,
                                reqSimple
                            )
                        )
                        if (reqSimple > 5.0) sb.append(activity.getString(R.string.grades_sim_impossible))
                        else if (reqSimple <= 3.0) sb.append(activity.getString(R.string.grades_sim_already_reached))
                    }
                    if (!reqEcts.isNaN() && !summary.ectsAverage.isNaN()) {
                        if (sb.isNotEmpty()) sb.append("\n\n")
                        sb.append(
                            String.format(
                                Language.currentLocale(activity),
                                activity.getString(R.string.grades_sim_req_weighted),
                                ectsPoints,
                                reqEcts
                            )
                        )
                        if (reqEcts > 5.0) sb.append(activity.getString(R.string.grades_sim_impossible))
                        else if (reqEcts <= 3.0) sb.append(activity.getString(R.string.grades_sim_already_reached))
                    }
                    resultView.text = sb.toString()
                } catch (_: Exception) {
                    resultView.text = activity.getString(R.string.grades_sim_input_error)
                }
            }

            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    compute.run()
                }

                override fun afterTextChanged(s: Editable) {}
            }
            targetField.addTextChangedListener(watcher)
            countField.addTextChangedListener(watcher)
            ectsField.addTextChangedListener(watcher)
            compute.run()

            MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.grades_simulator_title))
                .setView(dialogLayout)
                .setPositiveButton(activity.getString(R.string.btn_close), null)
                .show()
        }
    }
}

