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
import pl.vistula.checker.core.ReminderPlanner;

public class ClassReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_REMINDER = "pl.vistula.checker.CLASS_REMINDER";
    public static final String PREF_REMINDERS_ENABLED = "reminders_enabled";
    public static final String PREF_REMINDER_MINUTES = "reminder_minutes";
    private static final java.util.concurrent.ExecutorService DELIVERY = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_REMINDER.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        if (pending == null) { deliver(context, intent); return; }
        Context application = context.getApplicationContext();
        DELIVERY.execute(() -> {
            try { deliver(application, intent); }
            finally { pending.finish(); }
        });
    }

    private static void deliver(Context context, Intent intent) {
        synchronized (Repository.LOCK) { deliverLocked(context, intent); }
    }

    private static void deliverLocked(Context context, Intent intent) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("student_tools", Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_REMINDERS_ENABLED, false)) { cancel(context); return; }
        if (!canNotify(context)) { cancel(context); return; }
        JSONObject state = currentState(context);
        Snapshot snapshot = snapshot(state);
        String recordId = intent.getStringExtra("recordId");
        Record record = snapshot == null || recordId == null ? null : snapshot.records.get(recordId);
        LessonTime lesson = record == null || !record.kind.equals("plan") ? null : LessonTime.of(record);
        if (lesson == null || lesson.tentative() || !lesson.start.toInstant().toString().equals(intent.getStringExtra("start"))
                || !lesson.start.toInstant().isAfter(Instant.now()) || delivered(state).contains(ReminderPlanner.key(lesson))) {
            scheduleNext(context);
            return;
        }
        String title = record.title == null || record.title.trim().isEmpty() ? context.getString(R.string.reminder_unnamed_lesson) : record.title;
        String room = record.optMeta("room");
        String time = lesson.start.toLocalTime().toString().substring(0, 5);
        int minutes = (int) Math.max(1, (java.time.Duration.between(Instant.now(), lesson.start.toInstant()).getSeconds() + 59) / 60);

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
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content + "\nKliknij, aby otworzyć plan zajęć."))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(0xFF176B57);

            try {
                NotificationManagerCompat.from(context).notify(3001, builder.build());
                new Repository(context).update(latest -> {
                    org.json.JSONArray previous = latest.optJSONArray("reminderDeliveries");
                    org.json.JSONArray saved = new org.json.JSONArray().put(ReminderPlanner.key(lesson));
                    if (previous != null) for (int i = 0; i < previous.length() && saved.length() < 200; i++) saved.put(previous.getString(i));
                    latest.put("reminderDeliveries", saved);
                });
            } catch (SecurityException ignored) {}
              catch (Exception ignored) { cancelAlarm(context); return; }
        }

        // Schedule next reminder
        scheduleNext(context);
    }

    public static void scheduleNext(Context context) {
        synchronized (Repository.LOCK) { scheduleNextLocked(context); }
    }

    private static void scheduleNextLocked(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("student_tools", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(PREF_REMINDERS_ENABLED, false);
        if (!enabled) { cancel(context); return; }
        if (!canNotify(context)) { cancel(context); return; }
        int minutesBefore = prefs.getInt(PREF_REMINDER_MINUTES, 15);

        JSONObject state = currentState(context);
        Snapshot snapshot = snapshot(state);
        if (snapshot == null) { cancel(context); return; }

        Instant now = Instant.now();
        LessonTime nextLesson = ReminderPlanner.next(snapshot, delivered(state), now);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        if (nextLesson == null) {
            cancelAlarm(context);
            return;
        }
        long targetEpochMs = ReminderPlanner.triggerAt(nextLesson, minutesBefore, now);

        Intent fillIntent = new Intent(context, ClassReminderReceiver.class)
            .setAction(ACTION_REMINDER)
            .putExtra("recordId", nextLesson.record.id)
            .putExtra("start", nextLesson.start.toInstant().toString());
        PendingIntent scheduledPi = PendingIntent.getBroadcast(context, 302, fillIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        if (Build.VERSION.SDK_INT >= 23) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpochMs, scheduledPi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, targetEpochMs, scheduledPi);
        }
    }

    public static void cancel(Context context) {
        synchronized (Repository.LOCK) {
            cancelAlarm(context);
            NotificationManagerCompat.from(context).cancel(3001);
        }
    }

    private static void cancelAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, ClassReminderReceiver.class).setAction(ACTION_REMINDER);
        PendingIntent pi = PendingIntent.getBroadcast(context, 302, intent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) { am.cancel(pi); pi.cancel(); }
    }

    private static JSONObject currentState(Context context) {
        try {
            return new Repository(context).read();
        } catch (Exception ignored) {}
        return new JSONObject();
    }
    private static Snapshot snapshot(JSONObject state) {
        try {
            JSONObject raw = state.optJSONObject("snapshot");
            if (!state.optString("token").isEmpty() && raw != null) return Snapshot.from(raw);
        } catch (Exception ignored) {}
        return null;
    }
    private static Set<String> delivered(JSONObject state) {
        Set<String> keys = new HashSet<>();
        org.json.JSONArray entries = state.optJSONArray("reminderDeliveries");
        if (entries != null) for (int i = 0; i < entries.length(); i++) keys.add(entries.optString(i));
        return keys;
    }
    public static boolean canNotify(Context context) {
        if ((Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        android.app.NotificationChannel channel = manager == null ? null : manager.getNotificationChannel(NotificationHelper.CHANNEL_REMINDERS);
        return channel == null || channel.getImportance() != android.app.NotificationManager.IMPORTANCE_NONE;
    }
}
