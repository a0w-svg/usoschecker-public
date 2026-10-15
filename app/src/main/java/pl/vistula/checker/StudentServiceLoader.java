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
        if (credentials.optString("token").isEmpty()) throw new IllegalStateException("Połącz konto USOS, aby pobrać dane.");
        if (credentials.optLong("retryAt") > System.currentTimeMillis()) throw new IllegalStateException("USOS prosi o przerwę. Spróbuj później.");
        if (!section.equals("cards") && !section.equals("documents")) throw new IllegalArgumentException("Nieznana usługa.");
        String scope = section.equals("cards") ? "cards" : "adm_documents";
        if (!StudentServices.hasScope(credentials.optString("scopes"), scope)) throw new IllegalStateException(
            "Brak zakresu zgody „" + scope + "”. Dodaj go w ustawieniach konta i połącz konto ponownie.");
        UsosClient client = new UsosClient(credentials);
        JSONObject previous = credentials.optJSONObject("studentServices");
        JSONObject updates = previous == null ? new JSONObject() : new JSONObject(previous.toString());
        if (section.equals("cards")) {
            fetch(updates, "cards", () -> StudentServices.safeCards(new JSONArray(client.get("services/cards/user", Map.of()))));
            JSONObject cardAttempt = updates.getJSONObject("cards");
            if (cardAttempt.optLong("retryAt") <= System.currentTimeMillis() && !cardAttempt.optBoolean("authorizationError"))
                fetch(updates, "mcard", () -> StudentServices.safeMobileCard(new JSONObject(client.get("services/cards/mcard2", Map.of("fields",
                    "why_cannot_order|why_cannot_revoke|expected_expiration_date|latest_order[id|order_date|status|student_number|expiration_date]|orders[id|order_date|status|student_number|expiration_date]")))));
        } else fetch(updates, "documents", () -> StudentServices.safeDocuments(new JSONArray(client.get("services/adm_documents/user_documents",
            Map.of("fields", "id|published_date|delivered_date|id_blobbox|actions|name|status|type_name")))));
        repository.update(state -> {
            if (!credentials.optString("token").equals(state.optString("token"))) throw new IllegalStateException("Konto zmieniło się podczas pobierania.");
            state.put("studentServices", updates);
            for (String key : new String[]{"cards", "mcard", "documents"}) {
                JSONObject entry = updates.optJSONObject(key); if (entry == null) continue;
                if (entry.optLong("retryAt") > state.optLong("retryAt")) state.put("retryAt", entry.optLong("retryAt"));
                if (entry.optBoolean("authorizationError")) state.put("authorizationError", true).put("enabled", false);
            }
        });
    }
    interface Fetch { Object get() throws Exception; }
    static void fetch(JSONObject store, String key, Fetch fetch) throws Exception {
        JSONObject previous = store.optJSONObject(key);
        JSONObject entry = previous == null ? new JSONObject() : new JSONObject(previous.toString());
        entry.put("attemptAt", System.currentTimeMillis());
        try { entry.put("data", fetch.get()).put("fetchedAt", System.currentTimeMillis()).put("error", "").put("retryAt", 0).put("authorizationError", false); }
        catch (Exception e) {
            entry.put("error", Repository.message(e));
            if (e instanceof UsosClient.ApiException) {
                UsosClient.ApiException api = (UsosClient.ApiException) e;
                entry.put("retryAt", api.retryAt).put("authorizationError", api.authorization);
            }
        }
        store.put(key, entry);
    }
}
