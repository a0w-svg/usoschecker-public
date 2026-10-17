package pl.vistula.checker;

import android.content.Context;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.json.*;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.Diff;
import pl.vistula.checker.core.Snapshot;
import pl.vistula.checker.core.UsosParser;

public final class Repository {
    static final Object LOCK = new Object();
    private static final Object SYNC_LOCK = new Object();
    private static final Object AUTH_LOCK = new Object();
    private final Vault vault;
    private final Context context;
    private final android.content.SharedPreferences attendance;
    private static final Map<String, Object> editEpochs = new HashMap<>();
    private final String storePath;
    public static final class EditSession {
        private final Object epoch;
        private final String[] identity;
        private EditSession(Object epoch, JSONObject state) {
            this.epoch = epoch;
            identity = Arrays.stream(IDENTITY_FIELDS).map(state::optString).toArray(String[]::new);
        }
    }
    private static final String[] IDENTITY_FIELDS = {"token", "tokenSecret", "key", "secret", "accountSession"};
    interface ClientFactory { UsosClient create(JSONObject state); }
    private final ClientFactory clients;
    public Repository(Context c) { this(c, UsosClient::new); }
    Repository(Context c, ClientFactory clients) { vault = new Vault(c); this.clients = clients; context = c.getApplicationContext(); storePath = c.getNoBackupFilesDir().getAbsolutePath(); attendance = c.getSharedPreferences("attendance_tracker", Context.MODE_PRIVATE); }
    public EditSession editSession(JSONObject displayedState) {
        synchronized (LOCK) { return new EditSession(editEpochs.computeIfAbsent(storePath, ignored -> new Object()), displayedState); }
    }
    public void update(EditSession session, Update operation) throws Exception {
        synchronized (LOCK) {
            JSONObject current = vault.read();
            if (session.epoch != editEpochs.get(storePath)) throw loginChanged();
            for (int i = 0; i < IDENTITY_FIELDS.length; i++)
                if (!session.identity[i].equals(current.optString(IDENTITY_FIELDS[i]))) throw loginChanged();
            operation.apply(current);
            vault.write(current);
        }
    }
    public JSONObject read() throws Exception { synchronized (LOCK) { return vault.read(); } }
    public interface Update { void apply(JSONObject state) throws Exception; }
    public void update(Update operation) throws Exception {
        synchronized (LOCK) { JSONObject state = vault.read(); operation.apply(state); vault.write(state); }
    }
    public void begin(String key, String secret, String scopes) throws Exception {
        scopes = pl.vistula.checker.core.StudentServices.normalizeScopes(scopes);
        synchronized (AUTH_LOCK) {
            String attempt = UUID.randomUUID().toString();
            synchronized (LOCK) {
                JSONObject s = vault.read();
                if (!s.optString("token").isEmpty()) throw new IllegalStateException("Najpierw odłącz obecne konto.");
                s.put("authAttempt", attempt);
                vault.write(s);
            }
            JSONObject credentials = new JSONObject().put("key", key).put("secret", secret);
            Map<String, String> request = clients.create(credentials).begin(scopes);
            synchronized (LOCK) {
                JSONObject s = vault.read();
                if (!attempt.equals(s.optString("authAttempt")) || !s.optString("token").isEmpty()) throw loginChanged();
                s.put("key", key).put("secret", secret).put("requestToken", request.get("oauth_token"))
                    .put("requestSecret", request.get("oauth_token_secret")).put("scopes", scopes);
                vault.write(s);
            }
        }
    }
    public void finish(String pin) throws Exception {
        JSONObject connected;
        synchronized (AUTH_LOCK) {
            JSONObject pending = read();
            if (pending.optString("requestToken").isEmpty()) throw new IllegalStateException("Najpierw rozpocznij logowanie w przeglądarce.");
            if (!pending.optString("token").isEmpty()) throw loginChanged();
            Map<String, String> access = clients.create(pending).finish(pending, pin);
            synchronized (LOCK) {
                JSONObject s = vault.read();
                for (String field : new String[]{"authAttempt", "key", "secret", "requestToken", "requestSecret", "token"})
                    if (!pending.optString(field).equals(s.optString(field))) throw loginChanged();
                s.put("token", access.get("oauth_token")).put("tokenSecret", access.get("oauth_token_secret"))
                    .put("accountSession", UUID.randomUUID().toString());
                for (String field : new String[]{"requestToken", "requestSecret", "authAttempt", "snapshot", "history", "studentServices",
                        "retryAt", "authorizationError", "studentNumber", "email", "programme", "studentStatus", "stage", "admissionDate", "photoUrl", "reminderDeliveries"}) s.remove(field);
                s.put("enabled", true).put("error", "").put("user", "Student USOS");
                vault.write(s);
                connected = new JSONObject(s.toString());
            }
        }
        // Access credentials must survive a slow, unavailable, or interrupted profile lookup.
        JSONObject profile = new JSONObject();
        Exception profileFailure = null;
        try {
            JSONObject user = new JSONObject(clients.create(connected).get("services/users/user",
                Map.of("fields", UsosClient.profileFields(connected))));
            UsosParser.UserProfile parsed = UsosParser.userProfile(user);
            profile.put("user", parsed.name.isEmpty() ? (user.optString("first_name") + " " + user.optString("last_name")).trim() : parsed.name);
            if (!parsed.studentNumber.isEmpty()) profile.put("studentNumber", parsed.studentNumber);
            if (!parsed.email.isEmpty()) profile.put("email", parsed.email);
            if (!parsed.programme.isEmpty()) profile.put("programme", parsed.programme);
            if (!parsed.studentStatus.isEmpty()) profile.put("studentStatus", parsed.studentStatus);
            if (!parsed.stage.isEmpty()) profile.put("stage", parsed.stage);
            if (!parsed.admissionDate.isEmpty()) profile.put("admissionDate", parsed.admissionDate);
            if (!parsed.photoUrl.isEmpty()) profile.put("photoUrl", parsed.photoUrl);
        } catch (Exception e) {
            profileFailure = e;
            boolean fallback = e instanceof JSONException || (e instanceof UsosClient.ApiException
                && !((UsosClient.ApiException) e).authorization
                && (((UsosClient.ApiException) e).status == 400 || ((UsosClient.ApiException) e).status == 403));
            if (fallback) try {
                JSONObject user = new JSONObject(clients.create(connected).get("services/users/user",
                    Map.of("fields", "id|first_name|last_name")));
                profile.put("user", (user.optString("first_name") + " " + user.optString("last_name")).trim());
                profileFailure = null;
            } catch (Exception basicFailure) { profileFailure = basicFailure; }
        }
        synchronized (LOCK) {
            JSONObject latest = vault.read();
            if (!sameAccount(connected, latest)) throw loginChanged();
            java.util.Iterator<String> fields = profile.keys();
            while (fields.hasNext()) { String field = fields.next(); latest.put(field, profile.get(field)); }
            if (profileFailure instanceof UsosClient.ApiException) {
                UsosClient.ApiException failure = (UsosClient.ApiException) profileFailure;
                latest.put("error", message(failure));
                latest.put("retryAt", Math.max(latest.optLong("retryAt"), failure.retryAt));
                if (failure.authorization) latest.put("authorizationError", true).put("enabled", false);
            }
            vault.write(latest);
        }
    }
    private static IllegalStateException loginChanged() { return new IllegalStateException("Logowanie zostało anulowane lub konto się zmieniło."); }
    public List<Change> sync(boolean manual) throws Exception {
        return sync(manual, s -> new UsosClient(s).snapshot());
    }
    public List<Change> syncWithProgress(boolean manual, java.util.function.Consumer<String> progress) throws Exception {
        return sync(manual, s -> new UsosClient(s, progress).snapshot());
    }
    List<Change> syncAndNotify(boolean manual, java.util.function.Consumer<String> progress) throws Exception {
        return sync(manual, s -> new UsosClient(s, progress).snapshot(), changes -> NotificationHelper.notifyChanges(context, changes));
    }
    interface SnapshotSource { Snapshot fetch(JSONObject state) throws Exception; }
    List<Change> sync(boolean manual, SnapshotSource source) throws Exception {
        return sync(manual, source, changes -> {});
    }
    List<Change> sync(boolean manual, SnapshotSource source, java.util.function.Consumer<List<Change>> publish) throws Exception {
        synchronized (SYNC_LOCK) {
            JSONObject s;
            synchronized (LOCK) {
                s = vault.read();
                if (s.optString("token").isEmpty() || (!manual && !s.optBoolean("enabled"))) return List.of();
                if (s.optLong("retryAt") > System.currentTimeMillis()) throw new IllegalStateException("USOS prosi o przerwę w żądaniach. Spróbuj po "
                    + java.time.Instant.ofEpochMilli(s.getLong("retryAt")).atZone(UsosClient.ZONE).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")) + ".");
                s.put("attemptAt", System.currentTimeMillis()); vault.write(s);
            }
            try {
                if (s.optString("studentNumber").isEmpty()) {
                    try {
                        JSONObject user = new JSONObject(new UsosClient(s).get("services/users/user",
                            Map.of("fields", UsosClient.profileFields(s))));
                        UsosParser.UserProfile profile = UsosParser.userProfile(user);
                        if (!profile.name.isEmpty()) s.put("user", profile.name);
                        if (!profile.studentNumber.isEmpty()) s.put("studentNumber", profile.studentNumber);
                        if (!profile.email.isEmpty()) s.put("email", profile.email);
                        if (!profile.programme.isEmpty()) s.put("programme", profile.programme);
                        if (!profile.studentStatus.isEmpty()) s.put("studentStatus", profile.studentStatus);
                        if (!profile.stage.isEmpty()) s.put("stage", profile.stage);
                        if (!profile.admissionDate.isEmpty()) s.put("admissionDate", profile.admissionDate);
                        if (!profile.photoUrl.isEmpty()) s.put("photoUrl", profile.photoUrl);
                    } catch (Exception ignored) {}
                }
                Snapshot current = source.fetch(s);
                synchronized (LOCK) {
                    JSONObject latest = vault.read();
                    if (!sameAccount(s, latest) || (!manual && !latest.optBoolean("enabled"))) return List.of();
                    Snapshot previous = latest.optJSONObject("snapshot") == null ? null : Snapshot.from(latest.getJSONObject("snapshot"));
                    long now = System.currentTimeMillis();
                    List<Change> changes = Diff.between(previous, current, now, LocalDate.now(UsosClient.ZONE).toString());
                    JSONArray oldHistory = latest.optJSONArray("history");
                    JSONArray history = new JSONArray();
                    for (Change c : changes) { if (history.length() < 200) history.put(c.json()); }
                    if (oldHistory != null) for (int i = 0; i < oldHistory.length() && history.length() < 200; i++) history.put(oldHistory.get(i));
                    JSONObject snapshot = current.json();
                    for (String field : new String[]{"user", "studentNumber", "email", "programme", "studentStatus", "stage", "admissionDate", "photoUrl"}) {
                        if (s.has(field)) latest.put(field, s.get(field));
                    }
                    latest.put("history", history).put("snapshot", snapshot).put("successAt", now).put("error", "");
                    // Do not erase a cooldown or authorization failure reported by another request during this sync.
                    if (latest.optLong("retryAt") == s.optLong("retryAt")) latest.remove("retryAt");
                    if (latest.optBoolean("authorizationError") == s.optBoolean("authorizationError")) latest.remove("authorizationError");
                    vault.write(latest);
                    // Publish under the account lock so logout can clear every notification after it completes.
                    try { publish.accept(changes); }
                    catch (RuntimeException ignored) { /* A delivery failure must not invalidate a committed snapshot. */ }
                    return changes;
                }
            } catch (Exception e) {
                synchronized (LOCK) {
                    JSONObject latest = vault.read();
                    if (sameAccount(s, latest)) {
                        latest.put("error", message(e));
                        Exception cause = cause(e);
                        if (cause instanceof UsosClient.ApiException && ((UsosClient.ApiException) cause).authorization) latest.put("enabled", false);
                        if (cause instanceof UsosClient.ApiException) {
                            UsosClient.ApiException failure = (UsosClient.ApiException) cause;
                            latest.put("retryAt", Math.max(latest.optLong("retryAt"), failure.retryAt))
                                .put("authorizationError", latest.optBoolean("authorizationError") || failure.authorization);
                        }
                        vault.write(latest);
                    }
                }
                throw e;
            }
        }
    }
    static boolean sameAccount(JSONObject expected, JSONObject current) {
        for (String field : new String[]{"token", "tokenSecret", "key", "secret", "accountSession"})
            if (!expected.optString(field).equals(current.optString(field))) return false;
        return !current.optString("token").isEmpty();
    }
    public void disconnect() { synchronized (LOCK) {
        vault.clear();
        attendance.edit().clear().apply();
        editEpochs.put(storePath, new Object());
    } }
    static Exception cause(Exception e) {
        return e instanceof UsosClient.SyncException && e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
    }
    static String message(Exception e) {
        if (e instanceof UsosClient.SyncException) return "Etap: " + ((UsosClient.SyncException) e).stage + "\n" + message(cause(e));
        if (e instanceof java.net.UnknownHostException) return "Brak połączenia z USOS. Sprawdź dostęp do internetu.";
        if (e instanceof java.net.SocketTimeoutException) return "USOS nie odpowiedział na czas. Spróbuj ponownie.";
        if (e instanceof JSONException || e instanceof java.time.format.DateTimeParseException)
            return "USOS zwrócił niepełne lub nieznane dane. Poprzedni zapis został zachowany.";
        if (e instanceof UsosClient.ApiException || e instanceof IllegalStateException || e instanceof IllegalArgumentException)
            return e.getMessage() == null ? "Nie udało się wykonać operacji." : e.getMessage();
        if (e instanceof IOException) return "Nie udało się pobrać lub zapisać danych. Poprzedni zapis został zachowany.";
        return "Nie udało się odczytać bezpiecznego magazynu danych. Spróbuj ponownie lub odłącz konto.";
    }
}
