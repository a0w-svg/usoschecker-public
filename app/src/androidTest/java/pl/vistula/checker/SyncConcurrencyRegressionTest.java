package pl.vistula.checker;

import android.content.Context;
import android.content.ContextWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SyncConcurrencyRegressionTest {
    private Context context;
    private File directory;
    private Repository repository;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final CountDownLatch fetching = new CountDownLatch(1), release = new CountDownLatch(1);

    @Before public void setUp() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        org.junit.Assume.assumeTrue("Run with -PisolatedTests", target.getPackageName().endsWith(".qa"));
        directory = new File(target.getCacheDir(), "sync_fixture_" + UUID.randomUUID());
        assertTrue(directory.mkdir());
        context = new ContextWrapper(target) {
            @Override public File getNoBackupFilesDir() { return directory; }
        };
        repository = new Repository(context);
        repository.update(s -> s.put("token", "old-account").put("studentNumber", "test-student").put("enabled", true));
    }

    @After public void tearDown() throws Exception {
        release.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        if (context != null) { new Vault(context).clear(); assertTrue(directory.delete()); }
    }

    @Test public void localReadsAndEditsCompleteWhileNetworkIsBlockedAndSurviveCommit() throws Exception {
        Future<List<Change>> sync = startSync(true, false);
        long cooldown = System.currentTimeMillis() + 60000;
        Future<JSONObject> edit = pool.submit(() -> {
            assertEquals("old-account", repository.read().getString("token"));
            repository.update(s -> s.put("tasks", new JSONArray().put(new JSONObject().put("title", "Concurrent task")))
                .put("interval", 45).put("retryAt", cooldown));
            return repository.read();
        });
        assertEquals(45, edit.get(3, TimeUnit.SECONDS).getInt("interval"));
        release.countDown();
        sync.get(5, TimeUnit.SECONDS);
        JSONObject state = repository.read();
        assertEquals("Concurrent task", state.getJSONArray("tasks").getJSONObject(0).getString("title"));
        assertEquals(45, state.getInt("interval"));
        assertEquals(cooldown, state.getLong("retryAt"));
        assertTrue(state.has("successAt"));
    }

    @Test public void completedOldAccountSyncCannotRestoreDisconnectedData() throws Exception {
        Future<List<Change>> sync = startSync(true, false);
        pool.submit(() -> { repository.disconnect(); return null; }).get(3, TimeUnit.SECONDS);
        release.countDown();
        assertTrue(sync.get(5, TimeUnit.SECONDS).isEmpty());
        assertEquals(0, repository.read().length());
    }

    @Test public void completedOldAccountSyncCannotOverwriteANewAccount() throws Exception {
        Future<List<Change>> sync = startSync(true, false);
        switchAccount();
        release.countDown();
        assertTrue(sync.get(5, TimeUnit.SECONDS).isEmpty());
        assertNewAccountUnchanged();
    }

    @Test public void failedOldAccountSyncCannotWriteErrorsIntoANewAccount() throws Exception {
        Future<List<Change>> sync = startSync(true, true);
        switchAccount();
        release.countDown();
        try { sync.get(5, TimeUnit.SECONDS); fail(); }
        catch (ExecutionException expected) { assertTrue(expected.getCause() instanceof IOException); }
        assertNewAccountUnchanged();
    }

    @Test public void disablingBackgroundSyncWhileFetchingPreventsCommit() throws Exception {
        Future<List<Change>> sync = startSync(false, false);
        pool.submit(() -> { repository.update(s -> s.put("enabled", false)); return null; }).get(3, TimeUnit.SECONDS);
        release.countDown();
        assertTrue(sync.get(5, TimeUnit.SECONDS).isEmpty());
        assertFalse(repository.read().has("snapshot"));
        assertFalse(repository.read().optBoolean("enabled"));
    }

    @Test public void failedRefreshPreservesBaselineAndHistoryAndSuccessfulRetryDoesNotDuplicateChanges() throws Exception {
        Snapshot baseline = new Snapshot("2026-10-01", "2026-11-01");
        baseline.add(new Record("grade", "ocena", "Grade", "4", "", ""));
        Snapshot updated = new Snapshot(baseline.start, baseline.end);
        updated.add(new Record("grade", "ocena", "Grade", "5", "", ""));
        assertTrue(repository.sync(true, s -> baseline).isEmpty());
        assertEquals(1, repository.sync(true, s -> updated).size());
        JSONObject before = repository.read();
        try { repository.sync(true, s -> { throw new IOException("Offline"); }); fail(); }
        catch (IOException expected) {}
        JSONObject after = repository.read();
        assertEquals(before.getJSONObject("snapshot").toString(), after.getJSONObject("snapshot").toString());
        assertEquals(before.getJSONArray("history").toString(), after.getJSONArray("history").toString());
        assertEquals(before.getLong("successAt"), after.getLong("successAt"));
        assertTrue(repository.sync(true, s -> updated).isEmpty());
        assertEquals(1, repository.read().getJSONArray("history").length());
    }

    @Test public void concurrentCooldownAndAuthorizationFailureSurviveAnotherRequestFailure() throws Exception {
        Future<List<Change>> sync = pool.submit(() -> repository.sync(true, credentials -> {
            fetching.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Test request not released");
            throw new UsosClient.ApiException(503, "api_error");
        }));
        assertTrue(fetching.await(5, TimeUnit.SECONDS));
        long cooldown = System.currentTimeMillis() + 60000;
        pool.submit(() -> {
            repository.update(s -> s.put("retryAt", cooldown).put("authorizationError", true).put("enabled", false));
            return null;
        }).get(3, TimeUnit.SECONDS);
        release.countDown();
        try { sync.get(5, TimeUnit.SECONDS); fail(); }
        catch (ExecutionException expected) { assertTrue(expected.getCause() instanceof UsosClient.ApiException); }
        assertEquals(cooldown, repository.read().getLong("retryAt"));
        assertTrue(repository.read().getBoolean("authorizationError"));
        assertFalse(repository.read().getBoolean("enabled"));
    }

    @Test public void notificationPublicationFinishesBeforeLogoutCanClearTheSession() throws Exception {
        CountDownLatch publishing = new CountDownLatch(1), disconnecting = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger order = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger publishedAt = new java.util.concurrent.atomic.AtomicInteger();
        Future<?> sync = pool.submit(() -> repository.sync(true, s -> new Snapshot("2026-10-01", "2026-11-01"), changes -> {
            publishing.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            publishedAt.set(order.incrementAndGet());
        }));
        assertTrue(publishing.await(5, TimeUnit.SECONDS));
        Future<Integer> logout = pool.submit(() -> {
            disconnecting.countDown();
            repository.disconnect();
            return order.incrementAndGet();
        });
        assertTrue(disconnecting.await(5, TimeUnit.SECONDS));
        release.countDown();
        sync.get(5, TimeUnit.SECONDS);
        assertTrue(publishedAt.get() < logout.get(5, TimeUnit.SECONDS));
        assertEquals(0, repository.read().length());
    }

    @Test public void notificationFailureDoesNotDiscardACommittedSnapshot() throws Exception {
        repository.sync(true, s -> new Snapshot("2026-10-01", "2026-11-01"), changes -> { throw new IllegalStateException("Delivery unavailable"); });
        assertTrue(repository.read().has("snapshot"));
        assertTrue(repository.read().has("successAt"));
        assertEquals("", repository.read().getString("error"));
    }

    private Future<List<Change>> startSync(boolean manual, boolean fail) throws Exception {
        Future<List<Change>> future = pool.submit(() -> repository.sync(manual, credentials -> {
            fetching.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Test did not release the request");
            if (fail) throw new IOException("Old account network failure");
            Snapshot snapshot = new Snapshot("2026-10-01", "2026-11-01");
            snapshot.add(new Record("old-grade", "ocena", "Old account grade", "5", "", ""));
            return snapshot;
        }));
        assertTrue("Network request was not started", fetching.await(5, TimeUnit.SECONDS));
        return future;
    }

    private void switchAccount() throws Exception {
        pool.submit(() -> {
            repository.disconnect();
            repository.update(s -> s.put("token", "new-account").put("user", "New user"));
            return null;
        }).get(3, TimeUnit.SECONDS);
    }

    private void assertNewAccountUnchanged() throws Exception {
        JSONObject state = repository.read();
        assertEquals("new-account", state.getString("token"));
        assertEquals("New user", state.getString("user"));
        assertFalse(state.has("snapshot"));
        assertFalse(state.has("error"));
        assertFalse(state.has("successAt"));
    }
}
