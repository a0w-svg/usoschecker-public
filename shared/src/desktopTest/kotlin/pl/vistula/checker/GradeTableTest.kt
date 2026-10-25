package pl.vistula.checker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject
import pl.vistula.checker.core.Record
import pl.vistula.checker.ui.compose.GradeTable

class GradeTableTest {
    @Test fun readableLabelsExpandKnownTypesAndPreserveAdditionalForms() {
        assertEquals("Wykład", GradeTable.fullLabel("lecture", "pl"))
        assertEquals("Exercises", GradeTable.fullLabel("exercises", "en"))
        assertEquals("Лабораторна", GradeTable.fullLabel("lab", "uk"))
        assertEquals("Projekt", GradeTable.fullLabel("Projekt", "pl"))
    }
    private fun grade(id: String, type: String, value: String, session: Int = 1) =
        Record(id, "ocena", "Course", value, "", "2026Z").also {
            it.metadata.put("classType", type).put("sessionNumber", session)
        }

    @Test fun columnsKeepUnknownTypesAndEveryRetakeInItsOwnForm() {
        val rows = listOf(grade("l1", "Wykład", "2"), grade("l2", "Wykład", "4", 2),
            grade("lab", "Laboratorium", "5"), grade("project", "Projekt", "zal"))
        val columns = GradeTable.columns(rows, null)
        assertEquals(listOf("lecture", "lab", "Projekt"), columns)
        assertEquals("2 / 4", GradeTable.values(rows, "lecture", null))
        assertEquals("5", GradeTable.values(rows, "lab", null))
        assertEquals("zal", GradeTable.values(rows, "Projekt", null))
        assertEquals("—", GradeTable.values(rows, "exercises", null))
    }

    @Test fun canonicalTypesAlignTranslatedGradesAndNeverDiscardUnspecifiedTypes() {
        val translated = grade("translated", "Laboratory", "4,5").also { it.metadata.put("classTypeCanonical", "Laboratorium") }
        val unspecified = Record("unknown", "ocena", "Course", "pass", "", "2026Z")
        val rows = listOf(translated, unspecified)
        assertEquals("lab", GradeTable.key(translated, null))
        assertTrue(GradeTable.columns(rows, null).contains(GradeTable.key(unspecified, null)))
        assertEquals("4,5", GradeTable.values(rows, "lab", null))
        assertEquals("pass", GradeTable.values(rows, GradeTable.key(unspecified, null), null))
        assertEquals("Лаб.", GradeTable.label("lab", "uk"))
    }
}
