package pl.vistula.checker.ui.compose

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import pl.vistula.checker.core.LessonTime

data class PlacedLesson(
    val lesson: LessonTime,
    val startMinutes: Int,
    val endMinutes: Int,
    val column: Int,
    val columnCount: Int
)

object TimelineLanePlanner {
    @JvmStatic
    fun hourBounds(lessons: List<LessonTime>, day: LocalDate): IntArray {
        var first = 8
        var last = 20
        for (lesson in lessons) {
            val startMin = if (lesson.start.toLocalDate().isBefore(day)) 0 else lesson.start.hour * 60 + lesson.start.minute
            val endMin = if (lesson.end.toLocalDate().isAfter(day)) 1440 else lesson.end.hour * 60 + lesson.end.minute
            first = min(first, startMin / 60)
            last = max(last, (endMin + 59) / 60)
        }
        val clampedFirst = first.coerceIn(0, 22)
        val clampedLast = last.coerceIn(clampedFirst + 1, 24)
        return intArrayOf(clampedFirst, clampedLast)
    }

    @JvmStatic
    fun placeLessons(lessons: List<LessonTime>, day: LocalDate): List<PlacedLesson> {
        if (lessons.isEmpty()) return emptyList()
        class MutableItem(
            val lesson: LessonTime,
            val start: Int,
            val end: Int,
            var column: Int = 0,
            var columnCount: Int = 1
        )

        val items = lessons.map { lesson ->
            val startMin = if (lesson.start.toLocalDate().isBefore(day)) 0 else lesson.start.hour * 60 + lesson.start.minute
            val endMin = if (lesson.end.toLocalDate().isAfter(day)) 1440 else lesson.end.hour * 60 + lesson.end.minute
            MutableItem(lesson, startMin, max(startMin + 15, endMin))
        }.sortedBy { it.start }

        val activeGroup = ArrayList<MutableItem>()
        var groupEnd = -1

        fun finishGroup(group: List<MutableItem>) {
            var cols = 1
            for (item in group) cols = max(cols, item.column + 1)
            for (item in group) item.columnCount = cols
        }

        for (item in items) {
            if (item.start >= groupEnd && activeGroup.isNotEmpty()) {
                finishGroup(activeGroup)
                activeGroup.clear()
            }
            val occupied = HashSet<Int>()
            for (prev in activeGroup) {
                if (prev.end > item.start) occupied.add(prev.column)
            }
            while (occupied.contains(item.column)) {
                item.column++
            }
            activeGroup.add(item)
            groupEnd = max(groupEnd, item.end)
        }
        finishGroup(activeGroup)

        return items.map { PlacedLesson(it.lesson, it.start, it.end, it.column, it.columnCount) }
    }
}

