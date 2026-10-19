package pl.vistula.checker

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
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

@RunWith(AndroidJUnit4::class)
class AuthConcurrencyRegressionTest {
    private var context: Context? = null
    private lateinit var directory: File
    private lateinit var repository: Repository
    private val pool = Executors.newFixedThreadPool(2)
    private val paused = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private var blockedEndpoint: String? = null
    private var profileUnavailable = false
    private var profileStatus = 200
    private var profileCalls = 0
    private var rejectRichProfile = false

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        Assume.assumeTrue("Run with -PisolatedTests", target.packageName.endsWith(".qa"))
        directory = File(target.cacheDir, "auth_fixture_" + UUID.randomUUID())
        assertTrue(directory.mkdir())
        val ctx = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        context = ctx
        repository = Repository(ctx) { state -> UsosClient(state, {}, ::open) }
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
    fun pendingLoginDoesNotBlockStorageAndPreservesConcurrentSettings() {
        val login = startBegin()
        pool.submit(Callable {
            repository.update { s -> s.put("interval", 45) }
            assertEquals(45, repository.read().getInt("interval"))
            null
        }).get(3, TimeUnit.SECONDS)
        release.countDown()
        login.get(5, TimeUnit.SECONDS)
        assertEquals(45, repository.read().getInt("interval"))
        assertEquals("request-token", repository.read().getString("requestToken"))
    }

    @Test
    fun logoutWhileStartingLoginCannotRestorePendingCredentials() {
        val login = startBegin()
        disconnect()
        release.countDown()
        assertCanceled(login)
        assertEquals(0, repository.read().length())
    }

    @Test
    fun tokenExchangeDoesNotBlockLocalEditsOrOverwriteThem() {
        preparePending()
        val login = startFinish("oauth/access_token")
        pool.submit(Callable {
            repository.update { s -> s.put("interval", 45) }
            null
        }).get(3, TimeUnit.SECONDS)
        release.countDown()
        login.get(5, TimeUnit.SECONDS)
        assertEquals(45, repository.read().getInt("interval"))
        assertEquals("access-token", repository.read().getString("token"))
    }

    @Test
    fun logoutDuringExchangeCannotReconnectTheAccount() {
        preparePending()
        val login = startFinish("oauth/access_token")
        disconnect()
        release.countDown()
        assertCanceled(login)
        assertEquals(0, repository.read().length())
    }

    @Test
    fun accessCredentialsAreDurableBeforeProfileLookupFinishes() {
        preparePending()
        val login = startFinish("users/user")
        val saved = pool.submit(Callable { repository.read() }).get(3, TimeUnit.SECONDS)
        assertEquals("access-token", saved.getString("token"))
        assertEquals("access-secret", saved.getString("tokenSecret"))
        assertFalse(saved.has("requestToken"))
        assertFalse(saved.has("requestSecret"))
        assertFalse(saved.getString("accountSession").isEmpty())
        release.countDown()
        login.get(5, TimeUnit.SECONDS)
    }

    @Test
    fun lateProfileCannotRestoreALoggedOutAccount() {
        preparePending()
        val login = startFinish("users/user")
        disconnect()
        release.countDown()
        assertCanceled(login)
        assertEquals(0, repository.read().length())
    }

    @Test
    fun lateProfileCannotOverwriteAnotherSessionEvenIfTokensMatch() {
        preparePending()
        val login = startFinish("users/user")
        pool.submit(Callable {
            repository.update { s -> s.put("accountSession", "new-session").put("user", "New session user") }
            null
        }).get(3, TimeUnit.SECONDS)
        release.countDown()
        assertCanceled(login)
        assertEquals("New session user", repository.read().getString("user"))
    }

    @Test
    fun unavailableProfileDoesNotLoseIssuedCredentialsOrReuseStaleDetails() {
        preparePending()
        repository.update { s ->
            s.put("email", "old-account@example.test")
                .put("studentServices", JSONObject().put("private", "old"))
        }
        profileUnavailable = true
        repository.finish("1234")
        val saved = repository.read()
        assertEquals("access-token", saved.getString("token"))
        assertEquals("Student USOS", saved.getString("user"))
        assertFalse(saved.has("email"))
        assertFalse(saved.has("studentServices"))
        assertFalse(saved.has("requestSecret"))
    }

