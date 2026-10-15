package pl.vistula.checker;

import java.time.*;
import org.json.*;
import java.util.List;
import pl.vistula.checker.core.AcademicTerm;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.CourseTestRecord;
import pl.vistula.checker.core.Diff;
import pl.vistula.checker.core.NewsArticle;
import pl.vistula.checker.core.PaymentInfo;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;
import pl.vistula.checker.core.ThesisInfo;

public final class Demo {
    private Demo() {}
    public static JSONObject state(boolean changed) throws JSONException { return state(changed, 56); }
    public static JSONObject state(boolean changed, int days) throws JSONException {
        LocalDate date = LocalDate.now(UsosClient.ZONE);
        Snapshot baseline = new Snapshot(date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(), date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(days).toString());
        JSONObject gradeMetaOld = new JSONObject()
            .put("courseId", "FIN-101").put("examId", "4201").put("sessionNumber", "1")
            .put("term", "2026Z").put("valueSymbol", "4,0").put("valueDescription", "dobry")
            .put("passes", true).put("countsIntoAverage", true).put("ects", 5.0)
            .put("gradeTypeId", "OCENA").put("classType", "Wykład")
            .put("dateAcquisition", date + " 08:30:00")
            .put("modificationAuthor", "1048");
        baseline.add(new Record("grade:demo:1", "ocena", "Podstawy finansów", "4,0 · termin 1", date + " 08:30:00", "", "2026Z", "", "", 5.0, gradeMetaOld));

        JSONObject planMetaOld = new JSONObject()
            .put("courseId", "STAT-202").put("room", "204").put("roomId", "R-204").put("building", "Budynek Główny").put("buildingId", "B-MAIN")
            .put("classType", "Ćwiczenia").put("classTypeId", "CW").put("groupNumber", "1").put("lecturers", "Anna Kowalska")
            .put("type", "classgroup2").put("startTime", date.plusDays(1) + " 09:00:00").put("endTime", date.plusDays(1) + " 10:30:00")
            .put("date", date.plusDays(1).toString());
        baseline.add(new Record("plan:demo", "plan", "Statystyka opisowa", date.plusDays(1) + " 09:00 – 10:30\nĆwiczenia\nSala 204\nProwadzący: Anna Kowalska", "", date.plusDays(1).toString(), "", date.plusDays(1) + " 09:00:00", date.plusDays(1) + " 10:30:00", Double.NaN, planMetaOld));

        Snapshot current = new Snapshot(baseline.start, baseline.end);
        JSONObject gradeMetaNew = new JSONObject()
            .put("courseId", "FIN-101").put("examId", "4201").put("sessionNumber", "1")
            .put("term", "2026Z").put("valueSymbol", "4,5").put("valueDescription", "bardzo dobry")
            .put("passes", true).put("countsIntoAverage", true).put("ects", 5.0)
            .put("gradeTypeId", "OCENA").put("classType", "Wykład")
            .put("dateAcquisition", date + " 08:30:00")
            .put("modificationAuthor", "1048")
            .put("comment", "Poprawa oceny po dodatkowym kolokwium");
        current.add(new Record("grade:demo:1", "ocena", "Podstawy finansów", "4,5 · termin 1\nPoprawa oceny po dodatkowym kolokwium", date + " 10:20:00", "", "2026Z", "", "", 5.0, gradeMetaNew));

        JSONObject finCwMeta = new JSONObject()
            .put("courseId", "FIN-101").put("examId", "4202").put("sessionNumber", "1")
            .put("term", "2026Z").put("valueSymbol", "5,0").put("valueDescription", "bardzo dobry")
            .put("passes", true).put("countsIntoAverage", true).put("ects", 2.0)
            .put("gradeTypeId", "OCENA").put("classType", "Ćwiczenia")
            .put("dateAcquisition", date + " 09:00:00").put("modificationAuthor", "1048")
            .put("comment", "Zaliczenie ćwiczeń rachunkowych");
        Record finCw = new Record("grade:demo:fin:cw", "ocena", "Podstawy finansów", "5,0 · termin 1\nZaliczenie ćwiczeń rachunkowych", date + " 09:00:00", "", "2026Z", "", "", 2.0, finCwMeta);
        baseline.add(finCw); current.add(finCw);

        JSONObject progLabMeta = new JSONObject()
            .put("courseId", "INF-201").put("examId", "5101").put("sessionNumber", "1")
            .put("term", "2026Z").put("valueSymbol", "5,0").put("valueDescription", "bardzo dobry")
            .put("passes", true).put("countsIntoAverage", true).put("ects", 3.0)
            .put("gradeTypeId", "OCENA").put("classType", "Laboratorium")
            .put("dateAcquisition", date + " 11:00:00").put("modificationAuthor", "2031")
            .put("comment", "Zaliczone projekty laboratoryjne 1–4");
        Record progLab = new Record("grade:demo:prog:lab", "ocena", "Programowanie obiektowe", "5,0 · termin 1\nZaliczone projekty laboratoryjne 1–4", date + " 11:00:00", "", "2026Z", "", "", 3.0, progLabMeta);
        baseline.add(progLab); current.add(progLab);

        JSONObject progWykMeta = new JSONObject()
            .put("courseId", "INF-201").put("examId", "5102").put("sessionNumber", "1")
            .put("term", "2026Z").put("valueSymbol", "4,5").put("valueDescription", "bardzo dobry")
            .put("passes", true).put("countsIntoAverage", true).put("ects", 3.0)
            .put("gradeTypeId", "OCENA").put("classType", "Wykład")
            .put("dateAcquisition", date + " 12:00:00").put("modificationAuthor", "2031");
        Record progWyk = new Record("grade:demo:prog:wyk", "ocena", "Programowanie obiektowe", "4,5 · termin 1", date + " 12:00:00", "", "2026Z", "", "", 3.0, progWykMeta);
        baseline.add(progWyk); current.add(progWyk);

        JSONObject planMetaNew = new JSONObject()
            .put("courseId", "STAT-202").put("room", "315").put("roomId", "R-315").put("building", "Budynek Główny").put("buildingId", "B-MAIN")
            .put("classType", "Ćwiczenia").put("classTypeId", "CW").put("groupNumber", "1").put("lecturers", "Anna Kowalska")
            .put("type", "classgroup2").put("startTime", date.plusDays(1) + " 09:00:00").put("endTime", date.plusDays(1) + " 10:30:00")
            .put("date", date.plusDays(1).toString());
        current.add(new Record("plan:demo", "plan", "Statystyka opisowa", date.plusDays(1) + " 09:00 – 10:30\nĆwiczenia\nSala 315\nProwadzący: Anna Kowalska", "", date.plusDays(1).toString(), "", date.plusDays(1) + " 09:00:00", date.plusDays(1) + " 10:30:00", Double.NaN, planMetaNew));
        LocalDateTime now = LocalDateTime.now(UsosClient.ZONE).withSecond(0).withNano(0);
        LocalDateTime lectureStart = now.minusMinutes(15); if (lectureStart.toLocalDate().isBefore(date)) lectureStart = date.atStartOfDay();
        LocalDateTime lectureEnd = now.plusMinutes(45);
        Record active = new Record("plan:demo:current", "plan", "Ekonomia", lectureStart.toLocalTime() + " – " + lectureEnd.toLocalTime()
            + "\nWykład\nSala 101\nProwadzący: Jan Nowak", "", date.toString(), "", lectureStart.format(pl.vistula.checker.core.UsosParser.TIME), lectureEnd.format(pl.vistula.checker.core.UsosParser.TIME));
        baseline.add(active); current.add(active);
        for (int i = 1; i < days / 7; i++) {
            LocalDate next = LocalDate.parse(baseline.start).plusDays(i * 7L + 1);
            Record future = new Record("plan:demo:week:" + i, "plan", "Statystyka opisowa",
                "09:00 – 10:30\nĆwiczenia\nSala 204\nProwadzący: Anna Kowalska", "", next.toString());
            baseline.add(future); current.add(future);
        }
        Record english = new Record("grade:demo:2", "ocena", "Język angielski", "ZAL · termin 1", date + " 08:00:00", "", "2025L", "", "", 2.0);
        baseline.add(english); current.add(english);
        Record math = new Record("grade:demo:3", "ocena", "Matematyka dyskretna", "5,0 · termin 1", date + " 08:00:00", "", "2025L", "", "", 4.0);
        baseline.add(math); current.add(math);

        PaymentInfo demoPayments = new PaymentInfo(
            List.of(new PaymentInfo.Account("acc:tuition", "Subkonto czesnego (Vistula)", "PLN", "PL42 1020 1026 0000 1234 5678 9012", 0.0)),
            List.of(
                new PaymentInfo.Installment("inst:1", "Czesne – semestr zimowy (rata 1/4)", 850.0, "PLN", date.minusDays(5).toString(), "Opłacona", date.minusDays(7).toString()),
                new PaymentInfo.Installment("inst:2", "Czesne – semestr zimowy (rata 2/4)", 850.0, "PLN", date.plusDays(25).toString(), "Do zapłaty", ""),
                new PaymentInfo.Installment("inst:3", "Czesne – semestr zimowy (rata 3/4)", 850.0, "PLN", date.plusDays(55).toString(), "Zaplanowana", ""),
                new PaymentInfo.Installment("inst:4", "Czesne – semestr zimowy (rata 4/4)", 850.0, "PLN", date.plusDays(85).toString(), "Zaplanowana", "")
            )
        );
        baseline.payments = demoPayments; current.payments = demoPayments;

        AcademicTerm term2026Z = new AcademicTerm("2026Z", "Semestr zimowy 2026/2027", date.withDayOfMonth(1).toString(), date.plusMonths(4).toString(), date.plusMonths(9).toString());
        AcademicTerm term2025L = new AcademicTerm("2025L", "Semestr letni 2025/2026", date.minusMonths(6).toString(), date.minusDays(1).toString(), date.minusDays(1).toString());
        baseline.terms.put("2026Z", term2026Z); baseline.terms.put("2025L", term2025L);
        current.terms.put("2026Z", term2026Z); current.terms.put("2025L", term2025L);

        ThesisInfo demoThesis = new ThesisInfo("Projekt i implementacja mobilnego systemu monitorowania postępów akademickich", "W trakcie realizacji", "dr inż. Marek Zieliński", "prof. dr hab. Andrzej Wiśniewski");
        baseline.thesis = demoThesis; current.thesis = demoThesis;

        List<CourseTestRecord> demoTests = List.of(
            new CourseTestRecord("test:1", "FIN-101", "Podstawy finansów", "Kolokwium 1 – Sprawozdawczość", 18.5, 20.0, "5.0", date.minusDays(6).toString()),
            new CourseTestRecord("test:2", "STAT-202", "Statystyka opisowa", "Sprawdzian cząstkowy – Zmienne losowe", 14.0, 15.0, "4.5", date.minusDays(2).toString()),
            new CourseTestRecord("test:3", "INF-201", "Programowanie obiektowe", "Kolokwium laboratoryjne – Wzorce projektowe", 19.0, 20.0, "5.0", date.minusDays(4).toString())
        );
        baseline.tests.addAll(demoTests); current.tests.addAll(demoTests);

        List<NewsArticle> demoNews = List.of(
            new NewsArticle("news:1", "Godziny rektorskie w dniu 31 października", date.minusDays(3).toString(), "Biuro Rektora AFiBV", "Zarządzeniem Rektora ogłasza się godziny wolne od zajęć dydaktycznych dla studentów wszystkich kierunków.", "https://vistula.edu.pl"),
            new NewsArticle("news:2", "Harmonogram składania wniosków o stypendium rektora", date.minusDays(10).toString(), "Dział Spraw Studenckich", "Przypominamy o terminie składania wniosków o stypendia za wyniki w nauce na semestr zimowy.", "https://vistula.edu.pl")
        );
        baseline.news.addAll(demoNews); current.news.addAll(demoNews);

        JSONArray history = new JSONArray();
        if (changed) for (Change c : Diff.between(baseline, current, System.currentTimeMillis(), date.toString())) history.put(c.json());
        return new JSONObject()
            .put("user", "Konto demonstracyjne")
            .put("studentNumber", "35892")
            .put("email", "j.kowalski@vistula.edu.pl")
            .put("programme", "Informatyka · studia inżynierskie")
            .put("studentStatus", "Aktywny student")
            .put("stage", "Semestr 5")
            .put("admissionDate", "2024-10-01")
            .put("successAt", System.currentTimeMillis())
            .put("snapshot", (changed ? current : baseline).json())
            .put("history", history);
    }
}
