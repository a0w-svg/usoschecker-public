package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UngradedCoursesTest {
    private fun snapshot(): Snapshot {
        val s = Snapshot("2026-10-01", "2026-11-01")
        val editions = JSONObject(
            "{\"winter\":[{\"course_id\":\"A\",\"course_name\":{\"pl\":\"Analiza\"}},{\"course_id\":\"B\",\"course_name\":{\"pl\":\"Fizyka\"}}],\"summer\":[{\"course_id\":\"A\",\"course_name\":{\"pl\":\"Analiza\"}}]}"
        )
        s.courses.addAll(UsosParser.courseEditions(editions, mapOf("winter\u0000A" to 5.0)))
        return s
    }

    @Test
    fun enrolledCoursesSurviveStorageWithoutBecomingGradeRecords() {
        val s = Snapshot.from(snapshot().json())
        assertEquals(3, s.courses.size)
        assertTrue(s.records.isEmpty())
        assertEquals(2, GradeBrowser.ungraded(s, "winter", "all", "").size)
        assertEquals(5.0, GradeBrowser.ungraded(s, "winter", "all", "Analiza")[0].ects, 0.0)
        assertEquals(0, GradeSummary.of(s.courses).numeric)
        assertEquals(0, GradeSummary.of(s.courses).other)
    }

    @Test
    fun actualGradeReplacesOnlyTheMatchingCourseAndSemester() {
        val s = snapshot()
        s.add(
            Record(
                "grade", "ocena", "Analiza", "4", "", "", "winter", "", "", 5.0,
                JSONObject().put("courseId", "A")
            )
        )
        assertEquals("Fizyka", GradeBrowser.ungraded(s, "winter", "all", "")[0].title)
        assertEquals(1, GradeBrowser.ungraded(s, "summer", "all", "").size)
        assertEquals(4.0, GradeSummary.of(s.records.values).average, 0.0)
    }

    @Test
    fun placeholdersDoNotGenerateGradeChangeNotifications() {
        val previous = snapshot()
        val current = snapshot()
        current.courses.clear()
        assertTrue(Diff.between(previous, current, 1, "2026-10-15").isEmpty())
    }

    @Test
    fun ungradedCoursesRespectSearchAndDoNotPretendToHaveAGradeType() {
        val s = snapshot()
        assertEquals(1, GradeBrowser.ungraded(s, "winter", "all", "Fizyka").size)
        assertTrue(GradeBrowser.ungraded(s, "winter", "numeric", "").isEmpty())
        assertTrue(GradeBrowser.ungraded(s, "winter", "other", "").isEmpty())
        assertTrue(GradeBrowser.ungraded(s, "winter", "all", "missing").isEmpty())
    }

    @Test
    fun oldSnapshotsRemainReadableWithoutCourseEditions() {
        val s = Snapshot.from(JSONObject("{\"start\":\"2026-10-01\",\"end\":\"2026-11-01\",\"records\":[]}"))
        assertTrue(s.courses.isEmpty())
    }
}
