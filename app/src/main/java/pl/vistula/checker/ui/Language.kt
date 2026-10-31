package pl.vistula.checker.ui

import androidx.core.content.edit
import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import pl.vistula.checker.R

/**
 * Manages in-app language selection (Polish, English, Ukrainian, and System default).
 */
class Language private constructor() {
    companion object {
        @JvmField
        val TAGS: Array<String> = arrayOf("", "pl", "en", "uk")

        @JvmStatic
        fun currentTag(): String {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return ""
            val loc = locales[0]
            return loc?.language ?: ""
        }

        @JvmStatic
        // All supported languages ship in the base bundle (language.enableSplit=false),
        // enforced by verifyReleaseConfiguration. This context also serves offline workers.
        @SuppressLint("AppBundleLocaleChanges")
        fun localizedContext(context: Context): Context {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(currentLocale(context))
            return context.createConfigurationContext(configuration)
        }

        @JvmStatic
        fun currentLocale(context: Context?): Locale {
            if (context != null && Build.VERSION.SDK_INT >= 33) {
                val manager = context.getSystemService(LocaleManager::class.java)
                if (manager != null && !manager.applicationLocales.isEmpty) {
                    return manager.applicationLocales[0]
                }
            }
            val locales = AppCompatDelegate.getApplicationLocales()
            if (!locales.isEmpty) {
                val loc = locales[0]
                if (loc != null) return loc
            }
            if (context != null) {
                if (Build.VERSION.SDK_INT < 33) {
                    val saved = context.getSharedPreferences("api_language", Context.MODE_PRIVATE)
                        .getString("tag", "") ?: ""
                    if (saved.isNotEmpty()) return Locale.forLanguageTag(saved)
                }
                try {
                    val loc = context.resources.configuration.locales[0]
                    if (loc != null && (loc.language == "pl" || loc.language == "en" || loc.language == "uk")) {
                        return loc
                    }
                } catch (e: Exception) {
                    pl.vistula.checker.Diagnostics.swallowed("Language", e)
                }
            }
            val system = Locale.getDefault()
            return if (system.language in setOf("pl", "en", "uk")) system else Locale.forLanguageTag("pl-PL")
        }

        @JvmStatic
        fun setLanguage(tag: String?) {
            if (tag.isNullOrEmpty()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
        }

        @JvmStatic
        fun choose(activity: AppCompatActivity) {
            val labelResIds = intArrayOf(
                R.string.language_system,
                R.string.language_pl,
                R.string.language_en,
                R.string.language_uk
            )
            val items = Array<CharSequence>(labelResIds.size) { i ->
                activity.getString(labelResIds[i])
            }
            val current = currentTag()
            var selected = 0
            for (i in TAGS.indices) {
                if (TAGS[i].equals(current, ignoreCase = true)) {
                    selected = i
                    break
                }
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(items, selected) { dialog, which ->
                    dialog.dismiss()
                    activity.getSharedPreferences("api_language", Context.MODE_PRIVATE)
                        .edit {
                            putString("tag", TAGS[which])
                        }
                    setLanguage(TAGS[which])
                }
                .setNegativeButton(R.string.btn_close, null)
                .show()
        }
    }
}

