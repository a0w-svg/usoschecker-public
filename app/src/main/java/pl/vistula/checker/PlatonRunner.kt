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
import org.json.JSONObject
import pl.vistula.checker.core.PlatonCrawler
import pl.vistula.checker.core.PlatonSync

/** One Platon synchronisation, shared by the Tasks screen and the background worker. */
object PlatonRunner {
    class Summary(
        val found: Int,
        val added: Int,
        val updated: Int,
        val skipped: Int,
        val newTitles: List<String>,
        val gradeChanges: List<Pair<String, String>>,
        val newMessages: List<String> = emptyList(),
        val events: List<Event> = emptyList(),
    )

    /** One Platon change shown in the Changes feed; [type] is "task", "grade" or "message". */
    class Event(val type: String, val title: String, val before: String, val after: String)

    private const val PREFS = "student_tools"
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

    /** Merges [crawled] into the account state [s] and reports what is new or changed. */
    fun apply(s: JSONObject, crawled: PlatonCrawler.Result, now: Long): Summary {
        val known = HashSet<String>()
        s.optJSONArray("tasks")?.let { rows ->
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.optString("externalId")?.let { known.add(it) }
        }
        val oldGrades = HashMap<String, String>()
        s.optJSONArray("platonCourses")?.let { rows ->
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { oldGrades[it.optString("url")] = it.optString("grade") }
        }
        var added = 0
        var updated = 0
        var skipped = 0
        if (crawled.tasks.isNotEmpty()) {
            val merged = PlatonSync.merge(s.optJSONArray("tasks"), crawled.tasks, now)
            s.put("tasks", merged.tasks)
            added = merged.added
            updated = merged.updated
            skipped = merged.skipped
        }
        val firstRun = oldGrades.isEmpty()
        val gradeChanges = ArrayList<Pair<String, String>>()
        if (crawled.courses.isNotEmpty()) {
            s.put("platonCourses", PlatonSync.coursesJson(crawled.courses, now))
            if (!firstRun) for (c in crawled.courses) {
                val before = oldGrades[c.url]
                if (before != null && before != c.grade && c.grade.isNotEmpty()) gradeChanges.add(c.title to c.grade)
            }
        }
        val newTitles = if (known.isEmpty() && firstRun) emptyList()
        else crawled.tasks.filter { it.externalId !in known && !it.submitted }.map { it.title }
        val seenMessages = HashSet<String>()
        val hadMessages = s.optJSONArray("platonMessages") != null
        s.optJSONArray("platonMessages")?.let { rows ->
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.optString("id")?.let { seenMessages.add(it) }
        }
        var newMessages: List<String> = emptyList()
        if (crawled.messages.isNotEmpty()) {
            s.put("platonMessages", PlatonSync.messagesJson(crawled.messages))
            if (hadMessages) newMessages = crawled.messages.filter { it.unread && it.id !in seenMessages }.map { it.subject }
        }
        val events = ArrayList<Event>()
        for (task in crawled.tasks) {
            if (task.externalId !in known && !task.submitted && newTitles.contains(task.title)) {
                val due = task.due?.toString().orEmpty()
                events.add(Event("task", task.title, "", listOf(task.course, due).filter { it.isNotEmpty() }.joinToString(" · ")))
            }
        }
        for ((title, grade) in gradeChanges) {
            val before = crawled.courses.firstOrNull { it.title == title }?.url?.let { oldGrades[it] }.orEmpty()
            events.add(Event("grade", title, before, grade))
        }
        for (m in crawled.messages) {
            if (m.unread && m.id !in seenMessages && newMessages.contains(m.subject)) events.add(Event("message", m.subject, "", m.sender))
        }
        prependHistory(s, events, now)
        return Summary(
            crawled.tasks.size + crawled.courses.size + crawled.messages.size,
            added, updated, skipped, newTitles, gradeChanges, newMessages, events,
        )
    }

    /** Adds Platon events to the front of the Changes feed (kind "platon"), keeping the feed's 200-entry cap. */
    private fun prependHistory(s: JSONObject, events: List<Event>, now: Long) {
        if (events.isEmpty()) return
        val history = org.json.JSONArray()
        for (e in events.take(50)) {
            val summary = if (e.before.isEmpty()) e.after else "${e.before} → ${e.after}"
            history.put(
                JSONObject()
                    .put("kind", "platon")
                    .put("title", e.title)
                    .put("action", if (e.type == "grade") "Zmieniono" else "Dodano")
                    .put("before", e.before)
                    .put("after", e.after)
                    .put("sourceTime", "")
                    .put("detectedAt", now)
                    .put("metadata", JSONObject().put("platonEvent", e.type).put("summary", summary)),
            )
        }
        s.optJSONArray("history")?.let { old ->
            var i = 0
            while (i < old.length() && history.length() < 200) history.put(old.get(i++))
        }
        s.put("history", history)
    }

    /** Signs out of Platon on this device: forgets the linked page, flags and the in-app browser's cookies. */
    fun forget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove("platonSource")
            remove("platonSyncedAt")
            remove("platonOffered")
            remove("platonExpired")
            remove("platonLastError")
        }
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
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val source = prefs.getString("platonSource", null)
        if (source.isNullOrEmpty()) return
        if (System.currentTimeMillis() - prefs.getLong("platonSyncedAt", 0L) < MIN_BACKGROUND_GAP_MS) return
        // Skip when the student's own sync is already running; the next periodic run will catch up.
        exclusive(wait = false) {
            try {
                val crawled = crawlRenewing(context, source)
                if (crawled.tasks.isEmpty() && crawled.courses.isEmpty() && crawled.messages.isEmpty()) return@exclusive
                var summary: Summary? = null
                val now = System.currentTimeMillis()
                Repository(context).update { s -> summary = apply(s, crawled, now) }
                prefs.edit {
                    putLong("platonSyncedAt", now)
                    remove("platonLastError")
                }
                summary?.let { notify(context, it) }
            } catch (e: PlatonClient.SessionExpiredException) {
                prefs.edit { putBoolean("platonExpired", true) }
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString("platonLastError", error.javaClass.simpleName) }
    }

    fun clearError(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove("platonLastError") }
    }

    private fun notify(context: Context, summary: Summary) {
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
