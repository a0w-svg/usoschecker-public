package pl.vistula.checker.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
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
        UiHelper.note(activity, content, activity.getString(R.string.history_subtitle), UiHelper.MUTED);

        JSONArray history = d.optJSONArray("history");
        List<Change> allChanges = new ArrayList<>();
        int countGrades = 0, countPlan = 0, countAdded = 0, countModified = 0, countRemoved = 0;
        if (history != null) {
            for (int i = 0; i < history.length(); ++i) {
                try {
                    Change c = Change.from(history.getJSONObject(i));
                    allChanges.add(c);
                    if ("ocena".equals(c.kind)) countGrades++;
                    else if ("plan".equals(c.kind)) countPlan++;
                    if ("Dodano".equals(c.action)) countAdded++;
                    else if ("Zmieniono".equals(c.action)) countModified++;
                    else if ("Usunięto".equals(c.action)) countRemoved++;
                } catch (Exception ignored) {}
            }
        }
        allChanges.sort((a, b) -> Long.compare(b.effectiveTimestamp(), a.effectiveTimestamp()));

        UiHelper.chips(activity, content,
            new String[]{"wszystkie", "ocena", "plan", "Dodano", "Zmieniono", "Usunięto"},
            new String[]{
                activity.getString(R.string.history_chip_all, allChanges.size()),
                activity.getString(R.string.history_chip_grades, countGrades),
                activity.getString(R.string.history_chip_plan, countPlan),
                activity.getString(R.string.history_chip_added, countAdded),
                activity.getString(R.string.history_chip_modified, countModified),
                activity.getString(R.string.history_chip_removed, countRemoved)
            },
            filter, onFilterChanged);

        List<Change> rows = new ArrayList<>();
        for (Change c : allChanges) {
            if (filter.equals("wszystkie") || filter.equals(c.kind) || filter.equals(c.action)) {
                rows.add(c);
            }
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
                List<String> diffList = getDiffSummaryList(activity, c);
                String diffSummaryText = String.join(" ", diffList);
                if (matches(currentQuery, c.title, c.before, c.after, c.action, c.sourceTime, c.date,
                    c.beforeDate, c.afterDate, eventDateDescription(activity, c), formatFullDate(c.date),
                    diffSummaryText,
                    c.optMeta("courseId"), c.optMeta("room"), c.optMeta("lecturers"), c.optMeta("comment"), c.optMeta("valueDescription"), c.optMeta("term"),
                    c.optBeforeMeta("room"), c.optAfterMeta("room"),
                    c.optBeforeMeta("comment"), c.optAfterMeta("comment"),
                    c.optBeforeMeta("valueDescription"), c.optAfterMeta("valueDescription"),
                    c.optBeforeMeta("lecturers"), c.optAfterMeta("lecturers"),
                    c.optBeforeMeta("building"), c.optAfterMeta("building"))) {
                    java.time.LocalDate day = c.effectiveTimestamp() > 0
                        ? Instant.ofEpochMilli(c.effectiveTimestamp()).atZone(UsosClient.ZONE).toLocalDate() : null;
                    if (day != null && !day.equals(sectionDay)) {
                        LocalDate today = LocalDate.now(UsosClient.ZONE);
                        String headerText;
                        if (day.equals(today)) {
                            headerText = activity.getString(R.string.date_heading_today, UiHelper.shortDate(day));
                        } else if (day.equals(today.minusDays(1))) {
                            headerText = activity.getString(R.string.date_heading_yesterday, UiHelper.shortDate(day));
                        } else {
                            headerText = UiHelper.shortDate(day);
                        }
                        UiHelper.heading(activity, list, headerText);
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
                if (!currentQuery.trim().isEmpty()) {
                    box.addView(UiHelper.button(activity, activity.getString(R.string.btn_clear_search), () -> {
                        search.setText("");
                        onQueryChanged.accept("");
                    }, false));
                } else if (!filter.equals("wszystkie")) {
                    box.addView(UiHelper.button(activity, "Pokaż wszystkie zmiany", () -> onFilterChanged.accept("wszystkie"), false));
                } else if (demo && onSimulate != null) {
                    box.addView(UiHelper.button(activity, activity.getString(R.string.history_btn_simulate), onSimulate, true));
                }
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

    public static String formatFullDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return "";
        String s = dateStr.trim();
        try {
            if (s.length() >= 19 && (s.contains(" ") || s.contains("T"))) {
                String normalized = s.substring(0, 19).replace('T', ' ');
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(normalized, pl.vistula.checker.core.UsosParser.TIME);
                String dayName = ldt.format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("pl-PL")));
                String capDay = dayName.substring(0, 1).toUpperCase(Locale.forLanguageTag("pl-PL")) + dayName.substring(1);
                return capDay + ", " + ldt.format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("pl-PL")));
            }
            if (s.length() >= 10) {
                java.time.LocalDate ld = java.time.LocalDate.parse(s.substring(0, 10));
                String dayName = ld.format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("pl-PL")));
                String capDay = dayName.substring(0, 1).toUpperCase(Locale.forLanguageTag("pl-PL")) + dayName.substring(1);
                return capDay + ", " + ld.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("pl-PL")));
            }
        } catch (Exception ignored) {}
        return s;
    }

    public static String eventDateDescription(android.content.Context context, Change c) {
        if (c.kind.equals("plan")) {
            if (!c.beforeDate.isEmpty() && !c.afterDate.isEmpty() && !c.beforeDate.equals(c.afterDate)) {
                return context.getString(R.string.history_item_plan_date_diff, formatFullDate(c.beforeDate), formatFullDate(c.afterDate));
            }
            String date = !c.date.isEmpty() ? c.date : (!c.afterDate.isEmpty() ? c.afterDate : c.beforeDate);
            if (!date.isEmpty()) {
                return context.getString(R.string.history_item_plan_date, formatFullDate(date));
            }
        } else {
            String date = !c.date.isEmpty() ? c.date : (!c.sourceTime.isEmpty() ? c.sourceTime : (!c.afterDate.isEmpty() ? c.afterDate : c.beforeDate));
            if (!date.isEmpty()) {
                return context.getString(R.string.history_item_grade_date, formatFullDate(date));
            }
        }
        return "";
    }

    public static String formatRelativeTime(long millis) {
        if (millis <= 0) return "";
        try {
            Instant event = Instant.ofEpochMilli(millis);
            Instant now = Instant.now();
            long diffSec = java.time.Duration.between(event, now).getSeconds();
            if (diffSec >= 0 && diffSec < 60) return "przed chwilą";
            if (diffSec >= 60 && diffSec < 3600) return (diffSec / 60) + " min temu";

            LocalDate eventDate = event.atZone(UsosClient.ZONE).toLocalDate();
            LocalDate today = LocalDate.now(UsosClient.ZONE);
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pl-PL"));
            String timeStr = event.atZone(UsosClient.ZONE).format(timeFmt);
            if (eventDate.equals(today)) return "Dzisiaj, " + timeStr;
            if (eventDate.equals(today.minusDays(1))) return "Wczoraj, " + timeStr;
            return event.atZone(UsosClient.ZONE).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("pl-PL")));
        } catch (Exception ignored) {
            return "";
        }
    }

    public static List<String> getDiffSummaryList(android.content.Context context, Change c) {
        List<String> diffs = new ArrayList<>();
        String diffGrade = c.optMeta("diffGrade");
        if ("ocena".equals(c.kind) && !diffGrade.isEmpty()) diffs.add("🎓 " + context.getString(R.string.change_grade) + ": " + diffGrade);
        String diffRoom = c.optMeta("diffRoom");
        if (!diffRoom.isEmpty()) diffs.add("📍 " + context.getString(R.string.change_room) + ": " + diffRoom);
        String diffTime = c.optMeta("diffTime");
        if (!diffTime.isEmpty()) diffs.add("⏰ " + context.getString(R.string.change_time) + ": " + diffTime);
        String diffDate = c.optMeta("diffDate");
        if (!diffDate.isEmpty()) {
            if (diffDate.contains(" → ")) {
                String[] parts = diffDate.split(" → ");
                if (parts.length == 2) {
                    diffs.add("📅 " + context.getString(R.string.change_date) + ": " + formatFullDate(parts[0]) + " → " + formatFullDate(parts[1]));
                } else {
                    diffs.add("📅 " + context.getString(R.string.change_date) + ": " + diffDate);
                }
            } else {
                diffs.add("📅 " + context.getString(R.string.change_date) + ": " + formatFullDate(diffDate));
            }
        }
        String diffLecturers = c.optMeta("diffLecturers");
        if (!diffLecturers.isEmpty()) diffs.add("👨‍🏫 " + context.getString(R.string.change_lecturers) + ": " + diffLecturers);
        String diffGroup = c.optMeta("diffGroup");
        if (!diffGroup.isEmpty()) diffs.add("👥 " + context.getString(R.string.change_group) + ": " + diffGroup);
        String diffBuilding = c.optMeta("diffBuilding");
        if (!diffBuilding.isEmpty()) diffs.add("🏢 " + context.getString(R.string.change_building) + ": " + diffBuilding);
        String diffClassType = c.optMeta("diffClassType");
        if (!diffClassType.isEmpty()) diffs.add("📚 " + context.getString(R.string.change_classtype) + ": " + diffClassType);
        String diffPasses = c.optMeta("diffPasses");
        if (!diffPasses.isEmpty()) diffs.add("🎯 " + context.getString(R.string.change_passes) + ": " + diffPasses);
        String diffCounts = c.optMeta("diffCountsIntoAverage");
        if (!diffCounts.isEmpty()) diffs.add("⚖️ " + context.getString(R.string.change_counts_average) + ": " + diffCounts);
        String diffValueDesc = c.optMeta("diffValueDescription");
        if (!diffValueDesc.isEmpty()) diffs.add("📝 " + context.getString(R.string.change_value_description) + ": " + diffValueDesc);
        String diffEcts = c.optMeta("diffEcts");
        if (!diffEcts.isEmpty()) diffs.add("🪙 " + context.getString(R.string.change_ects) + ": " + diffEcts);
        String diffComment = c.optMeta("diffComment");
        if (!diffComment.isEmpty()) diffs.add("💬 " + context.getString(R.string.change_comment) + ": " + diffComment);
        return diffs;
    }

    public static void renderChangeCard(MainActivity activity, LinearLayout parent, Change c) {
        LinearLayout box = UiHelper.card(activity, parent, Color.WHITE);
        box.setClickable(true);
        box.setFocusable(true);

        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        int actionBg, actionFg;
        String actionText;
        if (c.action.equals("Dodano")) {
            actionBg = Color.rgb(232, 245, 233);
            actionFg = Color.rgb(27, 94, 32);
            actionText = "+ " + activity.getString(R.string.history_action_added);
        } else if (c.action.equals("Usunięto")) {
            actionBg = Color.rgb(255, 235, 238);
            actionFg = Color.rgb(183, 28, 28);
            actionText = "✕ " + activity.getString(R.string.history_action_removed);
        } else {
            actionBg = Color.rgb(238, 242, 255);
            actionFg = Color.rgb(49, 46, 129);
            actionText = "✎ " + activity.getString(R.string.history_action_modified);
        }

        LinearLayout badgesGroup = new LinearLayout(activity);
        badgesGroup.setOrientation(LinearLayout.HORIZONTAL);
        badgesGroup.setGravity(Gravity.CENTER_VERTICAL);
        UiHelper.badge(activity, badgesGroup, c.kind.equals("ocena") ? ("🎓 " + activity.getString(R.string.history_category_grade)) : ("📅 " + activity.getString(R.string.history_category_plan)), Color.rgb(241, 245, 249), UiHelper.GREEN);
        UiHelper.badge(activity, badgesGroup, actionText, actionBg, actionFg);

        topBar.addView(badgesGroup, new LinearLayout.LayoutParams(0, -2, 1f));

        String relTime = formatRelativeTime(c.effectiveTimestamp());
        if (!relTime.isEmpty()) {
            TextView timeBadge = UiHelper.text(activity, relTime, 11, UiHelper.MUTED, false);
            timeBadge.setGravity(Gravity.END);
            topBar.addView(timeBadge);
        }
        box.addView(topBar);

        UiHelper.add(activity, box, c.title, 18, UiHelper.INK, true);

        String courseId = c.optMeta("courseId");
        String term = c.optMeta("term");
        if (!courseId.isEmpty() || !term.isEmpty()) {
            String sub = (!courseId.isEmpty() ? "Kod: " + courseId : "") + (!courseId.isEmpty() && !term.isEmpty() ? " · " : "") + (!term.isEmpty() ? "Semestr: " + term : "");
            UiHelper.add(activity, box, sub, 12, UiHelper.MUTED, false);
        }

        String eventDateText = eventDateDescription(activity, c);
        if (!eventDateText.isEmpty()) {
            LinearLayout dateBadge = UiHelper.card(activity, box, Color.rgb(244, 247, 244));
            dateBadge.setPadding(UiHelper.dp(activity, 10), UiHelper.dp(activity, 6), UiHelper.dp(activity, 10), UiHelper.dp(activity, 6));
            UiHelper.add(activity, dateBadge, "📅 " + eventDateText, 13, UiHelper.GREEN, true);
        }

        if (c.action.equals("Zmieniono")) {
            if (c.kind.equals("ocena") && (!c.before.isEmpty() || !c.after.isEmpty())) {
                LinearLayout gradeRow = new LinearLayout(activity);
                gradeRow.setOrientation(LinearLayout.HORIZONTAL);
                gradeRow.setGravity(Gravity.CENTER_VERTICAL);
                gradeRow.setPadding(0, UiHelper.dp(activity, 4), 0, UiHelper.dp(activity, 6));

                TextView beforeBadge = UiHelper.badge(activity, gradeRow, c.before.isEmpty() ? "—" : c.before, Color.rgb(244, 245, 246), Color.rgb(100, 116, 139));
                beforeBadge.setTextSize(16);
                beforeBadge.setPadding(UiHelper.dp(activity, 14), UiHelper.dp(activity, 6), UiHelper.dp(activity, 14), UiHelper.dp(activity, 6));

                TextView arrow = UiHelper.text(activity, " ➔ ", 18, UiHelper.GREEN, true);
                gradeRow.addView(arrow);

                TextView afterBadge = UiHelper.badge(activity, gradeRow, c.after.isEmpty() ? "—" : c.after, Color.rgb(214, 241, 229), UiHelper.GREEN);
                afterBadge.setTextSize(18);
                afterBadge.setPadding(UiHelper.dp(activity, 14), UiHelper.dp(activity, 6), UiHelper.dp(activity, 14), UiHelper.dp(activity, 6));

                box.addView(gradeRow);
            }

            List<String> diffPills = getDiffSummaryList(activity, c);
            if (!diffPills.isEmpty()) {
                LinearLayout diffsBox = UiHelper.card(activity, box, Color.rgb(240, 247, 255));
                diffsBox.setPadding(UiHelper.dp(activity, 12), UiHelper.dp(activity, 8), UiHelper.dp(activity, 12), UiHelper.dp(activity, 8));
                UiHelper.add(activity, diffsBox, activity.getString(R.string.history_diff_summary_title), 12, Color.rgb(30, 64, 175), true);
                for (String diff : diffPills) {
                    UiHelper.add(activity, diffsBox, diff, 13, UiHelper.INK, false);
                }
            }

            if (!c.kind.equals("ocena")) {
                if (!c.before.isEmpty()) {
                    LinearLayout before = UiHelper.card(activity, box, Color.rgb(244, 247, 244));
                    if (!c.beforeDate.isEmpty() && !c.afterDate.isEmpty() && !c.beforeDate.equals(c.afterDate)) {
                        UiHelper.add(activity, before, "📅 Przed zmianą (" + formatFullDate(c.beforeDate) + "):", 12, UiHelper.MUTED, true);
                    }
                    UiHelper.add(activity, before, activity.getString(R.string.history_value_before, c.before), 14, UiHelper.MUTED, false);
                }
                if (!c.after.isEmpty()) {
                    LinearLayout after = UiHelper.card(activity, box, Color.rgb(214, 241, 229));
                    if (!c.beforeDate.isEmpty() && !c.afterDate.isEmpty() && !c.beforeDate.equals(c.afterDate)) {
                        UiHelper.add(activity, after, "📅 Po zmianie (" + formatFullDate(c.afterDate) + "):", 12, UiHelper.GREEN, true);
                    }
                    UiHelper.add(activity, after, activity.getString(R.string.history_value_after, c.after), 14, UiHelper.GREEN, true);
                }
            }
        } else if (c.action.equals("Dodano")) {
            String val = !c.after.isEmpty() ? c.after : c.before;
            if (!val.isEmpty()) {
                LinearLayout addedBox = UiHelper.card(activity, box, Color.rgb(232, 245, 233));
                addedBox.setPadding(UiHelper.dp(activity, 12), UiHelper.dp(activity, 8), UiHelper.dp(activity, 12), UiHelper.dp(activity, 8));
                UiHelper.add(activity, addedBox, val, 14, Color.rgb(27, 94, 32), true);
            }
        } else if (c.action.equals("Usunięto")) {
            String val = !c.before.isEmpty() ? c.before : c.after;
            if (!val.isEmpty()) {
                LinearLayout removedBox = UiHelper.card(activity, box, Color.rgb(255, 235, 238));
                removedBox.setPadding(UiHelper.dp(activity, 12), UiHelper.dp(activity, 8), UiHelper.dp(activity, 12), UiHelper.dp(activity, 8));
                UiHelper.add(activity, removedBox, val, 14, Color.rgb(183, 28, 28), false);
            }
        } else {
            String val = !c.after.isEmpty() ? c.after : c.before;
            if (!val.isEmpty()) UiHelper.add(activity, box, val, 14, UiHelper.INK, false);
        }

        String metadataDetails = changeMetadata(activity, c);
        String detectedTime = c.detectedAt > 0 ? Instant.ofEpochMilli(c.detectedAt).atZone(UsosClient.ZONE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss", Locale.forLanguageTag("pl-PL"))) : c.date;
        String timeInfo = "Wykryto: " + (c.detectedAt > 0 ? Instant.ofEpochMilli(c.detectedAt).atZone(UsosClient.ZONE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.forLanguageTag("pl-PL"))) : c.date);
        UiHelper.add(activity, box, timeInfo, 12, UiHelper.MUTED, false);

        String fullShareText = buildShareText(activity, c, eventDateText, metadataDetails, detectedTime);

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);

        Button btnDetails = UiHelper.button(activity, activity.getString(R.string.history_btn_details), () -> showChangeDetails(activity, c), false);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -2, 1f);
        p1.rightMargin = UiHelper.dp(activity, 8);
        btnRow.addView(btnDetails, p1);

        Button btnShare = UiHelper.button(activity, "📤 " + activity.getString(R.string.share_change_btn), () -> {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.history_share_subject, c.title, c.action));
            share.putExtra(Intent.EXTRA_TEXT, fullShareText);
            try {
                activity.startActivity(Intent.createChooser(share, activity.getString(R.string.share_change_title)));
            } catch (Exception e) {
                ClipboardManager cb = activity.getSystemService(ClipboardManager.class);
                if (cb != null) {
                    cb.setPrimaryClip(UiHelper.privateClip(activity.getString(R.string.history_clipboard_label), fullShareText));
                    Toast.makeText(activity, R.string.toast_history_details_copied, Toast.LENGTH_SHORT).show();
                }
            }
        }, false);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, -2, 1f);
        btnRow.addView(btnShare, p2);

        box.addView(btnRow);
        box.setOnClickListener(v -> showChangeDetails(activity, c));
    }

    public static void showChangeDetails(MainActivity activity, Change c) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiHelper.dp(activity, 24), UiHelper.dp(activity, 8), UiHelper.dp(activity, 24), UiHelper.dp(activity, 8));

        UiHelper.add(activity, box, c.title, 20, UiHelper.INK, true);
        UiHelper.add(activity, box, "Akcja: " + c.action + " · Typ: " + c.kind, 13, UiHelper.GREEN, true);

        String eventDateText = eventDateDescription(activity, c);
        if (!eventDateText.isEmpty()) {
            UiHelper.add(activity, box, "📅 " + eventDateText, 14, UiHelper.GREEN, true);
        }

        if (!c.before.isEmpty()) UiHelper.add(activity, box, activity.getString(R.string.history_value_before, c.before), 14, UiHelper.MUTED, false);
        if (!c.after.isEmpty()) UiHelper.add(activity, box, activity.getString(R.string.history_value_after, c.after), 14, UiHelper.INK, true);
        String metadataDetails = changeMetadata(activity, c);
        if (!metadataDetails.isEmpty()) UiHelper.add(activity, box, metadataDetails, 14, UiHelper.INK, false);

        String detectedTime = c.detectedAt > 0 ? Instant.ofEpochMilli(c.detectedAt).atZone(UsosClient.ZONE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss", Locale.forLanguageTag("pl-PL"))) : c.date;
        UiHelper.add(activity, box, "Wykryto: " + detectedTime, 12, UiHelper.MUTED, false);
        if (!c.sourceTime.isEmpty()) UiHelper.add(activity, box, "Czas z serwera USOS: " + c.sourceTime, 12, UiHelper.MUTED, false);

        String fullShareText = buildShareText(activity, c, eventDateText, metadataDetails, detectedTime);
        box.addView(UiHelper.button(activity, "📤 " + activity.getString(R.string.share_change_btn), () -> {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.history_share_subject, c.title, c.action));
            share.putExtra(Intent.EXTRA_TEXT, fullShareText);
            try {
                activity.startActivity(Intent.createChooser(share, activity.getString(R.string.share_change_title)));
            } catch (Exception e) {
                ClipboardManager cb = activity.getSystemService(ClipboardManager.class);
                if (cb != null) {
                    cb.setPrimaryClip(UiHelper.privateClip(activity.getString(R.string.history_clipboard_label), fullShareText));
                    Toast.makeText(activity, R.string.toast_history_details_copied, Toast.LENGTH_SHORT).show();
                }
            }
        }, false));

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
                    cb.setPrimaryClip(UiHelper.privateClip(activity.getString(R.string.history_clipboard_label), fullShareText));
                    Toast.makeText(activity, R.string.toast_history_details_copied, Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private static String buildShareText(MainActivity activity, Change c, String eventDateText, String metadataDetails, String detectedTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔔 ").append(c.title).append("\n");
        sb.append(activity.getString(R.string.history_share_subject, c.title, c.action)).append("\n");
        if (!eventDateText.isEmpty()) sb.append("📅 ").append(eventDateText).append("\n");
        if (!c.before.isEmpty()) sb.append(activity.getString(R.string.history_value_before, c.before)).append("\n");
        if (!c.after.isEmpty()) sb.append(activity.getString(R.string.history_value_after, c.after)).append("\n");
        if (!metadataDetails.isEmpty()) sb.append(metadataDetails).append("\n");
        sb.append("Wykryto: ").append(detectedTime).append("\n");
        if (!c.sourceTime.isEmpty()) sb.append("USOS: ").append(c.sourceTime).append("\n");
        sb.append("\n— ").append(activity.getString(R.string.history_share_footer));
        return sb.toString().trim();
    }

    private static String changeMetadata(MainActivity activity, Change change) {
        StringBuilder text = new StringBuilder();
        List<String> diffs = getDiffSummaryList(activity, change);
        if (!diffs.isEmpty()) {
            for (String diff : diffs) {
                text.append(diff).append("\n");
            }
        }
        String[] keys = {"room", "building", "lecturers", "groupNumber", "classType", "comment", "valueDescription", "term"};
        String[] labels = {activity.getString(R.string.change_room), activity.getString(R.string.change_building),
            activity.getString(R.string.change_lecturers), activity.getString(R.string.change_group),
            activity.getString(R.string.change_classtype), activity.getString(R.string.change_comment),
            activity.getString(R.string.change_value_description), activity.getString(R.string.change_term)};
        for (int i = 0; i < keys.length; i++) {
            String before = change.optBeforeMeta(keys[i]), after = change.optAfterMeta(keys[i]);
            if (before.isEmpty() && after.isEmpty()) continue;
            if (change.action.equals("Zmieniono")) {
                if (!before.equals(after)) continue; // Already in diffs
                text.append(labels[i]).append(": ").append(after).append("\n");
            } else if (change.action.equals("Dodano")) {
                String val = !after.isEmpty() ? after : before;
                if (!val.isEmpty()) text.append(labels[i]).append(": ").append(val).append("\n");
            } else if (change.action.equals("Usunięto")) {
                String val = !before.isEmpty() ? before : after;
                if (!val.isEmpty()) text.append(labels[i]).append(": ").append(val).append("\n");
            }
        }
        if (!change.beforeDate.isEmpty() && !change.afterDate.isEmpty() && !change.beforeDate.equals(change.afterDate)) {
            text.append(activity.getString(R.string.change_date))
                .append(": ").append(formatFullDate(change.beforeDate)).append(" → ").append(formatFullDate(change.afterDate)).append("\n");
        } else if (!change.date.isEmpty()) {
            text.append(activity.getString(R.string.change_date))
                .append(": ").append(formatFullDate(change.date)).append("\n");
        } else if (!change.afterDate.isEmpty()) {
            text.append(activity.getString(R.string.change_date))
                .append(": ").append(formatFullDate(change.afterDate)).append("\n");
        }
        return text.toString().trim();
    }

    private static boolean matches(String query, String... values) {
        return pl.vistula.checker.core.StudentSearch.matches(query == null ? "" : query, values);
    }
}
