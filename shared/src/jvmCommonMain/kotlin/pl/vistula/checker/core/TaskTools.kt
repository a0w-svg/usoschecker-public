package pl.vistula.checker.core

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Comparator
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Local task operations preserve legacy entries and never mutate their source. */
object TaskTools {
    @JvmStatic
    @Throws(JSONException::class)
    fun save(source: JSONArray?, replacement: JSONObject): JSONArray {
        val id = replacement.getString("id")
        if (id.isEmpty() || replacement.optString("title").trim().isEmpty() ||
            replacement.optString("title").length > 200 || replacement.optString("notes").length > 2000)
            throw IllegalArgumentException("Nieprawidłowa treść zadania.")
        val platonUrl = PlatonLinks.normalize(replacement.optString("platonUrl"))
        val copy = JSONObject(replacement.toString())
        if (copy.has("progress")) copy.put("progress", maxOf(0, minOf(100, copy.optInt("progress", 0))))
        if (platonUrl.isEmpty()) copy.remove("platonUrl") else copy.put("platonUrl", platonUrl)
        val result = if (source == null) JSONArray() else JSONArray(source.toString())
        for (i in 0 until result.length()) {
            val entry = result.optJSONObject(i)
            if (entry != null && id == entry.optString("id")) {
                result.put(i, copy)
                return result
            }
        }
        if (result.length() >= 200) throw IllegalStateException("Limit 200 zadań. Usuń inny wpis, aby dodać zadanie.")
        return result.put(copy)
    }

    @JvmStatic
    fun priority(task: JSONObject): Int = maxOf(0, minOf(2, task.optInt("priority", 1)))

    /** Completion in percent; a finished task is always 100. */
    @JvmStatic
    fun progress(task: JSONObject): Int =
        if (task.optBoolean("done")) 100 else maxOf(0, minOf(99, task.optInt("progress", 0)))

    /** Days until the deadline (negative when overdue), or null without a valid date. */
    @JvmStatic
    fun daysLeft(task: JSONObject, today: LocalDate): Long? =
        dueDate(task)?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }

    /** Toggles completion, keeping progress consistent: done is 100%, reopening a finished task resets it. */
    @JvmStatic
    @Throws(JSONException::class)
    fun toggled(task: JSONObject): JSONObject {
        val copy = JSONObject(task.toString())
        val done = !task.optBoolean("done")
        copy.put("done", done)
        if (done) copy.put("progress", 100) else copy.put("progress", 0)
        return copy
    }

    /** Share of finished tasks among all, 0..100. */
    @JvmStatic
    fun completionPercent(source: JSONArray?): Int {
        if (source == null || source.length() == 0) return 0
        var done = 0
        for (i in 0 until source.length()) if (source.optJSONObject(i)?.optBoolean("done") == true) done++
        return done * 100 / source.length()
    }

    @JvmStatic
    fun dueDate(task: JSONObject): LocalDate? = try {
        LocalDate.parse(task.optString("due"))
    } catch (e: DateTimeParseException) {
        null
    }

    @JvmStatic
    fun matches(task: JSONObject, filter: String, query: String?, today: LocalDate): Boolean {
        if (!StudentSearch.matches(
                query ?: "",
                task.optString("title"),
                task.optString("notes"),
                task.optString("due"),
                task.optString("platonUrl"),
                if (task.optString("platonUrl").isEmpty()) "" else "Platon",
            )) return false
        val done = task.optBoolean("done")
        val due = dueDate(task)
        return when (filter) {
            "platon" -> PlatonLinks.isTaskLink(task.optString("platonUrl"))
            "open" -> !done
            "done" -> done
            "today" -> !done && today == due
            "overdue" -> !done && due != null && due.isBefore(today)
            "week" -> !done && due != null && !due.isBefore(today) && due.isBefore(today.plusDays(7))
            else -> true
        }
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun sorted(source: JSONArray?, order: String?): List<JSONObject> {
        val rows = ArrayList<JSONObject>()
        if (source != null) for (i in 0 until source.length()) rows.add(source.getJSONObject(i))
        val byDue = Comparator.comparing<JSONObject, String> { it.optString("due") }
        val byPriority = Comparator.comparingInt<JSONObject> { priority(it) }.reversed()
        val byTitle = Comparator.comparing<JSONObject, String> { it.optString("title").lowercase(Locale.ROOT) }
        val selected = when (order) {
            "priority" -> byPriority.thenComparing(byDue)
            "title" -> byTitle.thenComparing(byDue)
            else -> byDue.thenComparing(byPriority)
        }
        rows.sortWith(Comparator.comparing<JSONObject, Boolean> { it.optBoolean("done") }.thenComparing(selected).thenComparing { it.optString("id") })
        return rows
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun restore(source: JSONArray?, deleted: JSONObject): JSONArray {
        val result = if (source == null) JSONArray() else JSONArray(source.toString())
        for (i in 0 until result.length()) if (result.getJSONObject(i).optString("id") == deleted.getString("id")) return result
        if (result.length() >= 200) throw IllegalStateException("Limit 200 zadań. Usuń inny wpis, aby przywrócić zadanie.")
        result.put(JSONObject(deleted.toString()))
        return result
    }
}

