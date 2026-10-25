package pl.vistula.checker.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.*

/**
 * Today, as in the design prototype: one hero (the next class), what is left of that day, the
 * latest changes and the week as a quiet strip. Phone = one column; wide = hero in the middle,
 * the two lists as wings either side and the week under the hero.
 */
@Composable
internal fun StudentDashboard(
    state: JSONObject, snapshot: Snapshot?, strings: UiStrings, isWide: Boolean,
    onNavigateTab: (VistulaTab) -> Unit, onNavigateTool: (String) -> Unit, onOpenScheduleDate: (LocalDate) -> Unit,
    onEnableDemo: () -> Unit, onOpenUrl: (String) -> Unit
) {
    val t = VistulaDesign.tokens
    val language = strings.languageCode
    val locale = Locale.forLanguageTag(language)
    fun tr(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = Instant.now() } }
    val today = now.atZone(UsosClient.ZONE).toLocalDate()
    val records = snapshot?.records?.values ?: emptyList()
    val lessons = LessonTime.sorted(records)
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val dayShort = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    val limit = if (isWide) 5 else 3

    val current = lessons.firstOrNull { !it.tentative() && it.current(now) }
    val next = current ?: lessons.firstOrNull { it.start.toInstant().isAfter(now) }
    val heroDate = next?.start?.toLocalDate() ?: today
    val laterThatDay = if (next == null) emptyList() else lessons.filter {
        it.intersects(heroDate) && it.start.toInstant().isAfter(next.start.toInstant())
    }
    val history = state.optJSONArray("history") ?: JSONArray()
    val recent = (0 until history.length()).mapNotNull { runCatching { Change.from(history.getJSONObject(it)) }.getOrNull() }
        .sortedByDescending { it.effectiveTimestamp() }

    fun roomOf(l: LessonTime) = l.record.optMeta("room").let { if (it.isEmpty()) "" else tr("Sala ", "Room ", "Аудиторія ") + it }
    fun facts(l: LessonTime, withDate: Boolean): String = listOfNotNull(
        if (withDate) l.start.format(dayShort) else null,
        l.start.format(clock) + "–" + l.end.format(clock),
        roomOf(l).ifEmpty { null }
    ).joinToString(" · ")

    val hero: @Composable () -> Unit = {
        val label = if (current != null) strings.heroOngoingClass else tr("Najbliższe zajęcia", "Next class", "Наступне заняття")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button) { onOpenScheduleDate(heroDate) }
                .padding(top = 32.dp, bottom = if (isWide) 32.dp else 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.width(28.dp).height(2.dp).background(t.accentMark))
            Text(label.uppercase(locale), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelMedium, color = t.muted)
            if (next == null) {
                Text(tr("Brak zaplanowanych zajęć", "None planned yet", "Поки нічого не заплановано"),
                    Modifier.padding(top = 10.dp).semantics { heading() }, fontSize = if (isWide) 30.sp else 26.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, textAlign = TextAlign.Center, color = t.text)
                Text(if (snapshot == null) tr("Odśwież dane konta lub uruchom demo.", "Refresh your account or try demo.", "Оновіть дані або спробуйте демо.")
                    else tr("Sprawdzono zapisany plan", "Checked the saved plan", "Перевірено збережений план"),
                    Modifier.padding(top = 12.dp), fontSize = 15.sp, color = t.muted, textAlign = TextAlign.Center)
            } else {
                Text(next.record.title, Modifier.padding(top = 10.dp).padding(horizontal = 8.dp).semantics { heading() },
                    fontSize = if (isWide) 40.sp else 34.sp, lineHeight = if (isWide) 44.sp else 37.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, textAlign = TextAlign.Center, color = t.text)
                Text(facts(next, next.start.toLocalDate() != today), Modifier.padding(top = 12.dp), fontSize = 15.sp,
                    letterSpacing = 0.3.sp, textAlign = TextAlign.Center, color = t.text)
                val minutes = maxOf(1L, (Duration.between(now, if (current != null) next.end.toInstant() else next.start.toInstant()).seconds + 59) / 60)
                fun span(m: Long) = if (m < 60) "$m min" else "${m / 60} h" + if (m % 60 != 0L) " ${m % 60} min" else ""
                val days = ChronoUnit.DAYS.between(today, next.start.toLocalDate())
                val eta = when {
                    next.tentative() -> tr("Termin orientacyjny", "Tentative time", "Орієнтовний час")
                    current != null -> tr("Do końca: ${span(minutes)}", "Ends in ${span(minutes)}", "До кінця: ${span(minutes)}")
                    days == 0L -> tr("Początek za ${span(minutes)}", "Starts in ${span(minutes)}", "Початок за ${span(minutes)}")
                    days == 1L -> tr("Jutro", "Tomorrow", "Завтра")
                    else -> tr("Za $days dni", "In $days days", "Через $days дн.")
                }
                Text(eta, Modifier.padding(top = 16.dp).clip(RoundedCornerShape(percent = 50)).background(t.accentSoft)
                    .padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 12.sp, color = t.accent)
            }
        }
    }

    val later: @Composable () -> Unit = {
        val title = if (heroDate == today) tr("Dzisiaj później", "Later today", "Пізніше сьогодні")
        else tr("Później tego dnia", "Later that day", "Пізніше цього дня")
        if (next != null && laterThatDay.isNotEmpty()) {
            val shown = if (laterThatDay.size <= limit + 1) laterThatDay else laterThatDay.take(limit)
            val rest = laterThatDay.size - shown.size
            SectionHead(title, { onOpenScheduleDate(heroDate) })
            Tray {
                shown.forEachIndexed { i, l ->
                    if (i > 0) TrayDivider()
                    ItemRow(l.record.title, facts(l, false), onClick = { onOpenScheduleDate(heroDate) })
                }
                if (rest > 0) {
                    TrayDivider()
                    Text(tr("+$rest więcej", "+$rest more", "+$rest ще") + if (heroDate == today) tr(" dzisiaj", " today", " сьогодні") else "",
                        Modifier.fillMaxWidth().clickable { onOpenScheduleDate(heroDate) }.padding(horizontal = 16.dp, vertical = 13.dp),
                        fontSize = 13.sp, color = t.muted)
                }
            }
        } else if (isWide) {
            SectionHead(if (next == null) tr("Dzisiaj", "Today", "Сьогодні") else title, { onOpenScheduleDate(heroDate) })
            QuietLine(if (next == null) tr("Brak zajęć dzisiaj", "No classes today", "Сьогодні немає занять")
                else tr("Nic więcej tego dnia", "Nothing else that day", "Більше нічого цього дня"))
        }
    }

    val changes: @Composable () -> Unit = {
        SectionHead(tr("Zmiany", "Changes", "Зміни"), { onNavigateTab(VistulaTab.HISTORY) })
        if (recent.isEmpty()) QuietLine(tr("Brak zapisanych zmian", "No saved changes", "Немає збережених змін"))
        else {
            val shown = if (recent.size <= limit + 1) recent else recent.take(limit)
            val rest = recent.size - shown.size
            Tray {
                shown.forEachIndexed { i, c ->
                    if (i > 0) TrayDivider()
                    ChangeRow(c, language) { onNavigateTab(VistulaTab.HISTORY) }
                }
                if (rest > 0) {
                    TrayDivider()
                    Text(tr("+$rest więcej zmian", "+$rest more changes", "+$rest змін ще"),
                        Modifier.fillMaxWidth().clickable { onNavigateTab(VistulaTab.HISTORY) }.padding(horizontal = 16.dp, vertical = 13.dp),
                        fontSize = 13.sp, color = t.muted)
                }
            }
        }
    }

    val week: @Composable () -> Unit = {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val month = DateTimeFormatter.ofPattern("MMM", locale)
        val range = if (monday.month == sunday.month) "${monday.dayOfMonth}–${sunday.dayOfMonth} ${sunday.format(month)}"
        else "${monday.dayOfMonth} ${monday.format(month)} – ${sunday.dayOfMonth} ${sunday.format(month)}"
        SectionHead(tr("Ten tydzień", "This week", "Цей тиждень") + " · " + range, { onOpenScheduleDate(today) })
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            repeat(7) { index ->
                val date = monday.plusDays(index.toLong())
                val count = lessons.count { it.intersects(date) && !it.tentative() }
                DayCell(
                    dow = date.format(DateTimeFormatter.ofPattern("EEE", locale)).trimEnd('.'), date = date.dayOfMonth, count = count,
                    today = date == today, selected = false, onClick = { onOpenScheduleDate(date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = if (isWide) 0.dp else 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (isWide) 8.dp else 32.dp)
    ) {
        if (state.optString("token").isEmpty()) {
            OnboardingWelcomeCard(strings, onEnableDemo) { onNavigateTool("account") }
        }
        if (isWide) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
                Column(Modifier.width(300.dp).padding(top = 38.dp)) { later() }
                Column(Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    hero()
                    Column(Modifier.width(360.dp)) { week() }
                }
                Column(Modifier.width(300.dp).padding(top = 38.dp)) { changes() }
            }
        } else {
            Column { hero() }
            Column { later() }
            Column { changes() }
            Column { week() }
        }
    }
}

