package pl.vistula.checker.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import pl.vistula.checker.DemoStudentServices
import pl.vistula.checker.KeyValueStore
import pl.vistula.checker.LecturerWriteActions
import pl.vistula.checker.UsosClient

class LecturerTest {
    @Test
    fun directoryCombinesNamesAndHandlesUnavailablePeople() {
        val names = UsosParser.lecturerNames(
            JSONObject(
                """
                {"1":{"first_name":" Anna ","last_name":" Kowalska "},"2":null,
                 "3":{"first_name":null,"last_name":null},"4":{"first_name":"Łukasz","last_name":"Żółć"}}
                """.trimIndent()
            )
        )
        assertEquals("Anna Kowalska", names["1"])
        assertEquals("Łukasz Żółć", names["4"])
        assertFalse(names.containsKey("2"))
        assertFalse(names.containsKey("3"))
    }

    @Test
    fun lecturerIdsAreDeduplicatedAndNullsAreIgnored() {
        val rows = JSONArray(
            """
            [{"lecturer_ids":[2,1,2,null]},{"lecturer_ids":[1,3]},{"lecturer_ids":null}]
            """.trimIndent()
        )
        assertEquals(setOf("1", "2", "3"), UsosParser.lecturerIds(rows))
    }

    @Test
    fun timetableShowsFullNamesAndExplicitFallbackAndIsOrderIndependent() {
        val activity = JSONObject(
            """
            {"type":"classgroup2","sm_id":99,"start_time":"2026-10-14 09:00:00","end_time":"2026-10-14 10:30:00",
             "name":{"pl":"Statystyka"},"lecturer_ids":[2,1,null]}
            """.trimIndent()
        )
        val old = Snapshot("2026-10-13", "2026-11-10")
        val now = Snapshot(old.start, old.end)
        UsosParser.timetable(old, JSONArray().put(activity), mapOf("1" to "Anna Kowalska"))
        val value = old.records["plan:sm:99"]!!.value
        assertTrue(value.contains("Prowadzący: Anna Kowalska, ID: 2"))
        assertFalse(value.contains("null"))
        activity.put("lecturer_ids", JSONArray().put(1).put(2))
        UsosParser.timetable(now, JSONArray().put(activity), mapOf("1" to "Anna Kowalska"))
        assertTrue(Diff.between(old, now, 1, "2026-10-13").isEmpty())
    }

    @Test
    fun accountRoleDetectsStudentLecturerAndBothModes() {
        assertEquals(AccountRole.Mode.STUDENT, AccountRole.mode(JSONObject()))

        val studentOnly = JSONObject().put("staffStatus", 0).put("studentStatusCode", 2)
        assertEquals(AccountRole.Mode.STUDENT, AccountRole.mode(studentOnly))
        assertFalse(AccountRole.lecturer(studentOnly))
        assertFalse(AccountRole.lecturerOnly(studentOnly))

        val lecturerOnly = JSONObject().put("staffStatus", 2).put("studentStatusCode", 0)
        assertEquals(AccountRole.Mode.LECTURER, AccountRole.mode(lecturerOnly))
        assertTrue(AccountRole.lecturer(lecturerOnly))
        assertTrue(AccountRole.lecturerOnly(lecturerOnly))

        val bothRoles = JSONObject().put("staffStatus", 2).put("studentStatusCode", 2)
        assertEquals(AccountRole.Mode.BOTH, AccountRole.mode(bothRoles))
        assertTrue(AccountRole.lecturer(bothRoles))
        assertFalse(AccountRole.lecturerOnly(bothRoles))

        val teachingDetected = JSONObject().put("staffStatus", 2).put("studentStatusCode", 0).put(
            "studentServices",
            JSONObject().put("teaching", JSONObject().put("data", JSONObject().put("courses", JSONArray().put(JSONObject().put("id", "C1")))))
        )
        assertEquals(AccountRole.Mode.LECTURER, AccountRole.mode(teachingDetected))
        assertTrue(AccountRole.hasTeaching(teachingDetected))
    }

