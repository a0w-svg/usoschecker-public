package pl.vistula.checker.core;

import java.time.*;
import java.util.*;

/** Semester sections use university dates; term identifiers are never guessed as dates. */
public final class GradeSemesters {
    private GradeSemesters() {}
    private static LocalDate start(Snapshot snapshot, String id) {
        try { return LocalDate.parse(snapshot.terms.get(id).startDate); }
        catch (Exception ignored) { return LocalDate.MIN; }
    }
    public static String current(Snapshot snapshot, LocalDate today) {
        return snapshot.terms.entrySet().stream().filter(entry -> !entry.getKey().isEmpty() && entry.getValue().isCurrent(today))
            .sorted(Comparator.<Map.Entry<String, AcademicTerm>, LocalDate>comparing(entry -> start(snapshot, entry.getKey())).reversed()
                .thenComparing(entry -> entry.getValue().endDate).thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public static List<String> ordered(Snapshot snapshot, LocalDate today) {
        Set<String> ids = new HashSet<>();
        for (String id : snapshot.terms.keySet()) if (!id.isEmpty()) ids.add(id);
        for (Record course : snapshot.courses) ids.add(course.term);
        for (Record record : snapshot.records.values())
            if (record.kind.equals("ocena") || (record.kind.equals("plan") && !record.term.isEmpty())) ids.add(record.term);
        String current = current(snapshot, today);
        if (current != null) ids.add(current);
        List<String> result = new ArrayList<>(ids);
        result.sort(Comparator.<String, Boolean>comparing(id -> !id.equals(current))
            .thenComparing(String::isEmpty)
            .thenComparing(Comparator.comparing((String id) -> start(snapshot, id)).reversed())
            .thenComparing(Comparator.reverseOrder()));
        return result;
    }
    public static List<Record> inTerm(List<Record> rows, String term) {
        List<Record> result = new ArrayList<>();
        for (Record row : rows) if (row.term.equals(term)) result.add(row);
        return result;
    }
    public static String testTerm(Snapshot snapshot, CourseTestRecord test) {
        Set<String> terms = new HashSet<>();
        for (Record row : snapshot.records.values()) {
            if (!row.kind.equals("ocena")) continue;
            boolean match = !test.courseId.isEmpty() ? test.courseId.equalsIgnoreCase(row.optMeta("courseId"))
                : !test.courseName.isEmpty() && test.courseName.equalsIgnoreCase(row.title);
            if (match) terms.add(row.term);
        }
        return terms.size() == 1 ? terms.iterator().next() : null;
    }
}
