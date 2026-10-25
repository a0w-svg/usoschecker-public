package pl.vistula.checker.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val PillShape = RoundedCornerShape(percent = 50)

/** Small uppercase label used for section titles; optional chevron makes the whole header a button. */
@Composable
internal fun SectionHead(title: String, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val t = VistulaDesign.tokens
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = if (onClick != null) 48.dp else 32.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 4.dp).semantics { heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelMedium, color = t.muted,
            modifier = Modifier.weight(1f, fill = false))
        if (onClick != null) Text("›", fontSize = 18.sp, color = t.quiet)
    }
}

/** One quiet tray for every secondary group: rounded 14dp, rows separated by hairlines. */
@Composable
internal fun Tray(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = VistulaDesign.tokens
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier.fillMaxWidth().clip(shape)
            .background(t.surface)
            .let { if (t.isDark) it else it.border(BorderStroke(1.dp, t.line), shape) },
        content = content
    )
}

@Composable
internal fun TrayDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = VistulaDesign.tokens.line)
}

/** Row grammar: subject on top, muted line under it, optional value on the right. */
@Composable
internal fun ItemRow(
    title: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    strike: Boolean = false,
    onClick: (() -> Unit)? = null,
    end: (@Composable RowScope.() -> Unit)? = null
) {
    val t = VistulaDesign.tokens
    Row(
        modifier = modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp,
                color = if (strike) t.muted else t.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (strike) TextDecoration.LineThrough else null)
            if (!sub.isNullOrEmpty()) Text(sub, fontSize = 13.sp, lineHeight = 18.sp, color = t.muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        end?.invoke(this)
    }
}

/** Quiet single line shown instead of an empty tray. */
@Composable
internal fun QuietLine(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(horizontal = 4.dp), fontSize = 13.sp, color = VistulaDesign.tokens.muted)
}

/** Pill segmented control (Changes filter). */
@Composable
internal fun PillSegment(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = VistulaDesign.tokens
    Row(
        modifier = modifier.fillMaxWidth().clip(PillShape).background(t.surface)
            .let { if (t.isDark) it else it.border(BorderStroke(1.dp, t.line), PillShape) }.selectableGroup().padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (id, label) ->
            val on = id == selected
            Box(
                Modifier.weight(1f).clip(PillShape).background(if (on) t.fill else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSelect(id) }.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 13.sp, color = if (on) t.text else t.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Round 44dp icon-like button. */
@Composable
internal fun RoundButton(onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null, content: @Composable () -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { if (label != null) contentDescription = label },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** The floating pill menu: text only, current page on a soft fill. */
@Composable
internal fun PillNav(tabs: List<Pair<VistulaTab, String>>, current: VistulaTab, onSelect: (VistulaTab) -> Unit, modifier: Modifier = Modifier) {
    val t = VistulaDesign.tokens
    Row(
        modifier = modifier.clip(PillShape).background(t.navBg).border(BorderStroke(1.dp, t.line), PillShape)
            .selectableGroup().padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (tab, label) ->
            val on = tab == current
            Box(
                Modifier.weight(1f).clip(PillShape).background(if (on) t.fill else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSelect(tab) }
                    .heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 12.sp, letterSpacing = 0.02.sp, color = if (on) t.text else t.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A day in the week strip: weekday, date circle (filled when today/selected) and up to 4 dots. */
@Composable
internal fun DayCell(
    dow: String, date: Int, count: Int, today: Boolean, selected: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false, description: String? = null
) {
    val t = VistulaDesign.tokens
    val on = today || selected
    val circle = if (large) 38.dp else 34.dp
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected; if (description != null) contentDescription = description }
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(dow.uppercase(Locale.getDefault()), fontSize = 11.sp, letterSpacing = 0.88.sp, color = if (today) t.text else t.quiet)
        Box(
            Modifier.padding(top = 4.dp).size(circle).clip(CircleShape).background(if (on) t.fill else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(date.toString(), fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = if (on) t.text else if (count == 0) t.quiet else t.muted)
        }
        Row(Modifier.padding(top = 4.dp).height(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(minOf(count, 4)) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) t.text else t.quiet))
            }
        }
    }
}

