package pl.vistula.checker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.function.Consumer
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.core.NewsArticle
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.StudentSearch

/**
 * Controller for rendering the University News and Announcements screen.
 */
class NewsScreenController private constructor() {

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject,
            query: String,
            onQueryChanged: Consumer<String>,
            onRefresh: Runnable
        ) {
            ToolScreenUi.header(activity, content, R.string.tool_news_title) { menu ->
                ToolScreenUi.action(menu, R.string.news_refresh_btn, onRefresh)
                ToolScreenUi.action(menu, R.string.tool_api_methods) { activity.openApiModule("news") }
            }
            val raw = d.optJSONObject("snapshot")
            val snapshot = if (raw != null) Snapshot.from(raw) else null
            if (snapshot != null && snapshot.serviceErrors.containsKey("news")) {
                UiHelper.note(
                    activity,
                    content,
                    activity.getString(R.string.services_refresh_error, snapshot.serviceErrors["news"]),
                    activity.getColor(R.color.app_error)
                )
            }
            val articles = if (snapshot != null && snapshot.news != null) snapshot.news else emptyList()

            val search = UiHelper.searchField(
                activity,
                content,
                activity.getString(R.string.news_search_hint),
                query,
                activity.getString(R.string.news_search_desc)
            ) { onQueryChanged.accept("") }

            val list = LinearLayout(activity)
            list.orientation = LinearLayout.VERTICAL
            content.addView(list)

            val populate = Runnable {
                list.removeAllViews()
                val q = search.text.toString().trim()
                val filtered = ArrayList<NewsArticle>()
                for (a in articles) {
                    if (StudentSearch.matches(q, a.title, a.headline, a.author, UiHelper.plain(a.content))) {
                        filtered.add(a)
                    }
                }

                if (filtered.isEmpty()) {
                    val emptyBox = UiHelper.section(activity, list, Color.WHITE)
                    UiHelper.add(
                        activity,
                        emptyBox,
                        if (q.isEmpty()) activity.getString(R.string.news_empty_title) else activity.getString(R.string.news_no_match_title),
                        17,
                        UiHelper.INK,
                        true
                    )
                    UiHelper.note(
                        activity,
                        emptyBox,
                        if (q.isEmpty()) activity.getString(R.string.news_empty_desc) else activity.getString(R.string.news_no_match_desc),
                        UiHelper.MUTED
                    )
                    if (q.isNotEmpty()) {
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
                    }
                } else {
                    UiHelper.note(activity, list, activity.getString(R.string.news_count, filtered.size), UiHelper.MUTED)
                    val inflater = LayoutInflater.from(activity)
                    for (a in filtered) {
                        val itemCard = inflater.inflate(R.layout.item_news_card, list, false)
                        val title = itemCard.findViewById<TextView>(R.id.news_item_title)
                        val meta = itemCard.findViewById<TextView>(R.id.news_item_meta)
                        val headline = itemCard.findViewById<TextView>(R.id.news_item_headline)

                        title.text = a.title
                        val formattedDate = HistoryScreenController.formatFullDate(a.publicationDate)
                        val metaText = (if (formattedDate.isNotEmpty()) formattedDate else a.publicationDate) +
                            if (a.author.isNotEmpty()) " · ${a.author}" else ""
                        meta.text = metaText

                        if (a.headline.isNotEmpty()) {
                            headline.visibility = View.VISIBLE
                            headline.text = a.headline
                        } else {
                            headline.visibility = View.GONE
                        }

                        itemCard.isClickable = true
                        itemCard.isFocusable = true
                        itemCard.setOnClickListener { showNewsDetails(activity, a) }
                        list.addView(itemCard)
                    }
                }
            }

            UiHelper.watchSearch(search) { value ->
                onQueryChanged.accept(value)
                populate.run()
            }
            populate.run()
        }

        private fun showNewsDetails(activity: MainActivity, article: NewsArticle) {
            val box = LinearLayout(activity)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(
                UiHelper.dp(activity, 24),
                UiHelper.dp(activity, 8),
                UiHelper.dp(activity, 24),
                UiHelper.dp(activity, 8)
            )

            UiHelper.add(activity, box, article.title, 20, UiHelper.INK, true)
            val formattedDate = HistoryScreenController.formatFullDate(article.publicationDate)
            val meta = (if (formattedDate.isNotEmpty()) formattedDate else article.publicationDate) +
                if (article.author.isNotEmpty()) " · ${article.author}" else ""
            UiHelper.add(activity, box, meta, 13, UiHelper.GREEN, true)

            if (article.content.isNotEmpty() || article.headline.isNotEmpty()) {
                UiHelper.add(
                    activity,
                    box,
                    if (article.content.isEmpty()) article.headline else UiHelper.plain(article.content),
                    15,
                    UiHelper.INK,
                    false
                )
            }

            val scroll = ScrollView(activity)
            scroll.addView(box)

            val builder = MaterialAlertDialogBuilder(activity)
                .setView(scroll)
                .setPositiveButton(activity.getString(R.string.btn_close), null)
                .setNeutralButton(activity.getString(R.string.news_copy_content)) { _, _ ->
                    val cb = activity.getSystemService(ClipboardManager::class.java)
                    if (cb != null) {
                        val text = article.title + "\n\n" + meta + "\n\n" +
                            (if (article.content.isEmpty()) article.headline else UiHelper.plain(article.content)) +
                            if (article.url.isEmpty()) "" else "\n\n${article.url}"
                        cb.setPrimaryClip(ClipData.newPlainText("Komunikat Vistula", text))
                        Toast.makeText(activity, activity.getString(R.string.news_copied_toast), Toast.LENGTH_SHORT).show()
                    }
                }

            if (article.url.isNotEmpty()) {
                builder.setNegativeButton(activity.getString(R.string.news_open_page)) { _, _ ->
                    UiHelper.browser(activity, article.url)
                }
            }

            builder.show()
        }
    }
}

