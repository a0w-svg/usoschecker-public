package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import pl.vistula.checker.DesktopTaskActions

class DesktopTaskActionsTest {
    @Test fun creationValidatesDatesContentAndTheSharedTaskLimit() {
        val source = JSONArray()
        assertThrows(IllegalArgumentException::class.java) { DesktopTaskActions.create(source, " ", "", "") }
        assertThrows(java.time.format.DateTimeParseException::class.java) { DesktopTaskActions.create(source, "Task", "", "2026-02-30") }
        assertThrows(IllegalArgumentException::class.java) { DesktopTaskActions.create(source, "x".repeat(201), "", "") }
        val full = JSONArray()
        repeat(200) { full.put(JSONObject().put("id", "$it").put("title", "Existing $it")) }
        assertThrows(IllegalStateException::class.java) { DesktopTaskActions.create(full, "Task", "", "") }
        assertEquals(200, full.length())
        val result = DesktopTaskActions.create(source, " New task ", "Course", "2026-10-21")
        assertTrue(result.getJSONObject(0).getString("id").isNotEmpty())
        assertEquals("New task", result.getJSONObject(0).getString("title"))
        assertEquals(0, source.length())
    }

    @Test fun identicalTimestampsAndTitlesDoNotToggleOrDeleteTheWrongTask() {
        val first = JSONObject().put("id", "first").put("title", "Task").put("createdAt", 1L)
        val second = JSONObject().put("id", "second").put("title", "Task").put("createdAt", 1L)
        val source = JSONArray().put(first).put(second)
        val changed = DesktopTaskActions.toggle(source, second)
        assertFalse(changed.getJSONObject(0).optBoolean("done"))
        assertTrue(changed.getJSONObject(1).getBoolean("done"))
        assertFalse(second.optBoolean("done"))
        val deleted = DesktopTaskActions.delete(source, second)
        assertEquals(1, deleted.length())
        assertEquals("first", deleted.getJSONObject(0).getString("id"))
    }

    @Test fun legacyTaskCanBeEditedWithoutMutatingSourceOrDuplicatingTheRow() {
        val legacy = JSONObject().put("title", "Legacy").put("createdAt", 1L)
        val source = JSONArray().put(legacy)
        val changed = DesktopTaskActions.toggle(source, legacy)
        assertEquals(1, changed.length())
        assertTrue(changed.getJSONObject(0).optBoolean("done"))
        assertTrue(changed.getJSONObject(0).getString("id").isNotBlank())
        assertFalse(legacy.has("id"))
    }
}
