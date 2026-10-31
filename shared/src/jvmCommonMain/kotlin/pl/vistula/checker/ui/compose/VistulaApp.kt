package pl.vistula.checker.ui.compose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.ChangeNotifier
import pl.vistula.checker.KeyValueStore
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.SyncScheduler
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.CourseTestRecord
import pl.vistula.checker.core.DaySummary
import pl.vistula.checker.core.GradeBrowser
import pl.vistula.checker.core.GradeSummary
import pl.vistula.checker.core.IcsExporter
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.NewsArticle
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.ScheduleBreaks
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.TaskTools

enum class VistulaTab(val id: String) {
    HOME("home"),
    SCHEDULE("schedule"),
    GRADES("grades"),
    HISTORY("history"),
    MORE("more");

    val icon: ImageVector
        get() = when (this) {
            HOME -> VistulaIcons.Home
            SCHEDULE -> VistulaIcons.Schedule
            GRADES -> VistulaIcons.Grades
            HISTORY -> VistulaIcons.History
            MORE -> VistulaIcons.Settings
        }

    fun label(strings: UiStrings): String = when (this) {
        HOME -> strings.tabHome
        SCHEDULE -> strings.tabSchedule
        GRADES -> strings.tabGrades
        HISTORY -> strings.tabHistory
        MORE -> strings.tabMore
    }
}

private fun parseSnapshot(state: JSONObject, language: String): Snapshot? {
    val raw = state.optJSONObject("snapshot") ?: return null
    return try {
        Snapshot.from(raw).forLanguage(language)
    } catch (_: Exception) {
        null
    }
}

