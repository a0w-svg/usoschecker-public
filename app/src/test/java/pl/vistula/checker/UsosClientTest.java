package pl.vistula.checker;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class UsosClientTest {
    private static class Connection extends HttpURLConnection {
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        int status = 200;
        String body = "{}", mime = "Application/JSON; charset=utf-8";
        boolean disconnected;
        Connection() throws MalformedURLException { super(new URL(UsosClient.BASE)); }
        @Override public void connect() {}
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return status; }
        @Override public String getContentType() { return mime; }
        @Override public String getHeaderField(String name) { return name.equals("Retry-After") ? "60" : null; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)); }
        @Override public InputStream getErrorStream() { return getInputStream(); }
        @Override public OutputStream getOutputStream() { return sent; }
    }
    private JSONObject credentials() throws Exception {
        return new JSONObject().put("key", "test-key").put("secret", "test-secret").put("token", "private-token").put("tokenSecret", "private-secret");
    }
    private JSONObject metadata(String consumer, String token) throws Exception {
        return new JSONObject().put("name", "services/apisrv/installation")
            .put("auth_options", new JSONObject().put("consumer", consumer).put("token", token));
    }
    @Test public void ignoredTokenIsNotSentAndMimeIsNormalized() throws Exception {
        Connection connection = new Connection(); String[] url = {""};
        UsosClient client = new UsosClient(credentials(), progress -> {}, address -> { url[0] = address; return connection; });
        UsosClient.Response response = client.explore(metadata("required", "ignored"), Map.of(), "GET", null, null);
        assertTrue(url[0].contains("oauth_signature="));
        assertFalse(url[0].contains("oauth_token="));
        assertFalse(url[0].contains("private-token"));
        assertEquals("application/json", response.mime);
        assertFalse(response.binary());
        assertTrue(connection.disconnected);
    }
    @Test public void formPostKeepsParametersInBodyAndSignsPost() throws Exception {
        Connection connection = new Connection(); String[] url = {""};
        UsosClient client = new UsosClient(credentials(), progress -> {}, address -> { url[0] = address; return connection; });
        client.explore(metadata("required", "required"), Map.of("fields", "name|id"), "POST", null, null);
        assertFalse(url[0].contains("?"));
        assertEquals("POST", connection.getRequestMethod());
        assertTrue(connection.sent.toString("UTF-8").contains("fields=name%7Cid"));
        assertTrue(connection.sent.toString("UTF-8").contains("oauth_token=private-token"));
        assertTrue(connection.disconnected);
    }
    @Test public void formErrorsPreserveRetryInformationAndHideRawBody() throws Exception {
        Connection connection = new Connection(); connection.status = 429; connection.body = "oauth_problem=rate_limit&oauth_token=private-token";
        UsosClient client = new UsosClient(credentials(), progress -> {}, address -> connection);
        long before = System.currentTimeMillis();
        try { client.get("services/apisrv/installation", Map.of()); fail("Expected API failure"); }
        catch (UsosClient.ApiException failure) {
            assertTrue(failure.retryable); assertFalse(failure.authorization);
            assertTrue(failure.retryAt >= before + 60000);
            assertFalse(failure.getMessage().contains("private-token"));
        }
        assertTrue(connection.disconnected);
    }
    @Test public void rejectedLocalRequestsNeverOpenAConnection() throws Exception {
        UsosClient client = new UsosClient(credentials(), progress -> {}, address -> { throw new AssertionError("Network must not be used"); });
        try { client.explore(metadata("required", "required"), Map.of("oauth_token", "injected"), "GET", null, null); fail(); }
        catch (IllegalArgumentException expected) {}
        try { client.explore(metadata("required", "required"), Map.of(), "POST", new byte[1], "file.bin"); fail(); }
        catch (IllegalArgumentException expected) {}
        try { client.request("DELETE", "services/apisrv/installation", Map.of(), false, "", ""); fail(); }
        catch (IllegalArgumentException expected) {}
    }
    @Test public void completeSnapshotAcceptsEmptyGradeCollectionsAndLoadsEveryWeek() throws Exception {
        java.util.concurrent.atomic.AtomicInteger pages = new java.util.concurrent.atomic.AtomicInteger();
        UsosClient client = new UsosClient(credentials(), progress -> {}, address -> {
            Connection connection = new Connection();
            if (address.contains("courses/user_ects_points")) connection.body = "{}";
            else if (address.contains("courses/user")) connection.body = "{\"course_editions\":{\"2026Z\":[{\"course_id\":\"FIN\",\"course_name\":{\"pl\":\"Finanse\"}}]}}";
            else if (address.contains("grades/terms2")) connection.body = "{\"2026Z\":{\"FIN\":{\"course_grades\":null,\"course_units_grades\":{}}}}";
            else if (address.contains("tt/student")) { connection.body = "[]"; pages.incrementAndGet(); }
            else if (address.contains("terms/terms") || address.contains("payments/") || address.contains("theses/user") || address.contains("news/search")) connection.body = "[]";
            else if (address.contains("crstests/user")) connection.body = "[]";
            else throw new AssertionError("Unexpected request: " + address);
            return connection;
        });
        assertTrue(client.snapshot().records.isEmpty());
        assertEquals(8, pages.get());
    }
    @Test public void completeSnapshotParsesEctsAndAttachesToGradeRecord() throws Exception {
        UsosClient client = new UsosClient(credentials(), progress -> {}, address -> {
            Connection connection = new Connection();
            if (address.contains("courses/user_ects_points")) connection.body = "{\"2026Z\":{\"FIN\":\"5.0\"}}";
            else if (address.contains("courses/user")) connection.body = "{\"course_editions\":{\"2026Z\":[{\"course_id\":\"FIN\",\"course_name\":{\"pl\":\"Finanse\"}}]}}";
            else if (address.contains("grades/terms2")) connection.body = "{\"2026Z\":{\"FIN\":{\"course_grades\":[{\"1\":{\"exam_id\":10,\"exam_session_number\":1,\"value_symbol\":\"4.5\",\"date_modified\":\"2026-10-14 12:00:00\",\"comment\":null}}],\"course_units_grades\":{}}}}";
            else if (address.contains("tt/student")) connection.body = "[]";
            else if (address.contains("terms/terms") || address.contains("payments/") || address.contains("theses/user") || address.contains("news/search")) connection.body = "[]";
            else if (address.contains("crstests/user")) connection.body = "[]";
            else throw new AssertionError("Unexpected request: " + address);
            return connection;
        });
        pl.vistula.checker.core.Snapshot snapshot = client.snapshot();
        assertEquals(1, snapshot.records.size());
        pl.vistula.checker.core.Record grade = snapshot.records.get("grade:10:1");
        assertNotNull(grade);
        assertEquals(5.0, grade.ects, 0.0001);
    }
    @Test public void completeSnapshotLoadsTermsPaymentsThesesAndTests() throws Exception {
        UsosClient client = new UsosClient(credentials(), progress -> {}, address -> {
            Connection connection = new Connection();
            if (address.contains("courses/user_ects_points")) connection.body = "{}";
            else if (address.contains("courses/user")) connection.body = "{\"course_editions\":{\"2026Z\":[{\"course_id\":\"FIN\",\"course_name\":{\"pl\":\"Finanse\"}}]}}";
            else if (address.contains("terms/terms")) connection.body = "{\"2026Z\":{\"name\":{\"pl\":\"Semestr zimowy\"},\"start_date\":\"2026-10-01\",\"end_date\":\"2027-02-15\"}}";
            else if (address.contains("payments/user_accounts")) connection.body = "[{\"account_number\":\"PL999\",\"name\":\"Subkonto czesnego\",\"currency\":\"PLN\"}]";
            else if (address.contains("payments/user_payments")) connection.body = "[{\"id\":\"p1\",\"total_amount\":850.0,\"currency\":\"PLN\",\"description\":{\"pl\":\"Czesne\"},\"state\":\"unsettled\",\"payment_deadline\":\"2026-10-31\"}]";
            else if (address.contains("theses/user")) connection.body = "[{\"title\":{\"pl\":\"System wykrywania anomalii\"},\"status\":{\"pl\":\"W toku\"}}]";
            else if (address.contains("crstests/user")) connection.body = "[{\"id\":\"t1\",\"course_id\":\"FIN\",\"name\":{\"pl\":\"Kolokwium 1\"},\"points\":18.0,\"max_points\":20.0}]";
            else if (address.contains("news/search")) connection.body = "{\"items\":[{\"article\":{\"id\":\"n1\",\"title\":{\"pl\":\"Inauguracja roku\"},\"publication_date\":\"2026-10-01\"}}]}";
            else if (address.contains("grades/terms2")) connection.body = "{\"2026Z\":{\"FIN\":{\"course_grades\":[],\"course_units_grades\":{}}}}";
            else if (address.contains("tt/student")) connection.body = "[]";
            else throw new AssertionError("Unexpected request: " + address);
            return connection;
        });
        pl.vistula.checker.core.Snapshot snapshot = client.snapshot();
        assertEquals(1, snapshot.terms.size());
        assertEquals("Semestr zimowy", snapshot.terms.get("2026Z").name);
        assertNotNull(snapshot.payments);
        assertEquals("PL999", snapshot.payments.accounts.get(0).bankAccountNumber);
        assertNotNull(snapshot.thesis);
        assertEquals("System wykrywania anomalii", snapshot.thesis.title);
        assertEquals(1, snapshot.tests.size());
        assertEquals("Kolokwium 1", snapshot.tests.get(0).testName);
        assertEquals(1, snapshot.news.size());
        assertEquals("Inauguracja roku", snapshot.news.get(0).title);
    }
    @Test public void malformedGradesReportTheirStageWithoutExposingResponseBodies() throws Exception {
        UsosClient client = new UsosClient(credentials(), progress -> {}, address -> {
            Connection connection = new Connection();
            if (address.contains("courses/user_ects_points")) connection.body = "{}";
            else connection.body = address.contains("courses/user") ? "{\"course_editions\":{\"2026Z\":[]}}" : "private-response";
            return connection;
        });
        try { client.snapshot(); fail("Expected invalid grades"); }
        catch (UsosClient.SyncException failure) {
            assertTrue(Repository.message(failure).contains("grades/terms2"));
            assertFalse(Repository.message(failure).contains("private-response"));
            assertTrue(Repository.cause(failure) instanceof org.json.JSONException);
        }
    }
    @Test public void wrappedAuthorizationErrorsRetainTheirClassification() throws Exception {
        Connection connection = new Connection(); connection.status = 401; connection.body = "oauth_problem=token_expired";
        UsosClient client = new UsosClient(credentials(), progress -> {}, address -> connection);
        try { client.snapshot(); fail("Expected expired session"); }
        catch (UsosClient.SyncException failure) {
            UsosClient.ApiException cause = (UsosClient.ApiException) Repository.cause(failure);
            assertTrue(cause.authorization); assertFalse(cause.retryable);
            assertTrue(Repository.message(failure).contains("services/courses/user"));
        }
    }
    @Test public void paymentScopeMissingIsCapturedGracefullyInSnapshot() throws Exception {
        UsosClient client = new UsosClient(credentials(), progress -> {}, address -> {
            Connection connection = new Connection();
            if (address.contains("courses/user_ects_points")) connection.body = "{}";
            else if (address.contains("courses/user")) connection.body = "{\"course_editions\":{\"2026Z\":[{\"course_id\":\"FIN\",\"course_name\":{\"pl\":\"Finanse\"}}]}}";
            else if (address.contains("terms/terms")) connection.body = "{\"2026Z\":{\"name\":{\"pl\":\"Semestr zimowy\"},\"start_date\":\"2026-10-01\",\"end_date\":\"2027-02-15\"}}";
            else if (address.contains("payments/")) {
                connection.status = 403;
                connection.body = "{\"message\":\"User has not granted access to scope 'payments'\"}";
            }
            else if (address.contains("theses/user") || address.contains("news/search")) connection.body = "[]";
            else if (address.contains("crstests/user")) connection.body = "[]";
            else if (address.contains("grades/terms2")) connection.body = "{\"2026Z\":{\"FIN\":{\"course_grades\":[],\"course_units_grades\":{}}}}";
            else if (address.contains("tt/student")) connection.body = "[]";
            return connection;
        });
        pl.vistula.checker.core.Snapshot snapshot = client.snapshot();
        assertNotNull(snapshot.payments);
        assertTrue(snapshot.payments.scopeMissing);
        assertTrue(snapshot.payments.statusNotice.contains("payments"));
    }
    @Test public void beginSanitizesDirtyScopesWithoutEmptyTokens() throws Exception {
        String[] captured = {""};
        UsosClient client = new UsosClient(credentials(), msg -> {}, address -> {
            captured[0] = address;
            Connection c = new Connection();
            c.body = "oauth_token=tok1&oauth_token_secret=sec1&oauth_callback_confirmed=true";
            return c;
        });
        Map<String, String> res = client.begin(" grades | | studies||offline_access| ");
        assertEquals("tok1", res.get("oauth_token"));
        assertTrue(captured[0].contains("scopes=grades%7Cstudies%7Coffline_access"));
        assertFalse(captured[0].contains("%7C%7C"));
    }
    @Test public void finishStripsWhitespaceFromPinVerifier() throws Exception {
        String[] captured = {""};
        UsosClient client = new UsosClient(credentials(), msg -> {}, address -> {
            captured[0] = address;
            Connection c = new Connection();
            c.body = "oauth_token=accTok&oauth_token_secret=accSec";
            return c;
        });
        JSONObject state = new JSONObject().put("requestToken", "reqT").put("requestSecret", "reqS");
        Map<String, String> res = client.finish(state, " 1234 5678 \n");
        assertEquals("accTok", res.get("oauth_token"));
        assertTrue(captured[0].contains("oauth_verifier=12345678"));
    }
}
