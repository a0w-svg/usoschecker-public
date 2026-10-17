package pl.vistula.checker.ui;

import android.content.Context;
import android.app.UiModeManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import pl.vistula.checker.R;

/** Device-local appearance preference, independent of the connected student account. */
public final class Appearance {
    private Appearance() {}

    public static int mode(Context context) {
        Object stored = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getAll().get("nightMode");
        return stored instanceof Integer && supported((Integer) stored)
            ? (Integer) stored : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    private static boolean supported(int mode) {
        return mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            || mode == AppCompatDelegate.MODE_NIGHT_NO || mode == AppCompatDelegate.MODE_NIGHT_YES;
    }

    /** Android 12+ persists this app-local choice for cold starts and the launch screen. */
    public static void apply(Context context) {
        int mode = mode(context);
        UiModeManager manager = context.getSystemService(UiModeManager.class);
        if (manager != null) {
            // AUTO clears the package override, inheriting the phone's current configuration.
            manager.setApplicationNightMode(mode == AppCompatDelegate.MODE_NIGHT_NO ? UiModeManager.MODE_NIGHT_NO
                : mode == AppCompatDelegate.MODE_NIGHT_YES ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_AUTO);
        }
    }

    public static void setMode(Context context, int mode) {
        if (!supported(mode)) throw new IllegalArgumentException("Nieprawidłowy tryb wyglądu.");
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putInt("nightMode", mode).apply();
        apply(context);
    }

    public static void choose(AppCompatActivity activity) {
        int[] modes = {AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES};
        int selected = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i] == mode(activity)) selected = i;
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.appearance_title)
            .setSingleChoiceItems(R.array.appearance_modes, selected, (dialog, which) -> {
                dialog.dismiss();
                setMode(activity, modes[which]);
            }).setNegativeButton(R.string.btn_close, null).show();
    }
}
