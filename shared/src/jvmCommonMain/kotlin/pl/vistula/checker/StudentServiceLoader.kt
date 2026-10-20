package pl.vistula.checker

import java.io.IOException
import java.net.SocketTimeoutException
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.CampusAffiliation

/** On-demand services keep their own encrypted cache separate from grade/plan monitoring. */
object StudentServiceLoader {
    @JvmStatic
    @JvmOverloads
    @Throws(Exception::class)
    fun refresh(repository: StateRepository, section: String,
                clients: SharedRepository.ClientFactory = SharedRepository.ClientFactory { UsosClient(it) },
                publicNews: () -> JSONObject = { UniversityNews.load() }) {
        val credentials = repository.read()
        val publicRequest = section == "news" && CampusAffiliation.canShowPublicNews(credentials)
        if (!publicRequest && (credentials.optString("token").isEmpty() || credentials.optBoolean("authorizationError"))) {
            throw IllegalStateException("Połącz konto USOS, aby pobrać dane.")
        }
        if (!publicRequest && credentials.optLong("retryAt") > System.currentTimeMillis()) {
            throw IllegalStateException("USOS prosi o przerwę. Spróbuj później.")
        }
        if (section !in setOf("cards", "documents", "payments", "news", "usosNews", "remittances", "affiliation", "teaching")) {
            throw IllegalArgumentException("Nieznana usługa.")
        }
        val scope = when (section) { "documents" -> "adm_documents"; "remittances" -> "payments"; else -> section }
        if (section !in setOf("news", "usosNews", "affiliation", "teaching") && !StudentServices.hasScope(credentials.optString("scopes"), scope)) {
            throw IllegalStateException(
                "Brak zakresu zgody „$scope”. Dodaj go w ustawieniach konta i połącz konto ponownie."
            )
        }
        val client = clients.create(credentials)
        val previous = credentials.optJSONObject("studentServices")
        val updates = JSONObject()
        if (section == "cards") {
            fetch(updates, previous, "mcard") {
                StudentServices.safeMobileCard(JSONObject(client.get("services/cards/mcard2", mapOf(
                    "fields" to "why_cannot_order|why_cannot_revoke|expected_expiration_date|latest_order[id|order_date|status|expiration_date]"
                ))))
            }
        } else if (section == "documents") {
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
        } else {
            val key = if (section == "usosNews") "news" else section
            fetch(updates, previous, key) {
                val campus = CampusAffiliation.selected(credentials)
                if (section == "news" && CampusAffiliation.canShowPublicNews(credentials)) publicNews().put("campus", campus)
                else if (section == "news" || section == "usosNews") {
                    check(campus.isNotEmpty()) { "Campus selection required" }
                    ToolServiceData.fetch(client, key, campus)
                } else ToolServiceData.fetch(client, key, CampusAffiliation.selected(credentials), credentials)
            }
        }
        repository.update { state -> applyUpdates(state, credentials, updates) }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun applyUpdates(state: JSONObject, credentials: JSONObject, updates: JSONObject) {
        val anonymousNews = credentials.optString("token").isEmpty() && state.optString("token").isEmpty() &&
            updates.length() == 1 && updates.has("news")
        if (!anonymousNews && !SharedRepository.sameAccount(credentials, state)) {
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
                .put("errorKind", "").put("httpStatus", 0)
                .put("retryAt", 0)
                .put("authorizationError", false)
        } catch (e: Exception) {
            entry.put("error", SharedRepository.message(e))
            entry.put("errorKind", when (e) {
                is UsosClient.ApiException -> "http"
                is SocketTimeoutException -> "timeout"
                is JSONException -> "format"
                is IOException -> "network"
                else -> "operation"
            }).put("httpStatus", if (e is UsosClient.ApiException) e.status else 0)
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

