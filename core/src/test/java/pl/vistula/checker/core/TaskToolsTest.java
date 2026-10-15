package pl.vistula.checker.core;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskToolsTest {
    @Test public void saveEnforcesLimitButAllowsEditingAtCapacity() throws Exception {
        JSONArray rows = new JSONArray();
        for (int i = 0; i < 200; i++) rows.put(task("" + i, "Old", "2026-10-13", 1, false));
        JSONArray edited = TaskTools.save(rows, task("0", "Updated", "2026-10-13", 1, false));
        assertEquals(200, edited.length());
        assertEquals("Updated", edited.getJSONObject(0).getString("title"));
        assertEquals("Old", rows.getJSONObject(0).getString("title"));
        try { TaskTools.save(edited, task("extra", "Extra", "2026-10-13", 1, false)); fail(); }
        catch (IllegalStateException expected) { assertEquals(200, edited.length()); }
    }
    @Test public void saveCopiesInputAndPreservesLegacyRows() throws Exception {
        JSONObject replacement = task("new", "Title", "2026-10-13", 1, false);
        JSONArray saved = TaskTools.save(new JSONArray().put("legacy"), replacement);
        replacement.put("notes", "changed");
        assertEquals("legacy", saved.getString(0));
        assertEquals("Notatka", saved.getJSONObject(1).getString("notes"));
    }
    @Test(expected = IllegalArgumentException.class) public void saveRejectsOversizedNotes() throws Exception {
        TaskTools.save(null, task("a", "Title", "2026-10-13", 1, false).put("notes", "x".repeat(2001)));
    }
    private JSONObject task(String id, String title, String due, int priority, boolean done) throws Exception {
        return new JSONObject().put("id", id).put("title", title).put("due", due).put("priority", priority).put("done", done).put("notes", "Notatka");
    }
    @Test public void sortModesAlwaysKeepCompletedTasksLast() throws Exception {
        JSONArray rows = new JSONArray().put(task("low", "Beta", "2026-10-13", 0, false))
            .put(task("high", "Alfa", "2026-10-20", 2, false)).put(task("done", "A", "2026-10-01", 2, true));
        assertEquals("low", TaskTools.sorted(rows, "due").get(0).getString("id"));
        assertEquals("high", TaskTools.sorted(rows, "priority").get(0).getString("id"));
        assertEquals("high", TaskTools.sorted(rows, "title").get(0).getString("id"));
        for (String order : new String[]{"due", "priority", "title"}) assertEquals("done", TaskTools.sorted(rows, order).get(2).getString("id"));
        assertEquals("low", rows.getJSONObject(0).getString("id"));
    }
    @Test public void legacyPrioritiesAndOutOfRangeValuesHaveSafeDefaults() throws Exception {
        assertEquals(1, TaskTools.priority(new JSONObject()));
        assertEquals(0, TaskTools.priority(new JSONObject().put("priority", -4)));
        assertEquals(2, TaskTools.priority(new JSONObject().put("priority", 9)));
    }
    @Test public void undoPreservesNotesAndNeverOverwritesExistingTasks() throws Exception {
        JSONObject deleted = task("one", "Alfa", "2026-10-13", 2, true);
        JSONArray source = new JSONArray();
        JSONArray restored = TaskTools.restore(source, deleted);
        assertEquals(0, source.length()); assertEquals(1, restored.length());
        assertEquals("Notatka", restored.getJSONObject(0).getString("notes"));
        assertTrue(restored.getJSONObject(0).getBoolean("done"));
        restored.getJSONObject(0).put("title", "Nowsza treść");
        JSONArray repeated = TaskTools.restore(restored, deleted);
        assertEquals(1, repeated.length()); assertEquals("Nowsza treść", repeated.getJSONObject(0).getString("title"));
        assertEquals("Alfa", deleted.getString("title"));
    }
    @Test(expected = IllegalStateException.class) public void undoHonorsTaskLimit() throws Exception {
        JSONArray rows = new JSONArray();
        for (int i = 0; i < 200; i++) rows.put(task(String.valueOf(i), "Test", "2026-10-13", 1, false));
        TaskTools.restore(rows, task("deleted", "Test", "2026-10-13", 1, false));
    }
}
