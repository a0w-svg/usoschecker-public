package pl.vistula.checker.core

import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * When a failed USOS request may be repeated transparently.
 *
 * Only idempotent GETs are retried, at most [MAX_RETRIES] times, and only for network-level failures that usually
 * clear within seconds: connect or read timeouts and dropped connections. Any HTTP answer, including 429 and 5xx,
 * is final: the server has spoken, so the app honours its cooldown through [ApiFailure.retryAt] instead of asking
 * again straight away.
 */
object RetryPolicy {
    const val MAX_RETRIES = 2
    private val BACKOFF_MILLIS = longArrayOf(400L, 1_200L)

    @JvmStatic
    fun allows(method: String, attempt: Int): Boolean = method == "GET" && attempt < MAX_RETRIES

    @JvmStatic
    fun retryableNetworkError(error: Throwable): Boolean =
        error is SocketTimeoutException || error is ConnectException || (error is SocketException && error !is java.net.BindException)

    /** Delay before retry number [attempt] (0 for the first retry), plus [jitter] in 0..1 to avoid synchronised retries. */
    @JvmStatic
    @JvmOverloads
    fun delayMillis(attempt: Int, jitter: Double = 0.0): Long =
        BACKOFF_MILLIS[attempt.coerceIn(0, BACKOFF_MILLIS.size - 1)] + (jitter.coerceIn(0.0, 1.0) * 150).toLong()
}
