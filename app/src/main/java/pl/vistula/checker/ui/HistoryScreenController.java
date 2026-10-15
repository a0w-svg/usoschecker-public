package pl.vistula.checker.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.UsosClient;
import pl.vistula.checker.core.Change;

/**
 * Controller for rendering the Changes History screen and audit details.
 */
public final class HistoryScreenController {

    private HistoryScreenController() {}

    public static void render(MainActivity activity, LinearLayout content, JSONObject d,
                              String filter, String query, boolean demo,
                              java.util.function.Consumer<String> onFilterChanged,
                              java.util.function.Consumer<String> onQueryChanged,
                              Runnable onSimulate) {
        UiHelper.title(activity, content,
            activity.getString(R.string.history_title),
            activity.getString(R.string.history_subtitle));

        UiHelper.chips(activity, content,
            new String[]{"wszystkie", "ocena", "plan", "Dodano", "Zmieniono", "Usunięto"},
            new String[]{"Wszystkie", "Oceny", "Plan", activity.getString(R.string.history_action_added),
                activity.getString(R.string.history_action_modified), activity.getString(R.string.history_action_removed)},
            filter, onFilterChanged);

        JSONArray history = d.optJSONArray("history");
        List<Change> rows = new ArrayList<>();
        if (history != null) {
            for (int i = 0; i < history.length(); ++i) {
                try {
                    Change c = Change.from(history.getJSONObject(i));
                    if (filter.equals("wszystkie") || filter.equals(c.kind) || filter.equals(c.action)) {
                        rows.add(c);
                    }
                } catch (Exception ignored) {}
            }
            rows.sort((a, b) -> Long.compare(b.effectiveTimestamp(), a.effectiveTimestamp()));
        }

        EditText search = UiHelper.searchField(activity, content,
            activity.getString(R.string.history_search_hint),
            query, null, () -> onQueryChanged.accept(""));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        Runnable populate = () -> {
            list.removeAllViews();
            int count = 0;
            String currentQuery = search.getText().toString();
            java.time.LocalDate sectionDay = null;

            for (Change c : rows) {
                if (matches(currentQuery, c.title, c.before, c.after, c.action, c.sourceTime, c.date,
                    c.beforeDate, c.afterDate,
                    c.optMeta("courseId"), c.optMeta("room"), c.optMeta("lecturers"), c.optMeta("comment"), c.optMeta("valueDescription"), c.optMeta("term"),
                    c.optBeforeMeta("room"), c.optAfterMeta("room"),
                    c.optBeforeMeta("comment"), c.optAfterMeta("comment"),
                    c.optBeforeMeta("valueDescription"), c.optAfterMeta("valueDescription"),
                    c.optBeforeMeta("lecturers"), c.optAfterMeta("lecturers"),
                    c.optBeforeMeta("building"), c.optAfterMeta("building"))) {
                    java.time.LocalDate day = c.effectiveTimestamp() > 0
                        ? Instant.ofEpochMilli(c.effectiveTimestamp()).atZone(UsosClient.ZONE).toLocalDate() : null;
                    if (day != null && !day.equals(sectionDay)) {
                        UiHelper.heading(activity, list, UiHelper.shortDate(day));
                        sectionDay = day;
                    }
                    renderChangeCard(activity, list, c);
                    ++count;
                }
            }

            if (count == 0) {
                LinearLayout box = UiHelper.card(activity, list, Color.WHITE);
                UiHelper.add(activity, box,
                    currentQuery.trim().isEmpty() ? activity.getString(R.string.history_empty_title) : activity.getString(R.string.history_no_match_title),
                    17, UiHelper.INK, true);
                UiHelper.add(activity, box,
                    currentQuery.trim().isEmpty() ? activity.getString(R.string.history_empty_desc) : activity.getString(R.string.history_no_match_desc),
                    14, UiHelper.MUTED, false);
            }

            UiHelper.note(activity, list,
                activity.getString(R.string.history_count_note, count, rows.size()), UiHelper.MUTED);
        };

        UiHelper.watchSearch(search, value -> {
            onQueryChanged.accept(value);
            populate.run();
        });
        populate.run();

        if (demo) {
            content.addView(UiHelper.button(activity, activity.getString(R.string.history_btn_simulate), onSimulate, true));
        }
    }

