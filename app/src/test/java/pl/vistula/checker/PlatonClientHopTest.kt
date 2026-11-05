package pl.vistula.checker

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PlatonClientHopTest {
    private val here = "https://platon.vistula.edu.pl/Zadanie/1"

    @Test
    fun followsRelativeAndSameHostRedirects() {
        assertEquals("https://platon.vistula.edu.pl/Lms/Zadanie/2?a=1", PlatonClient.nextHop(here, "/Lms/Zadanie/2?a=1"))
        assertEquals("https://platon.vistula.edu.pl/X", PlatonClient.nextHop(here, "https://platon.vistula.edu.pl/X"))
    }

    @Test
    fun anythingOffPlatonOrBackToSignInMeansExpired() {
        for (location in listOf(
            "https://login.microsoftonline.com/x", "http://platon.vistula.edu.pl/x", "https://evil.example/x",
            "https://platon.vistula.edu.pl@evil.example/x", "https://platon.vistula.edu.pl/Logowanie", "", null,
        )) {
            try {
                PlatonClient.nextHop(here, location)
                fail("should expire: $location")
            } catch (expected: PlatonClient.SessionExpiredException) {
            }
        }
    }
}
