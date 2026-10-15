package pl.vistula.checker;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import pl.vistula.checker.ui.UiHelper;
import pl.vistula.checker.widget.QuickStatusWidget;
import pl.vistula.checker.widget.TodayScheduleWidget;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SecurityRegressionTest {
    @Test public void installedWidgetReceiversArePrivate() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (Class<?> receiver : new Class<?>[]{TodayScheduleWidget.class, QuickStatusWidget.class}) {
            assertFalse(context.getPackageManager().getReceiverInfo(new ComponentName(context, receiver), 0).exported);
        }
    }

    @Test public void unsafeBrowserLinksNeverReachAnActivityLauncher() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context context = new ContextWrapper(target) {
            @Override public void startActivity(Intent intent) { fail("Unsafe URL launched: " + intent); }
            @Override public void startActivity(Intent intent, Bundle options) { fail("Unsafe URL launched: " + intent); }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (String address : new String[]{"intent://payload/#Intent;scheme=file;end", "file:///data/data/private",
                    "content://private/state", "javascript:alert(1)", "https://trusted.org@evil.org/"}) {
                UiHelper.browser(context, address);
            }
        });
    }
}
