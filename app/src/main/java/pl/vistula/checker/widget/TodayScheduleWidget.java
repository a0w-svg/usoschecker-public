package pl.vistula.checker.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.JSONObject;
import pl.vistula.checker.Demo;
import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.Repository;
import pl.vistula.checker.UsosClient;
import pl.vistula.checker.core.AcademicTerm;
import pl.vistula.checker.core.LessonTime;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;

public class TodayScheduleWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "pl.vistula.checker.widget.REFRESH_TODAY_SCHEDULE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            pl.vistula.checker.SyncWorker.refreshFromWidget(context);
        }
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) return;
        ComponentName component = new ComponentName(context, TodayScheduleWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids != null && ids.length > 0) {
            for (int id : ids) updateWidget(context, manager, id);
        }
    }

    static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_schedule);

        // Click root -> open timetable in MainActivity (tab 5)
        Intent openApp = new Intent(context, MainActivity.class)
            .putExtra("tab", 5)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 101, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.widget_root, pi);

        // Click refresh button -> broadcast ACTION_REFRESH
        Intent refreshIntent = new Intent(context, TodayScheduleWidget.class).setAction(ACTION_REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, 102, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPi);

        // Read snapshot
        JSONObject state;
        try {
            state = new Repository(context).read();
        } catch (Exception e) {
            state = new JSONObject();
        }
        boolean demo = state.optString("token").isEmpty();
        if (demo) {
            try { state = Demo.state(false, 56); } catch (Exception ignored) {}
        }
        JSONObject raw = state.optJSONObject("snapshot");
        Snapshot snapshot = null;
        if (raw != null) {
            try { snapshot = Snapshot.from(raw); } catch (Exception ignored) {}
        }

        LocalDate today = LocalDate.now(UsosClient.ZONE);
        Locale widgetLocale = pl.vistula.checker.ui.Language.currentLocale(context);
        views.setTextViewText(R.id.widget_date_text, today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", widgetLocale)));

        // Academic term info
        if (snapshot != null && !snapshot.terms.isEmpty()) {
            AcademicTerm active = null;
            for (AcademicTerm t : snapshot.terms.values()) {
                if (t.isCurrent(today)) { active = t; break; }
            }
            if (active != null) {
                views.setTextViewText(R.id.widget_term_text, active.name);
            } else {
                views.setTextViewText(R.id.widget_term_text, snapshot.terms.values().iterator().next().name);
            }
        } else {
            views.setTextViewText(R.id.widget_term_text, context.getString(R.string.app_full_university_name));
        }

        if (snapshot == null || snapshot.records.isEmpty()) {
            views.setViewVisibility(R.id.widget_lesson_card, View.GONE);
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE);
            views.setTextViewText(R.id.widget_empty_text, demo ? context.getString(R.string.widget_empty_demo) : context.getString(R.string.widget_empty_connect));
            manager.updateAppWidget(appWidgetId, views);
            return;
        }

        Instant now = Instant.now();
        List<LessonTime> todayLessons = new ArrayList<>();
        LessonTime current = null;
        LessonTime next = null;

        for (Record r : snapshot.records.values()) {
            if (!r.kind.equals("plan")) continue;
            LessonTime lt = LessonTime.of(r);
            if (lt.intersects(today)) {
                todayLessons.add(lt);
                if (lt.contains(now)) current = lt;
                else if (lt.start.toInstant().isAfter(now) && (next == null || lt.start.isBefore(next.start))) {
                    next = lt;
                }
            }
        }

        LessonTime highlight = current != null ? current : next;
        boolean isFutureUpcoming = false;
        if (highlight == null && !todayLessons.isEmpty()) {
            todayLessons.sort(Comparator.comparing(a -> a.start));
            highlight = todayLessons.get(todayLessons.size() - 1);
        } else if (highlight == null) {
            LessonTime nearestFuture = null;
            for (Record r : snapshot.records.values()) {
                if (!r.kind.equals("plan")) continue;
                LessonTime lt = LessonTime.of(r);
                if (lt.start.toInstant().isAfter(now)) {
                    if (nearestFuture == null || lt.start.isBefore(nearestFuture.start)) {
                        nearestFuture = lt;
                    }
                }
            }
            if (nearestFuture != null) {
                highlight = nearestFuture;
                isFutureUpcoming = true;
            }
        }

        if (highlight != null) {
            views.setViewVisibility(R.id.widget_lesson_card, View.VISIBLE);
            views.setViewVisibility(R.id.widget_empty_text, View.GONE);

            boolean isCurrent = highlight.contains(now);
            if (isFutureUpcoming) {
                String dayStr = highlight.start.toLocalDate().format(DateTimeFormatter.ofPattern("EEE, d.MM", widgetLocale)).toUpperCase(widgetLocale);
                views.setTextViewText(R.id.widget_lesson_status, context.getString(R.string.widget_status_next, dayStr));
            } else {
                views.setTextViewText(R.id.widget_lesson_status, isCurrent ? context.getString(R.string.widget_status_ongoing) : (highlight.start.toInstant().isAfter(now) ? context.getString(R.string.widget_status_upcoming_today) : context.getString(R.string.widget_status_ended_today)));
            }

            String timeStr = highlight.start.toLocalTime().toString().substring(0, 5) + " – " + highlight.end.toLocalTime().toString().substring(0, 5);
            views.setTextViewText(R.id.widget_lesson_time, timeStr);
            views.setTextViewText(R.id.widget_lesson_title, highlight.record.title);

            String room = highlight.record.optMeta("room");
            String building = highlight.record.optMeta("building");
            String location = (!room.isEmpty() ? "Sala " + room : "") + (!building.isEmpty() ? (!room.isEmpty() ? " · " : "") + building : "");
            if (location.isEmpty()) location = "Vistula";
            views.setTextViewText(R.id.widget_lesson_room, location);

            String lecturers = highlight.record.optMeta("lecturers");
            if (isFutureUpcoming) {
                views.setTextViewText(R.id.widget_lesson_extra, lecturers.isEmpty() ? context.getString(R.string.widget_extra_day_off) : lecturers);
            } else {
                views.setTextViewText(R.id.widget_lesson_extra, lecturers.isEmpty() ? context.getString(R.string.widget_extra_today_classes, todayLessons.size()) : lecturers);
            }
        } else {
            views.setViewVisibility(R.id.widget_lesson_card, View.GONE);
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE);
            views.setTextViewText(R.id.widget_empty_text, context.getString(R.string.widget_empty_no_more));
        }

        manager.updateAppWidget(appWidgetId, views);
    }
}
