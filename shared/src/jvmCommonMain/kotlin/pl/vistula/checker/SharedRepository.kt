package pl.vistula.checker

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.core.ApiLanguage
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.Diff
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.UsosParser

/**
 * Multiplatform repository coordinating encrypted state storage, OAuth login sessions,
 * localized snapshot views, and concurrent sync operations across Android and Desktop.
 */
class SharedRepository @JvmOverloads constructor(
    private val vault: VaultStore,
    private val languageProvider: Supplier<String> = Supplier { "pl" },
    private val onClearAttendance: Runnable = Runnable {},
    private val changeNotifier: ChangeNotifier = ChangeNotifier {},
    private val clients: ClientFactory = ClientFactory { UsosClient(it) }
) : StateRepository {
    private val storePath: String = vault.storePath

    fun interface ClientFactory {
        fun create(state: JSONObject): UsosClient
    }

    fun interface SnapshotSource {
        @Throws(Exception::class)
        fun fetch(state: JSONObject): Snapshot
    }

    class EditSession internal constructor(
        internal val epoch: Any,
        state: JSONObject
    ) {
        internal val identity: Array<String> = IDENTITY_FIELDS.map { state.optString(it) }.toTypedArray()
    }

    fun editSession(displayedState: JSONObject): EditSession = synchronized(LOCK) {
        EditSession(editEpochs.computeIfAbsent(storePath) { Any() }, displayedState)
    }

    @Throws(Exception::class)
    fun update(session: EditSession, operation: StateRepository.Update) {
        synchronized(LOCK) {
            val current = vault.read()
            if (session.epoch !== editEpochs[storePath]) throw loginChanged()
            for (i in IDENTITY_FIELDS.indices) {
                if (session.identity[i] != current.optString(IDENTITY_FIELDS[i])) throw loginChanged()
            }
            operation.apply(current)
            vault.write(current)
        }
    }

    @Throws(Exception::class)
    override fun read(): JSONObject = synchronized(LOCK) {
        localizedView(vault.read(), languageProvider.get())
    }

    @Throws(Exception::class)
    override fun update(operation: StateRepository.Update) {
        synchronized(LOCK) {
            val state = vault.read()
            operation.apply(state)
            vault.write(state)
        }
    }

    @Throws(Exception::class)
    fun begin(key: String, secret: String, scopes: String?) {
        val normalizedScopes = StudentServices.normalizeScopes(scopes)
        synchronized(AUTH_LOCK) {
            val attempt = UUID.randomUUID().toString()
            synchronized(LOCK) {
                val s = vault.read()
                if (s.optString("token").isNotEmpty()) {
                    throw IllegalStateException("Najpierw odłącz obecne konto.")
                }
                s.put("authAttempt", attempt)
                vault.write(s)
            }
            val credentials = JSONObject().put("key", key).put("secret", secret)
            val request = clients.create(credentials).begin(normalizedScopes)
            synchronized(LOCK) {
                val s = vault.read()
                if (attempt != s.optString("authAttempt") || s.optString("token").isNotEmpty()) {
                    throw loginChanged()
                }
                s.put("key", key)
                    .put("secret", secret)
                    .put("requestToken", request["oauth_token"])
                    .put("requestSecret", request["oauth_token_secret"])
                    .put("scopes", normalizedScopes)
                vault.write(s)
            }
        }
    }

    @Throws(Exception::class)
    fun finish(pin: String?) {
        val connected: JSONObject
        synchronized(AUTH_LOCK) {
            val pending = read()
            if (pending.optString("requestToken").isEmpty()) {
                throw IllegalStateException("Najpierw rozpocznij logowanie w przeglądarce.")
            }
            if (pending.optString("token").isNotEmpty()) throw loginChanged()
            val access = clients.create(pending).finish(pending, pin)
            synchronized(LOCK) {
                val s = vault.read()
                for (field in arrayOf("authAttempt", "key", "secret", "requestToken", "requestSecret", "token")) {
                    if (pending.optString(field) != s.optString(field)) throw loginChanged()
                }
                s.put("token", access["oauth_token"])
                    .put("tokenSecret", access["oauth_token_secret"])
                    .put("accountSession", UUID.randomUUID().toString())
                for (field in arrayOf(
                    "requestToken", "requestSecret", "authAttempt", "snapshot", "history", "studentServices", "campusOverride",
                    "retryAt", "authorizationError", "studentNumber", "email", "programme", "studentStatus",
                    "stage", "admissionDate", "photoUrl", "profileTranslations", "reminderDeliveries", "staffStatus", "studentStatusCode"
                )) {
                    s.remove(field)
                }
                s.put("enabled", true).put("error", "").put("user", "Student USOS")
                vault.write(s)
                connected = JSONObject(s.toString())
            }
        }
        // Access credentials must survive a slow, unavailable, or interrupted profile lookup.
        var profile = JSONObject()
        var profileFailure: Exception? = null
        try {
            val user = JSONObject(
                clients.create(connected).get(
                    "services/users/user",
                    mapOf("fields" to UsosClient.profileFields(connected))
                )
            )
            profile = profileData(user)
        } catch (e: Exception) {
            profileFailure = e
            val fallback = e is JSONException || (
                e is UsosClient.ApiException && !e.authorization && (e.status == 400 || e.status == 403)
                )
            if (fallback) {
                try {
                    val user = JSONObject(
                        clients.create(connected).get(
                            "services/users/user",
                            mapOf("fields" to "id|first_name|last_name")
                        )
                    )
                    profile.put("user", (user.optString("first_name") + " " + user.optString("last_name")).trim())
                    profileFailure = null
                } catch (basicFailure: Exception) {
                    profileFailure = basicFailure
                }
            }
        }
        synchronized(LOCK) {
            val latest = vault.read()
            if (!sameAccount(connected, latest)) throw loginChanged()
            val fields = profile.keys()
            while (fields.hasNext()) {
                val field = fields.next()
                latest.put(field, profile.get(field))
            }
            val failure = profileFailure
            if (failure is UsosClient.ApiException) {
                latest.put("error", message(failure))
                latest.put("retryAt", max(latest.optLong("retryAt"), failure.retryAt))
                if (failure.authorization) latest.put("authorizationError", true).put("enabled", false)
            }
            vault.write(latest)
        }
    }

    @Throws(Exception::class)
    fun sync(manual: Boolean): List<Change> =
        sync(manual, { s -> UsosClient(s).snapshot() }, { }, true)

    @Throws(Exception::class)
    fun syncWithProgress(manual: Boolean, progress: Consumer<String>): List<Change> =
        sync(manual, { s -> UsosClient(s, progress).snapshot() }, { }, true)

    @Throws(Exception::class)
    fun syncAndNotify(manual: Boolean, progress: Consumer<String>): List<Change> =
        sync(manual, { s -> UsosClient(s, progress).snapshot() }, { changes -> changeNotifier.notifyChanges(changes) }, true)

    @JvmOverloads
    @Throws(Exception::class)
    fun sync(
        manual: Boolean,
        source: SnapshotSource,
        publish: Consumer<List<Change>> = Consumer {},
        refreshProfile: Boolean = false
    ): List<Change> {
        synchronized(SYNC_LOCK) {
            val s: JSONObject
            synchronized(LOCK) {
                s = vault.read()
                if (s.optString("token").isEmpty() || s.optString("token") == "demo" || (!manual && !s.optBoolean("enabled"))) return emptyList()
                if (s.optLong("retryAt") > System.currentTimeMillis()) {
                    throw IllegalStateException(
                        "USOS prosi o przerwę w żądaniach. Spróbuj po " +
                            Instant.ofEpochMilli(s.getLong("retryAt"))
                                .atZone(UsosClient.ZONE)
                                .format(DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")) + "."
                    )
                }
                s.put("attemptAt", System.currentTimeMillis())
                vault.write(s)
            }
            try {
                if (refreshProfile || (s.optString("studentNumber").isEmpty() && !s.has("staffStatus"))) {
                    try {
                        val user = JSONObject(
                            clients.create(s).get(
                                "services/users/user",
                                mapOf("fields" to UsosClient.profileFields(s))
                            )
                        )
                        val fields = profileData(user)
                        for (field in UsosParser.sortedKeys(fields)) s.put(field, fields.get(field))
                    } catch (ignored: Exception) {}
                }
                val current = source.fetch(s)
                synchronized(LOCK) {
                    val latest = vault.read()
                    if (!sameAccount(s, latest) || (!manual && !latest.optBoolean("enabled"))) return emptyList()
                    val previous = if (latest.optJSONObject("snapshot") == null) {
                        null
                    } else {
                        Snapshot.from(latest.getJSONObject("snapshot"))
                    }
                    val now = System.currentTimeMillis()
                    val today = LocalDate.now(UsosClient.ZONE).toString()
                    var changes = Diff.between(previous, current, now, today)
                    val oldHistory = latest.optJSONArray("history")
                    val history = JSONArray()
                    val translations = LinkedHashMap<String, List<Change>>()
                    for (language in arrayOf("en", "uk")) {
                        translations[language] = Diff.between(previous, current, now, today, language)
                    }
                    var i = 0
                    while (i < changes.size && history.length() < 200) {
                        val entry = changes[i].json()
                        val localized = JSONObject()
                        for ((language, list) in translations) {
                            localized.put(language, list[i].json())
                        }
                        history.put(entry.put("localized", localized))
                        i++
                    }
                    changes = Diff.between(previous, current, now, today, languageProvider.get())
                    if (oldHistory != null) {
                        var j = 0
                        while (j < oldHistory.length() && history.length() < 200) {
                            history.put(oldHistory.get(j))
                            j++
                        }
                    }
                    val snapshot = current.json()
                    for (field in arrayOf(
                        "user", "studentNumber", "email", "programme", "studentStatus",
                        "stage", "admissionDate", "photoUrl", "profileTranslations", "staffStatus", "studentStatusCode"
                    )) {
                        if (s.has(field)) latest.put(field, s.get(field))
                    }
                    latest.put("history", history).put("snapshot", snapshot).put("successAt", now).put("error", "")
                        .put("errorKind", "").put("httpStatus", 0)
                    // Do not erase a cooldown or authorization failure reported by another request during this sync.
                    if (latest.optLong("retryAt") == s.optLong("retryAt")) latest.remove("retryAt")
                    if (latest.optBoolean("authorizationError") == s.optBoolean("authorizationError")) {
                        latest.remove("authorizationError")
                    }
                    vault.write(latest)
                    // Publish under the account lock so logout can clear every notification after it completes.
                    try {
                        publish.accept(changes)
                    } catch (ignored: RuntimeException) {
                        /* A delivery failure must not invalidate a committed snapshot. */
                    }
                    return changes
                }
            } catch (e: Exception) {
                synchronized(LOCK) {
                    val latest = vault.read()
                    if (sameAccount(s, latest)) {
                        latest.put("error", message(e))
                        val cause = cause(e)
                        latest.put("errorKind", pl.vistula.checker.core.SyncProgress.errorKind(cause))
                            .put("httpStatus", if (cause is UsosClient.ApiException) cause.status else 0)
                        if (cause is UsosClient.ApiException && cause.authorization) {
                            latest.put("enabled", false)
                        }
                        if (cause is UsosClient.ApiException) {
                            latest.put("retryAt", max(latest.optLong("retryAt"), cause.retryAt))
                                .put("authorizationError", latest.optBoolean("authorizationError") || cause.authorization)
                        }
                        vault.write(latest)
                    }
                }
                throw e
            }
        }
    }

    /** Older desktop releases persisted demo in the personal vault. Never clear a real account during recovery. */
    fun discardLegacyDemo(): Boolean = synchronized(LOCK) {
        if (vault.read().optString("token") != "demo") false
        else { disconnect(); true }
    }

    fun disconnect() {
        synchronized(LOCK) {
            vault.clear()
            onClearAttendance.run()
            editEpochs[storePath] = Any()
        }
    }

    companion object {
        @JvmField val LOCK: Any = Any()
        private val SYNC_LOCK: Any = Any()
        private val AUTH_LOCK: Any = Any()
        private val editEpochs: MutableMap<String, Any> = HashMap()
        private val IDENTITY_FIELDS = arrayOf("token", "tokenSecret", "key", "secret", "accountSession")

        private fun loginChanged(): IllegalStateException =
            IllegalStateException("Logowanie zostało anulowane lub konto się zmieniło.")

        @JvmStatic
        @Throws(Exception::class)
        fun localizedView(source: JSONObject, language: String?): JSONObject {
            val view = JSONObject(source.toString())
            val rawSnapshot = view.optJSONObject("snapshot")
            if (rawSnapshot != null) {
                view.put("snapshot", Snapshot.from(rawSnapshot).forLanguage(language).json())
            }
            val history = view.optJSONArray("history")
            if (history != null) {
                for (i in 0 until history.length()) {
                    val entry = history.optJSONObject(i) ?: continue
                    val translations = entry.optJSONObject("localized")
                    val translated = translations?.optJSONObject(ApiLanguage.normalize(language))
                    if (translated != null) history.put(i, translated)
                }
            }
            val profiles = view.optJSONObject("profileTranslations")
            val profile = profiles?.optJSONObject(ApiLanguage.normalize(language))
            if (profile != null) {
                for (key in UsosParser.sortedKeys(profile)) view.put(key, profile.get(key))
            }
            return view
        }

        @Throws(Exception::class)
        private fun profileData(user: JSONObject): JSONObject {
            val fields = JSONObject()
            fields.put("staffStatus", user.optInt("staff_status", -1))
            fields.put("studentStatusCode", user.optInt("student_status", -1))
            val translations = JSONObject()
            for (language in ApiLanguage.SUPPORTED) {
                val profile = ApiLanguage.`in`(language) { UsosParser.userProfile(user) }
                val translated = JSONObject()
                    .put("user", profile.name)
                    .put("studentNumber", profile.studentNumber)
                    .put("email", profile.email)
                    .put("programme", profile.programme)
                    .put("studentStatus", profile.studentStatus)
                    .put("stage", profile.stage)
                    .put("admissionDate", profile.admissionDate)
                    .put("photoUrl", profile.photoUrl)
                translations.put(language, translated)
                if (language == "pl") {
                    for (key in UsosParser.sortedKeys(translated)) fields.put(key, translated.get(key))
                }
            }
            return fields.put("profileTranslations", translations)
        }

        @JvmStatic
        fun sameAccount(expected: JSONObject, current: JSONObject): Boolean {
            for (field in IDENTITY_FIELDS) {
                if (expected.optString(field) != current.optString(field)) return false
            }
            return current.optString("token").isNotEmpty()
        }

        @JvmStatic
        fun cause(e: Exception): Exception =
            if (e is UsosClient.SyncException && e.cause is Exception) e.cause as Exception else e

        @JvmStatic
        fun message(e: Exception): String {
            if (e is UsosClient.SyncException) {
                return "Etap: " + e.stage + "\n" + message(cause(e))
            }
            if (e is UnknownHostException) return "Brak połączenia z USOS. Sprawdź dostęp do internetu."
            if (e is SocketTimeoutException) return "USOS nie odpowiedział na czas. Spróbuj ponownie."
            if (e is JSONException || e is DateTimeParseException) {
                return "USOS zwrócił niepełne lub nieznane dane. Poprzedni zapis został zachowany."
            }
            if (e is UsosClient.ApiException || e is IllegalStateException || e is IllegalArgumentException) {
                return e.message ?: "Nie udało się wykonać operacji."
            }
            if (e is IOException) return "Nie udało się pobrać lub zapisać danych. Poprzedni zapis został zachowany."
            return "Nie udało się odczytać bezpiecznego magazynu danych. Spróbuj ponownie lub odłącz konto."
        }
    }
}

