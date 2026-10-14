package pl.vistula.checker.core;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public final class PlanBrowser {
    private PlanBrowser() {}
    public static List<LessonTime> select(List<LessonTime> lessons, LocalDate day, String mode, String query) {
        LocalDate week = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<LessonTime> result = new ArrayList<>();
        for (LessonTime lesson : lessons) {
            if (!StudentSearch.matches(query, lesson.record.title, lesson.record.value, lesson.record.date)) continue;
            boolean inRange = mode.equals("agenda") || (mode.equals("week")
                ? lesson.start.toInstant().isBefore(week.plusDays(7).atStartOfDay(LessonTime.ZONE).toInstant()) && lesson.end.toInstant().isAfter(week.atStartOfDay(LessonTime.ZONE).toInstant())
                : lesson.intersects(day));
            if (inRange) result.add(lesson);
        }
        return result;
    }
    public static LocalDate nextDay(List<LessonTime> lessons, LocalDate day, LocalDate end, String query) {
        LocalDate result = null;
        for (LessonTime lesson : lessons) {
            LocalDate candidate = lesson.start.toLocalDate().isAfter(day) ? lesson.start.toLocalDate() : day.plusDays(1);
            if (candidate.isAfter(end) || !lesson.intersects(candidate) || (result != null && !candidate.isBefore(result))) continue;
            if (StudentSearch.matches(query, lesson.record.title, lesson.record.value, lesson.record.date)) result = candidate;
        }
        return result;
    }
}
