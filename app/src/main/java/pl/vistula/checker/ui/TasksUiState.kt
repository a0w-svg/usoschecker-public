package pl.vistula.checker.ui

import android.os.Bundle

/** Filter, search and sort state of the Tasks screen; survives recreation through [save] / [restore]. */
class TasksUiState {
    var filter: String = "all"
    var query: String = ""
    var order: String = "due"

    /** Shows [selectedFilter] with an empty search, as when opening Tasks from the dashboard. */
    fun open(selectedFilter: String) {
        filter = selectedFilter
        query = ""
    }

    fun save(out: Bundle) {
        out.putString("taskFilter", filter)
        out.putString("taskQuery", query)
        out.putString("taskOrder", order)
    }

    fun restore(bundle: Bundle) {
        filter = bundle.getString("taskFilter", "all")
        query = bundle.getString("taskQuery", "")
        order = bundle.getString("taskOrder", "due")
    }
}
