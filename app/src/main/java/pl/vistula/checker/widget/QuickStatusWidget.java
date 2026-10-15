package pl.vistula.checker.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;
import java.util.Locale;
import org.json.JSONObject;
import pl.vistula.checker.Demo;
import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.Repository;
import pl.vistula.checker.core.GradeSummary;
import pl.vistula.checker.core.PaymentInfo;
import pl.vistula.checker.core.Snapshot;

public class QuickStatusWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "pl.vistula.checker.widget.REFRESH_QUICK_STATUS";

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
        ComponentName component = new ComponentName(context, QuickStatusWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids != null && ids.length > 0) {
            for (int id : ids) updateWidget(context, manager, id);
        }
    }

    static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_status);

        // Click whole widget -> open Start (tab 0)
        Intent openHome = new Intent(context, MainActivity.class)
            .putExtra("tab", 0)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piHome = PendingIntent.getActivity(context, 201, openHome,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.widget_status_root, piHome);

        // Click grades box -> open Oceny (tab 2)
        Intent openGrades = new Intent(context, MainActivity.class)
            .putExtra("tab", 2)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piGrades = PendingIntent.getActivity(context, 202, openGrades,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.widget_grades_box, piGrades);

        // Click payments box -> open Płatności (tab 8)
        Intent openPayments = new Intent(context, MainActivity.class)
            .putExtra("tab", 8)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piPayments = PendingIntent.getActivity(context, 203, openPayments,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.widget_payments_box, piPayments);

        // Click refresh
        Intent refreshIntent = new Intent(context, QuickStatusWidget.class).setAction(ACTION_REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, 204, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.widget_status_refresh, refreshPi);

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

        String user = state.optString("user", demo ? "Jan Kowalski" : "Student USOS");
        views.setTextViewText(R.id.widget_status_name, user);
        String album = state.optString("studentNumber", demo ? "34589" : "");
        String program = state.optString("programme", state.optString("program", demo ? "Finanse i Rachunkowość" : ""));
        views.setTextViewText(R.id.widget_status_album, (!album.isEmpty() ? "Nr albumu: " + album : "") + (!program.isEmpty() ? (!album.isEmpty() ? " · " : "") + program : ""));

        // Grade calculation
        if (snapshot != null && !snapshot.records.isEmpty()) {
            GradeSummary summary = GradeSummary.of(snapshot.records.values());
            if (!Double.isNaN(summary.ectsAverage)) {
                views.setTextViewText(R.id.widget_status_grade_value, String.format(Locale.forLanguageTag("pl-PL"), "%.2f", summary.ectsAverage));
                String sub = summary.ectsAverage >= 4.50 ? context.getString(R.string.widget_grade_sub_stipend, summary.totalEcts) : context.getString(R.string.widget_grade_sub_weighted, summary.totalEcts);
                views.setTextViewText(R.id.widget_status_grade_sub, sub);
            } else if (!Double.isNaN(summary.average)) {
                views.setTextViewText(R.id.widget_status_grade_value, String.format(Locale.forLanguageTag("pl-PL"), "%.2f", summary.average));
                views.setTextViewText(R.id.widget_status_grade_sub, context.getString(R.string.widget_grade_sub_simple));
            } else {
                views.setTextViewText(R.id.widget_status_grade_value, "—");
                views.setTextViewText(R.id.widget_status_grade_sub, context.getString(R.string.widget_grade_sub_none));
            }
        } else {
            views.setTextViewText(R.id.widget_status_grade_value, "—");
            views.setTextViewText(R.id.widget_status_grade_sub, context.getString(R.string.widget_grade_sub_none));
        }

        // Payments
        if (snapshot != null && snapshot.payments != null) {
            PaymentInfo p = snapshot.payments;
            double unpaid = p.totalUnpaidAmount();
            views.setTextViewText(R.id.widget_status_payment_value, String.format(Locale.forLanguageTag("pl-PL"), "%.2f PLN", unpaid));
            if (p.hasOverdue()) {
                views.setTextViewText(R.id.widget_status_payment_sub, context.getString(R.string.widget_payment_overdue));
            } else if (unpaid == 0) {
                views.setTextViewText(R.id.widget_status_payment_sub, context.getString(R.string.widget_payment_all_paid));
            } else {
                views.setTextViewText(R.id.widget_status_payment_sub, context.getString(R.string.widget_payment_to_pay));
            }
        } else {
            views.setTextViewText(R.id.widget_status_payment_value, "0,00 PLN");
            views.setTextViewText(R.id.widget_status_payment_sub, context.getString(R.string.widget_payment_none));
        }

        manager.updateAppWidget(appWidgetId, views);
    }
}
