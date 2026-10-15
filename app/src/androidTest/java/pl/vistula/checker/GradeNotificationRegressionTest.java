package pl.vistula.checker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.Record;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GradeNotificationRegressionTest {
    private Context context;
    private NotificationManager manager;
    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        org.junit.Assume.assumeTrue("Run with -PisolatedTests", context.getPackageName().endsWith(".qa"));
        manager = context.getSystemService(NotificationManager.class);
        manager.cancel(10);
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
    }
    @After public void tearDown() { if (manager != null) manager.cancel(10); }

    @Test public void removedGradeIsLabeledAsRemovedAndIncludesTheOldValue() throws Exception {
        Notification notification = publish(new Change(grade("5"), null, 1));
        assertEquals("Usunięta ocena: Finanse", notification.extras.getString(Notification.EXTRA_TITLE));
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT).startsWith("5"));
    }

    @Test public void modifiedGradeShowsBothValuesInsteadOfClaimingItIsNew() throws Exception {
        Notification notification = publish(new Change(grade("4"), grade("5"), 1));
        assertEquals("Zmieniona ocena: Finanse", notification.extras.getString(Notification.EXTRA_TITLE));
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT).contains("4 → 5"));
    }

    @Test public void newlyAddedGradeKeepsItsNewGradeLabel() throws Exception {
        Notification notification = publish(new Change(null, grade("5"), 1));
        assertEquals("Nowa ocena: Finanse", notification.extras.getString(Notification.EXTRA_TITLE));
    }

    @Test public void mixedBatchDescribesChangesAndPreservesRemovedValues() throws Exception {
        NotificationHelper.notifyChanges(context, List.of(new Change(grade("4"), null, 1), new Change(null, grade("5"), 2)));
        Notification notification = posted();
        assertEquals("Zmiany ocen w USOS (2)", notification.extras.getString(Notification.EXTRA_TITLE));
        assertFalse(notification.extras.getString(Notification.EXTRA_TEXT).contains("nowych ocen"));
        CharSequence[] lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        assertNotNull(lines);
        assertTrue(lines[0].toString().contains("Usunięto · Finanse: 4"));
    }

    private Record grade(String value) { return new Record("grade", "ocena", "Finanse", value, "", ""); }
    private Notification publish(Change change) throws Exception {
        NotificationHelper.notifyChanges(context, List.of(change));
        return posted();
    }
    private Notification posted() throws Exception {
        long until = System.currentTimeMillis() + 3000;
        do {
            for (StatusBarNotification notification : manager.getActiveNotifications())
                if (notification.getId() == 10) return notification.getNotification();
            Thread.sleep(25);
        } while (System.currentTimeMillis() < until);
        throw new AssertionError("Expected grade notification");
    }
}
