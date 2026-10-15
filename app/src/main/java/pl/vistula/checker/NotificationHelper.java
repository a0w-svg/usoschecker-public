package pl.vistula.checker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.ArrayList;
import java.util.List;
import pl.vistula.checker.core.Change;

public final class NotificationHelper {
    public static final String CHANNEL_GRADES = "usos_grades";
    public static final String CHANNEL_SCHEDULE = "usos_schedule";
    public static final String CHANNEL_REMINDERS = "usos_reminders";

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        // Channel 1: Grades - HIGH importance (sound, heads-up display)
        NotificationChannel gradesChannel = new NotificationChannel(
            CHANNEL_GRADES, "Nowe oceny z USOS", NotificationManager.IMPORTANCE_HIGH);
        gradesChannel.setDescription("Powiadomienia o nowych i zmienionych ocenach");
        gradesChannel.enableVibration(true);

        // Channel 2: Schedule changes - DEFAULT importance
        NotificationChannel scheduleChannel = new NotificationChannel(
            CHANNEL_SCHEDULE, "Zmiany w planie zajęć", NotificationManager.IMPORTANCE_DEFAULT);
        scheduleChannel.setDescription("Powiadomienia o zmianach sal, godzin i odwołaniach zajęć");

        // Channel 3: Reminders - DEFAULT importance
        NotificationChannel remindersChannel = new NotificationChannel(
            CHANNEL_REMINDERS, "Przypomnienia akademickie", NotificationManager.IMPORTANCE_DEFAULT);
        remindersChannel.setDescription("Przypomnienia o zbliżających się zajęciach i terminach opłat");