    @Test
    fun featureAccessIncludesLecturerAndExtrasScopePresets() {
        val updated = FeatureAccess.change("grades|studies|offline_access", "lecturer", true)
        assertTrue(FeatureAccess.selected(updated, "lecturer"))
        assertTrue(updated.contains("staff_perspective"))
        assertTrue(updated.contains("crstests"))

        val withExtras = FeatureAccess.change(updated, "extras", true)
        assertTrue(FeatureAccess.selected(withExtras, "extras"))
        assertTrue(withExtras.contains("surveys_reports"))
        assertTrue(withExtras.contains("mailclient"))
    }

    @Test
    fun apiCatalogHidesLecturerWriteMethodsInStudentModeAndShowsThemForLecturers() {
        val studentState = JSONObject().put("staffStatus", 0).put("studentStatus", 2)
        val lecturerState = JSONObject().put("staffStatus", 2).put("studentStatus", 0)
        val writeMethods = listOf(
            "services/crstests/update_grades",
            "services/crstests/update_points",
            "services/crstests/change_grade_node_properties",
            "services/crstests/change_task_node_properties",
            "services/attendance/update_attendance",
            "services/attendance/change_list_mode",
            "services/theses/sign_report"
        )
        for (method in writeMethods) {
            assertTrue(ApiCatalog.lecturerWriteMethod(method))
            assertFalse(ApiCatalog.visibleForAccount(method, studentState))
            assertTrue(ApiCatalog.visibleForAccount(method, lecturerState))
        }
        assertTrue(ApiCatalog.visibleForAccount("services/courses/user", studentState))
    }

    @Test
    fun lecturerServicesSanitizesAndCapsCachedPayloads() {
        val rawGroupDetail = JSONObject()
            .put("course_unit_id", "CU-99")
            .put("group_number", 1)
            .put("course_name", JSONObject().put("pl", "Kryptografia").put("en", "Cryptography"))
            .put("class_type", JSONObject().put("pl", "Wykład").put("en", "Lecture"))
            .put("lecturers", JSONArray().put(JSONObject().put("id", "7").put("first_name", "Jan").put("last_name", "Nowak")))
        val participants = JSONArray()
        for (i in 1..225) {
            participants.put(
                JSONObject()
                    .put("id", "S-$i")
                    .put("first_name", "Student")
                    .put("last_name", "Nr$i")
                    .put("student_number", "${40000 + i}")
                    .put("email", "secret$i@example.edu")
                    .put("pesel", "00000000000")
            )
        }
        rawGroupDetail.put("participants", participants)

        val safe = LecturerServices.safeGroupDetail(rawGroupDetail)
        val savedParticipants = safe.getJSONArray("participants")
        assertEquals(LecturerServices.MAX_PARTICIPANTS, savedParticipants.length())
        assertTrue(safe.getBoolean("participantsTruncated"))
        assertEquals(225, safe.getInt("participantsTotal"))
        assertFalse(savedParticipants.getJSONObject(0).has("email"))
        assertFalse(savedParticipants.getJSONObject(0).has("pesel"))
        assertEquals("Cryptography", StudentServices.localized(safe.opt("course_name"), "en"))

        val rawNode = JSONObject()
            .put("id", "501")
            .put("name", JSONObject().put("pl", "Kolokwium").put("en", "Midterm"))
            .put("type", "task")
            .put("visible_for_students", true)
            .put("task_node_details", JSONObject().put("points_max", 50.0).put("points_min", 0.0).put("points_precision", 1))
            .put("private_note", "Tajna notatka")
        val safeNode = LecturerServices.safeNodeDetail(rawNode)
        assertFalse(safeNode.toString().contains("Tajna notatka"))
        assertEquals("Midterm", StudentServices.localized(safeNode.opt("name"), "en"))
        assertEquals(50.0, safeNode.getDouble("points_max"), 0.01)
    }

