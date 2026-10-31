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
        val lecturerAttendance = JSONObject().put(
            "lists",
            JSONArray().put(
                JSONObject()
                    .put("id", "att-101")
                    .put("course_unit_id", "CU-501")
                    .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                    .put("group_number", "1")
                    .put("date", today.minusDays(1).toString())
                    .put("mode", "intramural")
                    .put("owner_id", "staff-1")
                    .put("owner_name", "Anna Kowalska")
            )
        )
        val lecturerReports = JSONObject()
            .put(
                "surveys",
                JSONArray().put(
                    JSONObject()
                        .put("id", "srv-2026Z-1")
                        .put("name", JSONObject().put("pl", "Ocena zajęć dydaktycznych").put("en", "Course evaluation survey"))
                        .put("headline", JSONObject().put("pl", "Laboratorium gr. 1").put("en", "Laboratory group 1"))
                        .put("start_date", today.minusDays(14).toString())
                        .put("end_date", today.plusDays(7).toString())
                        .put("number_of_answers", 18)
                        .put("course_unit_id", "CU-501")
                        .put("group_number", "1")
                        .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                )
            )
            .put(
                "signableTheses",
                JSONArray().put(
                    JSONObject()
                        .put("id", "th-rep-1")
                        .put("type", "bachelor")
                        .put("title", JSONObject().put("pl", "Architektura mikroserwisów w chmurze").put("en", "Cloud Microservice Architecture"))
                        .put("status", "ready_to_sign")
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
            .put("usedEcts", "120.0")
            .put(
                "primaryGroups",
                JSONArray().put(
                    JSONObject()
                        .put("id", "prg-1")
                        .put("number", "INF-Z-3")
                        .put("description", JSONObject().put("pl", "Grupa dziekańska 3").put("en", "Dean's group 3"))
                        .put("programme_id", "S1-INF")
                        .put("programme_name", JSONObject().put("pl", "Informatyka, studia I stopnia").put("en", "Computer Science, BSc"))
                        .put("stage_id", "3Z")
                        .put("term_id", "2026Z")
                )
            )
            .put(
                "customGroups",
                JSONArray().put(
                    JSONObject()
                        .put("id", "csg-1")
                        .put("name", "Koło Naukowe AI")
                        .put("description", "Sekcja projektów uczenia maszynowego")
                        .put("expiration_date", today.plusMonths(6).toString())
                )
            )
        val mobility = JSONObject()
            .put(
                "studentMobilities",
                JSONArray().put(
                    JSONObject()
                        .put("id", "mob-1")
                        .put("category", JSONObject().put("pl", "Wyjazd na studia").put("en", "Study mobility"))
                        .put("programme", JSONObject().put("pl", "Erasmus+ KA131").put("en", "Erasmus+ KA131"))
                        .put("institution", JSONObject().put("pl", "Politechnika w Mediolanie").put("en", "Politecnico di Milano"))
                        .put("start_date", "2026-02-15")
                        .put("end_date", "2026-07-10")
                        .put("state", JSONObject().put("pl", "Zaakceptowany").put("en", "Accepted"))
                )
            )
            .put(
                "coordinatedMobilities",
                JSONArray().put(
                    JSONObject()
                        .put("id", "mob-coord-1")
                        .put("category", JSONObject().put("pl", "Koordynacja umowy").put("en", "Agreement coordination"))
                        .put("programme", JSONObject().put("pl", "Erasmus+").put("en", "Erasmus+"))
                        .put("institution", JSONObject().put("pl", "Uniwersytet Techniczny w Monachium").put("en", "Technical University of Munich"))
                        .put("start_date", "2025-10-01")
                        .put("end_date", "2027-09-30")
                        .put("state", JSONObject().put("pl", "Aktywny").put("en", "Active"))
                )
            )
        val placementAndSlips = JSONObject()
            .put(
                "placementTests",
                JSONArray().put(
                    JSONObject()
                        .put("id", "plc-en-1")
                        .put("name", JSONObject().put("pl", "Test poziomujący z języka angielskiego").put("en", "English placement test"))
                        .put("description", JSONObject().put("pl", "Poziom B2/C1").put("en", "Level B2/C1"))
                        .put("max_score", "100")
                        .put("score", "88")
                        .put("level", "B2+")
                )
            )
            .put(
                "slipsToAnswer",
                JSONArray().put(
                    JSONObject()
                        .put("id", "slip-q-1")
                        .put("slip_id", "slip-10")
                        .put("question_id", "q-1")
                        .put("question", "Potwierdzenie udziału w szkoleniu BHP / OHS training confirmation")
                )
            )
            .put("slipRoles", JSONArray().put("candidate"))
        val campusGuide = JSONObject()
            .put(
                "guideChapters",
                JSONArray().put(
                    JSONObject()
                        .put("id", "ch-1")
                        .put("title", JSONObject().put("pl", "Pierwsze kroki na uczelni").put("en", "First steps at the university"))
                        .put(
                            "pages",
                            JSONArray().put(
                                JSONObject()
                                    .put("id", "pg-1")
                                    .put("title", JSONObject().put("pl", "Dziekanat i obsługa studenta").put("en", "Dean's office & student support"))
                            )
                        )
                )
            )
            .put(
                "dormitories",
                JSONArray().put(
                    JSONObject()
                        .put("id", "dorm-1")
                        .put("name", JSONObject().put("pl", "Dom Studencki Stokłosy").put("en", "Stokłosy Dormitory"))
                        .put("short_name", JSONObject().put("pl", "DS Stokłosy").put("en", "DS Stokłosy"))
                        .put("address", "ul. Stokłosy 3, Warszawa")
                        .put("description", JSONObject().put("pl", "Pokoje 1- i 2-osobowe blisko kampusu").put("en", "Single and double rooms near campus"))
                )
            )
            .put(
                "buildings",
                JSONArray().put(
                    JSONObject()
                        .put("id", "BLD-MAIN")
                        .put("name", JSONObject().put("pl", "Budynek Główny Kampusu").put("en", "Main Campus Building"))
                        .put("postal_address", "ul. Stokłosy 3, 02-787 Warszawa")
                        .put("campus_name", JSONObject().put("pl", "Kampus Ursynów").put("en", "Ursynów Campus"))
                )
            )
            .put(
                "allocationPeriods",
                JSONArray().put(
                    JSONObject()
                        .put("id", "alloc-2026")
                        .put("name", JSONObject().put("pl", "Akcja kwaterunkowa 2026/2027").put("en", "Housing allocation 2026/2027"))
                        .put("description", JSONObject().put("pl", "Tura jesienna").put("en", "Autumn round"))
                )
            )
        val communications = JSONObject()
            .put(
                "statements",
                JSONArray().put(
                    JSONObject()
                        .put("id", "stmt-1")
                        .put("subject", "Komunikat organizacyjny semestru zimowego")
                        .put("content", "Potwierdź zapoznanie się z harmonogramem sesji egzaminacyjnej.")
                        .put("expiration_date", today.plusDays(30).toString())
                )
            )
            .put(
                "notifications",
                JSONArray().put(
                    JSONObject()
                        .put("id", "notif-1")
                        .put("type", "crstests/new_grade")
                        .put("created", "${today.minusDays(1)} 12:15:00")
                        .put("summary", JSONObject().put("pl", "Nowy wynik w sprawdzianach: Kolokwium 1").put("en", "New test score: Midterm 1"))
                )
            )
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("id", "msg-1")
                        .put("subject", "Informacja dla grupy laboratoryjnej 1")
                        .put("status", "sent")
                        .put("date", today.minusDays(2).toString())
                )
            )
            .put(
                "caseCategories",
                JSONArray().put(
                    JSONObject()
                        .put("id", "cat-1")
                        .put("name", JSONObject().put("pl", "Sprawy dydaktyczne").put("en", "Academic affairs"))
                )
            )
        communications.put(
            "surveys",
            JSONArray().put(
                JSONObject()
                    .put("id", "survey-1")
                    .put("survey_type", "course")
                    .put("name", JSONObject().put("pl", "Ankieta oceny zajęć: Programowanie obiektowe").put("en", "Course evaluation: Object-Oriented Programming"))
                    .put("headline", "Pomóż nam ulepszyć zajęcia.")
                    .put("start_date", today.minusDays(3).toString())
                    .put("end_date", today.plusDays(11).toString())
                    .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                    .put("group_number", "1")
                    .put("can_i_fill_out", true)
                    .put("did_i_fill_out", false)
            )
        )
        val demoPaymentDetails = JSONArray().put(
            JSONObject()
                .put("id", "pay-2026Z")
                .put("description", JSONObject().put("pl", "Czesne semestr zimowy 2026/2027").put("en", "Tuition winter semester 2026/2027"))
                .put("type", "tuition")
                .put("state", "active")
                .put("total_amount", 3400.0)
                .put("saldo_amount", 2550.0)
                .put("interest", 0.0)
                .put("currency", "PLN")
                .put("has_bonus", true)
                .put("bonus_amount", 100.0)
                .put("bonus_deadline", today.plusDays(10).toString())
                .put("payment_deadline", today.plusDays(25).toString())
                .put("default_choice_date", today.minusDays(30).toString())
                .put("chosenPlan", "plan-4")
                .put("plans", JSONArray()
                    .put(JSONObject().put("id", "plan-1").put("currency", "PLN").put("is_default", false)
                        .put("installments", JSONArray().put(JSONObject().put("amount", 3400.0).put("payment_date", today.plusDays(25).toString()).put("number", 1).put("description", JSONObject().put("pl", "Jednorazowo").put("en", "Single installment")))))
                    .put(JSONObject().put("id", "plan-4").put("currency", "PLN").put("is_default", true)
                        .put("installments", JSONArray()
                            .put(JSONObject().put("amount", 850.0).put("payment_date", today.minusDays(5).toString()).put("number", 1).put("description", JSONObject().put("pl", "Rata 1/4").put("en", "Installment 1/4")))
                            .put(JSONObject().put("amount", 850.0).put("payment_date", today.plusDays(25).toString()).put("number", 2).put("description", JSONObject().put("pl", "Rata 2/4").put("en", "Installment 2/4")))
                            .put(JSONObject().put("amount", 850.0).put("payment_date", today.plusDays(55).toString()).put("number", 3).put("description", JSONObject().put("pl", "Rata 3/4").put("en", "Installment 3/4")))
                            .put(JSONObject().put("amount", 850.0).put("payment_date", today.plusDays(85).toString()).put("number", 4).put("description", JSONObject().put("pl", "Rata 4/4").put("en", "Installment 4/4")))))
                )
        )
        val demoRemittances = JSONArray()
            .put(JSONObject()
                .put("id", "rem-101")
                .put("amount", 850.0)
                .put("remaining", 0.0)
                .put("date", today.minusDays(7).toString())
                .put("status", "settled")
                .put("is_settled", true)
                .put("currency", "PLN")
                .put("description", JSONObject().put("pl", "Wpłata na czesne – rata 1").put("en", "Tuition fee payment – installment 1")))
            .put(JSONObject()
                .put("id", "rem-102")
                .put("amount", 250.0)
                .put("remaining", 0.0)
                .put("date", today.minusDays(20).toString())
                .put("status", "settled")
                .put("is_settled", true)
                .put("currency", "PLN")
                .put("description", JSONObject().put("pl", "Zwrot nadpłaty").put("en", "Overpayment refund")))
        val demoTheses = JSONArray().put(
            JSONObject()
                .put("id", "ths-101")
                .put("type", "engineer")
                .put("title", "Projekt i implementacja mobilnego systemu monitorowania postępów akademickich")
                .put("titles", JSONObject().put("pl", "Projekt i implementacja mobilnego systemu monitorowania postępów akademickich").put("en", "Design and implementation of mobile academic progress tracking system"))
                .put("status", "in_progress")
                .put("supervisors", JSONArray().put(JSONObject().put("id", "staff-1").put("first_name", "Anna").put("last_name", "Kowalska")))
                .put("authors", JSONArray().put(JSONObject().put("id", "stud-1").put("first_name", "Jan").put("last_name", "Nowak")))
                .put("faculty_name", JSONObject().put("pl", "Wydział Informatyki").put("en", "Faculty of Computer Science"))
        )
        val now = System.currentTimeMillis()
        return JSONObject()
            .put("physicalCards", JSONObject().put("data", JSONArray().put(card)).put("fetchedAt", now))
            .put("mcard", JSONObject().put("data", mobile).put("fetchedAt", now))
            .put("documents", JSONObject().put("data", documents).put("fetchedAt", now))
            .put("payments", JSONObject().put("data", JSONObject().put("details", demoPaymentDetails)).put("fetchedAt", now))
            .put("remittances", JSONObject().put("data", JSONObject().put("items", demoRemittances)).put("fetchedAt", now))
            .put("theses", JSONObject().put("data", JSONObject().put("theses", demoTheses)).put("fetchedAt", now))
            .put("teaching", JSONObject().put("data", JSONObject().put("courses", teachingCourses)).put("fetchedAt", now))
            .put("lecturerGroups", JSONObject().put("data", lecturerGroups).put("fetchedAt", now))
            .put("lecturerTests", JSONObject().put("data", lecturerTests).put("fetchedAt", now))
            .put("lecturerMeetings", JSONObject().put("data", lecturerMeetings).put("fetchedAt", now))
            .put("lecturerAttendance", JSONObject().put("data", lecturerAttendance).put("fetchedAt", now))
            .put("lecturerReports", JSONObject().put("data", lecturerReports).put("fetchedAt", now))
            .put("staffDirectory", JSONObject().put("data", staffDirectory).put("fetchedAt", now))
            .put("registrations", JSONObject().put("data", registrations).put("fetchedAt", now))
            .put("tokenRegistrations", JSONObject().put("data", JSONObject().put("registrations", JSONArray().put(
                JSONObject().put("id", "token-demo").put("description", JSONObject().put("pl", "Rejestracja żetonowa na języki").put("en", "Language token registration"))
                    .put("status", "active").put("message", JSONObject())
            ))).put("fetchedAt", now))
            .put("exams", JSONObject().put("data", exams).put("fetchedAt", now))
            .put("academicCalendar", JSONObject().put("data", academicCalendar).put("fetchedAt", now))
            .put("mobility", JSONObject().put("data", mobility).put("fetchedAt", now))
            .put("placementAndSlips", JSONObject().put("data", placementAndSlips).put("fetchedAt", now))
            .put("campusGuide", JSONObject().put("data", campusGuide).put("fetchedAt", now))
            .put("communications", JSONObject().put("data", communications).put("fetchedAt", now))
    }

    @JvmStatic
    fun demoAttendanceDetail(listId: String): JSONObject {
        val today = LocalDate.now(UsosClient.ZONE)
        return JSONObject()
            .put("id", listId.ifEmpty { "att-101" })
            .put("course_unit_id", "CU-501")
            .put("group_number", "1")
            .put("date", today.minusDays(1).toString())
            .put("mode", "intramural")
            .put(
                "entries",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("student_id", "101")
                            .put("first_name", "Jan")
                            .put("last_name", "Nowak")
                            .put("student_number", "550101")
                            .put("attendance_mode", "intramural")
                            .put("comment", "")
                    )
                    .put(
                        JSONObject()
                            .put("student_id", "102")
                            .put("first_name", "Maria")
                            .put("last_name", "Wiśniewska")
                            .put("student_number", "550102")
                            .put("attendance_mode", "justified_absence")
                            .put("comment", "Zwolnienie lekarskie")
                    )
            )
            .put("entriesTotal", 2)
            .put("entriesTruncated", false)
    }

    @JvmStatic
    fun demoCourseSearch(query: String): JSONObject = JSONObject()
        .put(
            "courses",
            JSONArray().put(
                JSONObject()
                    .put("id", "INF-201")
                    .put("name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                    .put("match", "Programowanie obiektowe ($query)")
                    .put("ects", "6.0")
                    .put("fac_id", "0000")
                    .put("is_currently_conducted", true)
                    .put("profile_url", "https://usosweb.vistula.edu.pl/")
            )
        )
        .put("hasMore", false)

    @JvmStatic
    fun demoCourseDetails(courseId: String): JSONObject = JSONObject()
        .put("id", courseId)
        .put("name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
        .put("lang_id", "en").put("ects", "6")
        .put("description", JSONObject().put("pl", "Przykładowy opis przedmiotu.").put("en", "Example course description."))
        .put("learning_outcomes", JSONObject().put("pl", "Projektowanie prostych programów.").put("en", "Design simple programs."))
        .put("assessment_criteria", JSONObject().put("pl", "Projekt i egzamin.").put("en", "Project and exam."))

    @JvmStatic
    fun demoProgrammeDetails(programmeId: String): JSONObject = JSONObject().put("id", programmeId)
        .put("name", JSONObject().put("pl", "Informatyka").put("en", "Computer Science"))
        .put("faculty_name", JSONObject().put("pl", "Wydział przykładowy").put("en", "Example faculty"))
        .put("mode_of_studies", JSONObject().put("pl", "Stacjonarne").put("en", "Full-time"))
        .put("level_of_studies", JSONObject().put("pl", "Studia pierwszego stopnia").put("en", "First-cycle studies"))
        .put("duration", JSONObject().put("pl", "3 lata").put("en", "3 years"))
        .put("professional_status", JSONObject().put("pl", "Przykładowe kwalifikacje").put("en", "Example qualifications"))

    @JvmStatic
    fun demoStudentProgrammeStages(): JSONObject = JSONObject()
        .put("stages", JSONArray().put(JSONObject().put("id", "1Z")).put(JSONObject().put("id", "2L")))
        .put("stages_available", true).put("stages_unrecognized", false).put("stages_truncated", false)

    @JvmStatic
    fun demoStudyStage(stageId: String): JSONObject = JSONObject().put("id", stageId)
        .put("name", JSONObject().put("pl", "Przykładowy etap $stageId").put("en", "Example stage $stageId"))

    @JvmStatic
    fun demoCalendarSearch(start: String, end: String): JSONObject {
        require(ToolServiceData.validCalendarRange(start, end))
        val day = LocalDate.now(UsosClient.ZONE).plusDays(5)
        val events = JSONArray()
        if (!day.isBefore(LocalDate.parse(start)) && day.isBefore(LocalDate.parse(end))) {
            events.put(JSONObject().put("id", "cal-1")
                .put("name", JSONObject().put("pl", "Dzień rektorski").put("en", "Rector's Day"))
                .put("start_date", day.toString()).put("end_date", day.toString()).put("type", "rector").put("is_day_off", true))
        }
        return JSONObject().put("events", events).put("truncated", false)
    }

    @JvmStatic
    fun demoCalendarEvent(eventId: String): JSONObject {
        val day = LocalDate.now(UsosClient.ZONE).plusDays(5).toString()
        return JSONObject().put("id", eventId)
            .put("name", JSONObject().put("pl", "Dzień rektorski").put("en", "Rector's Day"))
            .put("start_date", day).put("end_date", day).put("type", "rector").put("is_day_off", true)
            .put("faculty_name", JSONObject().put("pl", "Wydział przykładowy").put("en", "Example faculty"))
    }

    @JvmStatic
    fun demoSurveyDetail(surveyId: String): JSONObject {
        val today = LocalDate.now(UsosClient.ZONE)
        return JSONObject().put("id", surveyId).put("survey_type", "course")
            .put("name", JSONObject().put("pl", "Ankieta oceny zajęć: Programowanie obiektowe").put("en", "Course evaluation: Object-Oriented Programming"))
            .put("headline", "Pomóż nam ulepszyć zajęcia.")
            .put("start_date", today.minusDays(3).toString()).put("end_date", today.plusDays(11).toString())
            .put("lecturer", "Anna Kowalska")
            .put("faculty_name", JSONObject().put("pl", "Wydział przykładowy").put("en", "Example faculty"))
            .put("programme_name", JSONObject())
            .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
            .put("can_i_fill_out", true).put("did_i_fill_out", false).put("has_final_comment", true)
            .put("questions", JSONArray()
                .put(JSONObject().put("number", "1").put("level", 0).put("text", "Czy zajęcia były prowadzone zrozumiale?")
                    .put("answers", JSONArray().put("Tak").put("Raczej tak").put("Raczej nie").put("Nie")).put("allow_comment", true))
                .put(JSONObject().put("number", "2").put("level", 0).put("text", "Czy materiały były pomocne?")
                    .put("answers", JSONArray().put("Tak").put("Nie"))))
            .put("questions_truncated", false)
    }

    @JvmStatic
    fun demoMailMessage(messageId: String): JSONObject {
        val today = LocalDate.now(UsosClient.ZONE)
        return JSONObject().put("id", messageId).put("subject", "Informacja dla grupy laboratoryjnej 1")
            .put("status", "sent").put("date", today.minusDays(2).toString())
            .put("content", "Dzień dobry,\n\nnajbliższe laboratorium odbędzie się w sali 312. Prosimy o zabranie laptopów.\n\nPozdrawiam")
            .put("content_truncated", false).put("recipients_count", 24)
            .put("attachments", JSONArray().put(JSONObject().put("id", "att-1").put("filename", "instrukcja.pdf")
                .put("description", "Instrukcja do laboratorium").put("url", "").put("size", 182400)))
    }

    @JvmStatic
    fun demoMailMessages(messageIds: List<String>): JSONArray = JSONArray().apply {
        for (id in messageIds) put(demoMailMessage(id))
    }

    @JvmStatic
    fun demoMailAttachments(attachmentIds: List<String>): JSONArray = JSONArray().apply {
        for (id in attachmentIds) put(JSONObject().put("id", id).put("filename", "$id.pdf").put("description", "Dokument").put("url", "").put("size", 102400))
    }

    @JvmStatic
    fun demoRemittanceDetail(remittanceId: String): JSONObject = JSONObject().put("id", remittanceId)
        .put("amount", 250.0).put("remaining", 0.0).put("is_settled", true).put("currency", "PLN")
        .put("date", LocalDate.now(UsosClient.ZONE).minusDays(20).toString())
        .put("description", JSONObject().put("pl", "Zwrot nadpłaty").put("en", "Overpayment refund"))
        .put("faculty_name", JSONObject().put("pl", "Wydział przykładowy").put("en", "Example faculty"))

    @JvmStatic
    fun demoExamDetails(examId: String): JSONObject {
        val date = LocalDate.now(UsosClient.ZONE).plusDays(14)
        return JSONObject().put("id", examId)
            .put("name", JSONObject().put("pl", "Egzamin końcowy").put("en", "Final exam"))
            .put("description", JSONObject().put("pl", "Przykładowe dane egzaminu").put("en", "Example exam details"))
            .put("registration_start", "").put("registration_end", "")
            .put("groups", JSONArray().put(JSONObject().put("number", "1")
                .put("exam_start", "$date 10:00:00").put("exam_end", "$date 11:30:00").put("room_number", "312")))
            .put("groups_truncated", false)
    }

    @JvmStatic
    fun demoTokenRegistrationDetails(registrationId: String): JSONObject {
        val start = LocalDate.now(UsosClient.ZONE).plusDays(3)
        return JSONObject().put("id", registrationId)
            .put("description", JSONObject().put("pl", "Rejestracja żetonowa na języki").put("en", "Language token registration"))
            .put("message", JSONObject().put("pl", "Przykładowe dane rejestracji").put("en", "Example registration details"))
            .put("rounds", JSONArray().put(JSONObject().put("id", "demo-round-1")
                .put("name", JSONObject().put("pl", "Tura podstawowa").put("en", "Main round"))
                .put("start_date", "$start 09:00:00").put("end_date", "${start.plusDays(5)} 20:00:00")
                .put("selection_limit", 2).put("is_prioritized", true).put("is_only_entitled", false)))
            .put("rounds_available", true).put("rounds_truncated", false)
    }

    @JvmStatic
    fun demoRoomSchedule(roomId: String): JSONObject {
        val today = LocalDate.now(UsosClient.ZONE)
        return JSONObject()
            .put("id", roomId.ifEmpty { "312" })
            .put("number", roomId.ifEmpty { "312" })
            .put("type", "didactic")
            .put("capacity", 36)
            .put("building_id", "BLD-MAIN")
            .put("building_name", JSONObject().put("pl", "Budynek Główny Kampusu").put("en", "Main Campus Building"))
            .put(
                "lessons",
                JSONArray().put(
                    JSONObject()
                        .put("start_time", "${today.plusDays(1)} 10:00:00")
                        .put("end_time", "${today.plusDays(1)} 11:30:00")
                        .put("name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                        .put("classtype_name", JSONObject().put("pl", "Laboratorium").put("en", "Laboratory"))
                )
            )
    }

    @JvmStatic
    fun demoStaffProfile(userId: String): JSONObject = JSONObject()
        .put("id", userId.ifEmpty { "staff-1" })
        .put("first_name", "Anna")
        .put("last_name", "Kowalska")
        .put("title_before", "dr inż.")
        .put("title_after", "")
        .put("office_hours", JSONObject().put("pl", "Wtorki 14:00–15:30").put("en", "Tuesdays 14:00–15:30"))
        .put("room", "312")

    @JvmStatic
    fun demoStaffSchedule(userId: String): JSONArray {
        val today = LocalDate.now(UsosClient.ZONE)
        return JSONArray().put(
            JSONObject()
                .put("start_time", "${today.plusDays(1)} 10:00:00")
                .put("end_time", "${today.plusDays(1)} 11:30:00")
                .put("name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
                .put("room_number", "312")
                .put("building_name", JSONObject().put("pl", "Budynek Główny").put("en", "Main Building"))
                .put("classtype_name", JSONObject().put("pl", "Wykład").put("en", "Lecture"))
        )
    }

    @JvmStatic
    fun demoGroupDetail(courseUnitId: String, groupNumber: String): JSONObject = JSONObject()
        .put("course_unit_id", courseUnitId.ifEmpty { "CU-501" })
        .put("group_number", groupNumber.ifEmpty { "1" })
        .put("course_id", "INF-201")
        .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
        .put("class_type", JSONObject().put("pl", "Laboratorium").put("en", "Laboratory"))
        .put("term_id", "2026Z")
        .put("participantsTotal", 2)
        .put("participantsTruncated", false)
        .put("participants", JSONArray()
            .put(JSONObject().put("id", "101").put("first_name", "Jan").put("last_name", "Nowak").put("student_number", "550101"))
            .put(JSONObject().put("id", "102").put("first_name", "Maria").put("last_name", "Wiśniewska").put("student_number", "550102"))
        )
        .put("lecturers", JSONArray().put(
            JSONObject().put("id", "staff-1").put("first_name", "Anna").put("last_name", "Kowalska")
        ))

    @JvmStatic
    fun demoNodeDetail(nodeId: String): JSONObject = JSONObject()
        .put("id", nodeId.ifEmpty { "node-task-1" })
        .put("parent_id", "node-root-1")
        .put("name", JSONObject().put("pl", "Kolokwium 1").put("en", "Midterm 1"))
        .put("description", JSONObject().put("pl", "Kolokwium z materiału części 1").put("en", "Part 1 midterm exam"))
        .put("type", "task")
        .put("visible_for_students", true)
        .put("points_min", 0.0)
        .put("points_max", 20.0)
        .put("points_precision", 1)
        .put("grade_type_id", "OCENA")
        .put("permissions", JSONObject().put("can_view", true).put("can_give_points", true).put("can_give_grades", true))
        .put("histogram", JSONArray()
            .put(JSONObject().put("label", "15.0–20.0").put("count", 14))
            .put(JSONObject().put("label", "10.0–14.5").put("count", 8))
            .put(JSONObject().put("label", "0.0–9.5").put("count", 2))
        )

    @JvmStatic
    fun demoDormitoryDetail(dormId: String): JSONObject = JSONObject()
        .put("id", dormId.ifEmpty { "dorm-1" })
        .put("name", JSONObject().put("pl", "Dom Studencki Stokłosy").put("en", "Stokłosy Dormitory"))
        .put("short_name", JSONObject().put("pl", "DS Stokłosy").put("en", "DS Stokłosy"))
        .put("address", "ul. Stokłosy 3, Warszawa")
        .put("description", JSONObject().put("pl", "Pokoje 1- i 2-osobowe z łazienkami, blisko metra i kampusu.").put("en", "Single and double en-suite rooms near metro and campus."))

    @JvmStatic
    fun demoAllocationPeriodDetail(periodId: String): JSONObject = JSONObject()
        .put("id", periodId.ifEmpty { "alloc-2026" })
        .put("term_id", "2026Z")
        .put("status_name", JSONObject().put("pl", "Wnioskowanie otwarte").put("en", "Applications open"))
        .put("status_desc", JSONObject().put("pl", "Można składać wnioski o przyznanie miejsca w domu studenckim.").put("en", "Applications for dorm rooms can be submitted."))
        .put("start_date", "2026-06-01 00:00:00")
        .put("end_date", "2026-09-15 23:59:59")

    @JvmStatic
    fun demoGuidePage(pageId: String): JSONObject = JSONObject()
        .put("id", pageId.ifEmpty { "pg-1" })
        .put("title", JSONObject().put("pl", "Dziekanat i obsługa studenta").put("en", "Dean's office & student support"))
        .put("entries", JSONArray()
            .put(JSONObject().put("id", "ent-1").put("title", JSONObject().put("pl", "Godziny przyjęć").put("en", "Opening hours"))
                .put("content", "Dziekanat przyjmuje od poniedziałku do piątku w godzinach 9:00–15:00 w pokoju 105."))
            .put(JSONObject().put("id", "ent-2").put("title", JSONObject().put("pl", "Wnioski i podania").put("en", "Applications & petitions"))
                .put("content", "Wszystkie podania należy składać za pośrednictwem systemu USOSweb."))
        )

    @JvmStatic
    fun demoThesisDetail(thesisId: String): JSONObject = JSONObject()
        .put("id", thesisId.ifEmpty { "ths-101" })
        .put("type", "engineer")
        .put("title", "Projekt i implementacja mobilnego systemu monitorowania postępów akademickich")
        .put("titles", JSONObject().put("pl", "Projekt i implementacja mobilnego systemu monitorowania postępów akademickich").put("en", "Design and implementation of mobile academic progress tracking system"))
        .put("status", "in_progress")
        .put("faculty_name", JSONObject().put("pl", "Wydział Informatyki").put("en", "Faculty of Computer Science"))
        .put("supervisors", JSONArray().put(JSONObject().put("id", "staff-1").put("first_name", "Anna").put("last_name", "Kowalska")))
        .put("authors", JSONArray().put(JSONObject().put("id", "stud-1").put("first_name", "Jan").put("last_name", "Nowak")))

    @JvmStatic
    fun demoCourseEdition(courseId: String, termId: String): JSONObject = JSONObject()
        .put("course_id", courseId.ifEmpty { "INF-201" })
        .put("term_id", termId.ifEmpty { "2026Z" })
        .put("course_name", JSONObject().put("pl", "Programowanie obiektowe").put("en", "Object-Oriented Programming"))
        .put("term_name", JSONObject().put("pl", "Semestr zimowy 2026/2027").put("en", "Winter semester 2026/2027"))
        .put("coordinators", JSONArray().put(JSONObject().put("id", "staff-1").put("first_name", "Anna").put("last_name", "Kowalska")))
        .put("lecturers", JSONArray().put(JSONObject().put("id", "staff-1").put("first_name", "Anna").put("last_name", "Kowalska")))

}
