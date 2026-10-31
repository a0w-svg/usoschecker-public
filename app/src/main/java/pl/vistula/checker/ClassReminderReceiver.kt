package pl.vistula.checker

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.ReminderPlanner
import pl.vistula.checker.core.Snapshot

class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_REMINDER != intent.action) return
        val pending = goAsync()
        if (pending == null) {
            deliver(context, intent)
            return
        }
        val application = context.applicationContext
        DELIVERY.execute {
            try {
                deliver(application, intent)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER: String = "pl.vistula.checker.CLASS_REMINDER"
        const val PREF_REMINDERS_ENABLED: String = "reminders_enabled"
        const val PREF_REMINDER_MINUTES: String = "reminder_minutes"
        private val DELIVERY = Executors.newSingleThreadExecutor()

        private fun deliver(context: Context, intent: Intent) {
            synchronized(Repository.LOCK) {
                deliverLocked(context, intent)
            }
        }

        private fun deliverLocked(context: Context, intent: Intent) {
            val textContext = pl.vistula.checker.ui.Language.localizedContext(context)
            val prefs = context.getSharedPreferences("student_tools", Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PREF_REMINDERS_ENABLED, false)) {
                cancel(context)
                return
            }
            if (!canNotify(context)) {
                cancel(context)
                return
            }
            val state = currentState(context)
            val snapshot = snapshot(state)
            val recordId = intent.getStringExtra("recordId")
            val record = if (snapshot == null || recordId == null) null else snapshot.records[recordId]
            val lesson = if (record == null || record.kind != "plan") null else LessonTime.of(record)
            if (lesson == null ||
                lesson.tentative() ||
                lesson.start.toInstant().toString() != intent.getStringExtra("start") ||
                !lesson.start.toInstant().isAfter(Instant.now()) ||
                delivered(state).contains(ReminderPlanner.key(lesson))
            ) {
                scheduleNext(context)
                return
            }
            val title = if (record!!.title.trim().isEmpty()) {
                context.getString(R.string.reminder_unnamed_lesson)
            } else {
                record.title
            }
            val room = record.optMeta("room")
            val time = lesson.start.toLocalTime().toString().substring(0, 5)
            val minutes = max(
                1L,
                (Duration.between(Instant.now(), lesson.start.toInstant()).seconds + 59) / 60
            ).toInt()

            if (title.isNotEmpty()) {
                NotificationHelper.createChannels(context)

                val openPlan = Intent(context, MainActivity::class.java)
                    .putExtra("tab", 5)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pi = PendingIntent.getActivity(
                    context,
                    301,
                    openPlan,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val content = textContext.getString(R.string.notification_class_time, minutes, (if (room.isNotEmpty()) textContext.getString(R.string.notification_class_room, room) + " · " else "") + time)
                val builder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(textContext.getString(R.string.notification_class_title, title))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content + "\n" + textContext.getString(R.string.notification_schedule_hint)))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setColor(0xFF176B57.toInt())

                try {
                    NotificationManagerCompat.from(context).notify(3001, builder.build())
                    Repository(context).update { latest ->
                        val previous = latest.optJSONArray("reminderDeliveries")
                        val saved = JSONArray().put(ReminderPlanner.key(lesson))
                        if (previous != null) {
                            var i = 0
                            while (i < previous.length() && saved.length() < 200) {
                                saved.put(previous.getString(i))
                                i++
                            }
                        }
                        latest.put("reminderDeliveries", saved)
                    }
                } catch (ignored: SecurityException) {
                } catch (ignored: Exception) {
                    cancelAlarm(context)
                    return
                }
            }

            // Schedule next reminder
            scheduleNext(context)
        }

        @JvmStatic
        fun scheduleNext(context: Context) {
            synchronized(Repository.LOCK) {
                scheduleNextLocked(context)
            }
        }

        private fun scheduleNextLocked(context: Context) {
            val prefs = context.getSharedPreferences("student_tools", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(PREF_REMINDERS_ENABLED, false)
            if (!enabled) {
                cancel(context)
                return
            }
            if (!canNotify(context)) {
                cancel(context)
                return
            }
            val minutesBefore = prefs.getInt(PREF_REMINDER_MINUTES, 15)

            val state = currentState(context)
            val snapshot = snapshot(state)
            if (snapshot == null) {
                cancel(context)
                return
            }

            val now = Instant.now()
            val nextLesson = ReminderPlanner.next(snapshot, delivered(state), now)

            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            if (nextLesson == null) {
                cancelAlarm(context)
                return
            }
            val targetEpochMs = ReminderPlanner.triggerAt(nextLesson, minutesBefore, now)

            val fillIntent = Intent(context, ClassReminderReceiver::class.java)
                .setAction(ACTION_REMINDER)
                .putExtra("recordId", nextLesson.record.id)
                .putExtra("start", nextLesson.start.toInstant().toString())
            val scheduledPi = PendingIntent.getBroadcast(
                context,
                302,
                fillIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEpochMs, scheduledPi)
        }

        @JvmStatic
        fun cancel(context: Context) {
            synchronized(Repository.LOCK) {
                cancelAlarm(context)
                NotificationManagerCompat.from(context).cancel(3001)
            }
        }

        private fun cancelAlarm(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ClassReminderReceiver::class.java).setAction(ACTION_REMINDER)
            val pi = PendingIntent.getBroadcast(
                context,
                302,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }

        private fun currentState(context: Context): JSONObject {
            try {
                return Repository(context).read()
            } catch (e: Exception) {
                pl.vistula.checker.Diagnostics.swallowed("ClassReminderReceiver", e)
            }
            return JSONObject()
        }

        private fun snapshot(state: JSONObject): Snapshot? {
            try {
                val raw = state.optJSONObject("snapshot")
                if (state.optString("token").isNotEmpty() && raw != null) return Snapshot.from(raw)
            } catch (e: Exception) {
                pl.vistula.checker.Diagnostics.swallowed("ClassReminderReceiver", e)
            }
            return null
        }

        private fun delivered(state: JSONObject): Set<String> {
            val keys = HashSet<String>()
            val entries = state.optJSONArray("reminderDeliveries")
            if (entries != null) {
                for (i in 0 until entries.length()) {
                    keys.add(entries.optString(i))
                }
            }
            return keys
        }

        @JvmStatic
        fun canNotify(context: Context): Boolean {
            if ((Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ||
                !NotificationManagerCompat.from(context).areNotificationsEnabled()
            ) {
                return false
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager?.getNotificationChannel(NotificationHelper.CHANNEL_REMINDERS)
            return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        }
    }
}

