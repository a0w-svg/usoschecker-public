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
internal fun PlacementBlock(env: ExtrasEnv) {
    val repository = env.repository
    val language = env.language
    val tr = env.tr
    val services = env.services
    val runAsync = env.runAsync
    val onReloadState = env.onReloadState
    SectionHead(
        title = tr("Testy poziomujące i obiegówki", "Placement tests & clearance slips", "Тести рівня та обхідні листи"),
        onClick = {
            runAsync {
                StudentServiceLoader.refresh(repository, "placementAndSlips")
                onReloadState()
            }
        },
        modifier = Modifier
    )
    val plcEntry = services?.optJSONObject("placementAndSlips")
    ServiceStatusBanner(plcEntry, language)
    val plcData = plcEntry?.optJSONObject("data")
    val tests = plcData?.optJSONArray("placementTests") ?: JSONArray()
    val slips = plcData?.optJSONArray("slipsToAnswer") ?: JSONArray()
    if (tests.length() == 0 && slips.length() == 0) {
        QuietLine(tr("Brak testów poziomujących lub pytań obiegówek.", "No placement tests or clearance slip questions.", "Немає тестів рівня або питань обхідних листів."))
    } else {
        Tray {
            var anyRow = false
            for (i in 0 until tests.length()) {
                if (anyRow) TrayDivider()
                anyRow = true
                val t = tests.getJSONObject(i)
                ItemRow(
                    title = StudentServices.localized(t.opt("name"), language).ifEmpty { t.optString("id") },
                    sub = listOf(
                        t.optString("score").takeIf { it.isNotEmpty() }?.let { "${tr("Wynik", "Score", "Результат")}: $it/${t.optString("max_score")}" }.orEmpty(),
                        t.optString("level")
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                )
            }
            for (i in 0 until slips.length()) {
                if (anyRow) TrayDivider()
                anyRow = true
                val s = slips.getJSONObject(i)
                ItemRow(
                    title = s.optString("question").ifEmpty { s.optString("id") },
                    sub = "Slip #${s.optString("slip_id")}"
                )
            }
        }
    }
}
