package pl.vistula.checker;

import android.content.Context;
import java.util.List;
import java.util.function.Consumer;
import org.json.JSONObject;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.Snapshot;
import pl.vistula.checker.ui.Language;

/** Android adapter around KMP SharedRepository using Android Keystore Vault and SharedPreferences. */
public final class Repository implements StateRepository {
    static final Object LOCK = SharedRepository.LOCK;
    private final SharedRepository delegate;

    public static final class EditSession {
        final SharedRepository.EditSession inner;
        EditSession(SharedRepository.EditSession inner) { this.inner = inner; }
    }

    public interface ClientFactory extends SharedRepository.ClientFactory {}
    public interface Update extends StateRepository.Update {}
    public interface SnapshotSource extends SharedRepository.SnapshotSource {}

    public Repository(Context c) {
        this(c, UsosClient::new);
    }

    Repository(Context c, ClientFactory clients) {
        Context appContext = c.getApplicationContext();
        android.content.SharedPreferences attendance = c.getSharedPreferences("attendance_tracker", Context.MODE_PRIVATE);
        this.delegate = new SharedRepository(
            new Vault(c),
            () -> Language.currentLocale(appContext).getLanguage(),
            () -> attendance.edit().clear().apply(),
            changes -> NotificationHelper.notifyChanges(appContext, changes),
            clients
        );
    }

    public EditSession editSession(JSONObject displayedState) {
        return new EditSession(delegate.editSession(displayedState));
    }

    public void update(EditSession session, Update operation) throws Exception {
        delegate.update(session.inner, operation);
    }

    @Override
    public JSONObject read() throws Exception {
        return delegate.read();
    }

    static JSONObject localizedView(JSONObject source, String language) throws Exception {
        return SharedRepository.localizedView(source, language);
    }

    @Override
    public void update(StateRepository.Update operation) throws Exception {
        delegate.update(operation);
    }

    public void update(Update operation) throws Exception {
        delegate.update(operation);
    }

    public void begin(String key, String secret, String scopes) throws Exception {
        delegate.begin(key, secret, scopes);
    }

    public void finish(String pin) throws Exception {
        delegate.finish(pin);
    }

    public List<Change> sync(boolean manual) throws Exception {
        return delegate.sync(manual);
    }

    public List<Change> syncWithProgress(boolean manual, Consumer<String> progress) throws Exception {
        return delegate.syncWithProgress(manual, progress);
    }

    List<Change> syncAndNotify(boolean manual, Consumer<String> progress) throws Exception {
        return delegate.syncAndNotify(manual, progress);
    }

    List<Change> sync(boolean manual, SnapshotSource source) throws Exception {
        return delegate.sync(manual, source);
    }

    List<Change> sync(boolean manual, SnapshotSource source, Consumer<List<Change>> publish) throws Exception {
        return delegate.sync(manual, source, publish, false);
    }

    static boolean sameAccount(JSONObject expected, JSONObject current) {
        return SharedRepository.sameAccount(expected, current);
    }

    public void disconnect() {
        delegate.disconnect();
    }

    static Exception cause(Exception e) {
        return SharedRepository.cause(e);
    }

    public static String message(Exception e) {
        return SharedRepository.message(e);
    }
}
