package pl.vistula.checker.core;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class LecturerTest {
    @Test public void directoryCombinesNamesAndHandlesUnavailablePeople() throws Exception {
        Map<String, String> names = UsosParser.lecturerNames(new JSONObject("""
            {"1":{"first_name":" Anna ","last_name":" Kowalska "},"2":null,
             "3":{"first_name":null,"last_name":null},"4":{"first_name":"Łukasz","last_name":"Żółć"}}
            """));
        assertEquals("Anna Kowalska", names.get("1")); assertEquals("Łukasz Żółć", names.get("4"));
        assertFalse(names.containsKey("2")); assertFalse(names.containsKey("3"));
    }
    @Test public void lecturerIdsAreDeduplicatedAndNullsAreIgnored() throws Exception {
        JSONArray rows = new JSONArray("""
            [{"lecturer_ids":[2,1,2,null]},{"lecturer_ids":[1,3]},{"lecturer_ids":null}]
            """);
        assertEquals(Set.of("1", "2", "3"), UsosParser.lecturerIds(rows));
    }
    @Test public void timetableShowsFullNamesAndExplicitFallbackAndIsOrderIndependent() throws Exception {
        JSONObject activity = new JSONObject("""
            {"type":"classgroup2","sm_id":99,"start_time":"2026-10-14 09:00:00","end_time":"2026-10-14 10:30:00",
             "name":{"pl":"Statystyka"},"lecturer_ids":[2,1,null]}
            """);
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot(old.start, old.end);
        UsosParser.timetable(old, new JSONArray().put(activity), Map.of("1", "Anna Kowalska"));
        String value = old.records.get("plan:sm:99").value;
        assertTrue(value.contains("Prowadzący: Anna Kowalska, ID: 2")); assertFalse(value.contains("null"));
        activity.put("lecturer_ids", new JSONArray().put(1).put(2));
        UsosParser.timetable(now, new JSONArray().put(activity), Map.of("1", "Anna Kowalska"));
        assertTrue(Diff.between(old, now, 1, "2026-10-13").isEmpty());
    }
}