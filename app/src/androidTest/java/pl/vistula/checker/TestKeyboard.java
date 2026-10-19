package pl.vistula.checker;

import android.app.Activity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.platform.app.InstrumentationRegistry;

/** Android 16 can omit the legacy IME result callback used by Espresso. */
final class TestKeyboard {
    static void hide(Activity activity) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View focus = activity.getCurrentFocus();
            if (focus != null) focus.clearFocus();
            InputMethodManager keyboard = activity.getSystemService(InputMethodManager.class);
            keyboard.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
        });
        long deadline = android.os.SystemClock.uptimeMillis() + 3000;
        boolean[] visible = {true};
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
                visible[0] = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
            });
            if (!visible[0]) return;
            android.os.SystemClock.sleep(50);
        }
        org.junit.Assert.assertFalse("Keyboard must close before opening a menu", visible[0]);
    }
}
