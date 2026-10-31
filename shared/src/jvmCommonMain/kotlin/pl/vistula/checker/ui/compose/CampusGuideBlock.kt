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
internal fun CampusGuideBlock(env: ExtrasEnv) {
    val state = env.state
    val repository = env.repository
    val language = env.language
    val tr = env.tr
    val services = env.services
    val live = env.live
    val runAsync = env.runAsync
    val onReloadState = env.onReloadState
    var courseQuery by remember { mutableStateOf("") }
    var courseSearchResults by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    var selectedCourse by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var roomIdQuery by remember { mutableStateOf("") }
    var roomScheduleResult by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    var buildingRooms by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONArray>?>(null) }
    var selectedDormitory by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var selectedAllocationPeriod by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var selectedGuidePage by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
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
