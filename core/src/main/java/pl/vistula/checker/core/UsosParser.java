package pl.vistula.checker.core;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Validates response shape before a snapshot can be committed. */
public final class UsosParser {
    private UsosParser() {}
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static String optionalText(JSONObject o, String field) {
        return o.isNull(field) ? "" : o.optString(field, "");
    }
    public static String language(JSONObject o) {
        String pl = optionalText(o, "pl");
        if (!pl.isEmpty()) return pl;
        String en = optionalText(o, "en");
        if (!en.isEmpty()) return en;
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) { String value = optionalText(o, keys.next()); if (!value.isEmpty()) return value; }
        return "";
    }
    public static Map<String, String> courseNames(JSONObject courses) {
        Map<String, String> names = new TreeMap<>();
        for (String term : sortedKeys(courses)) {
            JSONArray rows = courses.optJSONArray(term);
            if (rows == null) continue;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject c = rows.optJSONObject(i);
                if (c == null) continue;
                String id = c.optString("course_id");
                if (id.isEmpty() || c.optJSONObject("course_name") == null) continue;
                names.put(id, language(c.optJSONObject("course_name")));
            }
        }
        return names;
    }
    public static List<String> sortedKeys(JSONObject object) {
        List<String> keys = new ArrayList<>();
        object.keys().forEachRemaining(keys::add);
        Collections.sort(keys); return keys;
    }
    public static void grades(Snapshot s, JSONObject grades, Map<String, String> names) throws JSONException {
        for (String term : sortedKeys(grades)) {
            JSONObject courses = grades.getJSONObject(term);
            if (courses == null) continue;
            for (String course : sortedKeys(courses)) {
                JSONObject edition = courses.getJSONObject(course);
                if (edition == null) continue;
                if (!edition.has("course_grades") || !edition.has("course_units_grades")) throw new JSONException("Niepełne dane ocen.");
                if (!edition.isNull("course_grades"))
                    readExams(s, edition.getJSONArray("course_grades"), course, names, term);
                JSONObject units = edition.isNull("course_units_grades") ? null : edition.getJSONObject("course_units_grades");
                if (units != null) {
                    for (String unit : sortedKeys(units)) {
                        if (!units.isNull(unit))
                            readExams(s, units.getJSONArray(unit), course, names, term);
                    }
                }
            }
        }
    }
    private static void readExams(Snapshot s, JSONArray exams, String course, Map<String, String> names, String term) throws JSONException {
        for (int i = 0; i < exams.length(); i++) {
            JSONObject sessions = exams.getJSONObject(i);
            if (sessions == null) continue;
            for (String session : sortedKeys(sessions)) {
                if (sessions.isNull(session)) continue;
                JSONObject g = sessions.getJSONObject(session);
                if (g == null) continue;
                String exam = g.get("exam_id").toString();
                String number = g.get("exam_session_number").toString();
                String symbol = g.getString("value_symbol");
                String modified = optionalText(g, "date_modified");
                if (!modified.isEmpty()) {
                    LocalDateTime.parse(modified, TIME);
                }
                String comment = optionalText(g, "comment");
                String value = symbol + " · termin " + number + (comment.isEmpty() ? "" : "\n" + comment);
                Record r = new Record("grade:" + exam + ":" + number, "ocena", names.getOrDefault(course, course), value, modified, "", term);
                // The same exam can be referenced by a course and its unit.
                Record existing = s.records.get(r.id);
                if (existing == null) s.add(r);
                else if (!existing.same(r)) throw new IllegalArgumentException("Sprzeczne dane oceny: " + r.id);
            }
        }
    }
    public static Set<String> lecturerIds(JSONArray activities) throws JSONException {
        Set<String> ids = new TreeSet<>();
        for (int i = 0; i < activities.length(); i++) {
            JSONArray lecturers = activities.getJSONObject(i).optJSONArray("lecturer_ids");
            if (lecturers == null) continue;
            for (int j = 0; j < lecturers.length(); j++) if (!lecturers.isNull(j)) {
                String id = lecturers.get(j).toString();
                if (!id.matches("[0-9]+")) throw new JSONException("Nieprawidłowy identyfikator prowadzącego.");
                ids.add(id);
            }
        }
        return ids;
    }
    public static Map<String, String> lecturerNames(JSONObject users) throws JSONException {
        Map<String, String> names = new TreeMap<>();
        for (String id : sortedKeys(users)) {
            if (users.isNull(id)) continue;
            JSONObject person = users.getJSONObject(id);
            String first = optionalText(person, "first_name").trim();
            String last = optionalText(person, "last_name").trim();
            String name = (first + " " + last).trim();
            if (!name.isEmpty()) names.put(id, name);
        }
        return names;
    }
    public static void timetable(Snapshot s, JSONArray activities) {
        timetable(s, activities, java.util.Collections.emptyMap());
    }
    public static void timetable(Snapshot s, JSONArray activities, Map<String, String> lecturerNames) {
        for (int i = 0; i < activities.length(); i++) {
            JSONObject a = activities.optJSONObject(i);
            if (a == null) throw new IllegalArgumentException("Niepełne dane planu.");
            String type = a.optString("type", "");
            String start = a.optString("start_time", ""), end = a.optString("end_time", "");
            if (start.isEmpty() || end.isEmpty()) throw new IllegalArgumentException("Brak godzin zajęć.");
            try {
                LocalDateTime parsedStart = LocalDateTime.parse(start, TIME);
                if (!LocalDateTime.parse(end, TIME).isAfter(parsedStart)) throw new IllegalArgumentException("Nieprawidłowe godziny zajęć.");
                String date = parsedStart.toLocalDate().toString();
                if (!s.covers(date)) continue;

                String genericName = a.optJSONObject("name") == null ? "Brak nazwy" : language(a.getJSONObject("name"));
                String courseName = a.optJSONObject("course_name") == null ? genericName : language(a.getJSONObject("course_name"));

                String stable;
                if (type.equals("classgroup2") && !a.isNull("sm_id")) stable = "sm:" + a.optString("sm_id", "");
                else if (type.equals("classgroup")) stable = "cg:" + a.optString("unit_id", "") + ":" + a.optString("group_number", "") + ":" + a.optString("cgwm_id", "") + ":" + date;
                else stable = type + ":" + a.optString("unit_id", "") + ":" + a.optString("group_number", "") + ":" + courseName + ":" + start;
                String room = optionalText(a, "room_number");
                String building = a.optJSONObject("building_name") == null ? "" : language(a.getJSONObject("building_name"));
                String classType = a.optJSONObject("classtype_name") == null ? "" : language(a.getJSONObject("classtype_name"));
                JSONArray lecturers = a.optJSONArray("lecturer_ids");
                List<String> ids = new ArrayList<>();
                if (lecturers != null) {
                    for (int j = 0; j < lecturers.length(); j++) {
                        if (lecturers.isNull(j)) continue;
                        String lid = String.valueOf(lecturers.opt(j));
                        ids.add(lecturerNames.getOrDefault(lid, "ID: " + lid));
                    }
                }
                Collections.sort(ids);
                String value = start.substring(11, 16) + " – " + end.substring(11, 16)
                    + (classType.isEmpty() ? "" : "\n" + classType)
                    + (room.isEmpty() ? "" : "\nSala " + room)
                    + (building.isEmpty() ? "" : " · " + building)
                    + (ids.isEmpty() ? "" : "\nProwadzący: " + String.join(", ", ids))
                    + (a.optString("frequency", "").equals("other") || a.optString("frequency", "").equals("once") ? "\nTermin orientacyjny" : "");
                s.add(new Record("plan:" + stable, "plan", courseName, value, "", date, "", start, end));
            } catch (Exception e) { throw new IllegalArgumentException("Nieprawidłowe dane planu.", e); }
        }
    }
}
