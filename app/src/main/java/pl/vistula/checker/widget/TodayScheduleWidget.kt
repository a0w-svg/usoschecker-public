package pl.vistula.checker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject
import pl.vistula.checker.Demo
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.Repository
import pl.vistula.checker.SyncWorker
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.AcademicTerm
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.ui.Language

class TodayScheduleWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_REFRESH == intent.action) {
            SyncWorker.refreshFromWidget(context)
        }
    }

    companion object {
        const val ACTION_REFRESH: String = "pl.vistula.checker.widget.REFRESH_TODAY_SCHEDULE"

        @JvmStatic
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, TodayScheduleWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids != null && ids.isNotEmpty()) {
                for (id in ids) updateWidget(context, manager, id)
            }
        }

        @JvmStatic
        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_today_schedule)

            // Click root -> open timetable in MainActivity (tab 5)
            val openApp = Intent(context, MainActivity::class.java)
                .putExtra("tab", 5)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                context,
                101,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            // Click refresh button -> broadcast ACTION_REFRESH
            val refreshIntent = Intent(context, TodayScheduleWidget::class.java).setAction(ACTION_REFRESH)
            val refreshPi = PendingIntent.getBroadcast(
                context,
                102,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPi)

            // Read snapshot
            var state: JSONObject = try {
                Repository(context).read()
            } catch (e: Exception) {
                JSONObject()
            }
            val demo = state.optString("token").isEmpty()
            if (demo) {
                try {
                    state = Demo.state(false, 56)
                } catch (e: Exception) {
                    pl.vistula.checker.Diagnostics.swallowed("TodayScheduleWidget", e)
                }
            }
            val raw = state.optJSONObject("snapshot")
            var snapshot: Snapshot? = null
            if (raw != null) {
                try {
                    snapshot = Snapshot.from(raw)
                } catch (e: Exception) {
                    pl.vistula.checker.Diagnostics.swallowed("TodayScheduleWidget", e)
                }
            }

            val today = LocalDate.now(UsosClient.ZONE)
            val widgetLocale: Locale = Language.currentLocale(context)
            views.setTextViewText(R.id.widget_date_text, today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", widgetLocale)))

            // Academic term info
            if (snapshot != null && snapshot.terms.isNotEmpty()) {
                var active: AcademicTerm? = null
                for (t in snapshot.terms.values) {
                    if (t.isCurrent(today)) {
                        active = t
                        break
                    }
                }
                if (active != null) {
                    views.setTextViewText(R.id.widget_term_text, active.name)
                } else {
                    views.setTextViewText(R.id.widget_term_text, snapshot.terms.values.iterator().next().name)
                }
            } else {
                views.setTextViewText(R.id.widget_term_text, context.getString(R.string.app_full_university_name))
            }

            if (snapshot == null || snapshot.records.isEmpty()) {
                views.setViewVisibility(R.id.widget_lesson_card, View.GONE)
                views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_empty_text,
                    if (demo) context.getString(R.string.widget_empty_demo) else context.getString(R.string.widget_empty_connect)
                )
                manager.updateAppWidget(appWidgetId, views)
                return
            }

            val now = Instant.now()
            val todayLessons = ArrayList<LessonTime>()
            var current: LessonTime? = null
            var next: LessonTime? = null

            for (r in snapshot.records.values) {
                if (r.kind != "plan") continue
                val lt = LessonTime.of(r)
                if (lt.intersects(today)) {
                    todayLessons.add(lt)
                    if (lt.contains(now)) {
                        current = lt
                    } else if (lt.start.toInstant().isAfter(now) && (next == null || lt.start.isBefore(next.start))) {
                        next = lt
                    }
                }
            }

            var highlight = current ?: next
            var isFutureUpcoming = false
            if (highlight == null && todayLessons.isNotEmpty()) {
                todayLessons.sortBy { it.start }
                highlight = todayLessons[todayLessons.size - 1]
            } else if (highlight == null) {
                var nearestFuture: LessonTime? = null
                for (r in snapshot.records.values) {
                    if (r.kind != "plan") continue
                    val lt = LessonTime.of(r)
                    if (lt.start.toInstant().isAfter(now)) {
                        if (nearestFuture == null || lt.start.isBefore(nearestFuture.start)) {
                            nearestFuture = lt
                        }
                    }
                }
                if (nearestFuture != null) {
                    highlight = nearestFuture
                    isFutureUpcoming = true
                }
            }

            if (highlight != null) {
                views.setViewVisibility(R.id.widget_lesson_card, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_text, View.GONE)

                val isCurrent = highlight.contains(now)
                if (isFutureUpcoming) {
                    val dayStr = highlight.start.toLocalDate()
                        .format(DateTimeFormatter.ofPattern("EEE, d.MM", widgetLocale))
                        .uppercase(widgetLocale)
                    views.setTextViewText(R.id.widget_lesson_status, context.getString(R.string.widget_status_next, dayStr))
                } else {
                    views.setTextViewText(
                        R.id.widget_lesson_status,
                        if (isCurrent) {
                            context.getString(R.string.widget_status_ongoing)
                        } else if (highlight.start.toInstant().isAfter(now)) {
                            context.getString(R.string.widget_status_upcoming_today)
                        } else {
                            context.getString(R.string.widget_status_ended_today)
                        }
                    )
                }

                val timeStr = highlight.start.toLocalTime().toString().substring(0, 5) +
                    " – " +
                    highlight.end.toLocalTime().toString().substring(0, 5)
                views.setTextViewText(R.id.widget_lesson_time, timeStr)
                views.setTextViewText(R.id.widget_lesson_title, highlight.record.title)

                val room = highlight.record.optMeta("room")
                val building = highlight.record.optMeta("building")
                var location = (if (room.isNotEmpty()) "Sala $room" else "") +
                    (if (building.isNotEmpty()) (if (room.isNotEmpty()) " · " else "") + building else "")
                if (location.isEmpty()) location = "Vistula"
                views.setTextViewText(R.id.widget_lesson_room, location)

                val lecturers = highlight.record.optMeta("lecturers")
                if (isFutureUpcoming) {
                    views.setTextViewText(
                        R.id.widget_lesson_extra,
                        lecturers.ifEmpty { context.getString(R.string.widget_extra_day_off) }
                    )
                } else {
                    views.setTextViewText(
                        R.id.widget_lesson_extra,
                        lecturers.ifEmpty { context.getString(R.string.widget_extra_today_classes, todayLessons.size) }
                    )
                }
            } else {
                views.setViewVisibility(R.id.widget_lesson_card, View.GONE)
                views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty_text, context.getString(R.string.widget_empty_no_more))
            }

            manager.updateAppWidget(appWidgetId, views)
        }
    }
}

