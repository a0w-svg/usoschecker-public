package pl.vistula.checker.ui

/** Student-service sections that must be fresh before a screen is shown. */
object TabSections {
    private val LECTURER = listOf("teaching", "lecturerGroups", "lecturerTests", "lecturerMeetings", "lecturerAttendance", "lecturerReports")
    private val ACADEMIC_EXTRAS = listOf(
        "registrations", "tokenRegistrations", "exams", "academicCalendar", "mobility",
        "placementAndSlips", "campusGuide", "communications", "theses"
    )

    /** Sections to refresh when opening [tab]; the first one is the screen's primary section. */
    @JvmStatic
    fun forTab(tab: Int, lecturerAccount: Boolean): List<String> = when (tab) {
        Tabs.PAYMENTS -> listOf("payments", "remittances")
        Tabs.NEWS -> listOf("news")
        Tabs.CARDS -> listOf("cards")
        Tabs.DOCUMENTS -> listOf("documents")
        Tabs.LECTURER -> if (lecturerAccount) LECTURER else emptyList()
        Tabs.ACADEMIC_EXTRAS -> ACADEMIC_EXTRAS
        Tabs.STAFF_DIRECTORY -> listOf("staffDirectory")
        else -> emptyList()
    }
}
