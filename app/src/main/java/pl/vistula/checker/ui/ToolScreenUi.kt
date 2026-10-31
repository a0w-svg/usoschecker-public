package pl.vistula.checker.ui

import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import java.util.function.Consumer
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R

/** Shared flat headers and overflow actions for tools in More. */
class ToolScreenUi private constructor() {
    companion object {
        @JvmStatic
        fun header(activity: MainActivity, content: LinearLayout, title: Int, actions: Consumer<Menu>?) {
            val row = LayoutInflater.from(activity).inflate(R.layout.view_tool_header, content, false)
            row.findViewById<TextView>(R.id.tool_header_title).setText(title)
            val more = row.findViewById<View>(R.id.tool_screen_more)
            if (actions == null) {
                more.visibility = View.GONE
            } else {
                more.contentDescription = activity.getString(R.string.tool_options, activity.getString(title))
                more.setOnClickListener {
                    val popup = PopupMenu(activity, more)
                    actions.accept(popup.menu)
                    popup.show()
                }
            }
            content.addView(row)
        }

        @JvmStatic
        fun action(menu: Menu, label: Int, action: Runnable) {
            menu.add(label).setOnMenuItemClickListener {
                action.run()
                true
            }
        }

        @JvmStatic
        fun overflow(activity: MainActivity, parent: LinearLayout, description: String, actions: Consumer<Menu>): View {
            val more = ScheduleScreenController.headerIcon(activity, R.drawable.ic_schedule_more) {}
            more.contentDescription = description
            parent.addView(more, LinearLayout.LayoutParams(UiHelper.dp(activity, 48), -2))
            more.setOnClickListener {
                val popup = PopupMenu(activity, more)
                actions.accept(popup.menu)
                popup.show()
            }
            return more
        }
    }
}

