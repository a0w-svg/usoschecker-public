package pl.vistula.checker.core;

import java.util.*;
import org.json.*;

public final class Snapshot {
    public final Map<String, Record> records = new TreeMap<>();
    public final String start, end;
    public PaymentInfo payments;
    public final Map<String, AcademicTerm> terms = new LinkedHashMap<>();
    public ThesisInfo thesis;
    public final List<CourseTestRecord> tests = new ArrayList<>();
    public final List<NewsArticle> news = new ArrayList<>();

    public Snapshot(String start, String end) { this.start = start; this.end = end; }
    public void add(Record r) {
        if (records.putIfAbsent(r.id, r) != null)
            throw new IllegalArgumentException("USOS zwrócił powielony identyfikator danych.");
    }
    public String findClassTypeForUnit(String unitId) {
        if (unitId == null || unitId.isEmpty()) return "";
        for (Record r : records.values()) {
            if ("plan".equals(r.kind) && unitId.equals(r.optMeta("unitId"))) {
                String type = r.optMeta("classType");
                if (!type.isEmpty()) return type;
            }
        }
        return "";
    }
    public boolean covers(String date) { return date.compareTo(start) >= 0 && date.compareTo(end) < 0; }
    public JSONObject json() throws JSONException {
        JSONArray rows = new JSONArray();
        for (Record r : records.values()) rows.put(r.json());
        JSONObject o = new JSONObject().put("start", start).put("end", end).put("records", rows);
        if (payments != null) o.put("payments", payments.json());
        if (terms != null && !terms.isEmpty()) {
            JSONObject tObj = new JSONObject();
            for (Map.Entry<String, AcademicTerm> e : terms.entrySet()) tObj.put(e.getKey(), e.getValue().json());
            o.put("terms", tObj);
        }
        if (thesis != null && thesis.hasThesis()) o.put("thesis", thesis.json());
        if (tests != null && !tests.isEmpty()) {
            JSONArray testArr = new JSONArray();
            for (CourseTestRecord ctr : tests) testArr.put(ctr.json());
            o.put("tests", testArr);
        }
        if (!news.isEmpty()) {
            JSONArray newsArr = new JSONArray();
            for (NewsArticle na : news) newsArr.put(na.json());
            o.put("news", newsArr);
        }
        return o;
    }
    public static Snapshot from(JSONObject o) throws JSONException {
        Snapshot s = new Snapshot(o.getString("start"), o.getString("end"));
        JSONArray rows = o.getJSONArray("records");
        for (int i = 0; i < rows.length(); i++) s.add(Record.from(rows.getJSONObject(i)));
        if (o.has("payments")) s.payments = PaymentInfo.from(o.optJSONObject("payments"));
        if (o.has("terms")) {
            JSONObject tObj = o.optJSONObject("terms");
            if (tObj != null) {
                for (Iterator<String> it = tObj.keys(); it.hasNext(); ) {
                    String k = it.next();
                    s.terms.put(k, AcademicTerm.from(tObj.optJSONObject(k)));
                }
            }
        }
        if (o.has("thesis")) s.thesis = ThesisInfo.from(o.optJSONObject("thesis"));
        if (o.has("tests")) {
            JSONArray testArr = o.optJSONArray("tests");
            if (testArr != null) {
                for (int i = 0; i < testArr.length(); i++) {
                    s.tests.add(CourseTestRecord.from(testArr.optJSONObject(i)));
                }
            }
        }
        if (o.has("news")) {
            JSONArray newsArr = o.optJSONArray("news");
            if (newsArr != null) {
                for (int i = 0; i < newsArr.length(); i++) {
                    s.news.add(NewsArticle.from(newsArr.optJSONObject(i)));
                }
            }
        }
        return s;
    }
}
