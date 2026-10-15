package pl.vistula.checker.core;

import java.time.Instant;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReminderPlannerTest {
    private final Instant now = Instant.parse("2026-10-15T07:50:00Z");
    private Snapshot snapshot() {
        Snapshot snapshot = new Snapshot("2026-10-15", "2026-10-16");
        snapshot.add(new Record("class", "plan", "Class", "", "", "2026-10-15", "", "2026-10-15 10:00:00", "2026-10-15 11:00:00"));
        return snapshot;
    }
    @Test public void enablingInsideLeadWindowSchedulesPromptCatchUp() {
        LessonTime next = ReminderPlanner.next(snapshot(), Set.of(), now);
        assertNotNull(next);
        assertEquals(now.toEpochMilli() + 1000, ReminderPlanner.triggerAt(next, 15, now));
    }
    @Test public void futureLeadWindowKeepsTheConfiguredTime() {
        LessonTime next = ReminderPlanner.next(snapshot(), Set.of(), now.minusSeconds(3600));
        assertEquals(Instant.parse("2026-10-15T07:30:00Z").toEpochMilli(), ReminderPlanner.triggerAt(next, 30, now.minusSeconds(3600)));
    }
    @Test public void alreadyDeliveredClassIsNotSelectedAgain() {
        Snapshot snapshot = snapshot();
        LessonTime next = ReminderPlanner.next(snapshot, Set.of(), now);
        assertNull(ReminderPlanner.next(snapshot, Set.of(ReminderPlanner.key(next)), now));
    }
    @Test public void startedAndTentativeClassesAreSkipped() {
        Snapshot snapshot = snapshot();
        assertNull(ReminderPlanner.next(snapshot, Set.of(), now.plusSeconds(600)));
        snapshot.records.clear();
        snapshot.add(new Record("tentative", "plan", "Maybe", "Termin orientacyjny", "", "2026-10-15", "",
            "2026-10-15 10:00:00", "2026-10-15 11:00:00"));
        assertNull(ReminderPlanner.next(snapshot, Set.of(), now));
    }
    @Test public void movingAClassCreatesANewDeliveryKey() {
        LessonTime original = ReminderPlanner.next(snapshot(), Set.of(), now);
        Snapshot moved = new Snapshot("2026-10-15", "2026-10-16");
        moved.add(new Record("class", "plan", "Class", "", "", "2026-10-15", "", "2026-10-15 12:00:00", "2026-10-15 13:00:00"));
        assertNotNull(ReminderPlanner.next(moved, Set.of(ReminderPlanner.key(original)), now));
    }
}
