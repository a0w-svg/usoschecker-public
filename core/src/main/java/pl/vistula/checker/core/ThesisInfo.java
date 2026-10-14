package pl.vistula.checker.core;

import org.json.*;

public final class ThesisInfo {
    public final String title;
    public final String status;
    public final String supervisors;
    public final String reviewers;

    public ThesisInfo(String title, String status, String supervisors, String reviewers) {
        this.title = title; this.status = status; this.supervisors = supervisors; this.reviewers = reviewers;
    }

    public boolean hasThesis() {
        return title != null && !title.trim().isEmpty();
    }

    public JSONObject json() throws JSONException {
        return new JSONObject().put("title", title).put("status", status)
            .put("supervisors", supervisors).put("reviewers", reviewers);
    }

    public static ThesisInfo from(JSONObject o) {
        if (o == null) return new ThesisInfo("", "", "", "");
        return new ThesisInfo(o.optString("title", ""), o.optString("status", ""),
            o.optString("supervisors", ""), o.optString("reviewers", ""));
    }
}
