package pl.vistula.checker.core

import org.junit.Assert.*
import org.junit.Test

class StudentSearchTest {
    @Test
    fun acceptsPolishAccentsOrKeyboardEquivalents() {
        assertTrue(StudentSearch.matches("CWICZENIA LODZ", "Ćwiczenia", "Łódź"))
        assertTrue(StudentSearch.matches("język", "Jezyk angielski"))
        assertTrue(StudentSearch.matches("finansow 4,5", "Podstawy finansów", "4,5 · termin 1"))
    }

    @Test
    fun matchesEveryWordAndKeepsEmptyQueryUseful() {
        assertTrue(StudentSearch.matches("  ", "A"))
        assertFalse(StudentSearch.matches("Finanse Kowalski", "Finanse", "Nowak"))
        assertTrue(StudentSearch.matches("Nowak Finanse", "Finanse", "Nowak"))
    }
}
