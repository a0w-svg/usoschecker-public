package pl.vistula.checker.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GradeBrowserTest {
    private Record grade(String id, String title, String value, String modified, String term) { return new Record(id, "ocena", title, value, modified, "", term); }
    @Test public void filtersCombineSemesterTypeAndSearchWithoutMutatingData() {
        List<Record> rows = List.of(grade("1", "Finanse", "4,5 · termin 1", "", "2026Z"), grade("2", "Język", "ZAL · termin 1", "", "2025L"));
        assertEquals(1, GradeBrowser.select(rows, "2026Z", "numeric", "name", "FINANSE 4,5").size());
        assertTrue(GradeBrowser.select(rows, "2026Z", "other", "name", "").isEmpty());
        assertEquals("2", GradeBrowser.select(rows, "", "other", "name", "ZAL").get(0).id);
        assertEquals(2, rows.size());
    }
    @Test public void numericAndRecentSortingHandleUnknownValuesAndMissingDates() {
        List<Record> rows = List.of(grade("1", "A", "ZAL", "", ""), grade("2", "B", "3", "2026-10-12 10:00:00", ""), grade("3", "C", "5", "2026-10-13 10:00:00", ""));
        assertEquals("3", GradeBrowser.select(rows, "", "all", "value", "").get(0).id);
        assertEquals("1", GradeBrowser.select(rows, "", "all", "value", "").get(2).id);
        assertEquals("3", GradeBrowser.select(rows, "", "all", "recent", "").get(0).id);
        assertEquals("1", GradeBrowser.select(rows, "", "all", "recent", "").get(2).id);
        assertNull(GradeBrowser.number(grade("4", "A", "4+", "", "")));
    }
    @Test public void groupsKeepEveryExamEntryAndSeparateSemesters() {
        List<Record> rows = List.of(grade("1", "A", "3", "", "2026Z"), grade("2", "A", "5", "", "2026Z"), grade("3", "A", "4", "", "2025L"));
        List<List<Record>> groups = GradeBrowser.groups(rows);
        assertEquals(2, groups.size()); assertEquals(2, groups.get(0).size()); assertEquals(1, groups.get(1).size());
        assertEquals(3, GradeBrowser.select(rows, "", "all", "name", "").size());
    }
}
