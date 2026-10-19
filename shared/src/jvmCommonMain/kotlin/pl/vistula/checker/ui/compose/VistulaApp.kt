package pl.vistula.checker.ui.compose

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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

enum class VistulaTab(val id: String, val labelPl: String, val labelEn: String, val badgeSymbol: String) {
    HOME("home", "Start", "Home", "🏠"),
    SCHEDULE("schedule", "Plan", "Schedule", "📅"),
    GRADES("grades", "Oceny", "Grades", "🎓"),
    HISTORY("history", "Zmiany", "Changes", "🔔"),
    MORE("more", "Więcej", "More", "⚙️")
}

private fun parseSnapshot(state: JSONObject): Snapshot? {
    val raw = state.optJSONObject("snapshot") ?: return null
    return try {
        Snapshot.from(raw)
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
    externalTabTrigger: VistulaTab? = null
) {
    val scope = rememberCoroutineScope()
    var stateVersion by remember { mutableIntStateOf(0) }
    var state by remember(stateVersion) { mutableStateOf(repository.read()) }
    var currentTab by remember { mutableStateOf(VistulaTab.HOME) }
    var themeMode by remember { mutableStateOf(preferences.getString("ui_theme_mode", "dark") ?: "dark") }
    var language by remember { mutableStateOf(preferences.getString("ui_language", "pl") ?: "pl") }
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
                    statusMessage = "Odświeżono dane demonstracyjne."
                } else if (state.optString("token").isEmpty()) {
                    statusMessage = "Połącz konto USOS lub włącz tryb demo w sekcji Więcej."
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
                        "Zaktualizowano i wykryto nowe zmiany (${changes.size})!"
                    } else {
                        "Dane są aktualne."
                    }
                }
            } catch (e: Exception) {
                statusMessage = SharedRepository.message(e)
            }
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
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    ) {
                        VistulaTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Text(tab.badgeSymbol) },
                                label = { Text(if (language == "en") tab.labelEn else tab.labelPl) }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TopStatusBar(
                            state = state,
                            statusMessage = statusMessage,
                            isRefreshing = isRefreshing,
                            onRefresh = refreshAll,
                            onEnableDemo = {
                                applyDemoState(repository, false)
                                reloadState()
                                statusMessage = "Włączono tryb demo."
                            }
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            TabContent(
                                currentTab = currentTab,
                                isWide = true,
                                state = state,
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
                                onOpenUrl = onOpenUrl,
                                onExportText = onExportText,
                                onReloadState = reloadState,
                                onStatusMessage = { statusMessage = it },
                                runAsync = runAsync
                            )
                        }
                    }
                }
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            VistulaTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Text(tab.badgeSymbol) },
                                    label = { Text(if (language == "en") tab.labelEn else tab.labelPl) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        TopStatusBar(
                            state = state,
                            statusMessage = statusMessage,
                            isRefreshing = isRefreshing,
                            onRefresh = refreshAll,
                            onEnableDemo = {
                                applyDemoState(repository, false)
                                reloadState()
                                statusMessage = "Włączono tryb demo."
                            }
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            TabContent(
                                currentTab = currentTab,
                                isWide = false,
                                state = state,
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

@Composable
private fun TopStatusBar(
    state: JSONObject,
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
                    text = if (isDemo) "Tryb Demo · ${state.optString("user", "Student")}"
                    else if (isConnected) state.optString("user", "Połączono z USOS")
                    else "Niepołączono z USOS",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val sub = statusMessage.ifEmpty {
                    val error = state.optString("error")
                    if (error.isNotEmpty()) error else "Gotowy do pracy offline/online"
                }
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isConnected) {
                    OutlinedButton(onClick = onEnableDemo) {
                        Text("Demo")
                    }
                }
                Button(onClick = onRefresh, enabled = !isRefreshing) {
                    Text(if (isRefreshing) "Odświeżanie..." else "Synchronizuj")
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
    repository: SharedRepository,
    preferences: KeyValueStore,
    syncScheduler: SyncScheduler?,
    themeMode: String,
    language: String,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onNavigateTab: (VistulaTab) -> Unit,
    onOpenUrl: (String) -> Unit,
    onExportText: (String, String, String) -> Unit,
    onReloadState: () -> Unit,
    onStatusMessage: (String) -> Unit,
    runAsync: (() -> Unit) -> Unit
) {
    val snapshot = remember(state) { parseSnapshot(state) }
    when (currentTab) {
        VistulaTab.HOME -> HomeScreen(
            state = state,
            snapshot = snapshot,
            isWide = isWide,
            onNavigateTab = onNavigateTab,
            onOpenUrl = onOpenUrl
        )
        VistulaTab.SCHEDULE -> ScheduleScreen(
            snapshot = snapshot,
            isWide = isWide,
            onExportText = onExportText,
            onStatusMessage = onStatusMessage
        )
        VistulaTab.GRADES -> GradesScreen(
            snapshot = snapshot,
            isWide = isWide
        )
        VistulaTab.HISTORY -> HistoryScreen(
            state = state
        )
        VistulaTab.MORE -> MoreScreen(
            state = state,
            snapshot = snapshot,
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
    isWide: Boolean,
    onNavigateTab: (VistulaTab) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val nowInstant = Instant.now()
    val today = LocalDate.now(UsosClient.ZONE)
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
                            "TRWAJĄCE ZAJĘCIA ($minsLeft min do końca)"
                        }
                        nextLesson != null -> {
                            val minsUntil = Duration.between(nowInstant, nextLesson.start.toInstant()).toMinutes().coerceAtLeast(0)
                            "NASTĘPNE ZAJĘCIA (za $minsUntil min)"
                        }
                        else -> "DZISIAJ ($today)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                if (activeLesson != null) {
                    val r = activeLesson.record
                    Text(
                        text = r.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
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
                            "Zakończono dzisiejsze zajęcia (${todaySummary.meetings})"
                        } else {
                            "Brak zaplanowanych zajęć na dziś"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNavigateTab(VistulaTab.SCHEDULE) }) {
                        Text("Otwórz plan zajęć")
                    }
                    OutlinedButton(onClick = { onNavigateTab(VistulaTab.GRADES) }) {
                        Text("Zobacz oceny")
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
                title = "Średnia ocen",
                value = if (!avgVal.isNaN()) String.format(Locale.US, "%.2f", avgVal) else "—",
                subtitle = "Ocen: ${gradeSummary.numeric + gradeSummary.other} (ECTS: ${String.format(Locale.US, "%.0f", gradeSummary.totalEcts)})",
                modifier = Modifier.weight(1f).width(160.dp)
            )
            KpiStatCard(
                title = "Dzisiejszy plan",
                value = "${todaySummary.meetings} zajęć",
                subtitle = if (todaySummary.meetings > 0) "Zajętość: ${todaySummary.busyMinutes} min" else "Wolny dzień",
                modifier = Modifier.weight(1f).width(160.dp)
            )
            KpiStatCard(
                title = "Zadania i terminy",
                value = "$openTasksCount otwartych",
                subtitle = if (overdueTasksCount > 0) "$overdueTasksCount po terminie!" else "Brak zaległości",
                modifier = Modifier.weight(1f).width(160.dp)
            )
            val payments = snapshot?.payments
            val installmentsCount = payments?.installments?.size ?: 0
            KpiStatCard(
                title = "Płatności",
                value = if (installmentsCount > 0) "Rat: $installmentsCount" else "Rozliczone",
                subtitle = payments?.statusNotice?.ifEmpty { "Status finansowy" } ?: "Brak danych",
                modifier = Modifier.weight(1f).width(160.dp)
            )
        }

        if (isWide) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    RecentGradesCard(grades = grades.take(5), snapshot = snapshot, onSeeAll = { onNavigateTab(VistulaTab.GRADES) })
                }
                Box(modifier = Modifier.weight(1f)) {
                    NewsListCard(news = news.take(5), onOpenUrl = onOpenUrl)
                }
            }
        } else {
            RecentGradesCard(grades = grades.take(5), snapshot = snapshot, onSeeAll = { onNavigateTab(VistulaTab.GRADES) })
            NewsListCard(news = news.take(5), onOpenUrl = onOpenUrl)
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentGradesCard(grades: List<Record>, snapshot: Snapshot?, onSeeAll: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ostatnie oceny", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onSeeAll) { Text("Wszystkie") }
            }
            if (grades.isEmpty()) {
                Text("Brak zapisanych ocen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                style = MaterialTheme.typography.bodySmall,
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
private fun NewsListCard(news: List<NewsArticle>, onOpenUrl: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Aktualności uczelni", style = MaterialTheme.typography.titleMedium)
            if (news.isEmpty()) {
                Text("Brak aktualności w pamięci podręcznej.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        val body = article.headline.ifEmpty { article.content }
                        if (body.isNotEmpty()) {
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodySmall,
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
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { selectedDate = selectedDate.minusWeeks(1) }) { Text("◀ Tydzień") }
                        OutlinedButton(onClick = { selectedDate = LocalDate.now(UsosClient.ZONE) }) { Text("Dziś") }
                        OutlinedButton(onClick = { selectedDate = selectedDate.plusWeeks(1) }) { Text("Tydzień ▶") }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("day" to "Oś dnia", "week" to "Cały tydzień", "agenda" to "Lista / Agenda").forEach { (mode, label) ->
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
                            onStatusMessage("Wyeksportowano ${filteredLessons.size} zajęć do formatu ICS.")
                        }
                    ) {
                        Text("Eksportuj ICS (${filteredLessons.size})")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Szukaj przedmiotu, sali, prowadzącego...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (classTypes.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedType.isEmpty(),
                            onClick = { selectedType = "" },
                            label = { Text("Wszystkie typy") }
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
                            text = "Podsumowanie dnia ($selectedDate): ${daySummary.meetings} zajęć",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (daySummary.meetings > 0) {
                            Text(
                                text = "Zajętość: ${daySummary.busyMinutes} min · Okienka: ${daySummary.gapMinutes} min" +
                                    (if (daySummary.conflicts > 0) " · Kolizje: ${daySummary.conflicts}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (breaks.isNotEmpty()) {
                            Text(
                                text = "Okienka między zajęciami: " + breaks.joinToString(", ") {
                                    "${it.start.format(TIME_FMT)}-${it.end.format(TIME_FMT)} (${it.minutes} min)"
                                },
                                style = MaterialTheme.typography.bodySmall,
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
                            text = "Brak zajęć w wybranym dniu ($selectedDate).",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    if (isWide) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                DayTimelineCompose(lessons = dayLessons, breaks = breaks)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                dayLessons.forEach { lt -> ScheduleLessonCard(lt) }
                            }
                        }
                    } else {
                        DayTimelineCompose(lessons = dayLessons, breaks = breaks)
                        dayLessons.forEach { lt -> ScheduleLessonCard(lt) }
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
                                    Text("Brak zajęć", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Brak zajęć spełniających kryteria.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    filteredLessons.forEach { lt -> ScheduleLessonCard(lt) }
                }
            }
        }
    }
}

@Composable
fun DayTimelineCompose(
    lessons: List<LessonTime>,
    breaks: List<ScheduleBreaks.Break>
) {
    val startHour = remember(lessons) {
        (lessons.minOfOrNull { it.start.hour } ?: 8).coerceIn(6, 20)
    }
    val endHour = remember(lessons) {
        ((lessons.maxOfOrNull { it.end.hour + 1 } ?: 20)).coerceIn(startHour + 1, 23)
    }
    val totalMinutes = ((endHour - startHour) * 60).coerceAtLeast(60)
    val primaryColor = MaterialTheme.colorScheme.primaryContainer
    val accentColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val breakColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wizualna oś dnia ($startHour:00 – $endHour:00)", style = MaterialTheme.typography.titleMedium)
            Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val leftGutter = 32.dp.toPx()
                    val contentWidth = (size.width - leftGutter).coerceAtLeast(100f)

                    for (h in startHour..endHour) {
                        val ratio = ((h - startHour) * 60f) / totalMinutes.toFloat()
                        val y = ratio * size.height
                        drawLine(
                            color = outlineColor,
                            start = Offset(leftGutter, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    breaks.forEach { br ->
                        val brStartMins = br.start.hour * 60 + br.start.minute
                        val brEndMins = br.end.hour * 60 + br.end.minute
                        val topRatio = ((brStartMins - startHour * 60).toFloat() / totalMinutes).coerceIn(0f, 1f)
                        val bottomRatio = ((brEndMins - startHour * 60).toFloat() / totalMinutes).coerceIn(0f, 1f)
                        val y = topRatio * size.height
                        val h = ((bottomRatio - topRatio) * size.height).coerceAtLeast(4f)
                        drawRoundRect(
                            color = breakColor,
                            topLeft = Offset(leftGutter + 4f, y),
                            size = Size(contentWidth - 8f, h),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }

                    lessons.forEach { lt ->
                        val ltStartMins = lt.start.hour * 60 + lt.start.minute
                        val ltEndMins = lt.end.hour * 60 + lt.end.minute
                        val topRatio = ((ltStartMins - startHour * 60).toFloat() / totalMinutes).coerceIn(0f, 1f)
                        val bottomRatio = ((ltEndMins - startHour * 60).toFloat() / totalMinutes).coerceIn(0f, 1f)
                        val x = leftGutter + 4f
                        val y = topRatio * size.height
                        val w = (contentWidth - 8f).coerceAtLeast(24f)
                        val h = ((bottomRatio - topRatio) * size.height).coerceAtLeast(18f)

                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(x, y),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(x, y),
                            size = Size(4.dp.toPx(), h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleLessonCard(lesson: LessonTime) {
    val r = lesson.record
    val classType = r.optMeta("classType")
    val room = r.optMeta("room")
    val building = r.optMeta("building")
    val lecturers = r.optMeta("lecturers")
    val group = r.optMeta("groupNumber")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
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
                        style = MaterialTheme.typography.labelSmall,
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
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradesScreen(
    snapshot: Snapshot?,
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
                        Text("Podsumowanie ocen", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Ocen liczbowych: ${summary.numeric} · Innych: ${summary.other} · Oczekujących: ${ungradedCourses.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val avg = if (!summary.ectsAverage.isNaN()) summary.ectsAverage else summary.average
                        Text(
                            text = if (!avg.isNaN()) String.format(Locale.US, "Średnia: %.2f", avg) else "Średnia: —",
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
                            label = { Text("Wszystkie semestry") }
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
                    label = { Text("Szukaj przedmiotu lub oceny...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "all" to "Wszystkie",
                        "numeric" to "Liczbowe",
                        "labs" to "Laboratoria",
                        "exercises" to "Ćwiczenia",
                        "retakes" to "Poprawki"
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = filterMode == id,
                            onClick = { filterMode = id },
                            label = { Text(label) }
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    listOf(
                        "title" to "Sortuj A-Z",
                        "recent" to "Najnowsze",
                        "value" to "Najwyższe"
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
                    GradeGroupDetailCard(group = selectedGroup!!, snapshot = snapshot, onClose = { selectedGroup = null })
                }
            }
        } else {
            groups.forEach { group ->
                GradeGroupCard(group = group, snapshot = snapshot, onClick = { selectedGroup = if (selectedGroup == group) null else group })
                if (selectedGroup == group) {
                    GradeGroupDetailCard(group = group, snapshot = snapshot, onClose = { selectedGroup = null })
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
                    Text("Przedmioty oczekujące na ocenę (${ungradedCourses.size})", style = MaterialTheme.typography.titleMedium)
                    ungradedCourses.forEach { course ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(course.title, style = MaterialTheme.typography.bodyMedium)
                            Text(course.term, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Drzewo sprawdzianów i kolokwiów (${crstests.size})", style = MaterialTheme.typography.titleMedium)
                    crstests.forEach { test: CourseTestRecord ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
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
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (test.date.isNotEmpty()) {
                                Text(test.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("${first.term} · $symbols", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun GradeGroupDetailCard(group: List<Record>, snapshot: Snapshot?, onClose: () -> Unit) {
    val first = group.firstOrNull() ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Szczegóły ocen: ${first.title}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onClose) { Text("Zamknij") }
            }
            group.forEach { g ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(GradeBrowser.classType(g, snapshot), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(GradeBrowser.symbol(g), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(g.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val comment = g.optMeta("comment")
                    if (comment.isNotEmpty()) {
                        Text("Komentarz: $comment", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(state: JSONObject) {
    val history = state.optJSONArray("history") ?: JSONArray()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Historia zmian USOS (${history.length()})", style = MaterialTheme.typography.headlineMedium)
        if (history.length() == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "Brak zarejestrowanych zmian w planie lub ocenach. Po wykryciu różnic przez synchronizację w tle pojawią się one tutaj.",
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
                            text = entry.optString("title", "Zdarzenie"),
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
                "account" to "Konto i Wygląd",
                "gpa" to "Kalkulator ECTS / GPA",
                "tasks" to "Zadania",
                "attendance" to "Frekwencja",
                "services" to "Finanse i Usługi Studenckie"
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
                preferences = preferences
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
