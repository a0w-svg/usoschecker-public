package pl.vistula.checker;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import pl.vistula.checker.core.Change;

public final class SyncWorker extends Worker {
    static final String NAME = "usos_periodic_sync";
    static final String CHANNEL = "usos_changes";
    public SyncWorker(Context context, WorkerParameters params) { super(context, params); }
    @Override public Result doWork() {
        try {
            List<Change> changes = new Repository(getApplicationContext()).sync(false);
            notifyChanges(getApplicationContext(), changes);
            return Result.success();
        } catch (Exception e) {
            if (e instanceof UsosClient.ApiException && !((UsosClient.ApiException) e).retryable) return Result.failure();
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }
    static void schedule(Context context, long minutes) {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(SyncWorker.class, minutes, TimeUnit.MINUTES)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, work);
    }
    static void cancel(Context context) { WorkManager.getInstance(context).cancelUniqueWork(NAME); }
    static void notifyChanges(Context c, List<Change> changes) {
        if (changes.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Zmiany w USOS", NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent = new Intent(c, MainActivity.class).putExtra("showHistory", true);
        PendingIntent pending = PendingIntent.getActivity(c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Nowe zmiany w USOS")
            .setContentText("Wykryto " + changes.size() + " zmian. Otwórz historię, aby zobaczyć szczegóły.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending).setAutoCancel(true);
        try { NotificationManagerCompat.from(c).notify(1, notification.build()); }
        catch (SecurityException ignored) { /* History remains available even if permission was revoked. */ }
    }
}
