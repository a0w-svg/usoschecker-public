package pl.vistula.checker.core

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import org.json.JSONException
import org.json.JSONObject

class Change {
    @JvmField val kind: String
    @JvmField val title: String
    @JvmField val action: String
    @JvmField val before: String
    @JvmField val after: String
    @JvmField val sourceTime: String
    @JvmField val date: String
    @JvmField val beforeDate: String
    @JvmField val afterDate: String
    @JvmField val detectedAt: Long
    @JvmField val metadata: JSONObject
    @JvmField val beforeMeta: JSONObject
    @JvmField val afterMeta: JSONObject

    constructor(old: Record?, current: Record?, detectedAt: Long) {
        val r = current ?: old ?: throw IllegalArgumentException("Both old and current records cannot be null")
        this.kind = r.kind
        this.title = r.title
        this.action = if (old == null) "Dodano" else if (current == null) "Usunięto" else "Zmieniono"
        this.before = old?.value ?: ""
        this.after = current?.value ?: ""
        this.sourceTime = if (current != null && current.sourceTime.isNotEmpty()) current.sourceTime else (old?.sourceTime ?: "")
        var d = if (current != null && current.date.isNotEmpty()) current.date else (old?.date ?: "")
        if (d.isEmpty() && this.sourceTime.length >= 10) {
            d = this.sourceTime.substring(0, 10)
        }
        if (d.isEmpty()) {
            var da = r.metadata.optString("dateAcquisition", "")
            if (da.isEmpty()) da = r.metadata.optString("dateModified", "")
            if (da.length >= 10) {
                d = da.substring(0, 10)
            }
        }
        this.date = d

        val bDate = if (old == null) "" else (if (old.date.isNotEmpty()) old.date else (if (old.sourceTime.isNotEmpty()) old.sourceTime else old.optMeta("dateAcquisition")))
        this.beforeDate = bDate
        val aDate = if (current == null) "" else (if (current.date.isNotEmpty()) current.date else (if (current.sourceTime.isNotEmpty()) current.sourceTime else current.optMeta("dateAcquisition")))
        this.afterDate = aDate
        this.detectedAt = detectedAt

        this.beforeMeta = if (old != null) cloneJson(old.metadata) else JSONObject()
        this.afterMeta = if (current != null) cloneJson(current.metadata) else JSONObject()
        val meta = cloneJson(r.metadata)

        if (old != null) {
            if (old.startTime.isNotEmpty() && !this.beforeMeta.has("startTime")) try { this.beforeMeta.put("startTime", old.startTime) } catch (ignored: JSONException) {}
            if (old.endTime.isNotEmpty() && !this.beforeMeta.has("endTime")) try { this.beforeMeta.put("endTime", old.endTime) } catch (ignored: JSONException) {}
            if (!old.ects.isNaN() && !this.beforeMeta.has("ects")) try { this.beforeMeta.put("ects", old.ects) } catch (ignored: JSONException) {}
            if (old.term.isNotEmpty() && !this.beforeMeta.has("term")) try { this.beforeMeta.put("term", old.term) } catch (ignored: JSONException) {}
            if (this.beforeDate.isNotEmpty() && !this.beforeMeta.has("date")) try { this.beforeMeta.put("date", this.beforeDate) } catch (ignored: JSONException) {}
        }
        if (current != null) {
            if (current.startTime.isNotEmpty() && !this.afterMeta.has("startTime")) try { this.afterMeta.put("startTime", current.startTime) } catch (ignored: JSONException) {}
            if (current.endTime.isNotEmpty() && !this.afterMeta.has("endTime")) try { this.afterMeta.put("endTime", current.endTime) } catch (ignored: JSONException) {}
            if (!current.ects.isNaN() && !this.afterMeta.has("ects")) try { this.afterMeta.put("ects", current.ects) } catch (ignored: JSONException) {}
            if (current.term.isNotEmpty() && !this.afterMeta.has("term")) try { this.afterMeta.put("term", current.term) } catch (ignored: JSONException) {}
            if (this.afterDate.isNotEmpty() && !this.afterMeta.has("date")) try { this.afterMeta.put("date", this.afterDate) } catch (ignored: JSONException) {}
        }
        if (r.startTime.isNotEmpty() && !meta.has("startTime")) try { meta.put("startTime", r.startTime) } catch (ignored: JSONException) {}
        if (r.endTime.isNotEmpty() && !meta.has("endTime")) try { meta.put("endTime", r.endTime) } catch (ignored: JSONException) {}
        if (!r.ects.isNaN() && !meta.has("ects")) try { meta.put("ects", r.ects) } catch (ignored: JSONException) {}
        if (r.term.isNotEmpty() && !meta.has("term")) try { meta.put("term", r.term) } catch (ignored: JSONException) {}
        if (this.date.isNotEmpty() && !meta.has("date")) try { meta.put("date", this.date) } catch (ignored: JSONException) {}

        if (old != null && current != null) {
            var oldVal = old.metadata.optString("valueSymbol", "")
            if (oldVal.isEmpty() && old.value.isNotEmpty()) oldVal = old.value.split(" · ")[0].trim()
            var curVal = current.metadata.optString("valueSymbol", "")
            if (curVal.isEmpty() && current.value.isNotEmpty()) curVal = current.value.split(" · ")[0].trim()
            if ("ocena" == kind && oldVal.isNotEmpty() && curVal.isNotEmpty() && oldVal != curVal) {
                try { meta.put("diffGrade", "$oldVal → $curVal") } catch (ignored: JSONException) {}
            }
            val oldRoom = old.metadata.optString("room", "").trim()
            val curRoom = current.metadata.optString("room", "").trim()
            if (oldRoom != curRoom) {
                try { meta.put("diffRoom", changedText(oldRoom, curRoom)) } catch (ignored: JSONException) {}
            }
            val oldTime = if (old.startTime.isNotEmpty()) old.startTime else old.metadata.optString("startTime", "").trim()
            val curTime = if (current.startTime.isNotEmpty()) current.startTime else current.metadata.optString("startTime", "").trim()
            if (oldTime.isNotEmpty() && curTime.isNotEmpty()) {
                val oldEndTime = if (old.endTime.isNotEmpty()) old.endTime else old.metadata.optString("endTime", "").trim()
                val curEndTime = if (current.endTime.isNotEmpty()) current.endTime else current.metadata.optString("endTime", "").trim()
                val oldRange = (if (oldTime.length >= 16) oldTime.substring(11, 16) else oldTime) + "–" + (if (oldEndTime.length >= 16) oldEndTime.substring(11, 16) else oldEndTime)
                val curRange = (if (curTime.length >= 16) curTime.substring(11, 16) else curTime) + "–" + (if (curEndTime.length >= 16) curEndTime.substring(11, 16) else curEndTime)
                if (oldRange != curRange) {
                    try { meta.put("diffTime", "$oldRange → $curRange") } catch (ignored: JSONException) {}
                }
            }
            val oldLec = old.metadata.optString("lecturers", "").trim()
            val curLec = current.metadata.optString("lecturers", "").trim()
            if (oldLec != curLec) {
                try { meta.put("diffLecturers", changedText(oldLec, curLec)) } catch (ignored: JSONException) {}
            }
            val oldGroup = old.metadata.optString("groupNumber", "").trim()
            val curGroup = current.metadata.optString("groupNumber", "").trim()
            if (oldGroup != curGroup) {
                try { meta.put("diffGroup", changedText(oldGroup, curGroup)) } catch (ignored: JSONException) {}
            }
            val oldBld = old.metadata.optString("building", "").trim()
            val curBld = current.metadata.optString("building", "").trim()
            if (oldBld != curBld) {
                try { meta.put("diffBuilding", changedText(oldBld, curBld)) } catch (ignored: JSONException) {}
            }
            val oldType = old.metadata.optString("classType", "").trim()
            val curType = current.metadata.optString("classType", "").trim()
            if (oldType != curType) {
                try { meta.put("diffClassType", changedText(oldType, curType)) } catch (ignored: JSONException) {}
            }
            if (old.metadata.has("passes") && current.metadata.has("passes")) {
                val op = old.metadata.optBoolean("passes")
                val cp = current.metadata.optBoolean("passes")
                if (op != cp) {
                    try { meta.put("diffPasses", (if (op) "Zaliczono" else "Brak zaliczenia") + " → " + (if (cp) "Zaliczono" else "Brak zaliczenia")) } catch (ignored: JSONException) {}
                }
            }
            if (old.metadata.has("countsIntoAverage") && current.metadata.has("countsIntoAverage")) {
                val oc = old.metadata.optBoolean("countsIntoAverage")
                val cc = current.metadata.optBoolean("countsIntoAverage")
                if (oc != cc) {
                    try { meta.put("diffCountsIntoAverage", (if (oc) "Wliczana do średniej" else "Niewliczana do średniej") + " → " + (if (cc) "Wliczana do średniej" else "Niewliczana do średniej")) } catch (ignored: JSONException) {}
                }
            }
            val oldDesc = old.metadata.optString("valueDescription", "").trim()
            val curDesc = current.metadata.optString("valueDescription", "").trim()
            if (oldDesc != curDesc && (oldDesc.isNotEmpty() || curDesc.isNotEmpty())) {
                try { meta.put("diffValueDescription", (if (oldDesc.isEmpty()) "(brak)" else oldDesc) + " → " + (if (curDesc.isEmpty()) "(brak)" else curDesc)) } catch (ignored: JSONException) {}
            }
            val oldCom = old.metadata.optString("comment", "").trim()
            val curCom = current.metadata.optString("comment", "").trim()
            if (oldCom != curCom && (oldCom.isNotEmpty() || curCom.isNotEmpty())) {
                try { meta.put("diffComment", (if (oldCom.isEmpty()) "(brak)" else oldCom) + " → " + (if (curCom.isEmpty()) "(brak)" else curCom)) } catch (ignored: JSONException) {}
            }
            var od = old.metadata.optString("date", old.date).trim()
            if (od.isEmpty() && bDate.length >= 10) od = bDate.substring(0, 10)
            var cd = current.metadata.optString("date", current.date).trim()
            if (cd.isEmpty() && aDate.length >= 10) cd = aDate.substring(0, 10)
            if (od.isNotEmpty() && cd.isNotEmpty() && od != cd) {
                try { meta.put("diffDate", "$od → $cd") } catch (ignored: JSONException) {}
            }
            val oe = if (!old.ects.isNaN()) old.ects else old.metadata.optDouble("ects", Double.NaN)
            val ce = if (!current.ects.isNaN()) current.ects else current.metadata.optDouble("ects", Double.NaN)
            if (!oe.isNaN() && !ce.isNaN() && abs(oe - ce) > 0.001) {
                try { meta.put("diffEcts", String.format(Locale.forLanguageTag("pl-PL"), "%.1f → %.1f ECTS", oe, ce)) } catch (ignored: JSONException) {}
            }
        }
        if (this.date.isNotEmpty() && !meta.has("date")) {
            try { meta.put("date", this.date) } catch (ignored: JSONException) {}
        }
        if ("ocena" != kind) meta.remove("diffGrade")
        this.metadata = meta
    }

