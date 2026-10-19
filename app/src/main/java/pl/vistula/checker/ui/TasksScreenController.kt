package pl.vistula.checker.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.function.Consumer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.PlatonLinks
import pl.vistula.checker.core.TaskTools

/**
 * Controller for rendering the Student Tasks Planner screen.
 */
class TasksScreenController private constructor() {

    interface TaskCallback {
        fun onSaveTask(task: JSONObject)
        fun onMutateTask(id: String, remove: Boolean)
    }

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            rows: JSONArray,
            taskFilter: String,
            taskOrder: String,
            taskQuery: String,
            onFilterChanged: Consumer<String>,
            onOrderChanged: Consumer<String>,
            onQueryChanged: Consumer<String>,
            callback: TaskCallback
        ) {
            val today = LocalDate.now(UsosClient.ZONE)
            var pending = 0
            var doneCount = 0
            var todayCount = 0
            var weekCount = 0
            var overdueCount = 0
            var platonCount = 0
            for (i in 0 until rows.length()) {
                val t = rows.optJSONObject(i) ?: continue
                if (PlatonLinks.isTaskLink(t.optString("platonUrl"))) platonCount++
                val done = t.optBoolean("done")
                if (done) doneCount++ else pending++
                val due = TaskTools.dueDate(t)
                if (!done && due != null) {
                    if (due.isBefore(today)) overdueCount++
                    if (due == today) todayCount++
                    if (!due.isBefore(today) && due.isBefore(today.plusDays(7))) weekCount++
                }
            }

            UiHelper.note(
                activity,
                content,
                activity.getString(R.string.tasks_summary, pending, doneCount),
                UiHelper.MUTED
            )

            val header = LinearLayout(activity)
            header.gravity = Gravity.CENTER_VERTICAL
            content.addView(header, content.childCount - 1)
            val heading = TextView(activity)
            heading.setText(R.string.nav_tasks)
            heading.textSize = 20f
            heading.setPadding(UiHelper.dp(activity, 8), 0, 0, 0)
            ViewCompat.setAccessibilityHeading(heading, true)
            heading.setTypeface(null, Typeface.BOLD)
            heading.setTextColor(UiHelper.foreground(activity, UiHelper.INK))
            header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
            val add = ScheduleScreenController.headerButton(activity, "+") {}
            add.contentDescription = activity.getString(R.string.btn_add_task)
            add.id = R.id.tasks_add
            add.setOnClickListener { showTaskDialog(activity, null, "", rows.length(), callback) }
            header.addView(add, LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2))
            val more = ScheduleScreenController.headerIcon(activity, R.drawable.ic_schedule_more) {}
            more.contentDescription = activity.getString(R.string.tasks_options)
            more.id = R.id.tasks_more
            header.addView(more, LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2))

            val filterKeys = arrayOf("all", "open", "done", "platon", "today", "week", "overdue")
            val filterLabels = intArrayOf(
                R.string.tasks_chip_all,
                R.string.tasks_chip_open,
                R.string.tasks_chip_done,
                R.string.tasks_platon_title,
                R.string.tasks_chip_today,
                R.string.tasks_chip_week,
                R.string.tasks_chip_overdue
            )
            val counts = intArrayOf(rows.length(), pending, doneCount, platonCount, todayCount, weekCount, overdueCount)
            val orderKeys = arrayOf("due", "priority", "title")
            val orderLabels = intArrayOf(R.string.tasks_sort_due, R.string.tasks_sort_priority, R.string.tasks_sort_title)
            if ("all" != taskFilter || "due" != taskOrder) {
                var label = ""
                for (i in filterKeys.indices) {
                    if (filterKeys[i] == taskFilter) label = activity.getString(filterLabels[i])
                }
                if ("due" != taskOrder) {
                    for (i in orderKeys.indices) {
                        if (orderKeys[i] == taskOrder) label += " · " + activity.getString(orderLabels[i])
                    }
                }
                val selected = ScheduleScreenController.headerButton(activity, "$label ×") {}
                selected.contentDescription = activity.getString(R.string.grades_filter_reset)
                selected.textSize = 12f
                selected.setOnClickListener {
                    onOrderChanged.accept("due")
                    onFilterChanged.accept("all")
                }
                content.addView(selected)
            }
            val searchContainer = arrayOfNulls<View>(1)
            val search = UiHelper.searchField(
                activity,
                content,
                activity.getString(R.string.tasks_search_hint),
                taskQuery,
                null
            ) {
                onQueryChanged.accept("")
                val container = searchContainer[0]
                if (container != null) {
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(container.windowToken, 0)
                    container.clearFocus()
                    container.visibility = View.GONE
                }
            }
            val searchBox = content.getChildAt(content.childCount - 1)
            searchContainer[0] = searchBox
            searchBox.visibility = if (taskQuery.isEmpty()) View.GONE else View.VISIBLE
            more.setOnClickListener {
                val popup = PopupMenu(activity, more)
                val menu = popup.menu
                menu.add(R.string.tasks_search_action).setOnMenuItemClickListener {
                    searchBox.visibility = View.VISIBLE
                    search.requestFocus()
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
                    true
                }
                val filters = menu.addSubMenu(R.string.tasks_filter_title)
                for (i in filterKeys.indices) {
                    val key = filterKeys[i]
                    filters.add("${activity.getString(filterLabels[i])} (${counts[i]})")
                        .setCheckable(true)
                        .setChecked(key == taskFilter)
                        .setOnMenuItemClickListener {
                            onFilterChanged.accept(key)
                            true
                        }
                }
                val ordering = menu.addSubMenu(R.string.tasks_sort_note)
                for (i in orderKeys.indices) {
                    val key = orderKeys[i]
                    ordering.add(orderLabels[i])
                        .setCheckable(true)
                        .setChecked(key == taskOrder)
                        .setOnMenuItemClickListener {
                            onOrderChanged.accept(key)
                            true
                        }
                }
                menu.add(R.string.tasks_platon_open).setOnMenuItemClickListener {
                    UiHelper.browser(activity, PlatonLinks.HOME)
                    true
                }
                menu.add(R.string.tasks_platon_add).setOnMenuItemClickListener {
                    showTaskDialog(activity, null, "", rows.length(), callback, true)
                    true
                }
                menu.add(R.string.tasks_platon_title).setOnMenuItemClickListener {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.tasks_platon_title)
                        .setMessage(R.string.tasks_platon_desc)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    true
                }
                popup.show()
            }

            val list = LinearLayout(activity)
            list.orientation = LinearLayout.VERTICAL
            content.addView(list)

            val sorted = TaskTools.sorted(rows, taskOrder)
            val populate = Runnable {
                list.removeAllViews()
                var count = 0
                val inflater = LayoutInflater.from(activity)

                for (t in sorted) {
                    val done = t.optBoolean("done")
                    val due = TaskTools.dueDate(t)
                    val titleText = t.optString("title")

                    if (TaskTools.matches(t, taskFilter, search.text.toString(), today)) {
                        ++count
                        val id = t.optString("id")
                        val card = inflater.inflate(R.layout.item_task_card, list, false)

                        val title = card.findViewById<TextView>(R.id.task_item_title)
                        val dueView = card.findViewById<TextView>(R.id.task_item_due)
                        val toggle = card.findViewById<CheckBox>(R.id.task_item_btn_toggle)
                        val options = card.findViewById<View>(R.id.task_item_more)
                        title.text = titleText
                        title.setTextColor(UiHelper.foreground(activity, if (done) UiHelper.MUTED else UiHelper.INK))
                        if (done) {
                            title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        }

                        val formattedDate = due?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                            ?: activity.getString(R.string.tasks_due_unknown)
                        var statusText: String
                        val statusColor: Int
                        when {
                            done -> {
                                statusText = activity.getString(R.string.tasks_status_done, formattedDate)
                                statusColor = UiHelper.MUTED
                            }
                            due != null && due.isBefore(today) -> {
                                statusText = activity.getString(R.string.tasks_status_overdue, formattedDate)
                                statusColor = Color.rgb(160, 48, 40)
                            }
                            today == due -> {
                                statusText = activity.getString(R.string.tasks_status_today, formattedDate)
                                statusColor = UiHelper.GREEN
                            }
                            else -> {
                                statusText = activity.getString(R.string.tasks_status_due, formattedDate)
                                statusColor = UiHelper.GREEN
                            }
                        }
                        if (!done && TaskTools.priority(t) == 2) {
                            statusText += " · " + activity.getString(R.string.tasks_priority_high)
                        }
                        if (PlatonLinks.isTaskLink(t.optString("platonUrl"))) {
                            statusText += " · Platon"
                        }
                        dueView.text = statusText
                        dueView.setTextColor(UiHelper.foreground(activity, statusColor))
                        card.setOnClickListener { showTaskDialog(activity, t, "", rows.length(), callback) }
                        toggle.isChecked = done
                        toggle.contentDescription = "${activity.getString(if (done) R.string.tasks_btn_mark_open else R.string.tasks_btn_mark_done)}: $titleText"
                        toggle.setOnClickListener { callback.onMutateTask(id, false) }
                        options.contentDescription = activity.getString(R.string.tasks_task_options, titleText)
                        options.setOnClickListener {
                            val popup = PopupMenu(activity, options)
                            popup.menu.add(R.string.tasks_btn_edit).setOnMenuItemClickListener {
                                showTaskDialog(activity, t, "", rows.length(), callback)
                                true
                            }
                            popup.menu.add(if (done) R.string.tasks_btn_mark_open else R.string.tasks_btn_mark_done)
                                .setOnMenuItemClickListener {
                                    callback.onMutateTask(id, false)
                                    true
                                }
                            val url = t.optString("platonUrl")
                            if (PlatonLinks.isTaskLink(url)) {
                                popup.menu.add(R.string.tasks_platon_open_task)
                                    .setOnMenuItemClickListener {
                                        UiHelper.browser(activity, url)
                                        true
                                    }
                            }
                            popup.menu.add(R.string.tasks_btn_delete).setOnMenuItemClickListener {
                                MaterialAlertDialogBuilder(activity)
                                    .setTitle(R.string.tasks_delete_dialog_title)
                                    .setMessage(titleText)
                                    .setNegativeButton(R.string.btn_cancel, null)
                                    .setPositiveButton(R.string.tasks_btn_delete) { _, _ -> callback.onMutateTask(id, true) }
                                    .show()
                                true
                            }
                            popup.show()
                        }

                        list.addView(card)
                    }
                }

                if (count == 0) {
                    val emptyBox = UiHelper.card(activity, list, Color.WHITE)
                    UiHelper.add(
                        activity,
                        emptyBox,
                        if (rows.length() == 0) activity.getString(R.string.tasks_empty_title) else activity.getString(R.string.tasks_no_match_title),
                        17,
                        UiHelper.INK,
                        true
                    )
                    UiHelper.add(
                        activity,
                        emptyBox,
                        if (rows.length() == 0) activity.getString(R.string.tasks_empty_desc) else activity.getString(R.string.tasks_no_match_desc),
                        14,
                        UiHelper.MUTED,
                        false
                    )
                    if (search.text.toString().trim().isNotEmpty()) {
                        emptyBox.addView(
                            UiHelper.button(
                                activity,
                                activity.getString(R.string.btn_clear_search),
                                {
                                    search.setText("")
                                    onQueryChanged.accept("")
                                },
                                false
                            )
                        )
                    } else if ("all" != taskFilter) {
                        emptyBox.addView(
                            UiHelper.button(
                                activity,
                                "${activity.getString(R.string.tasks_chip_all)} (${rows.length()})",
                                { onFilterChanged.accept("all") },
                                false
                            )
                        )
                    }
                }
            }

            UiHelper.watchSearch(search) { value ->
                onQueryChanged.accept(value)
                populate.run()
            }
            populate.run()
        }

        @JvmStatic
        fun showTaskDialog(
            activity: MainActivity,
            existing: JSONObject?,
            suggestedTitle: String,
            totalCount: Int,
            callback: TaskCallback
        ) {
            showTaskDialog(activity, existing, suggestedTitle, totalCount, callback, false)
        }

        private fun showTaskDialog(
            activity: MainActivity,
            existing: JSONObject?,
            suggestedTitle: String,
            totalCount: Int,
            callback: TaskCallback,
            requirePlaton: Boolean
        ) {
            val inflater = LayoutInflater.from(activity)
            val dialogView = inflater.inflate(R.layout.dialog_task_input, null)

            val name = dialogView.findViewById<TextInputEditText>(R.id.task_input_title)
            val priority = dialogView.findViewById<Spinner>(R.id.task_input_priority)
            val notes = dialogView.findViewById<TextInputEditText>(R.id.task_input_notes)
            val platonUrl = dialogView.findViewById<TextInputEditText>(R.id.task_input_platon_url)
            platonUrl.setText(if (existing == null) "" else existing.optString("platonUrl"))
            val dateBtn = dialogView.findViewById<MaterialButton>(R.id.task_input_date_btn)

            name.setText(if (existing == null) suggestedTitle else existing.optString("title"))

            val prioOptions = arrayOf(
                activity.getString(R.string.tasks_priority_low),
                activity.getString(R.string.tasks_priority_normal),
                activity.getString(R.string.tasks_priority_high)
            )
            priority.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, prioOptions)
            priority.setSelection(if (existing == null) 1 else TaskTools.priority(existing))

            notes.setText(if (existing == null) "" else existing.optString("notes"))

            val storedDue = if (existing == null) null else TaskTools.dueDate(existing)
            var due = storedDue ?: LocalDate.now(UsosClient.ZONE)
            dateBtn.text = activity.getString(R.string.task_due_date, due.toString())
            dateBtn.setOnClickListener {
                DatePickerDialog(
                    activity,
                    { _, year, month, day ->
                        due = LocalDate.of(year, month + 1, day)
                        dateBtn.text = activity.getString(R.string.task_due_date, due.toString())
                    },
                    due.year,
                    due.monthValue - 1,
                    due.dayOfMonth
                ).show()
            }

            val dialog = MaterialAlertDialogBuilder(activity)
                .setTitle(
                    if (existing == null) {
                        activity.getString(R.string.tasks_dialog_new_title)
                    } else {
                        activity.getString(R.string.tasks_dialog_edit_title)
                    }
                )
                .setView(dialogView)
                .setNegativeButton(activity.getString(R.string.btn_cancel), null)
                .setPositiveButton(activity.getString(R.string.btn_save), null)
                .create()

            dialog.setOnShowListener {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val title = name.text?.toString()?.trim() ?: ""
                    val notesText = notes.text?.toString() ?: ""
                    when {
                        title.isEmpty() -> {
                            name.error = activity.getString(R.string.tasks_error_empty_title)
                            name.requestFocus()
                        }
                        title.length > 200 -> {
                            name.error = activity.getString(R.string.tasks_error_title_length)
                            name.requestFocus()
                        }
                        notesText.length > 2000 -> {
                            notes.error = activity.getString(R.string.tasks_error_notes_length)
                            notes.requestFocus()
                        }
                        existing == null && totalCount >= 200 -> {
                            name.error = activity.getString(R.string.tasks_error_limit)
                        }
                        else -> {
                            val link = try {
                                val normalized = PlatonLinks.normalize(platonUrl.text?.toString() ?: "")
                                require(!(requirePlaton && normalized.isEmpty()))
                                normalized
                            } catch (_: IllegalArgumentException) {
                                platonUrl.error = activity.getString(R.string.tasks_platon_invalid_link)
                                platonUrl.requestFocus()
                                return@setOnClickListener
                            }
                            try {
                                val entry = if (existing == null) JSONObject() else JSONObject(existing.toString())
                                if (link.isEmpty()) entry.remove("platonUrl") else entry.put("platonUrl", link)
                                entry.put("id", if (existing == null) UUID.randomUUID().toString() else existing.getString("id"))
                                    .put("title", title)
                                    .put("due", due.toString())
                                    .put("done", existing != null && existing.optBoolean("done"))
                                    .put("priority", priority.selectedItemPosition)
                                    .put("notes", notesText.trim())
                                dialog.dismiss()
                                callback.onSaveTask(entry)
                            } catch (_: JSONException) {
                            }
                        }
                    }
                }
            }
            dialog.show()
        }
    }
}

