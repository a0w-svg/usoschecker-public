package pl.vistula.checker.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.KeyValueStore
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.StudentServiceLoader
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.AttendanceTracker
import pl.vistula.checker.core.GpaCalculator
import pl.vistula.checker.core.GradeSummary
import pl.vistula.checker.core.OAuth
import pl.vistula.checker.core.PlatonLinks
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.TaskTools
import pl.vistula.checker.core.WebLinks

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountSection(
    state: JSONObject,
    repository: SharedRepository,
    preferences: KeyValueStore,
    themeMode: String,
    language: String,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onEnableDemo: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    fun t(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    val demo = state.optString("token") == "demo"
    val connected = state.optString("token").isNotEmpty() && !demo
    val pendingToken = state.optString("requestToken")
    var keyInput by remember(state.optString("key")) { mutableStateOf(state.optString("key")) }
    var secretInput by remember(state.optString("secret")) { mutableStateOf(state.optString("secret")) }
    var scopesInput by remember(state.optString("scopes")) {
        mutableStateOf(state.optString("scopes").ifEmpty { "grades|studies|payments|crstests|cards|adm_documents|email|photo|offline_access" })
    }
    var pinInput by remember(pendingToken) { mutableStateOf("") }
    var showSecret by remember(state.optString("secret"), state.optString("accountSession")) { mutableStateOf(false) }
    var confirmDisconnect by remember(state.optString("accountSession")) { mutableStateOf(false) }
    val editSession = remember(state.optString("accountSession"), state.optString("token")) { repository.editSession(state) }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Konto USOS i wygląd", "USOS account and appearance", "Обліковий запис USOS і вигляд"), style = MaterialTheme.typography.titleLarge)
            when {
                demo -> {
                    Text(t("Demo nie zmienia zapisanego konta ani jego danych.", "Demo leaves your saved account and its data intact.", "Демо зберігає ваш обліковий запис і його дані без змін."))
                    Button(onClick = onEnableDemo) { Text(t("Zakończ demo", "Exit demo", "Вийти з демо")) }
                }
                connected -> {
                    Text(state.optString("user"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    val details = listOf("studentNumber", "programme", "stage", "studentStatus", "email")
                        .map { state.optString(it) }.filter { it.isNotBlank() }
                    if (details.isNotEmpty()) Text(details.joinToString(" · "))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Switch(checked = state.optBoolean("enabled"), onCheckedChange = { enabled ->
                            runAsync { repository.update(editSession) { it.put("enabled", enabled) }; onReloadState() }
                        })
                        Text(t("Odświeżanie w tle", "Background refresh", "Фонове оновлення"))
                    }
                    OutlinedButton(onClick = { confirmDisconnect = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(t("Odłącz konto", "Disconnect account", "Відключити обліковий запис"))
                    }
                }
                else -> {
                    Text(t("Połącz konto własnym kluczem OAuth i kodem PIN z USOS.",
                        "Connect with your OAuth credentials and the PIN from USOS.", "Підключіться власними даними OAuth і PIN-кодом USOS."))
                    OutlinedTextField(value = keyInput, onValueChange = { keyInput = it }, label = { Text("Consumer Key") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = secretInput, onValueChange = { secretInput = it }, label = { Text("Consumer Secret") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = if (showSecret) androidx.compose.ui.text.input.VisualTransformation.None
                            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = { androidx.compose.material3.TextButton(onClick = { showSecret = !showSecret }) {
                            Text(if (showSecret) t("Ukryj", "Hide", "Приховати") else t("Pokaż", "Show", "Показати"))
                        } })
                    OutlinedTextField(value = scopesInput, onValueChange = { scopesInput = it },
                        label = { Text(t("Zakresy zgody", "Permission scopes", "Дозволи доступу")) }, modifier = Modifier.fillMaxWidth(), singleLine = true); ScopePresetRow(scopesInput, language) { scopesInput = it }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = keyInput.isNotBlank() && secretInput.isNotBlank(), onClick = {
                            val key = keyInput.trim(); val secret = secretInput.trim(); val scopes = scopesInput.trim()
                            runAsync {
                                repository.begin(key, secret, scopes)
                                val token = repository.read().optString("requestToken")
                                check(token.isNotBlank())
                                onOpenUrl(UsosClient.BASE + "services/oauth/authorize?oauth_token=" + OAuth.encode(token))
                                onReloadState()
                                onStatusMessage(t("Autoryzuj w przeglądarce i wklej PIN.", "Authorize in your browser and paste the PIN.", "Авторизуйтеся в браузері та вставте PIN."))
                            }
                        }) { Text(t("1. Rozpocznij logowanie", "1. Start sign-in", "1. Почати вхід")) }
                        OutlinedButton(onClick = onEnableDemo) { Text(t("Wypróbuj demo", "Try demo", "Спробувати демо")) }
                    }
                    if (pendingToken.isNotEmpty()) {
                        OutlinedTextField(value = pinInput, onValueChange = { pinInput = it },
                            label = { Text(t("PIN z USOS", "PIN from USOS", "PIN із USOS")) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        Button(enabled = pinInput.isNotBlank(), onClick = {
                            val pin = pinInput.trim(); pinInput = ""
                            runAsync {
                                try {
                                    repository.finish(pin)
                                    secretInput = ""; showSecret = false
                                    repository.sync(true)
                                    onStatusMessage(t("Połączono i odświeżono konto.", "Account connected and refreshed.", "Обліковий запис підключено та оновлено."))
                                } finally { onReloadState() }
                            }
                        }) { Text(t("2. Zatwierdź PIN", "2. Confirm PIN", "2. Підтвердити PIN")) }
                    }
                }
            }
            TextButton(onClick = { onOpenUrl("https://github.com/Kaydjen") }) {
                Text(t("Współautor designu: Kaydjen", "Design co-author: Kaydjen", "Співавтор дизайну: Kaydjen"))
            }
            ExperimentalLecturerWritesSettingRow(state, preferences, language, onReloadState); Text(t("Motyw", "Theme", "Тема"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to t("Systemowy", "System", "Системна"), "light" to t("Jasny", "Light", "Світла"),
                    "dark" to t("Ciemny", "Dark", "Темна"), "oled" to "OLED").forEach { (mode, label) ->
                    FilterChip(selected = themeMode == mode, onClick = { onThemeChanged(mode) }, label = { Text(label) })
                }
            }
            Text(t("Język interfejsu i danych", "Interface and data language", "Мова інтерфейсу та даних"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pl" to "Polski", "en" to "English", "uk" to "Українська").forEach { (tag, label) ->
                    FilterChip(selected = language == tag, onClick = { onLanguageChanged(tag) }, label = { Text(label) })
                }
            }
        }
    }
    if (confirmDisconnect) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        title = { Text(t("Odłączyć konto?", "Disconnect account?", "Відключити обліковий запис?")) },
        text = { Text(t("Usunie to lokalne dane konta z tego urządzenia.", "This removes local account data from this device.", "Це видалить локальні дані облікового запису з цього пристрою.")) },
        confirmButton = { Button(onClick = {
            confirmDisconnect = false
            runAsync { repository.disconnect(); secretInput = ""; pinInput = ""; onReloadState() }
        }) { Text(t("Odłącz", "Disconnect", "Відключити")) } },
        dismissButton = { OutlinedButton(onClick = { confirmDisconnect = false }) { Text(t("Anuluj", "Cancel", "Скасувати")) } }
    )
}

@Composable
fun GpaSimulatorSection(snapshot: Snapshot?, language: String = "pl") {
    fun t(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    val summary = remember(snapshot) {
        if (snapshot == null) GradeSummary.of(emptyList()) else GpaCalculator.calculate(snapshot.records.values)
    }
    val currentGpa = if (summary.ectsAverage.isNaN()) (if (summary.average.isNaN()) 0.0 else summary.average) else summary.ectsAverage
    val currentEcts = if (summary.totalEcts > 0) summary.totalEcts else summary.numeric * 4.0
    var targetGpa by remember { mutableDoubleStateOf(GpaCalculator.SCHOLARSHIP_THRESHOLD) }
    var remainingEcts by remember { mutableDoubleStateOf(15.0) }
    val sim = pl.vistula.checker.core.ApiLanguage.`in`(language) { GpaCalculator.simulate(currentGpa, currentEcts, targetGpa, remainingEcts) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Symulator GPA i stypendium", "GPA and scholarship simulator", "Симулятор GPA та стипендії"), style = MaterialTheme.typography.titleLarge)
            Text(
                text = String.format(
                    Locale.forLanguageTag(language),
                    t("Obecna średnia ważona: %s (ECTS: %.1f)", "Current weighted average: %s (ECTS: %.1f)", "Поточний середньозважений бал: %s (ECTS: %.1f)"),
                    if (currentGpa > 0) String.format(Locale.forLanguageTag(language), "%.2f", currentGpa) else "—",
                    currentEcts
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(pl.vistula.checker.core.ApiLanguage.`in`(language) { GpaCalculator.scholarshipStatus(if (currentGpa > 0) currentGpa else Double.NaN) }, style = MaterialTheme.typography.bodyMedium)
            Text(pl.vistula.checker.core.ApiLanguage.`in`(language) { GpaCalculator.honorsStatus(if (currentGpa > 0) currentGpa else Double.NaN) }, style = MaterialTheme.typography.bodyMedium)

            Text(
                String.format(Locale.forLanguageTag(language), t("Docelowa średnia: %.2f", "Target average: %.2f", "Бажаний середній бал: %.2f"), targetGpa),
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = targetGpa.toFloat(),
                onValueChange = { targetGpa = it.toDouble() },
                valueRange = 3.0f..5.0f
            )
            Text(
                String.format(Locale.forLanguageTag(language), t("Pozostałe punkty ECTS: %.0f", "Remaining ECTS credits: %.0f", "Залишок кредитів ECTS: %.0f"), remainingEcts),
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = remainingEcts.toFloat(),
                onValueChange = { remainingEcts = it.toInt().toDouble() },
                valueRange = 1f..60f
            )
            Text(
                text = sim.statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (sim.achievable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksSection(
    state: JSONObject, repository: SharedRepository, language: String,
    onReloadState: () -> Unit, runAsync: (() -> Unit) -> Unit
) {
    fun t(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    var titleInput by remember { mutableStateOf("") }
    var courseInput by remember { mutableStateOf("") }
    var dueInput by remember { mutableStateOf(LocalDate.now(UsosClient.ZONE).plusDays(7).toString()) }
    var filter by remember { mutableStateOf("open") }
    var deleteTarget by remember { mutableStateOf<JSONObject?>(null) }
    val dueValid = dueInput.isBlank() || runCatching { LocalDate.parse(dueInput) }.isSuccess
    val sorted = TaskTools.sorted(state.optJSONArray("tasks"), "due")
        .filter { TaskTools.matches(it, filter, "", LocalDate.now(UsosClient.ZONE)) }
    val session = repository.editSession(state)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Zadania i terminy", "Tasks and deadlines", "Завдання та терміни"), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("open" to t("Do zrobienia", "To do", "До виконання"), "overdue" to t("Po terminie", "Overdue", "Прострочені"),
                    "done" to t("Ukończone", "Completed", "Виконані"), "all" to t("Wszystkie", "All", "Усі")).forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label) })
                }
            }
            OutlinedTextField(value = titleInput, onValueChange = { titleInput = it }, singleLine = true,
                label = { Text(t("Nowe zadanie", "New task", "Нове завдання")) }, modifier = Modifier.fillMaxWidth(), isError = titleInput.length > 200)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = courseInput, onValueChange = { courseInput = it }, singleLine = true,
                    label = { Text(t("Przedmiot", "Course", "Предмет")) }, modifier = Modifier.width(210.dp), isError = courseInput.length > 200)
                OutlinedTextField(value = dueInput, onValueChange = { dueInput = it }, singleLine = true,
                    label = { Text(t("Termin (YYYY-MM-DD)", "Due date (YYYY-MM-DD)", "Термін (YYYY-MM-DD)")) }, modifier = Modifier.width(210.dp), isError = !dueValid)
            }
            Button(enabled = titleInput.isNotBlank() && titleInput.length <= 200 && courseInput.length <= 200 && dueValid, onClick = {
                val title = titleInput; val course = courseInput; val due = dueInput
                runAsync {
                    repository.update(session) { it.put("tasks", pl.vistula.checker.DesktopTaskActions.create(it.optJSONArray("tasks"), title, course, due)) }
                    if (titleInput == title) titleInput = ""
                    onReloadState()
                }
            }) { Text(t("Dodaj", "Add", "Додати")) }
            if (sorted.isEmpty()) Text(t("Brak zadań w wybranym widoku.", "No tasks in this view.", "У цьому перегляді немає завдань."))
            for (task in sorted) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text((if (task.optBoolean("done")) "✓ " else "") + task.optString("title"), style = MaterialTheme.typography.titleMedium)
                    Text(listOf(task.optString("course"), task.optString("due")).filter { it.isNotBlank() }.joinToString(" · "))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { runAsync {
                            repository.update(session) { it.put("tasks", pl.vistula.checker.DesktopTaskActions.toggle(it.optJSONArray("tasks"), task)) }
                            onReloadState()
                        } }) { Text(if (task.optBoolean("done")) t("Cofnij", "Reopen", "Поновити") else t("Gotowe", "Complete", "Виконано")) }
                        OutlinedButton(onClick = { deleteTarget = task }) { Text(t("Usuń", "Delete", "Видалити")) }
                    }
                }
            }
        }
    }
    deleteTarget?.let { task -> androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleteTarget = null }, title = { Text(t("Usunąć zadanie?", "Delete task?", "Видалити завдання?")) },
        text = { Text(task.optString("title")) }, confirmButton = {
            Button(onClick = { deleteTarget = null; runAsync {
                repository.update(session) { it.put("tasks", pl.vistula.checker.DesktopTaskActions.delete(it.optJSONArray("tasks"), task)) }
                onReloadState()
            } }) { Text(t("Usuń", "Delete", "Видалити")) }
        }, dismissButton = { OutlinedButton(onClick = { deleteTarget = null }) { Text(t("Anuluj", "Cancel", "Скасувати")) } }
    ) }
}

