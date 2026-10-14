package pl.vistula.checker.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import org.json.*;

public class CoreTest {
    private Record grade(String value, String time) {
        return new Record("grade:1:1", "ocena", "Finanse", value, time, "");
    }
    private Record lesson(String id, String date, String value) {
        return new Record(id, "plan", "Statystyka", value, "", date);
    }
    @Test public void oauthMatchesPublishedHmacSha1Example() throws Exception {
        // OAuth 1.0 specification section 9.1.1, photos.example.net.
        Map<String, String> p = new HashMap<>();
        p.put("file", "vacation.jpg"); p.put("size", "original");
        p.put("oauth_consumer_key", "dpf43f3p2l4k3l03"); p.put("oauth_token", "nnch734d00sl2jdk");
        p.put("oauth_nonce", "kllo9940pd9333jh"); p.put("oauth_timestamp", "1191242096");
        p.put("oauth_signature_method", "HMAC-SHA1"); p.put("oauth_version", "1.0");
        assertEquals("tR3+Ty81lMeYAr/Fid0kMTYa/WM=", OAuth.signature("GET", "http://photos.example.net/photos", p,
            "kd94hf93k423kf44", "pfkkdhi9sl3r4s00"));
    }
    @Test public void oauthSortsByEncodedKeysAndEscapesUnicode() {
        assertEquals("%C4%85%20%2B%2A~", OAuth.encode("ą +*~"));
        assertEquals("%C4%85=a&z=b", OAuth.query(Map.of("z", "b", "ą", "a")));
    }
    @Test public void oauthFormDecodesEscapedTokenAndIgnoresExtraFields() {
        Map<String, String> result = OAuth.form("oauth_token=a%2Bb&oauth_token_secret=x%3Dy&extra=true");
        assertEquals("a+b", result.get("oauth_token")); assertEquals("x=y", result.get("oauth_token_secret"));
    }
    @Test(expected = IllegalArgumentException.class) public void emptyTokenIsRejected() { OAuth.form("oauth_token=&oauth_token_secret=x"); }
    @Test public void baselineAndUnchangedDownloadDoNotNotify() {
        Snapshot s = new Snapshot("2026-10-13", "2026-11-10"); s.add(grade("4", "2026-10-13 10:00:00"));
        assertTrue(Diff.between(null, s, 1, "2026-10-13").isEmpty());
        assertTrue(Diff.between(s, s, 1, "2026-10-13").isEmpty());
    }
    @Test public void gradeAndRoomChangeCarryBeforeAfterAndBothTimes() {
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot(old.start, old.end);
        old.add(grade("4", "2026-10-13 10:00:00")); now.add(grade("5", "2026-10-13 10:30:00"));
        old.add(lesson("plan:1", "2026-10-14", "09:00 Sala 204")); now.add(lesson("plan:1", "2026-10-14", "10:00 Sala 315"));
        List<Change> diff = Diff.between(old, now, 42, "2026-10-13");
        assertEquals(2, diff.size()); assertEquals("4", diff.get(0).before); assertEquals("5", diff.get(0).after);
        assertEquals(42, diff.get(0).detectedAt); assertEquals("2026-10-13 10:30:00", diff.get(0).sourceTime);
        assertEquals("Zmieniono", diff.get(1).action); assertEquals("", diff.get(1).sourceTime);
    }
    @Test public void rollingWindowDoesNotInventScheduleChanges() {
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot("2026-10-14", "2026-11-11");
        old.add(lesson("plan:expired", "2026-10-13", "finished"));
        now.add(lesson("plan:entering", "2026-11-10", "newly in window"));
        assertTrue(Diff.between(old, now, 42, "2026-10-14").isEmpty());
    }
    @Test public void additionsAndCancellationsInsideOverlapAreReported() {
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot(old.start, old.end);
        old.add(lesson("plan:cancelled", "2026-10-14", "cancelled")); now.add(lesson("plan:added", "2026-10-15", "added"));
        List<Change> diff = Diff.between(old, now, 42, "2026-10-13");
        assertEquals(2, diff.size()); assertEquals("Dodano", diff.get(0).action); assertEquals("Usunięto", diff.get(1).action);
    }
    @Test public void sourceTimeUpdateWithoutValueChangeIsDetected() {
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot(old.start, old.end);
        old.add(grade("4", "2026-10-13 10:00:00")); now.add(grade("4", "2026-10-13 10:30:00"));
        assertEquals(1, Diff.between(old, now, 42, "2026-10-13").size());
    }
    @Test public void snapshotsRoundTripWithoutLosingDates() throws Exception {
        Snapshot s = new Snapshot("2026-10-13", "2026-11-10"); s.add(grade("4", "2026-10-13 10:00:00"));
        Snapshot restored = Snapshot.from(new JSONObject(s.json().toString()));
        assertTrue(Diff.between(s, restored, 42, "2026-10-13").isEmpty()); assertEquals(s.start, restored.start);
    }
    @Test public void parsesTerms2IncludingUnitExamsAndNullSessions() throws Exception {
        JSONObject grades = new JSONObject("""
            {"2026Z":{"FIN":{"course_grades":[{"1":{"exam_id":1,"exam_session_number":1,"value_symbol":"4",
              "date_modified":"2026-10-13 10:00:00","comment":null},"2":null}],
              "course_units_grades":{"20":[{"1":{"exam_id":2,"exam_session_number":1,"value_symbol":"ZAL",
              "date_modified":"2026-10-13 11:00:00","comment":"Ćwiczenia"}}]}}}}
            """);
        Snapshot s = new Snapshot("2026-10-13", "2026-11-10");
        UsosParser.grades(s, grades, Map.of("FIN", "Finanse"));
        assertEquals(2, s.records.size()); assertEquals("Finanse", s.records.get("grade:1:1").title);
        assertTrue(s.records.get("grade:2:1").value.contains("Ćwiczenia"));
    }
    @Test public void timetableUsesStableMeetingIdWhenRoomOrTimeChanges() throws Exception {
        JSONObject activity = new JSONObject("""
            {"type":"classgroup2","sm_id":99,"start_time":"2026-10-14 09:00:00","end_time":"2026-10-14 10:30:00",
             "name":{"pl":"Statystyka"},"room_number":"204","lecturer_ids":[2,1]}
            """);
        Snapshot old = new Snapshot("2026-10-13", "2026-11-10"), now = new Snapshot(old.start, old.end);
        UsosParser.timetable(old, new JSONArray().put(activity));
        activity.put("room_number", "315").put("start_time", "2026-10-14 09:30:00").put("lecturer_ids", new JSONArray().put(1).put(2));
        UsosParser.timetable(now, new JSONArray().put(activity));
        List<Change> changes = Diff.between(old, now, 42, "2026-10-13");
        assertEquals(1, changes.size()); assertEquals("Zmieniono", changes.get(0).action);
    }
    @Test(expected = JSONException.class) public void malformedGradeCannotBecomeAValidSnapshot() throws Exception {
        UsosParser.grades(new Snapshot("2026-10-13", "2026-11-10"), new JSONObject("{\"2026Z\":{\"FIN\":{}}}"), Map.of());
    }
    @Test(expected = IllegalArgumentException.class) public void duplicateTimetableIdRejectsResponse() throws Exception {
        JSONObject activity = new JSONObject("""
            {"type":"classgroup2","sm_id":99,"start_time":"2026-10-14 09:00:00","end_time":"2026-10-14 10:30:00","name":{"pl":"Statystyka"}}
            """);
        UsosParser.timetable(new Snapshot("2026-10-13", "2026-11-10"), new JSONArray().put(activity).put(activity));
    }
    @Test public void changePrioritizesUsosDateAndPreservesSourceOnDeletion() throws Exception {
        Record gradeOld = grade("4", "2026-10-13 10:00:00");
        Record gradeNew = grade("5", "2026-10-13 11:30:00");
        Change update = new Change(gradeOld, gradeNew, 999999L);
        assertEquals("2026-10-13 11:30:00", update.sourceTime);
        assertNotEquals(999999L, update.effectiveTimestamp());
        assertTrue(update.effectiveTimestamp() > 0);

        Change deletion = new Change(gradeNew, null, 999999L);
        assertEquals("Usunięto", deletion.action);
        assertEquals("2026-10-13 11:30:00", deletion.sourceTime);
        assertEquals(update.effectiveTimestamp(), deletion.effectiveTimestamp());

        Record lesson = lesson("plan:99", "2026-10-15", "Sala 101");
        Change lessonChange = new Change(null, lesson, 12345L);
        assertEquals("", lessonChange.sourceTime);
        assertEquals("2026-10-15", lessonChange.date);
        assertEquals(12345L, lessonChange.effectiveTimestamp());

        Change roundtrip = Change.from(new JSONObject(update.json().toString()));
        assertEquals(update.sourceTime, roundtrip.sourceTime);
        assertEquals(update.effectiveTimestamp(), roundtrip.effectiveTimestamp());
    }

