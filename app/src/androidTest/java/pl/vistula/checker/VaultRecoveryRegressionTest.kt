package pl.vistula.checker

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A store that cannot be decrypted must offer a confirmed reset instead of failing every action for good. */
@RunWith(AndroidJUnit4::class)
class VaultRecoveryRegressionTest {
    private lateinit var target: Context
    private lateinit var file: File
    private var saved: ByteArray? = null

    @Before
    fun setUp() {
        target = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue("Run with -PisolatedTests", target.packageName.endsWith(".qa"))
        file = File(target.noBackupFilesDir, "state.bin")
        saved = if (file.exists()) file.readBytes() else null
    }

    @After
    fun tearDown() {
        if (!::file.isInitialized) return
        // The Keystore key is untouched by a reset, so the fixture store that was there before is readable again.
        val bytes = saved
        if (bytes != null) file.writeBytes(bytes) else file.delete()
    }

    @Test
    fun unreadableStoreOffersAResetThatLeavesAnEmptyUsableStore() {
        // Right framing (version byte, IV, tag length) but not produced by this device's key.
        file.writeBytes(ByteArray(64) { if (it == 0) 1 else (it * 7).toByte() })
        try {
            Vault(target).read()
            org.junit.Assert.fail("A foreign ciphertext must not be readable")
        } catch (expected: VaultUnreadableException) {
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            eventually {
                onView(withText(R.string.vault_unreadable_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            }
            onView(withText(R.string.vault_reset_btn)).inRoot(isDialog()).perform(click())
            eventually { assertEquals(0, Vault(target).read().length()) }
        }

        // The cleared store accepts writes again.
        Repository(target).update { state -> state.put("interval", 30L) }
        assertTrue(Repository(target).read().has("interval"))
        Repository(target).disconnect()
    }

    @Test
    fun cancellingTheResetKeepsTheStoreUntouched() {
        val damaged = ByteArray(64) { if (it == 0) 1 else (it * 3).toByte() }
        file.writeBytes(damaged)
        ActivityScenario.launch(MainActivity::class.java).use {
            eventually {
                onView(withText(R.string.vault_unreadable_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            }
            onView(withText(R.string.btn_cancel)).inRoot(isDialog()).perform(click())
        }
        assertTrue("Cancel must not delete or replace the saved file", damaged.contentEquals(file.readBytes()))
    }

    private fun eventually(check: () -> Unit) {
        var last: Throwable? = null
        repeat(80) {
            try {
                check()
                return
            } catch (e: Throwable) {
                last = e
                Thread.sleep(125)
            }
        }
        throw checkNotNull(last)
    }
}
