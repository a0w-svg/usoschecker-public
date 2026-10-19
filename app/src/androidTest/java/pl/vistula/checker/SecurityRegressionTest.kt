package pl.vistula.checker

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.ui.UiHelper
import pl.vistula.checker.widget.QuickStatusWidget
import pl.vistula.checker.widget.TodayScheduleWidget

@RunWith(AndroidJUnit4::class)
class SecurityRegressionTest {
    @Test
    fun installedWidgetReceiversArePrivate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (receiver in arrayOf(TodayScheduleWidget::class.java, QuickStatusWidget::class.java)) {
            assertFalse(context.packageManager.getReceiverInfo(ComponentName(context, receiver), 0).exported)
        }
    }

    @Test
    fun unsafeBrowserLinksNeverReachAnActivityLauncher() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(target) {
            override fun startActivity(intent: Intent) {
                fail("Unsafe URL launched: $intent")
            }

            override fun startActivity(intent: Intent, options: Bundle?) {
                fail("Unsafe URL launched: $intent")
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (address in arrayOf(
                "intent://payload/#Intent;scheme=file;end",
                "file:///data/data/private",
                "content://private/state",
                "javascript:alert(1)",
                "https://trusted.org@evil.org/"
            )) {
                UiHelper.browser(context, address)
            }
        }
    }
}
