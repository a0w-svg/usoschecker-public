package pl.vistula.checker.ui;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import pl.vistula.checker.R;
import java.util.Locale;

/**
 * Manages in-app language selection (Polish, English, Ukrainian, and System default).
 */
public final class Language {
    private Language() {}

    public static final String[] TAGS = {"", "pl", "en", "uk"};

    public static String currentTag() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty()) return "";
        Locale loc = locales.get(0);
        return loc != null ? loc.getLanguage() : "";
    }

    public static Locale currentLocale(Context context) {
        if (context != null && android.os.Build.VERSION.SDK_INT >= 33) {
            android.app.LocaleManager manager = context.getSystemService(android.app.LocaleManager.class);
            if (manager != null && !manager.getApplicationLocales().isEmpty()) return manager.getApplicationLocales().get(0);
        }
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            Locale loc = locales.get(0);
            if (loc != null) return loc;
        }
        if (context != null) {
            if (android.os.Build.VERSION.SDK_INT < 33) {
                String saved = context.getSharedPreferences("api_language", Context.MODE_PRIVATE).getString("tag", "");
                if (!saved.isEmpty()) return Locale.forLanguageTag(saved);
            }
            try {
                Locale loc = context.getResources().getConfiguration().getLocales().get(0);
                if (loc != null && ("pl".equals(loc.getLanguage()) || "en".equals(loc.getLanguage()) || "uk".equals(loc.getLanguage()))) {
                    return loc;
                }
            } catch (Exception ignored) {}
        }
        return Locale.forLanguageTag("pl-PL");
    }

    public static void setLanguage(String tag) {
        if (tag == null || tag.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        }
    }

    public static void choose(AppCompatActivity activity) {
        int[] labelResIds = {
            R.string.language_system,
            R.string.language_pl,
            R.string.language_en,
            R.string.language_uk
        };
        CharSequence[] items = new CharSequence[labelResIds.length];
        for (int i = 0; i < labelResIds.length; i++) {
            items[i] = activity.getString(labelResIds[i]);
        }
        String current = currentTag();
        int selected = 0;
        for (int i = 0; i < TAGS.length; i++) {
            if (TAGS[i].equalsIgnoreCase(current)) {
                selected = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(items, selected, (dialog, which) -> {
                dialog.dismiss();
                activity.getSharedPreferences("api_language", Context.MODE_PRIVATE).edit().putString("tag", TAGS[which]).apply();
                setLanguage(TAGS[which]);
            })
            .setNegativeButton(R.string.btn_close, null)
            .show();
    }
}
