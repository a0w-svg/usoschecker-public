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
import android.view.inputmethod.EditorInfo
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
import com.google.android.material.textfield.TextInputLayout
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
            val counts = TaskCounts.of(rows, today)
            val screen = LayoutInflater.from(activity).inflate(R.layout.screen_tasks, content, false)
            content.addView(screen)

            screen.findViewById<TextView>(R.id.tasks_summary).text =
                activity.getString(R.string.tasks_summary, counts.pending, counts.done)
            screen.findViewById<View>(R.id.tasks_add).setOnClickListener {
                showTaskDialog(activity, null, "", rows.length(), callback)
            }

            val searchLayout = screen.findViewById<TextInputLayout>(R.id.tasks_search_layout)
            val search = screen.findViewById<TextInputEditText>(R.id.tasks_search_input)
            search.setText(taskQuery)
            searchLayout.visibility = if (taskQuery.isEmpty()) View.GONE else View.VISIBLE
            searchLayout.setEndIconOnClickListener {
                search.setText("")
                onQueryChanged.accept("")
                activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(searchLayout.windowToken, 0)
                searchLayout.clearFocus()
                searchLayout.visibility = View.GONE
            }
            search.setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
                activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
                true
            }

            bindFilterChip(activity, screen, taskFilter, taskOrder, onFilterChanged, onOrderChanged)
            val more = screen.findViewById<View>(R.id.tasks_more)
            more.setOnClickListener {
                showOptionsMenu(activity, more, rows.length(), counts, taskFilter, taskOrder, searchLayout, search,
                    onFilterChanged, onOrderChanged, callback)
            }

            val list = screen.findViewById<LinearLayout>(R.id.tasks_list)
            val sorted = TaskTools.sorted(rows, taskOrder)
            val populate = Runnable {
                list.removeAllViews()
                var shown = 0
                for (task in sorted) {
                    if (!TaskTools.matches(task, taskFilter, search.text.toString(), today)) continue
                    shown++
                    list.addView(taskCard(activity, list, task, today, rows.length(), callback))
                }
                if (shown == 0) {
                    list.addView(emptyState(activity, list, rows.length(), search, taskFilter, onQueryChanged, onFilterChanged))
                }
            }
            UiHelper.watchSearch(search) { value ->
                onQueryChanged.accept(value)
                populate.run()
            }
            populate.run()
        }

        /** Counts shown in the filter menu and summary line. */
        private class TaskCounts(
            val total: Int, val pending: Int, val done: Int, val platon: Int, val today: Int, val week: Int, val overdue: Int
        ) {
            fun asArray() = intArrayOf(total, pending, done, platon, today, week, overdue)

            companion object {
                fun of(rows: JSONArray, today: LocalDate): TaskCounts {
                    var pending = 0
                    var done = 0
                    var dueToday = 0
                    var week = 0
                    var overdue = 0
                    var platon = 0
                    for (i in 0 until rows.length()) {
                        val t = rows.optJSONObject(i) ?: continue
                        if (PlatonLinks.isTaskLink(t.optString("platonUrl"))) platon++
                        val isDone = t.optBoolean("done")
                        if (isDone) done++ else pending++
                        val due = TaskTools.dueDate(t)
                        if (!isDone && due != null) {
                            if (due.isBefore(today)) overdue++
                            if (due == today) dueToday++
                            if (!due.isBefore(today) && due.isBefore(today.plusDays(7))) week++
                        }
                    }
                    return TaskCounts(rows.length(), pending, done, platon, dueToday, week, overdue)
                }
            }
        }

        private val FILTER_LABELS = intArrayOf(
            R.string.tasks_chip_all, R.string.tasks_chip_open, R.string.tasks_chip_done, R.string.tasks_platon_title,
            R.string.tasks_chip_today, R.string.tasks_chip_week, R.string.tasks_chip_overdue
        )
        private val ORDER_LABELS = intArrayOf(R.string.tasks_sort_due, R.string.tasks_sort_priority, R.string.tasks_sort_title)

        /** The "active filter x" chip appears only while a non-default filter or order is applied. */
        private fun bindFilterChip(
            activity: MainActivity, screen: View, taskFilter: String, taskOrder: String,
            onFilterChanged: Consumer<String>, onOrderChanged: Consumer<String>
        ) {
            if (TasksUiState.FILTER_ALL == taskFilter && TasksUiState.ORDER_DUE == taskOrder) return
            var label = ""
            TasksUiState.FILTERS.forEachIndexed { i, key -> if (key == taskFilter) label = activity.getString(FILTER_LABELS[i]) }
            if (TasksUiState.ORDER_DUE != taskOrder) {
                TasksUiState.ORDERS.forEachIndexed { i, key -> if (key == taskOrder) label += " · " + activity.getString(ORDER_LABELS[i]) }
            }
            screen.findViewById<MaterialButton>(R.id.tasks_filter_chip).apply {
                text = activity.getString(R.string.tasks_filter_active, label)
                visibility = View.VISIBLE
                setOnClickListener {
                    onOrderChanged.accept(TasksUiState.ORDER_DUE)
                    onFilterChanged.accept(TasksUiState.FILTER_ALL)
                }
            }
        }

        private fun showOptionsMenu(
            activity: MainActivity, anchor: View, total: Int, counts: TaskCounts, taskFilter: String, taskOrder: String,
            searchLayout: TextInputLayout, search: TextInputEditText,
            onFilterChanged: Consumer<String>, onOrderChanged: Consumer<String>, callback: TaskCallback
        ) {
            val popup = PopupMenu(activity, anchor)
            val menu = popup.menu
            menu.add(R.string.tasks_search_action).setOnMenuItemClickListener {
                searchLayout.visibility = View.VISIBLE
                search.requestFocus()
                activity.getSystemService(InputMethodManager::class.java)?.showSoftInput(search, 0)
                true
            }
            val filters = menu.addSubMenu(R.string.tasks_filter_title)
            val numbers = counts.asArray()
            TasksUiState.FILTERS.forEachIndexed { i, key ->
                filters.add("${activity.getString(FILTER_LABELS[i])} (${numbers[i]})")
                    .setCheckable(true)
                    .setChecked(key == taskFilter)
                    .setOnMenuItemClickListener { onFilterChanged.accept(key); true }
            }
            val ordering = menu.addSubMenu(R.string.tasks_sort_note)
            TasksUiState.ORDERS.forEachIndexed { i, key ->
                ordering.add(ORDER_LABELS[i])
                    .setCheckable(true)
                    .setChecked(key == taskOrder)
                    .setOnMenuItemClickListener { onOrderChanged.accept(key); true }
            }
            menu.add(R.string.tasks_platon_open).setOnMenuItemClickListener {
                UiHelper.browser(activity, PlatonLinks.HOME)
                true
            }
            menu.add(R.string.tasks_platon_add).setOnMenuItemClickListener {
                showTaskDialog(activity, null, "", total, callback, true)
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

        private fun taskCard(
            activity: MainActivity, list: LinearLayout, task: JSONObject, today: LocalDate, total: Int, callback: TaskCallback
        ): View {
            val done = task.optBoolean("done")
            val due = TaskTools.dueDate(task)
            val titleText = task.optString("title")
            val id = task.optString("id")
            val card = LayoutInflater.from(activity).inflate(R.layout.item_task_card, list, false)
            val title = card.findViewById<TextView>(R.id.task_item_title)
            val dueView = card.findViewById<TextView>(R.id.task_item_due)
            val toggle = card.findViewById<CheckBox>(R.id.task_item_btn_toggle)
            val options = card.findViewById<View>(R.id.task_item_more)

            title.text = titleText
            if (done) {
                title.setTextColor(activity.getColor(R.color.app_muted))
                title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }

            val formattedDate = due?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: activity.getString(R.string.tasks_due_unknown)
            var statusText = when {
                done -> activity.getString(R.string.tasks_status_done, formattedDate)
                due != null && due.isBefore(today) -> activity.getString(R.string.tasks_status_overdue, formattedDate)
                today == due -> activity.getString(R.string.tasks_status_today, formattedDate)
                else -> activity.getString(R.string.tasks_status_due, formattedDate)
            }
            if (!done && TaskTools.priority(task) == 2) statusText += " · " + activity.getString(R.string.tasks_priority_high)
            if (PlatonLinks.isTaskLink(task.optString("platonUrl"))) statusText += " · Platon"
            dueView.text = statusText
            dueView.setTextColor(
                activity.getColor(
                    when {
                        done -> R.color.app_muted
                        due != null && due.isBefore(today) -> R.color.app_error
                        else -> R.color.app_ink
                    }
                )
            )

            card.setOnClickListener { showTaskDialog(activity, task, "", total, callback) }
            toggle.isChecked = done
            toggle.contentDescription = "${activity.getString(if (done) R.string.tasks_btn_mark_open else R.string.tasks_btn_mark_done)}: $titleText"
            toggle.setOnClickListener { callback.onMutateTask(id, false) }
            options.contentDescription = activity.getString(R.string.tasks_task_options, titleText)
            options.setOnClickListener { showTaskMenu(activity, options, task, done, titleText, id, total, callback) }
            return card
        }

        private fun showTaskMenu(
            activity: MainActivity, anchor: View, task: JSONObject, done: Boolean, titleText: String, id: String,
            total: Int, callback: TaskCallback
        ) {
            val popup = PopupMenu(activity, anchor)
            popup.menu.add(R.string.tasks_btn_edit).setOnMenuItemClickListener {
                showTaskDialog(activity, task, "", total, callback)
                true
            }
            popup.menu.add(if (done) R.string.tasks_btn_mark_open else R.string.tasks_btn_mark_done).setOnMenuItemClickListener {
                callback.onMutateTask(id, false)
                true
            }
            val url = task.optString("platonUrl")
            if (PlatonLinks.isTaskLink(url)) {
                popup.menu.add(R.string.tasks_platon_open_task).setOnMenuItemClickListener {
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

        private fun emptyState(
            activity: MainActivity, list: LinearLayout, total: Int, search: TextInputEditText, taskFilter: String,
            onQueryChanged: Consumer<String>, onFilterChanged: Consumer<String>
        ): View {
            val box = LayoutInflater.from(activity).inflate(R.layout.view_empty_state, list, false) as LinearLayout
            box.findViewById<TextView>(R.id.empty_title).setText(if (total == 0) R.string.tasks_empty_title else R.string.tasks_no_match_title)
            box.findViewById<TextView>(R.id.empty_description).setText(if (total == 0) R.string.tasks_empty_desc else R.string.tasks_no_match_desc)
            if (search.text.toString().trim().isNotEmpty()) {
                box.addView(UiHelper.button(activity, activity.getString(R.string.btn_clear_search), {
                    search.setText("")
                    onQueryChanged.accept("")
                }, false))
            } else if (TasksUiState.FILTER_ALL != taskFilter) {
                box.addView(UiHelper.button(activity, "${activity.getString(R.string.tasks_chip_all)} ($total)", {
                    onFilterChanged.accept(TasksUiState.FILTER_ALL)
                }, false))
            }
            return box
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