    @Test
    fun profileSessionExpiryAndRateLimitsAreSavedWithoutAFallbackRequest() {
        for (status in intArrayOf(401, 429)) {
            repository.disconnect()
            preparePending()
            profileStatus = status
            profileCalls = 0
            val before = System.currentTimeMillis()
            repository.finish("1234")
            val saved = repository.read()
            assertEquals("access-token", saved.getString("token"))
            assertEquals(1, profileCalls)
            if (status == 401) {
                assertTrue(saved.getBoolean("authorizationError"))
                assertFalse(saved.getBoolean("enabled"))
            } else {
                assertTrue(saved.getLong("retryAt") >= before + 60000)
            }
        }
    }

    @Test
    fun unsupportedProfileFieldsCanStillFallBackToBasicProfile() {
        preparePending()
        rejectRichProfile = true
        repository.finish("1234")
        assertEquals(2, profileCalls)
        assertEquals("Test Student", repository.read().getString("user"))
    }

    private fun preparePending() {
        repository.update { s ->
            s.put("key", "test-key")
                .put("secret", "test-secret")
                .put("authAttempt", "test-attempt")
                .put("requestToken", "request-token")
                .put("requestSecret", "request-secret")
                .put("scopes", "grades|studies|offline_access")
        }
    }

    @Test
    fun storedConsentMatchesNormalizedOAuthRequest() {
        repository.begin("test-key", "test-secret", " grades | | studies | grades | offline_access | ")
        assertEquals("grades|studies|offline_access", repository.read().getString("scopes"))
        repository.finish("1234")
        assertEquals("grades|studies|offline_access", repository.read().getString("scopes"))
    }

    private fun startBegin(): Future<*> {
        blockedEndpoint = "oauth/request_token"
        val future = pool.submit(Callable {
            repository.begin("test-key", "test-secret", "grades")
            null
        })
        assertTrue(paused.await(5, TimeUnit.SECONDS))
        return future
    }

    private fun startFinish(endpoint: String): Future<*> {
        blockedEndpoint = endpoint
        val future = pool.submit(Callable {
            repository.finish("1234")
            null
        })
        assertTrue(paused.await(5, TimeUnit.SECONDS))
        return future
    }

    private fun disconnect() {
        pool.submit(Callable {
            repository.disconnect()
            null
        }).get(3, TimeUnit.SECONDS)
    }

    private fun assertCanceled(future: Future<*>) {
        try {
            future.get(5, TimeUnit.SECONDS)
            fail("Canceled login was accepted")
        } catch (expected: ExecutionException) {
            assertTrue(expected.cause is IllegalStateException)
        }
    }

    private fun open(address: String): HttpURLConnection {
        val blocked = blockedEndpoint
        if (blocked != null && address.contains(blocked)) {
            paused.countDown()
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw IOException("Test request was not released")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException(e)
            }
        }
        val body = when {
            address.contains("oauth/request_token") -> "oauth_token=request-token&oauth_token_secret=request-secret&oauth_callback_confirmed=true"
            address.contains("oauth/access_token") -> "oauth_token=access-token&oauth_token_secret=access-secret"
            address.contains("users/user") -> {
                profileCalls++
                if (profileUnavailable) throw IOException("Profile unavailable")
                if (profileStatus != 200) return Connection(if (profileStatus == 401) "oauth_problem=token_expired" else "{}", profileStatus)
                if (rejectRichProfile && address.contains("student_number")) return Connection("{}", 400)
                "{\"id\":\"1\",\"first_name\":\"Test\",\"last_name\":\"Student\"}"
            }
            else -> throw AssertionError("Unexpected endpoint")
        }
        return Connection(body)
    }

    private class Connection(private val body: String, private val status: Int = 200) : HttpURLConnection(URL(UsosClient.BASE)) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getHeaderField(name: String): String? = if (status == 429 && name == "Retry-After") "60" else null
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8))
        override fun getErrorStream(): InputStream = inputStream
    }
}
