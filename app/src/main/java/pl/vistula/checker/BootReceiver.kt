package pl.vistula.checker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

/**
 * Alarms are cleared by a reboot and by an app update. This re-arms the next class reminder (and the
 * periodic sync keeps its own WorkManager schedule), so reminders do not stay silent until the app is opened.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Only the two system broadcasts declared in the manifest are accepted; anything else is ignored.
        val booted = Intent.ACTION_BOOT_COMPLETED.equals(intent.action)
        val updated = Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.action)
        if (!booted && !updated) return
        val app = context.applicationContext
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                ClassReminderReceiver.scheduleNext(app)
            } catch (e: Exception) {
                Diagnostics.swallowed("BootReceiver", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
