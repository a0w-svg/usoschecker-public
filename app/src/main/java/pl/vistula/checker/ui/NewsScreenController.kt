package pl.vistula.checker.ui

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
import pl.vistula.checker.core.CampusAffiliation
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
            onRefresh: Runnable,
            cachedEntry: JSONObject = JSONObject()
        ) {
            ToolScreenUi.header(activity, content, R.string.tool_news_title) { menu ->
                ToolScreenUi.action(menu, R.string.news_university_action, onRefresh)
                ToolScreenUi.action(menu, R.string.news_usos_action) { activity.refreshStudentServices("usosNews") }
                ToolScreenUi.action(menu, R.string.tool_api_methods) { activity.openApiModule("news") }
            }
            val account = activity.serviceAccountState()
            val campus = CampusAffiliation.selected(account)
            if (!activity.isDemo() && campus.isEmpty()) {
                StudentCardsController.status(activity, content, account.optJSONObject("studentServices")?.optJSONObject("affiliation") ?: JSONObject())
                UiHelper.empty(activity, content, activity.getString(R.string.campus_unknown), activity.getString(R.string.campus_automatic_hint))
                content.addView(UiHelper.button(activity, activity.getString(R.string.campus_choose), {
                    CampusSettingsController.choose(activity, account)
                }, true))
                if (activity.connected()) content.addView(UiHelper.button(activity, activity.getString(R.string.campus_detect), {
                    activity.refreshStudentServices("affiliation")
                }, false))
                return
            }
            val data = cachedEntry.optJSONObject("data")
            val entry = if (activity.isDemo() || data != null && CampusAffiliation.newsMatches(data, account)) cachedEntry
                else JSONObject(cachedEntry.toString()).apply { remove("data"); remove("fetchedAt") }
            if (!activity.isDemo()) {
                UiHelper.note(activity, content, CampusAffiliation.campuses[campus], UiHelper.MUTED)
                CampusAffiliation.website(campus)?.let { website ->
                    content.addView(UiHelper.actionRow(activity, activity.getString(R.string.campus_official_site), {
                        UiHelper.browser(activity, website)
                    }, false))
                }
            }
            val raw = if (activity.isDemo()) d.optJSONObject("snapshot") else null
            val snapshot = if (raw != null) Snapshot.from(raw) else null
            val language = pl.vistula.checker.core.ApiLanguage.normalize(Language.currentLocale(activity).language)
            val cached = entry.optJSONObject("data")?.optJSONArray(language)
            val useCache = cached != null && (entry.optJSONObject("data")?.optString("source") == "university" || snapshot == null || snapshot.serviceErrors.containsKey("news") ||
                entry.optLong("fetchedAt") >= d.optLong("successAt"))
            if (useCache || entry.optLong("attemptAt") > d.optLong("successAt")) {
                StudentCardsController.status(activity, content, entry)
            } else if (snapshot != null && snapshot.serviceErrors.containsKey("news")) {
                UiHelper.note(activity, content, activity.getString(R.string.services_refresh_error,
                    activity.getString(R.string.error_operation)), activity.getColor(R.color.app_error))
            }
            val articles = if (useCache) (0 until cached.length()).map { NewsArticle.from(cached.optJSONObject(it)) }
                else snapshot?.news ?: emptyList()
            if (useCache) {
                val university = entry.getJSONObject("data").optString("source") == "university"
                UiHelper.note(activity, content, activity.getString(if (university) R.string.news_source_university else R.string.news_source_usos), UiHelper.MUTED)
                if (university && language == "uk") UiHelper.note(activity, content, activity.getString(R.string.news_language_fallback), UiHelper.MUTED)
            }
            if (snapshot == null && cached == null) {
                UiHelper.note(activity, content, activity.getString(R.string.services_not_loaded), UiHelper.MUTED)
                content.addView(UiHelper.button(activity, activity.getString(R.string.btn_refresh), onRefresh, true))
            }

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
                    if (StudentSearch.matches(q, UiHelper.plain(a.title), UiHelper.plain(a.headline), a.author, UiHelper.plain(a.content))) {
                        filtered.add(a)
                    }
                }

                if (filtered.isEmpty()) {
                    val emptyBox = UiHelper.section(activity, list, Color.WHITE)
                    UiHelper.add(
                        activity,
                        emptyBox,
                        if (q.isEmpty()) activity.getString(if (activity.isDemo()) R.string.news_empty_title else R.string.campus_news_empty) else activity.getString(R.string.news_no_match_title),
                        17,
                        UiHelper.INK,
                        true
                    )
                    UiHelper.note(
                        activity,
                        emptyBox,
                        if (q.isEmpty()) activity.getString(if (activity.isDemo()) R.string.news_empty_desc else R.string.campus_news_hint) else activity.getString(R.string.news_no_match_desc),
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

                        title.text = UiHelper.plain(a.title)
                        val formattedDate = HistoryScreenController.formatFullDate(a.publicationDate)
                        val metaText = (if (formattedDate.isNotEmpty()) formattedDate else a.publicationDate) +
                            if (a.author.isNotEmpty()) " · ${a.author}" else ""
                        meta.text = metaText

                        if (a.headline.isNotEmpty()) {
                            headline.visibility = View.VISIBLE
                            headline.text = UiHelper.plain(a.headline)
                            headline.maxLines = 3
                            headline.ellipsize = android.text.TextUtils.TruncateAt.END
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

            UiHelper.add(activity, box, UiHelper.plain(article.title), 20, UiHelper.INK, true)
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
                        cb.setPrimaryClip(UiHelper.privateClip(activity.getString(R.string.tool_news_title), text))
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

