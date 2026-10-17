package pl.vistula.checker.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppReleaseTest {
    private JSONObject release() throws Exception {
        return new JSONObject().put("tag_name", "v1.10.2").put("draft", false).put("prerelease", false)
            .put("published_at", "2026-10-17T12:00:00Z").put("body", "Nowości\n• Poprawki")
            .put("html_url", AppRelease.REPOSITORY + "/releases/tag/v1.10.2")
            .put("assets", new JSONArray().put(new JSONObject().put("state", "uploaded").put("size", 123)
                .put("name", "VistulaChecker-1.10.2.apk")
                .put("browser_download_url", AppRelease.REPOSITORY + "/releases/download/v1.10.2/VistulaChecker-1.10.2.apk")));
    }
    @Test public void stableReadyApkPreservesNotesAndUsesNumericVersionOrdering() throws Exception {
        AppRelease parsed = AppRelease.parse(release());
        assertNotNull(parsed);
        assertTrue(parsed.newerThan("1.9.99"));
        assertFalse(parsed.newerThan("1.10.2"));
        assertFalse(parsed.newerThan("2.0.0"));
        assertEquals("Nowości\n• Poprawki", parsed.notes);
    }
    @Test public void draftPrereleaseAndUnpublishedReleasesAreIgnored() throws Exception {
        assertNull(AppRelease.parse(release().put("draft", true)));
        assertNull(AppRelease.parse(release().put("prerelease", true)));
        assertNull(AppRelease.parse(release().put("published_at", "")));
        assertNull(AppRelease.parse(new JSONObject()));
    }
    @Test public void malformedAndPrereleaseVersionsAreIgnored() throws Exception {
        for (String version : new String[]{"v1.2", "v1.2.3-beta", "v9999999999999999.1.1", "v1.2.3/evil", "1.2.3"})
            assertNull(AppRelease.parse(release().put("tag_name", version)));
    }
    @Test public void missingAndUnreadyPackagesAreIgnored() throws Exception {
        assertNull(AppRelease.parse(release().put("assets", new JSONArray())));
        JSONObject json = release();
        json.getJSONArray("assets").getJSONObject(0).put("state", "new");
        assertNull(AppRelease.parse(json));
        json = release();
        json.getJSONArray("assets").getJSONObject(0).put("size", 0);
        assertNull(AppRelease.parse(json));
    }
    @Test public void externalAndLookalikeLinksAreRejected() throws Exception {
        assertNull(AppRelease.parse(release().put("html_url", "https://github.com.evil.test/releases/tag/v1.10.2")));
        JSONObject json = release();
        json.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://evil.test/update.apk");
        assertNull(AppRelease.parse(json));
        json.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "http://github.com/a0w-svg/usoschecker-public/releases/download/v1.10.2/VistulaChecker-1.10.2.apk");
        assertNull(AppRelease.parse(json));
    }
    @Test public void longNotesAreBounded() throws Exception {
        assertEquals(12000, AppRelease.parse(release().put("body", "x".repeat(13000))).notes.length());
    }
}
