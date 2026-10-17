package pl.vistula.checker.core;

import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Local attendance grouped by semester, course and class format. */
public final class AttendanceTracker {
    public static final int LIMIT = 3;
    private AttendanceTracker() {}
    public static String key(String term, String course, String type) {
        return new JSONArray().put(term).put(course).put(type).toString();
    }
    public static String type(Record row) {
        String label = row.optMeta("classType").trim();
        String value = label.isEmpty() ? row.optMeta("classTypeId").trim() : label;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("w") || lower.equals("wyk") || lower.contains("wykład") || lower.contains("lecture")) return "Wykład";
        if (lower.equals("c") || lower.equals("cw") || lower.equals("cwi") || lower.contains("ćwicz") || lower.contains("exercise")) return "Ćwiczenia";
        if (lower.equals("l") || lower.equals("lab") || lower.contains("laborat")) return "Laboratorium";
        if (lower.equals("s") || lower.equals("sem") || lower.contains("seminar")) return "Seminarium";
        if (lower.equals("p") || lower.equals("pro") || lower.equals("prj") || lower.contains("projekt") || lower.contains("project")) return "Projekt";
        if (lower.equals("lek") || lower.contains("lektor") || lower.contains("language")) return "Lektorat";
        if (lower.equals("kon") || lower.contains("konwersat")) return "Konwersatorium";
        return value;
    }
    public static String course(Record row) {
        return row.optMeta("courseId").isEmpty() ? row.title : row.optMeta("courseId");
    }
    public static String term(Snapshot snapshot, Record row) {
        if (!row.term.isEmpty()) return row.term;
        LocalDate date = StudentServices.date(row.date);
        Set<String> matches = new HashSet<>();
        if (date != null) for (AcademicTerm term : snapshot.terms.values())
            if (term.isCurrent(date)) matches.add(term.id);
        Set<String> courseTerms = new HashSet<>();
        for (Record edition : snapshot.courses)
            if (course(edition).equals(course(row)) && !edition.term.isEmpty()) courseTerms.add(edition.term);
        if (matches.size() > 1) matches.retainAll(courseTerms);
        if (matches.size() == 1) return matches.iterator().next();
        // Only undated entries can use a unique course edition without university calendar dates.
        if (date == null && courseTerms.size() == 1) return courseTerms.iterator().next();
        return "";
    }
    public static JSONObject item(String name, String course, String term, String type) throws JSONException {
        return new JSONObject().put("name", name).put("courseId", course).put("term", term)
            .put("classType", type).put("absences", 0).put("limit", LIMIT).put("notes", "");
    }
    public static JSONObject reconcile(JSONObject saved, Snapshot snapshot) throws JSONException {
        JSONObject result = new JSONObject(saved.toString());
        // Keep undated legacy totals intact; users can assign them in the editor.
        for (Iterator<String> ids = result.keys(); ids.hasNext();) {
            String id = ids.next();
            JSONObject entry = result.optJSONObject(id);
            if (entry == null) continue;
            if (!entry.has("courseId")) entry.put("courseId", id);
            if (!entry.has("term")) entry.put("term", "");
            if (!entry.has("classType")) entry.put("classType", "");
            entry.put("limit", LIMIT);
        }
        if (snapshot != null) for (Record row : snapshot.records.values()) {
            if (!row.kind.equals("plan")) continue;
            String term = term(snapshot, row), type = type(row), course = course(row);
            String id = key(term, course, type);
            if (!result.has(id)) result.put(id, item(row.title, course, term, type));
        }
        return result;
    }
    public static String record(JSONObject saved, Snapshot snapshot, Record lesson) throws JSONException {
        String term = term(snapshot, lesson), type = type(lesson), course = course(lesson);
        String id = key(term, course, type);
        JSONObject entry = saved.optJSONObject(id);
        if (entry == null) { entry = item(lesson.title, course, term, type); saved.put(id, entry); }
        JSONArray lessons = entry.optJSONArray("lessons");
        if (lessons == null) lessons = new JSONArray();
        for (int i = 0; i < lessons.length(); i++) if (lesson.id.equals(lessons.optString(i))) return id;
        lessons.put(lesson.id);
        entry.put("lessons", lessons).put("absences", Math.max(0, entry.optInt("absences")) + 1).put("limit", LIMIT);
        String notes = entry.optString("notes");
        entry.put("notes", (notes.isEmpty() ? "" : notes + "\n") + "Opuszczono: " + lesson.date + " " + lesson.startTime);
        return id;
    }
    public static boolean exceeded(int count) { return count > LIMIT; }
    public static boolean warning(int count) { return count >= LIMIT - 1 && !exceeded(count); }
}
