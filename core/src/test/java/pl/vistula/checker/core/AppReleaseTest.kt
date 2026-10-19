package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private fun release(): JSONObject =
        JSONObject().put("tag_name", "v1.10.2").put("draft", false).put("prerelease", false)
            .put("published_at", "2026-10-17T12:00:00Z").put("body", "Nowości\n• Poprawki")
            .put("html_url", AppRelease.REPOSITORY + "/releases/tag/v1.10.2")
            .put(
                "assets",
                JSONArray().put(
                    JSONObject().put("state", "uploaded").put("size", 123)
                        .put("name", "VistulaChecker-1.10.2.apk")
                        .put("browser_download_url", AppRelease.REPOSITORY + "/releases/download/v1.10.2/VistulaChecker-1.10.2.apk")
                )
            )

    @Test
    fun stableReadyApkPreservesNotesAndUsesNumericVersionOrdering() {
        val parsed = AppRelease.parse(release())
        assertNotNull(parsed)
        assertTrue(parsed!!.newerThan("1.9.99"))
        assertFalse(parsed.newerThan("1.10.2"))
        assertFalse(parsed.newerThan("2.0.0"))
        assertEquals("Nowości\n• Poprawki", parsed.notes)
    }

    @Test
    fun draftPrereleaseAndUnpublishedReleasesAreIgnored() {
        assertNull(AppRelease.parse(release().put("draft", true)))
        assertNull(AppRelease.parse(release().put("prerelease", true)))
        assertNull(AppRelease.parse(release().put("published_at", "")))
        assertNull(AppRelease.parse(JSONObject()))
    }

    @Test
    fun malformedAndPrereleaseVersionsAreIgnored() {
        for (version in arrayOf("v1.2", "v1.2.3-beta", "v9999999999999999.1.1", "v1.2.3/evil", "1.2.3")) {
            assertNull(AppRelease.parse(release().put("tag_name", version)))
        }
    }

    @Test
    fun missingAndUnreadyPackagesAreIgnored() {
        assertNull(AppRelease.parse(release().put("assets", JSONArray())))
        var json = release()
        json.getJSONArray("assets").getJSONObject(0).put("state", "new")
        assertNull(AppRelease.parse(json))
        json = release()
        json.getJSONArray("assets").getJSONObject(0).put("size", 0)
        assertNull(AppRelease.parse(json))
    }

    @Test
    fun externalAndLookalikeLinksAreRejected() {
        assertNull(AppRelease.parse(release().put("html_url", "https://github.com.evil.test/releases/tag/v1.10.2")))
        val json = release()
        json.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://evil.test/update.apk")
        assertNull(AppRelease.parse(json))
        json.getJSONArray("assets").getJSONObject(0).put(
            "browser_download_url",
            "http://github.com/a0w-svg/usoschecker-public/releases/download/v1.10.2/VistulaChecker-1.10.2.apk"
        )
        assertNull(AppRelease.parse(json))
    }

    @Test
    fun longNotesAreBounded() {
        assertEquals(12000, AppRelease.parse(release().put("body", "x".repeat(13000)))!!.notes.length)
    }
}
