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
    @Test public void releaseWorkerRemainsConstructibleAfterOptimization() throws Exception {
        java.lang.reflect.Constructor<?> constructor = Class.forName("pl.vistula.checker.ReleaseUpdateWorker")
            .getConstructor(android.content.Context.class, androidx.work.WorkerParameters.class);
        assertTrue(java.lang.reflect.Modifier.isPublic(constructor.getModifiers()));
    }
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
    @Test public void toolsReturnToMoreWithBackAndNavigationReselect() {
        onView(withId(R.id.nav_item_more)).perform(click());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> rule.getActivity().navigate(7));
        onView(withId(R.id.tasks_more)).check(matches(isDisplayed()));
        systemBack();
        onView(withId(R.id.nav_item_more)).check(matches(isSelected()));
        onView(withId(R.id.tasks_more)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> rule.getActivity().navigate(7));
        onView(withId(R.id.nav_item_more)).perform(click());
        onView(withId(R.id.tasks_more)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
    }
    @Test public void moreContainsEverySecondaryDestination() {
        int[] labels = {R.string.cards_title, R.string.documents_title, R.string.tool_gpa_title,
            R.string.tool_attendance_title, R.string.tool_tasks_title, R.string.tool_payments_title,
            R.string.tool_news_title, R.string.tool_campus_title, R.string.tool_account_title, R.string.tool_api_title};
        for (int label : labels) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                rule.getActivity().navigate(6);
                android.widget.LinearLayout content = rule.getActivity().findViewById(R.id.main_content);
                String name = rule.getActivity().getString(label);
                android.view.View destination = null;
                for (int i = 0; i < content.getChildCount(); i++) {
                    android.view.View candidate = content.getChildAt(i);
                    if (candidate.getContentDescription() != null && candidate.getContentDescription().toString().startsWith(name + ". ")) destination = candidate;
                }
                assertNotNull("Missing destination: " + name, destination);
                assertTrue(destination.performClick());
                assertNull("Destination did not open: " + name, destination.getParent());
            });
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
}
