package pl.vistula.checker.core

import java.time.Instant
import kotlin.math.max

/** Selects the next confirmed, future class that has not already produced a reminder. */
object ReminderPlanner {
    @JvmStatic
    fun key(lesson: LessonTime): String = "${lesson.record.id}@${lesson.start.toInstant()}"

    @JvmStatic
    fun next(snapshot: Snapshot, delivered: Set<String>, now: Instant): LessonTime? {
        for (lesson in LessonTime.sorted(snapshot.records.values)) {
            if (!lesson.tentative() && lesson.start.toInstant().isAfter(now) && !delivered.contains(key(lesson))) {
                return lesson
            }
        }
        return null
    }

    @JvmStatic
    fun triggerAt(lesson: LessonTime, minutesBefore: Int, now: Instant): Long {
        val lead = if (minutesBefore in 1..120) minutesBefore else 15
        return max(now.toEpochMilli() + 1000, lesson.start.toInstant().toEpochMilli() - lead * 60000L)
    }
}

