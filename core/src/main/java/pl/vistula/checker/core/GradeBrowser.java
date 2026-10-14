package pl.vistula.checker.core;

import java.util.*;

/** Filters and groups grade entries without inferring academic pass/fail rules. */
public final class GradeBrowser {
    private GradeBrowser() {}
    public static String symbol(Record row) { return row.value.split("·", 2)[0].trim(); }
    public static Double number(Record row) {
        String value = symbol(row).replace(',', '.');
        if (!value.matches("[0-9]+(?:\\.[0-9]+)?")) return null;
        double number = Double.parseDouble(value);
        return Double.isFinite(number) ? number : null;
    }
    public static List<Record> select(Collection<Record> rows, String term, String type, String order, String query) {
        List<Record> result = new ArrayList<>();
        for (Record row : rows) {
            if (!row.kind.equals("ocena") || (!term.isEmpty() && !term.equals(row.term))) continue;
            boolean numeric = number(row) != null;
            if ((type.equals("numeric") && !numeric) || (type.equals("other") && numeric)) continue;
            if (StudentSearch.matches(query, row.title, row.value, row.term)) result.add(row);
        }
        Comparator<Record> byTitle = Comparator.comparing(r -> r.title.toLowerCase(Locale.ROOT));
        Comparator<Record> byRecent = Comparator.comparing((Record r) -> r.sourceTime).reversed();
        Comparator<Record> byValue = Comparator.comparingDouble((Record r) -> number(r) == null ? Double.NEGATIVE_INFINITY : number(r)).reversed();
        Comparator<Record> selected = order.equals("recent") ? byRecent.thenComparing(byTitle)
            : order.equals("value") ? byValue.thenComparing(byTitle) : byTitle.thenComparing(byRecent);
        result.sort(selected.thenComparing(r -> r.term).thenComparing(r -> r.id));
        return result;
    }
    public static List<List<Record>> groups(List<Record> rows) {
        Map<String, List<Record>> result = new LinkedHashMap<>();
        for (Record row : rows) result.computeIfAbsent(row.title + "\u0000" + row.term, key -> new ArrayList<>()).add(row);
        return new ArrayList<>(result.values());
    }
}
