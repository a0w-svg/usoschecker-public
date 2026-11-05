package pl.vistula.checker

import org.json.JSONObject

/**
 * Remembers the last decrypted state together with a cheap change stamp of the file it came from, so repeated
 * reads (the foreground refresh, receivers, workers) skip decryption and file IO while nothing has changed.
 * A stamp of -1 means "unknown": nothing is cached or served. Callers always get a fresh copy to mutate.
 */
class StateCache {
    private var stamp = NO_STAMP
    private var raw: String? = null

    @Synchronized
    fun get(currentStamp: Long): JSONObject? {
        if (currentStamp == NO_STAMP || currentStamp != stamp) return null
        return raw?.let { JSONObject(it) }
    }

    @Synchronized
    fun put(currentStamp: Long, state: JSONObject) {
        if (currentStamp == NO_STAMP) {
            invalidate()
            return
        }
        stamp = currentStamp
        raw = state.toString()
    }

    @Synchronized
    fun invalidate() {
        stamp = NO_STAMP
        raw = null
    }

    companion object {
        const val NO_STAMP = -1L

        /** Combines modification time and size of a state file; both zero (missing file) is "unknown". */
        @JvmStatic
        fun stampOf(lastModified: Long, length: Long): Long =
            if (lastModified <= 0L && length <= 0L) NO_STAMP else lastModified * 31 + length
    }
}
