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
    val env = ExtrasEnv(state, repository, language, onReloadState, onStatusMessage, runAsync)
    val services = env.services
    val live = env.live
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
            RegistrationsBlock(env)
        }

        if (section == "academicCalendar" || section == "exams" || section == "services") {
            ExamsAndCalendarBlock(env)
        }

        if (section == "theses" || section == "services") {
            ThesesBlock(env)
        }

        if (section == "mobility" || section == "academicCalendar" || section == "services") {
            MobilityBlock(env)
        }

        if (section == "placementAndSlips" || section == "services") {
            PlacementBlock(env)
        }

        if (section == "campusGuide" || section == "academicCalendar" || section == "services") {
            CampusGuideBlock(env)
        }

        if (section == "communications" || section == "services") {
            CommunicationsBlock(env)
        }
    }
}

@Composable
internal fun MailDetailCard(message: JSONObject, language: String, onClose: () -> Unit) {
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
internal fun SurveyDetailCard(survey: JSONObject, language: String, onClose: () -> Unit) {
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
