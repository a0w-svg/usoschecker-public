package pl.vistula.checker.core

import java.time.LocalDate
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class PlatonSyncTest {
    private val page = "https://platon.vistula.edu.pl/Student/Zadania"
    private val html = """
        <html><script>var a = '<tr><td><a href="/x/evil">Skrypt</a> 01.01.2030</td></tr>';</script>
        <table>
        <tr><td><a href="/Zadanie/Details/11?x=1">Esej &amp; analiza</a></td><td><a href="/Kurs/5">Historia</a></td>
            <td>Termin: 15.11.2026 23:59</td><td>Nieoddane</td></tr>
        <tr><td><a href="https://platon.vistula.edu.pl/Zadanie/Details/12">Projekt</a></td>
            <td>2026-12-01</td><td>Oddane</td></tr>
        <tr><td><a href="https://evil.example/Zadanie/13">Obce</a></td><td>10.11.2026</td></tr>
        <tr><td><a href="/Zadanie/Details/14?token=abc">Z tokenem</a></td><td>10.11.2026</td></tr>
        <tr><td><a href="/Zadanie/Details/15">Test</a></td><td>20.11.2026</td><td>Wykonano 40%</td></tr>
        </table></html>
    """.trimIndent()

    @Test
    fun parserReadsOnlySafePlatonLinks() {
        val tasks = PlatonTaskParser.parse(html, page)
        assertEquals(listOf("Esej & analiza", "Projekt", "Test"), tasks.map { it.title })
        val essay = tasks[0]
        assertEquals("https://platon.vistula.edu.pl/Zadanie/Details/11?x=1", essay.url)
        assertEquals("Historia", essay.course)
        assertEquals(LocalDate.of(2026, 11, 15), essay.due)
        assertEquals("23:59", essay.dueTime)
        assertFalse(essay.submitted)
        assertTrue(tasks[1].submitted)
        assertEquals(100, tasks[1].progress)
        assertEquals(LocalDate.of(2026, 12, 1), tasks[1].due)
    }

    @Test
    fun parserHandlesGarbageAndForeignPages() {
        assertTrue(PlatonTaskParser.parse("", page).isEmpty())
        assertTrue(PlatonTaskParser.parse("<tr><td>no link 10.10.2026</td></tr>", page).isEmpty())
        assertTrue(PlatonTaskParser.parse(html, "https://evil.example/").isEmpty())
    }

    @Test
    fun mergeAddsThenUpdatesAndKeepsLocalEdits() {
        val remote = PlatonTaskParser.parse(html, page)
        val first = PlatonSync.merge(null, remote, 1L)
        assertEquals(3, first.added)
        assertEquals(0, first.updated)
        val essay = first.tasks.getJSONObject(0)
        assertEquals("platon", essay.getString("source"))
        assertEquals("2026-11-15", essay.getString("due"))
        assertTrue(first.tasks.getJSONObject(1).getBoolean("done"))

        essay.put("notes", "moje").put("priority", 2).put("progress", 30).put("progressManual", true)
        val changedRemote = remote.map { if (it.title.startsWith("Esej")) it.copy(due = LocalDate.of(2026, 11, 20)) else it }
        val second = PlatonSync.merge(first.tasks, changedRemote, 2L)
        assertEquals(0, second.added)
        assertEquals(1, second.updated)
        val kept = second.tasks.getJSONObject(0)
        assertEquals("2026-11-20", kept.getString("due"))
        assertEquals("moje", kept.getString("notes"))
        assertEquals(30, kept.getInt("progress"))
        assertEquals(3, second.tasks.length())
        assertEquals(3, first.tasks.length())
    }

    @Test
    fun remoteSubmissionCompletesTask() {
        val remote = PlatonTaskParser.parse(html, page)
        val local = PlatonSync.merge(null, remote, 1L).tasks
        val submitted = remote.map { if (it.title.startsWith("Esej")) it.copy(submitted = true, progress = 100) else it }
        val merged = PlatonSync.merge(local, submitted, 2L).tasks.getJSONObject(0)
        assertTrue(merged.getBoolean("done"))
        assertEquals(100, merged.getInt("progress"))
    }

    @Test
    fun mergeStopsAtLimitWithoutDroppingTasks() {
        val full = JSONArray()
        for (i in 0 until 200) full.put(org.json.JSONObject().put("id", "$i").put("title", "t$i"))
        val result = PlatonSync.merge(full, PlatonTaskParser.parse(html, page), 1L)
        assertEquals(200, result.tasks.length())
        assertEquals(3, result.skipped)
    }

    @Test
    fun detectsLoginPages() {
        assertTrue(PlatonSync.isLoginPage("https://platon.vistula.edu.pl/Logowanie", ""))
        assertTrue(PlatonSync.isLoginPage("https://platon.vistula.edu.pl/x", "<input type=\"password\">"))
        assertFalse(PlatonSync.isLoginPage("https://platon.vistula.edu.pl/Student", "<html>ok</html>"))
    }

    @Test
    fun progressAndDeadlineHelpers() {
        val today = LocalDate.of(2026, 11, 5)
        val task = org.json.JSONObject().put("id", "1").put("title", "A").put("due", "2026-11-08").put("progress", 150)
        assertEquals(3L, TaskTools.daysLeft(task, today))
        assertEquals(99, TaskTools.progress(task))
        val done = TaskTools.toggled(task)
        assertEquals(100, TaskTools.progress(done))
        assertEquals(0, TaskTools.toggled(done).getInt("progress"))
        assertEquals(50, TaskTools.completionPercent(JSONArray().put(done).put(task)))
        assertEquals(100, TaskTools.save(null, task).getJSONObject(0).getInt("progress"))
    }

    private val home = "https://platon.vistula.edu.pl/StronaStartowa"
    private val course = """<h1>Przedmiot - Języki (OII3NP009OI) - 2026Z - 1 Lab       Wróć do: x</h1>
        <div class="ibox"><div class="ibox-title"><div class="text">Elementy wymagane</div></div><div class="ibox-content">
        <div class="dd3-content mb-2 To_Be_Evaluated" id="1"> Rozwiązania lab 1<br>
          <div><a class="btn" href="/LmsProfil/SciezkaSzkoleniowaElementUzytkownik/RedirectToElement?sciezkaSzkoleniowaElementId=379183&amp;sciezkaSzkoleniowaElementTyp=ZADANIE">Podgląd</a>
          <span class="label label-success">Zadanie</span> <i class="fa fa-clock-o"></i> 2026-10-16 19:46:57 Oczekuje na ocenę</div></div>
        <div class="dd3-content mb-2 Not_Attempted" id="2"> Lab 2<br>
          <a href="/LmsProfil/SciezkaSzkoleniowaElementUzytkownik/RedirectToElement?sciezkaSzkoleniowaElementId=5&amp;sciezkaSzkoleniowaElementTyp=ZADANIE">x</a>
          <span class="label label-success">Zadanie</span></div></div></div>
        <div class="ibox"><div class="ibox-content">
        <div class="dd3-content Passed" id="3"> Lab 1 plik<br><span class="label label-success">Plik</span></div></div></div>"""

    @Test
    fun crawlerFollowsCoursesAndReadsDeadlines() {
        val dash = """<a href="/LmsProfil/SciezkaSzkoleniowaElementUzytkownik/SciezkaSzkoleniowaElementyBySciezkaSzkoleniowaId/115888">Kurs</a>
            <a href="/LmsProfil/SciezkaSzkoleniowaElementUzytkownik/SciezkaSzkoleniowaElementyBySciezkaSzkoleniowaId/115888">Kurs</a>"""
        val pages = mapOf(
            home to dash,
            "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaElementUzytkownik/SciezkaSzkoleniowaElementyBySciezkaSzkoleniowaId/115888" to course,
        )
        val tasks = PlatonCrawler.crawl(home) { url ->
            when {
                url.contains("ElementId=379183") -> url to "<p>Data oddania: 18.10.2026</p>"
                url.contains("ElementId=5&") -> url to "<p>Data oddania: 25.11.2026 12:00</p>"
                else -> url to (pages[url] ?: throw java.io.IOException("404"))
            }
        }
        assertEquals(listOf("Rozwiązania lab 1", "Lab 2"), tasks.map { it.title })
        assertTrue(tasks[0].submitted)
        assertEquals(LocalDate.of(2026, 10, 18), tasks[0].due)
        assertEquals("Języki (OII3NP009OI) - 2026Z - 1 Lab", tasks[0].course)
        assertFalse(tasks[1].submitted)
        assertEquals(LocalDate.of(2026, 11, 25), tasks[1].due)
        assertEquals("12:00", tasks[1].dueTime)
    }
}

