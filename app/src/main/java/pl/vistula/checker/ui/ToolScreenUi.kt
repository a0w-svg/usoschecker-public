package pl.vistula.checker.ui

import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.core.view.ViewCompat
import java.util.function.Consumer
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R

/** Shared flat headers and overflow actions for tools in More. */
class ToolScreenUi private constructor() {
    companion object {
        @JvmStatic
        fun header(activity: MainActivity, content: LinearLayout, title: Int, actions: Consumer<Menu>?) {
            val row = LinearLayout(activity)
            row.gravity = Gravity.CENTER_VERTICAL
            val label = UiHelper.text(activity, activity.getString(title), 20, UiHelper.INK, true)
            label.setPadding(UiHelper.dp(activity, 8), 0, 0, 0)
            ViewCompat.setAccessibilityHeading(label, true)
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            row.minimumHeight = UiHelper.dp(activity, 64)
            if (actions != null) {
                val more = ScheduleScreenController.headerIcon(activity, R.drawable.ic_schedule_more) {}
                more.id = R.id.tool_screen_more
                more.contentDescription = activity.getString(R.string.tool_options, activity.getString(title))
                row.addView(more, LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2))
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

