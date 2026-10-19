package pl.vistula.checker.ui

import android.content.DialogInterface
import android.graphics.Color
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
 */
class AttendanceScreenController private constructor() {

    companion object {
        @JvmStatic
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject,
            filter: String,
            onFilterChanged: Consumer<String>,
            requestRender: Runnable
        ) {
            val headerIndex = content.childCount
            val prefs = activity.attendancePreferences()
            var saved = try {
                JSONObject(prefs.getString("data", "{}") ?: "{}")
            } catch (_: Exception) {
                JSONObject()
            }

            val raw = d.optJSONObject("snapshot")
            var snapshot: Snapshot? = null
            if (raw != null) {
                try {
                    snapshot = Snapshot.from(raw)
                } catch (_: JSONException) {
                }
            }
            saved = try {
                AttendanceTracker.reconcile(saved, snapshot)
            } catch (e: JSONException) {
                throw IllegalStateException(e)
            }
            prefs.edit().putString("data", saved.toString()).apply()
            val terms = ArrayList<String>()
            if (snapshot != null) {
                terms.addAll(GradeSemesters.ordered(snapshot, LocalDate.now(UsosClient.ZONE)))
            }
            var savedKeys = saved.keys()
            while (savedKeys.hasNext()) {
                val entry = saved.optJSONObject(savedKeys.next())
                if (entry != null && !terms.contains(entry.optString("term"))) {
                    terms.add(entry.optString("term"))
                }
            }
            if (terms.isEmpty()) terms.add("")
            var selectedTerm = prefs.getString("term", terms[0]) ?: terms[0]
            if (!terms.contains(selectedTerm)) selectedTerm = terms[0]
            val activeTerm = selectedTerm
            val termLabels = Array(terms.size) { i ->
                val id = terms[i]
                when {
                    id.isEmpty() -> activity.getString(R.string.attendance_unassigned)
                    snapshot != null && snapshot.terms.containsKey(id) -> "${snapshot.terms[id]!!.name} · $id"
                    else -> id
                }
            }
            prefs.edit().putString("term", activeTerm).apply()
            var activeType = prefs.getString("classType", "all") ?: "all"
            val types = LinkedHashSet<String>()
            types.add("all")
            savedKeys = saved.keys()
            while (savedKeys.hasNext()) {
                val entry = saved.optJSONObject(savedKeys.next())
                if (entry != null && activeTerm == entry.optString("term")) {
                    types.add(entry.optString("classType"))
                }
            }
            if (!types.contains(activeType)) activeType = "all"
            val selectedType = activeType
            val typeValues = types.toTypedArray()
            val typeLabels = Array(typeValues.size) { i ->
                val t = typeValues[i]
                when {
                    t == "all" -> activity.getString(R.string.attendance_all_types)
                    t.isEmpty() -> activity.getString(R.string.attendance_unknown_type)
                    else -> t
                }
            }
            var totalCourses = 0
            var warningCount = 0
            var exceededCount = 0
            var totalAbsences = 0
            var keys = saved.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = saved.optJSONObject(key) ?: continue
                if (!matches(item, activeTerm, selectedType)) continue
                totalCourses++
                val count = item.optInt("absences", 0)
                totalAbsences += count
                if (AttendanceTracker.exceeded(count)) exceededCount++
                else if (AttendanceTracker.warning(count)) warningCount++
            }

