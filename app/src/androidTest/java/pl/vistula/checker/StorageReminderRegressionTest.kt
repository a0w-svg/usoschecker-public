package pl.vistula.checker

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.ZonedDateTime
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.UsosParser

@RunWith(AndroidJUnit4::class)
class StorageReminderRegressionTest {
    private lateinit var target: Context
    private var context: Context? = null
    private lateinit var directory: File
    private lateinit var preferenceName: String

    @Before
    fun setUp() {
        target = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue("Run with -PisolatedTests", target.packageName.endsWith(".qa"))
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        preferenceName = "security_fixture_" + UUID.randomUUID()
        directory = File(target.cacheDir, preferenceName)
        assertTrue(directory.mkdir())
        context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                return target.getSharedPreferences(preferenceName, mode)
            }
        }
    }

    @After
    fun tearDown() {
        val ctx = context ?: return
        ClassReminderReceiver.cancel(ctx)
        Vault(ctx).clear()
        target.deleteSharedPreferences(preferenceName)
        assertTrue(directory.delete())
    }

    @Test
    fun vaultRestoresBackupWhenBaseIsMissing() {
        val vault = Vault(context!!)
        vault.write(JSONObject().put("token", "backup-token"))
        val base = File(directory, "state.bin")
        Files.move(base.toPath(), File(directory, "state.bin.bak").toPath())
        assertEquals("backup-token", vault.read().getString("token"))
        assertTrue(base.exists())
        vault.write(JSONObject().put("token", "new-token"))
        assertEquals("new-token", vault.read().getString("token"))
    }

    @Test
    fun disconnectClearsPreviousAccountsAttendance() {
        val ctx = context!!
        val repository = Repository(ctx)
        val prefs = ctx.getSharedPreferences("attendance_tracker", Context.MODE_PRIVATE)
        prefs.edit().putString("data", "personal-attendance").putString("term", "2026Z").commit()
        repository.update { state -> state.put("token", "fixture-token") }
        repository.disconnect()
        assertTrue(repository.read().optString("token").isEmpty())
        assertFalse(prefs.contains("data"))
        assertFalse(prefs.contains("term"))
    }

    @Test
    fun corruptBackupIsNotMistakenForAnEmptyStore() {
        val vault = Vault(context!!)
        assertEquals(0, vault.read().length())
        Files.write(File(directory, "state.bin.bak").toPath(), byteArrayOf(1, 2, 3))
        try {
            vault.read()
            fail("Corrupt backup must fail instead of allowing silent data replacement")
        } catch (expected: IOException) {}
    }

    @Test
    fun disconnectedAccountsNeverScheduleDemoReminders() {
        preferences().edit().putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, true).commit()
        ClassReminderReceiver.scheduleNext(context!!)
        assertNull(pendingReminder())
    }

    @Test
    fun tentativeClassesAreNeitherScheduledNorDeliveredAsConfirmedReminders() {
        val ctx = context!!
        val lesson = saveLesson()
        val vault = Vault(ctx)
        val state = vault.read()
        state.getJSONObject("snapshot").getJSONArray("records").getJSONObject(0).put("value", "Termin orientacyjny")
        vault.write(state)
        ClassReminderReceiver.scheduleNext(ctx)
        assertNull(pendingReminder())
        ClassReminderReceiver().onReceive(ctx, reminder(lesson))
        assertEquals(0, notifications().activeNotifications.size)
    }

    @Test
    fun disablingRemindersCancelsAnExistingAlarmAndRejectsDelivery() {
        val ctx = context!!
        val lesson = saveLesson()
        ClassReminderReceiver.scheduleNext(ctx)
        assertNotNull(pendingReminder())
        preferences().edit().putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, false).commit()
        ClassReminderReceiver().onReceive(ctx, reminder(lesson))
        assertNull(pendingReminder())
        assertEquals(0, notifications().activeNotifications.size)
    }

    @Test
    fun removedLessonsAndOldPayloadsDoNotProduceNotifications() {
        val ctx = context!!
        val lesson = saveLesson()
        val stale = reminder(lesson).putExtra("recordId", "removed-lesson").putExtra("title", "Old private title")
        ClassReminderReceiver().onReceive(ctx, stale)
        assertEquals(0, notifications().activeNotifications.size)
        ClassReminderReceiver().onReceive(
            ctx,
            Intent(ClassReminderReceiver.ACTION_REMINDER).putExtra("title", "Old payload without lesson ID")
        )
        assertEquals(0, notifications().activeNotifications.size)
    }

    @Test
    fun reminderUsesCurrentLessonDetailsInsteadOfIntentPayload() {
        val ctx = context!!
        val lesson = saveLesson()
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            target.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
        ClassReminderReceiver().onReceive(ctx, reminder(lesson).putExtra("title", "Stale private title"))
        val active = notifications().activeNotifications
        assertEquals(1, active.size)
        assertEquals(pl.vistula.checker.ui.Language.localizedContext(ctx).getString(R.string.notification_class_title, "Current lesson"), active[0].notification.extras.getString("android.title"))
    }

    @Test
    fun duplicateAlarmDeliveryIsIgnoredUsingAnEncryptedDeliveryRecord() {
        val ctx = context!!
        val lesson = saveLesson()
        val receiver = ClassReminderReceiver()
        receiver.onReceive(ctx, reminder(lesson))
        assertEquals(1, Vault(ctx).read().getJSONArray("reminderDeliveries").length())
        val firstPost = notifications().activeNotifications[0].postTime
        receiver.onReceive(ctx, reminder(lesson))
        assertEquals(1, Vault(ctx).read().getJSONArray("reminderDeliveries").length())
        assertEquals(firstPost, notifications().activeNotifications[0].postTime)
        assertNull(pendingReminder())
    }

    @Test
    fun missingPermissionCancelsAlarmsWithoutMarkingAClassDelivered() {
        val ctx = context!!
        val lesson = saveLesson()
        ClassReminderReceiver.scheduleNext(ctx)
        assertNotNull(pendingReminder())
        val denied = object : ContextWrapper(ctx) {
            override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_DENIED
        }
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 33)
        ClassReminderReceiver.scheduleNext(denied)
        assertNull(pendingReminder())
        ClassReminderReceiver().onReceive(denied, reminder(lesson))
        assertFalse(Vault(ctx).read().has("reminderDeliveries"))
        assertEquals(0, notifications().activeNotifications.size)
    }

    @Test
    fun unnamedClassesUseAFallbackTitleAndDoNotRepeatCatchUpAlarms() {
        val ctx = context!!
        val lesson = saveLesson()
        val vault = Vault(ctx)
        val state = vault.read()
        state.getJSONObject("snapshot").getJSONArray("records").getJSONObject(0).put("title", "")
        vault.write(state)
        ClassReminderReceiver().onReceive(ctx, reminder(lesson))
        assertEquals(
            pl.vistula.checker.ui.Language.localizedContext(ctx).getString(R.string.notification_class_title, pl.vistula.checker.ui.Language.localizedContext(ctx).getString(R.string.reminder_unnamed_lesson)),
            notifications().activeNotifications[0].notification.extras.getString("android.title")
        )
        assertEquals(1, vault.read().getJSONArray("reminderDeliveries").length())
        assertNull(pendingReminder())
    }

    private fun preferences(): SharedPreferences = context!!.getSharedPreferences("student_tools", Context.MODE_PRIVATE)
    private fun notifications(): NotificationManager = target.getSystemService(NotificationManager::class.java)

    private fun pendingReminder(): PendingIntent? {
        return PendingIntent.getBroadcast(
            context!!,
            302,
            Intent(context!!, ClassReminderReceiver::class.java).setAction(ClassReminderReceiver.ACTION_REMINDER),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reminder(lesson: Record): Intent {
        return Intent(ClassReminderReceiver.ACTION_REMINDER)
            .putExtra("recordId", lesson.id)
            .putExtra("start", LessonTime.of(lesson).start.toInstant().toString())
    }

    private fun saveLesson(): Record {
        notifications().cancelAll()
        preferences().edit().putBoolean(ClassReminderReceiver.PREF_REMINDERS_ENABLED, true).commit()
        val start = ZonedDateTime.now(UsosClient.ZONE).plusHours(2).withSecond(0).withNano(0)
        val lesson = Record(
            "lesson",
            "plan",
            "Current lesson",
            "",
            "",
            start.toLocalDate().toString(),
            "",
            start.toLocalDateTime().format(UsosParser.TIME),
            start.plusHours(1).toLocalDateTime().format(UsosParser.TIME)
        )
        val snapshot = Snapshot(start.toLocalDate().toString(), start.toLocalDate().plusDays(7).toString())
        snapshot.add(lesson)
        Vault(context!!).write(JSONObject().put("token", "test-account").put("snapshot", snapshot.json()))
        return lesson
    }
}
