package pl.vistula.checker;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class ProductionReadinessTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    @Before public void qaOnly() {
        Assume.assumeTrue(rule.getActivity().getPackageName().endsWith(".qa"));
        try {
            long now = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_CANCEL, 0, 0, 0);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(cancel, true);
            cancel.recycle();
        } catch (Throwable ignored) {}
    }
    @Test public void testInstallationContainsNoDeveloperCredentials() {
        assertTrue("QA must not embed a developer key", BuildConfig.USOS_KEY.isEmpty());
        assertTrue("QA must not embed a developer secret", BuildConfig.USOS_SECRET.isEmpty());
    }
    @Test public void platformBackReturnsFromGradesToHomeOnAndroid16() {
        onView(withId(R.id.nav_item_grades)).perform(click());
        onView(withId(R.id.nav_item_grades)).check(matches(isSelected()));
        systemBack();
        waitForHomeSelected(3000);
        onView(withId(R.id.nav_item_home)).check(matches(isSelected()));
    }
    private void waitForHomeSelected(long timeoutMs) {
        long deadline = android.os.SystemClock.uptimeMillis() + timeoutMs;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            boolean[] selected = {false};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                android.view.View home = rule.getActivity() != null ? rule.getActivity().findViewById(R.id.nav_item_home) : null;
                if (home != null && home.isSelected()) selected[0] = true;
            });
            if (selected[0]) return;
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
    }
    @Test public void platformBackClosesDrawerWithoutLeavingHome() {
        DrawerIdle idle = new DrawerIdle(rule.getActivity().findViewById(R.id.drawer_layout));
        androidx.test.espresso.IdlingRegistry.getInstance().register(idle);
        try {
            onView(withContentDescription(R.string.menu_open)).perform(click());
            onView(withId(R.id.drawer_layout)).check((view, failure) -> {
                if (failure != null) throw failure;
                assertTrue(((androidx.drawerlayout.widget.DrawerLayout) view).isDrawerOpen(androidx.core.view.GravityCompat.START));
            });
            idle.setExpectClose(true);
            systemBack();
            onView(withId(R.id.nav_item_home)).check(matches(isSelected()));
            onView(withId(R.id.drawer_layout)).check((view, failure) -> {
                if (failure != null) throw failure;
                assertFalse(((androidx.drawerlayout.widget.DrawerLayout) view).isDrawerOpen(androidx.core.view.GravityCompat.START));
            });
        } finally {
            androidx.test.espresso.IdlingRegistry.getInstance().unregister(idle);
            idle.drawer.removeDrawerListener(idle);
        }
    }
    private void systemBack() {
        // API 36 no longer dispatches synthetic KEYCODE_BACK to the app's views.
        android.app.UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        assertTrue(ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
        try { ui.waitForIdle(200, 5000); }
        catch (java.util.concurrent.TimeoutException e) { throw new AssertionError("System back did not settle", e); }
    }
    @Test public void manifestPreservesTransportAndStorageSafety() throws Exception {
        ApplicationInfo info = rule.getActivity().getApplicationInfo();
        assertTrue(info.targetSdkVersion >= 36);
        assertEquals(BuildConfig.DEBUG, (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        assertEquals(0, info.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
        assertEquals(0, info.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC);
        PackageInfo pkg = rule.getActivity().getPackageManager().getPackageInfo(info.packageName, PackageManager.GET_PROVIDERS);
        if (!BuildConfig.DEBUG && pkg.providers != null) for (android.content.pm.ProviderInfo provider : pkg.providers)
            assertFalse(provider.authority != null && provider.authority.contains("debugexports"));
    }
    private static final class DrawerIdle implements androidx.drawerlayout.widget.DrawerLayout.DrawerListener, androidx.test.espresso.IdlingResource {
        private final androidx.drawerlayout.widget.DrawerLayout drawer;
        private int state = androidx.drawerlayout.widget.DrawerLayout.STATE_IDLE;
        private boolean expectClose;
        private ResourceCallback callback;
        DrawerIdle(androidx.drawerlayout.widget.DrawerLayout drawer) { this.drawer = drawer; drawer.addDrawerListener(this); }
        public String getName() { return "Drawer animation"; }
        public boolean isIdleNow() {
            if (expectClose && drawer.isDrawerOpen(androidx.core.view.GravityCompat.START)) return false;
            return state == androidx.drawerlayout.widget.DrawerLayout.STATE_IDLE;
        }
        public void setExpectClose(boolean expectClose) { this.expectClose = expectClose; }
        public void registerIdleTransitionCallback(ResourceCallback callback) { this.callback = callback; }
        public void onDrawerSlide(android.view.View view, float offset) {}
        public void onDrawerOpened(android.view.View view) {}
        public void onDrawerClosed(android.view.View view) {
            expectClose = false;
            if (callback != null) callback.onTransitionToIdle();
        }
        @Override public void onDrawerStateChanged(int state) {
            this.state = state;
            if (isIdleNow() && callback != null) callback.onTransitionToIdle();
        }
    }
}