    @Test
    fun additionalApiModuleParsersAndAttendanceWrites() {
        val attLists = LecturerServices.safeAttendanceLists(
            JSONArray().put(
                JSONObject()
                    .put("id", "att-1")
                    .put("course_unit", JSONObject().put("id", "CU-10"))
                    .put("group_number", 2)
                    .put("date", "2026-10-20")
                    .put("mode", "intramural")
                    .put("owner", JSONObject().put("id", "7").put("first_name", "Anna").put("last_name", "Kowalska"))
            )
        )
        assertEquals(1, attLists.length())
        assertEquals("Anna Kowalska", attLists.getJSONObject(0).getString("owner_name"))

        val attEntries = LecturerServices.safeAttendanceEntries(
            JSONObject().put("id", "att-1").put("date", "2026-10-20").put("mode", "intramural"),
            JSONArray().put(
                JSONObject()
                    .put("student", JSONObject().put("id", "301").put("first_name", "Jan").put("last_name", "Nowak"))
                    .put("attendance_mode", "intramural")
                    .put("comment", "Obecny")
            )
        )
        assertEquals("att-1", attEntries.getString("id"))
        assertEquals(1, attEntries.getJSONArray("entries").length())

        val reports = LecturerServices.safeLecturerReports(
            JSONArray().put(JSONObject().put("id", "srv-1").put("name", JSONObject().put("pl", "Ankieta")).put("number_of_answers", 12)),
            JSONArray().put(JSONObject().put("id", "th-1").put("type", "master").put("title", JSONObject().put("pl", "Praca")))
        )
        assertEquals(1, reports.getJSONArray("surveys").length())
        assertEquals(1, reports.getJSONArray("signableTheses").length())

        val studyGroups = LecturerServices.safeStudyGroupsAndCredits(
            "90.0",
            JSONArray().put(JSONObject().put("id", "pg-1").put("number", "G1")),
            JSONArray().put(JSONObject().put("id", "cg-1").put("name", "AI Club"))
        )
        assertEquals("90.0", studyGroups.getString("usedEcts"))
        assertEquals(1, studyGroups.getJSONArray("primaryGroups").length())
        assertEquals(1, studyGroups.getJSONArray("customGroups").length())

        val mobs = LecturerServices.safeMobilities(
            JSONArray().put(JSONObject().put("id", "m-1").put("programme", JSONObject().put("en", "Erasmus+")))
        )
        assertEquals(1, mobs.length())

        val plc = LecturerServices.safePlacementAndSlips(
            JSONArray().put(JSONObject().put("test", JSONObject().put("id", "t1").put("max_score", "100")).put("result", JSONObject().put("score", "85"))),
            JSONArray().put(JSONObject().put("id", "s1").put("question", JSONObject().put("question", "BHP"))),
            JSONArray().put("student")
        )
        assertEquals("85", plc.getJSONArray("placementTests").getJSONObject(0).getString("score"))
        assertEquals("BHP", plc.getJSONArray("slipsToAnswer").getJSONObject(0).getString("question"))

        val guide = LecturerServices.safeCampusGuide(
            JSONArray().put(JSONObject().put("id", "c1").put("title", JSONObject().put("pl", "Rozdział"))),
            JSONArray().put(JSONObject().put("id", "d1").put("address", "Stokłosy 3")),
            JSONArray().put(JSONObject().put("id", "b1").put("postal_address", "Warszawa"))
        )
        assertEquals(1, guide.getJSONArray("guideChapters").length())
        assertEquals(1, guide.getJSONArray("dormitories").length())
        assertEquals(1, guide.getJSONArray("buildings").length())

        val comms = LecturerServices.safeCommunications(
            JSONArray().put(JSONObject().put("id", "st1").put("subject", "Oświadczenie")),
            JSONArray().put(JSONObject().put("id", "n1").put("type", "grades/new")),
            JSONArray().put(JSONObject().put("id", "msg1").put("subject", "Info")),
            JSONObject().put("1", JSONObject().put("id", "1").put("name", JSONObject().put("pl", "Dydaktyka")))
        )
        assertEquals(1, comms.getJSONArray("statements").length())
        assertEquals(1, comms.getJSONArray("notifications").length())
        assertEquals(1, comms.getJSONArray("messages").length())
        assertEquals(1, comms.getJSONArray("caseCategories").length())

        val courses = LecturerServices.safeCourseSearchResults(
            JSONArray().put(JSONObject().put("course_id", "INF-1").put("match", "<b>Informatyka</b>")),
            false
        )
        assertEquals("Informatyka", courses.getJSONArray("courses").getJSONObject(0).getString("match"))

        val roomSched = LecturerServices.safeRoomSchedule(
            JSONObject().put("id", "312").put("number", "312").put("capacity", 30),
            JSONArray().put(JSONObject().put("start_time", "2026-10-21 10:00:00").put("end_time", "2026-10-21 11:30:00"))
        )
        assertEquals("312", roomSched.getString("number"))
        assertEquals(1, roomSched.getJSONArray("lessons").length())

        val demo = DemoStudentServices.data()
        assertTrue(demo.has("lecturerAttendance"))
        assertTrue(demo.has("lecturerReports"))
        assertTrue(demo.has("mobility"))
        assertTrue(demo.has("placementAndSlips"))
        assertTrue(demo.has("campusGuide"))
        assertTrue(demo.has("communications"))

        // Experimental attendance write verification (accepts empty {} response + authoritative re-read)
        val store = MapStore()
        LecturerWriteActions.setEnabled(store, true)
        LecturerWriteActions.acknowledge(store)
        val lecturerCreds = JSONObject()
            .put("key", "k").put("secret", "s")
            .put("token", "t").put("tokenSecret", "ts")
            .put("staffStatus", 2).put("studentStatus", 0)
            .put("scopes", "staff_perspective")
        val attCalls = mutableListOf<String>()
        val attClient = UsosClient(lecturerCreds, {}, { uri ->
            attCalls.add(uri)
            when {
                uri.contains("services/attendance/attendance_list") ->
                    FakeHttpConnection(uri, 200, """{"id":"att-1","group_number":1,"date":"2026-10-20","mode":"remote"}""")
                uri.contains("services/attendance/update_attendance") ->
                    FakeHttpConnection(uri, 200, "{}")
                uri.contains("services/attendance/change_list_mode") ->
                    FakeHttpConnection(uri, 200, "{}")
                uri.contains("services/attendance/attendance") ->
                    FakeHttpConnection(uri, 200, """[{"student":{"id":"301","first_name":"Jan","last_name":"Nowak"},"attendance_mode":"remote","comment":"Online"}]""")
                else -> FakeHttpConnection(uri, 404, "{}")
            }
        })
        val updatedAtt = LecturerWriteActions.updateAttendance(attClient, lecturerCreds, "att-1", "301", "remote", "Online", store)
        assertEquals("att-1", updatedAtt.getString("id"))
        assertEquals(1, attCalls.count { it.contains("update_attendance") })
        val updatedMode = LecturerWriteActions.changeAttendanceListMode(attClient, lecturerCreds, "att-1", "remote", store)
        assertEquals("remote", updatedMode.getString("mode"))
    }