    @Throws(JSONException::class)
    private constructor(o: JSONObject) {
        kind = o.getString("kind")
        title = o.getString("title")
        action = o.getString("action")
        before = o.optString("before", "")
        after = o.optString("after", "")
        sourceTime = o.optString("sourceTime", "")
        beforeDate = o.optString("beforeDate", "")
        afterDate = o.optString("afterDate", "")
        detectedAt = o.optLong("detectedAt", 0L)
        val meta = o.optJSONObject("metadata")
        this.metadata = cloneJson(meta)
        if ("ocena" != kind) this.metadata.remove("diffGrade")
        var d = o.optString("date", "")
        if (d.isEmpty() && sourceTime.length >= 10) {
            d = sourceTime.substring(0, 10)
        }
        if (d.isEmpty() && this.metadata.has("date")) {
            d = this.metadata.optString("date", "")
        }
        this.date = d
        this.beforeMeta = o.optJSONObject("beforeMeta") ?: JSONObject()
        this.afterMeta = o.optJSONObject("afterMeta") ?: JSONObject()
    }

    @Throws(JSONException::class)
    fun json(): JSONObject {
        val o = JSONObject()
            .put("kind", kind)
            .put("title", title)
            .put("action", action)
            .put("before", before)
            .put("after", after)
            .put("sourceTime", sourceTime)
            .put("detectedAt", detectedAt)
        if (date.isNotEmpty()) o.put("date", date)
        if (beforeDate.isNotEmpty()) o.put("beforeDate", beforeDate)
        if (afterDate.isNotEmpty()) o.put("afterDate", afterDate)
        if (metadata.length() > 0) o.put("metadata", metadata)
        if (beforeMeta.length() > 0) o.put("beforeMeta", beforeMeta)
        if (afterMeta.length() > 0) o.put("afterMeta", afterMeta)
        return o
    }