/** A change as a tray row: subject, what kind of change, and the new value on the right. */
@Composable
internal fun ChangeRow(change: Change, language: String, onClick: (() -> Unit)? = null) {
    val t = VistulaDesign.tokens
    fun tr(pl: String, en: String, uk: String) = toolText(language, pl, en, uk)
    val diff = listOf("diffRoom", "diffTime", "diffGrade", "diffLecturers", "diffDate").map { it to change.optMeta(it) }
        .firstOrNull { it.second.isNotEmpty() }
    val kind = when (diff?.first) {
        "diffRoom" -> tr("Zmiana sali", "Room changed", "Зміна аудиторії")
        "diffTime" -> tr("Zmiana godziny", "Time changed", "Зміна часу")
        "diffGrade" -> tr("Zmiana oceny", "Grade changed", "Зміна оцінки")
        "diffLecturers" -> tr("Zmiana prowadzącego", "Teacher changed", "Зміна викладача")
        "diffDate" -> tr("Zmiana terminu", "Date changed", "Зміна дати")
        else -> if (change.kind == "ocena") tr("Nowa ocena", "New grade", "Нова оцінка") else when (change.action) {
            "Dodano" -> tr("Dodano", "Added", "Додано"); "Usunięto" -> tr("Odwołane", "Cancelled", "Скасовано")
            else -> tr("Zmieniono", "Changed", "Змінено")
        }
    }
    val value = diff?.second ?: if (change.kind == "ocena") change.after.split(" · ").firstOrNull().orEmpty() else ""
    ItemRow(change.title, kind, onClick = onClick) {
        if (value.isNotEmpty()) {
            val parts = value.split(" → ")
            if (parts.size == 2) {
                Row {
                    Text(parts[0], fontSize = 15.sp, color = t.muted, maxLines = 1)
                    Text(" → ", fontSize = 15.sp, color = t.quiet)
                    Text(parts[1], fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.text, maxLines = 1)
                }
            } else Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.text, maxLines = 1)
        }
    }
}
