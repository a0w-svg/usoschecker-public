package pl.vistula.checker.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parsers run against saved copies of Platon's real page structure (anonymized, under test/resources/platon).
 * When Platon changes its markup, replace the fixture with a new anonymized capture and these tests show what broke.
 */
class PlatonFixturesTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/platon/$name")) { "missing fixture $name" }.readBytes().toString(Charsets.UTF_8)

    private val base = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaElementUzytkownik/SciezkaSzkoleniowaElementyBySciezkaSzkoleniowaId/900100"

    @Test
    fun courseElementsGiveAssignmentsWithStatusAndCourse() {
        val tasks = PlatonTaskParser.parseElements(fixture("course.html"), base)
        assertEquals(listOf("Rozwiązania lab 1", "Lab 2 - kolekcje", "Kolokwium 1"), tasks.map { it.title })
        assertTrue(tasks.all { it.course.startsWith("Programowanie obiektowe") })
        assertTrue(tasks[0].submitted)
        assertEquals("Oczekuje na ocenę", tasks[0].status)
        assertFalse(tasks[1].submitted)
        assertTrue(tasks[1].needsDetail)
        assertTrue(tasks[2].submitted)
        assertFalse(tasks[2].needsDetail)
        assertTrue(tasks.none { it.title.contains("wykład") }) // files are not assignments
        assertTrue(tasks.all { it.url.contains("RedirectToElement") && !it.url.contains("Token") })
    }

    @Test
    fun taskDetailGivesTheDeadlineNotTheScriptDate() {
        val (due, time) = PlatonTaskParser.parseDeadline(fixture("task-detail.html"))
        assertEquals(LocalDate.of(2026, 10, 18), due)
        assertEquals("23:59", time)
    }

    @Test
    fun courseListGivesCardsAndTokenAndCurrentSemester() {
        val html = fixture("course-list.html")
        val page = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaUzytkownik/SciezkaSzkoleniowaLista"
        assertEquals("FIXTURE-TOKEN", PlatonTaskParser.formToken(html))
        val cards = PlatonTaskParser.parseCourseCards(html, page)
        assertEquals(2, cards.size)
        assertEquals(35, cards[0].progress)
        assertEquals("NZal", cards[0].grade)
        assertEquals(5, cards[0].runs)
        assertEquals("Dst.+", cards[1].grade)
        val current = PlatonTaskParser.currentSemester(PlatonTaskParser.courseEntries(html, page))
        assertEquals(1, current.size)
        assertTrue(current[0].label.contains("2026Z"))
    }

    @Test
    fun inboxGivesUnreadAndDecodedSubjects() {
        val rows = PlatonTaskParser.parseMessages(fixture("inbox.html"))
        assertEquals(listOf("700001", "700002", "700003"), rows.map { it.id })
        assertEquals(listOf(true, false, false), rows.map { it.unread })
        assertEquals("Zmiana terminu laboratorium & sali", rows[1].subject)
        assertEquals("Dr Anna Przykładowa", rows[1].sender)
        assertEquals("2026-10-20 10:22:18", rows[0].date)
    }

    @Test
    fun unknownMarkupYieldsNothingInsteadOfGarbage() {
        val page = "https://platon.vistula.edu.pl/x"
        val unknown = "<html><body><p>Brak uprawnień</p><a href=\"/Menu\">Menu</a></body></html>"
        assertTrue(PlatonTaskParser.parseElements(unknown, page).isEmpty())
        assertTrue(PlatonTaskParser.parseCourseCards(unknown, page).isEmpty())
        assertTrue(PlatonTaskParser.parseMessages(unknown).isEmpty())
        assertNull(PlatonTaskParser.parseDeadline(unknown).first)
    }
}

class PlatonDepthTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/platon/$name")) { "missing fixture $name" }.readBytes().toString(Charsets.UTF_8)

    @Test
    fun messageDetailGivesSubjectSenderDateAndTidyBody() {
        val detail = checkNotNull(PlatonTaskParser.parseMessageDetail(fixture("message.html")))
        assertEquals("Powiadomienie o ocenie zadania Lab 4 & więcej", detail.subject)
        assertEquals("Dr Anna Przykładowa", detail.sender)
        assertEquals("2026-10-20 00:53:47", detail.date)
        assertEquals(
            "Witaj Jan Przykładowy\nTwoje zadanie Lab 4 zostało ocenione. Otrzymana ocena: Bardzo dobry\n\nPozdrawiamy,\nZespół Portalu",
            detail.body,
        )
        assertNull(PlatonTaskParser.parseMessageDetail("<html><body>Brak uprawnień</body></html>"))
        assertNull(PlatonTaskParser.parseMessageDetail(null))
    }

    @Test
    fun messageUrlAcceptsOnlyPlainNumbers() {
        assertTrue(PlatonLinks.messageUrl("569561")!!.startsWith("https://platon.vistula.edu.pl/Wiadomosci/Wiadomosci/Wiadomosc?wiadomoscId=569561&"))
        assertEquals("https://platon.vistula.edu.pl", PlatonLinks.normalize(PlatonLinks.messageUrl("1")).substringBefore("/Wiadomosci"))
        for (bad in listOf("", "12a", "1&x=2", "../1", " 5", null, "1234567890123")) assertNull("must reject: $bad", PlatonLinks.messageUrl(bad))
    }

    @Test
    fun semesterSelectionKeepsNewestByDefaultAndHonoursChoices() {
        fun e(id: Int, label: String) = PlatonTaskParser.CourseEntry("https://platon.vistula.edu.pl/x/SzkoleniowaId/$id", label)
        val all = listOf(e(1, "A - 2026Z - 1 W"), e(2, "B - 2026L - 1 W"), e(3, "C - 2025Z - 1 W"), e(4, "D bez semestru"))
        assertEquals(listOf("2026Z", "2026L", "2025Z"), PlatonTaskParser.semesters(all))
        assertEquals(listOf(1, 4), PlatonTaskParser.inSemesters(all, emptySet()).map { it.url.substringAfterLast('/').toInt() })
        assertEquals(listOf(1, 3, 4), PlatonTaskParser.inSemesters(all, setOf("2026Z", "2025Z")).map { it.url.substringAfterLast('/').toInt() })
    }

    @Test
    fun crawlerReadsSelectedOlderSemestersAndReportsAvailableOnes() {
        val list = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaUzytkownik/SciezkiSzkoleniowe"
        fun course(id: Int, title: String, task: Int) =
            """<h1>Przedmiot - $title       Wróć do: x</h1><div class="dd3-content Passed"> Zad $task<br>
               <a href="/LmsProfil/SciezkaSzkoleniowaElementUzytkownik/RedirectToElement?sciezkaSzkoleniowaElementId=$task&amp;sciezkaSzkoleniowaElementTyp=ZADANIE">p</a>
               <span class="label label-success">Zadanie</span></div>"""
        val pages = mapOf(
            "https://platon.vistula.edu.pl/LmsProfil/X/ElementyBySciezkaSzkoleniowaId/1" to course(1, "Nowy - 2026Z", 11),
            "https://platon.vistula.edu.pl/LmsProfil/X/ElementyBySciezkaSzkoleniowaId/2" to course(2, "Stary - 2025Z", 22),
        )
        val listing = """<a href="/LmsProfil/X/ElementyBySciezkaSzkoleniowaId/1">Uruchom Nowy - 2026Z</a>
                         <a href="/LmsProfil/X/ElementyBySciezkaSzkoleniowaId/2">Uruchom Stary - 2025Z</a>"""
        fun run(selected: Set<String>) = PlatonCrawler.crawlAll(
            "https://platon.vistula.edu.pl/StronaStartowa",
            { url, form -> url to (if (form["Page"] == "1") listing else "") },
            selected,
        ) { url ->
            url to when {
                url == list -> """<input name="__RequestVerificationToken" value="t">"""
                pages.containsKey(url) -> pages.getValue(url)
                else -> throw java.io.IOException("404")
            }
        }
        val defaultRun = run(emptySet())
        assertEquals(listOf("Zad 11"), defaultRun.tasks.map { it.title })
        assertEquals(listOf("2026Z", "2025Z"), defaultRun.semesters)
        assertEquals(listOf("Zad 11", "Zad 22"), run(setOf("2026Z", "2025Z")).tasks.map { it.title })
    }

    @Test
    fun stateStoresSemestersMarksMessagesReadAndListsCourseAssignments() {
        val state = org.json.JSONObject()
        val crawled = PlatonCrawler.Result(emptyList(), emptyList(), emptyList(), listOf("2026Z", "2025Z"))
        PlatonState.apply(state, crawled, 1L)
        assertEquals(listOf("2026Z", "2025Z"), (0 until 2).map { state.getJSONArray("platonSemesters").getString(it) })

        state.put("platonMessages", org.json.JSONArray().put(org.json.JSONObject().put("id", "7").put("unread", true)).put(org.json.JSONObject().put("id", "8").put("unread", false)))
        assertTrue(PlatonState.markMessageRead(state, "7"))
        assertFalse(PlatonState.markMessageRead(state, "7"))
        assertFalse(PlatonState.markMessageRead(state, "8"))
        assertFalse(PlatonState.markMessageRead(state, "99"))
        assertFalse(state.getJSONArray("platonMessages").getJSONObject(0).getBoolean("unread"))

        val tasks = org.json.JSONArray()
            .put(org.json.JSONObject().put("source", "platon").put("course", "Kurs A - 2026Z").put("title", "T1"))
            .put(org.json.JSONObject().put("source", "platon").put("course", "Kurs B").put("title", "T2"))
            .put(org.json.JSONObject().put("course", "Kurs A - 2026Z").put("title", "ręczne"))
        assertEquals(listOf("T1"), PlatonState.assignmentsFor(tasks, " kurs a - 2026z ").map { it.getString("title") })
    }

    @Test
    fun notificationFilterKeepsOnlyChosenKinds() {
        val summary = PlatonState.Summary(3, 1, 1, 0, listOf("task"), listOf("c" to "5"), listOf("msg"))
        val none = summary.only(tasks = false, grades = false, messages = false)
        assertTrue(none.newTitles.isEmpty() && none.gradeChanges.isEmpty() && none.newMessages.isEmpty())
        val grades = summary.only(tasks = false, grades = true, messages = false)
        assertEquals(listOf("c" to "5"), grades.gradeChanges)
        assertTrue(grades.newTitles.isEmpty())
    }
}
