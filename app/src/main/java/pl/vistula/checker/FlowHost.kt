package pl.vistula.checker

import android.view.View
import org.json.JSONObject

/**
 * What the screen flows extracted from [MainActivity] ([PlatonFlow], [TasksFlow]) need from it. Keeping this narrow
 * stops the flows from reaching into the Activity's private state.
 */
interface FlowHost {
    /** Context for dialogs, toasts, resources and activity results. */
    val activity: MainActivity

    val repository: Repository

    /** Account state last read from the repository (the demo account has its own, separate data). */
    var state: JSONObject

    val isDemo: Boolean
    val isBusy: Boolean
    val rootView: View

    fun connected(): Boolean

    /** Re-draws the current screen from [state]. */
    fun render()

    fun navigate(tab: Int)

    /** Runs [job] off the main thread (one at a time) and delivers its result to [done] on the main thread. */
    fun <T> runTask(job: () -> T, done: (T) -> Unit)

    fun showError(message: String)
}
