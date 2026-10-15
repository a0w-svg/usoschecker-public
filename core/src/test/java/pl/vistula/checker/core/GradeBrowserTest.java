package pl.vistula.checker.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GradeBrowserTest {
    @Test public void courseIdsSeparateIdenticalNamesAndCanBeSearched() {
        Record first = new Record("first", "ocena", "Finanse", "4", "", "", "2026Z", "", "", Double.NaN,
            new org.json.JSONObject().put("courseId", "FIN-101").put("sessionNumber", "2"));
        Record second = new Record("second", "ocena", "Finanse", "5", "", "", "2026Z", "", "", Double.NaN,
            new org.json.JSONObject().put("courseId", "FIN-202").put("sessionNumber", "1"));
        List<Record> rows = List.of(first, second);
        assertEquals(2, GradeBrowser.groups(rows).size());
        assertEquals("first", GradeBrowser.select(rows, "", "all", "name", "FIN-101").get(0).id);
        assertEquals(1, GradeBrowser.select(rows, "", "retakes", "name", "").size());
        assertEquals("first", GradeBrowser.select(rows, "", "retakes", "name", "").get(0).id);
    }
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
    @Test public void classTypeDetectionResolvesLaboratoriesAndExercises() {
        Record r1 = new Record("1", "ocena", "Programowanie", "5 · termin 1", "", "", "2026Z", "", "", Double.NaN,
            new org.json.JSONObject().put("comment", "Zaliczone laboratorium"));
        assertEquals("Typ zajęć niepodany", GradeBrowser.classType(r1));

        Record r2 = new Record("2", "ocena", "Statystyka", "4 · termin 1", "", "", "2026Z", "", "", Double.NaN,
            new org.json.JSONObject().put("classTypeId", "CW"));
        assertEquals("Ćwiczenia", GradeBrowser.classType(r2));

        Snapshot s = new Snapshot("2026-10-10", "2026-11-10");
        Record planLab = new Record("plan:1", "plan", "Fizyka", "09:00", "", "2026-10-14", "", "2026-10-14 09:00:00", "2026-10-14 10:30:00", Double.NaN,
            new org.json.JSONObject().put("unitId", "unit-50").put("classType", "Laboratorium fizyczne"));
        s.add(planLab);

        Record r3 = new Record("3", "ocena", "Fizyka", "4.5 · termin 1", "", "", "2026Z", "", "", Double.NaN,
            new org.json.JSONObject().put("unitId", "unit-50"));
        assertEquals("Laboratorium fizyczne", GradeBrowser.classType(r3, s));

        List<Record> list = List.of(r1, r2, r3);
        assertEquals(1, GradeBrowser.select(list, "", "labs", "name", "", s).size());
        assertEquals(1, GradeBrowser.select(list, "", "exercises", "name", "", s).size());
    }
}