    fun usosTimestamp(): Long {
        if (sourceTime.isNotEmpty()) {
            try {
                val dt = LocalDateTime.parse(sourceTime, UsosParser.TIME)
                return dt.atZone(ZoneId.of("Europe/Warsaw")).toInstant().toEpochMilli()
            } catch (ignored: Exception) {}
        }
        return 0L
    }

    fun effectiveTimestamp(): Long {
        val t = usosTimestamp()
        return if (t > 0) t else detectedAt
    }

    fun optMeta(key: String): String = metadata.optString(key, "")
    fun optBeforeMeta(key: String): String = beforeMeta.optString(key, "")
    fun optAfterMeta(key: String): String = afterMeta.optString(key, "")

    fun getPasses(): Boolean? = if (metadata.has("passes")) metadata.optBoolean("passes") else null
    fun getBeforePasses(): Boolean? = if (beforeMeta.has("passes")) beforeMeta.optBoolean("passes") else null
    fun getAfterPasses(): Boolean? = if (afterMeta.has("passes")) afterMeta.optBoolean("passes") else null

    fun getCountsIntoAverage(): Boolean? = if (metadata.has("countsIntoAverage")) metadata.optBoolean("countsIntoAverage") else null
    fun getBeforeCountsIntoAverage(): Boolean? = if (beforeMeta.has("countsIntoAverage")) beforeMeta.optBoolean("countsIntoAverage") else null
    fun getAfterCountsIntoAverage(): Boolean? = if (afterMeta.has("countsIntoAverage")) afterMeta.optBoolean("countsIntoAverage") else null