    @Test public void changeCarriesRichApiMetadataAndDetectsSpecificDiffs() throws Exception {
        JSONObject oldMeta = new JSONObject().put("room", "204").put("lecturers", "Anna Kowalska").put("groupNumber", "1")
            .put("startTime", "2026-10-15 09:00:00").put("endTime", "2026-10-15 10:30:00");
        JSONObject newMeta = new JSONObject().put("room", "315").put("lecturers", "Jan Kowalski").put("groupNumber", "2")
            .put("startTime", "2026-10-15 09:30:00").put("endTime", "2026-10-15 11:00:00");
        Record rOld = new Record("plan:1", "plan", "Matematyka", "v1", "", "2026-10-15", "", "2026-10-15 09:00:00", "2026-10-15 10:30:00", Double.NaN, oldMeta);
        Record rNew = new Record("plan:1", "plan", "Matematyka", "v2", "", "2026-10-15", "", "2026-10-15 09:30:00", "2026-10-15 11:00:00", Double.NaN, newMeta);

        Change c = new Change(rOld, rNew, 1000L);
        assertEquals("204 → 315", c.optMeta("diffRoom"));
        assertEquals("Anna Kowalska → Jan Kowalski", c.optMeta("diffLecturers"));
        assertEquals("1 → 2", c.optMeta("diffGroup"));
        assertEquals("09:00–10:30 → 09:30–11:00", c.optMeta("diffTime"));

        JSONObject gradeJson = new JSONObject("""
            {"2026Z":{"INF":{"course_grades":[{"1":{"exam_id":501,"exam_session_number":1,"value_symbol":"5",
              "passes":true,"value_description":{"pl":"bardzo dobry"},"counts_into_average":true,
              "date_modified":"2026-10-14 12:00:00","date_acquisition":"2026-10-14 11:45:00",
              "comment":"Egzamin zdany celująco"}}],"course_units_grades":null}}}
            """);
        Snapshot s = new Snapshot("2026-10-13", "2026-11-10");
        UsosParser.grades(s, gradeJson, Map.of("INF", "Informatyka"), Map.of("INF", 6.0));
        Record gradeRecord = s.records.get("grade:501:1");
        assertNotNull(gradeRecord);
        assertEquals(Boolean.TRUE, gradeRecord.metadata.optBoolean("passes"));
        assertEquals("bardzo dobry", gradeRecord.metadata.optString("valueDescription"));
        assertEquals(Boolean.TRUE, gradeRecord.metadata.optBoolean("countsIntoAverage"));
        assertEquals("Egzamin zdany celująco", gradeRecord.metadata.optString("comment"));
        assertEquals(6.0, gradeRecord.metadata.optDouble("ects"), 0.001);

        Change gradeChange = new Change(null, gradeRecord, 2000L);
        assertEquals(Boolean.TRUE, gradeChange.getPasses());
        assertEquals(Boolean.TRUE, gradeChange.getCountsIntoAverage());
        assertEquals("bardzo dobry", gradeChange.optMeta("valueDescription"));
        assertEquals("Egzamin zdany celująco", gradeChange.optMeta("comment"));
        assertEquals(6.0, gradeChange.getEcts(), 0.001);

        Change restored = Change.from(new JSONObject(gradeChange.json().toString()));
        assertEquals(Boolean.TRUE, restored.getPasses());
        assertEquals("bardzo dobry", restored.optMeta("valueDescription"));
    }

