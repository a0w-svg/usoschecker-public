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
