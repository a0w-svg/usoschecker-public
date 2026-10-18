package pl.vistula.checker.core;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public final class PlanBrowser {
    private PlanBrowser() {}
    public static NavigableSet<LocalDate> daysWithLessons(List<LessonTime> lessons, String query) {
        NavigableSet<LocalDate> days = new TreeSet<>();
        for (LessonTime lesson : lessons) {
            if (!StudentSearch.matches(query, lesson.record.title, lesson.record.value, lesson.record.date)) continue;
            LocalDate last = lesson.end.minusNanos(1).toLocalDate();
            for (LocalDate day = lesson.start.toLocalDate(); !day.isAfter(last); day = day.plusDays(1)) days.add(day);
        }
        return days;
    }
    public static LocalDate nearestDay(NavigableSet<LocalDate> days, LocalDate requested) {
        LocalDate next = days.ceiling(requested);
        return next != null ? next : days.isEmpty() ? requested : days.last();
    }
    public static LocalDate adjacentDay(NavigableSet<LocalDate> days, LocalDate active, boolean weekly, int direction) {
        LocalDate monday = active.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return direction > 0 ? days.higher(weekly ? monday.plusDays(6) : active)
            : days.lower(weekly ? monday : active);
    }
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
