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
internal fun ScopePresetRow(
    scopesInput: String,
    language: String,
    onScopesChanged: (String) -> Unit
) {
    val tr = Translator(language)
    val options = listOf(
        "payments" to tr("Płatności", "Payments", "Платежі"),
        "cards" to tr("Legitymacja", "Student ID", "Посвідчення"),
        "documents" to tr("Dokumenty", "Documents", "Документи"),
        "tests" to tr("Sprawdziany", "Course tests", "Тести"),
        "lecturer" to tr("Wykładowca (staff_perspective)", "Lecturer (staff_perspective)", "Викладач (staff_perspective)"),
        "profile" to tr("Profil i zdjęcie", "Profile & photo", "Профіль і фото"),
        "extras" to tr("Komunikaty, ankiety i testy", "Messages, surveys & placement", "Повідомлення, анкети й тести")
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            tr(
                "Zestawy uprawnień (zmiana wymaga ponownej autoryzacji w USOS):",
                "Permission presets (changing scopes requires USOS reauthorization):",
                "Набори дозволів (зміна потребує повторної авторизації в USOS):"
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (feature, label) ->
                val selected = FeatureAccess.selected(scopesInput, feature)
                FilterChip(
                    selected = selected,
                    onClick = { onScopesChanged(FeatureAccess.change(scopesInput, feature, !selected)) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
internal fun ExperimentalLecturerWritesSettingRow(
    state: JSONObject,
    preferences: KeyValueStore,
    language: String,
    onReloadState: () -> Unit
) {
    if (!AccountRole.lecturer(state)) return
    val tr = Translator(language)
    var enabled by remember(preferences) { mutableStateOf(LecturerWriteActions.isEnabled(preferences)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("Zapis ocen, punktów i obecności (Wykładowca)", "Grade, point & attendance entry (Lecturer)", "Введення оцінок, балів і присутності (Викладач)"),
                    style = MaterialTheme.typography.titleSmall
                )
                ExperimentalBadge(language)
            }
            Text(
                tr(
                    "Funkcja eksperymentalna oparta na dokumentacji API USOS. Zawsze sprawdzaj wynik w USOSweb.",
                    "Experimental feature built from USOS API docs. Always verify results in USOSweb.",
                    "Експериментальна функція за документацією USOS API. Завжди перевіряйте результат у USOSweb."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { next ->
                LecturerWriteActions.setEnabled(preferences, next)
                enabled = next
                onReloadState()
            }
        )
    }
}

@Composable
internal fun ExperimentalBadge(language: String) {
    val t = VistulaDesign.tokens
    Text(
        text = toolText(language, "Eksperymentalne", "Experimental", "Експериментально"),
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(t.accentSoft)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = t.accent
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LecturerWorkspaceSection(
    state: JSONObject,
    repository: SharedRepository,
    preferences: KeyValueStore?,
    language: String,
    initialTab: String = "teaching",
    onOpenUrl: (String) -> Unit,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    if (!AccountRole.lecturer(state)) return
    val t = VistulaDesign.tokens
    val tr = Translator(language)
    var subTab by remember(initialTab) {
        mutableStateOf(
            when (initialTab) {
                "lecturerGroups" -> "groups"
                "lecturerTests" -> "tests"
                "lecturerMeetings" -> "meetings"
                "lecturerAttendance" -> "attendance"
                "lecturerReports" -> "reports"
                else -> "courses"
            }
        )
    }
    var selectedGroupDetail by remember { mutableStateOf<JSONObject?>(null) }
    var selectedNodeDetail by remember { mutableStateOf<JSONObject?>(null) }
    var selectedAttendanceDetail by remember { mutableStateOf<JSONObject?>(null) }
    val live = state.optString("token").isNotEmpty() && state.optString("token") != "demo" && !state.optBoolean("authorizationError")
    LaunchedEffect(subTab, state.optString("accountSession"), live) {
        if (!live) return@LaunchedEffect
        val targetSec = when (subTab) {
            "groups" -> "lecturerGroups"
            "tests" -> "lecturerTests"
            "meetings" -> "lecturerMeetings"
            "attendance" -> "lecturerAttendance"
            "reports" -> "lecturerReports"
            else -> "teaching"
        }
        if (targetSec == "lecturerTests" && !StudentServices.hasScope(state.optString("scopes"), "crstests")) return@LaunchedEffect
        val attempted = state.optJSONObject("studentServices")?.optJSONObject(targetSec)?.optLong("attemptAt") ?: 0L
        if (attempted == 0L) {
            runAsync {
                StudentServiceLoader.refresh(repository, targetSec)
                onReloadState()
            }
        }
    }
    val services = state.optJSONObject("studentServices")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "courses" to tr("Przedmioty", "Courses", "Предмети"),
                "groups" to tr("Grupy", "Groups", "Групи"),
                "tests" to tr("Sprawdziany", "Gradebooks", "Тести"),
                "meetings" to tr("Konsultacje", "Meetings", "Консультації"),
                "attendance" to tr("Obecności", "Attendance", "Присутність"),
                "reports" to tr("Ankiety i dyplomy", "Surveys & theses", "Анкети й дипломи")
            ).forEach { (id, label) ->
                FilterChip(selected = subTab == id, onClick = { subTab = id }, label = { Text(label) })
            }
        }

        when (subTab) {
            "courses" -> {
                SectionHead(
                    title = tr("Prowadzone przedmioty", "Conducted courses", "Предмети, що викладаються"),
                    onClick = {
                        runAsync {
                            StudentServiceLoader.refresh(repository, "teaching")
                            onReloadState()
                        }
                    },
                    modifier = Modifier
                )
                val entry = services?.optJSONObject("teaching")
                ServiceStatusBanner(entry, language)
                val courses = entry?.optJSONObject("data")?.optJSONArray("courses")
                if (courses == null || courses.length() == 0) {
                    QuietLine(
                        if (entry?.has("fetchedAt") == true) {
                            tr("USOS nie zwrócił aktywnych prowadzonych przedmiotów.", "USOS returned no active conducted courses.", "USOS не повернув активних предметів.")
                        } else {
                            tr("Pobierz listę prowadzonych przedmiotów z USOS.", "Load conducted courses from USOS.", "Завантажте список предметів із USOS.")
                        }
                    )
                } else {
                    Tray {
                        for (i in 0 until courses.length()) {
                            if (i > 0) TrayDivider()
                            val course = courses.getJSONObject(i)
                            ItemRow(
                                title = StudentServices.localized(course.opt("name"), language),
                                sub = StudentServices.localized(course.opt("term"), language)
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { onOpenUrl("https://usosweb.vistula.edu.pl/kontroler.php?_action=dla_prac") }) {
                    Text(tr("Funkcje wykładowcy w USOSweb", "Lecturer tools in USOSweb", "Функції викладача в USOSweb"))
                }
            }

            "groups" -> {
                SectionHead(
                    title = tr("Grupy zajęciowe i koordynacja", "Class groups & coordination", "Навчальні групи та координація"),
                    onClick = {
                        runAsync {
                            StudentServiceLoader.refresh(repository, "lecturerGroups")
                            onReloadState()
                        }
                    },
                    modifier = Modifier
                )
                val entry = services?.optJSONObject("lecturerGroups")
                ServiceStatusBanner(entry, language)
                val data = entry?.optJSONObject("data")
                val groups = data?.optJSONArray("groups")
                val coordinated = data?.optJSONArray("coordinated")
                if ((groups == null || groups.length() == 0) && (coordinated == null || coordinated.length() == 0)) {
                    QuietLine(tr("Brak pobranych grup zajęciowych. Kliknij Odśwież.", "No class groups loaded. Tap Refresh.", "Немає завантажених груп. Натисніть Оновити."))
                } else {
                    if (groups != null && groups.length() > 0) {
                        Tray {
                            for (i in 0 until groups.length()) {
                                if (i > 0) TrayDivider()
                                val g = groups.getJSONObject(i)
                                val unitId = g.optString("course_unit_id")
                                val groupNr = g.optString("group_number")
                                val title = StudentServices.localized(g.opt("course_name"), language).ifEmpty { g.optString("course_id") }
                                val sub = listOf(
                                    StudentServices.localized(g.opt("class_type"), language),
                                    tr("Grupa ", "Group ", "Група ") + groupNr,
                                    g.optString("term_id")
                                ).filter { it.isNotBlank() }.joinToString(" · ")
                                ItemRow(
                                    title = title,
                                    sub = sub,
                                    onClick = if (unitId.isNotEmpty() && groupNr.isNotEmpty()) {
                                        {
                                            runAsync {
                                                val creds = repository.read()
                                                check(SharedRepository.sameAccount(state, creds))
                                                selectedGroupDetail = if (creds.optString("token") == "demo") {
                                                    DemoStudentServices.demoGroupDetail(unitId, groupNr)
                                                } else {
                                                    ToolServiceData.fetchGroupDetail(UsosClient(creds), creds, unitId, groupNr)
                                                }
                                            }
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                    if (coordinated != null && coordinated.length() > 0) {
                        SectionHead(tr("Koordynowane przedmioty", "Coordinated courses", "Координовані предмети"))
                        Tray {
                            for (i in 0 until coordinated.length()) {
                                if (i > 0) TrayDivider()
                                val c = coordinated.getJSONObject(i)
                                ItemRow(
                                    title = StudentServices.localized(c.opt("course_name"), language).ifEmpty { c.optString("course_id") },
                                    sub = StudentServices.localized(c.opt("term_name"), language).ifEmpty { c.optString("term_id") }
                                )
                            }
                        }
                    }
                }
                selectedGroupDetail?.let { detail ->
                    val participants = detail.optJSONArray("participants") ?: JSONArray()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    StudentServices.localized(detail.opt("course_name"), language) +
                                        " · " + tr("Grupa ", "Group ", "Група ") + detail.optString("group_number"),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                OutlinedButton(onClick = { selectedGroupDetail = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                            }
                            Text(
                                tr("Uczestnicy grupy (", "Group participants (", "Учасники групи (") +
                                    "${participants.length()}/${detail.optInt("participantsTotal", participants.length())}):",
                                style = MaterialTheme.typography.labelLarge,
                                color = t.muted
                            )
                            if (detail.optBoolean("participantsTruncated", false)) {
                                Text(
                                    tr("Lista ograniczona do ${LecturerServices.MAX_PARTICIPANTS} osób.", "List capped at ${LecturerServices.MAX_PARTICIPANTS} participants.", "Список обмежено ${LecturerServices.MAX_PARTICIPANTS} особами."),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (participants.length() == 0) {
                                Text(tr("Brak uczestników lub brak uprawnień zakresu 'studies'.", "No participants or missing 'studies' scope.", "Немає учасників або бракує дозволу 'studies'."))
                            } else {
                                for (i in 0 until participants.length()) {
                                    val p = LecturerServices.Participant(participants.getJSONObject(i))
                                    val album = if (p.studentNumber.isNotEmpty()) " (${p.studentNumber})" else ""
                                    Text("${i + 1}. ${p.displayName()}$album · ID: ${p.id}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            "tests" -> {
                SectionHead(
                    title = tr("Drzewa sprawdzianów i ocen", "Course test & gradebook trees", "Дерева тестів та оцінок"),
                    onClick = {
                        runAsync {
                            StudentServiceLoader.refresh(repository, "lecturerTests")
                            onReloadState()
                        }
                    },
                    modifier = Modifier
                )
                val entry = services?.optJSONObject("lecturerTests")
                ServiceStatusBanner(entry, language)
                val trees = entry?.optJSONObject("data")?.optJSONArray("trees")
                if (trees == null || trees.length() == 0) {
                    QuietLine(tr("Brak pobranych drzew sprawdzianów (wymagany zakres 'crstests').", "No course test trees loaded (requires 'crstests' scope).", "Немає завантажених дерев тестів (потрібен дозвіл 'crstests')."))
                } else {
                    for (i in 0 until trees.length()) {
                        val tree = trees.getJSONObject(i)
                        val courseTitle = StudentServices.localized(tree.opt("course_name"), language).ifEmpty { tree.optString("course_id") }
                        val termId = tree.optString("term_id")
                        SectionHead("$courseTitle · $termId")
                        val nodes = tree.optJSONArray("nodes") ?: JSONArray()
                        if (nodes.length() == 0) {
                            QuietLine(tr("Brak węzłów podrzędnych.", "No subnodes.", "Немає підвузлів."))
                        } else {
                            Tray {
                                for (j in 0 until nodes.length()) {
                                    if (j > 0) TrayDivider()
                                    val node = nodes.getJSONObject(j)
                                    val nodeId = node.optString("id")
                                    val nodeName = StudentServices.localized(node.opt("name"), language).ifEmpty { nodeId }
                                    val nodeType = node.optString("type")
                                    val vis = if (node.optBoolean("visible_for_students", false)) {
                                        tr("widoczny dla studentów", "visible to students", "видимий студентам")
                                    } else {
                                        tr("ukryty", "hidden", "прихований")
                                    }
                                    ItemRow(
                                        title = nodeName,
                                        sub = "ID: $nodeId · $nodeType · $vis",
                                        onClick = {
                                            runAsync {
                                                val creds = repository.read()
                                                check(SharedRepository.sameAccount(state, creds))
                                                selectedNodeDetail = if (creds.optString("token") == "demo") {
                                                    DemoStudentServices.demoNodeDetail(nodeId)
                                                } else {
                                                    ToolServiceData.fetchNodeDetail(UsosClient(creds), creds, nodeId)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                selectedNodeDetail?.let { detail ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    StudentServices.localized(detail.opt("name"), language).ifEmpty { detail.optString("id") },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                OutlinedButton(onClick = { selectedNodeDetail = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                            }
                            Text("ID: ${detail.optString("id")} · ${detail.optString("type")}", style = MaterialTheme.typography.bodySmall, color = t.muted)
                            val hist = detail.optJSONArray("histogram") ?: JSONArray()
                            if (hist.length() > 0) {
                                Text(tr("Statystyki węzła:", "Node statistics:", "Статистика вузла:"), style = MaterialTheme.typography.labelLarge)
                                for (k in 0 until hist.length()) {
                                    val h = hist.getJSONObject(k)
                                    Text("${h.optString("label")}: ${h.optInt("count")}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                ExperimentalLecturerWriteCard(
                    state = state,
                    repository = repository,
                    preferences = preferences,
                    language = language,
                    initialNodeId = selectedNodeDetail?.optString("id").orEmpty(),
                    onAuthoritativeNodeUpdated = { selectedNodeDetail = it },
                    onStatusMessage = onStatusMessage,
                    runAsync = runAsync
                )
            }

            "meetings" -> {
                SectionHead(
                    title = tr("Konsultacje i spotkania prowadzącego", "Lecturer consultations & meetings", "Консультації та зустрічі викладача"),
                    onClick = {
                        runAsync {
                            StudentServiceLoader.refresh(repository, "lecturerMeetings")
                            onReloadState()
                        }
                    },
                    modifier = Modifier
                )
                val entry = services?.optJSONObject("lecturerMeetings")
                ServiceStatusBanner(entry, language)
                val meetings = entry?.optJSONObject("data")?.optJSONArray("meetings")
                if (meetings == null || meetings.length() == 0) {
                    QuietLine(tr("Brak zaplanowanych spotkań w USOS.", "No lecturer meetings found in USOS.", "У USOS немає запланованих зустрічей."))
                } else {
                    Tray {
                        for (i in 0 until meetings.length()) {
                            if (i > 0) TrayDivider()
                            val m = meetings.getJSONObject(i)
                            val name = StudentServices.localized(m.opt("name"), language).ifEmpty { m.optString("id") }
                            val comment = StudentServices.localized(m.opt("comment"), language)
                            val dates = m.optJSONArray("dates") ?: JSONArray()
                            val firstDate = if (dates.length() > 0) {
                                val slot = dates.getJSONObject(0)
                                "${slot.optString("start_time")} – ${slot.optString("end_time")} (${tr("limit", "limit", "ліміт")}: ${slot.optInt("seats_limit")})"
                            } else ""
                            ItemRow(
                                title = name,
                                sub = listOf(comment, firstDate).filter { it.isNotBlank() }.joinToString(" · ")
                            )
                        }
                    }
                }
            }

            "attendance" -> {
                SectionHead(
                    title = tr("Listy obecności w USOS", "USOS attendance lists", "Списки присутності в USOS"),
                    onClick = {
                        runAsync {
                            StudentServiceLoader.refresh(repository, "lecturerAttendance")
                            onReloadState()
                        }
                    },
                    modifier = Modifier
                )
                val entry = services?.optJSONObject("lecturerAttendance")
                ServiceStatusBanner(entry, language)
                val lists = entry?.optJSONObject("data")?.optJSONArray("lists")
                if (lists == null || lists.length() == 0) {
                    QuietLine(tr("Brak pobranych list obecności.", "No attendance lists loaded.", "Немає завантажених списків присутності."))
                } else {
                    Tray {
                        for (i in 0 until lists.length()) {
                            if (i > 0) TrayDivider()
                            val l = lists.getJSONObject(i)
                            val listId = l.optString("id")
                            val title = StudentServices.localized(l.opt("course_name"), language)
                                .ifEmpty { "ID: $listId · ${l.optString("course_unit_id")}" }
                            val sub = listOf(
                                l.optString("date"),
                                tr("Grupa ", "Group ", "Група ") + l.optString("group_number"),
                                l.optString("mode")
                            ).filter { it.isNotBlank() }.joinToString(" · ")
                            ItemRow(
                                title = title,
                                sub = sub,
                                onClick = if (listId.isNotEmpty()) {
                                    {
                                        runAsync {
                                            val creds = repository.read()
                                            check(SharedRepository.sameAccount(state, creds))
                                            selectedAttendanceDetail = if (creds.optString("token") == "demo") {
                                                DemoStudentServices.demoAttendanceDetail(listId)
                                            } else {
                                                ToolServiceData.fetchAttendanceDetail(UsosClient(creds), creds, listId)
                                            }
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }
                selectedAttendanceDetail?.let { detail ->
                    val entries = detail.optJSONArray("entries") ?: JSONArray()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tr("Lista obecności #", "Attendance list #", "Список присутності #") +
                                        "${detail.optString("id")} · ${detail.optString("date")} (${detail.optString("mode")})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                OutlinedButton(onClick = { selectedAttendanceDetail = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                            }
                            if (entries.length() == 0) {
                                Text(tr("Brak wpisów na tej liście.", "No attendance entries on this list.", "У цьому списку немає записів."))
                            } else {
                                for (k in 0 until entries.length()) {
                                    val e = entries.getJSONObject(k)
                                    val name = listOf(e.optString("first_name"), e.optString("last_name")).filter { it.isNotEmpty() }.joinToString(" ")
                                        .ifEmpty { "ID: ${e.optString("student_id")}" }
                                    val mode = e.optString("attendance_mode")
                                    val comment = e.optString("comment").takeIf { it.isNotEmpty() }?.let { " — $it" }.orEmpty()
                                    Text("${k + 1}. $name (ID: ${e.optString("student_id")}): $mode$comment", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                ExperimentalAttendanceWriteCard(
                    state = state,
                    repository = repository,
                    preferences = preferences,
                    language = language,
                    initialListId = selectedAttendanceDetail?.optString("id").orEmpty(),
                    onAuthoritativeAttendanceUpdated = { selectedAttendanceDetail = it },
                    onStatusMessage = onStatusMessage,
                    runAsync = runAsync
                )
            }

            "reports" -> {
                SectionHead(
                    title = tr("Ankiety dydaktyczne i protokoły prac dyplomowych", "Teaching surveys & thesis reports", "Анкети викладання та протоколи дипломних робіт"),
                    onClick = {
                        runAsync {
                            StudentServiceLoader.refresh(repository, "lecturerReports")
                            onReloadState()
                        }
                    },
                    modifier = Modifier
                )
                val entry = services?.optJSONObject("lecturerReports")
                ServiceStatusBanner(entry, language)
                val data = entry?.optJSONObject("data")
                val surveys = data?.optJSONArray("surveys") ?: JSONArray()
                val theses = data?.optJSONArray("signableTheses") ?: JSONArray()
                if (surveys.length() == 0 && theses.length() == 0) {
                    QuietLine(tr("Brak pobranych ankiet dydaktycznych (surveys_reports) lub protokołów dyplomowych.", "No teaching surveys (surveys_reports) or thesis reports loaded.", "Немає завантажених анкет викладання (surveys_reports) або дипломних протоколів."))
                } else {
                    if (surveys.length() > 0) {
                        SectionHead(tr("Ankiety o prowadzącym", "Surveys about lecturer", "Анкети про викладача"))
                        Tray {
                            for (i in 0 until surveys.length()) {
                                if (i > 0) TrayDivider()
                                val s = surveys.getJSONObject(i)
                                val title = StudentServices.localized(s.opt("name"), language).ifEmpty { s.optString("id") }
                                val course = StudentServices.localized(s.opt("course_name"), language)
                                val answers = s.optInt("number_of_answers", 0)
                                ItemRow(
                                    title = title,
                                    sub = listOf(course, "${tr("odpowiedzi", "answers", "відповідей")}: $answers", "${s.optString("start_date")} – ${s.optString("end_date")}")
                                        .filter { it.isNotBlank() }.joinToString(" · ")
                                )
                            }
                        }
                    }
                    if (theses.length() > 0) {
                        SectionHead(tr("Protokoły prac dyplomowych do podpisu", "Signable thesis reports", "Протоколи дипломних робіт до підпису"))
                        Tray {
                            for (i in 0 until theses.length()) {
                                if (i > 0) TrayDivider()
                                val th = theses.getJSONObject(i)
                                ItemRow(
                                    title = StudentServices.localized(th.opt("title"), language).ifEmpty { th.optString("id") },
                                    sub = listOf(th.optString("type"), th.optString("status")).filter { it.isNotBlank() }.joinToString(" · ")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
