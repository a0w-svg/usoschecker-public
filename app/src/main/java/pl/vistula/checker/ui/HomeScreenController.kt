package pl.vistula.checker.ui

import android.graphics.Color
import android.widget.LinearLayout
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
            renderKpis(activity, content, d, state, demo, demoTasks, onLessonDetails)
            renderDeadlinesPreview(activity, content, if (demo) demoTasks else state.optJSONArray("tasks"))

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
                        UiHelper.add(activity, payBox, activity.getString(R.string.home_payments_due, unpaid), 19, UiHelper.INK, true)
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

            // Recent changes
            renderRecentChanges(activity, content, d)
        }

        @Throws(Exception::class)
        private fun renderKpis(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject,
            state: JSONObject,
            demo: Boolean,
            demoTasks: JSONArray?,
            onLessonDetails: Consumer<LessonTime>
        ) {
            val raw = d.optJSONObject("snapshot")
            val records = if (raw == null) ArrayList() else ArrayList(Snapshot.from(raw).records.values)
            var grades = 0
            var todayCount = 0
            var openTasks = 0
            val now = Instant.now()
            val today = LocalDate.now(UsosClient.ZONE)

            for (r in records) {
                if (r.kind == "ocena") grades++
                else if (LessonTime.of(r).intersects(today)) todayCount++
            }

            val tasks = if (demo) {
                demoTasks ?: JSONArray()
            } else {
                state.optJSONArray("tasks") ?: JSONArray()
            }
            for (i in 0 until tasks.length()) {
                if (!tasks.getJSONObject(i).optBoolean("done")) openTasks++
            }

            val stats = LinearLayout(activity)
            stats.orientation = LinearLayout.HORIZONTAL
            val counts = intArrayOf(grades, todayCount, openTasks)
            val titles = arrayOf(
                activity.getString(R.string.stat_grades),
                activity.getString(R.string.stat_today_classes),
                activity.getString(R.string.stat_tasks)
            )
            val destinations = intArrayOf(2, 5, 7)

            for (i in 0..2) {
                val target = destinations[i]
                val countColor = UiHelper.INK
                val card = UiHelper.kpiCard(activity, stats, counts[i].toString(), titles[i], countColor) {
                    activity.navigate(target)
                }
                if (i > 0) {
                    val cp = card.layoutParams as LinearLayout.LayoutParams
                    cp.marginStart = UiHelper.dp(activity, 8)
                    card.layoutParams = cp
                }
            }
            val statsParams = LinearLayout.LayoutParams(-1, -2)
            statsParams.bottomMargin = UiHelper.dp(activity, 14)
            content.addView(stats, statsParams)

            // Ongoing lecture
            renderOngoingLectures(activity, content, records, onLessonDetails)

            // Upcoming lecture
            UiHelper.heading(activity, content, activity.getString(R.string.classes_upcoming))
            var upcoming: LessonTime? = null
            for (lesson in LessonTime.sorted(records)) {
                if (lesson.start.toInstant().isAfter(now)) {
                    upcoming = lesson
                    break
                }
            }

            if (upcoming == null) {
                UiHelper.empty(
                    activity,
                    content,
                    activity.getString(R.string.classes_no_upcoming),
                    if (raw == null) activity.getString(R.string.refresh_offline) else activity.getString(R.string.home_schedule_empty_hint)
                )
            } else {
                val focused = LessonTime.current(records, now).isEmpty()
                val box = UiHelper.card(activity, content, if (focused) Color.rgb(214, 241, 229) else Color.WHITE)
                UiHelper.add(activity, box, upcoming.record.title, 20, UiHelper.INK, true)
                UiHelper.add(activity, box, UiHelper.shortDate(upcoming.start.toLocalDate()), 13, UiHelper.MUTED, true)
                renderLessonSummary(activity, box, upcoming)
                val next = upcoming
                box.isClickable = true
                box.isFocusable = true
                box.setOnClickListener { onLessonDetails.accept(next) }
                box.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.btn_open_plan),
                        {
                            activity.setCalendarMode("day")
                            activity.openScheduleDate(next.start.toLocalDate().toString())
                        },
                        focused
                    )
                )
            }
        }

        private fun renderOngoingLectures(
            activity: MainActivity,
            parent: LinearLayout,
            rows: Collection<Record>,
            onLessonDetails: Consumer<LessonTime>
        ) {
            val current = LessonTime.current(rows, Instant.now())
            if (current.isEmpty()) {
                UiHelper.note(activity, parent, activity.getString(R.string.classes_no_ongoing), UiHelper.MUTED)
                return
            }
            for (lesson in current) {
                val box = UiHelper.card(activity, parent, if (lesson === current[0]) Color.rgb(214, 241, 229) else Color.WHITE)
                UiHelper.add(activity, box, activity.getString(R.string.classes_ongoing), 11, UiHelper.GREEN, true)
                UiHelper.add(activity, box, lesson.record.title, 22, UiHelper.INK, true)
                renderLessonSummary(activity, box, lesson)
                box.isClickable = true
                box.isFocusable = true
                box.setOnClickListener { onLessonDetails.accept(lesson) }
                val remaining = max(1L, (Duration.between(Instant.now(), lesson.end.toInstant()).seconds + 59) / 60)
                UiHelper.add(activity, box, activity.getString(R.string.home_classes_remaining_min, remaining), 12, UiHelper.GREEN, false)
            }
        }

        private fun renderLessonSummary(activity: MainActivity, box: LinearLayout, lesson: LessonTime) {
            val clock = DateTimeFormatter.ofPattern("HH:mm")
            UiHelper.add(activity, box, "${lesson.start.format(clock)} – ${lesson.end.format(clock)}", 16, UiHelper.INK, true)
            var room = lesson.record.metadata.optString("room").trim()
            if (room.isEmpty()) {
                for (line in lesson.record.value.split("\n")) {
                    if (line.startsWith("Sala ")) {
                        room = line.substring(5).trim()
                        break
                    }
                }
            }
            if (room.isNotEmpty()) {
                UiHelper.add(activity, box, activity.getString(R.string.lesson_room_label, room), 14, UiHelper.MUTED, false)
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
                UiHelper.heading(activity, content, activity.getString(R.string.home_recent_changes))
                val recent = ArrayList<Change>()
                for (i in 0 until rows.length()) {
                    recent.add(Change.from(rows.getJSONObject(i)))
                }
                recent.sortWith { a, b -> b.effectiveTimestamp().compareTo(a.effectiveTimestamp()) }
                for (i in 0 until min(3, recent.size)) {
                    HistoryScreenController.renderChangeCard(activity, content, recent[i])
                }
                content.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.home_all_history_btn),
                        { activity.navigate(1) },
                        false
                    )
                )
            }
        }
    }
}

