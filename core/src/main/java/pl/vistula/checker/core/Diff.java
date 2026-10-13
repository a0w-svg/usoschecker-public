package pl.vistula.checker.core;

import java.util.*;

public final class Diff {
    private Diff() {}
    /** The initial download is a baseline. A sliding timetable window is compared only in overlap. */
    public static List<Change> between(Snapshot previous, Snapshot current, long now, String today) {
        List<Change> changes = new ArrayList<>();
        if (previous == null) return changes;
        for (Record r : current.records.values()) {
            Record old = previous.records.get(r.id);
            if (r.kind.equals("plan") && (r.date.compareTo(today) < 0 || !previous.covers(r.date))) continue;
            if (old == null || !r.same(old)) changes.add(new Change(old, r, now));
        }
        for (Record old : previous.records.values()) {
            if (current.records.containsKey(old.id)) continue;
            if (old.kind.equals("plan") && (!current.covers(old.date) || old.date.compareTo(today) < 0)) continue;
            changes.add(new Change(old, null, now));
        }
        return changes;
    }
}
