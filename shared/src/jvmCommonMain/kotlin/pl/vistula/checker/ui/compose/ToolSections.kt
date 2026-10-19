package pl.vistula.checker.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
    preferences: KeyValueStore
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Kontrola nieobecności (limit: ${AttendanceTracker.LIMIT})", style = MaterialTheme.typography.titleLarge)
            val keys = savedJson.keys().asSequence().toList().sorted()
            if (keys.isEmpty()) {
                Text("Brak zajęć w planie do śledzenia obecności.", style = MaterialTheme.typography.bodyMedium)
            } else {
                keys.forEach { key ->
                    val entry = savedJson.optJSONObject(key) ?: return@forEach
                    val count = entry.optInt("absences", 0)
                    val limit = entry.optInt("limit", AttendanceTracker.LIMIT)
                    val name = entry.optString("name", key)
                    val classType = entry.optString("classType")
                    val term = entry.optString("term")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            val meta = listOfNotNull(
                                classType.takeIf { it.isNotEmpty() },
                                term.takeIf { it.isNotEmpty() }
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "$count / $limit",
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    AttendanceTracker.exceeded(count) -> MaterialTheme.colorScheme.error
                                    AttendanceTracker.warning(count) -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                            OutlinedButton(
                                onClick = {
                                    val next = JSONObject(savedJson.toString())
                                    next.getJSONObject(key).put("absences", (count - 1).coerceAtLeast(0))
                                    preferences.putString("attendance_data", next.toString())
                                    savedJson = next
                                }
                            ) { Text("−") }
                            OutlinedButton(
                                onClick = {
                                    val next = JSONObject(savedJson.toString())
                                    next.getJSONObject(key).put("absences", count + 1)
                                    preferences.putString("attendance_data", next.toString())
                                    savedJson = next
                                }
                            ) { Text("+") }
                        }
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
            val cardsArr = services?.optJSONObject("cards")?.optJSONArray("data")
            if (cardsArr != null && cardsArr.length() > 0) {
                for (i in 0 until cardsArr.length()) {
                    val card = StudentServices.Card(cardsArr.getJSONObject(i))
                    Text(
                        "Legitymacja #${card.id} (${card.studentNumber}) · ważna do: ${card.expires.ifEmpty { "—" }}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
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
            if ((cardsArr == null || cardsArr.length() == 0) && (docsArr == null || docsArr.length() == 0)) {
                Text("Brak pobranych legitymacji lub dokumentów.", style = MaterialTheme.typography.bodyMedium)
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

