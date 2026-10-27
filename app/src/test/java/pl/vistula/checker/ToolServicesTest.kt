package pl.vistula.checker

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ToolServicesTest {
    private class Connection(val body: String, val status: Int = 200) : HttpURLConnection(URL(UsosClient.BASE)) {
        val sent = ByteArrayOutputStream()
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = "application/json"
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getErrorStream() = inputStream
        override fun getOutputStream() = sent
    }

    private fun credentials() = JSONObject().put("key", "fixture-key").put("secret", "fixture-secret")
        .put("token", "fixture-token").put("tokenSecret", "fixture-token-secret")
        .put("scopes", "grades|studies|payments|adm_documents|cards").put("accountSession", "fixture-session")

    private class Store(var state: JSONObject) : StateRepository {
        override fun read() = JSONObject(state.toString())
        override fun update(operation: StateRepository.Update) = operation.apply(state)
    }

    @Test fun lecturerCampusUsesEmploymentRatherThanStudentProgrammes() {
        val state = credentials().put("staffStatus", 2).put("studentStatusCode", 0)
        val client = UsosClient(state, {}) { address ->
            assertFalse(address.contains("progs/student"))
            when {
                address.contains("employment_positions") -> Connection("""[{"faculty":{"id":"O0000"}}]""")
                else -> Connection("""{"id":"O0000","name":{"pl":"Filia"},"email":"office@example.org"}""")
            }
        }
        assertEquals("O0000", ToolServiceData.fetch(client, "affiliation", "", state).getString("detected"))
    }

    @Test fun teachingCoursesKeepAllTranslationsAndDropUnrequestedFields() {
        val state = credentials().put("staffStatus", 2)
        val client = UsosClient(state, {}) { address ->
            assertTrue(address.contains("services/users/user?"))
            Connection("""{"course_editions_conducted":[{"id":"1","course":{"id":"C","name":{"pl":"Analiza","en":"Analysis"}},"term":{"id":"Z","name":{"en":"Winter"}},"private":"discard"}]}""")
        }
        val data = ToolServiceData.fetch(client, "teaching", "", state)
        val course = data.getJSONArray("courses").getJSONObject(0)
        assertEquals("Analysis", course.getJSONObject("name").getString("en"))
        assertFalse(data.toString().contains("discard"))
    }

    @Test fun activationAcceptsBothReadyStatusesAndRequestsSecretsOnlyOnDemand() {
        for (status in listOf("ready", "ready_to_receive")) {
            var calls = 0
            val client = UsosClient(credentials(), {}) { address ->
                calls++
                assertTrue(address.contains("/mcard_order2?"))
                assertTrue(address.contains("order_id=issued"))
                assertTrue(address.contains("token%7Cdistribution_pass"))
                Connection("""{"id":"issued","status":"$status","token":"fixture-activation","distribution_pass":"fixture-password"}""")
            }
            val data = MobileCardActions.activation(client, "issued")
            assertEquals("fixture-activation", data.getString("token"))
            assertEquals("fixture-password", data.getString("distribution_pass"))
            assertEquals(1, calls)
            assertFalse(pl.vistula.checker.core.StudentServices.safeMobileCard(JSONObject().put("latest_order", data)).toString().contains("fixture-activation"))
        }
    }

    @Test fun activationRejectsWrongOrderMissingCodesAndNonReadyStatuses() {
        for (body in listOf(
            """{"id":"other","status":"ready","token":"t","distribution_pass":"p"}""",
            """{"id":"issued","status":"ready","token":null,"distribution_pass":"p"}""",
            """{"id":"issued","status":"in_progress","token":"t","distribution_pass":"p"}"""
        )) {
            val client = UsosClient(credentials(), {}) { Connection(body) }
            assertThrows(IllegalStateException::class.java) { MobileCardActions.activation(client, "issued") }
        }
    }

    @Test fun cardsRefreshKeepsMobileAndPhysicalDataWithoutActivationSecrets() {
        val store = Store(credentials().put("scopes", "cards"))
        var calls = 0
        StudentServiceLoader.refresh(store, "cards", SharedRepository.ClientFactory { state ->
            UsosClient(state, {}) { address ->
                calls++
                assertFalse(address.contains("distribution_pass"))
                if (address.contains("services/cards/mcard2?")) Connection("""{"why_cannot_order":null,"why_cannot_revoke":null,"latest_order":null}""")
                else {
                    assertTrue(address.contains("services/cards/user?"))
                    Connection("""[{"id":"1","student_number":"42","expiration_date":"2027-03-31","card_key":"fixture-private"}]""")
                }
            }
        })
        assertEquals(2, calls)
        assertTrue(store.state.getJSONObject("studentServices").getJSONObject("mcard").has("data"))
        val physical = store.state.getJSONObject("studentServices").getJSONObject("physicalCards").getJSONArray("data")
        assertEquals("42", physical.getJSONObject(0).getString("student_number"))
        assertFalse(physical.toString().contains("fixture-private"))
    }

    @Test fun revokingAnOldOrderNeverSendsAPost() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("/mcard2?"))
            Connection("""{"why_cannot_revoke":null,"latest_order":{"id":"new","status":"received"}}""")
        }
        assertThrows(IllegalStateException::class.java) { MobileCardActions.perform(client, credentials(), false, "old") }
    }

    @Test fun cardActionsRecheckAccountAndSendExactlyOnce() {
        var writes = 0
        var checked = false
        val client = UsosClient(credentials(), {}) { address ->
            if (address.contains("/mcard2?")) Connection("""{"why_cannot_order":null}""")
            else {
                assertTrue(checked)
                assertTrue(address.endsWith("/make_mcard_order"))
                writes++
                Connection("""{"success":true}""")
            }
        }
        MobileCardActions.perform(client, credentials(), true, "") { checked = true }
        assertEquals(1, writes)
    }

    @Test fun invalidEligibilityCannotEnableAnOrder() {
        for (value in listOf("false", "123", "[]", "{\"en\":123}")) {
            val client = UsosClient(credentials(), {}) { address ->
                assertTrue(address.contains("/mcard2?"))
                Connection("{\"why_cannot_order\":$value}")
            }
            assertThrows(IllegalStateException::class.java) { MobileCardActions.perform(client, credentials(), true, "") }
        }
    }

    @Test fun manualCampusGetsContactsWithoutAnActiveProgramme() {
        val client = UsosClient(credentials(), {}) { address ->
            when {
                address.contains("/progs/student?") -> Connection("[]")
                else -> {
                    assertTrue(address.contains("/fac/faculty?"))
                    assertTrue(address.contains("fac_id=O0000"))
                    Connection("""{"id":"O0000","name":{"pl":"Filia","en":"Branch"},"email":"office@example.org","phone_numbers":["+48 123"]}""")
                }
            }
        }
        val result = ToolServiceData.fetch(client, "affiliation", "O0000")
        assertEquals("", result.getString("detected"))
        val unit = result.getJSONArray("units").getJSONObject(0)
        assertEquals("office@example.org", unit.getString("email"))
        assertEquals("+48 123", unit.getJSONArray("phones").getString(0))
    }

    @Test fun teachingToolsRequireVerifiedLecturerRole() {
        val client = UsosClient(credentials(), {}) { throw AssertionError("No request for non-lecturers") }
        assertThrows(IllegalStateException::class.java) { ToolServiceData.fetch(client, "teaching") }
    }

    @Test fun paymentRefreshNeedsOnlyTwoRequestsAndPreservesOtherCaches() {
        val store = Store(credentials().put("studentServices", JSONObject().put("documents", JSONObject().put("data", "keep"))))
        val calls = mutableListOf<String>()
        StudentServiceLoader.refresh(store, "payments", SharedRepository.ClientFactory { state ->
            UsosClient(state, {}) { address ->
                calls.add(address.substringBefore('?'))
                Connection("[]")
            }
        })
        assertEquals(listOf(UsosClient.BASE + "services/payments/user_accounts", UsosClient.BASE + "services/payments/user_payments"), calls)
        assertEquals("keep", store.state.getJSONObject("studentServices").getJSONObject("documents").getString("data"))
        assertTrue(store.state.getJSONObject("studentServices").getJSONObject("payments").getJSONObject("data").has("uk"))
    }

    @Test fun malformedPaymentResponseKeepsPreviousDataAndTimestamp() {
        val previous = JSONObject().put("data", JSONObject().put("pl", "keep")).put("fetchedAt", 123L)
        val store = Store(credentials().put("studentServices", JSONObject().put("payments", previous)))
        StudentServiceLoader.refresh(store, "payments", SharedRepository.ClientFactory { state ->
            UsosClient(state, {}) { Connection("null") }
        })
        val entry = store.state.getJSONObject("studentServices").getJSONObject("payments")
        assertEquals(123L, entry.getLong("fetchedAt"))
        assertEquals("keep", entry.getJSONObject("data").getString("pl"))
        assertEquals("format", entry.getString("errorKind"))
    }

    @Test fun accountSwitchRejectsLateServiceWrite() {
        val store = Store(credentials())
        assertThrows(IllegalStateException::class.java) {
            StudentServiceLoader.refresh(store, "payments", SharedRepository.ClientFactory { state ->
                UsosClient(state, {}) { store.state = credentials().put("accountSession", "another-session"); Connection("[]") }
            })
        }
        assertFalse(store.state.has("studentServices"))
    }

    @Test fun branchNewsCannotBypassAccountOrAuthorizationChecks() {
        for (state in listOf(JSONObject().put("campusOverride", "O0000"),
            credentials().put("campusOverride", "O0000").put("authorizationError", true))) {
            assertThrows(IllegalStateException::class.java) {
                StudentServiceLoader.refresh(Store(state), "news", SharedRepository.ClientFactory {
                    throw AssertionError("Unauthorized news request")
                })
            }
        }
    }

    @Test fun branchNewsUsesTheExactFacultySelector() {
        val state = credentials()
        var url = ""
        val client = UsosClient(state, {}) { address ->
            url = address
            Connection("""{"items":[],"total":0,"next_page":false}""")
        }
        val data = ToolServiceData.fetch(client, "news", "O0000")
        assertTrue(url.contains("facpattern=%2BO0000"))
        assertEquals("O0000", data.getString("campus"))
        ToolServiceData.fetch(client, "news", "0000")
        assertTrue(url.contains("facpattern=%3D0000"))
        assertFalse(url.contains("facpattern=%2B0000"))
    }

    @Test fun publicNewsWorksWithoutAnAccountOrCoreSynchronization() {
        val store = Store(JSONObject().put("retryAt", Long.MAX_VALUE).put("campusOverride", "0000"))
        StudentServiceLoader.refresh(store, "news", SharedRepository.ClientFactory { state -> UsosClient(state) },
            { JSONObject().put("source", "university").put("pl", JSONArray()).put("en", JSONArray()).put("uk", JSONArray()) })
        assertEquals("university", store.state.getJSONObject("studentServices").getJSONObject("news").getJSONObject("data").getString("source"))
    }

    @Test fun officialNewsNeverSendsOAuthAndRejectsForeignArticleLinks() {
        val replies = mutableListOf<Connection>()
        val result = UniversityNews.load { address ->
            assertTrue(address.startsWith("https://vistula.edu.pl/wp-json/"))
            Connection("""[{"id":1,"title":{"rendered":"A"},"date":"2026-10-19","link":"https://vistula.edu.pl/news-a"},{"id":2,"title":{"rendered":"B"},"link":"https://evil.example/"}]""").also { replies.add(it) }
        }
        assertEquals(2, replies.size)
        assertEquals(1, result.getJSONArray("pl").length())
        assertEquals(1, result.getJSONArray("uk").length())
        for (connection in replies) {
            assertTrue(connection.closed)
            assertNull(connection.getRequestProperty("Authorization"))
            assertFalse(connection.instanceFollowRedirects)
        }
    }

    @Test fun officialNewsDoesNotFollowRedirectsOrTreatBadResponsesAsEmpty() {
        val redirect = Connection("{}", 302)
        assertThrows(java.io.IOException::class.java) { UniversityNews.load { redirect } }
        assertTrue(redirect.closed)
        assertThrows(JSONException::class.java) { UniversityNews.load { Connection("{}") } }
    }

    @Test fun receiptCodeErrorIsReturnedWithoutRetryOrFalseSuccess() {
        var writes = 0
        val state = credentials()
        val client = UsosClient(state, {}) { address ->
            if (address.contains("/document?")) Connection("""{"id":"1","actions":["RECEIVE_DOCUMENT"]}""")
            else { writes++; Connection("""{"status":"Err","message":{"en":"Incorrect code"}}""") }
        }
        val result = StudentServiceActions.receive(client, state, "1", "123456")
        assertEquals("Err", result.getString("status"))
        assertEquals(1, writes)
    }

    @Test fun unavailableDocumentActionCannotSendAReceipt() {
        val state = credentials()
        val client = UsosClient(state, {}) { address ->
            assertTrue(address.contains("/document?"))
            Connection("""{"id":"1","actions":[]}""")
        }
        assertThrows(IllegalStateException::class.java) { StudentServiceActions.receive(client, state, "1", "123456") }
    }

    @Test fun changedInstallmentTermsCannotBeConfirmedUsingOldAmounts() {
        val state = credentials()
        val client = UsosClient(state, {}) { address ->
            assertTrue(address.contains("/payment?"))
            Connection("""{"id":"1","available_installment_plans":[{"id":"2","currency":"PLN","installments":[{"amount":200,"payment_date":"2026-11-01"}]}]}""")
        }
        val shown = JSONObject("""{"id":"2","currency":"PLN","installments":[{"amount":100,"payment_date":"2026-11-01"}]}""")
        assertThrows(IllegalStateException::class.java) { StudentServiceActions.choosePlan(client, state, "1", "payment", "2", shown) }
    }

    @Test fun eligibleInstallmentChoiceIsPostedOnce() {
        val state = credentials()
        var writes = 0
        val client = UsosClient(state, {}) { address ->
            if (address.contains("/payment?")) Connection("""{"id":"1","available_installment_plans":[{"id":"2","currency":"PLN","installments":[{"amount":100,"payment_date":"2026-11-01"}]}]}""")
            else { writes++; Connection("{}") }
        }
        val shown = JSONObject("""{"id":"2","currency":"PLN","installments":[{"amount":100,"payment_date":"2026-11-01"}]}""")
        StudentServiceActions.choosePlan(client, state, "1", "payment", "2", shown)
        assertEquals(1, writes)
    }

    @Test fun changedFallbackCurrencyPreventsChoosingPlan() {
        var writes = 0
        val client = UsosClient(credentials(), {}) { address ->
            if (address.contains("/payment?")) Connection("""{"id":"1","currency":"EUR","available_installment_plans":[{"id":"2","installments":[{"amount":100,"payment_date":"2026-11-01"}]}]}""")
            else { writes++; Connection("{}") }
        }
        val shown = JSONObject("""{"id":"2","installments":[{"amount":100,"payment_date":"2026-11-01"}]}""")
        assertThrows(IllegalStateException::class.java) { StudentServiceActions.choosePlan(client, credentials(), "1", "payment", "2", shown, "PLN") }
        assertEquals(0, writes)
    }

    @Test fun ambiguousCardWriteResponsesRequireStatusRefresh() {
        for ((body, status) in listOf("{}" to 200, "{}" to 503, "{}" to 408)) {
            var writes = 0
            val client = UsosClient(credentials(), {}) { address ->
                if (address.contains("/mcard2?")) Connection("""{"why_cannot_order":null}""")
                else { writes++; Connection(body, status) }
            }
            assertThrows(MobileCardActions.Uncertain::class.java) { MobileCardActions.perform(client, credentials(), true, "") }
            assertEquals(1, writes)
        }
    }
}
