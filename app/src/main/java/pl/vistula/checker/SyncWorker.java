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
import pl.vistula.checker.widget.TodayScheduleWidget;
import pl.vistula.checker.widget.QuickStatusWidget;

public final class SyncWorker extends Worker {
    static final String NAME = "usos_periodic_sync";
    public SyncWorker(Context context, WorkerParameters params) { super(context, params); }
    @Override public Result doWork() {
        try {
            Context context = getApplicationContext();
            new Repository(context).syncAndNotify(false, message -> {});
            ClassReminderReceiver.scheduleNext(context);
            TodayScheduleWidget.updateAll(context);
            QuickStatusWidget.updateAll(context);
            return Result.success();
        } catch (Exception e) {
            Exception cause = Repository.cause(e);
            if (cause instanceof UsosClient.ApiException && !((UsosClient.ApiException) cause).retryable) return Result.failure();
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }
    static void schedule(Context context, long minutes) {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(SyncWorker.class, minutes, TimeUnit.MINUTES)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, work);
    }
    static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME);
        WorkManager.getInstance(context).cancelUniqueWork("usos_widget_sync");
    }
    public static void refreshFromWidget(Context context) {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build();
        WorkManager.getInstance(context).enqueueUniqueWork("usos_widget_sync", ExistingWorkPolicy.KEEP, work);
    }
    static void notifyChanges(Context c, List<Change> changes) {
        NotificationHelper.notifyChanges(c, changes);
    }
}
