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
internal fun CommunicationsBlock(env: ExtrasEnv) {
    val state = env.state
    val repository = env.repository
    val language = env.language
    val tr = env.tr
    val services = env.services
    val runAsync = env.runAsync
    val onReloadState = env.onReloadState
    var surveyDetail by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    var mailDetail by remember(state.optString("accountSession")) { mutableStateOf<JSONObject?>(null) }
    fun loadSurvey(id: String) {
        if (state.optString("token") == "demo") surveyDetail = DemoStudentServices.demoSurveyDetail(id)
        else runAsync {
            val creds = repository.read()
            check(SharedRepository.sameAccount(state, creds))
            val detail = ToolServiceData.fetchSurveyDetail(UsosClient(creds), id)
            check(SharedRepository.sameAccount(creds, repository.read()))
            surveyDetail = detail
        }
    }
    fun loadMail(id: String) {
        if (state.optString("token") == "demo") mailDetail = DemoStudentServices.demoMailMessage(id)
        else runAsync {
            val creds = repository.read()
            check(SharedRepository.sameAccount(state, creds))
            val detail = ToolServiceData.fetchMailMessage(UsosClient(creds), id)
            check(SharedRepository.sameAccount(creds, repository.read()))
            mailDetail = detail
        }
    }
    SectionHead(
        title = tr("Oświadczenia, powiadomienia i USOSmail", "Statements, notifications & USOSmail", "Заяви, сповіщення та USOSmail"),
        onClick = {
            runAsync {
                StudentServiceLoader.refresh(repository, "communications")
                onReloadState()
            }
        },
        modifier = Modifier
    )
    val commEntry = services?.optJSONObject("communications")
    ServiceStatusBanner(commEntry, language)
    surveyDetail?.let { SurveyDetailCard(it, language) { surveyDetail = null } }
    mailDetail?.let { MailDetailCard(it, language) { mailDetail = null } }
    val commData = commEntry?.optJSONObject("data")
    val statements = commData?.optJSONArray("statements") ?: JSONArray()
    val notifications = commData?.optJSONArray("notifications") ?: JSONArray()
    val messages = commData?.optJSONArray("messages") ?: JSONArray()
    val surveys = commData?.optJSONArray("surveys") ?: JSONArray()
    if (statements.length() == 0 && notifications.length() == 0 && messages.length() == 0 && surveys.length() == 0) {
        QuietLine(tr("Brak pobranych oświadczeń, powiadomień (events) lub wiadomości (mailclient).", "No statements, notifications (events), or messages (mailclient) loaded.", "Немає завантажених заяв, сповіщень (events) або повідомлень (mailclient)."))
    } else {
        Tray {
            var anyRow = false
            for (i in 0 until statements.length()) {
                if (anyRow) TrayDivider()
                anyRow = true
                val s = statements.getJSONObject(i)
                ItemRow(
                    title = s.optString("subject").ifEmpty { s.optString("id") },
                    sub = listOf(s.optString("content"), s.optString("expiration_date")).filter { it.isNotBlank() }.joinToString(" · ")
                )
            }
            for (i in 0 until notifications.length()) {
                if (anyRow) TrayDivider()
                anyRow = true
                val n = notifications.getJSONObject(i)
                ItemRow(
                    title = StudentServices.localized(n.opt("summary"), language).ifEmpty { n.optString("type") },
                    sub = listOf(n.optString("type"), n.optString("created")).filter { it.isNotBlank() }.joinToString(" · ")
                )
            }
            for (i in 0 until surveys.length()) {
                if (anyRow) TrayDivider()
                anyRow = true
                val s = surveys.getJSONObject(i)
                val id = s.optString("id")
                val status = when {
                    s.optBoolean("did_i_fill_out") -> tr("wypełniona", "completed", "заповнено")
                    s.has("can_i_fill_out") && !s.optBoolean("can_i_fill_out") -> tr("niedostępna", "unavailable", "недоступно")
                    s.has("can_i_fill_out") -> tr("do wypełnienia", "to fill out", "заповнити")
                    else -> ""
                }
                ItemRow(
                    title = "${tr("Ankieta", "Survey", "Анкета")}: " + StudentServices.localized(s.opt("name"), language).ifEmpty { id },
                    sub = listOf(status, s.optString("end_date")).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = if (id.isNotEmpty()) ({ loadSurvey(id) }) else null
                )
            }
            for (i in 0 until messages.length()) {
                if (anyRow) TrayDivider()
                anyRow = true
                val m = messages.getJSONObject(i)
                ItemRow(
                    title = m.optString("subject").ifEmpty { m.optString("id") },
                    sub = listOf(m.optString("status"), m.optString("date")).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = if (m.optString("id").isNotEmpty()) ({ loadMail(m.optString("id")) }) else null
                )
            }
        }
    }
}
