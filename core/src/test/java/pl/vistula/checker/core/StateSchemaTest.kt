package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateSchemaTest {
    @Test
    fun stampsOldStatesAndRepairsCompletedTasks() {
        val state = JSONObject().put("token", "secret-token").put(
            "tasks",
            JSONArray()
                .put(JSONObject().put("id", "1").put("done", true).put("progress", 40))
                .put(JSONObject().put("id", "2").put("done", false).put("progress", 40))
                .put("legacy-entry"),
        )
        assertTrue(StateSchema.migrate(state))
        assertEquals(StateSchema.CURRENT, state.getInt(StateKeys.SCHEMA_VERSION))
        val tasks = state.getJSONArray("tasks")
        assertEquals(100, tasks.getJSONObject(0).getInt("progress"))
        assertEquals(40, tasks.getJSONObject(1).getInt("progress"))
        assertEquals("legacy-entry", tasks.getString(2))
        assertEquals("secret-token", state.getString("token"))
    }

    @Test
    fun isIdempotentAndLeavesCurrentStatesAlone() {
        val state = JSONObject().put("tasks", JSONArray().put(JSONObject().put("done", true).put("progress", 3)))
        assertTrue(StateSchema.migrate(state))
        val once = state.toString()
        assertFalse(StateSchema.migrate(state))
        assertEquals(once, state.toString())
        val current = JSONObject().put(StateKeys.SCHEMA_VERSION, StateSchema.CURRENT).put("tasks", JSONArray().put(JSONObject().put("done", true).put("progress", 3)))
        assertFalse(StateSchema.migrate(current))
        assertEquals(3, current.getJSONArray("tasks").getJSONObject(0).getInt("progress"))
    }

    @Test
    fun toleratesMissingOrMalformedData() {
        assertFalse(StateSchema.migrate(JSONObject()))
        val odd = JSONObject().put("tasks", "not an array")
        assertTrue(StateSchema.migrate(odd))
        assertEquals("not an array", odd.getString("tasks"))
    }
}
