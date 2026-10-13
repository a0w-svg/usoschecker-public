package pl.vistula.checker.core;

import java.util.*;
import org.json.*;

public final class Snapshot {
    public final Map<String, Record> records = new TreeMap<>();
    public final String start, end;
    public Snapshot(String start, String end) { this.start = start; this.end = end; }
    public void add(Record r) {
        if (records.putIfAbsent(r.id, r) != null)
            throw new IllegalArgumentException("USOS zwrócił powielony identyfikator danych.");
    }
    public boolean covers(String date) { return date.compareTo(start) >= 0 && date.compareTo(end) < 0; }
    public JSONObject json() throws JSONException {
        JSONArray rows = new JSONArray();
        for (Record r : records.values()) rows.put(r.json());
        return new JSONObject().put("start", start).put("end", end).put("records", rows);
    }
    public static Snapshot from(JSONObject o) throws JSONException {
        Snapshot s = new Snapshot(o.getString("start"), o.getString("end"));
        JSONArray rows = o.getJSONArray("records");
        for (int i = 0; i < rows.length(); i++) s.add(Record.from(rows.getJSONObject(i)));
        return s;
    }
}
