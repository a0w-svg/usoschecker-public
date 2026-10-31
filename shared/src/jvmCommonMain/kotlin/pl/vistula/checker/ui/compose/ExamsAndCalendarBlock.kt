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
internal fun ExamsAndCalendarBlock(env: ExtrasEnv) {
    val state = env.state
    val repository = env.repository
    val language = env.language
    val tr = env.tr
    val services = env.services
    val live = env.live
    val runAsync = env.runAsync
    val onReloadState = env.onReloadState
    var selectedExam by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var selectedProgramme by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var selectedStudentProgrammeId by remember(state.optString("accountSession")) { mutableStateOf("") }
    var programmeStages by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    var selectedStage by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
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
