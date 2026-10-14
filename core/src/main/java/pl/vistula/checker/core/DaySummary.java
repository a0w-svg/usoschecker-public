package pl.vistula.checker.core;

import java.time.*;
import java.util.*;

/** Duration counts the union of visible meetings clipped to a Warsaw calendar day. */
public final class DaySummary {
    public final int meetings, tentative, conflicts;
    public final long busyMinutes, gapMinutes;
    private DaySummary(int meetings, int tentative, int conflicts, long busyMinutes, long gapMinutes) {
        this.meetings = meetings; this.tentative = tentative; this.conflicts = conflicts;
        this.busyMinutes = busyMinutes; this.gapMinutes = gapMinutes;
    }
    public static DaySummary of(Collection<LessonTime> rows, LocalDate day) {
        Instant dayStart = day.atStartOfDay(LessonTime.ZONE).toInstant(), dayEnd = day.plusDays(1).atStartOfDay(LessonTime.ZONE).toInstant();
        List<Instant[]> intervals = new ArrayList<>(); int tentative = 0;
        for (LessonTime row : rows) if (row.intersects(day)) {
            if (row.tentative()) tentative++;
            intervals.add(new Instant[]{row.start.toInstant().isBefore(dayStart) ? dayStart : row.start.toInstant(), row.end.toInstant().isAfter(dayEnd) ? dayEnd : row.end.toInstant()});
        }
        intervals.sort(Comparator.comparing(a -> a[0]));
        if (intervals.isEmpty()) return new DaySummary(0, 0, 0, 0, 0);
        int conflicts = 0;
        for (int i = 0; i < intervals.size(); i++) for (int j = i + 1; j < intervals.size(); j++) {
            if (!intervals.get(j)[0].isBefore(intervals.get(i)[1])) break;
            conflicts++;
        }
        Instant begin = intervals.get(0)[0], end = intervals.get(0)[1]; long seconds = 0, gapSeconds = 0;
        for (int i = 1; i < intervals.size(); i++) {
            Instant[] next = intervals.get(i);
            if (next[0].isAfter(end)) {
                seconds += Duration.between(begin, end).getSeconds(); gapSeconds += Duration.between(end, next[0]).getSeconds(); begin = next[0]; end = next[1];
            } else if (next[1].isAfter(end)) end = next[1];
        }
        seconds += Duration.between(begin, end).getSeconds();
        return new DaySummary(intervals.size(), tentative, conflicts, seconds / 60, gapSeconds / 60);
    }
}
