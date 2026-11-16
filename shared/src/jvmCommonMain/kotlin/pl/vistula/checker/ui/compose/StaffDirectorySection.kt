package pl.vistula.checker.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.DemoStudentServices
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.StudentServiceLoader
import pl.vistula.checker.ToolServiceData
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.LecturerServices
import pl.vistula.checker.core.StudentServices

@Composable
internal fun StaffDirectorySection(
    state: JSONObject,
    repository: SharedRepository,
    language: String,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    val tr = Translator(language)
    val sessionKey = state.optString("accountSession")
    var query by remember(sessionKey) { mutableStateOf("") }
    var validationError by remember(sessionKey) { mutableStateOf(false) }
    var searchResults by remember(sessionKey) { mutableStateOf<JSONObject?>(null) }
    var staffSchedule by remember(sessionKey) { mutableStateOf<Pair<String, JSONArray>?>(null) }
    var staffProfile by remember(sessionKey) { mutableStateOf<JSONObject?>(null) }
    val entry = state.optJSONObject("studentServices")?.optJSONObject("staffDirectory")
    val activeData = searchResults ?: entry?.optJSONObject("data")
    val users = activeData?.optJSONArray("users") ?: JSONArray()
    val live = state.optString("token").isNotEmpty() && state.optString("token") != "demo" && !state.optBoolean("authorizationError")
    LaunchedEffect(sessionKey, live) {
        if (live && (entry?.optLong("attemptAt") ?: 0L) == 0L) {
            runAsync {
                StudentServiceLoader.refresh(repository, "staffDirectory")
                onReloadState()
            }
        }
    }

    val performSearch: () -> Unit = {
        val q = query.trim()
        if (q.isEmpty()) {
            validationError = false
            searchResults = null
        } else if (q.length !in 2..80) {
            validationError = true
        } else if (state.optString("token").isNotEmpty()) {
            validationError = false
            runAsync {
                if (state.optString("token") == "demo") {
                    searchResults = DemoStudentServices.demoStaffDirectory(q)
                } else {
                    val creds = repository.read()
                    check(SharedRepository.sameAccount(state, creds))
                    searchResults = ToolServiceData.searchTeachers(UsosClient(creds), q)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHead(
            title = tr("Katalog dydaktyków i plan pracowników", "Staff directory & schedule", "Каталог викладачів і розклад"),
            onClick = if (state.optString("token") == "demo") null else ({
                query = ""
                validationError = false
                searchResults = null
                runAsync {
                    StudentServiceLoader.refresh(repository, "staffDirectory")
                    onReloadState()
                }
            }),
            modifier = Modifier
        )
        ServiceStatusBanner(entry, language)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    if (it.length <= 80) {
                        query = it
                        val trimmed = it.trim()
                        if (validationError && (trimmed.isEmpty() || trimmed.length >= 2)) {
                            validationError = false
                        }
                        if (trimmed.isEmpty() && searchResults != null) {
                            searchResults = null
                        }
                    }
                },
                label = { Text(tr("Szukaj prowadzącego (min. 2 znaki)", "Search teacher (min. 2 chars)", "Пошук викладача (мін. 2 символи)")) },
                isError = validationError,
                supportingText = if (validationError) {
                    { Text(tr("Wpisz od 2 do 80 znaków.", "Enter 2 to 80 characters.", "Введіть від 2 до 80 символів.")) }
                } else null,
                trailingIcon = if (query.isNotEmpty() || searchResults != null) {
                    {
                        TextButton(onClick = {
                            query = ""
                            validationError = false
                            searchResults = null
                        }) {
                            Text(tr("Wyczyść", "Clear", "Очистити"))
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                enabled = state.optString("token").isNotEmpty(),
                onClick = performSearch
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
                        u.optString("room").takeIf { it.isNotEmpty() }?.let { tr("Pokój: ", "Office: ", "Кабінет: ") + it } ?: "",
                        StudentServices.localized(u.opt("office_hours"), language)
                    ).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "ID: $uid" }
                    ItemRow(
                        title = fullName.ifEmpty { "ID: $uid" },
                        sub = sub,
                        onClick = if (uid.isNotEmpty() && state.optString("token").isNotEmpty()) {
                            {
                                runAsync {
                                    if (state.optString("token") == "demo") {
                                        staffProfile = DemoStudentServices.demoStaffProfile(uid)
                                        staffSchedule = (fullName.ifEmpty { uid }) to DemoStudentServices.demoStaffSchedule(uid)
                                    } else {
                                        val creds = repository.read()
                                        check(SharedRepository.sameAccount(state, creds))
                                        val today = LocalDate.now(UsosClient.ZONE).toString()
                                        val client = UsosClient(creds)
                                        val profile = ToolServiceData.fetchStaffProfile(client, uid)
                                        val lessons = ToolServiceData.fetchStaffSchedule(client, uid, today)
                                        check(SharedRepository.sameAccount(creds, repository.read()))
                                        staffProfile = profile
                                        staffSchedule = fullName.ifEmpty { uid } to lessons
                                    }
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
                        OutlinedButton(onClick = { staffSchedule = null; staffProfile = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
                    }
                    val office = StudentServices.localized(staffProfile?.opt("office_hours"), language)
                    val room = staffProfile?.optString("room").orEmpty()
                    if (room.isNotBlank()) Text(tr("Pokój: ", "Office: ", "Кабінет: ") + room)
                    if (office.isNotBlank()) Text(office)
                    if (lessons.length() == 0) {
                        Text(tr("Brak zaplanowanych zajęć w najbliższych 7 dniach.", "No classes scheduled in the next 7 days.", "Немає занять у найближчі 7 днів."))
                    } else {
                        for (i in 0 until lessons.length()) {
                            val l = lessons.getJSONObject(i)
                            val title = StudentServices.localized(l.opt("name"), language)
                            val roomNum = l.optString("room_number")
                            val classType = StudentServices.localized(l.opt("classtype_name"), language)
                            val head = listOf(title, classType.takeIf { it.isNotBlank() && !title.contains(it, ignoreCase = true) }.orEmpty())
                                .filter { it.isNotEmpty() }.joinToString(" · ")
                            Text("$head · ${LecturerServices.lessonSpan(l.optString("start_time"), l.optString("end_time"), language)}" + (if (roomNum.isNotEmpty()) " ($roomNum)" else ""))
                        }
                    }
                }
            }
        }
    }
}
