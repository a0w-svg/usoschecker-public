package pl.vistula.checker.core;

import java.util.*;
import org.json.*;

/** Local task operations preserve legacy entries and never mutate their source. */
public final class TaskTools {
    private TaskTools() {}
    public static int priority(JSONObject task) { return Math.max(0, Math.min(2, task.optInt("priority", 1))); }
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
