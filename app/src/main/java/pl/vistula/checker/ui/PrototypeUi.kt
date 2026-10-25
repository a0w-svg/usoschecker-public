package pl.vistula.checker.ui

import androidx.core.content.withStyledAttributes
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import pl.vistula.checker.R
import pl.vistula.checker.core.LessonTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Shared native geometry from the supplied prototype: small weekday, circular date, quiet dots. */
internal object PrototypeUi {
    fun week(context: Context, monday: LocalDate, selected: LocalDate, lessons: List<LessonTime>, large: Boolean, onSelect: (LocalDate) -> Unit): HorizontalScrollView {
        val viewport = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; isFillViewport = true }
        val row = LinearLayout(context).apply {
            minimumWidth = UiHelper.dp(context, maxOf(48, (42 * context.resources.configuration.fontScale).toInt()) * 7)
        }
        viewport.addView(row)
        val locale = Language.currentLocale(context)
        repeat(7) { index ->
            val date = monday.plusDays(index.toLong())
            val count = lessons.count { it.intersects(date) && !it.tentative() }
            val active = date == selected
            val day = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                minimumWidth = UiHelper.dp(context, 48); minimumHeight = UiHelper.dp(context, 72)
                setPadding(0, UiHelper.dp(context, 6), 0, UiHelper.dp(context, 6))
                isClickable = true; isFocusable = true; isSelected = active
                contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)) + ": " + context.getString(R.string.design_class_count, count)
                context.withStyledAttributes(attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)) { foreground = getDrawable(0) }
                setOnClickListener { onSelect(date) }
            }
            day.addView(UiHelper.text(context, date.format(DateTimeFormatter.ofPattern("EEE", locale)).trimEnd('.').uppercase(locale), 11, UiHelper.MUTED, false).apply {
                letterSpacing = .08f; gravity = Gravity.CENTER
            })
            val size = UiHelper.dp(context, if (large) 38 else 34)
            day.addView(UiHelper.text(context, date.dayOfMonth.toString(), 15, if (active) UiHelper.INK else UiHelper.MUTED, active).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (active) context.getColor(R.color.app_secondary_container) else Color.TRANSPARENT)
                }
            }, LinearLayout.LayoutParams(size, size).apply { topMargin = UiHelper.dp(context, 4) })
            val dots = LinearLayout(context).apply { gravity = Gravity.CENTER }
            repeat(minOf(4, count)) { dot ->
                dots.addView(View(context).apply { background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(context.getColor(if (active) R.color.app_ink else R.color.app_faint))
                } }, LinearLayout.LayoutParams(UiHelper.dp(context, 4), UiHelper.dp(context, 4)).apply { if (dot > 0) marginStart = UiHelper.dp(context, 3) })
            }
            day.addView(dots, LinearLayout.LayoutParams(-2, UiHelper.dp(context, 4)).apply { topMargin = UiHelper.dp(context, 4) })
            androidx.core.view.ViewCompat.setScreenReaderFocusable(day, true)
            row.addView(day, LinearLayout.LayoutParams(0, -2, 1f).apply { width = UiHelper.dp(context, maxOf(48, (42 * context.resources.configuration.fontScale).toInt())) })
        }
        return viewport
    }
}
