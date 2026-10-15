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
        this.sourceTime = (current != null && !current.sourceTime.isEmpty()) ? current.sourceTime : (old != null ? old.sourceTime : "");
        String d = (current != null && !current.date.isEmpty()) ? current.date : (old != null ? old.date : "");
        if (d.isEmpty()) {
            if (!this.sourceTime.isEmpty() && this.sourceTime.length() >= 10) {
                d = this.sourceTime.substring(0, 10);
            }
        }
        if (d.isEmpty() && r != null && r.metadata != null) {
            String da = r.metadata.optString("dateAcquisition", "");
            if (da.isEmpty()) da = r.metadata.optString("dateModified", "");
            if (!da.isEmpty() && da.length() >= 10) {
                d = da.substring(0, 10);
            }
        }
        this.date = d;

        String bDate = old == null ? "" : (!old.date.isEmpty() ? old.date : (!old.sourceTime.isEmpty() ? old.sourceTime : old.optMeta("dateAcquisition")));
        this.beforeDate = bDate;
        String aDate = current == null ? "" : (!current.date.isEmpty() ? current.date : (!current.sourceTime.isEmpty() ? current.sourceTime : current.optMeta("dateAcquisition")));
        this.afterDate = aDate;
        this.detectedAt = detectedAt;

        this.beforeMeta = old != null && old.metadata != null ? cloneJson(old.metadata) : new JSONObject();
        this.afterMeta = current != null && current.metadata != null ? cloneJson(current.metadata) : new JSONObject();
        JSONObject meta = cloneJson(r != null ? r.metadata : null);

        if (old != null) {
            if (!old.startTime.isEmpty() && !this.beforeMeta.has("startTime")) try { this.beforeMeta.put("startTime", old.startTime); } catch (JSONException ignored) {}
            if (!old.endTime.isEmpty() && !this.beforeMeta.has("endTime")) try { this.beforeMeta.put("endTime", old.endTime); } catch (JSONException ignored) {}
            if (!Double.isNaN(old.ects) && !this.beforeMeta.has("ects")) try { this.beforeMeta.put("ects", old.ects); } catch (JSONException ignored) {}
            if (!old.term.isEmpty() && !this.beforeMeta.has("term")) try { this.beforeMeta.put("term", old.term); } catch (JSONException ignored) {}
            if (!this.beforeDate.isEmpty() && !this.beforeMeta.has("date")) try { this.beforeMeta.put("date", this.beforeDate); } catch (JSONException ignored) {}
        }
        if (current != null) {
            if (!current.startTime.isEmpty() && !this.afterMeta.has("startTime")) try { this.afterMeta.put("startTime", current.startTime); } catch (JSONException ignored) {}
            if (!current.endTime.isEmpty() && !this.afterMeta.has("endTime")) try { this.afterMeta.put("endTime", current.endTime); } catch (JSONException ignored) {}
            if (!Double.isNaN(current.ects) && !this.afterMeta.has("ects")) try { this.afterMeta.put("ects", current.ects); } catch (JSONException ignored) {}
            if (!current.term.isEmpty() && !this.afterMeta.has("term")) try { this.afterMeta.put("term", current.term); } catch (JSONException ignored) {}
            if (!this.afterDate.isEmpty() && !this.afterMeta.has("date")) try { this.afterMeta.put("date", this.afterDate); } catch (JSONException ignored) {}
        }
        if (r != null) {
            if (!r.startTime.isEmpty() && !meta.has("startTime")) try { meta.put("startTime", r.startTime); } catch (JSONException ignored) {}
            if (!r.endTime.isEmpty() && !meta.has("endTime")) try { meta.put("endTime", r.endTime); } catch (JSONException ignored) {}
            if (!Double.isNaN(r.ects) && !meta.has("ects")) try { meta.put("ects", r.ects); } catch (JSONException ignored) {}
            if (!r.term.isEmpty() && !meta.has("term")) try { meta.put("term", r.term); } catch (JSONException ignored) {}
            if (!this.date.isEmpty() && !meta.has("date")) try { meta.put("date", this.date); } catch (JSONException ignored) {}
        }

        if (old != null && old.metadata != null && current != null && current.metadata != null) {
            String oldVal = old.metadata.optString("valueSymbol", "");
            if (oldVal.isEmpty() && !old.value.isEmpty()) oldVal = old.value.split(" · ")[0].trim();
            String curVal = current.metadata.optString("valueSymbol", "");
            if (curVal.isEmpty() && !current.value.isEmpty()) curVal = current.value.split(" · ")[0].trim();
            if ("ocena".equals(kind) && !oldVal.isEmpty() && !curVal.isEmpty() && !oldVal.equals(curVal)) {
                try { meta.put("diffGrade", oldVal + " → " + curVal); } catch (JSONException ignored) {}
            }
            String oldRoom = old.metadata.optString("room", "").trim();
            String curRoom = current.metadata.optString("room", "").trim();
            if (!oldRoom.equals(curRoom)) {
                try { meta.put("diffRoom", changedText(oldRoom, curRoom)); } catch (JSONException ignored) {}
            }
            String oldTime = !old.startTime.isEmpty() ? old.startTime : old.metadata.optString("startTime", "").trim();
            String curTime = !current.startTime.isEmpty() ? current.startTime : current.metadata.optString("startTime", "").trim();
            if (!oldTime.isEmpty() && !curTime.isEmpty()) {
                String oldEndTime = !old.endTime.isEmpty() ? old.endTime : old.metadata.optString("endTime", "").trim();
                String curEndTime = !current.endTime.isEmpty() ? current.endTime : current.metadata.optString("endTime", "").trim();
                String oldRange = (oldTime.length() >= 16 ? oldTime.substring(11, 16) : oldTime) + "–" + (oldEndTime.length() >= 16 ? oldEndTime.substring(11, 16) : oldEndTime);
                String curRange = (curTime.length() >= 16 ? curTime.substring(11, 16) : curTime) + "–" + (curEndTime.length() >= 16 ? curEndTime.substring(11, 16) : curEndTime);
                if (!oldRange.equals(curRange)) {
                    try { meta.put("diffTime", oldRange + " → " + curRange); } catch (JSONException ignored) {}
                }
            }
            String oldLec = old.metadata.optString("lecturers", "").trim();
            String curLec = current.metadata.optString("lecturers", "").trim();
            if (!oldLec.equals(curLec)) {
                try { meta.put("diffLecturers", changedText(oldLec, curLec)); } catch (JSONException ignored) {}
            }
            String oldGroup = old.metadata.optString("groupNumber", "").trim();
            String curGroup = current.metadata.optString("groupNumber", "").trim();
            if (!oldGroup.equals(curGroup)) {
                try { meta.put("diffGroup", changedText(oldGroup, curGroup)); } catch (JSONException ignored) {}
            }
            String oldBld = old.metadata.optString("building", "").trim();
            String curBld = current.metadata.optString("building", "").trim();
            if (!oldBld.equals(curBld)) {
                try { meta.put("diffBuilding", changedText(oldBld, curBld)); } catch (JSONException ignored) {}
            }
            String oldType = old.metadata.optString("classType", "").trim();
            String curType = current.metadata.optString("classType", "").trim();
            if (!oldType.equals(curType)) {
                try { meta.put("diffClassType", changedText(oldType, curType)); } catch (JSONException ignored) {}
            }
            if (old.metadata.has("passes") && current.metadata.has("passes")) {
                boolean op = old.metadata.optBoolean("passes");
                boolean cp = current.metadata.optBoolean("passes");
                if (op != cp) {
                    try { meta.put("diffPasses", (op ? "Zaliczono" : "Brak zaliczenia") + " → " + (cp ? "Zaliczono" : "Brak zaliczenia")); } catch (JSONException ignored) {}
                }
            }
            if (old.metadata.has("countsIntoAverage") && current.metadata.has("countsIntoAverage")) {
                boolean oc = old.metadata.optBoolean("countsIntoAverage");
                boolean cc = current.metadata.optBoolean("countsIntoAverage");
                if (oc != cc) {
                    try { meta.put("diffCountsIntoAverage", (oc ? "Wliczana do średniej" : "Niewliczana do średniej") + " → " + (cc ? "Wliczana do średniej" : "Niewliczana do średniej")); } catch (JSONException ignored) {}
                }
            }
            String oldDesc = old.metadata.optString("valueDescription", "").trim();
            String curDesc = current.metadata.optString("valueDescription", "").trim();
            if (!oldDesc.equals(curDesc) && (!oldDesc.isEmpty() || !curDesc.isEmpty())) {
                try { meta.put("diffValueDescription", (oldDesc.isEmpty() ? "(brak)" : oldDesc) + " → " + (curDesc.isEmpty() ? "(brak)" : curDesc)); } catch (JSONException ignored) {}
            }
            String oldCom = old.metadata.optString("comment", "").trim();
            String curCom = current.metadata.optString("comment", "").trim();
            if (!oldCom.equals(curCom) && (!oldCom.isEmpty() || !curCom.isEmpty())) {
                try { meta.put("diffComment", (oldCom.isEmpty() ? "(brak)" : oldCom) + " → " + (curCom.isEmpty() ? "(brak)" : curCom)); } catch (JSONException ignored) {}
            }
            String od = old.metadata.optString("date", old.date).trim();
            if (od.isEmpty() && bDate.length() >= 10) od = bDate.substring(0, 10);
            String cd = current.metadata.optString("date", current.date).trim();
            if (cd.isEmpty() && aDate.length() >= 10) cd = aDate.substring(0, 10);
            if (!od.isEmpty() && !cd.isEmpty() && !od.equals(cd)) {
                try { meta.put("diffDate", od + " → " + cd); } catch (JSONException ignored) {}
            }
            double oe = !Double.isNaN(old.ects) ? old.ects : old.metadata.optDouble("ects", Double.NaN);
            double ce = !Double.isNaN(current.ects) ? current.ects : current.metadata.optDouble("ects", Double.NaN);
            if (!Double.isNaN(oe) && !Double.isNaN(ce) && Math.abs(oe - ce) > 0.001) {
                try { meta.put("diffEcts", String.format(java.util.Locale.forLanguageTag("pl-PL"), "%.1f → %.1f ECTS", oe, ce)); } catch (JSONException ignored) {}
            }
        }
        if (!this.date.isEmpty() && !meta.has("date")) {
            try { meta.put("date", this.date); } catch (JSONException ignored) {}
        }
        if (!"ocena".equals(kind)) meta.remove("diffGrade");
        this.metadata = meta;
    }

    private static String changedText(String before, String after) {
        return (before.isEmpty() ? "(brak)" : before) + " → " + (after.isEmpty() ? "(brak)" : after);
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
        sourceTime = o.optString("sourceTime", "");
        beforeDate = o.optString("beforeDate", ""); afterDate = o.optString("afterDate", "");
        detectedAt = o.optLong("detectedAt", 0L);
        JSONObject meta = o.optJSONObject("metadata");
        this.metadata = cloneJson(meta);
        if (!"ocena".equals(kind)) this.metadata.remove("diffGrade");
        String d = o.optString("date", "");
        if (d.isEmpty() && !sourceTime.isEmpty() && sourceTime.length() >= 10) {
            d = sourceTime.substring(0, 10);
        }
        if (d.isEmpty() && this.metadata.has("date")) {
            d = this.metadata.optString("date", "");
        }
        this.date = d;
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

    public String getCourseId() { return optMeta("courseId"); }
    public String getRoom() { return optMeta("room"); }
    public String getLecturers() { return optMeta("lecturers"); }
    public String getBuilding() { return optMeta("building"); }
    public String getTerm() { return optMeta("term"); }
    public String getClassType() { return optMeta("classType"); }
    public String getGroupNumber() { return optMeta("groupNumber"); }
    public String getValueDescription() { return optMeta("valueDescription"); }
    public String getComment() { return optMeta("comment"); }
}



