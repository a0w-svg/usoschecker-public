package pl.vistula.checker.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
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
import pl.vistula.checker.core.PaymentAmounts
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.ScheduleConflictDetector
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.TaskTools

/**
 * Controller for the Home dashboard. Each block is inflated from XML (`view_home_*`, `item_home_*`);
 * this class decides what to show and binds the data.
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
            val snapshot = d.optJSONObject("snapshot")?.let { Snapshot.from(it) }
            val lessons = LessonTime.sorted(snapshot?.records?.values ?: emptyList())
            renderFocus(activity, content, d, snapshot, lessons, onLessonDetails)
            renderRecentChanges(activity, content, d)
            renderWeek(activity, content, snapshot, lessons)
            renderDeadlinesPreview(activity, content, if (demo) demoTasks else state.optJSONArray("tasks"))
            sectionLink(activity, content, R.string.design_shortcuts) { activity.navigate(Tabs.MORE) }
            renderShortcuts(activity, content)
            snapshot ?: return
            renderPaymentsAlert(activity, content, snapshot)
            renderConflictAlert(activity, content, snapshot)
            renderNewsAlert(activity, content, snapshot)
        }

        private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
        private val DUE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        private fun inflate(activity: MainActivity, layout: Int, parent: LinearLayout): View =
            UiHelper.inflate(activity, layout, parent)

        private fun sectionLink(activity: MainActivity, parent: LinearLayout, title: Int, action: () -> Unit) {
            val link = inflate(activity, R.layout.view_section_link, parent) as MaterialButton
            link.text = activity.getString(R.string.home_section_link, activity.getString(title))
            link.setOnClickListener { action() }
            parent.addView(link)
        }

        private fun openDay(activity: MainActivity, day: LocalDate) {
            activity.setCalendarQuery("")
            activity.setCalendarMode("day")
            activity.openScheduleDate(day.toString())
        }

        private fun renderShortcuts(activity: MainActivity, content: LinearLayout) {
            val view = inflate(activity, R.layout.view_home_shortcuts, content)
            view.findViewById<View>(R.id.home_open_schedule).setOnClickListener { openDay(activity, LocalDate.now(UsosClient.ZONE)) }
            view.findViewById<View>(R.id.home_open_grades).setOnClickListener { activity.navigate(Tabs.GRADES) }
            view.findViewById<View>(R.id.home_open_tasks).setOnClickListener { activity.navigate(Tabs.TASKS) }
            view.findViewById<View>(R.id.home_open_news).setOnClickListener { activity.navigate(Tabs.NEWS) }
            content.addView(view)
        }

        private fun tint(activity: MainActivity, view: View, color: Int) {
            view.backgroundTintList = ColorStateList.valueOf(activity.getColor(color))
        }

        private fun alert(
            activity: MainActivity, content: LinearLayout, eyebrow: String, eyebrowColor: Int, title: String,
            body: String, note: String, button: String, destination: () -> Unit
        ): View {
            val card = inflate(activity, R.layout.view_home_alert, content)
            card.findViewById<TextView>(R.id.alert_eyebrow).apply {
                text = eyebrow
                setTextColor(activity.getColor(eyebrowColor))
            }
            card.findViewById<TextView>(R.id.alert_title).text = title
            card.findViewById<TextView>(R.id.alert_body).showIfNotEmpty(body)
            card.findViewById<TextView>(R.id.alert_note).showIfNotEmpty(note)
            card.findViewById<MaterialButton>(R.id.alert_button).apply {
                text = button
                setOnClickListener { destination() }
            }
            card.setOnClickListener { destination() }
            content.addView(card)
            return card
        }

        private fun renderPaymentsAlert(activity: MainActivity, content: LinearLayout, snapshot: Snapshot) {
            val payments = snapshot.payments ?: return
            val overdue = payments.hasOverdue()
            if (!overdue && (payments.hasUnknownSettlement() || payments.totalUnpaidAmount() <= 0)) return
            val title = if (payments.hasUnknownSettlement()) activity.getString(R.string.payments_settlement_unknown)
                else activity.getString(R.string.home_payments_due, PaymentAmounts.unpaid(payments, Language.currentLocale(activity).language))
            val subaccount = payments.accounts.firstOrNull()?.let {
                activity.getString(R.string.home_payments_subaccount, PaymentsScreenController.formatIban(it.bankAccountNumber))
            }.orEmpty()
            val card = alert(
                activity, content,
                activity.getString(if (overdue) R.string.home_payments_attention else R.string.home_payments_regular),
                if (overdue) R.color.app_error else R.color.app_ink,
                title, subaccount, "", activity.getString(R.string.home_payments_btn)
            ) { activity.navigate(Tabs.PAYMENTS) }
            if (overdue) tint(activity, card, R.color.app_badge_error_bg)
        }

        private fun renderConflictAlert(activity: MainActivity, content: LinearLayout, snapshot: Snapshot) {
            val conflicts = ScheduleConflictDetector.findConflicts(snapshot.records.values)
            if (conflicts.isEmpty()) return
            val card = alert(
                activity, content,
                activity.getString(R.string.home_conflicts_title, conflicts.size), R.color.app_warning,
                activity.getString(R.string.home_conflicts_heading), conflicts[0].description(),
                if (conflicts.size > 1) activity.getString(R.string.home_conflicts_more, conflicts.size - 1) else "",
                activity.getString(R.string.home_conflicts_btn)
            ) {
                activity.setCalendarMode("day")
                activity.openScheduleDate(conflicts[0].first.date)
            }
            tint(activity, card, R.color.app_badge_warning_bg)
        }

        private fun renderNewsAlert(activity: MainActivity, content: LinearLayout, snapshot: Snapshot) {
            val top = snapshot.news.firstOrNull() ?: return
            val formattedDate = HistoryScreenController.formatFullDate(top.publicationDate)
            val dateLine = (if (formattedDate.isEmpty()) top.publicationDate else formattedDate) +
                if (top.author.isNotEmpty()) " · ${top.author}" else ""
            alert(
                activity, content, activity.getString(R.string.home_news_title), R.color.app_muted,
                top.title, top.headline, dateLine, activity.getString(R.string.home_news_all_btn, snapshot.news.size)
            ) { activity.navigate(Tabs.NEWS) }
        }

        private fun renderFocus(
            activity: MainActivity, parent: LinearLayout, data: JSONObject, snapshot: Snapshot?,
            lessons: List<LessonTime>, details: Consumer<LessonTime>
        ) {
            val now = Instant.now()
            val today = LocalDate.now(UsosClient.ZONE)
            val current = lessons.filter { !it.tentative() && it.current(now) }
            val focus = current.firstOrNull() ?: lessons.firstOrNull { it.start.toInstant().isAfter(now) }

            val view = inflate(activity, R.layout.view_home_focus, parent)
            parent.addView(view)
            view.findViewById<TextView>(R.id.home_updated).text =
                activity.getString(R.string.payments_data_freshness, UiHelper.timestamp(data.optLong("successAt")))
            view.findViewById<TextView>(R.id.home_caption).setText(if (current.isNotEmpty()) R.string.classes_ongoing else R.string.design_next_class)
            view.findViewById<TextView>(R.id.home_title).text =
                focus?.record?.title ?: activity.getString(if (snapshot == null) R.string.dashboard_no_data else R.string.classes_no_upcoming)

            if (focus != null) {
                bindFocusLesson(activity, view, focus, current.isNotEmpty(), now, today)
                view.findViewById<View>(R.id.home_hero).apply {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { details.accept(focus) }
                }
            } else {
                view.findViewById<TextView>(R.id.home_hint).apply {
                    setText(if (snapshot == null) R.string.refresh_offline else R.string.home_schedule_empty_hint)
                    visibility = View.VISIBLE
                }
                view.findViewById<MaterialButton>(R.id.home_focus_action).apply {
                    setText(if (snapshot == null) R.string.btn_connect else R.string.btn_open_plan)
                    visibility = View.VISIBLE
                    setOnClickListener { if (snapshot == null) activity.navigate(Tabs.ACCOUNT) else openDay(activity, today) }
                }
            }

            // Concurrent classes and tentative times stay visible, without a false countdown.
            val focusDate = focus?.start?.toLocalDate() ?: today
            val rest = lessons.filter { it !== focus && it.intersects(focusDate) && it.end.toInstant().isAfter(now) }
            if (rest.isNotEmpty()) {
                sectionLink(activity, parent, if (focusDate == today) R.string.design_later_today else R.string.design_later_that_day) { openDay(activity, focusDate) }
                val box = UiHelper.card(activity, parent)
                rest.forEachIndexed { index, lesson ->
                    if (index > 0) UiHelper.trayDivider(activity, box)
                    box.addView(lessonRow(activity, box, lesson, details))
                }
            }
            val upcoming = lessons.firstOrNull { it.start.toInstant().isAfter(now) }
            if (current.isNotEmpty() && upcoming != null && !upcoming.intersects(today)) {
                sectionLink(activity, parent, R.string.design_next_class) { openDay(activity, upcoming.start.toLocalDate()) }
                val nextBox = UiHelper.card(activity, parent)
                nextBox.addView(UiHelper.actionRow(activity, upcoming.record.title, { details.accept(upcoming) }))
                UiHelper.note(activity, nextBox, UiHelper.shortDate(upcoming.start.toLocalDate()) + " · " +
                    upcoming.start.format(CLOCK) +
                    if (upcoming.tentative()) " · " + activity.getString(R.string.design_tentative) else "", Tone.MUTED)
            }
        }

        private fun bindFocusLesson(activity: MainActivity, view: View, focus: LessonTime, ongoing: Boolean, now: Instant, today: LocalDate) {
                        val room = room(focus.record)
            val date = if (focus.start.toLocalDate() == today) "" else UiHelper.shortDate(focus.start.toLocalDate()) + " · "
            val detail = date + focus.start.format(CLOCK) + "–" + focus.end.format(CLOCK) +
                if (room.isEmpty()) "" else " · " + activity.getString(R.string.lesson_room_label, room)
            view.findViewById<TextView>(R.id.home_detail).apply {
                text = detail
                visibility = View.VISIBLE
            }
            view.findViewById<TextView>(R.id.home_status).apply {
                text = when {
                    focus.tentative() -> activity.getString(R.string.design_tentative)
                    ongoing -> activity.getString(R.string.home_classes_remaining_min, max(1L, (Duration.between(now, focus.end.toInstant()).seconds + 59) / 60))
                    focus.start.toLocalDate() == today -> activity.getString(R.string.design_starts_minutes, max(1L, (Duration.between(now, focus.start.toInstant()).seconds + 59) / 60))
                    else -> focus.start.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Language.currentLocale(activity)))
                }
                visibility = View.VISIBLE
            }
        }

        private fun lessonRow(activity: MainActivity, parent: LinearLayout, lesson: LessonTime, details: Consumer<LessonTime>): View {
            val row = inflate(activity, R.layout.item_home_lesson, parent)
                        val room = room(lesson.record)
            row.findViewById<TextView>(R.id.home_lesson_title).text = lesson.record.title
            val time = lesson.start.format(CLOCK) + "–" + lesson.end.format(CLOCK) +
                (if (room.isEmpty()) "" else " · " + activity.getString(R.string.lesson_room_label, room)) +
                (if (lesson.tentative()) " · " + activity.getString(R.string.design_tentative) else "")
            row.findViewById<TextView>(R.id.home_lesson_time).text = time
            row.setOnClickListener { details.accept(lesson) }
            return row
        }

        private fun room(record: Record): String = record.optMeta("room").trim().ifEmpty {
            record.value.lineSequence().firstOrNull { it.startsWith("Sala ") }?.substring(5)?.trim().orEmpty()
        }

        private fun renderWeek(activity: MainActivity, parent: LinearLayout, snapshot: Snapshot?, lessons: List<LessonTime>) {
            if (snapshot == null) return
            val today = LocalDate.now(UsosClient.ZONE)
            val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            sectionLink(activity, parent, R.string.design_this_week) { openDay(activity, today) }
            parent.addView(PrototypeUi.week(activity, monday, today, lessons, false) { openDay(activity, it) }, LinearLayout.LayoutParams(-1, -2))
        }

        @Throws(JSONException::class)
        private fun renderDeadlinesPreview(activity: MainActivity, content: LinearLayout, rows: JSONArray?) {
            if (rows == null) return
            val open = ArrayList<JSONObject>()
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                if (!row.optBoolean("done")) open.add(row)
            }
            if (open.isEmpty()) return
            open.sortWith(compareBy { it.optString("due") })
            UiHelper.heading(activity, content, activity.getString(R.string.deadlines_upcoming))
            val box = UiHelper.card(activity, content)
            val today = LocalDate.now(UsosClient.ZONE)
            for (i in 0 until min(3, open.size)) {
                val task = open[i]
                val due = TaskTools.dueDate(task)
                val item = inflate(activity, R.layout.item_home_deadline, box)
                item.findViewById<TextView>(R.id.home_deadline_title).text = task.optString("title")
                item.findViewById<TextView>(R.id.home_deadline_due).apply {
                    if (due == null) {
                        setText(R.string.tasks_due_unknown)
                    } else {
                        val prefix = when {
                            due.isBefore(today) -> "${activity.getString(R.string.tasks_chip_overdue)} · "
                            due == today -> "${activity.getString(R.string.tasks_chip_today)} · "
                            else -> ""
                        }
                        val line = prefix + due.format(DUE_DATE)
                        text = line
                        if (due.isBefore(today)) setTone(Tone.ERROR)
                    }
                }
                box.addView(item)
            }
            box.isClickable = true
            box.isFocusable = true
            box.setOnClickListener { activity.navigate(Tabs.TASKS) }
            box.addView(UiHelper.button(activity, activity.getString(R.string.home_open_tasks), { activity.navigate(Tabs.TASKS) }, false))
        }

        @Throws(Exception::class)
        private fun renderRecentChanges(activity: MainActivity, content: LinearLayout, d: JSONObject) {
            val rows = d.optJSONArray("history")
            if (rows == null || rows.length() == 0) return
            sectionLink(activity, content, R.string.home_recent_changes) { activity.navigate(Tabs.HISTORY) }
            val recent = ArrayList<Change>()
            for (i in 0 until rows.length()) recent.add(Change.from(rows.getJSONObject(i)))
            recent.sortWith { a, b -> b.effectiveTimestamp().compareTo(a.effectiveTimestamp()) }
            val box = UiHelper.tray(activity, content)
            recent.take(2).forEachIndexed { index, change ->
                if (index > 0) UiHelper.trayDivider(activity, box)
                HistoryScreenController.renderChangeRow(activity, box, change)
            }
        }
    }
}
