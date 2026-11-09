package pl.vistula.checker.core

import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Small helpers for running independent network reads at the same time with a fixed upper bound. */
object Parallel {
    /**
     * Runs [work] for every item with at most [threads] running at once and returns the results in input order.
     * An [IOException] from one item yields null for that item only; any other exception (for example an expired
     * session) cancels the rest and is rethrown, so callers keep their usual failure handling.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun <T, R> map(items: List<T>, threads: Int, work: (T) -> R): List<R?> {
        if (items.isEmpty()) return emptyList()
        if (items.size == 1 || threads <= 1) return items.map { runOne(work, it) }
        val pool = Executors.newFixedThreadPool(minOf(threads, items.size)) { r -> Thread(r, "parallel-read").apply { isDaemon = true } }
        val futures = ArrayList<Future<R?>>(items.size)
        try {
            for (item in items) futures.add(pool.submit(Callable { runOne(work, item) }))
            return futures.map { await(it) }
        } finally {
            for (f in futures) f.cancel(true)
            pool.shutdownNow()
        }
    }

    /** Runs [block] on its own thread; [await] returns its result or rethrows its exception. */
    class Started<T> internal constructor(private val future: Future<T>) {
        @Throws(Exception::class)
        fun await(): T = await(future)
    }

    @JvmStatic
    fun <T> start(block: () -> T): Started<T> {
        val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "parallel-read").apply { isDaemon = true } }
        val future = pool.submit(Callable { block() })
        pool.shutdown()
        return Started(future)
    }

    private fun <T, R> runOne(work: (T) -> R, item: T): R? = try {
        work(item)
    } catch (e: IOException) {
        null
    }

    @Throws(Exception::class)
    private fun <R> await(future: Future<R>): R = try {
        future.get()
    } catch (e: ExecutionException) {
        throw (e.cause as? Exception) ?: e
    }
}
