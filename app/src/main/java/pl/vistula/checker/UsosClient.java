package pl.vistula.checker;

import pl.vistula.checker.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.json.*;

final class UsosClient {
    static final String BASE = "https://usosapps.vistula.edu.pl/";
    static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private final String key, secret, token, tokenSecret;
    private final int planDays;
    private final java.util.function.Consumer<String> progress;
    interface ConnectionSource { HttpURLConnection open(String url) throws IOException; }
    private final ConnectionSource connections;
    static final class Response {
        final byte[] bytes;
        final String mime;
        Response(byte[] bytes, String mime) { this.bytes = bytes; this.mime = mime == null || mime.trim().isEmpty() ? "application/octet-stream" : mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT); }
        boolean binary() { return !(mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") || mime.contains("javascript")); }
        String text() { return new String(bytes, StandardCharsets.UTF_8); }
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
    }
    static final class ApiException extends IOException {
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
        String encoded = OAuth.query(values);
        boolean post = method.equals("POST");
        HttpURLConnection connection = connections.open(url + ((post && file == null) || encoded.isEmpty() ? "" : "?" + encoded));
        connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", "VistulaChecker/1.0 (Android)");
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
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
    Map<String, String> begin(String scopes) throws Exception {
        Map<String, String> result = OAuth.form(request("GET", "services/oauth/request_token",
            Map.of("oauth_callback", "oob", "scopes", scopes), true, "", ""));
        if (!"true".equals(result.get("oauth_callback_confirmed"))) throw new IOException("USOS nie potwierdził protokołu OAuth 1.0a.");
        return result;
    }
    Map<String, String> finish(JSONObject state, String pin) throws Exception {
        return OAuth.form(request("GET", "services/oauth/access_token", Map.of("oauth_verifier", pin), true,
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
        LocalDate start = LocalDate.now(ZONE).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Snapshot s = new Snapshot(start.toString(), start.plusDays(planDays).toString());
        reportProgress("Pobieranie semestrów · courses/user");
        JSONObject editions = new JSONObject(get("services/courses/user", Map.of("active_terms_only", "false",
            "fields", "course_editions[course_id|course_name]"))).getJSONObject("course_editions");
        Map<String, String> names = UsosParser.courseNames(editions);
        List<String> terms = UsosParser.sortedKeys(editions);
        // Batch term IDs to avoid URL limits; every batch must succeed before commit.
        for (int i = 0; i < terms.size(); i += 8) {
            reportProgress("Pobieranie ocen · grades/terms2");
            List<String> batch = terms.subList(i, Math.min(terms.size(), i + 8));
            JSONObject grades = new JSONObject(get("services/grades/terms2", Map.of("term_ids", String.join("|", batch),
                "fields", "exam_id|exam_session_number|value_symbol|date_modified|comment")));
            for (String term : batch) if (!grades.has(term) || grades.isNull(term)) throw new IOException("USOS zwrócił niepełny zestaw ocen.");
            UsosParser.grades(s, grades, names);
        }
        List<JSONArray> weeks = TimetableLoader.load(start, planDays, (pageStart, days) ->
            new JSONArray(get("services/tt/student", Map.of("start", pageStart.toString(), "days", String.valueOf(days),
                "fields", "type|start_time|end_time|name|unit_id|group_number|cgwm_id|sm_id|room_number|building_name|classtype_name|lecturer_ids|frequency"))), this::reportProgress);
        reportProgress("Pobieranie nazwisk prowadzących · users/users");
        Map<String, String> lecturerNames = resolveLecturers(weeks, ids -> new JSONObject(get("services/users/users",
            Map.of("user_ids", String.join("|", ids), "fields", "first_name|last_name"))));
        reportProgress("Odczyt planu zajęć · tt/student");
        for (JSONArray rows : weeks) UsosParser.timetable(s, rows, lecturerNames);
        return s;
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
