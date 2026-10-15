package pl.vistula.checker;

import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import pl.vistula.checker.core.StudentServices;

/** On-demand services keep their own encrypted cache separate from grade/plan monitoring. */
final class StudentServiceLoader {
    private StudentServiceLoader() {}
    static void refresh(Repository repository, String section) throws Exception {
        JSONObject credentials = repository.read();
        if (credentials.optString("token").isEmpty() || credentials.optBoolean("authorizationError"))
            throw new IllegalStateException("Połącz konto USOS, aby pobrać dane.");
        if (credentials.optLong("retryAt") > System.currentTimeMillis()) throw new IllegalStateException("USOS prosi o przerwę. Spróbuj później.");
        if (!section.equals("cards") && !section.equals("documents")) throw new IllegalArgumentException("Nieznana usługa.");
        String scope = section.equals("cards") ? "cards" : "adm_documents";
        if (!StudentServices.hasScope(credentials.optString("scopes"), scope)) throw new IllegalStateException(
            "Brak zakresu zgody „" + scope + "”. Dodaj go w ustawieniach konta i połącz konto ponownie.");
        UsosClient client = new UsosClient(credentials);
        JSONObject previous = credentials.optJSONObject("studentServices");
        JSONObject updates = new JSONObject();
        if (section.equals("cards")) {
            boolean proceed = fetch(updates, previous, "cards", () -> StudentServices.safeCards(new JSONArray(client.get("services/cards/user", Map.of()))));
            if (proceed)
                fetch(updates, previous, "mcard", () -> StudentServices.safeMobileCard(new JSONObject(client.get("services/cards/mcard2", Map.of("fields",
                    "why_cannot_order|why_cannot_revoke|expected_expiration_date|latest_order[id|order_date|status|student_number|expiration_date]|orders[id|order_date|status|student_number|expiration_date]")))));
        } else fetch(updates, previous, "documents", () -> StudentServices.safeDocuments(new JSONArray(client.get("services/adm_documents/user_documents",
            Map.of("fields", "id|published_date|delivered_date|id_blobbox|actions|name|status|type_name")))));
        repository.update(state -> applyUpdates(state, credentials, updates));
    }
    static void applyUpdates(JSONObject state, JSONObject credentials, JSONObject updates) throws Exception {
        if (!Repository.sameAccount(credentials, state)) throw new IllegalStateException("Konto zmieniło się podczas pobierania.");
        JSONObject services = state.optJSONObject("studentServices");
        if (services == null) services = new JSONObject();
        java.util.Iterator<String> keys = updates.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject entry = updates.getJSONObject(key);
            JSONObject current = services.optJSONObject(key);
            if (current != null && current.optLong("attemptAt") > entry.optLong("attemptAt")) continue;
            services.put(key, new JSONObject(entry.toString()));
            if (entry.optLong("retryAt") > state.optLong("retryAt")) state.put("retryAt", entry.optLong("retryAt"));
            if (entry.optBoolean("authorizationError")) state.put("authorizationError", true).put("enabled", false);
        }
        state.put("studentServices", services);
    }
    interface Fetch { Object get() throws Exception; }
    private static boolean fetch(JSONObject store, JSONObject previous, String key, Fetch fetch) throws Exception {
        if (previous != null && previous.has(key)) store.put(key, new JSONObject(previous.getJSONObject(key).toString()));
        return fetch(store, key, fetch);
    }
    static boolean fetch(JSONObject store, String key, Fetch fetch) throws Exception {
        JSONObject previous = store.optJSONObject(key);
        JSONObject entry = previous == null ? new JSONObject() : new JSONObject(previous.toString());
        entry.put("attemptAt", System.currentTimeMillis()).put("retryAt", 0).put("authorizationError", false);
        boolean proceed = true;
        try { entry.put("data", fetch.get()).put("fetchedAt", System.currentTimeMillis()).put("error", "").put("retryAt", 0).put("authorizationError", false); }
        catch (Exception e) {
            entry.put("error", Repository.message(e));
            if (e instanceof UsosClient.ApiException) {
                UsosClient.ApiException api = (UsosClient.ApiException) e;
                entry.put("retryAt", api.retryAt).put("authorizationError", api.authorization);
                proceed = !api.retryable && !api.authorization && api.status != 401;
            } else if (e instanceof java.io.IOException) proceed = false;
        }
        store.put(key, entry);
        return proceed;
    }
}
