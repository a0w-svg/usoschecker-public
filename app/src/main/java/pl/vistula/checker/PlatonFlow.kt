package pl.vistula.checker

import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import pl.vistula.checker.core.PlatonState
import pl.vistula.checker.core.StateKeys
import pl.vistula.checker.ui.Tabs

/**
 * Everything the student can do with Platon from the Tasks screen: sign in, sync, read cached courses and messages,
 * sign out, and the one-time offer after USOS login. [PlatonRunner] does the crawling and state merging;
 * this class owns the screen flow (dialogs, toasts, when to sync).
 *
 * Must be created while the Activity is being constructed: it registers the sign-in result launcher.
 */
class PlatonFlow(private val host: FlowHost) {
    private val activity get() = host.activity
    private val prefs = StudentPrefs(host.activity)

    /** True once an automatic sync was attempted since the last successful sync, so a failure is not retried in a loop. */
    private var autoTried = false

    private val login = host.activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val url = result.data?.getStringExtra(PlatonLoginActivity.EXTRA_URL)
        if (result.resultCode == android.app.Activity.RESULT_OK && url != null) {
            prefs.platonSource = url
            sync()
        }
    }

    private class Outcome(val state: JSONObject?, val found: Int, val added: Int, val updated: Int, val skipped: Int)

    /** Opens the Platon sign-in WebView; once signed in the account is synchronised. */
    fun connect() {
        if (host.isDemo) {
            Toast.makeText(activity, R.string.services_demo_action, Toast.LENGTH_LONG).show()
            return
        }
        login.launch(Intent(activity, PlatonLoginActivity::class.java).putExtra(PlatonLoginActivity.EXTRA_URL, prefs.platonSource))
    }

    /** Reads Platon with the WebView session and merges assignments, progress, courses and messages. */
    fun sync(auto: Boolean = false) {
        if (host.isDemo) {
            if (!auto) Toast.makeText(activity, R.string.services_demo_action, Toast.LENGTH_LONG).show()
            return
        }
        val source = prefs.platonSource
        if (source == null) {
            if (!auto) connect()
            return
        }
        val repository = host.repository
        val session = repository.editSession(host.state)
        val context = activity.applicationContext
        host.runTask(
            {
                try {
                    PlatonRunner.exclusive(wait = true) {
                        val crawled = PlatonRunner.crawlRenewing(context, source)
                        var summary: PlatonState.Summary? = null
                        if (crawled.tasks.isNotEmpty() || crawled.courses.isNotEmpty() || crawled.messages.isNotEmpty()) {
                            repository.update(session) { s -> summary = PlatonState.apply(s, crawled, System.currentTimeMillis()) }
                        }
                        PlatonRunner.clearError(context)
                        Outcome(repository.read(), summary?.found ?: 0, summary?.added ?: 0, summary?.updated ?: 0, summary?.skipped ?: 0)
                    }!!
                } catch (e: PlatonClient.SessionExpiredException) {
                    Outcome(null, 0, 0, 0, 0)
                } catch (e: Exception) {
                    PlatonRunner.recordError(context, e)
                    throw e
                }
            },
            { outcome ->
                val fresh = outcome.state
                when {
                    fresh == null -> {
                        if (auto) {
                            Toast.makeText(activity, R.string.tasks_platon_expired, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, R.string.tasks_platon_expired, Toast.LENGTH_LONG).show()
                            connect()
                        }
                    }
                    outcome.found == 0 -> {
                        if (!auto) Toast.makeText(activity, R.string.tasks_platon_none, Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        host.state = fresh
                        host.render()
                        prefs.platonSyncedAt = System.currentTimeMillis()
                        autoTried = false
                        val text = activity.getString(R.string.tasks_platon_synced, outcome.added, outcome.updated) +
                            if (outcome.skipped > 0) "\n" + activity.getString(R.string.tasks_error_limit) else ""
                        if (!auto || outcome.added + outcome.updated > 0) Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    /** Asks before signing out; also drops the cached courses and messages. */
    fun disconnect() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.tasks_platon_disconnect_title)
            .setMessage(R.string.tasks_platon_disconnect_message)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.tasks_platon_disconnect) { _, _ ->
                val repository = host.repository
                val session = repository.editSession(host.state)
                val context = activity.applicationContext
                host.runTask(
                    {
                        PlatonRunner.forget(context)
                        repository.update(session) { s ->
                            s.remove(StateKeys.PLATON_COURSES)
                            s.remove(StateKeys.PLATON_MESSAGES)
                        }
                        repository.read()
                    },
                    { s ->
                        host.state = s
                        host.render()
                        Toast.makeText(activity, R.string.tasks_platon_disconnected, Toast.LENGTH_LONG).show()
                    },
                )
            }
            .show()
    }

    fun openMessages() {
        host.navigate(Tabs.TASKS)
        showMessages()
    }

    fun showMessages() {
        val rows = host.state.optJSONArray(StateKeys.PLATON_MESSAGES)
        if (rows == null || rows.length() == 0) {
            Toast.makeText(activity, R.string.tasks_platon_courses_empty, Toast.LENGTH_LONG).show()
            return
        }
        val text = StringBuilder()
        var unread = 0
        for (i in 0 until rows.length()) {
            val m = rows.optJSONObject(i) ?: continue
            val isUnread = m.optBoolean("unread")
            if (isUnread) unread++
            if (text.isNotEmpty()) text.append("\n\n")
            if (isUnread) text.append("● ")
            text.append(m.optString("subject")).append('\n').append(m.optString("sender"))
            val date = m.optString("date")
            if (date.isNotEmpty()) text.append(" · ").append(date)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.tasks_platon_messages_title, unread))
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun showCourses() {
        val rows = host.state.optJSONArray(StateKeys.PLATON_COURSES)
        if (rows == null || rows.length() == 0) {
            Toast.makeText(activity, R.string.tasks_platon_courses_empty, Toast.LENGTH_LONG).show()
            return
        }
        val text = StringBuilder()
        for (i in 0 until rows.length()) {
            val c = rows.optJSONObject(i) ?: continue
            if (text.isNotEmpty()) text.append("\n\n")
            text.append(c.optString("title")).append('\n')
                .append(activity.getString(R.string.tasks_platon_course_line, c.optInt("progress"), c.optString("grade").ifEmpty { "—" }, c.optInt("runs")))
            val badge = c.optString("badge")
            if (badge.isNotEmpty()) text.append(" · ").append(badge)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.tasks_platon_courses_title, rows.length()))
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Right after the USOS sign-in succeeds, offers the one-tap Platon sign-in once. */
    fun offer() {
        if (host.isDemo || !host.connected() || prefs.platonOffered || prefs.platonLinked) return
        prefs.platonOffered = true
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.tasks_platon_offer_title)
            .setMessage(R.string.tasks_platon_offer_message)
            .setNegativeButton(R.string.tasks_platon_offer_later, null)
            .setPositiveButton(R.string.tasks_platon_connect) { _, _ -> connect() }
            .show()
    }

    /** Refreshes Platon when Tasks opens, at most every [maxAgeMs] and once per app start after a failure. */
    fun autoSync(maxAgeMs: Long = DEFAULT_MAX_AGE_MS) {
        if (host.isDemo || host.isBusy || autoTried) return
        if (!prefs.platonLinked) return
        if (prefs.platonExpired) {
            // The background refresh found the Platon session expired: ask once for a one-tap sign-in.
            prefs.platonExpired = false
            autoTried = true
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.tasks_platon_expired)
                .setNegativeButton(R.string.tasks_platon_offer_later, null)
                .setPositiveButton(R.string.tasks_platon_connect) { _, _ -> connect() }
                .show()
            return
        }
        if (System.currentTimeMillis() - prefs.platonSyncedAt < maxAgeMs) return
        autoTried = true
        sync(auto = true)
    }

    /** After a manual USOS refresh: sync Platon too if it has not been synced in the last few minutes. */
    fun afterUsosRefresh() {
        autoTried = false
        autoSync(maxAgeMs = AFTER_USOS_MAX_AGE_MS)
    }

    /** Clears the "already tried" latch, e.g. after an account change. */
    fun resetAutoTry() {
        autoTried = false
    }

    private companion object {
        const val DEFAULT_MAX_AGE_MS = 30 * 60_000L
        const val AFTER_USOS_MAX_AGE_MS = 5 * 60_000L
    }
}
