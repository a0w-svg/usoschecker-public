package pl.vistula.checker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import java.time.LocalDate
import org.json.JSONArray
import pl.vistula.checker.core.TaskTools

/** Once a day tells the student about open tasks that are overdue, due today or due tomorrow. */
object TaskDeadlineReminder {
    private const val NOTIFICATION_ID = 3002
    private const val PREFS = "student_tools"

    fun notifyIfDue(context: Context, tasks: JSONArray?, today: LocalDate = LocalDate.now(UsosClient.ZONE)) {
        if (tasks == null || tasks.length() == 0) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("taskReminderDay", null) == today.toString()) return

        var overdue = 0
        var dueToday = 0
        var tomorrow = 0
        var first: String? = null
        for (i in 0 until tasks.length()) {
            val task = tasks.optJSONObject(i) ?: continue
            if (task.optBoolean("done")) continue
            val days = TaskTools.daysLeft(task, today) ?: continue
            when {
                days < 0 -> overdue++
                days == 0L -> dueToday++
                days == 1L -> tomorrow++
                else -> continue
            }
            if (first == null) first = task.optString("title")
        }
        val total = overdue + dueToday + tomorrow
        if (total == 0) return

        val text = pl.vistula.checker.ui.Language.localizedContext(context)
        val parts = ArrayList<String>()
        if (overdue > 0) parts.add(text.getString(R.string.notification_tasks_overdue, overdue))
        if (dueToday > 0) parts.add(text.getString(R.string.notification_tasks_today, dueToday))
        if (tomorrow > 0) parts.add(text.getString(R.string.notification_tasks_tomorrow, tomorrow))
        val open = Intent(context, MainActivity::class.java)
            .putExtra("tab", pl.vistula.checker.ui.Tabs.TASKS)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 302, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationHelper.createChannels(context)
        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.getString(R.string.notification_tasks_title))
            .setContentText(parts.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString("\n") + (first?.let { "\n$it" } ?: "")))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationHelper.publicVersion(context, NotificationHelper.CHANNEL_REMINDERS))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs.edit { putString("taskReminderDay", today.toString()) }
        } catch (ignored: SecurityException) {
        }
    }
}
