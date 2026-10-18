package pl.vistula.checker.ui;

import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.UsosClient;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.LessonTime;
import pl.vistula.checker.core.NewsArticle;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.ScheduleConflictDetector;
import pl.vistula.checker.core.Snapshot;

/**
 * Controller for rendering the Home dashboard screen.
 */
public final class HomeScreenController {

    private HomeScreenController() {}

    public static void render(MainActivity activity, LinearLayout content, JSONObject d,
                              JSONObject state, boolean demo, JSONArray demoTasks,
                              java.util.function.Consumer<LessonTime> onLessonDetails) throws Exception {
        renderKpis(activity, content, d, state, demo, demoTasks, onLessonDetails);
        renderDeadlinesPreview(activity, content, demo ? demoTasks : state.optJSONArray("tasks"));

        // Payments alert, schedule conflict, latest news
        JSONObject rawSnap = d.optJSONObject("snapshot");
        if (rawSnap != null) {
            Snapshot snap = Snapshot.from(rawSnap);
            if (snap.payments != null) {
                double unpaid = snap.payments.totalUnpaidAmount();
                if (unpaid > 0 || snap.payments.hasOverdue()) {
                    LinearLayout payBox = UiHelper.card(activity, content, snap.payments.hasOverdue() ? Color.rgb(255, 240, 240) : Color.WHITE);
                    UiHelper.add(activity, payBox,
                        snap.payments.hasOverdue() ? activity.getString(R.string.home_payments_attention) : activity.getString(R.string.home_payments_regular),
                        11, snap.payments.hasOverdue() ? Color.rgb(160, 48, 40) : UiHelper.GREEN, true);
                    UiHelper.add(activity, payBox, activity.getString(R.string.home_payments_due, unpaid), 19, UiHelper.INK, true);
                    if (!snap.payments.accounts.isEmpty()) {
                        UiHelper.add(activity, payBox, activity.getString(R.string.home_payments_subaccount, PaymentsScreenController.formatIban(snap.payments.accounts.get(0).bankAccountNumber)), 13, UiHelper.MUTED, false);
                    }
                    payBox.setClickable(true);
                    payBox.setFocusable(true);
                    payBox.setOnClickListener(v -> activity.navigate(8));
                    payBox.addView(UiHelper.button(activity, activity.getString(R.string.home_payments_btn), () -> activity.navigate(8), false));
                }
            }

            List<ScheduleConflictDetector.Conflict> conflicts = ScheduleConflictDetector.findConflicts(snap.records.values());
            if (!conflicts.isEmpty()) {
                LinearLayout conflictBox = UiHelper.card(activity, content, Color.rgb(255, 243, 224));
                UiHelper.add(activity, conflictBox, activity.getString(R.string.home_conflicts_title, conflicts.size()), 11, Color.rgb(217, 119, 6), true);
                UiHelper.add(activity, conflictBox, activity.getString(R.string.home_conflicts_heading), 17, UiHelper.INK, true);
                UiHelper.add(activity, conflictBox, conflicts.get(0).description(), 13, UiHelper.INK, false);
                if (conflicts.size() > 1) {
                    UiHelper.note(activity, conflictBox, activity.getString(R.string.home_conflicts_more, (conflicts.size() - 1)), UiHelper.MUTED);
                }
                conflictBox.addView(UiHelper.button(activity, activity.getString(R.string.home_conflicts_btn), () -> {
                    activity.setCalendarMode("day");
                    activity.openScheduleDate(conflicts.get(0).first.date);
                }, false));
            }

            if (snap.news != null && !snap.news.isEmpty()) {
                NewsArticle top = snap.news.get(0);
                LinearLayout newsBox = UiHelper.card(activity, content, Color.WHITE);
                UiHelper.add(activity, newsBox, activity.getString(R.string.home_news_title), 11, UiHelper.MUTED, true);
                UiHelper.add(activity, newsBox, top.title, 17, UiHelper.INK, true);
                if (!top.headline.isEmpty()) UiHelper.add(activity, newsBox, top.headline, 13, UiHelper.MUTED, false);
                String formattedPubDate = HistoryScreenController.formatFullDate(top.publicationDate);
                UiHelper.add(activity, newsBox, (formattedPubDate.isEmpty() ? top.publicationDate : formattedPubDate) + (!top.author.isEmpty() ? " · " + top.author : ""), 12, UiHelper.MUTED, false);
                newsBox.setClickable(true);
                newsBox.setFocusable(true);
                newsBox.setOnClickListener(v -> activity.navigate(11));
                newsBox.addView(UiHelper.button(activity, activity.getString(R.string.home_news_all_btn, snap.news.size()), () -> activity.navigate(11), false));
            }
        }

        // Recent changes
        renderRecentChanges(activity, content, d);

    }

