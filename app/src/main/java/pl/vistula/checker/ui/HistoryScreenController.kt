package pl.vistula.checker.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.min
import org.json.JSONObject
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.StudentSearch
import pl.vistula.checker.core.UsosParser

/**
 * Controller for rendering the Changes History screen and audit details.
 */
class HistoryScreenController private constructor() {

    companion object {
        @JvmStatic
        fun render(
            activity: MainActivity,
            content: LinearLayout,
            d: JSONObject,
            filter: String,
            query: String,
            demo: Boolean,
            onFilterChanged: Consumer<String>,
            onQueryChanged: Consumer<String>,
            onSimulate: Runnable?
        ) {
            val history = d.optJSONArray("history")
            val allChanges = ArrayList<Change>()
            var countGrades = 0
            var countPlan = 0
            var countAdded = 0
            var countModified = 0
            var countRemoved = 0
            if (history != null) {
                for (i in 0 until history.length()) {
                    try {
                        val c = Change.from(history.getJSONObject(i))
                        allChanges.add(c)
                        if ("ocena" == c.kind) countGrades++
                        else if ("plan" == c.kind) countPlan++
                        when (c.action) {
                            "Dodano" -> countAdded++
                            "Zmieniono" -> countModified++
                            "Usunięto" -> countRemoved++
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            allChanges.sortWith { a, b -> b.effectiveTimestamp().compareTo(a.effectiveTimestamp()) }

            val filters = arrayOf("wszystkie", "ocena", "plan", "Dodano", "Zmieniono", "Usunięto")
            val labels = arrayOf(
                activity.getString(R.string.history_chip_all, allChanges.size),
                activity.getString(R.string.history_chip_grades, countGrades),
                activity.getString(R.string.history_chip_plan, countPlan),
                activity.getString(R.string.history_chip_added, countAdded),
                activity.getString(R.string.history_chip_modified, countModified),
                activity.getString(R.string.history_chip_removed, countRemoved)
            )
            val header = LinearLayout(activity)
            header.gravity = Gravity.CENTER_VERTICAL
            val title = UiHelper.text(activity, activity.getString(R.string.history_title), 20, UiHelper.INK, true)
            title.setPadding(UiHelper.dp(activity, 8), 0, 0, 0)
            title.minHeight = UiHelper.dp(activity, 64)
            header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            title.gravity = Gravity.CENTER_VERTICAL
            val more = ScheduleScreenController.headerIcon(activity, R.drawable.ic_schedule_more) {}
            more.id = R.id.history_more
            more.contentDescription = activity.getString(R.string.history_options)
            header.addView(more, LinearLayout.LayoutParams(UiHelper.dp(activity, 56), -2))
            content.addView(header)
            if (filter != "wszystkie") {
                for (i in filters.indices) {
                    if (filters[i] == filter) {
                        val activeFilter = ScheduleScreenController.headerButton(activity, "${labels[i]} ×") {
                            onFilterChanged.accept("wszystkie")
                        }
                        activeFilter.textSize = 14f
                        activeFilter.isSelected = true
                        activeFilter.contentDescription = "${labels[i]}. ${activity.getString(R.string.history_clear_filter)}"
                        content.addView(activeFilter)
                    }
                }
            }

            val rows = ArrayList<Change>()
            for (c in allChanges) {
                if (filter == "wszystkie" || filter == c.kind || filter == c.action) {
                    rows.add(c)
                }
            }

            val search = UiHelper.searchField(
                activity,
                content,
                activity.getString(R.string.history_search_hint),
                query,
                null
            ) { onQueryChanged.accept("") }

            val searchBox = content.getChildAt(content.childCount - 1)
            searchBox.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            more.setOnClickListener {
                val menu = PopupMenu(activity, more)
                for (i in filters.indices) {
                    menu.menu.add(1, i + 1, i, labels[i]).setCheckable(true).isChecked = filters[i] == filter
                }
                menu.menu.setGroupCheckable(1, true, true)
                menu.menu.add(2, 7, 7, R.string.history_search_action)
                if (demo && onSimulate != null) {
                    menu.menu.add(2, 8, 8, R.string.history_btn_simulate)
                }
                menu.setOnMenuItemClickListener { item ->
                    val id = item.itemId
                    when {
                        id in 1..filters.size -> onFilterChanged.accept(filters[id - 1])
                        id == 7 -> {
                            searchBox.visibility = View.VISIBLE
                            search.requestFocus()
                            search.post {
                                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                    .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                        id == 8 -> onSimulate?.run()
                    }
                    true
                }
                menu.show()
            }

            val list = LinearLayout(activity)
            list.orientation = LinearLayout.VERTICAL
            content.addView(list)

            val populate = Runnable {
                list.removeAllViews()
                var count = 0
                val currentQuery = search.text.toString()
                var sectionDay: LocalDate? = null

                for (c in rows) {
                    val diffList = getDiffSummaryList(activity, c)
                    val diffSummaryText = diffList.joinToString(" ")
                    if (matches(
                            currentQuery,
                            c.title,
                            c.before,
                            c.after,
                            c.action,
                            c.sourceTime,
                            c.date,
                            c.beforeDate,
                            c.afterDate,
                            eventDateDescription(activity, c),
                            formatFullDate(c.date),
                            diffSummaryText,
                            c.optMeta("courseId"),
                            c.optMeta("room"),
                            c.optMeta("lecturers"),
                            c.optMeta("comment"),
                            c.optMeta("valueDescription"),
                            c.optMeta("term"),
                            c.optBeforeMeta("room"),
                            c.optAfterMeta("room"),
                            c.optBeforeMeta("comment"),
                            c.optAfterMeta("comment"),
                            c.optBeforeMeta("valueDescription"),
                            c.optAfterMeta("valueDescription"),
                            c.optBeforeMeta("lecturers"),
                            c.optAfterMeta("lecturers"),
                            c.optBeforeMeta("building"),
                            c.optAfterMeta("building")
                        )
                    ) {
                        val day = if (c.effectiveTimestamp() > 0) {
                            Instant.ofEpochMilli(c.effectiveTimestamp()).atZone(UsosClient.ZONE).toLocalDate()
                        } else {
                            null
                        }
                        if (day != null && day != sectionDay) {
                            val today = LocalDate.now(UsosClient.ZONE)
                            val headerText = when (day) {
                                today -> activity.getString(R.string.date_heading_today, UiHelper.shortDate(day))
                                today.minusDays(1) -> activity.getString(R.string.date_heading_yesterday, UiHelper.shortDate(day))
                                else -> UiHelper.shortDate(day)
                            }
                            UiHelper.heading(activity, list, headerText)
                            sectionDay = day
                        }
                        renderChangeCard(activity, list, c)
                        ++count
                    }
                }

                if (count == 0) {
                    val box = UiHelper.card(activity, list, Color.WHITE)
                    UiHelper.add(
                        activity,
                        box,
                        if (currentQuery.trim().isEmpty()) {
                            activity.getString(R.string.history_empty_title)
                        } else {
                            activity.getString(R.string.history_no_match_title)
                        },
                        17,
                        UiHelper.INK,
                        true
                    )
                    UiHelper.add(
                        activity,
                        box,
                        if (currentQuery.trim().isEmpty()) {
                            activity.getString(R.string.history_empty_desc)
                        } else {
                            activity.getString(R.string.history_no_match_desc)
                        },
                        14,
                        UiHelper.MUTED,
                        false
                    )
                    if (currentQuery.trim().isNotEmpty()) {
                        box.addView(
                            UiHelper.button(
                                activity,
                                activity.getString(R.string.btn_clear_search),
                                {
                                    search.setText("")
                                    onQueryChanged.accept("")
                                },
                                false
                            )
                        )
                    } else if (filter != "wszystkie") {
                        box.addView(
                            UiHelper.button(
                                activity,
                                activity.getString(R.string.history_show_all),
                                { onFilterChanged.accept("wszystkie") },
                                false
                            )
                        )
                    } else if (demo && onSimulate != null) {
                        box.addView(UiHelper.button(activity, activity.getString(R.string.history_btn_simulate), onSimulate, true))
                    }
                }
            }

            UiHelper.watchSearch(search) { value ->
                onQueryChanged.accept(value)
                populate.run()
            }
            populate.run()
        }

        @JvmStatic
        fun formatFullDate(dateStr: String?): String {
            return formatFullDate(Language.currentLocale(null), dateStr)
        }

        @JvmStatic
        fun formatFullDate(locale: Locale?, dateStr: String?): String {
            if (dateStr == null || dateStr.trim().isEmpty()) return ""
            val s = dateStr.trim()
            try {
                val loc = locale ?: Language.currentLocale(null)
                if (s.length >= 19 && (s.contains(" ") || s.contains("T"))) {
                    val normalized = s.substring(0, 19).replace('T', ' ')
                    val ldt = LocalDateTime.parse(normalized, UsosParser.TIME)
                    val dayName = ldt.format(DateTimeFormatter.ofPattern("EEEE", loc))
                    val capDay = if (dayName.isEmpty()) "" else dayName.substring(0, 1).uppercase(loc) + dayName.substring(1)
                    return "$capDay, ${ldt.format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", loc))}"
                }
                if (s.length >= 10) {
                    val ld = LocalDate.parse(s.substring(0, 10))
                    val dayName = ld.format(DateTimeFormatter.ofPattern("EEEE", loc))
                    val capDay = if (dayName.isEmpty()) "" else dayName.substring(0, 1).uppercase(loc) + dayName.substring(1)
                    return "$capDay, ${ld.format(DateTimeFormatter.ofPattern("d MMMM yyyy", loc))}"
                }
            } catch (_: Exception) {
            }
            return s
        }

        @JvmStatic
        fun eventDateDescription(context: Context, c: Change): String {
            val locale = Language.currentLocale(context)
            if (c.kind == "plan") {
                if (c.beforeDate.isNotEmpty() && c.afterDate.isNotEmpty() && c.beforeDate != c.afterDate) {
                    return context.getString(
                        R.string.history_item_plan_date_diff,
                        formatFullDate(locale, c.beforeDate),
                        formatFullDate(locale, c.afterDate)
                    )
                }
                val date = when {
                    c.date.isNotEmpty() -> c.date
                    c.afterDate.isNotEmpty() -> c.afterDate
                    else -> c.beforeDate
                }
                if (date.isNotEmpty()) {
                    return context.getString(R.string.history_item_plan_date, formatFullDate(locale, date))
                }
            } else {
                val date = when {
                    c.date.isNotEmpty() -> c.date
                    c.sourceTime.isNotEmpty() -> c.sourceTime
                    c.afterDate.isNotEmpty() -> c.afterDate
                    else -> c.beforeDate
                }
                if (date.isNotEmpty()) {
                    return context.getString(R.string.history_item_grade_date, formatFullDate(locale, date))
                }
            }
            return ""
        }

        @JvmStatic
        fun formatRelativeTime(millis: Long): String {
            return formatRelativeTime(null, millis)
        }

        @JvmStatic
        fun formatRelativeTime(context: Context?, millis: Long): String {
            if (millis <= 0) return ""
            return try {
                val event = Instant.ofEpochMilli(millis)
                val now = Instant.now()
                val diffSec = Duration.between(event, now).seconds
                if (diffSec in 0..59) {
                    return context?.getString(R.string.time_just_now) ?: "przed chwilą"
                }
                if (diffSec in 60..3599) {
                    return context?.getString(R.string.time_minutes_ago, (diffSec / 60).toInt()) ?: "${diffSec / 60} min temu"
                }

                val eventDate = event.atZone(UsosClient.ZONE).toLocalDate()
                val today = LocalDate.now(UsosClient.ZONE)
                val locale = Language.currentLocale(context)
                val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)
                val timeStr = event.atZone(UsosClient.ZONE).format(timeFmt)
                if (eventDate == today) {
                    return context?.getString(R.string.history_time_today, timeStr) ?: "Dzisiaj, $timeStr"
                }
                if (eventDate == today.minusDays(1)) {
                    return context?.getString(R.string.history_time_yesterday, timeStr) ?: "Wczoraj, $timeStr"
                }
                event.atZone(UsosClient.ZONE).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", locale))
            } catch (_: Exception) {
                ""
            }
        }

        @JvmStatic
        fun getDiffSummaryList(context: Context, c: Change): List<String> {
            val diffs = ArrayList<String>()
            val diffGrade = c.optMeta("diffGrade")
            if ("ocena" == c.kind && diffGrade.isNotEmpty()) {
                diffs.add("🎓 ${context.getString(R.string.change_grade)}: $diffGrade")
            }
            val diffRoom = c.optMeta("diffRoom")
            if (diffRoom.isNotEmpty()) {
                diffs.add("📍 ${context.getString(R.string.change_room)}: $diffRoom")
            }
            val diffTime = c.optMeta("diffTime")
            if (diffTime.isNotEmpty()) {
                diffs.add("⏰ ${context.getString(R.string.change_time)}: $diffTime")
            }
            val diffDate = c.optMeta("diffDate")
            if (diffDate.isNotEmpty()) {
                if (diffDate.contains(" → ")) {
                    val parts = diffDate.split(" → ")
                    if (parts.size == 2) {
                        diffs.add("📅 ${context.getString(R.string.change_date)}: ${formatFullDate(parts[0])} → ${formatFullDate(parts[1])}")
                    } else {
                        diffs.add("📅 ${context.getString(R.string.change_date)}: $diffDate")
                    }
                } else {
                    diffs.add("📅 ${context.getString(R.string.change_date)}: ${formatFullDate(diffDate)}")
                }
            }
            val diffLecturers = c.optMeta("diffLecturers")
            if (diffLecturers.isNotEmpty()) {
                diffs.add("👨‍🏫 ${context.getString(R.string.change_lecturers)}: $diffLecturers")
            }
            val diffGroup = c.optMeta("diffGroup")
            if (diffGroup.isNotEmpty()) {
                diffs.add("👥 ${context.getString(R.string.change_group)}: $diffGroup")
            }
            val diffBuilding = c.optMeta("diffBuilding")
            if (diffBuilding.isNotEmpty()) {
                diffs.add("🏢 ${context.getString(R.string.change_building)}: $diffBuilding")
            }
            val diffClassType = c.optMeta("diffClassType")
            if (diffClassType.isNotEmpty()) {
                diffs.add("📚 ${context.getString(R.string.change_classtype)}: $diffClassType")
            }
            val diffPasses = c.optMeta("diffPasses")
            if (diffPasses.isNotEmpty()) {
                diffs.add("🎯 ${context.getString(R.string.change_passes)}: $diffPasses")
            }
            val diffCounts = c.optMeta("diffCountsIntoAverage")
            if (diffCounts.isNotEmpty()) {
                diffs.add("⚖️ ${context.getString(R.string.change_counts_average)}: $diffCounts")
            }
            val diffValueDesc = c.optMeta("diffValueDescription")
            if (diffValueDesc.isNotEmpty()) {
                diffs.add("📝 ${context.getString(R.string.change_value_description)}: $diffValueDesc")
            }
            val diffEcts = c.optMeta("diffEcts")
            if (diffEcts.isNotEmpty()) {
                diffs.add("🪙 ${context.getString(R.string.change_ects)}: $diffEcts")
            }
            val diffComment = c.optMeta("diffComment")
            if (diffComment.isNotEmpty()) {
                diffs.add("💬 ${context.getString(R.string.change_comment)}: $diffComment")
            }
            return diffs
        }

        @JvmStatic
        fun renderChangeCard(activity: MainActivity, parent: LinearLayout, c: Change) {
            val box = UiHelper.card(activity, parent, Color.WHITE)
            box.isClickable = true
            box.isFocusable = true

            val category = activity.getString(if (c.kind == "ocena") R.string.history_category_grade else R.string.history_category_plan)
            val action = activity.getString(
                when (c.action) {
                    "Dodano" -> R.string.history_action_added
                    "Usunięto" -> R.string.history_action_removed
                    else -> R.string.history_action_modified
                }
            )
            val time = formatRelativeTime(activity, c.effectiveTimestamp())
            UiHelper.add(
                activity,
                box,
                "$category · $action" + if (time.isEmpty()) "" else " · $time",
                12,
                UiHelper.MUTED,
                false
            )
            UiHelper.add(activity, box, c.title, 17, UiHelper.INK, true)

            val diffs = getDiffSummaryList(activity, c)
            if (c.action == "Zmieniono" && diffs.isNotEmpty()) {
                for (i in 0 until min(3, diffs.size)) {
                    UiHelper.add(activity, box, diffs[i].replaceFirst(Regex("^[^\\p{L}]+"), ""), 14, UiHelper.INK, false)
                }
            } else {
                val value = when (c.action) {
                    "Zmieniono" -> "${c.before} → ${c.after}"
                    "Usunięto" -> if (c.before.isEmpty()) c.after else c.before
                    else -> if (c.after.isEmpty()) c.before else c.after
                }
                if (value.isNotEmpty()) {
                    UiHelper.add(activity, box, value, 14, UiHelper.INK, false)
                }
            }
            val eventDate = eventDateDescription(activity, c)
            if (eventDate.isNotEmpty()) {
                UiHelper.add(activity, box, eventDate, 12, UiHelper.MUTED, false)
            }
            ViewCompat.setScreenReaderFocusable(box, true)
            ViewCompat.replaceAccessibilityAction(
                box,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                activity.getString(R.string.history_btn_details)
            ) { _, _ ->
                showChangeDetails(activity, c)
                true
            }
            box.setOnClickListener { showChangeDetails(activity, c) }
        }

        @JvmStatic
        fun showChangeDetails(activity: MainActivity, c: Change) {
            val box = LinearLayout(activity)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(
                UiHelper.dp(activity, 24),
                UiHelper.dp(activity, 8),
                UiHelper.dp(activity, 24),
                UiHelper.dp(activity, 8)
            )

            UiHelper.add(activity, box, c.title, 20, UiHelper.INK, true)
            UiHelper.add(activity, box, "Akcja: ${c.action} · Typ: ${c.kind}", 13, UiHelper.GREEN, true)

            val eventDateText = eventDateDescription(activity, c)
            if (eventDateText.isNotEmpty()) {
                UiHelper.add(activity, box, "📅 $eventDateText", 14, UiHelper.GREEN, true)
            }

            if (c.before.isNotEmpty()) {
                UiHelper.add(activity, box, activity.getString(R.string.history_value_before, c.before), 14, UiHelper.MUTED, false)
            }
            if (c.after.isNotEmpty()) {
                UiHelper.add(activity, box, activity.getString(R.string.history_value_after, c.after), 14, UiHelper.INK, true)
            }
            val metadataDetails = changeMetadata(activity, c)
            if (metadataDetails.isNotEmpty()) {
                UiHelper.add(activity, box, metadataDetails, 14, UiHelper.INK, false)
            }

            val detailsLocale = Language.currentLocale(activity)
            val detectedTime = if (c.detectedAt > 0) {
                Instant.ofEpochMilli(c.detectedAt).atZone(UsosClient.ZONE)
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss", detailsLocale))
            } else {
                c.date
            }
            UiHelper.add(activity, box, activity.getString(R.string.history_detected_prefix, detectedTime), 12, UiHelper.MUTED, false)
            if (c.sourceTime.isNotEmpty()) {
                UiHelper.add(activity, box, activity.getString(R.string.history_server_time, c.sourceTime), 12, UiHelper.MUTED, false)
            }

            val fullShareText = buildShareText(activity, c, eventDateText, metadataDetails, detectedTime)
            box.addView(
                UiHelper.button(
                    activity,
                    "📤 ${activity.getString(R.string.share_change_btn)}",
                    {
                        val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                        share.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.history_share_subject, c.title, c.action))
                        share.putExtra(Intent.EXTRA_TEXT, fullShareText)
                        try {
                            activity.startActivity(Intent.createChooser(share, activity.getString(R.string.share_change_title)))
                        } catch (_: Exception) {
                            val cb = activity.getSystemService(ClipboardManager::class.java)
                            if (cb != null) {
                                cb.setPrimaryClip(UiHelper.privateClip(activity.getString(R.string.history_clipboard_label), fullShareText))
                                Toast.makeText(activity, R.string.toast_history_details_copied, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    false
                )
            )

            try {
                val allData = UiHelper.text(activity, c.json().toString(2), 12, UiHelper.INK, false)
                allData.setTextIsSelectable(true)
                allData.visibility = View.GONE
                box.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.grades_full_data),
                        { allData.visibility = if (allData.visibility == View.GONE) View.VISIBLE else View.GONE },
                        false
                    )
                )
                box.addView(allData)
            } catch (_: Exception) {
            }

            val scroll = UiHelper.dialogScroll(activity, box)

            MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.history_details_title))
                .setView(scroll)
                .setPositiveButton(activity.getString(R.string.btn_close), null)
                .setNeutralButton(activity.getString(R.string.history_details_copy)) { _, _ ->
                    val cb = activity.getSystemService(ClipboardManager::class.java)
                    if (cb != null) {
                        cb.setPrimaryClip(UiHelper.privateClip(activity.getString(R.string.history_clipboard_label), fullShareText))
                        Toast.makeText(activity, R.string.toast_history_details_copied, Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        private fun buildShareText(
            activity: MainActivity,
            c: Change,
            eventDateText: String,
            metadataDetails: String,
            detectedTime: String
        ): String {
            val sb = StringBuilder()
            sb.append("🔔 ").append(c.title).append("\n")
            sb.append(activity.getString(R.string.history_share_subject, c.title, c.action)).append("\n")
            if (eventDateText.isNotEmpty()) sb.append("📅 ").append(eventDateText).append("\n")
            if (c.before.isNotEmpty()) sb.append(activity.getString(R.string.history_value_before, c.before)).append("\n")
            if (c.after.isNotEmpty()) sb.append(activity.getString(R.string.history_value_after, c.after)).append("\n")
            if (metadataDetails.isNotEmpty()) sb.append(metadataDetails).append("\n")
            sb.append("Wykryto: ").append(detectedTime).append("\n")
            if (c.sourceTime.isNotEmpty()) sb.append("USOS: ").append(c.sourceTime).append("\n")
            sb.append("\n— ").append(activity.getString(R.string.history_share_footer))
            return sb.toString().trim()
        }

        private fun changeMetadata(activity: MainActivity, change: Change): String {
            val text = StringBuilder()
            val diffs = getDiffSummaryList(activity, change)
            if (diffs.isNotEmpty()) {
                for (diff in diffs) {
                    text.append(diff).append("\n")
                }
            }
            val keys = arrayOf("room", "building", "lecturers", "groupNumber", "classType", "comment", "valueDescription", "term")
            val labels = arrayOf(
                activity.getString(R.string.change_room),
                activity.getString(R.string.change_building),
                activity.getString(R.string.change_lecturers),
                activity.getString(R.string.change_group),
                activity.getString(R.string.change_classtype),
                activity.getString(R.string.change_comment),
                activity.getString(R.string.change_value_description),
                activity.getString(R.string.change_term)
            )
            for (i in keys.indices) {
                val before = change.optBeforeMeta(keys[i])
                val after = change.optAfterMeta(keys[i])
                if (before.isEmpty() && after.isEmpty()) continue
                when (change.action) {
                    "Zmieniono" -> {
                        if (before != after) continue // Already in diffs
                        text.append(labels[i]).append(": ").append(after).append("\n")
                    }
                    "Dodano" -> {
                        val value = if (after.isNotEmpty()) after else before
                        if (value.isNotEmpty()) text.append(labels[i]).append(": ").append(value).append("\n")
                    }
                    "Usunięto" -> {
                        val value = if (before.isNotEmpty()) before else after
                        if (value.isNotEmpty()) text.append(labels[i]).append(": ").append(value).append("\n")
                    }
                }
            }
            if (change.beforeDate.isNotEmpty() && change.afterDate.isNotEmpty() && change.beforeDate != change.afterDate) {
                text.append(activity.getString(R.string.change_date))
                    .append(": ").append(formatFullDate(change.beforeDate))
                    .append(" → ").append(formatFullDate(change.afterDate)).append("\n")
            } else if (change.date.isNotEmpty()) {
                text.append(activity.getString(R.string.change_date))
                    .append(": ").append(formatFullDate(change.date)).append("\n")
            } else if (change.afterDate.isNotEmpty()) {
                text.append(activity.getString(R.string.change_date))
                    .append(": ").append(formatFullDate(change.afterDate)).append("\n")
            }
            return text.toString().trim()
        }

        private fun matches(query: String?, vararg values: String?): Boolean {
            return StudentSearch.matches(query ?: "", *Array(values.size) { values[it] ?: "" })
        }
    }
}

