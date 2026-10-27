package pl.vistula.checker

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

object DemoStudentServices {
    @JvmStatic
    @Throws(Exception::class)
    fun data(): JSONObject {
        val today = LocalDate.now(UsosClient.ZONE)
        val card = JSONObject()
            .put("id", "demo-card").put("type", "student").put("student_number", "123456")
            .put("barcode_number", "DEMO-123456").put("date_of_issue", today.minusMonths(6).toString())
            .put("expiration_date", today.plusDays(24).toString())
        val order = JSONObject()
            .put("id", "demo-order").put("status", "ready_to_receive")
            .put("student_number", "123456").put("order_date", "${today.minusDays(2)} 10:00:00")
            .put("expiration_date", today.plusMonths(6).toString())
        val mobile = JSONObject()
            .put("latest_order", order).put("orders", JSONArray().put(order))
            .put("why_cannot_order", "Masz już zamówienie mLegitymacji.").put("why_cannot_revoke", "")
            .put("expected_expiration_date", today.plusMonths(6).toString())
            .put("orderEligibilityKnown", true).put("revokeEligibilityKnown", true)
        val documents = JSONArray()
            .put(
                JSONObject()
                    .put("id", "demo-doc-1").put("name", "Zaświadczenie o studiowaniu")
                    .put("status", "Udostępniony").put("type_name", "Zaświadczenie")
                    .put("published_date", today.minusDays(3).toString())
                    .put("id_blobbox", "demo-blob")
                    .put("actions", JSONArray().put("GET_DOCUMENT").put("GET_UPO"))
            )
            .put(
                JSONObject()
                    .put("id", "demo-doc-2").put("name", "Decyzja stypendialna")
                    .put("status", "Do odbioru").put("type_name", "Decyzja")
                    .put("published_date", today.minusDays(1).toString())
                    .put("actions", JSONArray().put("RECEIVE_DOCUMENT"))
            )
        val teachingCourses = JSONArray()
            .put(
                JSONObject()
                    .put("id", "INF-201|2026Z")
                    .put("name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                    .put("term", JSONObject().put("pl", "Semestr zimowy 2026/2027").put("en", "Winter semester 2026/2027"))
            )
            .put(
                JSONObject()
                    .put("id", "STAT-202|2026Z")
                    .put("name", JSONObject().put("pl", "Statystyka opisowa").put("en", "Descriptive Statistics"))
                    .put("term", JSONObject().put("pl", "Semestr zimowy 2026/2027").put("en", "Winter semester 2026/2027"))
            )
        val lecturerGroups = JSONObject()
            .put(
                "groups",
                JSONArray().put(
                    JSONObject()
                        .put("course_unit_id", "CU-501")
                        .put("group_number", "1")
                        .put("course_id", "INF-201")
                        .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                        .put("class_type", JSONObject().put("pl", "Laboratorium").put("en", "Laboratory"))
                        .put("term_id", "2026Z")
                        .put("group_url", "https://usosweb.vistula.edu.pl/")
                )
            )
            .put(
                "coordinated",
                JSONArray().put(
                    JSONObject()
                        .put("course_id", "INF-201")
                        .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                        .put("term_id", "2026Z")
                        .put("term_name", JSONObject().put("pl", "Semestr zimowy 2026/2027").put("en", "Winter semester 2026/2027"))
                )
            )
        val lecturerTests = JSONObject().put(
            "trees",
            JSONArray().put(
                JSONObject()
                    .put("course_id", "INF-201")
                    .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                    .put("term_id", "2026Z")
                    .put("term_name", JSONObject().put("pl", "Semestr zimowy 2026/2027").put("en", "Winter semester 2026/2027"))
                    .put("root_id", "node-root-1")
                    .put("root_name", JSONObject().put("pl", "Ocena końcowa").put("en", "Final assessment"))
                    .put(
                        "nodes",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "node-task-1")
                                    .put("parent_id", "node-root-1")
                                    .put("order", 1)
                                    .put("name", JSONObject().put("pl", "Kolokwium 1").put("en", "Midterm 1"))
                                    .put("type", "task")
                                    .put("visible_for_students", true)
                            )
                            .put(
                                JSONObject()
                                    .put("id", "node-grade-1")
                                    .put("parent_id", "node-root-1")
                                    .put("order", 2)
                                    .put("name", JSONObject().put("pl", "Ocena z laboratorium").put("en", "Lab grade"))
                                    .put("type", "grade")
                                    .put("visible_for_students", true)
                            )
                    )
                    .put("nodesTruncated", false)
            )
        )
        val lecturerMeetings = JSONObject().put(
            "meetings",
            JSONArray().put(
                JSONObject()
                    .put("id", "meet-1")
                    .put("name", JSONObject().put("pl", "Konsultacje tygodniowe").put("en", "Weekly office hours"))
                    .put("comment", JSONObject().put("pl", "Pokój 312 lub online").put("en", "Room 312 or online"))
                    .put("open", true)
                    .put(
                        "dates",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "slot-1")
                                .put("start_time", "${today.plusDays(2)} 14:00:00")
                                .put("end_time", "${today.plusDays(2)} 15:30:00")
                                .put("seats_limit", 6)
                                .put("reserve_seats_limit", 2)
                                .put("comment", JSONObject().put("pl", "Konsultacje projektowe").put("en", "Project consultation"))
                        )
                    )
            )
        )
        val staffDirectory = JSONObject()
            .put(
                "users",
                JSONArray().put(
                    JSONObject()
                        .put("id", "staff-1")
                        .put("first_name", "Anna")
                        .put("last_name", "Kowalska")
                        .put("title_before", "dr inż.")
                        .put("title_after", "")
                        .put("office_hours", JSONObject().put("pl", "Wtorki 14:00–15:30").put("en", "Tuesdays 14:00–15:30"))
                        .put("room", "312")
                )
            )
            .put("hasMore", false)
            .put("total", 1)
        val registrations = JSONObject()
            .put(
                "registrations",
                JSONArray().put(
                    JSONObject()
                        .put("id", "reg-2026Z")
                        .put("description", JSONObject().put("pl", "Rejestracja na przedmioty semestru zimowego").put("en", "Winter semester course registration"))
                        .put("message", JSONObject().put("pl", "Tura podstawowa").put("en", "Primary round"))
                        .put("type", "direct")
                        .put("status", "active")
                        .put("is_linkage_required", true)
                )
            )
            .put(
                "cart",
                JSONArray().put(
                    JSONObject()
                        .put("course_id", "INF-201")
                        .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                        .put("term_id", "2026Z")
                        .put("user_registration_status", "registered")
                        .put("registration_status", "open")
                        .put("is_registration_valid", true)
                        .put("registrations_count", 24)
                        .put("limits", 30)
                )
            )
        val exams = JSONObject().put(
            "exams",
            JSONArray().put(
                JSONObject()
                    .put("id", "exam-101")
                    .put("code", "E-INF-201")
                    .put("course_id", "INF-201")
                    .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                    .put("term_id", "2026Z")
                    .put("registration_start", "${today.plusDays(14)} 08:00:00")
                    .put("registration_end", "${today.plusDays(28)} 23:59:00")
            )
        )
        val academicCalendar = JSONObject()
            .put(
                "events",
                JSONArray().put(
                    JSONObject()
                        .put("id", "cal-1")
                        .put("name", JSONObject().put("pl", "Dzień rektorski").put("en", "Rector's Day"))
                        .put("start_date", today.plusDays(5).toString())
                        .put("end_date", today.plusDays(5).toString())
                        .put("type", "rector")
                        .put("is_day_off", true)
                )
            )
            .put(
                "programmes",
                JSONArray().put(
                    JSONObject()
                        .put("id", "prog-1")
                        .put("status", "active")
                        .put("admission_date", "2024-10-01")
                        .put("programme_id", "S1-INF")
                        .put("programme_name", JSONObject().put("pl", "Informatyka, studia I stopnia").put("en", "Computer Science, BSc"))
                )
            )
        val now = System.currentTimeMillis()
        return JSONObject()
            .put("physicalCards", JSONObject().put("data", JSONArray().put(card)).put("fetchedAt", now))
            .put("mcard", JSONObject().put("data", mobile).put("fetchedAt", now))
            .put("documents", JSONObject().put("data", documents).put("fetchedAt", now))
            .put("teaching", JSONObject().put("data", JSONObject().put("courses", teachingCourses)).put("fetchedAt", now))
            .put("lecturerGroups", JSONObject().put("data", lecturerGroups).put("fetchedAt", now))
            .put("lecturerTests", JSONObject().put("data", lecturerTests).put("fetchedAt", now))
            .put("lecturerMeetings", JSONObject().put("data", lecturerMeetings).put("fetchedAt", now))
            .put("staffDirectory", JSONObject().put("data", staffDirectory).put("fetchedAt", now))
            .put("registrations", JSONObject().put("data", registrations).put("fetchedAt", now))
            .put("exams", JSONObject().put("data", exams).put("fetchedAt", now))
            .put("academicCalendar", JSONObject().put("data", academicCalendar).put("fetchedAt", now))
    }
}
