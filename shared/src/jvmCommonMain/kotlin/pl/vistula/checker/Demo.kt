package pl.vistula.checker

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.core.AcademicTerm
import pl.vistula.checker.core.CourseTestRecord
import pl.vistula.checker.core.Diff
import pl.vistula.checker.core.NewsArticle
import pl.vistula.checker.core.PaymentInfo
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.core.ThesisInfo
import pl.vistula.checker.core.UsosParser

object Demo {
    @JvmStatic
    @JvmOverloads
    @Throws(JSONException::class)
    fun state(changed: Boolean, days: Int = 56): JSONObject {
        val date = LocalDate.now(UsosClient.ZONE)
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val baseline = Snapshot(monday.toString(), monday.plusDays(days.toLong()).toString())
        val gradeMetaOld = JSONObject()
            .put("courseId", "FIN-101").put("examId", "4201").put("sessionNumber", "1")
            .put("term", "2026Z").put("valueSymbol", "4,0").put("valueDescription", "dobry")
            .put("passes", true).put("countsIntoAverage", true).put("ects", 5.0)
            .put("gradeTypeId", "OCENA").put("classType", "Wykład")
            .put("dateAcquisition", "$date 08:30:00")
            .put("modificationAuthor", "1048")
        baseline.add(Record("grade:demo:1", "ocena", "Podstawy finansów", "4,0 · termin 1", "$date 08:30:00", "", "2026Z", "", "", 5.0, gradeMetaOld))

        val planMetaOld = JSONObject()
            .put("courseId", "STAT-202").put("room", "204").put("roomId", "R-204").put("building", "Budynek Główny").put("buildingId", "B-MAIN")
            .put("classType", "Ćwiczenia").put("classTypeId", "CW").put("groupNumber", "1").put("lecturers", "Anna Kowalska")
            .put("type", "classgroup2").put("startTime", "${date.plusDays(1)} 09:00:00").put("endTime", "${date.plusDays(1)} 10:30:00")
            .put("date", date.plusDays(1).toString())
        baseline.add(
            Record(
                "plan:demo", "plan", "Statystyka opisowa",
                "${date.plusDays(1)} 09:00 – 10:30\nĆwiczenia\nSala 204\nProwadzący: Anna Kowalska",
                "", date.plusDays(1).toString(), "",
                "${date.plusDays(1)} 09:00:00", "${date.plusDays(1)} 10:30:00", Double.NaN, planMetaOld
            )
        )

        val current = Snapshot(baseline.start, baseline.end)
        val gradeMetaNew = JSONObject()
            .put("courseId", "FIN-101").put("examId", "4201").put("sessionNumber", "1")
            .put("term", "2026Z").put("valueSymbol", "4,5").put("valueDescription", "bardzo dobry")
            .put("passes", true).put("countsIntoAverage", true).put("ects", 5.0)
            .put("gradeTypeId", "OCENA").put("classType", "Wykład")
            .put("dateAcquisition", "$date 08:30:00")
            .put("modificationAuthor", "1048")
            .put("comment", "Poprawa oceny po dodatkowym kolokwium")
        current.add(
            Record(
                "grade:demo:1", "ocena", "Podstawy finansów",
                "4,5 · termin 1\nPoprawa oceny po dodatkowym kolokwium",
                "$date 10:20:00", "", "2026Z", "", "", 5.0, gradeMetaNew
            )
        )

        val finCwMeta = JSONObject()
            .put("courseId", "FIN-101").put("examId", "4202").put("sessionNumber", "1")
            .put("term", "2026Z").put("valueSymbol", "5,0").put("valueDescription", "bardzo dobry")
            .put("passes", true).put("countsIntoAverage", true).put("ects", 2.0)
            .put("gradeTypeId", "OCENA").put("classType", "Ćwiczenia")
            .put("dateAcquisition", "$date 09:00:00").put("modificationAuthor", "1048")
            .put("comment", "Zaliczenie ćwiczeń rachunkowych")
        val finCw = Record(
            "grade:demo:fin:cw", "ocena", "Podstawy finansów",
            "5,0 · termin 1\nZaliczenie ćwiczeń rachunkowych",
            "$date 09:00:00", "", "2026Z", "", "", 2.0, finCwMeta
        )
        baseline.add(finCw)
        current.add(finCw)

        val progLabMeta = JSONObject()
            .put("courseId", "INF-201").put("examId", "5101").put("sessionNumber", "1")
            .put("term", "2026Z").put("valueSymbol", "5,0").put("valueDescription", "bardzo dobry")
            .put("passes", true).put("countsIntoAverage", true).put("ects", 3.0)
            .put("gradeTypeId", "OCENA").put("classType", "Laboratorium")
            .put("dateAcquisition", "$date 11:00:00").put("modificationAuthor", "2031")
            .put("comment", "Zaliczone projekty laboratoryjne 1–4")
        val progLab = Record(
            "grade:demo:prog:lab", "ocena", "Programowanie obiektowe",
            "5,0 · termin 1\nZaliczone projekty laboratoryjne 1–4",
            "$date 11:00:00", "", "2026Z", "", "", 3.0, progLabMeta
        )
        baseline.add(progLab)
        current.add(progLab)

        val progWykMeta = JSONObject()
            .put("courseId", "INF-201").put("examId", "5102").put("sessionNumber", "1")
            .put("term", "2026Z").put("valueSymbol", "4,5").put("valueDescription", "bardzo dobry")
            .put("passes", true).put("countsIntoAverage", true).put("ects", 3.0)
            .put("gradeTypeId", "OCENA").put("classType", "Wykład")
            .put("dateAcquisition", "$date 12:00:00").put("modificationAuthor", "2031")
        val progWyk = Record(
            "grade:demo:prog:wyk", "ocena", "Programowanie obiektowe",
            "4,5 · termin 1", "$date 12:00:00", "", "2026Z", "", "", 3.0, progWykMeta
        )
        baseline.add(progWyk)
        current.add(progWyk)

        val planMetaNew = JSONObject()
            .put("courseId", "STAT-202").put("room", "315").put("roomId", "R-315").put("building", "Budynek Główny").put("buildingId", "B-MAIN")
            .put("classType", "Ćwiczenia").put("classTypeId", "CW").put("groupNumber", "1").put("lecturers", "Anna Kowalska")
            .put("type", "classgroup2").put("startTime", "${date.plusDays(1)} 09:00:00").put("endTime", "${date.plusDays(1)} 10:30:00")
            .put("date", date.plusDays(1).toString())
        current.add(
            Record(
                "plan:demo", "plan", "Statystyka opisowa",
                "${date.plusDays(1)} 09:00 – 10:30\nĆwiczenia\nSala 315\nProwadzący: Anna Kowalska",
                "", date.plusDays(1).toString(), "",
                "${date.plusDays(1)} 09:00:00", "${date.plusDays(1)} 10:30:00", Double.NaN, planMetaNew
            )
        )
        val now = LocalDateTime.now(UsosClient.ZONE).withSecond(0).withNano(0)
        var lectureStart = now.minusMinutes(15)
        if (lectureStart.toLocalDate().isBefore(date)) lectureStart = date.atStartOfDay()
        val lectureEnd = now.plusMinutes(45)
        val active = Record(
            "plan:demo:current", "plan", "Ekonomia",
            "${lectureStart.toLocalTime()} – ${lectureEnd.toLocalTime()}\nWykład\nSala 101\nProwadzący: Jan Nowak",
            "", date.toString(), "", lectureStart.format(UsosParser.TIME), lectureEnd.format(UsosParser.TIME)
        )
        baseline.add(active)
        current.add(active)
        for (i in 1 until days / 7) {
            val next = LocalDate.parse(baseline.start).plusDays(i * 7L + 1)
            val future = Record(
                "plan:demo:week:$i", "plan", "Statystyka opisowa",
                "09:00 – 10:30\nĆwiczenia\nSala 204\nProwadzący: Anna Kowalska", "", next.toString()
            )
            baseline.add(future)
            current.add(future)
        }
        val english = Record("grade:demo:2", "ocena", "Język angielski", "ZAL · termin 1", "$date 08:00:00", "", "2025L", "", "", 2.0)
        baseline.add(english)
        current.add(english)
        val math = Record("grade:demo:3", "ocena", "Matematyka dyskretna", "5,0 · termin 1", "$date 08:00:00", "", "2025L", "", "", 4.0)
        baseline.add(math)
        current.add(math)

        val demoPayments = PaymentInfo(
            listOf(PaymentInfo.Account("acc:tuition", "Subkonto czesnego (Vistula)", "PLN", "PL42 1020 1026 0000 1234 5678 9012", 0.0)),
            listOf(
                PaymentInfo.Installment("inst:1", "Czesne – semestr zimowy (rata 1/4)", 850.0, "PLN", date.minusDays(5).toString(), "Opłacona", date.minusDays(7).toString()),
                PaymentInfo.Installment("inst:2", "Czesne – semestr zimowy (rata 2/4)", 850.0, "PLN", date.plusDays(25).toString(), "Do zapłaty", ""),
                PaymentInfo.Installment("inst:3", "Czesne – semestr zimowy (rata 3/4)", 850.0, "PLN", date.plusDays(55).toString(), "Zaplanowana", ""),
                PaymentInfo.Installment("inst:4", "Czesne – semestr zimowy (rata 4/4)", 850.0, "PLN", date.plusDays(85).toString(), "Zaplanowana", "")
            )
        )
        baseline.payments = demoPayments
        current.payments = demoPayments

        val term2026Z = AcademicTerm("2026Z", "Semestr zimowy 2026/2027", date.withDayOfMonth(1).toString(), date.plusMonths(4).toString(), date.plusMonths(9).toString())
        val term2025L = AcademicTerm("2025L", "Semestr letni 2025/2026", date.minusMonths(6).toString(), date.minusDays(1).toString(), date.minusDays(1).toString())
        baseline.terms["2026Z"] = term2026Z
        baseline.terms["2025L"] = term2025L
        current.terms["2026Z"] = term2026Z
        current.terms["2025L"] = term2025L

        val demoThesis = ThesisInfo(
            "Projekt i implementacja mobilnego systemu monitorowania postępów akademickich",
            "W trakcie realizacji",
            "dr inż. Marek Zieliński",
            "prof. dr hab. Andrzej Wiśniewski"
        )
        baseline.thesis = demoThesis
        current.thesis = demoThesis

        val demoTests = listOf(
            CourseTestRecord("test:1", "FIN-101", "Podstawy finansów", "Kolokwium 1 – Sprawozdawczość", 18.5, 20.0, "5.0", date.minusDays(6).toString()),
            CourseTestRecord("test:2", "STAT-202", "Statystyka opisowa", "Sprawdzian cząstkowy – Zmienne losowe", 14.0, 15.0, "4.5", date.minusDays(2).toString()),
            CourseTestRecord("test:3", "INF-201", "Programowanie obiektowe", "Kolokwium laboratoryjne – Wzorce projektowe", 19.0, 20.0, "5.0", date.minusDays(4).toString())
        )
        baseline.tests.addAll(demoTests)
        current.tests.addAll(demoTests)

        val demoNews = listOf(
            NewsArticle(
                "news:1", "Godziny rektorskie w dniu 31 października", date.minusDays(3).toString(),
                "Biuro Rektora AFiBV",
                "Zarządzeniem Rektora ogłasza się godziny wolne od zajęć dydaktycznych dla studentów wszystkich kierunków.",
                "https://vistula.edu.pl"
            ),
            NewsArticle(
                "news:2", "Harmonogram składania wniosków o stypendium rektora", date.minusDays(10).toString(),
                "Dział Spraw Studenckich",
                "Przypominamy o terminie składania wniosków o stypendia za wyniki w nauce na semestr zimowy.",
                "https://vistula.edu.pl"
            )
        )
        baseline.news.addAll(demoNews)
        current.news.addAll(demoNews)

        val history = JSONArray()
        if (changed) {
            for (c in Diff.between(baseline, current, System.currentTimeMillis(), date.toString())) {
                history.put(c.json())
            }
        }
        return JSONObject()
            .put("user", "Konto demonstracyjne")
            .put("studentNumber", "35892")
            .put("email", "j.kowalski@vistula.edu.pl")
            .put("programme", "Informatyka · studia inżynierskie")
            .put("studentStatus", "Aktywny student")
            .put("stage", "Semestr 5")
            .put("admissionDate", "2024-10-01")
            .put("successAt", System.currentTimeMillis())
            .put("snapshot", (if (changed) current else baseline).json())
            .put("history", history)
    }
}

