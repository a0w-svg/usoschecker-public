package pl.vistula.checker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointsInputTest {
    @Test
    fun acceptsDotAndCommaDecimals() {
        assertEquals(4.5, PointsInput.parse("4.5")!!, 0.0)
        assertEquals(4.5, PointsInput.parse("4,5")!!, 0.0)
        assertEquals(-2.0, PointsInput.parse(" -2 ")!!, 0.0)
    }

    @Test
    fun rejectsEmptyTextAndNonFiniteNumbers() {
        assertNull(PointsInput.parse(""))
        assertNull(PointsInput.parse("abc"))
        assertNull(PointsInput.parse("NaN"))
        assertNull(PointsInput.parse("Infinity"))
        assertNull(PointsInput.parse("1,2,3"))
    }
}
