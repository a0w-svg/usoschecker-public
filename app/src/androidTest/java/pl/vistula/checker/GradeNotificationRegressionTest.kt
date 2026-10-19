package pl.vistula.checker

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.Record

@RunWith(AndroidJUnit4::class)
class GradeNotificationRegressionTest {
    private lateinit var context: Context
    private var manager: NotificationManager? = null

    @Before
    fun setUp() {
        context = pl.vistula.checker.ui.Language.localizedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        Assume.assumeTrue("Run with -PisolatedTests", context.packageName.endsWith(".qa"))
        val nm = context.getSystemService(NotificationManager::class.java)
        manager = nm
        nm.cancel(10)
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun tearDown() {
        manager?.cancel(10)
    }

    @Test
    fun removedGradeIsLabeledAsRemovedAndIncludesTheOldValue() {
        val notification = publish(Change(grade("5"), null, 1))
        assertEquals(context.getString(R.string.notification_grade_removed, "Finanse"), notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)!!.startsWith("5"))
    }

    @Test
    fun modifiedGradeShowsBothValuesInsteadOfClaimingItIsNew() {
        val notification = publish(Change(grade("4"), grade("5"), 1))
        assertEquals(context.getString(R.string.notification_grade_modified, "Finanse"), notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("4 → 5"))
    }

    @Test
    fun newlyAddedGradeKeepsItsNewGradeLabel() {
        val notification = publish(Change(null, grade("5"), 1))
        assertEquals(context.getString(R.string.notification_grade_added, "Finanse"), notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun mixedBatchDescribesChangesAndPreservesRemovedValues() {
        NotificationHelper.notifyChanges(context, listOf(Change(grade("4"), null, 1), Change(null, grade("5"), 2)))
        val notification = posted()
        assertEquals(context.getString(R.string.notification_grade_changes, 2), notification.extras.getString(Notification.EXTRA_TITLE))
        assertFalse(notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("nowych ocen"))
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertNotNull(lines)
        assertTrue(lines!![0].toString().contains(context.getString(R.string.history_action_removed) + " · Finanse: 4"))
    }

    private fun grade(value: String): Record = Record("grade", "ocena", "Finanse", value, "", "")

    private fun publish(change: Change): Notification {
        NotificationHelper.notifyChanges(context, listOf(change))
        return posted()
    }

    private fun posted(): Notification {
        val until = System.currentTimeMillis() + 3000
        do {
            for (notification in manager!!.activeNotifications) {
                if (notification.id == 10) return notification.notification
            }
            Thread.sleep(25)
        } while (System.currentTimeMillis() < until)
        throw AssertionError("Expected grade notification")
    }
}
