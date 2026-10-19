package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlatonLinksTest {
    @Test
    fun sessionKeysCannotBeSavedAsTaskLinks() {
        for (parameter in arrayOf("sesskey", "sid", "sessionid", "PHPSESSID", "JSESSIONID", "authorization")) {
            assertFalse(parameter, PlatonLinks.isTaskLink("https://platon.vistula.edu.pl/mod/assign/view.php?id=1&$parameter=private"))
            assertFalse(parameter, PlatonLinks.isTaskLink("https://platon.vistula.edu.pl/#$parameter=private"))
        }
    }

    @Test
    fun acceptsOnlyPlatonHttpsLinksWithoutCredentials() {
        assertEquals("https://platon.vistula.edu.pl/", PlatonLinks.normalize(" HTTPS://PLATON.VISTULA.EDU.PL "))
        assertEquals("https://platon.vistula.edu.pl/test?id=12#task", PlatonLinks.normalize("https://platon.vistula.edu.pl/test?id=12#task"))
        for (value in arrayOf(
            "http://platon.vistula.edu.pl/", "https://platon.vistula.edu.pl.evil.org/",
            "https://user@platon.vistula.edu.pl/", "javascript:alert(1)", "https://platon.vistula.edu.pl:444/",
            "https://platon.vistula.edu.pl/?ticket=secret", "https://platon.vistula.edu.pl/?access_token=secret",
            "https://platon.vistula.edu.pl/?%74icket=secret", "https://platon.vistula.edu.pl/#access_token=secret",
            "https://platon.vistula.edu.pl/\nsecret", "//platon.vistula.edu.pl/"
        )) {
            assertFalse(value, PlatonLinks.isTaskLink(value))
        }
    }

    @Test
    fun taskSavePreservesLinkDoneStateNotesAndSupportsSearchingPlaton() {
        val task = JSONObject().put("id", "one").put("title", "Projekt").put("done", true)
            .put("notes", "Moje notatki").put("platonUrl", " HTTPS://PLATON.VISTULA.EDU.PL/test?id=1 ")
        val saved = TaskTools.save(null, task).getJSONObject(0)
        assertEquals("https://platon.vistula.edu.pl/test?id=1", saved.getString("platonUrl"))
        assertTrue(saved.getBoolean("done"))
        assertEquals("Moje notatki", saved.getString("notes"))
        assertTrue(TaskTools.matches(saved, "all", "Platon", LocalDate.now()))
        assertTrue(TaskTools.matches(saved, "platon", "", LocalDate.now()))
        assertFalse(TaskTools.matches(JSONObject().put("title", "Local"), "platon", "", LocalDate.now()))
        assertTrue(task.getString("platonUrl").startsWith(" "))
    }

    @Test
    fun oldTasksNeedNoLinkAndLinkCanBeRemoved() {
        val task = JSONObject().put("id", "one").put("title", "Projekt")
        assertFalse(TaskTools.save(null, task).getJSONObject(0).has("platonUrl"))
        task.put("platonUrl", "https://platon.vistula.edu.pl/test")
        val stored = TaskTools.save(null, task)
        task.put("platonUrl", "")
        assertFalse(TaskTools.save(stored, task).getJSONObject(0).has("platonUrl"))
        assertTrue(stored.getJSONObject(0).has("platonUrl"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun persistenceRejectsUnsafePlatonLinks() {
        TaskTools.save(null, JSONObject().put("id", "one").put("title", "Projekt").put("platonUrl", "https://evil.org/"))
    }
}