@Composable
fun AttendanceSection(
    snapshot: Snapshot?,
    preferences: KeyValueStore,
    strings: UiStrings = UiStrings.PL,
    onStatusMessage: (String) -> Unit = {}
) {
    var savedJson by remember(snapshot) {
        val raw = preferences.getString("attendance_data", "{}")
        val parsed = try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
        mutableStateOf(AttendanceTracker.reconcile(parsed, snapshot))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${strings.sectionAttendance} (limit: ${AttendanceTracker.LIMIT})", style = MaterialTheme.typography.titleLarge)
            val keys = savedJson.keys().asSequence().toList().sorted()
            if (keys.isEmpty()) {
                Text(toolText(strings.languageCode, "Brak zajęć w planie do śledzenia obecności.", "No classes available for attendance tracking.", "Немає занять для відстеження відвідуваності."), style = MaterialTheme.typography.bodyMedium)
            } else {
                keys.forEach { key ->
                    val entry = savedJson.optJSONObject(key) ?: return@forEach
                    val count = entry.optInt("absences", 0)
                    val limit = entry.optInt("limit", AttendanceTracker.LIMIT).coerceAtLeast(1)
                    val name = entry.optString("name", key)
                    val classType = entry.optString("classType")
                    val term = entry.optString("term")
                    val isExceeded = AttendanceTracker.exceeded(count)
                    val isWarning = AttendanceTracker.warning(count)
                    val statusColor = when {
                        isExceeded -> MaterialTheme.colorScheme.error
                        isWarning -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val statusContainer = when {
                        isExceeded -> MaterialTheme.colorScheme.errorContainer
                        isWarning -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val statusOnContainer = when {
                        isExceeded -> MaterialTheme.colorScheme.onErrorContainer
                        isWarning -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                    val statusLabel = when {
                        isExceeded -> "! ${strings.attendanceExceeded} ($count/$limit)"
                        isWarning -> "⚠ ${strings.attendanceWarning} ($count/$limit)"
                        else -> "✓ ${strings.attendanceOk} ($count/$limit)"
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                val meta = listOfNotNull(
                                    classType.takeIf { it.isNotEmpty() },
                                    term.takeIf { it.isNotEmpty() }
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) {
                                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    color = statusContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = statusLabel,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = statusOnContainer
                                    )
                                }
                                OutlinedButton(
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                    onClick = {
                                        val next = JSONObject(savedJson.toString())
                                        next.getJSONObject(key).put("absences", (count - 1).coerceAtLeast(0))
                                        try {
                                            preferences.putString("attendance_data", next.toString())
                                            savedJson = next
                                        } catch (e: Exception) { onStatusMessage(pl.vistula.checker.core.SyncProgress.error(e, strings.languageCode)) }
                                    }
                                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                                OutlinedButton(
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                    onClick = {
                                        val next = JSONObject(savedJson.toString())
                                        next.getJSONObject(key).put("absences", count + 1)
                                        try {
                                            preferences.putString("attendance_data", next.toString())
                                            savedJson = next
                                        } catch (e: Exception) { onStatusMessage(pl.vistula.checker.core.SyncProgress.error(e, strings.languageCode)) }
                                    }
                                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { (count.toFloat() / limit.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentsAndServicesSection(
    state: JSONObject,
    snapshot: Snapshot?,
    language: String = "pl",
    repository: SharedRepository,
    onOpenUrl: (String) -> Unit,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: ((() -> Unit) -> Unit),
    section: String = "services"
) {
    fun t(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    val live = state.optString("token").isNotEmpty() && state.optString("token") != "demo" && !state.optBoolean("authorizationError")
    val payments = ToolReadModels.payments(state, snapshot, language)
    if (section == "services" || section == "payments") Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Płatności i subkonta USOS", "USOS payments and accounts", "Платежі та рахунки USOS"), style = MaterialTheme.typography.titleLarge)
            OutlinedButton(enabled = live && StudentServices.hasScope(state.optString("scopes"), "payments"), onClick = {
                runAsync { StudentServiceLoader.refresh(repository, "payments"); onReloadState() }
            }) { Text(t("Odśwież płatności", "Refresh payments", "Оновити платежі")) }
            val paymentError = state.optJSONObject("studentServices")?.optJSONObject("payments")
            if (!paymentError?.optString("error").isNullOrEmpty()) Text(pl.vistula.checker.core.SyncProgress.cachedError(paymentError, language), color = MaterialTheme.colorScheme.error)
            if (payments == null) {
                Text(t("Brak danych o płatnościach. Zsynchronizuj konto.", "No payment data. Refresh your account.", "Немає даних про платежі. Оновіть обліковий запис."), style = MaterialTheme.typography.bodyMedium)
            } else {
                if (payments.statusNotice.isNotEmpty()) {
                    Text(payments.statusNotice, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                }
                payments.accounts.forEach { acc ->
                    Text(
                        "${acc.name}: ${acc.bankAccountNumber} · ${acc.balance} ${acc.currency}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (payments.hasUnknownSettlement()) Text(t("Nie można ustalić rozliczenia wszystkich pozycji. Sprawdź saldo w USOSweb.", "Settlement is not known for every item. Check your balance in USOSweb.", "Статус оплати деяких позицій невідомий. Перевірте баланс у USOSweb."))
                payments.installments.forEach { inst ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(inst.name, style = MaterialTheme.typography.titleMedium)
                            val status = if (inst.isSettlementUnknown()) t("Rozliczenie nieznane", "Settlement unknown", "Статус оплати невідомий") else inst.status
                            Text("${inst.deadline.ifEmpty { "—" }} · $status", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(pl.vistula.checker.core.PaymentAmounts.format(inst.amount, inst.currency, language), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    val services = state.optJSONObject("studentServices")
    if (section != "payments") Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t("Legitymacje i dokumenty", "Student cards and documents", "Посвідчення та документи"), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = live && listOf("cards", "adm_documents").any { StudentServices.hasScope(state.optString("scopes"), it) },
                        onClick = {
                            runAsync {
                                try {
                                    for ((section, consent) in listOf("cards" to "cards", "documents" to "adm_documents")) {
                                        if (StudentServices.hasScope(state.optString("scopes"), consent)) StudentServiceLoader.refresh(repository, section)
                                    }
                                    onReloadState()
                                    val entries = repository.read().optJSONObject("studentServices")
                                    val failure = listOf("mcard", "physicalCards", "documents").mapNotNull { entries?.optJSONObject(it) }.firstOrNull { it.optString("error").isNotEmpty() }
                                    onStatusMessage(if (failure != null) pl.vistula.checker.core.SyncProgress.cachedError(failure, language) else t("Odświeżono legitymacje i dokumenty.", "Student cards and documents refreshed.", "Посвідчення та документи оновлено."))
                                } catch (e: Exception) {
                                    onStatusMessage(pl.vistula.checker.core.SyncProgress.error(e, language))
                                }
                            }
                        }
                    ) {
                        Text(t("Pobierz z USOS", "Load from USOS", "Завантажити з USOS"))
                    }
                }
            }
            for (key in listOf("mcard", "physicalCards", "documents")) {
                val entry = services?.optJSONObject(key)
                if (!entry?.optString("error").isNullOrEmpty()) Text(pl.vistula.checker.core.SyncProgress.cachedError(entry, language), color = MaterialTheme.colorScheme.error)
            }
            if (section != "documents") {
                Text(t("Karty fizyczne", "Physical cards", "Фізичні картки"), style = MaterialTheme.typography.titleMedium)
                val cards = services?.optJSONObject("physicalCards")?.optJSONArray("data")
                val cardEntry = services?.optJSONObject("physicalCards")
                val fetchedAt = cardEntry?.optLong("fetchedAt") ?: 0L
                if (fetchedAt > 0) {
                    val timestamp = java.time.Instant.ofEpochMilli(fetchedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    Text(t("Pobrano: ", "Fetched: ", "Завантажено: ") + timestamp, style = MaterialTheme.typography.bodySmall)
                }
                if (cards != null && !cardEntry?.optString("error").isNullOrEmpty()) Text(t("Pokazano zapisane dane.", "Showing cached data.", "Показано збережені дані."))
                if (cards == null) {
                    Text(t("Dane kart nie zostały jeszcze pobrane.", "Card data has not been loaded yet.", "Дані карток ще не завантажено."))
                } else if (cards.length() == 0) {
                    Text(t("USOS nie zwrócił kart fizycznych.", "USOS returned no physical cards.", "USOS не повернув фізичних карток."))
                } else for (i in 0 until cards.length()) {
                    val card = StudentServices.Card(cards.optJSONObject(i))
                    if (card.type.isNotEmpty()) Text(t("Typ karty: ", "Card type: ", "Тип картки: ") + card.type)
                    if (card.studentNumber.isNotEmpty()) Text(t("Numer albumu: ", "Student number: ", "Номер студента: ") + card.studentNumber)
                    if (card.issued.isNotEmpty()) Text(t("Data wydania: ", "Issued: ", "Дата видачі: ") + card.issued)
                    if (card.expires.isNotEmpty()) Text(t("Data ważności: ", "Expires: ", "Дійсне до: ") + card.expires)
                }
                MobileCardControls(state, repository, language, onReloadState, onStatusMessage, runAsync)
            }
            val docsArr = services?.optJSONObject("documents")?.optJSONArray("data")
            if (section != "cards" && docsArr != null && docsArr.length() > 0) {
                for (i in 0 until docsArr.length()) {
                    val doc = StudentServices.Document(docsArr.getJSONObject(i), language)
                    Text(
                        "${doc.name} (${doc.typeName}) · ${doc.status}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (section != "cards" && (docsArr == null || docsArr.length() == 0)) {
                Text(t("Brak pobranych dokumentów.", "No documents loaded.", "Немає завантажених документів."), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    val thesis = snapshot?.thesis
    if (section == "services" && snapshot?.serviceErrors?.containsKey("theses") == true) {
        Text(t("Nie udało się pobrać danych pracy dyplomowej. Spróbuj ponownie po synchronizacji.", "Thesis information could not be loaded. Try syncing again.", "Не вдалося завантажити дані дипломної роботи. Спробуйте синхронізувати ще раз."))
    }
    if (section == "services" && thesis != null && thesis.hasThesis()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Praca dyplomowa", "Thesis", "Дипломна робота"), style = MaterialTheme.typography.titleLarge)
                Text(thesis.title, style = MaterialTheme.typography.titleMedium)
                val details = listOf(thesis.status, thesis.supervisors).filter { it.isNotEmpty() }.joinToString(" · ")
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (section == "services") {
        AcademicExtrasSection(state, repository, language, "services", onReloadState, onStatusMessage, runAsync)
    }
    if (section == "services") Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Szybkie linki uczelniane (USOS / Platon)", "University links (USOS / Platon)", "Посилання університету (USOS / Platon)"), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenUrl("https://usosweb.vistula.edu.pl/kontroler.php?_action=dla_stud/studia/oceny/index") }) { Text(t("USOSweb Oceny", "USOSweb grades", "Оцінки USOSweb")) }
                OutlinedButton(onClick = { onOpenUrl("https://usosweb.vistula.edu.pl/kontroler.php?_action=home/plan") }) { Text(t("USOSweb Plan", "USOSweb timetable", "Розклад USOSweb")) }
                OutlinedButton(onClick = { onOpenUrl(PlatonLinks.HOME) }) { Text("Platon LMS") }
                OutlinedButton(onClick = { onOpenUrl(WebLinks.apiDocumentation(null)) }) { Text("USOS API Docs") }
            }
        }
    }
}


@Composable
private fun MobileCardControls(
    state: JSONObject, repository: SharedRepository, language: String,
    onReloadState: () -> Unit, onStatusMessage: (String) -> Unit, runAsync: (() -> Unit) -> Unit
) {
    fun label(polish: String, en: String, uk: String) = when (pl.vistula.checker.core.ApiLanguage.normalize(language)) {
        "en" -> en; "uk" -> uk; else -> polish
    }
    val data = state.optJSONObject("studentServices")?.optJSONObject("mcard")?.optJSONObject("data")
    val latest = data?.optJSONObject("latest_order")
    val id = latest?.optString("id").orEmpty()
    val live = state.optString("token").isNotEmpty() && state.optString("token") != "demo" &&
        !state.optBoolean("authorizationError") && StudentServices.hasScope(state.optString("scopes"), "cards")
    var pending by remember(state.optString("accountSession"), id) { mutableStateOf<Boolean?>(null) }
    var activation by remember(state.optString("accountSession"), id) { mutableStateOf<JSONObject?>(null) }
    Text(label("mLegitymacja", "Mobile student card", "Мобільне студентське посвідчення"), style = MaterialTheme.typography.titleMedium)
    if (latest != null) {
        val rawStatus = StudentServices.text(latest, "status")
        val status = when (rawStatus) {
            "pending" -> label("Oczekuje na wysłanie", "Pending", "Очікує надсилання")
            "in_progress" -> label("W trakcie przetwarzania", "Processing", "Обробляється")
            "ready", "ready_to_receive" -> label("Gotowa do aktywacji", "Ready to activate", "Готове до активації")
            "received" -> label("Zainstalowana", "Installed", "Встановлено")
            "error" -> label("Błąd zamówienia", "Order error", "Помилка замовлення")
            "revoked", "revoked_by_me" -> label("Unieważniona", "Revoked", "Анулювано")
            else -> rawStatus.ifEmpty { label("Status nieznany", "Status unknown", "Статус невідомий") }
        }
        Text(status)
        val ordered = StudentServices.text(latest, "order_date")
        val expiry = StudentServices.text(latest, "expiration_date")
        if (ordered.isNotEmpty()) Text(label("Data zamówienia: ", "Ordered: ", "Дата замовлення: ") + ordered)
        if (expiry.isNotEmpty()) Text(label("Ważna do: ", "Expires: ", "Дійсне до: ") + expiry)
    } else if (data != null) Text(label("Brak bieżącego zamówienia", "No current order", "Немає поточного замовлення"))
    val expectedExpiry = StudentServices.text(data, "expected_expiration_date")
    if (expectedExpiry.isNotEmpty()) Text(label("Przewidywana ważność: ", "Expected expiry: ", "Очікувана дата закінчення: ") + expectedExpiry)
    for (order in listOf(true, false)) {
        val field = if (order) "why_cannot_order" else "why_cannot_revoke"
        val reason = StudentServices.localized(data?.opt(field), language)
        if (reason.isNotEmpty()) Text(reason)
        val known = data?.optBoolean(if (order) "orderEligibilityKnown" else "revokeEligibilityKnown") == true
        if (known && reason.isEmpty() && (order || id.isNotEmpty())) {
            OutlinedButton(enabled = live, onClick = { pending = order }) {
                Text(if (order) label("Wydaj", "Issue", "Видати") else label("Unieważnij", "Revoke", "Анулювати"))
            }
        }
    }
    if (id.isNotEmpty() && pl.vistula.checker.MobileCardActions.ready(latest?.optString("status").orEmpty())) {
        OutlinedButton(enabled = live, onClick = {
            runAsync {
                try {
                    val credentials = repository.read()
                    check(SharedRepository.sameAccount(state, credentials))
                    val result = pl.vistula.checker.MobileCardActions.activation(UsosClient(credentials), id)
                    check(SharedRepository.sameAccount(credentials, repository.read()))
                    activation = result
                } catch (_: Exception) {
                    onStatusMessage(label("Dane aktywacji nie są jeszcze dostępne.", "Activation data is not available yet.", "Дані активації ще недоступні."))
                }
            }
        }) { Text(label("Pobierz dane aktywacji", "Fetch activation data", "Отримати дані активації")) }
    }
    activation?.let { data ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { activation = null },
            title = { Text(label("Aktywacja w mObywatelu", "Activation in mObywatel", "Активація в mObywatel")) },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Column {
                        Text(label("Token aktywacji", "Activation token", "Токен активації"))
                        Text(StudentServices.text(data, "token"))
                        Text(label("Kod dystrybucyjny", "Distribution password", "Пароль розповсюдження"))
                        Text(StudentServices.text(data, "distribution_pass"))
                    }
                }
            },
            confirmButton = { Button(onClick = { activation = null }) { Text(label("Zamknij", "Close", "Закрити")) } }
        )
    }
    pending?.let { order ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (order) label("Wydać mLegitymację?", "Issue mobile card?", "Видати посвідчення?")
                else label("Unieważnić mLegitymację?", "Revoke mobile card?", "Анулювати посвідчення?")) },
            confirmButton = {
                Button(onClick = {
                    pending = null
                    runAsync {
                        try {
                            val credentials = repository.read()
                            check(SharedRepository.sameAccount(state, credentials))
                            pl.vistula.checker.MobileCardActions.perform(UsosClient(credentials), credentials, order, id) {
                                check(SharedRepository.sameAccount(credentials, repository.read()))
                            }
                            try {
                                StudentServiceLoader.refresh(repository, "cards")
                                onReloadState()
                            } catch (e: Exception) {
                                throw pl.vistula.checker.MobileCardActions.ConfirmedRefreshFailed(e)
                            }
                        } catch (_: pl.vistula.checker.MobileCardActions.ConfirmedRefreshFailed) {
                            onStatusMessage(label("Operacja potwierdzona. Nie udało się odświeżyć statusu; odśwież go przed ponowieniem.",
                                "Action confirmed. Status could not be refreshed; refresh it before trying again.", "Операцію підтверджено. Не вдалося оновити статус; оновіть його перед повтором."))
                        } catch (_: pl.vistula.checker.MobileCardActions.Uncertain) {
                            onStatusMessage(label("Wynik nieznany. Odśwież status przed ponowieniem.",
                                "Result unknown. Refresh the status before trying again.", "Результат невідомий. Оновіть статус перед повтором."))
                        } catch (_: Exception) {
                            onStatusMessage(label("Nie udało się wykonać operacji.", "Could not complete the action.", "Не вдалося виконати дію."))
                        }
                    }
                }) { Text(label("Potwierdź", "Confirm", "Підтвердити")) }
            },
            dismissButton = { OutlinedButton(onClick = { pending = null }) { Text(label("Anuluj", "Cancel", "Скасувати")) } }
        )
    }
}

@Composable
fun LecturerSection(
    state: JSONObject, repository: SharedRepository, language: String, onOpenUrl: (String) -> Unit,
    onReloadState: () -> Unit, onStatusMessage: (String) -> Unit, runAsync: (() -> Unit) -> Unit
) {
    if (!pl.vistula.checker.core.AccountRole.lecturer(state)) return; LecturerWorkspaceSection(state, repository, null, language, "teaching", onOpenUrl, onReloadState, onStatusMessage, runAsync); if (true) return
    fun label(polish: String, english: String, ukrainian: String) = when (pl.vistula.checker.core.ApiLanguage.normalize(language)) {
        "en" -> english; "uk" -> ukrainian; else -> polish
    }
    Text(label("Panel wykładowcy", "Lecturer panel", "Панель викладача"), style = MaterialTheme.typography.titleLarge)
    OutlinedButton(onClick = {
        runAsync {
            try {
                StudentServiceLoader.refresh(repository, "teaching")
                onReloadState()
            } catch (_: Exception) {
                onStatusMessage(label("Nie udało się pobrać przedmiotów.", "Could not load courses.", "Не вдалося завантажити предмети."))
            }
        }
    }) { Text(label("Odśwież", "Refresh", "Оновити")) }
    val courses = state.optJSONObject("studentServices")?.optJSONObject("teaching")?.optJSONObject("data")?.optJSONArray("courses")
    if (courses == null || courses.length() == 0) Text(label("Brak pobranych przedmiotów.", "No courses loaded.", "Немає завантажених предметів."))
    else for (i in 0 until courses.length()) {
        val course = courses.getJSONObject(i)
        Text(StudentServices.localized(course.opt("name"), language), style = MaterialTheme.typography.titleMedium)
        Text(StudentServices.localized(course.opt("term"), language))
    }
    OutlinedButton(onClick = { onOpenUrl("https://usosweb.vistula.edu.pl/kontroler.php?_action=dla_prac") }) {
        Text(label("Funkcje wykładowcy w USOSweb", "Lecturer tools in USOSweb", "Функції викладача в USOSweb"))
    }
}
