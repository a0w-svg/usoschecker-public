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
import pl.vistula.checker.Demo
import pl.vistula.checker.DemoStudentServices
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
    onOpenUrl: (String) -> Unit,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: ((() -> Unit) -> Unit)
) {
    val connected = state.optString("token").isNotEmpty()
    val pendingToken = state.optString("requestToken")
    var keyInput by remember(state.optString("key")) { mutableStateOf(state.optString("key")) }
    var secretInput by remember(state.optString("secret")) { mutableStateOf(state.optString("secret")) }
    var scopesInput by remember(state.optString("scopes")) {
        mutableStateOf(
            state.optString("scopes").ifEmpty {
                "grades|studies|payments|crstests|cards|adm_documents|email|photo|offline_access"
            }
        )
    }
    var pinInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Konto USOS i wygląd", style = MaterialTheme.typography.titleLarge)
            if (connected) {
                Text(
                    text = state.optString("user", "Student USOS"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                val details = listOfNotNull(
                    state.optString("studentNumber").takeIf { it.isNotEmpty() }?.let { "Album: $it" },
                    state.optString("programme").takeIf { it.isNotEmpty() },
                    state.optString("stage").takeIf { it.isNotEmpty() },
                    state.optString("studentStatus").takeIf { it.isNotEmpty() },
                    state.optString("email").takeIf { it.isNotEmpty() }
                )
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            repository.disconnect()
                            onReloadState()
                            onStatusMessage("Odłączono konto USOS i wyczyszczono sejf.")
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Odłącz konto")
                    }
                }
            } else {
                Text(
                    "Połącz konto USOS za pomocą klucza OAuth 1.0a lub uruchom tryb demonstracyjny.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Consumer Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it },
                    label = { Text("Consumer Secret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = scopesInput,
                    onValueChange = { scopesInput = it },
                    label = { Text("Zakresy zgody (scopes)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val k = keyInput.trim()
                            val s = secretInput.trim()
                            val sc = scopesInput.trim()
                            runAsync {
                                try {
                                    repository.begin(k, s, sc)
                                    val updated = repository.read()
                                    val reqTok = updated.optString("requestToken")
                                    if (reqTok.isNotEmpty()) {
                                        onOpenUrl(
                                            UsosClient.BASE + "services/oauth/authorize?oauth_token=" +
                                                OAuth.encode(reqTok)
                                        )
                                    }
                                    onReloadState()
                                    onStatusMessage("Autoryzuj aplikację w przeglądarce i wklej kod PIN.")
                                } catch (e: Exception) {
                                    onStatusMessage(SharedRepository.message(e))
                                }
                            }
                        }
                    ) {
                        Text("1. Rozpocznij logowanie")
                    }
                    OutlinedButton(
                        onClick = {
                            val demo = Demo.state(true)
                            demo.put("studentServices", DemoStudentServices.data())
                            repository.update { s ->
                                for (k in demo.keys()) s.put(k, demo.get(k))
                            }
                            onReloadState()
                            onStatusMessage("Wczytano dane demonstracyjne.")
                        }
                    ) {
                        Text("Tryb Demo")
                    }
                }
                if (pendingToken.isNotEmpty()) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Kod PIN z USOS") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val pin = pinInput.trim()
                            runAsync {
                                try {
                                    repository.finish(pin)
                                    repository.sync(true)
                                    onReloadState()
                                    onStatusMessage("Połączono i zsynchronizowano konto USOS.")
                                } catch (e: Exception) {
                                    onReloadState()
                                    onStatusMessage(SharedRepository.message(e))
                                }
                            }
                        }
                    ) {
                        Text("2. Zatwierdź PIN")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Motyw kolorystyczny", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "system" to "Systemowy",
                    "light" to "Jasny",
                    "dark" to "Ciemny",
                    "oled" to "OLED Czarny"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = {
                            preferences.putString("theme", mode)
                            onThemeChanged(mode)
                        },
                        label = { Text(label) }
                    )
                }
            }

            Text("Język interfejsu i danych USOS", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pl" to "Polski", "en" to "English", "uk" to "Українська").forEach { (lang, label) ->
                    FilterChip(
                        selected = language == lang,
                        onClick = {
                            preferences.putString("language", lang)
                            onLanguageChanged(lang)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
fun GpaSimulatorSection(snapshot: Snapshot?) {
    val summary = remember(snapshot) {
        if (snapshot == null) GradeSummary.of(emptyList()) else GpaCalculator.calculate(snapshot.records.values)
    }
    val currentGpa = if (summary.ectsAverage.isNaN()) (if (summary.average.isNaN()) 0.0 else summary.average) else summary.ectsAverage
    val currentEcts = if (summary.totalEcts > 0) summary.totalEcts else summary.numeric * 4.0
    var targetGpa by remember { mutableDoubleStateOf(GpaCalculator.SCHOLARSHIP_THRESHOLD) }
    var remainingEcts by remember { mutableDoubleStateOf(15.0) }
    val sim = GpaCalculator.simulate(currentGpa, currentEcts, targetGpa, remainingEcts)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Symulator GPA i stypendium", style = MaterialTheme.typography.titleLarge)
            Text(
                text = String.format(
                    Locale.forLanguageTag("pl-PL"),
                    "Obecna średnia ważona: %s (ECTS: %.1f)",
                    if (currentGpa > 0) String.format(Locale.forLanguageTag("pl-PL"), "%.2f", currentGpa) else "—",
                    currentEcts
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(GpaCalculator.scholarshipStatus( if (currentGpa > 0) currentGpa else Double.NaN ), style = MaterialTheme.typography.bodyMedium)
            Text(GpaCalculator.honorsStatus( if (currentGpa > 0) currentGpa else Double.NaN ), style = MaterialTheme.typography.bodyMedium)

            Text(
                String.format(Locale.forLanguageTag("pl-PL"), "Docelowa średnia: %.2f", targetGpa),
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = targetGpa.toFloat(),
                onValueChange = { targetGpa = it.toDouble() },
                valueRange = 3.0f..5.0f
            )
            Text(
                String.format(Locale.forLanguageTag("pl-PL"), "Pozostałe punkty ECTS: %.0f", remainingEcts),
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
    state: JSONObject,
    repository: SharedRepository,
    onReloadState: () -> Unit
) {
    var titleInput by remember { mutableStateOf("") }
    var courseInput by remember { mutableStateOf("") }
    var dueInput by remember { mutableStateOf(LocalDate.now(UsosClient.ZONE).plusDays(7).toString()) }
    var filter by remember { mutableStateOf("open") }
    val rawTasks = state.optJSONArray("tasks") ?: JSONArray()
    val today = LocalDate.now(UsosClient.ZONE)
    val sorted = TaskTools.sorted(rawTasks, "due").filter { TaskTools.matches(it, filter, "", today) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Zadania i terminy", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("active" to "Do zrobienia", "overdue" to "Po terminie", "done" to "Ukończone", "all" to "Wszystkie").forEach { (key, label) ->
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Nowe zadanie") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = courseInput,
                    onValueChange = { courseInput = it },
                    label = { Text("Przedmiot") },
                    modifier = Modifier.width(140.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dueInput,
                    onValueChange = { dueInput = it },
                    label = { Text("Termin (YYYY-MM-DD)") },
                    modifier = Modifier.width(150.dp),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (titleInput.isNotBlank()) {
                            repository.update { s ->
                                val arr = s.optJSONArray("tasks") ?: JSONArray()
                                arr.put(
                                    JSONObject()
                                        .put("title", titleInput.trim())
                                        .put("course", courseInput.trim())
                                        .put("due", dueInput.trim())
                                        .put("done", false)
                                        .put("createdAt", System.currentTimeMillis())
                                )
                                s.put("tasks", arr)
                            }
                            titleInput = ""
                            onReloadState()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text("Dodaj")
                }
            }
            if (sorted.isEmpty()) {
                Text("Brak zadań w wybranym widoku.", style = MaterialTheme.typography.bodyMedium)
            } else {
                sorted.forEach { task ->
                    val done = task.optBoolean("done")
                    val created = task.optLong("createdAt")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (if (done) "✓ " else "") + task.optString("title"),
                                style = MaterialTheme.typography.titleMedium
                            )
                            val sub = listOfNotNull(
                                task.optString("course").takeIf { it.isNotEmpty() },
                                task.optString("due").takeIf { it.isNotEmpty() }?.let { "Termin: $it" }
                            ).joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                Text(sub, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    repository.update { s ->
                                        val arr = s.optJSONArray("tasks") ?: JSONArray()
                                        for (i in 0 until arr.length()) {
                                            val item = arr.optJSONObject(i) ?: continue
                                            if (item.optLong("createdAt") == created && item.optString("title") == task.optString("title")) {
                                                item.put("done", !done)
                                                break
                                            }
                                        }
                                        s.put("tasks", arr)
                                    }
                                    onReloadState()
                                }
                            ) {
                                Text(if (done) "Cofnij" else "Gotowe")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceSection(
    snapshot: Snapshot?,
    preferences: KeyValueStore,
    strings: UiStrings = UiStrings.PL
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
                Text("Brak zajęć w planie do śledzenia obecności.", style = MaterialTheme.typography.bodyMedium)
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
                                        preferences.putString("attendance_data", next.toString())
                                        savedJson = next
                                    }
                                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                                OutlinedButton(
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                    onClick = {
                                        val next = JSONObject(savedJson.toString())
                                        next.getJSONObject(key).put("absences", count + 1)
                                        preferences.putString("attendance_data", next.toString())
                                        savedJson = next
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
    runAsync: ((() -> Unit) -> Unit)
) {
    val payments = snapshot?.payments
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Płatności i subkonta USOS", style = MaterialTheme.typography.titleLarge)
            if (payments == null) {
                Text("Brak danych o płatnościach. Zsynchronizuj konto.", style = MaterialTheme.typography.bodyMedium)
            } else {
                if (payments.statusNotice.isNotEmpty()) {
                    Text(payments.statusNotice, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                }
                payments.accounts.forEach { acc ->
                    Text(
                        "${acc.name}: ${acc.bankAccountNumber} (Saldo: ${acc.balance} ${acc.currency})",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                payments.installments.forEach { inst ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(inst.name, style = MaterialTheme.typography.titleMedium)
                            Text("Termin: ${inst.deadline.ifEmpty { "—" }} · ${inst.status}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("${inst.amount} ${inst.currency}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    val services = state.optJSONObject("studentServices")
    Card(
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
                Text("Legitymacje i dokumenty", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            runAsync {
                                try {
                                    StudentServiceLoader.refresh(repository, "cards")
                                    StudentServiceLoader.refresh(repository, "documents")
                                    onReloadState()
                                    onStatusMessage("Odświeżono legitymacje i dokumenty.")
                                } catch (e: Exception) {
                                    onStatusMessage(SharedRepository.message(e))
                                }
                            }
                        }
                    ) {
                        Text("Pobierz z USOS")
                    }
                }
            }
            MobileCardControls(state, repository, language, onReloadState, onStatusMessage, runAsync)
            val docsArr = services?.optJSONObject("documents")?.optJSONArray("data")
            if (docsArr != null && docsArr.length() > 0) {
                for (i in 0 until docsArr.length()) {
                    val doc = StudentServices.Document(docsArr.getJSONObject(i))
                    Text(
                        "${doc.name} (${doc.typeName}) · Status: ${doc.status}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (docsArr == null || docsArr.length() == 0) {
                Text("Brak pobranych dokumentów.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    val thesis = snapshot?.thesis
    if (thesis != null && thesis.hasThesis()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Praca dyplomowa", style = MaterialTheme.typography.titleLarge)
                Text(thesis.title, style = MaterialTheme.typography.titleMedium)
                Text("Status: ${thesis.status} · Promotor: ${thesis.supervisors}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Szybkie linki uczelniane (USOS / Platon)", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenUrl("https://usosweb.vistula.edu.pl/kontroler.php?_action=dla_stud/studia/oceny/index") }) { Text("USOSweb Oceny") }
                OutlinedButton(onClick = { onOpenUrl("https://usosweb.vistula.edu.pl/kontroler.php?_action=home/plan") }) { Text("USOSweb Plan") }
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
                            StudentServiceLoader.refresh(repository, "cards")
                            onReloadState()
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
    if (!pl.vistula.checker.core.AccountRole.lecturer(state)) return
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
