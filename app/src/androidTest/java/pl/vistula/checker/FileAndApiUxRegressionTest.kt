package pl.vistula.checker

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.ui.ApiExplorerController

@RunWith(AndroidJUnit4::class)
class FileAndApiUxRegressionTest {
    @JvmField
    @Rule
    val rule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun forgedFilePickerResultCannotOverwriteAPrivateFile() {
        val victim = File(rule.activity.cacheDir, "private-export-guard.txt")
        Files.write(victim.toPath(), "private-original".toByteArray(StandardCharsets.UTF_8))
        Intents.init()
        try {
            interceptPicker(Uri.fromFile(victim))
            main { rule.activity.saveCalendar("attacker-overwrite") }
            onView(withText(R.string.error_invalid_document_uri)).inRoot(isDialog()).check(matches(isDisplayed()))
            assertEquals("private-original", String(Files.readAllBytes(victim.toPath()), StandardCharsets.UTF_8))
        } finally {
            Intents.release()
            victim.delete()
        }
    }

    @Test
    fun contentProviderExportTruncatesThePreviousDocument() {
        val activity = rule.activity
        val directory = File(activity.cacheDir, "test-exports")
        directory.mkdirs()
        val document = File(directory, "truncate-test.ics")
        Files.write(document.toPath(), "old-private-document-that-is-much-longer".toByteArray(StandardCharsets.UTF_8))
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".debugexports", document)
        Intents.init()
        try {
            interceptPicker(uri)
            main { activity.saveCalendar("short") }
            val until = System.currentTimeMillis() + 5000
            while (document.length() != 5L && System.currentTimeMillis() < until) Thread.sleep(50)
            assertEquals("short", String(Files.readAllBytes(document.toPath()), StandardCharsets.UTF_8))
        } finally {
            Intents.release()
            document.delete()
        }
    }

    @Test
    fun invalidAndCanceledApiRequestsPreserveTheLastSuccessfulResponse() {
        val activity = rule.activity
        val metadata = JSONObject()
            .put("name", "services/apisrv/installation")
            .put("auth_options", JSONObject().put("consumer", "ignored").put("token", "ignored"))
            .put("arguments", JSONArray().put(JSONObject().put("name", "id").put("is_required", true)))
        val content = arrayOfNulls<LinearLayout>(1)
        val requests = AtomicInteger()
        main {
            content[0] = LinearLayout(activity)
            val scroll = ScrollView(activity)
            scroll.addView(content[0])
            try {
                activity.apiDraft(metadata.getString("name")).put("id", "1")
                ApiExplorerController.renderMethodDetails(
                    activity,
                    content[0]!!,
                    scroll,
                    metadata,
                    mutableSetOf(),
                    null,
                    {},
                    object : ApiExplorerController.ApiCallback {
                        override fun onSelectUploadFile(method: String) {}
                        override fun onSaveResponse(response: UsosClient.Response) {}
                        override fun onExecuteRequest(
                            meta: JSONObject,
                            values: Map<String, String>,
                            method: String,
                            file: ByteArray?,
                            success: Consumer<UsosClient.Response>,
                            failure: Consumer<String>
                        ) {
                            requests.incrementAndGet()
                            success.accept(UsosClient.Response("{\"result\":1}".toByteArray(StandardCharsets.UTF_8), "application/json"))
                        }
                    }
                )
            } catch (e: Exception) {
                throw AssertionError(e)
            }
            find(content[0]!!, activity.getString(R.string.api_execute_btn))!!.performClick()
        }
        onView(withText(R.string.api_btn_execute)).inRoot(isDialog()).perform(click())
        main {
            assertTrue(find(content[0]!!, activity.getString(R.string.api_export_response))!!.isEnabled)
            val input = input(content[0]!!)!!
            input.setText("")
            find(content[0]!!, activity.getString(R.string.api_execute_btn))!!.performClick()
            assertNotNull(input.error)
            assertTrue(find(content[0]!!, activity.getString(R.string.api_export_response))!!.isEnabled)
            assertTrue(find(content[0]!!, activity.getString(R.string.api_copy_response))!!.isEnabled)
            input.setText("1")
            find(content[0]!!, activity.getString(R.string.api_execute_btn))!!.performClick()
        }
        onView(withText(R.string.btn_cancel)).inRoot(isDialog()).perform(click())
        main { assertTrue(find(content[0]!!, activity.getString(R.string.api_export_response))!!.isEnabled) }
        assertEquals(1, requests.get())
    }

    private fun interceptPicker(uri: Uri) {
        Intents.intending(IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
    }

    private fun main(action: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun find(view: View, label: String): View? {
        if (view is TextView && label == view.text.toString()) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = find(view.getChildAt(i), label)
                if (found != null) return found
            }
        }
        return null
    }

    private fun input(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = input(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }
}