class PlatonCourseListTest {
    @Test
    fun keepsOnlyNewestSemesterAndReadsToken() {
        val base = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaUzytkownik/SciezkaSzkoleniowaLista"
        val html = """<input name="__RequestVerificationToken" type="hidden" value="tok123" />
            <a href="/LmsProfil/X/ElementyBySciezkaSzkoleniowaId/1">Uruchom<span class="sr-only"> Analiza (A) - 2026Z - 1 W</span></a>
            <a href="/LmsProfil/X/ElementyBySciezkaSzkoleniowaId/2">Uruchom<span class="sr-only"> Stary kurs - 2026L - 1 W</span></a>
            <a href="/LmsProfil/X/ElementyBySciezkaSzkoleniowaId/3">Uruchom<span class="sr-only"> Bez semestru</span></a>"""
        assertEquals("tok123", PlatonTaskParser.formToken(html))
        val current = PlatonTaskParser.currentSemester(PlatonTaskParser.courseEntries(html, base))
        assertEquals(listOf("1", "3"), current.map { it.url.substringAfterLast('/') })
    }

    @Test
    fun crawlerReadsPagedCourseListAndStatus() {
        val list = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaUzytkownik/SciezkiSzkoleniowe"
        val endpoint = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaUzytkownik/SciezkaSzkoleniowaLista"
        val courseUrl = "https://platon.vistula.edu.pl/LmsProfil/X/ElementyBySciezkaSzkoleniowaId/9"
        val course = """<h1>Przedmiot - Kurs - 2026Z       Wróć do: x</h1><div class="dd3-content To_Be_Evaluated"> Zad<br>
            <a href="/LmsProfil/SciezkaSzkoleniowaElementUzytkownik/RedirectToElement?sciezkaSzkoleniowaElementId=7&amp;sciezkaSzkoleniowaElementTyp=ZADANIE">p</a>
            <span class="label label-success">Zadanie</span><span rel="tooltip" title="Oczekuje na ocenę">Oczekuje na ocenę</span></div>"""
        val posts = java.util.concurrent.atomic.AtomicInteger()
        val tasks = PlatonCrawler.crawl("https://platon.vistula.edu.pl/StronaStartowa", { url, form ->
            assertEquals(endpoint, url)
            posts.incrementAndGet()
            url to if (form["Page"] == "1") """<a href="/LmsProfil/X/ElementyBySciezkaSzkoleniowaId/9">Uruchom Kurs - 2026Z</a>""" else ""
        }) { url ->
            url to when {
                url == list -> """<input name="__RequestVerificationToken" value="t">"""
                url == courseUrl -> course
                url.contains("ElementId=7") -> "Data oddania: 01.12.2026"
                else -> throw java.io.IOException("404")
            }
        }
        // Pages are requested in a wave, so the empty page that ends the list is requested too.
        assertTrue(posts.get() in 2..5)
        assertEquals(1, tasks.size)
        assertEquals("Oczekuje na ocenę", tasks[0].status)
        assertEquals(LocalDate.of(2026, 12, 1), tasks[0].due)
    }
}

