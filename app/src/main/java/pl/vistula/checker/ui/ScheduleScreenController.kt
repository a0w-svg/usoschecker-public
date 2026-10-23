package pl.vistula.checker.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.HashMap
import java.util.HashSet
import java.util.function.Consumer
import java.util.function.IntConsumer
import kotlin.math.max
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.AttendanceTracker
import pl.vistula.checker.core.DaySummary
import pl.vistula.checker.core.IcsExporter
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.core.PlanBrowser
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.ScheduleBreaks
import pl.vistula.checker.core.ScheduleConflictDetector
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.StudentSearch

/**
 * Controller for rendering the Schedule / Timetable screen, lessons, day picker, and ICS export.
 */
class ScheduleScreenController private constructor() {

    interface ScheduleListener {
        fun onOpenTask(title: String)
        fun onAbsenceRecorded()
    }

    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject,
            state: JSONObject,
            demo: Boolean,
            calendarDate: String,
            calendarMode: String,
            demoPlanDays: Int,
            onDateChanged: Consumer<String>,
            onModeChanged: Consumer<String>,
            onRefresh: Runnable,
            listener: ScheduleListener
        ) {
            val raw = d.optJSONObject("snapshot")
            if (raw == null) {
                UiHelper.empty(
                    activity,
                    content,
                    activity.getString(R.string.grades_no_data),
                    activity.getString(R.string.refresh_offline)
                )
                val connected = state.optString("token").isNotEmpty()
                content.addView(
                    UiHelper.button(
                        activity,
                        if (connected) activity.getString(R.string.btn_refresh) else activity.getString(R.string.btn_connect),
                        { if (connected) onRefresh.run() else activity.navigate(4) },
                        true
                    )
                )
                return
            }

            val snapshot = Snapshot.from(raw)
            val planRows = ArrayList<Record>()
            for (r in snapshot.records.values) {
                if (r.kind == "plan") planRows.add(r)
            }

            val conflicts = ScheduleConflictDetector.findConflicts(planRows)
            val conflictLessonKeys = HashSet<String>()
            for (c in conflicts) {
                conflictLessonKeys.add(c.first.id)
                conflictLessonKeys.add(c.second.id)
            }

            val today = LocalDate.now(UsosClient.ZONE)
            val allLessons = LessonTime.sorted(planRows)
            val lessonDays = PlanBrowser.daysWithLessons(allLessons, activity.getCalendarQuery())
            var requested = if (calendarDate.isEmpty()) today else LocalDate.parse(calendarDate)
            if (activity.shouldFocusCurrentSchedule() && allLessons.any { lesson ->
                    !lesson.tentative() && lesson.current(Instant.now()) &&
                        StudentSearch.matches(activity.getCalendarQuery(), lesson.record.title, lesson.record.value, lesson.record.date)
                }
            ) {
                requested = today
            }
            val activeDate = PlanBrowser.nearestDay(lessonDays, requested)
            activity.setCalendarDate(activeDate.toString())
            val weekly = calendarMode == "week"
            val monday = activeDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val move = IntConsumer { direction ->
                val target = PlanBrowser.adjacentDay(
                    PlanBrowser.daysWithLessons(allLessons, activity.getCalendarQuery()),
                    activeDate,
                    weekly,
                    direction
                ) ?: return@IntConsumer
                activity.setCalendarDate(target.toString())
                if (calendarMode == "agenda") onModeChanged.accept("day")
                else onDateChanged.accept(target.toString())
            }
            activity.setScheduleNavigation(move)

            val navBar = LinearLayout(activity)
            navBar.gravity = Gravity.CENTER_VERTICAL
            val previous = headerIcon(activity, R.drawable.ic_schedule_previous) { move.accept(-1) }
            previous.contentDescription = activity.getString(if (weekly) R.string.schedule_previous_week else R.string.schedule_previous_day)
            val next = headerIcon(activity, R.drawable.ic_schedule_next) { move.accept(1) }
            next.contentDescription = activity.getString(if (weekly) R.string.schedule_next_week else R.string.schedule_next_day)
            val navLocale = Language.currentLocale(activity)
            val dateLabel = when {
                calendarMode == "agenda" -> activity.getString(R.string.schedule_mode_agenda)
                weekly -> monday.format(DateTimeFormatter.ofPattern("d MMM", navLocale)) + " – " +
                    monday.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", navLocale))
                else -> activeDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", navLocale))
            }
            val date = headerButton(activity, dateLabel) {
                val days = ArrayList(PlanBrowser.daysWithLessons(allLessons, activity.getCalendarQuery()))
                if (days.isEmpty()) {
                    Toast.makeText(activity, R.string.schedule_no_search_results, Toast.LENGTH_SHORT).show()
                    return@headerButton
                }
                val labels = Array(days.size) { i ->
                    days[i].format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", navLocale))
                }
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.schedule_btn_choose_date)
                    .setSingleChoiceItems(labels, days.indexOf(activeDate)) { dialog, index ->
                        activity.setCalendarDate(days[index].toString())
                        onModeChanged.accept(if (weekly) "week" else "day")
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.btn_close, null)
                    .show()
            }
            date.contentDescription = "${activity.getString(R.string.schedule_btn_choose_date)}: $dateLabel"
            date.textSize = 18f
            date.setTextColor(activity.getColor(R.color.app_ink))
            date.minHeight = UiHelper.dp(activity, 64)
            date.minimumHeight = UiHelper.dp(activity, 64)
            date.setPadding(
                UiHelper.dp(activity, 8),
                UiHelper.dp(activity, 4),
                UiHelper.dp(activity, 8),
                UiHelper.dp(activity, 4)
            )
            date.setTypeface(null, Typeface.NORMAL)
            val more = headerIcon(activity, R.drawable.ic_schedule_more) {}
            more.id = R.id.schedule_more
            more.contentDescription = activity.getString(R.string.schedule_options)
            previous.isEnabled = PlanBrowser.adjacentDay(lessonDays, activeDate, weekly, -1) != null
            next.isEnabled = PlanBrowser.adjacentDay(lessonDays, activeDate, weekly, 1) != null
            navBar.addView(previous, LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2))
            navBar.addView(date, LinearLayout.LayoutParams(0, -2, 1f))
            navBar.addView(next, LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2))
            navBar.addView(more, LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2))
            content.addView(navBar)

            val search = UiHelper.searchField(
                activity,
                content,
                activity.getString(R.string.schedule_search_hint),
                activity.getCalendarQuery(),
                null
            ) { activity.setCalendarQuery("") }
            val searchBox = content.getChildAt(content.childCount - 1)
            searchBox.visibility = if (activity.getCalendarQuery().isEmpty()) View.GONE else View.VISIBLE
            val results = LinearLayout(activity)
            results.orientation = LinearLayout.VERTICAL
            results.id = R.id.schedule_swipe_content
            results.minimumHeight = UiHelper.dp(activity, 240)
            content.addView(results)
            more.setOnClickListener {
                val menu = PopupMenu(activity, more)
                val modes = arrayOf("day", "week", "agenda")
                val labels = intArrayOf(R.string.schedule_day_label, R.string.schedule_mode_week, R.string.schedule_mode_agenda)
                for (i in modes.indices) {
                    menu.menu.add(1, i + 1, i, labels[i]).setCheckable(true).isChecked =
                        modes[i] == (if (calendarMode == "list") "day" else calendarMode)
                }
                menu.menu.setGroupCheckable(1, true, true)
                menu.menu.add(2, 4, 4, R.string.schedule_btn_today)
                menu.menu.add(2, 5, 5, R.string.schedule_search_action)
                val visible = PlanBrowser.select(LessonTime.sorted(planRows), activeDate, calendarMode, activity.getCalendarQuery())
                menu.menu.add(3, 6, 6, R.string.schedule_export_ics).isEnabled = visible.isNotEmpty()
                menu.menu.add(3, 7, 7, R.string.schedule_share_plan).isEnabled = visible.isNotEmpty()
                menu.menu.add(4, 8, 8, R.string.schedule_info_title)
                menu.menu.add(4, 9, 9, R.string.btn_refresh)
                menu.setOnMenuItemClickListener { item ->
                    when (val id = item.itemId) {
                        in 1..3 -> onModeChanged.accept(modes[id - 1])
                        4 -> {
                            activity.setCalendarDate(today.toString())
                            onModeChanged.accept(if (weekly) "week" else "day")
                        }
                        5 -> {
                            searchBox.visibility = View.VISIBLE
                            search.requestFocus()
                            search.post {
                                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                    .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                        6 -> {
                            val rows = ArrayList<Record>()
                            for (lesson in visible) rows.add(lesson.record)
                            exportPlanToIcs(activity, rows)
                        }
                        7 -> sharePlanText(activity, visible, activeDate)
                        8 -> {
                            var info = activity.getString(
                                R.string.schedule_saved_range,
                                snapshot.start,
                                snapshot.end,
                                UiHelper.timestamp(d.optLong("successAt"))
                            ) + "\n\n" + activity.getString(R.string.schedule_warsaw_zone)
                            if (conflicts.isNotEmpty()) {
                                info += "\n\n" + activity.getString(R.string.home_conflicts_title, conflicts.size) +
                                    "\n" + conflicts[0].description()
                            }
                            MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.schedule_info_title)
                                .setMessage(info)
                                .setPositiveButton(R.string.btn_close, null)
                                .show()
                        }
                        9 -> onRefresh.run()
                    }
                    true
                }
                menu.show()
            }

            val populateRef = arrayOfNulls<Runnable>(1)
            populateRef[0] = Runnable {
                renderResults(
                    activity,
                    results,
                    allLessons,
                    activeDate,
                    calendarMode,
                    search.text.toString(),
                    conflictLessonKeys,
                    onDateChanged,
                    onModeChanged,
                    listener
                ) {
                    search.setText("")
                    activity.setCalendarQuery("")
                    populateRef[0]!!.run()
                }
            }
            UiHelper.watchSearch(search) { value ->
                activity.setCalendarQuery(value)
                val matchingDays = PlanBrowser.daysWithLessons(allLessons, value)
                previous.isEnabled = PlanBrowser.adjacentDay(matchingDays, activeDate, weekly, -1) != null
                next.isEnabled = PlanBrowser.adjacentDay(matchingDays, activeDate, weekly, 1) != null
                populateRef[0]!!.run()
            }
            populateRef[0]!!.run()
        }

        private fun renderResults(
            activity: MainActivity,
            content: LinearLayout,
            allLessons: List<LessonTime>,
            activeDate: LocalDate,
            calendarMode: String,
            query: String,
            conflictLessonKeys: Set<String>,
            onDateChanged: Consumer<String>,
            onModeChanged: Consumer<String>,
            listener: ScheduleListener,
            onClearSearch: Runnable?
        ) {
            content.removeAllViews()
            val visible = PlanBrowser.select(allLessons, activeDate, calendarMode, query)

            val contentLocale = Language.currentLocale(activity)
            if (visible.isEmpty()) {
                UiHelper.empty(
                    activity,
                    content,
                    activeDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", contentLocale)),
                    when {
                        query.trim().isNotEmpty() -> activity.getString(R.string.schedule_no_search_results)
                        calendarMode == "week" -> activity.getString(R.string.schedule_week_empty)
                        calendarMode == "agenda" -> activity.getString(R.string.schedule_agenda_empty)
                        else -> activity.getString(R.string.schedule_no_classes)
                    }
                )
                if (query.trim().isNotEmpty() && onClearSearch != null) {
                    content.addView(UiHelper.button(activity, activity.getString(R.string.btn_clear_search), onClearSearch, false))
                }
                var nextAfter: LessonTime? = null
                for (l in PlanBrowser.select(allLessons, activeDate, "agenda", query)) {
                    if (l.start.toLocalDate().isAfter(activeDate)) {
                        nextAfter = l
                        break
                    }
                }
                if (nextAfter != null) {
                    val targetLesson = nextAfter
                    content.addView(
                        UiHelper.button(
                            activity,
                            "${activity.getString(R.string.schedule_btn_next_class)} (${UiHelper.shortDate(nextAfter.start.toLocalDate())})",
                            {
                                onDateChanged.accept(targetLesson.start.toLocalDate().toString())
                                onModeChanged.accept("day")
                            },
                            false
                        )
                    )
                }
            } else {
                var currentSectionDay: LocalDate? = null
                val inflater = LayoutInflater.from(activity)
                val breaks = HashMap<Instant, ScheduleBreaks.Break>()

                for (lesson in visible) {
                    val lessonDay = if (calendarMode == "day" || calendarMode == "list") activeDate else lesson.start.toLocalDate()
                    if (lessonDay != currentSectionDay) {
                        currentSectionDay = lessonDay
                        if (calendarMode == "week" || calendarMode == "agenda") {
                            UiHelper.heading(activity, content, lessonDay.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", contentLocale)))
                        }
                        breaks.clear()
                        // Use the complete plan so a search cannot turn hidden meetings into free time.
                        for (gap in ScheduleBreaks.inDay(allLessons, lessonDay)) {
                            breaks[gap.end.toInstant()] = gap
                        }
                    }

                    val gap = breaks.remove(lesson.start.toInstant())
                    if (gap != null) {
                        val breakCard = UiHelper.card(activity, content, Color.rgb(240, 245, 241))
                        breakCard.setPadding(UiHelper.dp(activity, 16), UiHelper.dp(activity, 12), UiHelper.dp(activity, 16), 0)
                        UiHelper.add(activity, breakCard, activity.getString(R.string.schedule_break_title, gap.minutes), 14, UiHelper.GREEN, true)
                        UiHelper.add(
                            activity,
                            breakCard,
                            activity.getString(
                                R.string.schedule_break_time,
                                gap.start.format(DateTimeFormatter.ofPattern("HH:mm")),
                                gap.end.format(DateTimeFormatter.ofPattern("HH:mm"))
                            ),
                            13,
                            UiHelper.MUTED,
                            false
                        )
                    }

                    val isConflict = conflictLessonKeys.contains(lesson.record.id)
                    val card = inflater.inflate(R.layout.item_lesson_card, content, false)
                    val timeView = card.findViewById<TextView>(R.id.lesson_item_time)
                    val conflictBadge = card.findViewById<TextView>(R.id.lesson_item_conflict_badge)
                    val titleView = card.findViewById<TextView>(R.id.lesson_item_title)
                    val detailsView = card.findViewById<TextView>(R.id.lesson_item_details)
                    val btnDetails = card.findViewById<MaterialButton>(R.id.lesson_item_btn_details)

                    var timeText = lesson.start.format(DateTimeFormatter.ofPattern("HH:mm")) + " – " +
                        lesson.end.format(DateTimeFormatter.ofPattern("HH:mm"))
                    if (lesson.start.toLocalDate() != lesson.end.toLocalDate()) {
                        timeText += " · " + activity.getString(R.string.schedule_overnight)
                    }
                    timeView.text = timeText
                    if (!lesson.tentative() && lesson.current(Instant.now())) {
                        val currentBadge = card.findViewById<TextView>(R.id.lesson_item_current_badge)
                        currentBadge.visibility = View.VISIBLE
                        val remaining = max(1L, (Duration.between(Instant.now(), lesson.end.toInstant()).seconds + 59) / 60)
                        currentBadge.text = activity.getString(R.string.schedule_current_badge, remaining)
                        card.id = R.id.schedule_current_lesson
                        card.stateDescription = activity.getString(R.string.classes_ongoing)
                        card.setBackgroundResource(R.drawable.bg_schedule_current)
                    }

                    conflictBadge.visibility = if (isConflict) View.VISIBLE else View.GONE

                    titleView.text = lesson.record.title
                    var summary = lessonSummary(lesson, activity)
                    val roomView = card.findViewById<TextView>(R.id.lesson_item_room)
                    val room = lessonRoom(lesson.record)
                    if (room.isNotEmpty()) {
                        roomView.visibility = View.VISIBLE
                        roomView.text = activity.getString(R.string.lesson_room_label, room)
                        val secondary = ArrayList<String>()
                        for (line in summary.split("\n")) {
                            if (line != activity.getString(R.string.notification_class_room, room)) secondary.add(line)
                        }
                        summary = secondary.joinToString("\n")
                    }
                    detailsView.text = summary
                    detailsView.visibility = if (summary.isEmpty()) View.GONE else View.VISIBLE

                    card.isClickable = true
                    card.isFocusable = true
                    card.setOnClickListener { showLessonDetails(activity, lesson, listener) }
                    btnDetails.visibility = View.GONE
                    content.addView(card)
                }
            }

            if (visible.isNotEmpty() && calendarMode != "agenda" && calendarMode != "week") {
                val summary = DaySummary.of(visible, activeDate)
                UiHelper.note(
                    activity,
                    content,
                    activity.getString(
                        R.string.schedule_summary_compact,
                        summary.meetings,
                        summary.busyMinutes / 60,
                        summary.busyMinutes % 60
                    ),
                    UiHelper.MUTED
                )
            }
        }

        @JvmStatic
        fun headerButton(context: Context, label: String, action: Runnable): Button {
            val button = UiHelper.button(context, label, action, false)
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            button.setPadding(UiHelper.dp(context, 4), 0, UiHelper.dp(context, 4), 0)
            button.textSize = 28f
            button.minHeight = UiHelper.dp(context, 64)
            button.minimumHeight = UiHelper.dp(context, 64)
            (button as MaterialButton).insetTop = 0
            button.insetBottom = 0
            return button
        }

        @JvmStatic
        fun headerIcon(context: Context, icon: Int, action: Runnable): Button {
            val button = headerButton(context, "", action) as MaterialButton
            button.setIconResource(icon)
            button.iconSize = UiHelper.dp(context, 28)
            button.iconPadding = 0
            button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            button.iconTint = button.textColors
            button.gravity = Gravity.CENTER
            return button
        }

        @JvmStatic
        fun showLessonDetails(activity: MainActivity, lesson: LessonTime, listener: ScheduleListener) {
            val details = UiHelper.shortDate(lesson.start.toLocalDate()) + "\n\n" + lesson.record.value
            val body = LinearLayout(activity)
            body.orientation = LinearLayout.VERTICAL
            body.setPadding(UiHelper.dp(activity, 20), UiHelper.dp(activity, 8), UiHelper.dp(activity, 20), UiHelper.dp(activity, 8))
            UiHelper.detailField(
                activity,
                body,
                activity.getString(R.string.schedule_when),
                UiHelper.shortDate(lesson.start.toLocalDate()) + " · " +
                    lesson.start.format(DateTimeFormatter.ofPattern("HH:mm")) + " – " +
                    (if (lesson.start.toLocalDate() != lesson.end.toLocalDate()) {
                        UiHelper.shortDate(lesson.end.toLocalDate()) + " "
                    } else {
                        ""
                    }) +
                    lesson.end.format(DateTimeFormatter.ofPattern("HH:mm"))
            )
            val summary = lessonSummary(lesson, activity)
            if (summary.isNotEmpty()) UiHelper.note(activity, body, summary, UiHelper.INK)
            for (key in arrayOf("building", "groupNumber")) {
                val value = lesson.record.optMeta(key)
                if (value.isNotEmpty()) {
                    UiHelper.detailField(
                        activity,
                        body,
                        activity.getString(if (key == "building") R.string.schedule_building else R.string.schedule_group),
                        value
                    )
                }
            }
            try {
                val raw = UiHelper.text(activity, lesson.record.json().toString(2), 12, UiHelper.INK, false)
                raw.setTextIsSelectable(true)
                raw.visibility = View.GONE
                body.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.grades_full_data),
                        { raw.visibility = if (raw.visibility == View.GONE) View.VISIBLE else View.GONE },
                        false
                    )
                )
                body.addView(raw)
            } catch (_: Exception) {
            }
            val scroller = UiHelper.dialogScroll(activity, body)
            MaterialAlertDialogBuilder(activity)
                .setTitle(lesson.record.title)
                .setView(scroller)
                .setNegativeButton(activity.getString(R.string.btn_close), null)
                .setNeutralButton("Dodaj zadanie") { _, _ -> listener.onOpenTask("Zadanie: ${lesson.record.title}") }
                .setPositiveButton(activity.getString(R.string.btn_more_options)) { _, _ ->
                    val options = arrayOf<CharSequence>(
                        activity.getString(R.string.lesson_option_absence),
                        activity.getString(R.string.lesson_option_share),
                        activity.getString(R.string.lesson_option_copy)
                    )
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(lesson.record.title)
                        .setItems(options) { _, idx ->
                            when (idx) {
                                0 -> recordAbsenceForLesson(activity, lesson, listener)
                                1 -> {
                                    val share = Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, "${lesson.record.title}\n$details")
                                    try {
                                        activity.startActivity(Intent.createChooser(share, activity.getString(R.string.schedule_share_plan)))
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(activity, R.string.toast_no_share_app, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                2 -> UiHelper.copyToClipboard(
                                    activity,
                                    activity.getString(R.string.btn_class_details),
                                    "${lesson.record.title}\n$details"
                                )
                            }
                        }
                        .show()
                }
                .show()
        }

        @JvmStatic
        fun lessonRoom(record: Record): String {
            val room = record.optMeta("room").trim()
            if (room.isNotEmpty()) return room
            for (line in record.value.split("\n")) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Sala ")) {
                    return trimmed.substring(5).split(" · ", limit = 2)[0].trim()
                }
            }
            return ""
        }

        /** Avoid repeating the time and building on cards. Old snapshots still keep their readable description. */
        @JvmStatic
        @JvmOverloads
        fun lessonSummary(lesson: LessonTime, context: android.content.Context? = null): String {
            val record = lesson.record
            val lines = ArrayList<String>()
            var type = record.optMeta("classType")
            var room = record.optMeta("room")
            var lecturers = record.optMeta("lecturers")
            // Mixed/older snapshots may have only some of the structured fields.
            val legacyType = ArrayList<String>()
            val timeRegex = Regex("(?:\\d{4}-\\d{2}-\\d{2} )?\\d{2}:\\d{2}\\s*[–−-]\\s*\\d{2}:\\d{2}")
            for (line in record.value.split("\n")) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Sala ")) {
                    if (room.isEmpty()) room = trimmed.substring("Sala ".length)
                } else if (trimmed.startsWith("Prowadzący: ")) {
                    if (lecturers.isEmpty()) lecturers = trimmed.substring("Prowadzący: ".length)
                } else if (trimmed.isNotEmpty() && !timeRegex.matches(trimmed) && trimmed != "Termin orientacyjny") {
                    legacyType.add(trimmed)
                }
            }
            if (type.isEmpty()) type = legacyType.joinToString("\n")
            if (type.isNotEmpty()) lines.add(type)
            if (room.isNotEmpty()) lines.add(context?.getString(R.string.notification_class_room, room) ?: "Sala $room")
            if (lecturers.isNotEmpty()) lines.add((context?.getString(R.string.notification_diff_3) ?: "Prowadzący:") + " " + lecturers)
            if (lesson.tentative()) lines.add(context?.getString(R.string.schedule_tentative_badge) ?: "Termin orientacyjny")
            return lines.joinToString("\n")
        }

        private fun recordAbsenceForLesson(activity: MainActivity, lesson: LessonTime, listener: ScheduleListener) {
            val courseTitle = "${lesson.record.title} · ${AttendanceTracker.type(lesson.record)}"
            val prefs = activity.attendancePreferences()
            val count: Int
            val limit = AttendanceTracker.LIMIT
            try {
                val raw = activity.display().optJSONObject("snapshot")
                val snapshot = if (raw == null) Snapshot("", "") else Snapshot.from(raw)
                val saved = AttendanceTracker.reconcile(JSONObject(prefs.getString("data", "{}") ?: "{}"), snapshot)
                val id = AttendanceTracker.record(saved, snapshot, lesson.record)
                val item = saved.getJSONObject(id)
                count = item.optInt("absences")
                prefs.edit()
                    .putString("data", saved.toString())
                    .putString("term", item.optString("term"))
                    .putString("classType", "all")
                    .apply()
            } catch (_: Exception) {
                Toast.makeText(activity, R.string.attendance_save_error, Toast.LENGTH_LONG).show()
                return
            }

            if (count >= limit) {
                val alertMsg = if (count > limit) {
                    activity.getString(R.string.attendance_alert_msg_exceeded)
                } else {
                    activity.getString(R.string.attendance_alert_msg_reached)
                }
                MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.attendance_alert_title))
                    .setMessage(activity.getString(R.string.attendance_alert_body, courseTitle, count, limit, alertMsg))
                    .setPositiveButton(activity.getString(R.string.attendance_alert_btn_open)) { _, _ -> activity.navigate(12) }
                    .setNegativeButton(activity.getString(R.string.attendance_alert_btn_understand), null)
                    .show()
            } else {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.attendance_recorded_toast, courseTitle, count, limit),
                    Toast.LENGTH_LONG
                ).show()
            }
            listener.onAbsenceRecorded()
        }

        private fun exportPlanToIcs(activity: MainActivity, rows: List<Record>) {
            if (rows.isEmpty()) return
            try {
                activity.saveCalendar(pl.vistula.checker.core.ApiLanguage.`in`(Language.currentLocale(activity).language) { IcsExporter.export(rows) })
            } catch (_: Exception) {
                Toast.makeText(activity, R.string.toast_export_calendar_error, Toast.LENGTH_SHORT).show()
            }
        }

        private fun sharePlanText(activity: MainActivity, visible: List<LessonTime>, activeDate: LocalDate) {
            if (visible.isEmpty()) return
            val sb = StringBuilder()
            val shareLocale = Language.currentLocale(activity)
            sb.append(
                activity.getString(
                    R.string.schedule_share_title,
                    activeDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", shareLocale))
                )
            ).append("\n\n")
            for (l in visible) {
                sb.append(l.start.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                    .append(" – ")
                    .append(l.end.format(DateTimeFormatter.ofPattern("HH:mm")))
                    .append(" | ")
                    .append(l.record.title)
                    .append("\n")
                    .append(l.record.value)
                    .append("\n\n")
            }
            sb.append(activity.getString(R.string.share_generated_by))
            val share = Intent(Intent.ACTION_SEND).setType("text/plain")
            share.putExtra(Intent.EXTRA_TEXT, sb.toString())
            try {
                activity.startActivity(Intent.createChooser(share, activity.getString(R.string.schedule_share_plan)))
            } catch (_: ActivityNotFoundException) {
                UiHelper.copyToClipboard(activity, activity.getString(R.string.schedule_clipboard_label), sb.toString())
            }
        }
    }
}

