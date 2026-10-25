package pl.vistula.checker.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.StudentServiceLoader
import pl.vistula.checker.core.CampusAffiliation
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.SyncProgress

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampusSection(
    state: JSONObject, repository: SharedRepository, language: String, onOpenUrl: (String) -> Unit,
    onReloadState: () -> Unit, onStatusMessage: (String) -> Unit, runAsync: (() -> Unit) -> Unit
) {
    fun t(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    val selected = CampusAffiliation.selected(state)
    val live = state.optString("token").isNotEmpty() && state.optString("token") != "demo"
    Text(t("Kampus i kontakty", "Campus and contacts", "Кампус і контакти"), style = MaterialTheme.typography.titleLarge)
    Text(CampusAffiliation.campuses[selected] ?: t("Wybierz kampus", "Choose a campus", "Виберіть кампус"), style = MaterialTheme.typography.titleMedium)
    Text(t("Wybór automatyczny korzysta z danych konta USOS. Możesz też wybrać filię ręcznie.",
        "Automatic selection uses your USOS affiliation. You can also choose a branch manually.",
        "Автоматичний вибір використовує дані USOS. Ви також можете вибрати філію вручну."))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val choices = linkedMapOf("" to t("Automatycznie", "Automatic", "Автоматично")) + CampusAffiliation.campuses
        for ((id, name) in choices) FilterChip(selected = state.optString("campusOverride") == id, onClick = {
            val session = repository.editSession(state)
            runAsync {
                repository.update(session) {
                    if (id.isEmpty()) it.remove("campusOverride") else it.put("campusOverride", id)
                    it.optJSONObject("studentServices")?.remove("news")
                }
                if (live) StudentServiceLoader.refresh(repository, "affiliation")
                onReloadState()
            }
        }, label = { Text(name) })
    }
    OutlinedButton(enabled = live, onClick = {
        runAsync { StudentServiceLoader.refresh(repository, "affiliation"); onReloadState() }
    }) { Text(t("Odśwież kontakty", "Refresh contacts", "Оновити контакти")) }
    val entry = state.optJSONObject("studentServices")?.optJSONObject("affiliation")
    if (!entry?.optString("error").isNullOrEmpty()) Text(SyncProgress.cachedError(entry, language), color = MaterialTheme.colorScheme.error)
    val units = entry?.optJSONObject("data")?.optJSONArray("units")
    var count = 0
    if (units != null) for (i in 0 until units.length()) {
        val unit = units.optJSONObject(i) ?: continue
        if (selected.isEmpty() || unit.optString("campus") != selected) continue
        count++
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            SelectionContainer {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(StudentServices.localized(unit.opt("name"), language), style = MaterialTheme.typography.titleMedium)
                    for (key in listOf("address", "email")) StudentServices.text(unit, key).takeIf { it.isNotBlank() }?.let { Text(it) }
                    val phones = unit.optJSONArray("phones")
                    if (phones != null) for (n in 0 until phones.length()) Text(phones.optString(n))
                }
            }
        }
    }
    if (count == 0) Text(t("Brak pobranych kontaktów dla tej filii. Skorzystaj z oficjalnej strony lub odśwież dane konta.",
        "No contacts loaded for this branch. Visit the official website or refresh your account data.",
        "Немає завантажених контактів цієї філії. Перейдіть на офіційний сайт або оновіть дані облікового запису."))
    CampusAffiliation.website(selected)?.let { website ->
        Button(onClick = { onOpenUrl(website) }) { Text(t("Oficjalna strona filii", "Official branch website", "Офіційний сайт філії")) }
    }
}
