package pl.vistula.checker.core;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Validates response shape before a snapshot can be committed. */
public final class UsosParser {
    private UsosParser() {}
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static String language(JSONObject o) {
        String pl = o.optString("pl", "");
        if (!pl.isEmpty()) return pl;
        String en = o.optString("en", "");
        if (!en.isEmpty()) return en;
        Iterator<String> keys = o.keys();
        return keys.hasNext() ? o.optString(keys.next(), "") : "";
    }
    public static Map<String, String> courseNames(JSONObject courses) throws JSONException {
        Map<String, String> names = new TreeMap<>();
        for (String term : sortedKeys(courses)) {
            JSONArray rows = courses.getJSONArray(term);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject c = rows.getJSONObject(i);
                names.put(c.getString("course_id"), language(c.getJSONObject("course_name")));
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
            for (String course : sortedKeys(courses)) {
                JSONObject edition = courses.getJSONObject(course);
                readExams(s, edition.getJSONArray("course_grades"), course, names);
                JSONObject units = edition.getJSONObject("course_units_grades");
                for (String unit : sortedKeys(units)) readExams(s, units.getJSONArray(unit), course, names);
            }
        }
    }
    private static void readExams(Snapshot s, JSONArray exams, String course, Map<String, String> names) throws JSONException {
        for (int i = 0; i < exams.length(); i++) {
            JSONObject sessions = exams.getJSONObject(i);
            for (String session : sortedKeys(sessions)) {
                if (sessions.isNull(session)) continue;
                JSONObject g = sessions.getJSONObject(session);
                String exam = String.valueOf(g.get("exam_id"));
                String number = String.valueOf(g.get("exam_session_number"));
                String symbol = g.getString("value_symbol");
                String modified = g.getString("date_modified");
                LocalDateTime.parse(modified, TIME);
                String comment = g.isNull("comment") ? "" : g.getString("comment");
                String value = symbol + " · termin " + number + (comment.isEmpty() ? "" : "\n" + comment);
                Record r = new Record("grade:" + exam + ":" + number, "ocena", names.getOrDefault(course, course), value, modified, "");
                // The same exam can be referenced by a course and its unit.
                Record existing = s.records.get(r.id);
                if (existing == null) s.add(r);
                else if (!existing.same(r)) throw new IllegalArgumentException("Sprzeczne dane ocen w USOS.");
            }
        }
    }
    public static void timetable(Snapshot s, JSONArray activities) throws JSONException {
        for (int i = 0; i < activities.length(); i++) {
            JSONObject a = activities.getJSONObject(i);
            String type = a.getString("type");
            String start = a.getString("start_time"), end = a.getString("end_time");
            LocalDateTime parsedStart = LocalDateTime.parse(start, TIME);
            if (!LocalDateTime.parse(end, TIME).isAfter(parsedStart))
                throw new IllegalArgumentException("Nieprawidłowy czas zajęć w USOS.");
            String date = parsedStart.toLocalDate().toString();
            if (!s.covers(date)) throw new IllegalArgumentException("Zajęcia poza zakresem odpowiedzi USOS.");
            String name = language(a.getJSONObject("name"));
            String stable;
            if (type.equals("classgroup2") && !a.isNull("sm_id")) stable = "sm:" + a.get("sm_id");
            else if (type.equals("classgroup")) stable = "cg:" + a.get("unit_id") + ":" + a.get("group_number") + ":" + a.get("cgwm_id") + ":" + date;
            else stable = type + ":" + a.optString("unit_id", "") + ":" + a.optString("group_number", "") + ":" + name + ":" + start;
            String room = a.optString("room_number", "");
            String building = a.optJSONObject("building_name") == null ? "" : language(a.getJSONObject("building_name"));
            String classType = a.optJSONObject("classtype_name") == null ? "" : language(a.getJSONObject("classtype_name"));
            JSONArray lecturers = a.optJSONArray("lecturer_ids");
            List<String> ids = new ArrayList<>();
            if (lecturers != null) for (int j = 0; j < lecturers.length(); j++) ids.add(String.valueOf(lecturers.get(j)));
            Collections.sort(ids);
            String value = start.substring(0, 16) + " – " + end.substring(11, 16)
                + (classType.isEmpty() ? "" : "\n" + classType)
                + (room.isEmpty() ? "" : "\nSala " + room)
                + (building.isEmpty() ? "" : " · " + building)
                + (ids.isEmpty() ? "" : "\nProwadzący (ID): " + String.join(", ", ids))
                + (a.optString("frequency", "").equals("other") || a.optString("frequency", "").equals("once") ? "\nTermin orientacyjny w USOS" : "");
            s.add(new Record("plan:" + stable, "plan", name, value, "", date));
        }
    }
}
