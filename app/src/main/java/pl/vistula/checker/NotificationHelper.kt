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
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            // Channel 1: Grades - HIGH importance (sound, heads-up display)
            val gradesChannel = NotificationChannel(
                CHANNEL_GRADES,
                "Nowe oceny z USOS",
                NotificationManager.IMPORTANCE_HIGH
            )
            gradesChannel.description = "Powiadomienia o nowych i zmienionych ocenach"
            gradesChannel.enableVibration(true)

            // Channel 2: Schedule changes - DEFAULT importance
            val scheduleChannel = NotificationChannel(
                CHANNEL_SCHEDULE,
                "Zmiany w planie zajęć",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            scheduleChannel.description = "Powiadomienia o zmianach sal, godzin i odwołaniach zajęć"

            // Channel 3: Reminders - DEFAULT importance
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Przypomnienia akademickie",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            remindersChannel.description = "Przypomnienia o zbliżających się zajęciach i terminach opłat"

            manager.createNotificationChannel(gradesChannel)
            manager.createNotificationChannel(scheduleChannel)
            manager.createNotificationChannel(remindersChannel)
        }

        @JvmStatic
        fun notifyChanges(context: Context, changes: List<Change>?) {
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
                    PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
                )

                val historyIntent = Intent(context, MainActivity::class.java)
                    .putExtra("showHistory", true)
                    .putExtra("historyKind", "ocena")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pendingHistory = PendingIntent.getActivity(
                    context,
                    2,
                    historyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
                )

                val title = if (gradeChanges.size == 1) {
                    context.getString(
                        when (gradeChanges[0].action) {
                            "Dodano" -> R.string.notification_grade_added
                            "Usunięto" -> R.string.notification_grade_removed
                            else -> R.string.notification_grade_modified
                        },
                        gradeChanges[0].title
                    )
                } else {
                    context.getString(R.string.notification_grade_changes, gradeChanges.size)
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
                        inbox.addLine("${g.action} · ${g.title}: ${gradeValue(g)}" + if (gDate.isNotEmpty()) " ($gDate)" else "")
                    }
                    if (gradeChanges.size > 5) inbox.setSummaryText("+ jeszcze ${gradeChanges.size - 5} ocen")
                    builder.setStyle(inbox)
                    builder.setContentText(context.getString(R.string.notification_grade_changes_body, gradeChanges.size))
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
                    PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
                )

                val title = if (scheduleChanges.size == 1) {
                    "Zmiana w planie: ${scheduleChanges[0].title}"
                } else {
                    "Zmiany w planie zajęć (${scheduleChanges.size})"
                }

                val builder = NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_nav_plan, "Zobacz plan", pending)

                if (scheduleChanges.size == 1) {
                    val single = scheduleChanges[0]
                    val diffList = ArrayList<String>()
                    if (single.optMeta("diffDate").isNotEmpty()) diffList.add("Data: " + single.optMeta("diffDate"))
                    if (single.optMeta("diffTime").isNotEmpty()) diffList.add("Godziny: " + single.optMeta("diffTime"))
                    if (single.optMeta("diffRoom").isNotEmpty()) diffList.add("Sala: " + single.optMeta("diffRoom"))
                    if (single.optMeta("diffLecturers").isNotEmpty()) diffList.add("Wykładowca: " + single.optMeta("diffLecturers"))
                    if (single.optMeta("diffBuilding").isNotEmpty()) diffList.add("Budynek: " + single.optMeta("diffBuilding"))
                    if (single.optMeta("diffGroup").isNotEmpty()) diffList.add("Grupa: " + single.optMeta("diffGroup"))
                    val diff = if (diffList.isNotEmpty()) diffList.joinToString(" · ") else "${single.action}: ${single.title}"

                    val dateText = if (single.beforeDate.isNotEmpty() && single.afterDate.isNotEmpty() && single.beforeDate != single.afterDate) {
                        HistoryScreenController.formatFullDate(single.beforeDate) + " → " + HistoryScreenController.formatFullDate(single.afterDate)
                    } else {
                        HistoryScreenController.formatFullDate(single.date)
                    }
                    builder.setContentText(diff + if (dateText.isNotEmpty()) " · $dateText" else "")
                    builder.setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            diff + if (dateText.isNotEmpty()) "\nTermin: $dateText" else ""
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
                        if (sDiff.isEmpty()) sDiff = s.action
                        inbox.addLine("${s.title}: $sDiff" + if (sDate.isNotEmpty()) " ($sDate)" else "")
                    }
                    if (scheduleChanges.size > 5) inbox.setSummaryText("+ jeszcze ${scheduleChanges.size - 5} zmian")
                    builder.setStyle(inbox)
                    builder.setContentText("Wykryto zmiany sal lub godzin zajęć (${scheduleChanges.size}).")
                }

                try {
                    manager.notify(20, builder.build())
                } catch (ignored: SecurityException) {
                }
            }
        }

        private fun gradeValue(change: Change): String {
            if ("Usunięto" == change.action) return change.before
            if ("Zmieniono" == change.action && change.before != change.after) return "${change.before} → ${change.after}"
            return change.after
        }

        @JvmStatic
        fun sendTestNotification(context: Context) {
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
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_GRADES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Test powiadomień: Nowa ocena!")
                .setContentText("Inżynieria Oprogramowania: 5.0 (bardzo dobry) · 6 ECTS")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Inżynieria Oprogramowania: 5.0 (bardzo dobry) · 6 ECTS\nPowiadomienia działają prawidłowo! Dotknij, aby przejść do ocen."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .addAction(R.drawable.ic_nav_grades, "Otwórz oceny", pending)

            try {
                NotificationManagerCompat.from(context).notify(99, builder.build())
            } catch (ignored: SecurityException) {
            }
        }
    }
}

