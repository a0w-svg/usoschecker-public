package pl.vistula.checker.core

import java.util.Locale
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Local attendance grouped by semester, course and class format. */
object AttendanceTracker {
    const val LIMIT: Int = 3

    @JvmStatic
    fun key(term: String?, course: String?, type: String?): String =
        JSONArray().put(term).put(course).put(type).toString()

    @JvmStatic
    fun type(row: Record): String {
        val label = row.optMeta("classTypeCanonical", row.optMeta("classType")).trim()
        val value = label.ifEmpty { row.optMeta("classTypeId").trim() }
        val lower = value.lowercase(Locale.ROOT)
        if (lower == "w" || lower == "wyk" || lower.contains("wykład") || lower.contains("lecture")) return "Wykład"
        if (lower == "c" || lower == "cw" || lower == "cwi" || lower.contains("ćwicz") || lower.contains("exercise")) return "Ćwiczenia"
        if (lower == "l" || lower == "lab" || lower.contains("laborat")) return "Laboratorium"
        if (lower == "s" || lower == "sem" || lower.contains("seminar")) return "Seminarium"
        if (lower == "p" || lower == "pro" || lower == "prj" || lower.contains("projekt") || lower.contains("project")) return "Projekt"
        if (lower == "lek" || lower.contains("lektor") || lower.contains("language")) return "Lektorat"
        if (lower == "kon" || lower.contains("konwersat")) return "Konwersatorium"
        return value
    }

    @JvmStatic
    fun course(row: Record): String =
        row.optMeta("courseId").ifEmpty { row.title }

    @JvmStatic
    fun term(snapshot: Snapshot, row: Record): String {
        if (row.term.isNotEmpty()) return row.term
        val date = StudentServices.date(row.date)
        val matches = HashSet<String>()
        if (date != null) {
            for (term in snapshot.terms.values) {
                if (term.isCurrent(date)) matches.add(term.id)
            }
        }
        val courseTerms = HashSet<String>()
        for (edition in snapshot.courses) {
            if (course(edition) == course(row) && edition.term.isNotEmpty()) {
                courseTerms.add(edition.term)
            }
        }
        if (matches.size > 1) matches.retainAll(courseTerms)
        if (matches.size == 1) return matches.iterator().next()
        // Only undated entries can use a unique course edition without university calendar dates.
        if (date == null && courseTerms.size == 1) return courseTerms.iterator().next()
        return ""
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun item(name: String, course: String, term: String, type: String): JSONObject =
        JSONObject()
            .put("name", name)
            .put("courseId", course)
            .put("term", term)
            .put("classType", type)
            .put("absences", 0)
            .put("limit", LIMIT)
            .put("notes", "")

    @JvmStatic
    @Throws(JSONException::class)
    fun reconcile(saved: JSONObject, snapshot: Snapshot?): JSONObject {
        val result = JSONObject(saved.toString())
        // Keep undated legacy totals intact; users can assign them in the editor.
        val ids = result.keys()
        while (ids.hasNext()) {
            val id = ids.next()
            val entry = result.optJSONObject(id) ?: continue
            if (!entry.has("courseId")) entry.put("courseId", id)
            if (!entry.has("term")) entry.put("term", "")
            if (!entry.has("classType")) entry.put("classType", "")
            entry.put("limit", LIMIT)
        }
        if (snapshot != null) {
            for (row in snapshot.records.values) {
                if (row.kind != "plan") continue
                val t = term(snapshot, row)
                val tp = type(row)
                val c = course(row)
                val id = key(t, c, tp)
                if (!result.has(id)) result.put(id, item(row.title, c, t, tp))
            }
        }
        return result
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun record(saved: JSONObject, snapshot: Snapshot, lesson: Record): String {
        val t = term(snapshot, lesson)
        val tp = type(lesson)
        val c = course(lesson)
        val id = key(t, c, tp)
        var entry = saved.optJSONObject(id)
        if (entry == null) {
            entry = item(lesson.title, c, t, tp)
            saved.put(id, entry)
        }
        var lessons = entry.optJSONArray("lessons")
        if (lessons == null) lessons = JSONArray()
        for (i in 0 until lessons.length()) {
            if (lesson.id == lessons.optString(i)) return id
        }
        lessons.put(lesson.id)
        entry.put("lessons", lessons)
            .put("absences", max(0, entry.optInt("absences")) + 1)
            .put("limit", LIMIT)
        val notes = entry.optString("notes")
        entry.put("notes", (if (notes.isEmpty()) "" else "$notes\n") + "Opuszczono: " + lesson.date + " " + lesson.startTime)
        return id
    }

    @JvmStatic
    fun exceeded(count: Int): Boolean = count > LIMIT

    @JvmStatic
    fun warning(count: Int): Boolean = count >= LIMIT - 1 && !exceeded(count)
}

