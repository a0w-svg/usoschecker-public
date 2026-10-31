package pl.vistula.checker.ui

import android.os.Bundle

/** Filter, sort and expansion state of the Grades screen; survives recreation through [save] / [restore]. */
class GradesUiState {
    var query: String = ""
    var type: String = TYPE_ALL
    var order: String = ORDER_NAME
    var filtersExpanded: Boolean = false
    var semester: String = ""
    var semesterInitialized: Boolean = false
    val semesterExpansion: MutableMap<String, Boolean> = HashMap()

    /** Forgets the selected semester and which semesters were expanded (e.g. after an account switch). */
    fun clearSemester() {
        semesterExpansion.clear()
        semester = ""
    }

    fun resetFilters() {
        clearSemester()
        type = TYPE_ALL
        query = ""
        order = ORDER_NAME
    }

    fun select(term: String) {
        semester = term
        semesterInitialized = true
    }

    fun save(out: Bundle) {
        out.putString("semester", semester)
        out.putBoolean("semesterInitialized", semesterInitialized)
        out.putString("gradeQuery", query)
        out.putString("gradeType", type)
        out.putString("gradeOrder", order)
        out.putBoolean("gradeFiltersExpanded", filtersExpanded)
        val expanded = Bundle()
        for ((key, value) in semesterExpansion) expanded.putBoolean(key, value)
        out.putBundle("gradeSemesterExpansion", expanded)
    }

    fun restore(bundle: Bundle) {
        query = bundle.getString("gradeQuery", "")
        type = bundle.getString("gradeType", TYPE_ALL)?.takeIf { it in TYPES } ?: TYPE_ALL
        order = bundle.getString("gradeOrder", order)?.takeIf { it in ORDERS } ?: ORDER_NAME
        filtersExpanded = bundle.getBoolean("gradeFiltersExpanded")
        bundle.getBundle("gradeSemesterExpansion")?.let { expanded ->
            for (term in expanded.keySet()) semesterExpansion[term] = expanded.getBoolean(term)
        }
        semester = bundle.getString("semester", "")
        semesterInitialized = bundle.getBoolean("semesterInitialized", bundle.containsKey("semester"))
    }

    companion object {
        const val TYPE_ALL = "all"
        const val ORDER_NAME = "name"
        val TYPES = arrayOf(TYPE_ALL, "labs", "exercises", "numeric", "other", "retakes")
        val ORDERS = arrayOf(ORDER_NAME, "recent", "value")
    }
}
