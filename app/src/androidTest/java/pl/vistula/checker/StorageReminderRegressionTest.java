package pl.vistula.checker;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;
import pl.vistula.checker.core.UsosParser;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StorageReminderRegressionTest {
    private Context target, context;
    private File directory;
    private String preferenceName;

    @Before public void setUp() {
        target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        org.junit.Assume.assumeTrue("Run with -PisolatedTests", target.getPackageName().endsWith(".qa"));
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .grantRuntimePermission(target.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        preferenceName = "security_fixture_" + UUID.randomUUID();
        directory = new File(target.getCacheDir(), preferenceName);
        assertTrue(directory.mkdir());
        context = new ContextWrapper(target) {
            @Override public File getNoBackupFilesDir() { return directory; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return target.getSharedPreferences(preferenceName, mode);
            }
        };
    }

    @After public void tearDown() {
        if (context == null) return;
        ClassReminderReceiver.cancel(context);
        new Vault(context).clear();
        target.deleteSharedPreferences(preferenceName);
        assertTrue(directory.delete());
    }

    @Test public void vaultRestoresBackupWhenBaseIsMissing() throws Exception {
        Vault vault = new Vault(context);
        vault.write(new JSONObject().put("token", "backup-token"));
        File base = new File(directory, "state.bin");
        Files.move(base.toPath(), new File(directory, "state.bin.bak").toPath());
        assertEquals("backup-token", vault.read().getString("token"));
        assertTrue(base.exists());
        vault.write(new JSONObject().put("token", "new-token"));
        assertEquals("new-token", vault.read().getString("token"));
    }
    @Test public void disconnectClearsPreviousAccountsAttendance() throws Exception {
        Repository repository = new Repository(context);
        SharedPreferences prefs = context.getSharedPreferences("attendance_tracker", Context.MODE_PRIVATE);
        prefs.edit().putString("data", "personal-attendance").putString("term", "2026Z").commit();
        repository.update(state -> state.put("token", "fixture-token"));
        repository.disconnect();
        assertTrue(repository.read().optString("token").isEmpty());
        assertFalse(prefs.contains("data"));
        assertFalse(prefs.contains("term"));
    }

    @Test public void corruptBackupIsNotMistakenForAnEmptyStore() throws Exception {
        Vault vault = new Vault(context);
        assertEquals(0, vault.read().length());
        Files.write(new File(directory, "state.bin.bak").toPath(), new byte[]{1, 2, 3});
        try { vault.read(); fail("Corrupt backup must fail instead of allowing silent data replacement"); }
        catch (java.io.IOException expected) {}
    }

    @Test public void disconnectedAccountsNeverScheduleDemoReminders() {
        preferences().edit().putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, true).commit();
        ClassReminderReceiver.scheduleNext(context);
        assertNull(pendingReminder());
    }
    @Test public void tentativeClassesAreNeitherScheduledNorDeliveredAsConfirmedReminders() throws Exception {
        Record lesson = saveLesson();
        Vault vault = new Vault(context);
        JSONObject state = vault.read();
        state.getJSONObject("snapshot").getJSONArray("records").getJSONObject(0).put("value", "Termin orientacyjny");
        vault.write(state);
        ClassReminderReceiver.scheduleNext(context);
        assertNull(pendingReminder());
        new ClassReminderReceiver().onReceive(context, reminder(lesson));
        assertEquals(0, notifications().getActiveNotifications().length);
    }

    @Test public void disablingRemindersCancelsAnExistingAlarmAndRejectsDelivery() throws Exception {
        Record lesson = saveLesson();
        ClassReminderReceiver.scheduleNext(context);
        assertNotNull(pendingReminder());
        preferences().edit().putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, false).commit();
        new ClassReminderReceiver().onReceive(context, reminder(lesson));
        assertNull(pendingReminder());
        assertEquals(0, notifications().getActiveNotifications().length);
    }

    @Test public void removedLessonsAndOldPayloadsDoNotProduceNotifications() throws Exception {
        Record lesson = saveLesson();
        Intent stale = reminder(lesson).putExtra("recordId", "removed-lesson").putExtra("title", "Old private title");
        new ClassReminderReceiver().onReceive(context, stale);
        assertEquals(0, notifications().getActiveNotifications().length);
        new ClassReminderReceiver().onReceive(context, new Intent(ClassReminderReceiver.ACTION_REMINDER)
            .putExtra("title", "Old payload without lesson ID"));
        assertEquals(0, notifications().getActiveNotifications().length);
    }

    @Test public void reminderUsesCurrentLessonDetailsInsteadOfIntentPayload() throws Exception {
        Record lesson = saveLesson();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
            target.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        new ClassReminderReceiver().onReceive(context, reminder(lesson).putExtra("title", "Stale private title"));
        android.service.notification.StatusBarNotification[] active = notifications().getActiveNotifications();
        assertEquals(1, active.length);
        assertEquals("Zbliżające się zajęcia: Current lesson", active[0].getNotification().extras.getString("android.title"));
    }

    @Test public void duplicateAlarmDeliveryIsIgnoredUsingAnEncryptedDeliveryRecord() throws Exception {
        Record lesson = saveLesson();
        ClassReminderReceiver receiver = new ClassReminderReceiver();
        receiver.onReceive(context, reminder(lesson));
        assertEquals(1, new Vault(context).read().getJSONArray("reminderDeliveries").length());
        long firstPost = notifications().getActiveNotifications()[0].getPostTime();
        receiver.onReceive(context, reminder(lesson));
        assertEquals(1, new Vault(context).read().getJSONArray("reminderDeliveries").length());
        assertEquals(firstPost, notifications().getActiveNotifications()[0].getPostTime());
        assertNull(pendingReminder());
    }

    @Test public void missingPermissionCancelsAlarmsWithoutMarkingAClassDelivered() throws Exception {
        Record lesson = saveLesson();
        ClassReminderReceiver.scheduleNext(context);
        assertNotNull(pendingReminder());
        Context denied = new ContextWrapper(context) {
            @Override public int checkSelfPermission(String permission) { return android.content.pm.PackageManager.PERMISSION_DENIED; }
        };
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 33);
        ClassReminderReceiver.scheduleNext(denied);
        assertNull(pendingReminder());
        new ClassReminderReceiver().onReceive(denied, reminder(lesson));
        assertFalse(new Vault(context).read().has("reminderDeliveries"));
        assertEquals(0, notifications().getActiveNotifications().length);
    }

    @Test public void unnamedClassesUseAFallbackTitleAndDoNotRepeatCatchUpAlarms() throws Exception {
        Record lesson = saveLesson();
        Vault vault = new Vault(context);
        JSONObject state = vault.read();
        state.getJSONObject("snapshot").getJSONArray("records").getJSONObject(0).put("title", "");
        vault.write(state);
        new ClassReminderReceiver().onReceive(context, reminder(lesson));
        assertEquals("Zbliżające się zajęcia: " + context.getString(R.string.reminder_unnamed_lesson), notifications().getActiveNotifications()[0].getNotification().extras.getString("android.title"));
        assertEquals(1, vault.read().getJSONArray("reminderDeliveries").length());
        assertNull(pendingReminder());
    }

    private SharedPreferences preferences() { return context.getSharedPreferences("student_tools", Context.MODE_PRIVATE); }
    private NotificationManager notifications() { return target.getSystemService(NotificationManager.class); }
    private PendingIntent pendingReminder() {
        return PendingIntent.getBroadcast(context, 302, new Intent(context, ClassReminderReceiver.class)
            .setAction(ClassReminderReceiver.ACTION_REMINDER), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    }
    private Intent reminder(Record lesson) {
        return new Intent(ClassReminderReceiver.ACTION_REMINDER).putExtra("recordId", lesson.id)
            .putExtra("start", pl.vistula.checker.core.LessonTime.of(lesson).start.toInstant().toString());
    }
    private Record saveLesson() throws Exception {
        notifications().cancelAll();
        preferences().edit().putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, true).commit();
        ZonedDateTime start = ZonedDateTime.now(UsosClient.ZONE).plusHours(2).withSecond(0).withNano(0);
        Record lesson = new Record("lesson", "plan", "Current lesson", "", "", start.toLocalDate().toString(), "",
            start.toLocalDateTime().format(UsosParser.TIME), start.plusHours(1).toLocalDateTime().format(UsosParser.TIME));
        Snapshot snapshot = new Snapshot(start.toLocalDate().toString(), start.toLocalDate().plusDays(7).toString());
        snapshot.add(lesson);
        new Vault(context).write(new JSONObject().put("token", "test-account").put("snapshot", snapshot.json()));
        return lesson;
    }
}
