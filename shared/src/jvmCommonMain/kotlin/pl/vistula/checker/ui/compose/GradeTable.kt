package pl.vistula.checker.ui.compose

import pl.vistula.checker.core.GradeBrowser
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.Snapshot
import java.util.Locale

/** Keep every assessment and attempt visible, including forms outside the three common columns. */
object GradeTable {
    fun key(row: Record, snapshot: Snapshot?): String {
        val type = GradeBrowser.canonicalClassType(row, snapshot)
        return when (type.lowercase(Locale.ROOT)) {
            "wykład", "lecture" -> "lecture"
            "ćwiczenia", "cwiczenia", "exercises", "tutorial" -> "exercises"
            "laboratorium", "laboratory", "lab" -> "lab"
            else -> type
        }
    }

    fun columns(rows: List<Record>, snapshot: Snapshot?): List<String> {
        val present = rows.map { key(it, snapshot) }.distinct()
        return listOf("lecture", "exercises", "lab").filter { it in present } +
            present.filter { it !in listOf("lecture", "exercises", "lab") }
    }

    fun label(key: String, language: String): String = when (key) {
        "lecture" -> toolText(language, "Wyk.", "Lec.", "Лек.")
        "exercises" -> toolText(language, "Ćw.", "Ex.", "Пр.")
        "lab" -> toolText(language, "Lab.", "Lab.", "Лаб.")
        "Ocena z protokołu przedmiotu" -> toolText(language, "Końc.", "Final", "Підс.")
        "Ocena z protokołu zajęć" -> toolText(language, "Zaj.", "Class", "Зан.")
        "Typ zajęć niepodany" -> toolText(language, "Inne", "Other", "Інші")
        else -> key
    }

    fun values(rows: List<Record>, column: String, snapshot: Snapshot?): String =
        rows.filter { key(it, snapshot) == column }
            .joinToString(" / ") { GradeBrowser.symbol(it) }.ifEmpty { "—" }
}
