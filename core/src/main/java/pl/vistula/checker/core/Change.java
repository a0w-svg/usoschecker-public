package pl.vistula.checker.core;

import java.time.*;
import java.util.Iterator;
import org.json.*;

public final class Change {
    public final String kind, title, action, before, after, sourceTime, date;
    public final String beforeDate, afterDate;
    public final long detectedAt;
    public final JSONObject metadata;
    public final JSONObject beforeMeta, afterMeta;

    public Change(Record old, Record current, long detectedAt) {
        Record r = current == null ? old : current;
        this.kind = r.kind; this.title = r.title;
        this.action = old == null ? "Dodano" : current == null ? "Usunięto" : "Zmieniono";
        this.before = old == null ? "" : old.value;
        this.after = current == null ? "" : current.value;
        this.beforeDate = old == null ? "" : (!old.sourceTime.isEmpty() ? old.sourceTime : old.date);
        this.afterDate = current == null ? "" : (!current.sourceTime.isEmpty() ? current.sourceTime : current.date);
        this.sourceTime = (current != null && !current.sourceTime.isEmpty()) ? current.sourceTime : (old != null ? old.sourceTime : "");
        this.date = (current != null && !current.date.isEmpty()) ? current.date : (old != null ? old.date : "");
        this.detectedAt = detectedAt;

        this.beforeMeta = old != null && old.metadata != null ? cloneJson(old.metadata) : new JSONObject();
        this.afterMeta = current != null && current.metadata != null ? cloneJson(current.metadata) : new JSONObject();

        JSONObject meta = cloneJson(r != null ? r.metadata : null);
        if (old != null && old.metadata != null && current != null && current.metadata != null) {
            String oldVal = old.metadata.optString("valueSymbol", "");
            if (oldVal.isEmpty() && !old.value.isEmpty()) oldVal = old.value.split(" · ")[0].trim();
            String curVal = current.metadata.optString("valueSymbol", "");
            if (curVal.isEmpty() && !current.value.isEmpty()) curVal = current.value.split(" · ")[0].trim();
            if (!oldVal.isEmpty() && !curVal.isEmpty() && !oldVal.equals(curVal)) {
                try { meta.put("diffGrade", oldVal + " → " + curVal); } catch (JSONException ignored) {}
            }
            String oldRoom = old.metadata.optString("room", "");
            String curRoom = current.metadata.optString("room", "");
            if (!oldRoom.isEmpty() && !curRoom.isEmpty() && !oldRoom.equals(curRoom)) {
                try { meta.put("diffRoom", oldRoom + " → " + curRoom); } catch (JSONException ignored) {}
            }
            String oldTime = old.metadata.optString("startTime", "");
            String curTime = current.metadata.optString("startTime", "");
            if (!oldTime.isEmpty() && !curTime.isEmpty() && !oldTime.equals(curTime)) {
                String oldRange = (oldTime.length() >= 16 ? oldTime.substring(11, 16) : oldTime) + "–" + (old.endTime.length() >= 16 ? old.endTime.substring(11, 16) : old.endTime);
                String curRange = (curTime.length() >= 16 ? curTime.substring(11, 16) : curTime) + "–" + (current.endTime.length() >= 16 ? current.endTime.substring(11, 16) : current.endTime);
                try { meta.put("diffTime", oldRange + " → " + curRange); } catch (JSONException ignored) {}
            }
            String oldLec = old.metadata.optString("lecturers", "");
            String curLec = current.metadata.optString("lecturers", "");
            if (!oldLec.isEmpty() && !curLec.isEmpty() && !oldLec.equals(curLec)) {
                try { meta.put("diffLecturers", oldLec + " → " + curLec); } catch (JSONException ignored) {}
            }
            String oldGroup = old.metadata.optString("groupNumber", "");
            String curGroup = current.metadata.optString("groupNumber", "");
            if (!oldGroup.isEmpty() && !curGroup.isEmpty() && !oldGroup.equals(curGroup)) {
                try { meta.put("diffGroup", oldGroup + " → " + curGroup); } catch (JSONException ignored) {}
            }
            String oldBld = old.metadata.optString("building", "");
            String curBld = current.metadata.optString("building", "");
            if (!oldBld.isEmpty() && !curBld.isEmpty() && !oldBld.equals(curBld)) {
                try { meta.put("diffBuilding", oldBld + " → " + curBld); } catch (JSONException ignored) {}
            }
            String oldType = old.metadata.optString("classType", "");
            String curType = current.metadata.optString("classType", "");
            if (!oldType.isEmpty() && !curType.isEmpty() && !oldType.equals(curType)) {
                try { meta.put("diffClassType", oldType + " → " + curType); } catch (JSONException ignored) {}
            }
            if (old.metadata.has("passes") && current.metadata.has("passes")) {
                boolean op = old.metadata.optBoolean("passes");
                boolean cp = current.metadata.optBoolean("passes");
                if (op != cp) {
                    try { meta.put("diffPasses", (op ? "Zaliczono" : "Brak zaliczenia") + " → " + (cp ? "Zaliczono" : "Brak zaliczenia")); } catch (JSONException ignored) {}
                }
            }
            String oldCom = old.metadata.optString("comment", "");
            String curCom = current.metadata.optString("comment", "");
            if (!oldCom.equals(curCom) && (!oldCom.isEmpty() || !curCom.isEmpty())) {
                try { meta.put("diffComment", (oldCom.isEmpty() ? "(brak)" : oldCom) + " → " + (curCom.isEmpty() ? "(brak)" : curCom)); } catch (JSONException ignored) {}
            }
            String od = old.metadata.optString("date", old.date);
            String cd = current.metadata.optString("date", current.date);
            if (!od.isEmpty() && !cd.isEmpty() && !od.equals(cd)) {
                try { meta.put("diffDate", od + " → " + cd); } catch (JSONException ignored) {}
            }
            if (old.metadata.has("ects") && current.metadata.has("ects")) {
                double oe = old.metadata.optDouble("ects", Double.NaN);
                double ce = current.metadata.optDouble("ects", Double.NaN);
                if (!Double.isNaN(oe) && !Double.isNaN(ce) && Math.abs(oe - ce) > 0.001) {
                    try { meta.put("diffEcts", String.format(java.util.Locale.forLanguageTag("pl-PL"), "%.1f → %.1f ECTS", oe, ce)); } catch (JSONException ignored) {}
                }
            }
        }
        this.metadata = meta;
    }

