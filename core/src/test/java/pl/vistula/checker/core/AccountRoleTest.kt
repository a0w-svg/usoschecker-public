package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountRoleTest {
    @Test fun rolesRequireExplicitProfileStatusAndPreserveDualAccounts() {
        assertFalse(AccountRole.lecturer(JSONObject()))
        assertFalse(AccountRole.lecturer(JSONObject().put("staffStatus", 1)))
        val teacher = JSONObject().put("staffStatus", 2).put("studentStatusCode", 0)
        assertTrue(AccountRole.lecturerOnly(teacher))
        teacher.put("studentStatusCode", 2)
        assertTrue(AccountRole.lecturer(teacher))
        assertFalse(AccountRole.lecturerOnly(teacher))
        teacher.remove("studentStatusCode")
        assertFalse(AccountRole.lecturerOnly(teacher))
    }

    @Test fun refreshStagesAndTimetableCountsFollowSelectedLanguage() {
        assertEquals("Loading grades · grades/terms2", SyncProgress.localized("Pobieranie ocen · grades/terms2", "en-GB"))
        assertEquals("Loading timetable · 3/8 weeks", SyncProgress.localized("Pobieranie planu · 3/8 tygodni", "en"))
        assertEquals("Завантаження розкладу · 3/8 тижнів", SyncProgress.localized("Pobieranie planu · 3/8 tygodni", "uk"))
        assertEquals("Pobieranie ocen", SyncProgress.localized("Pobieranie ocen", "pl"))
        assertEquals("Refreshing data…", SyncProgress.localized("New stage", "en"))
    }
}
