package pl.vistula.checker.core;

import org.json.*;

public final class Change {
    public final String kind, title, action, before, after, sourceTime;
    public final long detectedAt;
    public Change(Record old, Record current, long detectedAt) {
        Record r = current == null ? old : current;
        this.kind = r.kind; this.title = r.title;
        this.action = old == null ? "Dodano" : current == null ? "Usunięto" : "Zmieniono";
        this.before = old == null ? "" : old.value;
        this.after = current == null ? "" : current.value;
        this.sourceTime = current == null ? "" : current.sourceTime;
        this.detectedAt = detectedAt;
    }
    private Change(JSONObject o) throws JSONException {
        kind = o.getString("kind"); title = o.getString("title"); action = o.getString("action");
        before = o.getString("before"); after = o.getString("after");
        sourceTime = o.getString("sourceTime"); detectedAt = o.getLong("detectedAt");
    }
    public JSONObject json() throws JSONException {
        return new JSONObject().put("kind", kind).put("title", title).put("action", action)
            .put("before", before).put("after", after).put("sourceTime", sourceTime).put("detectedAt", detectedAt);
    }
    public static Change from(JSONObject o) throws JSONException { return new Change(o); }
}
