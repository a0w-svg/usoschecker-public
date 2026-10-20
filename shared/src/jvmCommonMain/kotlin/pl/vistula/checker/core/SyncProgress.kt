package pl.vistula.checker.core

/** Presentation-time translation also covers timetable progress emitted from worker threads. */
object SyncProgress {
    private val labels = mapOf(
        "Pobieranie semestrów" to Pair("Loading semesters", "Завантаження семестрів"),
        "Pobieranie punktów ECTS" to Pair("Loading ECTS credits", "Завантаження кредитів ECTS"),
        "Pobieranie kalendarza semestrów" to Pair("Loading academic calendar", "Завантаження академічного календаря"),
        "Pobieranie subkont studenta" to Pair("Loading payment accounts", "Завантаження платіжних рахунків"),
        "Pobieranie należności i rat" to Pair("Loading payments and instalments", "Завантаження платежів і внесків"),
        "Pobieranie prac dyplomowych" to Pair("Loading theses", "Завантаження дипломних робіт"),
        "Pobieranie sprawdzianów cząstkowych" to Pair("Loading coursework results", "Завантаження результатів контрольних робіт"),
        "Pobieranie komunikatów uczelni" to Pair("Loading university news", "Завантаження новин університету"),
        "Pobieranie ocen" to Pair("Loading grades", "Завантаження оцінок"),
        "Pobieranie nazwisk prowadzących" to Pair("Loading lecturers", "Завантаження викладачів"),
        "Odczyt planu zajęć" to Pair("Reading timetable", "Читання розкладу занять"),
        "Rozpoznawanie typów zajęć" to Pair("Loading class types", "Завантаження типів занять"),
        "Rozpoczęcie synchronizacji" to Pair("Starting refresh", "Початок оновлення")
    )
    fun localized(message: String, language: String?): String {
        val lang = ApiLanguage.normalize(language)
        if (lang == "pl") return message
        val timetable = Regex("Pobieranie planu · (\\d+/\\d+) tygodni").matchEntire(message)
        if (timetable != null) return if (lang == "uk") "Завантаження розкладу · ${timetable.groupValues[1]} тижнів"
            else "Loading timetable · ${timetable.groupValues[1]} weeks"
        val label = labels[message.substringBefore(" · ")] ?: return if (lang == "uk") "Оновлення даних…" else "Refreshing data…"
        return (if (lang == "uk") label.second else label.first) + message.substringAfter(" · ", "").let { if (it.isEmpty()) "" else " · $it" }
    }
}
