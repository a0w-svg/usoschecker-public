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
internal fun ThesesBlock(env: ExtrasEnv) {
    val state = env.state
    val repository = env.repository
    val language = env.language
    val tr = env.tr
    val services = env.services
    val runAsync = env.runAsync
    val onReloadState = env.onReloadState
    var selectedThesis by remember(state.optString("accountSession")) { mutableStateOf<Pair<String, JSONObject>?>(null) }
    SectionHead(
        title = tr("Prace dyplomowe", "Theses", "Дипломні роботи"),
        onClick = {
            runAsync {
                StudentServiceLoader.refresh(repository, "theses")
                onReloadState()
            }
        },
        modifier = Modifier
    )
    val thesisEntry = services?.optJSONObject("theses")
    ServiceStatusBanner(thesisEntry, language)
    val theses = thesisEntry?.optJSONObject("data")?.optJSONArray("theses") ?: JSONArray()
    if (theses.length() == 0) {
        QuietLine(tr("Brak zarejestrowanych prac dyplomowych.", "No registered theses found.", "Немає зареєстрованих дипломних робіт."))
    } else {
        Tray {
            for (i in 0 until theses.length()) {
                if (i > 0) TrayDivider()
                val th = theses.getJSONObject(i)
                val thId = th.optString("id")
                val title = StudentServices.localized(th.opt("title") ?: th.opt("titles"), language).ifEmpty { thId }
                val sub = listOf(th.optString("type"), th.optString("status"), tr("Szczegóły pracy", "Thesis details", "Деталі роботи")).filter { it.isNotBlank() }.joinToString(" · ")
                ItemRow(
                    title = title,
                    sub = sub,
                    onClick = if (thId.isNotEmpty()) ({
                        if (state.optString("token") == "demo") {
                            selectedThesis = title to DemoStudentServices.demoThesisDetail(thId)
                        } else runAsync {
                            val creds = repository.read()
                            check(SharedRepository.sameAccount(state, creds))
                            val detail = ToolServiceData.fetchThesisDetail(UsosClient(creds), thId)
                            check(SharedRepository.sameAccount(creds, repository.read()))
                            selectedThesis = title to detail
                        }
                    }) else null
                )
            }
        }
    }
    selectedThesis?.let { (fallback, detail) ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(StudentServices.localized(detail.opt("title") ?: detail.opt("titles"), language).ifEmpty { fallback }, style = MaterialTheme.typography.titleMedium)
                val sup = detail.optJSONArray("supervisors") ?: JSONArray()
                if (sup.length() > 0) {
                    val names = (0 until sup.length()).map { sup.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
                    Text(tr("Promotorzy: ", "Supervisors: ", "Керівники: ") + names)
                }
                val aut = detail.optJSONArray("authors") ?: JSONArray()
                if (aut.length() > 0) {
                    val names = (0 until aut.length()).map { aut.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
                    Text(tr("Autorzy: ", "Authors: ", "Автори: ") + names)
                }
                StudentServices.localized(detail.opt("faculty_name"), language).takeIf { it.isNotBlank() }?.let { Text(tr("Wydział: ", "Faculty: ", "Факультет: ") + it) }
                OutlinedButton(onClick = { selectedThesis = null }) { Text(tr("Zamknij", "Close", "Закрити")) }
            }
        }
    }
}