    @Test
    fun lecturerWriteActionsEnforceRoleFlagScopeAndNoRetryOnUncertainty() {
        val store = MapStore()
        val studentCreds = JSONObject()
            .put("key", "k").put("secret", "s")
            .put("token", "t").put("tokenSecret", "ts")
            .put("staffStatus", 0).put("studentStatus", 2)
            .put("scopes", "staff_perspective|crstests")
        val lecturerCreds = JSONObject(studentCreds.toString())
            .put("staffStatus", 2)

        val dummyClient = UsosClient(lecturerCreds, {}, { uri ->
            FakeHttpConnection(uri, 200, """{"id":"101","type":"task","visible_for_students":true}""")
        })

        // 1. Student mode rejected
        try {
            LecturerWriteActions.updatePoints(dummyClient, studentCreds, "101", "2001", 15.0, "", null, store)
            fail("Expected student account rejection")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Lecturer"))
        }

        // 2. Experimental flag disabled rejected
        LecturerWriteActions.setEnabled(store, false)
        try {
            LecturerWriteActions.updatePoints(dummyClient, lecturerCreds, "101", "2001", 15.0, "", null, store)
            fail("Expected disabled experimental flag rejection")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Experimental"))
        }

        // 3. Enabled but unacknowledged rejected
        LecturerWriteActions.setEnabled(store, true)
        try {
            LecturerWriteActions.updatePoints(dummyClient, lecturerCreds, "101", "2001", 15.0, "", null, store)
            fail("Expected unacknowledged experimental notice rejection")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("acknowledgment"))
        }

