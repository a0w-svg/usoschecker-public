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

    @Test fun teacherSearchUsesDocumentedResponseAndArguments() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/users/search_current_teachers?"))
            assertFalse(address.contains("fields="))
            Connection("""{"items":[{"user_id":"42","match":"Jan <b>Kowal</b>ski"}],"next_page":true}""")
        }
        val result = ToolServiceData.searchTeachers(client, "Kowal")
        val user = result.getJSONArray("users").getJSONObject(0)
        assertEquals("42", user.getString("id"))
        assertEquals("Jan Kowalski", user.getString("first_name"))
        assertTrue(result.getBoolean("hasMore"))
    }

    @Test fun staffProfileRequestsOnlyDirectoryFieldsAndDropsPrivateData() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/users/user?"))
            assertTrue(address.contains("user_id=42"))
            Connection("""{"id":"42","first_name":"Jan","last_name":"Kowalski","titles":{"before":"dr"},"office_hours":"Tuesday 12:00","room":"A1","pesel":"private"}""")
        }
        val profile = ToolServiceData.fetchStaffProfile(client, "42")
        assertEquals("A1", profile.getString("room"))
        assertEquals("Tuesday 12:00", profile.getString("office_hours"))
        assertEquals("dr", profile.getString("title_before"))
        assertFalse(profile.toString().contains("private"))
        assertThrows(IllegalArgumentException::class.java) { ToolServiceData.fetchStaffProfile(client, "../bad") }
    }

    @Test fun registrationRoundsExposeDatesWithoutExtraFields() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/registrations/course_registration_rounds?"))
            assertTrue(address.contains("registration_id=R1"))
            Connection("""[{"id":"round1","name":{"en":"First round"},"status":"open","start_date":"2026-11-01","end_date":"2026-11-05","private":"discard"}]""")
        }
        val round = ToolServiceData.fetchRegistrationRounds(client, "R1").getJSONObject(0)
        assertEquals("First round", round.getJSONObject("name").getString("en"))
        assertEquals("2026-11-05", round.getString("end_date"))
        assertFalse(round.toString().contains("discard"))
        assertThrows(IllegalArgumentException::class.java) { ToolServiceData.fetchRegistrationRounds(client, "../bad") }
    }

    @Test fun tokenRegistrationsHaveIndependentCacheAndUseStudiesScope() {
        val store = Store(credentials().put("scopes", "studies"))
        StudentServiceLoader.refresh(store, "tokenRegistrations", SharedRepository.ClientFactory { state ->
            UsosClient(state, {}) { address ->
                assertTrue(address.contains("services/registrations/user_token_registrations?"))
                Connection("""[{"id":"T1","description":{"en":"Language registration"},"status":"open","private":"discard"}]""")
            }
        })
        val entry = store.state.getJSONObject("studentServices").getJSONObject("tokenRegistrations")
        val row = entry.getJSONObject("data").getJSONArray("registrations").getJSONObject(0)
        assertEquals("Language registration", row.getJSONObject("description").getString("en"))
        assertFalse(entry.toString().contains("discard"))
        assertEquals("studies", StudentServiceLoader.requiredScope("tokenRegistrations"))
    }

    @Test fun tokenRegistrationDetailsPreserveRoundRulesWithoutPrivateFields() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/registrations/token_registration?"))
            assertTrue(address.contains("id=token%2F1"))
            Connection("""{"id":"token/1","rounds":[{"id":"R1","name":{"en":"Main"},"start_date":"2026-11-01","end_date":"2026-11-05","selection_limit":2,"is_prioritized":true,"is_only_entitled":null,"private":"discard"}]}""")
        }
        val result = ToolServiceData.fetchTokenRegistrationDetails(client, "token/1")
        val round = result.getJSONArray("rounds").getJSONObject(0)
        assertTrue(result.getBoolean("rounds_available"))
        assertEquals(2, round.getInt("selection_limit"))
        assertTrue(round.getBoolean("is_prioritized"))
        assertFalse(round.has("is_only_entitled"))
        assertFalse(result.toString().contains("discard"))
        assertThrows(IllegalArgumentException::class.java) { ToolServiceData.fetchTokenRegistrationDetails(client, "bad\nvalue") }
    }

    @Test fun tokenRegistrationMissingRoundsIsNotReportedAsEmpty() {
        val client = UsosClient(credentials(), {}) { Connection("""{"id":"T1","rounds":null}""") }
        val result = ToolServiceData.fetchTokenRegistrationDetails(client, "T1")
        assertFalse(result.getBoolean("rounds_available"))
        assertEquals(0, result.getJSONArray("rounds").length())
    }

    @Test fun courseSearchUsesDocumentedFieldsAndKeepsApproximateCredits() {
        var calls = 0
        val client = UsosClient(credentials(), {}) { address ->
            calls++
            assertTrue(address.contains("services/courses/search?"))
            assertTrue(address.contains("ects_credits_simplified"))
            assertFalse(address.contains("items%5B"))
            Connection("""{"items":[{"id":"INF-1","name":{"en":"Programming"},"ects_credits_simplified":6.0,"match":"<b>Prog</b>ramming"}],"next_page":true}""")
        }
        val result = ToolServiceData.searchCourses(client, "Prog")
        val course = result.getJSONArray("courses").getJSONObject(0)
        assertEquals("INF-1", course.getString("id"))
        assertEquals("6.0", course.getString("ects"))
        assertEquals("Programming", course.getString("match"))
        assertTrue(result.getBoolean("hasMore"))
        assertEquals(1, calls)
    }

    @Test fun courseDetailsReadDescriptionsWithoutUnrequestedFields() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/courses/course?"))
            assertTrue(address.contains("course_id=INF%2F1"))
            assertTrue(address.contains("learning_outcomes"))
            Connection("""{"id":"INF/1","name":{"en":"Programming"},"ects_credits_simplified":5.0,"description":{"en":"<p>Learn &amp; practice.</p>"},"learning_outcomes":{"en":"Build programs"},"private":"discard"}""")
        }
        val detail = ToolServiceData.fetchCourseDetails(client, "INF/1")
        assertEquals("5.0", detail.getString("ects"))
        assertEquals("Learn & practice.", ToolServiceData.courseText(detail.opt("description"), "en"))
        assertEquals("Build programs", ToolServiceData.courseText(detail.opt("learning_outcomes"), "en"))
        assertEquals("x < 5 > 3", ToolServiceData.courseText("x < 5 > 3", "en"))
        assertFalse(detail.toString().contains("discard"))
        assertThrows(IllegalArgumentException::class.java) { ToolServiceData.fetchCourseDetails(client, "invalid\nvalue") }
    }

    @Test fun programmeDetailsPreserveTranslationsAndDiscardUnrequestedData() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/progs/programme?"))
            assertTrue(address.contains("programme_id=S1%2FINF"))
            Connection("""{"id":"S1/INF","name":{"en":"Computer Science","pl":"Informatyka"},"faculty":{"id":"F1","name":{"en":"Computing"},"private":"discard"},"mode_of_studies":{"en":"Full-time"},"duration":{"en":"3 years"},"private":"discard"}""")
        }
        val data = ToolServiceData.fetchProgrammeDetails(client, "S1/INF")
        assertEquals("Informatyka", data.getJSONObject("name").getString("pl"))
        assertEquals("Computing", data.getJSONObject("faculty_name").getString("en"))
        assertEquals("3 years", data.getJSONObject("duration").getString("en"))
        assertFalse(data.toString().contains("discard"))
        assertThrows(IllegalArgumentException::class.java) { ToolServiceData.fetchProgrammeDetails(client, "bad\nvalue") }
    }

    @Test fun programmeStagesPreserveIdsAndExposeUnsupportedRows() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/progs/student_programme?"))
            assertTrue(address.contains("student_programme_id=SP%2F1"))
            assertFalse(address.contains("user%5B"))
            Connection("""{"id":"SP/1","stages":[{"stage_id":"1Z","private":"discard"},"2L",{"unexpected":"x"}],"private":"discard"}""")
        }
        val data = ToolServiceData.fetchStudentProgrammeStages(client, "SP/1")
        assertTrue(data.getBoolean("stages_available"))
        assertTrue(data.getBoolean("stages_unrecognized"))
        assertEquals("1Z", data.getJSONArray("stages").getJSONObject(0).getString("id"))
        assertEquals("2L", data.getJSONArray("stages").getJSONObject(1).getString("id"))
        assertFalse(data.toString().contains("discard"))
        assertThrows(IllegalArgumentException::class.java) { ToolServiceData.fetchStudentProgrammeStages(client, "bad\nvalue") }
    }

    @Test fun absentStudyStagesAreDifferentFromAnEmptyList() {
        val missing = UsosClient(credentials(), {}) { Connection("""{"id":"P1","stages":null}""") }
        val empty = UsosClient(credentials(), {}) { Connection("""{"id":"P1","stages":[]}""") }
        assertFalse(ToolServiceData.fetchStudentProgrammeStages(missing, "P1").getBoolean("stages_available"))
        assertTrue(ToolServiceData.fetchStudentProgrammeStages(empty, "P1").getBoolean("stages_available"))
    }

    @Test fun studyStageReturnsLocalizedNameOnly() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/progs/stage?"))
            assertTrue(address.contains("id=1Z"))
            Connection("""{"id":"1Z","name":{"en":"First semester"},"private":"discard"}""")
        }
        val stage = ToolServiceData.fetchStudyStage(client, "1Z")
        assertEquals("First semester", stage.getJSONObject("name").getString("en"))
        assertFalse(stage.toString().contains("discard"))
    }

    @Test fun calendarSearchChecksOneMonthBoundaryAndKeepsEvents() {
        var calls = 0
        val client = UsosClient(credentials(), {}) { address ->
            calls++
            assertTrue(address.contains("services/calendar/search?"))
            assertTrue(address.contains("faculty_id=0000"))
            assertTrue(address.contains("start_date=2026-01-31"))
            assertTrue(address.contains("end_date=2026-02-28"))
            Connection("""[{"id":"E1","name":{"en":"Exam session"},"start_date":"2026-02-01","end_date":"2026-02-02","private":"discard"}]""")
        }
        val data = ToolServiceData.searchCalendar(client, "0000", "2026-01-31", "2026-02-28")
        assertEquals("Exam session", data.getJSONArray("events").getJSONObject(0).getJSONObject("name").getString("en"))
        assertFalse(data.toString().contains("discard"))
        assertFalse(ToolServiceData.validCalendarRange("2026-01-31", "2026-03-01"))
        assertFalse(ToolServiceData.validCalendarRange("2026-01-31", "2026-01-31"))
        assertFalse(ToolServiceData.validCalendarRange("bad", "2026-02-28"))
        assertThrows(IllegalArgumentException::class.java) { ToolServiceData.searchCalendar(client, "0000", "2026-01-31", "2026-03-01") }
        assertEquals(1, calls)
    }

    @Test fun calendarEventKeepsFacultyAndDoesNotGuessUnknownDayOff() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/calendar/calendar_event?"))
            assertTrue(address.contains("id=E%2F1"))
            Connection("""{"id":"E/1","name":{"en":"Event"},"faculty":{"name":{"en":"Faculty"},"private":"discard"},"is_day_off":null,"private":"discard"}""")
        }
        val event = ToolServiceData.fetchCalendarEvent(client, "E/1")
        assertEquals("Faculty", event.getJSONObject("faculty_name").getString("en"))
        assertFalse(event.has("is_day_off"))
        assertFalse(event.toString().contains("discard"))
        assertThrows(IllegalArgumentException::class.java) { ToolServiceData.fetchCalendarEvent(client, "bad\nvalue") }
    }

    @Test fun buildingRoomsRetainOnlyDirectoryFields() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/geo/building2?"))
            assertTrue(address.contains("building_id=B1"))
            Connection("""{"id":"B1","rooms":[{"id":"R1","number":"101","capacity":30,"private":"discard"}]}""")
        }
        val rooms = ToolServiceData.fetchBuildingRooms(client, "B1")
        assertEquals("101", rooms.getJSONObject(0).getString("number"))
        assertFalse(rooms.toString().contains("discard"))
        assertThrows(IllegalArgumentException::class.java) { ToolServiceData.fetchBuildingRooms(client, "../bad") }
    }

    @Test fun examDetailsKeepGroupTimesAndExcludeAttendees() {
        val client = UsosClient(credentials(), {}) { address ->
            assertTrue(address.contains("services/exams/exam?"))
            assertTrue(address.contains("id=exam-1"))
            assertFalse(address.contains("attendees"))
            Connection("""{"id":"exam-1","name":{"en":"Final exam"},"registration_start":"2026-12-01","groups":[{"number":1,"exam_start":"2027-01-10 09:00:00","exam_end":"2027-01-10 10:30:00","room":{"id":"R1","number":"101"},"attendees":[{"first_name":"private"}]}]}""")
        }
        val detail = ToolServiceData.fetchExamDetails(client, "exam-1")
        val group = detail.getJSONArray("groups").getJSONObject(0)
        assertEquals("2027-01-10 09:00:00", group.getString("exam_start"))
        assertEquals("101", group.getString("room_number"))
        assertFalse(detail.toString().contains("private"))
        assertThrows(IllegalArgumentException::class.java) { ToolServiceData.fetchExamDetails(client, "../bad") }
    }

    @Test fun registrationCartFailureIsNotReportedAsAnEmptyCart() {
        val client = UsosClient(credentials(), {}) { address ->
            if (address.contains("user_registrations")) Connection("[]")
            else Connection("{}", 500)
        }
        assertThrows(Exception::class.java) { ToolServiceData.fetch(client, "registrations", "", credentials()) }
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
