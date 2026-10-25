package pl.vistula.checker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import pl.vistula.checker.core.LessonTime
import pl.vistula.checker.ui.UiHelper

/** Scrollable day grid with separate lanes for overlapping classes. */
class DayTimelineView private constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    private val date: LocalDate,
    lessons: List<LessonTime>,
    click: Consumer<LessonTime>
) : FrameLayout(context, attrs, defStyleAttr) {
    private val items: MutableList<Item> = ArrayList()
    private val firstHour: Int
    private val lastHour: Int

    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : this(context, attrs, defStyleAttr, LocalDate.now(LessonTime.ZONE), emptyList(), Consumer {})

    constructor(
        context: Context,
        date: LocalDate,
        lessons: List<LessonTime>,
        click: Consumer<LessonTime>
    ) : this(context, null, 0, date, lessons, click)

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun dp(value: Int): Int = dp(value.toFloat())

    private class Item {
        lateinit var lesson: LessonTime
        var start: Int = 0
        var end: Int = 0
        var column: Int = 0
        var columns: Int = 1
        lateinit var view: TextView
    }

    init {
        var first = 8
        var last = 20
        for (lesson in lessons) {
            val item = Item()
            item.lesson = lesson
            item.start = if (lesson.start.toLocalDate().isBefore(date)) 0 else lesson.start.hour * 60 + lesson.start.minute
            item.end = if (lesson.end.toLocalDate().isAfter(date)) 1440 else lesson.end.hour * 60 + lesson.end.minute
            first = min(first, item.start / 60)
            last = max(last, (item.end + 59) / 60)
            items.add(item)
        }
        firstHour = first
        lastHour = min(24, max(first + 1, last))
        minimumHeight = dp((lastHour - firstHour) * HOUR_HEIGHT + 24)
        layoutParams = LinearLayout.LayoutParams(-1, dp((lastHour - firstHour) * HOUR_HEIGHT + 24))
        addView(Lines(context, false), LayoutParams(-1, -1))
        items.sortBy { it.start }
        val group = ArrayList<Item>()
        var groupEnd = -1
        for (item in items) {
            if (item.start >= groupEnd && group.isNotEmpty()) {
                finishGroup(group)
                group.clear()
            }
            val occupied = HashSet<Int>()
            for (previous in group) {
                if (previous.end > item.start) occupied.add(previous.column)
            }
            while (occupied.contains(item.column)) item.column++
            group.add(item)
            groupEnd = max(groupEnd, item.end)
            val block = TextView(context)
            item.view = block
            val current = item.lesson.current(Instant.now()) && !item.lesson.tentative()
            block.setTextColor(UiHelper.foreground(context, Color.rgb(20, 64, 49)))
            block.textSize = 12f
            block.setPadding(dp(8), dp(4), dp(6), dp(4))
            block.gravity = Gravity.TOP
            block.ellipsize = TextUtils.TruncateAt.END
            val hours = item.lesson.start.format(CLOCK) + " – " + item.lesson.end.format(CLOCK)
            var room = ""
            val roomNumber = item.lesson.record.optMeta("room")
            if (roomNumber.isNotEmpty()) {
                room = context.getString(R.string.lesson_room_label, roomNumber)
            } else {
                for (line in item.lesson.record.value.split("\n")) {
                    if (line.startsWith("Sala ")) room = line
                }
            }
            var label = (if (item.lesson.tentative()) "Orientacyjne · " else "") + item.lesson.record.title
            val height = dp((item.end - item.start) * HOUR_HEIGHT / 60f)
            if (height >= dp(42)) label += "\n$hours"
            if (height >= dp(72) && room.isNotEmpty()) label += "\n$room"
            block.text = label
            block.maxLines = if (height >= dp(72)) 4 else if (height >= dp(42)) 2 else 1
            block.contentDescription = (if (current) "Teraz. " else "") + item.lesson.record.title + ". " + item.lesson.record.value
            val background = GradientDrawable()
            background.setColor(
                UiHelper.background(
                    context,
                    if (current) Color.rgb(190, 232, 211)
                    else if (item.lesson.tentative()) Color.rgb(251, 240, 217)
                    else Color.rgb(232, 243, 236)
                )
            )
            background.cornerRadius = dp(10).toFloat()
            background.setStroke(
                dp(if (current) 2 else 1),
                if (current) Color.rgb(23, 107, 87)
                else if (item.lesson.tentative()) Color.rgb(183, 141, 59)
                else Color.rgb(186, 210, 195)
            )
            block.background = background
            block.isClickable = true
            block.isFocusable = true
            block.setOnClickListener { click.accept(item.lesson) }
            addView(block, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        finishGroup(group)
        addView(Lines(context, true), LayoutParams(-1, -1))
    }

    private fun finishGroup(group: List<Item>) {
        var columns = 1
        for (item in group) columns = max(columns, item.column + 1)
        for (item in group) item.columns = columns
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        for (item in items) {
            val width = max(dp(24), (w - dp(GUTTER + 8)) / item.columns)
            val targetWidth = max(dp(20), width - dp(4))
            val targetHeight = max(dp(24), dp((item.end - item.start) * HOUR_HEIGHT / 60f) - dp(2))
            val targetLeft = dp(GUTTER) + item.column * width
            val targetTop = dp((item.start - firstHour * 60) * HOUR_HEIGHT / 60f + 8)
            val params = item.view.layoutParams as LayoutParams
            params.width = targetWidth
            params.height = targetHeight
            params.leftMargin = targetLeft
            params.topMargin = targetTop
        }
        super.onMeasure(widthSpec, heightSpec)
    }

    fun nowOffset(): Int =
        dp((LocalTime.now(LessonTime.ZONE).hour * 60 + LocalTime.now(LessonTime.ZONE).minute - firstHour * 60) * HOUR_HEIGHT / 60f + 8)

    private inner class Lines(context: Context, private val nowOnly: Boolean) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val hourLabels = Array(lastHour - firstHour + 1) { i ->
            String.format(Locale.ROOT, "%02d:00", firstHour + i)
        }
        private val labelColor = UiHelper.foreground(context, Color.rgb(104, 119, 110))
        private val outlineColor = context.getColor(R.color.app_outline_soft)
        private val nowColor = UiHelper.foreground(context, Color.rgb(192, 49, 49))

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            if (!nowOnly) {
                paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
                for (hour in firstHour..lastHour) {
                    val y = dp((hour - firstHour) * HOUR_HEIGHT + 8).toFloat()
                    paint.color = labelColor
                    canvas.drawText(hourLabels[hour - firstHour], dp(1).toFloat(), y + dp(4), paint)
                    paint.color = outlineColor
                    paint.strokeWidth = dp(1).toFloat()
                    canvas.drawLine(dp(GUTTER).toFloat(), y, width.toFloat(), y, paint)
                }
            } else if (date == LocalDate.now(LessonTime.ZONE)) {
                val y = nowOffset().toFloat()
                if (y >= 0 && y <= height) {
                    paint.color = nowColor
                    paint.strokeWidth = dp(2).toFloat()
                    canvas.drawCircle(dp(GUTTER).toFloat(), y, dp(4).toFloat(), paint)
                    canvas.drawLine(dp(GUTTER).toFloat(), y, width.toFloat(), y, paint)
                }
            }
        }
    }

    companion object {
        private const val HOUR_HEIGHT = 72
        private const val GUTTER = 48
        private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

