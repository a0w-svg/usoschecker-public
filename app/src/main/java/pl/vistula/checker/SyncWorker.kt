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
        val context = applicationContext
        var failure: Exception? = null
        try {
            Repository(context).syncAndNotify(false, Consumer {})
        } catch (e: Exception) {
            failure = e
        }
        // The steps below do not need a fresh USOS snapshot: Platon is another server, and reminders and widgets
        // work from saved data. A failed USOS sync must not silence them.
        step("tasks") { TaskDeadlineReminder.notifyIfDue(context, Repository(context).read().optJSONArray("tasks")) }
        step("platon") { PlatonRunner.runInBackground(context) }
        step("reminders") { ClassReminderReceiver.scheduleNext(context) }
        step("widgets") {
            TodayScheduleWidget.updateAll(context)
            QuickStatusWidget.updateAll(context)
        }
        val failed = failure ?: return Result.success()
        return if (shouldRetry(Repository.cause(failed), runAttemptCount)) Result.retry() else Result.failure()
    }

    private inline fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Diagnostics.swallowed("SyncWorker/$name", e)
        }
    }

    companion object {
        const val NAME: String = "usos_periodic_sync"

        /**
         * Retry only what can heal within minutes: network failures and API answers marked retryable. A cooldown,
         * a missing scope, an expired session or an unreadable store fail the same way again, so they wait for
         * the next period instead of waking the phone three more times.
         */
        @JvmStatic
        fun shouldRetry(cause: Exception, attempt: Int): Boolean {
            if (attempt >= 3) return false
            return when (cause) {
                is UsosClient.ApiException -> cause.retryable
                is VaultUnreadableException -> false
                is java.io.IOException -> true
                else -> false
            }
        }

        @JvmStatic
        fun schedule(context: Context, minutes: Long) {
            val work = PeriodicWorkRequest.Builder(SyncWorker::class.java, minutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
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