    private static JSONObject cloneJson(JSONObject o) {
        if (o == null) return new JSONObject();
        try {
            return new JSONObject(o.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private Change(JSONObject o) throws JSONException {
        kind = o.getString("kind"); title = o.getString("title"); action = o.getString("action");
        before = o.optString("before", ""); after = o.optString("after", "");
        sourceTime = o.optString("sourceTime", ""); date = o.optString("date", "");
        beforeDate = o.optString("beforeDate", ""); afterDate = o.optString("afterDate", "");
        detectedAt = o.optLong("detectedAt", 0L);
        JSONObject meta = o.optJSONObject("metadata");
        this.metadata = meta != null ? meta : new JSONObject();
        JSONObject bMeta = o.optJSONObject("beforeMeta");
        this.beforeMeta = bMeta != null ? bMeta : new JSONObject();
        JSONObject aMeta = o.optJSONObject("afterMeta");
        this.afterMeta = aMeta != null ? aMeta : new JSONObject();
    }

    public JSONObject json() throws JSONException {
        JSONObject o = new JSONObject().put("kind", kind).put("title", title).put("action", action)
            .put("before", before).put("after", after).put("sourceTime", sourceTime).put("detectedAt", detectedAt);
        if (!date.isEmpty()) o.put("date", date);
        if (!beforeDate.isEmpty()) o.put("beforeDate", beforeDate);
        if (!afterDate.isEmpty()) o.put("afterDate", afterDate);
        if (metadata != null && metadata.length() > 0) o.put("metadata", metadata);
        if (beforeMeta != null && beforeMeta.length() > 0) o.put("beforeMeta", beforeMeta);
        if (afterMeta != null && afterMeta.length() > 0) o.put("afterMeta", afterMeta);
        return o;
    }

    public static Change from(JSONObject o) throws JSONException { return new Change(o); }

    public long usosTimestamp() {
        if (sourceTime != null && !sourceTime.isEmpty()) {
            try {
                LocalDateTime dt = LocalDateTime.parse(sourceTime, UsosParser.TIME);
                return dt.atZone(ZoneId.of("Europe/Warsaw")).toInstant().toEpochMilli();
            } catch (Exception ignored) {}
        }
        return 0L;
    }

    public long effectiveTimestamp() {
        long t = usosTimestamp();
        return t > 0 ? t : detectedAt;
    }

    public String optMeta(String key) {
        return metadata != null ? metadata.optString(key, "") : "";
    }

    public String optBeforeMeta(String key) {
        return beforeMeta != null ? beforeMeta.optString(key, "") : "";
    }

    public String optAfterMeta(String key) {
        return afterMeta != null ? afterMeta.optString(key, "") : "";
    }

    public Boolean getPasses() {
        return metadata != null && metadata.has("passes") ? metadata.optBoolean("passes") : null;
    }

    public Boolean getBeforePasses() {
        return beforeMeta != null && beforeMeta.has("passes") ? beforeMeta.optBoolean("passes") : null;
    }

    public Boolean getAfterPasses() {
        return afterMeta != null && afterMeta.has("passes") ? afterMeta.optBoolean("passes") : null;
    }

    public Boolean getCountsIntoAverage() {
        return metadata != null && metadata.has("countsIntoAverage") ? metadata.optBoolean("countsIntoAverage") : null;
    }

    public Boolean getBeforeCountsIntoAverage() {
        return beforeMeta != null && beforeMeta.has("countsIntoAverage") ? beforeMeta.optBoolean("countsIntoAverage") : null;
    }

    public Boolean getAfterCountsIntoAverage() {
        return afterMeta != null && afterMeta.has("countsIntoAverage") ? afterMeta.optBoolean("countsIntoAverage") : null;
    }

    public double getEcts() {
        return metadata != null && metadata.has("ects") ? metadata.optDouble("ects", Double.NaN) : Double.NaN;
    }

    public double getBeforeEcts() {
        return beforeMeta != null && beforeMeta.has("ects") ? beforeMeta.optDouble("ects", Double.NaN) : Double.NaN;
    }

    public double getAfterEcts() {
        return afterMeta != null && afterMeta.has("ects") ? afterMeta.optDouble("ects", Double.NaN) : Double.NaN;
    }
}



