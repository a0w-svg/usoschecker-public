package pl.vistula.checker.ui

import android.os.Bundle

/** Filter and search of the History screen; survives recreation through [save] / [restore]. */
class HistoryUiState {
    var filter: String = ALL
    var query: String = ""

    /** Shows [selectedFilter] with an empty search, as when opened from a notification. */
    fun open(selectedFilter: String) {
        filter = selectedFilter
        query = ""
    }

    fun save(out: Bundle) {
        out.putString("filter", filter)
        out.putString("historyQuery", query)
    }

    fun restore(bundle: Bundle) {
        filter = bundle.getString("filter", ALL)
        query = bundle.getString("historyQuery", "")
    }

    companion object {
        /** Filter key that shows every change; the other keys are change kinds or actions. */
        const val ALL = "wszystkie"
    }
}
