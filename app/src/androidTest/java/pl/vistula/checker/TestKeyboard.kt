package pl.vistula.checker

import android.app.Activity
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse

/** Android 16 can omit the legacy IME result callback used by Espresso. */
internal object TestKeyboard {
    @JvmStatic
    fun hide(activity: Activity) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.currentFocus?.clearFocus()
            val keyboard = activity.getSystemService(InputMethodManager::class.java)
            keyboard.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        val deadline = SystemClock.uptimeMillis() + 3000
        val visible = booleanArrayOf(true)
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                visible[0] = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())
            }
            if (!visible[0]) return
            SystemClock.sleep(50)
        }
        assertFalse("Keyboard must close before opening a menu", visible[0])
    }
}
