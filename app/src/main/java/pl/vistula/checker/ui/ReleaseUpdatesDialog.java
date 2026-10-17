package pl.vistula.checker.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import pl.vistula.checker.BuildConfig;
import pl.vistula.checker.R;
import pl.vistula.checker.ReleaseUpdates;
import pl.vistula.checker.core.AppRelease;

public final class ReleaseUpdatesDialog {
    private ReleaseUpdatesDialog() {}

    public static void show(AppCompatActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Context application = activity.getApplicationContext();
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, 0, padding, padding);
        TextView status = UiHelper.text(activity, "", 15, UiHelper.INK, false);
        layout.addView(status);
        status.setTextIsSelectable(true);
        AppRelease[] latest = {ReleaseUpdates.cached(application)};
        render(activity, status, latest[0], false);
        Button download = UiHelper.button(activity, activity.getString(latest[0] != null && latest[0].newerThan(BuildConfig.VERSION_NAME)
                ? R.string.updates_download : R.string.updates_release_history), () -> {
            AppRelease release = latest[0];
            UiHelper.browser(activity, release != null && release.newerThan(BuildConfig.VERSION_NAME)
                ? release.apkUrl : AppRelease.REPOSITORY + "/releases/latest");
        }, false);
        layout.addView(download);
        MaterialSwitch enabled = new MaterialSwitch(activity);
        enabled.setText(R.string.updates_notify_enabled);
        enabled.setChecked(ReleaseUpdates.enabled(application));
        layout.addView(enabled);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            ReleaseUpdates.setEnabled(application, checked);
            if (checked && Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        });
        layout.addView(UiHelper.button(activity, activity.getString(R.string.updates_notification_settings), () -> {
            if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            else activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
        }, false));
        UiHelper.note(activity, layout, activity.getString(R.string.updates_background_note), UiHelper.MUTED);
        UiHelper.add(activity, layout, activity.getString(R.string.updates_installed_notes, BuildConfig.VERSION_NAME), 16, UiHelper.INK, true);
        UiHelper.note(activity, layout, installedNotes(activity), UiHelper.MUTED);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(layout);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.updates_title).setView(scroll)
            .setPositiveButton(R.string.updates_check, null).setNegativeButton(R.string.btn_close, null).create();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        androidx.lifecycle.LifecycleEventObserver lifecycle = (owner, event) -> {
            if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) dialog.dismiss();
        };
        activity.getLifecycle().addObserver(lifecycle);
        dialog.setOnDismissListener(ignored -> {
            executor.shutdownNow();
            activity.getLifecycle().removeObserver(lifecycle);
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            status.setText(R.string.updates_checking);
            executor.execute(() -> {
                AppRelease result = null;
                try { result = ReleaseUpdates.check(application, true); } catch (Exception ignoredError) { }
                AppRelease checked = result;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    if (checked != null) latest[0] = checked;
                    download.setText(latest[0] != null && latest[0].newerThan(BuildConfig.VERSION_NAME)
                        ? R.string.updates_download : R.string.updates_release_history);
                    render(activity, status, latest[0], checked != null);
                    if (checked == null) status.setText(activity.getString(R.string.updates_error) + "\n\n" + status.getText());
                });
            });
        }));
        dialog.show();
    }

    private static void render(Context context, TextView status, AppRelease release, boolean fresh) {
        if (release != null && release.newerThan(BuildConfig.VERSION_NAME)) {
            status.setText(context.getString(R.string.updates_available, release.version) + "\n\n"
                + (release.notes.isBlank() ? context.getString(R.string.updates_notes_missing) : release.notes)
                + (fresh ? "" : "\n\n" + context.getString(R.string.updates_cached)));
        } else status.setText(context.getString(fresh ? R.string.updates_current : R.string.updates_not_checked));
    }

    private static String installedNotes(Context context) {
        try (InputStream input = context.getResources().openRawResource(R.raw.whats_new)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { return context.getString(R.string.updates_notes_missing); }
    }
}
