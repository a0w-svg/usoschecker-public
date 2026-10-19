package pl.vistula.checker.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.core.NewsArticle;
import pl.vistula.checker.core.Snapshot;

/**
 * Controller for rendering the University News and Announcements screen.
 */
public final class NewsScreenController {

    private NewsScreenController() {}

    public static void render(MainActivity activity, LinearLayout content, JSONObject d,
                              String query, java.util.function.Consumer<String> onQueryChanged,
                              Runnable onRefresh) throws JSONException {

        ToolScreenUi.header(activity, content, R.string.tool_news_title, menu -> {
            ToolScreenUi.action(menu, R.string.news_refresh_btn, onRefresh);
            ToolScreenUi.action(menu, R.string.tool_api_methods, () -> activity.openApiModule("news"));
        });
        JSONObject raw = d.optJSONObject("snapshot");
        Snapshot snapshot = raw != null ? Snapshot.from(raw) : null;
        if (snapshot != null && snapshot.serviceErrors.containsKey("news")) UiHelper.note(activity, content,
            activity.getString(R.string.services_refresh_error, snapshot.serviceErrors.get("news")), activity.getColor(R.color.app_error));
        List<NewsArticle> articles = snapshot != null && snapshot.news != null ? snapshot.news : Collections.emptyList();

        EditText search = UiHelper.searchField(activity, content,
            activity.getString(R.string.news_search_hint),
            query,
            activity.getString(R.string.news_search_desc),
            () -> onQueryChanged.accept(""));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        Runnable populate = () -> {
            list.removeAllViews();
            String q = search.getText().toString().trim();
            List<NewsArticle> filtered = new ArrayList<>();
            for (NewsArticle a : articles) {
                if (pl.vistula.checker.core.StudentSearch.matches(q, a.title, a.headline, a.author, UiHelper.plain(a.content))) {
                    filtered.add(a);
                }
            }

            if (filtered.isEmpty()) {
                LinearLayout emptyBox = UiHelper.section(activity, list, Color.WHITE);
                UiHelper.add(activity, emptyBox,
                    q.isEmpty() ? activity.getString(R.string.news_empty_title) : activity.getString(R.string.news_no_match_title),
                    17, UiHelper.INK, true);
                UiHelper.note(activity, emptyBox,
                    q.isEmpty() ? activity.getString(R.string.news_empty_desc) : activity.getString(R.string.news_no_match_desc),
                    UiHelper.MUTED);
                if (!q.isEmpty()) {
                    emptyBox.addView(UiHelper.button(activity, activity.getString(R.string.btn_clear_search), () -> {
                        search.setText("");
                        onQueryChanged.accept("");
                    }, false));
                }
            } else {
                UiHelper.note(activity, list, activity.getString(R.string.news_count, filtered.size()), UiHelper.MUTED);
                LayoutInflater inflater = LayoutInflater.from(activity);
                for (NewsArticle a : filtered) {
                    View itemCard = inflater.inflate(R.layout.item_news_card, list, false);
                    TextView title = itemCard.findViewById(R.id.news_item_title);
                    TextView meta = itemCard.findViewById(R.id.news_item_meta);
                    TextView headline = itemCard.findViewById(R.id.news_item_headline);

                    title.setText(a.title);
                    String formattedDate = HistoryScreenController.formatFullDate(a.publicationDate);
                    String metaText = (!formattedDate.isEmpty() ? formattedDate : a.publicationDate) + (!a.author.isEmpty() ? " · " + a.author : "");
                    meta.setText(metaText);

                    if (!a.headline.isEmpty()) {
                        headline.setVisibility(View.VISIBLE);
                        headline.setText(a.headline);
                    } else {
                        headline.setVisibility(View.GONE);
                    }

                    itemCard.setClickable(true);
                    itemCard.setFocusable(true);
                    itemCard.setOnClickListener(v -> showNewsDetails(activity, a));
                    list.addView(itemCard);
                }
            }
        };

        UiHelper.watchSearch(search, value -> {
            onQueryChanged.accept(value);
            populate.run();
        });
        populate.run();

    }

    private static void showNewsDetails(MainActivity activity, NewsArticle article) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiHelper.dp(activity, 24), UiHelper.dp(activity, 8), UiHelper.dp(activity, 24), UiHelper.dp(activity, 8));

        UiHelper.add(activity, box, article.title, 20, UiHelper.INK, true);
        String formattedDate = HistoryScreenController.formatFullDate(article.publicationDate);
        String meta = (!formattedDate.isEmpty() ? formattedDate : article.publicationDate) + (!article.author.isEmpty() ? " · " + article.author : "");
        UiHelper.add(activity, box, meta, 13, UiHelper.GREEN, true);

        if (!article.content.isEmpty() || !article.headline.isEmpty()) {
            UiHelper.add(activity, box, article.content.isEmpty() ? article.headline : UiHelper.plain(article.content), 15, UiHelper.INK, false);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(box);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
            .setView(scroll)
            .setPositiveButton(activity.getString(R.string.btn_close), null)
            .setNeutralButton(activity.getString(R.string.news_copy_content), (dlg, which) -> {
                ClipboardManager cb = activity.getSystemService(ClipboardManager.class);
                if (cb != null) {
                    String text = article.title + "\n\n" + meta + "\n\n" + (article.content.isEmpty() ? article.headline : UiHelper.plain(article.content)) + (article.url.isEmpty() ? "" : "\n\n" + article.url);
                    cb.setPrimaryClip(ClipData.newPlainText("Komunikat Vistula", text));
                    Toast.makeText(activity, activity.getString(R.string.news_copied_toast), Toast.LENGTH_SHORT).show();
                }
            });

        if (!article.url.isEmpty()) {
            builder.setNegativeButton(activity.getString(R.string.news_open_page), (dlg, which) -> UiHelper.browser(activity, article.url));
        }

        builder.show();
    }
}
