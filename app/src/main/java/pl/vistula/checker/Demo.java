package pl.vistula.checker;

import java.time.*;
import org.json.*;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.Diff;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;

final class Demo {
    private Demo() {}
    static JSONObject state(boolean changed) throws JSONException { return state(changed, 56); }
    static JSONObject state(boolean changed, int days) throws JSONException {
        LocalDate date = LocalDate.now(UsosClient.ZONE);
        Snapshot baseline = new Snapshot(date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(), date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(days).toString());
        baseline.add(new Record("grade:demo:1", "ocena", "Podstawy finansów", "4,0 · termin 1", date + " 08:30:00", "", "2026Z"));
        baseline.add(new Record("plan:demo", "plan", "Statystyka opisowa", date.plusDays(1) + " 09:00 – 10:30\nĆwiczenia\nSala 204\nProwadzący: Anna Kowalska", "", date.plusDays(1).toString()));
        Snapshot current = new Snapshot(baseline.start, baseline.end);
        current.add(new Record("grade:demo:1", "ocena", "Podstawy finansów", "4,5 · termin 1", date + " 10:20:00", "", "2026Z"));
        current.add(new Record("plan:demo", "plan", "Statystyka opisowa", date.plusDays(1) + " 09:00 – 10:30\nĆwiczenia\nSala 315\nProwadzący: Anna Kowalska", "", date.plusDays(1).toString()));
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
        Record english = new Record("grade:demo:2", "ocena", "Język angielski", "ZAL · termin 1", date + " 08:00:00", "", "2025L");
        baseline.add(english); current.add(english);
        JSONArray history = new JSONArray();
        if (changed) for (Change c : Diff.between(baseline, current, System.currentTimeMillis(), date.toString())) history.put(c.json());
        return new JSONObject().put("user", "Konto demonstracyjne").put("successAt", System.currentTimeMillis())
            .put("snapshot", (changed ? current : baseline).json()).put("history", history);
    }
}
