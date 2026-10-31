package pl.vistula.checker.ui

import android.content.DialogInterface
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import java.util.function.Consumer
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.AttendanceTracker
import pl.vistula.checker.core.GradeSemesters
import pl.vistula.checker.core.Snapshot

/**
 * Controller for tracking course attendance and absence limits.
 * Layouts: `screen_attendance`, `item_attendance_course`, `dialog_attendance_course`.
 */
class AttendanceScreenController private constructor() {

    companion object {
        /** Everything the screen needs, derived from the saved tracker data and the current snapshot. */
        private class Overview(
            val saved: JSONObject,
            val terms: List<String>,
            val termLabels: List<String>,
            val activeTerm: String,
            val typeValues: List<String>,
            val typeLabels: List<String>,
            val activeType: String,
            val courses: Int,
            val warnings: Int,
            val exceeded: Int,
            val absences: Int
        )

        private fun readSaved(prefs: SharedPreferences): JSONObject = try {
            JSONObject(prefs.getString("data", "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }

        private fun overview(activity: MainActivity, d: JSONObject, prefs: SharedPreferences): Overview {
            val snapshot = d.optJSONObject("snapshot")?.let {
                try {
                    Snapshot.from(it)
                } catch (_: JSONException) {
                    null
                }
            }
            val saved = try {
                AttendanceTracker.reconcile(readSaved(prefs), snapshot)
            } catch (e: JSONException) {
                throw IllegalStateException(e)
            }
            prefs.edit { putString("data", saved.toString()) }

            val terms = ArrayList<String>()
            if (snapshot != null) terms.addAll(GradeSemesters.ordered(snapshot, LocalDate.now(UsosClient.ZONE)))
            for (key in saved.keys()) {
                val term = saved.optJSONObject(key)?.optString("term") ?: continue
                if (!terms.contains(term)) terms.add(term)
            }
            if (terms.isEmpty()) terms.add("")
            var activeTerm = prefs.getString("term", terms[0]) ?: terms[0]
            if (!terms.contains(activeTerm)) activeTerm = terms[0]
            prefs.edit { putString("term", activeTerm) }
            val termLabels = terms.map { id ->
                when {
                    id.isEmpty() -> activity.getString(R.string.attendance_unassigned)
                    snapshot != null && snapshot.terms.containsKey(id) -> "${snapshot.terms[id]!!.name} · $id"
                    else -> id
                }
            }

            val types = LinkedHashSet<String>()
            types.add(AttendanceFilter.TYPE_ALL)
            for (key in saved.keys()) {
                val entry = saved.optJSONObject(key) ?: continue
                if (activeTerm == entry.optString("term")) types.add(entry.optString("classType"))
            }
            var activeType = prefs.getString("classType", AttendanceFilter.TYPE_ALL) ?: AttendanceFilter.TYPE_ALL
            if (!types.contains(activeType)) activeType = AttendanceFilter.TYPE_ALL
            val typeValues = types.toList()
            val typeLabels = typeValues.map { t ->
                when {
                    t == AttendanceFilter.TYPE_ALL -> activity.getString(R.string.attendance_all_types)
                    t.isEmpty() -> activity.getString(R.string.attendance_unknown_type)
                    else -> classType(activity, t)
                }
            }

            var courses = 0
            var warnings = 0
            var exceeded = 0
            var absences = 0
            for (key in saved.keys()) {
                val item = saved.optJSONObject(key) ?: continue
                if (!matches(item, activeTerm, activeType)) continue
                courses++
                val count = item.optInt("absences", 0)
                absences += count
                if (AttendanceTracker.exceeded(count)) exceeded++ else if (AttendanceTracker.warning(count)) warnings++
            }
            return Overview(saved, terms, termLabels, activeTerm, typeValues, typeLabels, activeType, courses, warnings, exceeded, absences)
        }

        @JvmStatic
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject,
            filter: String,
            onFilterChanged: Consumer<String>,
            requestRender: Runnable
        ) {
            val prefs = activity.attendancePreferences()
            val data = overview(activity, d, prefs)
            ToolScreenUi.header(activity, content, R.string.tool_attendance_title) { menu ->
                buildMenu(activity, menu, prefs, data, filter, onFilterChanged, requestRender)
            }
            val screen = LayoutInflater.from(activity).inflate(R.layout.screen_attendance, content, false)
            content.addView(screen)

            screen.findViewById<TextView>(R.id.attendance_term).text = data.termLabels[data.terms.indexOf(data.activeTerm)]
            screen.findViewById<TextView>(R.id.attendance_stats).text =
                activity.getString(R.string.attendance_stats_summary, data.courses, data.absences)
            screen.findViewById<TextView>(R.id.attendance_alert).apply {
                when {
                    data.exceeded > 0 -> {
                        text = activity.getString(R.string.attendance_note_exceeded, data.exceeded)
                        setTextColor(activity.getColor(R.color.app_error))
                        visibility = View.VISIBLE
                    }
                    data.warnings > 0 -> {
                        text = activity.getString(R.string.attendance_note_warning, data.warnings)
                        setTextColor(activity.getColor(R.color.app_muted))
                        visibility = View.VISIBLE
                    }
                }
            }

            val list = screen.findViewById<LinearLayout>(R.id.attendance_list)
            val codes = data.saved.keys().asSequence().toList().sortedWith { a, b ->
                val nameA = data.saved.optJSONObject(a)?.optString("name", a) ?: a
                val nameB = data.saved.optJSONObject(b)?.optString("name", b) ?: b
                nameA.compareTo(nameB, ignoreCase = true)
            }
            var shown = 0
            for (code in codes) {
                val item = data.saved.optJSONObject(code) ?: continue
                if (!matches(item, data.activeTerm, data.activeType)) continue
                val count = item.optInt("absences", 0)
                if (AttendanceFilter.WARNING == filter && !AttendanceTracker.warning(count)) continue
                if (AttendanceFilter.EXCEEDED == filter && !AttendanceTracker.exceeded(count)) continue
                shown++
                list.addView(courseCard(activity, list, code, item, data.saved, prefs, requestRender))
            }
            if (shown == 0) {
                val empty = LayoutInflater.from(activity).inflate(R.layout.view_empty_state, list, false) as LinearLayout
                empty.findViewById<TextView>(R.id.empty_title).setText(R.string.attendance_empty_filter)
                empty.findViewById<TextView>(R.id.empty_description).setText(R.string.attendance_empty_desc)
                list.addView(empty)
                if (AttendanceFilter.ALL != filter) {
                    list.addView(UiHelper.button(activity, activity.getString(R.string.attendance_filter_all, data.courses), {
                        onFilterChanged.accept(AttendanceFilter.ALL)
                    }, false))
                }
            }
        }

        private fun buildMenu(
            activity: MainActivity, menu: android.view.Menu, prefs: SharedPreferences, data: Overview, filter: String,
            onFilterChanged: Consumer<String>, requestRender: Runnable
        ) {
            ToolScreenUi.action(menu, R.string.attendance_btn_add_course) { showEditCourseDialog(activity, null, requestRender) }
            val semesters = menu.addSubMenu(R.string.attendance_semester)
            data.terms.forEachIndexed { i, term ->
                semesters.add(data.termLabels[i]).setCheckable(true).setChecked(term == data.activeTerm)
                    .setOnMenuItemClickListener {
                        prefs.edit { putString("term", term) }
                        requestRender.run()
                        true
                    }
            }
            val classTypes = menu.addSubMenu(R.string.attendance_all_types)
            data.typeValues.forEachIndexed { i, type ->
                classTypes.add(data.typeLabels[i]).setCheckable(true).setChecked(type == data.activeType)
                    .setOnMenuItemClickListener {
                        prefs.edit { putString("classType", type) }
                        requestRender.run()
                        true
                    }
            }
            val statuses = menu.addSubMenu(R.string.grades_filter_type)
            val labels = arrayOf(
                activity.getString(R.string.attendance_filter_all, data.courses),
                activity.getString(R.string.attendance_filter_warning, data.warnings),
                activity.getString(R.string.attendance_filter_exceeded, data.exceeded)
            )
            AttendanceFilter.STATUSES.forEachIndexed { i, key ->
                statuses.add(labels[i]).setCheckable(true).setChecked(key == filter)
                    .setOnMenuItemClickListener {
                        onFilterChanged.accept(key)
                        true
                    }
            }
            ToolScreenUi.action(menu, R.string.tool_api_methods) { activity.openApiModule("attendance") }
            ToolScreenUi.action(menu, R.string.attendance_rule_footer) {
                MaterialAlertDialogBuilder(activity)
                    .setMessage(R.string.attendance_rule_footer)
                    .setPositiveButton(R.string.btn_close, null)
                    .show()
            }
        }

        private fun courseCard(
            activity: MainActivity, list: LinearLayout, code: String, item: JSONObject, saved: JSONObject,
            prefs: SharedPreferences, requestRender: Runnable
        ): View {
            val count = item.optInt("absences", 0)
            val limit = AttendanceTracker.LIMIT
            val card = LayoutInflater.from(activity).inflate(R.layout.item_attendance_course, list, false)
            card.setOnClickListener { showEditCourseDialog(activity, code, requestRender) }
            card.findViewById<TextView>(R.id.attendance_course_name).text = item.optString("name", code)
            card.findViewById<TextView>(R.id.attendance_course_type).text =
                if (item.optString("classType").isEmpty()) activity.getString(R.string.attendance_unknown_type)
                else classType(activity, item.optString("classType"))

            val (badgeText, badgeColor) = when {
                count > limit -> activity.getString(R.string.attendance_badge_exceeded, count, limit, count - limit) to R.color.app_error
                count == limit -> activity.getString(R.string.attendance_badge_limit_reached, count, limit) to R.color.app_warning
                count == limit - 1 && limit > 1 -> activity.getString(R.string.attendance_badge_last_one, count, limit) to R.color.app_warning
                else -> activity.getString(R.string.attendance_badge_safe, count, limit, limit - count) to R.color.app_ink
            }
            card.findViewById<TextView>(R.id.attendance_course_badge).apply {
                text = badgeText
                setTextColor(activity.getColor(badgeColor))
            }

            fun store(update: () -> Unit) {
                try {
                    update()
                    saved.put(code, item)
                    prefs.edit { putString("data", saved.toString()) }
                    requestRender.run()
                } catch (_: JSONException) {
                }
            }
            card.findViewById<MaterialButton>(R.id.attendance_minus).apply {
                isEnabled = count > 0
                setOnClickListener {
                    store {
                        item.put("absences", max(0, count - 1))
                        val lessons = item.optJSONArray("lessons")
                        if (lessons != null && lessons.length() > 0) lessons.remove(lessons.length() - 1)
                    }
                }
            }
            card.findViewById<MaterialButton>(R.id.attendance_plus).setOnClickListener {
                store {
                    item.put("absences", count + 1)
                    item.put("lessons", (item.optJSONArray("lessons") ?: JSONArray()).put(""))
                }
            }
            return card
        }

        private fun classType(activity: MainActivity, value: String): String {
            val labels = mapOf("Wykład" to R.string.class_lecture, "Ćwiczenia" to R.string.class_exercises,
                "Laboratorium" to R.string.class_lab, "Projekt" to R.string.class_project,
                "Seminarium" to R.string.class_seminar, "Lektorat" to R.string.class_language,
                "Konwersatorium" to R.string.class_discussion, "Inne" to R.string.class_other)
            return labels[value]?.let { activity.getString(it) } ?: value
        }

        private fun matches(item: JSONObject, term: String, type: String): Boolean {
            return term == item.optString("term") && (type == AttendanceFilter.TYPE_ALL || type == item.optString("classType"))
        }

        private val CLASS_TYPES = listOf("Wykład", "Ćwiczenia", "Laboratorium", "Projekt", "Seminarium", "Lektorat", "Konwersatorium", "Inne")

        @JvmStatic
        fun showEditCourseDialog(activity: MainActivity, code: String?, onComplete: Runnable) {
            val prefs = activity.attendancePreferences()
            val saved = readSaved(prefs)
            val existing = if (code != null) saved.optJSONObject(code) else null

            val form = LayoutInflater.from(activity).inflate(R.layout.dialog_attendance_course, null)
            val nameLayout = form.findViewById<TextInputLayout>(R.id.attendance_name_layout)
            val termLayout = form.findViewById<TextInputLayout>(R.id.attendance_term_layout)
            val nameField = form.findViewById<TextInputEditText>(R.id.attendance_input_name)
            val termField = form.findViewById<TextInputEditText>(R.id.attendance_input_term)
            val notesField = form.findViewById<TextInputEditText>(R.id.attendance_input_notes)
            val typeField = form.findViewById<Spinner>(R.id.attendance_input_type)

            nameField.setText(if (existing != null) existing.optString("name", code.orEmpty()) else "")
            termField.setText(if (existing != null) existing.optString("term") else prefs.getString("term", ""))
            notesField.setText(if (existing != null) existing.optString("notes", "") else "")

            val typeOptions = ArrayList(CLASS_TYPES)
            val oldType = if (existing == null) "Wykład" else existing.optString("classType")
            if (!typeOptions.contains(oldType)) typeOptions.add(oldType)
            val typeLabels = typeOptions.map { t -> if (t.isEmpty()) activity.getString(R.string.attendance_unknown_type) else classType(activity, t) }
            typeField.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, typeLabels)
            typeField.setSelection(max(0, typeOptions.indexOf(oldType)))

            val builder = MaterialAlertDialogBuilder(activity)
                .setTitle(
                    if (existing == null) activity.getString(R.string.attendance_dialog_add)
                    else activity.getString(R.string.attendance_dialog_edit, existing.optString("name", code.orEmpty()))
                )
                .setView(form)
                .setNegativeButton(activity.getString(R.string.btn_cancel), null)
                .setPositiveButton(activity.getString(R.string.btn_save), null)
            if (existing != null) {
                builder.setNeutralButton(activity.getString(R.string.attendance_dialog_delete)) { _, _ ->
                    saved.remove(code)
                    prefs.edit { putString("data", saved.toString()) }
                    onComplete.run()
                }
            }

            val dialog = builder.create()
            dialog.setOnShowListener {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val name = nameField.text?.toString()?.trim() ?: ""
                    if (name.isEmpty()) {
                        nameLayout.error = activity.getString(R.string.attendance_dialog_error_name)
                        return@setOnClickListener
                    }
                    val term = termField.text?.toString()?.trim() ?: ""
                    if (term.isEmpty()) {
                        termLayout.error = activity.getString(R.string.attendance_semester_required)
                        return@setOnClickListener
                    }
                    val type = typeOptions[typeField.selectedItemPosition]
                    if (type.isEmpty()) {
                        Toast.makeText(activity, R.string.attendance_type_required, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    try {
                        val course = if (existing != null) existing.optString("courseId", code.orEmpty()) else name
                        val c = AttendanceTracker.key(term, course, type)
                        val target = saved.optJSONObject(c)
                        if (c != code && target != null && (target.optInt("absences") > 0 || target.optString("notes").isNotEmpty())) {
                            nameLayout.error = activity.getString(R.string.attendance_duplicate)
                            return@setOnClickListener
                        }
                        val item = existing ?: JSONObject()
                        item.put("name", name)
                        item.put("limit", AttendanceTracker.LIMIT)
                            .put("courseId", course)
                            .put("term", term)
                            .put("classType", type)
                        item.put("notes", notesField.text?.toString()?.trim() ?: "")
                        if (!item.has("absences")) item.put("absences", 0)
                        if (code != null && code != c) saved.remove(code)
                        saved.put(c, item)
                        prefs.edit { putString("data", saved.toString()) }
                        dialog.dismiss()
                        onComplete.run()
                    } catch (_: JSONException) {
                    }
                }
            }
            dialog.show()
        }
    }
}
