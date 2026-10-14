package pl.vistula.checker.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public final class ScheduleConflictDetector {
    private ScheduleConflictDetector() {}

    public static final class Conflict {
        public final Record first;
        public final Record second;
        public final long overlapMinutes;

        public Conflict(Record first, Record second, long overlapMinutes) {
            this.first = first;
            this.second = second;
            this.overlapMinutes = overlapMinutes;
        }

        public String description() {
            return String.format(Locale.forLanguageTag("pl-PL"),
                "Kolizja w planie zajęć: %s oraz %s nakładają się na siebie przez %d min (%s)",
                first.title, second.title, overlapMinutes, first.date);
        }
    }

    public static List<Conflict> findConflicts(Collection<Record> records) {
        if (records == null || records.size() < 2) return Collections.emptyList();

        List<Record> lessons = new ArrayList<>();
        for (Record r : records) {
            if ("plan".equals(r.kind) && r.startTime != null && !r.startTime.isEmpty() && r.endTime != null && !r.endTime.isEmpty()) {
                lessons.add(r);
            }
        }

        // Group lessons by date (YYYY-MM-DD)
        Map<String, List<Record>> byDate = new HashMap<>();
        for (Record l : lessons) {
            byDate.computeIfAbsent(l.date, k -> new ArrayList<>()).add(l);
        }

        List<Conflict> conflicts = new ArrayList<>();
        for (List<Record> dayLessons : byDate.values()) {
            if (dayLessons.size() < 2) continue;
            for (int i = 0; i < dayLessons.size(); i++) {
                Record a = dayLessons.get(i);
                LocalDateTime startA, endA;
                try {
                    startA = LocalDateTime.parse(a.startTime.replace(" ", "T"));
                    endA = LocalDateTime.parse(a.endTime.replace(" ", "T"));
                } catch (Exception ignored) { continue; }

                for (int j = i + 1; j < dayLessons.size(); j++) {
                    Record b = dayLessons.get(j);
                    LocalDateTime startB, endB;
                    try {
                        startB = LocalDateTime.parse(b.startTime.replace(" ", "T"));
                        endB = LocalDateTime.parse(b.endTime.replace(" ", "T"));
                    } catch (Exception ignored) { continue; }

                    // Check for overlap: startA < endB && startB < endA
                    if (startA.isBefore(endB) && startB.isBefore(endA)) {
                        LocalDateTime overlapStart = startA.isAfter(startB) ? startA : startB;
                        LocalDateTime overlapEnd = endA.isBefore(endB) ? endA : endB;
                        long minutes = Duration.between(overlapStart, overlapEnd).toMinutes();
                        if (minutes > 0) {
                            conflicts.add(new Conflict(a, b, minutes));
                        }
                    }
                }
            }
        }
        return conflicts;
    }

    public static boolean hasConflicts(Collection<Record> records) {
        return !findConflicts(records).isEmpty();
    }
}
