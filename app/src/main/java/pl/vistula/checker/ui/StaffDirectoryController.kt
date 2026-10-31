package pl.vistula.checker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.DemoStudentServices
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.ToolServiceData
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.StudentServices

/** Staff directory screen: teacher search and the schedule of a chosen teacher. Layout lives in `screen_staff_directory.xml`. */
class StaffDirectoryController private constructor() {
    companion object {
        private const val MIN_QUERY = 2
        private const val MAX_QUERY = 80

        @JvmStatic
        fun render(activity: MainActivity, content: LinearLayout, services: JSONObject) {
            val language = Language.currentLocale(activity).language
            ToolScreenUi.header(activity, content, R.string.staff_directory_title) { menu ->
                ToolScreenUi.action(menu, R.string.btn_refresh) { activity.refreshStudentServices("staffDirectory") }
            }
            val screen = LayoutInflater.from(activity).inflate(R.layout.screen_staff_directory, content, false)
            content.addView(screen)

            val entry = services.optJSONObject("staffDirectory") ?: JSONObject()
            StudentCardsController.status(activity, screen.findViewById(R.id.staff_status), entry)

            val layout = screen.findViewById<TextInputLayout>(R.id.staff_search_layout)
            val input = screen.findViewById<TextInputEditText>(R.id.staff_search_input)
            val results = Results(
                screen.findViewById(R.id.staff_results_card),
                screen.findViewById(R.id.staff_results),
                screen.findViewById(R.id.staff_empty),
                screen.findViewById(R.id.staff_more)
            )

            fun search() {
                val value = input.text?.toString()?.trim().orEmpty()
                if (value.length !in MIN_QUERY..MAX_QUERY) {
                    layout.error = activity.getString(R.string.staff_search_invalid)
                    return
                }
                layout.error = null
                activity.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
                activity.universityRead({ client -> ToolServiceData.searchTeachers(client, value) }) { data ->
                    if (screen.isAttachedToWindow) showResults(activity, results, data, language)
                }
            }
            screen.findViewById<View>(R.id.staff_search_button).setOnClickListener { search() }
            input.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    search()
                    true
                } else false
            }
            showResults(activity, results, entry.optJSONObject("data") ?: JSONObject(), language)
        }

        private class Results(val card: View, val list: LinearLayout, val empty: TextView, val more: TextView)

        private fun showResults(activity: MainActivity, results: Results, data: JSONObject, language: String) {
            results.list.removeAllViews()
            val staff = data.optJSONArray("users") ?: JSONArray()
            val any = staff.length() > 0
            results.card.visibility = if (any) View.VISIBLE else View.GONE
            results.empty.visibility = if (any) View.GONE else View.VISIBLE
            results.more.visibility = if (any && data.optBoolean("hasMore")) View.VISIBLE else View.GONE
            val inflater = LayoutInflater.from(activity)
            var shown = 0
            for (i in 0 until staff.length()) {
                val member = staff.optJSONObject(i) ?: continue
                val row = inflater.inflate(R.layout.item_staff_member, results.list, false)
                bindMember(activity, row, member, language)
                // The divider belongs between rows, so the first row hides its own.
                if (shown == 0) row.findViewById<View>(R.id.staff_divider).visibility = View.GONE
                results.list.addView(row)
                shown++
            }
        }

        private fun bindMember(activity: MainActivity, row: View, member: JSONObject, language: String) {
            val fullName = listOf(
                StudentServices.text(member, "title_before"),
                StudentServices.text(member, "first_name"),
                StudentServices.text(member, "last_name"),
                StudentServices.text(member, "title_after")
            ).filter { it.isNotEmpty() }.joinToString(" ")
            val details = listOfNotNull(
                StudentServices.text(member, "room").takeIf { it.isNotEmpty() },
                StudentServices.localized(member.opt("office_hours"), language).takeIf { it.isNotEmpty() }
            ).joinToString(" · ")
            val id = StudentServices.text(member, "id")
            val title = fullName.ifEmpty { id }

            row.findViewById<TextView>(R.id.staff_name).text = fullName
            row.findViewById<TextView>(R.id.staff_details).apply {
                text = details
                visibility = if (details.isEmpty()) View.GONE else View.VISIBLE
            }
            val tap = row.findViewById<View>(R.id.staff_row)
            if (id.isEmpty()) {
                tap.isClickable = false
                tap.background = null
                row.findViewById<View>(R.id.staff_chevron).visibility = View.GONE
                return
            }
            tap.contentDescription = listOf(fullName, details).filter { it.isNotEmpty() }.joinToString(", ")
            tap.setOnClickListener {
                if (activity.isDemo()) {
                    LecturerScreenController.showStaffScheduleDialog(
                        activity, title, DemoStudentServices.demoStaffProfile(id), DemoStudentServices.demoStaffSchedule(id), language
                    )
                } else {
                    activity.universityRead({ client ->
                        val profile = ToolServiceData.fetchStaffProfile(client, id)
                        val today = java.time.LocalDate.now(UsosClient.ZONE).toString()
                        profile to ToolServiceData.fetchStaffSchedule(client, id, today)
                    }) { (profile, lessons) ->
                        LecturerScreenController.showStaffScheduleDialog(activity, title, profile, lessons, language)
                    }
                }
            }
        }
    }
}