        manager.createNotificationChannel(gradesChannel);
        manager.createNotificationChannel(scheduleChannel);
        manager.createNotificationChannel(remindersChannel);
    }

    public static void notifyChanges(Context context, List<Change> changes) {
        if (changes == null || changes.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        createChannels(context);

        List<Change> gradeChanges = new ArrayList<>();
        List<Change> scheduleChanges = new ArrayList<>();

        for (Change c : changes) {
            if ("ocena".equals(c.kind)) gradeChanges.add(c);
            else if ("plan".equals(c.kind)) scheduleChanges.add(c);
        }

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);

        // 1. Notify Grades if present
        if (!gradeChanges.isEmpty()) {
            Intent intent = new Intent(context, MainActivity.class)
                .putExtra("tab", 2)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending = PendingIntent.getActivity(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

            Intent historyIntent = new Intent(context, MainActivity.class)
                .putExtra("showHistory", true)
                .putExtra("historyKind", "ocena")
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingHistory = PendingIntent.getActivity(context, 2, historyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

            String title = gradeChanges.size() == 1
                ? context.getString("Dodano".equals(gradeChanges.get(0).action) ? R.string.notification_grade_added
                    : "Usunięto".equals(gradeChanges.get(0).action) ? R.string.notification_grade_removed : R.string.notification_grade_modified,
                    gradeChanges.get(0).title)
                : context.getString(R.string.notification_grade_changes, gradeChanges.size());

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_GRADES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(R.drawable.ic_nav_grades, "Zobacz oceny", pending)
                .addAction(R.drawable.ic_nav_history, "Historia zmian", pendingHistory);

            if (gradeChanges.size() == 1) {
                Change single = gradeChanges.get(0);
                String desc = single.optMeta("valueDescription");
                String text = gradeValue(single) + (desc.isEmpty() ? "" : " (" + desc + ")") + (single.getEcts() > 0 ? " · " + single.getEcts() + " ECTS" : "");
                String dateText = !single.date.isEmpty() ? single.date : (!single.sourceTime.isEmpty() ? single.sourceTime : "");
                String formattedDate = pl.vistula.checker.ui.HistoryScreenController.formatFullDate(dateText);
                builder.setContentText(text + (!formattedDate.isEmpty() ? " · " + formattedDate : ""));
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(text + (!formattedDate.isEmpty() ? "\nData wpisania: " + formattedDate : "") + (single.sourceTime.isEmpty() || single.sourceTime.equals(dateText) ? "" : "\nCzas w USOS: " + single.sourceTime)));
            } else {
                NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle().setBigContentTitle(title);
                for (int i = 0; i < Math.min(5, gradeChanges.size()); i++) {
                    Change g = gradeChanges.get(i);
                    String gDate = pl.vistula.checker.ui.HistoryScreenController.formatFullDate(g.date);
                    inbox.addLine(g.action + " · " + g.title + ": " + gradeValue(g) + (!gDate.isEmpty() ? " (" + gDate + ")" : ""));
                }
                if (gradeChanges.size() > 5) inbox.setSummaryText("+ jeszcze " + (gradeChanges.size() - 5) + " ocen");
                builder.setStyle(inbox);
                builder.setContentText(context.getString(R.string.notification_grade_changes_body, gradeChanges.size()));
            }

            try { manager.notify(10, builder.build()); } catch (SecurityException ignored) {}
        }

        // 2. Notify Schedule changes if present
        if (!scheduleChanges.isEmpty()) {
            Intent intent = new Intent(context, MainActivity.class)
                .putExtra("tab", 5)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending = PendingIntent.getActivity(context, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

            String title = scheduleChanges.size() == 1
                ? "Zmiana w planie: " + scheduleChanges.get(0).title
                : "Zmiany w planie zajęć (" + scheduleChanges.size() + ")";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_nav_plan, "Zobacz plan", pending);

            if (scheduleChanges.size() == 1) {
                Change single = scheduleChanges.get(0);
                List<String> diffList = new ArrayList<>();
                if (!single.optMeta("diffDate").isEmpty()) diffList.add("Data: " + single.optMeta("diffDate"));
                if (!single.optMeta("diffTime").isEmpty()) diffList.add("Godziny: " + single.optMeta("diffTime"));
                if (!single.optMeta("diffRoom").isEmpty()) diffList.add("Sala: " + single.optMeta("diffRoom"));
                if (!single.optMeta("diffLecturers").isEmpty()) diffList.add("Wykładowca: " + single.optMeta("diffLecturers"));
                if (!single.optMeta("diffBuilding").isEmpty()) diffList.add("Budynek: " + single.optMeta("diffBuilding"));
                if (!single.optMeta("diffGroup").isEmpty()) diffList.add("Grupa: " + single.optMeta("diffGroup"));
                String diff = !diffList.isEmpty() ? String.join(" · ", diffList) : (single.action + ": " + single.title);

                String dateText = !single.beforeDate.isEmpty() && !single.afterDate.isEmpty() && !single.beforeDate.equals(single.afterDate)
                    ? pl.vistula.checker.ui.HistoryScreenController.formatFullDate(single.beforeDate) + " → " + pl.vistula.checker.ui.HistoryScreenController.formatFullDate(single.afterDate)
                    : pl.vistula.checker.ui.HistoryScreenController.formatFullDate(single.date);
                builder.setContentText(diff + (!dateText.isEmpty() ? " · " + dateText : ""));
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(diff + (!dateText.isEmpty() ? "\nTermin: " + dateText : "")));
            } else {
                NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle().setBigContentTitle(title);
                for (int i = 0; i < Math.min(5, scheduleChanges.size()); i++) {
                    Change s = scheduleChanges.get(i);
                    String sDate = !s.beforeDate.isEmpty() && !s.afterDate.isEmpty() && !s.beforeDate.equals(s.afterDate)
                        ? pl.vistula.checker.ui.HistoryScreenController.formatFullDate(s.beforeDate) + " → " + pl.vistula.checker.ui.HistoryScreenController.formatFullDate(s.afterDate)
                        : pl.vistula.checker.ui.HistoryScreenController.formatFullDate(s.date);
                    String sDiff = s.optMeta("diffRoom");
                    if (sDiff.isEmpty()) sDiff = s.optMeta("diffTime");
                    if (sDiff.isEmpty()) sDiff = s.action;
                    inbox.addLine(s.title + ": " + sDiff + (!sDate.isEmpty() ? " (" + sDate + ")" : ""));
                }
                if (scheduleChanges.size() > 5) inbox.setSummaryText("+ jeszcze " + (scheduleChanges.size() - 5) + " zmian");
                builder.setStyle(inbox);
                builder.setContentText("Wykryto zmiany sal lub godzin zajęć (" + scheduleChanges.size() + ").");
            }

            try { manager.notify(20, builder.build()); } catch (SecurityException ignored) {}
        }
    }

    private static String gradeValue(Change change) {
        if ("Usunięto".equals(change.action)) return change.before;
        if ("Zmieniono".equals(change.action) && !change.before.equals(change.after)) return change.before + " → " + change.after;
        return change.after;
    }

    public static void sendTestNotification(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        createChannels(context);

        Intent intent = new Intent(context, MainActivity.class)
            .putExtra("tab", 2)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 99, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_GRADES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Test powiadomień: Nowa ocena!")
            .setContentText("Inżynieria Oprogramowania: 5.0 (bardzo dobry) · 6 ECTS")
            .setStyle(new NotificationCompat.BigTextStyle().bigText("Inżynieria Oprogramowania: 5.0 (bardzo dobry) · 6 ECTS\nPowiadomienia działają prawidłowo! Dotknij, aby przejść do ocen."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(R.drawable.ic_nav_grades, "Otwórz oceny", pending);

        try {
            NotificationManagerCompat.from(context).notify(99, builder.build());
        } catch (SecurityException ignored) {}
    }
}