            val courses = totalCourses
            val warnings = warningCount
            val exceeded = exceededCount
            ToolScreenUi.header(activity, content, R.string.tool_attendance_title) { menu ->
                ToolScreenUi.action(menu, R.string.attendance_btn_add_course) {
                    showEditCourseDialog(activity, null, requestRender)
                }
                val semesters = menu.addSubMenu(R.string.attendance_semester)
                for (i in terms.indices) {
                    val next = terms[i]
                    semesters.add(termLabels[i]).setCheckable(true).setChecked(next == activeTerm)
                        .setOnMenuItemClickListener {
                            prefs.edit().putString("term", next).apply()
                            requestRender.run()
                            true
                        }
                }
                val classTypes = menu.addSubMenu(R.string.attendance_all_types)
                for (i in typeValues.indices) {
                    val next = typeValues[i]
                    classTypes.add(typeLabels[i]).setCheckable(true).setChecked(next == selectedType)
                        .setOnMenuItemClickListener {
                            prefs.edit().putString("classType", next).apply()
                            requestRender.run()
                            true
                        }
                }
                val statuses = menu.addSubMenu(R.string.grades_filter_type)
                val statusKeys = arrayOf("all", "warning", "exceeded")
                val labels = arrayOf(
                    activity.getString(R.string.attendance_filter_all, courses),
                    activity.getString(R.string.attendance_filter_warning, warnings),
                    activity.getString(R.string.attendance_filter_exceeded, exceeded)
                )
                for (i in statusKeys.indices) {
                    val key = statusKeys[i]
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
            val heading = content.getChildAt(content.childCount - 1)
            content.removeView(heading)
            content.addView(heading, headerIndex)
            UiHelper.note(activity, content, termLabels[terms.indexOf(activeTerm)], UiHelper.MUTED)
            UiHelper.note(activity, content, activity.getString(R.string.attendance_stats_summary, totalCourses, totalAbsences), UiHelper.MUTED)
            if (exceededCount > 0) {
                UiHelper.note(activity, content, activity.getString(R.string.attendance_note_exceeded, exceededCount), activity.getColor(R.color.app_error))
            } else if (warningCount > 0) {
                UiHelper.note(activity, content, activity.getString(R.string.attendance_note_warning, warningCount), UiHelper.MUTED)
            }

            val list = LinearLayout(activity)
            list.orientation = LinearLayout.VERTICAL
            content.addView(list)

            val sortedKeys = ArrayList<String>()
            keys = saved.keys()
            while (keys.hasNext()) sortedKeys.add(keys.next())
            val finalSavedRef = saved
            sortedKeys.sortWith { a, b ->
                val objA = finalSavedRef.optJSONObject(a)
                val objB = finalSavedRef.optJSONObject(b)
                val nameA = objA?.optString("name", a) ?: a
                val nameB = objB?.optString("name", b) ?: b
                nameA.compareTo(nameB, ignoreCase = true)
            }

            var shown = 0
            for (code in sortedKeys) {
                val item = saved.optJSONObject(code)
                if (item == null || !matches(item, activeTerm, selectedType)) continue
                val count = item.optInt("absences", 0)
                val limit = AttendanceTracker.LIMIT
                val name = item.optString("name", code)

                if ("warning" == filter && !AttendanceTracker.warning(count)) continue
                if ("exceeded" == filter && !AttendanceTracker.exceeded(count)) continue

                shown++
                val itemCard = UiHelper.section(activity, list, Color.WHITE)
                itemCard.isClickable = true
                itemCard.isFocusable = true
                itemCard.setOnClickListener { showEditCourseDialog(activity, code, requestRender) }
                UiHelper.add(activity, itemCard, name, 17, UiHelper.INK, true)
                UiHelper.note(
                    activity,
                    itemCard,
                    if (item.optString("classType").isEmpty()) {
                        activity.getString(R.string.attendance_unknown_type)
                    } else {
                        item.optString("classType")
                    },
                    UiHelper.MUTED
                )

                val badgeText: String
                val badgeColor: Int
                when {
                    count > limit -> {
                        badgeText = activity.getString(R.string.attendance_badge_exceeded, count, limit, count - limit)
                        badgeColor = Color.rgb(160, 48, 40)
                    }
                    count == limit -> {
                        badgeText = activity.getString(R.string.attendance_badge_limit_reached, count, limit)
                        badgeColor = Color.rgb(180, 83, 9)
                    }
                    count == limit - 1 && limit > 1 -> {
                        badgeText = activity.getString(R.string.attendance_badge_last_one, count, limit)
                        badgeColor = Color.rgb(217, 119, 6)
                    }
                    else -> {
                        badgeText = activity.getString(R.string.attendance_badge_safe, count, limit, limit - count)
                        badgeColor = UiHelper.GREEN
                    }
                }
                UiHelper.add(activity, itemCard, badgeText, 14, badgeColor, true)

                val actions = LinearLayout(activity)
                actions.orientation = LinearLayout.HORIZONTAL

                val minusBtn = UiHelper.button(
                    activity,
                    activity.getString(R.string.attendance_btn_minus),
                    {
                        val newCount = max(0, count - 1)
                        try {
                            item.put("absences", newCount)
                            val lessons = item.optJSONArray("lessons")
                            if (lessons != null && lessons.length() > 0) {
                                lessons.remove(lessons.length() - 1)
                            }
                            finalSavedRef.put(code, item)
                            prefs.edit().putString("data", finalSavedRef.toString()).apply()
                            requestRender.run()
                        } catch (_: JSONException) {
                        }
                    },
                    false
                )
                minusBtn.isEnabled = count > 0

                val plusBtn = UiHelper.button(
                    activity,
                    activity.getString(R.string.attendance_btn_plus),
                    {
                        try {
                            item.put("absences", count + 1)
                            var lessons = item.optJSONArray("lessons")
                            if (lessons == null) lessons = JSONArray()
                            item.put("lessons", lessons.put(""))
                            finalSavedRef.put(code, item)
                            prefs.edit().putString("data", finalSavedRef.toString()).apply()
                            requestRender.run()
                        } catch (_: JSONException) {
                        }
                    },
                    true
                )

                val b1 = LinearLayout.LayoutParams(0, -2, 1f)
                b1.marginEnd = UiHelper.dp(activity, 4)
                val b2 = LinearLayout.LayoutParams(0, -2, 2.2f)
                b2.marginEnd = UiHelper.dp(activity, 4)
                actions.addView(minusBtn, b1)
                actions.addView(plusBtn, b2)
                itemCard.addView(actions)
            }

            if (shown == 0) {
                UiHelper.empty(
                    activity,
                    list,
                    activity.getString(R.string.attendance_empty_filter),
                    activity.getString(R.string.attendance_empty_desc)
                )
                if ("all" != filter) {
                    list.addView(
                        UiHelper.button(
                            activity,
                            activity.getString(R.string.attendance_filter_all, totalCourses),
                            { onFilterChanged.accept("all") },
                            false
                        )
                    )
                }
            }
        }

        private fun matches(item: JSONObject, term: String, type: String): Boolean {
            return term == item.optString("term") && (type == "all" || type == item.optString("classType"))
        }

        @JvmStatic
        fun showEditCourseDialog(activity: MainActivity, code: String?, onComplete: Runnable) {
            val prefs = activity.attendancePreferences()
            val saved = try {
                JSONObject(prefs.getString("data", "{}") ?: "{}")
            } catch (_: Exception) {
                JSONObject()
            }
            val existing = if (code != null) saved.optJSONObject(code) else null

            val form = LinearLayout(activity)
            form.orientation = LinearLayout.VERTICAL
            form.setPadding(UiHelper.dp(activity, 24), UiHelper.dp(activity, 8), UiHelper.dp(activity, 24), 0)

            val nameField = UiHelper.field(
                activity,
                form,
                activity.getString(R.string.attendance_dialog_name_hint),
                if (existing != null) existing.optString("name", code) else "",
                false
            )
            val termField = UiHelper.field(
                activity,
                form,
                activity.getString(R.string.attendance_semester_hint),
                if (existing != null) existing.optString("term") else prefs.getString("term", ""),
                false
            )
            UiHelper.add(activity, form, activity.getString(R.string.attendance_class_type), 14, UiHelper.MUTED, true)
            val typeOptions = ArrayList(
                listOf(
                    "Wykład",
                    "Ćwiczenia",
                    "Laboratorium",
                    "Projekt",
                    "Seminarium",
                    "Lektorat",
                    "Konwersatorium",
                    "Inne"
                )
            )
            val oldType = if (existing == null) "Wykład" else existing.optString("classType")
            if (!typeOptions.contains(oldType)) typeOptions.add(oldType)
            val typeField = Spinner(activity)
            val typeLabels = Array(typeOptions.size) { i ->
                val t = typeOptions[i]
                if (t.isEmpty()) activity.getString(R.string.attendance_unknown_type) else t
            }
            typeField.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, typeLabels)
            typeField.setSelection(max(0, typeOptions.indexOf(oldType)))
            form.addView(typeField)
            UiHelper.note(activity, form, activity.getString(R.string.attendance_fixed_limit), UiHelper.MUTED)

            val notesField = UiHelper.field(
                activity,
                form,
                activity.getString(R.string.attendance_dialog_notes_hint),
                if (existing != null) existing.optString("notes", "") else "",
                false
            )
            notesField.isSingleLine = false
            notesField.minLines = 2

            val formScroll = ScrollView(activity)
            formScroll.addView(form)

            val builder = MaterialAlertDialogBuilder(activity)
                .setTitle(
                    if (existing == null) {
                        activity.getString(R.string.attendance_dialog_add)
                    } else {
                        activity.getString(R.string.attendance_dialog_edit, existing.optString("name", code))
                    }
                )
                .setView(formScroll)
                .setNegativeButton(activity.getString(R.string.btn_cancel), null)
                .setPositiveButton(activity.getString(R.string.btn_save), null)

            if (existing != null) {
                builder.setNeutralButton(activity.getString(R.string.attendance_dialog_delete)) { _, _ ->
                    saved.remove(code)
                    prefs.edit().putString("data", saved.toString()).apply()
                    onComplete.run()
                }
            }

            val dialog = builder.create()
            dialog.setOnShowListener {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val name = nameField.text?.toString()?.trim() ?: ""
                    if (name.isEmpty()) {
                        nameField.error = activity.getString(R.string.attendance_dialog_error_name)
                        return@setOnClickListener
                    }
                    val term = termField.text.toString().trim()
                    if (term.isEmpty()) {
                        termField.error = activity.getString(R.string.attendance_semester_required)
                        return@setOnClickListener
                    }
                    val type = typeOptions[typeField.selectedItemPosition]
                    if (type.isEmpty()) {
                        Toast.makeText(activity, R.string.attendance_type_required, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    try {
                        val course = if (existing != null) existing.optString("courseId", code) else name
                        val c = AttendanceTracker.key(term, course, type)
                        val target = saved.optJSONObject(c)
                        if (c != code && target != null && (target.optInt("absences") > 0 || target.optString("notes").isNotEmpty())) {
                            nameField.error = activity.getString(R.string.attendance_duplicate)
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
                        prefs.edit().putString("data", saved.toString()).apply()
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

