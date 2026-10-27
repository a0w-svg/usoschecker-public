package pl.vistula.checker

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import pl.vistula.checker.core.OAuth
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class UsosClientTest {
    @Test
    fun lecturerOnlySyncUsesCombinedTimetableWithoutStudentModules() {
        val calls = AtomicInteger()
        val state = credentials().put("staffStatus", 2).put("studentStatusCode", 0).put("scopes", "offline_access")
        val client = UsosClient(state, {}) { address ->
            assertTrue(address, address.contains("/tt/user?"))
            calls.incrementAndGet()
            Connection().apply { body = "[]" }
        }
        val snapshot = client.snapshot()
        assertEquals(8, calls.get())
        assertTrue(snapshot.courses.isEmpty())
        assertNull(snapshot.payments)
    }

    @Test
    fun studentLecturerKeepsStudentDataAndUsesCombinedTimetable() {
        val state = credentials().put("staffStatus", 2).put("studentStatusCode", 2)
        val pages = AtomicInteger()
        val client = UsosClient(state, {}) { address ->
            assertFalse(address.contains("/tt/student?"))
            if (address.contains("/tt/user?")) {
                pages.incrementAndGet()
                Connection().apply { body = "[]" }
            } else snapshotConnection(address)
        }
        assertNotNull(client.snapshot().payments)
        assertEquals(8, pages.get())
    }

    @Test
    fun basicConsentSkipsPaymentRequestsAndStillLoadsGradesAndPlan() {
        for (scopes in listOf("grades|studies|offline_access", " grades | studies | other_payments | offline_access ")) {
            val planPages = AtomicInteger()
            val gradeRequests = AtomicInteger()
            val client = UsosClient(credentials().put("scopes", scopes), {}) { address ->
                assertFalse("No payment consent", address.contains("services/payments/"))
                if (address.contains("grades/terms2")) gradeRequests.incrementAndGet()
                if (address.contains("tt/student")) planPages.incrementAndGet()
                snapshotConnection(address)
            }
            val snapshot = client.snapshot()
            assertTrue(snapshot.payments!!.scopeMissing)
            assertTrue(snapshot.payments!!.statusNotice.contains("payments"))
            assertEquals(1, gradeRequests.get())
            assertEquals(8, planPages.get())
        }
    }

    @Test
    fun snapshotStoresAllUiLanguagesWithoutExtraRequests() {
        val calls = AtomicInteger()
        val client = UsosClient(credentials().put("scopes", "grades|studies"), {}) { address ->
            calls.incrementAndGet()
            val result = snapshotConnection(address)
            if (address.contains("services/courses/user?")) {
                result.body = "{\"course_editions\":{\"2026Z\":[{\"course_id\":\"C\",\"course_name\":{\"pl\":\"Analiza\",\"en\":\"Analysis\",\"uk\":\"Аналіз\"}}]}}"
            }
            result
        }
        val snapshot = client.snapshot()
        assertEquals("Analiza", snapshot.courses[0].title)
        assertEquals("Analysis", snapshot.forLanguage("en").courses[0].title)
        assertEquals("Аналіз", snapshot.forLanguage("uk").courses[0].title)
        assertTrue("One download per endpoint/page", calls.get() < 25)
        val view = Repository.localizedView(JSONObject().put("snapshot", snapshot.json()), "en")
        assertEquals("Analysis", view.getJSONObject("snapshot").getJSONArray("courses").getJSONObject(0).getString("title"))
        assertEquals("Analiza", snapshot.courses[0].title)
    }

    @Test fun thesisUsesAuthenticatedUserAndDocumentedResponseShape() {
        val client = UsosClient(credentials().put("scopes", "grades|studies"), {}) { address ->
            when {
                address.contains("services/users/user?") -> Connection().apply { body = """{"id":"42"}""" }
                address.contains("services/theses/user?") -> Connection().apply {
                    assertTrue(address.contains("user_id=42"))
                    assertTrue(address.contains("authored_theses"))
                    body = """{"authored_theses":[{"title":"Tytuł główny","titles":{"pl":"Praca","en":"Thesis"},"supervisors":[{"first_name":"Anna","last_name":"Nowak"}]}]}"""
                }
                else -> snapshotConnection(address)
            }
        }
        val snapshot = client.snapshot()
        assertEquals("Praca", snapshot.thesis!!.title)
        assertEquals("Anna Nowak", snapshot.thesis!!.supervisors)
        assertEquals("Thesis", snapshot.forLanguage("en").thesis!!.title)
    }

    @Test
    fun missingCoreConsentAbortsBeforeNetworkRequests() {
        for (scopes in listOf("", "studies|payments", "grades|payments")) {
            val client = UsosClient(credentials().put("scopes", scopes), {}) {
                throw AssertionError("Must preserve snapshot until core consent is available")
            }
            try {
                client.snapshot()
                fail()
            } catch (failure: UsosClient.SyncException) {
                assertTrue(Repository.cause(failure) is IllegalStateException)
                assertTrue(Repository.message(failure).contains("zgody"))
            }
        }
    }

    @Test
    fun paymentConsentRejectedByServerSkipsRemainingPaymentRequests() {
        val deniedRequests = intArrayOf(0)
        val client = UsosClient(credentials(), {}) { address ->
            assertFalse(address.contains("payments/user_payments"))
            val connection = snapshotConnection(address)
            if (address.contains("payments/user_accounts")) {
                deniedRequests[0]++
                connection.status = 403
            }
            connection
        }
        assertTrue(client.snapshot().payments!!.scopeMissing)
        assertEquals(1, deniedRequests[0])
    }

    @Test
    fun profileFieldsFollowConsentAndMailIsNotEmailConsent() {
        val basic = UsosClient.profileFields(credentials().put("scopes", "grades|studies|mail|offline_access"))
        assertTrue(basic.contains("student_number"))
        assertFalse(basic.contains("email"))
        assertFalse(basic.contains("photo_urls"))
        val extended = UsosClient.profileFields(credentials().put("scopes", " studies | email | photo "))
        assertTrue(extended.contains("|email"))
        assertTrue(extended.contains("|photo_urls[200x200]"))
        val unknown = UsosClient.profileFields(JSONObject())
        assertFalse(unknown.contains("student_programmes"))
        assertFalse(unknown.contains("email"))
    }

    @Test fun courseTestsFetchOnlyVisibleNodesWithDocumentedSelectors() {
        val details = ArrayList<String>()
        val client = UsosClient(credentials(), {}) { address ->
            when {
                address.contains("crstests/participant2") -> Connection().apply {
                    val decoded = java.net.URLDecoder.decode(address, "UTF-8")
                    assertFalse(decoded.contains("subnodes_deep[id|parent_id|name|type|visible_for_students|task_node_details"))
                    body = """[{"course_edition":{"course":{"id":"C"},"term":{"id":"T"}},"root":{"id":"r","type":"root","visible_for_students":true,"subnodes":[{"id":"injected","task_node_details":{"students_points":{"points":99}}}],"subnodes_deep":[{"id":"f","parent_id":"r","type":"folder","visible_for_students":false},{"id":"hidden","parent_id":"f","type":"task","visible_for_students":true},{"id":"shown","parent_id":"r","type":"task","visible_for_students":true,"name":{"en":"Quiz"}}]}}]"""
                }
                address.contains("crstests/node2") -> Connection().apply {
                    details.add(address)
                    assertTrue(address.contains("node_id=shown"))
                    body = """{"id":"shown","parent_id":"r","type":"task","visible_for_students":true,"task_node_details":{"points_max":10,"students_points":{"points":7}},"grade_node_details":{"students_grade":{"grade_value":{"symbol":"5"}}}}"""
                }
                else -> snapshotConnection(address)
            }
        }
        val snapshot = client.snapshot()
        assertEquals(1, details.size)
        assertEquals(7.0, snapshot.tests.single().points, 0.0)
        assertEquals("", snapshot.tests.single().grade)
        assertEquals("Quiz", snapshot.forLanguage("en").tests.single().testName)
    }

    @Test fun incompleteCourseTestAncestryIsReportedWithoutFetchingDetails() {
        val client = UsosClient(credentials(), {}) { address ->
            when {
                address.contains("crstests/participant2") -> Connection().apply {
                    body = """[{"root":{"id":"r","visible_for_students":true,"subnodes_deep":[{"id":"x","parent_id":"missing","type":"task","visible_for_students":true}]}}]"""
                }
                address.contains("crstests/node2") -> throw AssertionError("Untrusted ancestry must not be fetched")
                else -> snapshotConnection(address)
            }
        }
        val snapshot = client.snapshot()
        assertTrue(snapshot.tests.isEmpty())
        assertTrue(snapshot.serviceErrors.containsKey("crstests"))
    }

    @Test fun malformedCourseTestDetailsAreNotPresentedAsAnEmptySuccess() {
        val client = UsosClient(credentials(), {}) { address ->
            when {
                address.contains("crstests/participant2") -> Connection().apply {
                    body = """[{"root":{"id":"r","visible_for_students":true,"subnodes_deep":[{"id":"x","parent_id":"r","type":"task","visible_for_students":true}]}}]"""
                }
                address.contains("crstests/node2") -> Connection().apply {
                    body = """{"id":"x","parent_id":"r","type":"task","visible_for_students":true,"task_node_details":null}"""
                }
                else -> snapshotConnection(address)
            }
        }
        val snapshot = client.snapshot()
        assertTrue(snapshot.tests.isEmpty())
        assertTrue(snapshot.serviceErrors.containsKey("crstests"))
    }

    @Test fun courseTestDetailRequestsAreCapped() {
        val children = org.json.JSONArray()
        repeat(51) { children.put(JSONObject().put("id", "n$it").put("parent_id", "r").put("type", "task").put("visible_for_students", true)) }
        val data = org.json.JSONArray().put(JSONObject().put("root", JSONObject().put("id", "r").put("visible_for_students", true).put("subnodes_deep", children)))
        val client = UsosClient(credentials(), {}) { address ->
            when {
                address.contains("crstests/participant2") -> Connection().apply { body = data.toString() }
                address.contains("crstests/node2") -> throw AssertionError("Limit must be checked before detail requests")
                else -> snapshotConnection(address)
            }
        }
        assertTrue(client.snapshot().serviceErrors.containsKey("crstests"))
    }

    @Test fun courseTestDetailTimeoutDoesNotDiscardCoreSnapshot() {
        var gradesLoaded = false
        val client = UsosClient(credentials(), {}) { address ->
            when {
                address.contains("crstests/participant2") -> Connection().apply {
                    body = """[{"root":{"id":"r","visible_for_students":true,"subnodes_deep":[{"id":"x","parent_id":"r","type":"task","visible_for_students":true}]}}]"""
                }
                address.contains("crstests/node2") -> throw SocketTimeoutException("fixture timeout")
                else -> {
                    if (address.contains("grades/terms2")) gradesLoaded = true
                    snapshotConnection(address)
                }
            }
        }
        val snapshot = client.snapshot()
        assertTrue(snapshot.serviceErrors.containsKey("crstests"))
        assertTrue(snapshot.tests.isEmpty())
        assertTrue(gradesLoaded)
    }

    @Test fun courseTestTaskCannotBeAnAncestorOfAnotherTask() {
        val client = UsosClient(credentials(), {}) { address ->
            when {
                address.contains("crstests/participant2") -> Connection().apply {
                    body = """[{"root":{"id":"r","type":"root","visible_for_students":true,"subnodes_deep":[{"id":"a","parent_id":"r","type":"task","visible_for_students":true},{"id":"b","parent_id":"a","type":"task","visible_for_students":true}]}}]"""
                }
                address.contains("crstests/node2") -> throw AssertionError("Invalid hierarchy must be rejected before details")
                else -> snapshotConnection(address)
            }
        }
        assertTrue(client.snapshot().serviceErrors.containsKey("crstests"))
    }

    @Test fun unexpectedNestedCourseTestTreeIsReportedAsIncomplete() {
        val client = UsosClient(credentials(), {}) { address ->
            when {
                address.contains("crstests/participant2") -> Connection().apply {
                    body = """[{"root":{"id":"r","visible_for_students":true,"subnodes_deep":[{"id":"folder","parent_id":"r","type":"folder","visible_for_students":true,"subnodes":[{"id":"lost"}]}]}}]"""
                }
                address.contains("crstests/node2") -> throw AssertionError("Unexpected tree must not be fetched")
                else -> snapshotConnection(address)
            }
        }
        assertTrue(client.snapshot().serviceErrors.containsKey("crstests"))
    }

    private class Connection : HttpURLConnection(URL(UsosClient.BASE)) {
        val sent = ByteArrayOutputStream()
        var status = 200
        var body = "{}"
        var mime = "Application/JSON; charset=utf-8"
        var disconnected = false

        override fun connect() {}
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getContentType(): String = mime
        override fun getHeaderField(name: String?): String? =
            if (name == "Retry-After") "60" else null
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8))
        override fun getErrorStream(): InputStream = inputStream
        override fun getOutputStream(): OutputStream = sent
    }

    private fun credentials(): JSONObject =
        JSONObject().put("key", "test-key").put("secret", "test-secret")
            .put("token", "private-token").put("tokenSecret", "private-secret")
            .put("scopes", "grades|studies|payments|crstests|offline_access")

    private fun metadata(consumer: String, token: String): JSONObject =
        JSONObject().put("name", "services/apisrv/installation")
            .put("auth_options", JSONObject().put("consumer", consumer).put("token", token))

    @Test
    fun ignoredTokenIsNotSentAndMimeIsNormalized() {
        val connection = Connection()
        val url = arrayOf("")
        val client = UsosClient(credentials(), {}) { address ->
            url[0] = address
            connection
        }
        val response = client.explore(metadata("required", "ignored"), emptyMap(), "GET", null, null)
        assertTrue(url[0].contains("oauth_signature="))
        assertFalse(url[0].contains("oauth_token="))
        assertFalse(url[0].contains("private-token"))
        assertEquals("application/json", response.mime)
        assertFalse(response.binary())
        assertTrue(connection.disconnected)
    }

    @Test
    fun authenticatedResponsesCannotUseThePlatformCache() {
        val connection = Connection()
        val client = UsosClient(credentials(), {}) { connection }
        client.get("services/apisrv/installation", emptyMap())
        assertFalse(connection.useCaches)
        assertEquals("no-store", connection.getRequestProperty("Cache-Control"))
    }

    @Test
    fun formPostKeepsParametersInBodyAndSignsPost() {
        val connection = Connection()
        val url = arrayOf("")
        val client = UsosClient(credentials(), {}) { address ->
            url[0] = address
            connection
        }
        client.explore(metadata("required", "required"), mapOf("fields" to "name|id"), "POST", null, null)
        assertFalse(url[0].contains("?"))
        assertEquals("POST", connection.requestMethod)
        assertTrue(connection.sent.toString("UTF-8").contains("fields=name%7Cid"))
        assertTrue(connection.sent.toString("UTF-8").contains("oauth_token=private-token"))
        assertTrue(connection.disconnected)
    }

    @Test
    fun multipartCredentialsUseAuthorizationHeaderInsteadOfUrlOrBody() {
        val connection = Connection()
        val url = arrayOf("")
        val credentials = credentials().put("secret", "consumer&secret").put("tokenSecret", "token/secret")
        val client = UsosClient(credentials, {}) { address ->
            url[0] = address
            connection
        }
        client.requestData(
            "POST", "services/blobbox/put", mapOf("read_access" to "consumer"), true,
            "private-token", "token/secret", "test-file".toByteArray(StandardCharsets.UTF_8), "upload.txt"
        )
        assertEquals(UsosClient.BASE + "services/blobbox/put?read_access=consumer", url[0])
        val header = connection.getRequestProperty("Authorization")
        assertTrue(header.startsWith("OAuth "))
        assertTrue(header.contains("oauth_signature_method=\"PLAINTEXT\""))
        assertTrue(header.contains("oauth_token=\"private-token\""))
        val signature = OAuth.encode("consumer&secret") + "&" + OAuth.encode("token/secret")
        assertTrue(header.contains("oauth_signature=\"" + OAuth.encode(signature) + "\""))
        assertTrue(connection.sent.toString("UTF-8").contains("test-file"))
        assertFalse(connection.sent.toString("UTF-8").contains("oauth_"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
    }

    @Test
    fun unsignedMultipartDoesNotAcquireAnAuthorizationHeader() {
        val connection = Connection()
        val client = UsosClient(credentials(), {}) { address ->
            assertEquals(UsosClient.BASE + "services/blobbox/put_unsigned", address)
            connection
        }
        client.requestData("POST", "services/blobbox/put_unsigned", emptyMap(), false, "", "", byteArrayOf(1), "upload.bin")
        assertNull(connection.getRequestProperty("Authorization"))
    }

    @Test
    fun formErrorsPreserveRetryInformationAndHideRawBody() {
        val connection = Connection()
        connection.status = 429
        connection.body = "oauth_problem=rate_limit&oauth_token=private-token"
        val client = UsosClient(credentials(), {}) { connection }
        val before = System.currentTimeMillis()
        try {
            client.get("services/apisrv/installation", emptyMap())
            fail("Expected API failure")
        } catch (failure: UsosClient.ApiException) {
            assertTrue(failure.retryable)
            assertFalse(failure.authorization)
            assertTrue(failure.retryAt >= before + 60000)
            assertFalse(failure.message.contains("private-token"))
        }
        assertTrue(connection.disconnected)
    }

    @Test
    fun rejectedLocalRequestsNeverOpenAConnection() {
        val client = UsosClient(credentials(), {}) { throw AssertionError("Network must not be used") }
        try {
            client.explore(metadata("required", "required"), mapOf("oauth_token" to "injected"), "GET", null, null)
            fail()
        } catch (expected: IllegalArgumentException) {
        }
        try {
            client.explore(metadata("required", "required"), emptyMap(), "POST", ByteArray(1), "file.bin")
            fail()
        } catch (expected: IllegalArgumentException) {
        }
        try {
            client.request("DELETE", "services/apisrv/installation", emptyMap(), false, "", "")
            fail()
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun completeSnapshotAcceptsEmptyGradeCollectionsAndLoadsEveryWeek() {
        val pages = AtomicInteger()
        val client = UsosClient(credentials().put("scopes", "grades|studies|payments"), {}) { address ->
            val connection = Connection()
            when {
                address.contains("courses/user_ects_points") -> connection.body = "{}"
                address.contains("courses/user") -> connection.body = "{\"course_editions\":{\"2026Z\":[{\"course_id\":\"FIN\",\"course_name\":{\"pl\":\"Finanse\"}}]}}"
                address.contains("grades/terms2") -> connection.body = "{\"2026Z\":{\"FIN\":{\"course_grades\":null,\"course_units_grades\":{}}}}"
                address.contains("tt/student") -> {
                    connection.body = "[]"
                    pages.incrementAndGet()
                }
                address.contains("services/users/user?") -> connection.body = "{\"id\":\"42\"}"
                address.contains("theses/user") -> connection.body = "{\"authored_theses\":[]}"
                address.contains("terms/terms") || address.contains("payments/") || address.contains("news/search") -> connection.body = "[]"
                address.contains("crstests/participant2") -> connection.body = "[{\"course_edition\": {\"course\": {\"id\": \"FIN\", \"name\": {\"pl\": \"Finanse\"}}, \"term\": {\"id\": \"2026Z\"}}, \"root\": {\"id\": \"root\", \"type\": \"root\", \"subnodes_deep\": [{\"id\": \"t1\", \"name\": {\"pl\": \"Kolokwium 1\"}, \"type\": \"task\", \"task_node_details\": {\"points_max\": 20, \"students_points\": {\"points\": 18, \"last_changed\": \"2026-10-10\"}}}]}}]"
                else -> throw AssertionError("Unexpected request: $address")
            }
            connection
        }
        val snapshot = client.snapshot()
        assertTrue(snapshot.records.isEmpty())
        assertEquals(1, snapshot.courses.size)
        assertEquals("Finanse", snapshot.courses[0].title)
        assertEquals("2026Z", snapshot.courses[0].term)
        assertEquals(8, pages.get())
    }

    @Test
    fun completeSnapshotParsesEctsAndAttachesToGradeRecord() {
        val client = UsosClient(credentials().put("scopes", "grades|studies|payments"), {}) { address ->
            val connection = Connection()
            when {
                address.contains("courses/user_ects_points") -> connection.body = "{\"2026Z\":{\"FIN\":\"5.0\"}}"
                address.contains("courses/user") -> connection.body = "{\"course_editions\":{\"2026Z\":[{\"course_id\":\"FIN\",\"course_name\":{\"pl\":\"Finanse\"}}]}}"
                address.contains("grades/terms2") -> connection.body = "{\"2026Z\":{\"FIN\":{\"course_grades\":[{\"1\":{\"exam_id\":10,\"exam_session_number\":1,\"value_symbol\":\"4.5\",\"date_modified\":\"2026-10-14 12:00:00\",\"comment\":null}}],\"course_units_grades\":{}}}}"
                address.contains("tt/student") -> connection.body = "[]"
                address.contains("services/users/user?") -> connection.body = "{\"id\":\"42\"}"
                address.contains("theses/user") -> connection.body = "{\"authored_theses\":[]}"
                address.contains("terms/terms") || address.contains("payments/") || address.contains("news/search") -> connection.body = "[]"
                address.contains("crstests/participant2") -> connection.body = "[{\"course_edition\": {\"course\": {\"id\": \"FIN\", \"name\": {\"pl\": \"Finanse\"}}, \"term\": {\"id\": \"2026Z\"}}, \"root\": {\"id\": \"root\", \"type\": \"root\", \"subnodes_deep\": [{\"id\": \"t1\", \"name\": {\"pl\": \"Kolokwium 1\"}, \"type\": \"task\", \"task_node_details\": {\"points_max\": 20, \"students_points\": {\"points\": 18, \"last_changed\": \"2026-10-10\"}}}]}}]"
                else -> throw AssertionError("Unexpected request: $address")
            }
            connection
        }
        val snapshot = client.snapshot()
        assertEquals(1, snapshot.records.size)
        val grade = snapshot.records["grade:10:1"]
        assertNotNull(grade)
        assertEquals(5.0, grade!!.ects, 0.0001)
    }

    @Test
    fun completeSnapshotLoadsTermsPaymentsThesesAndTests() {
        val client = UsosClient(credentials(), {}) { address ->
            val connection = Connection()
            when {
                address.contains("courses/user_ects_points") -> connection.body = "{}"
                address.contains("courses/user") -> connection.body = "{\"course_editions\":{\"2026Z\":[{\"course_id\":\"FIN\",\"course_name\":{\"pl\":\"Finanse\"}}]}}"
                address.contains("terms/terms") -> connection.body = "{\"2026Z\":{\"name\":{\"pl\":\"Semestr zimowy\"},\"start_date\":\"2026-10-01\",\"end_date\":\"2027-02-15\"}}"
                address.contains("payments/user_accounts") -> connection.body = "[{\"account_number\":\"PL999\",\"name\":\"Subkonto czesnego\",\"currency\":\"PLN\"}]"
                address.contains("payments/user_payments") -> connection.body = "[{\"id\":\"p1\",\"total_amount\":850.0,\"currency\":\"PLN\",\"description\":{\"pl\":\"Czesne\"},\"state\":\"unsettled\",\"payment_deadline\":\"2026-10-31\"}]"
                address.contains("services/users/user?") -> connection.body = "{\"id\":\"42\"}"
                address.contains("theses/user") -> connection.body = "{\"authored_theses\":[{\"title\":\"System wykrywania anomalii\"}]}"
                address.contains("crstests/participant2") -> connection.body = "[{\"course_edition\":{\"course\":{\"id\":\"FIN\",\"name\":{\"pl\":\"Finanse\"}},\"term\":{\"id\":\"2026Z\"}},\"root\":{\"id\":\"root\",\"type\":\"root\",\"visible_for_students\":true,\"subnodes_deep\":[{\"id\":\"t1\",\"parent_id\":\"root\",\"name\":{\"pl\":\"Kolokwium 1\"},\"type\":\"task\",\"visible_for_students\":true}]}}]"
                address.contains("crstests/node2") -> connection.body = "{\"id\":\"t1\",\"parent_id\":\"root\",\"type\":\"task\",\"visible_for_students\":true,\"task_node_details\":{\"points_max\":20,\"students_points\":{\"points\":18,\"last_changed\":\"2026-10-10\"}}}"
                address.contains("news/search") -> connection.body = "{\"items\":[{\"article\":{\"id\":\"n1\",\"title\":{\"pl\":\"Inauguracja roku\"},\"publication_date\":\"2026-10-01\"}}]}"
                address.contains("grades/terms2") -> connection.body = "{\"2026Z\":{\"FIN\":{\"course_grades\":[],\"course_units_grades\":{}}}}"
                address.contains("tt/student") -> connection.body = "[]"
                else -> throw AssertionError("Unexpected request: $address")
            }
            connection
        }
        val snapshot = client.snapshot()
        assertEquals(1, snapshot.terms.size)
        assertEquals("Semestr zimowy", snapshot.terms["2026Z"]!!.name)
        assertNotNull(snapshot.payments)
        assertEquals("PL999", snapshot.payments!!.accounts[0].bankAccountNumber)
        assertNotNull(snapshot.thesis)
        assertEquals("System wykrywania anomalii", snapshot.thesis!!.title)
        assertEquals(1, snapshot.tests.size)
        assertEquals("Kolokwium 1", snapshot.tests[0].testName)
        assertEquals("2026Z", snapshot.tests[0].term)
        assertEquals(18.0, snapshot.tests[0].points, 0.001)
        assertEquals(1, snapshot.news.size)
        assertEquals("Inauguracja roku", snapshot.news[0].title)
    }

    @Test
    fun malformedGradesReportTheirStageWithoutExposingResponseBodies() {
        val client = UsosClient(credentials(), {}) { address ->
            val connection = Connection()
            connection.body = when {
                address.contains("courses/user_ects_points") -> "{}"
                address.contains("courses/user") -> "{\"course_editions\":{\"2026Z\":[]}}"
                else -> "private-response"
            }
            connection
        }
        try {
            client.snapshot()
            fail("Expected invalid grades")
        } catch (failure: UsosClient.SyncException) {
            assertTrue(Repository.message(failure).contains("grades/terms2"))
            assertFalse(Repository.message(failure).contains("private-response"))
            assertTrue(Repository.cause(failure) is JSONException)
        }
    }

    @Test
    fun wrappedAuthorizationErrorsRetainTheirClassification() {
        val connection = Connection()
        connection.status = 401
        connection.body = "oauth_problem=token_expired"
        val client = UsosClient(credentials(), {}) { connection }
        try {
            client.snapshot()
            fail("Expected expired session")
        } catch (failure: UsosClient.SyncException) {
            val cause = Repository.cause(failure) as UsosClient.ApiException
            assertTrue(cause.authorization)
            assertFalse(cause.retryable)
            assertTrue(Repository.message(failure).contains("services/courses/user"))
        }
    }

    @Test
    fun paymentScopeMissingIsCapturedGracefullyInSnapshot() {
        val client = UsosClient(credentials().put("scopes", "grades|studies|payments"), {}) { address ->
            val connection = Connection()
            when {
                address.contains("courses/user_ects_points") -> connection.body = "{}"
                address.contains("courses/user") -> connection.body = "{\"course_editions\":{\"2026Z\":[{\"course_id\":\"FIN\",\"course_name\":{\"pl\":\"Finanse\"}}]}}"
                address.contains("terms/terms") -> connection.body = "{\"2026Z\":{\"name\":{\"pl\":\"Semestr zimowy\"},\"start_date\":\"2026-10-01\",\"end_date\":\"2027-02-15\"}}"
                address.contains("payments/") -> {
                    connection.status = 403
                    connection.body = "{\"message\":\"User has not granted access to scope 'payments'\"}"
                }
                address.contains("theses/user") || address.contains("news/search") -> connection.body = "[]"
                address.contains("crstests/participant2") -> connection.body = "[{\"course_edition\": {\"course\": {\"id\": \"FIN\", \"name\": {\"pl\": \"Finanse\"}}, \"term\": {\"id\": \"2026Z\"}}, \"root\": {\"id\": \"root\", \"type\": \"root\", \"subnodes_deep\": [{\"id\": \"t1\", \"name\": {\"pl\": \"Kolokwium 1\"}, \"type\": \"task\", \"task_node_details\": {\"points_max\": 20, \"students_points\": {\"points\": 18, \"last_changed\": \"2026-10-10\"}}}]}}]"
                address.contains("grades/terms2") -> connection.body = "{\"2026Z\":{\"FIN\":{\"course_grades\":[],\"course_units_grades\":{}}}}"
                address.contains("tt/student") -> connection.body = "[]"
            }
            connection
        }
        val snapshot = client.snapshot()
        assertNotNull(snapshot.payments)
        assertTrue(snapshot.payments!!.scopeMissing)
        assertTrue(snapshot.payments!!.statusNotice.contains("payments"))
    }

    @Test
    fun beginSanitizesDirtyScopesWithoutEmptyTokens() {
        val captured = arrayOf("")
        val client = UsosClient(credentials(), {}) { address ->
            captured[0] = address
            val c = Connection()
            c.body = "oauth_token=tok1&oauth_token_secret=sec1&oauth_callback_confirmed=true"
            c
        }
        val res = client.begin(" grades | | studies||offline_access| ")
        assertEquals("tok1", res["oauth_token"])
        assertTrue(captured[0].contains("scopes=grades%7Cstudies%7Coffline_access"))
        assertFalse(captured[0].contains("%7C%7C"))
    }

    @Test
    fun paymentHttpFailuresAbortSnapshotWithTheirRetryAndAuthClassification() {
        for (endpoint in listOf("payments/user_accounts", "payments/user_payments")) {
            for (status in intArrayOf(401, 429, 503)) {
                val client = paymentFailureClient(
                    endpoint,
                    status,
                    if (status == 401) "oauth_problem=token_expired" else "oauth_problem=api_error",
                    false
                )
                try {
                    client.snapshot()
                    fail("Failed payment request must not produce an empty snapshot")
                } catch (failure: UsosClient.SyncException) {
                    val cause = Repository.cause(failure) as UsosClient.ApiException
                    assertEquals(status, cause.status)
                    assertTrue(failure.stage.contains(endpoint))
                    assertEquals(status == 401, cause.authorization)
                    assertEquals(status != 401, cause.retryable)
                }
            }
        }
    }

    @Test
    fun paymentTransportAndMalformedResponseFailuresAbortSnapshot() {
        for (endpoint in listOf("payments/user_accounts", "payments/user_payments")) {
            try {
                paymentFailureClient(endpoint, 200, "", true).snapshot()
                fail()
            } catch (failure: UsosClient.SyncException) {
                assertTrue(Repository.cause(failure) is SocketTimeoutException)
                assertTrue(failure.stage.contains(endpoint))
            }
            try {
                paymentFailureClient(endpoint, 200, "{", false).snapshot()
                fail()
            } catch (failure: UsosClient.SyncException) {
                assertTrue(Repository.cause(failure) is JSONException)
            }
        }
    }

    private fun paymentFailureClient(endpoint: String, status: Int, body: String, timeout: Boolean): UsosClient =
        UsosClient(credentials(), {}) { address ->
            val connection = Connection()
            if (address.contains(endpoint)) {
                if (timeout) throw SocketTimeoutException("Simulated payment timeout")
                connection.status = status
                connection.body = body
            } else if (address.contains("courses/user_ects_points")) {
                connection.body = "{}"
            } else if (address.contains("courses/user")) {
                connection.body = "{\"course_editions\":{}}"
            } else {
                connection.body = "[]"
            }
            connection
        }

    @Test
    fun optionalModulesHonorSessionExpiryAndCooldownsBeforeMakingMoreRequests() {
        for (endpoint in listOf("courses/user_ects_points", "terms/terms", "theses/user", "crstests/participant2", "news/search")) {
            for (status in intArrayOf(401, 429, 503)) {
                val failed = booleanArrayOf(false)
                val client = UsosClient(credentials(), {}) { address ->
                    assertFalse("Continued requests after optional-module failure", failed[0])
                    val connection = snapshotConnection(address)
                    if (address.contains(endpoint)) {
                        failed[0] = true
                        connection.status = status
                        connection.body = if (status == 401) "oauth_problem=token_expired" else "{}"
                    }
                    connection
                }
                try {
                    client.snapshot()
                    fail("Optional-module failures must preserve the previous snapshot")
                } catch (failure: UsosClient.SyncException) {
                    val cause = Repository.cause(failure) as UsosClient.ApiException
                    assertEquals(status, cause.status)
                    assertEquals("services/$endpoint", cause.endpoint)
                    assertEquals(status == 401, cause.authorization)
                    assertEquals(status != 401, cause.retryable)
                }
            }
        }
    }

    @Test
    fun unavailableOptionalModulesStillAllowCoreSnapshot() {
        val client = UsosClient(credentials(), {}) { address ->
            val connection = snapshotConnection(address)
            if (address.contains("courses/user_ects_points") || address.contains("terms/terms") ||
                address.contains("theses/user") || address.contains("crstests/participant2") || address.contains("news/search")
            ) {
                connection.status = 403
                connection.body = "{}"
            }
            connection
        }
        assertTrue(client.snapshot().records.isEmpty())
    }

    private fun snapshotConnection(address: String): Connection {
        val connection = Connection()
        connection.body = when {
            address.contains("services/users/user?") -> "{\"id\":\"42\"}"
            address.contains("services/theses/user?") -> "{\"authored_theses\":[]}"
            address.contains("courses/user_ects_points") -> "{}"
            address.contains("courses/user") -> "{\"course_editions\":{\"2026Z\":[]}}"
            address.contains("grades/terms2") -> "{\"2026Z\":{}}"
            else -> "[]"
        }
        return connection
    }

    @Test
    fun finishStripsWhitespaceFromPinVerifier() {
        val captured = arrayOf("")
        val client = UsosClient(credentials(), {}) { address ->
            captured[0] = address
            val c = Connection()
            c.body = "oauth_token=accTok&oauth_token_secret=accSec"
            c
        }
        val state = JSONObject().put("requestToken", "reqT").put("requestSecret", "reqS")
        val res = client.finish(state, " 1234 5678 \n")
        assertEquals("accTok", res["oauth_token"])
        assertTrue(captured[0].contains("oauth_verifier=12345678"))
    }
}
