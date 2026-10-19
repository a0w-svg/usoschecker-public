package pl.vistula.checker

import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.ui.AccountScreenController
import pl.vistula.checker.ui.UiHelper

@RunWith(AndroidJUnit4::class)
class PrivacyUiRegressionTest {
    @JvmField
    @Rule
    val rule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun clipboardContentIsMarkedSensitiveWithoutChangingItsValue() {
        val clip = UiHelper.privateClip("Private API response", "private-test-value")
        assertTrue(clip.description.extras.getBoolean("android.content.extra.IS_SENSITIVE"))
        assertEquals("private-test-value", clip.getItemAt(0).text.toString())
    }

    @Test
    fun authorizationPinIsMaskedAndNotRepeatedInLabelsOrMessages() {
        val state = JSONObject().put("requestToken", "request-test")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = rule.activity
            val content = LinearLayout(activity)
            val listener = object : AccountScreenController.AccountListener {
                override fun onBeginOAuth(key: String, secret: String, scopes: String) {}
                override fun onConfirmPin(pin: String) {}
                override fun onToggleEnabled(enabled: Boolean) {}
                override fun onUpdateInterval(interval: Long) {}
                override fun onDisconnect() {}
                override fun onStartDemo() {}
                override fun onEndDemo() {}
                override fun onRequestNotifications() {}
            }
            AccountScreenController.render(activity, content, state, state, false, "12345678", listener)
            val pin = findPin(content)
            assertNotNull(pin)
            assertTrue(pin!!.transformationMethod is PasswordTransformationMethod)
            assertFalse(pin.isSaveEnabled)
            assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, pin.importantForAutofill)
            assertNotEquals(0, pin.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
            assertNoPinInLabels(content)
            assertFalse(activity.getString(R.string.pin_detected_clipboard).contains("%"))
            assertFalse(activity.getString(R.string.account_toast_pin_pasted).contains("%"))
        }
    }

    private fun findPin(view: View): EditText? {
        if (view is EditText && "12345678" == view.text.toString()) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findPin(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun assertNoPinInLabels(view: View) {
        if (view is TextView && view !is EditText) {
            assertFalse(view.text.toString().contains("12345678"))
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) assertNoPinInLabels(view.getChildAt(i))
        }
    }
}
