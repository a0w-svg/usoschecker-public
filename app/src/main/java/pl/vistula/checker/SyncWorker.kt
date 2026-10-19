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
import java.util.function.Consumer
import pl.vistula.checker.core.Change
import pl.vistula.checker.widget.QuickStatusWidget
import pl.vistula.checker.widget.TodayScheduleWidget

class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val context = applicationContext
            Repository(context).syncAndNotify(false, Consumer {})
            ClassReminderReceiver.scheduleNext(context)
            TodayScheduleWidget.updateAll(context)
            QuickStatusWidget.updateAll(context)
            Result.success()
        } catch (e: Exception) {
            val cause = Repository.cause(e)
            if (cause is UsosClient.ApiException && !cause.retryable) return Result.failure()
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val NAME: String = "usos_periodic_sync"

        @JvmStatic
        fun schedule(context: Context, minutes: Long) {
            val work = PeriodicWorkRequest.Builder(SyncWorker::class.java, minutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, work)
        }

        @JvmStatic
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
            WorkManager.getInstance(context).cancelUniqueWork("usos_widget_sync")
        }

        @JvmStatic
        fun refreshFromWidget(context: Context) {
            val work = OneTimeWorkRequest.Builder(SyncWorker::class.java)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("usos_widget_sync", ExistingWorkPolicy.KEEP, work)
        }

        @JvmStatic
        fun notifyChanges(c: Context, changes: List<Change>?) {
            NotificationHelper.notifyChanges(c, changes)
        }
    }
}

