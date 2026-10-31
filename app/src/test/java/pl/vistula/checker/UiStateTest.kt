package pl.vistula.checker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.vistula.checker.ui.ApiExplorerState
import pl.vistula.checker.ui.GradesUiState
import pl.vistula.checker.ui.HistoryUiState
import pl.vistula.checker.ui.ScheduleUiState
import pl.vistula.checker.ui.Tabs
import pl.vistula.checker.ui.TasksUiState

class UiStateTest {
    @Test
    fun gradesResetFiltersRestoresDefaultsAndForgetsExpansion() {
        val grades = GradesUiState().apply {
            query = "math"; type = "exam"; order = "date"; semester = "2025L"
            semesterExpansion["2025L"] = true
        }
        grades.resetFilters()
        assertEquals("", grades.query)
        assertEquals("all", grades.type)
        assertEquals("name", grades.order)
        assertEquals("", grades.semester)
        assertTrue(grades.semesterExpansion.isEmpty())
    }

    @Test
    fun gradesSelectMarksSemesterAsInitialized() {
        val grades = GradesUiState()
        assertFalse(grades.semesterInitialized)
        grades.select("2026Z")
        assertEquals("2026Z", grades.semester)
        assertTrue(grades.semesterInitialized)
    }

    @Test
    fun gradesClearSemesterKeepsFilters() {
        val grades = GradesUiState().apply { type = "exam"; semester = "2025L"; semesterExpansion["x"] = false }
        grades.clearSemester()
        assertEquals("exam", grades.type)
        assertEquals("", grades.semester)
        assertTrue(grades.semesterExpansion.isEmpty())
    }

    @Test
    fun tasksOpenSetsFilterAndClearsSearch() {
        val tasks = TasksUiState().apply { query = "old" }
        tasks.open("overdue")
        assertEquals("overdue", tasks.filter)
        assertEquals("", tasks.query)
        assertEquals("due", tasks.order)
    }

    @Test
    fun historyOpenSetsFilterAndClearsSearch() {
        val history = HistoryUiState().apply { query = "old" }
        assertEquals(HistoryUiState.ALL, history.filter)
        history.open("ocena")
        assertEquals("ocena", history.filter)
        assertEquals("", history.query)
    }

    @Test
    fun scheduleSelectMarksDayAsSelected() {
        val schedule = ScheduleUiState()
        assertFalse(schedule.dateSelected)
        schedule.select("2026-10-31")
        assertEquals("2026-10-31", schedule.date)
        assertTrue(schedule.dateSelected)
        assertFalse(schedule.focusCurrent)
    }

    @Test
    fun apiOpenModuleResetsSearchPagingAndFavoritesFilter() {
        val api = ApiExplorerState().apply { query = "grades"; limit = 240; favoritesOnly = true }
        api.openModule("services/grades")
        assertEquals("services/grades", api.module)
        assertEquals("", api.query)
        assertEquals(ApiExplorerState.DEFAULT_LIMIT, api.limit)
        assertFalse(api.favoritesOnly)
    }

    @Test
    fun apiCloseMethodForgetsOpenMethodButKeepsDrafts() {
        val api = ApiExplorerState().apply {
            currentMethod = "services/grades/latest"
            currentMethodJson = org.json.JSONObject()
            drafts.put("services/grades/latest", org.json.JSONObject().put("a", "b"))
        }
        api.closeMethod()
        assertNull(api.currentMethod)
        assertNull(api.currentMethodJson)
        assertTrue(api.drafts.has("services/grades/latest"))
    }

    @Test
    fun externalTabIdsOutsideKnownScreensFallBackToHome() {
        assertEquals(Tabs.HOME, Tabs.fromExternal(-1))
        assertEquals(Tabs.HOME, Tabs.fromExternal(Tabs.STAFF_DIRECTORY + 1))
        assertEquals(Tabs.HOME, Tabs.fromExternal(Int.MAX_VALUE))
        assertEquals(Tabs.PAYMENTS, Tabs.fromExternal(Tabs.PAYMENTS))
        assertEquals(Tabs.STAFF_DIRECTORY, Tabs.fromExternal(17))
    }
}

class TasksKeysTest {
    @Test
    fun defaultsAreAmongTheAllowedKeys() {
        val tasks = TasksUiState()
        assertTrue(tasks.filter in TasksUiState.FILTERS)
        assertTrue(tasks.order in TasksUiState.ORDERS)
    }

    @Test
    fun keysAreUniqueSoSavedStateStaysUnambiguous() {
        assertEquals(TasksUiState.FILTERS.size, TasksUiState.FILTERS.toSet().size)
        assertEquals(TasksUiState.ORDERS.size, TasksUiState.ORDERS.toSet().size)
    }
}

class GradesKeysTest {
    @Test
    fun defaultsAreAmongTheAllowedKeys() {
        val grades = GradesUiState()
        assertTrue(grades.type in GradesUiState.TYPES)
        assertTrue(grades.order in GradesUiState.ORDERS)
    }

    @Test
    fun keysAreUnique() {
        assertEquals(GradesUiState.TYPES.size, GradesUiState.TYPES.toSet().size)
        assertEquals(GradesUiState.ORDERS.size, GradesUiState.ORDERS.toSet().size)
    }
}

class AttendanceFilterTest {
    @Test
    fun unknownOrMissingStatusFallsBackToAll() {
        assertEquals(pl.vistula.checker.ui.AttendanceFilter.ALL, pl.vistula.checker.ui.AttendanceFilter.statusOrAll(null))
        assertEquals(pl.vistula.checker.ui.AttendanceFilter.ALL, pl.vistula.checker.ui.AttendanceFilter.statusOrAll("nonsense"))
        assertEquals("warning", pl.vistula.checker.ui.AttendanceFilter.statusOrAll("warning"))
        assertEquals("exceeded", pl.vistula.checker.ui.AttendanceFilter.statusOrAll("exceeded"))
    }
}
