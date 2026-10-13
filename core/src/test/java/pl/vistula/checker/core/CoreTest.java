package pl.vistula.checker.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import org.json.*;

public class CoreTest {
    private Record grade(String value, String time) {
        return new Record("grade:1:1", "ocena", "Finanse", value, time, "");
    }
    private Record lesson(String id, String date, String value) {
        return new Record(id, "plan", "Statystyka", value, "", date);
    }
    @Test public void oauthMatchesPublishedHmacSha1Example() throws Exception {
        // OAuth 1.0 specification section 9.1.1, photos.example.net.
        Map<String, String> p = new HashMap<>();
        p.put("file", "vacation.jpg"); p.put("size", "original");
        p.put("oauth_consumer_key", "dpf43f3p2l4k3l03"); p.put("oauth_token", "nnch734d00sl2jdk");
        p.put("oauth_nonce", "kllo9940pd9333jh"); p.put("oauth_timestamp", "1191242096");
        p.put("oauth_signature_method", "HMAC-SHA1"); p.put("oauth_version", "1.0");
        assertEquals("tR3+Ty81lMeYAr/Fid0kMTYa/WM=", OAuth.signature("GET", "http://photos.example.net/photos", p,
            "kd94hf93k423kf44", "pfkkdhi9sl3r4s00"));
    }
    @Test public void oauthSortsByEncodedKeysAndEscapesUnicode() {
        assertEquals("%C4%85%20%2B%2A~", OAuth.encode("ą +*~"));
        assertEquals("%C4%85=a&z=b", OAuth.query(Map.of("z", "b", "ą", "a")));
    }
    @Test public void oauthFormDecodesEscapedTokenAndIgnoresExtraFields() {
        Map<String, String> result = OAuth.form("oauth_token=a%2Bb&oauth_token_secret=x%3Dy&extra=true");
        assertEquals("a+b", result.get("oauth_token")); assertEquals("x=y", result.get("oauth_token_secret"));
    }
    @Test(expected = IllegalArgumentException.class) public void emptyTokenIsRejected() { OAuth.form("oauth_token=&oauth_token_secret=x"); }
    @Test public void baselineAndUnchangedDownloadDoNotNotify() {
        Snapshot s = new Snapshot("2026-10-13", "2026-11-10"); s.add(grade("4", "2026-10-13 10:00:00"));
        assertTrue(Diff.between(null, s, 1, "2026-10-13").isEmpty());
        assertTrue(Diff.between(s, s, 1, "2026-10-13").isEmpty());
    }
    @Test public void gradeAndRoomChangeCarryBeforeAfterAndBothTimes() {
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot(old.start, old.end);
        old.add(grade("4", "2026-10-13 10:00:00")); now.add(grade("5", "2026-10-13 10:30:00"));
        old.add(lesson("plan:1", "2026-10-14", "09:00 Sala 204")); now.add(lesson("plan:1", "2026-10-14", "10:00 Sala 315"));
        List<Change> diff = Diff.between(old, now, 42, "2026-10-13");
        assertEquals(2, diff.size()); assertEquals("4", diff.get(0).before); assertEquals("5", diff.get(0).after);
        assertEquals(42, diff.get(0).detectedAt); assertEquals("2026-10-13 10:30:00", diff.get(0).sourceTime);
        assertEquals("Zmieniono", diff.get(1).action); assertEquals("", diff.get(1).sourceTime);
    }
    @Test public void rollingWindowDoesNotInventScheduleChanges() {
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot("2026-10-14", "2026-11-11");
        old.add(lesson("plan:expired", "2026-10-13", "finished"));
        now.add(lesson("plan:entering", "2026-11-10", "newly in window"));
        assertTrue(Diff.between(old, now, 42, "2026-10-14").isEmpty());
    }
    @Test public void additionsAndCancellationsInsideOverlapAreReported() {
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot(old.start, old.end);
        old.add(lesson("plan:cancelled", "2026-10-14", "cancelled")); now.add(lesson("plan:added", "2026-10-15", "added"));
        List<Change> diff = Diff.between(old, now, 42, "2026-10-13");
        assertEquals(2, diff.size()); assertEquals("Dodano", diff.get(0).action); assertEquals("Usunięto", diff.get(1).action);
    }
    @Test public void sourceTimeUpdateWithoutValueChangeIsDetected() {
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot(old.start, old.end);
        old.add(grade("4", "2026-10-13 10:00:00")); now.add(grade("4", "2026-10-13 10:30:00"));
        assertEquals(1, Diff.between(old, now, 42, "2026-10-13").size());
    }
    @Test public void snapshotsRoundTripWithoutLosingDates() throws Exception {
        Snapshot s = new Snapshot("2026-10-13", "2026-11-10"); s.add(grade("4", "2026-10-13 10:00:00"));
        Snapshot restored = Snapshot.from(new JSONObject(s.json().toString()));
        assertTrue(Diff.between(s, restored, 42, "2026-10-13").isEmpty()); assertEquals(s.start, restored.start);
    }
    @Test public void parsesTerms2IncludingUnitExamsAndNullSessions() throws Exception {
        JSONObject grades = new JSONObject("""
            {"2026Z":{"FIN":{"course_grades":[{"1":{"exam_id":1,"exam_session_number":1,"value_symbol":"4",
              "date_modified":"2026-10-13 10:00:00","comment":null},"2":null}],
              "course_units_grades":{"20":[{"1":{"exam_id":2,"exam_session_number":1,"value_symbol":"ZAL",
              "date_modified":"2026-10-13 11:00:00","comment":"Ćwiczenia"}}]}}}}
            """);
        Snapshot s = new Snapshot("2026-10-13", "2026-11-10");
        UsosParser.grades(s, grades, Map.of("FIN", "Finanse"));
        assertEquals(2, s.records.size()); assertEquals("Finanse", s.records.get("grade:1:1").title);
        assertTrue(s.records.get("grade:2:1").value.contains("Ćwiczenia"));
    }
    @Test public void timetableUsesStableMeetingIdWhenRoomOrTimeChanges() throws Exception {
        JSONObject activity = new JSONObject("""
            {"type":"classgroup2","sm_id":99,"start_time":"2026-10-14 09:00:00","end_time":"2026-10-14 10:30:00",
             "name":{"pl":"Statystyka"},"room_number":"204","lecturer_ids":[2,1]}
            """);
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot(old.start, old.end);
        UsosParser.timetable(old, new JSONArray().put(activity));
        activity.put("room_number", "315").put("start_time", "2026-10-14 09:30:00").put("lecturer_ids", new JSONArray().put(1).put(2));
        UsosParser.timetable(now, new JSONArray().put(activity));
        List<Change> changes = Diff.between(old, now, 42, "2026-10-13");
        assertEquals(1, changes.size()); assertEquals("Zmieniono", changes.get(0).action);
    }
    @Test(expected = JSONException.class) public void malformedGradeCannotBecomeAValidSnapshot() throws Exception {
        UsosParser.grades(new Snapshot("2026-10-13", "2026-11-10"), new JSONObject("{\"2026Z\":{\"FIN\":{}}}"), Map.of());
    }
    @Test(expected = IllegalArgumentException.class) public void duplicateTimetableIdRejectsResponse() throws Exception {
        JSONObject activity = new JSONObject("""
            {"type":"classgroup2","sm_id":99,"start_time":"2026-10-14 09:00:00","end_time":"2026-10-14 10:30:00","name":{"pl":"Statystyka"}}
            """);
        UsosParser.timetable(new Snapshot("2026-10-13", "2026-11-10"), new JSONArray().put(activity).put(activity));
    }
}
