package pl.vistula.checker.ui;

import android.content.Context;
import android.graphics.Color;
import android.widget.LinearLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import pl.vistula.checker.BuildConfig;
import pl.vistula.checker.R;

/** Attribution and an offline copy of the project's license. */
public final class AppInfo {
    private AppInfo() {}
    public static void render(Context context, LinearLayout parent) {
        LinearLayout card = UiHelper.card(context, parent, Color.WHITE);
        UiHelper.add(context, card, context.getString(R.string.about_title), 18, UiHelper.INK, true);
        UiHelper.add(context, card, context.getString(R.string.about_author), 14, UiHelper.INK, false);
        UiHelper.note(context, card, context.getString(R.string.about_version, BuildConfig.VERSION_NAME), UiHelper.MUTED);
        UiHelper.note(context, card, context.getString(R.string.about_license), UiHelper.MUTED);
        card.addView(UiHelper.button(context, context.getString(R.string.about_license_open), () -> showLicense(context), false));
    }
    private static void showLicense(Context context) {
        String text;
        try (InputStream stream = context.getResources().openRawResource(R.raw.license_mit)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            android.widget.Toast.makeText(context, R.string.about_license_error, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.about_license))
            .setMessage(text + "\n\n" + context.getString(R.string.about_license_scope))
            .setPositiveButton(android.R.string.ok, null).show();
    }
}
