package pl.vistula.checker.core;

import java.util.*;

/** Descriptive, unweighted and ECTS-weighted statistics over numeric grade entries. */
public final class GradeSummary {
    public final int numeric, other;
    public final double average, min, max, median, sum;
    public final double ectsAverage, totalEcts, ectsSum;
    public final int ectsGradedCount;

    private GradeSummary(int numeric, int other, double sum, double min, double max, double median,
                         double ectsSum, double totalEcts, int ectsGradedCount) {
        this.numeric = numeric; this.other = other; this.sum = sum;
        this.average = numeric == 0 ? Double.NaN : sum / numeric;
        this.min = numeric == 0 ? Double.NaN : min; this.max = numeric == 0 ? Double.NaN : max;
        this.median = numeric == 0 ? Double.NaN : median;
        this.ectsSum = ectsSum;
        this.totalEcts = totalEcts;
        this.ectsGradedCount = ectsGradedCount;
        this.ectsAverage = totalEcts > 0 ? ectsSum / totalEcts : Double.NaN;
    }

    public static GradeSummary of(Collection<Record> rows) {
        int numeric = 0, other = 0; double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        List<Double> values = new ArrayList<>();
        Map<String, CourseEctsGrade> courseGrades = new LinkedHashMap<>();

        for (Record row : rows) {
            if (!row.kind.equals("ocena")) continue;
            Double value = GradeBrowser.number(row);
            if (value == null) { other++; continue; }
            numeric++; sum += value; min = Math.min(min, value); max = Math.max(max, value); values.add(value);

            if (!Double.isNaN(row.ects) && row.ects > 0 && !Boolean.FALSE.equals(row.metadata.opt("countsIntoAverage"))) {
                String courseKey = (row.optMeta("courseId").isEmpty() ? "title:" + row.title : "id:" + row.optMeta("courseId")) + "\u0000" + row.term;
                CourseEctsGrade existing = courseGrades.get(courseKey);
                int scopeRank = "course".equals(row.optMeta("gradeScope")) || row.metadata.optBoolean("alsoCourseProtocol") ? 1 : 0;
                // Descriptive estimate: prefer course protocols, then the highest visible entry in that scope.
                if (existing == null || scopeRank > existing.scopeRank || (scopeRank == existing.scopeRank && value > existing.grade)) {
                    courseGrades.put(courseKey, new CourseEctsGrade(value, row.ects, scopeRank));
                }
            }
        }
        Collections.sort(values);
        double median = 0;
        if (!values.isEmpty()) {
            int mid = values.size() / 2;
            median = values.size() % 2 == 1 ? values.get(mid) : (values.get(mid - 1) + values.get(mid)) / 2;
        }

        double ectsSum = 0;
        double totalEcts = 0;
        for (CourseEctsGrade cg : courseGrades.values()) {
            ectsSum += cg.grade * cg.ects;
            totalEcts += cg.ects;
        }

        return new GradeSummary(numeric, other, sum, min, max, median, ectsSum, totalEcts, courseGrades.size());
    }

    private static final class CourseEctsGrade {
        final double grade, ects;
        final int scopeRank;
        CourseEctsGrade(double grade, double ects, int scopeRank) { this.grade = grade; this.ects = ects; this.scopeRank = scopeRank; }
    }

    /**
     * Group rows by semester (term) and calculate GradeSummary for each semester.
     * Ordered anti-chronologically.
     */
    public static Map<String, GradeSummary> bySemester(Collection<Record> rows) {
        Map<String, List<Record>> groups = new TreeMap<>(Comparator.reverseOrder());
        for (Record r : rows) {
            if (!r.kind.equals("ocena")) continue;
            String term = r.term.isEmpty() ? "Brak semestru" : r.term;
            groups.computeIfAbsent(term, k -> new ArrayList<>()).add(r);
        }
        Map<String, GradeSummary> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Record>> entry : groups.entrySet()) {
            result.put(entry.getKey(), GradeSummary.of(entry.getValue()));
        }
        return result;
    }

    /**
     * Average that the next {@code count} numeric grades must have for the simple average to reach
     * {@code target}. Returns NaN when the input is invalid.
     */
    public double requiredAverage(double target, int count) {
        if (count <= 0 || Double.isNaN(target) || Double.isInfinite(target)) return Double.NaN;
        return (target * (numeric + count) - sum) / count;
    }

    /**
     * Grade required on a course with {@code courseEcts} points to reach target ECTS weighted average.
     */
    public double requiredEctsGrade(double targetAverage, double courseEcts) {
        if (courseEcts <= 0 || Double.isNaN(targetAverage) || Double.isInfinite(targetAverage)) return Double.NaN;
        if (Double.isNaN(ectsAverage) || totalEcts <= 0) return targetAverage;
        return (targetAverage * (totalEcts + courseEcts) - ectsSum) / courseEcts;
    }
}
