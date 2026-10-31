package pl.vistula.checker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.vistula.checker.core.AccountRole
import pl.vistula.checker.ui.MoreScreenController
import pl.vistula.checker.ui.Tabs

class MoreLayoutTest {
    private fun destinations(mode: AccountRole.Mode) =
        MoreScreenController.layout(mode).flatMap { group -> group.entries.map { it.destination } }

    @Test
    fun noDestinationIsListedTwiceInAnyMode() {
        AccountRole.Mode.values().forEach { mode ->
            val all = destinations(mode)
            assertEquals("duplicate tool in $mode: $all", all.size, all.toSet().size)
        }
    }

    @Test
    fun everyGroupHasAtLeastOneEntry() {
        AccountRole.Mode.values().forEach { mode ->
            MoreScreenController.layout(mode).forEach { assertTrue(it.entries.isNotEmpty()) }
        }
    }

    @Test
    fun studentSeesStudyToolsButNotLecturerWorkspace() {
        val student = destinations(AccountRole.Mode.STUDENT)
        assertTrue(student.containsAll(listOf(Tabs.CARDS, Tabs.PAYMENTS, Tabs.GPA_SIMULATOR, Tabs.TASKS)))
        assertFalse(student.contains(Tabs.LECTURER))
    }

    @Test
    fun lecturerOnlyAccountHasNoStudentStudiesOrGpaSimulator() {
        val lecturer = destinations(AccountRole.Mode.LECTURER)
        assertTrue(lecturer.contains(Tabs.LECTURER))
        assertFalse(lecturer.contains(Tabs.CARDS))
        assertFalse(lecturer.contains(Tabs.PAYMENTS))
        assertFalse(lecturer.contains(Tabs.GPA_SIMULATOR))
        assertTrue(lecturer.contains(Tabs.ACADEMIC_EXTRAS))
    }

    @Test
    fun accountWithBothRolesGetsEveryToolOnce() {
        val both = destinations(AccountRole.Mode.BOTH)
        assertTrue(both.containsAll(destinations(AccountRole.Mode.STUDENT)))
        assertTrue(both.contains(Tabs.LECTURER))
    }
}