class PlatonCourseCardTest {
    private val base = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaUzytkownik/SciezkaSzkoleniowaLista"
    private val html = """
        <div class="card kafelek"><span class="badge badge-pill badge-secondary topBadges1 Right"> NIEZDANE </span>
        <div class="card-title">Sztuczna inteligencja - 2026Z - 1 Lab</div>
        <div class="cardRow"><div class="textSmall">Progress</div><div>40%</div></div>
        <div class="cardRow"><div class="textSmall">Liczba uruchomień: 5</div><div class="textSmall">Ocena: NZal</div></div>
        <a href="/LmsProfil/SciezkaSzkoleniowaElementUzytkownik/SciezkaSzkoleniowaElementyBySciezkaSzkoleniowaId/117260"><i></i> Uruchom<span class="sr-only"> Sztuczna inteligencja - 2026Z - 1 Lab</span></a></div>
        <div class="card kafelek"><div class="card-title">Bez linku</div></div>
        <div class="card kafelek"><span class="topBadges1"> ZDANE </span>
        <div class="cardRow"><div class="textSmall">Progress</div><div>100%</div></div>
        <div class="textSmall">Ocena: Dst.+</div>
        <a href="/x/SzkoleniowaId/9">Uruchom<span class="sr-only"> Analiza - 2025Z</span></a></div>"""

