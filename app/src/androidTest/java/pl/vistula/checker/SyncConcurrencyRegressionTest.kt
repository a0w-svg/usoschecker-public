package pl.vistula.checker

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot

@RunWith(AndroidJUnit4::class)
class SyncConcurrencyRegressionTest {
    private var context: Context? = null
    private lateinit var directory: File
    private lateinit var repository: Repository
    private val pool = Executors.newFixedThreadPool(2)
    private val fetching = CountDownLatch(1)
    private val release = CountDownLatch(1)

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue("Run with -PisolatedTests", target.packageName.endsWith(".qa"))
        directory = File(target.cacheDir, "sync_fixture_" + UUID.randomUUID())
        assertTrue(directory.mkdir())
        val ctx = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        context = ctx
        repository = Repository(ctx)
        repository.update { s -> s.put("token", "old-account").put("studentNumber", "test-student").put("enabled", true) }
    }

    @After
    fun tearDown() {
        release.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        context?.let {
            Vault(it).clear()
            assertTrue(directory.delete())
        }
    }

    @Test
    fun localReadsAndEditsCompleteWhileNetworkIsBlockedAndSurviveCommit() {
        val sync = startSync(manual = true, fail = false)
        val cooldown = System.currentTimeMillis() + 60000
        val edit = pool.submit(Callable {
            assertEquals("old-account", repository.read().getString("token"))
            repository.update { s ->
                s.put("tasks", JSONArray().put(JSONObject().put("title", "Concurrent task")))
                    .put("interval", 45)
                    .put("retryAt", cooldown)
            }
            repository.read()
        })
        assertEquals(45, edit.get(3, TimeUnit.SECONDS).getInt("interval"))
        release.countDown()
        sync.get(5, TimeUnit.SECONDS)
        val state = repository.read()
        assertEquals("Concurrent task", state.getJSONArray("tasks").getJSONObject(0).getString("title"))
        assertEquals(45, state.getInt("interval"))
        assertEquals(cooldown, state.getLong("retryAt"))
        assertTrue(state.has("successAt"))
    }

    @Test
    fun completedOldAccountSyncCannotRestoreDisconnectedData() {
        val sync = startSync(manual = true, fail = false)
        pool.submit(Callable {
            repository.disconnect()
            null
        }).get(3, TimeUnit.SECONDS)
        release.countDown()
        assertTrue(sync.get(5, TimeUnit.SECONDS).isEmpty())
        assertEquals(0, repository.read().length())
    }

    @Test
    fun completedOldAccountSyncCannotOverwriteANewAccount() {
        val sync = startSync(manual = true, fail = false)
        switchAccount()
        release.countDown()
        assertTrue(sync.get(5, TimeUnit.SECONDS).isEmpty())
        assertNewAccountUnchanged()
    }

    @Test
    fun failedOldAccountSyncCannotWriteErrorsIntoANewAccount() {
        val sync = startSync(manual = true, fail = true)
        switchAccount()
        release.countDown()
        try {
            sync.get(5, TimeUnit.SECONDS)
            fail()
        } catch (expected: ExecutionException) {
            assertTrue(expected.cause is IOException)
        }
        assertNewAccountUnchanged()
    }

    @Test
    fun disablingBackgroundSyncWhileFetchingPreventsCommit() {
        val sync = startSync(manual = false, fail = false)
        pool.submit(Callable {
            repository.update { s -> s.put("enabled", false) }
            null
        }).get(3, TimeUnit.SECONDS)
        release.countDown()
        assertTrue(sync.get(5, TimeUnit.SECONDS).isEmpty())
        assertFalse(repository.read().has("snapshot"))
        assertFalse(repository.read().optBoolean("enabled"))
    }

    @Test
    fun failedRefreshPreservesBaselineAndHistoryAndSuccessfulRetryDoesNotDuplicateChanges() {
        val baseline = Snapshot("2026-10-01", "2026-11-01")
        baseline.add(Record("grade", "ocena", "Grade", "4", "", ""))
        val updated = Snapshot(baseline.start, baseline.end)
        updated.add(Record("grade", "ocena", "Grade", "5", "", ""))
        assertTrue(repository.sync(true) { baseline }.isEmpty())
        assertEquals(1, repository.sync(true) { updated }.size)
        val before = repository.read()
        try {
            repository.sync(true) { throw IOException("Offline") }
            fail()
        } catch (expected: IOException) {}
        val after = repository.read()
        assertEquals(before.getJSONObject("snapshot").toString(), after.getJSONObject("snapshot").toString())
        assertEquals(before.getJSONArray("history").toString(), after.getJSONArray("history").toString())
        assertEquals(before.getLong("successAt"), after.getLong("successAt"))
        assertTrue(repository.sync(true) { updated }.isEmpty())
        assertEquals(1, repository.read().getJSONArray("history").length())
    }

    @Test
    fun concurrentCooldownAndAuthorizationFailureSurviveAnotherRequestFailure() {
        val sync = pool.submit(Callable {
            repository.sync(true) {
                fetching.countDown()
                if (!release.await(10, TimeUnit.SECONDS)) throw IOException("Test request not released")
                throw UsosClient.ApiException(503, "api_error")
            }
        })
        assertTrue(fetching.await(5, TimeUnit.SECONDS))
        val cooldown = System.currentTimeMillis() + 60000
        pool.submit(Callable {
            repository.update { s -> s.put("retryAt", cooldown).put("authorizationError", true).put("enabled", false) }
            null
        }).get(3, TimeUnit.SECONDS)
        release.countDown()
        try {
            sync.get(5, TimeUnit.SECONDS)
            fail()
        } catch (expected: ExecutionException) {
            assertTrue(expected.cause is UsosClient.ApiException)
        }
        assertEquals(cooldown, repository.read().getLong("retryAt"))
        assertTrue(repository.read().getBoolean("authorizationError"))
        assertFalse(repository.read().getBoolean("enabled"))
    }

    @Test
    fun notificationPublicationFinishesBeforeLogoutCanClearTheSession() {
        val publishing = CountDownLatch(1)
        val disconnecting = CountDownLatch(1)
        val order = AtomicInteger()
        val publishedAt = AtomicInteger()
        val sync = pool.submit(Callable {
            repository.sync(
                true,
                { Snapshot("2026-10-01", "2026-11-01") },
                {
                    publishing.countDown()
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS))
                    } catch (e: InterruptedException) {
                        throw AssertionError(e)
                    }
                    publishedAt.set(order.incrementAndGet())
                }
            )
        })
        assertTrue(publishing.await(5, TimeUnit.SECONDS))
        val logout = pool.submit(Callable {
            disconnecting.countDown()
            repository.disconnect()
            order.incrementAndGet()
        })
        assertTrue(disconnecting.await(5, TimeUnit.SECONDS))
        release.countDown()
        sync.get(5, TimeUnit.SECONDS)
        assertTrue(publishedAt.get() < logout.get(5, TimeUnit.SECONDS))
        assertEquals(0, repository.read().length())
    }

    @Test
    fun notificationFailureDoesNotDiscardACommittedSnapshot() {
        repository.sync(
            true,
            { Snapshot("2026-10-01", "2026-11-01") },
            { throw IllegalStateException("Delivery unavailable") }
        )
        assertTrue(repository.read().has("snapshot"))
        assertTrue(repository.read().has("successAt"))
        assertEquals("", repository.read().getString("error"))
    }

    private fun startSync(manual: Boolean, fail: Boolean): Future<List<Change>> {
        val future = pool.submit(Callable {
            repository.sync(manual) {
                fetching.countDown()
                if (!release.await(10, TimeUnit.SECONDS)) throw IOException("Test did not release the request")
                if (fail) throw IOException("Old account network failure")
                val snapshot = Snapshot("2026-10-01", "2026-11-01")
                snapshot.add(Record("old-grade", "ocena", "Old account grade", "5", "", ""))
                snapshot
            }
        })
        assertTrue("Network request was not started", fetching.await(5, TimeUnit.SECONDS))
        return future
    }

    private fun switchAccount() {
        pool.submit(Callable {
            repository.disconnect()
            repository.update { s -> s.put("token", "new-account").put("user", "New user") }
            null
        }).get(3, TimeUnit.SECONDS)
    }

    private fun assertNewAccountUnchanged() {
        val state = repository.read()
        assertEquals("new-account", state.getString("token"))
        assertEquals("New user", state.getString("user"))
        assertFalse(state.has("snapshot"))
        assertFalse(state.has("error"))
        assertFalse(state.has("successAt"))
    }
}