    @Test public void parsesPaymentInfoAndCalculatesBalances() throws Exception {
        JSONObject raw = new JSONObject("""
            {"user_accounts":[{"id":"1","name":{"pl":"Subkonto ogólne"},"currency":"PLN","bank_account_number":"PL12345678901234567890123456","balance":0.0}],
             "installments":[{"id":"inst-1","name":{"pl":"Czesne semestr zimowy rata 1"},"amount":1250.0,"currency":"PLN","deadline":"2026-10-15","status":"unpaid"},
                            {"id":"inst-2","name":{"pl":"Czesne semestr zimowy rata 2"},"amount":1250.0,"currency":"PLN","deadline":"2026-11-15","status":"paid"}]}
            """);
        PaymentInfo p = UsosParser.payments(raw);
        assertNotNull(p);
        assertEquals(1, p.accounts.size());
        assertEquals("PL12345678901234567890123456", p.accounts.get(0).bankAccountNumber);
        assertEquals(2, p.installments.size());
        assertEquals(1250.0, p.totalUnpaidAmount(), 0.01);
        assertFalse(p.hasOverdue());

        PaymentInfo restored = PaymentInfo.from(p.json());
        assertEquals(p.accounts.get(0).bankAccountNumber, restored.accounts.get(0).bankAccountNumber);
        assertEquals(p.installments.size(), restored.installments.size());
        assertEquals(1250.0, restored.totalUnpaidAmount(), 0.01);
    }

