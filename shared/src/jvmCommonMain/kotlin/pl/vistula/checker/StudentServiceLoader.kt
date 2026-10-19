package pl.vistula.checker

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import pl.vistula.checker.core.StudentServices

/** On-demand services keep their own encrypted cache separate from grade/plan monitoring. */
object StudentServiceLoader {
    @JvmStatic
    @Throws(Exception::class)
    fun refresh(repository: StateRepository, section: String) {
        val credentials = repository.read()
        if (credentials.optString("token").isEmpty() || credentials.optBoolean("authorizationError")) {
            throw IllegalStateException("Połącz konto USOS, aby pobrać dane.")
        }
        if (credentials.optLong("retryAt") > System.currentTimeMillis()) {
            throw IllegalStateException("USOS prosi o przerwę. Spróbuj później.")
        }
        if (section != "cards" && section != "documents") {
            throw IllegalArgumentException("Nieznana usługa.")
        }
        val scope = if (section == "cards") "cards" else "adm_documents"
        if (!StudentServices.hasScope(credentials.optString("scopes"), scope)) {
            throw IllegalStateException(
                "Brak zakresu zgody „$scope”. Dodaj go w ustawieniach konta i połącz konto ponownie."
            )
        }
        val client = UsosClient(credentials)
        val previous = credentials.optJSONObject("studentServices")
        val updates = JSONObject()
        if (section == "cards") {
            val proceed = fetch(updates, previous, "cards") {
                StudentServices.safeCards(JSONArray(client.get("services/cards/user", emptyMap())))
            }
            if (proceed) {
                fetch(updates, previous, "mcard") {
                    StudentServices.safeMobileCard(
                        JSONObject(
                            client.get(
                                "services/cards/mcard2",
                                mapOf(
                                    "fields" to "why_cannot_order|why_cannot_revoke|expected_expiration_date|latest_order[id|order_date|status|student_number|expiration_date]|orders[id|order_date|status|student_number|expiration_date]"
                                )
                            )
                        )
                    )
                }
            }
        } else {
            fetch(updates, previous, "documents") {
                StudentServices.safeDocuments(
                    JSONArray(
                        client.get(
                            "services/adm_documents/user_documents",
                            mapOf("fields" to "id|published_date|delivered_date|id_blobbox|actions|name|status|type_name")
                        )
                    )
                )
            }
        }
        repository.update { state -> applyUpdates(state, credentials, updates) }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun applyUpdates(state: JSONObject, credentials: JSONObject, updates: JSONObject) {
        if (!SharedRepository.sameAccount(credentials, state)) {
            throw IllegalStateException("Konto zmieniło się podczas pobierania.")
        }
        var services = state.optJSONObject("studentServices")
        if (services == null) services = JSONObject()
        val keys = updates.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = updates.getJSONObject(key)
            val current = services.optJSONObject(key)
            if (current != null && current.optLong("attemptAt") > entry.optLong("attemptAt")) continue
            services.put(key, JSONObject(entry.toString()))
            if (entry.optLong("retryAt") > state.optLong("retryAt")) {
                state.put("retryAt", entry.optLong("retryAt"))
            }
            if (entry.optBoolean("authorizationError")) {
                state.put("authorizationError", true).put("enabled", false)
            }
        }
        state.put("studentServices", services)
    }

    fun interface Fetch {
        @Throws(Exception::class)
        fun get(): Any
    }

    @Throws(Exception::class)
    private fun fetch(store: JSONObject, previous: JSONObject?, key: String, fetch: Fetch): Boolean {
        if (previous != null && previous.has(key)) {
            store.put(key, JSONObject(previous.getJSONObject(key).toString()))
        }
        return fetch(store, key, fetch)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun fetch(store: JSONObject, key: String, fetch: Fetch): Boolean {
        val previous = store.optJSONObject(key)
        val entry = if (previous == null) JSONObject() else JSONObject(previous.toString())
        entry.put("attemptAt", System.currentTimeMillis()).put("retryAt", 0).put("authorizationError", false)
        var proceed = true
        try {
            entry.put("data", fetch.get())
                .put("fetchedAt", System.currentTimeMillis())
                .put("error", "")
                .put("retryAt", 0)
                .put("authorizationError", false)
        } catch (e: Exception) {
            entry.put("error", SharedRepository.message(e))
            if (e is UsosClient.ApiException) {
                entry.put("retryAt", e.retryAt).put("authorizationError", e.authorization)
                proceed = !e.retryable && !e.authorization && e.status != 401
            } else if (e is IOException) {
                proceed = false
            }
        }
        store.put(key, entry)
        return proceed
    }
}

