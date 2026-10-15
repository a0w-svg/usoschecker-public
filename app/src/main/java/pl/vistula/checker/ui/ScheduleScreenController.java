package pl.vistula.checker.ui;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.UsosClient;
import pl.vistula.checker.core.DaySummary;
import pl.vistula.checker.core.IcsExporter;
import pl.vistula.checker.core.LessonTime;
import pl.vistula.checker.core.PlanBrowser;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.ScheduleConflictDetector;
import pl.vistula.checker.core.Snapshot;

/**
 * Controller for rendering the Schedule / Timetable screen, lessons, day picker, and ICS export.
 */
public final class ScheduleScreenController {

    private ScheduleScreenController() {}

    public interface ScheduleListener {
        void onOpenTask(String title);
        void onAbsenceRecorded();
    }

    public static void render(MainActivity activity, LinearLayout content, JSONObject d,
                              JSONObject state, boolean demo,
                              String calendarDate, String calendarMode, int demoPlanDays,
                              java.util.function.Consumer<String> onDateChanged,
                              java.util.function.Consumer<String> onModeChanged,
                              Runnable onRefresh,
                              ScheduleListener listener) throws Exception {
        UiHelper.heading(activity, content, activity.getString(R.string.schedule_heading));
        UiHelper.note(activity, content, activity.getString(R.string.schedule_warsaw_zone), UiHelper.MUTED);

        JSONObject raw = d.optJSONObject("snapshot");
        if (raw == null) {
            UiHelper.empty(activity, content,
                activity.getString(R.string.grades_no_data),
                activity.getString(R.string.refresh_offline));
            boolean connected = !state.optString("token").isEmpty();
            content.addView(UiHelper.button(activity,
                connected ? activity.getString(R.string.btn_refresh) : activity.getString(R.string.btn_connect),
                () -> { if (connected) onRefresh.run(); else activity.navigate(4); }, true));
            return;
        }

        Snapshot snapshot = Snapshot.from(raw);
        UiHelper.note(activity, content, activity.getString(R.string.schedule_saved_range, snapshot.start, snapshot.end,
            UiHelper.timestamp(d.optLong("successAt"))), UiHelper.MUTED);
        List<Record> planRows = new ArrayList<>();
        for (Record r : snapshot.records.values()) {
            if (r.kind.equals("plan")) planRows.add(r);
        }

        List<ScheduleConflictDetector.Conflict> conflicts = ScheduleConflictDetector.findConflicts(planRows);
        Set<String> conflictLessonKeys = new HashSet<>();
        for (ScheduleConflictDetector.Conflict c : conflicts) {
            conflictLessonKeys.add(c.first.id);
            conflictLessonKeys.add(c.second.id);
        }

        if (!conflicts.isEmpty()) {
            LinearLayout conflictCard = UiHelper.card(activity, content, Color.rgb(255, 243, 224));
            UiHelper.add(activity, conflictCard, activity.getString(R.string.home_conflicts_title, conflicts.size()), 11, Color.rgb(217, 119, 6), true);
            UiHelper.add(activity, conflictCard, activity.getString(R.string.home_conflicts_heading), 16, UiHelper.INK, true);
            UiHelper.add(activity, conflictCard, conflicts.get(0).description(), 13, UiHelper.INK, false);
            if (conflicts.size() > 1) {
                UiHelper.note(activity, conflictCard, activity.getString(R.string.home_conflicts_more, (conflicts.size() - 1)), UiHelper.MUTED);
            }
        }

        LocalDate today = LocalDate.now(UsosClient.ZONE);
        LocalDate activeDate = calendarDate.isEmpty() ? today : LocalDate.parse(calendarDate);

        // Date selection bar
        LinearLayout navBar = new LinearLayout(activity);
        navBar.setOrientation(LinearLayout.HORIZONTAL);

        Button todayBtn = UiHelper.button(activity, activity.getString(R.string.schedule_btn_today), () -> {
            onDateChanged.accept(today.toString());
            onModeChanged.accept("day");
        }, activeDate.equals(today) && calendarMode.equals("day"));

        Button chooseDateBtn = UiHelper.button(activity, activity.getString(R.string.schedule_btn_choose_date), () -> {
            new DatePickerDialog(activity, (picker, year, month, day) -> {
                LocalDate picked = LocalDate.of(year, month + 1, day);
                onDateChanged.accept(picked.toString());
                onModeChanged.accept("day");
            }, activeDate.getYear(), activeDate.getMonthValue() - 1, activeDate.getDayOfMonth()).show();
        }, false);

        todayBtn.setTextSize(12);
        todayBtn.setMinHeight(UiHelper.dp(activity, 48));
        chooseDateBtn.setTextSize(12);
        chooseDateBtn.setMinHeight(UiHelper.dp(activity, 48));

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, -2, 1);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1);
        lp1.setMarginEnd(UiHelper.dp(activity, 6));
        navBar.addView(todayBtn, lp1);
        navBar.addView(chooseDateBtn, lp2);
        content.addView(navBar);

        // Mode chips
        UiHelper.chips(activity, content,
            new String[]{"day", "week", "agenda"},
            new String[]{activity.getString(R.string.schedule_mode_day, activeDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pl-PL")))),
                activity.getString(R.string.schedule_mode_week),
                activity.getString(R.string.schedule_mode_agenda)},
            calendarMode.equals("list") ? "day" : calendarMode, onModeChanged);

        LocalDate monday = activeDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LinearLayout weekBar = new LinearLayout(activity);
        weekBar.setOrientation(LinearLayout.HORIZONTAL);
        weekBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button previous = UiHelper.button(activity, "‹", () -> onDateChanged.accept(activeDate.minusWeeks(1).toString()), false);
        previous.setContentDescription(activity.getString(R.string.schedule_previous_week));
        Button next = UiHelper.button(activity, "›", () -> onDateChanged.accept(activeDate.plusWeeks(1).toString()), false);
        next.setContentDescription(activity.getString(R.string.schedule_next_week));
        weekBar.addView(previous, new LinearLayout.LayoutParams(UiHelper.dp(activity, 52), -2));
        TextView weekTitle = UiHelper.text(activity, monday.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pl-PL")))
            + " – " + monday.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("pl-PL"))), 14, UiHelper.INK, true);
        weekTitle.setGravity(android.view.Gravity.CENTER);
        weekBar.addView(weekTitle, new LinearLayout.LayoutParams(0, -2, 1));
        weekBar.addView(next, new LinearLayout.LayoutParams(UiHelper.dp(activity, 52), -2));
        content.addView(weekBar);
        String[] dayValues = new String[7], dayLabels = new String[7];
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            dayValues[i] = date.toString();
            dayLabels[i] = UiHelper.shortDate(date);
        }
        UiHelper.chips(activity, content, dayValues, dayLabels, activeDate.toString(), date -> {
            activity.setCalendarDate(date);
            onModeChanged.accept("day");
        });
        EditText search = UiHelper.searchField(activity, content, activity.getString(R.string.schedule_search_hint), activity.getCalendarQuery(), null, null);
        LinearLayout results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);

        List<LessonTime> allLessons = LessonTime.sorted(planRows);
        Runnable populate = () -> renderResults(activity, results, allLessons, activeDate, calendarMode,
            search.getText().toString(), conflictLessonKeys, onDateChanged, onModeChanged, listener);
        UiHelper.watchSearch(search, value -> { activity.setCalendarQuery(value); populate.run(); });
        populate.run();
        content.addView(UiHelper.button(activity, activity.getString(R.string.btn_refresh), onRefresh, false));
    }

    private static void renderResults(MainActivity activity, LinearLayout content, List<LessonTime> allLessons,
                                      LocalDate activeDate, String calendarMode, String query, Set<String> conflictLessonKeys,
                                      java.util.function.Consumer<String> onDateChanged,
                                      java.util.function.Consumer<String> onModeChanged, ScheduleListener listener) {
        content.removeAllViews();
        List<LessonTime> visible = PlanBrowser.select(allLessons, activeDate, calendarMode, query);
        if (!calendarMode.equals("agenda") && !calendarMode.equals("week")) {
            DaySummary summary = DaySummary.of(visible, activeDate);
            LinearLayout summaryBox = UiHelper.card(activity, content, Color.rgb(214, 241, 229));
            UiHelper.add(activity, summaryBox, activity.getString(R.string.schedule_day_summary, summary.meetings,
                summary.busyMinutes / 60, summary.busyMinutes % 60, summary.gapMinutes), 16, UiHelper.INK, true);
            UiHelper.note(activity, summaryBox, activity.getString(R.string.schedule_summary_hint), UiHelper.MUTED);
        }

        if (visible.isEmpty()) {
            UiHelper.empty(activity, content,
                activeDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("pl-PL"))),
                !query.trim().isEmpty() ? activity.getString(R.string.schedule_no_search_results)
                    : calendarMode.equals("week") ? activity.getString(R.string.schedule_week_empty)
                    : calendarMode.equals("agenda") ? activity.getString(R.string.schedule_agenda_empty)
                    : activity.getString(R.string.schedule_no_classes));
            LessonTime nextAfter = null;
            for (LessonTime l : PlanBrowser.select(allLessons, activeDate, "agenda", query)) {
                if (l.start.toLocalDate().isAfter(activeDate)) {
                    nextAfter = l;
                    break;
                }
            }
            if (nextAfter != null) {
                final LessonTime targetLesson = nextAfter;
                content.addView(UiHelper.button(activity, activity.getString(R.string.schedule_btn_next_class) + " (" + UiHelper.shortDate(nextAfter.start.toLocalDate()) + ")", () -> {
                    onDateChanged.accept(targetLesson.start.toLocalDate().toString());
                    onModeChanged.accept("day");
                }, false));
            }
        } else {
            LocalDate currentSectionDay = null;
            LayoutInflater inflater = LayoutInflater.from(activity);

            for (LessonTime lesson : visible) {
                LocalDate lessonDay = calendarMode.equals("day") || calendarMode.equals("list") ? activeDate : lesson.start.toLocalDate();
                if (!lessonDay.equals(currentSectionDay)) {
                    currentSectionDay = lessonDay;
                    UiHelper.heading(activity, content, lessonDay.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("pl-PL"))));
                }

                boolean isConflict = conflictLessonKeys.contains(lesson.record.id);
                View card = inflater.inflate(R.layout.item_lesson_card, content, false);
                TextView timeView = card.findViewById(R.id.lesson_item_time);
                TextView conflictBadge = card.findViewById(R.id.lesson_item_conflict_badge);
                TextView titleView = card.findViewById(R.id.lesson_item_title);
                TextView detailsView = card.findViewById(R.id.lesson_item_details);
                MaterialButton btnDetails = card.findViewById(R.id.lesson_item_btn_details);

                String timeText = lesson.start.format(DateTimeFormatter.ofPattern("HH:mm")) + " – " + lesson.end.format(DateTimeFormatter.ofPattern("HH:mm"));
                if (!lesson.start.toLocalDate().equals(lesson.end.toLocalDate())) timeText += " · "
                    + activity.getString(R.string.schedule_overnight);
                timeView.setText(timeText);
                if (!lesson.tentative() && lesson.current(Instant.now())) {
                    timeView.setText(activity.getString(R.string.classes_ongoing) + " · " + timeText);
                    card.setBackgroundResource(R.drawable.bg_card_mint);
                }

                if (isConflict) conflictBadge.setVisibility(View.VISIBLE);
                else conflictBadge.setVisibility(View.GONE);

                titleView.setText(lesson.record.title);
                detailsView.setText(lesson.record.value);

                btnDetails.setOnClickListener(v -> showLessonDetails(activity, lesson, listener));
                content.addView(card);
            }
        }

        // Action buttons (Export ICS, Share)
        Button export = UiHelper.button(activity, activity.getString(R.string.schedule_export_ics), () -> {
            List<Record> rows = new ArrayList<>();
            for (LessonTime lesson : visible) rows.add(lesson.record);
            exportPlanToIcs(activity, rows);
        }, false);
        export.setEnabled(!visible.isEmpty());
        content.addView(export);
        Button share = UiHelper.button(activity, activity.getString(R.string.schedule_share_plan),
            () -> sharePlanText(activity, visible, activeDate), false);
        share.setEnabled(!visible.isEmpty());
        content.addView(share);
    }

    public static void showLessonDetails(MainActivity activity, LessonTime lesson, ScheduleListener listener) {
        String details = UiHelper.shortDate(lesson.start.toLocalDate()) + "\n\n" + lesson.record.value;
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(UiHelper.dp(activity, 20), UiHelper.dp(activity, 8), UiHelper.dp(activity, 20), UiHelper.dp(activity, 8));
        for (java.util.Map.Entry<String, String> field : pl.vistula.checker.core.RecordDetails.fields(lesson.record, null).entrySet()) {
            UiHelper.detailField(activity, body, field.getKey(), field.getValue());
        }
        try {
            TextView raw = UiHelper.text(activity, lesson.record.json().toString(2), 12, UiHelper.INK, false);
            raw.setTextIsSelectable(true); raw.setVisibility(View.GONE);
            body.addView(UiHelper.button(activity, activity.getString(R.string.grades_full_data), () ->
                raw.setVisibility(raw.getVisibility() == View.GONE ? View.VISIBLE : View.GONE), false));
            body.addView(raw);
        } catch (Exception ignored) {}
        android.widget.ScrollView scroller = UiHelper.dialogScroll(activity, body);
        new MaterialAlertDialogBuilder(activity)
            .setTitle(lesson.record.title)
            .setView(scroller)
            .setNegativeButton(activity.getString(R.string.btn_close), null)
            .setNeutralButton("Dodaj zadanie", (dialog, which) -> listener.onOpenTask("Zadanie: " + lesson.record.title))
            .setPositiveButton(activity.getString(R.string.btn_more_options), (dialog, which) -> {
                CharSequence[] options = {
                    activity.getString(R.string.lesson_option_absence),
                    activity.getString(R.string.lesson_option_share),
                    activity.getString(R.string.lesson_option_copy)
                };
                new MaterialAlertDialogBuilder(activity)
                    .setTitle(lesson.record.title)
                    .setItems(options, (dlg, idx) -> {
                        if (idx == 0) {
                            recordAbsenceForLesson(activity, lesson, listener);
                        } else if (idx == 1) {
                            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lesson.record.title + "\n" + details);
                            try { activity.startActivity(Intent.createChooser(share, activity.getString(R.string.schedule_share_plan))); }
                            catch (ActivityNotFoundException e) { Toast.makeText(activity, R.string.toast_no_share_app, Toast.LENGTH_SHORT).show(); }
                        } else if (idx == 2) {
                            UiHelper.copyToClipboard(activity, activity.getString(R.string.btn_class_details), lesson.record.title + "\n" + details);
                        }
                    }).show();
            }).show();
    }

    private static void recordAbsenceForLesson(MainActivity activity, LessonTime lesson, ScheduleListener listener) {
        String courseTitle = lesson.record.title;
        String courseCode = lesson.record.optMeta("courseId");
        if (courseCode.isEmpty()) courseCode = courseTitle;

        SharedPreferences prefs = activity.getSharedPreferences("attendance_tracker", Context.MODE_PRIVATE);
        JSONObject saved;
        try { saved = new JSONObject(prefs.getString("data", "{}")); } catch (Exception e) { saved = new JSONObject(); }

        JSONObject item = saved.optJSONObject(courseCode);
        if (item == null) {
            Iterator<String> it = saved.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject obj = saved.optJSONObject(k);
                if (obj != null && courseTitle.equalsIgnoreCase(obj.optString("name"))) {
                    item = obj;
                    courseCode = k;
                    break;
                }
            }
        }

        if (item == null) {
            item = new JSONObject();
            try {
                item.put("name", courseTitle);
                item.put("absences", 0);
                item.put("limit", 2);
                item.put("notes", "");
                saved.put(courseCode, item);
            } catch (Exception ignored) {}
        }

        int count = item.optInt("absences", 0) + 1;
        int limit = item.optInt("limit", 2);
        try {
            item.put("absences", count);
            String oldNotes = item.optString("notes", "");
            String dateStamp = lesson.start.toLocalDate().toString() + " (" + lesson.start.toLocalTime().toString().substring(0, 5) + ")";
            String newNotes = oldNotes.isEmpty() ? "Opuszczono: " + dateStamp : oldNotes + "\nOpuszczono: " + dateStamp;
            item.put("notes", newNotes);
            saved.put(courseCode, item);
            prefs.edit().putString("data", saved.toString()).apply();
        } catch (Exception ignored) {}

        if (count >= limit) {
            String alertMsg = count > limit
                ? activity.getString(R.string.attendance_alert_msg_exceeded)
                : activity.getString(R.string.attendance_alert_msg_reached);
            new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.attendance_alert_title))
                .setMessage(activity.getString(R.string.attendance_alert_body, courseTitle, count, limit, alertMsg))
                .setPositiveButton(activity.getString(R.string.attendance_alert_btn_open), (d, w) -> activity.navigate(12))
                .setNegativeButton(activity.getString(R.string.attendance_alert_btn_understand), null)
                .show();
        } else {
            Toast.makeText(activity, activity.getString(R.string.attendance_recorded_toast, courseTitle, count, limit), Toast.LENGTH_LONG).show();
        }
        listener.onAbsenceRecorded();
    }

    private static void exportPlanToIcs(MainActivity activity, List<Record> rows) {
        if (rows.isEmpty()) return;
        try {
            activity.saveCalendar(IcsExporter.export(rows));
        } catch (Exception e) {
            Toast.makeText(activity, R.string.toast_export_calendar_error, Toast.LENGTH_SHORT).show();
        }
    }

    private static void sharePlanText(MainActivity activity, List<LessonTime> visible, LocalDate activeDate) {
        if (visible.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append(activity.getString(R.string.schedule_share_title, activeDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("pl-PL"))))).append("\n\n");
        for (LessonTime l : visible) {
            sb.append(l.start.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(" – ").append(l.end.format(DateTimeFormatter.ofPattern("HH:mm"))).append(" | ")
                .append(l.record.title).append("\n").append(l.record.value).append("\n\n");
        }
        sb.append(activity.getString(R.string.share_generated_by));
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, sb.toString());
        try { activity.startActivity(Intent.createChooser(share, activity.getString(R.string.schedule_share_plan))); }
        catch (ActivityNotFoundException e) { UiHelper.copyToClipboard(activity, activity.getString(R.string.schedule_clipboard_label), sb.toString()); }
    }
}
