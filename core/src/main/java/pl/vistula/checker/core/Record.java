package pl.vistula.checker.core;

import org.json.JSONObject;
import org.json.JSONException;

public final class Record {
    public final String id, kind, title, value, sourceTime, date;
    public Record(String id, String kind, String title, String value, String sourceTime, String date) {
        this.id = id; this.kind = kind; this.title = title; this.value = value;
        this.sourceTime = sourceTime; this.date = date;
    }
    public JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("kind", kind).put("title", title)
            .put("value", value).put("sourceTime", sourceTime).put("date", date);
    }
    public static Record from(JSONObject o) throws JSONException {
        return new Record(o.getString("id"), o.getString("kind"), o.getString("title"),
            o.getString("value"), o.getString("sourceTime"), o.getString("date"));
    }
    public boolean same(Record other) {
        return value.equals(other.value) && title.equals(other.title) && sourceTime.equals(other.sourceTime);
    }
}
