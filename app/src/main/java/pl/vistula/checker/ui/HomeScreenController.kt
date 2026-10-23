package pl.vistula.checker.ui

import android.graphics.Color
import android.widget.LinearLayout
import android.view.Gravity
import android.view.View
import android.graphics.drawable.GradientDrawable
import androidx.core.view.ViewCompat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.ScheduleConflictDetector
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.TaskTools

/**
 * Controller for rendering the Home dashboard screen.
 */
class HomeScreenController private constructor() {

    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject,
            state: JSONObject,
            demo: Boolean,
            demoTasks: JSONArray?,
            onLessonDetails: Consumer<LessonTime>
        ) {
            renderFocus(activity, content, d, onLessonDetails)
            renderRecentChanges(activity, content, d)
            renderWeek(activity, content, d)
            renderDeadlinesPreview(activity, content, if (demo) demoTasks else state.optJSONArray("tasks"))
            sectionLink(activity, content, R.string.design_shortcuts) { activity.navigate(6) }
            val shortcuts = arrayOf(
                Triple(R.id.home_open_schedule, R.string.dashboard_plan, 5),
                Triple(R.id.home_open_grades, R.string.dashboard_grades, 2),
                Triple(R.id.home_open_tasks, R.string.dashboard_tasks, 7),
                Triple(R.id.home_open_news, R.string.dashboard_news, 11)
            )
            for (pair in shortcuts.toList().chunked(2)) {
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEachIndexed { index, (id, title, destination) ->
                    val button = UiHelper.button(activity, activity.getString(title), {
                        if (destination == 5) {
                            activity.setCalendarQuery("")
                            activity.setCalendarMode("day")
                            activity.openScheduleDate(LocalDate.now(UsosClient.ZONE).toString())
                        } else activity.navigate(destination)
                    }, false).apply { this.id = id }
                    row.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index > 0) marginStart = UiHelper.dp(activity, 8)
                    })
                }
                content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiHelper.dp(activity, 4) })
            }

            // Payments alert, schedule conflict, latest news
            val rawSnap = d.optJSONObject("snapshot")
            if (rawSnap != null) {
                val snap = Snapshot.from(rawSnap)
                val payments = snap.payments
                if (payments != null) {
                    val unpaid = payments.totalUnpaidAmount()
                    if (unpaid > 0 || payments.hasOverdue()) {
                        val payBox = UiHelper.card(
                            activity,
                            content,
                            if (payments.hasOverdue()) Color.rgb(255, 240, 240) else Color.WHITE
                        )
                        UiHelper.add(
                            activity,
                            payBox,
                            if (payments.hasOverdue()) {
                                activity.getString(R.string.home_payments_attention)
                            } else {
                                activity.getString(R.string.home_payments_regular)
                            },
                            11,
                            if (payments.hasOverdue()) Color.rgb(160, 48, 40) else UiHelper.GREEN,
                            true
                        )
                        UiHelper.add(activity, payBox, activity.getString(R.string.home_payments_due,
                            pl.vistula.checker.core.PaymentAmounts.unpaid(payments, Language.currentLocale(activity).language)), 19, UiHelper.INK, true)
                        if (payments.accounts.isNotEmpty()) {
                            UiHelper.add(
                                activity,
                                payBox,
                                activity.getString(
                                    R.string.home_payments_subaccount,
                                    PaymentsScreenController.formatIban(payments.accounts[0].bankAccountNumber)
                                ),
                                13,
                                UiHelper.MUTED,
                                false
                            )
                        }
                        payBox.isClickable = true
                        payBox.isFocusable = true
                        payBox.setOnClickListener { activity.navigate(8) }
                        payBox.addView(UiHelper.button(activity, activity.getString(R.string.home_payments_btn), { activity.navigate(8) }, false))
                    }
                }

                val conflicts = ScheduleConflictDetector.findConflicts(snap.records.values)
                if (conflicts.isNotEmpty()) {
                    val conflictBox = UiHelper.card(activity, content, Color.rgb(255, 243, 224))
                    UiHelper.add(
                        activity,
                        conflictBox,
                        activity.getString(R.string.home_conflicts_title, conflicts.size),
                        11,
                        Color.rgb(217, 119, 6),
                        true
                    )
                    UiHelper.add(activity, conflictBox, activity.getString(R.string.home_conflicts_heading), 17, UiHelper.INK, true)
                    UiHelper.add(activity, conflictBox, conflicts[0].description(), 13, UiHelper.INK, false)
                    if (conflicts.size > 1) {
                        UiHelper.note(
                            activity,
                            conflictBox,
                            activity.getString(R.string.home_conflicts_more, conflicts.size - 1),
                            UiHelper.MUTED
                        )
                    }
                    conflictBox.addView(
                        UiHelper.button(
                            activity,
                            activity.getString(R.string.home_conflicts_btn),
                            {
                                activity.setCalendarMode("day")
                                activity.openScheduleDate(conflicts[0].first.date)
                            },
                            false
                        )
                    )
                }

                if (snap.news != null && snap.news.isNotEmpty()) {
                    val top = snap.news[0]
                    val newsBox = UiHelper.card(activity, content, Color.WHITE)
                    UiHelper.add(activity, newsBox, activity.getString(R.string.home_news_title), 11, UiHelper.MUTED, true)
                    UiHelper.add(activity, newsBox, top.title, 17, UiHelper.INK, true)
                    if (top.headline.isNotEmpty()) {
                        UiHelper.add(activity, newsBox, top.headline, 13, UiHelper.MUTED, false)
                    }
                    val formattedPubDate = HistoryScreenController.formatFullDate(top.publicationDate)
                    UiHelper.add(
                        activity,
                        newsBox,
                        (if (formattedPubDate.isEmpty()) top.publicationDate else formattedPubDate) +
                            if (top.author.isNotEmpty()) " · ${top.author}" else "",
                        12,
                        UiHelper.MUTED,
                        false
                    )
                    newsBox.isClickable = true
                    newsBox.isFocusable = true
                    newsBox.setOnClickListener { activity.navigate(11) }
                    newsBox.addView(
                        UiHelper.button(
                            activity,
                            activity.getString(R.string.home_news_all_btn, snap.news.size),
                            { activity.navigate(11) },
                            false
                        )
                    )
                }
            }

        }

        private fun sectionLink(activity: MainActivity, parent: LinearLayout, title: Int, action: () -> Unit) {
            val label = UiHelper.actionRow(activity, activity.getString(title).uppercase(Language.currentLocale(activity)) + "  ›", { action() }, false)
            label.textSize = 11f
            label.letterSpacing = .12f
            label.setTextColor(activity.getColor(R.color.app_muted))
            label.setPadding(0, UiHelper.dp(activity, 12), 0, 0)
            ViewCompat.setAccessibilityHeading(label, true)
            parent.addView(label)
        }

        private fun openDay(activity: MainActivity, day: LocalDate) {
            activity.setCalendarQuery("")
            activity.setCalendarMode("day")
            activity.openScheduleDate(day.toString())
        }

        private fun renderFocus(activity: MainActivity, parent: LinearLayout, data: JSONObject, details: Consumer<LessonTime>) {
            val snapshot = data.optJSONObject("snapshot")?.let { Snapshot.from(it) }
            val now = Instant.now()
            val today = LocalDate.now(UsosClient.ZONE)
            val lessons = LessonTime.sorted(snapshot?.records?.values ?: emptyList())
            val current = lessons.filter { !it.tentative() && it.current(now) }
            val focus = current.firstOrNull() ?: lessons.firstOrNull { it.start.toInstant().isAfter(now) }
            val updated = UiHelper.text(activity,
                activity.getString(R.string.payments_data_freshness, UiHelper.timestamp(data.optLong("successAt"))), 12, UiHelper.MUTED, false)
            updated.gravity = Gravity.CENTER
            parent.addView(updated, LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiHelper.dp(activity, 12) })

            val hero = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, UiHelper.dp(activity, 32), 0, UiHelper.dp(activity, 32))
            }
            parent.addView(hero, LinearLayout.LayoutParams(-1, -2))
            hero.addView(View(activity).apply { setBackgroundColor(activity.getColor(R.color.app_accent)) },
                LinearLayout.LayoutParams(UiHelper.dp(activity, 28), UiHelper.dp(activity, 2)).apply { bottomMargin = UiHelper.dp(activity, 16) })
            fun line(value: String, size: Int, color: Int, bold: Boolean) = UiHelper.text(activity, value, size, color, bold).also {
                it.gravity = Gravity.CENTER
                hero.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiHelper.dp(activity, 12) })
            }
            val caption = line(activity.getString(if (current.isNotEmpty()) R.string.classes_ongoing else R.string.design_next_class), 11, UiHelper.MUTED, false)
            caption.isAllCaps = true
            caption.letterSpacing = .16f
            val title = line(focus?.record?.title ?: activity.getString(if (snapshot == null) R.string.dashboard_no_data else R.string.classes_no_upcoming), 32, UiHelper.INK, true)
            ViewCompat.setAccessibilityHeading(title, true)
            if (focus != null) {
                val clock = DateTimeFormatter.ofPattern("HH:mm")
                val room = room(focus.record)
                val date = if (focus.start.toLocalDate() == today) "" else UiHelper.shortDate(focus.start.toLocalDate()) + " · "
                line(date + focus.start.format(clock) + "–" + focus.end.format(clock) +
                    if (room.isEmpty()) "" else " · " + activity.getString(R.string.lesson_room_label, room), 15, UiHelper.INK, false)
                val status = when {
                    focus.tentative() -> activity.getString(R.string.design_tentative)
                    current.isNotEmpty() -> activity.getString(R.string.home_classes_remaining_min, max(1L, (Duration.between(now, focus.end.toInstant()).seconds + 59) / 60))
                    focus.start.toLocalDate() == today -> activity.getString(R.string.design_starts_minutes, max(1L, (Duration.between(now, focus.start.toInstant()).seconds + 59) / 60))
                    else -> focus.start.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Language.currentLocale(activity)))
                }
                val badge = UiHelper.text(activity, status, 12, UiHelper.INK, false).apply {
                    setTextColor(activity.getColor(R.color.app_accent))
                    gravity = Gravity.CENTER
                    setPadding(UiHelper.dp(activity, 14), UiHelper.dp(activity, 8), UiHelper.dp(activity, 14), UiHelper.dp(activity, 8))
                    background = GradientDrawable().apply {
                        setColor(activity.getColor(R.color.app_accent_container))
                        cornerRadius = UiHelper.dp(activity, 24).toFloat()
                    }
                }
                hero.addView(badge, LinearLayout.LayoutParams(-2, -2))
                hero.isClickable = true
                hero.isFocusable = true
                hero.setOnClickListener { details.accept(focus) }
            } else {
                line(activity.getString(if (snapshot == null) R.string.refresh_offline else R.string.home_schedule_empty_hint), 14, UiHelper.MUTED, false)
                hero.addView(UiHelper.button(activity, activity.getString(if (snapshot == null) R.string.btn_connect else R.string.btn_open_plan), {
                    if (snapshot == null) activity.navigate(4) else openDay(activity, today)
                }, false))
            }
            // Concurrent classes and tentative times stay visible, without a false countdown.
            val rest = lessons.filter { it !== focus && it.intersects(today) && it.end.toInstant().isAfter(now) }
            if (rest.isNotEmpty()) {
                sectionLink(activity, parent, R.string.design_later_today) { openDay(activity, today) }
                val box = UiHelper.card(activity, parent, Color.WHITE)
                rest.forEachIndexed { index, lesson ->
                    if (index > 0) divider(activity, box)
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        minimumHeight = UiHelper.dp(activity, 64)
                        setPadding(0, UiHelper.dp(activity, 12), 0, UiHelper.dp(activity, 4))
                        isClickable = true; isFocusable = true
                        setOnClickListener { details.accept(lesson) }
                    }
                    UiHelper.add(activity, row, lesson.record.title, 16, UiHelper.INK, true)
                    val clock = DateTimeFormatter.ofPattern("HH:mm")
                    val room = room(lesson.record)
                    UiHelper.add(activity, row, lesson.start.format(clock) + "–" + lesson.end.format(clock) +
                        (if (room.isEmpty()) "" else " · " + activity.getString(R.string.lesson_room_label, room)) +
                        (if (lesson.tentative()) " · " + activity.getString(R.string.design_tentative) else ""), 13, UiHelper.MUTED, false)
                    box.addView(row)
                }
            }
            val upcoming = lessons.firstOrNull { it.start.toInstant().isAfter(now) }
            if (current.isNotEmpty() && upcoming != null && !upcoming.intersects(today)) {
                sectionLink(activity, parent, R.string.design_next_class) { openDay(activity, upcoming.start.toLocalDate()) }
                val nextBox = UiHelper.card(activity, parent, Color.WHITE)
                nextBox.addView(UiHelper.actionRow(activity, upcoming.record.title, { details.accept(upcoming) }, false))
                UiHelper.note(activity, nextBox, UiHelper.shortDate(upcoming.start.toLocalDate()) + " · " +
                    upcoming.start.format(DateTimeFormatter.ofPattern("HH:mm")) +
                    if (upcoming.tentative()) " · " + activity.getString(R.string.design_tentative) else "", UiHelper.MUTED)
            }
        }

        private fun divider(activity: MainActivity, parent: LinearLayout) {
            parent.addView(View(activity).apply { setBackgroundColor(activity.getColor(R.color.app_outline_inner)) },
                LinearLayout.LayoutParams(-1, UiHelper.dp(activity, 1)))
        }

        private fun room(record: Record): String = record.optMeta("room").trim().ifEmpty {
            record.value.lineSequence().firstOrNull { it.startsWith("Sala ") }?.substring(5)?.trim().orEmpty()
        }

        private fun renderWeek(activity: MainActivity, parent: LinearLayout, data: JSONObject) {
            val snapshot = data.optJSONObject("snapshot")?.let { Snapshot.from(it) } ?: return
            val today = LocalDate.now(UsosClient.ZONE)
            val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val lessons = LessonTime.sorted(snapshot.records.values)
            sectionLink(activity, parent, R.string.design_this_week) { openDay(activity, today) }
            // Horizontal scrolling retains 48dp targets at narrow widths and large font scales.
            val scroll = android.widget.HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
            val week = LinearLayout(activity)
            scroll.addView(week)
            parent.addView(scroll, LinearLayout.LayoutParams(-1, -2))
            repeat(7) { index ->
                val day = monday.plusDays(index.toLong())
                val count = lessons.count { it.intersects(day) }
                val button = UiHelper.button(activity,
                    day.format(DateTimeFormatter.ofPattern("EEE", Language.currentLocale(activity))).uppercase(Language.currentLocale(activity)) +
                        "\n" + day.dayOfMonth + "\n" + if (count == 0) "—" else "•".repeat(min(4, count)), { openDay(activity, day) }, false)
                button.textSize = 12f
                button.setPadding(0, UiHelper.dp(activity, 8), 0, UiHelper.dp(activity, 8))
                button.contentDescription = day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Language.currentLocale(activity))) +
                    ": " + activity.getString(R.string.design_class_count, count)
                button.backgroundTintList = android.content.res.ColorStateList.valueOf(activity.getColor(
                    if (day == today) R.color.app_secondary_container else R.color.app_background))
                val width = max(52, (52 * activity.resources.configuration.fontScale).toInt())
                week.addView(button, LinearLayout.LayoutParams(UiHelper.dp(activity, width), -2).apply { marginEnd = UiHelper.dp(activity, 3) })
            }
        }

        @Throws(JSONException::class)
        private fun renderDeadlinesPreview(activity: MainActivity, content: LinearLayout, rows: JSONArray?) {
            if (rows == null) return
            val open = ArrayList<JSONObject>()
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                if (!row.optBoolean("done")) {
                    open.add(row)
                }
            }

            if (open.isNotEmpty()) {
                open.sortWith(compareBy { it.optString("due") })
                UiHelper.heading(activity, content, activity.getString(R.string.deadlines_upcoming))
                val box = UiHelper.card(activity, content, Color.WHITE)
                val today = LocalDate.now(UsosClient.ZONE)

                for (i in 0 until min(3, open.size)) {
                    val t = open[i]
                    val due = TaskTools.dueDate(t)
                    UiHelper.add(activity, box, t.optString("title"), 16, UiHelper.INK, true)
                    if (due == null) {
                        UiHelper.note(activity, box, activity.getString(R.string.tasks_due_unknown), UiHelper.MUTED)
                        continue
                    }
                    val prefix = when {
                        due.isBefore(today) -> "${activity.getString(R.string.tasks_chip_overdue)} · "
                        due == today -> "${activity.getString(R.string.tasks_chip_today)} · "
                        else -> ""
                    }
                    UiHelper.add(
                        activity,
                        box,
                        prefix + due.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        13,
                        if (due.isBefore(today)) Color.rgb(160, 48, 40) else UiHelper.MUTED,
                        false
                    )
                }

                box.isClickable = true
                box.isFocusable = true
                box.setOnClickListener { activity.navigate(7) }
                box.addView(UiHelper.button(activity, activity.getString(R.string.home_open_tasks), { activity.navigate(7) }, false))
            }
        }

        @Throws(Exception::class)
        private fun renderRecentChanges(activity: MainActivity, content: LinearLayout, d: JSONObject) {
            val rows = d.optJSONArray("history")
            if (rows != null && rows.length() != 0) {
                sectionLink(activity, content, R.string.home_recent_changes) { activity.navigate(1) }
                val recent = ArrayList<Change>()
                for (i in 0 until rows.length()) {
                    recent.add(Change.from(rows.getJSONObject(i)))
                }
                recent.sortWith { a, b -> b.effectiveTimestamp().compareTo(a.effectiveTimestamp()) }
                val box = UiHelper.card(activity, content, Color.WHITE)
                recent.take(2).forEachIndexed { index, change ->
                    if (index > 0) divider(activity, box)
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        minimumHeight = UiHelper.dp(activity, 64)
                        setPadding(0, UiHelper.dp(activity, 12), 0, 0)
                        isClickable = true; isFocusable = true
                        setOnClickListener { HistoryScreenController.showChangeDetails(activity, change) }
                    }
                    UiHelper.add(activity, row, change.title, 16, UiHelper.INK, true)
                    val summary = HistoryScreenController.getDiffSummaryList(activity, change).joinToString(" · ")
                    UiHelper.add(activity, row, summary.ifEmpty { HistoryScreenController.eventDateDescription(activity, change) }, 13, UiHelper.MUTED, false)
                    box.addView(row)
                }
            }
        }
    }
}

