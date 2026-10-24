package pl.vistula.checker

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.nio.charset.StandardCharsets
import java.util.function.Consumer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.ui.ApiExplorerController

@RunWith(AndroidJUnit4::class)
class CredentialsAndNavigationRegressionTest {
    @JvmField
    @Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun activationResponsesAreNeitherDisplayedNorAvailableToCopyOrExport() {
        val activity = rule.activity
        val metadata = JSONObject()
            .put("name", "services/cards/mcard_order2")
            .put("auth_options", JSONObject().put("consumer", "required").put("token", "required"))
            .put("arguments", JSONArray())
        val content = arrayOfNulls<LinearLayout>(1)
        for (mime in arrayOf("application/json", "text/plain", "application/octet-stream")) {
            main {
                content[0] = LinearLayout(activity)
                val scroll = ScrollView(activity)
                scroll.addView(content[0])
                try {
                    ApiExplorerController.renderMethodDetails(
                        activity,
                        content[0]!!,
                        scroll,
                        metadata,
                        mutableSetOf(),
                        null,
                        {},
                        object : ApiExplorerController.ApiCallback {
                            override fun onSelectUploadFile(currentMethod: String) {}
                            override fun onSaveResponse(response: UsosClient.Response) {
                                fail("Secret response exported")
                            }

                            override fun onExecuteRequest(
                                metadata: JSONObject,
                                values: Map<String, String>,
                                method: String,
                                uploadBytes: ByteArray?,
                                onSuccess: Consumer<UsosClient.Response>,
                                onFailure: Consumer<String>
                            ) {
                                onSuccess.accept(
                                    UsosClient.Response(
                                        "{\"token\":\"private-activation-token\",\"distribution_pass\":\"private-password\"}"
                                            .toByteArray(StandardCharsets.UTF_8),
                                        mime
                                    )
                                )
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
                assertNotNull(find(content[0]!!, activity.getString(R.string.api_activation_hidden)))
                assertFalse(allText(content[0]!!).contains("private-activation-token"))
                assertFalse(allText(content[0]!!).contains("private-password"))
                assertFalse(find(content[0]!!, activity.getString(R.string.api_export_response))!!.isEnabled)
                assertFalse(find(content[0]!!, activity.getString(R.string.api_copy_response))!!.isEnabled)
            }
        }
    }

    @Test
    fun historyDeepLinksSelectTheCategoryAndClearAnOldSearch() {
        main {
            val activity = rule.activity
            val gradesPrefix = activity.getString(R.string.history_chip_grades, 0).substringBefore("(").trim() + " ("
            val planPrefix = activity.getString(R.string.history_chip_plan, 0).substringBefore("(").trim() + " ("
            activity.navigate(1)
            val search = input(activity.window.decorView)
            assertNotNull(search)
            search!!.setText("old unrelated search")
            activity.onNewIntent(Intent(activity, MainActivity::class.java).putExtra("showHistory", true).putExtra("historyKind", "ocena"))
            assertEquals("", input(activity.window.decorView)!!.text.toString())
            assertTrue(selectedLabel(activity.window.decorView).startsWith(gradesPrefix))
            activity.onNewIntent(Intent(activity, MainActivity::class.java).putExtra("showHistory", true).putExtra("historyKind", "plan"))
            assertTrue(selectedLabel(activity.window.decorView).startsWith(planPrefix))
        }
    }

    @Test
    fun gradeAndScheduleDestinationsOpenTheirOwnScreens() {
        main {
            val activity = rule.activity
            activity.onNewIntent(Intent(activity, MainActivity::class.java).putExtra("tab", 2))
            assertEquals(
                R.id.nav_item_grades,
                activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId
            )
            activity.onNewIntent(Intent(activity, MainActivity::class.java).putExtra("tab", 5))
            assertEquals(
                R.id.nav_item_plan,
                activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId
            )
        }
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

    private fun allText(view: View): String {
        val text = StringBuilder()
        if (view is TextView) text.append(view.text)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) text.append(allText(view.getChildAt(i)))
        }
        return text.toString()
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

    private fun selectedLabel(view: View): String {
        val activity = rule.activity
        val gradesPrefix = activity.getString(R.string.history_chip_grades, 0).substringBefore("(").trim() + " ("
        val planPrefix = activity.getString(R.string.history_chip_plan, 0).substringBefore("(").trim() + " ("
        if (view is TextView && view.isSelected &&
            (view.text.toString().startsWith(gradesPrefix) || view.text.toString().startsWith(planPrefix))
        ) {
            return view.text.toString()
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = selectedLabel(view.getChildAt(i))
                if (found.isNotEmpty()) return found
            }
        }
        return ""
    }
}
