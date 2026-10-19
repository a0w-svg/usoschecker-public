package pl.vistula.checker.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.json.JSONException
import org.json.JSONObject

class AcademicTerm(
    @JvmField val id: String,
    @JvmField val name: String,
    @JvmField val startDate: String,
    @JvmField val endDate: String,
    @JvmField val finishDate: String,
) {
    fun isCurrent(today: LocalDate): Boolean {
        if (startDate.isEmpty() || endDate.isEmpty()) return false
        return try {
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            !today.isBefore(start) && !today.isAfter(end)
        } catch (e: Exception) {
            false
        }
    }

    fun daysRemaining(today: LocalDate): Long {
        if (endDate.isEmpty()) return -1
        return try {
            ChronoUnit.DAYS.between(today, LocalDate.parse(endDate))
        } catch (e: Exception) {
            -1
        }
    }

    @Throws(JSONException::class)
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("startDate", startDate)
        .put("endDate", endDate).put("finishDate", finishDate)

    companion object {
        @JvmStatic
        fun from(o: JSONObject?): AcademicTerm {
            if (o == null) return AcademicTerm("", "", "", "", "")
            return AcademicTerm(
                o.optString("id", ""), o.optString("name", ""),
                o.optString("startDate", ""), o.optString("endDate", ""), o.optString("finishDate", ""),
            )
        }
    }
}