    private static void renderKpis(MainActivity activity, LinearLayout content, JSONObject d,
                                   JSONObject state, boolean demo, JSONArray demoTasks,
                                   java.util.function.Consumer<LessonTime> onLessonDetails) throws Exception {
        JSONObject raw = d.optJSONObject("snapshot");
        List<Record> records = raw == null ? new ArrayList<>() : new ArrayList<>(Snapshot.from(raw).records.values());
        int grades = 0, todayCount = 0, openTasks = 0;
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(UsosClient.ZONE);

        for (Record r : records) {
            if (r.kind.equals("ocena")) grades++;
            else if (LessonTime.of(r).intersects(today)) todayCount++;
        }

        JSONArray tasks = demo ? demoTasks : (state.optJSONArray("tasks") != null ? state.optJSONArray("tasks") : new JSONArray());
        for (int i = 0; i < tasks.length(); i++) {
            if (!tasks.getJSONObject(i).optBoolean("done")) openTasks++;
        }

        LinearLayout stats = new LinearLayout(activity);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        int[] counts = {grades, todayCount, openTasks};
        String[] titles = {activity.getString(R.string.stat_grades), activity.getString(R.string.stat_today_classes), activity.getString(R.string.stat_tasks)};
        int[] destinations = {2, 5, 7};

        for (int i = 0; i < 3; i++) {
            final int target = destinations[i];
            int countColor = UiHelper.INK;
            View card = UiHelper.kpiCard(activity, stats, String.valueOf(counts[i]), titles[i], countColor, v -> activity.navigate(target));
            if (i > 0) {
                LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) card.getLayoutParams();
                cp.setMarginStart(UiHelper.dp(activity, 8));
                card.setLayoutParams(cp);
            }
        }
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, -2);
        statsParams.bottomMargin = UiHelper.dp(activity, 14);
        content.addView(stats, statsParams);

        // Ongoing lecture
        renderOngoingLectures(activity, content, records);

        // Upcoming lecture
        UiHelper.heading(activity, content, activity.getString(R.string.classes_upcoming));
        LessonTime upcoming = null;
        for (LessonTime lesson : LessonTime.sorted(records)) {
            if (lesson.start.toInstant().isAfter(now)) { upcoming = lesson; break; }
        }

        if (upcoming == null) {
            UiHelper.empty(activity, content,
                activity.getString(R.string.classes_no_upcoming),
                raw == null ? activity.getString(R.string.refresh_offline) : activity.getString(R.string.home_schedule_empty_hint));
        } else {
            boolean focused = LessonTime.current(records, now).isEmpty();
            LinearLayout box = UiHelper.card(activity, content, focused ? Color.rgb(214, 241, 229) : Color.WHITE);
            UiHelper.add(activity, box, upcoming.record.title, 20, UiHelper.INK, true);
            UiHelper.add(activity, box, UiHelper.shortDate(upcoming.start.toLocalDate()), 13, UiHelper.MUTED, true);
            UiHelper.add(activity, box, upcoming.record.value, 14, UiHelper.MUTED, false);
            LessonTime next = upcoming;
            box.setClickable(true);
            box.setFocusable(true);
            box.setOnClickListener(v -> onLessonDetails.accept(next));
            box.addView(UiHelper.button(activity, activity.getString(R.string.btn_open_plan), () -> {
                activity.setCalendarMode("day");
                activity.openScheduleDate(next.start.toLocalDate().toString());
            }, focused));
        }
    }

    private static void renderOngoingLectures(MainActivity activity, LinearLayout parent, Collection<Record> rows) {
        List<LessonTime> current = LessonTime.current(rows, Instant.now());
        if (current.isEmpty()) {
            UiHelper.note(activity, parent, activity.getString(R.string.classes_no_ongoing), UiHelper.MUTED);
            return;
        }
        for (LessonTime lesson : current) {
            LinearLayout box = UiHelper.card(activity, parent, lesson == current.get(0) ? Color.rgb(214, 241, 229) : Color.WHITE);
            UiHelper.add(activity, box, activity.getString(R.string.classes_ongoing), 11, UiHelper.GREEN, true);
            UiHelper.add(activity, box, lesson.record.title, 22, UiHelper.INK, true);
            UiHelper.add(activity, box, lesson.record.value, 14, UiHelper.INK, false);
            long remaining = Math.max(1, (java.time.Duration.between(Instant.now(), lesson.end.toInstant()).getSeconds() + 59) / 60);
            UiHelper.add(activity, box, activity.getString(R.string.home_classes_remaining_min, remaining), 12, UiHelper.GREEN, false);
        }
    }

    private static void renderDeadlinesPreview(MainActivity activity, LinearLayout content, JSONArray rows) throws JSONException {
        if (rows == null) return;
        List<JSONObject> open = new ArrayList<>();
        for (int i = 0; i < rows.length(); ++i) {
            if (!rows.getJSONObject(i).optBoolean("done")) {
                open.add(rows.getJSONObject(i));
            }
        }

        if (!open.isEmpty()) {
            open.sort(Comparator.comparing(tx -> tx.optString("due")));
            UiHelper.heading(activity, content, activity.getString(R.string.deadlines_upcoming));
            LinearLayout box = UiHelper.card(activity, content, Color.WHITE);
            LocalDate today = LocalDate.now(UsosClient.ZONE);

            for (int i = 0; i < Math.min(3, open.size()); ++i) {
                JSONObject t = open.get(i);
                LocalDate due = pl.vistula.checker.core.TaskTools.dueDate(t);
                UiHelper.add(activity, box, t.optString("title"), 16, UiHelper.INK, true);
                if (due == null) {
                    UiHelper.note(activity, box, activity.getString(R.string.tasks_due_unknown), UiHelper.MUTED);
                    continue;
                }
                String prefix = due.isBefore(today) ? (activity.getString(R.string.tasks_chip_overdue) + " · ") : (due.equals(today) ? (activity.getString(R.string.tasks_chip_today) + " · ") : "");
                UiHelper.add(activity, box, prefix + due.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), 13,
                    due.isBefore(today) ? Color.rgb(160, 48, 40) : UiHelper.MUTED, false);
            }

            box.setClickable(true);
            box.setFocusable(true);
            box.setOnClickListener(v -> activity.navigate(7));
            box.addView(UiHelper.button(activity, activity.getString(R.string.home_open_tasks), () -> activity.navigate(7), false));
        }
    }

    private static void renderRecentChanges(MainActivity activity, LinearLayout content, JSONObject d) throws Exception {
        JSONArray rows = d.optJSONArray("history");
        UiHelper.heading(activity, content, activity.getString(R.string.home_recent_changes));
        if (rows != null && rows.length() != 0) {
            List<Change> recent = new ArrayList<>();
            for (int i = 0; i < rows.length(); ++i) {
                recent.add(Change.from(rows.getJSONObject(i)));
            }
            recent.sort((a, b) -> Long.compare(b.effectiveTimestamp(), a.effectiveTimestamp()));
            for (int i = 0; i < Math.min(3, recent.size()); ++i) {
                HistoryScreenController.renderChangeCard(activity, content, recent.get(i));
            }
            content.addView(UiHelper.button(activity, activity.getString(R.string.home_all_history_btn), () -> activity.navigate(1), false));
        } else {
            UiHelper.empty(activity, content,
                activity.getString(R.string.home_no_changes_title),
                activity.getString(R.string.home_no_changes_desc));
        }
    }
}
