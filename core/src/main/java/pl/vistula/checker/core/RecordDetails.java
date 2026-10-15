package pl.vistula.checker.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONObject;

/** Every stored field remains visible; unrecognized API additions keep their original key. */
public final class RecordDetails {
    private RecordDetails() {}
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("courseId", "Kod przedmiotu"), Map.entry("examId", "ID protokołu"), Map.entry("sessionNumber", "Numer terminu"),
        Map.entry("term", "Semestr"), Map.entry("valueSymbol", "Symbol oceny"), Map.entry("valueDescription", "Opis oceny USOS"),
        Map.entry("gradeTypeId", "ID systemu oceniania"), Map.entry("unitId", "ID jednostki zajęć"),
        Map.entry("classTypeId", "ID typu zajęć"), Map.entry("classType", "Typ zajęć"), Map.entry("classTypeSource", "Źródło typu zajęć"),
        Map.entry("comment", "Komentarz"), Map.entry("dateAcquisition", "Data uzyskania oceny"), Map.entry("dateModified", "Ostatnia modyfikacja USOS"),
        Map.entry("modificationAuthor", "ID autora modyfikacji"), Map.entry("modificationAuthorName", "Autor modyfikacji"),
        Map.entry("countsIntoAverage", "Wliczanie do średniej według USOS"), Map.entry("passes", "Zaliczenie według USOS"),
        Map.entry("ects", "Punkty ECTS przedmiotu"), Map.entry("gradeScope", "Rodzaj protokołu"),
        Map.entry("room", "Sala"), Map.entry("building", "Budynek"), Map.entry("lecturers", "Prowadzący"),
        Map.entry("groupNumber", "Numer grupy"), Map.entry("rawGrade", "Pełna odpowiedź oceny"),
        Map.entry("unitData", "Dane jednostki zajęć"), Map.entry("date", "Data"), Map.entry("sourceTime", "Czas źródłowy USOS"));
    public static String display(Object value) {
        if (value == null || value == JSONObject.NULL) return "Nie podano";
        if (value instanceof Boolean) return (Boolean) value ? "Tak" : "Nie";
        if (value instanceof JSONObject) {
            try { return ((JSONObject) value).toString(2); } catch (Exception e) { return value.toString(); }
        }
        if (value instanceof JSONArray) {
            try { return ((JSONArray) value).toString(2); } catch (Exception e) { return value.toString(); }
        }
        return value.toString().isEmpty() ? "Nie podano" : value.toString();
    }
    public static LinkedHashMap<String, String> fields(Record record, Snapshot snapshot) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        result.put("Przedmiot", record.title);
        if (record.kind.equals("ocena")) {
            result.put("Ocena", GradeBrowser.symbol(record));
            result.put("Typ zajęć / protokół", GradeBrowser.classType(record, snapshot));
        } else {
            result.put("Termin i opis", record.value);
            if (!record.startTime.isEmpty()) result.put("Początek", record.startTime);
            if (!record.endTime.isEmpty()) result.put("Koniec", record.endTime);
        }
        if (!record.term.isEmpty()) result.put("Semestr", record.term);
        if (!record.sourceTime.isEmpty()) result.put("Ostatnia modyfikacja USOS", record.sourceTime);
        if (Double.isFinite(record.ects)) result.put("Punkty ECTS przedmiotu", String.valueOf(record.ects));
        TreeSet<String> keys = new TreeSet<>();
        for (java.util.Iterator<String> iterator = record.metadata.keys(); iterator.hasNext();) keys.add(iterator.next());
        for (String key : keys) {
            if (key.equals("rawGrade") || key.equals("rawLesson") || key.equals("unitData")) continue;
            Object value = record.kind.equals("ocena") && key.equals("classType") ? GradeBrowser.classType(record, snapshot) : record.metadata.opt(key);
            if (key.equals("gradeScope")) value = "unit".equals(value) ? "Protokół jednostki zajęć" : "course".equals(value) ? "Protokół przedmiotu" : value;
            if (key.equals("classTypeSource")) value = "unit_api".equals(value) ? "USOS courses/units" : "timetable".equals(value) ? "Plan USOS" : value;
            result.put(LABELS.getOrDefault(key, key), display(value));
        }
        result.put("Identyfikator zapisu", record.id);
        return result;
    }
}
