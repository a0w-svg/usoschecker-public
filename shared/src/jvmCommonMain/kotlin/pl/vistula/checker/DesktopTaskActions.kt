package pl.vistula.checker

import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.core.TaskTools

/** Desktop uses the same validation and limits as mobile; legacy entries acquire an ID on edit. */
object DesktopTaskActions {
    fun create(source: JSONArray?, title: String, course: String, due: String): JSONArray {
        require(course.length <= 200)
        if (due.isNotBlank()) LocalDate.parse(due.trim())
        return TaskTools.save(source, JSONObject().put("id", UUID.randomUUID().toString()).put("title", title.trim())
            .put("course", course.trim()).put("due", due.trim()).put("done", false).put("createdAt", System.currentTimeMillis()))
    }

    private fun same(row: JSONObject, target: JSONObject): Boolean =
        if (target.optString("id").isNotEmpty()) row.optString("id") == target.optString("id")
        else row.toString() == target.toString()

    fun toggle(source: JSONArray?, target: JSONObject): JSONArray {
        val rows = JSONArray(source?.toString() ?: "[]")
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            if (!same(row, target)) continue
            if (row.optString("id").isEmpty()) row.put("id", UUID.randomUUID().toString())
            row.put("done", !target.optBoolean("done"))
            return rows
        }
        return rows
    }

    fun delete(source: JSONArray?, target: JSONObject): JSONArray {
        val result = JSONArray()
        var removed = false
        if (source != null) for (i in 0 until source.length()) {
            val row = source.getJSONObject(i)
            if (!removed && same(row, target)) removed = true else result.put(JSONObject(row.toString()))
        }
        return result
    }
}
