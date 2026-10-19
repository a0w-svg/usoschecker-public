package pl.vistula.checker.ui.compose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import pl.vistula.checker.Demo
import pl.vistula.checker.DemoStudentServices
import pl.vistula.checker.KeyValueStore
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.SyncScheduler
import pl.vistula.checker.UsosClient
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

private fun applyDemoState(repository: SharedRepository, changed: Boolean) {
    val demo = Demo.state(changed)
    demo.put("studentServices", DemoStudentServices.data())
    repository.update { s ->
        val existingKeys = s.keys().asSequence().toList()
        for (k in existingKeys) s.remove(k)
        val demoKeys = demo.keys()
        while (demoKeys.hasNext()) {
            val k = demoKeys.next()
            s.put(k, demo.get(k))
        }
    }
}

@Composable
fun VistulaApp(
    repository: SharedRepository,
    preferences: KeyValueStore,
    notifier: ChangeNotifier? = null,
    syncScheduler: SyncScheduler? = null,
    onOpenUrl: (String) -> Unit = {},
    onExportText: (filename: String, mimeType: String, content: String) -> Unit = { _, _, _ -> },
    externalTabTrigger: VistulaTab? = null,
    externalSyncTrigger: Int = 0,
    externalDemoTrigger: Int = 0
) {
    val scope = rememberCoroutineScope()
    var stateVersion by remember { mutableIntStateOf(0) }
    var state by remember(stateVersion) { mutableStateOf(repository.read()) }
    var currentTab by remember { mutableStateOf(VistulaTab.HOME) }
    var themeMode by remember {
        mutableStateOf(
            preferences.getString("ui_theme_mode", preferences.getString("theme", "dark") ?: "dark") ?: "dark"
        )
    }
    var language by remember {
        mutableStateOf(
            preferences.getString("ui_language", preferences.getString("language", "pl") ?: "pl") ?: "pl"
        )
    }
    val strings = remember(language) { UiStrings.forLanguage(language) }
    var statusMessage by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(externalTabTrigger) {
        if (externalTabTrigger != null) {
            currentTab = externalTabTrigger
        }
    }

    val reloadState: () -> Unit = {
        stateVersion++
    }

    val runAsync: (() -> Unit) -> Unit = { block ->
        scope.launch {
            isRefreshing = true
            try {
                withContext(Dispatchers.IO) {
                    block()
                }
            } finally {
                stateVersion++
                isRefreshing = false
            }
        }
    }

    val refreshAll: () -> Unit = {
        runAsync {
            try {
                if ("demo" == state.optString("token")) {
                    applyDemoState(repository, true)
                    statusMessage = "OK (${strings.statusDemoMode})"
                } else if (state.optString("token").isEmpty()) {
                    statusMessage = strings.onboardingPointOauth
                } else {
                    val changes = repository.sync(
                        true,
                        { s -> UsosClient(s, java.util.function.Consumer { msg -> statusMessage = msg }).snapshot() },
                        { diff ->
                            if (diff.isNotEmpty() && state.optBoolean("notifications", true)) {
                                notifier?.notifyChanges(diff)
                            }
                        },
                        true
                    )
                    statusMessage = if (changes.isNotEmpty()) {
                        "Δ +${changes.size}"
                    } else {
                        "OK"
                    }
                }
            } catch (e: Exception) {
                statusMessage = SharedRepository.message(e)
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
            applyDemoState(repository, false)
            reloadState()
            statusMessage = strings.statusDemoMode
        }
    }

    VistulaTheme(themeMode = themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val isWide = maxWidth >= 640.dp
            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Vistula",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "USOS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    ) {
                        VistulaTab.entries.forEach { tab ->
                            val label = tab.label(strings)
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TopStatusBar(
                            state = state,
                            strings = strings,
                            statusMessage = statusMessage,
                            isRefreshing = isRefreshing,
                            onRefresh = refreshAll,
                            onEnableDemo = {
                                applyDemoState(repository, false)
                                reloadState()
                                statusMessage = strings.statusDemoMode
                            }
                        )
                        if (isRefreshing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Crossfade(targetState = currentTab, label = "tabCrossfade") { tab ->
                                TabContent(
                                    currentTab = tab,
                                    isWide = true,
                                    state = state,
                                    strings = strings,
                                    repository = repository,
                                    preferences = preferences,
                                    syncScheduler = syncScheduler,
                                    themeMode = themeMode,
                                    language = language,
                                    onThemeChanged = {
                                        preferences.putString("ui_theme_mode", it)
                                        themeMode = it
                                    },
                                    onLanguageChanged = {
                                        preferences.putString("ui_language", it)
                                        language = it
                                    },
                                    onNavigateTab = { currentTab = it },
                                    onEnableDemo = {
                                        applyDemoState(repository, false)
                                        reloadState()
                                        statusMessage = strings.statusDemoMode
                                    },
                                    onOpenUrl = onOpenUrl,
                                    onExportText = onExportText,
                                    onReloadState = reloadState,
                                    onStatusMessage = { statusMessage = it },
                                    runAsync = runAsync
                                )
                            }
                        }
                    }
                }
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            VistulaTab.entries.forEach { tab ->
                                val label = tab.label(strings)
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        TopStatusBar(
                            state = state,
                            strings = strings,
                            statusMessage = statusMessage,
                            isRefreshing = isRefreshing,
                            onRefresh = refreshAll,
                            onEnableDemo = {
                                applyDemoState(repository, false)
                                reloadState()
                                statusMessage = strings.statusDemoMode
                            }
                        )
                        if (isRefreshing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Crossfade(targetState = currentTab, label = "tabCrossfadeMobile") { tab ->
                                TabContent(
                                    currentTab = tab,
                                    isWide = false,
                                    state = state,
                                    strings = strings,
                                    repository = repository,
                                    preferences = preferences,
                                    syncScheduler = syncScheduler,
                                    themeMode = themeMode,
                                    language = language,
                                    onThemeChanged = {
                                        preferences.putString("ui_theme_mode", it)
                                        themeMode = it
                                    },
                                    onLanguageChanged = {
                                        preferences.putString("ui_language", it)
                                        language = it
                                    },
                                    onNavigateTab = { currentTab = it },
                                    onEnableDemo = {
                                        applyDemoState(repository, false)
                                        reloadState()
                                        statusMessage = strings.statusDemoMode
                                    },
                                    onOpenUrl = onOpenUrl,
                                    onExportText = onExportText,
                                    onReloadState = reloadState,
                                    onStatusMessage = { statusMessage = it },
                                    runAsync = runAsync
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
private fun TopStatusBar(
    state: JSONObject,
    strings: UiStrings,
    statusMessage: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onEnableDemo: () -> Unit
) {
    val token = state.optString("token")
    val isConnected = token.isNotEmpty()
    val isDemo = token == "demo"
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDemo) "${strings.statusDemoMode} · ${state.optString("user", "Student")}"
                    else if (isConnected) state.optString("user", strings.statusConnected)
                    else strings.statusDisconnected,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val sub = statusMessage.ifEmpty {
                    val error = state.optString("error")
                    if (error.isNotEmpty()) error else strings.statusReady
                }
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isConnected) {
                    OutlinedButton(onClick = onEnableDemo) {
                        Text(strings.btnDemo)
                    }
                }
                Button(onClick = onRefresh, enabled = !isRefreshing) {
                    Text(if (isRefreshing) strings.btnSyncing else strings.btnSync)
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    currentTab: VistulaTab,
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
    onExportText: (String, String, String) -> Unit,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    val snapshot = remember(state, language) { parseSnapshot(state, language) }
    when (currentTab) {
        VistulaTab.HOME -> HomeScreen(
            state = state,
            snapshot = snapshot,
            strings = strings,
            isWide = isWide,
            onNavigateTab = onNavigateTab,
            onEnableDemo = onEnableDemo,
            onOpenUrl = onOpenUrl
        )
        VistulaTab.SCHEDULE -> ScheduleScreen(
            snapshot = snapshot,
            strings = strings,
            isWide = isWide,
            onExportText = onExportText,
            onStatusMessage = onStatusMessage
        )
        VistulaTab.GRADES -> GradesScreen(
            snapshot = snapshot,
            strings = strings,
            isWide = isWide
        )
        VistulaTab.HISTORY -> HistoryScreen(
            state = state,
            strings = strings
        )
        VistulaTab.MORE -> MoreScreen(
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
            onOpenUrl = onOpenUrl,
            onReloadState = onReloadState,
            onStatusMessage = onStatusMessage,
            runAsync = runAsync
        )
    }
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: JSONObject,
    snapshot: Snapshot?,
    strings: UiStrings,
    isWide: Boolean,
    onNavigateTab: (VistulaTab) -> Unit,
    onEnableDemo: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val nowInstant = Instant.now()
    val today = LocalDate.now(UsosClient.ZONE)
    val isConnected = state.optString("token").isNotEmpty()
    val allRecords = snapshot?.records?.values ?: emptyList()
    val lessons = remember(snapshot) { LessonTime.sorted(allRecords) }
    val grades = remember(snapshot) {
        GradeBrowser.select(allRecords, "", "all", "recent", "", snapshot)
    }
    val gradeSummary = remember(snapshot) { GradeSummary.of(allRecords) }
    val currentLessons = remember(lessons, nowInstant) { LessonTime.current(allRecords, nowInstant) }
    val nextLesson = remember(lessons, nowInstant) {
        lessons.firstOrNull { !it.tentative() && it.start.toInstant().isAfter(nowInstant) }
    }
    val todaySummary = remember(lessons, today) { DaySummary.of(lessons, today) }
    val openTasksCount = remember(state, today) {
        val rawTasks = state.optJSONArray("tasks") ?: JSONArray()
        TaskTools.sorted(rawTasks, "due").count { TaskTools.matches(it, "open", "", today) }
    }
    val overdueTasksCount = remember(state, today) {
        val rawTasks = state.optJSONArray("tasks") ?: JSONArray()
        TaskTools.sorted(rawTasks, "due").count { TaskTools.matches(it, "overdue", "", today) }
    }
    val news = snapshot?.news ?: emptyList()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isConnected) {
            OnboardingWelcomeCard(
                strings = strings,
                onEnableDemo = onEnableDemo,
                onConnectAccount = { onNavigateTab(VistulaTab.MORE) }
            )
        }

        // Hero Card: Current / Next Class
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val activeLesson = currentLessons.firstOrNull() ?: nextLesson
                Text(
                    text = when {
                        currentLessons.isNotEmpty() -> {
                            val minsLeft = Duration.between(nowInstant, currentLessons.first().end.toInstant()).toMinutes().coerceAtLeast(0)
                            "${strings.heroOngoingClass} ($minsLeft min)"
                        }
                        nextLesson != null -> {
                            val minsUntil = Duration.between(nowInstant, nextLesson.start.toInstant()).toMinutes().coerceAtLeast(0)
                            "${strings.heroNextClass} ($minsUntil min)"
                        }
                        else -> "${strings.heroToday} ($today)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                if (activeLesson != null) {
                    val r = activeLesson.record
                    Text(
                        text = r.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() }
                    )
                    val timeLabel = "${activeLesson.start.format(TIME_FMT)} – ${activeLesson.end.format(TIME_FMT)}"
                    val room = r.optMeta("room")
                    val building = r.optMeta("building")
                    val location = listOfNotNull(
                        room.takeIf { it.isNotEmpty() }?.let { "Sala $it" },
                        building.takeIf { it.isNotEmpty() }
                    ).joinToString(", ")
                    Text(
                        text = listOfNotNull(
                            timeLabel,
                            location.takeIf { it.isNotEmpty() },
                            r.optMeta("classType").takeIf { it.isNotEmpty() }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        text = if (todaySummary.meetings > 0) {
                            "${strings.heroFinishedToday} (${todaySummary.meetings})"
                        } else {
                            strings.heroNoClassesToday
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNavigateTab(VistulaTab.SCHEDULE) }) {
                        Text(strings.btnOpenSchedule)
                    }
                    OutlinedButton(onClick = { onNavigateTab(VistulaTab.GRADES) }) {
                        Text(strings.btnViewGrades)
                    }
                }
            }
        }

        // KPI Grid
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val avgVal = if (!gradeSummary.ectsAverage.isNaN()) gradeSummary.ectsAverage else gradeSummary.average
            KpiStatCard(
                title = strings.kpiAverageTitle,
                value = if (!avgVal.isNaN()) String.format(Locale.US, "%.2f", avgVal) else "—",
                subtitle = "ECTS: ${String.format(Locale.US, "%.0f", gradeSummary.totalEcts)} (${gradeSummary.numeric + gradeSummary.other})",
                modifier = Modifier.weight(1f).width(160.dp)
            )
            KpiStatCard(
                title = strings.kpiTodayPlanTitle,
                value = "${todaySummary.meetings}",
                subtitle = if (todaySummary.meetings > 0) "${todaySummary.busyMinutes} min" else strings.kpiFreeDay,
                modifier = Modifier.weight(1f).width(160.dp)
            )
            KpiStatCard(
                title = strings.kpiTasksTitle,
                value = "$openTasksCount",
                subtitle = if (overdueTasksCount > 0) "! $overdueTasksCount" else strings.kpiNoOverdue,
                modifier = Modifier.weight(1f).width(160.dp)
            )
            val payments = snapshot?.payments
            val installmentsCount = payments?.installments?.size ?: 0
            KpiStatCard(
                title = strings.kpiPaymentsTitle,
                value = if (installmentsCount > 0) "$installmentsCount" else strings.kpiSettled,
                subtitle = payments?.statusNotice?.ifEmpty { strings.kpiSettled } ?: strings.kpiSettled,
                modifier = Modifier.weight(1f).width(160.dp)
            )
        }

        if (isWide) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    RecentGradesCard(
                        grades = grades.take(5),
                        snapshot = snapshot,
                        strings = strings,
                        onSeeAll = { onNavigateTab(VistulaTab.GRADES) }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    NewsListCard(news = news.take(5), strings = strings, onOpenUrl = onOpenUrl)
                }
            }
        } else {
            RecentGradesCard(
                grades = grades.take(5),
                snapshot = snapshot,
                strings = strings,
                onSeeAll = { onNavigateTab(VistulaTab.GRADES) }
            )
            NewsListCard(news = news.take(5), strings = strings, onOpenUrl = onOpenUrl)
        }
    }
}

