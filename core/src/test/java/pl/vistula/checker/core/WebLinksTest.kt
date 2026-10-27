package pl.vistula.checker.core

import org.junit.Assert.*
import org.junit.Test

class WebLinksTest {
    @Test
    fun documentationLinksIncludeServicesAndMethodAnchor() {
        assertEquals("https://usosapps.vistula.edu.pl/developers/api/services/grades/#terms2", WebLinks.apiDocumentation("services/grades/terms2"))
        assertEquals("https://usosapps.vistula.edu.pl/developers/api/services/oauth/#authorize", WebLinks.apiDocumentation("services/oauth/authorize"))
        assertEquals("https://usosapps.vistula.edu.pl/developers/api/", WebLinks.apiDocumentation("javascript:bad"))
    }

    @Test
    fun acceptsOrdinaryWebLinks() {
        assertTrue(WebLinks.isWebUrl("https://vistula.edu.pl/news?id=12#details"))
        assertFalse(WebLinks.isWebUrl("http://example.org/"))
        assertTrue(WebLinks.isWebUrl("HTTPS://example.org/"))
    }

    @Test
    fun rejectsNonWebSchemesAndMalformedAddresses() {
        for (address in arrayOf(
            null, "", "http://example.org/", "javascript:alert(1)", "file:///data/data/private",
            "content://pl.vistula.checker/state", "intent://example/#Intent;scheme=file;end",
            "tel:123", "//example.org/path", "https:example.org", "https:///path",
            "https://trusted.org@evil.org/", "https://example.org\\@evil.org/",
            "https://example.org/\nintent:payload"
        )) {
            assertFalse("Unsafe link accepted: $address", WebLinks.isWebUrl(address))
        }
    }
}
