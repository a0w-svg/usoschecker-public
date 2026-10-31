package pl.vistula.checker.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.vistula.checker.KeyValueStore
import pl.vistula.checker.OperationGate
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.SyncProgress

/**
 * Hosts one tools section (for example `"staffDirectory"`) on its own, for screens that are not Compose,
 * such as the Android app. It owns the reload and background-work plumbing that `VistulaApp` provides.
 *
 * Pass [fixedState] for demo mode: the section then always renders that state and never reads [repository].
 */
@Composable
fun SectionHost(
    section: String,
    repository: SharedRepository,
    preferences: KeyValueStore,
    language: String,
    themeMode: String,
    fixedState: JSONObject?,
    onOpenUrl: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val gate = remember { OperationGate() }
    var version by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(fixedState ?: JSONObject()) }
    var status by remember { mutableStateOf("") }
    val strings = remember(language) { UiStrings.forLanguage(language) }

    LaunchedEffect(version, language, fixedState) {
        if (fixedState != null) {
            state = fixedState
            return@LaunchedEffect
        }
        try {
            state = withContext(Dispatchers.IO) { SharedRepository.localizedView(repository.read(), language) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            status = SyncProgress.error(e, language)
        }
    }
    val setStatus: (String) -> Unit = { message -> scope.launch(Dispatchers.Main.immediate) { status = message } }
    val reload: () -> Unit = { scope.launch(Dispatchers.Main.immediate) { version++ } }
    val runAsync: (() -> Unit) -> Unit = { block ->
        if (gate.tryStart()) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { block() }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    setStatus(SyncProgress.error(e, language))
                } finally {
                    gate.finish()
                    version++
                }
            }
        }
    }
    val snapshot = remember(state, language) {
        runCatching { state.optJSONObject("snapshot")?.let { Snapshot.from(it).forLanguage(language) } }.getOrNull()
    }

    VistulaTheme(themeMode = themeMode) {
        Column(Modifier.fillMaxWidth()) {
            if (status.isNotEmpty()) Text(status, fontSize = 12.sp, color = VistulaDesign.tokens.muted)
            SectionContent(
                section, state, snapshot, strings, repository, preferences, themeMode, language,
                {}, {}, {}, onOpenUrl, reload, setStatus, runAsync
            )
        }
    }
}
