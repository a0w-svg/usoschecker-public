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
internal fun MobilityBlock(env: ExtrasEnv) {
    val state = env.state
    val repository = env.repository
    val language = env.language
    val tr = env.tr
    val services = env.services
    val runAsync = env.runAsync
    val onReloadState = env.onReloadState
    SectionHead(
        title = tr("Mobilność studencka i wymiany (Erasmus+)", "Academic mobility & exchanges (Erasmus+)", "Академічна мобільність та обміни (Erasmus+)"),
        onClick = {
            runAsync {
                StudentServiceLoader.refresh(repository, "mobility")
                onReloadState()
            }
        },
        modifier = Modifier
    )
    val mobEntry = services?.optJSONObject("mobility")
    ServiceStatusBanner(mobEntry, language)
    val mobData = mobEntry?.optJSONObject("data")
    val studentMobs = mobData?.optJSONArray("studentMobilities") ?: JSONArray()
    val coordMobs = mobData?.optJSONArray("coordinatedMobilities") ?: JSONArray()
    if (studentMobs.length() == 0 && coordMobs.length() == 0) {
        QuietLine(tr("Brak zarejestrowanych wyjazdów lub wymian w USOS.", "No academic mobility entries found in USOS.", "У USOS немає зареєстрованих обмінів."))
    } else {
        Tray {
            var anyRow = false
            for (i in 0 until studentMobs.length()) {
                if (anyRow) TrayDivider()
                anyRow = true
                val m = studentMobs.getJSONObject(i)
                ItemRow(
                    title = StudentServices.localized(m.opt("institution"), language).ifEmpty { StudentServices.localized(m.opt("programme"), language) },
                    sub = listOf(
                        StudentServices.localized(m.opt("programme"), language),
                        "${m.optString("start_date")} – ${m.optString("end_date")}",
                        StudentServices.localized(m.opt("state"), language)
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                )
            }
            for (i in 0 until coordMobs.length()) {
                if (anyRow) TrayDivider()
                anyRow = true
                val m = coordMobs.getJSONObject(i)
                ItemRow(
                    title = StudentServices.localized(m.opt("institution"), language).ifEmpty { m.optString("id") },
                    sub = listOf(
                        tr("Koordynator", "Coordinator", "Координатор"),
                        StudentServices.localized(m.opt("programme"), language),
                        StudentServices.localized(m.opt("state"), language)
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                )
            }
        }
    }
}
