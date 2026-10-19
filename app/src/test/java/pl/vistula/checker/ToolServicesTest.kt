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
        .put("scopes", "grades|studies|payments|adm_documents").put("accountSession", "fixture-session")

    private class Store(var state: JSONObject) : StateRepository {
        override fun read() = JSONObject(state.toString())
        override fun update(operation: StateRepository.Update) = operation.apply(state)
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
}
