package pl.vistula.checker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.min
import pl.vistula.checker.core.Change
import pl.vistula.checker.ui.HistoryScreenController

class NotificationHelper private constructor() {
    companion object {
        const val CHANNEL_GRADES: String = "usos_grades"
        const val CHANNEL_SCHEDULE: String = "usos_schedule"
        const val CHANNEL_REMINDERS: String = "usos_reminders"

        @JvmStatic
        fun createChannels(context: Context) {
            val textContext = pl.vistula.checker.ui.Language.localizedContext(context)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            // Channel 1: Grades - HIGH importance (sound, heads-up display)
            val gradesChannel = NotificationChannel(
                CHANNEL_GRADES,
                textContext.getString(R.string.notification_grades_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            gradesChannel.description = textContext.getString(R.string.notification_grades_description)
            gradesChannel.enableVibration(true)

            // Channel 2: Schedule changes - DEFAULT importance
            val scheduleChannel = NotificationChannel(
                CHANNEL_SCHEDULE,
                textContext.getString(R.string.notification_schedule_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            scheduleChannel.description = textContext.getString(R.string.notification_schedule_description)

            // Channel 3: Reminders - DEFAULT importance
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                textContext.getString(R.string.notification_reminders_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            remindersChannel.description = textContext.getString(R.string.notification_reminders_description)

            manager.createNotificationChannel(gradesChannel)
            manager.createNotificationChannel(scheduleChannel)
            manager.createNotificationChannel(remindersChannel)
        }

        @JvmStatic
        fun notifyChanges(context: Context, changes: List<Change>?) {
            val textContext = pl.vistula.checker.ui.Language.localizedContext(context)
            if (changes.isNullOrEmpty()) return
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            createChannels(context)

            val gradeChanges = ArrayList<Change>()
            val scheduleChanges = ArrayList<Change>()

            for (c in changes) {
                if ("ocena" == c.kind) gradeChanges.add(c)
                else if ("plan" == c.kind) scheduleChanges.add(c)
            }

            val manager = NotificationManagerCompat.from(context)

            // 1. Notify Grades if present
            if (gradeChanges.isNotEmpty()) {
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("tab", 2)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pending = PendingIntent.getActivity(
                    context,
                    1,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val historyIntent = Intent(context, MainActivity::class.java)
                    .putExtra("showHistory", true)
                    .putExtra("historyKind", "ocena")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pendingHistory = PendingIntent.getActivity(
                    context,
                    2,
                    historyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val title = if (gradeChanges.size == 1) {
                    textContext.getString(
                        when (gradeChanges[0].action) {
                            "Dodano" -> R.string.notification_grade_added
                            "Usunięto" -> R.string.notification_grade_removed
                            else -> R.string.notification_grade_modified
                        },
                        gradeChanges[0].title
                    )
                } else {
                    textContext.getString(R.string.notification_grade_changes, gradeChanges.size)
                }

                val builder = NotificationCompat.Builder(context, CHANNEL_GRADES)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .addAction(R.drawable.ic_nav_grades, "Zobacz oceny", pending)
                    .addAction(R.drawable.ic_nav_history, "Historia zmian", pendingHistory)

                if (gradeChanges.size == 1) {
                    val single = gradeChanges[0]
                    val desc = single.optMeta("valueDescription")
                    val text = gradeValue(single) +
                        (if (desc.isEmpty()) "" else " ($desc)") +
                        (if (single.getEcts() > 0) " · ${single.getEcts()} ECTS" else "")
                    val dateText = if (single.date.isNotEmpty()) single.date else if (single.sourceTime.isNotEmpty()) single.sourceTime else ""
                    val formattedDate = HistoryScreenController.formatFullDate(dateText)
                    builder.setContentText(text + if (formattedDate.isNotEmpty()) " · $formattedDate" else "")
                    builder.setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            text +
                                (if (formattedDate.isNotEmpty()) "\nData wpisania: $formattedDate" else "") +
                                (if (single.sourceTime.isEmpty() || single.sourceTime == dateText) "" else "\nCzas w USOS: ${single.sourceTime}")
                        )
                    )
                } else {
                    val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
                    for (i in 0 until min(5, gradeChanges.size)) {
                        val g = gradeChanges[i]
                        val gDate = HistoryScreenController.formatFullDate(g.date)
                        inbox.addLine("${actionLabel(textContext, g.action)} · ${g.title}: ${gradeValue(g)}" + if (gDate.isNotEmpty()) " ($gDate)" else "")
                    }
                    if (gradeChanges.size > 5) inbox.setSummaryText(textContext.resources.getQuantityString(R.plurals.notification_more_changes, gradeChanges.size - 5, gradeChanges.size - 5))
                    builder.setStyle(inbox)
                    builder.setContentText(textContext.resources.getQuantityString(R.plurals.notification_grade_changes_body, gradeChanges.size, gradeChanges.size))
                }

                try {
                    manager.notify(10, builder.build())
                } catch (ignored: SecurityException) {
                }
            }

            // 2. Notify Schedule changes if present
            if (scheduleChanges.isNotEmpty()) {
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("tab", 5)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pending = PendingIntent.getActivity(
                    context,
                    3,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val title = if (scheduleChanges.size == 1) {
                    textContext.getString(R.string.notification_schedule_single, scheduleChanges[0].title)
                } else {
                    textContext.getString(R.string.notification_schedule_count, scheduleChanges.size)
                }

                val builder = NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_nav_plan, textContext.getString(R.string.notification_open_schedule), pending)

                if (scheduleChanges.size == 1) {
                    val single = scheduleChanges[0]
                    val diffList = ArrayList<String>()
                    if (single.optMeta("diffDate").isNotEmpty()) diffList.add(textContext.getString(R.string.notification_diff_0) + " " + single.optMeta("diffDate"))
                    if (single.optMeta("diffTime").isNotEmpty()) diffList.add(textContext.getString(R.string.notification_diff_1) + " " + single.optMeta("diffTime"))
                    if (single.optMeta("diffRoom").isNotEmpty()) diffList.add(textContext.getString(R.string.notification_diff_2) + " " + single.optMeta("diffRoom"))
                    if (single.optMeta("diffLecturers").isNotEmpty()) diffList.add(textContext.getString(R.string.notification_diff_3) + " " + single.optMeta("diffLecturers"))
                    if (single.optMeta("diffBuilding").isNotEmpty()) diffList.add(textContext.getString(R.string.notification_diff_4) + " " + single.optMeta("diffBuilding"))
                    if (single.optMeta("diffGroup").isNotEmpty()) diffList.add(textContext.getString(R.string.notification_diff_5) + " " + single.optMeta("diffGroup"))
                    val diff = if (diffList.isNotEmpty()) diffList.joinToString(" · ") else "${actionLabel(textContext, single.action)}: ${single.title}"

                    val dateText = if (single.beforeDate.isNotEmpty() && single.afterDate.isNotEmpty() && single.beforeDate != single.afterDate) {
                        HistoryScreenController.formatFullDate(single.beforeDate) + " → " + HistoryScreenController.formatFullDate(single.afterDate)
                    } else {
                        HistoryScreenController.formatFullDate(single.date)
                    }
                    builder.setContentText(diff + if (dateText.isNotEmpty()) " · $dateText" else "")
                    builder.setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            diff + if (dateText.isNotEmpty()) "\n" + textContext.getString(R.string.notification_diff_0) + " " + dateText else ""
                        )
                    )
                } else {
                    val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
                    for (i in 0 until min(5, scheduleChanges.size)) {
                        val s = scheduleChanges[i]
                        val sDate = if (s.beforeDate.isNotEmpty() && s.afterDate.isNotEmpty() && s.beforeDate != s.afterDate) {
                            HistoryScreenController.formatFullDate(s.beforeDate) + " → " + HistoryScreenController.formatFullDate(s.afterDate)
                        } else {
                            HistoryScreenController.formatFullDate(s.date)
                        }
                        var sDiff = s.optMeta("diffRoom")
                        if (sDiff.isEmpty()) sDiff = s.optMeta("diffTime")
                        if (sDiff.isEmpty()) sDiff = actionLabel(textContext, s.action)
                        inbox.addLine("${s.title}: $sDiff" + if (sDate.isNotEmpty()) " ($sDate)" else "")
                    }
                    if (scheduleChanges.size > 5) inbox.setSummaryText(textContext.resources.getQuantityString(R.plurals.notification_more_changes, scheduleChanges.size - 5, scheduleChanges.size - 5))
                    builder.setStyle(inbox)
                    builder.setContentText(textContext.resources.getQuantityString(R.plurals.notification_schedule_body, scheduleChanges.size, scheduleChanges.size))
                }

                try {
                    manager.notify(20, builder.build())
                } catch (ignored: SecurityException) {
                }
            }
        }

        private fun actionLabel(context: Context, action: String): String = context.getString(when (action) {
            "Dodano" -> R.string.history_action_added
            "Usunięto" -> R.string.history_action_removed
            else -> R.string.history_action_modified
        })

        private fun gradeValue(change: Change): String {
            if ("Usunięto" == change.action) return change.before
            if ("Zmieniono" == change.action && change.before != change.after) return "${change.before} → ${change.after}"
            return change.after
        }

        @JvmStatic
        fun sendTestNotification(context: Context) {
            val textContext = pl.vistula.checker.ui.Language.localizedContext(context)
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            createChannels(context)

            val intent = Intent(context, MainActivity::class.java)
                .putExtra("tab", 2)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context,
                99,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_GRADES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(textContext.getString(R.string.notification_test_title))
                .setContentText(textContext.getString(R.string.notification_test_body))
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        textContext.getString(R.string.notification_test_body) + "\n" + textContext.getString(R.string.notification_test_detail)
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .addAction(R.drawable.ic_nav_grades, textContext.getString(R.string.notification_open_grades), pending)

            try {
                NotificationManagerCompat.from(context).notify(99, builder.build())
            } catch (ignored: SecurityException) {
            }
        }
    }
}

