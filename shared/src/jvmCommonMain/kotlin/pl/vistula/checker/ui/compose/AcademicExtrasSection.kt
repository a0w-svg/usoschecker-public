package pl.vistula.checker.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.DemoStudentServices
import pl.vistula.checker.KeyValueStore
import pl.vistula.checker.LecturerWriteActions
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.StudentServiceLoader
import pl.vistula.checker.ToolServiceData
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.AccountRole
import pl.vistula.checker.core.CampusAffiliation
import pl.vistula.checker.core.FeatureAccess
import pl.vistula.checker.core.LecturerServices
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.SyncProgress

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AcademicExtrasSection(
    state: JSONObject,
    repository: SharedRepository,
    language: String,
    section: String,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    val tr = Translator(language)
    val services = state.optJSONObject("studentServices")
    var courseQuery by remember { mutableStateOf("") }
    var courseSearchResults by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    var selectedCourse by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var roomIdQuery by remember { mutableStateOf("") }
    var roomScheduleResult by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    var buildingRooms by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONArray>?>(null) }
    var registrationRounds by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONArray>?>(null) }
    var tokenDetails by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var selectedExam by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var selectedProgramme by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var selectedStudentProgrammeId by remember(state.optString("accountSession")) { mutableStateOf("") }
    var programmeStages by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    var surveyDetail by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    var mailDetail by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    var selectedThesis by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var selectedDormitory by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var selectedAllocationPeriod by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var selectedGuidePage by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    fun loadSurvey(id: String) {
        if (state.optString("token") == "demo") surveyDetail = DemoStudentServices.demoSurveyDetail(id)
        else runAsync {
            val creds = repository.read()
            check(SharedRepository.sameAccount(state, creds))
            val detail = ToolServiceData.fetchSurveyDetail(UsosClient(creds), id)
            check(SharedRepository.sameAccount(creds, repository.read()))
            surveyDetail = detail
        }
    }
    fun loadMail(id: String) {
        if (state.optString("token") == "demo") mailDetail = DemoStudentServices.demoMailMessage(id)
        else runAsync {
            val creds = repository.read()
            check(SharedRepository.sameAccount(state, creds))
            val detail = ToolServiceData.fetchMailMessage(UsosClient(creds), id)
            check(SharedRepository.sameAccount(creds, repository.read()))
            mailDetail = detail
        }
    }
    var selectedStage by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    val live = state.optString("token").isNotEmpty() && state.optString("token") != "demo" && !state.optBoolean("authorizationError")
    val calendarCampus = CampusAffiliation.selected(state)
    var calendarFrom by remember { mutableStateOf(LocalDate.now(UsosClient.ZONE).toString()) }
    var calendarUntil by remember { mutableStateOf(LocalDate.now(UsosClient.ZONE).plusDays(28).toString()) }
    var calendarError by remember { mutableStateOf("") }
    var calendarResults by remember(state.optString("accountSession"), calendarCampus) { mutableStateOf<JSONObject?>(null) }
    var calendarEvent by remember(state.optString("accountSession"), calendarCampus) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    fun loadCalendarEvent(id: String, title: String) {
        if (state.optString("token") == "demo") calendarEvent = title to DemoStudentServices.demoCalendarEvent(id)
        else runAsync {
            val creds = repository.read()
            check(SharedRepository.sameAccount(state, creds))
            val detail = ToolServiceData.fetchCalendarEvent(UsosClient(creds), id)
            val latest = repository.read()
            check(SharedRepository.sameAccount(creds, latest) && CampusAffiliation.selected(latest) == calendarCampus)
            calendarEvent = title to detail
        }
    }
    LaunchedEffect(section, state.optString("accountSession"), live) {
        if (!live) return@LaunchedEffect
        val wanted = when (section) {
            "services" -> listOf("registrations", "tokenRegistrations", "exams", "academicCalendar", "mobility", "placementAndSlips", "campusGuide", "communications", "theses")
            "registrations" -> listOf("registrations", "tokenRegistrations")
            "academicCalendar", "exams" -> listOf("exams", "academicCalendar")
            else -> listOf(section)
        }.filter { sec ->
            val okScope = when (sec) {
                "registrations", "tokenRegistrations", "exams" -> !AccountRole.lecturerOnly(state) && StudentServices.hasScope(state.optString("scopes"), "studies")
                else -> true
            }
            okScope && (services?.optJSONObject(sec)?.optLong("attemptAt") ?: 0L) == 0L
        }
        if (wanted.isNotEmpty()) {
            runAsync {
                for (sec in wanted) {
                    runCatching { StudentServiceLoader.refresh(repository, sec) }
                }
                onReloadState()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (section == "registrations" || section == "services") {
            SectionHead(
                title = tr("Rejestracje i koszyk zapisów (tylko odczyt)", "Course registrations & cart (read-only)", "Реєстрації та кошик записів (лише читання)"),
                onClick = {
                    runAsync {
                        StudentServiceLoader.refresh(repository, "registrations")
                        StudentServiceLoader.refresh(repository, "tokenRegistrations")
                        onReloadState()
                    }
                },
                modifier = Modifier
            )
            val regEntry = services?.optJSONObject("registrations")
            ServiceStatusBanner(regEntry, language)
            val regData = regEntry?.optJSONObject("data")
            val regs = regData?.optJSONArray("registrations") ?: JSONArray()
            val cart = regData?.optJSONArray("cart") ?: JSONArray()
            if (regs.length() == 0 && cart.length() == 0) {
                QuietLine(tr("Brak aktywnych tur rejestracji lub koszyka.", "No active registration rounds or cart items.", "Немає активних турів реєстрації чи кошика."))
            } else {
                Tray {
                    for (i in 0 until regs.length()) {
                        if (i > 0) TrayDivider()
                        val r = regs.getJSONObject(i)
                        val id = r.optString("id")
                        val title = StudentServices.localized(r.opt("description"), language).ifEmpty { id }
                        ItemRow(
                            title = title,
                            sub = listOf(r.optString("status"), StudentServices.localized(r.opt("message"), language)).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = if (live && id.isNotEmpty()) ({
                                runAsync {
                                    val creds = repository.read()
                                    check(SharedRepository.sameAccount(state, creds))
                                    val rounds = ToolServiceData.fetchRegistrationRounds(UsosClient(creds), id)
                                    check(SharedRepository.sameAccount(creds, repository.read()))
                                    registrationRounds = title to rounds
                                }
                            }) else null
                        )
                    }
                    for (i in 0 until cart.length()) {
                        if (regs.length() > 0 || i > 0) TrayDivider()
                        val c = cart.getJSONObject(i)
                        val limits = c.optInt("limits", 0)
                        val count = c.optInt("registrations_count", 0)
                        val places = if (limits > 0) "$count/$limits" else "$count"
                        ItemRow(
                            title = StudentServices.localized(c.opt("course_name"), language).ifEmpty { c.optString("course_id") },
                            sub = "${c.optString("term_id")} · ${c.optString("user_registration_status")} · ${tr("miejsca", "places", "місця")}: $places"
                        )
                    }
                }
            }
            registrationRounds?.let { (title, rounds) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (rounds.length() == 0) Text(tr("Brak tur dla tej rejestracji.", "No rounds for this registration.", "Для цієї реєстрації немає турів."))
                        for (i in 0 until rounds.length()) {
                            val round = rounds.optJSONObject(i) ?: continue
                            Text(listOf(StudentServices.localized(round.opt("name"), language), round.optString("status"), round.optString("start_date"), round.optString("end_date")).filter { it.isNotBlank() }.joinToString(" · "))
                        }
                        OutlinedButton(onClick = { registrationRounds = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                }
            }
            Text(tr("Rejestracje żetonowe", "Token registrations", "Реєстрації за токенами"), style = MaterialTheme.typography.titleMedium)
            val tokenEntry = services?.optJSONObject("tokenRegistrations")
            ServiceStatusBanner(tokenEntry, language)
            val tokenRows = tokenEntry?.optJSONObject("data")?.optJSONArray("registrations") ?: JSONArray()
            if (tokenRows.length() == 0 && (tokenEntry?.optLong("fetchedAt") ?: 0L) > 0L) {
                QuietLine(tr("Brak rejestracji żetonowych.", "No token registrations.", "Немає реєстрацій за токенами."))
            } else if (tokenRows.length() > 0) {
                Tray {
                    for (i in 0 until tokenRows.length()) {
                        if (i > 0) TrayDivider()
                        val row = tokenRows.getJSONObject(i)
                        val id = row.optString("id")
                        val title = StudentServices.localized(row.opt("description"), language).ifEmpty { id }
                        ItemRow(title = title,
                            sub = listOf(row.optString("status"), StudentServices.localized(row.opt("message"), language), tr("Zobacz tury", "View rounds", "Переглянути тури")).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = if (id.isNotBlank() && (live || state.optString("token") == "demo")) ({
                                if (state.optString("token") == "demo") tokenDetails = title to DemoStudentServices.demoTokenRegistrationDetails(id)
                                else runAsync {
                                    val creds = repository.read()
                                    check(SharedRepository.sameAccount(state, creds))
                                    val detail = ToolServiceData.fetchTokenRegistrationDetails(UsosClient(creds), id)
                                    check(SharedRepository.sameAccount(creds, repository.read()))
                                    tokenDetails = title to detail
                                }
                            }) else null)
                    }
                }
            }
            tokenDetails?.let { (fallback, detail) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(StudentServices.localized(detail.opt("description"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                        StudentServices.localized(detail.opt("message"), language).takeIf { it.isNotBlank() }?.let { Text(it) }
                        val rounds = detail.optJSONArray("rounds") ?: JSONArray()
                        if (!detail.optBoolean("rounds_available")) Text(tr("Informacje o turach są niedostępne.", "Round details are unavailable.", "Інформація про тури недоступна."))
                        else if (rounds.length() == 0) Text(tr("Brak tur dla tej rejestracji.", "No rounds for this registration.", "Для цієї реєстрації немає турів."))
                        for (i in 0 until rounds.length()) {
                            val round = rounds.optJSONObject(i) ?: continue
                            val start = round.optString("start_date")
                            val end = round.optString("end_date")
                            val items = listOf(StudentServices.localized(round.opt("name"), language).ifEmpty { round.optString("id") },
                                if (start.isNotBlank() || end.isNotBlank()) "${start.ifBlank { "—" }} – ${end.ifBlank { "—" }}" else "",
                                if (round.has("selection_limit")) tr("Limit wyborów: ", "Choice limit: ", "Ліміт виборів: ") + round.optInt("selection_limit") else "",
                                if (round.optBoolean("is_prioritized")) tr("Z priorytetami", "Prioritized", "З пріоритетами") else "",
                                if (round.optBoolean("is_only_entitled")) tr("Tylko uprawnieni", "Eligible students only", "Лише для уповноважених") else "")
                            Text(items.filter { it.isNotBlank() }.joinToString(" · "))
                        }
                        if (detail.optBoolean("rounds_truncated")) Text(tr("Pokazano pierwsze 100 tur.", "Showing the first 100 rounds.", "Показано перші 100 турів."))
                        OutlinedButton(onClick = { tokenDetails = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                }
            }
        }

        if (section == "academicCalendar" || section == "exams" || section == "services") {
            SectionHead(
                title = tr("Sesja egzaminacyjna, grupy i punkty ECTS", "Exams, study groups & ECTS credits", "Екзамени, навчальні групи та кредити ECTS"),
                onClick = {
                    runAsync {
                        if (!AccountRole.lecturerOnly(state) && StudentServices.hasScope(state.optString("scopes"), "studies")) {
                            runCatching { StudentServiceLoader.refresh(repository, "exams") }
                        }
                        StudentServiceLoader.refresh(repository, "academicCalendar")
                        onReloadState()
                    }
                },
                modifier = Modifier
            )
            val examEntry = services?.optJSONObject("exams")
            val calEntry = services?.optJSONObject("academicCalendar")
            ServiceStatusBanner(examEntry, language)
            ServiceStatusBanner(calEntry, language)
            val calData = calEntry?.optJSONObject("data")
            val exams = examEntry?.optJSONObject("data")?.optJSONArray("exams") ?: JSONArray()
            val events = calData?.optJSONArray("events") ?: JSONArray()
            val progs = calData?.optJSONArray("programmes") ?: JSONArray()
            val usedEcts = calData?.optString("usedEcts").orEmpty()
            val primaryGroups = calData?.optJSONArray("primaryGroups") ?: JSONArray()
            val customGroups = calData?.optJSONArray("customGroups") ?: JSONArray()
            if (exams.length() == 0 && events.length() == 0 && progs.length() == 0 && usedEcts.isEmpty() && primaryGroups.length() == 0 && customGroups.length() == 0) {
                QuietLine(tr("Brak pobranych wydarzeń kalendarza lub egzaminów.", "No calendar events or exams loaded.", "Немає завантажених подій календаря чи іспитів."))
            } else {
                Tray {
                    var anyRow = false
                    if (usedEcts.isNotEmpty()) {
                        anyRow = true
                        ItemRow(
                            title = tr("Wykorzystana pula punktów ECTS", "Used ECTS credits sum", "Використана сума кредитів ECTS"),
                            sub = "$usedEcts ECTS"
                        )
                    }
                    for (i in 0 until exams.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val e = exams.getJSONObject(i)
                        val id = e.optString("id")
                        val title = StudentServices.localized(e.opt("course_name"), language).ifEmpty { e.optString("code") }
                        ItemRow(
                            title = title,
                            sub = listOf(e.optString("term_id"), tr("Zobacz szczegóły egzaminu", "View exam details", "Переглянути деталі іспиту")).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = if (id.isNotEmpty() && (live || state.optString("token") == "demo")) ({
                                if (state.optString("token") == "demo") {
                                    selectedExam = title to DemoStudentServices.demoExamDetails(id)
                                } else runAsync {
                                    val creds = repository.read()
                                    check(SharedRepository.sameAccount(state, creds))
                                    val detail = ToolServiceData.fetchExamDetails(UsosClient(creds), id)
                                    check(SharedRepository.sameAccount(creds, repository.read()))
                                    selectedExam = title to detail
                                }
                            }) else null
                        )
                    }
                    for (i in 0 until events.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val ev = events.getJSONObject(i)
                        val id = ev.optString("id")
                        val title = StudentServices.localized(ev.opt("name"), language).ifEmpty { id }
                        val dayOff = if (ev.optBoolean("is_day_off", false)) tr("dzień wolny", "day off", "вихідний") else ev.optString("type")
                        ItemRow(
                            title = title,
                            sub = "${ev.optString("start_date")} – ${ev.optString("end_date")} · $dayOff",
                            onClick = if (id.isNotBlank() && (live || state.optString("token") == "demo")) ({ loadCalendarEvent(id, title) }) else null
                        )
                    }
                    for (i in 0 until progs.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val p = progs.getJSONObject(i)
                        val id = p.optString("programme_id")
                        val studentProgrammeId = p.optString("id")
                        val title = StudentServices.localized(p.opt("programme_name"), language).ifEmpty { id }
                        ItemRow(
                            title = title,
                            sub = listOf(p.optString("status"), p.optString("admission_date"), tr("Zobacz kierunek", "View programme", "Переглянути програму")).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = if (id.isNotBlank() && (live || state.optString("token") == "demo")) ({
                                if (state.optString("token") == "demo") {
                                    selectedStudentProgrammeId = studentProgrammeId
                                    programmeStages = null; selectedStage = null
                                    selectedProgramme = title to DemoStudentServices.demoProgrammeDetails(id)
                                }
                                else runAsync {
                                    val creds = repository.read()
                                    check(SharedRepository.sameAccount(state, creds))
                                    val detail = ToolServiceData.fetchProgrammeDetails(UsosClient(creds), id)
                                    check(SharedRepository.sameAccount(creds, repository.read()))
                                    selectedStudentProgrammeId = studentProgrammeId
                                    programmeStages = null; selectedStage = null
                                    selectedProgramme = title to detail
                                }
                            }) else null
                        )
                    }
                    for (i in 0 until primaryGroups.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val g = primaryGroups.getJSONObject(i)
                        ItemRow(
                            title = StudentServices.localized(g.opt("description"), language).ifEmpty { g.optString("number") },
                            sub = listOf(g.optString("number"), g.optString("stage_id"), g.optString("term_id")).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    }
                    for (i in 0 until customGroups.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val g = customGroups.getJSONObject(i)
                        ItemRow(
                            title = g.optString("name").ifEmpty { g.optString("id") },
                            sub = listOf(g.optString("description"), g.optString("expiration_date")).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    }
                }
            }
            Text(tr("Wyszukaj wydarzenia kalendarza", "Search calendar events", "Пошук подій календаря"), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = calendarFrom, onValueChange = { calendarFrom = it; calendarError = "" },
                label = { Text(tr("Od (RRRR-MM-DD)", "From (YYYY-MM-DD)", "Від (РРРР-ММ-ДД)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = calendarUntil, onValueChange = { calendarUntil = it; calendarError = "" },
                label = { Text(tr("Przed datą (RRRR-MM-DD)", "Before (YYYY-MM-DD)", "До дати (РРРР-ММ-ДД)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = calendarError.isNotEmpty())
            if (calendarError.isNotEmpty()) Text(calendarError, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = {
                val start = calendarFrom.trim(); val end = calendarUntil.trim()
                calendarError = ""
                when {
                    !ToolServiceData.validCalendarRange(start, end) -> calendarError = tr("Podaj poprawny zakres do jednego miesiąca.", "Enter a valid range of up to one month.", "Введіть коректний період до одного місяця.")
                    state.optString("token") == "demo" -> { calendarResults = DemoStudentServices.demoCalendarSearch(start, end); calendarEvent = null }
                    !live -> calendarError = tr("Połącz konto USOS.", "Connect your USOS account.", "Підключіть обліковий запис USOS.")
                    calendarCampus.isEmpty() -> calendarError = tr("Najpierw wybierz kampus w ustawieniach.", "Select a campus in settings first.", "Спочатку виберіть кампус у налаштуваннях.")
                    else -> runAsync {
                        val creds = repository.read()
                        check(SharedRepository.sameAccount(state, creds) && CampusAffiliation.selected(creds) == calendarCampus)
                        val result = ToolServiceData.searchCalendar(UsosClient(creds), calendarCampus, start, end)
                        val latest = repository.read()
                        check(SharedRepository.sameAccount(creds, latest) && CampusAffiliation.selected(latest) == calendarCampus)
                        calendarResults = result; calendarEvent = null
                    }
                }
            }) { Text(tr("Szukaj", "Search", "Пошук")) }
            calendarResults?.let { result ->
                val rows = result.optJSONArray("events") ?: JSONArray()
                if (rows.length() == 0) QuietLine(tr("Brak wydarzeń w tym zakresie.", "No events in this range.", "У цьому періоді немає подій."))
                else Tray {
                    for (i in 0 until rows.length()) {
                        if (i > 0) TrayDivider()
                        val event = rows.getJSONObject(i)
                        val id = event.optString("id")
                        val title = StudentServices.localized(event.opt("name"), language).ifEmpty { id }
                        ItemRow(title = title, sub = listOf(event.optString("start_date"), event.optString("end_date")).filter { it.isNotBlank() }.joinToString(" – "),
                            onClick = if (id.isNotBlank() && (live || state.optString("token") == "demo")) ({ loadCalendarEvent(id, title) }) else null)
                    }
                }
                if (result.optBoolean("truncated")) QuietLine(tr("Pokazano pierwsze 100 wydarzeń.", "Showing the first 100 events.", "Показано перші 100 подій."))
            }
            calendarEvent?.let { (fallback, event) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(StudentServices.localized(event.opt("name"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                        val dates = listOf(event.optString("start_date"), event.optString("end_date")).filter { it.isNotBlank() }
                        if (dates.isNotEmpty()) Text(dates.joinToString(" – "))
                        StudentServices.localized(event.opt("faculty_name"), language).takeIf { it.isNotBlank() }?.let { Text(tr("Wydział: ", "Faculty: ", "Факультет: ") + it) }
                        event.optString("type").takeIf { it.isNotBlank() }?.let { Text(tr("Typ: ", "Type: ", "Тип: ") + it) }
                        if (event.has("is_day_off")) Text(if (event.optBoolean("is_day_off")) tr("Dzień wolny", "Day off", "Вихідний") else tr("Nie jest dniem wolnym", "Not a day off", "Не є вихідним"))
                        OutlinedButton(onClick = { calendarEvent = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                }
            }
            selectedExam?.let { (fallback, detail) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(StudentServices.localized(detail.opt("name"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                        StudentServices.localized(detail.opt("description"), language).takeIf { it.isNotBlank() }?.let { Text(it) }
                        val start = detail.optString("registration_start")
                        val end = detail.optString("registration_end")
                        if (start.isNotBlank() || end.isNotBlank()) Text(tr("Rejestracja: ", "Registration: ", "Реєстрація: ") + "${start.ifBlank { "—" }} – ${end.ifBlank { "—" }}")
                        val groups = detail.optJSONArray("groups") ?: JSONArray()
                        if (groups.length() == 0) Text(tr("Brak dostępnych informacji o grupach.", "No group details available.", "Інформація про групи недоступна."))
                        for (i in 0 until groups.length()) {
                            val group = groups.optJSONObject(i) ?: continue
                            val parts = listOf(group.optString("number").takeIf { it.isNotBlank() }?.let { tr("Grupa ", "Group ", "Група ") + it }.orEmpty(), group.optString("exam_start"), group.optString("exam_end"), group.optString("room_number").takeIf { it.isNotBlank() }?.let { tr("Sala ", "Room ", "Аудиторія ") + it }.orEmpty())
                            Text(parts.filter { it.isNotBlank() }.joinToString(" · "))
                        }
                        if (detail.optBoolean("groups_truncated")) Text(tr("Pokazano pierwsze 100 grup.", "Showing the first 100 groups.", "Показано перші 100 груп."))
                        OutlinedButton(onClick = { selectedExam = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                }
            }
            selectedProgramme?.let { (fallback, programme) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(StudentServices.localized(programme.opt("name"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                        val fields = listOf("faculty_name" to tr("Wydział", "Faculty", "Факультет"), "mode_of_studies" to tr("Tryb", "Mode", "Форма"),
                            "level_of_studies" to tr("Poziom", "Level", "Рівень"), "duration" to tr("Czas trwania", "Duration", "Тривалість"),
                            "professional_status" to tr("Kwalifikacje", "Qualifications", "Кваліфікація"))
                        val values = fields.mapNotNull { (key, label) -> ToolServiceData.courseText(programme.opt(key), language).takeIf { it.isNotBlank() }?.let { "$label: $it" } }
                        if (values.isEmpty()) Text(tr("Brak dostępnych szczegółów kierunku.", "No programme details available.", "Деталі програми недоступні."))
                        for (value in values) Text(value)
                        if (selectedStudentProgrammeId.isNotBlank()) OutlinedButton(onClick = {
                            val id = selectedStudentProgrammeId
                            if (state.optString("token") == "demo") {
                                programmeStages = DemoStudentServices.demoStudentProgrammeStages(); selectedStage = null
                            } else runAsync {
                                val creds = repository.read()
                                check(SharedRepository.sameAccount(state, creds))
                                val result = ToolServiceData.fetchStudentProgrammeStages(UsosClient(creds), id)
                                check(SharedRepository.sameAccount(creds, repository.read()) && selectedStudentProgrammeId == id)
                                programmeStages = result; selectedStage = null
                            }
                        }) { Text(tr("Zobacz etapy studiów", "View study stages", "Переглянути етапи навчання")) }
                        programmeStages?.let { result ->
                            val stages = result.optJSONArray("stages") ?: JSONArray()
                            if (!result.optBoolean("stages_available") || (stages.length() == 0 && result.optBoolean("stages_unrecognized"))) {
                                Text(tr("Informacje o etapach są niedostępne.", "Study stage details are unavailable.", "Інформація про етапи недоступна."))
                            } else if (stages.length() == 0) Text(tr("Brak etapów w tym programie.", "No stages are listed for this programme.", "Для цієї програми етапів не вказано."))
                            if (stages.length() > 0 && (result.optBoolean("stages_truncated") || result.optBoolean("stages_unrecognized"))) {
                                Text(tr("Pokazano tylko dostępne etapy (maks. 100).", "Only available stages are shown (maximum 100).", "Показано лише доступні етапи (максимум 100)."))
                            }
                            for (i in 0 until stages.length()) {
                                val id = stages.optJSONObject(i)?.optString("id").orEmpty()
                                if (id.isNotBlank()) OutlinedButton(onClick = {
                                    if (state.optString("token") == "demo") selectedStage = DemoStudentServices.demoStudyStage(id)
                                    else runAsync {
                                        val creds = repository.read()
                                        val selectedId = selectedStudentProgrammeId
                                        check(SharedRepository.sameAccount(state, creds))
                                        val stage = ToolServiceData.fetchStudyStage(UsosClient(creds), id)
                                        check(SharedRepository.sameAccount(creds, repository.read()) && selectedStudentProgrammeId == selectedId)
                                        selectedStage = stage
                                    }
                                }) { Text(id) }
                            }
                            selectedStage?.let { stage -> Text(StudentServices.localized(stage.opt("name"), language).ifEmpty { stage.optString("id") }) }
                        }
                        OutlinedButton(onClick = { selectedProgramme = null; programmeStages = null; selectedStage = null; selectedStudentProgrammeId = "" }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                }
            }
        }

        if (section == "theses" || section == "services") {
            SectionHead(
                title = tr("Prace dyplomowe", "Theses", "Дипломні роботи"),
                onClick = {
                    runAsync {
                        StudentServiceLoader.refresh(repository, "theses")
                        onReloadState()
                    }
                },
                modifier = Modifier
            )
            val thesisEntry = services?.optJSONObject("theses")
            ServiceStatusBanner(thesisEntry, language)
            val theses = thesisEntry?.optJSONObject("data")?.optJSONArray("theses") ?: JSONArray()
            if (theses.length() == 0) {
                QuietLine(tr("Brak zarejestrowanych prac dyplomowych.", "No registered theses found.", "Немає зареєстрованих дипломних робіт."))
            } else {
                Tray {
                    for (i in 0 until theses.length()) {
                        if (i > 0) TrayDivider()
                        val th = theses.getJSONObject(i)
                        val thId = th.optString("id")
                        val title = StudentServices.localized(th.opt("title") ?: th.opt("titles"), language).ifEmpty { thId }
                        val sub = listOf(th.optString("type"), th.optString("status"), tr("Szczegóły pracy", "Thesis details", "Деталі роботи")).filter { it.isNotBlank() }.joinToString(" · ")
                        ItemRow(
                            title = title,
                            sub = sub,
                            onClick = if (thId.isNotEmpty()) ({
                                if (state.optString("token") == "demo") {
                                    selectedThesis = title to DemoStudentServices.demoThesisDetail(thId)
                                } else runAsync {
                                    val creds = repository.read()
                                    check(SharedRepository.sameAccount(state, creds))
                                    val detail = ToolServiceData.fetchThesisDetail(UsosClient(creds), thId)
                                    check(SharedRepository.sameAccount(creds, repository.read()))
                                    selectedThesis = title to detail
                                }
                            }) else null
                        )
                    }
                }
            }
            selectedThesis?.let { (fallback, detail) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(StudentServices.localized(detail.opt("title") ?: detail.opt("titles"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                        val sup = detail.optJSONArray("supervisors") ?: JSONArray()
                        if (sup.length() > 0) {
                            val names = (0 until sup.length()).map { sup.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
                            Text(tr("Promotorzy: ", "Supervisors: ", "Керівники: ") + names)
                        }
                        val aut = detail.optJSONArray("authors") ?: JSONArray()
                        if (aut.length() > 0) {
                            val names = (0 until aut.length()).map { aut.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
                            Text(tr("Autorzy: ", "Authors: ", "Автори: ") + names)
                        }
                        StudentServices.localized(detail.opt("faculty_name"), language).takeIf { it.isNotBlank() }?.let { Text(tr("Wydział: ", "Faculty: ", "Факультет: ") + it) }
                        OutlinedButton(onClick = { selectedThesis = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                }
            }
        }

        if (section == "mobility" || section == "academicCalendar" || section == "services") {
            SectionHead(
                title = tr("Mobilność studencka i wymiany (Erasmus+)", "Academic mobility & exchanges (Erasmus+)", "Академічна мобільність та обміни (Erasmus+)"),
                onClick = {
                    runAsync {
                        StudentServiceLoader.refresh(repository, "mobility")
                        onReloadState()
                    }
                },
                modifier = Modifier
            )
            val mobEntry = services?.optJSONObject("mobility")
            ServiceStatusBanner(mobEntry, language)
            val mobData = mobEntry?.optJSONObject("data")
            val studentMobs = mobData?.optJSONArray("studentMobilities") ?: JSONArray()
            val coordMobs = mobData?.optJSONArray("coordinatedMobilities") ?: JSONArray()
            if (studentMobs.length() == 0 && coordMobs.length() == 0) {
                QuietLine(tr("Brak zarejestrowanych wyjazdów lub wymian w USOS.", "No academic mobility entries found in USOS.", "У USOS немає зареєстрованих обмінів."))
            } else {
                Tray {
                    var anyRow = false
                    for (i in 0 until studentMobs.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val m = studentMobs.getJSONObject(i)
                        ItemRow(
                            title = StudentServices.localized(m.opt("institution"), language).ifEmpty { StudentServices.localized(m.opt("programme"), language) },
                            sub = listOf(
                                StudentServices.localized(m.opt("programme"), language),
                                "${m.optString("start_date")} – ${m.optString("end_date")}",
                                StudentServices.localized(m.opt("state"), language)
                            ).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    }
                    for (i in 0 until coordMobs.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val m = coordMobs.getJSONObject(i)
                        ItemRow(
                            title = StudentServices.localized(m.opt("institution"), language).ifEmpty { m.optString("id") },
                            sub = listOf(
                                tr("Koordynator", "Coordinator", "Координатор"),
                                StudentServices.localized(m.opt("programme"), language),
                                StudentServices.localized(m.opt("state"), language)
                            ).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    }
                }
            }
        }

        if (section == "placementAndSlips" || section == "services") {
            SectionHead(
                title = tr("Testy poziomujące i obiegówki", "Placement tests & clearance slips", "Тести рівня та обхідні листи"),
                onClick = {
                    runAsync {
                        StudentServiceLoader.refresh(repository, "placementAndSlips")
                        onReloadState()
                    }
                },
                modifier = Modifier
            )
            val plcEntry = services?.optJSONObject("placementAndSlips")
            ServiceStatusBanner(plcEntry, language)
            val plcData = plcEntry?.optJSONObject("data")
            val tests = plcData?.optJSONArray("placementTests") ?: JSONArray()
            val slips = plcData?.optJSONArray("slipsToAnswer") ?: JSONArray()
            if (tests.length() == 0 && slips.length() == 0) {
                QuietLine(tr("Brak testów poziomujących lub pytań obiegówek.", "No placement tests or clearance slip questions.", "Немає тестів рівня або питань обхідних листів."))
            } else {
                Tray {
                    var anyRow = false
                    for (i in 0 until tests.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val t = tests.getJSONObject(i)
                        ItemRow(
                            title = StudentServices.localized(t.opt("name"), language).ifEmpty { t.optString("id") },
                            sub = listOf(
                                t.optString("score").takeIf { it.isNotEmpty() }?.let { "${tr("Wynik", "Score", "Результат")}: $it/${t.optString("max_score")}" }.orEmpty(),
                                t.optString("level")
                            ).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    }
                    for (i in 0 until slips.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val s = slips.getJSONObject(i)
                        ItemRow(
                            title = s.optString("question").ifEmpty { s.optString("id") },
                            sub = "Slip #${s.optString("slip_id")}"
                        )
                    }
                }
            }
        }

        if (section == "campusGuide" || section == "academicCalendar" || section == "services") {
            SectionHead(
                title = tr("Informator, akademiki, budynki i katalog zajęć", "Campus guide, dormitories, buildings & courses", "Довідник, гуртожитки, будівлі та каталог занять"),
                onClick = {
                    runAsync {
                        StudentServiceLoader.refresh(repository, "campusGuide")
                        onReloadState()
                    }
                },
                modifier = Modifier
            )
            val guideEntry = services?.optJSONObject("campusGuide")
            ServiceStatusBanner(guideEntry, language)
            val guideData = guideEntry?.optJSONObject("data")
            val chapters = guideData?.optJSONArray("guideChapters") ?: JSONArray()
            val dorms = guideData?.optJSONArray("dormitories") ?: JSONArray()
            val buildings = guideData?.optJSONArray("buildings") ?: JSONArray()
            if (chapters.length() > 0 || dorms.length() > 0 || buildings.length() > 0) {
                Tray {
                    var anyRow = false
                    for (i in 0 until chapters.length()) {
                        val ch = chapters.getJSONObject(i)
                        val chTitle = StudentServices.localized(ch.opt("title"), language).ifEmpty { ch.optString("id") }
                        val pages = ch.optJSONArray("pages") ?: JSONArray()
                        for (j in 0 until pages.length()) {
                            if (anyRow) TrayDivider()
                            anyRow = true
                            val pg = pages.getJSONObject(j)
                            val pgId = pg.optString("id")
                            val pgTitle = StudentServices.localized(pg.opt("title"), language).ifEmpty { pgId }
                            ItemRow(
                                title = pgTitle,
                                sub = listOf(tr("Rozdział: ", "Chapter: ", "Розділ: ") + chTitle, tr("Przeczytaj stronę", "Read page", "Читати сторінку")).joinToString(" · "),
                                onClick = if (pgId.isNotEmpty()) ({
                                    if (state.optString("token") == "demo") {
                                        selectedGuidePage = pgTitle to DemoStudentServices.demoGuidePage(pgId)
                                    } else runAsync {
                                        val creds = repository.read()
                                        check(SharedRepository.sameAccount(state, creds))
                                        val detail = ToolServiceData.fetchGuidePage(UsosClient(creds), pgId)
                                        check(SharedRepository.sameAccount(creds, repository.read()))
                                        selectedGuidePage = pgTitle to detail
                                    }
                                }) else null
                            )
                        }
                    }
                    for (i in 0 until dorms.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val d = dorms.getJSONObject(i)
                        val dormId = d.optString("id")
                        val dormTitle = StudentServices.localized(d.opt("name"), language).ifEmpty { dormId }
                        ItemRow(
                            title = dormTitle,
                            sub = listOf(d.optString("address"), StudentServices.localized(d.opt("description"), language),
                                if (dormId.isNotEmpty()) tr("Zobacz szczegóły", "View details", "Переглянути деталі") else "").filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = if (dormId.isNotEmpty()) ({
                                if (state.optString("token") == "demo") {
                                    selectedDormitory = dormTitle to DemoStudentServices.demoDormitoryDetail(dormId)
                                } else runAsync {
                                    val creds = repository.read()
                                    check(SharedRepository.sameAccount(state, creds))
                                    val detail = ToolServiceData.fetchDormitoryDetail(UsosClient(creds), dormId)
                                    check(SharedRepository.sameAccount(creds, repository.read()))
                                    selectedDormitory = dormTitle to detail
                                }
                            }) else null
                        )
                    }
                    for (i in 0 until buildings.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val b = buildings.getJSONObject(i)
                        val buildingId = b.optString("id")
                        val buildingName = StudentServices.localized(b.opt("name"), language).ifEmpty { buildingId }
                        ItemRow(
                            title = buildingName,
                            sub = listOf(b.optString("postal_address"), StudentServices.localized(b.opt("campus_name"), language),
                                if (live && buildingId.isNotEmpty()) tr("Zobacz sale", "View rooms", "Переглянути аудиторії") else "").filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = if (live && buildingId.isNotEmpty()) ({
                                runAsync {
                                    val creds = repository.read()
                                    check(SharedRepository.sameAccount(state, creds))
                                    val rooms = ToolServiceData.fetchBuildingRooms(UsosClient(creds), buildingId)
                                    check(SharedRepository.sameAccount(creds, repository.read()))
                                    buildingRooms = buildingName to rooms
                                }
                            }) else null
                        )
                    }
                }
            }
            val periods = guideData?.optJSONArray("allocationPeriods") ?: JSONArray()
            if (periods.length() > 0) {
                Text(tr("Tury przydziału miejsc w akademikach", "Dormitory allocation periods", "Періоди розподілу гуртожитків"), style = MaterialTheme.typography.titleMedium)
                Tray {
                    for (i in 0 until periods.length()) {
                        if (i > 0) TrayDivider()
                        val p = periods.getJSONObject(i)
                        val pid = p.optString("id")
                        val pTitle = StudentServices.localized(p.opt("name") ?: p.opt("status_name"), language).ifEmpty { pid }
                        val pDesc = StudentServices.localized(p.opt("description") ?: p.opt("status_desc"), language)
                        ItemRow(
                            title = pTitle,
                            sub = listOf(pDesc, tr("Zobacz terminy", "View dates", "Переглянути дати")).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = if (pid.isNotEmpty()) ({
                                if (state.optString("token") == "demo") {
                                    selectedAllocationPeriod = pTitle to DemoStudentServices.demoAllocationPeriodDetail(pid)
                                } else runAsync {
                                    val creds = repository.read()
                                    check(SharedRepository.sameAccount(state, creds))
                                    val detail = ToolServiceData.fetchAllocationPeriodDetail(UsosClient(creds), pid)
                                    check(SharedRepository.sameAccount(creds, repository.read()))
                                    selectedAllocationPeriod = pTitle to detail
                                }
                            }) else null
                        )
                    }
                }
            }
            selectedAllocationPeriod?.let { (fallback, detail) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(StudentServices.localized(detail.opt("status_name"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                        val desc = StudentServices.localized(detail.opt("status_desc"), language)
                        if (desc.isNotBlank()) Text(desc)
                        val dates = listOf(detail.optString("start_date"), detail.optString("end_date")).filter { it.isNotBlank() }
                        if (dates.isNotEmpty()) Text(tr("Termin: ", "Dates: ", "Термін: ") + dates.joinToString(" – "))
                        OutlinedButton(onClick = { selectedAllocationPeriod = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                }
            }
            selectedDormitory?.let { (fallback, detail) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(StudentServices.localized(detail.opt("name"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                        val addr = detail.optString("address")
                        if (addr.isNotBlank()) Text(tr("Adres: ", "Address: ", "Адреса: ") + addr)
                        val desc = StudentServices.localized(detail.opt("description"), language)
                        if (desc.isNotBlank()) Text(desc)
                        OutlinedButton(onClick = { selectedDormitory = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                }
            }
            selectedGuidePage?.let { (fallback, detail) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(StudentServices.localized(detail.opt("title"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                        val entries = detail.optJSONArray("entries") ?: JSONArray()
                        for (i in 0 until entries.length()) {
                            val e = entries.optJSONObject(i) ?: continue
                            val et = StudentServices.localized(e.opt("title"), language)
                            val ec = e.optString("content")
                            if (et.isNotBlank()) Text(et, style = MaterialTheme.typography.titleSmall)
                            if (ec.isNotBlank()) Text(ec)
                        }
                        OutlinedButton(onClick = { selectedGuidePage = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                }
            }
            buildingRooms?.let { (name, rooms) ->
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (rooms.length() == 0) QuietLine(tr("Brak sal w tym budynku.", "No rooms found in this building.", "У цій будівлі немає аудиторій."))
                if (rooms.length() == 100) QuietLine(tr("Pokazano najwyżej 100 sal.", "Showing up to 100 rooms.", "Показано не більше 100 аудиторій."))
                if (rooms.length() > 0) Tray {
                    for (i in 0 until rooms.length()) {
                        if (i > 0) TrayDivider()
                        val room = rooms.getJSONObject(i)
                        val id = room.optString("id")
                        ItemRow(title = room.optString("number").ifEmpty { id }, sub = tr("Zobacz plan sali", "View room schedule", "Переглянути розклад аудиторії"),
                            onClick = if (id.isNotEmpty()) ({
                                runAsync {
                                    val creds = repository.read()
                                    check(SharedRepository.sameAccount(state, creds))
                                    val result = ToolServiceData.fetchRoomSchedule(UsosClient(creds), id, LocalDate.now(UsosClient.ZONE).toString())
                                    check(SharedRepository.sameAccount(creds, repository.read()))
                                    roomScheduleResult = result
                                }
                            }) else null)
                    }
                }
                OutlinedButton(onClick = { buildingRooms = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = courseQuery,
                    onValueChange = { courseQuery = it },
                    label = { Text(tr("Szukaj przedmiotu w katalogu (min. 2 znaki)", "Search course catalog (min. 2 chars)", "Пошук предмета в каталозі (мін. 2 симв.)")) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    enabled = courseQuery.trim().length >= 2 && state.optString("token").isNotEmpty(),
                    onClick = {
                        val q = courseQuery.trim()
                        runAsync {
                            val creds = repository.read()
                            check(SharedRepository.sameAccount(state, creds))
                            val result = if (creds.optString("token") == "demo") {
                                DemoStudentServices.demoCourseSearch(q)
                            } else {
                                ToolServiceData.searchCourses(UsosClient(creds), q)
                            }
                            check(SharedRepository.sameAccount(creds, repository.read()))
                            courseSearchResults = result
                            selectedCourse = null
                        }
                    }
                ) {
                    Text(tr("Szukaj", "Search", "Знайти"))
                }
            }
            courseSearchResults?.optJSONArray("courses")?.let { courses ->
                if (courses.length() == 0) QuietLine(tr("Nie znaleziono przedmiotów. Spróbuj innej nazwy.", "No courses found. Try another name.", "Предметів не знайдено. Спробуйте іншу назву."))
                if (courses.length() > 0) {
                    Tray {
                        for (i in 0 until courses.length()) {
                            if (i > 0) TrayDivider()
                            val c = courses.getJSONObject(i)
                            val id = c.optString("id")
                            val title = StudentServices.localized(c.opt("name"), language).ifEmpty { c.optString("match") }
                            val ects = c.optString("ects").takeIf { it.isNotEmpty() }?.let { tr("około $it ECTS", "approximately $it ECTS", "приблизно $it ECTS") }.orEmpty()
                            ItemRow(
                                title = title,
                                sub = listOf(id, ects, tr("Zobacz szczegóły", "View details", "Переглянути деталі")).filter { it.isNotBlank() }.joinToString(" · "),
                                onClick = if (id.isNotBlank() && (live || state.optString("token") == "demo")) ({
                                    if (state.optString("token") == "demo") selectedCourse = title to DemoStudentServices.demoCourseDetails(id)
                                    else runAsync {
                                        val creds = repository.read()
                                        check(SharedRepository.sameAccount(state, creds))
                                        val detail = ToolServiceData.fetchCourseDetails(UsosClient(creds), id)
                                        check(SharedRepository.sameAccount(creds, repository.read()))
                                        selectedCourse = title to detail
                                    }
                                }) else null
                            )
                        }
                    }
                }
            }
            selectedCourse?.let { (fallback, course) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(StudentServices.localized(course.opt("name"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                        course.optString("lang_id").takeIf { it.isNotBlank() }?.let { Text(tr("Język: ", "Language: ", "Мова: ") + it) }
                        course.optString("ects").takeIf { it.isNotBlank() }?.let { Text(tr("około $it ECTS", "approximately $it ECTS", "приблизно $it ECTS")) }
                        val sections = listOf("description" to tr("Opis", "Description", "Опис"), "learning_outcomes" to tr("Efekty uczenia", "Learning outcomes", "Результати навчання"), "assessment_criteria" to tr("Kryteria oceniania", "Assessment criteria", "Критерії оцінювання"))
                        if (course.optString("lang_id").isBlank() && course.optString("ects").isBlank() && sections.all { ToolServiceData.courseText(course.opt(it.first), language).isBlank() }) {
                            Text(tr("Brak dostępnych szczegółów przedmiotu.", "No course details available.", "Деталі предмета недоступні."))
                        }
                        for ((key, label) in sections) {
                            val value = ToolServiceData.courseText(course.opt(key), language)
                            if (value.isNotBlank()) { Text(label, style = MaterialTheme.typography.titleSmall); Text(value) }
                        }
                        OutlinedButton(onClick = { selectedCourse = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = roomIdQuery,
                    onValueChange = { roomIdQuery = it },
                    label = { Text(tr("ID sali (plan 7-dniowy)", "Room ID (7-day timetable)", "ID аудиторії (7-денний розклад)")) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    enabled = roomIdQuery.trim().isNotEmpty() && state.optString("token").isNotEmpty(),
                    onClick = {
                        val rid = roomIdQuery.trim()
                        runAsync {
                            val creds = repository.read()
                            check(SharedRepository.sameAccount(state, creds))
                            val today = LocalDate.now(UsosClient.ZONE).toString()
                            val result = if (creds.optString("token") == "demo") {
                                DemoStudentServices.demoRoomSchedule(rid)
                            } else {
                                ToolServiceData.fetchRoomSchedule(UsosClient(creds), rid, today)
                            }
                            check(SharedRepository.sameAccount(creds, repository.read()))
                            roomScheduleResult = result
                        }
                    }
                ) {
                    Text(tr("Plan sali", "Room schedule", "Розклад аудиторії"))
                }
            }
            roomScheduleResult?.let { roomObj ->
                val lessons = roomObj.optJSONArray("lessons") ?: JSONArray()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr("Sala ", "Room ", "Аудиторія ") + roomObj.optString("number").ifEmpty { roomObj.optString("id") },
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedButton(onClick = { roomScheduleResult = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                        }
                        if (lessons.length() == 0) {
                            Text(tr("Brak zaplanowanych zajęć w tej sali w najbliższych 7 dniach.", "No classes in this room in the next 7 days.", "Немає занять у цій аудиторії в найближчі 7 днів."))
                        } else {
                            for (i in 0 until lessons.length()) {
                                val l = lessons.getJSONObject(i)
                                Text("${StudentServices.localized(l.opt("name"), language)} · ${l.optString("start_time")} – ${l.optString("end_time")}")
                            }
                        }
                    }
                }
            }
        }

        if (section == "communications" || section == "services") {
            SectionHead(
                title = tr("Oświadczenia, powiadomienia i USOSmail", "Statements, notifications & USOSmail", "Заяви, сповіщення та USOSmail"),
                onClick = {
                    runAsync {
                        StudentServiceLoader.refresh(repository, "communications")
                        onReloadState()
                    }
                },
                modifier = Modifier
            )
            val commEntry = services?.optJSONObject("communications")
            ServiceStatusBanner(commEntry, language)
            surveyDetail?.let { SurveyDetailCard(it, language) { surveyDetail = null } }
            mailDetail?.let { MailDetailCard(it, language) { mailDetail = null } }
            val commData = commEntry?.optJSONObject("data")
            val statements = commData?.optJSONArray("statements") ?: JSONArray()
            val notifications = commData?.optJSONArray("notifications") ?: JSONArray()
            val messages = commData?.optJSONArray("messages") ?: JSONArray()
            val surveys = commData?.optJSONArray("surveys") ?: JSONArray()
            if (statements.length() == 0 && notifications.length() == 0 && messages.length() == 0 && surveys.length() == 0) {
                QuietLine(tr("Brak pobranych oświadczeń, powiadomień (events) lub wiadomości (mailclient).", "No statements, notifications (events), or messages (mailclient) loaded.", "Немає завантажених заяв, сповіщень (events) або повідомлень (mailclient)."))
            } else {
                Tray {
                    var anyRow = false
                    for (i in 0 until statements.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val s = statements.getJSONObject(i)
                        ItemRow(
                            title = s.optString("subject").ifEmpty { s.optString("id") },
                            sub = listOf(s.optString("content"), s.optString("expiration_date")).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    }
                    for (i in 0 until notifications.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val n = notifications.getJSONObject(i)
                        ItemRow(
                            title = StudentServices.localized(n.opt("summary"), language).ifEmpty { n.optString("type") },
                            sub = listOf(n.optString("type"), n.optString("created")).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    }
                    for (i in 0 until surveys.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val s = surveys.getJSONObject(i)
                        val id = s.optString("id")
                        val status = when {
                            s.optBoolean("did_i_fill_out") -> tr("wypełniona", "completed", "заповнено")
                            s.has("can_i_fill_out") && !s.optBoolean("can_i_fill_out") -> tr("niedostępna", "unavailable", "недоступно")
                            s.has("can_i_fill_out") -> tr("do wypełnienia", "to fill out", "заповнити")
                            else -> ""
                        }
                        ItemRow(
                            title = "${tr("Ankieta", "Survey", "Анкета")}: " + StudentServices.localized(s.opt("name"), language).ifEmpty { id },
                            sub = listOf(status, s.optString("end_date")).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = if (id.isNotEmpty()) ({ loadSurvey(id) }) else null
                        )
                    }
                    for (i in 0 until messages.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val m = messages.getJSONObject(i)
                        ItemRow(
                            title = m.optString("subject").ifEmpty { m.optString("id") },
                            sub = listOf(m.optString("status"), m.optString("date")).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = if (m.optString("id").isNotEmpty()) ({ loadMail(m.optString("id")) }) else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MailDetailCard(message: JSONObject, language: String, onClose: () -> Unit) {
    val tr = Translator(language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(message.optString("subject").ifEmpty { tr("Wiadomość USOSmail", "USOSmail message", "Повідомлення USOSmail") }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onClose) { Text(tr("Zamknij", "Close", "Закрити")) }
            }
            val meta = listOf(message.optString("status"), message.optString("date"),
                if (message.has("recipients_count")) "${tr("odbiorcy", "recipients", "одержувачі")}: ${message.optInt("recipients_count")}" else "")
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall)
            Text(message.optString("content").ifEmpty { tr("Brak treści wiadomości.", "The message has no content.", "Повідомлення без змісту.") })
            if (message.optBoolean("content_truncated")) Text(tr("Treść została skrócona.", "The content was shortened.", "Зміст скорочено."), style = MaterialTheme.typography.bodySmall)
            val attachments = message.optJSONArray("attachments") ?: JSONArray()
            for (i in 0 until attachments.length()) {
                val a = attachments.optJSONObject(i) ?: continue
                val size = if (a.has("size")) "${(a.optInt("size") + 1023) / 1024} KB" else ""
                Text("📎 " + listOf(a.optString("filename").ifEmpty { a.optString("id") }, size, a.optString("description")).filter { it.isNotBlank() }.joinToString(" · "))
            }
        }
    }
}

@Composable
private fun SurveyDetailCard(survey: JSONObject, language: String, onClose: () -> Unit) {
    val tr = Translator(language)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(StudentServices.localized(survey.opt("name"), language).ifEmpty { tr("Ankieta", "Survey", "Анкета") }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onClose) { Text(tr("Zamknij", "Close", "Закрити")) }
            }
            survey.optString("headline").takeIf { it.isNotBlank() }?.let { Text(it) }
            val meta = listOf(
                StudentServices.localized(survey.opt("course_name"), language),
                survey.optString("lecturer"),
                StudentServices.localized(survey.opt("faculty_name"), language),
                listOf(survey.optString("start_date"), survey.optString("end_date")).filter { it.isNotBlank() }.joinToString(" – ")
            ).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall)
            if (survey.has("filled_out_count") && survey.has("entitled_count")) {
                Text("${tr("Wypełnienia", "Responses", "Відповіді")}: ${survey.optInt("filled_out_count")}/${survey.optInt("entitled_count")}", style = MaterialTheme.typography.bodySmall)
            }
            val questions = survey.optJSONArray("questions") ?: JSONArray()
            if (questions.length() == 0) Text(tr("Pytania nie są dostępne w aplikacji.", "Questions are not available in the app.", "Питання недоступні в застосунку."))
            for (i in 0 until questions.length()) {
                val q = questions.optJSONObject(i) ?: continue
                Column(Modifier.padding(start = (q.optInt("level") * 12).dp)) {
                    Text("${q.optString("number")} ${q.optString("text")}".trim(), style = MaterialTheme.typography.bodyMedium)
                    val answers = q.optJSONArray("answers") ?: JSONArray()
                    for (j in 0 until answers.length()) Text("• " + answers.optString(j), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (survey.optBoolean("questions_truncated")) Text(tr("Pokazano pierwsze pytania.", "Showing the first questions only.", "Показано лише перші питання."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Ankietę wypełnisz w USOSweb.", "Fill out the survey in USOSweb.", "Заповніть анкету в USOSweb."), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ServiceStatusBanner(entry: JSONObject?, language: String) {
    if (entry == null) return
    val error = entry.optString("error")
    if (error.isNotEmpty()) {
        Text(
            text = SyncProgress.cachedError(entry, language),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
