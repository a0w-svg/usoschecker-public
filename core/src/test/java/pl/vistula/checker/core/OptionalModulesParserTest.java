package pl.vistula.checker.core;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class OptionalModulesParserTest {
    @Test public void courseTreesKeepZeroPointsSkipMissingResultsAndPreserveSemester() throws Exception {
        JSONObject task = new JSONObject().put("id", "quiz").put("name", new JSONObject().put("pl", "Kolokwium").put("en", "Quiz"))
            .put("task_node_details", new JSONObject().put("points_max", 20).put("students_points", new JSONObject().put("points", 0)));
        JSONObject empty = new JSONObject().put("id", "empty").put("task_node_details", new JSONObject().put("students_points", new JSONObject().put("points", JSONObject.NULL)));
        JSONObject hidden = new JSONObject(task.toString()).put("id", "hidden").put("visible_for_students", false);
        JSONObject root = new JSONObject().put("id", "root").put("subnodes_deep", new JSONArray().put(task).put(empty).put(hidden));
        JSONArray tests = new JSONArray().put(new JSONObject().put("root", root).put("course_edition", new JSONObject()
            .put("course", new JSONObject().put("id", "FIN")).put("term", new JSONObject().put("id", "2026Z"))));
        List<CourseTestRecord> result = ApiLanguage.in("en", () -> UsosParser.courseTestTrees(tests, Map.of("FIN", "Finance")));
        assertEquals(1, result.size());
        CourseTestRecord quiz = result.get(0);
        assertEquals("Quiz", quiz.testName); assertEquals(0, quiz.points, 0); assertEquals(20, quiz.maxPoints, 0);
        assertEquals("2026Z", CourseTestRecord.from(quiz.json()).term);
        assertEquals("2026Z", GradeSemesters.testTerm(new Snapshot("", ""), quiz));
    }
    @Test public void gradeNodesReadTheirOwnGradeAndDoNotInventPoints() throws Exception {
        JSONObject node = new JSONObject().put("id", "grade").put("name", "Exam")
            .put("grade_node_details", new JSONObject().put("students_grade", new JSONObject()
                .put("grade_value", new JSONObject().put("symbol", "4,5")).put("last_changed", "2026-10-18")));
        CourseTestRecord grade = UsosParser.courseTestTrees(new JSONArray().put(new JSONObject().put("root", node)), Map.of()).get(0);
        assertEquals("4,5", grade.grade); assertEquals(0, grade.maxPoints, 0); assertEquals("2026-10-18", grade.date);
    }
    @Test public void newsAuthorsAndFullContentFollowSelectedLanguageAndSurviveCache() throws Exception {
        JSONObject article = new JSONObject().put("id", "news").put("title", new JSONObject().put("en", "Library").put("pl", "Biblioteka"))
            .put("author", new JSONObject().put("en", "Student office").put("pl", "Dziekanat"))
            .put("content_html", new JSONObject().put("en", "<p>Full article</p>").put("pl", "<p>Pełna treść</p>"));
        NewsArticle news = ApiLanguage.in("en", () -> UsosParser.newsArticles(new JSONArray().put(article)).get(0));
        assertEquals("Student office", news.author);
        assertEquals("<p>Full article</p>", NewsArticle.from(news.json()).content);
        assertFalse(news.author.contains("{"));
    }
    @Test public void moduleErrorsRemainVisibleAfterSnapshotSerialization() throws Exception {
        Snapshot snapshot = new Snapshot("", ""); snapshot.serviceErrors.put("news", "Unavailable");
        assertEquals("Unavailable", Snapshot.from(snapshot.json()).serviceErrors.get("news"));
    }
}
