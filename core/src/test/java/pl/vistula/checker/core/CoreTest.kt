package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CoreTest {
    private fun grade(value: String, time: String): Record =
        Record("grade:1:1", "ocena", "Finanse", value, time, "")

    private fun lesson(id: String, date: String, value: String): Record =
        Record(id, "plan", "Statystyka", value, "", date)

    @Test
    fun oauthMatchesPublishedHmacSha1Example() {
        // OAuth 1.0 specification section 9.1.1, photos.example.net.
        val p = HashMap<String, String>()
        p["file"] = "vacation.jpg"
        p["size"] = "original"
        p["oauth_consumer_key"] = "dpf43f3p2l4k3l03"
        p["oauth_token"] = "nnch734d00sl2jdk"
        p["oauth_nonce"] = "kllo9940pd9333jh"
        p["oauth_timestamp"] = "1191242096"
        p["oauth_signature_method"] = "HMAC-SHA1"
        p["oauth_version"] = "1.0"
        assertEquals(
            "tR3+Ty81lMeYAr/Fid0kMTYa/WM=",
            OAuth.signature("GET", "http://photos.example.net/photos", p, "kd94hf93k423kf44", "pfkkdhi9sl3r4s00")
        )
    }

    @Test
    fun oauthSortsByEncodedKeysAndEscapesUnicode() {
        assertEquals("%C4%85%20%2B%2A~", OAuth.encode("ą +*~"))
        assertEquals("%C4%85=a&z=b", OAuth.query(mapOf("z" to "b", "ą" to "a")))
    }

    @Test
    fun oauthFormDecodesEscapedTokenAndIgnoresExtraFields() {
        val result = OAuth.form("oauth_token=a%2Bb&oauth_token_secret=x%3Dy&extra=true")
        assertEquals("a+b", result["oauth_token"])
        assertEquals("x=y", result["oauth_token_secret"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyTokenIsRejected() {
        OAuth.form("oauth_token=&oauth_token_secret=x")
    }

    @Test
    fun baselineAndUnchangedDownloadDoNotNotify() {
        val s = Snapshot("2026-10-13", "2026-11-10")
        s.add(grade("4", "2026-10-13 10:00:00"))
        assertTrue(Diff.between(null, s, 1, "2026-10-13").isEmpty())
        assertTrue(Diff.between(s, s, 1, "2026-10-13").isEmpty())
    }

    @Test
    fun gradeAndRoomChangeCarryBeforeAfterAndBothTimes() {
        val old = Snapshot("2026-10-13", "2026-11-10")
        val now = Snapshot(old.start, old.end)
        old.add(grade("4", "2026-10-13 10:00:00"))
        now.add(grade("5", "2026-10-13 10:30:00"))
        old.add(lesson("plan:1", "2026-10-14", "09:00 Sala 204"))
        now.add(lesson("plan:1", "2026-10-14", "10:00 Sala 315"))
        val diff = Diff.between(old, now, 42, "2026-10-13")
        assertEquals(2, diff.size)
        assertEquals("4", diff[0].before)
        assertEquals("5", diff[0].after)
        assertEquals(42L, diff[0].detectedAt)
        assertEquals("2026-10-13 10:30:00", diff[0].sourceTime)
        assertEquals("Zmieniono", diff[1].action)
        assertEquals("", diff[1].sourceTime)
    }

    @Test
    fun rollingWindowDoesNotInventScheduleChanges() {
        val old = Snapshot("2026-10-13", "2026-11-10")
        val now = Snapshot("2026-10-14", "2026-11-11")
        old.add(lesson("plan:expired", "2026-10-13", "finished"))
        now.add(lesson("plan:entering", "2026-11-10", "newly in window"))
        assertTrue(Diff.between(old, now, 42, "2026-10-14").isEmpty())
    }

    @Test
    fun additionsAndCancellationsInsideOverlapAreReported() {
        val old = Snapshot("2026-10-13", "2026-11-10")
        val now = Snapshot(old.start, old.end)
        old.add(lesson("plan:cancelled", "2026-10-14", "cancelled"))
        now.add(lesson("plan:added", "2026-10-15", "added"))
        val diff = Diff.between(old, now, 42, "2026-10-13")
        assertEquals(2, diff.size)
        assertEquals("Dodano", diff[0].action)
        assertEquals("Usunięto", diff[1].action)
    }

    @Test
    fun sourceTimeUpdateWithoutValueChangeIsDetected() {
        val old = Snapshot("2026-10-13", "2026-11-10")
        val now = Snapshot(old.start, old.end)
        old.add(grade("4", "2026-10-13 10:00:00"))
        now.add(grade("4", "2026-10-13 10:30:00"))
        assertEquals(1, Diff.between(old, now, 42, "2026-10-13").size)
    }

    @Test
    fun snapshotsRoundTripWithoutLosingDates() {
        val s = Snapshot("2026-10-13", "2026-11-10")
        s.add(grade("4", "2026-10-13 10:00:00"))
        val restored = Snapshot.from(JSONObject(s.json().toString()))
        assertTrue(Diff.between(s, restored, 42, "2026-10-13").isEmpty())
        assertEquals(s.start, restored.start)
    }

    @Test
    fun parsesTerms2IncludingUnitExamsAndNullSessions() {
        val grades = JSONObject(
            """
            {"2026Z":{"FIN":{"course_grades":[{"1":{"exam_id":1,"exam_session_number":1,"value_symbol":"4",
              "date_modified":"2026-10-13 10:00:00","comment":null},"2":null}],
              "course_units_grades":{"20":[{"1":{"exam_id":2,"exam_session_number":1,"value_symbol":"ZAL",
              "date_modified":"2026-10-13 11:00:00","comment":"Ćwiczenia"}}]}}}}
            """.trimIndent()
        )
        val s = Snapshot("2026-10-13", "2026-11-10")
        UsosParser.grades(s, grades, mapOf("FIN" to "Finanse"))
        assertEquals(2, s.records.size)
        assertEquals("Finanse", s.records["grade:1:1"]!!.title)
        assertTrue(s.records["grade:2:1"]!!.value.contains("Ćwiczenia"))
    }

    @Test
    fun timetableUsesStableMeetingIdWhenRoomOrTimeChanges() {
        val activity = JSONObject(
            """
            {"type":"classgroup2","sm_id":99,"start_time":"2026-10-14 09:00:00","end_time":"2026-10-14 10:30:00",
             "name":{"pl":"Statystyka"},"room_number":"204","lecturer_ids":[2,1]}
            """.trimIndent()
        )
        val old = Snapshot("2026-10-13", "2026-11-10")
        val now = Snapshot(old.start, old.end)
        UsosParser.timetable(old, JSONArray().put(activity))
        activity.put("room_number", "315").put("start_time", "2026-10-14 09:30:00").put("lecturer_ids", JSONArray().put(1).put(2))
        UsosParser.timetable(now, JSONArray().put(activity))
        val changes = Diff.between(old, now, 42, "2026-10-13")
        assertEquals(1, changes.size)
        assertEquals("Zmieniono", changes[0].action)
    }

    @Test(expected = JSONException::class)
    fun malformedGradeCannotBecomeAValidSnapshot() {
        UsosParser.grades(Snapshot("2026-10-13", "2026-11-10"), JSONObject("{\"2026Z\":{\"FIN\":{}}}"), emptyMap())
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateTimetableIdRejectsResponse() {
        val activity = JSONObject(
            """
            {"type":"classgroup2","sm_id":99,"start_time":"2026-10-14 09:00:00","end_time":"2026-10-14 10:30:00","name":{"pl":"Statystyka"}}
            """.trimIndent()
        )
        UsosParser.timetable(Snapshot("2026-10-13", "2026-11-10"), JSONArray().put(activity).put(activity))
    }

    @Test
    fun changePrioritizesUsosDateAndPreservesSourceOnDeletion() {
        val gradeOld = grade("4", "2026-10-13 10:00:00")
        val gradeNew = grade("5", "2026-10-13 11:30:00")
        val update = Change(gradeOld, gradeNew, 999999L)
        assertEquals("2026-10-13 11:30:00", update.sourceTime)
        assertEquals("2026-10-13", update.date)
        assertNotEquals(999999L, update.effectiveTimestamp())
        assertTrue(update.effectiveTimestamp() > 0)

        val deletion = Change(gradeNew, null, 999999L)
        assertEquals("Usunięto", deletion.action)
        assertEquals("2026-10-13 11:30:00", deletion.sourceTime)
        assertEquals("2026-10-13", deletion.date)
        assertEquals(update.effectiveTimestamp(), deletion.effectiveTimestamp())

        val lesson = lesson("plan:99", "2026-10-15", "Sala 101")
        val lessonChange = Change(null, lesson, 12345L)
        assertEquals("", lessonChange.sourceTime)
        assertEquals("2026-10-15", lessonChange.date)
        assertEquals(12345L, lessonChange.effectiveTimestamp())

        val roundtrip = Change.from(JSONObject(update.json().toString()))
        assertEquals(update.sourceTime, roundtrip.sourceTime)
        assertEquals(update.date, roundtrip.date)
        assertEquals(update.effectiveTimestamp(), roundtrip.effectiveTimestamp())
    }

    @Test
    fun changeCarriesRichApiMetadataAndDetectsSpecificDiffs() {
        val oldMeta = JSONObject().put("room", "204").put("lecturers", "Anna Kowalska").put("groupNumber", "1")
            .put("startTime", "2026-10-15 09:00:00").put("endTime", "2026-10-15 10:30:00").put("valueDescription", "wykład")
        val newMeta = JSONObject().put("room", "315").put("lecturers", "Jan Kowalski").put("groupNumber", "2")
            .put("startTime", "2026-10-22 09:30:00").put("endTime", "2026-10-22 11:00:00").put("valueDescription", "ćwiczenia")
        val rOld = Record("plan:1", "plan", "Matematyka", "v1", "", "2026-10-15", "", "2026-10-15 09:00:00", "2026-10-15 10:30:00", Double.NaN, oldMeta)
        val rNew = Record("plan:1", "plan", "Matematyka", "v2", "", "2026-10-22", "", "2026-10-22 09:30:00", "2026-10-22 11:00:00", Double.NaN, newMeta)

        val c = Change(rOld, rNew, 1000L)
        assertEquals("204 → 315", c.optMeta("diffRoom"))
        assertEquals("Anna Kowalska → Jan Kowalski", c.optMeta("diffLecturers"))
        assertEquals("1 → 2", c.optMeta("diffGroup"))
        assertEquals("09:00–10:30 → 09:30–11:00", c.optMeta("diffTime"))
        assertEquals("2026-10-15 → 2026-10-22", c.optMeta("diffDate"))
        assertEquals("wykład → ćwiczenia", c.optMeta("diffValueDescription"))
        assertEquals("2026-10-15", c.beforeDate)
        assertEquals("2026-10-22", c.afterDate)
        assertEquals("315", c.getRoom())
        assertEquals("Jan Kowalski", c.getLecturers())
        assertEquals("2", c.getGroupNumber())

        // Test with startTime and ects on Record directly without metadata entries
        val rec1 = Record("plan:2", "plan", "Fizyka", "v1", "", "2026-10-15", "2026Z", "2026-10-15 08:00:00", "2026-10-15 09:30:00", 3.0, JSONObject())
        val rec2 = Record("plan:2", "plan", "Fizyka", "v2", "", "2026-10-15", "2026Z", "2026-10-15 10:00:00", "2026-10-15 11:30:00", 5.0, JSONObject())
        val cDirect = Change(rec1, rec2, 1005L)
        assertEquals("08:00–09:30 → 10:00–11:30", cDirect.optMeta("diffTime"))
        assertEquals("3,0 → 5,0 ECTS", cDirect.optMeta("diffEcts"))
        assertEquals(5.0, cDirect.getEcts(), 0.001)
        assertEquals(3.0, cDirect.getBeforeEcts(), 0.001)
        assertEquals(5.0, cDirect.getAfterEcts(), 0.001)
        assertEquals("2026Z", cDirect.getTerm())

        val gradeJson = JSONObject(
            """
            {"2026Z":{"INF":{"course_grades":[{"1":{"exam_id":501,"exam_session_number":1,"value_symbol":"5",
              "passes":true,"value_description":{"pl":"bardzo dobry"},"counts_into_average":true,
              "date_modified":"2026-10-14 12:00:00","date_acquisition":"2026-10-14 11:45:00",
              "comment":"Egzamin zdany celująco"}}],"course_units_grades":null}}}
            """.trimIndent()
        )
        val s = Snapshot("2026-10-13", "2026-11-10")
        UsosParser.grades(s, gradeJson, mapOf("INF" to "Informatyka"), mapOf("2026Z\u0000INF" to 6.0))
        val gradeRecord = s.records["grade:501:1"]
        assertNotNull(gradeRecord)
        assertEquals(true, gradeRecord!!.metadata.optBoolean("passes"))
        assertEquals("bardzo dobry", gradeRecord.metadata.optString("valueDescription"))
        assertEquals(true, gradeRecord.metadata.optBoolean("countsIntoAverage"))
        assertEquals("Egzamin zdany celująco", gradeRecord.metadata.optString("comment"))
        assertEquals(6.0, gradeRecord.metadata.optDouble("ects"), 0.001)

        val gradeChange = Change(null, gradeRecord, 2000L)
        assertEquals("2026-10-14", gradeChange.date)
        assertEquals(true, gradeChange.getPasses())
        assertEquals(true, gradeChange.getCountsIntoAverage())
        assertEquals("bardzo dobry", gradeChange.optMeta("valueDescription"))
        assertEquals("Egzamin zdany celująco", gradeChange.optMeta("comment"))
        assertEquals(6.0, gradeChange.getEcts(), 0.001)

        val restored = Change.from(JSONObject(gradeChange.json().toString()))
        assertEquals("2026-10-14", restored.date)
        assertEquals(true, restored.getPasses())
        assertEquals("bardzo dobry", restored.optMeta("valueDescription"))
    }

    @Test
    fun parsesPaymentInfoAndCalculatesBalances() {
        val raw = JSONObject(
            """
            {"user_accounts":[{"id":"1","name":{"pl":"Subkonto ogólne"},"currency":"PLN","bank_account_number":"PL12345678901234567890123456","balance":0.0}],
             "installments":[{"id":"inst-1","name":{"pl":"Czesne semestr zimowy rata 1"},"amount":1250.0,"currency":"PLN","deadline":"2026-10-15","status":"unpaid"},
                            {"id":"inst-2","name":{"pl":"Czesne semestr zimowy rata 2"},"amount":1250.0,"currency":"PLN","deadline":"2026-11-15","status":"paid"}]}
            """.trimIndent()
        )
        raw.getJSONArray("installments").getJSONObject(0).put("deadline", LocalDate.now().plusDays(1).toString())
        val p = UsosParser.payments(raw)
        assertNotNull(p)
        assertEquals(1, p.accounts.size)
        assertEquals("PL12345678901234567890123456", p.accounts[0].bankAccountNumber)
        assertEquals(2, p.installments.size)
        assertEquals(1250.0, p.totalUnpaidAmount(), 0.01)
        assertFalse(p.hasOverdue())

        val restored = PaymentInfo.from(p.json())
        assertEquals(p.accounts[0].bankAccountNumber, restored.accounts[0].bankAccountNumber)
        assertEquals(p.installments.size, restored.installments.size)
        assertEquals(1250.0, restored.totalUnpaidAmount(), 0.01)
    }

    @Test
    fun parsesAcademicTermsAndDetectsActiveTerm() {
        val raw = JSONObject(
            """
            {"2026Z":{"name":{"pl":"Semestr zimowy 2026/2027"},"start_date":"2026-10-01","end_date":"2027-02-15","finish_date":"2027-02-28"},
             "2026L":{"name":{"pl":"Semestr letni 2026/2027"},"start_date":"2027-03-01","end_date":"2027-06-30","finish_date":"2027-09-30"}}
            """.trimIndent()
        )
        val terms = UsosParser.academicTerms(raw)
        assertEquals(2, terms.size)
        val termZ = terms["2026Z"]
        assertNotNull(termZ)
        assertEquals("2026Z", termZ!!.id)
        assertEquals("Semestr zimowy 2026/2027", termZ.name)
        assertTrue(termZ.isCurrent(LocalDate.parse("2026-10-14")))
        assertFalse(termZ.isCurrent(LocalDate.parse("2027-04-01")))

        val restored = AcademicTerm.from(termZ.json())
        assertEquals(termZ.id, restored.id)
        assertEquals(termZ.name, restored.name)
    }

    @Test
    fun parsesThesisAndCourseTests() {
        val rawTheses = JSONArray(
            """
            [{"title":{"pl":"Inteligentny system wczesnego wykrywania anomalii"},"status":{"pl":"W trakcie realizacji"},
              "supervisors":[{"first_name":"Jan","last_name":"Kowalski"}],
              "reviewers":[{"first_name":"Anna","last_name":"Nowak"}]}]
            """.trimIndent()
        )
        val thesis = UsosParser.thesis(rawTheses)
        assertNotNull(thesis)
        assertTrue(thesis.hasThesis())
        assertEquals("Inteligentny system wczesnego wykrywania anomalii", thesis.title)
        assertEquals("Jan Kowalski", thesis.supervisors)
        assertEquals("Anna Nowak", thesis.reviewers)

        val restoredThesis = ThesisInfo.from(thesis.json())
        assertEquals(thesis.title, restoredThesis.title)
        assertEquals(thesis.supervisors, restoredThesis.supervisors)

        val rawTests = JSONArray(
            """
            [{"id":"test-101","course_id":"INF-ALG","name":{"pl":"Kolokwium 1 - Rachunek macierzowy"},
              "points":18.5,"max_points":20.0,"grade":"5.0","date":"2026-10-20"}]
            """.trimIndent()
        )
        val tests = UsosParser.courseTests(rawTests, mapOf("INF-ALG" to "Algebra"))
        assertEquals(1, tests.size)
        val ctr = tests[0]
        assertEquals("test-101", ctr.id)
        assertEquals("Kolokwium 1 - Rachunek macierzowy", ctr.testName)
        assertEquals("Algebra", ctr.courseName)
        assertEquals(92.5, ctr.percentage(), 0.01)

        val restoredCtr = CourseTestRecord.from(ctr.json())
        assertEquals(ctr.id, restoredCtr.id)
        assertEquals(ctr.percentage(), restoredCtr.percentage(), 0.01)
    }

    @Test
    fun snapshotWithAllExtendedDataRoundtripsSuccessfully() {
        val s = Snapshot("2026-10-13", "2026-11-10")
        s.add(grade("5", "2026-10-14 12:00:00"))
        s.terms["2026Z"] = AcademicTerm("2026Z", "Semestr zimowy", "2026-10-01", "2027-02-15", "2027-02-28")
        s.thesis = ThesisInfo("Temat", "W toku", "Promotor", "Recenzent")
        s.tests.add(CourseTestRecord("t1", "MAT", "Matematyka", "Kolokwium 1", 15.0, 20.0, "4.0", "2026-10-20"))
        val p = PaymentInfo(
            listOf(PaymentInfo.Account("1", "Konto", "PLN", "PL123", 0.0)),
            emptyList()
        )
        s.payments = p

        val restored = Snapshot.from(JSONObject(s.json().toString()))
        assertEquals(1, restored.records.size)
        assertEquals(1, restored.terms.size)
        assertEquals("Semestr zimowy", restored.terms["2026Z"]!!.name)
        assertNotNull(restored.thesis)
        assertEquals("Temat", restored.thesis!!.title)
        assertEquals(1, restored.tests.size)
        assertEquals("Kolokwium 1", restored.tests[0].testName)
        assertNotNull(restored.payments)
        assertEquals("PL123", restored.payments!!.accounts[0].bankAccountNumber)
    }

    @Test
    fun recordOptMetaProvidesValuesWithFallbacks() {
        val meta = JSONObject().put("room", "302").put("building", "A")
        val r = Record("id1", "plan", "Matematyka", "09:00 - 10:30", "", "2026-10-15", "2026Z", "2026-10-15 09:00:00", "2026-10-15 10:30:00", 4.0, meta)
        assertEquals("302", r.optMeta("room"))
        assertEquals("A", r.optMeta("building"))
        assertEquals("", r.optMeta("nonexistent"))
        assertEquals("default-val", r.optMeta("nonexistent", "default-val"))
    }

    @Test
    fun lessonTimeContainsAccuratelyChecksRange() {
        val r = Record("plan:1", "plan", "Wykład", "09:00 - 10:30", "", "2026-10-15", "", "2026-10-15 09:00:00", "2026-10-15 10:30:00")
        val lt = LessonTime.of(r)
        val before = LocalDateTime.parse("2026-10-15T08:59:59").atZone(LessonTime.ZONE).toInstant()
        val inside = LocalDateTime.parse("2026-10-15T09:30:00").atZone(LessonTime.ZONE).toInstant()
        val after = LocalDateTime.parse("2026-10-15T10:30:00").atZone(LessonTime.ZONE).toInstant()

        assertFalse(lt.contains(before))
        assertTrue(lt.contains(inside))
        assertFalse(lt.contains(after))
    }

    @Test
    fun parsesUserAccountsWithRealUsosFormat() {
        val rawAccounts = JSONArray(
            """
            [
              {
                "account_number": "PL12102010260000123456789012",
                "account_owner": "Akademia Finansów i Biznesu Vistula",
                "name": {"pl": "Subkonto czesnego - WBiSM", "en": "Tuition subaccount"},
                "currency": "PLN",
                "swift": "BPHKPLPK",
                "faculty": {"id": "WBiSM", "name": {"pl": "Wydział Biznesu"}}
              },
              {
                "account_number": "PL98102010260000987654321098",
                "account_owner": "Akademia Finansów i Biznesu Vistula",
                "name": "Subkonto wpłat dodatkowych",
                "currency": "EUR"
              }
            ]
            """.trimIndent()
        )
        val list = UsosParser.accounts(rawAccounts)
        assertEquals(2, list.size)

        val a1 = list[0]
        assertEquals("PL12102010260000123456789012", a1.bankAccountNumber)
        assertEquals("Subkonto czesnego - WBiSM", a1.name)
        assertEquals("PLN", a1.currency)
        assertEquals("BPHKPLPK", a1.swift)
        assertEquals("Akademia Finansów i Biznesu Vistula", a1.accountOwner)
        assertEquals("Wydział Biznesu", a1.faculty)

        val a2 = list[1]
        assertEquals("PL98102010260000987654321098", a2.bankAccountNumber)
        assertEquals("Subkonto wpłat dodatkowych", a2.name)
        assertEquals("EUR", a2.currency)

        val restored = PaymentInfo.Account.from(a1.json())
        assertEquals(a1.bankAccountNumber, restored.bankAccountNumber)
        assertEquals(a1.faculty, restored.faculty)
        assertEquals(a1.swift, restored.swift)
    }

    @Test
    fun parsesUserPaymentsWithInstallmentPlansAndSinglePayments() {
        val rawPayments = JSONArray(
            """
            [
              {
                "id": "pay-100",
                "type": "tuition_fee",
                "description": {"pl": "Czesne - semestr zimowy 2026/2027"},
                "state": "unsettled",
                "account_number": "PL44102010260000444455556666",
                "currency": "PLN",
                "chosen_installment_plan": {
                  "id": "plan-4",
                  "currency": "PLN",
                  "installments": [
                    {
                      "number": 1,
                      "amount": 950.0,
                      "payment_date": "2026-10-15",
                      "description": {"pl": "Rata 1/4"}
                    },
                    {
                      "number": 2,
                      "amount": 950.0,
                      "payment_date": "2026-11-15",
                      "description": {"pl": "Rata 2/4"}
                    }
                  ]
                }
              },
              {
                "id": "pay-200",
                "type": "course_retake",
                "description": {"pl": "Powtarzanie przedmiotu - Algorytmy"},
                "state": "paid",
                "total_amount": 450.0,
                "currency": "PLN",
                "payment_deadline": "2026-09-30",
                "payment_date": "2026-09-25",
                "account_number": "PL55102010260000555566667777"
              }
            ]
            """.trimIndent()
        )

        val installments = UsosParser.installments(rawPayments)
        assertEquals(3, installments.size)

        // First installment from plan
        val i1 = installments[0]
        assertEquals("pay-100:rata:1", i1.id)
        assertEquals("Czesne - semestr zimowy 2026/2027 – Rata 1/4", i1.name)
        assertEquals(950.0, i1.amount, 0.001)
        assertEquals("2026-10-15", i1.deadline)
        assertEquals("PL44102010260000444455556666", i1.bankAccountNumber)

        // Second installment from plan
        val i2 = installments[1]
        assertEquals("pay-100:rata:2", i2.id)
        assertEquals("Czesne - semestr zimowy 2026/2027 – Rata 2/4", i2.name)
        assertEquals(950.0, i2.amount, 0.001)
        assertEquals("2026-11-15", i2.deadline)

        // Third item (direct single payment)
        val i3 = installments[2]
        assertEquals("pay-200", i3.id)
        assertEquals("Powtarzanie przedmiotu - Algorytmy", i3.name)
        assertEquals(450.0, i3.amount, 0.001)
        assertEquals("Opłacona", i3.status)
        assertTrue(i3.isPaid())
        assertEquals("2026-09-25", i3.paymentDate)
        assertEquals("PL55102010260000555566667777", i3.bankAccountNumber)
    }

    @Test
    fun paymentsMergesAccountsAndSynthesizesMissingSubaccounts() {
        val rawAccounts = JSONArray(
            """
            [{"account_number":"PL1111","name":"Konto podstawowe"}]
            """.trimIndent()
        )
        val rawPayments = JSONArray(
            """
            [
              {"id":"1","description":"Rata czesnego","total_amount":800.0,"account_number":"PL2222","state":"unsettled"}
            ]
            """.trimIndent()
        )

        val p = UsosParser.payments(rawAccounts, rawPayments)
        assertEquals(2, p.accounts.size)
        assertEquals("PL1111", p.accounts[0].bankAccountNumber)
        assertEquals("PL2222", p.accounts[1].bankAccountNumber)
        assertEquals(1, p.installments.size)
        assertTrue(p.hasUnknownSettlement())
    }

    @Test
    fun paymentInfoOverdueDetectionAndUnpaidSum() {
        val pastDue = PaymentInfo.Installment("1", "Rata 1", 500.0, "PLN", "2020-01-01", "Do zapłaty", "")
        assertTrue(pastDue.isOverdue())
        assertFalse(pastDue.isPaid())

        val paid = PaymentInfo.Installment("2", "Rata 2", 500.0, "PLN", "2020-01-01", "Opłacona", "2019-12-30")
        assertFalse(paid.isOverdue())
        assertTrue(paid.isPaid())

        val future = PaymentInfo.Installment("3", "Rata 3", 600.0, "PLN", "2099-01-01", "Do zapłaty", "")
        assertFalse(future.isOverdue())
        assertFalse(future.isPaid())

        val info = PaymentInfo(emptyList(), listOf(pastDue, paid, future))
        assertTrue(info.hasOverdue())
        assertEquals(1100.0, info.totalUnpaidAmount(), 0.001)
    }
}
