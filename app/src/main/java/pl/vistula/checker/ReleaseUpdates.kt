package pl.vistula.checker

import androidx.core.content.edit
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import pl.vistula.checker.core.AppRelease

/** Public release metadata only: no USOS client, account, cookies or authentication. */
class ReleaseUpdates private constructor() {
    fun interface Source {
        @Throws(Exception::class)
        fun fetch(): JSONObject
    }

    companion object {
        const val CHANNEL: String = "app_updates"
        const val NOTIFICATION_ID: Int = 104
        const val SHOW_UPDATES: String = "showReleaseUpdates"
        private const val ENDPOINT = "https://api.github.com/repos/a0w-svg/usoschecker-public/releases/latest"

        @JvmStatic
        fun preferences(context: Context): SharedPreferences =
            context.getSharedPreferences("release_updates", Context.MODE_PRIVATE)

        @JvmStatic
        fun enabled(context: Context): Boolean = preferences(context).getBoolean("enabled", true)

        @JvmStatic
        fun setEnabled(context: Context, enabled: Boolean) {
            preferences(context).edit { putBoolean("enabled", enabled) }
            if (!enabled) context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            ReleaseUpdateWorker.schedule(context)
        }

        @JvmStatic
        fun cached(context: Context): AppRelease? {
            return try {
                AppRelease.parse(JSONObject(preferences(context).getString("release", "{}") ?: "{}"))
            } catch (ignored: Exception) {
                null
            }
        }

        /** Serializes foreground and worker requests and limits automatic successful checks to twice daily. */
        @JvmStatic
        @Synchronized
        @Throws(Exception::class)
        fun check(context: Context, force: Boolean): AppRelease =
            check(context, force, Source { fetch() })

        @JvmStatic
        @Synchronized
        @Throws(Exception::class)
        fun check(context: Context, force: Boolean, source: Source): AppRelease {
            val prefs = preferences(context)
            val now = System.currentTimeMillis()
            val last = prefs.getLong("checkedAt", 0)
            var release = cached(context)
            if (!force && last > 0 && now >= last && now - last < TimeUnit.HOURS.toMillis(12)) {
                notifyAvailable(context, release)
                return release!!
            }
            val json = source.fetch()
            release = AppRelease.parse(json) ?: throw IOException("Release not ready")
            prefs.edit { putString("release", json.toString()).putLong("checkedAt", now) }
            notifyAvailable(context, release)
            return release
        }

        @Throws(Exception::class)
        private fun fetch(): JSONObject {
            val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "VistulaChecker/" + BuildConfig.VERSION_NAME)
            try {
                if (connection.responseCode != 200) throw IOException("Release service unavailable")
                connection.inputStream.use { input ->
                    ByteArrayOutputStream().use { bytes ->
                        val buffer = ByteArray(4096)
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            if (bytes.size() + count > 262144) throw IOException("Release response too large")
                            bytes.write(buffer, 0, count)
                        }
                        return JSONObject(bytes.toString(StandardCharsets.UTF_8.name()))
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

        @JvmStatic
        @Synchronized
        fun notifyAvailable(context: Context, release: AppRelease?) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (release == null || !release.newerThan(BuildConfig.VERSION_NAME)) {
                manager.cancel(NOTIFICATION_ID)
                return
            }
            if (!enabled(context) || release.version == preferences(context).getString("notified", "")) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.updates_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
                manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE
            ) {
                return
            }
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(SHOW_UPDATES, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notes = if (release.notes.isBlank()) context.getString(R.string.updates_notes_missing) else release.notes
            try {
                manager.notify(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(context.getString(R.string.updates_available, release.version))
                        .setContentText(context.getString(R.string.updates_tap_notes))
                        .setStyle(NotificationCompat.BigTextStyle().bigText(notes))
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .build()
                )
                preferences(context).edit { putString("notified", release.version) }
            } catch (ignored: SecurityException) {
                /* Permission can change between the check and posting. */
            }
        }
    }
}

