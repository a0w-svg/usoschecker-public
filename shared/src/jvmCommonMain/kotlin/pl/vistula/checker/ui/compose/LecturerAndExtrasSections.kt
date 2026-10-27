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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.KeyValueStore
import pl.vistula.checker.LecturerWriteActions
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.StudentServiceLoader
import pl.vistula.checker.ToolServiceData
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.AccountRole
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
    fun tr(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    val options = listOf(
        "payments" to tr("Płatności", "Payments", "Платежі"),
        "cards" to tr("Legitymacja", "Student ID", "Посвідчення"),
        "documents" to tr("Dokumenty", "Documents", "Документи"),
        "tests" to tr("Sprawdziany", "Course tests", "Тести"),
        "lecturer" to tr("Wykładowca (staff_perspective)", "Lecturer (staff_perspective)", "Викладач (staff_perspective)"),
        "profile" to tr("Profil i zdjęcie", "Profile & photo", "Профіль і фото")
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
    fun tr(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    var enabled by remember(preferences) { mutableStateOf(LecturerWriteActions.isEnabled(preferences)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("Zapis ocen i punktów (Wykładowca)", "Grade & point entry (Lecturer)", "Введення оцінок і балів (Викладач)"),
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
    fun tr(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    var subTab by remember(initialTab) {
        mutableStateOf(
            when (initialTab) {
                "lecturerGroups" -> "groups"
                "lecturerTests" -> "tests"
                "lecturerMeetings" -> "meetings"
                else -> "courses"
            }
        )
    }
    var selectedGroupDetail by remember { mutableStateOf<JSONObject?>(null) }
    var selectedNodeDetail by remember { mutableStateOf<JSONObject?>(null) }
    val services = state.optJSONObject("studentServices")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PillSegment(
            options = listOf(
                "courses" to tr("Przedmioty", "Courses", "Предмети"),
                "groups" to tr("Grupy", "Groups", "Групи"),
                "tests" to tr("Sprawdziany", "Gradebooks", "Тести"),
                "meetings" to tr("Konsultacje", "Meetings", "Консультації")
            ),
            selected = subTab,
            onSelect = { subTab = it }
        )

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
                                                selectedGroupDetail = ToolServiceData.fetchGroupDetail(UsosClient(creds), creds, unitId, groupNr)
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
                                                selectedNodeDetail = ToolServiceData.fetchNodeDetail(UsosClient(creds), creds, nodeId)
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExperimentalLecturerWriteCard(
    state: JSONObject,
    repository: SharedRepository,
    preferences: KeyValueStore?,
    language: String,
    initialNodeId: String,
    onAuthoritativeNodeUpdated: (JSONObject) -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    if (!AccountRole.lecturer(state)) return
    fun tr(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    val enabled = preferences == null || LecturerWriteActions.isEnabled(preferences)
    var acknowledged by remember(preferences) { mutableStateOf(preferences == null || LecturerWriteActions.isAcknowledged(preferences)) }
    var writeMode by remember { mutableStateOf("points") } // points | grade | taskProps | gradeProps
    var nodeId by remember(initialNodeId) { mutableStateOf(initialNodeId) }
    var studentId by remember { mutableStateOf("") }
    var pointsInput by remember { mutableStateOf("") }
    var gradeOrderInput by remember { mutableStateOf("1") }
    var commentInput by remember { mutableStateOf("") }
    var lastChangedInput by remember { mutableStateOf("") }
    var namePlInput by remember { mutableStateOf("") }
    var nameEnInput by remember { mutableStateOf("") }
    var visibleForStudents by remember { mutableStateOf(true) }
    var pointsMinInput by remember { mutableStateOf("0") }
    var pointsMaxInput by remember { mutableStateOf("20") }
    var pointsPrecisionInput by remember { mutableStateOf("1") }
    var confirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("Wprowadzanie punktów i ocen", "Gradebook & point entry", "Введення балів та оцінок"),
                    style = MaterialTheme.typography.titleMedium
                )
                ExperimentalBadge(language)
            }
            if (!enabled) {
                Text(
                    tr(
                        "Eksperymentalne akcje zapisu wykładowcy są wyłączone w ustawieniach Konta.",
                        "Experimental lecturer write actions are turned off in Account settings.",
                        "Експериментальні дії запису викладача вимкнено в налаштуваннях облікового запису."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            if (!acknowledged && preferences != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            tr(
                                "Niezweryfikowane na żywym serwerze uczelni. Po każdym zapisie sprawdź wynik w USOS.",
                                "Not yet verified against the live university server. Check the result in USOS.",
                                "Ще не перевірено на живому сервері університету. Перевірте результат у USOS."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Button(onClick = {
                            LecturerWriteActions.acknowledge(preferences)
                            acknowledged = true
                        }) {
                            Text(tr("Rozumiem, odblokuj zapis eksperymentalny", "I understand, enable experimental writes", "Розумію, увімкнути експериментальний запис"))
                        }
                    }
                }
                return@Column
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "points" to tr("Punkty studenta", "Student points", "Бали студента"),
                    "grade" to tr("Ocena studenta", "Student grade", "Оцінка студента"),
                    "taskProps" to tr("Właściwości zadania", "Task properties", "Властивості завдання"),
                    "gradeProps" to tr("Właściwości oceny", "Grade node properties", "Властивості оцінки")
                ).forEach { (id, label) ->
                    FilterChip(selected = writeMode == id, onClick = { writeMode = id }, label = { Text(label) })
                }
            }

            OutlinedTextField(
                value = nodeId,
                onValueChange = { nodeId = it.trim() },
                label = { Text(tr("ID węzła (node_id)", "Node ID (node_id)", "ID вузла (node_id)")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            when (writeMode) {
                "points", "grade" -> {
                    OutlinedTextField(
                        value = studentId,
                        onValueChange = { studentId = it.trim() },
                        label = { Text(tr("ID użytkownika studenta (user_id)", "Student user ID (user_id)", "ID користувача студента (user_id)")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (writeMode == "points") {
                        OutlinedTextField(
                            value = pointsInput,
                            onValueChange = { pointsInput = it.trim() },
                            label = { Text(tr("Liczba punktów", "Points", "Кількість балів")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        OutlinedTextField(
                            value = gradeOrderInput,
                            onValueChange = { gradeOrderInput = it.trim() },
                            label = { Text(tr("Kolejność wartości oceny (order)", "Grade value order (order)", "Порядковий номер оцінки (order)")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        label = { Text(tr("Komentarz publiczny (do 300 zn.)", "Public comment (max 300 chars)", "Публічний коментар (до 300 симв.)")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lastChangedInput,
                        onValueChange = { lastChangedInput = it.trim() },
                        label = { Text(tr("Знаcznik last_changed (opcjonalnie)", "last_changed token (optional)", " позначка last_changed (необов’язково)")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "taskProps", "gradeProps" -> {
                    OutlinedTextField(
                        value = namePlInput,
                        onValueChange = { namePlInput = it },
                        label = { Text(tr("Nazwa węzła (PL)", "Node name (PL)", "Назва вузла (PL)")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = nameEnInput,
                        onValueChange = { nameEnInput = it },
                        label = { Text(tr("Nazwa węzła (EN)", "Node name (EN)", "Назва вузла (EN)")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = visibleForStudents, onCheckedChange = { visibleForStudents = it })
                        Text(tr("Widoczny dla studentów", "Visible to students", "Видимий для студентів"))
                    }
                    if (writeMode == "taskProps") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pointsMinInput,
                                onValueChange = { pointsMinInput = it.trim() },
                                label = { Text("points_min (<=0)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = pointsMaxInput,
                                onValueChange = { pointsMaxInput = it.trim() },
                                label = { Text("points_max (>0)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = pointsPrecisionInput,
                                onValueChange = { pointsPrecisionInput = it.trim() },
                                label = { Text("precision (0..3)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            Button(
                enabled = nodeId.isNotBlank() && when (writeMode) {
                    "points" -> studentId.isNotBlank() && pointsInput.toDoubleOrNull() != null
                    "grade" -> studentId.isNotBlank() && gradeOrderInput.toIntOrNull() != null
                    else -> namePlInput.isNotBlank() && nameEnInput.isNotBlank()
                },
                onClick = { confirmDialog = true }
            ) {
                Text(tr("Wyślij do USOS (z potwierdzeniem)", "Submit to USOS (with confirmation)", "Надіслати в USOS (із підтвердженням)"))
            }
        }
    }

    if (confirmDialog) {
        val summaryText = when (writeMode) {
            "points" -> tr(
                "Węzeł: $nodeId\nStudent ID: $studentId\nPunkty: $pointsInput",
                "Node: $nodeId\nStudent ID: $studentId\nPoints: $pointsInput",
                "Вузол: $nodeId\nStudent ID: $studentId\nБали: $pointsInput"
            )
            "grade" -> tr(
                "Węzeł: $nodeId\nStudent ID: $studentId\nOrder oceny: $gradeOrderInput",
                "Node: $nodeId\nStudent ID: $studentId\nGrade order: $gradeOrderInput",
                "Вузол: $nodeId\nStudent ID: $studentId\nOrder оцінки: $gradeOrderInput"
            )
            else -> tr(
                "Węzeł: $nodeId\nPL: $namePlInput\nEN: $nameEnInput",
                "Node: $nodeId\nPL: $namePlInput\nEN: $nameEnInput",
                "Вузол: $nodeId\nPL: $namePlInput\nEN: $nameEnInput"
            )
        }
        AlertDialog(
            onDismissRequest = { confirmDialog = false },
            title = { Text(tr("Potwierdź zapis eksperymentalny", "Confirm experimental write", "Підтвердьте експериментальний запис")) },
            text = { Text(summaryText) },
            confirmButton = {
                Button(onClick = {
                    confirmDialog = false
                    runAsync {
                        try {
                            val creds = repository.read()
                            check(SharedRepository.sameAccount(state, creds))
                            val client = UsosClient(creds)
                            val checkSameAccount = { check(SharedRepository.sameAccount(creds, repository.read())) }
                            val updatedNode = when (writeMode) {
                                "points" -> LecturerWriteActions.updatePoints(
                                    client = client,
                                    state = creds,
                                    nodeId = nodeId,
                                    studentId = studentId,
                                    points = pointsInput.toDouble(),
                                    publicComment = commentInput,
                                    lastChanged = lastChangedInput.ifBlank { null },
                                    preferences = preferences,
                                    checkAccount = checkSameAccount
                                )
                                "grade" -> LecturerWriteActions.updateGrade(
                                    client = client,
                                    state = creds,
                                    nodeId = nodeId,
                                    studentId = studentId,
                                    order = gradeOrderInput.toInt(),
                                    publicComment = commentInput,
                                    lastChanged = lastChangedInput.ifBlank { null },
                                    preferences = preferences,
                                    checkAccount = checkSameAccount
                                )
                                "gradeProps" -> LecturerWriteActions.changeGradeNodeProperties(
                                    client = client,
                                    state = creds,
                                    nodeId = nodeId,
                                    namePl = namePlInput,
                                    nameEn = nameEnInput,
                                    visibleForStudents = visibleForStudents,
                                    preferences = preferences,
                                    checkAccount = checkSameAccount
                                )
                                else -> LecturerWriteActions.changeTaskNodeProperties(
                                    client = client,
                                    state = creds,
                                    nodeId = nodeId,
                                    namePl = namePlInput,
                                    nameEn = nameEnInput,
                                    visibleForStudents = visibleForStudents,
                                    pointsMin = pointsMinInput.toDoubleOrNull() ?: 0.0,
                                    pointsMax = pointsMaxInput.toDoubleOrNull() ?: 20.0,
                                    pointsPrecision = pointsPrecisionInput.toIntOrNull() ?: 1,
                                    preferences = preferences,
                                    checkAccount = checkSameAccount
                                )
                            }
                            onAuthoritativeNodeUpdated(updatedNode)
                            onStatusMessage(tr("Zapisano i zweryfikowano stan węzła z USOS.", "Saved and verified authoritative node state from USOS.", "Збережено та перевірено стан вузла з USOS."))
                        } catch (_: LecturerWriteActions.ConfirmedRefreshFailed) {
                            onStatusMessage(tr("Zapis potwierdzony, ale ponowny odczyt węzła nie powiódł się. Odśwież przed kolejną próbą.", "Write confirmed, but node re-read failed. Refresh before trying again.", "Запис підтверджено, але повторне читання вузла не вдалося. Оновіть перед повтором."))
                        } catch (_: LecturerWriteActions.Uncertain) {
                            onStatusMessage(tr("Wynik zapisu nieznany. Nie ponawiaj automatycznie — sprawdź stan w USOS.", "Write outcome unknown. Do not retry automatically — check status in USOS.", "Результат запису невідомий. Не повторюйте автоматично — перевірте стан у USOS."))
                        } catch (e: Exception) {
                            onStatusMessage(SyncProgress.error(e, language))
                        }
                    }
                }) { Text(tr("Potwierdź", "Confirm", "Підтвердити")) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDialog = false }) { Text(tr("Anuluj", "Cancel", "Скасувати")) }
            }
        )
    }
}

@Composable
internal fun StaffDirectorySection(
    state: JSONObject,
    repository: SharedRepository,
    language: String,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    fun tr(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<JSONObject?>(null) }
    var staffSchedule by remember { mutableStateOf<Pair<String, JSONArray>?>(null) }
    val entry = state.optJSONObject("studentServices")?.optJSONObject("staffDirectory")
    val activeData = searchResults ?: entry?.optJSONObject("data")
    val users = activeData?.optJSONArray("users") ?: JSONArray()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHead(
            title = tr("Katalog dydaktyków i plan pracowników", "Staff directory & schedule", "Каталог викладачів і розклад"),
            onClick = {
                runAsync {
                    searchResults = null
                    StudentServiceLoader.refresh(repository, "staffDirectory")
                    onReloadState()
                }
            },
            modifier = Modifier
        )
        ServiceStatusBanner(entry, language)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(tr("Szukaj prowadzącego (min. 2 znaki)", "Search teacher (min. 2 chars)", "Пошук викладача (мін. 2 символи)")) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                enabled = query.trim().length >= 2 && state.optString("token").isNotEmpty() && state.optString("token") != "demo",
                onClick = {
                    val q = query.trim()
                    runAsync {
                        val creds = repository.read()
                        check(SharedRepository.sameAccount(state, creds))
                        searchResults = ToolServiceData.searchTeachers(UsosClient(creds), q)
                    }
                }
            ) {
                Text(tr("Szukaj", "Search", "Знайти"))
            }
        }
        if (users.length() == 0) {
            QuietLine(tr("Brak pobranych pracowników. Użyj wyszukiwarki lub pobierz indeks wydziału.", "No staff entries loaded. Search or load the faculty index.", "Немає завантажених викладачів. Скористайтеся пошуком або завантажте індекс."))
        } else {
            Tray {
                for (i in 0 until users.length()) {
                    if (i > 0) TrayDivider()
                    val u = users.getJSONObject(i)
                    val uid = u.optString("id")
                    val fullName = listOf(u.optString("title_before"), u.optString("first_name"), u.optString("last_name"), u.optString("title_after"))
                        .filter { it.isNotBlank() }.joinToString(" ")
                    val sub = listOf(
                        StudentServices.localized(u.opt("office_hours"), language),
                        u.optString("room").takeIf { it.isNotEmpty() }?.let { tr("Pokój ", "Room ", "Кімн. ") + it } ?: ""
                    ).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "ID: $uid" }
                    ItemRow(
                        title = fullName.ifEmpty { "ID: $uid" },
                        sub = sub,
                        onClick = if (uid.isNotEmpty() && state.optString("token").isNotEmpty() && state.optString("token") != "demo") {
                            {
                                runAsync {
                                    val creds = repository.read()
                                    check(SharedRepository.sameAccount(state, creds))
                                    val today = LocalDate.now(UsosClient.ZONE).toString()
                                    val lessons = ToolServiceData.fetchStaffSchedule(UsosClient(creds), uid, today)
                                    staffSchedule = fullName.ifEmpty { uid } to lessons
                                }
                            }
                        } else null
                    )
                }
            }
            if (activeData?.optBoolean("hasMore", false) == true) {
                QuietLine(tr("Pokazano pierwsze ${LecturerServices.MAX_STAFF_RESULTS} wyników. Doprecyzuj zapytanie.", "Showing first ${LecturerServices.MAX_STAFF_RESULTS} results. Refine your search.", "Показано перші ${LecturerServices.MAX_STAFF_RESULTS} результатів. Уточніть запит."))
            }
        }
        staffSchedule?.let { (teacherName, lessons) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Plan 7-dniowy: ", "7-day schedule: ", "7-денний розклад: ") + teacherName, style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { staffSchedule = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                    if (lessons.length() == 0) {
                        Text(tr("Brak zaplanowanych zajęć w najbliższych 7 dniach.", "No classes scheduled in the next 7 days.", "Немає занять у найближчі 7 днів."))
                    } else {
                        for (i in 0 until lessons.length()) {
                            val l = lessons.getJSONObject(i)
                            val title = StudentServices.localized(l.opt("name"), language)
                            val room = l.optString("room_number")
                            Text("$title · ${l.optString("start_time")} – ${l.optString("end_time")}" + (if (room.isNotEmpty()) " ($room)" else ""))
                        }
                    }
                }
            }
        }
    }
}

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
    fun tr(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    val services = state.optJSONObject("studentServices")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (section == "registrations" || section == "services") {
            SectionHead(
                title = tr("Rejestracje i koszyk zapisów (tylko odczyt)", "Course registrations & cart (read-only)", "Реєстрації та кошик записів (лише читання)"),
                onClick = {
                    runAsync {
                        StudentServiceLoader.refresh(repository, "registrations")
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
                        ItemRow(
                            title = StudentServices.localized(r.opt("description"), language).ifEmpty { r.optString("id") },
                            sub = listOf(r.optString("status"), StudentServices.localized(r.opt("message"), language)).filter { it.isNotBlank() }.joinToString(" · ")
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
        }

        if (section == "academicCalendar" || section == "exams" || section == "services") {
            SectionHead(
                title = tr("Sesja egzaminacyjna i kalendarz akademicki", "Exam sessions & academic calendar", "Екзаменаційна сесія та академічний календар"),
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
            val exams = examEntry?.optJSONObject("data")?.optJSONArray("exams") ?: JSONArray()
            val events = calEntry?.optJSONObject("data")?.optJSONArray("events") ?: JSONArray()
            val progs = calEntry?.optJSONObject("data")?.optJSONArray("programmes") ?: JSONArray()
            if (exams.length() == 0 && events.length() == 0 && progs.length() == 0) {
                QuietLine(tr("Brak pobranych wydarzeń kalendarza lub egzaminów.", "No calendar events or exams loaded.", "Немає завантажених подій календаря чи іспитів."))
            } else {
                Tray {
                    var anyRow = false
                    for (i in 0 until exams.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val e = exams.getJSONObject(i)
                        ItemRow(
                            title = StudentServices.localized(e.opt("course_name"), language).ifEmpty { e.optString("code") },
                            sub = "${e.optString("term_id")} · ${e.optString("registration_start")} – ${e.optString("registration_end")}"
                        )
                    }
                    for (i in 0 until events.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val ev = events.getJSONObject(i)
                        val dayOff = if (ev.optBoolean("is_day_off", false)) tr("dzień wolny", "day off", "вихідний") else ev.optString("type")
                        ItemRow(
                            title = StudentServices.localized(ev.opt("name"), language).ifEmpty { ev.optString("id") },
                            sub = "${ev.optString("start_date")} – ${ev.optString("end_date")} · $dayOff"
                        )
                    }
                    for (i in 0 until progs.length()) {
                        if (anyRow) TrayDivider()
                        anyRow = true
                        val p = progs.getJSONObject(i)
                        ItemRow(
                            title = StudentServices.localized(p.opt("programme_name"), language).ifEmpty { p.optString("programme_id") },
                            sub = "${p.optString("status")} · ${p.optString("admission_date")}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusBanner(entry: JSONObject?, language: String) {
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

