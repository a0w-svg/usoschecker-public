package pl.vistula.checker.ui

import android.os.Bundle

/** Filter and search of the History screen; survives recreation through [save] / [restore]. */
class HistoryUiState {
    var filter: String = "wszystkie"
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
        filter = bundle.getString("filter", "wszystkie")
        query = bundle.getString("historyQuery", "")
    }
}