@Composable
fun VistulaApp(
    repository: SharedRepository,
    preferences: KeyValueStore,
    notifier: ChangeNotifier? = null,
    syncScheduler: SyncScheduler? = null,
    onOpenUrl: (String) -> Unit = {},
    onExportText: (filename: String, mimeType: String, content: String) -> Boolean = { _, _, _ -> false },
    externalTabTrigger: VistulaTab? = null,
    externalSyncTrigger: Int = 0,
    externalDemoTrigger: Int = 0,
    externalNavigationTrigger: Int = 0
) {
    val scope = rememberCoroutineScope()
    var stateVersion by remember { mutableIntStateOf(0) }
    val demoWorkspace = remember { pl.vistula.checker.DemoWorkspace() }
    var demoMode by remember { mutableStateOf(false) }
    var legacyDemo by remember { mutableStateOf(false) }
    val activeRepository = if (demoMode) demoWorkspace.repository else repository
    val activePreferences = if (demoMode) demoWorkspace.preferences else preferences
    val gate = remember { pl.vistula.checker.OperationGate() }
    var state by remember { mutableStateOf(JSONObject()) }
    var currentTab by remember { mutableStateOf(VistulaTab.HOME) }
    var tabSelection by remember { mutableIntStateOf(0) }
    var moreSection by remember { mutableStateOf("") }
    var homeScheduleDate by remember { mutableStateOf<LocalDate?>(null) }
    var themeMode by remember {
        mutableStateOf(
            preferences.getString("ui_theme_mode", preferences.getString("theme", "dark"))
        )
    }
    var language by remember {
        mutableStateOf(
            preferences.getString("ui_language", preferences.getString("language", "pl"))
        )
    }
    val strings = remember(language) { UiStrings.forLanguage(language) }
    var statusMessage by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(externalTabTrigger, externalNavigationTrigger) {
        if (externalTabTrigger != null) {
            homeScheduleDate = null
            moreSection = ""
            currentTab = externalTabTrigger
            tabSelection++
        }
    }

    val setStatus: (String) -> Unit = { message -> scope.launch(Dispatchers.Main.immediate) { statusMessage = message } }
    val reloadState: () -> Unit = { scope.launch(Dispatchers.Main.immediate) { stateVersion++ } }
    LaunchedEffect(activeRepository, stateVersion, language) {
        try {
            val loaded = withContext(Dispatchers.IO) { SharedRepository.localizedView(activeRepository.read(), language) }
            if (activeRepository === repository && loaded.optString("token") == "demo") {
                legacyDemo = true
                demoMode = true
                state = demoWorkspace.repository.read()
            } else state = loaded
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            statusMessage = pl.vistula.checker.core.SyncProgress.error(e, language)
        }
    }
    LaunchedEffect(activeRepository) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            if (!gate.isRunning()) stateVersion++
        }
    }
    val runAsync: (() -> Unit) -> Unit = { block ->
        if (gate.tryStart()) {
            isRefreshing = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { block() }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    setStatus(pl.vistula.checker.core.SyncProgress.error(e, language))
                } finally {
                    gate.finish()
                    stateVersion++
                    isRefreshing = false
                }
            }
        }
    }
    val toggleDemo: () -> Unit = {
        if (legacyDemo && !gate.isRunning()) {
            runAsync {
                repository.discardLegacyDemo()
                scope.launch(Dispatchers.Main.immediate) {
                    legacyDemo = false; demoMode = false; state = JSONObject(); stateVersion++
                }
            }
        } else if (!gate.isRunning()) {
            demoMode = !demoMode
            state = JSONObject()
            statusMessage = if (demoMode) strings.statusDemoMode else ""
            stateVersion++
        }
    }
    val openUrl: (String) -> Unit = { address ->
        scope.launch(Dispatchers.Main.immediate) {
            try {
                require(pl.vistula.checker.core.WebLinks.isWebUrl(address))
                onOpenUrl(address)
            } catch (e: Exception) { statusMessage = pl.vistula.checker.core.SyncProgress.error(e, language) }
        }
    }

    val refreshAll: () -> Unit = {
        runAsync {
            try {
                if ("demo" == state.optString("token")) {
                    demoWorkspace.reset(true)
                    setStatus("OK (${strings.statusDemoMode})")
                } else if (state.optString("token").isEmpty()) {
                    setStatus(strings.onboardingPointOauth)
                } else {
                    val changes = activeRepository.sync(
                        true,
                        { s -> UsosClient(s, java.util.function.Consumer { msg -> setStatus(pl.vistula.checker.core.SyncProgress.localized(msg, language)) }).snapshot() },
                        { diff ->
                            if (diff.isNotEmpty() && state.optBoolean("notifications", true)) {
                                notifier?.notifyChanges(diff)
                            }
                        },
                        true
                    )
                    setStatus(if (changes.isNotEmpty()) "Δ +${changes.size}" else "OK")
                }
            } catch (e: Exception) {
                setStatus(pl.vistula.checker.core.SyncProgress.error(e, language))
            }
        }
    }

    LaunchedEffect(externalSyncTrigger) {
        if (externalSyncTrigger > 0) {
            refreshAll()
        }
    }

    LaunchedEffect(externalDemoTrigger) {
        if (externalDemoTrigger > 0) {
            toggleDemo()
        }
    }

    VistulaTheme(themeMode = themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Prototype breakpoints: the menu moves to the top and the Today stage opens up at 1100dp.
            val isWide = maxWidth >= 1100.dp * maxOf(1f, androidx.compose.ui.platform.LocalDensity.current.fontScale)
            val lecturerOnly = pl.vistula.checker.core.AccountRole.lecturerOnly(state)
            val tabs = VistulaTab.entries.map { it to if (it == VistulaTab.GRADES && lecturerOnly) toolText(language, "Dydaktyka", "Teaching", "Викладання") else it.label(strings) }
            val selectTab: (VistulaTab) -> Unit = { homeScheduleDate = null; moreSection = ""; currentTab = it; tabSelection++ }
            val statusBar: @Composable (@Composable (() -> Unit)?) -> Unit = { nav ->
                TopStatusBar(
                    state = state,
                    strings = strings,
                    statusMessage = statusMessage,
                    isRefreshing = isRefreshing,
                    onRefresh = refreshAll,
                    onEnableDemo = toggleDemo,
                    nav = nav
                )
            }
            val body: @Composable () -> Unit = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val maxContent = if (isWide && currentTab == VistulaTab.HOME) 1180.dp else if (isWide) 640.dp else 560.dp
                    Box(modifier = Modifier.widthIn(max = maxContent).fillMaxSize()) {
                        Crossfade(targetState = currentTab, label = "tabCrossfade") { tab ->
                            androidx.compose.runtime.key(activeRepository, state.optString("accountSession"), tabSelection) {
                                TabContent(
                                    currentTab = tab,
                                    moreSection = moreSection,
                                    homeScheduleDate = homeScheduleDate,
                                    onOpenScheduleDate = { homeScheduleDate = it; currentTab = VistulaTab.SCHEDULE },
                                    onNavigateTool = { moreSection = it; currentTab = VistulaTab.MORE },
                                    isWide = isWide,
                                    state = state,
                                    strings = strings,
                                    repository = activeRepository,
                                    preferences = activePreferences,
                                    syncScheduler = syncScheduler,
                                    themeMode = themeMode,
                                    language = language,
                                    onThemeChanged = {
                                        try { preferences.putString("ui_theme_mode", it); themeMode = it }
                                        catch (e: Exception) { setStatus(pl.vistula.checker.core.SyncProgress.error(e, language)) }
                                    },
                                    onLanguageChanged = {
                                        try { preferences.putString("ui_language", it); language = it; statusMessage = "" }
                                        catch (e: Exception) { setStatus(pl.vistula.checker.core.SyncProgress.error(e, language)) }
                                    },
                                    onNavigateTab = selectTab,
                                    onEnableDemo = toggleDemo,
                                    onOpenUrl = openUrl,
                                    onExportText = onExportText,
                                    onReloadState = reloadState,
                                    onStatusMessage = setStatus,
                                    runAsync = runAsync
                                )
                            }
                        }
                    }
                }
            }
            if (isWide) {
                Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    statusBar { PillNav(tabs, currentTab, selectTab) }
                    if (isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) { body() }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        statusBar(null)
                        if (isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { body() }
                    PillNav(
                        tabs, currentTab, selectTab,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 12.dp, vertical = 14.dp)
                            .widthIn(max = 560.dp).fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Quiet freshness line with the two actions; on wide screens it also hosts the centred pill menu. */
@Composable
private fun TopStatusBar(
    state: JSONObject,
    strings: UiStrings,
    statusMessage: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onEnableDemo: () -> Unit,
    nav: (@Composable () -> Unit)? = null
) {
    val t = VistulaDesign.tokens
    val token = state.optString("token")
    val isConnected = token.isNotEmpty()
    val isDemo = token == "demo"
    val sub = statusMessage.ifEmpty {
        val error = state.optString("error")
        if (error.isNotEmpty()) pl.vistula.checker.core.SyncProgress.cachedError(state, strings.languageCode) else strings.statusReady
    }
    val who = if (isDemo) "${strings.statusDemoMode} · ${state.optString("user", "Student")}"
    else if (isConnected) state.optString("user", strings.statusConnected)
    else strings.statusDisconnected
    val actions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isConnected || isDemo) {
                Text(
                    if (isDemo) toolText(strings.languageCode, "Zakończ demo", "Exit demo", "Вийти з демо") else strings.btnDemo,
                    fontSize = 12.sp, color = if (isRefreshing) t.quiet else t.muted,
                    modifier = Modifier.clip(RoundedCornerShape(percent = 50)).clickable(enabled = !isRefreshing, role = Role.Button, onClick = onEnableDemo)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Text(
                if (isRefreshing) strings.btnSyncing else strings.btnSync,
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isRefreshing) t.quiet else t.ink,
                modifier = Modifier.clip(RoundedCornerShape(percent = 50)).background(if (isRefreshing) t.fill else t.paper)
                    .clickable(enabled = !isRefreshing, role = Role.Button, onClick = onRefresh)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
    val info: @Composable (Alignment.Horizontal) -> Unit = { align ->
        Column(horizontalAlignment = align) {
            Text(who, fontSize = 13.sp, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 12.sp, color = t.quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (nav != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { info(Alignment.Start) }
            Box(Modifier.width(500.dp)) { nav() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { actions() }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) { info(Alignment.Start) }
            actions()
        }
    }
}


@Composable
private fun TabContent(
    currentTab: VistulaTab,
    moreSection: String,
    homeScheduleDate: LocalDate?,
    onOpenScheduleDate: (LocalDate) -> Unit,
    onNavigateTool: (String) -> Unit,
    isWide: Boolean,
    state: JSONObject,
    strings: UiStrings,
    repository: SharedRepository,
    preferences: KeyValueStore,
    syncScheduler: SyncScheduler?,
    themeMode: String,
    language: String,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onNavigateTab: (VistulaTab) -> Unit,
    onEnableDemo: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onExportText: (String, String, String) -> Boolean,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    val snapshot = remember(state, language) { parseSnapshot(state, language) }
    when (currentTab) {
        VistulaTab.HOME -> HomeScreen(
            onOpenScheduleDate = onOpenScheduleDate,
            onNavigateTool = onNavigateTool,
            state = state,
            snapshot = snapshot,
            strings = strings,
            isWide = isWide,
            onNavigateTab = onNavigateTab,
            onEnableDemo = onEnableDemo,
            onOpenUrl = onOpenUrl
        )
        VistulaTab.SCHEDULE -> ScheduleScreen(
            initialDate = homeScheduleDate,
            snapshot = snapshot,
            strings = strings,
            isWide = isWide,
            onExportText = onExportText,
            onStatusMessage = onStatusMessage
        )
        VistulaTab.GRADES -> if (pl.vistula.checker.core.AccountRole.lecturerOnly(state)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LecturerWorkspaceSection(state, repository, preferences, language, "teaching", onOpenUrl, onReloadState, onStatusMessage, runAsync)
            }
        } else GradesScreen(
            snapshot = snapshot,
            strings = strings,
            isWide = isWide
        )
        VistulaTab.HISTORY -> HistoryScreen(
            state = state,
            strings = strings
        )
        VistulaTab.MORE -> MoreScreen(
            initialSection = moreSection,
            onNavigateTab = onNavigateTab,
            state = state,
            snapshot = snapshot,
            strings = strings,
            repository = repository,
            preferences = preferences,
            syncScheduler = syncScheduler,
            themeMode = themeMode,
            language = language,
            onThemeChanged = onThemeChanged,
            onLanguageChanged = onLanguageChanged,
            onEnableDemo = onEnableDemo,
            onOpenUrl = onOpenUrl,
            onReloadState = onReloadState,
            onStatusMessage = onStatusMessage,
            runAsync = runAsync
        )
    }
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(
    state: JSONObject, snapshot: Snapshot?, strings: UiStrings, isWide: Boolean,
    onNavigateTab: (VistulaTab) -> Unit, onEnableDemo: () -> Unit,
    onOpenUrl: (String) -> Unit, onNavigateTool: (String) -> Unit, onOpenScheduleDate: (LocalDate) -> Unit
) = StudentDashboard(state, snapshot, strings, isWide, onNavigateTab, onNavigateTool, onOpenScheduleDate, onEnableDemo, onOpenUrl)

@Composable
internal fun OnboardingWelcomeCard(
    strings: UiStrings,
    onEnableDemo: () -> Unit,
    onConnectAccount: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = strings.onboardingTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
            Text(strings.onboardingPointPrivacy, style = MaterialTheme.typography.bodyMedium)
            Text(strings.onboardingPointDemo, style = MaterialTheme.typography.bodyMedium)
            Text(strings.onboardingPointOauth, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onEnableDemo) {
                    Text(strings.onboardingTryDemo)
                }
                OutlinedButton(onClick = onConnectAccount) {
                    Text(strings.onboardingConnectAccount)
                }
            }
        }
    }
}

@Composable
private fun KpiStatCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun RecentGradesCard(
    grades: List<Record>,
    snapshot: Snapshot?,
    strings: UiStrings,
    onSeeAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(strings.recentGradesTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                OutlinedButton(onClick = onSeeAll) { Text(strings.btnSeeAll) }
            }
            if (grades.isEmpty()) {
                Text(strings.noGradesSaved, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                grades.forEach { grade ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(grade.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                text = listOfNotNull(
                                    GradeBrowser.classType(grade, snapshot).takeIf { it.isNotEmpty() },
                                    grade.term.takeIf { it.isNotEmpty() }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = GradeBrowser.symbol(grade).ifEmpty { "—" },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NewsListCard(news: List<NewsArticle>, strings: UiStrings, onOpenUrl: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.newsTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (news.isEmpty()) {
                Text(strings.noNewsCached, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                news.forEach { article ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = article.url.isNotEmpty()) { onOpenUrl(article.url) }
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(article.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        val meta = listOfNotNull(
                            article.author.takeIf { it.isNotEmpty() },
                            article.publicationDate.takeIf { it.isNotEmpty() }
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) {
                            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        val body = article.headline.ifEmpty { article.content }
                        if (body.isNotEmpty()) {
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    snapshot: Snapshot?,
    strings: UiStrings,
    isWide: Boolean,
    onExportText: (filename: String, mimeType: String, content: String) -> Boolean,
    onStatusMessage: (String) -> Unit,
    initialDate: LocalDate? = null
) {
    val allRecords = snapshot?.records?.values ?: emptyList()
    val allLessons = remember(snapshot) { LessonTime.sorted(allRecords) }
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate ?: LocalDate.now(UsosClient.ZONE)) }
    var viewMode by remember { mutableStateOf("day") } // day | week | agenda
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("") }
    var selectedLesson by remember { mutableStateOf<LessonTime?>(null) }
    var showOptions by remember { mutableStateOf(false) }

    val classTypes = remember(allLessons) {
        allLessons.mapNotNull { it.record.optMeta("classType").takeIf { t -> t.isNotBlank() } }.distinct().sorted()
    }
    val filteredLessons = remember(allLessons, searchQuery, selectedType) {
        allLessons.filter { lt ->
            val r = lt.record
            val matchesQuery = searchQuery.isBlank() ||
                r.title.contains(searchQuery, ignoreCase = true) ||
                r.value.contains(searchQuery, ignoreCase = true) ||
                r.optMeta("room").contains(searchQuery, ignoreCase = true) ||
                r.optMeta("building").contains(searchQuery, ignoreCase = true) ||
                r.optMeta("lecturers").contains(searchQuery, ignoreCase = true)
            val matchesType = selectedType.isEmpty() || r.optMeta("classType").equals(selectedType, ignoreCase = true)
            matchesQuery && matchesType
        }
    }

    val weekMonday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dayLessons = remember(filteredLessons, selectedDate) {
        filteredLessons.filter { it.intersects(selectedDate) }
    }
    val daySummary = remember(dayLessons, selectedDate) {
        DaySummary.of(dayLessons, selectedDate)
    }
    val breaks = remember(dayLessons, selectedDate) {
        ScheduleBreaks.inDay(dayLessons, selectedDate)
    }

    val language = strings.languageCode
    val locale = Locale.forLanguageTag(language)
    val t = VistulaDesign.tokens
    val monthFmt = DateTimeFormatter.ofPattern("MMM", locale)
    val longDay = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
    val weekEnd = weekMonday.plusDays(6)
    val period = if (weekMonday.month == weekEnd.month) "${weekMonday.dayOfMonth}–${weekEnd.dayOfMonth} ${weekEnd.format(monthFmt)}"
    else "${weekMonday.dayOfMonth} ${weekMonday.format(monthFmt)} – ${weekEnd.dayOfMonth} ${weekEnd.format(monthFmt)}"
    val todayDate = LocalDate.now(UsosClient.ZONE)
    val thisWeek = weekMonday == todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundButton(onClick = { selectedDate = selectedDate.minusWeeks(1) }, label = strings.btnPrevWeek) { Text("‹", fontSize = 24.sp, color = t.muted) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(period, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = t.text,
                    modifier = Modifier.semantics { heading() })
                if (thisWeek) Text(toolText(language, "Ten tydzień", "This week", "Цей тиждень"), fontSize = 12.sp, color = t.muted)
                else Text(toolText(language, "Wróć do tego tygodnia", "Back to this week", "Назад до цього тижня"), fontSize = 12.sp, color = t.muted,
                    modifier = Modifier.clickable { selectedDate = todayDate }.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            RoundButton(onClick = { selectedDate = selectedDate.plusWeeks(1) }, label = strings.btnNextWeek) { Text("›", fontSize = 24.sp, color = t.muted) }
            RoundButton(onClick = { showOptions = !showOptions }, label = strings.searchScheduleHint) { Text("⋯", fontSize = 22.sp, color = t.muted) }
        }

        Row(Modifier.fillMaxWidth()) {
            for (i in 0..6) {
                val day = weekMonday.plusDays(i.toLong())
                DayCell(
                    dow = day.format(DateTimeFormatter.ofPattern("EEE", locale)).trimEnd('.'), date = day.dayOfMonth,
                    count = filteredLessons.count { it.intersects(day) && !it.tentative() },
                    today = day == todayDate, selected = day == selectedDate, onClick = { selectedDate = day },
                    modifier = Modifier.weight(1f), large = true
                )
            }
        }

        if (showOptions) {
        PillSegment(
            listOf("day" to strings.viewDayTimeline, "week" to strings.viewFullWeek, "agenda" to strings.viewAgenda),
            viewMode, { viewMode = it }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(strings.searchScheduleHint) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(percent = 50),
            singleLine = true
        )

        if (classTypes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedType.isEmpty(),
                    onClick = { selectedType = "" },
                    label = { Text(strings.filterAllTypes) }
                )
                classTypes.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type) }
                    )
                }
            }
        }

        }
        when (viewMode) {
            "day" -> Column {
                SectionHead(selectedDate.format(longDay))
                if (dayLessons.isEmpty()) QuietLine(strings.noClassesOnDay)
                else Tray {
                    dayLessons.forEachIndexed { i, lt ->
                        if (i > 0) TrayDivider()
                        ScheduleLessonCard(
                            lesson = lt,
                            highlighted = selectedLesson == lt,
                            onClick = { selectedLesson = if (selectedLesson == lt) null else lt },
                            strings = strings
                        )
                    }
                }
                if (breaks.isNotEmpty()) {
                    QuietLine(
                        breaks.joinToString(", ") { "${it.start.format(TIME_FMT)}–${it.end.format(TIME_FMT)} (${it.minutes} min)" },
                        Modifier.padding(top = 12.dp)
                    )
                }
            }
            "week" -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                for (i in 0..6) {
                    val day = weekMonday.plusDays(i.toLong())
                    val events = filteredLessons.filter { it.intersects(day) }
                    if (events.isNotEmpty() || day.dayOfWeek.value <= DayOfWeek.FRIDAY.value) {
                        Column {
                            SectionHead(day.format(longDay))
                            if (events.isEmpty()) QuietLine(strings.noClassesOnDay)
                            else Tray {
                                events.forEachIndexed { index, lt ->
                                    if (index > 0) TrayDivider()
                                    ScheduleLessonCard(lt, selectedLesson == lt, { selectedLesson = if (selectedLesson == lt) null else lt }, strings = strings)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (filteredLessons.isEmpty()) QuietLine(strings.noClassesOnDay)
                else Tray {
                    filteredLessons.forEachIndexed { index, lt ->
                        if (index > 0) TrayDivider()
                        ScheduleLessonCard(lt, selectedLesson == lt, { selectedLesson = if (selectedLesson == lt) null else lt }, showDate = true, strings = strings)
                    }
                }
            }
        }

        Text(
            "${strings.btnExportIcs} (${filteredLessons.size})", fontSize = 12.sp, color = t.muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(percent = 50)).clickable {
                val ics = IcsExporter.export(filteredLessons.map { it.record })
                try {
                    if (onExportText("vistula-schedule.ics", "text/calendar", ics)) {
                        onStatusMessage("${strings.btnExportIcs} (${filteredLessons.size})")
                    }
                } catch (e: Exception) {
                    onStatusMessage(pl.vistula.checker.core.SyncProgress.error(e, strings.languageCode))
                }
            }.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun DayTimelineCompose(
    day: LocalDate,
    lessons: List<LessonTime>,
    breaks: List<ScheduleBreaks.Break>,
    strings: UiStrings = UiStrings.PL,
    onSelectLesson: (LessonTime) -> Unit = {}
) {
    val bounds = remember(lessons, day) { TimelineLanePlanner.hourBounds(lessons, day) }
    val startHour = bounds[0]
    val endHour = bounds[1]
    val placedLessons = remember(lessons, day) { TimelineLanePlanner.placeLessons(lessons, day) }
    val totalMinutes = ((endHour - startHour) * 60).coerceAtLeast(60)
    val hourHeightDp = 56.dp
    val totalHeightDp = hourHeightDp * (endHour - startHour)
    val gutterWidthDp = 48.dp

    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val breakColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    val nowColor = Color(0xFFC03131)
    val isToday = day == LocalDate.now(LessonTime.ZONE)
    val nowTime = LocalTime.now(LessonTime.ZONE)
    val nowMinutes = nowTime.hour * 60 + nowTime.minute

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${strings.visualTimelineTitle} (${String.format(Locale.ROOT, "%02d:00", startHour)} – ${String.format(Locale.ROOT, "%02d:00", endHour)})",
                style = MaterialTheme.typography.titleMedium
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(totalHeightDp + 16.dp)) {
                val laneAreaWidth = (maxWidth - gutterWidthDp).coerceAtLeast(120.dp)

                // Background grid + break intervals + live "Now" line
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val leftGutterPx = gutterWidthDp.toPx()
                    val contentWidthPx = (size.width - leftGutterPx).coerceAtLeast(60f)
                    val usableHeightPx = totalHeightDp.toPx()

                    for (h in startHour..endHour) {
                        val ratio = ((h - startHour) * 60f) / totalMinutes.toFloat()
                        val y = ratio * usableHeightPx + 8.dp.toPx()
                        drawLine(
                            color = outlineColor,
                            start = Offset(leftGutterPx, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    breaks.forEach { br ->
                        val brStartMins = br.start.hour * 60 + br.start.minute
                        val brEndMins = br.end.hour * 60 + br.end.minute
                        val topRatio = ((brStartMins - startHour * 60).toFloat() / totalMinutes).coerceIn(0f, 1f)
                        val bottomRatio = ((brEndMins - startHour * 60).toFloat() / totalMinutes).coerceIn(0f, 1f)
                        val y = topRatio * usableHeightPx + 8.dp.toPx()
                        val h = ((bottomRatio - topRatio) * usableHeightPx).coerceAtLeast(4f)
                        drawRoundRect(
                            color = breakColor,
                            topLeft = Offset(leftGutterPx + 4f, y),
                            size = Size(contentWidthPx - 8f, h),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }

                    if (isToday && nowMinutes in (startHour * 60)..(endHour * 60)) {
                        val nowRatio = (nowMinutes - startHour * 60).toFloat() / totalMinutes.toFloat()
                        val y = nowRatio * usableHeightPx + 8.dp.toPx()
                        drawCircle(
                            color = nowColor,
                            radius = 4.dp.toPx(),
                            center = Offset(leftGutterPx, y)
                        )
                        drawLine(
                            color = nowColor,
                            start = Offset(leftGutterPx, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Hour labels in left gutter
                for (h in startHour..endHour) {
                    val offsetY = hourHeightDp * (h - startHour)
                    Text(
                        text = String.format(Locale.ROOT, "%02d:00", h),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.offset(x = 0.dp, y = offsetY)
                    )
                }

                // Multi-column clickable lesson cards
                placedLessons.forEach { placed ->
                    val lt = placed.lesson
                    val r = lt.record
                    val colWidth = laneAreaWidth / placed.columnCount.coerceAtLeast(1)
                    val offsetX = gutterWidthDp + colWidth * placed.column + 2.dp
                    val startRatio = ((placed.startMinutes - startHour * 60).toFloat() / totalMinutes).coerceIn(0f, 1f)
                    val endRatio = ((placed.endMinutes - startHour * 60).toFloat() / totalMinutes).coerceIn(0f, 1f)
                    val offsetY = totalHeightDp * startRatio + 8.dp
                    val blockHeight = (totalHeightDp * (endRatio - startRatio)).coerceAtLeast(28.dp)
                    val room = r.optMeta("room")
                    val timeRange = "${lt.start.format(TIME_FMT)}–${lt.end.format(TIME_FMT)}"

                    Surface(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .width((colWidth - 4.dp).coerceAtLeast(36.dp))
                            .height(blockHeight)
                            .clickable { onSelectLesson(lt) },
                        color = if (lt.tentative()) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (lt.tentative()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = r.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (lt.tentative()) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = if (blockHeight >= 52.dp) 2 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (blockHeight >= 40.dp) {
                                Text(
                                    text = if (room.isNotEmpty()) "$timeRange · Sala $room" else timeRange,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (lt.tentative()) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleLessonCard(
    lesson: LessonTime,
    highlighted: Boolean = false,
    onClick: () -> Unit = {},
    showDate: Boolean = false,
    strings: UiStrings = UiStrings.PL
) {
    val t = VistulaDesign.tokens
    val r = lesson.record
    val classType = r.optMeta("classType")
    val room = r.optMeta("room")
    val building = r.optMeta("building")
    val lecturers = r.optMeta("lecturers")
    val group = r.optMeta("groupNumber")
    val time = "${lesson.start.format(TIME_FMT)}–${lesson.end.format(TIME_FMT)}"
    @Composable fun subject() {
        Text(r.title, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = t.text)
        if (room.isNotEmpty()) Text(toolText(strings.languageCode, "Sala $room", "Room $room", "Аудиторія $room"),
            fontSize = 14.sp, lineHeight = 21.sp, color = t.text, modifier = Modifier.padding(top = 6.dp))
        val description = listOfNotNull(classType.takeIf { it.isNotEmpty() },
            if (lesson.tentative()) toolText(strings.languageCode, "Termin orientacyjny", "Tentative time", "Орієнтовний час") else null).joinToString(" · ")
        if (description.isNotEmpty()) Text(description, fontSize = 13.sp, lineHeight = 20.sp, color = t.muted, modifier = Modifier.padding(top = 4.dp))
    }
    Column(Modifier.fillMaxWidth().background(if (highlighted) t.fillSoft else Color.Transparent)) {
        BoxWithConstraints(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp, 18.dp)) {
            val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            if (maxWidth < 520.dp * scale) Column {
                if (showDate) Text(lesson.start.toLocalDate().toString(), fontSize = 13.sp, color = t.muted)
                Text(time, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = t.text, modifier = Modifier.padding(bottom = 8.dp))
                subject()
            } else Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(112.dp * scale)) {
                    if (showDate) Text(lesson.start.toLocalDate().toString(), fontSize = 13.sp, color = t.muted)
                    Text(lesson.start.format(TIME_FMT), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = t.text)
                    Text(lesson.end.format(TIME_FMT), fontSize = 14.sp, color = t.muted, modifier = Modifier.padding(top = 4.dp))
                }
                Column(Modifier.weight(1f)) { subject() }
            }
        }
        if (highlighted) {
            val meta = listOfNotNull(
                building.takeIf { it.isNotEmpty() },
                lecturers.takeIf { it.isNotEmpty() },
                group.takeIf { it.isNotEmpty() }?.let { "Grupa $it" }
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, fontSize = 14.sp, lineHeight = 21.sp, color = t.muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradesScreen(
    snapshot: Snapshot?,
    strings: UiStrings,
    isWide: Boolean
) {
    val allRecords = snapshot?.records?.values ?: emptyList()
    val termOrder = remember(snapshot) {
        val terms = LinkedHashSet<String>()
        allRecords.forEach { if (it.kind == "ocena" && it.term.isNotEmpty()) terms.add(it.term) }
        snapshot?.courses?.forEach { if (it.term.isNotEmpty()) terms.add(it.term) }
        terms.toList().sortedDescending()
    }

    var selectedTerm by remember(termOrder) { mutableStateOf(snapshot?.let {
        pl.vistula.checker.core.GradeSemesters.current(it, LocalDate.now(UsosClient.ZONE))
    } ?: termOrder.firstOrNull().orEmpty()) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("all") } // all | numeric | other | retakes | labs | exercises
    var sortMode by remember { mutableStateOf("title") } // title | recent | value
    var selectedGroup by remember { mutableStateOf<List<Record>?>(null) }

    val filteredGrades = remember(snapshot, selectedTerm, searchQuery, filterMode, sortMode) {
        GradeBrowser.select(allRecords, selectedTerm, filterMode, sortMode, searchQuery, snapshot)
    }
    val ungradedCourses = remember(snapshot, selectedTerm, searchQuery, filterMode) {
        if (snapshot == null) emptyList()
        else if (selectedTerm.isNotEmpty()) GradeBrowser.ungraded(snapshot, selectedTerm, filterMode, searchQuery)
        else termOrder.flatMap { t -> GradeBrowser.ungraded(snapshot, t, filterMode, searchQuery) }
    }
    val summary = remember(filteredGrades) { GradeSummary.of(filteredGrades) }
    val groups = remember(filteredGrades) { GradeBrowser.groups(filteredGrades) }
    val crstests = snapshot?.tests ?: emptyList()

    val t = VistulaDesign.tokens
    var termMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    val columns = remember(filteredGrades, snapshot) { GradeTable.columns(filteredGrades, snapshot) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { termMenu = true }) {
                    Text((snapshot?.terms?.get(selectedTerm)?.name ?: selectedTerm).ifEmpty { strings.filterAllSemesters } + "  ⌄",
                        color = t.text, fontSize = 15.sp)
                }
                DropdownMenu(expanded = termMenu, onDismissRequest = { termMenu = false }) {
                    (listOf("") + termOrder).forEach { term ->
                        DropdownMenuItem(text = { Text(if (term.isEmpty()) strings.filterAllSemesters else snapshot?.terms?.get(term)?.name ?: term) },
                            onClick = { selectedTerm = term; selectedGroup = null; termMenu = false })
                    }
                }
            }
            RoundButton(onClick = { showSearch = !showSearch }, label = strings.searchGradesHint) {
                Icon(VistulaIcons.Search, null, Modifier.size(18.dp), tint = t.muted)
            }
            RoundButton(onClick = { showOptions = !showOptions }, label = toolText(strings.languageCode, "Opcje ocen i sprawdziany", "Grade options and tests", "Параметри оцінок і тести")) {
                Text("⋯", fontSize = 22.sp, color = t.muted)
            }
        }
        if (showSearch || searchQuery.isNotEmpty()) OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it }, label = { Text(strings.searchGradesHint) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(24.dp)
        )
        if (showOptions) Tray {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val avg = if (!summary.ectsAverage.isNaN()) summary.ectsAverage else summary.average
                Text(if (!avg.isNaN()) String.format(Locale.US, "GPA: %.2f", avg) else "GPA: —", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to strings.filterAllGrades, "numeric" to strings.filterNumeric,
                        "labs" to strings.filterLabs, "exercises" to strings.filterExercises, "retakes" to strings.filterRetakes
                    ).forEach { (id, label) -> FilterChip(selected = filterMode == id, onClick = { filterMode = id }, label = { Text(label) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("title" to strings.sortAZ, "recent" to strings.sortRecent, "value" to strings.sortHighest
                    ).forEach { (id, label) -> FilterChip(selected = sortMode == id, onClick = { sortMode = id }, label = { Text(label) }) }
                }
            }
        }
        if (groups.isEmpty() && ungradedCourses.isEmpty()) QuietLine(strings.noGradesSaved)
        if (groups.isNotEmpty()) {
            // Extra class types get their own columns; scroll the whole table so headings stay aligned.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                val stacked = !isWide || maxWidth < 640.dp * scale
                val tableWidth = if (stacked) maxWidth else maxOf(maxWidth, 240.dp + 112.dp * columns.size * scale)
                Column(Modifier.horizontalScroll(rememberScrollState()).width(tableWidth)) {
                    if (!stacked) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        columns.forEach { column ->
                            Text(GradeTable.fullLabel(column, strings.languageCode), Modifier.width(112.dp * scale),
                                color = t.muted, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                        }
                    }
                    Tray {
                        groups.forEachIndexed { index, group ->
                            if (index > 0) TrayDivider()
                            GradeGroupCard(group, snapshot, columns, strings, selectedTerm.isEmpty(), stacked) { selectedGroup = if (selectedGroup == group) null else group }
                            if (selectedGroup == group) GradeGroupDetailCard(group, snapshot, strings) { selectedGroup = null }
                        }
                    }
                }
            }
        }

        if (ungradedCourses.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${strings.ungradedCoursesTitle} (${ungradedCourses.size})", style = MaterialTheme.typography.titleMedium)
                    ungradedCourses.forEach { course ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(course.title, style = MaterialTheme.typography.bodyMedium)
                            Text(course.term, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (showOptions && snapshot?.serviceErrors?.containsKey("crstests") == true) {
            val missingScope = snapshot.serviceErrors["crstests"] == "crstests"
            Text(if (missingScope) toolText(strings.languageCode,
                "Aby pobrać sprawdziany, dodaj zgodę crstests i połącz konto ponownie.",
                "To load course tests, grant the crstests permission and reconnect your account.",
                "Щоб завантажити тести, надайте дозвіл crstests і повторно підключіть обліковий запис.")
            else toolText(strings.languageCode,
                "Nie udało się w całości pobrać sprawdzianów. Spróbuj ponownie później.",
                "Course tests could not be loaded completely. Try again later.",
                "Не вдалося повністю завантажити тести. Спробуйте пізніше."), color = MaterialTheme.colorScheme.error)
        }
        if (showOptions && crstests.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${strings.courseTestsTitle} (${crstests.size})", style = MaterialTheme.typography.titleMedium)
                    crstests.forEach { test: CourseTestRecord ->
                        val pct = test.percentage()
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = listOfNotNull(
                                        test.courseName.takeIf { it.isNotEmpty() },
                                        test.testName.takeIf { it.isNotEmpty() }
                                    ).joinToString(" — "),
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                val result = listOfNotNull(
                                    if (test.hasPoints) (if (test.maxPoints > 0) "${test.points} / ${test.maxPoints}" else "${test.points}") +
                                        " " + toolText(strings.languageCode, "pkt", "points", "балів") else null,
                                    test.grade.takeIf { it.isNotEmpty() }
                                ).joinToString(" · ")
                                Text(
                                    text = result,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (!pct.isNaN()) {
                                LinearProgressIndicator(
                                    progress = { (pct.toFloat() / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                            if (test.date.isNotEmpty()) {
                                Text(test.date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeGroupCard(group: List<Record>, snapshot: Snapshot?, columns: List<String>, strings: UiStrings, showTerm: Boolean, stacked: Boolean, onClick: () -> Unit) {
    val first = group.firstOrNull() ?: return
    val t = VistulaDesign.tokens
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    @Composable fun course() {
            Text(first.title, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = t.text)
            if (showTerm && first.term.isNotEmpty()) Text(first.term, fontSize = 12.sp, color = t.muted)
    }
    @Composable fun grade(column: String, modifier: Modifier) {
        Text(GradeTable.values(group, column, snapshot),
            modifier.semantics { contentDescription = GradeTable.fullLabel(column, strings.languageCode) + ": " + GradeTable.values(group, column, snapshot) },
            fontSize = if (stacked) 20.sp else 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, color = t.text, textAlign = TextAlign.Center)
    }
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp)) {
        if (stacked) {
            course()
            val perRow = if (scale > 1.3f) 2 else 3
            columns.chunked(perRow).forEach { chunk ->
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    chunk.forEach { column ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(GradeTable.fullLabel(column, strings.languageCode), fontSize = 13.sp, lineHeight = 18.sp, minLines = 2, color = t.muted, textAlign = TextAlign.Center)
                            grade(column, Modifier.fillMaxWidth())
                        }
                    }
                    repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) { course() }
            columns.forEach { column -> grade(column, Modifier.width(112.dp * scale)) }
        }
    }
}

@Composable
private fun GradeGroupDetailCard(
    group: List<Record>,
    snapshot: Snapshot?,
    strings: UiStrings,
    onClose: () -> Unit
) {
    val first = group.firstOrNull() ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(first.title, modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onClose) { Text(strings.btnClose) }
            }
            group.forEach { g ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(GradeBrowser.classType(g, snapshot), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(GradeBrowser.symbol(g), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(g.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val comment = g.optMeta("comment")
                    if (comment.isNotEmpty()) {
                        Text(comment, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(state: JSONObject, strings: UiStrings = UiStrings.PL) {
    val language = strings.languageCode
    val tr = Translator(language)
    val history = state.optJSONArray("history") ?: JSONArray()
    val all = remember(history.toString()) {
        (0 until history.length()).mapNotNull { runCatching { Change.from(history.getJSONObject(it)) }.getOrNull() }
            .sortedByDescending { it.effectiveTimestamp() }
    }
    var filter by remember { mutableStateOf("all") }
    val grades = all.count { it.kind == "ocena" }
    val picked = when (filter) {
        "grades" -> all.filter { it.kind == "ocena" }
        "timetable" -> all.filter { it.kind != "ocena" }
        else -> all
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        PillSegment(
            listOf(
                "all" to tr("Wszystkie", "All", "Усі"),
                "grades" to tr("Oceny", "Grades", "Оцінки") + " $grades",
                "timetable" to tr("Plan", "Timetable", "Розклад") + " ${all.size - grades}"
            ),
            filter, { filter = it }
        )
        if (picked.isEmpty()) {
            QuietLine(if (all.isEmpty()) strings.historyEmpty else tr("Brak zmian w tej kategorii", "Nothing in this category", "У цій категорії нічого немає"))
        } else {
            Column {
                SectionHead(tr("Najnowsze", "Latest", "Останні"))
                Tray {
                    picked.forEachIndexed { i, change ->
                        if (i > 0) TrayDivider()
                        ChangeRow(change, language)
                    }
                }
            }
        }
    }
}

@Composable
fun MoreScreen(
    state: JSONObject,
    snapshot: Snapshot?,
    strings: UiStrings,
    repository: SharedRepository,
    preferences: KeyValueStore,
    syncScheduler: SyncScheduler?,
    themeMode: String,
    language: String,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onEnableDemo: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit,
    initialSection: String = "",
    onNavigateTab: (VistulaTab) -> Unit = {}
) {
    val t = VistulaDesign.tokens
    val tr = Translator(language)
    var activeSection by remember(initialSection) { mutableStateOf(initialSection) }
    val mode = pl.vistula.checker.core.AccountRole.mode(state)
    val groups = buildList {
        add(tr("Aktualizacje", "Updates", "Оновлення") to listOf(
            Triple("history", tr("Historia zmian", "Change history", "Історія змін"), tr("Zmiany ocen i planu", "Grade and schedule changes", "Зміни оцінок і розкладу"))
        ))
        if (mode != pl.vistula.checker.core.AccountRole.Mode.LECTURER) {
            add(tr("Student", "Student", "Студент") to listOf(
                Triple("cards", tr("Legitymacja", "Student ID", "Студентський квиток"), tr("Status i ważność legitymacji", "Card status and validity", "Статус і термін дії квитка")),
                Triple("documents", tr("Dokumenty", "Documents", "Документи"), tr("Dokumenty i decyzje", "Documents and decisions", "Документи та рішення")),
                Triple("payments", tr("Płatności", "Payments", "Платежі"), tr("Opłaty i terminy", "Fees and due dates", "Платежі й терміни")),
                Triple("registrations", tr("Rejestracje", "Registrations", "Реєстрації"), tr("Tury zapisów i koszyki", "Course registration rounds", "Тури реєстрації на предмети")),
                Triple("exams", tr("Egzaminy i kalendarz", "Exams & calendar", "Іспити та календар"), tr("Terminy sesji i egzaminy", "Exam sessions and exam dates", "Терміни сесії та іспити")),
                Triple("theses", tr("Prace dyplomowe", "Theses", "Дипломні роботи"), tr("Status i promotorzy prac", "Thesis status and supervisors", "Статус та керівники робіт")),
                Triple("tasks", strings.sectionTasks, tr("Własne zadania i terminy", "Your own tasks and deadlines", "Власні завдання й терміни")),
                Triple("attendance", strings.sectionAttendance, tr("Nieobecności według przedmiotów", "Absences by course", "Пропуски за предметами")),
                Triple("services", strings.sectionServices, tr("Pozostałe usługi i linki uczelniane", "Other services and university links", "Інші послуги та посилання університету"))
            ))
        }
        if (mode != pl.vistula.checker.core.AccountRole.Mode.STUDENT) {
            add(tr("Wykładowca", "Lecturer", "Викладач") to buildList {
                add(Triple("teaching", tr("Panel wykładowcy", "Lecturer panel", "Панель викладача"), tr("Prowadzone przedmioty i dyżury", "Conducted courses and office hours", "Предмети викладання та консультації")))
                add(Triple("lecturerGroups", tr("Grupy i uczestnicy", "Class groups & rosters", "Групи та учасники"), tr("Prowadzone grupy i koordynowane przedmioty", "Taught groups and coordinated courses", "Групи викладання та координовані предмети")))
                add(Triple("lecturerTests", tr("Sprawdziany i oceny", "Course tests & grading", "Перевірки та оцінювання"), tr("Drzewo sprawdzianów i wpisywanie ocen (Eksperymentalne)", "Test trees and experimental grading", "Дерево перевірок і виставлення оцінок (Експериментально)")))
                add(Triple("lecturerMeetings", tr("Konsultacje i spotkania", "Consultations & meetings", "Консультації та зустрічі"), tr("Harmonogram dyżurów i spotkań", "Office hours and meetings", "Розклад консультацій і зустрічей")))
                add(Triple("lecturerAttendance", tr("Listy obecności USOS", "USOS attendance lists", "Списки присутності USOS"), tr("Listy obecności i wpisywanie obecności (Eksperymentalne)", "Attendance lists and experimental updates", "Списки присутності та експериментальний запис")))
                add(Triple("lecturerReports", tr("Ankiety i prace dyplomowe", "Surveys & thesis reports", "Анкети та дипломні роботи"), tr("Ankiety dydaktyczne i protokoły prac", "Course surveys and thesis reports", "Анкети викладання та протоколи робіт")))
                if (mode == pl.vistula.checker.core.AccountRole.Mode.LECTURER) {
                    add(Triple("tasks", strings.sectionTasks, tr("Własne zadania i terminy", "Your own tasks and deadlines", "Власні завдання й терміни")))
                }
            })
        }
    } + listOf(
        tr("Uczelnia", "University", "Університет") to listOf(
            Triple("news", tr("Aktualności", "News", "Новини"), tr("Ogłoszenia uczelni", "University announcements", "Оголошення університету")),
            Triple("campus", tr("Kampus i kontakty", "Campus & contacts", "Кампус і контакти"), tr("Adresy i dane kontaktowe", "Addresses and contact details", "Адреси та контакти")),
            Triple("academicCalendar", tr("Kalendarz akademicki", "Academic calendar", "Академічний календар"), tr("Wydarzenia i terminy roku akademickiego", "Academic year events and dates", "Події та дати навчального року")),
            Triple("staffDirectory", tr("Katalog pracowników", "Staff directory", "Каталог працівників"), tr("Wyszukiwarka prowadzących i konsultacji", "Search lecturers and office hours", "Пошук викладачів і консультацій")),
            Triple("campusGuide", tr("Informator, akademiki i sale", "Campus guide, dorms & rooms", "Довідник, гуртожитки та аудиторії"), tr("Przewodnik, akademiki, wyszukiwarka przedmiotów i sal", "Guide, dormitories, course catalog & room schedules", "Довідник, гуртожитки, пошук предметів та аудиторій")),
            Triple("mobility", tr("Wymiany i Erasmus+", "Mobility & Erasmus+", "Обміни та Erasmus+"), tr("Wyjazdy studenckie i koordynacja umów", "Student exchanges and mobility coordination", "Студентські обміни та координація угод")),
            Triple("placementAndSlips", tr("Testy poziomujące i obiegówki", "Placement tests & slips", "Тести рівня та обхідні листи"), tr("Wyniki testów językowych i pytania obiegówek", "Placement test results and clearance slips", "Результати мовних тестів та обхідні листи")),
            Triple("communications", tr("Oświadczenia i USOSmail", "Statements & USOSmail", "Заяви та USOSmail"), tr("Oświadczenia, powiadomienia i wiadomości USOSmail", "Statements, notifications & USOSmail messages", "Заяви, сповіщення та повідомлення USOSmail"))
        ),
        tr("Aplikacja i narzędzia", "App & tools", "Застосунок і інструменти") to buildList {
            add(Triple("account", strings.sectionAccount, tr("Połączenie, wygląd i synchronizacja", "Connection, appearance and sync", "Підключення, вигляд і синхронізація")))
            if (mode != pl.vistula.checker.core.AccountRole.Mode.LECTURER) add(Triple("gpa", strings.sectionGpa, tr("Oblicz docelową średnią", "Calculate a target average", "Обчисліть цільовий середній бал")))
            add(Triple("catalog", tr("Katalog API USOS", "USOS API catalog", "Каталог API USOS"), tr("Dokumentacja metod API", "Browse API documentation", "Документація методів API")))
        }
    )
    val title = groups.flatMap { it.second }.firstOrNull { it.first == activeSection }?.second ?: ""

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (activeSection.isEmpty()) {
            groups.forEachIndexed { index, (group, items) ->
                Column(Modifier.padding(top = if (index == 0) 0.dp else 16.dp)) {
                    SectionHead(group)
                    Tray {
                        items.forEachIndexed { i, (id, itemTitle, sub) ->
                            if (i > 0) TrayDivider()
                            ItemRow(itemTitle, sub, onClick = {
                                when (id) {
                                    "history" -> onNavigateTab(VistulaTab.HISTORY)
                                    "catalog" -> onOpenUrl(pl.vistula.checker.core.WebLinks.apiDocumentation(null))
                                    else -> activeSection = id
                                }
                            })
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(onClick = { activeSection = "" }, label = strings.tabMore) { Text("‹", fontSize = 24.sp, color = t.muted) }
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = t.text, modifier = Modifier.semantics { heading() })
            }
        }

        SectionContent(
            activeSection, state, snapshot, strings, repository, preferences, themeMode, language,
            onThemeChanged, onLanguageChanged, onEnableDemo, onOpenUrl, onReloadState, onStatusMessage, runAsync
        )
    }
}

/** Content of one tools section; shared by [MoreScreen] and the Android-hosted [SectionHost]. */
@Composable
internal fun SectionContent(
    activeSection: String,
    state: JSONObject,
    snapshot: Snapshot?,
    strings: UiStrings,
    repository: SharedRepository,
    preferences: KeyValueStore,
    themeMode: String,
    language: String,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onEnableDemo: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    when (activeSection) {
        "" -> Unit
        "campus" -> CampusSection(state, repository, language, onOpenUrl, onReloadState, onStatusMessage, runAsync)
        "teaching", "lecturerGroups", "lecturerTests", "lecturerMeetings", "lecturerAttendance", "lecturerReports" -> LecturerWorkspaceSection(state, repository, preferences, language, activeSection, onOpenUrl, onReloadState, onStatusMessage, runAsync)
        "staffDirectory" -> StaffDirectorySection(state, repository, language, onReloadState, onStatusMessage, runAsync)
        "registrations", "exams", "theses", "academicCalendar", "mobility", "placementAndSlips", "campusGuide", "communications" -> AcademicExtrasSection(state, repository, language, activeSection, onReloadState, onStatusMessage, runAsync)
        "account" -> AccountSection(
            state = state,
            repository = repository,
            preferences = preferences,
            themeMode = themeMode,
            language = language,
            onThemeChanged = onThemeChanged,
            onLanguageChanged = onLanguageChanged,
            onEnableDemo = onEnableDemo,
            onOpenUrl = onOpenUrl,
            onReloadState = onReloadState,
            onStatusMessage = onStatusMessage,
            runAsync = runAsync
        )
        "gpa" -> GpaSimulatorSection(snapshot, language)
        "tasks" -> TasksSection(state, repository, language, onReloadState, runAsync)
        "news" -> NewsListCard(snapshot?.news?.take(10) ?: emptyList(), strings, onOpenUrl)
        "attendance" -> AttendanceSection(
            snapshot = snapshot,
            preferences = preferences,
            strings = strings,
            onStatusMessage = onStatusMessage
        )
        "services", "cards", "documents", "payments" -> PaymentsAndServicesSection(
            state = state,
            language = language,
            snapshot = snapshot,
            repository = repository,
            onOpenUrl = onOpenUrl,
            onReloadState = onReloadState,
            onStatusMessage = onStatusMessage,
            runAsync = runAsync,
            section = activeSection
        )
    }
}
