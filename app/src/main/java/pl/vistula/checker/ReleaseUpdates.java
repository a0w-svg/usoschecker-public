package pl.vistula.checker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import pl.vistula.checker.core.AppRelease;

/** Public release metadata only: no USOS client, account, cookies or authentication. */
public final class ReleaseUpdates {
    public static final String CHANNEL = "app_updates";
    public static final int NOTIFICATION_ID = 104;
    public static final String SHOW_UPDATES = "showReleaseUpdates";
    private static final String ENDPOINT = "https://api.github.com/repos/a0w-svg/usoschecker-public/releases/latest";
    private ReleaseUpdates() {}

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("release_updates", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) { return preferences(context).getBoolean("enabled", true); }

    public static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("enabled", enabled).apply();
        if (!enabled) context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        ReleaseUpdateWorker.schedule(context);
    }

    public static AppRelease cached(Context context) {
        try { return AppRelease.parse(new JSONObject(preferences(context).getString("release", "{}"))); }
        catch (Exception ignored) { return null; }
    }

    /** Serializes foreground and worker requests and limits automatic successful checks to twice daily. */
    public static synchronized AppRelease check(Context context, boolean force) throws Exception {
        return check(context, force, ReleaseUpdates::fetch);
    }

    interface Source { JSONObject fetch() throws Exception; }

    static synchronized AppRelease check(Context context, boolean force, Source source) throws Exception {
        SharedPreferences prefs = preferences(context);
        long now = System.currentTimeMillis(), last = prefs.getLong("checkedAt", 0);
        AppRelease release = cached(context);
        if (!force && last > 0 && now >= last && now - last < TimeUnit.HOURS.toMillis(12)) {
            notifyAvailable(context, release);
            return release;
        }
        JSONObject json = source.fetch();
        release = AppRelease.parse(json);
        if (release == null) throw new IOException("Release not ready");
        prefs.edit().putString("release", json.toString()).putLong("checkedAt", now).apply();
        notifyAvailable(context, release);
        return release;
    }

    private static JSONObject fetch() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "VistulaChecker/" + BuildConfig.VERSION_NAME);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Release service unavailable");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 262144) throw new IOException("Release response too large");
                    bytes.write(buffer, 0, count);
                }
                return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }

    static synchronized void notifyAvailable(Context context, AppRelease release) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (release == null || !release.newerThan(BuildConfig.VERSION_NAME)) {
            manager.cancel(NOTIFICATION_ID);
            return;
        }
        if (!enabled(context) || release.version.equals(preferences(context).getString("notified", ""))) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
            context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_DEFAULT));
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()
                || manager.getNotificationChannel(CHANNEL).getImportance() == NotificationManager.IMPORTANCE_NONE) return;
        Intent intent = new Intent(context, MainActivity.class).putExtra(SHOW_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String notes = release.notes.isBlank() ? context.getString(R.string.updates_notes_missing) : release.notes;
        try {
            manager.notify(NOTIFICATION_ID, new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.updates_available, release.version))
                .setContentText(context.getString(R.string.updates_tap_notes))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notes))
                .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build());
            preferences(context).edit().putString("notified", release.version).apply();
        } catch (SecurityException ignored) { /* Permission can change between the check and posting. */ }
    }
}
