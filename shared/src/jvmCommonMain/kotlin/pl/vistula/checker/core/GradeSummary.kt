package pl.vistula.checker.core

import java.util.TreeMap
import kotlin.math.max
import kotlin.math.min

/** Descriptive, unweighted and ECTS-weighted statistics over numeric grade entries. */
class GradeSummary private constructor(
    @JvmField val numeric: Int,
    @JvmField val other: Int,
    @JvmField val sum: Double,
    min: Double,
    max: Double,
    median: Double,
    @JvmField val ectsSum: Double,
    @JvmField val totalEcts: Double,
    @JvmField val ectsGradedCount: Int
) {
    @JvmField val average: Double = if (numeric == 0) Double.NaN else sum / numeric
    @JvmField val min: Double = if (numeric == 0) Double.NaN else min
    @JvmField val max: Double = if (numeric == 0) Double.NaN else max
    @JvmField val median: Double = if (numeric == 0) Double.NaN else median
    @JvmField val ectsAverage: Double = if (totalEcts > 0) ectsSum / totalEcts else Double.NaN

    private class CourseEctsGrade(val grade: Double, val ects: Double, val scopeRank: Int)

    /**
     * Average that the next `count` numeric grades must have for the simple average to reach
     * `target`. Returns NaN when the input is invalid.
     */
    fun requiredAverage(target: Double, count: Int): Double {
        if (count <= 0 || target.isNaN() || target.isInfinite()) return Double.NaN
        return (target * (numeric + count) - sum) / count
    }

    /**
     * Grade required on a course with `courseEcts` points to reach target ECTS weighted average.
     */
    fun requiredEctsGrade(targetAverage: Double, courseEcts: Double): Double {
        if (courseEcts <= 0 || targetAverage.isNaN() || targetAverage.isInfinite()) return Double.NaN
        if (ectsAverage.isNaN() || totalEcts <= 0) return targetAverage
        return (targetAverage * (totalEcts + courseEcts) - ectsSum) / courseEcts
    }

    companion object {
        @JvmStatic
        fun of(rows: Collection<Record>): GradeSummary {
            var numeric = 0
            var other = 0
            var sum = 0.0
            var minVal = Double.POSITIVE_INFINITY
            var maxVal = Double.NEGATIVE_INFINITY
            val values = ArrayList<Double>()
            val courseGrades = LinkedHashMap<String, CourseEctsGrade>()

            for (row in rows) {
                if (row.kind != "ocena") continue
                val value = GradeBrowser.number(row)
                if (value == null) {
                    other++
                    continue
                }
                numeric++
                sum += value
                minVal = min(minVal, value)
                maxVal = max(maxVal, value)
                values.add(value)

                if (!row.ects.isNaN() && row.ects > 0 && row.metadata.opt("countsIntoAverage") != java.lang.Boolean.FALSE) {
                    val courseKey = (if (row.optMeta("courseId").isEmpty()) "title:${row.title}" else "id:${row.optMeta("courseId")}") + "\u0000" + row.term
                    val existing = courseGrades[courseKey]
                    val scopeRank = if ("course" == row.optMeta("gradeScope") || row.metadata.optBoolean("alsoCourseProtocol")) 1 else 0
                    // Descriptive estimate: prefer course protocols, then the highest visible entry in that scope.
                    if (existing == null || scopeRank > existing.scopeRank || (scopeRank == existing.scopeRank && value > existing.grade)) {
                        courseGrades[courseKey] = CourseEctsGrade(value, row.ects, scopeRank)
                    }
                }
            }
            values.sort()
            var median = 0.0
            if (values.isNotEmpty()) {
                val mid = values.size / 2
                median = if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2.0
            }

            var ectsSum = 0.0
            var totalEcts = 0.0
            for (cg in courseGrades.values) {
                ectsSum += cg.grade * cg.ects
                totalEcts += cg.ects
            }

            return GradeSummary(numeric, other, sum, minVal, maxVal, median, ectsSum, totalEcts, courseGrades.size)
        }

        /**
         * Group rows by semester (term) and calculate GradeSummary for each semester.
         * Ordered anti-chronologically.
         */
        @JvmStatic
        fun bySemester(rows: Collection<Record>): Map<String, GradeSummary> {
            val groups = TreeMap<String, MutableList<Record>>(Comparator.reverseOrder())
            for (r in rows) {
                if (r.kind != "ocena") continue
                val term = if (r.term.isEmpty()) "Brak semestru" else r.term
                groups.computeIfAbsent(term) { ArrayList() }.add(r)
            }
            val result = LinkedHashMap<String, GradeSummary>()
            for ((k, v) in groups) {
                result[k] = of(v)
            }
            return result
        }
    }
}

