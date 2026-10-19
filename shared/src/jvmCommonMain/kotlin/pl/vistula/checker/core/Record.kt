package pl.vistula.checker.core

import org.json.JSONException
import org.json.JSONObject

class Record @JvmOverloads constructor(
    @JvmField val id: String,
    @JvmField val kind: String,
    @JvmField val title: String,
    @JvmField val value: String,
    @JvmField val sourceTime: String,
    @JvmField val date: String,
    @JvmField val term: String = "",
    @JvmField val startTime: String = "",
    @JvmField val endTime: String = "",
    @JvmField val ects: Double = Double.NaN,
    metadata: JSONObject? = null,
) {
    @JvmField
    val metadata: JSONObject

    init {
        var meta: JSONObject = if (metadata != null) {
            try { JSONObject(metadata.toString()) } catch (e: Exception) { JSONObject() }
        } else {
            JSONObject()
        }
        try {
            if (term.isNotEmpty() && !meta.has("term")) meta.put("term", term)
            if (!ects.isNaN() && !meta.has("ects")) meta.put("ects", ects)
            if (startTime.isNotEmpty() && !meta.has("startTime")) meta.put("startTime", startTime)
            if (endTime.isNotEmpty() && !meta.has("endTime")) meta.put("endTime", endTime)
            if (date.isNotEmpty() && !meta.has("date")) meta.put("date", date)
        } catch (ignored: JSONException) {
        }
        this.metadata = meta
    }

    @Throws(JSONException::class)
    fun json(): JSONObject {
        val o = JSONObject().put("id", id).put("kind", kind).put("title", title)
            .put("value", value).put("sourceTime", sourceTime).put("date", date).put("term", term)
            .put("startTime", startTime).put("endTime", endTime)
        if (!ects.isNaN()) o.put("ects", ects)
        if (metadata.length() > 0) o.put("metadata", metadata)
        return o
    }

    fun same(other: Record): Boolean {
        val sameDate = date == other.date || (kind == "ocena" && (date.isEmpty() || other.date.isEmpty()))
        return value == other.value && title == other.title && sourceTime == other.sourceTime &&
            sameDate && (startTime.isEmpty() || other.startTime.isEmpty() || startTime == other.startTime) &&
            (endTime.isEmpty() || other.endTime.isEmpty() || endTime == other.endTime) &&
            ((ects.isNaN() && other.ects.isNaN()) || Math.abs(ects - other.ects) < 0.0001) &&
            sameAcademicMetadata(other)
    }

    private fun sameAcademicMetadata(other: Record): Boolean {
        if (kind != "ocena") return true
        for (key in arrayOf("passes", "countsIntoAverage", "valueDescription", "gradeTypeId", "dateAcquisition", "modificationAuthor")) {
            // Added optional fields on an older saved record are enrichment, not a grade change.
            if (!metadata.has(key) || !other.metadata.has(key)) continue
            if (metadata.opt(key) != other.metadata.opt(key)) return false
        }
        return true
    }

    fun optMeta(key: String): String = optMeta(key, "")

    fun optMeta(key: String, fallback: String): String = metadata.optString(key, fallback)

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun from(o: JSONObject): Record = Record(
            o.getString("id"), o.getString("kind"), o.getString("title"),
            o.getString("value"), o.getString("sourceTime"), o.getString("date"),
            o.optString("term", ""), o.optString("startTime", ""), o.optString("endTime", ""),
            if (o.has("ects")) o.optDouble("ects", Double.NaN) else Double.NaN,
            o.optJSONObject("metadata"),
        )
    }
}

