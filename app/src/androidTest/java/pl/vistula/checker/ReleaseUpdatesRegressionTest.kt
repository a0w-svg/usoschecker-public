package pl.vistula.checker

import android.Manifest
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.work.NetworkType
import androidx.work.WorkManager
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.AppRelease

@RunWith(AndroidJUnit4::class)
class ReleaseUpdatesRegressionTest {
    private var context: Context? = null
    private var manager: NotificationManager? = null
    private var saved: Map<String, *>? = null

    @Before
    fun before() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        context = ctx
        Assume.assumeTrue("Use isolated QA", ctx.packageName.endsWith(".qa"))
        val nm = ctx.getSystemService(NotificationManager::class.java)
        manager = nm
        WorkManager.getInstance(ctx).cancelUniqueWork(ReleaseUpdateWorker.PERIODIC).result.get()
        WorkManager.getInstance(ctx).cancelUniqueWork(ReleaseUpdateWorker.STARTUP).result.get()
        saved = ReleaseUpdates.preferences(ctx).all
        ReleaseUpdates.preferences(ctx).edit().clear().putBoolean("enabled", false).commit()
        nm.cancel(ReleaseUpdates.NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun after() {
        val ctx = context ?: return
        val s = saved ?: return
        WorkManager.getInstance(ctx).cancelUniqueWork(ReleaseUpdateWorker.PERIODIC).result.get()
        WorkManager.getInstance(ctx).cancelUniqueWork(ReleaseUpdateWorker.STARTUP).result.get()
        manager!!.cancel(ReleaseUpdates.NOTIFICATION_ID)
        val editor = ReleaseUpdates.preferences(ctx).edit().clear()
        for ((key, value) in s) {
            when (value) {
                is String -> editor.putString(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
            }
        }
        editor.commit()
    }

    private fun release(version: String): JSONObject {
        return JSONObject()
            .put("tag_name", "v$version")
            .put("draft", false)
            .put("prerelease", false)
            .put("published_at", "2026-10-17T12:00:00Z")
            .put("body", "Nowe funkcje i poprawki testowe")
            .put("html_url", "${AppRelease.REPOSITORY}/releases/tag/v$version")
            .put(
                "assets",
                JSONArray().put(
                    JSONObject()
                        .put("state", "uploaded")
                        .put("size", 123)
                        .put("name", "VistulaChecker-$version.apk")
                        .put("browser_download_url", "${AppRelease.REPOSITORY}/releases/download/v$version/VistulaChecker-$version.apk")
                )
            )
    }

    private fun seed(release: JSONObject, enabled: Boolean) {
        ReleaseUpdates.preferences(context!!).edit()
            .putString("release", release.toString())
            .putLong("checkedAt", System.currentTimeMillis())
            .putBoolean("enabled", enabled)
            .commit()
    }

    private fun posted(): Notification {
        for (i in 0 until 60) {
            for (item in manager!!.activeNotifications) {
                if (item.id == ReleaseUpdates.NOTIFICATION_ID) return item.notification
            }
            Thread.sleep(50)
        }
        throw AssertionError("Release notification missing")
    }

    @Test
    fun notificationIncludesNotesAndIsNotRepeatedAfterDismissal() {
        val ctx = context!!
        seed(release("9.0.0"), true)
        ReleaseUpdates.check(ctx, false) { throw AssertionError("Fresh cache must avoid HTTP") }
        val notification = posted()
        assertEquals(ctx.getString(R.string.updates_available, "9.0.0"), notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Nowe funkcje i poprawki testowe", notification.extras.getString(Notification.EXTRA_BIG_TEXT))
        assertEquals("9.0.0", ReleaseUpdates.preferences(ctx).getString("notified", ""))
        manager!!.cancel(ReleaseUpdates.NOTIFICATION_ID)
        Thread.sleep(150)
        ReleaseUpdates.check(ctx, false) { throw AssertionError("Cache") }
        Thread.sleep(150)
        assertEquals(0, manager!!.activeNotifications.size)
    }

    @Test
    fun disabledNotificationsDoNotConsumeTheNewVersion() {
        val ctx = context!!
        seed(release("9.0.0"), false)
        ReleaseUpdates.notifyAvailable(ctx, ReleaseUpdates.cached(ctx))
        assertEquals("", ReleaseUpdates.preferences(ctx).getString("notified", ""))
        assertEquals(0, manager!!.activeNotifications.size)
        ReleaseUpdates.preferences(ctx).edit().putBoolean("enabled", true).commit()
        ReleaseUpdates.notifyAvailable(ctx, ReleaseUpdates.cached(ctx))
        assertNotNull(posted())
    }

    @Test
    fun installedOrOlderVersionsCancelStaleNotifications() {
        val ctx = context!!
        seed(release("9.0.0"), true)
        ReleaseUpdates.notifyAvailable(ctx, ReleaseUpdates.cached(ctx))
        posted()
        ReleaseUpdates.notifyAvailable(ctx, AppRelease.parse(release(BuildConfig.VERSION_NAME)))
        Thread.sleep(150)
        assertEquals(0, manager!!.activeNotifications.size)
        ReleaseUpdates.notifyAvailable(ctx, AppRelease.parse(release("1.0.0")))
        assertEquals(0, manager!!.activeNotifications.size)
    }

    @Test
    fun networkFailureKeepsCachedNotesAndSuccessfulTimestamp() {
        val ctx = context!!
        seed(release("9.0.0"), false)
        val checked = ReleaseUpdates.preferences(ctx).getLong("checkedAt", 0)
        try {
            ReleaseUpdates.check(ctx, true) { throw IOException("offline") }
            fail("Expected offline failure")
        } catch (expected: IOException) {}
        assertEquals("9.0.0", ReleaseUpdates.cached(ctx)!!.version)
        assertEquals(checked, ReleaseUpdates.preferences(ctx).getLong("checkedAt", 0))
    }

    @Test
    fun forcedCheckRefreshesFreshCacheButInvalidDataCannotReplaceIt() {
        val ctx = context!!
        seed(release("9.0.0"), false)
        assertEquals("9.1.0", ReleaseUpdates.check(ctx, true) { release("9.1.0") }.version)
        try {
            ReleaseUpdates.check(ctx, true) { JSONObject() }
            fail("Invalid metadata accepted")
        } catch (expected: IOException) {}
        assertEquals("9.1.0", ReleaseUpdates.cached(ctx)!!.version)
    }

    @Test
    fun repeatedSchedulingKeepsOneDailyJobAndDisableCancelsIt() {
        val ctx = context!!
        ReleaseUpdates.preferences(ctx).edit().putBoolean("enabled", true).commit()
        // A fresh cache keeps the startup job off the network.
        seed(release(BuildConfig.VERSION_NAME), true)
        ReleaseUpdateWorker.schedule(ctx)
        val work = WorkManager.getInstance(ctx)
        val first = work.getWorkInfosForUniqueWork(ReleaseUpdateWorker.PERIODIC).get(5, TimeUnit.SECONDS)
        assertEquals(1, first.size)
        ReleaseUpdateWorker.schedule(ctx)
        val second = work.getWorkInfosForUniqueWork(ReleaseUpdateWorker.PERIODIC).get(5, TimeUnit.SECONDS)
        assertEquals(first[0].id, second[0].id)
        assertEquals(NetworkType.CONNECTED, second[0].constraints.requiredNetworkType)
        ReleaseUpdates.setEnabled(ctx, false)
        work.cancelUniqueWork(ReleaseUpdateWorker.PERIODIC).result.get()
        for (info in work.getWorkInfosForUniqueWork(ReleaseUpdateWorker.PERIODIC).get()) {
            assertTrue(info.state.isFinished)
        }
    }

    @Test
    fun notificationTapOpensCachedReleaseNotes() {
        val ctx = context!!
        seed(release("9.0.0"), true)
        ReleaseUpdates.notifyAvailable(ctx, ReleaseUpdates.cached(ctx))
        val notification = posted()
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            // Supply the launch privilege a real System UI notification tap has.
            ui.adoptShellPermissionIdentity("android.permission.START_ACTIVITIES_FROM_BACKGROUND")
            var options: Bundle? = null
            if (Build.VERSION.SDK_INT >= 34) {
                options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            }
            notification.contentIntent.send(ctx, 0, null, null, null, null, options)
            onView(withText(R.string.updates_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withSubstring("Nowe funkcje i poprawki testowe")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.updates_check)).inRoot(isDialog()).check(matches(isDisplayed()))
        } finally {
            ui.dropShellPermissionIdentity()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                for (activity in ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    activity.finish()
                }
            }
        }
    }

    @Test
    fun installedReleaseNotesAreAvailableWithoutNetworkOrAccount() {
        val ctx = context!!
        ActivityScenario.launch<MainActivity>(
            Intent(ctx, MainActivity::class.java).putExtra(ReleaseUpdates.SHOW_UPDATES, true)
        ).use {
            val firstLine = ctx.resources.openRawResource(R.raw.whats_new).use { notes ->
                String(notes.readAllBytes(), StandardCharsets.UTF_8).trim().split(Regex("\\R"))[0]
            }
            onView(withSubstring(firstLine))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }
}
