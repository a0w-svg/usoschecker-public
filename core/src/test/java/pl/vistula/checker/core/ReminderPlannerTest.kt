package pl.vistula.checker.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ReminderPlannerTest {
    private val now = Instant.parse("2026-10-15T07:50:00Z")
    private fun snapshot(): Snapshot {
        val snapshot = Snapshot("2026-10-15", "2026-10-16")
        snapshot.add(Record("class", "plan", "Class", "", "", "2026-10-15", "", "2026-10-15 10:00:00", "2026-10-15 11:00:00"))
        return snapshot
    }

    @Test
    fun enablingInsideLeadWindowSchedulesPromptCatchUp() {
        val next = ReminderPlanner.next(snapshot(), emptySet(), now)
        assertNotNull(next)
        assertEquals(now.toEpochMilli() + 1000, ReminderPlanner.triggerAt(next!!, 15, now))
    }

    @Test
    fun futureLeadWindowKeepsTheConfiguredTime() {
        val next = ReminderPlanner.next(snapshot(), emptySet(), now.minusSeconds(3600))
        assertEquals(
            Instant.parse("2026-10-15T07:30:00Z").toEpochMilli(),
            ReminderPlanner.triggerAt(next!!, 30, now.minusSeconds(3600))
        )
    }

    @Test
    fun alreadyDeliveredClassIsNotSelectedAgain() {
        val snapshot = snapshot()
        val next = ReminderPlanner.next(snapshot, emptySet(), now)
        assertNull(ReminderPlanner.next(snapshot, setOf(ReminderPlanner.key(next!!)), now))
    }

    @Test
    fun startedAndTentativeClassesAreSkipped() {
        val snapshot = snapshot()
        assertNull(ReminderPlanner.next(snapshot, emptySet(), now.plusSeconds(600)))
        snapshot.records.clear()
        snapshot.add(
            Record(
                "tentative", "plan", "Maybe", "Termin orientacyjny", "", "2026-10-15", "",
                "2026-10-15 10:00:00", "2026-10-15 11:00:00"
            )
        )
        assertNull(ReminderPlanner.next(snapshot, emptySet(), now))
    }

    @Test
    fun movingAClassCreatesANewDeliveryKey() {
        val original = ReminderPlanner.next(snapshot(), emptySet(), now)
        val moved = Snapshot("2026-10-15", "2026-10-16")
        moved.add(Record("class", "plan", "Class", "", "", "2026-10-15", "", "2026-10-15 12:00:00", "2026-10-15 13:00:00"))
        assertNotNull(ReminderPlanner.next(moved, setOf(ReminderPlanner.key(original!!)), now))
    }
}
