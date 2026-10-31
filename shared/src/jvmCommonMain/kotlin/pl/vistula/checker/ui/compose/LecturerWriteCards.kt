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
import pl.vistula.checker.core.PointsInput
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.SyncProgress

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExperimentalAttendanceWriteCard(
    state: JSONObject,
    repository: SharedRepository,
    preferences: KeyValueStore?,
    language: String,
    initialListId: String,
    onAuthoritativeAttendanceUpdated: (JSONObject) -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    if (!AccountRole.lecturer(state)) return
    val tr = Translator(language)
    val enabled = preferences == null || LecturerWriteActions.isEnabled(preferences)
    val acknowledged = preferences == null || LecturerWriteActions.isAcknowledged(preferences)
    if (!enabled || !acknowledged) return

    var writeAction by remember { mutableStateOf("entry") } // entry | listMode
    var listId by remember(initialListId) { mutableStateOf(initialListId) }
    var studentId by remember { mutableStateOf("") }
    var attendanceMode by remember { mutableStateOf("intramural") }
    var listMode by remember { mutableStateOf("intramural") }
    var comment by remember { mutableStateOf("") }
    var confirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("Aktualizacja obecności w USOS", "Update USOS attendance", "Оновлення присутності в USOS"),
                    style = MaterialTheme.typography.titleMedium
                )
                ExperimentalBadge(language)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = writeAction == "entry", onClick = { writeAction = "entry" }, label = { Text(tr("Obecność studenta", "Student attendance", "Присутність студента")) })
                FilterChip(selected = writeAction == "listMode", onClick = { writeAction = "listMode" }, label = { Text(tr("Tryb listy", "List mode", "Режим списку")) })
            }
            OutlinedTextField(
                value = listId,
                onValueChange = { listId = it.trim() },
                label = { Text(tr("ID listy obecności (list_id)", "Attendance list ID (list_id)", "ID списку присутності (list_id)")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (writeAction == "entry") {
                OutlinedTextField(
                    value = studentId,
                    onValueChange = { studentId = it.trim() },
                    label = { Text(tr("ID studenta (student_id)", "Student ID (student_id)", "ID студента (student_id)")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LecturerWriteActions.ATTENDANCE_MODES.forEach { m ->
                        FilterChip(selected = attendanceMode == m, onClick = { attendanceMode = m }, label = { Text(m) })
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(tr("Komentarz (do 200 zn.)", "Comment (max 200 chars)", "Коментар (до 200 симв.)")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LecturerWriteActions.LIST_MODES.forEach { m ->
                        FilterChip(selected = listMode == m, onClick = { listMode = m }, label = { Text(m) })
                    }
                }
            }
            Button(
                enabled = listId.isNotBlank() && (writeAction == "listMode" || studentId.isNotBlank()),
                onClick = { confirmDialog = true }
            ) {
                Text(tr("Zapisz w USOS (z potwierdzeniem)", "Save to USOS (with confirmation)", "Зберегти в USOS (із підтвердженням)"))
            }
        }
    }

    if (confirmDialog) {
        AlertDialog(
            onDismissRequest = { confirmDialog = false },
            title = { Text(tr("Potwierdź zapis obecności", "Confirm attendance write", "Підтвердьте запис присутності")) },
            text = {
                Text(
                    if (writeAction == "entry") "list_id: $listId\nstudent_id: $studentId\nmode: $attendanceMode"
                    else "list_id: $listId\nmode: $listMode"
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmDialog = false
                    runAsync {
                        try {
                            val creds = repository.read()
                            check(SharedRepository.sameAccount(state, creds))
                            val client = UsosClient(creds)
                            val checkSameAccount = { check(SharedRepository.sameAccount(creds, repository.read())) }
                            val updated = if (writeAction == "entry") {
                                LecturerWriteActions.updateAttendance(
                                    client = client,
                                    state = creds,
                                    listId = listId,
                                    studentId = studentId,
                                    attendanceMode = attendanceMode,
                                    comment = comment,
                                    preferences = preferences,
                                    checkAccount = checkSameAccount
                                )
                            } else {
                                LecturerWriteActions.changeAttendanceListMode(
                                    client = client,
                                    state = creds,
                                    listId = listId,
                                    mode = listMode,
                                    preferences = preferences,
                                    checkAccount = checkSameAccount
                                )
                            }
                            onAuthoritativeAttendanceUpdated(updated)
                            onStatusMessage(tr("Zapisano i zweryfikowano listę obecności z USOS.", "Saved and verified attendance list from USOS.", "Збережено та перевірено список присутності з USOS."))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExperimentalLecturerWriteCard(
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
    val tr = Translator(language)
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
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = pointsInput.isNotEmpty() && PointsInput.parse(pointsInput) == null,
                            supportingText = {
                                if (pointsInput.isNotEmpty() && PointsInput.parse(pointsInput) == null) {
                                    Text(tr("Podaj liczbę, np. 4,5", "Enter a number, e.g. 4.5", "Введіть число, напр. 4,5"))
                                }
                            }
                        )
                    } else {
                        OutlinedTextField(
                            value = gradeOrderInput,
                            onValueChange = { gradeOrderInput = it.trim() },
                            label = { Text(tr("Kolejność wartości oceny (order)", "Grade value order (order)", "Порядковий номер оцінки (order)")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = gradeOrderInput.isNotEmpty() && gradeOrderInput.toIntOrNull() == null,
                            supportingText = {
                                if (gradeOrderInput.isNotEmpty() && gradeOrderInput.toIntOrNull() == null) {
                                    Text(tr("Podaj liczbę całkowitą", "Enter a whole number", "Введіть ціле число"))
                                }
                            }
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
                        label = { Text(tr("Znacznik last_changed (opcjonalnie)", "last_changed token (optional)", "Позначка last_changed (необов’язково)")) },
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
                                label = { Text(tr("Min. punktów (≤ 0)", "Min points (≤ 0)", "Мін. балів (≤ 0)")) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = pointsMaxInput,
                                onValueChange = { pointsMaxInput = it.trim() },
                                label = { Text(tr("Maks. punktów (> 0)", "Max points (> 0)", "Макс. балів (> 0)")) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = pointsPrecisionInput,
                                onValueChange = { pointsPrecisionInput = it.trim() },
                                label = { Text(tr("Dokładność (0–3)", "Precision (0–3)", "Точність (0–3)")) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            Button(
                enabled = nodeId.isNotBlank() && when (writeMode) {
                    "points" -> studentId.isNotBlank() && PointsInput.parse(pointsInput) != null
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
                                    points = checkNotNull(PointsInput.parse(pointsInput)),
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
