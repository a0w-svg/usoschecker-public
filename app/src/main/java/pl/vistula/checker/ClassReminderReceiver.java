package pl.vistula.checker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.time.Instant;
import java.util.*;
import org.json.JSONObject;
import pl.vistula.checker.core.LessonTime;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;

public class ClassReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_REMINDER = "pl.vistula.checker.CLASS_REMINDER";
    public static final String PREF_REMINDERS_ENABLED = "reminders_enabled";
    public static final String PREF_REMINDER_MINUTES = "reminder_minutes";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String room = intent.getStringExtra("room");
        String time = intent.getStringExtra("time");
        int minutes = intent.getIntExtra("minutes", 15);

        if (title != null && !title.isEmpty()) {
            NotificationHelper.createChannels(context);

            Intent openPlan = new Intent(context, MainActivity.class)
                .putExtra("tab", 5)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(context, 301, openPlan,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

            String content = "Za " + minutes + " min · " + (!room.isEmpty() ? "Sala " + room + " · " : "") + time;
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Zbliżające się zajęcia: " + title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content + "\nKliknij, aby otworzyć plan zajęć."))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(0xFF176B57);

            try {
                NotificationManagerCompat.from(context).notify(3001, builder.build());
            } catch (SecurityException ignored) {}
        }

        // Schedule next reminder
        scheduleNext(context);
    }

    public static void scheduleNext(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("student_tools", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(PREF_REMINDERS_ENABLED, false);
        if (!enabled) return;
        int minutesBefore = prefs.getInt(PREF_REMINDER_MINUTES, 15);

        JSONObject state;
        try {
            state = new Repository(context).read();
        } catch (Exception e) {
            state = new JSONObject();
        }
        if (state.optString("token").isEmpty()) {
            try { state = Demo.state(false, 56); } catch (Exception ignored) {}
        }

        JSONObject raw = state.optJSONObject("snapshot");
        if (raw == null) return;
        Snapshot snapshot = null;
        try { snapshot = Snapshot.from(raw); } catch (Exception ignored) {}
        if (snapshot == null) return;

        Instant now = Instant.now();
        List<LessonTime> lessons = LessonTime.sorted(snapshot.records.values());
        LessonTime nextLesson = null;
        long targetEpochMs = 0;

        for (LessonTime lt : lessons) {
            long lessonStartMs = lt.start.toInstant().toEpochMilli();
            long reminderMs = lessonStartMs - (minutesBefore * 60L * 1000L);
            if (reminderMs > now.toEpochMilli()) {
                nextLesson = lt;
                targetEpochMs = reminderMs;
                break;
            }
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ClassReminderReceiver.class).setAction(ACTION_REMINDER);
        PendingIntent pi = PendingIntent.getBroadcast(context, 302, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        if (nextLesson == null) {
            am.cancel(pi);
            return;
        }

        Record r = nextLesson.record;
        String room = r.optMeta("room");
        String building = r.optMeta("building");
        String loc = (!room.isEmpty() ? room : "") + (!building.isEmpty() ? " (" + building + ")" : "");
        String timeStr = nextLesson.start.toLocalTime().toString().substring(0, 5) + " – " + nextLesson.end.toLocalTime().toString().substring(0, 5);

        Intent fillIntent = new Intent(context, ClassReminderReceiver.class)
            .setAction(ACTION_REMINDER)
            .putExtra("title", r.title)
            .putExtra("room", loc)
            .putExtra("time", timeStr)
            .putExtra("minutes", minutesBefore);
        PendingIntent scheduledPi = PendingIntent.getBroadcast(context, 302, fillIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        if (Build.VERSION.SDK_INT >= 23) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpochMs, scheduledPi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, targetEpochMs, scheduledPi);
        }
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, ClassReminderReceiver.class).setAction(ACTION_REMINDER);
        PendingIntent pi = PendingIntent.getBroadcast(context, 302, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        am.cancel(pi);
    }
}