    public static void renderChangeCard(MainActivity activity, LinearLayout parent, Change c) {
        LinearLayout box = UiHelper.card(activity, parent, Color.WHITE);

        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        int actionBg, actionFg;
        String actionText;
        if (c.action.equals("Dodano")) {
            actionBg = Color.rgb(232, 245, 233);
            actionFg = Color.rgb(27, 94, 32);
            actionText = activity.getString(R.string.history_action_added);
        } else if (c.action.equals("Usunięto")) {
            actionBg = Color.rgb(255, 235, 238);
            actionFg = Color.rgb(183, 28, 28);
            actionText = activity.getString(R.string.history_action_removed);
        } else {
            actionBg = Color.rgb(238, 242, 255);
            actionFg = Color.rgb(49, 46, 129);
            actionText = activity.getString(R.string.history_action_modified);
        }

        UiHelper.badge(activity, topBar, c.kind.equals("ocena") ? activity.getString(R.string.history_category_grade) : activity.getString(R.string.history_category_plan), Color.rgb(241, 245, 249), UiHelper.GREEN);
        UiHelper.badge(activity, topBar, actionText, actionBg, actionFg);
        box.addView(topBar);

        UiHelper.add(activity, box, c.title, 18, UiHelper.INK, true);

        String courseId = c.optMeta("courseId");
        String term = c.optMeta("term");
        if (!courseId.isEmpty() || !term.isEmpty()) {
            String sub = (!courseId.isEmpty() ? "Kod: " + courseId : "") + (!courseId.isEmpty() && !term.isEmpty() ? " · " : "") + (!term.isEmpty() ? "Semestr: " + term : "");
            UiHelper.add(activity, box, sub, 12, UiHelper.MUTED, false);
        }

        if (c.action.equals("Zmieniono")) {
            if (!c.before.isEmpty()) {
                LinearLayout before = UiHelper.card(activity, box, Color.rgb(244, 247, 244));
                UiHelper.add(activity, before, activity.getString(R.string.history_value_before, c.before), 14, UiHelper.MUTED, false);
            }
            if (!c.after.isEmpty()) {
                LinearLayout after = UiHelper.card(activity, box, Color.rgb(214, 241, 229));
                UiHelper.add(activity, after, activity.getString(R.string.history_value_after, c.after), 14, UiHelper.GREEN, true);
            }
        } else {
            String val = !c.after.isEmpty() ? c.after : c.before;
            if (!val.isEmpty()) UiHelper.add(activity, box, val, 14, UiHelper.INK, false);
        }

        String timeInfo = "Wykryto: " + (c.detectedAt > 0 ? Instant.ofEpochMilli(c.detectedAt).atZone(UsosClient.ZONE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.forLanguageTag("pl-PL"))) : c.date);
        UiHelper.add(activity, box, timeInfo, 12, UiHelper.MUTED, false);

        box.addView(UiHelper.button(activity, activity.getString(R.string.history_btn_details), () -> showChangeDetails(activity, c), false));
    }

    public static void showChangeDetails(MainActivity activity, Change c) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiHelper.dp(activity, 24), UiHelper.dp(activity, 8), UiHelper.dp(activity, 24), UiHelper.dp(activity, 8));

        UiHelper.add(activity, box, c.title, 20, UiHelper.INK, true);
        UiHelper.add(activity, box, "Akcja: " + c.action + " · Typ: " + c.kind, 13, UiHelper.GREEN, true);

        if (!c.before.isEmpty()) UiHelper.add(activity, box, activity.getString(R.string.history_value_before, c.before), 14, UiHelper.MUTED, false);
        if (!c.after.isEmpty()) UiHelper.add(activity, box, activity.getString(R.string.history_value_after, c.after), 14, UiHelper.INK, true);
        String metadataDetails = changeMetadata(activity, c);
        if (!metadataDetails.isEmpty()) UiHelper.add(activity, box, metadataDetails, 14, UiHelper.INK, false);

        String detectedTime = c.detectedAt > 0 ? Instant.ofEpochMilli(c.detectedAt).atZone(UsosClient.ZONE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss", Locale.forLanguageTag("pl-PL"))) : c.date;
        UiHelper.add(activity, box, "Wykryto: " + detectedTime, 12, UiHelper.MUTED, false);
        if (!c.sourceTime.isEmpty()) UiHelper.add(activity, box, "Czas z serwera USOS: " + c.sourceTime, 12, UiHelper.MUTED, false);
        try {
            TextView allData = UiHelper.text(activity, c.json().toString(2), 12, UiHelper.INK, false);
            allData.setTextIsSelectable(true); allData.setVisibility(android.view.View.GONE);
            box.addView(UiHelper.button(activity, activity.getString(R.string.grades_full_data), () ->
                allData.setVisibility(allData.getVisibility() == android.view.View.GONE ? android.view.View.VISIBLE : android.view.View.GONE), false));
            box.addView(allData);
        } catch (Exception ignored) {}

        ScrollView scroll = UiHelper.dialogScroll(activity, box);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.history_details_title))
            .setView(scroll)
            .setPositiveButton(activity.getString(R.string.btn_close), null)
            .setNeutralButton(activity.getString(R.string.history_details_copy), (dlg, which) -> {
                ClipboardManager cb = activity.getSystemService(ClipboardManager.class);
                if (cb != null) {
                    String fullText = c.title + "\n" + c.action + " (" + c.kind + ")\nPrzed: " + c.before + "\nPo: " + c.after
                        + "\n" + metadataDetails + "\nWykryto: " + detectedTime + "\nUSOS: " + c.sourceTime;
                    cb.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.history_clipboard_label), fullText));
                    Toast.makeText(activity, R.string.toast_history_details_copied, Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private static String changeMetadata(MainActivity activity, Change change) {
        String[] keys = {"room", "building", "lecturers", "comment", "valueDescription", "term"};
        String[] labels = {activity.getString(R.string.change_room), activity.getString(R.string.change_building),
            activity.getString(R.string.change_lecturers), activity.getString(R.string.change_comment),
            activity.getString(R.string.change_description), activity.getString(R.string.change_term)};
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            String before = change.optBeforeMeta(keys[i]), after = change.optAfterMeta(keys[i]);
            if (before.isEmpty() && after.isEmpty()) continue;
            text.append(labels[i]).append(": ");
            if (!before.equals(after)) text.append(before.isEmpty() ? "—" : before).append(" → ");
            text.append(after.isEmpty() ? "—" : after).append("\n");
        }
        if (!change.beforeDate.equals(change.afterDate)) text.append(activity.getString(R.string.change_date))
            .append(": ").append(change.beforeDate).append(" → ").append(change.afterDate).append("\n");
        return text.toString().trim();
    }

    private static boolean matches(String query, String... values) {
        return pl.vistula.checker.core.StudentSearch.matches(query == null ? "" : query, values);
    }
}
