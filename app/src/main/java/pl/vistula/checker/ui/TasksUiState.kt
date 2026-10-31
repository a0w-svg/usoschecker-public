package pl.vistula.checker.ui

import android.os.Bundle

/** Filter, search and sort state of the Tasks screen; survives recreation through [save] / [restore]. */
class TasksUiState {
    var filter: String = FILTER_ALL
    var query: String = ""
    var order: String = ORDER_DUE

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
        filter = bundle.getString("taskFilter", FILTER_ALL)?.takeIf { it in FILTERS } ?: FILTER_ALL
        query = bundle.getString("taskQuery", "")
        order = bundle.getString("taskOrder", ORDER_DUE)?.takeIf { it in ORDERS } ?: ORDER_DUE
    }

    companion object {
        const val FILTER_ALL = "all"
        const val ORDER_DUE = "due"
        val FILTERS = arrayOf(FILTER_ALL, "open", "done", "platon", "today", "week", "overdue")
        val ORDERS = arrayOf(ORDER_DUE, "priority", "title")
    }
}
