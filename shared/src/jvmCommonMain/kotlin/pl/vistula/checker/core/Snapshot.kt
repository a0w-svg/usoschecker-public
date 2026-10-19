package pl.vistula.checker.core

import java.util.TreeMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Snapshot(
    @JvmField val start: String,
    @JvmField val end: String
) {
    @JvmField val records: MutableMap<String, Record> = TreeMap()
    // Enrolled course editions are separate from grades and never participate in grade diffs.
    @JvmField val courses: MutableList<Record> = ArrayList()
    // The primary snapshot remains Polish for stable monitoring across UI language changes.
    @JvmField val localized: MutableMap<String, Snapshot> = LinkedHashMap()
    @JvmField val serviceErrors: MutableMap<String, String> = LinkedHashMap()
    @JvmField var payments: PaymentInfo? = null
    @JvmField val terms: MutableMap<String, AcademicTerm> = LinkedHashMap()
    @JvmField var thesis: ThesisInfo? = null
    @JvmField val tests: MutableList<CourseTestRecord> = ArrayList()
    @JvmField val news: MutableList<NewsArticle> = ArrayList()

    fun forLanguage(language: String?): Snapshot =
        localized.getOrDefault(ApiLanguage.normalize(language), this)

    fun add(r: Record) {
        if (records.putIfAbsent(r.id, r) != null) {
            throw IllegalArgumentException("USOS zwrócił powielony identyfikator danych.")
        }
    }

    fun findClassTypeForUnit(unitId: String?): String {
        if (unitId.isNullOrEmpty()) return ""
        for (r in records.values) {
            if ("plan" == r.kind && unitId == r.optMeta("unitId")) {
                val type = r.optMeta("classType")
                if (type.isNotEmpty()) return type
            }
        }
        return ""
    }

    fun covers(date: String): Boolean = date >= start && date < end

    @Throws(JSONException::class)
    fun json(): JSONObject {
        val rows = JSONArray()
        for (r in records.values) rows.put(r.json())
        val o = JSONObject().put("start", start).put("end", end).put("records", rows)
        val courseRows = JSONArray()
        for (course in courses) courseRows.put(course.json())
        o.put("courses", courseRows)
        payments?.let { o.put("payments", it.json()) }
        if (serviceErrors.isNotEmpty()) o.put("serviceErrors", JSONObject(serviceErrors as Map<*, *>))
        if (terms.isNotEmpty()) {
            val tObj = JSONObject()
            for ((k, v) in terms) tObj.put(k, v.json())
            o.put("terms", tObj)
        }
        val th = thesis
        if (th != null && th.hasThesis()) o.put("thesis", th.json())
        if (tests.isNotEmpty()) {
            val testArr = JSONArray()
            for (ctr in tests) testArr.put(ctr.json())
            o.put("tests", testArr)
        }
        if (news.isNotEmpty()) {
            val newsArr = JSONArray()
            for (na in news) newsArr.put(na.json())
            o.put("news", newsArr)
        }
        if (localized.isNotEmpty()) {
            val translations = JSONObject()
            for ((k, v) in localized) translations.put(k, v.json())
            o.put("localized", translations)
        }
        return o
    }

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun from(o: JSONObject): Snapshot {
            val s = Snapshot(o.getString("start"), o.getString("end"))
            val rows = o.getJSONArray("records")
            for (i in 0 until rows.length()) s.add(Record.from(rows.getJSONObject(i)))
            val courseRows = o.optJSONArray("courses")
            if (courseRows != null) {
                for (i in 0 until courseRows.length()) s.courses.add(Record.from(courseRows.getJSONObject(i)))
            }
            val errors = o.optJSONObject("serviceErrors")
            if (errors != null) {
                val keys = errors.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    s.serviceErrors[key] = errors.optString(key)
                }
            }
            if (o.has("payments")) s.payments = PaymentInfo.from(o.optJSONObject("payments"))
            if (o.has("terms")) {
                val tObj = o.optJSONObject("terms")
                if (tObj != null) {
                    val it = tObj.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        s.terms[k] = AcademicTerm.from(tObj.optJSONObject(k))
                    }
                }
            }
            if (o.has("thesis")) s.thesis = ThesisInfo.from(o.optJSONObject("thesis"))
            if (o.has("tests")) {
                val testArr = o.optJSONArray("tests")
                if (testArr != null) {
                    for (i in 0 until testArr.length()) {
                        s.tests.add(CourseTestRecord.from(testArr.optJSONObject(i)))
                    }
                }
            }
            if (o.has("news")) {
                val newsArr = o.optJSONArray("news")
                if (newsArr != null) {
                    for (i in 0 until newsArr.length()) {
                        s.news.add(NewsArticle.from(newsArr.optJSONObject(i)))
                    }
                }
            }
            val translations = o.optJSONObject("localized")
            if (translations != null) {
                for (language in arrayOf("en", "uk")) {
                    val translation = translations.optJSONObject(language)
                    if (translation != null) {
                        val flat = JSONObject(translation.toString())
                        flat.remove("localized")
                        s.localized[language] = from(flat)
                    }
                }
            }
            return s
        }
    }
}

