package pl.vistula.checker.core;

import java.util.Collection;

/** Descriptive, unweighted statistics over numeric grade entries. */
public final class GradeSummary {
    public final int numeric, other;
    public final double average, min, max;
    private GradeSummary(int numeric, int other, double sum, double min, double max) {
        this.numeric = numeric; this.other = other;
        this.average = numeric == 0 ? Double.NaN : sum / numeric;
        this.min = numeric == 0 ? Double.NaN : min; this.max = numeric == 0 ? Double.NaN : max;
    }
    public static GradeSummary of(Collection<Record> rows) {
        int numeric = 0, other = 0; double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Record row : rows) {
            if (!row.kind.equals("ocena")) continue;
            Double value = GradeBrowser.number(row);
            if (value == null) { other++; continue; }
            numeric++; sum += value; min = Math.min(min, value); max = Math.max(max, value);
        }
        return new GradeSummary(numeric, other, sum, min, max);
    }
}
