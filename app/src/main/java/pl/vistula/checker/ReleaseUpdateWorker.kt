package pl.vistula.checker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Independent of account synchronization, including when the user is logged out. */
class ReleaseUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (!ReleaseUpdates.enabled(applicationContext)) return Result.success()
        return try {
            ReleaseUpdates.check(applicationContext, false)
            Result.success()
        } catch (ignored: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val PERIODIC: String = "app_release_check_daily"
        const val STARTUP: String = "app_release_check_startup"

        @JvmStatic
        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            if (!ReleaseUpdates.enabled(context)) {
                manager.cancelUniqueWork(PERIODIC)
                manager.cancelUniqueWork(STARTUP)
                return
            }
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            manager.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequest.Builder(ReleaseUpdateWorker::class.java, 24, TimeUnit.HOURS)
                    .setConstraints(online)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .build()
            )
            manager.enqueueUniqueWork(
                STARTUP,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequest.Builder(ReleaseUpdateWorker::class.java)
                    .setConstraints(online)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .build()
            )
        }
    }
}

