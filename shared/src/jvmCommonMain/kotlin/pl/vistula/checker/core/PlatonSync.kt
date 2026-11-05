package pl.vistula.checker.core

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Merges assignments read from Platon into the local task list without losing the student's own edits. */
object PlatonSync {
    class Result(val tasks: JSONArray, val added: Int, val updated: Int, val skipped: Int, val removed: Int = 0)

    /** True when the final address or page is a sign-in page, so the session cookie is missing or expired. */
    @JvmStatic
    fun isLoginPage(finalUrl: String?, html: String?): Boolean {
        val url = (finalUrl ?: "").lowercase()
        if (url.contains("/logowanie") || url.contains("/zaloguj") || url.contains("login.microsoftonline.com") ||
            url.contains("login.vistula.edu.pl")) return true
        val head = (html ?: "").take(6000).lowercase()
        return head.contains("type=\"password\"") || head.contains("type='password'")
    }

    @JvmStatic
    fun merge(local: JSONArray?, imported: List<PlatonTask>, now: Long): Result {
        val result = prune(local, imported)
        val removed = (local?.length() ?: 0) - result.length()
        val index = HashMap<String, Int>()
        for (i in 0 until result.length()) {
            val id = result.optJSONObject(i)?.optString("externalId").orEmpty()
            if (id.isNotEmpty()) index[id] = i
        }
        var added = 0
        var updated = 0
        var skipped = 0
        for (remote in imported) {
            val at = index[remote.externalId]
            if (at != null) {
                val entry = result.getJSONObject(at)
                if (apply(entry, remote, now)) updated++
                continue
            }
            if (result.length() >= 200) {
                skipped++
                continue
            }
            val entry = JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("title", remote.title)
                .put("done", remote.submitted)
                .put("priority", 1)
                .put("notes", "")
                .put("source", "platon")
                .put("externalId", remote.externalId)
                .put("platonUrl", remote.url)
                .put("progress", remote.progress ?: 0)
                .put("syncedAt", now)
            apply(entry, remote, now)
            result.put(entry)
            index[remote.externalId] = result.length() - 1
            added++
        }
        return Result(result, added, updated, skipped, removed)
    }

    /** Remote data wins for title, course and deadline; the student's own progress and completion are kept. */
    private fun apply(entry: JSONObject, remote: PlatonTask, now: Long): Boolean {
        val before = entry.toString()
        entry.put("title", remote.title)
        if (remote.course.isEmpty()) entry.remove("course") else entry.put("course", remote.course)
        if (remote.due != null) entry.put("due", remote.due.toString())
        if (remote.dueTime.isEmpty()) entry.remove("dueTime") else entry.put("dueTime", remote.dueTime)
        entry.put("platonUrl", remote.url)
        if (remote.status.isEmpty()) entry.remove("remoteStatus") else entry.put("remoteStatus", remote.status)
        if (remote.submitted && !entry.optBoolean("done")) {
            entry.put("done", true)
            entry.put("progress", 100)
        } else if (!entry.optBoolean("done") && !entry.optBoolean("progressManual") && remote.progress != null) {
            entry.put("progress", remote.progress)
        }
        val changed = before != entry.toString()
        entry.put("syncedAt", now)
        return changed
    }

    /** Drops untouched Platon entries that no longer appear in a non-empty import (stale or misread rows). */
    private fun prune(local: JSONArray?, imported: List<PlatonTask>): JSONArray {
        val result = JSONArray()
        if (local == null) return result
        val keep = imported.map { it.externalId }.toSet()
        for (i in 0 until local.length()) {
            val entry = local.opt(i)
            val row = entry as? JSONObject
            val stale = row != null && row.optString("source") == "platon" && imported.isNotEmpty() &&
                row.optString("externalId") !in keep && !row.optBoolean("done") &&
                row.optString("notes").isEmpty() && !row.optBoolean("progressManual")
            if (!stale) result.put(if (row == null) entry else JSONObject(row.toString()))
        }
        return result
    }

    /** Latest inbox rows kept for the offline message list. */
    @JvmStatic
    fun messagesJson(messages: List<PlatonTaskParser.PlatonMessage>): JSONArray {
        val result = JSONArray()
        for (m in messages.take(20)) {
            result.put(JSONObject().put("id", m.id).put("sender", m.sender).put("subject", m.subject)
                .put("date", m.date).put("unread", m.unread))
        }
        return result
    }

    /** Course overview stored next to the tasks: progress, grade and launches as shown by Platon. */
    @JvmStatic
    fun coursesJson(courses: List<PlatonTaskParser.PlatonCourse>, now: Long): JSONArray {
        val result = JSONArray()
        for (c in courses.take(60)) {
            result.put(JSONObject().put("url", c.url).put("title", c.title).put("progress", c.progress)
                .put("grade", c.grade).put("runs", c.runs).put("badge", c.badge).put("syncedAt", now))
        }
        return result
    }
}
