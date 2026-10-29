package pl.vistula.checker.ui

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.core.AccountRole

/** Grouped tools directory, with every existing destination retained and role-aware groups. */
class MoreScreenController private constructor() {
    companion object {
        @JvmStatic
        fun render(activity: MainActivity, content: LinearLayout) {
            fun group(title: Int, items: List<Triple<Int, Int, Int>>) {
                UiHelper.sectionLabel(activity, content, activity.getString(title))
                val tray = UiHelper.tray(activity, content)
                items.forEachIndexed { index, (name, description, destination) ->
                    if (index > 0) UiHelper.trayDivider(activity, tray)
                    val tile = UiHelper.toolTile(activity, tray, "", activity.getString(name), activity.getString(description)) {
                        activity.navigate(destination)
                    } as LinearLayout
                    tile.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    tile.setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), UiHelper.dp(activity, 12))
                    (tile.layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
                    tile.findViewById<View>(R.id.tool_tile_icon).visibility = View.GONE
                    tile.getChildAt(tile.childCount - 1).visibility = View.GONE
                    tile.findViewById<TextView>(R.id.tool_tile_title).textSize = 15f
                    tile.findViewById<TextView>(R.id.tool_tile_subtitle).textSize = 13f
                }
            }
            group(R.string.design_updates, listOf(Triple(R.string.history_title, R.string.design_history_description, 1)))
            val account = activity.serviceAccountState()
            val mode = AccountRole.mode(account)
            if (mode != AccountRole.Mode.LECTURER) {
                group(R.string.section_studies, listOf(
                    Triple(R.string.cards_title, R.string.cards_description, 13),
                    Triple(R.string.documents_title, R.string.documents_description, 14),
                    Triple(R.string.tool_attendance_title, R.string.tool_attendance_desc, 12),
                    Triple(R.string.tool_payments_title, R.string.tool_payments_desc, 8),
                    Triple(R.string.academic_extras_title, R.string.academic_extras_desc, 16),
                    Triple(R.string.tool_tasks_title, R.string.tool_tasks_desc, 7)
                ))
            }
            if (mode != AccountRole.Mode.STUDENT) {
                group(R.string.section_lecturer, buildList {
                    add(Triple(R.string.lecturer_title, R.string.lecturer_description, 15))
                    if (mode == AccountRole.Mode.LECTURER) {
                        add(Triple(R.string.tool_tasks_title, R.string.tool_tasks_desc, 7))
                    }
                })
            }
            group(R.string.section_university, buildList {
                add(Triple(R.string.tool_news_title, R.string.tool_news_desc, 11))
                add(Triple(R.string.tool_campus_title, R.string.tool_campus_desc, 10))
                add(Triple(R.string.staff_directory_title, R.string.staff_directory_desc, 17))
                if (mode == AccountRole.Mode.LECTURER) add(Triple(R.string.academic_extras_title, R.string.academic_extras_desc, 16))
            })
            group(R.string.section_app, buildList {
                add(Triple(R.string.tool_account_title, R.string.tool_account_desc, 4))
                if (mode != AccountRole.Mode.LECTURER) add(Triple(R.string.tool_gpa_title, R.string.tool_gpa_desc, 9))
                add(Triple(R.string.tool_api_title, R.string.tool_api_desc, 3))
                if (mode != AccountRole.Mode.STUDENT) add(Triple(R.string.lecturer_title, R.string.lecturer_description, 15))
            })
        }
    }
}
