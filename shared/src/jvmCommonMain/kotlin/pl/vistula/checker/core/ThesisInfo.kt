package pl.vistula.checker.core

import org.json.JSONException
import org.json.JSONObject

class ThesisInfo(
    @JvmField val title: String,
    @JvmField val status: String,
    @JvmField val supervisors: String,
    @JvmField val reviewers: String,
) {
    fun hasThesis(): Boolean = title.trim().isNotEmpty()

    @Throws(JSONException::class)
    fun json(): JSONObject = JSONObject().put("title", title).put("status", status)
        .put("supervisors", supervisors).put("reviewers", reviewers)

    companion object {
        @JvmStatic
        fun from(o: JSONObject?): ThesisInfo {
            if (o == null) return ThesisInfo("", "", "", "")
            return ThesisInfo(
                o.optString("title", ""), o.optString("status", ""),
                o.optString("supervisors", ""), o.optString("reviewers", ""),
            )
        }
    }
}

