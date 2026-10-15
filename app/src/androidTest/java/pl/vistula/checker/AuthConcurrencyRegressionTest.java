package pl.vistula.checker;

import android.content.Context;
import android.content.ContextWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AuthConcurrencyRegressionTest {
    private Context context;
    private File directory;
    private Repository repository;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final CountDownLatch paused = new CountDownLatch(1), release = new CountDownLatch(1);
    private String blockedEndpoint;
    private boolean profileUnavailable;
    private int profileStatus = 200, profileCalls;
    private boolean rejectRichProfile;

    @Before public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue("Run with -PisolatedTests", target.getPackageName().endsWith(".qa"));
        directory = new File(target.getCacheDir(), "auth_fixture_" + UUID.randomUUID());
        assertTrue(directory.mkdir());
        context = new ContextWrapper(target) {
            @Override public File getNoBackupFilesDir() { return directory; }
        };
        repository = new Repository(context, state -> new UsosClient(state, ignored -> {}, this::open));
    }

    @After public void tearDown() throws Exception {
        release.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        if (context != null) { new Vault(context).clear(); assertTrue(directory.delete()); }
    }

    @Test public void pendingLoginDoesNotBlockStorageAndPreservesConcurrentSettings() throws Exception {
        Future<?> login = startBegin();
        pool.submit(() -> {
            repository.update(s -> s.put("interval", 45));
            assertEquals(45, repository.read().getInt("interval"));
            return null;
        }).get(3, TimeUnit.SECONDS);
        release.countDown(); login.get(5, TimeUnit.SECONDS);
        assertEquals(45, repository.read().getInt("interval"));
        assertEquals("request-token", repository.read().getString("requestToken"));
    }

    @Test public void logoutWhileStartingLoginCannotRestorePendingCredentials() throws Exception {
        Future<?> login = startBegin();
        disconnect();
        release.countDown(); assertCanceled(login);
        assertEquals(0, repository.read().length());
    }

    @Test public void tokenExchangeDoesNotBlockLocalEditsOrOverwriteThem() throws Exception {
        preparePending();
        Future<?> login = startFinish("oauth/access_token");
        pool.submit(() -> { repository.update(s -> s.put("interval", 45)); return null; }).get(3, TimeUnit.SECONDS);
        release.countDown(); login.get(5, TimeUnit.SECONDS);
        assertEquals(45, repository.read().getInt("interval"));
        assertEquals("access-token", repository.read().getString("token"));
    }

    @Test public void logoutDuringExchangeCannotReconnectTheAccount() throws Exception {
        preparePending();
        Future<?> login = startFinish("oauth/access_token");
        disconnect();
        release.countDown(); assertCanceled(login);
        assertEquals(0, repository.read().length());
    }

    @Test public void accessCredentialsAreDurableBeforeProfileLookupFinishes() throws Exception {
        preparePending();
        Future<?> login = startFinish("users/user");
        JSONObject saved = pool.submit(() -> repository.read()).get(3, TimeUnit.SECONDS);
        assertEquals("access-token", saved.getString("token"));
        assertEquals("access-secret", saved.getString("tokenSecret"));
        assertFalse(saved.has("requestToken"));
        assertFalse(saved.has("requestSecret"));
        assertFalse(saved.getString("accountSession").isEmpty());
        release.countDown(); login.get(5, TimeUnit.SECONDS);
    }

    @Test public void lateProfileCannotRestoreALoggedOutAccount() throws Exception {
        preparePending();
        Future<?> login = startFinish("users/user");
        disconnect();
        release.countDown(); assertCanceled(login);
        assertEquals(0, repository.read().length());
    }

    @Test public void lateProfileCannotOverwriteAnotherSessionEvenIfTokensMatch() throws Exception {
        preparePending();
        Future<?> login = startFinish("users/user");
        pool.submit(() -> {
            repository.update(s -> s.put("accountSession", "new-session").put("user", "New session user"));
            return null;
        }).get(3, TimeUnit.SECONDS);
        release.countDown(); assertCanceled(login);
        assertEquals("New session user", repository.read().getString("user"));
    }

    @Test public void unavailableProfileDoesNotLoseIssuedCredentialsOrReuseStaleDetails() throws Exception {
        preparePending();
        repository.update(s -> s.put("email", "old-account@example.test").put("studentServices", new JSONObject().put("private", "old")));
        profileUnavailable = true;
        repository.finish("1234");
        JSONObject saved = repository.read();
        assertEquals("access-token", saved.getString("token"));
        assertEquals("Student USOS", saved.getString("user"));
        assertFalse(saved.has("email"));
        assertFalse(saved.has("studentServices"));
        assertFalse(saved.has("requestSecret"));
    }

    @Test public void profileSessionExpiryAndRateLimitsAreSavedWithoutAFallbackRequest() throws Exception {
        for (int status : new int[]{401, 429}) {
            repository.disconnect();
            preparePending();
            profileStatus = status;
            profileCalls = 0;
            long before = System.currentTimeMillis();
            repository.finish("1234");
            JSONObject saved = repository.read();
            assertEquals("access-token", saved.getString("token"));
            assertEquals(1, profileCalls);
            if (status == 401) {
                assertTrue(saved.getBoolean("authorizationError"));
                assertFalse(saved.getBoolean("enabled"));
            } else assertTrue(saved.getLong("retryAt") >= before + 60000);
        }
    }

    @Test public void unsupportedProfileFieldsCanStillFallBackToBasicProfile() throws Exception {
        preparePending();
        rejectRichProfile = true;
        repository.finish("1234");
        assertEquals(2, profileCalls);
        assertEquals("Test Student", repository.read().getString("user"));
    }

    private void preparePending() throws Exception {
        repository.update(s -> s.put("key", "test-key").put("secret", "test-secret").put("authAttempt", "test-attempt")
            .put("requestToken", "request-token").put("requestSecret", "request-secret"));
    }
    private Future<?> startBegin() throws Exception {
        blockedEndpoint = "oauth/request_token";
        Future<?> future = pool.submit(() -> { repository.begin("test-key", "test-secret", "grades"); return null; });
        assertTrue(paused.await(5, TimeUnit.SECONDS));
        return future;
    }
    private Future<?> startFinish(String endpoint) throws Exception {
        blockedEndpoint = endpoint;
        Future<?> future = pool.submit(() -> { repository.finish("1234"); return null; });
        assertTrue(paused.await(5, TimeUnit.SECONDS));
        return future;
    }
    private void disconnect() throws Exception {
        pool.submit(() -> { repository.disconnect(); return null; }).get(3, TimeUnit.SECONDS);
    }
    private void assertCanceled(Future<?> future) throws Exception {
        try { future.get(5, TimeUnit.SECONDS); fail("Canceled login was accepted"); }
        catch (ExecutionException expected) { assertTrue(expected.getCause() instanceof IllegalStateException); }
    }
    private HttpURLConnection open(String address) throws IOException {
        if (blockedEndpoint != null && address.contains(blockedEndpoint)) {
            paused.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Test request was not released"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        }
        String body;
        if (address.contains("oauth/request_token")) body = "oauth_token=request-token&oauth_token_secret=request-secret&oauth_callback_confirmed=true";
        else if (address.contains("oauth/access_token")) body = "oauth_token=access-token&oauth_token_secret=access-secret";
        else if (address.contains("users/user")) {
            profileCalls++;
            if (profileUnavailable) throw new IOException("Profile unavailable");
            if (profileStatus != 200) return new Connection(profileStatus == 401 ? "oauth_problem=token_expired" : "{}", profileStatus);
            if (rejectRichProfile && address.contains("student_number")) return new Connection("{}", 400);
            body = "{\"id\":\"1\",\"first_name\":\"Test\",\"last_name\":\"Student\"}";
        } else throw new AssertionError("Unexpected endpoint");
        return new Connection(body);
    }
    private static final class Connection extends HttpURLConnection {
        private final String body;
        private final int status;
        Connection(String body) throws MalformedURLException { this(body, 200); }
        Connection(String body, int status) throws MalformedURLException { super(new URL(UsosClient.BASE)); this.body = body; this.status = status; }
        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return status; }
        @Override public String getHeaderField(String name) { return status == 429 && name.equals("Retry-After") ? "60" : null; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)); }
        @Override public InputStream getErrorStream() { return getInputStream(); }
    }
}
