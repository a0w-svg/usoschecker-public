package pl.vistula.checker

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeRegressionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var originalState: JSONObject
    private var previousSeen = false
    private var previousStep = 0

    @Before fun setup() {
        assertTrue(context.packageName.endsWith(".qa"))
        originalState = Repository(context).read()
        val preferences = context.getSharedPreferences("welcome", Context.MODE_PRIVATE)
        previousSeen = preferences.getBoolean("seen", false)
        previousStep = preferences.getInt("step", 0)
        preferences.edit().clear().commit()
        Repository(context).update { state ->
            state.remove("token"); state.remove("key"); state.remove("secret"); state.remove("requestToken")
        }
    }

    @After fun restore() {
        if (::originalState.isInitialized) Repository(context).update { state ->
            state.keys().asSequence().toList().forEach { state.remove(it) }
            originalState.keys().forEach { key -> state.put(key, originalState.get(key)) }
        }
        context.getSharedPreferences("welcome", Context.MODE_PRIVATE).edit()
            .putBoolean("seen", previousSeen).putInt("step", previousStep).commit()
    }

    private fun awaitReady(scenario: ActivityScenario<MainActivity>) {
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until) {
            var ready = false
            scenario.onActivity { ready = it.findViewById<View>(R.id.progress_text).visibility == View.GONE }
            if (ready) return
            Thread.sleep(50)
        }
        fail("Initial loading did not finish")
    }

    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun firstLaunchGuidesToMaskedCredentialsAndSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<View>(R.id.welcome_continue))
                assertEquals(View.GONE, activity.findViewById<View>(R.id.bottom_navigation).visibility)
                activity.findViewById<View>(R.id.welcome_continue).performClick()
            }
            scenario.recreate()
            awaitReady(scenario)
            scenario.onActivity { activity ->
                val texts = views(activity.findViewById(R.id.main_content)).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(texts.contains(activity.getString(R.string.welcome_key_title)))
                activity.findViewById<View>(R.id.welcome_continue).performClick()
                assertEquals("settings.connection", activity.accountPage())
                val inputs = views(activity.findViewById(R.id.main_content)).filterIsInstance<EditText>()
                assertTrue(inputs.size >= 2)
                assertNotNull(inputs[1].transformationMethod)
                assertTrue(Repository(activity).read().optString("key").isEmpty())
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { assertNull(it.findViewById<View>(R.id.welcome_continue)) }
        }
    }

    @Test fun skipDoesNotConnectAndDoesNotRepeatOnNextLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.welcome_skip).performClick()
                assertFalse(activity.isDemo())
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.bottom_navigation).visibility)
                assertTrue(Repository(activity).read().optString("token").isEmpty())
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { assertNull(it.findViewById<View>(R.id.welcome_continue)) }
        }
    }

    @Test fun demoFromWelcomeDoesNotWriteAnAccount() {
        val before = Repository(context).read().toString()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.welcome_demo).performClick()
                assertTrue(activity.isDemo())
                assertNotNull(activity.findViewById<View>(R.id.home_open_schedule))
                assertEquals(before, Repository(activity).read().toString())
            }
        }
    }

    @Test fun existingCredentialsBypassWelcomeOnUpgrade() {
        Repository(context).update { it.put("key", "welcome-fixture-key") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { assertNull(it.findViewById<View>(R.id.welcome_continue)) }
        }
    }
}
