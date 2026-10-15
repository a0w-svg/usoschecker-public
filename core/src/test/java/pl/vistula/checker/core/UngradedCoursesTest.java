package pl.vistula.checker.core;

import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class UngradedCoursesTest {
    private Snapshot snapshot() throws Exception {
        Snapshot s = new Snapshot("2026-10-01", "2026-11-01");
        JSONObject editions = new JSONObject("{\"winter\":[{\"course_id\":\"A\",\"course_name\":{\"pl\":\"Analiza\"}},{\"course_id\":\"B\",\"course_name\":{\"pl\":\"Fizyka\"}}],\"summer\":[{\"course_id\":\"A\",\"course_name\":{\"pl\":\"Analiza\"}}]}");
        s.courses.addAll(UsosParser.courseEditions(editions, Map.of("winter\0A", 5d)));
        return s;
    }
    @Test public void enrolledCoursesSurviveStorageWithoutBecomingGradeRecords() throws Exception {
        Snapshot s = Snapshot.from(snapshot().json());
        assertEquals(3, s.courses.size()); assertTrue(s.records.isEmpty());
        assertEquals(2, GradeBrowser.ungraded(s, "winter", "all", "").size());
        assertEquals(5, GradeBrowser.ungraded(s, "winter", "all", "Analiza").get(0).ects, 0);
        assertEquals(0, GradeSummary.of(s.courses).numeric);
        assertEquals(0, GradeSummary.of(s.courses).other);
    }
    @Test public void actualGradeReplacesOnlyTheMatchingCourseAndSemester() throws Exception {
        Snapshot s = snapshot();
        s.add(new Record("grade", "ocena", "Analiza", "4", "", "", "winter", "", "", 5,
            new JSONObject().put("courseId", "A")));
        assertEquals("Fizyka", GradeBrowser.ungraded(s, "winter", "all", "").get(0).title);
        assertEquals(1, GradeBrowser.ungraded(s, "summer", "all", "").size());
        assertEquals(4, GradeSummary.of(s.records.values()).average, 0);
    }
    @Test public void placeholdersDoNotGenerateGradeChangeNotifications() throws Exception {
        Snapshot previous = snapshot(); Snapshot current = snapshot();
        current.courses.clear();
        assertTrue(Diff.between(previous, current, 1, "2026-10-15").isEmpty());
    }
    @Test public void ungradedCoursesRespectSearchAndDoNotPretendToHaveAGradeType() throws Exception {
        Snapshot s = snapshot();
        assertEquals(1, GradeBrowser.ungraded(s, "winter", "all", "Fizyka").size());
        assertTrue(GradeBrowser.ungraded(s, "winter", "numeric", "").isEmpty());
        assertTrue(GradeBrowser.ungraded(s, "winter", "other", "").isEmpty());
        assertTrue(GradeBrowser.ungraded(s, "winter", "all", "missing").isEmpty());
    }
    @Test public void oldSnapshotsRemainReadableWithoutCourseEditions() throws Exception {
        Snapshot s = Snapshot.from(new JSONObject("{\"start\":\"2026-10-01\",\"end\":\"2026-11-01\",\"records\":[]}"));
        assertTrue(s.courses.isEmpty());
    }
}
