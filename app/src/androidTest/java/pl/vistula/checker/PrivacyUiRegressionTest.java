package pl.vistula.checker;

import android.content.ClipData;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import pl.vistula.checker.ui.AccountScreenController;
import pl.vistula.checker.ui.UiHelper;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrivacyUiRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);

    @Test public void clipboardContentIsMarkedSensitiveWithoutChangingItsValue() {
        ClipData clip = UiHelper.privateClip("Private API response", "private-test-value");
        assertTrue(clip.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE"));
        assertEquals("private-test-value", clip.getItemAt(0).getText().toString());
    }

    @Test public void authorizationPinIsMaskedAndNotRepeatedInLabelsOrMessages() throws Exception {
        JSONObject state = new JSONObject().put("requestToken", "request-test");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MainActivity activity = rule.getActivity();
            LinearLayout content = new LinearLayout(activity);
            AccountScreenController.AccountListener listener = new AccountScreenController.AccountListener() {
                public void onBeginOAuth(String key, String secret, String scopes) {}
                public void onConfirmPin(String pin) {}
                public void onToggleEnabled(boolean enabled) {}
                public void onUpdateInterval(long interval) {}
                public void onDisconnect() {}
                public void onStartDemo() {}
                public void onEndDemo() {}
                public void onRequestNotifications() {}
            };
            AccountScreenController.render(activity, content, state, state, false, "12345678", listener);
            EditText pin = findPin(content);
            assertNotNull(pin);
            assertTrue(pin.getTransformationMethod() instanceof PasswordTransformationMethod);
            assertFalse(pin.isSaveEnabled());
            assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, pin.getImportantForAutofill());
            assertNotEquals(0, pin.getImeOptions() & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            assertNoPinInLabels(content);
            assertFalse(activity.getString(R.string.pin_detected_clipboard).contains("%"));
            assertFalse(activity.getString(R.string.account_toast_pin_pasted).contains("%"));
        });
    }

    private EditText findPin(View view) {
        if (view instanceof EditText && "12345678".equals(((EditText) view).getText().toString())) return (EditText) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            EditText found = findPin(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
    private void assertNoPinInLabels(View view) {
        if (view instanceof TextView && !(view instanceof EditText)) assertFalse(((TextView) view).getText().toString().contains("12345678"));
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) assertNoPinInLabels(((ViewGroup) view).getChildAt(i));
    }
}
