package pl.vistula.checker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.vistula.checker.ui.KeyPaste

class KeyPasteTest {
    // Synthetic fillers, not credentials.
    private val first = "a".repeat(10) + "1".repeat(10)
    private val second = "b".repeat(10) + "2".repeat(12)

    @Test
    fun readsLabelledPair() {
        assertEquals(first to second, KeyPaste.parse("Consumer Key: $first\nConsumer Secret: $second"))
    }

    @Test
    fun readsTwoBareTokens() {
        assertEquals(first to second, KeyPaste.parse("  $first \n $second  "))
    }

    @Test
    fun rejectsAnythingElse() {
        assertNull(KeyPaste.parse(null))
        assertNull(KeyPaste.parse(""))
        assertNull(KeyPaste.parse(first))
        assertNull(KeyPaste.parse("$first $first"))
        assertNull(KeyPaste.parse("hello world this is not a key"))
        assertNull(KeyPaste.parse("x".repeat(500)))
    }
}