@Composable
private fun OnboardingWelcomeCard(
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
private fun RecentGradesCard(
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
private fun NewsListCard(news: List<NewsArticle>, strings: UiStrings, onOpenUrl: (String) -> Unit) {
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
    onExportText: (filename: String, mimeType: String, content: String) -> Unit,
    onStatusMessage: (String) -> Unit
) {
    val allRecords = snapshot?.records?.values ?: emptyList()
    val allLessons = remember(snapshot) { LessonTime.sorted(allRecords) }
    var selectedDate by remember { mutableStateOf(LocalDate.now(UsosClient.ZONE)) }
    var viewMode by remember { mutableStateOf("day") } // day | week | agenda
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("") }
    var selectedLesson by remember { mutableStateOf<LessonTime?>(null) }

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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$weekMonday – ${weekMonday.plusDays(6)}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { selectedDate = selectedDate.minusWeeks(1) }) { Text(strings.btnPrevWeek) }
                        OutlinedButton(onClick = { selectedDate = LocalDate.now(UsosClient.ZONE) }) { Text(strings.btnToday) }
                        OutlinedButton(onClick = { selectedDate = selectedDate.plusWeeks(1) }) { Text(strings.btnNextWeek) }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "day" to strings.viewDayTimeline,
                        "week" to strings.viewFullWeek,
                        "agenda" to strings.viewAgenda
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = viewMode == mode,
                            onClick = { viewMode = mode },
                            label = { Text(label) }
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val ics = IcsExporter.export(filteredLessons.map { it.record })
                            onExportText("vistula-schedule.ics", "text/calendar", ics)
                            onStatusMessage("${strings.btnExportIcs} (${filteredLessons.size})")
                        }
                    ) {
                        Text("${strings.btnExportIcs} (${filteredLessons.size})")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(strings.searchScheduleHint) },
                    modifier = Modifier.fillMaxWidth(),
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

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0..6) {
                        val day = weekMonday.plusDays(i.toLong())
                        val count = filteredLessons.count { it.intersects(day) }
                        val dayLabel = day.dayOfWeek.name.take(3) + " " + day.dayOfMonth
                        FilterChip(
                            selected = day == selectedDate,
                            onClick = { selectedDate = day },
                            label = { Text(if (count > 0) "$dayLabel ($count)" else dayLabel) }
                        )
                    }
                }
            }
        }

        when (viewMode) {
            "day" -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${strings.daySummaryTitle} ($selectedDate): ${daySummary.meetings}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (daySummary.meetings > 0) {
                            Text(
                                text = "${daySummary.busyMinutes} min · ${daySummary.gapMinutes} min" +
                                    (if (daySummary.conflicts > 0) " · ! ${daySummary.conflicts}" else ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (breaks.isNotEmpty()) {
                            Text(
                                text = breaks.joinToString(", ") {
                                    "${it.start.format(TIME_FMT)}–${it.end.format(TIME_FMT)} (${it.minutes} min)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (dayLessons.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "${strings.noClassesOnDay} ($selectedDate).",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    if (isWide) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                DayTimelineCompose(
                                    day = selectedDate,
                                    lessons = dayLessons,
                                    breaks = breaks,
                                    strings = strings,
                                    onSelectLesson = { selectedLesson = it }
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                dayLessons.forEach { lt ->
                                    ScheduleLessonCard(
                                        lesson = lt,
                                        highlighted = selectedLesson == lt,
                                        onClick = { selectedLesson = if (selectedLesson == lt) null else lt }
                                    )
                                }
                            }
                        }
                    } else {
                        DayTimelineCompose(
                            day = selectedDate,
                            lessons = dayLessons,
                            breaks = breaks,
                            strings = strings,
                            onSelectLesson = { selectedLesson = it }
                        )
                        dayLessons.forEach { lt ->
                            ScheduleLessonCard(
                                lesson = lt,
                                highlighted = selectedLesson == lt,
                                onClick = { selectedLesson = if (selectedLesson == lt) null else lt }
                            )
                        }
                    }
                }
            }
            "week" -> {
                for (i in 0..6) {
                    val day = weekMonday.plusDays(i.toLong())
                    val events = filteredLessons.filter { it.intersects(day) }
                    if (events.isNotEmpty() || day.dayOfWeek.value <= DayOfWeek.FRIDAY.value) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "$day (${day.dayOfWeek.name})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (events.isEmpty()) {
                                    Text(strings.noClassesOnDay, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    events.forEach { lt -> ScheduleLessonCard(lt) }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (filteredLessons.isEmpty()) {
                    Text(strings.noClassesOnDay, style = MaterialTheme.typography.bodyMedium)
                } else {
                    filteredLessons.forEach { lt -> ScheduleLessonCard(lt) }
                }
            }
        }
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
    onClick: () -> Unit = {}
) {
    val r = lesson.record
    val classType = r.optMeta("classType")
    val room = r.optMeta("room")
    val building = r.optMeta("building")
    val lecturers = r.optMeta("lecturers")
    val group = r.optMeta("groupNumber")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = if (highlighted) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${lesson.start.format(TIME_FMT)} – ${lesson.end.format(TIME_FMT)} (${lesson.start.toLocalDate()})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (classType.isNotEmpty()) {
                    Text(
                        text = classType,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(r.title, style = MaterialTheme.typography.titleMedium)
            val meta = listOfNotNull(
                room.takeIf { it.isNotEmpty() }?.let { "Sala $it" },
                building.takeIf { it.isNotEmpty() },
                lecturers.takeIf { it.isNotEmpty() },
                group.takeIf { it.isNotEmpty() }?.let { "Grupa $it" }
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

    var selectedTerm by remember { mutableStateOf("") }
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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(strings.gradesSummaryTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text(
                            text = "${strings.filterNumeric}: ${summary.numeric} · ${summary.other} · ${ungradedCourses.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val avg = if (!summary.ectsAverage.isNaN()) summary.ectsAverage else summary.average
                        Text(
                            text = if (!avg.isNaN()) String.format(Locale.US, "GPA: %.2f", avg) else "GPA: —",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (termOrder.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedTerm.isEmpty(),
                            onClick = { selectedTerm = "" },
                            label = { Text(strings.filterAllSemesters) }
                        )
                        termOrder.forEach { termId ->
                            val termLabel = snapshot?.terms?.get(termId)?.name?.takeIf { it.isNotEmpty() } ?: termId
                            FilterChip(
                                selected = selectedTerm == termId,
                                onClick = { selectedTerm = termId },
                                label = { Text(termLabel) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(strings.searchGradesHint) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "all" to strings.filterAllGrades,
                        "numeric" to strings.filterNumeric,
                        "labs" to strings.filterLabs,
                        "exercises" to strings.filterExercises,
                        "retakes" to strings.filterRetakes
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = filterMode == id,
                            onClick = { filterMode = id },
                            label = { Text(label) }
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    listOf(
                        "title" to strings.sortAZ,
                        "recent" to strings.sortRecent,
                        "value" to strings.sortHighest
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = sortMode == id,
                            onClick = { sortMode = id },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        if (isWide && selectedGroup != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { group ->
                        GradeGroupCard(group = group, snapshot = snapshot, onClick = { selectedGroup = group })
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    GradeGroupDetailCard(
                        group = selectedGroup!!,
                        snapshot = snapshot,
                        strings = strings,
                        onClose = { selectedGroup = null }
                    )
                }
            }
        } else {
            groups.forEach { group ->
                GradeGroupCard(group = group, snapshot = snapshot, onClick = { selectedGroup = if (selectedGroup == group) null else group })
                if (selectedGroup == group) {
                    GradeGroupDetailCard(
                        group = group,
                        snapshot = snapshot,
                        strings = strings,
                        onClose = { selectedGroup = null }
                    )
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

        if (crstests.isNotEmpty()) {
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${test.points} / ${test.maxPoints} pkt" + (if (test.grade.isNotEmpty()) " (${test.grade})" else ""),
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
private fun GradeGroupCard(group: List<Record>, snapshot: Snapshot?, onClick: () -> Unit) {
    val first = group.firstOrNull() ?: return
    val symbols = group.joinToString(", ") { "${GradeBrowser.classType(it, snapshot)}: ${GradeBrowser.symbol(it)}" }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(first.title, style = MaterialTheme.typography.titleMedium)
                Text("${first.term} · $symbols", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = GradeBrowser.symbol(first),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
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
                Text(first.title, style = MaterialTheme.typography.titleMedium)
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
    val history = state.optJSONArray("history") ?: JSONArray()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "${strings.historyTitle} (${history.length()})",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        if (history.length() == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = strings.historyEmpty,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            for (i in 0 until history.length()) {
                val entry = history.optJSONObject(i) ?: continue
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = entry.optString("title", "Δ"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = entry.optString("details", entry.toString()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    onOpenUrl: (String) -> Unit,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    var activeSection by remember { mutableStateOf("account") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "account" to strings.sectionAccount,
                "gpa" to strings.sectionGpa,
                "tasks" to strings.sectionTasks,
                "attendance" to strings.sectionAttendance,
                "services" to strings.sectionServices
            ).forEach { (id, title) ->
                FilterChip(
                    selected = activeSection == id,
                    onClick = { activeSection = id },
                    label = { Text(title) }
                )
            }
        }

        when (activeSection) {
            "account" -> AccountSection(
                state = state,
                repository = repository,
                preferences = preferences,
                themeMode = themeMode,
                language = language,
                onThemeChanged = onThemeChanged,
                onLanguageChanged = onLanguageChanged,
                onOpenUrl = onOpenUrl,
                onReloadState = onReloadState,
                onStatusMessage = onStatusMessage,
                runAsync = runAsync
            )
            "gpa" -> GpaSimulatorSection(
                snapshot = snapshot
            )
            "tasks" -> TasksSection(
                state = state,
                repository = repository,
                onReloadState = onReloadState
            )
            "attendance" -> AttendanceSection(
                snapshot = snapshot,
                preferences = preferences,
                strings = strings
            )
            "services" -> PaymentsAndServicesSection(
                state = state,
                snapshot = snapshot,
                repository = repository,
                onOpenUrl = onOpenUrl,
                onReloadState = onReloadState,
                onStatusMessage = onStatusMessage,
                runAsync = runAsync
            )
        }
    }
}
