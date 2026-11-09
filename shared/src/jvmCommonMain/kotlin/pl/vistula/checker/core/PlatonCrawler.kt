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

    /** Requests in flight at once; Platon is a shared university server, so this stays modest. */
    private const val PARALLEL = 5

    /** Training board whose server-rendered HTML lists the current courses (the home page loads them with JavaScript). */
    private const val LIST_PAGE = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaUzytkownik/SciezkiSzkoleniowe"
    private const val LIST_ENDPOINT = "https://platon.vistula.edu.pl/LmsProfil/SciezkaSzkoleniowaUzytkownik/SciezkaSzkoleniowaLista"
    private const val MAX_PAGES = 8
    private const val BOARD = "https://platon.vistula.edu.pl/TablicaSzkoleniowa"

    class Result(
        val tasks: List<PlatonTask>,
        val courses: List<PlatonTaskParser.PlatonCourse>,
        val messages: List<PlatonTaskParser.PlatonMessage> = emptyList(),
        /** Every semester code found in the student's course list, newest first (for the semester picker). */
        val semesters: List<String> = emptyList(),
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
    ): List<PlatonTask> = crawlAll(start, post, fetch = fetch).tasks

    /** Deadline already known for an assignment from an earlier sync. */
    class KnownDeadline(val due: java.time.LocalDate, val time: String)

    /**
     * Reads everything the app shows. The inbox is read while the courses are, course pages and assignment pages
     * are read several at a time, and a submitted assignment whose deadline is already [known] is not fetched again.
     */
    @JvmStatic
    fun crawlAll(
        start: String,
        post: ((String, Map<String, String>) -> Pair<String, String>)? = null,
        semesters: Set<String> = emptySet(),
        known: Map<String, KnownDeadline> = emptyMap(),
        fetch: (String) -> Pair<String, String>,
    ): Result {
        val found = LinkedHashMap<String, PlatonTask>()
        var cards: List<PlatonTaskParser.PlatonCourse> = emptyList()
        var available: List<String> = emptyList()
        val inboxAhead = if (post != null) Parallel.start { inbox(fetch, post) } else null
        if (post != null) {
            val listed = try { currentCourses(fetch, post, semesters) } catch (e: IOException) { Listing(emptyList(), emptyList(), emptyList()) }
            cards = listed.cards
            available = listed.semesters
            val pages = Parallel.map(listed.entries, PARALLEL) { fetch(it.url) }
            for (page in pages) {
                if (page == null) continue
                PlatonTaskParser.parseElements(page.second, page.first).forEach { found.putIfAbsent(it.externalId, it) }
            }
        }
        if (found.isEmpty()) legacy(start, fetch, found)
        val tasks = if (found.isEmpty()) emptyList() else details(found, known, fetch)
        return Result(tasks, cards, inboxAhead?.await() ?: emptyList(), available)
    }

    private class Listing(
        val entries: List<PlatonTaskParser.CourseEntry>,
        val cards: List<PlatonTaskParser.PlatonCourse>,
        val semesters: List<String>,
    )

    /** All pages of the student's course list, reduced to the current semester. */
    private fun currentCourses(
        fetch: (String) -> Pair<String, String>,
        post: (String, Map<String, String>) -> Pair<String, String>,
        selected: Set<String>,
    ): Listing {
        val html = fetch(LIST_PAGE).second
        val token = PlatonTaskParser.formToken(html) ?: return Listing(emptyList(), emptyList(), emptyList())
        val all = LinkedHashMap<String, PlatonTaskParser.CourseEntry>()
        val cards = LinkedHashMap<String, PlatonTaskParser.PlatonCourse>()
        // Pages are requested in waves of PARALLEL; a page past the end is empty, which ends the walk.
        var next = 1
        var more = true
        while (more && next <= MAX_PAGES) {
            val wave = (next until minOf(next + PARALLEL, MAX_PAGES + 1)).toList()
            next += wave.size
            val results = Parallel.map(wave, PARALLEL) { page ->
                post(
                    LIST_ENDPOINT,
                    mapOf(
                        "__RequestVerificationToken" to token, "CzyWidokListy" to "False", "Search" to "", "SortColumn" to "",
                        "SortOrder" to "", "ViewType" to "Lista", "KategoriaNazwa" to "", "KategoriaSelectedId" to "0",
                        "SciezkaSzkoleniowaTerminSelectedId" to "0", "Page" to page.toString(),
                    ),
                )
            }
            for (result in results) {
                if (result == null) { more = false; break }
                val fresh = PlatonTaskParser.courseEntries(result.second, result.first).filter { it.url !in all }
                if (fresh.isEmpty()) { more = false; break }
                fresh.forEach { all[it.url] = it }
                PlatonTaskParser.parseCourseCards(result.second, result.first).forEach { cards.putIfAbsent(it.url, it) }
            }
        }
        val chosen = PlatonTaskParser.inSemesters(all.values.toList(), selected).take(MAX_COURSES)
        val urls = chosen.map { it.url }.toSet()
        return Listing(chosen, cards.values.filter { it.url in urls }, PlatonTaskParser.semesters(all.values.toList()))
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

    private fun details(
        found: Map<String, PlatonTask>,
        known: Map<String, KnownDeadline>,
        fetch: (String) -> Pair<String, String>,
    ): List<PlatonTask> {
        val result = found.values.toMutableList()
        val wanted = ArrayList<Int>()
        for (i in result.indices) {
            val task = result[i]
            if (!task.needsDetail) continue
            val earlier = known[task.externalId]
            if (task.submitted && earlier != null) {
                // Submitted work keeps its deadline; no need to open the page again.
                result[i] = task.copy(due = earlier.due, dueTime = earlier.time, needsDetail = false)
            } else if (wanted.size < MAX_DETAILS) {
                wanted.add(i)
            }
        }
        val pages = Parallel.map(wanted.map { result[it].url }, PARALLEL) { fetch(it) }
        for ((k, page) in pages.withIndex()) {
            if (page == null) continue
            val i = wanted[k]
            val (due, time) = PlatonTaskParser.parseDeadline(page.second)
            result[i] = result[i].copy(due = due, dueTime = time, needsDetail = false)
        }
        return result
    }
}
