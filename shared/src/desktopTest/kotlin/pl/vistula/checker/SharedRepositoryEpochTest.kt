package pl.vistula.checker

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.json.JSONObject
import pl.vistula.checker.core.StateKeys
import pl.vistula.checker.core.StateSchema

/** Edit sessions must not write into a different login, and concurrent updates must not lose writes. */
class SharedRepositoryEpochTest {
    private fun withRepository(block: (SharedRepository) -> Unit) {
        val directory = Files.createTempDirectory("repo-epoch-test").toFile()
        try {
            block(SharedRepository(DesktopVault(directory), { "pl" }))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun editSessionIsRejectedAfterTheAccountIsDisconnected() = withRepository { repo ->
        repo.update { it.put("token", "first").put("user", "A") }
        val session = repo.editSession(repo.read())
        repo.update(session) { it.put("note", "saved while the same account is active") }
        assertEquals("saved while the same account is active", repo.read().getString("note"))

        repo.disconnect()
        repo.update { it.put("token", "second").put("user", "B") }

        assertFailsWith<IllegalStateException> { repo.update(session) { it.put("note", "must not land in account B") } }
        assertTrue(!repo.read().has("note"))
    }

    @Test fun editSessionIsRejectedWhenTheLoginIdentityChanged() = withRepository { repo ->
        repo.update { it.put("token", "first") }
        val session = repo.editSession(repo.read())
        repo.update { it.put("token", "rotated") }
        assertFailsWith<IllegalStateException> { repo.update(session) { it.put("note", "x") } }
    }

    @Test fun concurrentUpdatesDoNotLoseWrites() = withRepository { repo ->
        repo.update { it.put("token", "t").put("counter", 0) }
        val threads = 8
        val perThread = 25
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        repeat(threads) {
            pool.execute {
                ready.countDown()
                go.await()
                repeat(perThread) { repo.update { s -> s.put("counter", s.getInt("counter") + 1) } }
            }
        }
        ready.await()
        go.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))
        assertEquals(threads * perThread, repo.read().getInt("counter"))
    }

    @Test fun readsAreMigratedAndWritesPersistTheSchemaVersion() = withRepository { repo ->
        repo.update { it.put("token", "t").put("tasks", org.json.JSONArray().put(JSONObject().put("id", "1").put("done", true).put("progress", 10))) }
        val view = repo.read()
        assertEquals(StateSchema.CURRENT, view.getInt(StateKeys.SCHEMA_VERSION))
        assertEquals(100, view.getJSONArray("tasks").getJSONObject(0).getInt("progress"))
    }
}
