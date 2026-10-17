package pl.vistula.checker.core;

import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.json.*;

/** Local task operations preserve legacy entries and never mutate their source. */
public final class TaskTools {
    private TaskTools() {}
    public static JSONArray save(JSONArray source, JSONObject replacement) throws JSONException {
        String id = replacement.getString("id");
        if (id.isEmpty() || replacement.optString("title").trim().isEmpty()
                || replacement.optString("title").length() > 200 || replacement.optString("notes").length() > 2000)
            throw new IllegalArgumentException("Nieprawidłowa treść zadania.");
        String platonUrl = PlatonLinks.normalize(replacement.optString("platonUrl"));
        JSONObject copy = new JSONObject(replacement.toString());
        if (platonUrl.isEmpty()) copy.remove("platonUrl"); else copy.put("platonUrl", platonUrl);
        JSONArray result = source == null ? new JSONArray() : new JSONArray(source.toString());
        for (int i = 0; i < result.length(); i++) {
            JSONObject entry = result.optJSONObject(i);
            if (entry != null && id.equals(entry.optString("id"))) {
                result.put(i, copy);
                return result;
            }
        }
        if (result.length() >= 200) throw new IllegalStateException("Limit 200 zadań. Usuń inny wpis, aby dodać zadanie.");
        return result.put(copy);
    }
    public static int priority(JSONObject task) { return Math.max(0, Math.min(2, task.optInt("priority", 1))); }
    public static LocalDate dueDate(JSONObject task) {
        try { return LocalDate.parse(task.optString("due")); }
        catch (DateTimeParseException e) { return null; }
    }
    public static boolean matches(JSONObject task, String filter, String query, LocalDate today) {
        if (!StudentSearch.matches(query == null ? "" : query, task.optString("title"), task.optString("notes"), task.optString("due"),
                task.optString("platonUrl"), task.optString("platonUrl").isEmpty() ? "" : "Platon")) return false;
        boolean done = task.optBoolean("done");
        LocalDate due = dueDate(task);
        switch (filter) {
            case "platon": return PlatonLinks.isTaskLink(task.optString("platonUrl"));
            case "open": return !done;
            case "done": return done;
            case "today": return !done && today.equals(due);
            case "overdue": return !done && due != null && due.isBefore(today);
            case "week": return !done && due != null && !due.isBefore(today) && due.isBefore(today.plusDays(7));
            default: return true;
        }
    }
    public static List<JSONObject> sorted(JSONArray source, String order) throws JSONException {
        List<JSONObject> rows = new ArrayList<>();
        if (source != null) for (int i = 0; i < source.length(); i++) rows.add(source.getJSONObject(i));
        Comparator<JSONObject> byDue = Comparator.comparing(t -> t.optString("due"));
        Comparator<JSONObject> byPriority = Comparator.comparingInt(TaskTools::priority).reversed();
        Comparator<JSONObject> byTitle = Comparator.comparing(t -> t.optString("title").toLowerCase(Locale.ROOT));
        Comparator<JSONObject> selected = "priority".equals(order) ? byPriority.thenComparing(byDue)
            : "title".equals(order) ? byTitle.thenComparing(byDue) : byDue.thenComparing(byPriority);
        rows.sort(Comparator.comparing((JSONObject t) -> t.optBoolean("done")).thenComparing(selected).thenComparing(t -> t.optString("id")));
        return rows;
    }
    public static JSONArray restore(JSONArray source, JSONObject deleted) throws JSONException {
        JSONArray result = source == null ? new JSONArray() : new JSONArray(source.toString());
        for (int i = 0; i < result.length(); i++) if (result.getJSONObject(i).optString("id").equals(deleted.getString("id"))) return result;
        if (result.length() >= 200) throw new IllegalStateException("Limit 200 zadań. Usuń inny wpis, aby przywrócić zadanie.");
        result.put(new JSONObject(deleted.toString()));
        return result;
    }
}
