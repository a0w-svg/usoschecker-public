package pl.vistula.checker;

import pl.vistula.checker.core.*;
import pl.vistula.checker.core.Record;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.json.*;

public final class UsosClient {
    public static final String BASE = "https://usosapps.vistula.edu.pl/";
    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private final String key, secret, token, tokenSecret;
    private final String scopes;
    private final int planDays;
    private final java.util.function.Consumer<String> progress;
    interface ConnectionSource { HttpURLConnection open(String url) throws IOException; }
    private final ConnectionSource connections;
    public static final class Response {
        public final byte[] bytes;
        public final String mime;
        public Response(byte[] bytes, String mime) { this.bytes = bytes; this.mime = mime == null || mime.trim().isEmpty() ? "application/octet-stream" : mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT); }
        public boolean binary() { return !(mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") || mime.contains("javascript")); }
        public String text() { return new String(bytes, StandardCharsets.UTF_8); }
    }
    UsosClient(JSONObject state) { this(state, message -> {}); }
    UsosClient(JSONObject state, java.util.function.Consumer<String> progress) {
        this(state, progress, url -> (HttpURLConnection) new URL(url).openConnection());
    }
    UsosClient(JSONObject state, java.util.function.Consumer<String> progress, ConnectionSource connections) {
        this.connections = connections;
        this.progress = progress; this.planDays = Math.min(112, Math.max(56, state.optInt("planDays", 56) / 7 * 7));
        key = state.optString("key"); secret = state.optString("secret");
        token = state.optString("token"); tokenSecret = state.optString("tokenSecret");
        scopes = StudentServices.normalizeScopes(state.optString("scopes"));
    }
    static final class ApiException extends IOException {
        final int status;
        final boolean authorization;
        final boolean retryable;
        final long retryAt;
        final String endpoint;
        ApiException(int status, String code) {
            this(status, new ApiFailure(status, new JSONObject(Map.of("code", code)).toString(), null, System.currentTimeMillis()));
        }
        ApiException(int status, ApiFailure failure) {
            this(status, failure, "");
        }
        ApiException(int status, ApiFailure failure, String endpoint) {
            super(failure.authorization ? "Sesja USOS wygasła. Połącz konto ponownie."
                : status == 401 ? "USOS nie zaakceptował podpisu żądania. Sprawdź klucz aplikacji i zegar telefonu."
                : status == 403 ? "Brak uprawnień do tej metody API. Sprawdź zakres zgody w USOS."
                : status == 429 ? "USOS ograniczył liczbę żądań. Spróbuj później."
                : status == 408 ? "USOS nie zakończył żądania na czas. Spróbuj ponownie."
                : status >= 500 ? "Serwer USOS jest chwilowo niedostępny (HTTP " + status + ")."
                : "USOS odrzucił żądanie (HTTP " + status + ", " + failure.code + "). Sprawdź parametry i uprawnienia.");
            this.status = status;
            authorization = failure.authorization;
            retryable = failure.retryable;
            retryAt = failure.retryAt;
            this.endpoint = endpoint;
        }
        @Override public String getMessage() { return super.getMessage() + (endpoint.isEmpty() ? "" : "\nMetoda API: " + endpoint); }
    }
    static String path(String path) {
        if (!path.matches("services/(?:[a-z0-9_]+/)+[a-z0-9_]+"))
            throw new IllegalArgumentException("Nieprawidłowa nazwa metody USOS API.");
        return BASE + path;
    }
    String request(String method, String endpoint, Map<String, String> params, boolean signed,
                   String suppliedToken, String suppliedSecret) throws Exception {
        return requestData(method, endpoint, params, signed, suppliedToken, suppliedSecret, null, null).text();
    }
    Response requestData(String method, String endpoint, Map<String, String> params, boolean signed,
                         String suppliedToken, String suppliedSecret, byte[] file, String filename) throws Exception {
        if (!method.equals("GET") && !method.equals("POST")) throw new IllegalArgumentException("Obsługiwane są żądania GET i POST.");
        if (file != null && (!method.equals("POST") || file.length > 8 * 1024 * 1024)) throw new IllegalArgumentException("Plik wymaga POST i nie może przekraczać 8 MB.");
        String url = path(endpoint);
        Map<String, String> values = new TreeMap<>(params);
        if (signed) {
            if (key.isEmpty() || secret.isEmpty()) throw new IllegalStateException("Ta metoda wymaga klucza aplikacji. Dodaj go w ustawieniach.");
            values = OAuth.sign(method, url, values, key, secret, suppliedToken, suppliedSecret);
            if (file != null) {
                // blobbox/put explicitly requires OAuth PLAINTEXT over verified HTTPS.
                values.put("oauth_signature_method", "PLAINTEXT");
                values.put("oauth_signature", OAuth.encode(secret) + "&" + OAuth.encode(suppliedSecret));
            }
        }
        String authorization = "";
        if (file != null && signed) {
            List<String> headers = new ArrayList<>();
            Iterator<Map.Entry<String, String>> parameters = values.entrySet().iterator();
            while (parameters.hasNext()) {
                Map.Entry<String, String> parameter = parameters.next();
                if (!parameter.getKey().startsWith("oauth_")) continue;
                headers.add(OAuth.encode(parameter.getKey()) + "=\"" + OAuth.encode(parameter.getValue()) + "\"");
                parameters.remove();
            }
            authorization = "OAuth " + String.join(", ", headers);
        }
        String encoded = OAuth.query(values);
        boolean post = method.equals("POST");
        HttpURLConnection connection = connections.open(url + ((post && file == null) || encoded.isEmpty() ? "" : "?" + encoded));
        connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false);
        // Authenticated responses must not enter the platform HTTP cache.
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-store");
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", "VistulaChecker/1.0 (Android)");
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (!authorization.isEmpty()) connection.setRequestProperty("Authorization", authorization);
        try {
            if (post) {
                connection.setDoOutput(true);
                byte[] body;
                if (file != null) {
                    String boundary = "VistulaChecker" + UUID.randomUUID().toString().replace("-", "");
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    ByteArrayOutputStream multipart = new ByteArrayOutputStream();
                    String safeName = filename == null ? "upload.bin" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
                    multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"data\"; filename=\"" + safeName
                        + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    multipart.write(file); multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                    body = multipart.toByteArray();
                } else {
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    body = encoded.getBytes(StandardCharsets.UTF_8);
                }
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream stream = connection.getOutputStream()) { stream.write(body); }
            }
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) throw new IOException("API zwróciło przekierowanie. Otwórz tę metodę w przeglądarce przez dokumentację.");
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String result = "";
            byte[] bytes = new byte[0];
            if (input != null) try (InputStream stream = input; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192]; int count;
                while ((count = stream.read(chunk)) != -1) {
                    if (buffer.size() + count > 8 * 1024 * 1024) throw new IOException("Odpowiedź API przekracza limit 8 MB.");
                    buffer.write(chunk, 0, count);
                }
                bytes = buffer.toByteArray(); result = buffer.toString("UTF-8");
            }
            if (status < 200 || status >= 300) {
                throw new ApiException(status, new ApiFailure(status, result, connection.getHeaderField("Retry-After"), System.currentTimeMillis()), endpoint);
            }
            return new Response(bytes, connection.getContentType());
        } finally { connection.disconnect(); }
    }
    String get(String endpoint, Map<String, String> params) throws Exception {
        return request("GET", endpoint, params, true, token, tokenSecret);
    }
    static String profileFields(JSONObject state) {
        String scopes = state.optString("scopes");
        String fields = "id|first_name|last_name|student_status";
        if (StudentServices.hasScope(scopes, "studies"))
            fields += "|student_number|student_programmes[programme[name]|admission_date|stages|status]";
        if (StudentServices.hasScope(scopes, "email")) fields += "|email";
        if (StudentServices.hasScope(scopes, "photo")) fields += "|photo_urls[200x200]";
        return fields;
    }
    Map<String, String> begin(String scopes) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("oauth_callback", "oob");
        String normalized = StudentServices.normalizeScopes(scopes);
        if (!normalized.isEmpty()) params.put("scopes", normalized);
        Map<String, String> result = OAuth.form(request("GET", "services/oauth/request_token",
            params, true, "", ""));
        if (!"true".equals(result.get("oauth_callback_confirmed"))) throw new IOException("USOS nie potwierdził protokołu OAuth 1.0a.");
        return result;
    }
    Map<String, String> finish(JSONObject state, String pin) throws Exception {
        String cleanPin = pin == null ? "" : pin.trim().replaceAll("\\s+", "");
        return OAuth.form(request("GET", "services/oauth/access_token", Map.of("oauth_verifier", cleanPin), true,
            state.getString("requestToken"), state.getString("requestSecret")));
    }
    Response explore(JSONObject metadata, Map<String, String> params, String method, byte[] file, String filename) throws Exception {
        JSONObject auth = metadata.getJSONObject("auth_options");
        for (String name : params.keySet()) if (name.startsWith("oauth_")) throw new IllegalArgumentException("Parametry OAuth są zarządzane przez aplikację. Do logowania użyj ekranu Konto.");
        if (file != null && !metadata.getString("name").matches("services/blobbox/put(?:_unsigned)?")) throw new IllegalArgumentException("Ta metoda nie obsługuje plików multipart.");
        boolean sign = !auth.optString("consumer").equals("ignored") && !key.isEmpty();
        if (auth.optString("token").equals("required") && token.isEmpty()) throw new IllegalStateException("Ta metoda wymaga połączenia konta USOS.");
        if (auth.optString("consumer").equals("required")) sign = true;
        boolean useToken = !auth.optString("token").equals("ignored");
        return requestData(file == null ? method : "POST", metadata.getString("name"), params, sign, useToken ? token : "", useToken ? tokenSecret : "", file, filename);
    }
    static final class SyncException extends Exception {
        final String stage;
        SyncException(String stage, Exception cause) { super(cause); this.stage = stage; }
    }
    private String stage = "Rozpoczęcie synchronizacji";
    private void reportProgress(String message) {
        stage = message + (message.startsWith("Pobieranie planu") ? " · tt/student" : "");
        progress.accept(message);
    }
    Snapshot snapshot() throws Exception {
        try { return fetchSnapshot(); }
        catch (Exception e) { throw new SyncException(stage, e); }
    }
    private Snapshot fetchSnapshot() throws Exception {
        for (String required : List.of("grades", "studies")) {
            if (!StudentServices.hasScope(scopes, required)) throw new IllegalStateException(
                "Brak zakresu zgody „" + required + "”. Połącz konto ponownie z zakresami grades|studies|offline_access.");
        }
        LocalDate start = LocalDate.now(ZONE).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Snapshot s = new Snapshot(start.toString(), start.plusDays(planDays).toString());
        reportProgress("Pobieranie semestrów · courses/user");
        JSONObject editions = new JSONObject(get("services/courses/user", Map.of("active_terms_only", "false",
            "fields", "course_editions[course_id|course_name]"))).getJSONObject("course_editions");
        Map<String, String> names = UsosParser.courseNames(editions);
        List<String> terms = UsosParser.sortedKeys(editions);

        reportProgress("Pobieranie punktów ECTS · courses/user_ects_points");
        Map<String, Double> ectsPoints = Collections.emptyMap();
        try {
            String ectsRaw = get("services/courses/user_ects_points", Map.of());
            ectsPoints = UsosParser.ectsPoints(new JSONObject(ectsRaw));
        } catch (Exception e) { optionalFailure(e); }

        reportProgress("Pobieranie kalendarza semestrów · terms/terms");
        s.courses.addAll(UsosParser.courseEditions(editions, ectsPoints));
        try {
            if (!terms.isEmpty()) {
                String termsRaw = get("services/terms/terms", Map.of("term_ids", String.join("|", terms),
                    "fields", "id|name|start_date|end_date|finish_date"));
                s.terms.putAll(UsosParser.academicTerms(new JSONObject(termsRaw)));
            }
        } catch (Exception e) { optionalFailure(e); }

        Object accountsRaw = null;
        boolean paymentsScopeMissing = !StudentServices.hasScope(scopes, "payments");
        String paymentsNotice = paymentsScopeMissing
            ? "Brak zgody na płatności (zakres 'payments'). Dodaj zakres i połącz konto ponownie." : "";
        if (!paymentsScopeMissing) {
            reportProgress("Pobieranie subkont studenta · payments/user_accounts");
            try {
                String accRaw = get("services/payments/user_accounts", Collections.emptyMap());
                accountsRaw = new JSONTokener(accRaw).nextValue();
            } catch (ApiException e) {
                if (e.status == 403 && !e.authorization) {
                    paymentsScopeMissing = true;
                    paymentsNotice = "Brak uprawnień do modułu płatności (wymagany zakres 'payments').";
                } else throw e;
            }
        }
        Object paymentsRaw = null;
        if (!paymentsScopeMissing) {
            reportProgress("Pobieranie należności i rat · payments/user_payments");
            try {
                String payRaw = get("services/payments/user_payments", Map.of("fields",
                    "id|description|type|state|account_number|payment_deadline|total_amount|saldo_amount|currency|chosen_installment_plan[id|currency|installments[amount|payment_date|description|number]]"));
                paymentsRaw = new JSONTokener(payRaw).nextValue();
            } catch (ApiException e) {
                if (e.status == 403 && !e.authorization) {
                    paymentsScopeMissing = true;
                    paymentsNotice = "Brak uprawnień do modułu płatności (wymagany zakres 'payments').";
                } else throw e;
            }
        }
        s.payments = UsosParser.payments(accountsRaw, paymentsRaw, paymentsScopeMissing, paymentsNotice);

        reportProgress("Pobieranie prac dyplomowych · theses/user");
        try {
            String thesesRaw = get("services/theses/user", Map.of("fields", "id|title|status|supervisors[first_name|last_name]|reviewers[first_name|last_name]"));
            s.thesis = UsosParser.thesis(new JSONTokener(thesesRaw).nextValue());
        } catch (Exception e) { optionalFailure(e); }

        reportProgress("Pobieranie sprawdzianów cząstkowych · crstests/user");
        try {
            String testsRaw = get("services/crstests/user", Map.of("fields", "id|course_id|name|points|max_points|grade|date"));
            s.tests.addAll(UsosParser.courseTests(new JSONTokener(testsRaw).nextValue(), names));
        } catch (Exception e) { optionalFailure(e); }

        reportProgress("Pobieranie komunikatów uczelni · news/search");
        try {
            String newsRaw = get("services/news/search", Map.of("num", "10",
                "fields", "items[article[id|title|publication_date|author[first_name|last_name]|headline_html]]"));
            s.news.addAll(UsosParser.newsArticles(new JSONTokener(newsRaw).nextValue()));
        } catch (Exception e) { optionalFailure(e); }

        // Batch term IDs to avoid URL limits; every batch must succeed before commit.
        for (int i = 0; i < terms.size(); i += 8) {
            reportProgress("Pobieranie ocen · grades/terms2");
            List<String> batch = terms.subList(i, Math.min(terms.size(), i + 8));
            JSONObject grades = new JSONObject(get("services/grades/terms2", Map.of("term_ids", String.join("|", batch),
                "fields", "exam_id|exam_session_number|value_symbol|passes|value_description|counts_into_average|grade_type_id|date_modified|date_acquisition|modification_author|comment")));
            for (String term : batch) if (!grades.has(term) || grades.isNull(term)) throw new IOException("USOS zwrócił niepełny zestaw ocen.");
            UsosParser.grades(s, grades, names, ectsPoints);
        }
        List<JSONArray> weeks = TimetableLoader.load(start, planDays, (pageStart, days) ->
            new JSONArray(get("services/tt/student", Map.of("start", pageStart.toString(), "days", String.valueOf(days),
                "fields", "type|start_time|end_time|name|course_id|unit_id|group_number|cgwm_id|sm_id|room_number|room_id|building_name|building_id|classtype_name|classtype_id|lecturer_ids|frequency|url|classgroup_profile_url"))), this::reportProgress);
        reportProgress("Pobieranie nazwisk prowadzących · users/users");
        Map<String, String> lecturerNames = resolveLecturers(weeks, ids -> new JSONObject(get("services/users/users",
            Map.of("user_ids", String.join("|", ids), "fields", "first_name|last_name"))));
        reportProgress("Odczyt planu zajęć · tt/student");
        for (JSONArray rows : weeks) UsosParser.timetable(s, rows, lecturerNames);
        enrichGradeUnits(s);
        return s;
    }
    private static void optionalFailure(Exception failure) throws Exception {
        // Optional modules may be unavailable, but session expiry and server cooldowns apply to the whole sync.
        if (failure instanceof ApiException) {
            ApiException api = (ApiException) failure;
            if (api.authorization || api.retryable || api.status == 401) throw api;
        } else if (failure instanceof IOException) throw failure;
    }
    private void enrichGradeUnits(Snapshot snapshot) throws Exception {
        List<String> unitIds = new ArrayList<>(snapshot.records.values().stream().filter(r -> r.kind.equals("ocena"))
            .map(r -> r.optMeta("unitId")).filter(id -> !id.isEmpty()).collect(java.util.stream.Collectors.toCollection(TreeSet::new)));
        JSONObject types = new JSONObject();
        if (!unitIds.isEmpty()) {
            reportProgress("Rozpoznawanie typów zajęć · courses/units");
            try { types = new JSONObject(request("GET", "services/courses/classtypes_index", Map.of(), false, "", "")); }
            catch (Exception e) { optionalFailure(e); }
        }
        for (int i = 0; i < unitIds.size(); i += 50) {
            List<String> batch = unitIds.subList(i, Math.min(i + 50, unitIds.size()));
            try {
                JSONObject units = new JSONObject(get("services/courses/units", Map.of("unit_ids", String.join("|", batch),
                    "fields", "id|course_name|course_id|term_id|classtype_id|homepage_url|profile_url")));
                for (Record grade : snapshot.records.values()) {
                    JSONObject unit = units.optJSONObject(grade.optMeta("unitId"));
                    if (!grade.kind.equals("ocena") || unit == null) continue;
                    grade.metadata.put("unitData", new JSONObject(unit.toString()));
                    String typeId = StudentServices.text(unit, "classtype_id");
                    if (!typeId.isEmpty()) grade.metadata.put("classTypeId", typeId);
                    JSONObject definition = types.optJSONObject(typeId);
                    String name = definition == null ? "" : StudentServices.localized(definition.opt("name"));
                    if (!name.isEmpty()) grade.metadata.put("classType", name).put("classTypeSource", "unit_api");
                }
            } catch (Exception e) { optionalFailure(e); }
        }
        // Resolve editor IDs independently; unavailable names never prevent a grade snapshot.
        List<String> authors = new ArrayList<>(snapshot.records.values().stream().filter(r -> r.kind.equals("ocena"))
            .map(r -> r.optMeta("modificationAuthor")).filter(id -> !id.isEmpty()).collect(java.util.stream.Collectors.toCollection(TreeSet::new)));
        for (int i = 0; i < authors.size(); i += 50) {
            try {
                Map<String, String> names = UsosParser.lecturerNames(new JSONObject(get("services/users/users", Map.of(
                    "user_ids", String.join("|", authors.subList(i, Math.min(i + 50, authors.size()))), "fields", "first_name|last_name"))));
                for (Record grade : snapshot.records.values()) {
                    String name = names.get(grade.optMeta("modificationAuthor"));
                    if (name != null && !name.isEmpty()) grade.metadata.put("modificationAuthorName", name);
                }
            } catch (Exception e) { optionalFailure(e); }
        }
    }
    interface LecturerSource { JSONObject fetch(List<String> ids) throws Exception; }
    static Map<String, String> resolveLecturers(List<JSONArray> weeks, LecturerSource source) throws Exception {
        Set<String> unique = new TreeSet<>();
        for (JSONArray week : weeks) unique.addAll(UsosParser.lecturerIds(week));
        List<String> ids = new ArrayList<>(unique);
        Map<String, String> names = new TreeMap<>();
        for (int i = 0; i < ids.size(); i += 50) {
            List<String> batch = ids.subList(i, Math.min(i + 50, ids.size()));
            JSONObject users = source.fetch(batch);
            for (String id : batch) if (!users.has(id)) throw new IOException("USOS zwrócił niepełne dane prowadzących.");
            names.putAll(UsosParser.lecturerNames(users));
        }
        return names;
    }
}
