package pl.vistula.checker.core

import org.junit.Assert.*
import org.junit.Test

class GpaCalculatorTest {
    @Test
    fun testSimulationAchievable() {
        // Current: 4.0 weighted across 30 ECTS. Target: 4.5. Remaining: 30 ECTS.
        // Total points needed: 4.5 * 60 = 270. Current points: 4.0 * 30 = 120.
        // Remaining points needed: 150 across 30 ECTS = 5.0!
        val res = GpaCalculator.simulate(4.0, 30.0, 4.5, 30.0)
        assertTrue(res.achievable)
        assertEquals(5.0, res.requiredGpa, 0.001)
    }

    @Test
    fun testSimulationUnachievable() {
        // Current: 3.5 weighted across 30 ECTS. Target: 4.5. Remaining: 10 ECTS.
        // Total points needed: 4.5 * 40 = 180. Current points: 3.5 * 30 = 105.
        // Remaining points needed: 75 across 10 ECTS = 7.5 > 5.0 -> unachievable!
        val res = GpaCalculator.simulate(3.5, 30.0, 4.5, 10.0)
        assertFalse(res.achievable)
        assertTrue(res.requiredGpa > 5.0)
    }

    @Test
    fun testScholarshipThresholds() {
        assertEquals("Wysoka szansa na stypendium Rektora (Kategoria I)", GpaCalculator.scholarshipStatus(4.85))
        assertEquals("Kwalifikacja do stypendium Rektora Vistula (min. 4.50)", GpaCalculator.scholarshipStatus(4.55))
        assertTrue(GpaCalculator.scholarshipStatus(4.20).contains("brakuje 0,30"))
    }
}
