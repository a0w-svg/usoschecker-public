package pl.vistula.checker

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.TreeMap
import java.util.TreeSet
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import pl.vistula.checker.core.ApiFailure
import pl.vistula.checker.core.RequestBudget
import pl.vistula.checker.core.ApiLanguage
import pl.vistula.checker.core.GradeBrowser
import pl.vistula.checker.core.OAuth
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.StudentServices
import pl.vistula.checker.core.TimetableLoader
import pl.vistula.checker.core.UsosParser

class UsosClient @JvmOverloads constructor(
    state: JSONObject,
    private val progress: Consumer<String> = Consumer {},
    private val connections: ConnectionSource = ConnectionSource { url -> URL(url).openConnection() as HttpURLConnection }
) {
    private val key: String = state.optString("key")
    private val secret: String = state.optString("secret")
    private val token: String = state.optString("token")
    private val tokenSecret: String = state.optString("tokenSecret")
    private val scopes: String = StudentServices.normalizeScopes(state.optString("scopes"))
    private val planDays: Int = min(112, max(56, state.optInt("planDays", 56) / 7 * 7))

    fun interface ConnectionSource {
        @Throws(IOException::class)
        fun open(url: String): HttpURLConnection
    }

    class Response(
        @JvmField val bytes: ByteArray,
        mime: String?
    ) {
        @JvmField val mime: String =
            if (mime == null || mime.trim().isEmpty()) {
                "application/octet-stream"
            } else {
                mime.split(";", limit = 2)[0].trim().lowercase(Locale.ROOT)
            }

        fun binary(): Boolean =
            !(mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") || mime.contains("javascript"))

        fun text(): String = String(bytes, StandardCharsets.UTF_8)
    }

    class ApiException @JvmOverloads constructor(
        @JvmField val status: Int,
        failure: ApiFailure,
        @JvmField val endpoint: String = ""
    ) : IOException(
        when {
            failure.authorization -> "Sesja USOS wygasła. Połącz konto ponownie."
            status == 401 -> "USOS nie zaakceptował podpisu żądania. Sprawdź klucz aplikacji i zegar telefonu."
            status == 403 -> "Brak uprawnień do tej metody API. Sprawdź zakres zgody w USOS."
            status == 429 -> "USOS ograniczył liczbę żądań. Spróbuj później."
            status == 408 -> "USOS nie zakończył żądania na czas. Spróbuj ponownie."
            status >= 500 -> "Serwer USOS jest chwilowo niedostępny (HTTP $status)."
            else -> "USOS odrzucił żądanie (HTTP $status, ${failure.code}). Sprawdź parametry i uprawnienia."
        }
    ) {
        @JvmField val authorization: Boolean = failure.authorization
        @JvmField val retryable: Boolean = failure.retryable
        @JvmField val retryAt: Long = failure.retryAt

        constructor(status: Int, code: String) : this(
            status,
            ApiFailure(status, JSONObject(mapOf("code" to code)).toString(), null, System.currentTimeMillis())
        )

        override val message: String
            get() = (super.message ?: "") + (if (endpoint.isEmpty()) "" else "\nMetoda API: $endpoint")
    }

    class SyncException(
        @JvmField val stage: String,
        cause: Exception
    ) : Exception(cause)

    @Throws(Exception::class)
    fun request(
        method: String,
        endpoint: String,
        params: Map<String, String>,
        signed: Boolean,
        suppliedToken: String,
        suppliedSecret: String
    ): String = requestData(method, endpoint, params, signed, suppliedToken, suppliedSecret, null, null).text()

    @Throws(Exception::class)
    fun requestData(
        method: String,
        endpoint: String,
        params: Map<String, String>,
        signed: Boolean,
        suppliedToken: String,
        suppliedSecret: String,
        file: ByteArray?,
        filename: String?
    ): Response {
        if (method != "GET" && method != "POST") {
            throw IllegalArgumentException("Obsługiwane są żądania GET i POST.")
        }
        if (file != null && (method != "POST" || file.size > 8 * 1024 * 1024)) {
            throw IllegalArgumentException("Plik wymaga POST i nie może przekraczać 8 MB.")
        }
        val url = path(endpoint)
        val budget = RequestBudget()
        var values: MutableMap<String, String> = TreeMap(params)
        if (signed) {
            if (key.isEmpty() || secret.isEmpty()) {
                throw IllegalStateException("Ta metoda wymaga klucza aplikacji. Dodaj go w ustawieniach.")
            }
            values = TreeMap(OAuth.sign(method, url, values, key, secret, suppliedToken, suppliedSecret))
            if (file != null) {
                // blobbox/put explicitly requires OAuth PLAINTEXT over verified HTTPS.
                values["oauth_signature_method"] = "PLAINTEXT"
                values["oauth_signature"] = OAuth.encode(secret) + "&" + OAuth.encode(suppliedSecret)
            }
        }
        var authorization = ""
        if (file != null && signed) {
            val headers = ArrayList<String>()
            val parameters = values.entries.iterator()
            while (parameters.hasNext()) {
                val parameter = parameters.next()
                if (!parameter.key.startsWith("oauth_")) continue
                headers.add(OAuth.encode(parameter.key) + "=\"" + OAuth.encode(parameter.value) + "\"")
                parameters.remove()
            }
            authorization = "OAuth " + headers.joinToString(", ")
        }
        val encoded = OAuth.query(values)
        val post = method == "POST"
        val connection = connections.open(url + (if ((post && file == null) || encoded.isEmpty()) "" else "?$encoded"))
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = false
        // Authenticated responses must not enter the platform HTTP cache.
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-store")
        connection.requestMethod = method
        connection.setRequestProperty("User-Agent", "VistulaChecker/1.0 (Android)")
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        if (authorization.isNotEmpty()) connection.setRequestProperty("Authorization", authorization)
        try {
            connection.readTimeout = budget.readTimeout()
            if (post) {
                connection.doOutput = true
                val body: ByteArray
                if (file != null) {
                    val boundary = "VistulaChecker" + UUID.randomUUID().toString().replace("-", "")
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    val multipart = ByteArrayOutputStream()
                    val safeName = filename?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "upload.bin"
                    multipart.write(
                        (
                            "--$boundary\r\nContent-Disposition: form-data; name=\"data\"; filename=\"$safeName" +
                                "\"\r\nContent-Type: application/octet-stream\r\n\r\n"
                            ).toByteArray(StandardCharsets.UTF_8)
                    )
                    multipart.write(file)
                    multipart.write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
                    body = multipart.toByteArray()
                } else {
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    body = encoded.toByteArray(StandardCharsets.UTF_8)
                }
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { stream -> stream.write(body) }
            }
            connection.readTimeout = budget.readTimeout()
            val status = connection.responseCode
            if (status in 300..399) {
                throw IOException("API zwróciło przekierowanie. Otwórz tę metodę w przeglądarce przez dokumentację.")
            }
            val input = if (status >= 400) connection.errorStream else connection.inputStream
            var result = ""
            var bytes = ByteArray(0)
            if (input != null) {
                input.use { stream ->
                    ByteArrayOutputStream().use { buffer ->
                        val chunk = ByteArray(8192)
                        while (true) {
                            connection.readTimeout = budget.readTimeout()
                            val count = stream.read(chunk)
                            if (count == -1) break
                            if (buffer.size() + count > 8 * 1024 * 1024) {
                                throw IOException("Odpowiedź API przekracza limit 8 MB.")
                            }
                            buffer.write(chunk, 0, count)
                        }
                        bytes = buffer.toByteArray()
                        result = buffer.toString("UTF-8")
                    }
                }
            }
            if (status < 200 || status >= 300) {
                throw ApiException(
                    status,
                    ApiFailure(status, result, connection.getHeaderField("Retry-After"), System.currentTimeMillis()),
                    endpoint
                )
            }
            return Response(bytes, connection.contentType)
        } finally {
            connection.disconnect()
        }
    }

    @Throws(Exception::class)
    fun get(endpoint: String, params: Map<String, String>): String =
        request("GET", endpoint, params, true, token, tokenSecret)

    @Throws(Exception::class)
    fun begin(scopes: String?): Map<String, String> {
        val params = HashMap<String, String>()
        params["oauth_callback"] = "oob"
        val normalized = StudentServices.normalizeScopes(scopes)
        if (normalized.isNotEmpty()) params["scopes"] = normalized
        val result = OAuth.form(request("GET", "services/oauth/request_token", params, true, "", ""))
        if ("true" != result["oauth_callback_confirmed"]) {
            throw IOException("USOS nie potwierdził protokołu OAuth 1.0a.")
        }
        return result
    }

    @Throws(Exception::class)
    fun finish(state: JSONObject, pin: String?): Map<String, String> {
        val cleanPin = pin?.trim()?.replace(Regex("\\s+"), "") ?: ""
        return OAuth.form(
            request(
                "GET",
                "services/oauth/access_token",
                mapOf("oauth_verifier" to cleanPin),
                true,
                state.getString("requestToken"),
                state.getString("requestSecret")
            )
        )
    }

    @Throws(Exception::class)
    fun explore(
        metadata: JSONObject,
        params: Map<String, String>,
        method: String,
        file: ByteArray?,
        filename: String?
    ): Response {
        val auth = metadata.getJSONObject("auth_options")
        for (name in params.keys) {
            if (name.startsWith("oauth_")) {
                throw IllegalArgumentException("Parametry OAuth są zarządzane przez aplikację. Do logowania użyj ekranu Konto.")
            }
        }
        if (file != null && !metadata.getString("name").matches(Regex("services/blobbox/put(?:_unsigned)?"))) {
            throw IllegalArgumentException("Ta metoda nie obsługuje plików multipart.")
        }
        var sign = auth.optString("consumer") != "ignored" && key.isNotEmpty()
        if (auth.optString("token") == "required" && token.isEmpty()) {
            throw IllegalStateException("Ta metoda wymaga połączenia konta USOS.")
        }
        if (auth.optString("consumer") == "required") sign = true
        val useToken = auth.optString("token") != "ignored"
        return requestData(
            if (file == null) method else "POST",
            metadata.getString("name"),
            params,
            sign,
            if (useToken) token else "",
            if (useToken) tokenSecret else "",
            file,
            filename
        )
    }

    private var stage = "Rozpoczęcie synchronizacji"

    private fun reportProgress(message: String) {
        stage = message + (if (message.startsWith("Pobieranie planu")) " · tt/student" else "")
        progress.accept(message)
    }

    @Throws(Exception::class)
    fun snapshot(): Snapshot = try {
        fetchSnapshot()
    } catch (e: Exception) {
        throw SyncException(stage, e)
    }

    private fun interface SnapshotParser {
        @Throws(Exception::class)
        fun parse(snapshot: Snapshot)
    }

    @Throws(Exception::class)
    private fun fetchSnapshot(): Snapshot {
        for (required in listOf("grades", "studies")) {
            if (!StudentServices.hasScope(scopes, required)) {
                throw IllegalStateException(
                    "Brak zakresu zgody „$required”. Połącz konto ponownie z zakresami grades|studies|offline_access."
                )
            }
        }
        val start = LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val s = Snapshot(start.toString(), start.plusDays(planDays.toLong()).toString())
        for (language in arrayOf("en", "uk")) s.localized[language] = Snapshot(s.start, s.end)
        reportProgress("Pobieranie semestrów · courses/user")
        val editions = JSONObject(
            get(
                "services/courses/user",
                mapOf("active_terms_only" to "false", "fields" to "course_editions[course_id|course_name]")
            )
        ).getJSONObject("course_editions")
        val terms = UsosParser.sortedKeys(editions)

        reportProgress("Pobieranie punktów ECTS · courses/user_ects_points")
        var ectsPoints: Map<String, Double> = emptyMap()
        try {
            val ectsRaw = get("services/courses/user_ects_points", emptyMap())
            ectsPoints = UsosParser.ectsPoints(JSONObject(ectsRaw))
        } catch (e: Exception) {
            optionalFailure(e)
        }

        reportProgress("Pobieranie kalendarza semestrów · terms/terms")
        val points = ectsPoints
        parseLanguages(s) { variant -> variant.courses.addAll(UsosParser.courseEditions(editions, points)) }
        try {
            if (terms.isNotEmpty()) {
                val termsRaw = get(
                    "services/terms/terms",
                    mapOf("term_ids" to terms.joinToString("|"), "fields" to "id|name|start_date|end_date|finish_date")
                )
                parseLanguages(s) { variant -> variant.terms.putAll(UsosParser.academicTerms(JSONObject(termsRaw))) }
            }
        } catch (e: Exception) {
            optionalFailure(e)
        }

        var accountsRaw: Any? = null
        var paymentsScopeMissing = !StudentServices.hasScope(scopes, "payments")
        var paymentsNotice = if (paymentsScopeMissing) {
            "Brak zgody na płatności (zakres 'payments'). Dodaj zakres i połącz konto ponownie."
        } else {
            ""
        }
        if (!paymentsScopeMissing) {
            reportProgress("Pobieranie subkont studenta · payments/user_accounts")
            try {
                val accRaw = get("services/payments/user_accounts", emptyMap())
                accountsRaw = JSONTokener(accRaw).nextValue()
            } catch (e: ApiException) {
                if (e.status == 403 && !e.authorization) {
                    paymentsScopeMissing = true
                    paymentsNotice = "Brak uprawnień do modułu płatności (wymagany zakres 'payments')."
                } else {
                    throw e
                }
            }
        }
        var paymentsRaw: Any? = null
        if (!paymentsScopeMissing) {
            reportProgress("Pobieranie należności i rat · payments/user_payments")
            try {
                val payRaw = get(
                    "services/payments/user_payments",
                    mapOf(
                        "fields" to "id|description|type|state|account_number|payment_deadline|total_amount|saldo_amount|currency|chosen_installment_plan[id|currency|installments[amount|payment_date|description|number]]"
                    )
                )
                paymentsRaw = JSONTokener(payRaw).nextValue()
            } catch (e: ApiException) {
                if (e.status == 403 && !e.authorization) {
                    paymentsScopeMissing = true
                    paymentsNotice = "Brak uprawnień do modułu płatności (wymagany zakres 'payments')."
                } else {
                    throw e
                }
            }
        }
        val accountData = accountsRaw
        val paymentData = paymentsRaw
        val missingPayments = paymentsScopeMissing
        val paymentMessage = paymentsNotice
        parseLanguages(s) { variant ->
            variant.payments = UsosParser.payments(accountData, paymentData, missingPayments, paymentMessage)
        }

        reportProgress("Pobieranie prac dyplomowych · theses/user")
        try {
            val thesesRaw = get(
                "services/theses/user",
                mapOf("fields" to "id|title|status|supervisors[first_name|last_name]|reviewers[first_name|last_name]")
            )
            parseLanguages(s) { variant -> variant.thesis = UsosParser.thesis(JSONTokener(thesesRaw).nextValue()) }
        } catch (e: Exception) {
            optionalFailure(e)
        }

        if (StudentServices.hasScope(scopes, "crstests")) {
            reportProgress("Pobieranie sprawdzianów cząstkowych · crstests/participant2")
            try {
                val node = "id|name|type|visible_for_students|task_node_details[points_max|students_points[points|last_changed]]|grade_node_details[students_grade[grade_value[symbol]|last_changed]]"
                val tests = JSONArray(
                    get(
                        "services/crstests/participant2",
                        mapOf(
                            "active_terms_only" to "false",
                            "fields" to "course_edition[course[id|name]|term[id]]|root[$node|subnodes_deep[$node]]"
                        )
                    )
                )
                parseLanguages(s) { variant ->
                    variant.tests.addAll(UsosParser.courseTestTrees(tests, UsosParser.courseNames(editions)))
                }
            } catch (e: Exception) {
                optionalFailure(e)
                parseLanguages(s) { variant -> variant.serviceErrors["crstests"] = SharedRepository.message(e) }
            }
        } else {
            parseLanguages(s) { variant -> variant.serviceErrors["crstests"] = "crstests" }
        }

        reportProgress("Pobieranie komunikatów uczelni · news/search")
        try {
            val newsRaw = get(
                "services/news/search",
                mapOf("num" to "10", "fields" to "items[article[id|title|publication_date|author|headline_html|content_html]]")
            )
            parseLanguages(s) { variant -> variant.news.addAll(UsosParser.newsArticles(JSONTokener(newsRaw).nextValue())) }
        } catch (e: Exception) {
            optionalFailure(e)
            parseLanguages(s) { variant -> variant.serviceErrors["news"] = SharedRepository.message(e) }
        }

        // Batch term IDs to avoid URL limits; every batch must succeed before commit.
        var i = 0
        while (i < terms.size) {
            reportProgress("Pobieranie ocen · grades/terms2")
            val batch = terms.subList(i, min(terms.size, i + 8))
            val grades = JSONObject(
                get(
                    "services/grades/terms2",
                    mapOf(
                        "term_ids" to batch.joinToString("|"),
                        "fields" to "exam_id|exam_session_number|value_symbol|passes|value_description|counts_into_average|grade_type_id|date_modified|date_acquisition|modification_author|comment"
                    )
                )
            )
            for (term in batch) {
                if (!grades.has(term) || grades.isNull(term)) {
                    throw IOException("USOS zwrócił niepełny zestaw ocen.")
                }
            }
            parseLanguages(s) { variant -> UsosParser.grades(variant, grades, UsosParser.courseNames(editions), points) }
            i += 8
        }
        val weeks = TimetableLoader.load(
            start,
            planDays,
            { pageStart, days ->
                JSONArray(
                    get(
                        "services/tt/student",
                        mapOf(
                            "start" to pageStart.toString(),
                            "days" to days.toString(),
                            "fields" to "type|start_time|end_time|name|course_id|unit_id|group_number|cgwm_id|sm_id|room_number|room_id|building_name|building_id|classtype_name|classtype_id|lecturer_ids|frequency|url|classgroup_profile_url"
                        )
                    )
                )
            },
            Consumer { reportProgress(it) }
        )
        reportProgress("Pobieranie nazwisk prowadzących · users/users")
        val lecturerNames = resolveLecturers(weeks) { ids ->
            JSONObject(
                get(
                    "services/users/users",
                    mapOf("user_ids" to ids.joinToString("|"), "fields" to "first_name|last_name")
                )
            )
        }
        reportProgress("Odczyt planu zajęć · tt/student")
        for (rows in weeks) {
            parseLanguages(s) { variant -> UsosParser.timetable(variant, rows, lecturerNames) }
        }
        enrichGradeUnits(s)
        for (translation in s.localized.values) {
            for (record in translation.records.values) {
                val canonical = s.records[record.id]
                if (canonical != null) {
                    record.metadata.put(
                        "classTypeCanonical",
                        if (canonical.kind == "ocena") GradeBrowser.classType(canonical, s) else canonical.optMeta("classType")
                    )
                }
            }
        }
        return s
    }

    @Throws(Exception::class)
    private fun enrichGradeUnits(snapshot: Snapshot) {
        val unitIds = ArrayList(
            snapshot.records.values
                .filter { it.kind == "ocena" }
                .map { it.optMeta("unitId") }
                .filter { it.isNotEmpty() }
                .toCollection(TreeSet())
        )
        var types = JSONObject()
        if (unitIds.isNotEmpty()) {
            reportProgress("Rozpoznawanie typów zajęć · courses/units")
            try {
                types = JSONObject(request("GET", "services/courses/classtypes_index", emptyMap(), false, "", ""))
            } catch (e: Exception) {
                optionalFailure(e)
            }
        }
        var i = 0
        while (i < unitIds.size) {
            val batch = unitIds.subList(i, min(i + 50, unitIds.size))
            try {
                val units = JSONObject(
                    get(
                        "services/courses/units",
                        mapOf(
                            "unit_ids" to batch.joinToString("|"),
                            "fields" to "id|course_name|course_id|term_id|classtype_id|homepage_url|profile_url"
                        )
                    )
                )
                val definitions = types
                parseLanguages(snapshot) { variant ->
                    for (grade in variant.records.values) {
                        val unit = units.optJSONObject(grade.optMeta("unitId"))
                        if (grade.kind != "ocena" || unit == null) continue
                        grade.metadata.put("unitData", JSONObject(unit.toString()))
                        val typeId = StudentServices.text(unit, "classtype_id")
                        if (typeId.isNotEmpty()) grade.metadata.put("classTypeId", typeId)
                        val definition = definitions.optJSONObject(typeId)
                        val name = if (definition == null) "" else StudentServices.localized(definition.opt("name"))
                        if (name.isNotEmpty()) {
                            grade.metadata.put("classType", name).put("classTypeSource", "unit_api")
                        }
                    }
                }
            } catch (e: Exception) {
                optionalFailure(e)
            }
            i += 50
        }
        // Resolve editor IDs independently; unavailable names never prevent a grade snapshot.
        val authors = ArrayList(
            snapshot.records.values
                .filter { it.kind == "ocena" }
                .map { it.optMeta("modificationAuthor") }
                .filter { it.isNotEmpty() }
                .toCollection(TreeSet())
        )
        var j = 0
        while (j < authors.size) {
            try {
                val names = UsosParser.lecturerNames(
                    JSONObject(
                        get(
                            "services/users/users",
                            mapOf(
                                "user_ids" to authors.subList(j, min(j + 50, authors.size)).joinToString("|"),
                                "fields" to "first_name|last_name"
                            )
                        )
                    )
                )
                parseLanguages(snapshot) { variant ->
                    for (grade in variant.records.values) {
                        val name = names[grade.optMeta("modificationAuthor")]
                        if (!name.isNullOrEmpty()) grade.metadata.put("modificationAuthorName", name)
                    }
                }
            } catch (e: Exception) {
                optionalFailure(e)
            }
            j += 50
        }
    }

    fun interface LecturerSource {
        @Throws(Exception::class)
        fun fetch(ids: List<String>): JSONObject
    }

    companion object {
        const val BASE: String = "https://usosapps.vistula.edu.pl/"
        @JvmField val ZONE: ZoneId = ZoneId.of("Europe/Warsaw")

        @JvmStatic
        fun path(path: String): String {
            if (!path.matches(Regex("services/(?:[a-z0-9_]+/)+[a-z0-9_]+"))) {
                throw IllegalArgumentException("Nieprawidłowa nazwa metody USOS API.")
            }
            return BASE + path
        }

        @JvmStatic
        fun profileFields(state: JSONObject): String {
            val scopes = state.optString("scopes")
            var fields = "id|first_name|last_name|student_status"
            if (StudentServices.hasScope(scopes, "studies")) {
                fields += "|student_number|student_programmes[programme[name]|admission_date|stages|status]"
            }
            if (StudentServices.hasScope(scopes, "email")) fields += "|email"
            if (StudentServices.hasScope(scopes, "photo")) fields += "|photo_urls[200x200]"
            return fields
        }

        @Throws(Exception::class)
        private fun parseLanguages(snapshot: Snapshot, parser: SnapshotParser) {
            for (language in ApiLanguage.SUPPORTED) {
                val target = snapshot.forLanguage(language)
                ApiLanguage.`in`(language) {
                    parser.parse(target)
                    null
                }
            }
        }

        @Throws(Exception::class)
        private fun optionalFailure(failure: Exception) {
            // Optional modules may be unavailable, but session expiry and server cooldowns apply to the whole sync.
            if (failure is ApiException) {
                if (failure.authorization || failure.retryable || failure.status == 401) throw failure
            } else if (failure is IOException) {
                throw failure
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun resolveLecturers(weeks: List<JSONArray>, source: LecturerSource): Map<String, String> {
            val unique = TreeSet<String>()
            for (week in weeks) unique.addAll(UsosParser.lecturerIds(week))
            val ids = ArrayList(unique)
            val names = TreeMap<String, String>()
            var i = 0
            while (i < ids.size) {
                val batch = ids.subList(i, min(i + 50, ids.size))
                val users = source.fetch(batch)
                for (id in batch) {
                    if (!users.has(id)) throw IOException("USOS zwrócił niepełne dane prowadzących.")
                }
                names.putAll(UsosParser.lecturerNames(users))
                i += 50
            }
            return names
        }
    }
}

