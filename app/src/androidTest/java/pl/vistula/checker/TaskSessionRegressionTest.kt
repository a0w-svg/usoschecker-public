package pl.vistula.checker

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskSessionRegressionTest {
    private var context: Context? = null
    private lateinit var directory: File
    private lateinit var repository: Repository

    @Before
    fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue(target.packageName.endsWith(".qa"))
        directory = File(target.cacheDir, "task_session_" + UUID.randomUUID())
        assertTrue(directory.mkdir())
        val ctx = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        context = ctx
        repository = Repository(ctx)
    }

    @After
    fun cleanup() {
        if (context != null) {
            repository.disconnect()
            assertTrue(directory.delete())
        }
    }

    private fun assertRejected(session: Repository.EditSession) {
        try {
            repository.update(session) { s -> s.put("privateNote", "old account") }
            fail("Stale edit accepted")
        } catch (expected: IllegalStateException) {
            assertFalse(repository.read().has("privateNote"))
        }
    }

    @Test
    fun guestTasksRemainEditableAcrossRepositoryInstances() {
        val session = repository.editSession(repository.read())
        Repository(context!!).update(session) { s -> s.put("privateNote", "guest task") }
        assertEquals("guest task", repository.read().getString("privateNote"))
    }

    @Test
    fun logoutInvalidatesGuestEditorsEvenWhenStateIsEmptyAgain() {
        val session = repository.editSession(repository.read())
        Repository(context!!).disconnect()
        assertRejected(session)
        assertEquals(0, repository.read().length())
    }

    @Test
    fun reconnectedAccountWithSameTokenRejectsOldEditor() {
        repository.update { s -> s.put("token", "same").put("accountSession", "first") }
        val session = repository.editSession(repository.read())
        repository.update { s -> s.put("accountSession", "second") }
        assertRejected(session)
    }

    @Test
    fun accountSwitchRejectsOldEditorWithoutChangingNewAccount() {
        repository.update { s -> s.put("token", "first") }
        val session = repository.editSession(repository.read())
        repository.update { s -> s.put("token", "second").put("privateNote", "new account") }
        try {
            repository.update(session) { s -> s.put("privateNote", "old account") }
            fail()
        } catch (expected: IllegalStateException) {
            assertEquals("new account", repository.read().getString("privateNote"))
        }
    }

    @Test
    fun unrelatedSettingsDoNotInvalidateTaskEditor() {
        val session = repository.editSession(repository.read())
        repository.update { s -> s.put("interval", 45) }
        repository.update(session) { s -> s.put("privateNote", "task") }
        assertEquals(45, repository.read().getInt("interval"))
    }
}
