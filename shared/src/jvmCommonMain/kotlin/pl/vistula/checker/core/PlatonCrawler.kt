package pl.vistula.checker.core

import java.io.IOException

/**
 * Collects assignments starting from a page the student chose: course pages linked from it are read,
 * then each open assignment's own page for its deadline. [fetch] returns (final address, HTML) and may
 * throw; an [IOException] on one assignment only skips its deadline, anything else aborts the sync.
 */
object PlatonCrawler {
    private const val MAX_COURSES = 40
    private const val MAX_DETAILS = 60

    /** Training board whose server-rendered HTML lists the current courses (the home page loads them with JavaScript). */
    private const val LIST_PAGE = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaUzytkownik/SciezkiSzkoleniowe"
    private const val LIST_ENDPOINT = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaUzytkownik/SciezkaSzkoleniowaLista"
    private const val MAX_PAGES = 8
    private const val BOARD = "https://platon.vistula.edu.pl/TablicaSzkoleniowa"

    class Result(
        val tasks: List<PlatonTask>,
        val courses: List<PlatonTaskParser.PlatonCourse>,
        val messages: List<PlatonTaskParser.PlatonMessage> = emptyList(),
    )

    private const val MESSAGES_PAGE = "https://platon.vistula.edu.pl/Wiadomosci"
    private const val MESSAGES_ENDPOINT = "https://platon.vistula.edu.pl/Wiadomosci/Wiadomosci/WiadomosciList"

    /** First page of the inbox (20 newest messages); empty when the message centre cannot be read. */
    private fun inbox(
        fetch: (String) -> Pair<String, String>,
        post: (String, Map<String, String>) -> Pair<String, String>,
    ): List<PlatonTaskParser.PlatonMessage> = try {
        val token = PlatonTaskParser.formToken(fetch(MESSAGES_PAGE).second)
        if (token == null) emptyList() else PlatonTaskParser.parseMessages(
            post(
                MESSAGES_ENDPOINT,
                mapOf(
                    "__RequestVerificationToken" to token, "KatalogId" to "1", "KatalogSymbol" to "ODBIORCZA",
                    "Title" to "Moje wiadomości", "PodgladWiadomoscId" to "0", "ObjectId" to "0", "Typ" to "",
                    "KandydatId" to "0", "CzyBaza" to "False", "Search" to "", "Page" to "1", "PageChange" to "1",
                    "PageTotalCount" to "0",
                ),
            ).second,
        ).take(20)
    } catch (e: IOException) {
        emptyList()
    }

    @JvmStatic
    fun crawl(
        start: String,
        post: ((String, Map<String, String>) -> Pair<String, String>)? = null,
        fetch: (String) -> Pair<String, String>,
    ): List<PlatonTask> = crawlAll(start, post, fetch).tasks

    @JvmStatic
    fun crawlAll(
        start: String,
        post: ((String, Map<String, String>) -> Pair<String, String>)? = null,
        fetch: (String) -> Pair<String, String>,
    ): Result {
        val found = LinkedHashMap<String, PlatonTask>()
        var cards: List<PlatonTaskParser.PlatonCourse> = emptyList()
        if (post != null) {
            val listed = try { currentCourses(fetch, post) } catch (e: IOException) { Listing(emptyList(), emptyList()) }
            cards = listed.cards
            for (course in listed.entries) {
                val page = try { fetch(course.url) } catch (e: IOException) { continue }
                PlatonTaskParser.parseElements(page.second, page.first).forEach { found.putIfAbsent(it.externalId, it) }
            }
        }
        if (found.isEmpty()) legacy(start, fetch, found)
        val messages = if (post != null) inbox(fetch, post) else emptyList()
        return Result(if (found.isEmpty()) emptyList() else details(found, fetch), cards, messages)
    }

    private class Listing(val entries: List<PlatonTaskParser.CourseEntry>, val cards: List<PlatonTaskParser.PlatonCourse>)

    /** All pages of the student's course list, reduced to the current semester. */
    private fun currentCourses(
        fetch: (String) -> Pair<String, String>,
        post: (String, Map<String, String>) -> Pair<String, String>,
    ): Listing {
        val html = fetch(LIST_PAGE).second
        val token = PlatonTaskParser.formToken(html) ?: return Listing(emptyList(), emptyList())
        val all = LinkedHashMap<String, PlatonTaskParser.CourseEntry>()
        val cards = LinkedHashMap<String, PlatonTaskParser.PlatonCourse>()
        for (page in 1..MAX_PAGES) {
            val form = mapOf(
                "__RequestVerificationToken" to token, "CzyWidokListy" to "False", "Search" to "", "SortColumn" to "",
                "SortOrder" to "", "ViewType" to "Lista", "KategoriaNazwa" to "", "KategoriaSelectedId" to "0",
                "SciezkaSzkoleniowaTerminSelectedId" to "0", "Page" to page.toString(),
            )
            val result = post(LIST_ENDPOINT, form)
            val fresh = PlatonTaskParser.courseEntries(result.second, result.first).filter { it.url !in all }
            if (fresh.isEmpty()) break
            fresh.forEach { all[it.url] = it }
            PlatonTaskParser.parseCourseCards(result.second, result.first).forEach { cards.putIfAbsent(it.url, it) }
        }
        val current = PlatonTaskParser.currentSemester(all.values.toList()).take(MAX_COURSES)
        val urls = current.map { it.url }.toSet()
        return Listing(current, cards.values.filter { it.url in urls })
    }

    private fun legacy(start: String, fetch: (String) -> Pair<String, String>, found: MutableMap<String, PlatonTask>) {
        for (entry in listOf(start, BOARD).distinct()) {
            val (url, html) = fetch(entry)
            PlatonTaskParser.parseElements(html, url).forEach { found.putIfAbsent(it.externalId, it) }
            for (course in PlatonTaskParser.courseLinks(html, url, MAX_COURSES)) {
                val page = try { fetch(course) } catch (e: IOException) { continue }
                PlatonTaskParser.parseElements(page.second, page.first).forEach { found.putIfAbsent(it.externalId, it) }
            }
            if (found.isNotEmpty()) break
        }
    }

    private fun details(found: Map<String, PlatonTask>, fetch: (String) -> Pair<String, String>): List<PlatonTask> {
        val result = found.values.toMutableList()
        var details = 0
        for (i in result.indices) {
            val task = result[i]
            if (!task.needsDetail || details >= MAX_DETAILS) continue
            details++
            val page = try { fetch(task.url) } catch (e: IOException) { continue }
            val (due, time) = PlatonTaskParser.parseDeadline(page.second)
            result[i] = task.copy(due = due, dueTime = time, needsDetail = false)
        }
        return result
    }
}