    @Test
    fun readsProgressGradeAndRuns() {
        val cards = PlatonTaskParser.parseCourseCards(html, base)
        assertEquals(2, cards.size)
        assertEquals("Sztuczna inteligencja - 2026Z - 1 Lab", cards[0].title)
        assertEquals(40, cards[0].progress)
        assertEquals("NZal", cards[0].grade)
        assertEquals(5, cards[0].runs)
        assertEquals("NIEZDANE", cards[0].badge)
        assertEquals(100, cards[1].progress)
        assertEquals("Dst.+", cards[1].grade)
        assertEquals(0, PlatonTaskParser.parseCourseCards("", base).size)
    }

    @Test
    fun storesCoursesAsJson() {
        val json = PlatonSync.coursesJson(PlatonTaskParser.parseCourseCards(html, base), 7L)
        assertEquals(2, json.length())
        assertEquals("NZal", json.getJSONObject(0).getString("grade"))
    }
}

class PlatonMessageTest {
    @Test
    fun parsesInboxRows() {
        val html = """<table><tbody>
        <tr class="font-weight-bold droppable_category" data-wiadomoscId="569587"><td><input type="checkbox"></td>
        <td><a href="#"> System</a> <span class="label-danger label pull-right">Nieodczytana</span></td>
        <td class="mail-subject"><a href="#">Powiadomienie o ocenie zadania Lab 5 &amp; więcej</a></td>
        <td class="text-right">2026-07-01 10:22:18</td></tr>
        <tr class="read droppable_category" data-wiadomoscId="569561"><td><a href="#">Jan Kowalski</a></td>
        <td class="mail-subject"><a href="#">Zebranie</a></td><td class="text-right">2026-06-30</td></tr>
        <tr><td>bez id</td></tr></tbody></table>"""
        val rows = PlatonTaskParser.parseMessages(html)
        assertEquals(2, rows.size)
        assertEquals("569587", rows[0].id)
        assertEquals("System", rows[0].sender)
        assertEquals("Powiadomienie o ocenie zadania Lab 5 & więcej", rows[0].subject)
        assertEquals("2026-07-01 10:22:18", rows[0].date)
        assertTrue(rows[0].unread)
        assertFalse(rows[1].unread)
        assertEquals("Jan Kowalski", rows[1].sender)
        assertTrue(PlatonTaskParser.parseMessages("").isEmpty())
        assertEquals(2, PlatonSync.messagesJson(rows).length())
    }
}
