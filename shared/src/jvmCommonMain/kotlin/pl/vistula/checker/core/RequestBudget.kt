package pl.vistula.checker.core

import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/** Monotonic budget checked between network stages and streamed response reads. */
class RequestBudget(private val nanos: () -> Long = System::nanoTime) {
    private val start = nanos()

    fun readTimeout(): Int {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Request cancelled")
        val remaining = 45_000L - (nanos() - start) / 1_000_000L
        if (remaining <= 0) throw SocketTimeoutException("Request deadline exceeded")
        return minOf(20_000L, remaining).toInt().coerceAtLeast(1)
    }
}
