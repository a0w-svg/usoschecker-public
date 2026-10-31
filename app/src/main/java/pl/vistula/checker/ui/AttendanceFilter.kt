package pl.vistula.checker.ui

/** Keys of the Attendance screen's status filter and class-type filter; the strings are persisted, so keep them stable. */
object AttendanceFilter {
    const val ALL = "all"
    const val WARNING = "warning"
    const val EXCEEDED = "exceeded"
    val STATUSES = arrayOf(ALL, WARNING, EXCEEDED)

    /** Class-type filter value meaning "every type" (stored in preferences as `classType`). */
    const val TYPE_ALL = "all"

    /** Returns [key] if it is a known status filter, otherwise [ALL]. */
    @JvmStatic
    fun statusOrAll(key: String?): String = if (key != null && key in STATUSES) key else ALL
}
