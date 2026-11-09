package pl.vistula.checker

import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import pl.vistula.checker.core.PlatonLinks
import pl.vistula.checker.core.PlatonState
import pl.vistula.checker.core.PlatonTaskParser
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
        val known = PlatonState.knownDeadlines(host.state.optJSONArray(StateKeys.TASKS))
        host.runTask(
            {
                try {
                    PlatonRunner.exclusive(wait = true) {
                        val crawled = PlatonRunner.crawlRenewing(context, source, known)
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

    /** The cached inbox as a list; tapping a message opens it (after a confirmation when it is unread). */
    fun showMessages() {
        val rows = host.state.optJSONArray(StateKeys.PLATON_MESSAGES)
        if (rows == null || rows.length() == 0) {
            Toast.makeText(activity, R.string.tasks_platon_courses_empty, Toast.LENGTH_LONG).show()
            return
        }
        val labels = ArrayList<CharSequence>()
        val ids = ArrayList<String>()
        var unread = 0
        for (i in 0 until rows.length()) {
            val m = rows.optJSONObject(i) ?: continue
            val isUnread = m.optBoolean("unread")
            if (isUnread) unread++
            val line = StringBuilder()
            if (isUnread) line.append("● ")
            line.append(m.optString("subject")).append('\n').append(m.optString("sender"))
            val date = m.optString("date")
            if (date.isNotEmpty()) line.append(" · ").append(date)
            labels.add(line)
            ids.add(m.optString("id"))
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.tasks_platon_messages_title, unread))
            .setItems(labels.toTypedArray()) { _, which -> openMessage(ids[which]) }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * Reads one message in the app. Opening a message makes Platon mark it as read (exactly as in the browser), so an
     * unread message asks first. Only the message page is requested; nothing is sent to Platon.
     */
    private fun openMessage(id: String) {
        val url = PlatonLinks.messageUrl(id) ?: return
        val unread = host.state.optJSONArray(StateKeys.PLATON_MESSAGES)?.let { rows ->
            (0 until rows.length()).any { rows.optJSONObject(it)?.let { m -> m.optString("id") == id && m.optBoolean("unread") } == true }
        } ?: false
        if (!unread) {
            fetchMessage(id, url)
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.tasks_platon_message_confirm_title)
            .setMessage(R.string.tasks_platon_message_confirm_message)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.tasks_platon_message_open) { _, _ -> fetchMessage(id, url) }
            .show()
    }

    private fun fetchMessage(id: String, url: String) {
        val repository = host.repository
        val session = repository.editSession(host.state)
        host.runTask(
            {
                try {
                    val detail = PlatonTaskParser.parseMessageDetail(PlatonClient.fetch(url).html)
                    var fresh: JSONObject? = null
                    if (detail != null) {
                        repository.update(session) { s -> PlatonState.markMessageRead(s, id) }
                        fresh = repository.read()
                    }
                    MessageResult(detail, fresh, expired = false)
                } catch (e: PlatonClient.SessionExpiredException) {
                    MessageResult(null, null, expired = true)
                }
            },
            { result ->
                result.state?.let {
                    host.state = it
                    host.render()
                }
                val detail = result.detail
                when {
                    result.expired -> {
                        Toast.makeText(activity, R.string.tasks_platon_expired, Toast.LENGTH_LONG).show()
                        connect()
                    }
                    detail == null -> Toast.makeText(activity, R.string.tasks_platon_message_failed, Toast.LENGTH_LONG).show()
                    else -> {
                        val header = listOf(detail.sender, detail.date).filter { it.isNotEmpty() }.joinToString(" · ")
                        MaterialAlertDialogBuilder(activity)
                            .setTitle(detail.subject.ifEmpty { activity.getString(R.string.tasks_platon_messages_menu) })
                            .setMessage((if (header.isEmpty()) "" else header + "\n\n") + detail.body)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            },
        )
    }

    private class MessageResult(val detail: PlatonTaskParser.PlatonMessageDetail?, val state: JSONObject?, val expired: Boolean)

    /** The cached courses as a list; tapping one shows its progress, grade and the assignments Platon listed for it. */
    fun showCourses() {
        val rows = host.state.optJSONArray(StateKeys.PLATON_COURSES)
        if (rows == null || rows.length() == 0) {
            Toast.makeText(activity, R.string.tasks_platon_courses_empty, Toast.LENGTH_LONG).show()
            return
        }
        val labels = ArrayList<CharSequence>()
        val courses = ArrayList<JSONObject>()
        for (i in 0 until rows.length()) {
            val c = rows.optJSONObject(i) ?: continue
            labels.add(c.optString("title") + "\n" + courseLine(c))
            courses.add(c)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.tasks_platon_courses_title, courses.size))
            .setItems(labels.toTypedArray()) { _, which -> showCourse(courses[which]) }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun courseLine(c: JSONObject): String {
        val badge = c.optString("badge")
        return activity.getString(R.string.tasks_platon_course_line, c.optInt("progress"), c.optString("grade").ifEmpty { "—" }, c.optInt("runs")) +
            if (badge.isNotEmpty()) " · $badge" else ""
    }

    private fun showCourse(course: JSONObject) {
        val text = StringBuilder(courseLine(course)).append("\n\n")
        val assignments = PlatonState.assignmentsFor(host.state.optJSONArray(StateKeys.TASKS), course.optString("title"))
        if (assignments.isEmpty()) {
            text.append(activity.getString(R.string.tasks_platon_course_no_assignments))
        } else {
            text.append(activity.getString(R.string.tasks_platon_course_assignments)).append('\n')
            for (t in assignments) {
                text.append(if (t.optBoolean("done")) "✓ " else "○ ").append(t.optString("title"))
                val due = t.optString("due")
                if (due.isNotEmpty()) text.append(" · ").append(due)
                val status = t.optString("remoteStatus")
                if (status.isNotEmpty()) text.append(" · ").append(status)
                text.append('\n')
            }
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(course.optString("title"))
            .setMessage(text.toString().trimEnd())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Notification and refresh choices, with a shortcut to the semester picker. Nothing here changes Platon itself. */
    fun showSettings() {
        val labels = arrayOf(
            activity.getString(R.string.tasks_platon_setting_notify_tasks),
            activity.getString(R.string.tasks_platon_setting_notify_grades),
            activity.getString(R.string.tasks_platon_setting_notify_messages),
            activity.getString(R.string.tasks_platon_setting_auto_refresh),
        )
        val checked = booleanArrayOf(prefs.platonNotifyTasks, prefs.platonNotifyGrades, prefs.platonNotifyMessages, prefs.platonAutoRefresh)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.tasks_platon_settings_title)
            .setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setNegativeButton(R.string.btn_cancel, null)
            .setNeutralButton(R.string.tasks_platon_setting_semesters) { _, _ ->
                saveSettings(checked)
                showSemesters()
            }
            .setPositiveButton(R.string.btn_save) { _, _ -> saveSettings(checked) }
            .show()
    }

    private fun saveSettings(checked: BooleanArray) {
        prefs.platonNotifyTasks = checked[0]
        prefs.platonNotifyGrades = checked[1]
        prefs.platonNotifyMessages = checked[2]
        prefs.platonAutoRefresh = checked[3]
    }

    /** Which semesters the sync reads. Older semesters add their assignments to the list; the choice is reversible. */
    fun showSemesters() {
        val rows = host.state.optJSONArray(StateKeys.PLATON_SEMESTERS)
        val available = (0 until (rows?.length() ?: 0)).mapNotNull { rows?.optString(it)?.takeIf { code -> code.isNotEmpty() } }
        if (available.isEmpty()) {
            Toast.makeText(activity, R.string.tasks_platon_semesters_empty, Toast.LENGTH_LONG).show()
            return
        }
        val saved = prefs.platonSemesters
        val selected = if (saved.isEmpty()) setOf(available.first()) else saved
        val checked = BooleanArray(available.size) { available[it] in selected }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.tasks_platon_semesters_title)
            .setMultiChoiceItems(available.toTypedArray(), checked) { _, which, value -> checked[which] = value }
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.tasks_platon_semesters_apply) { _, _ ->
                val chosen = available.filterIndexed { i, _ -> checked[i] }.toSet()
                // Only the newest semester (or nothing) selected is the default; store nothing for it.
                prefs.platonSemesters = if (chosen.isEmpty() || chosen == setOf(available.first())) emptySet() else chosen
                sync()
            }
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
        if (!prefs.platonLinked || !prefs.platonAutoRefresh) return
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
