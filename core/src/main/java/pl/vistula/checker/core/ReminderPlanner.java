package pl.vistula.checker.core;

import java.time.Instant;
import java.util.Set;

/** Selects the next confirmed, future class that has not already produced a reminder. */
public final class ReminderPlanner {
    private ReminderPlanner() {}
    public static String key(LessonTime lesson) { return lesson.record.id + "@" + lesson.start.toInstant(); }
    public static LessonTime next(Snapshot snapshot, Set<String> delivered, Instant now) {
        for (LessonTime lesson : LessonTime.sorted(snapshot.records.values()))
            if (!lesson.tentative() && lesson.start.toInstant().isAfter(now) && !delivered.contains(key(lesson))) return lesson;
        return null;
    }
    public static long triggerAt(LessonTime lesson, int minutesBefore, Instant now) {
        int lead = minutesBefore >= 1 && minutesBefore <= 120 ? minutesBefore : 15;
        return Math.max(now.toEpochMilli() + 1000, lesson.start.toInstant().toEpochMilli() - lead * 60000L);
    }
}
