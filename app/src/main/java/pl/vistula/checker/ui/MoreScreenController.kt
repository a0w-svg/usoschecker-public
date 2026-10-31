package pl.vistula.checker.ui

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.core.AccountRole

/** Grouped tools directory, with every existing destination retained and role-aware groups. */
class MoreScreenController private constructor() {
    /** One row of the tools directory: string resources for its title and description, and the [Tabs] id it opens. */
    data class Entry(val title: Int, val description: Int, val destination: Int)

    /** A titled section of the tools directory. */
    data class Group(val title: Int, val entries: List<Entry>)

    companion object {
        /**
         * The tools directory for [mode]. Pure data, so the layout can be unit-tested without Android views.
         * Every destination appears at most once.
         */
        @JvmStatic
        fun layout(mode: AccountRole.Mode): List<Group> = buildList {
            add(Group(R.string.design_updates, listOf(Entry(R.string.history_title, R.string.design_history_description, Tabs.HISTORY))))
            if (mode != AccountRole.Mode.LECTURER) {
                add(
                    Group(
                        R.string.section_studies, listOf(
                            Entry(R.string.cards_title, R.string.cards_description, Tabs.CARDS),
                            Entry(R.string.documents_title, R.string.documents_description, Tabs.DOCUMENTS),
                            Entry(R.string.tool_attendance_title, R.string.tool_attendance_desc, Tabs.ATTENDANCE),
                            Entry(R.string.tool_payments_title, R.string.tool_payments_desc, Tabs.PAYMENTS),
                            Entry(R.string.academic_extras_title, R.string.academic_extras_desc, Tabs.ACADEMIC_EXTRAS),
                            Entry(R.string.tool_tasks_title, R.string.tool_tasks_desc, Tabs.TASKS)
                        )
                    )
                )
            }
            if (mode != AccountRole.Mode.STUDENT) {
                add(
                    Group(
                        R.string.section_lecturer, buildList {
                            add(Entry(R.string.lecturer_title, R.string.lecturer_description, Tabs.LECTURER))
                            if (mode == AccountRole.Mode.LECTURER) {
                                add(Entry(R.string.tool_tasks_title, R.string.tool_tasks_desc, Tabs.TASKS))
                            }
                        }
                    )
                )
            }
            add(
                Group(
                    R.string.section_university, buildList {
                        add(Entry(R.string.tool_news_title, R.string.tool_news_desc, Tabs.NEWS))
                        add(Entry(R.string.tool_campus_title, R.string.tool_campus_desc, Tabs.CAMPUS_GUIDE))
                        add(Entry(R.string.staff_directory_title, R.string.staff_directory_desc, Tabs.STAFF_DIRECTORY))
                        if (mode == AccountRole.Mode.LECTURER) {
                            add(Entry(R.string.academic_extras_title, R.string.academic_extras_desc, Tabs.ACADEMIC_EXTRAS))
                        }
                    }
                )
            )
            add(
                Group(
                    R.string.section_app, buildList {
                        add(Entry(R.string.tool_account_title, R.string.tool_account_desc, Tabs.ACCOUNT))
                        if (mode != AccountRole.Mode.LECTURER) {
                            add(Entry(R.string.tool_gpa_title, R.string.tool_gpa_desc, Tabs.GPA_SIMULATOR))
                        }
                        add(Entry(R.string.tool_api_title, R.string.tool_api_desc, Tabs.API))
                    }
                )
            )
        }

        @JvmStatic
        fun render(activity: MainActivity, content: LinearLayout) {
            for (group in layout(AccountRole.mode(activity.serviceAccountState()))) {
                UiHelper.sectionLabel(activity, content, activity.getString(group.title))
                val tray = UiHelper.tray(activity, content)
                group.entries.forEachIndexed { index, entry ->
                    if (index > 0) UiHelper.trayDivider(activity, tray)
                    val tile = UiHelper.toolTile(
                        activity, tray, "", activity.getString(entry.title), activity.getString(entry.description)
                    ) {
                        activity.navigate(entry.destination)
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
        }
    }
}
