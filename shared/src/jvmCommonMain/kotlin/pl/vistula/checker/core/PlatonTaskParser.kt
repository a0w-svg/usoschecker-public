package pl.vistula.checker.core

import java.net.URI
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** One assignment read from a Platon page the signed-in student opened. */
data class PlatonTask(
    val url: String,
    val title: String,
    val course: String,
    val due: LocalDate?,
    val dueTime: String,
    val submitted: Boolean,
    val progress: Int?,
    /** True when the deadline is only on the assignment's own page and still has to be read. */
    val needsDetail: Boolean = false,
    /** Platon's own wording of the state, e.g. "Oczekuje na ocenę". */
    val status: String = "",
) {
    /** Stable key of the assignment: its normalized Platon address without a fragment. */
    val externalId: String get() = "platon:" + url.substringBefore('#')
}

/**
 * Best-effort reader of Platon HTML lists (table rows or list items that link to an assignment).
 * Platon has no public API, so the parser only trusts links that [PlatonLinks] accepts and
 * never executes or follows anything it reads. Unknown layouts yield an empty list, not an error.
 */
object PlatonTaskParser {
    const val MAX_TASKS = 200
    private const val MAX_HTML = 2_000_000

    private val SCRIPTS = Regex("(?is)<(script|style|noscript)\\b.*?</\\1\\s*>")
    private val BLOCKS = Regex("(?is)<(tr|li)\\b[^>]*>(.*?)</\\1\\s*>")
    private val ANCHOR = Regex("(?is)<a\\b([^>]*)>(.*?)</a\\s*>")
    private val HREF = Regex("(?is)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    private val TAGS = Regex("(?s)<[^>]*>")
    private val SPACES = Regex("\\s+")
    private val DATE_PL = Regex("(\\d{1,2})[.](\\d{1,2})[.](\\d{4})(?:\\s*,?\\s*(?:godz\\.?\\s*)?(\\d{1,2}:\\d{2}))?")
    private val DATE_ISO = Regex("(\\d{4})-(\\d{2})-(\\d{2})(?:[ T](\\d{1,2}:\\d{2}))?")
    private val DEADLINE_WORDS = Regex("(?i)(termin|deadline|due|do dnia|ostateczn|zakończeni|closes|until)[^0-9]{0,40}$")
    private val SUBMITTED = Regex("(?i)\\b(oddan\\w*|wykonan\\w*|zaliczon\\w*|ukończon\\w*|zakończon\\w*|ocenion\\w*|submitted|completed|done|graded|finished)\\b")
    private val NEGATED = Regex("(?i)\\b(nie|brak|not|no)\\s+(oddan\\w*|wykonan\\w*|zaliczon\\w*|submitted|completed|done)")
    private val PERCENT = Regex("(\\d{1,3})\\s*%")
    private val ARIA_NOW = Regex("(?i)aria-valuenow\\s*=\\s*[\"'](\\d{1,3})")

    private val ELEMENT_START = Regex("(?is)<div\\b[^>]*class=\"([^\"]*\\bdd3-content\\b[^\"]*)\"[^>]*>")
    private val LABEL = Regex("(?is)<span[^>]*class=\"label label-success\"[^>]*>(.*?)</span>")
    private val COURSE_TITLE = Regex("(?is)Przedmiot\\s*-\\s*(.+?)\\s*(?:<|Wróć do)")
    private val COURSE_LINK = Regex("(?i)SzkoleniowaId/\\d+")
    private val DETAIL_DEADLINE = Regex("(?i)(?:Data oddania|Termin(?: oddania| zakończenia)?|Deadline|Due date)\\s*:?\\s*(\\d{1,2})[.](\\d{1,2})[.](\\d{4})(?:\\s*,?\\s*(\\d{1,2}:\\d{2}))?")
    private val TASK_TYPES = setOf("zadanie", "test", "egzamin", "quiz", "assignment", "exam")

    private val STATUS = Regex("(?is)<span[^>]*rel=\"tooltip\"[^>]*title=\"([^\"]*)\"")
    private val SEMESTER = Regex("(\\d{4})([ZL])")

    /** A course from the paged "Przedmioty" list: its page and the label that carries the semester code. */
    class CourseEntry(val url: String, val label: String)

    @JvmStatic
    fun courseEntries(html: String?, pageUrl: String): List<CourseEntry> {
        if (html.isNullOrBlank()) return emptyList()
        val base = try { URI(PlatonLinks.normalize(pageUrl)) } catch (e: IllegalArgumentException) { return emptyList() }
        val found = LinkedHashMap<String, CourseEntry>()
        for (anchor in ANCHOR.findAll(html.take(MAX_HTML))) {
            val link = link(anchor.groupValues[1], base) ?: continue
            if (!COURSE_LINK.containsMatchIn(link)) continue
            found.putIfAbsent(link.substringBefore('#'), CourseEntry(link.substringBefore('#'), text(anchor.groupValues[2])))
        }
        return found.values.toList()
    }

    /** Keeps the courses of the newest semester found (and courses without a semester code). */
    @JvmStatic
    fun currentSemester(courses: List<CourseEntry>): List<CourseEntry> {
        fun rank(label: String): Int? = SEMESTER.find(label)?.let { it.groupValues[1].toInt() * 2 + if (it.groupValues[2] == "Z") 1 else 0 }
        val newest = courses.mapNotNull { rank(it.label) }.maxOrNull() ?: return courses
        return courses.filter { val r = rank(it.label); r == null || r == newest }
    }

    /** Anti-forgery token of a Platon form page; never logged or stored. */
    @JvmStatic
    fun formToken(html: String?): String? =
        html?.let { Regex("(?is)name=\"__RequestVerificationToken\"[^>]*value=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }

    /** A course card from the "Przedmioty" list: progress, grade and launch count shown by Platon. */
    data class PlatonCourse(val url: String, val title: String, val progress: Int, val grade: String, val runs: Int, val badge: String)

    private val CARD_START = Regex("(?i)class=\"card kafelek\"")
    private val CARD_PROGRESS = Regex("(?is)Progress</div>\\s*<div>\\s*(\\d{1,3})\\s*%")
    private val CARD_RUNS = Regex("(?i)(?:Liczba uruchomień|Launches)\\s*:\\s*(\\d+)")
    private val CARD_GRADE = Regex("(?i)(?:Ocena|Grade)\\s*:\\s*([^<]+)")
    private val CARD_BADGE = Regex("(?is)topBadges1[^>]*>(.*?)</span>")

    @JvmStatic
    fun parseCourseCards(html: String?, pageUrl: String): List<PlatonCourse> {
        if (html.isNullOrBlank()) return emptyList()
        val base = try { URI(PlatonLinks.normalize(pageUrl)) } catch (e: IllegalArgumentException) { return emptyList() }
        val clean = html.take(MAX_HTML)
        val starts = CARD_START.findAll(clean).map { it.range.first }.toList()
        val result = LinkedHashMap<String, PlatonCourse>()
        for ((i, from) in starts.withIndex()) {
            val segment = clean.substring(from, if (i + 1 < starts.size) starts[i + 1] else minOf(clean.length, from + 6000))
            val anchor = ANCHOR.findAll(segment).firstNotNullOfOrNull { a ->
                link(a.groupValues[1], base)?.takeIf { COURSE_LINK.containsMatchIn(it) }?.let { it to a.groupValues[2] }
            } ?: continue
            val title = text(anchor.second).removePrefix("Uruchom").removePrefix("Launch").trim().take(160)
            if (title.length < 2) continue
            val url = anchor.first.substringBefore('#')
            result.putIfAbsent(url, PlatonCourse(
                url, title,
                CARD_PROGRESS.find(segment)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100) ?: 0,
                CARD_GRADE.find(segment)?.groupValues?.get(1)?.let { decode(it).trim() }?.take(40) ?: "",
                CARD_RUNS.find(segment)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                CARD_BADGE.find(segment)?.let { text(it.groupValues[1]) }?.take(40) ?: "",
            ))
        }
        return result.values.toList()
    }

    /** One inbox row of Platon's message centre. */
    data class PlatonMessage(val id: String, val sender: String, val subject: String, val date: String, val unread: Boolean)

    private val MESSAGE_ROW = Regex("(?is)<tr\\b[^>]*data-wiadomoscId=\"(\\d{1,12})\"[^>]*>(.*?)</tr\\s*>")
    private val MESSAGE_SUBJECT = Regex("(?is)<td[^>]*class=\"[^\"]*mail-subject[^\"]*\"[^>]*>(.*?)</td>")
    private val UNREAD = Regex("(?i)Nieodczytana|Unread")
    private val MESSAGE_DATE = Regex("(\\d{4}-\\d{2}-\\d{2}(?:[ T]\\d{2}:\\d{2}(?::\\d{2})?)?)")

    @JvmStatic
    fun parseMessages(html: String?): List<PlatonMessage> {
        if (html.isNullOrBlank()) return emptyList()
        val result = LinkedHashMap<String, PlatonMessage>()
        for (row in MESSAGE_ROW.findAll(SCRIPTS.replace(html.take(MAX_HTML), " "))) {
            if (result.size >= 50) break
            val body = row.groupValues[2]
            val subject = MESSAGE_SUBJECT.find(body)?.let { text(it.groupValues[1]) } ?: continue
            val sender = ANCHOR.find(body)?.let { text(it.groupValues[2]) }.orEmpty()
            val unread = UNREAD.containsMatchIn(body)
            val date = MESSAGE_DATE.find(text(body.substringAfter("mail-subject")))?.groupValues?.get(1).orEmpty()
            if (subject.isNotEmpty()) {
                result.putIfAbsent(row.groupValues[1], PlatonMessage(row.groupValues[1], sender.removeSuffix(" Nieodczytana").trim().take(80), subject.take(200), date, unread))
            }
        }
        return result.values.toList()
    }

    /** Links to course pages (learning paths) found on a Platon page, at most [limit]. */
    @JvmStatic
    fun courseLinks(html: String?, pageUrl: String, limit: Int = 40): List<String> {
        if (html.isNullOrBlank()) return emptyList()
        val base = try { URI(PlatonLinks.normalize(pageUrl)) } catch (e: IllegalArgumentException) { return emptyList() }
        val found = LinkedHashSet<String>()
        for (anchor in ANCHOR.findAll(html.take(MAX_HTML))) {
            val link = link(anchor.groupValues[1], base) ?: continue
            if (COURSE_LINK.containsMatchIn(link)) found.add(link.substringBefore('#'))
            if (found.size >= limit) break
        }
        return found.toList()
    }

    /** Assignments listed as Platon "elements" (`dd3-content` blocks) on a course page. */
    @JvmStatic
    fun parseElements(html: String?, pageUrl: String): List<PlatonTask> {
        if (html.isNullOrBlank()) return emptyList()
        val base = try { URI(PlatonLinks.normalize(pageUrl)) } catch (e: IllegalArgumentException) { return emptyList() }
        val clean = SCRIPTS.replace(html.take(MAX_HTML), " ")
        val course = COURSE_TITLE.find(clean)?.let { text(it.groupValues[1]) }?.take(120) ?: ""
        val starts = ELEMENT_START.findAll(clean).toList()
        val result = LinkedHashMap<String, PlatonTask>()
        for ((i, start) in starts.withIndex()) {
            if (result.size >= MAX_TASKS) break
            var end = if (i + 1 < starts.size) starts[i + 1].range.first else minOf(clean.length, start.range.last + 4000)
            clean.indexOf("class=\"ibox\"", start.range.last).let { if (it in 0 until end) end = it }
            val segment = clean.substring(start.range.last + 1, end)
            val type = LABEL.find(segment)?.let { text(it.groupValues[1]) }?.lowercase(Locale.ROOT) ?: continue
            if (type !in TASK_TYPES) continue
            val title = text(segment.substringBefore('<')).take(200)
            val url = ANCHOR.findAll(segment).mapNotNull { link(it.groupValues[1], base) }
                .firstOrNull { it.contains("RedirectToElement", ignoreCase = true) } ?: continue
            if (title.length < 2) continue
            val state = start.groupValues[1]
            val passed = state.contains("Passed", ignoreCase = true)
            val submitted = passed || state.contains("To_Be_Evaluated", ignoreCase = true)
            val status = STATUS.find(segment)?.let { decode(it.groupValues[1]).trim() }?.take(80) ?: ""
            val task = PlatonTask(url, title, course, null, "", submitted, if (submitted) 100 else null, needsDetail = !passed, status = status)
            result.putIfAbsent(task.externalId, task)
        }
        return result.values.toList()
    }

    /** Deadline ("Data oddania") from an assignment's own page. */
    @JvmStatic
    fun parseDeadline(html: String?): Pair<LocalDate?, String> {
        if (html.isNullOrBlank()) return null to ""
        val m = DETAIL_DEADLINE.find(text(SCRIPTS.replace(html.take(MAX_HTML), " "))) ?: return null to ""
        return date(m.groupValues[3], m.groupValues[2], m.groupValues[1]) to time(m.groupValues[4])
    }

    @JvmStatic
    fun parse(html: String?, pageUrl: String): List<PlatonTask> {
        if (html.isNullOrBlank()) return emptyList()
        val base = try { URI(PlatonLinks.normalize(pageUrl)) } catch (e: IllegalArgumentException) { return emptyList() }
        val clean = SCRIPTS.replace(if (html.length > MAX_HTML) html.substring(0, MAX_HTML) else html, " ")
        val result = LinkedHashMap<String, PlatonTask>()
        for (block in BLOCKS.findAll(clean)) {
            if (result.size >= MAX_TASKS) break
            val task = readBlock(block.groupValues[2], base) ?: continue
            result.putIfAbsent(task.externalId, task)
        }
        return result.values.toList()
    }

    private fun readBlock(inner: String, base: URI): PlatonTask? {
        if (inner.contains("<tr", ignoreCase = true) || inner.contains("<li", ignoreCase = true)) return null
        var url: String? = null
        val labels = ArrayList<String>()
        for (anchor in ANCHOR.findAll(inner)) {
            val text = text(anchor.groupValues[2])
            val link = link(anchor.groupValues[1], base)
            if (link != null && text.length >= 3) {
                if (url == null) url = link
                labels.add(text)
            }
        }
        if (url == null || labels.isEmpty()) return null
        val plain = text(inner)
        val (due, time) = deadline(plain)
        val progress = (ARIA_NOW.find(inner)?.groupValues?.get(1) ?: PERCENT.find(plain)?.groupValues?.get(1))
            ?.toIntOrNull()?.takeIf { it in 0..100 }
        val submitted = !NEGATED.containsMatchIn(plain) && SUBMITTED.containsMatchIn(plain)
        return PlatonTask(
            url = url,
            title = labels[0].take(200),
            course = labels.getOrNull(1)?.takeIf { it != labels[0] }?.take(120) ?: "",
            due = due,
            dueTime = time,
            submitted = submitted,
            progress = if (submitted) 100 else progress,
        )
    }

    private fun link(attributes: String, base: URI): String? {
        val raw = HREF.find(attributes)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } ?: return null
        val value = decode(raw).trim()
        if (value.isEmpty() || value.startsWith("#") || value.startsWith("javascript:", ignoreCase = true)) return null
        return try {
            PlatonLinks.normalize(base.resolve(value).toString()).takeIf { it != PlatonLinks.HOME && it != PlatonLinks.HOME.trimEnd('/') }
        } catch (e: Exception) {
            null
        }
    }

    /** Prefers the date that follows a deadline word; otherwise the first date in the row. */
    private fun deadline(plain: String): Pair<LocalDate?, String> {
        val found = ArrayList<Triple<Int, LocalDate, String>>()
        for (m in DATE_PL.findAll(plain)) {
            val date = date(m.groupValues[3], m.groupValues[2], m.groupValues[1]) ?: continue
            found.add(Triple(m.range.first, date, time(m.groupValues[4])))
        }
        for (m in DATE_ISO.findAll(plain)) {
            val date = date(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: continue
            found.add(Triple(m.range.first, date, time(m.groupValues[4])))
        }
        if (found.isEmpty()) return null to ""
        found.sortBy { it.first }
        val chosen = found.firstOrNull { DEADLINE_WORDS.containsMatchIn(plain.substring(0, it.first)) } ?: found[0]
        return chosen.second to chosen.third
    }

    private fun date(year: String, month: String, day: String): LocalDate? = try {
        LocalDate.of(year.toInt(), month.toInt(), day.toInt())
    } catch (e: Exception) {
        null
    }

    private fun time(value: String): String = try {
        if (value.isEmpty()) "" else LocalTime.parse(value.padStart(5, '0')).toString()
    } catch (e: Exception) {
        ""
    }

    private fun text(html: String): String = decode(TAGS.replace(html, " ")).replace(SPACES, " ").trim()

    private fun decode(value: String): String {
        if (!value.contains('&')) return value
        return Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").replace(value) { m ->
            val name = m.groupValues[1]
            when {
                name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let(::chr)
                name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::chr)
                else -> when (name.lowercase(Locale.ROOT)) {
                    "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                    else -> null
                }
            } ?: m.value
        }
    }

    private fun chr(code: Int): String? = if (code in 1..0x10FFFF && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else null
}
