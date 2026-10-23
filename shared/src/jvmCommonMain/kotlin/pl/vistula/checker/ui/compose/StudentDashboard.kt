package pl.vistula.checker.ui.compose

import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.time.Duration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.*

/** The dashboard reads saved data; every action leads to its existing full screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudentDashboard(
    state: JSONObject, snapshot: Snapshot?, strings: UiStrings, isWide: Boolean,
    onNavigateTab: (VistulaTab) -> Unit, onNavigateTool: (String) -> Unit, onOpenScheduleDate: (LocalDate) -> Unit,
    onEnableDemo: () -> Unit, onOpenUrl: (String) -> Unit
) {
    val language = strings.languageCode
    fun tr(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = Instant.now() } }
    val today = now.atZone(UsosClient.ZONE).toLocalDate()
    var dayOffset by remember { mutableIntStateOf(0) }
    val day = today.plusDays(dayOffset.toLong())
    val records = snapshot?.records?.values ?: emptyList()
    val lessons = LessonTime.sorted(records)
    val dayLessons = lessons.filter { it.intersects(day) }
    val grades = GradeBrowser.select(records, "", "all", "recent", "", snapshot)
    val tasks = TaskTools.sorted(state.optJSONArray("tasks") ?: JSONArray(), "due")
        .filter { !it.optBoolean("done") }
    val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag(language))
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (isWide) 24.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.optString("token").isEmpty()) {
            OnboardingWelcomeCard(strings, onEnableDemo) { onNavigateTool("account") }
        }
        val current = lessons.firstOrNull { !it.tentative() && it.current(now) }
        val next = current ?: lessons.firstOrNull { it.start.toInstant().isAfter(now) }
        Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.width(28.dp).height(2.dp).background(MaterialTheme.colorScheme.primary))
            Text((if (current != null) strings.heroOngoingClass else tr("Najbliższe zajęcia", "Next class", "Наступне заняття")).uppercase(Locale.forLanguageTag(language)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 2.sp)
            Text(next?.record?.title ?: tr("Brak najbliższych zajęć", "No upcoming classes", "Немає наступних занять"),
                style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            if (next != null) {
                val room = next.record.optMeta("room")
                Text((if (next.start.toLocalDate() == today) "" else next.start.format(dateFormat) + " · ") +
                    next.start.format(clock) + "–" + next.end.format(clock) +
                    if (room.isEmpty()) "" else " · " + tr("Sala ", "Room ", "Аудиторія ") + room,
                    textAlign = TextAlign.Center)
                val minutes = maxOf(1L, (Duration.between(now, if (current != null) next.end.toInstant() else next.start.toInstant()).seconds + 59) / 60)
                val status = when {
                    next.tentative() -> tr("Termin orientacyjny", "Tentative time", "Орієнтовний час")
                    current != null -> tr("Do końca: $minutes min", "$minutes min remaining", "До кінця: $minutes хв")
                    next.start.toLocalDate() == today -> tr("Początek za $minutes min", "Starts in $minutes min", "Початок за $minutes хв")
                    else -> next.start.format(dateFormat)
                }
                Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
                TextButton(onClick = { onOpenScheduleDate(next.start.toLocalDate()) }) { Text(strings.btnOpenSchedule) }
            }
        }
        val agenda: @Composable () -> Unit = {
            DashboardPanel(tr("Plan dnia", "Day plan", "План дня")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to tr("Dzisiaj", "Today", "Сьогодні"), 1 to tr("Jutro", "Tomorrow", "Завтра")).forEach { (offset, label) ->
                        FilterChip(selected = dayOffset == offset, onClick = { dayOffset = offset }, label = { Text(label) })
                    }
                }
                Text(day.format(dateFormat), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (dayLessons.isEmpty()) Text(if (snapshot == null) tr("Odśwież dane konta lub uruchom demo.", "Refresh your account or try demo.", "Оновіть дані або спробуйте демо.")
                    else tr("Brak zajęć w zapisanym planie na ten dzień.", "No saved classes for this day.", "Цього дня немає збережених занять."))
                dayLessons.take(6).forEach { lesson ->
                    HorizontalDivider()
                    val current = !lesson.tentative() && lesson.current(now)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.widthIn(min = 56.dp)) {
                            Text(lesson.start.format(clock), style = MaterialTheme.typography.titleMedium)
                            Text(lesson.end.format(clock), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(lesson.record.title, style = MaterialTheme.typography.titleSmall)
                            val room = lesson.record.optMeta("room")
                            if (room.isNotEmpty()) Text(tr("Sala ", "Room ", "Аудиторія ") + room, style = MaterialTheme.typography.bodyMedium)
                            if (current) Text(strings.heroOngoingClass, color = MaterialTheme.colorScheme.primary)
                            if (lesson.tentative()) Text(tr("Termin orientacyjny", "Tentative time", "Орієнтовний час"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedButton(onClick = { onOpenScheduleDate(day) }) { Text(strings.btnOpenSchedule) }
            }
        }
        val deadlines: @Composable () -> Unit = {
            DashboardPanel(tr("Zadania i terminy", "Tasks and deadlines", "Завдання та терміни")) {
                if (tasks.isEmpty()) Text(tr("Nie masz otwartych zadań. Dodaj własny termin.", "No open tasks. Add your own deadline.", "Немає відкритих завдань. Додайте свій термін."))
                tasks.take(3).forEach { task ->
                    val due = TaskTools.dueDate(task)
                    TextButton(onClick = { onNavigateTool("tasks") }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(task.optString("title"), style = MaterialTheme.typography.titleSmall)
                            Text(when {
                                due == null -> tr("Bez terminu", "No deadline", "Без терміну")
                                due.isBefore(today) -> tr("Po terminie · ", "Overdue · ", "Прострочено · ") + due
                                due == today -> tr("Na dzisiaj", "Due today", "На сьогодні")
                                else -> due.toString()
                            }, color = if (due != null && due.isBefore(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedButton(onClick = { onNavigateTool("tasks") }) { Text(strings.sectionTasks) }
            }
        }
        if (isWide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                agenda()
                RecentGradesCard(grades.take(4), snapshot, strings) { onNavigateTab(VistulaTab.GRADES) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                deadlines()
                NewsListCard(snapshot?.news?.take(3) ?: emptyList(), strings, onOpenUrl)
            }
        } else {
            agenda(); deadlines()
            RecentGradesCard(grades.take(3), snapshot, strings) { onNavigateTab(VistulaTab.GRADES) }
            NewsListCard(snapshot?.news?.take(3) ?: emptyList(), strings, onOpenUrl)
        }
    }
}

@Composable
private fun DashboardPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}
