package pl.vistula.checker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Alarms are cleared by a reboot and by an app update. This re-arms the next class reminder (and the periodic sync
 * keeps its own WorkManager schedule), so reminders do not stay silent until the app is opened.
 *
 * Written in Java on purpose: it receives protected system broadcasts, and the intent check below is the plain
 * {@code ACTION.equals(intent.getAction())} form that static analysis recognises as verification.
 */
public final class BootReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        // Only the two system broadcasts declared in the manifest are accepted; anything else is ignored.
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            rearm(context);
        }
    }

    private void rearm(Context context) {
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        EXECUTOR.execute(() -> {
            try {
                ClassReminderReceiver.scheduleNext(app);
            } catch (Exception e) {
                Diagnostics.swallowed("BootReceiver", e);
            } finally {
                pending.finish();
            }
        });
    }
}