    @Test public void parsesAcademicTermsAndDetectsActiveTerm() throws Exception {
        JSONObject raw = new JSONObject("""
            {"2026Z":{"name":{"pl":"Semestr zimowy 2026/2027"},"start_date":"2026-10-01","end_date":"2027-02-15","finish_date":"2027-02-28"},
             "2026L":{"name":{"pl":"Semestr letni 2026/2027"},"start_date":"2027-03-01","end_date":"2027-06-30","finish_date":"2027-09-30"}}
            """);
        Map<String, AcademicTerm> terms = UsosParser.academicTerms(raw);
        assertEquals(2, terms.size());
        AcademicTerm termZ = terms.get("2026Z");
        assertNotNull(termZ);
        assertEquals("2026Z", termZ.id);
        assertEquals("Semestr zimowy 2026/2027", termZ.name);
        assertTrue(termZ.isCurrent(java.time.LocalDate.parse("2026-10-14")));
        assertFalse(termZ.isCurrent(java.time.LocalDate.parse("2027-04-01")));

        AcademicTerm restored = AcademicTerm.from(termZ.json());
        assertEquals(termZ.id, restored.id);
        assertEquals(termZ.name, restored.name);
    }

    @Test public void parsesThesisAndCourseTests() throws Exception {
        JSONArray rawTheses = new JSONArray("""
            [{"title":{"pl":"Inteligentny system wczesnego wykrywania anomalii"},"status":{"pl":"W trakcie realizacji"},
              "supervisors":[{"first_name":"Jan","last_name":"Kowalski"}],
              "reviewers":[{"first_name":"Anna","last_name":"Nowak"}]}]
            """);
        ThesisInfo thesis = UsosParser.thesis(rawTheses);
        assertNotNull(thesis);
        assertTrue(thesis.hasThesis());
        assertEquals("Inteligentny system wczesnego wykrywania anomalii", thesis.title);
        assertEquals("Jan Kowalski", thesis.supervisors);
        assertEquals("Anna Nowak", thesis.reviewers);

        ThesisInfo restoredThesis = ThesisInfo.from(thesis.json());
        assertEquals(thesis.title, restoredThesis.title);
        assertEquals(thesis.supervisors, restoredThesis.supervisors);

        JSONArray rawTests = new JSONArray("""
            [{"id":"test-101","course_id":"INF-ALG","name":{"pl":"Kolokwium 1 - Rachunek macierzowy"},
              "points":18.5,"max_points":20.0,"grade":"5.0","date":"2026-10-20"}]
            """);
        List<CourseTestRecord> tests = UsosParser.courseTests(rawTests, Map.of("INF-ALG", "Algebra"));
        assertEquals(1, tests.size());
        CourseTestRecord ctr = tests.get(0);
        assertEquals("test-101", ctr.id);
        assertEquals("Kolokwium 1 - Rachunek macierzowy", ctr.testName);
        assertEquals("Algebra", ctr.courseName);
        assertEquals(92.5, ctr.percentage(), 0.01);

        CourseTestRecord restoredCtr = CourseTestRecord.from(ctr.json());
        assertEquals(ctr.id, restoredCtr.id);
        assertEquals(ctr.percentage(), restoredCtr.percentage(), 0.01);
    }

