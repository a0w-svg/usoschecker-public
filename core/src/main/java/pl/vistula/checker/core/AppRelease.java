package pl.vistula.checker.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Only stable, published releases with a ready APK from our public repository. */
public final class AppRelease {
    public static final String REPOSITORY = "https://github.com/a0w-svg/usoschecker-public";
    public final String version, notes, pageUrl, apkUrl;

    private AppRelease(String version, String notes) {
        this.version = version;
        this.notes = notes;
        pageUrl = REPOSITORY + "/releases/tag/v" + version;
        apkUrl = REPOSITORY + "/releases/download/v" + version + "/VistulaChecker-" + version + ".apk";
    }

    public static AppRelease parse(JSONObject json) {
        String tag = json.optString("tag_name");
        if (!tag.matches("v[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}")
                || json.optBoolean("draft", true) || json.optBoolean("prerelease", true)
                || json.optString("published_at").isEmpty()) return null;
        String notes = json.optString("body");
        AppRelease release = new AppRelease(tag.substring(1), notes.substring(0, Math.min(notes.length(), 12000)));
        if (!release.pageUrl.equals(json.optString("html_url"))) return null;
        JSONArray assets = json.optJSONArray("assets");
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && "uploaded".equals(asset.optString("state")) && asset.optLong("size") > 0
                    && release.apkUrl.equals(asset.optString("browser_download_url"))
                    && ("VistulaChecker-" + release.version + ".apk").equals(asset.optString("name"))) return release;
        }
        return null;
    }

    public boolean newerThan(String installed) { return compareVersions(version, installed) > 0; }

    public static int compareVersions(String first, String second) {
        if (!first.matches("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}")
                || !second.matches("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}"))
            throw new IllegalArgumentException("Unsupported release version");
        String[] a = first.split("\\."), b = second.split("\\.");
        for (int i = 0; i < 3; i++) {
            int result = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (result != 0) return result;
        }
        return 0;
    }
}
