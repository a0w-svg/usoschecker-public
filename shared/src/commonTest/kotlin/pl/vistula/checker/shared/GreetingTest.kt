package pl.vistula.checker.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class GreetingTest {
    @Test
    fun greetingMentionsPlatform() {
        assertTrue(greeting().startsWith("Vistula Checker on "))
    }
}

