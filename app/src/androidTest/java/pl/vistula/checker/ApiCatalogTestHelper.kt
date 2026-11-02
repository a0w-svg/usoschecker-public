package pl.vistula.checker

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/** Finds API catalog rows by method name (for example "grades/latest"), independent of how a row is laid out. */
object ApiCatalogTestHelper {
    private fun find(root: View, name: String): View? {
        if (root is TextView && root.id == R.id.api_method_name && root.text.toString() == name) return root.parent as? View
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) find(root.getChildAt(i), name)?.let { return it }
        }
        return null
    }

    private fun row(activity: Activity, name: String): View? {
        var found: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { found = find(activity.window.decorView, name) }
        return found
    }

    fun has(activity: Activity, name: String) {
        assertTrue("Method row not shown: $name", row(activity, name) != null)
    }

    fun absent(activity: Activity, name: String) {
        assertNull("Method row should not be shown: $name", row(activity, name))
    }

    fun click(activity: Activity, name: String) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var target: View? = null
        while (target == null && SystemClock.uptimeMillis() < deadline) {
            target = row(activity, name)
            if (target == null) SystemClock.sleep(100)
        }
        assertTrue("Method row not available: $name", target != null)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { target!!.performClick() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
