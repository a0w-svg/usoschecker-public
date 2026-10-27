package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OptionalModulesParserTest {
    @Test fun malformedCourseTestPointsAreNotTurnedIntoZero() {
        val node = JSONObject().put("id", "task").put("task_node_details",
            JSONObject().put("points_max", 20).put("students_points", JSONObject().put("points", "unknown")))
        val tests = JSONArray().put(JSONObject().put("root", node))
        assertThrows(org.json.JSONException::class.java) { UsosParser.courseTestTrees(tests, emptyMap()) }
    }
    @Test
    fun courseTreesKeepZeroPointsSkipMissingResultsAndPreserveSemester() {
        val task = JSONObject().put("id", "quiz").put("name", JSONObject().put("pl", "Kolokwium").put("en", "Quiz"))
            .put("task_node_details", JSONObject().put("points_max", 20).put("students_points", JSONObject().put("points", 0)))
        val empty = JSONObject().put("id", "empty")
            .put("task_node_details", JSONObject().put("students_points", JSONObject().put("points", JSONObject.NULL)))
        val hidden = JSONObject(task.toString()).put("id", "hidden").put("visible_for_students", false)
        val root = JSONObject().put("id", "root").put("subnodes_deep", JSONArray().put(task).put(empty).put(hidden))
        val tests = JSONArray().put(
            JSONObject().put("root", root).put(
                "course_edition",
                JSONObject()
                    .put("course", JSONObject().put("id", "FIN"))
                    .put("term", JSONObject().put("id", "2026Z"))
            )
        )
        val result = ApiLanguage.`in`("en") { UsosParser.courseTestTrees(tests, mapOf("FIN" to "Finance")) }
        assertEquals(1, result.size)
        val quiz = result[0]
        assertEquals("Quiz", quiz.testName)
        assertEquals(0.0, quiz.points, 0.0)
        assertTrue(quiz.hasPoints)
        assertEquals(20.0, quiz.maxPoints, 0.0)
        assertEquals("2026Z", CourseTestRecord.from(quiz.json()).term)
        assertEquals("2026Z", GradeSemesters.testTerm(Snapshot("", ""), quiz))
    }

    @Test
    fun gradeNodesReadTheirOwnGradeAndDoNotInventPoints() {
        val node = JSONObject().put("id", "grade").put("name", "Exam")
            .put(
                "grade_node_details",
                JSONObject().put(
                    "students_grade",
                    JSONObject()
                        .put("grade_value", JSONObject().put("symbol", "4,5"))
                        .put("last_changed", "2026-10-18")
                )
            )
        val grade = UsosParser.courseTestTrees(JSONArray().put(JSONObject().put("root", node)), emptyMap())[0]
        assertEquals("4,5", grade.grade)
        assertFalse(grade.hasPoints)
        assertFalse(CourseTestRecord.from(grade.json()).hasPoints)
        assertEquals(0.0, grade.maxPoints, 0.0)
        assertEquals("2026-10-18", grade.date)
    }

    @Test
    fun newsAuthorsAndFullContentFollowSelectedLanguageAndSurviveCache() {
        val article = JSONObject().put("id", "news").put("title", JSONObject().put("en", "Library").put("pl", "Biblioteka"))
            .put("author", JSONObject().put("en", "Student office").put("pl", "Dziekanat"))
            .put("content_html", JSONObject().put("en", "<p>Full article</p>").put("pl", "<p>Pełna treść</p>"))
        val news = ApiLanguage.`in`("en") { UsosParser.newsArticles(JSONArray().put(article))[0] }
        assertEquals("Student office", news.author)
        assertEquals("<p>Full article</p>", NewsArticle.from(news.json()).content)
        assertFalse(news.author.contains("{"))
    }

    @Test
    fun moduleErrorsRemainVisibleAfterSnapshotSerialization() {
        val snapshot = Snapshot("", "")
        snapshot.serviceErrors["news"] = "Unavailable"
        assertEquals("Unavailable", Snapshot.from(snapshot.json()).serviceErrors["news"])
    }
}
