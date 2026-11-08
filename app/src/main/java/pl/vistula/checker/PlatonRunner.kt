package pl.vistula.checker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import pl.vistula.checker.core.PlatonCrawler
import pl.vistula.checker.core.PlatonState

/** One Platon synchronisation, shared by the Tasks screen and the background worker. */
object PlatonRunner {

    private const val MIN_BACKGROUND_GAP_MS = 55 * 60_000L

    /** Network part: reads courses, assignments and deadlines. Throws [PlatonClient.SessionExpiredException]. */
    @Throws(Exception::class)
    fun crawl(source: String): PlatonCrawler.Result = PlatonCrawler.crawlAll(
        source,
        { address, form ->
            val page = PlatonClient.fetch(address, form)
            page.url to page.html
        },
    ) { address ->
        val page = PlatonClient.fetch(address)
        page.url to page.html
    }

    /**
     * Like [crawl], but when the Platon session has expired it first tries to renew it silently through the
     * Microsoft session ([PlatonSession]) and retries once. Call from a background thread only.
     */
    @Throws(Exception::class)
    fun crawlRenewing(context: Context, source: String): PlatonCrawler.Result = try {
        crawl(source)
    } catch (e: PlatonClient.SessionExpiredException) {
        if (!PlatonSession.refresh(context)) throw e
        crawl(source)
    }

    /** Signs out of Platon on this device: forgets the linked page, flags and the in-app browser's cookies. */
    fun forget(context: Context) {
        StudentPrefs(context).forgetPlaton()
        try {
            android.webkit.CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        } catch (ignored: Exception) {
        }
    }

    /** Background refresh after the periodic USOS sync; silent when signed out or offline. */
    fun runInBackground(context: Context) {
        val prefs = StudentPrefs(context)
        val source = prefs.platonSource ?: return
        if (System.currentTimeMillis() - prefs.platonSyncedAt < MIN_BACKGROUND_GAP_MS) return
        // Skip when the student's own sync is already running; the next periodic run will catch up.
        exclusive(wait = false) {
            try {
                val crawled = crawlRenewing(context, source)
                if (crawled.tasks.isEmpty() && crawled.courses.isEmpty() && crawled.messages.isEmpty()) return@exclusive
                var summary: PlatonState.Summary? = null
                val now = System.currentTimeMillis()
                Repository(context).update { s -> summary = PlatonState.apply(s, crawled, now) }
                prefs.platonSyncedAt = now
                prefs.platonLastError = null
                summary?.let { notify(context, it) }
            } catch (e: PlatonClient.SessionExpiredException) {
                prefs.platonExpired = true
            } catch (e: Exception) {
                Diagnostics.swallowed("PlatonRunner", e)
                recordError(context, e)
            }
        }
    }

    private val syncLock = java.util.concurrent.locks.ReentrantLock()

    /**
     * Runs [block] under the process-wide Platon sync lock so a foreground and a background crawl never overlap.
     * With [wait] false it returns null at once when another sync holds the lock.
     */
    fun <T> exclusive(wait: Boolean, block: () -> T): T? {
        if (wait) syncLock.lock() else if (!syncLock.tryLock()) return null
        try {
            return block()
        } finally {
            syncLock.unlock()
        }
    }

    /** Remembers only the exception type so the Tasks screen can show that the last Platon sync failed. */
    fun recordError(context: Context, error: Throwable) {
        StudentPrefs(context).platonLastError = error.javaClass.simpleName
    }

    fun clearError(context: Context) {
        StudentPrefs(context).platonLastError = null
    }

    private fun notify(context: Context, summary: PlatonState.Summary) {
        if (summary.newTitles.isEmpty() && summary.gradeChanges.isEmpty() && summary.newMessages.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val text = pl.vistula.checker.ui.Language.localizedContext(context)
        NotificationHelper.createChannels(context)
        val open = Intent(context, MainActivity::class.java)
            .putExtra("tab", pl.vistula.checker.ui.Tabs.TASKS)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 303, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = NotificationManagerCompat.from(context)
        try {
            if (summary.newTitles.isNotEmpty()) {
                val body = summary.newTitles.take(5).joinToString("\n")
                manager.notify(3003, NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(text.getString(R.string.notification_platon_tasks_title, summary.newTitles.size))
                    .setContentText(summary.newTitles.first())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(NotificationHelper.publicVersion(context, NotificationHelper.CHANNEL_REMINDERS))
                    .setContentIntent(pending).setAutoCancel(true).build())
            }
            if (summary.newMessages.isNotEmpty()) {
                val body = summary.newMessages.take(5).joinToString("\n")
                manager.notify(3005, NotificationCompat.Builder(context, NotificationHelper.CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(text.getString(R.string.notification_platon_messages_title, summary.newMessages.size))
                    .setContentText(summary.newMessages.first())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(NotificationHelper.publicVersion(context, NotificationHelper.CHANNEL_REMINDERS))
                    .setContentIntent(pending).setAutoCancel(true).build())
            }
            if (summary.gradeChanges.isNotEmpty()) {
                val body = summary.gradeChanges.take(5).joinToString("\n") { "${it.first}: ${it.second}" }
                manager.notify(3004, NotificationCompat.Builder(context, NotificationHelper.CHANNEL_GRADES)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(text.getString(R.string.notification_platon_grades_title, summary.gradeChanges.size))
                    .setContentText(body.lineSequence().first())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(NotificationHelper.publicVersion(context, NotificationHelper.CHANNEL_GRADES))
                    .setContentIntent(pending).setAutoCancel(true).build())
            }
        } catch (ignored: SecurityException) {
        }
    }
}
