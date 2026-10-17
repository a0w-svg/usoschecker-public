package pl.vistula.checker;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Independent of account synchronization, including when the user is logged out. */
public final class ReleaseUpdateWorker extends Worker {
    static final String PERIODIC = "app_release_check_daily", STARTUP = "app_release_check_startup";
    public ReleaseUpdateWorker(Context context, WorkerParameters params) { super(context, params); }
    @Override public Result doWork() {
        if (!ReleaseUpdates.enabled(getApplicationContext())) return Result.success();
        try {
            ReleaseUpdates.check(getApplicationContext(), false);
            return Result.success();
        } catch (Exception ignored) {
            return getRunAttemptCount() < 2 ? Result.retry() : Result.failure();
        }
    }
    public static void schedule(Context context) {
        WorkManager manager = WorkManager.getInstance(context);
        if (!ReleaseUpdates.enabled(context)) {
            manager.cancelUniqueWork(PERIODIC);
            manager.cancelUniqueWork(STARTUP);
            return;
        }
        Constraints online = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(ReleaseUpdateWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(online).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build());
        manager.enqueueUniqueWork(STARTUP, ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(ReleaseUpdateWorker.class).setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build());
    }
}
