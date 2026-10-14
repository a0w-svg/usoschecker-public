package pl.vistula.checker.core;

import org.json.JSONObject;
import org.json.JSONException;

public final class Record {
    public final String id, kind, title, value, sourceTime, date, term, startTime, endTime;
    public final double ects;
    public final JSONObject metadata;

    public Record(String id, String kind, String title, String value, String sourceTime, String date) {
        this(id, kind, title, value, sourceTime, date, "");
    }
    public Record(String id, String kind, String title, String value, String sourceTime, String date, String term) {
        this(id, kind, title, value, sourceTime, date, term, "", "");
    }
    public Record(String id, String kind, String title, String value, String sourceTime, String date, String term, String startTime, String endTime) {
        this(id, kind, title, value, sourceTime, date, term, startTime, endTime, Double.NaN);
    }
    public Record(String id, String kind, String title, String value, String sourceTime, String date, String term, String startTime, String endTime, double ects) {
        this(id, kind, title, value, sourceTime, date, term, startTime, endTime, ects, new JSONObject());
    }
    public Record(String id, String kind, String title, String value, String sourceTime, String date, String term, String startTime, String endTime, double ects, JSONObject metadata) {
        this.startTime = startTime; this.endTime = endTime;
        this.id = id; this.kind = kind; this.title = title; this.value = value;
        this.sourceTime = sourceTime; this.date = date; this.term = term;
        this.ects = ects;
        JSONObject meta;
        if (metadata != null) {
            try { meta = new JSONObject(metadata.toString()); } catch (Exception e) { meta = new JSONObject(); }
        } else {
            meta = new JSONObject();
        }
        try {
            if (!term.isEmpty() && !meta.has("term")) meta.put("term", term);
            if (!Double.isNaN(ects) && !meta.has("ects")) meta.put("ects", ects);
            if (!startTime.isEmpty() && !meta.has("startTime")) meta.put("startTime", startTime);
            if (!endTime.isEmpty() && !meta.has("endTime")) meta.put("endTime", endTime);
            if (!date.isEmpty() && !meta.has("date")) meta.put("date", date);
        } catch (JSONException ignored) {}
        this.metadata = meta;
    }
    public JSONObject json() throws JSONException {
        JSONObject o = new JSONObject().put("id", id).put("kind", kind).put("title", title)
            .put("value", value).put("sourceTime", sourceTime).put("date", date).put("term", term).put("startTime", startTime).put("endTime", endTime);
        if (!Double.isNaN(ects)) o.put("ects", ects);
        if (metadata != null && metadata.length() > 0) o.put("metadata", metadata);
        return o;
    }
    public static Record from(JSONObject o) throws JSONException {
        return new Record(o.getString("id"), o.getString("kind"), o.getString("title"),
            o.getString("value"), o.getString("sourceTime"), o.getString("date"),
            o.optString("term", ""), o.optString("startTime", ""), o.optString("endTime", ""),
            o.has("ects") ? o.optDouble("ects", Double.NaN) : Double.NaN,
            o.optJSONObject("metadata"));
    }
    public boolean same(Record other) {
        return value.equals(other.value) && title.equals(other.title) && sourceTime.equals(other.sourceTime)
            && date.equals(other.date) && (startTime.isEmpty() || other.startTime.isEmpty() || startTime.equals(other.startTime))
            && (endTime.isEmpty() || other.endTime.isEmpty() || endTime.equals(other.endTime))
            && ((Double.isNaN(ects) && Double.isNaN(other.ects)) || Math.abs(ects - other.ects) < 0.0001);
    }
    public String optMeta(String key) {
        return optMeta(key, "");
    }
    public String optMeta(String key, String fallback) {
        return metadata != null ? metadata.optString(key, fallback) : fallback;
    }
}

