package pl.vistula.checker.core

import java.util.TreeSet
import org.json.JSONArray
import org.json.JSONObject

/** Every stored field remains visible; unrecognized API additions keep their original key. */
object RecordDetails {
    private val LABELS = mapOf(
        "courseId" to "Kod przedmiotu",
        "examId" to "ID protokołu",
        "sessionNumber" to "Numer terminu",
        "term" to "Semestr",
        "valueSymbol" to "Symbol oceny",
        "valueDescription" to "Opis oceny USOS",
        "gradeTypeId" to "ID systemu oceniania",
        "unitId" to "ID jednostki zajęć",
        "classTypeId" to "ID typu zajęć",
        "classType" to "Typ zajęć",
        "classTypeSource" to "Źródło typu zajęć",
        "comment" to "Komentarz",
        "dateAcquisition" to "Data uzyskania oceny",
        "dateModified" to "Ostatnia modyfikacja USOS",
        "modificationAuthor" to "ID autora modyfikacji",
        "modificationAuthorName" to "Autor modyfikacji",
        "countsIntoAverage" to "Wliczanie do średniej według USOS",
        "passes" to "Zaliczenie według USOS",
        "ects" to "Punkty ECTS przedmiotu",
        "gradeScope" to "Rodzaj protokołu",
        "room" to "Sala",
        "building" to "Budynek",
        "lecturers" to "Prowadzący",
        "groupNumber" to "Numer grupy",
        "rawGrade" to "Pełna odpowiedź oceny",
        "unitData" to "Dane jednostki zajęć",
        "date" to "Data",
        "sourceTime" to "Czas źródłowy USOS"
    )

    @JvmStatic
    fun display(value: Any?): String {
        if (value == null || value === JSONObject.NULL) return "Nie podano"
        if (value is Boolean) return if (value) "Tak" else "Nie"
        if (value is JSONObject) {
            return try { value.toString(2) } catch (e: Exception) { value.toString() }
        }
        if (value is JSONArray) {
            return try { value.toString(2) } catch (e: Exception) { value.toString() }
        }
        val str = value.toString()
        return str.ifEmpty { "Nie podano" }
    }

    @JvmStatic
    fun fields(record: Record, snapshot: Snapshot?): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        result["Przedmiot"] = record.title
        if (record.kind == "ocena") {
            result["Ocena"] = GradeBrowser.symbol(record)
            result["Typ zajęć / protokół"] = GradeBrowser.classType(record, snapshot)
        } else {
            result["Termin i opis"] = record.value
            if (record.startTime.isNotEmpty()) result["Początek"] = record.startTime
            if (record.endTime.isNotEmpty()) result["Koniec"] = record.endTime
        }
        if (record.term.isNotEmpty()) result["Semestr"] = record.term
        if (record.sourceTime.isNotEmpty()) result["Ostatnia modyfikacja USOS"] = record.sourceTime
        if (record.ects.isFinite()) result["Punkty ECTS przedmiotu"] = record.ects.toString()
        val keys = TreeSet<String>()
        val iterator = record.metadata.keys()
        while (iterator.hasNext()) keys.add(iterator.next())
        for (key in keys) {
            if (key == "rawGrade" || key == "rawLesson" || key == "unitData") continue
            var value: Any? = if (record.kind == "ocena" && key == "classType") {
                GradeBrowser.classType(record, snapshot)
            } else {
                record.metadata.opt(key)
            }
            if (key == "gradeScope") {
                value = when (value) {
                    "unit" -> "Protokół jednostki zajęć"
                    "course" -> "Protokół przedmiotu"
                    else -> value
                }
            }
            if (key == "classTypeSource") {
                value = when (value) {
                    "unit_api" -> "USOS courses/units"
                    "timetable" -> "Plan USOS"
                    else -> value
                }
            }
            result[LABELS.getOrDefault(key, key)] = display(value)
        }
        result["Identyfikator zapisu"] = record.id
        return result
    }
}

