package pl.vistula.checker.core

import org.json.JSONObject

/** Only stable, published releases with a ready APK from our public repository. */
class AppRelease private constructor(
    @JvmField val version: String,
    @JvmField val notes: String,
) {
    @JvmField val pageUrl: String = "$REPOSITORY/releases/tag/v$version"
    @JvmField val apkUrl: String = "$REPOSITORY/releases/download/v$version/VistulaChecker-$version.apk"

    fun newerThan(installed: String): Boolean = compareVersions(version, installed) > 0

    companion object {
        const val REPOSITORY = "https://github.com/a0w-svg/usoschecker-public"
        private val VERSION = Regex("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}")
        private val TAG = Regex("v[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}")

        @JvmStatic
        fun parse(json: JSONObject): AppRelease? {
            val tag = json.optString("tag_name")
            if (!tag.matches(TAG) || json.optBoolean("draft", true) || json.optBoolean("prerelease", true) ||
                json.optString("published_at").isEmpty()) return null
            val notes = json.optString("body")
            val release = AppRelease(tag.substring(1), notes.substring(0, minOf(notes.length, 12000)))
            if (release.pageUrl != json.optString("html_url")) return null
            val assets = json.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i)
                if (asset != null && "uploaded" == asset.optString("state") && asset.optLong("size") > 0 &&
                    release.apkUrl == asset.optString("browser_download_url") &&
                    "VistulaChecker-${release.version}.apk" == asset.optString("name")) return release
            }
            return null
        }

        @JvmStatic
        fun compareVersions(first: String, second: String): Int {
            if (!first.matches(VERSION) || !second.matches(VERSION))
                throw IllegalArgumentException("Unsupported release version")
            val a = first.split(".")
            val b = second.split(".")
            for (i in 0 until 3) {
                val result = a[i].toInt().compareTo(b[i].toInt())
                if (result != 0) return result
            }
            return 0
        }
    }
}

