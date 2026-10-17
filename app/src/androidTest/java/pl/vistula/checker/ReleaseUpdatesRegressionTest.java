package pl.vistula.checker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.AppRelease;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class ReleaseUpdatesRegressionTest {
    private Context context;
    private NotificationManager manager;
    private Map<String, ?> saved;

    @Before public void before() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue("Use isolated QA", context.getPackageName().endsWith(".qa"));
        manager = context.getSystemService(NotificationManager.class);
        WorkManager.getInstance(context).cancelUniqueWork(ReleaseUpdateWorker.PERIODIC).getResult().get();
        WorkManager.getInstance(context).cancelUniqueWork(ReleaseUpdateWorker.STARTUP).getResult().get();
        saved = ReleaseUpdates.preferences(context).getAll();
        ReleaseUpdates.preferences(context).edit().clear().putBoolean("enabled", false).commit();
        manager.cancel(ReleaseUpdates.NOTIFICATION_ID);
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
    }

    @After public void after() throws Exception {
        if (context == null || saved == null) return;
        WorkManager.getInstance(context).cancelUniqueWork(ReleaseUpdateWorker.PERIODIC).getResult().get();
        WorkManager.getInstance(context).cancelUniqueWork(ReleaseUpdateWorker.STARTUP).getResult().get();
        manager.cancel(ReleaseUpdates.NOTIFICATION_ID);
        SharedPreferences.Editor editor = ReleaseUpdates.preferences(context).edit().clear();
        for (Map.Entry<String, ?> item : saved.entrySet()) {
            Object value = item.getValue();
            if (value instanceof String) editor.putString(item.getKey(), (String) value);
            else if (value instanceof Long) editor.putLong(item.getKey(), (Long) value);
            else if (value instanceof Boolean) editor.putBoolean(item.getKey(), (Boolean) value);
        }
        editor.commit();
    }

    private JSONObject release(String version) throws Exception {
        return new JSONObject().put("tag_name", "v" + version).put("draft", false).put("prerelease", false)
            .put("published_at", "2026-10-17T12:00:00Z").put("body", "Nowe funkcje i poprawki testowe")
            .put("html_url", AppRelease.REPOSITORY + "/releases/tag/v" + version)
            .put("assets", new JSONArray().put(new JSONObject().put("state", "uploaded").put("size", 123)
                .put("name", "VistulaChecker-" + version + ".apk")
                .put("browser_download_url", AppRelease.REPOSITORY + "/releases/download/v" + version + "/VistulaChecker-" + version + ".apk")));
    }

    private void seed(JSONObject release, boolean enabled) {
        ReleaseUpdates.preferences(context).edit().putString("release", release.toString())
            .putLong("checkedAt", System.currentTimeMillis()).putBoolean("enabled", enabled).commit();
    }

    private Notification posted() throws Exception {
        for (int i = 0; i < 60; i++) {
            for (StatusBarNotification item : manager.getActiveNotifications())
                if (item.getId() == ReleaseUpdates.NOTIFICATION_ID) return item.getNotification();
            Thread.sleep(50);
        }
        throw new AssertionError("Release notification missing");
    }

    @Test public void notificationIncludesNotesAndIsNotRepeatedAfterDismissal() throws Exception {
        seed(release("9.0.0"), true);
        ReleaseUpdates.check(context, false, () -> { throw new AssertionError("Fresh cache must avoid HTTP"); });
        Notification notification = posted();
        assertEquals("Dostępna wersja 9.0.0", notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals("Nowe funkcje i poprawki testowe", notification.extras.getString(Notification.EXTRA_BIG_TEXT));
        assertEquals("9.0.0", ReleaseUpdates.preferences(context).getString("notified", ""));
        manager.cancel(ReleaseUpdates.NOTIFICATION_ID);
        Thread.sleep(150);
        ReleaseUpdates.check(context, false, () -> { throw new AssertionError("Cache"); });
        Thread.sleep(150);
        assertEquals(0, manager.getActiveNotifications().length);
    }

    @Test public void disabledNotificationsDoNotConsumeTheNewVersion() throws Exception {
        seed(release("9.0.0"), false);
        ReleaseUpdates.notifyAvailable(context, ReleaseUpdates.cached(context));
        assertEquals("", ReleaseUpdates.preferences(context).getString("notified", ""));
        assertEquals(0, manager.getActiveNotifications().length);
        ReleaseUpdates.preferences(context).edit().putBoolean("enabled", true).commit();
        ReleaseUpdates.notifyAvailable(context, ReleaseUpdates.cached(context));
        assertNotNull(posted());
    }

    @Test public void installedOrOlderVersionsCancelStaleNotifications() throws Exception {
        seed(release("9.0.0"), true);
        ReleaseUpdates.notifyAvailable(context, ReleaseUpdates.cached(context));
        posted();
        ReleaseUpdates.notifyAvailable(context, AppRelease.parse(release(BuildConfig.VERSION_NAME)));
        Thread.sleep(150);
        assertEquals(0, manager.getActiveNotifications().length);
        ReleaseUpdates.notifyAvailable(context, AppRelease.parse(release("1.0.0")));
        assertEquals(0, manager.getActiveNotifications().length);
    }

    @Test public void networkFailureKeepsCachedNotesAndSuccessfulTimestamp() throws Exception {
        seed(release("9.0.0"), false);
        long checked = ReleaseUpdates.preferences(context).getLong("checkedAt", 0);
        try {
            ReleaseUpdates.check(context, true, () -> { throw new IOException("offline"); });
            fail("Expected offline failure");
        } catch (IOException expected) { }
        assertEquals("9.0.0", ReleaseUpdates.cached(context).version);
        assertEquals(checked, ReleaseUpdates.preferences(context).getLong("checkedAt", 0));
    }

    @Test public void forcedCheckRefreshesFreshCacheButInvalidDataCannotReplaceIt() throws Exception {
        seed(release("9.0.0"), false);
        assertEquals("9.1.0", ReleaseUpdates.check(context, true, () -> release("9.1.0")).version);
        try {
            ReleaseUpdates.check(context, true, () -> new JSONObject());
            fail("Invalid metadata accepted");
        } catch (IOException expected) { }
        assertEquals("9.1.0", ReleaseUpdates.cached(context).version);
    }

    @Test public void repeatedSchedulingKeepsOneDailyJobAndDisableCancelsIt() throws Exception {
        ReleaseUpdates.preferences(context).edit().putBoolean("enabled", true).commit();
        // A fresh cache keeps the startup job off the network.
        seed(release(BuildConfig.VERSION_NAME), true);
        ReleaseUpdateWorker.schedule(context);
        WorkManager work = WorkManager.getInstance(context);
        List<WorkInfo> first = work.getWorkInfosForUniqueWork(ReleaseUpdateWorker.PERIODIC).get(5, TimeUnit.SECONDS);
        assertEquals(1, first.size());
        ReleaseUpdateWorker.schedule(context);
        List<WorkInfo> second = work.getWorkInfosForUniqueWork(ReleaseUpdateWorker.PERIODIC).get(5, TimeUnit.SECONDS);
        assertEquals(first.get(0).getId(), second.get(0).getId());
        assertEquals(androidx.work.NetworkType.CONNECTED, second.get(0).getConstraints().getRequiredNetworkType());
        ReleaseUpdates.setEnabled(context, false);
        work.cancelUniqueWork(ReleaseUpdateWorker.PERIODIC).getResult().get();
        for (WorkInfo info : work.getWorkInfosForUniqueWork(ReleaseUpdateWorker.PERIODIC).get())
            assertTrue(info.getState().isFinished());
    }

    @Test public void notificationTapOpensCachedReleaseNotes() throws Exception {
        seed(release("9.0.0"), true);
        ReleaseUpdates.notifyAvailable(context, ReleaseUpdates.cached(context));
        Notification notification = posted();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            notification.contentIntent.send();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withText(R.string.updates_title)).check(matches(isDisplayed()));
            onView(withSubstring("Nowe funkcje i poprawki testowe")).check(matches(isDisplayed()));
            onView(withText(R.string.updates_check)).check(matches(isDisplayed()));
        }
    }

    @Test public void blockedSystemNotificationsDoNotMarkTheVersionAsDelivered() throws Exception {
        seed(release("9.0.0"), true);
        try {
            shell("appops set " + context.getPackageName() + " POST_NOTIFICATION ignore");
            assertFalse(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled());
            ReleaseUpdates.notifyAvailable(context, ReleaseUpdates.cached(context));
            assertEquals("", ReleaseUpdates.preferences(context).getString("notified", ""));
        } finally {
            shell("appops set " + context.getPackageName() + " POST_NOTIFICATION allow");
        }
        ReleaseUpdates.notifyAvailable(context, ReleaseUpdates.cached(context));
        assertNotNull(posted());
    }

    private void shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(command);
                java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { }
        }
    }

    @Test public void installedReleaseNotesAreAvailableWithoutNetworkOrAccount() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(new Intent(context, MainActivity.class)
                .putExtra(ReleaseUpdates.SHOW_UPDATES, true))) {
            onView(withSubstring("Powiadomienia o nowych stabilnych wersjach"))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        }
    }
}
