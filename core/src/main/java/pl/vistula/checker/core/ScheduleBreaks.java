package pl.vistula.checker.core;

import java.time.*;
import java.util.*;

/** Free intervals between the union of all meetings within one Warsaw calendar day. */
public final class ScheduleBreaks {
    private ScheduleBreaks() {}
    public static final class Break {
        public final ZonedDateTime start, end;
        public final long minutes;
        private Break(Instant start, Instant end) {
            this.start = start.atZone(LessonTime.ZONE);
            this.end = end.atZone(LessonTime.ZONE);
            minutes = Duration.between(start, end).toMinutes();
        }
    }
    public static List<Break> inDay(Collection<LessonTime> lessons, LocalDate day) {
        Instant dayStart = day.atStartOfDay(LessonTime.ZONE).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(LessonTime.ZONE).toInstant();
        List<Instant[]> intervals = new ArrayList<>();
        for (LessonTime lesson : lessons) if (lesson.intersects(day)) {
            intervals.add(new Instant[]{lesson.start.toInstant().isBefore(dayStart) ? dayStart : lesson.start.toInstant(),
                lesson.end.toInstant().isAfter(dayEnd) ? dayEnd : lesson.end.toInstant()});
        }
        intervals.sort(Comparator.comparing(interval -> interval[0]));
        List<Break> result = new ArrayList<>();
        Instant occupiedUntil = null;
        for (Instant[] interval : intervals) {
            if (occupiedUntil != null && interval[0].isAfter(occupiedUntil)) {
                Break gap = new Break(occupiedUntil, interval[0]);
                if (gap.minutes > 0) result.add(gap);
            }
            if (occupiedUntil == null || interval[1].isAfter(occupiedUntil)) occupiedUntil = interval[1];
        }
        return result;
    }
}
