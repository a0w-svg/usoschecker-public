package pl.vistula.checker.core

import org.json.JSONException
import org.json.JSONObject

class CourseTestRecord @JvmOverloads constructor(
    @JvmField val id: String,
    @JvmField val courseId: String,
    @JvmField val courseName: String,
    @JvmField val testName: String,
    @JvmField val points: Double,
    @JvmField val maxPoints: Double,
    @JvmField val grade: String,
    @JvmField val date: String,
    @JvmField val term: String = "",
    @JvmField val hasPoints: Boolean = grade.isEmpty() || maxPoints > 0 || points != 0.0,
) {
    fun percentage(): Double = if (hasPoints && maxPoints > 0) (points / maxPoints) * 100.0 else Double.NaN

    @Throws(JSONException::class)
    fun json(): JSONObject = JSONObject().put("id", id).put("courseId", courseId).put("courseName", courseName)
        .put("testName", testName).put("points", points).put("maxPoints", maxPoints)
        .put("grade", grade).put("date", date).put("term", term).put("hasPoints", hasPoints)

    companion object {
        @JvmStatic
        fun from(o: JSONObject?): CourseTestRecord {
            if (o == null) return CourseTestRecord("", "", "", "", 0.0, 0.0, "", "")
            return CourseTestRecord(
                o.optString("id", ""), o.optString("courseId", ""),
                o.optString("courseName", ""), o.optString("testName", ""),
                o.optDouble("points", 0.0), o.optDouble("maxPoints", 0.0),
                o.optString("grade", ""), o.optString("date", ""), o.optString("term", ""),
                if (o.has("hasPoints")) o.optBoolean("hasPoints") else
                    (o.optString("grade", "").isEmpty() || o.optDouble("maxPoints", 0.0) > 0 || o.optDouble("points", 0.0) != 0.0),
            )
        }
    }
}

