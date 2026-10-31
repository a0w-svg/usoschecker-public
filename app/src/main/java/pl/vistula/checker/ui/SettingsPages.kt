package pl.vistula.checker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R

/** A short settings overview; each destination displays one focused group of controls. */
class SettingsPages(private val activity: MainActivity, private val content: LinearLayout) {
    private data class Page(val key: String, val title: Int, val body: LinearLayout, val row: View)
    private val pages = mutableListOf<Page>()

    fun page(key: String, title: Int, primary: Boolean = false): LinearLayout {
        val inflater = LayoutInflater.from(activity)
        val row = inflater.inflate(if (primary) R.layout.view_button_primary else R.layout.item_settings_page, content, false) as MaterialButton
        row.text = activity.getString(title)
        row.setOnClickListener { activity.openAccountPage(key) }
        content.addView(row)
        val body = inflater.inflate(R.layout.view_settings_body, content, false) as LinearLayout
        content.addView(body)
        pages.add(Page(key, title, body, row))
        return body
    }

    fun finish(preferred: String? = null) {
        val selected = pages.firstOrNull { it.key == (activity.accountPage() ?: preferred) }
        if (selected == null) {
            val order = listOf("settings.connection", "settings.account", "settings.profile", "settings.campus",
                "settings.sync", "settings.notifications", "settings.appearance", "settings.term", "settings.widgets", "settings.about")
            for (page in pages) { content.removeView(page.row); content.removeView(page.body) }
            for (page in pages.sortedBy { order.indexOf(it.key) }) {
                content.addView(page.row)
                content.addView(page.body)
            }
            return
        }
        activity.rememberAccountPage(selected.key)
        (selected.body.parent as? ViewGroup)?.removeView(selected.body)
        content.removeAllViews()
        ToolScreenUi.header(activity, content, selected.title, null)
        content.addView(UiHelper.actionRow(activity, activity.getString(R.string.settings_back), {
            activity.openAccountPage(null)
        }))
        selected.body.visibility = View.VISIBLE
        content.addView(selected.body)
    }
}
