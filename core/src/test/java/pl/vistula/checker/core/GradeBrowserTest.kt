package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GradeBrowserTest {
    @Test
    fun courseIdsSeparateIdenticalNamesAndCanBeSearched() {
        val first = Record(
            "first", "ocena", "Finanse", "4", "", "", "2026Z", "", "", Double.NaN,
            JSONObject().put("courseId", "FIN-101").put("sessionNumber", "2")
        )
        val second = Record(
            "second", "ocena", "Finanse", "5", "", "", "2026Z", "", "", Double.NaN,
            JSONObject().put("courseId", "FIN-202").put("sessionNumber", "1")
        )
        val rows = listOf(first, second)
        assertEquals(2, GradeBrowser.groups(rows).size)
        assertEquals("first", GradeBrowser.select(rows, "", "all", "name", "FIN-101")[0].id)
        assertEquals(1, GradeBrowser.select(rows, "", "retakes", "name", "").size)
        assertEquals("first", GradeBrowser.select(rows, "", "retakes", "name", "")[0].id)
    }

    private fun grade(id: String, title: String, value: String, modified: String, term: String): Record =
        Record(id, "ocena", title, value, modified, "", term)

    @Test
    fun filtersCombineSemesterTypeAndSearchWithoutMutatingData() {
        val rows = listOf(
            grade("1", "Finanse", "4,5 · termin 1", "", "2026Z"),
            grade("2", "Język", "ZAL · termin 1", "", "2025L")
        )
        assertEquals(1, GradeBrowser.select(rows, "2026Z", "numeric", "name", "FINANSE 4,5").size)
        assertTrue(GradeBrowser.select(rows, "2026Z", "other", "name", "").isEmpty())
        assertEquals("2", GradeBrowser.select(rows, "", "other", "name", "ZAL")[0].id)
        assertEquals(2, rows.size)
    }

    @Test
    fun numericAndRecentSortingHandleUnknownValuesAndMissingDates() {
        val rows = listOf(
            grade("1", "A", "ZAL", "", ""),
            grade("2", "B", "3", "2026-10-12 10:00:00", ""),
            grade("3", "C", "5", "2026-10-13 10:00:00", "")
        )
        assertEquals("3", GradeBrowser.select(rows, "", "all", "value", "")[0].id)
        assertEquals("1", GradeBrowser.select(rows, "", "all", "value", "")[2].id)
        assertEquals("3", GradeBrowser.select(rows, "", "all", "recent", "")[0].id)
        assertEquals("1", GradeBrowser.select(rows, "", "all", "recent", "")[2].id)
        assertNull(GradeBrowser.number(grade("4", "A", "4+", "", "")))
    }

    @Test
    fun groupsKeepEveryExamEntryAndSeparateSemesters() {
        val rows = listOf(
            grade("1", "A", "3", "", "2026Z"),
            grade("2", "A", "5", "", "2026Z"),
            grade("3", "A", "4", "", "2025L")
        )
        val groups = GradeBrowser.groups(rows)
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].size)
        assertEquals(1, groups[1].size)
        assertEquals(3, GradeBrowser.select(rows, "", "all", "name", "").size)
    }

    @Test
    fun classTypeDetectionResolvesLaboratoriesAndExercises() {
        val r1 = Record(
            "1", "ocena", "Programowanie", "5 · termin 1", "", "", "2026Z", "", "", Double.NaN,
            JSONObject().put("comment", "Zaliczone laboratorium")
        )
        assertEquals("Typ zajęć niepodany", GradeBrowser.classType(r1))

        val r2 = Record(
            "2", "ocena", "Statystyka", "4 · termin 1", "", "", "2026Z", "", "", Double.NaN,
            JSONObject().put("classTypeId", "CW")
        )
        assertEquals("Ćwiczenia", GradeBrowser.classType(r2))

        val s = Snapshot("2026-10-10", "2026-11-10")
        val planLab = Record(
            "plan:1", "plan", "Fizyka", "09:00", "", "2026-10-14", "", "2026-10-14 09:00:00", "2026-10-14 10:30:00", Double.NaN,
            JSONObject().put("unitId", "unit-50").put("classType", "Laboratorium fizyczne")
        )
        s.add(planLab)

        val r3 = Record(
            "3", "ocena", "Fizyka", "4.5 · termin 1", "", "", "2026Z", "", "", Double.NaN,
            JSONObject().put("unitId", "unit-50")
        )
        assertEquals("Laboratorium fizyczne", GradeBrowser.classType(r3, s))

        val list = listOf(r1, r2, r3)
        assertEquals(1, GradeBrowser.select(list, "", "labs", "name", "", s).size)
        assertEquals(1, GradeBrowser.select(list, "", "exercises", "name", "", s).size)
    }
}
