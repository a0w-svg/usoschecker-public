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
import pl.vistula.checker.core.ScheduleBreaks;
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

        LocalDate today = LocalDate.now(UsosClient.ZONE);
        LocalDate activeDate = calendarDate.isEmpty() ? today : LocalDate.parse(calendarDate);
        boolean weekly = calendarMode.equals("week");
        LocalDate monday = activeDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        java.util.function.IntConsumer move = direction -> {
            LocalDate target = activeDate.plusDays(direction * (weekly ? 7L : 1L));
            activity.setCalendarDate(target.toString());
            if (calendarMode.equals("agenda")) onModeChanged.accept("day");
            else onDateChanged.accept(target.toString());
        };

        LinearLayout navBar = new LinearLayout(activity);
        navBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button previous = headerButton(activity, "‹", () -> move.accept(-1));
        previous.setContentDescription(activity.getString(weekly ? R.string.schedule_previous_week : R.string.schedule_previous_day));
        Button next = headerButton(activity, "›", () -> move.accept(1));
        next.setContentDescription(activity.getString(weekly ? R.string.schedule_next_week : R.string.schedule_next_day));
        String dateLabel = calendarMode.equals("agenda") ? activity.getString(R.string.schedule_mode_agenda)
            : weekly ? monday.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pl-PL"))) + " – "
                + monday.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pl-PL")))
            : activeDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.forLanguageTag("pl-PL")));
        Button date = headerButton(activity, dateLabel, () -> {
            DatePickerDialog picker = new DatePickerDialog(activity, (view, year, month, day) -> {
                activity.setCalendarDate(LocalDate.of(year, month + 1, day).toString());
                onModeChanged.accept(weekly ? "week" : "day");
            }, activeDate.getYear(), activeDate.getMonthValue() - 1, activeDate.getDayOfMonth());
            picker.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, activity.getString(R.string.schedule_btn_today), (dialog, which) -> {
                activity.setCalendarDate(today.toString()); onModeChanged.accept(weekly ? "week" : "day");
            });
            picker.show();
        });
        date.setContentDescription(activity.getString(R.string.schedule_btn_choose_date) + ": " + dateLabel);
        date.setTextSize(16);
        date.setTypeface(null, android.graphics.Typeface.BOLD);
        Button more = headerButton(activity, "⋮", () -> {});
        more.setId(R.id.schedule_more);
        more.setContentDescription(activity.getString(R.string.schedule_options));
        navBar.addView(previous, new LinearLayout.LayoutParams(UiHelper.dp(activity, 48), -2));
        navBar.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
        navBar.addView(next, new LinearLayout.LayoutParams(UiHelper.dp(activity, 48), -2));
        navBar.addView(more, new LinearLayout.LayoutParams(UiHelper.dp(activity, 48), -2));
        content.addView(navBar);

        EditText search = UiHelper.searchField(activity, content, activity.getString(R.string.schedule_search_hint), activity.getCalendarQuery(), null, () -> activity.setCalendarQuery(""));
        View searchBox = content.getChildAt(content.getChildCount() - 1);
        searchBox.setVisibility(activity.getCalendarQuery().isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout results = new ScheduleSwipeLayout(activity, move);
        results.setId(R.id.schedule_swipe_content);
        results.setMinimumHeight(UiHelper.dp(activity, 240));
        content.addView(results);
        more.setOnClickListener(view -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(activity, more);
            String[] modes = {"day", "week", "agenda"};
            int[] labels = {R.string.schedule_day_label, R.string.schedule_mode_week, R.string.schedule_mode_agenda};
            for (int i = 0; i < modes.length; i++) menu.getMenu().add(1, i + 1, i, labels[i]).setCheckable(true)
                .setChecked(modes[i].equals(calendarMode.equals("list") ? "day" : calendarMode));
            menu.getMenu().setGroupCheckable(1, true, true);
            menu.getMenu().add(2, 4, 4, R.string.schedule_btn_today);
            menu.getMenu().add(2, 5, 5, R.string.schedule_search_action);
            List<LessonTime> visible = PlanBrowser.select(LessonTime.sorted(planRows), activeDate, calendarMode, activity.getCalendarQuery());
            menu.getMenu().add(3, 6, 6, R.string.schedule_export_ics).setEnabled(!visible.isEmpty());
            menu.getMenu().add(3, 7, 7, R.string.schedule_share_plan).setEnabled(!visible.isEmpty());
            menu.getMenu().add(4, 8, 8, R.string.schedule_info_title);
            menu.getMenu().add(4, 9, 9, R.string.btn_refresh);
            menu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id >= 1 && id <= 3) onModeChanged.accept(modes[id - 1]);
                else if (id == 4) { activity.setCalendarDate(today.toString()); onModeChanged.accept(weekly ? "week" : "day"); }
                else if (id == 5) {
                    searchBox.setVisibility(View.VISIBLE); search.requestFocus();
                    search.post(() -> ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT));
                } else if (id == 6) {
                    List<Record> rows = new ArrayList<>(); for (LessonTime lesson : visible) rows.add(lesson.record);
                    exportPlanToIcs(activity, rows);
                } else if (id == 7) sharePlanText(activity, visible, activeDate);
                else if (id == 8) {
                    String info = activity.getString(R.string.schedule_saved_range, snapshot.start, snapshot.end,
                        UiHelper.timestamp(d.optLong("successAt"))) + "\n\n" + activity.getString(R.string.schedule_warsaw_zone);
                    if (!conflicts.isEmpty()) info += "\n\n" + activity.getString(R.string.home_conflicts_title, conflicts.size())
                        + "\n" + conflicts.get(0).description();
                    new MaterialAlertDialogBuilder(activity).setTitle(R.string.schedule_info_title).setMessage(info)
                        .setPositiveButton(R.string.btn_close, null).show();
                } else if (id == 9) onRefresh.run();
                return true;
            });
            menu.show();
        });

        List<LessonTime> allLessons = LessonTime.sorted(planRows);
        Runnable[] populateRef = new Runnable[1];
        populateRef[0] = () -> renderResults(activity, results, allLessons, activeDate, calendarMode,
            search.getText().toString(), conflictLessonKeys, onDateChanged, onModeChanged, listener, () -> {
                search.setText("");
                activity.setCalendarQuery("");
                populateRef[0].run();
            });
        UiHelper.watchSearch(search, value -> { activity.setCalendarQuery(value); populateRef[0].run(); });
        populateRef[0].run();
    }

    private static void renderResults(MainActivity activity, LinearLayout content, List<LessonTime> allLessons,
                                      LocalDate activeDate, String calendarMode, String query, Set<String> conflictLessonKeys,
                                      java.util.function.Consumer<String> onDateChanged,
                                      java.util.function.Consumer<String> onModeChanged, ScheduleListener listener,
                                      Runnable onClearSearch) {
        content.removeAllViews();
        List<LessonTime> visible = PlanBrowser.select(allLessons, activeDate, calendarMode, query);

        if (visible.isEmpty()) {
            UiHelper.empty(activity, content,
                activeDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("pl-PL"))),
                !query.trim().isEmpty() ? activity.getString(R.string.schedule_no_search_results)
                    : calendarMode.equals("week") ? activity.getString(R.string.schedule_week_empty)
                    : calendarMode.equals("agenda") ? activity.getString(R.string.schedule_agenda_empty)
                    : activity.getString(R.string.schedule_no_classes));
            if (!query.trim().isEmpty() && onClearSearch != null) {
                content.addView(UiHelper.button(activity, activity.getString(R.string.btn_clear_search), onClearSearch, false));
            }
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
            java.util.Map<Instant, ScheduleBreaks.Break> breaks = new java.util.HashMap<>();

            for (LessonTime lesson : visible) {
                LocalDate lessonDay = calendarMode.equals("day") || calendarMode.equals("list") ? activeDate : lesson.start.toLocalDate();
                if (!lessonDay.equals(currentSectionDay)) {
                    currentSectionDay = lessonDay;
                    if (calendarMode.equals("week") || calendarMode.equals("agenda"))
                        UiHelper.heading(activity, content, lessonDay.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("pl-PL"))));
                    breaks.clear();
                    // Use the complete plan so a search cannot turn hidden meetings into free time.
                    for (ScheduleBreaks.Break gap : ScheduleBreaks.inDay(allLessons, lessonDay)) breaks.put(gap.end.toInstant(), gap);
                }

                ScheduleBreaks.Break gap = breaks.remove(lesson.start.toInstant());
                if (gap != null) {
                    LinearLayout breakCard = UiHelper.card(activity, content, Color.rgb(240, 245, 241));
                    breakCard.setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), 0);
                    UiHelper.add(activity, breakCard, activity.getString(R.string.schedule_break_title, gap.minutes), 14, UiHelper.GREEN, true);
                    UiHelper.add(activity, breakCard, activity.getString(R.string.schedule_break_time,
                        gap.start.format(DateTimeFormatter.ofPattern("HH:mm")), gap.end.format(DateTimeFormatter.ofPattern("HH:mm"))), 13, UiHelper.MUTED, false);
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
                String summary = lessonSummary(lesson);
                detailsView.setText(summary);
                detailsView.setVisibility(summary.isEmpty() ? View.GONE : View.VISIBLE);

                card.setClickable(true);
                card.setFocusable(true);
                card.setOnClickListener(v -> showLessonDetails(activity, lesson, listener));
                btnDetails.setVisibility(View.GONE);
                content.addView(card);
            }
        }

        if (!visible.isEmpty() && !calendarMode.equals("agenda") && !calendarMode.equals("week")) {
            DaySummary summary = DaySummary.of(visible, activeDate);
            UiHelper.note(activity, content, activity.getString(R.string.schedule_summary_compact,
                summary.meetings, summary.busyMinutes / 60, summary.busyMinutes % 60), UiHelper.MUTED);
        }
    }

    private static Button headerButton(Context context, String label, Runnable action) {
        Button button = UiHelper.button(context, label, action, false);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        button.setPadding(UiHelper.dp(context, 4), 0, UiHelper.dp(context, 4), 0);
        button.setMinHeight(UiHelper.dp(context, 48));
        return button;
    }

    public static void showLessonDetails(MainActivity activity, LessonTime lesson, ScheduleListener listener) {
        String details = UiHelper.shortDate(lesson.start.toLocalDate()) + "\n\n" + lesson.record.value;
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(UiHelper.dp(activity, 20), UiHelper.dp(activity, 8), UiHelper.dp(activity, 20), UiHelper.dp(activity, 8));
        UiHelper.detailField(activity, body, activity.getString(R.string.schedule_when),
            UiHelper.shortDate(lesson.start.toLocalDate()) + " · "
                + lesson.start.format(DateTimeFormatter.ofPattern("HH:mm")) + " – "
                + (!lesson.start.toLocalDate().equals(lesson.end.toLocalDate())
                    ? UiHelper.shortDate(lesson.end.toLocalDate()) + " " : "")
                + lesson.end.format(DateTimeFormatter.ofPattern("HH:mm")));
        String summary = lessonSummary(lesson);
        if (!summary.isEmpty()) UiHelper.note(activity, body, summary, UiHelper.INK);
        for (String key : new String[]{"building", "groupNumber"}) {
            String value = lesson.record.optMeta(key);
            if (!value.isEmpty()) UiHelper.detailField(activity, body,
                activity.getString(key.equals("building") ? R.string.schedule_building : R.string.schedule_group), value);
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

    /** Avoid repeating the time and building on cards. Old snapshots still keep their readable description. */
    public static String lessonSummary(LessonTime lesson) {
        pl.vistula.checker.core.Record record = lesson.record;
        List<String> lines = new ArrayList<>();
        String type = record.optMeta("classType");
        String room = record.optMeta("room");
        String lecturers = record.optMeta("lecturers");
        // Mixed/older snapshots may have only some of the structured fields.
        List<String> legacyType = new ArrayList<>();
        for (String line : record.value.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Sala ")) {
                if (room.isEmpty()) room = trimmed.substring("Sala ".length());
            } else if (trimmed.startsWith("Prowadzący: ")) {
                if (lecturers.isEmpty()) lecturers = trimmed.substring("Prowadzący: ".length());
            } else if (!trimmed.isEmpty() && !trimmed.matches("(?:\\d{4}-\\d{2}-\\d{2} )?\\d{2}:\\d{2}\\s*[–−-]\\s*\\d{2}:\\d{2}")
                && !trimmed.equals("Termin orientacyjny")) legacyType.add(trimmed);
        }
        if (type.isEmpty()) type = String.join("\n", legacyType);
        if (!type.isEmpty()) lines.add(type);
        if (!room.isEmpty()) lines.add("Sala " + room);
        if (!lecturers.isEmpty()) lines.add("Prowadzący: " + lecturers);
        if (lesson.tentative()) lines.add("Termin orientacyjny");
        return String.join("\n", lines);
    }

    private static void recordAbsenceForLesson(MainActivity activity, LessonTime lesson, ScheduleListener listener) {
        String courseTitle = lesson.record.title + " · " + pl.vistula.checker.core.AttendanceTracker.type(lesson.record);
        SharedPreferences prefs = activity.attendancePreferences();
        int count;
        int limit = pl.vistula.checker.core.AttendanceTracker.LIMIT;
        try {
            JSONObject raw = activity.display().optJSONObject("snapshot");
            Snapshot snapshot = raw == null ? new Snapshot("", "") : Snapshot.from(raw);
            JSONObject saved = pl.vistula.checker.core.AttendanceTracker.reconcile(new JSONObject(prefs.getString("data", "{}")), snapshot);
            String id = pl.vistula.checker.core.AttendanceTracker.record(saved, snapshot, lesson.record);
            JSONObject item = saved.getJSONObject(id);
            count = item.optInt("absences");
            prefs.edit().putString("data", saved.toString()).putString("term", item.optString("term")).putString("classType", "all").apply();
        } catch (Exception e) {
            Toast.makeText(activity, R.string.attendance_save_error, Toast.LENGTH_LONG).show();
            return;
        }

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
