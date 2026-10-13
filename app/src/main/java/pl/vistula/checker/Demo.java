package pl.vistula.checker;

import java.time.*;
import org.json.*;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.Diff;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;

final class Demo {
    private Demo() {}
    static JSONObject state(boolean changed) throws JSONException {
        LocalDate date = LocalDate.now(UsosClient.ZONE);
        Snapshot baseline = new Snapshot(date.toString(), date.plusDays(28).toString());
        baseline.add(new Record("grade:demo:1", "ocena", "Podstawy finansów", "4,0 · termin 1", date + " 08:30:00", ""));
        baseline.add(new Record("plan:demo", "plan", "Statystyka opisowa", date.plusDays(1) + " 09:00 – 10:30\nĆwiczenia\nSala 204", "", date.plusDays(1).toString()));
        Snapshot current = new Snapshot(baseline.start, baseline.end);
        current.add(new Record("grade:demo:1", "ocena", "Podstawy finansów", "4,5 · termin 1", date + " 10:20:00", ""));
        current.add(new Record("plan:demo", "plan", "Statystyka opisowa", date.plusDays(1) + " 09:00 – 10:30\nĆwiczenia\nSala 315", "", date.plusDays(1).toString()));
        JSONArray history = new JSONArray();
        if (changed) for (Change c : Diff.between(baseline, current, System.currentTimeMillis(), date.toString())) history.put(c.json());
        return new JSONObject().put("user", "Konto demonstracyjne").put("successAt", System.currentTimeMillis())
            .put("snapshot", (changed ? current : baseline).json()).put("history", history);
    }
}