    @Test public void snapshotWithAllExtendedDataRoundtripsSuccessfully() throws Exception {
        Snapshot s = new Snapshot("2026-10-13", "2026-11-10");
        s.add(grade("5", "2026-10-14 12:00:00"));
        s.terms.put("2026Z", new AcademicTerm("2026Z", "Semestr zimowy", "2026-10-01", "2027-02-15", "2027-02-28"));
        s.thesis = new ThesisInfo("Temat", "W toku", "Promotor", "Recenzent");
        s.tests.add(new CourseTestRecord("t1", "MAT", "Matematyka", "Kolokwium 1", 15.0, 20.0, "4.0", "2026-10-20"));
        PaymentInfo p = new PaymentInfo(
            List.of(new PaymentInfo.Account("1", "Konto", "PLN", "PL123", 0.0)),
            Collections.emptyList()
        );
        s.payments = p;

        Snapshot restored = Snapshot.from(new JSONObject(s.json().toString()));
        assertEquals(1, restored.records.size());
        assertEquals(1, restored.terms.size());
        assertEquals("Semestr zimowy", restored.terms.get("2026Z").name);
        assertNotNull(restored.thesis);
        assertEquals("Temat", restored.thesis.title);
        assertEquals(1, restored.tests.size());
        assertEquals("Kolokwium 1", restored.tests.get(0).testName);
        assertNotNull(restored.payments);
        assertEquals("PL123", restored.payments.accounts.get(0).bankAccountNumber);
    }

    @Test public void recordOptMetaProvidesValuesWithFallbacks() throws Exception {
        JSONObject meta = new JSONObject().put("room", "302").put("building", "A");
        Record r = new Record("id1", "plan", "Matematyka", "09:00 - 10:30", "", "2026-10-15", "2026Z", "2026-10-15 09:00:00", "2026-10-15 10:30:00", 4.0, meta);
        assertEquals("302", r.optMeta("room"));
        assertEquals("A", r.optMeta("building"));
        assertEquals("", r.optMeta("nonexistent"));
        assertEquals("default-val", r.optMeta("nonexistent", "default-val"));
    }

    @Test public void lessonTimeContainsAccuratelyChecksRange() {
        Record r = new Record("plan:1", "plan", "Wykład", "09:00 - 10:30", "", "2026-10-15", "", "2026-10-15 09:00:00", "2026-10-15 10:30:00");
        LessonTime lt = LessonTime.of(r);
        java.time.Instant before = java.time.LocalDateTime.parse("2026-10-15T08:59:59").atZone(LessonTime.ZONE).toInstant();
        java.time.Instant inside = java.time.LocalDateTime.parse("2026-10-15T09:30:00").atZone(LessonTime.ZONE).toInstant();
        java.time.Instant after = java.time.LocalDateTime.parse("2026-10-15T10:30:00").atZone(LessonTime.ZONE).toInstant();

        assertFalse(lt.contains(before));
        assertTrue(lt.contains(inside));
        assertFalse(lt.contains(after));
    }

    @Test public void parsesUserAccountsWithRealUsosFormat() throws Exception {
        JSONArray rawAccounts = new JSONArray("""
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
            """);
        List<PaymentInfo.Account> list = UsosParser.accounts(rawAccounts);
        assertEquals(2, list.size());

        PaymentInfo.Account a1 = list.get(0);
        assertEquals("PL12102010260000123456789012", a1.bankAccountNumber);
        assertEquals("Subkonto czesnego - WBiSM", a1.name);
        assertEquals("PLN", a1.currency);
        assertEquals("BPHKPLPK", a1.swift);
        assertEquals("Akademia Finansów i Biznesu Vistula", a1.accountOwner);
        assertEquals("Wydział Biznesu", a1.faculty);

        PaymentInfo.Account a2 = list.get(1);
        assertEquals("PL98102010260000987654321098", a2.bankAccountNumber);
        assertEquals("Subkonto wpłat dodatkowych", a2.name);
        assertEquals("EUR", a2.currency);

        PaymentInfo.Account restored = PaymentInfo.Account.from(a1.json());
        assertEquals(a1.bankAccountNumber, restored.bankAccountNumber);
        assertEquals(a1.faculty, restored.faculty);
        assertEquals(a1.swift, restored.swift);
    }

