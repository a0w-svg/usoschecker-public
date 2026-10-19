package pl.vistula.checker.core;

import java.util.*;

/** Filters and groups grade entries without inferring academic pass/fail rules. */
public final class GradeBrowser {
    private GradeBrowser() {}
    public static List<Record> ungraded(Snapshot snapshot, String term, String type, String query) {
        List<Record> result = new ArrayList<>();
        if (!type.equals("all")) return result;
        for (Record course : snapshot.courses) {
            if (!course.term.equals(term)) continue;
            boolean hasGrade = snapshot.records.values().stream().anyMatch(grade -> grade.kind.equals("ocena") && grade.term.equals(term)
                && (!grade.optMeta("courseId").isEmpty() ? grade.optMeta("courseId").equals(course.optMeta("courseId")) : grade.title.equals(course.title)));
            if (!hasGrade && StudentSearch.matches(query, course.title, course.term, course.optMeta("courseId"))) result.add(course);
        }
        result.sort(Comparator.comparing((Record course) -> course.title.toLowerCase(Locale.ROOT)).thenComparing(course -> course.id));
        return result;
    }
    public static String symbol(Record row) { return row.optMeta("valueSymbol").isEmpty() ? row.value.split("·", 2)[0].trim() : row.optMeta("valueSymbol"); }
    public static Double number(Record row) {
        String value = symbol(row).replace(',', '.');
        if (!value.matches("[0-9]+(?:\\.[0-9]+)?")) return null;
        double number = Double.parseDouble(value);
        return Double.isFinite(number) ? number : null;
    }
    public static List<Record> select(Collection<Record> rows, String term, String type, String order, String query) {
        return select(rows, term, type, order, query, null);
    }
    public static List<Record> select(Collection<Record> rows, String term, String type, String order, String query, Snapshot snapshot) {
        List<Record> result = new ArrayList<>();
        for (Record row : rows) {
            if (!row.kind.equals("ocena") || (!term.isEmpty() && !term.equals(row.term))) continue;
            boolean numeric = number(row) != null;
            String cType = canonicalClassType(row, snapshot).toLowerCase(Locale.ROOT);
            if (type.equals("numeric") && !numeric) continue;
            if (type.equals("other") && numeric) continue;
            if (type.equals("retakes") && row.metadata.optInt("sessionNumber", 1) <= 1) continue;
            if (type.equals("labs") && !cType.contains("lab")) continue;
            if (type.equals("exercises") && !cType.contains("ćwicz") && !cType.contains("cwicz")) continue;
            if (StudentSearch.matches(query, row.title, row.value, row.term, classType(row, snapshot), row.optMeta("comment"), row.optMeta("courseId"))) result.add(row);
        }
        Comparator<Record> byTitle = Comparator.comparing(r -> r.title.toLowerCase(Locale.ROOT));
        Comparator<Record> byRecent = Comparator.comparing((Record r) -> r.sourceTime).reversed();
        Comparator<Record> byValue = Comparator.comparingDouble((Record r) -> number(r) == null ? Double.NEGATIVE_INFINITY : number(r)).reversed();
        Comparator<Record> selected = order.equals("recent") ? byRecent.thenComparing(byTitle)
            : order.equals("value") ? byValue.thenComparing(byTitle) : byTitle.thenComparing(byRecent);
        result.sort(selected.thenComparing(r -> r.term).thenComparing(r -> r.id));
        return result;
    }
    public static String canonicalClassType(Record row, Snapshot snapshot) {
        return row.optMeta("classTypeCanonical", classType(row, snapshot));
    }
    public static String classType(Record row) {
        return classType(row, null);
    }
    public static String classType(Record row, Snapshot snapshot) {
        String direct = row.optMeta("classType");
        boolean legacyInference = !row.optMeta("gradeScope").isEmpty() && row.optMeta("classTypeSource").isEmpty() && row.optMeta("classTypeId").isEmpty();
        if (!legacyInference && !direct.isEmpty() && !direct.equals("Laboratorium / Ćwiczenia") && !direct.equals("Wykład / Ocena końcowa")) return direct;

        String unitId = row.optMeta("unitId");
        if (!unitId.isEmpty() && snapshot != null) {
            String fromTimetable = snapshot.findClassTypeForUnit(unitId);
            if (!fromTimetable.isEmpty()) return fromTimetable;
        }

        String typeId = row.optMeta("classTypeId");
        if ("LAB".equalsIgnoreCase(typeId)) return "Laboratorium";
        if ("CW".equalsIgnoreCase(typeId) || "CWI".equalsIgnoreCase(typeId)) return "Ćwiczenia";
        if ("WYK".equalsIgnoreCase(typeId) || "W".equalsIgnoreCase(typeId)) return "Wykład";
        if ("PRO".equalsIgnoreCase(typeId) || "PRJ".equalsIgnoreCase(typeId)) return "Projekt";
        if ("KON".equalsIgnoreCase(typeId)) return "Konwersatorium";
        if ("SEM".equalsIgnoreCase(typeId)) return "Seminarium";
        if ("LEK".equalsIgnoreCase(typeId)) return "Lektorat";

        if (!unitId.isEmpty() || Boolean.parseBoolean(row.optMeta("isUnitGrade", "false")) || "unit".equals(row.optMeta("gradeScope"))) {
            return "Ocena z protokołu zajęć";
        }
        if ("course".equals(row.optMeta("gradeScope"))) return "Ocena z protokołu przedmiotu";
        return "Typ zajęć niepodany";
    }
    public static List<List<Record>> groups(List<Record> rows) {
        Map<String, List<Record>> result = new LinkedHashMap<>();
        for (Record row : rows) result.computeIfAbsent((row.optMeta("courseId").isEmpty() ? "title:" + row.title : "id:" + row.optMeta("courseId"))
            + "\u0000" + row.term, key -> new ArrayList<>()).add(row);
        return new ArrayList<>(result.values());
    }
}
