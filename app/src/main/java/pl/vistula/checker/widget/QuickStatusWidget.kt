package pl.vistula.checker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.util.Locale
import org.json.JSONObject
import pl.vistula.checker.Demo
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.Repository
import pl.vistula.checker.SyncWorker
import pl.vistula.checker.core.GradeSummary
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.ui.Language

class QuickStatusWidget : AppWidgetProvider() {
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
        const val ACTION_REFRESH: String = "pl.vistula.checker.widget.REFRESH_QUICK_STATUS"

        @JvmStatic
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, QuickStatusWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids != null && ids.isNotEmpty()) {
                for (id in ids) updateWidget(context, manager, id)
            }
        }

        @JvmStatic
        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_status)

            // Click whole widget -> open Start (tab 0)
            val openHome = Intent(context, MainActivity::class.java)
                .putExtra("tab", 0)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val piHome = PendingIntent.getActivity(
                context,
                201,
                openHome,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_status_root, piHome)

            // Click grades box -> open Oceny (tab 2)
            val openGrades = Intent(context, MainActivity::class.java)
                .putExtra("tab", 2)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val piGrades = PendingIntent.getActivity(
                context,
                202,
                openGrades,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_grades_box, piGrades)

            // Click payments box -> open Płatności (tab 8)
            val openPayments = Intent(context, MainActivity::class.java)
                .putExtra("tab", 8)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val piPayments = PendingIntent.getActivity(
                context,
                203,
                openPayments,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_payments_box, piPayments)

            // Click refresh
            val refreshIntent = Intent(context, QuickStatusWidget::class.java).setAction(ACTION_REFRESH)
            val refreshPi = PendingIntent.getBroadcast(
                context,
                204,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_status_refresh, refreshPi)

            var state: JSONObject = try {
                Repository(context).read()
            } catch (e: Exception) {
                JSONObject()
            }
            val demo = state.optString("token").isEmpty()
            if (demo) {
                try {
                    state = Demo.state(false, 56)
                } catch (ignored: Exception) {
                }
            }
            val raw = state.optJSONObject("snapshot")
            var snapshot: Snapshot? = null
            if (raw != null) {
                try {
                    snapshot = Snapshot.from(raw)
                } catch (ignored: Exception) {
                }
            }

            val user = state.optString("user", if (demo) "Jan Kowalski" else "Student USOS")
            views.setTextViewText(R.id.widget_status_name, user)
            val album = state.optString("studentNumber", if (demo) "34589" else "")
            val program = state.optString("programme", state.optString("program", if (demo) "Finanse i Rachunkowość" else ""))
            views.setTextViewText(
                R.id.widget_status_album,
                (if (album.isNotEmpty()) "Nr albumu: $album" else "") +
                    (if (program.isNotEmpty()) (if (album.isNotEmpty()) " · " else "") + program else "")
            )

            // Grade calculation
            val widgetLocale: Locale = Language.currentLocale(context)
            if (snapshot != null && snapshot.records.isNotEmpty()) {
                val summary = GradeSummary.of(snapshot.records.values)
                if (!summary.ectsAverage.isNaN()) {
                    views.setTextViewText(R.id.widget_status_grade_value, String.format(widgetLocale, "%.2f", summary.ectsAverage))
                    val sub = if (summary.ectsAverage >= 4.50) {
                        context.getString(R.string.widget_grade_sub_stipend, summary.totalEcts)
                    } else {
                        context.getString(R.string.widget_grade_sub_weighted, summary.totalEcts)
                    }
                    views.setTextViewText(R.id.widget_status_grade_sub, sub)
                } else if (!summary.average.isNaN()) {
                    views.setTextViewText(R.id.widget_status_grade_value, String.format(widgetLocale, "%.2f", summary.average))
                    views.setTextViewText(R.id.widget_status_grade_sub, context.getString(R.string.widget_grade_sub_simple))
                } else {
                    views.setTextViewText(R.id.widget_status_grade_value, "—")
                    views.setTextViewText(R.id.widget_status_grade_sub, context.getString(R.string.widget_grade_sub_none))
                }
            } else {
                views.setTextViewText(R.id.widget_status_grade_value, "—")
                views.setTextViewText(R.id.widget_status_grade_sub, context.getString(R.string.widget_grade_sub_none))
            }

            // Payments
            val p = snapshot?.payments
            if (p != null) {
                val unpaid = p.totalUnpaidAmount()
                views.setTextViewText(R.id.widget_status_payment_value, String.format(widgetLocale, "%.2f PLN", unpaid))
                when {
                    p.hasOverdue() -> views.setTextViewText(R.id.widget_status_payment_sub, context.getString(R.string.widget_payment_overdue))
                    unpaid == 0.0 -> views.setTextViewText(R.id.widget_status_payment_sub, context.getString(R.string.widget_payment_all_paid))
                    else -> views.setTextViewText(R.id.widget_status_payment_sub, context.getString(R.string.widget_payment_to_pay))
                }
            } else {
                views.setTextViewText(R.id.widget_status_payment_value, "0,00 PLN")
                views.setTextViewText(R.id.widget_status_payment_sub, context.getString(R.string.widget_payment_none))
            }

            manager.updateAppWidget(appWidgetId, views)
        }
    }
}

