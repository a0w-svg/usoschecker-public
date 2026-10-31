package pl.vistula.checker

import android.util.Log

/** Records failures that are deliberately tolerated so they stay visible while debugging. */
object Diagnostics {
    /**
     * Logs only the exception type: messages can carry URLs or response bodies, which must not reach logcat.
     */
    @JvmStatic
    fun swallowed(tag: String, error: Throwable) {
        Log.d(tag, "Ignored " + error.javaClass.simpleName)
    }
}
