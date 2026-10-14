package pl.vistula.checker.core;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;

/** Fetches every seven-day page before returning any timetable data. */
public final class TimetableLoader {
    public interface Source { JSONArray fetch(LocalDate start, int days) throws Exception; }
    private TimetableLoader() {}
    public static List<JSONArray> load(LocalDate start, int days, Source source, Consumer<String> progress) throws Exception {
        if (days < 7 || days > 112 || days % 7 != 0) throw new IllegalArgumentException("Nieprawidłowy zakres planu.");
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<JSONArray>> pending = new ArrayList<>();
        try {
            int pages = days / 7;
            progress.accept("Pobieranie planu · 0/" + pages + " tygodni");
            for (int i = 0; i < pages; i++) {
                LocalDate pageStart = start.plusDays(i * 7L);
                pending.add(pool.submit(() -> {
                    JSONArray rows = source.fetch(pageStart, 7);
                    for (int n = 0; n < rows.length(); n++) {
                        LocalDate day = LocalDateTime.parse(rows.getJSONObject(n).getString("start_time"), UsosParser.TIME).toLocalDate();
                        if (day.isBefore(pageStart) || !day.isBefore(pageStart.plusDays(7)))
                            throw new JSONException("USOS zwrócił zajęcia spoza żądanego tygodnia.");
                    }
                    return rows;
                }));
            }
            List<JSONArray> result = new ArrayList<>();
            for (int i = 0; i < pending.size(); i++) {
                try { result.add(pending.get(i).get()); }
                catch (ExecutionException e) { if (e.getCause() instanceof Exception) throw (Exception) e.getCause(); throw e; }
                progress.accept("Pobieranie planu · " + (i + 1) + "/" + pages + " tygodni");
            }
            return result;
        } finally {
            for (Future<JSONArray> future : pending) future.cancel(true);
            pool.shutdownNow();
        }
    }
}