        // 4. Acknowledge and test successful single-flight write + authoritative re-read
        LecturerWriteActions.acknowledge(store)
        val calls = mutableListOf<String>()
        val okClient = UsosClient(lecturerCreds, {}, { uri ->
            calls.add(uri)
            when {
                uri.contains("services/crstests/node2") ->
                    FakeHttpConnection(uri, 200, """{"id":"101","name":{"pl":"Kolokwium"},"type":"task","visible_for_students":true,"task_node_details":{"points_min":0.0,"points_max":20.0}}""")
                uri.contains("services/crstests/update_points") ->
                    FakeHttpConnection(uri, 200, """{"success":true}""")
                else -> FakeHttpConnection(uri, 404, "{}")
            }
        })
        val updated = LecturerWriteActions.updatePoints(okClient, lecturerCreds, "101", "2001", 15.0, "OK", null, store)
        assertEquals("101", updated.optString("id"))
        assertEquals(1, calls.count { it.contains("update_points") })
        assertEquals(2, calls.count { it.contains("node2") })
        val audit = JSONArray(store.getString(LecturerWriteActions.PREF_AUDIT_LOG, "[]"))
        assertEquals(1, audit.length())
        assertEquals("update_points", audit.getJSONObject(0).getString("action"))
        assertEquals("101", audit.getJSONObject(0).getString("nodeId"))
        assertFalse(audit.toString().contains("OK"))

        // 5. Network drop during POST raises Uncertain without retrying POST
        val dropCalls = mutableListOf<String>()
        val dropClient = UsosClient(lecturerCreds, {}, { uri ->
            dropCalls.add(uri)
            if (uri.contains("services/crstests/update_points")) {
                throw IOException("Simulated socket timeout")
            }
            FakeHttpConnection(uri, 200, """{"id":"101","type":"task"}""")
        })
        try {
            LecturerWriteActions.updatePoints(dropClient, lecturerCreds, "101", "2001", 18.0, "", null, store)
            fail("Expected Uncertain exception on network drop")
        } catch (_: LecturerWriteActions.Uncertain) {
            assertEquals(1, dropCalls.count { it.contains("update_points") })
        }
    }

    private class MapStore : KeyValueStore {
        private val map = LinkedHashMap<String, String>()
        override fun getString(key: String, defaultValue: String): String = map[key] ?: defaultValue
        override fun putString(key: String, value: String) { map[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key]?.toBooleanStrictOrNull() ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { map[key] = value.toString() }
        override fun getLong(key: String, defaultValue: Long): Long = map[key]?.toLongOrNull() ?: defaultValue
        override fun putLong(key: String, value: Long) { map[key] = value.toString() }
        override fun clear() { map.clear() }
    }

    private class FakeHttpConnection(
        uri: String,
        private val status: Int,
        private val payload: String
    ) : HttpURLConnection(URI(uri).toURL()) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getContentType(): String = "application/json"
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))
        override fun getErrorStream(): InputStream = ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))
    }
}
