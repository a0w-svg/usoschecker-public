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
    fun errorKind(error: Exception): String = when (error) {
        is pl.vistula.checker.UsosClient.ApiException -> "http"
        is java.net.SocketTimeoutException -> "timeout"
        is org.json.JSONException, is java.time.format.DateTimeParseException -> "format"
        is java.io.IOException -> "network"
        else -> "operation"
    }

    fun error(error: Exception, language: String?): String {
        val cause = pl.vistula.checker.SharedRepository.cause(error)
        val state = org.json.JSONObject().put("errorKind", errorKind(cause))
        if (cause is pl.vistula.checker.UsosClient.ApiException) state.put("httpStatus", cause.status).put("authorizationError", cause.authorization)
        return cachedError(state, language)
    }

    fun cachedError(state: org.json.JSONObject, language: String?): String {
        val status = state.optInt("httpStatus")
        val key = when {
            state.optBoolean("authorizationError") -> "account_session_expired"
            state.optString("errorKind") == "http" -> when {
                status == 401 -> "error_signature"
                status == 403 -> "error_access"
                status == 429 -> "error_rate_limit"
                status == 408 -> "error_timeout"
                status >= 500 -> "error_server"
                else -> "error_operation"
            }
            else -> when (state.optString("errorKind")) {
                "timeout" -> "error_timeout"
                "format" -> "error_format"
                "network" -> "error_network"
                else -> "error_operation"
            }
        }
        return ApiLanguage.`in`(language) {
            when (key) {
                "account_session_expired" -> ApiLanguage.label("Sesja wygasła. Odłącz konto poniżej i zaloguj się ponownie. Zapisane dane pozostają dostępne do czasu odłączenia.", "Session expired. Disconnect account below and log in again. Cached data remains accessible until disconnected.", "Сесія закінчилася. Відключіть обліковий запис нижче та увійдіть знову. Збережені дані залишаються доступними до відключення.")
                "error_signature" -> ApiLanguage.label("USOS nie zaakceptował żądania. Sprawdź klucz aplikacji i zegar telefonu.", "USOS rejected the request. Check your application credentials and phone clock.", "USOS відхилив запит. Перевірте ключ застосунку та годинник телефону.")
                "error_access" -> ApiLanguage.label("Brak dostępu. Sprawdź zgody konta w ustawieniach.", "Access denied. Check your account permissions in Settings.", "Немає доступу. Перевірте дозволи облікового запису в налаштуваннях.")
                "error_rate_limit" -> ApiLanguage.label("Serwer prosi o przerwę. Spróbuj ponownie później.", "The server requests a pause. Try again later.", "Сервер просить зачекати. Спробуйте пізніше.")
                "error_timeout" -> ApiLanguage.label("Serwer nie odpowiedział na czas. Spróbuj ponownie.", "The server did not respond in time. Try again.", "Сервер не відповів вчасно. Спробуйте ще раз.")
                "error_server" -> ApiLanguage.label("Serwer jest chwilowo niedostępny. Spróbuj później.", "The server is temporarily unavailable. Try again later.", "Сервер тимчасово недоступний. Спробуйте пізніше.")
                "error_format" -> ApiLanguage.label("Odpowiedź ma nieznany format. Zachowano poprzednie dane.", "The response format was not recognized. Previous data was preserved.", "Невідомий формат відповіді. Попередні дані збережено.")
                "error_network" -> ApiLanguage.label("Nie udało się połączyć. Sprawdź internet i spróbuj ponownie.", "Could not connect. Check your connection and try again.", "Не вдалося підключитися. Перевірте інтернет і спробуйте ще раз.")
                else -> ApiLanguage.label("Nie udało się wykonać operacji. Sprawdź dane i spróbuj ponownie.", "The operation could not be completed. Check the details and try again.", "Не вдалося виконати дію. Перевірте дані та спробуйте ще раз.")
            }
        }
    }

}