    @Test public void parsesUserPaymentsWithInstallmentPlansAndSinglePayments() throws Exception {
        JSONArray rawPayments = new JSONArray("""
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
            """);

        List<PaymentInfo.Installment> installments = UsosParser.installments(rawPayments);
        assertEquals(3, installments.size());

        // First installment from plan
        PaymentInfo.Installment i1 = installments.get(0);
        assertEquals("pay-100:rata:1", i1.id);
        assertEquals("Czesne - semestr zimowy 2026/2027 – Rata 1/4", i1.name);
        assertEquals(950.0, i1.amount, 0.001);
        assertEquals("2026-10-15", i1.deadline);
        assertEquals("PL44102010260000444455556666", i1.bankAccountNumber);

        // Second installment from plan
        PaymentInfo.Installment i2 = installments.get(1);
        assertEquals("pay-100:rata:2", i2.id);
        assertEquals("Czesne - semestr zimowy 2026/2027 – Rata 2/4", i2.name);
        assertEquals(950.0, i2.amount, 0.001);
        assertEquals("2026-11-15", i2.deadline);

        // Third item (direct single payment)
        PaymentInfo.Installment i3 = installments.get(2);
        assertEquals("pay-200", i3.id);
        assertEquals("Powtarzanie przedmiotu - Algorytmy", i3.name);
        assertEquals(450.0, i3.amount, 0.001);
        assertEquals("Opłacona", i3.status);
        assertTrue(i3.isPaid());
        assertEquals("2026-09-25", i3.paymentDate);
        assertEquals("PL55102010260000555566667777", i3.bankAccountNumber);
    }

    @Test public void paymentsMergesAccountsAndSynthesizesMissingSubaccounts() throws Exception {
        JSONArray rawAccounts = new JSONArray("""
            [{"account_number":"PL1111","name":"Konto podstawowe"}]
            """);
        JSONArray rawPayments = new JSONArray("""
            [
              {"id":"1","description":"Rata czesnego","total_amount":800.0,"account_number":"PL2222","state":"unsettled"}
            ]
            """);

        PaymentInfo p = UsosParser.payments(rawAccounts, rawPayments);
        assertEquals(2, p.accounts.size());
        assertEquals("PL1111", p.accounts.get(0).bankAccountNumber);
        assertEquals("PL2222", p.accounts.get(1).bankAccountNumber);
        assertEquals(1, p.installments.size());
        assertEquals(800.0, p.totalUnpaidAmount(), 0.001);
    }

    @Test public void paymentInfoOverdueDetectionAndUnpaidSum() {
        PaymentInfo.Installment pastDue = new PaymentInfo.Installment("1", "Rata 1", 500.0, "PLN", "2020-01-01", "Do zapłaty", "");
        assertTrue(pastDue.isOverdue());
        assertFalse(pastDue.isPaid());

        PaymentInfo.Installment paid = new PaymentInfo.Installment("2", "Rata 2", 500.0, "PLN", "2020-01-01", "Opłacona", "2019-12-30");
        assertFalse(paid.isOverdue());
        assertTrue(paid.isPaid());

        PaymentInfo.Installment future = new PaymentInfo.Installment("3", "Rata 3", 600.0, "PLN", "2099-01-01", "Do zapłaty", "");
        assertFalse(future.isOverdue());
        assertFalse(future.isPaid());

        PaymentInfo info = new PaymentInfo(Collections.emptyList(), List.of(pastDue, paid, future));
        assertTrue(info.hasOverdue());
        assertEquals(1100.0, info.totalUnpaidAmount(), 0.001);
    }
}

