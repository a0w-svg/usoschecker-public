package pl.vistula.checker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.vistula.checker.ui.TabSections
import pl.vistula.checker.ui.Tabs

class TabSectionsTest {
    @Test
    fun paymentsRefreshesPaymentsFirst() {
        assertEquals(listOf("payments", "remittances"), TabSections.forTab(Tabs.PAYMENTS, false))
    }

    @Test
    fun lecturerScreenOnlyRefreshesTeachingDataForLecturerAccounts() {
        assertTrue(TabSections.forTab(Tabs.LECTURER, false).isEmpty())
        assertEquals("teaching", TabSections.forTab(Tabs.LECTURER, true).first())
    }

    @Test
    fun screensWithoutRemoteSectionsRefreshNothing() {
        listOf(Tabs.HOME, Tabs.HISTORY, Tabs.GRADES, Tabs.ACCOUNT, Tabs.MORE, Tabs.TASKS).forEach {
            assertTrue(TabSections.forTab(it, true).isEmpty())
        }
    }

    @Test
    fun staffDirectoryAndNewsHaveTheirOwnSection() {
        assertEquals(listOf("staffDirectory"), TabSections.forTab(Tabs.STAFF_DIRECTORY, false))
        assertEquals(listOf("news"), TabSections.forTab(Tabs.NEWS, false))
    }
}
