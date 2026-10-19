package pl.vistula.checker.core;

import org.json.*;

public final class CourseTestRecord {
    public final String id;
    public final String courseId;
    public final String courseName;
    public final String testName;
    public final double points;
    public final double maxPoints;
    public final String grade;
    public final String date;
    public final String term;

    public CourseTestRecord(String id, String courseId, String courseName, String testName, double points, double maxPoints, String grade, String date) {
        this(id, courseId, courseName, testName, points, maxPoints, grade, date, "");
    }
    public CourseTestRecord(String id, String courseId, String courseName, String testName, double points, double maxPoints, String grade, String date, String term) {
        this.term = term;
        this.id = id; this.courseId = courseId; this.courseName = courseName;
        this.testName = testName; this.points = points; this.maxPoints = maxPoints;
        this.grade = grade; this.date = date;
    }

    public double percentage() {
        return maxPoints > 0 ? (points / maxPoints) * 100.0 : Double.NaN;
    }

    public JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("courseId", courseId).put("courseName", courseName)
            .put("testName", testName).put("points", points).put("maxPoints", maxPoints)
            .put("grade", grade).put("date", date).put("term", term);
    }

    public static CourseTestRecord from(JSONObject o) {
        if (o == null) return new CourseTestRecord("", "", "", "", 0.0, 0.0, "", "");
        return new CourseTestRecord(o.optString("id", ""), o.optString("courseId", ""),
            o.optString("courseName", ""), o.optString("testName", ""),
            o.optDouble("points", 0.0), o.optDouble("maxPoints", 0.0),
            o.optString("grade", ""), o.optString("date", ""), o.optString("term", ""));
    }
}
