package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Applies a Platon crawl to the account state JSON: merges tasks, stores courses and messages, and appends
 * Changes-feed entries (kind "platon"). Pure JVM, so it is unit-tested without Android and reusable on desktop.
 */
object PlatonState {
    class Summary(
        val found: Int,
        val added: Int,
        val updated: Int,
        val skipped: Int,
        val newTitles: List<String>,
        val gradeChanges: List<Pair<String, String>>,
        val newMessages: List<String> = emptyList(),
        val events: List<Event> = emptyList(),
    ) {
        /** Same summary limited to the kinds of news the student wants notifications for. */
        fun only(tasks: Boolean, grades: Boolean, messages: Boolean): Summary = Summary(
            found, added, updated, skipped,
            if (tasks) newTitles else emptyList(),
            if (grades) gradeChanges else emptyList(),
            if (messages) newMessages else emptyList(),
            events,
        )
    }

    /** One Platon change shown in the Changes feed; [type] is "task", "grade" or "message". */
    class Event(val type: String, val title: String, val before: String, val after: String)

    /** Deadlines already stored for Platon tasks, by task key, so a sync does not fetch them again. */
    @JvmStatic
    fun knownDeadlines(tasks: JSONArray?): Map<String, PlatonCrawler.KnownDeadline> {
        val result = HashMap<String, PlatonCrawler.KnownDeadline>()
        if (tasks == null) return result
        for (i in 0 until tasks.length()) {
            val t = tasks.optJSONObject(i) ?: continue
            val id = t.optString("externalId")
            if (id.isEmpty() || t.optString("source") != "platon") continue
            val due = try { java.time.LocalDate.parse(t.optString("due")) } catch (e: Exception) { continue }
            result[id] = PlatonCrawler.KnownDeadline(due, t.optString("dueTime"))
        }
        return result
    }

    /** Marks inbox message [id] as read in the stored list; true when a stored unread message was changed. */
    @JvmStatic
    fun markMessageRead(s: JSONObject, id: String): Boolean {
        val rows = s.optJSONArray(StateKeys.PLATON_MESSAGES) ?: return false
        for (i in 0 until rows.length()) {
            val m = rows.optJSONObject(i) ?: continue
            if (m.optString("id") == id && m.optBoolean("unread")) {
                m.put("unread", false)
                return true
            }
        }
        return false
    }

    /** Tasks of [tasks] that Platon listed under the course called [courseTitle], in the given order. */
    @JvmStatic
    fun assignmentsFor(tasks: JSONArray?, courseTitle: String): List<JSONObject> {
        val result = ArrayList<JSONObject>()
        if (tasks == null) return result
        for (i in 0 until tasks.length()) {
            val t = tasks.optJSONObject(i) ?: continue
            if (t.optString("source") == "platon" && t.optString("course").trim().equals(courseTitle.trim(), ignoreCase = true)) result.add(t)
        }
        return result
    }

    /** Merges [crawled] into the account state [s] and reports what is new or changed. */
    fun apply(s: JSONObject, crawled: PlatonCrawler.Result, now: Long): Summary {
        val known = HashSet<String>()
        s.optJSONArray(StateKeys.TASKS)?.let { rows ->
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.optString("externalId")?.let { known.add(it) }
        }
        val oldGrades = HashMap<String, String>()
        s.optJSONArray(StateKeys.PLATON_COURSES)?.let { rows ->
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { oldGrades[it.optString("url")] = it.optString("grade") }
        }
        var added = 0
        var updated = 0
        var skipped = 0
        if (crawled.tasks.isNotEmpty()) {
            val merged = PlatonSync.merge(s.optJSONArray(StateKeys.TASKS), crawled.tasks, now)
            s.put(StateKeys.TASKS, merged.tasks)
            added = merged.added
            updated = merged.updated
            skipped = merged.skipped
        }
        val firstRun = oldGrades.isEmpty()
        val gradeChanges = ArrayList<Pair<String, String>>()
        if (crawled.courses.isNotEmpty()) {
            s.put(StateKeys.PLATON_COURSES, PlatonSync.coursesJson(crawled.courses, now))
            if (!firstRun) for (c in crawled.courses) {
                val before = oldGrades[c.url]
                if (before != null && before != c.grade && c.grade.isNotEmpty()) gradeChanges.add(c.title to c.grade)
            }
        }
        val newTitles = if (known.isEmpty() && firstRun) emptyList()
        else crawled.tasks.filter { it.externalId !in known && !it.submitted }.map { it.title }
        if (crawled.semesters.isNotEmpty()) {
            val list = JSONArray()
            crawled.semesters.forEach { list.put(it) }
            s.put(StateKeys.PLATON_SEMESTERS, list)
        }
        val seenMessages = HashSet<String>()
        val hadMessages = s.optJSONArray(StateKeys.PLATON_MESSAGES) != null
        s.optJSONArray(StateKeys.PLATON_MESSAGES)?.let { rows ->
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.optString("id")?.let { seenMessages.add(it) }
        }
        var newMessages: List<String> = emptyList()
        if (crawled.messages.isNotEmpty()) {
            s.put(StateKeys.PLATON_MESSAGES, PlatonSync.messagesJson(crawled.messages))
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
        val history = JSONArray()
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
        s.optJSONArray(StateKeys.HISTORY)?.let { old ->
            var i = 0
            while (i < old.length() && history.length() < 200) history.put(old.get(i++))
        }
        s.put(StateKeys.HISTORY, history)
    }
}
