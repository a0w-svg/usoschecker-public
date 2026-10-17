package pl.vistula.checker;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.AppRelease;
import static org.junit.Assert.*;

/** Opt-in fixture: revoke POST_NOTIFICATIONS via adb BEFORE starting instrumentation. */
@RunWith(AndroidJUnit4.class)
public class ReleaseNotificationPermissionTest {
    @Test public void deniedPermissionDoesNotConsumeVersionAndGrantAllowsNotification() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("QA only", context.getPackageName().endsWith(".qa"));
        assertEquals("Revoke permission before starting this fixture", PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.cancel(ReleaseUpdates.NOTIFICATION_ID);
        JSONObject json = new JSONObject().put("tag_name", "v9.0.0").put("draft", false).put("prerelease", false)
            .put("published_at", "2026-10-17T12:00:00Z").put("body", "Test zgody na powiadomienia")
            .put("html_url", AppRelease.REPOSITORY + "/releases/tag/v9.0.0")
            .put("assets", new JSONArray().put(new JSONObject().put("state", "uploaded").put("size", 123)
                .put("name", "VistulaChecker-9.0.0.apk")
                .put("browser_download_url", AppRelease.REPOSITORY + "/releases/download/v9.0.0/VistulaChecker-9.0.0.apk")));
        ReleaseUpdates.preferences(context).edit().clear().putBoolean("enabled", true).commit();
        AppRelease release = AppRelease.parse(json);
        try {
            ReleaseUpdates.notifyAvailable(context, release);
            assertEquals("", ReleaseUpdates.preferences(context).getString("notified", ""));
            assertEquals(0, manager.getActiveNotifications().length);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
            ReleaseUpdates.notifyAvailable(context, release);
            assertEquals("9.0.0", ReleaseUpdates.preferences(context).getString("notified", ""));
            boolean posted = false;
            for (int i = 0; i < 60 && !posted; i++) {
                for (android.service.notification.StatusBarNotification item : manager.getActiveNotifications())
                    if (item.getId() == ReleaseUpdates.NOTIFICATION_ID) posted = true;
                if (!posted) Thread.sleep(50);
            }
            assertTrue("Notification must be delivered after granting permission", posted);
        } finally {
            manager.cancel(ReleaseUpdates.NOTIFICATION_ID);
            ReleaseUpdates.preferences(context).edit().clear().putBoolean("enabled", false).commit();
        }
    }
}