    fun getEcts(): Double = if (metadata.has("ects")) metadata.optDouble("ects", Double.NaN) else Double.NaN
    fun getBeforeEcts(): Double = if (beforeMeta.has("ects")) beforeMeta.optDouble("ects", Double.NaN) else Double.NaN
    fun getAfterEcts(): Double = if (afterMeta.has("ects")) afterMeta.optDouble("ects", Double.NaN) else Double.NaN

    fun getCourseId(): String = optMeta("courseId")
    fun getRoom(): String = optMeta("room")
    fun getLecturers(): String = optMeta("lecturers")
    fun getBuilding(): String = optMeta("building")
    fun getTerm(): String = optMeta("term")
    fun getClassType(): String = optMeta("classType")
    fun getGroupNumber(): String = optMeta("groupNumber")
    fun getValueDescription(): String = optMeta("valueDescription")
    fun getComment(): String = optMeta("comment")

    companion object {
        private fun changedText(before: String, after: String): String =
            (if (before.isEmpty()) "(brak)" else before) + " → " + (if (after.isEmpty()) "(brak)" else after)

        private fun cloneJson(o: JSONObject?): JSONObject {
            if (o == null) return JSONObject()
            return try {
                JSONObject(o.toString())
            } catch (e: JSONException) {
                JSONObject()
            }
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun from(o: JSONObject): Change = Change(o)
    }
}

