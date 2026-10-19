package pl.vistula.checker.core

import java.util.Locale

/** Filters and groups grade entries without inferring academic pass/fail rules. */
object GradeBrowser {
    @JvmStatic
    fun ungraded(snapshot: Snapshot, term: String, type: String, query: String): List<Record> {
        val result = ArrayList<Record>()
        if (type != "all") return result
        for (course in snapshot.courses) {
            if (course.term != term) continue
            val hasGrade = snapshot.records.values.any { grade ->
                grade.kind == "ocena" && grade.term == term && (
                    if (grade.optMeta("courseId").isNotEmpty()) {
                        grade.optMeta("courseId") == course.optMeta("courseId")
                    } else {
                        grade.title == course.title
                    }
                    )
            }
            if (!hasGrade && StudentSearch.matches(query, course.title, course.term, course.optMeta("courseId"))) {
                result.add(course)
            }
        }
        result.sortWith(
            Comparator.comparing<Record, String> { it.title.lowercase(Locale.ROOT) }
                .thenComparing { it.id }
        )
        return result
    }

    @JvmStatic
    fun symbol(row: Record): String =
        if (row.optMeta("valueSymbol").isEmpty()) row.value.split("·", limit = 2)[0].trim() else row.optMeta("valueSymbol")

    @JvmStatic
    fun number(row: Record): Double? {
        val value = symbol(row).replace(',', '.')
        if (!value.matches(Regex("[0-9]+(?:\\.[0-9]+)?"))) return null
        val number = value.toDouble()
        return if (number.isFinite()) number else null
    }

    @JvmStatic
    @JvmOverloads
    fun select(
        rows: Collection<Record>,
        term: String,
        type: String,
        order: String,
        query: String,
        snapshot: Snapshot? = null
    ): List<Record> {
        val result = ArrayList<Record>()
        for (row in rows) {
            if (row.kind != "ocena" || (term.isNotEmpty() && term != row.term)) continue
            val numeric = number(row) != null
            val cType = canonicalClassType(row, snapshot).lowercase(Locale.ROOT)
            if (type == "numeric" && !numeric) continue
            if (type == "other" && numeric) continue
            if (type == "retakes" && row.metadata.optInt("sessionNumber", 1) <= 1) continue
            if (type == "labs" && !cType.contains("lab")) continue
            if (type == "exercises" && !cType.contains("ćwicz") && !cType.contains("cwicz")) continue
            if (StudentSearch.matches(query, row.title, row.value, row.term, classType(row, snapshot), row.optMeta("comment"), row.optMeta("courseId"))) {
                result.add(row)
            }
        }
        val byTitle = Comparator.comparing<Record, String> { it.title.lowercase(Locale.ROOT) }
        val byRecent = Comparator.comparing<Record, String> { it.sourceTime }.reversed()
        val byValue = Comparator.comparingDouble<Record> { number(it) ?: Double.NEGATIVE_INFINITY }.reversed()
        val selected = when (order) {
            "recent" -> byRecent.thenComparing(byTitle)
            "value" -> byValue.thenComparing(byTitle)
            else -> byTitle.thenComparing(byRecent)
        }
        result.sortWith(selected.thenComparing { it.term }.thenComparing { it.id })
        return result
    }

    @JvmStatic
    fun canonicalClassType(row: Record, snapshot: Snapshot?): String =
        row.optMeta("classTypeCanonical", classType(row, snapshot))

    @JvmStatic
    @JvmOverloads
    fun classType(row: Record, snapshot: Snapshot? = null): String {
        val direct = row.optMeta("classType")
        val legacyInference = row.optMeta("gradeScope").isNotEmpty() &&
            row.optMeta("classTypeSource").isEmpty() &&
            row.optMeta("classTypeId").isEmpty()
        if (!legacyInference && direct.isNotEmpty() && direct != "Laboratorium / Ćwiczenia" && direct != "Wykład / Ocena końcowa") {
            return direct
        }

        val unitId = row.optMeta("unitId")
        if (unitId.isNotEmpty() && snapshot != null) {
            val fromTimetable = snapshot.findClassTypeForUnit(unitId)
            if (fromTimetable.isNotEmpty()) return fromTimetable
        }

        val typeId = row.optMeta("classTypeId")
        if ("LAB".equals(typeId, ignoreCase = true)) return "Laboratorium"
        if ("CW".equals(typeId, ignoreCase = true) || "CWI".equals(typeId, ignoreCase = true)) return "Ćwiczenia"
        if ("WYK".equals(typeId, ignoreCase = true) || "W".equals(typeId, ignoreCase = true)) return "Wykład"
        if ("PRO".equals(typeId, ignoreCase = true) || "PRJ".equals(typeId, ignoreCase = true)) return "Projekt"
        if ("KON".equals(typeId, ignoreCase = true)) return "Konwersatorium"
        if ("SEM".equals(typeId, ignoreCase = true)) return "Seminarium"
        if ("LEK".equals(typeId, ignoreCase = true)) return "Lektorat"

        if (unitId.isNotEmpty() || row.optMeta("isUnitGrade", "false").toBoolean() || "unit" == row.optMeta("gradeScope")) {
            return "Ocena z protokołu zajęć"
        }
        if ("course" == row.optMeta("gradeScope")) return "Ocena z protokołu przedmiotu"
        return "Typ zajęć niepodany"
    }

    @JvmStatic
    fun groups(rows: List<Record>): List<List<Record>> {
        val result = LinkedHashMap<String, MutableList<Record>>()
        for (row in rows) {
            val key = (if (row.optMeta("courseId").isEmpty()) "title:${row.title}" else "id:${row.optMeta("courseId")}") + "\u0000" + row.term
            result.computeIfAbsent(key) { ArrayList() }.add(row)
        }
        return ArrayList(result.values)
    }
}

