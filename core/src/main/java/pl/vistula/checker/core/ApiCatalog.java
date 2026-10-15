package pl.vistula.checker.core;

import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Discover all catalog modules rather than a fixed subset of service names. */
public final class ApiCatalog {
    private ApiCatalog() {}
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("cards", "Legitymacje mLegitymacja karty studenta"), Map.entry("adm_documents", "Dokumenty decyzje zaświadczenia"),
        Map.entry("grades", "Oceny zaliczenia systemy oceniania"), Map.entry("tt", "Plan zajęć timetable harmonogram"),
        Map.entry("users", "Osoby profil studenta prowadzący"), Map.entry("courses", "Przedmioty zajęcia sylabusy"),
        Map.entry("payments", "Płatności opłaty czesne subkonta"), Map.entry("registrations", "Rejestracje zapisy na zajęcia"),
        Map.entry("exams", "Zapisy na egzaminy"), Map.entry("examrep", "Protokoły egzaminacyjne"), Map.entry("examrep2", "Protokoły egzaminacyjne"),
        Map.entry("crstests", "Sprawdziany kolokwia punkty"), Map.entry("groups", "Grupy zajęciowe"),
        Map.entry("progs", "Programy studiów kierunki"), Map.entry("theses", "Prace dyplomowe dyplomy"),
        Map.entry("terms", "Semestry terminy akademickie"), Map.entry("news", "Komunikaty aktualności ogłoszenia"),
        Map.entry("surveys", "Ankiety ocena zajęć"), Map.entry("attendance", "Obecności nieobecności"),
        Map.entry("calendar", "Kalendarz uczelni dni wolne"), Map.entry("credits", "Zaliczenia etapy studiów"),
        Map.entry("housing", "Akademiki zakwaterowanie"), Map.entry("internships", "Praktyki staże"),
        Map.entry("mobility", "Mobilność Erasmus wymiana"), Map.entry("slips", "Obiegówki"),
        Map.entry("statements", "Oświadczenia podpisy"), Map.entry("guide", "Informator uczelni"),
        Map.entry("fac", "Wydziały jednostki uczelni"), Map.entry("photos", "Zdjęcia profilowe"),
        Map.entry("phones", "Numery telefonów"), Map.entry("pit", "Dokumenty podatkowe PIT"),
        Map.entry("blobbox", "Pliki dokumenty binarne"), Map.entry("fileshare", "Udostępnianie plików"),
        Map.entry("events", "Zdarzenia powiadomienia"), Map.entry("events2", "Zdarzenia ustawienia powiadomień"),
        Map.entry("mailclient", "Wiadomości poczta"), Map.entry("mailing", "Wysyłanie wiadomości"),
        Map.entry("meetings", "Spotkania"), Map.entry("geo", "Lokalizacje sale budynki"), Map.entry("uprefs", "Preferencje użytkownika"));
    public static String module(String method) {
        String[] parts = method.split("/"); return parts.length >= 3 && parts[0].equals("services") ? parts[1] : "";
    }
    public static String label(String module) { return LABELS.getOrDefault(module, module); }
    public static Map<String, Integer> modules(JSONArray catalog) {
        Map<String, Integer> modules = new TreeMap<>();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject entry = catalog.optJSONObject(i); if (entry == null) continue;
            String module = module(entry.optString("name"));
            if (!module.isEmpty()) modules.put(module, modules.getOrDefault(module, 0) + 1);
        }
        return modules;
    }
    public static boolean matches(JSONObject entry, String selectedModule, String query) {
        String name = entry.optString("name"), module = module(name);
        return (selectedModule.isEmpty() || selectedModule.equals(module)) && StudentSearch.matches(query,
            name, entry.optString("brief_description"), label(module));
    }
    public static List<String> missingScopes(JSONObject metadata, String grantedScopes) {
        List<String> missing = new ArrayList<>();
        JSONObject auth = metadata.optJSONObject("auth_options");
        JSONArray scopes = auth == null ? null : auth.optJSONArray("scopes");
        if (scopes != null) for (int i = 0; i < scopes.length(); i++) {
            String scope = scopes.optString(i);
            if (!scope.isEmpty() && !StudentServices.hasScope(grantedScopes, scope)) missing.add(scope);
        }
        return missing;
    }
    public static boolean sensitiveParameter(String key) {
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("secret") || lower.contains("token") || lower.contains("password") || lower.contains("pass")
            || lower.equals("code") || lower.equals("pin") || lower.endsWith("_key") || lower.equals("certificate") || lower.equals("structure");
    }
    public static JSONObject restorableDrafts(JSONObject drafts) throws org.json.JSONException {
        JSONObject safe = new JSONObject();
        for (java.util.Iterator<String> methods = drafts.keys(); methods.hasNext();) {
            String method = methods.next(); JSONObject source = drafts.optJSONObject(method), values = new JSONObject();
            if (source == null) continue;
            for (java.util.Iterator<String> keys = source.keys(); keys.hasNext();) {
                String key = keys.next(); if (!sensitiveParameter(key)) values.put(key, source.opt(key));
            }
            safe.put(method, values);
        }
        return safe;
    }
}
