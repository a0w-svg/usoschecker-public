package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TaskToolsTest {
    @Test
    fun saveEnforcesLimitButAllowsEditingAtCapacity() {
        val rows = JSONArray()
        for (i in 0 until 200) rows.put(task("$i", "Old", "2026-10-13", 1, false))
        val edited = TaskTools.save(rows, task("0", "Updated", "2026-10-13", 1, false))
        assertEquals(200, edited.length())
        assertEquals("Updated", edited.getJSONObject(0).getString("title"))
        assertEquals("Old", rows.getJSONObject(0).getString("title"))
        try {
            TaskTools.save(edited, task("extra", "Extra", "2026-10-13", 1, false))
            fail()
        } catch (expected: IllegalStateException) {
            assertEquals(200, edited.length())
        }
    }

    @Test
    fun saveCopiesInputAndPreservesLegacyRows() {
        val replacement = task("new", "Title", "2026-10-13", 1, false)
        val saved = TaskTools.save(JSONArray().put("legacy"), replacement)
        replacement.put("notes", "changed")
        assertEquals("legacy", saved.getString(0))
        assertEquals("Notatka", saved.getJSONObject(1).getString("notes"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveRejectsOversizedNotes() {
        TaskTools.save(null, task("a", "Title", "2026-10-13", 1, false).put("notes", "x".repeat(2001)))
    }

    private fun task(id: String, title: String, due: String, priority: Int, done: Boolean): JSONObject =
        JSONObject().put("id", id).put("title", title).put("due", due)
            .put("priority", priority).put("done", done).put("notes", "Notatka")

    @Test
    fun sortModesAlwaysKeepCompletedTasksLast() {
        val rows = JSONArray().put(task("low", "Beta", "2026-10-13", 0, false))
            .put(task("high", "Alfa", "2026-10-20", 2, false)).put(task("done", "A", "2026-10-01", 2, true))
        assertEquals("low", TaskTools.sorted(rows, "due")[0].getString("id"))
        assertEquals("high", TaskTools.sorted(rows, "priority")[0].getString("id"))
        assertEquals("high", TaskTools.sorted(rows, "title")[0].getString("id"))
        for (order in arrayOf("due", "priority", "title")) {
            assertEquals("done", TaskTools.sorted(rows, order)[2].getString("id"))
        }
        assertEquals("low", rows.getJSONObject(0).getString("id"))
    }

    @Test
    fun legacyPrioritiesAndOutOfRangeValuesHaveSafeDefaults() {
        assertEquals(1, TaskTools.priority(JSONObject()))
        assertEquals(0, TaskTools.priority(JSONObject().put("priority", -4)))
        assertEquals(2, TaskTools.priority(JSONObject().put("priority", 9)))
    }

    @Test
    fun undoPreservesNotesAndNeverOverwritesExistingTasks() {
        val deleted = task("one", "Alfa", "2026-10-13", 2, true)
        val source = JSONArray()
        val restored = TaskTools.restore(source, deleted)
        assertEquals(0, source.length())
        assertEquals(1, restored.length())
        assertEquals("Notatka", restored.getJSONObject(0).getString("notes"))
        assertTrue(restored.getJSONObject(0).getBoolean("done"))
        restored.getJSONObject(0).put("title", "Nowsza treść")
        val repeated = TaskTools.restore(restored, deleted)
        assertEquals(1, repeated.length())
        assertEquals("Nowsza treść", repeated.getJSONObject(0).getString("title"))
        assertEquals("Alfa", deleted.getString("title"))
    }

    @Test(expected = IllegalStateException::class)
    fun undoHonorsTaskLimit() {
        val rows = JSONArray()
        for (i in 0 until 200) rows.put(task(i.toString(), "Test", "2026-10-13", 1, false))
        TaskTools.restore(rows, task("deleted", "Test", "2026-10-13", 1, false))
    }
}
