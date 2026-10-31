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
internal fun RegistrationsBlock(env: ExtrasEnv) {
    val state = env.state
    val repository = env.repository
    val language = env.language
    val tr = env.tr
    val services = env.services
    val live = env.live
    val runAsync = env.runAsync
    val onReloadState = env.onReloadState
    var registrationRounds by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONArray>?>(null) }
    var tokenDetails by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    SectionHead(
        title = tr("Rejestracje i koszyk zapisów (tylko odczyt)", "Course registrations & cart (read-only)", "Реєстрації та кошик записів (лише читання)"),
        onClick = {
            runAsync {
                StudentServiceLoader.refresh(repository, "registrations")
                StudentServiceLoader.refresh(repository, "tokenRegistrations")
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
                val id = r.optString("id")
                val title = StudentServices.localized(r.opt("description"), language).ifEmpty { id }
                ItemRow(
                    title = title,
                    sub = listOf(r.optString("status"), StudentServices.localized(r.opt("message"), language)).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = if (live && id.isNotEmpty()) ({
                        runAsync {
                            val creds = repository.read()
                            check(SharedRepository.sameAccount(state, creds))
                            val rounds = ToolServiceData.fetchRegistrationRounds(UsosClient(creds), id)
                            check(SharedRepository.sameAccount(creds, repository.read()))
                            registrationRounds = title to rounds
                        }
                    }) else null
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
    registrationRounds?.let { (title, rounds) ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (rounds.length() == 0) Text(tr("Brak tur dla tej rejestracji.", "No rounds for this registration.", "Для цієї реєстрації немає турів."))
                for (i in 0 until rounds.length()) {
                    val round = rounds.optJSONObject(i) ?: continue
                    Text(listOf(StudentServices.localized(round.opt("name"), language), round.optString("status"), round.optString("start_date"), round.optString("end_date")).filter { it.isNotBlank() }.joinToString(" · "))
                }
                OutlinedButton(onClick = { registrationRounds = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
            }
        }
    }
    Text(tr("Rejestracje żetonowe", "Token registrations", "Реєстрації за токенами"), style = MaterialTheme.typography.titleMedium)
    val tokenEntry = services?.optJSONObject("tokenRegistrations")
    ServiceStatusBanner(tokenEntry, language)
    val tokenRows = tokenEntry?.optJSONObject("data")?.optJSONArray("registrations") ?: JSONArray()
    if (tokenRows.length() == 0 && (tokenEntry?.optLong("fetchedAt") ?: 0L) > 0L) {
        QuietLine(tr("Brak rejestracji żetonowych.", "No token registrations.", "Немає реєстрацій за токенами."))
    } else if (tokenRows.length() > 0) {
        Tray {
            for (i in 0 until tokenRows.length()) {
                if (i > 0) TrayDivider()
                val row = tokenRows.getJSONObject(i)
                val id = row.optString("id")
                val title = StudentServices.localized(row.opt("description"), language).ifEmpty { id }
                ItemRow(title = title,
                    sub = listOf(row.optString("status"), StudentServices.localized(row.opt("message"), language), tr("Zobacz tury", "View rounds", "Переглянути тури")).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = if (id.isNotBlank() && (live || state.optString("token") == "demo")) ({
                        if (state.optString("token") == "demo") tokenDetails = title to DemoStudentServices.demoTokenRegistrationDetails(id)
                        else runAsync {
                            val creds = repository.read()
                            check(SharedRepository.sameAccount(state, creds))
                            val detail = ToolServiceData.fetchTokenRegistrationDetails(UsosClient(creds), id)
                            check(SharedRepository.sameAccount(creds, repository.read()))
                            tokenDetails = title to detail
                        }
                    }) else null)
            }
        }
    }
    tokenDetails?.let { (fallback, detail) ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(StudentServices.localized(detail.opt("description"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                StudentServices.localized(detail.opt("message"), language).takeIf { it.isNotBlank() }?.let { Text(it) }
                val rounds = detail.optJSONArray("rounds") ?: JSONArray()
                if (!detail.optBoolean("rounds_available")) Text(tr("Informacje o turach są niedostępne.", "Round details are unavailable.", "Інформація про тури недоступна."))
                else if (rounds.length() == 0) Text(tr("Brak tur dla tej rejestracji.", "No rounds for this registration.", "Для цієї реєстрації немає турів."))
                for (i in 0 until rounds.length()) {
                    val round = rounds.optJSONObject(i) ?: continue
                    val start = round.optString("start_date")
                    val end = round.optString("end_date")
                    val items = listOf(StudentServices.localized(round.opt("name"), language).ifEmpty { round.optString("id") },
                        if (start.isNotBlank() || end.isNotBlank()) "${start.ifBlank { "—" }} – ${end.ifBlank { "—" }}" else "",
                        if (round.has("selection_limit")) tr("Limit wyborów: ", "Choice limit: ", "Ліміт виборів: ") + round.optInt("selection_limit") else "",
                        if (round.optBoolean("is_prioritized")) tr("Z priorytetami", "Prioritized", "З пріоритетами") else "",
                        if (round.optBoolean("is_only_entitled")) tr("Tylko uprawnieni", "Eligible students only", "Лише для уповноважених") else "")
                    Text(items.filter { it.isNotBlank() }.joinToString(" · "))
                }
                if (detail.optBoolean("rounds_truncated")) Text(tr("Pokazano pierwsze 100 tur.", "Showing the first 100 rounds.", "Показано перші 100 турів."))
                OutlinedButton(onClick = { tokenDetails = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
            }
        }
    }
}
