package pl.vistula.checker.ui

import android.os.Bundle

/** Selected day, view mode and search of the Schedule screen; survives recreation through [save] / [restore]. */
class ScheduleUiState {
    var date: String = ""
    var dateSelected: Boolean = false
    var mode: String = "list"
    var query: String = ""

    /** Set when the next render should scroll to the current lesson; transient, never saved. */
    var focusCurrent: Boolean = false

    /** Shows [day] as the explicitly selected date. */
    fun select(day: String) {
        date = day
        dateSelected = true
    }

    fun save(out: Bundle) {
        out.putString("calendarDate", date)
        out.putBoolean("calendarDateSelected", dateSelected)
        out.putString("calendarMode", mode)
        out.putString("calendarQuery", query)
    }

    fun restore(bundle: Bundle) {
        date = bundle.getString("calendarDate", "")
        dateSelected = bundle.getBoolean("calendarDateSelected", false)
        query = bundle.getString("calendarQuery", "")
        mode = bundle.getString("calendarMode", mode)
    }
}
