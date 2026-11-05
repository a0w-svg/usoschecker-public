package pl.vistula.checker.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test
import pl.vistula.checker.StateCache

class StateCacheTest {
    @Test
    fun servesOnlyMatchingStampAndHandsOutIndependentCopies() {
        val cache = StateCache()
        assertNull(cache.get(5L))
        cache.put(5L, JSONObject().put("a", 1))
        val first = cache.get(5L)!!
        first.put("a", 2)
        assertEquals(1, cache.get(5L)!!.getInt("a"))
        assertNotSame(cache.get(5L), cache.get(5L))
        assertNull(cache.get(6L))
    }

    @Test
    fun unknownStampAndInvalidationNeverServe() {
        val cache = StateCache()
        cache.put(StateCache.NO_STAMP, JSONObject().put("a", 1))
        assertNull(cache.get(StateCache.NO_STAMP))
        cache.put(7L, JSONObject().put("a", 1))
        cache.invalidate()
        assertNull(cache.get(7L))
    }

    @Test
    fun missingFileHasNoStamp() {
        assertEquals(StateCache.NO_STAMP, StateCache.stampOf(0L, 0L))
        assertEquals(31L * 10 + 4, StateCache.stampOf(10L, 4L))
    }
}
