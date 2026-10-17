package pl.vistula.checker.core;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlatonLinksTest {
    @Test public void acceptsOnlyPlatonHttpsLinksWithoutCredentials() {
        assertEquals("https://platon.vistula.edu.pl/", PlatonLinks.normalize(" HTTPS://PLATON.VISTULA.EDU.PL "));
        assertEquals("https://platon.vistula.edu.pl/test?id=12#task", PlatonLinks.normalize("https://platon.vistula.edu.pl/test?id=12#task"));
        for (String value : new String[]{"http://platon.vistula.edu.pl/", "https://platon.vistula.edu.pl.evil.org/",
                "https://user@platon.vistula.edu.pl/", "javascript:alert(1)", "https://platon.vistula.edu.pl:444/",
                "https://platon.vistula.edu.pl/?ticket=secret", "https://platon.vistula.edu.pl/?access_token=secret",
                "https://platon.vistula.edu.pl/?%74icket=secret", "https://platon.vistula.edu.pl/#access_token=secret",
                "https://platon.vistula.edu.pl/\nsecret", "//platon.vistula.edu.pl/"}) assertFalse(value, PlatonLinks.isTaskLink(value));
    }
    @Test public void taskSavePreservesLinkDoneStateNotesAndSupportsSearchingPlaton() throws Exception {
        JSONObject task = new JSONObject().put("id", "one").put("title", "Projekt").put("done", true)
            .put("notes", "Moje notatki").put("platonUrl", " HTTPS://PLATON.VISTULA.EDU.PL/test?id=1 ");
        JSONObject saved = TaskTools.save(null, task).getJSONObject(0);
        assertEquals("https://platon.vistula.edu.pl/test?id=1", saved.getString("platonUrl"));
        assertTrue(saved.getBoolean("done"));
        assertEquals("Moje notatki", saved.getString("notes"));
        assertTrue(TaskTools.matches(saved, "all", "Platon", java.time.LocalDate.now()));
        assertTrue(TaskTools.matches(saved, "platon", "", java.time.LocalDate.now()));
        assertFalse(TaskTools.matches(new JSONObject().put("title", "Local"), "platon", "", java.time.LocalDate.now()));
        assertTrue(task.getString("platonUrl").startsWith(" "));
    }
    @Test public void oldTasksNeedNoLinkAndLinkCanBeRemoved() throws Exception {
        JSONObject task = new JSONObject().put("id", "one").put("title", "Projekt");
        assertFalse(TaskTools.save(null, task).getJSONObject(0).has("platonUrl"));
        task.put("platonUrl", "https://platon.vistula.edu.pl/test");
        JSONArray stored = TaskTools.save(null, task);
        task.put("platonUrl", "");
        assertFalse(TaskTools.save(stored, task).getJSONObject(0).has("platonUrl"));
        assertTrue(stored.getJSONObject(0).has("platonUrl"));
    }
    @Test(expected = IllegalArgumentException.class) public void persistenceRejectsUnsafePlatonLinks() throws Exception {
        TaskTools.save(null, new JSONObject().put("id", "one").put("title", "Projekt").put("platonUrl", "https://evil.org/"));
    }
}
