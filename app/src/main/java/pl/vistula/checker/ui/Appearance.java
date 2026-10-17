package pl.vistula.checker.ui;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import pl.vistula.checker.R;

/** Device-local appearance preference, independent of the connected student account. */
public final class Appearance {
    private Appearance() {}

    public static int mode(Context context) {
        return context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            .getInt("nightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static void choose(AppCompatActivity activity) {
        int[] modes = {AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES};
        int selected = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i] == mode(activity)) selected = i;
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.appearance_title)
            .setSingleChoiceItems(R.array.appearance_modes, selected, (dialog, which) -> {
                activity.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                    .putInt("nightMode", modes[which]).apply();
                dialog.dismiss();
                activity.getDelegate().setLocalNightMode(modes[which]);
            }).setNegativeButton(R.string.btn_close, null).show();
    }
}
