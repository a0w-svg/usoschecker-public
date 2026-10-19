package pl.vistula.checker

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.AppRelease

/** Opt-in fixture: revoke POST_NOTIFICATIONS via adb BEFORE starting instrumentation. */
@RunWith(AndroidJUnit4::class)
class ReleaseNotificationPermissionTest {
    @Test
    fun deniedPermissionDoesNotConsumeVersionAndGrantAllowsNotification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("QA only", context.packageName.endsWith(".qa"))
        assertEquals(
            "Revoke permission before starting this fixture",
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(ReleaseUpdates.NOTIFICATION_ID)
        val json = JSONObject().put("tag_name", "v9.0.0").put("draft", false).put("prerelease", false)
            .put("published_at", "2026-10-17T12:00:00Z").put("body", "Test zgody na powiadomienia")
            .put("html_url", AppRelease.REPOSITORY + "/releases/tag/v9.0.0")
            .put(
                "assets",
                JSONArray().put(
                    JSONObject().put("state", "uploaded").put("size", 123)
                        .put("name", "VistulaChecker-9.0.0.apk")
                        .put("browser_download_url", AppRelease.REPOSITORY + "/releases/download/v9.0.0/VistulaChecker-9.0.0.apk")
                )
            )
        ReleaseUpdates.preferences(context).edit().clear().putBoolean("enabled", true).commit()
        val release = AppRelease.parse(json)
        try {
            ReleaseUpdates.notifyAvailable(context, release)
            assertEquals("", ReleaseUpdates.preferences(context).getString("notified", ""))
            assertEquals(0, manager.activeNotifications.size)
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            ReleaseUpdates.notifyAvailable(context, release)
            assertEquals("9.0.0", ReleaseUpdates.preferences(context).getString("notified", ""))
            var posted = false
            var i = 0
            while (i < 60 && !posted) {
                for (item in manager.activeNotifications) {
                    if (item.id == ReleaseUpdates.NOTIFICATION_ID) posted = true
                }
                if (!posted) Thread.sleep(50)
                i++
            }
            assertTrue("Notification must be delivered after granting permission", posted)
        } finally {
            manager.cancel(ReleaseUpdates.NOTIFICATION_ID)
            ReleaseUpdates.preferences(context).edit().clear().putBoolean("enabled", false).commit()
        }
    }
}
