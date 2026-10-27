package pl.vistula.checker.core

import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/** Monotonic budget checked between network stages and streamed response reads. */
class RequestBudget(private val durationMs: Long = 45_000L, private val nanos: () -> Long = System::nanoTime) {
    private val start = nanos()

    fun expired(): Boolean = (nanos() - start) / 1_000_000L >= durationMs

    fun readTimeout(): Int {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Request cancelled")
        val remaining = durationMs - (nanos() - start) / 1_000_000L
        if (remaining <= 0) throw SocketTimeoutException("Request deadline exceeded")
        return minOf(20_000L, remaining).toInt().coerceAtLeast(1)
    }
}
