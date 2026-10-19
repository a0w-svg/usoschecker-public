package pl.vistula.checker.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Consumer
import org.json.JSONArray
import org.json.JSONException

/** Fetches every seven-day page before returning any timetable data. */
object TimetableLoader {
    fun interface Source {
        @Throws(Exception::class)
        fun fetch(start: LocalDate, days: Int): JSONArray
    }

    @JvmStatic
    @Throws(Exception::class)
    fun load(start: LocalDate, days: Int, source: Source, progress: Consumer<String>): List<JSONArray> {
        if (days < 7 || days > 112 || days % 7 != 0) {
            throw IllegalArgumentException("Nieprawidłowy zakres planu.")
        }
        val pool = Executors.newFixedThreadPool(3)
        val pending = ArrayList<Future<JSONArray>>()
        try {
            val pages = days / 7
            progress.accept("Pobieranie planu · 0/$pages tygodni")
            for (i in 0 until pages) {
                val pageStart = start.plusDays(i * 7L)
                pending.add(
                    pool.submit(
                        Callable {
                            val rows = source.fetch(pageStart, 7)
                            for (n in 0 until rows.length()) {
                                val day = LocalDateTime.parse(rows.getJSONObject(n).getString("start_time"), UsosParser.TIME).toLocalDate()
                                if (day.isBefore(pageStart) || !day.isBefore(pageStart.plusDays(7))) {
                                    throw JSONException("USOS zwrócił zajęcia spoza żądanego tygodnia.")
                                }
                            }
                            rows
                        }
                    )
                )
            }
            val result = ArrayList<JSONArray>()
            for (i in pending.indices) {
                try {
                    result.add(pending[i].get())
                } catch (e: ExecutionException) {
                    val cause = e.cause
                    if (cause is Exception) throw cause
                    throw e
                }
                progress.accept("Pobieranie planu · ${i + 1}/$pages tygodni")
            }
            return result
        } finally {
            for (future in pending) future.cancel(true)
            pool.shutdownNow()
        }
    }
}

