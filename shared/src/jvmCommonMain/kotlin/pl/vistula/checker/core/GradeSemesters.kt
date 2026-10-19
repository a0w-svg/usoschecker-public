package pl.vistula.checker.core

import java.time.LocalDate

/** Semester sections use university dates; term identifiers are never guessed as dates. */
object GradeSemesters {
    private fun start(snapshot: Snapshot, id: String): LocalDate = try {
        LocalDate.parse(snapshot.terms[id]!!.startDate)
    } catch (ignored: Exception) {
        LocalDate.MIN
    }

    @JvmStatic
    fun current(snapshot: Snapshot, today: LocalDate): String? =
        snapshot.terms.entries
            .filter { it.key.isNotEmpty() && it.value.isCurrent(today) }
            .sortedWith(
                Comparator.comparing<Map.Entry<String, AcademicTerm>, LocalDate> { start(snapshot, it.key) }.reversed()
                    .thenComparing { it.value.endDate }
                    .thenComparing { it.key }
            )
            .map { it.key }
            .firstOrNull()

    @JvmStatic
    fun ordered(snapshot: Snapshot, today: LocalDate): List<String> {
        val ids = HashSet<String>()
        for (id in snapshot.terms.keys) if (id.isNotEmpty()) ids.add(id)
        for (course in snapshot.courses) ids.add(course.term)
        for (record in snapshot.records.values) {
            if (record.kind == "ocena" || (record.kind == "plan" && record.term.isNotEmpty())) {
                ids.add(record.term)
            }
        }
        for (test in snapshot.tests) if (test.term.isNotEmpty()) ids.add(test.term)
        val current = current(snapshot, today)
        if (current != null) ids.add(current)
        val result = ArrayList(ids)
        result.sortWith(
            Comparator.comparing<String, Boolean> { it != current }
                .thenComparing { it.isEmpty() }
                .thenComparing(Comparator.comparing<String, LocalDate> { start(snapshot, it) }.reversed())
                .thenComparing(Comparator.reverseOrder())
        )
        return result
    }

    @JvmStatic
    fun inTerm(rows: List<Record>, term: String): List<Record> {
        val result = ArrayList<Record>()
        for (row in rows) if (row.term == term) result.add(row)
        return result
    }

    @JvmStatic
    fun testTerm(snapshot: Snapshot, test: CourseTestRecord): String? {
        if (test.term.isNotEmpty()) return test.term
        val terms = HashSet<String>()
        for (row in snapshot.records.values) {
            if (row.kind != "ocena") continue
            val match = if (test.courseId.isNotEmpty()) {
                test.courseId.equals(row.optMeta("courseId"), ignoreCase = true)
            } else {
                test.courseName.isNotEmpty() && test.courseName.equals(row.title, ignoreCase = true)
            }
            if (match) terms.add(row.term)
        }
        return if (terms.size == 1) terms.iterator().next() else null
    }
}

