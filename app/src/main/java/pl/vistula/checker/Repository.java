package pl.vistula.checker;

import android.content.Context;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.json.*;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.Diff;
import pl.vistula.checker.core.Snapshot;

final class Repository {
    static final Object LOCK = new Object();
    private final Vault vault;
    Repository(Context c) { vault = new Vault(c); }
    JSONObject read() throws Exception { synchronized (LOCK) { return vault.read(); } }
    interface Update { void apply(JSONObject state) throws Exception; }
    void update(Update operation) throws Exception {
        synchronized (LOCK) { JSONObject state = vault.read(); operation.apply(state); vault.write(state); }
    }
    void begin(String key, String secret, String scopes) throws Exception {
        synchronized (LOCK) {
            JSONObject s = vault.read();
            if (!s.optString("token").isEmpty()) throw new IllegalStateException("Najpierw odłącz obecne konto.");
            JSONObject credentials = new JSONObject().put("key", key).put("secret", secret);
            Map<String, String> request = new UsosClient(credentials).begin(scopes);
            s.put("key", key).put("secret", secret).put("requestToken", request.get("oauth_token"))
                .put("requestSecret", request.get("oauth_token_secret")).put("scopes", scopes);
            vault.write(s);
        }
    }
    void finish(String pin) throws Exception {
        synchronized (LOCK) {
            JSONObject s = vault.read();
            if (s.optString("requestToken").isEmpty()) throw new IllegalStateException("Najpierw rozpocznij logowanie w przeglądarce.");
            Map<String, String> access = new UsosClient(s).finish(s, pin);
            s.put("token", access.get("oauth_token")).put("tokenSecret", access.get("oauth_token_secret"));
            JSONObject user = new JSONObject(new UsosClient(s).get("services/users/user", Map.of("fields", "id|first_name|last_name")));
            s.put("user", user.getString("first_name") + " " + user.getString("last_name"));
            s.remove("requestToken"); s.remove("requestSecret"); s.remove("snapshot"); s.remove("history");
            s.remove("retryAt"); s.remove("authorizationError");
            s.put("enabled", true).put("error", ""); vault.write(s);
        }
    }
    List<Change> sync(boolean manual) throws Exception {
        return sync(manual, s -> new UsosClient(s).snapshot());
    }
    List<Change> syncWithProgress(boolean manual, java.util.function.Consumer<String> progress) throws Exception {
        return sync(manual, s -> new UsosClient(s, progress).snapshot());
    }
    interface SnapshotSource { Snapshot fetch(JSONObject state) throws Exception; }
    List<Change> sync(boolean manual, SnapshotSource source) throws Exception {
        synchronized (LOCK) {
            JSONObject s = vault.read();
            if (s.optString("token").isEmpty() || (!manual && !s.optBoolean("enabled"))) return List.of();
            if (s.optLong("retryAt") > System.currentTimeMillis()) throw new IllegalStateException("USOS prosi o przerwę w żądaniach. Spróbuj po "
                + java.time.Instant.ofEpochMilli(s.getLong("retryAt")).atZone(UsosClient.ZONE).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")) + ".");
            s.put("attemptAt", System.currentTimeMillis()); vault.write(s);
            try {
                Snapshot previous = s.optJSONObject("snapshot") == null ? null : Snapshot.from(s.getJSONObject("snapshot"));
                Snapshot current = source.fetch(s);
                long now = System.currentTimeMillis();
                List<Change> changes = Diff.between(previous, current, now, LocalDate.now(UsosClient.ZONE).toString());
                JSONArray oldHistory = s.optJSONArray("history");
                JSONArray history = new JSONArray();
                for (Change c : changes) { if (history.length() < 200) history.put(c.json()); }
                if (oldHistory != null) for (int i = 0; i < oldHistory.length() && history.length() < 200; i++) history.put(oldHistory.get(i));
                s.put("history", history).put("snapshot", current.json()).put("successAt", now).put("error", "");
                s.remove("retryAt"); s.remove("authorizationError");
                vault.write(s); return changes;
            } catch (Exception e) {
                s.put("error", message(e));
                Exception cause = cause(e);
                if (cause instanceof UsosClient.ApiException && ((UsosClient.ApiException) cause).authorization) s.put("enabled", false);
                if (cause instanceof UsosClient.ApiException) {
                    UsosClient.ApiException failure = (UsosClient.ApiException) cause;
                    s.put("retryAt", failure.retryAt).put("authorizationError", failure.authorization);
                }
                vault.write(s); throw e;
            }
        }
    }
    void disconnect() { synchronized (LOCK) { vault.clear(); } }
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
