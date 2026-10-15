package pl.vistula.checker;

import android.content.Context;
import android.content.ContextWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TaskSessionRegressionTest {
    private Context context;
    private File directory;
    private Repository repository;
    @Before public void setup() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue(target.getPackageName().endsWith(".qa"));
        directory = new File(target.getCacheDir(), "task_session_" + UUID.randomUUID());
        assertTrue(directory.mkdir());
        context = new ContextWrapper(target) {
            @Override public File getNoBackupFilesDir() { return directory; }
        };
        repository = new Repository(context);
    }
    @After public void cleanup() {
        if (context != null) { repository.disconnect(); assertTrue(directory.delete()); }
    }
    private void assertRejected(Repository.EditSession session) throws Exception {
        try { repository.update(session, s -> s.put("privateNote", "old account")); fail("Stale edit accepted"); }
        catch (IllegalStateException expected) { assertFalse(repository.read().has("privateNote")); }
    }
    @Test public void guestTasksRemainEditableAcrossRepositoryInstances() throws Exception {
        Repository.EditSession session = repository.editSession(repository.read());
        new Repository(context).update(session, s -> s.put("privateNote", "guest task"));
        assertEquals("guest task", repository.read().getString("privateNote"));
    }
    @Test public void logoutInvalidatesGuestEditorsEvenWhenStateIsEmptyAgain() throws Exception {
        Repository.EditSession session = repository.editSession(repository.read());
        new Repository(context).disconnect();
        assertRejected(session);
        assertEquals(0, repository.read().length());
    }
    @Test public void reconnectedAccountWithSameTokenRejectsOldEditor() throws Exception {
        repository.update(s -> s.put("token", "same").put("accountSession", "first"));
        Repository.EditSession session = repository.editSession(repository.read());
        repository.update(s -> s.put("accountSession", "second"));
        assertRejected(session);
    }
    @Test public void accountSwitchRejectsOldEditorWithoutChangingNewAccount() throws Exception {
        repository.update(s -> s.put("token", "first"));
        Repository.EditSession session = repository.editSession(repository.read());
        repository.update(s -> s.put("token", "second").put("privateNote", "new account"));
        try { repository.update(session, s -> s.put("privateNote", "old account")); fail(); }
        catch (IllegalStateException expected) { assertEquals("new account", repository.read().getString("privateNote")); }
    }
    @Test public void unrelatedSettingsDoNotInvalidateTaskEditor() throws Exception {
        Repository.EditSession session = repository.editSession(repository.read());
        repository.update(s -> s.put("interval", 45));
        repository.update(session, s -> s.put("privateNote", "task"));
        assertEquals(45, repository.read().getInt("interval"));
    }
}
