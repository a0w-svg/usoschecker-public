package pl.vistula.checker.core

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.vistula.checker.core.PlatonTaskParser.PlatonCourse

class PlatonStateTest {
    private val url = "https://platon.vistula.edu.pl/Zadanie/1"
    private fun task(id: String, submitted: Boolean = false) =
        PlatonTask("$url$id", "Zadanie $id", "Kurs", LocalDate.of(2026, 12, 1), "", submitted, null)
    private fun course(grade: String) = PlatonCourse("https://platon.vistula.edu.pl/c/SzkoleniowaId/9", "Kurs", 10, grade, 1, "")

    @Test
    fun firstSyncIsSilent() {
        val state = JSONObject()
        val summary = PlatonState.apply(state, PlatonCrawler.Result(listOf(task("a")), listOf(course("NZal"))), 1L)
        assertTrue(summary.newTitles.isEmpty())
        assertTrue(summary.gradeChanges.isEmpty())
        assertEquals(1, state.getJSONArray("tasks").length())
        assertEquals(1, state.getJSONArray("platonCourses").length())
    }

    @Test
    fun laterSyncReportsNewAssignmentsAndGradeChanges() {
        val state = JSONObject()
        PlatonState.apply(state, PlatonCrawler.Result(listOf(task("a")), listOf(course("NZal"))), 1L)
        val summary = PlatonState.apply(
            state, PlatonCrawler.Result(listOf(task("a"), task("b"), task("c", submitted = true)), listOf(course("Dst"))), 2L)
        assertEquals(listOf("Zadanie b"), summary.newTitles)
        assertEquals(listOf("Kurs" to "Dst"), summary.gradeChanges)
        assertEquals(3, state.getJSONArray("tasks").length())
        val history = state.getJSONArray("history")
        assertEquals(2, history.length())
        val task = history.getJSONObject(0)
        assertEquals("platon", task.getString("kind"))
        assertEquals("Dodano", task.getString("action"))
        assertEquals("task", task.getJSONObject("metadata").getString("platonEvent"))
        val grade = history.getJSONObject(1)
        assertEquals("Zmieniono", grade.getString("action"))
        assertEquals("NZal → Dst", grade.getJSONObject("metadata").getString("summary"))
        val again = PlatonState.apply(state, PlatonCrawler.Result(listOf(task("a"), task("b")), listOf(course("Dst"))), 3L)
        assertTrue(again.newTitles.isEmpty() && again.gradeChanges.isEmpty())
        assertEquals(2, state.getJSONArray("history").length())
        assertTrue(state.optJSONArray("tasks") is JSONArray)
    }
}
