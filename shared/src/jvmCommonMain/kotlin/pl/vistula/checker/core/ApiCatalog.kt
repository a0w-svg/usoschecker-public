package pl.vistula.checker.core

import java.util.Locale
import java.util.TreeMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Discover all catalog modules rather than a fixed subset of service names. */
object ApiCatalog {
    private val LABELS = mapOf(
        "cards" to "Legitymacje mLegitymacja karty studenta",
        "adm_documents" to "Dokumenty decyzje zaświadczenia",
        "grades" to "Oceny zaliczenia systemy oceniania",
        "tt" to "Plan zajęć timetable harmonogram",
        "users" to "Osoby profil studenta prowadzący",
        "courses" to "Przedmioty zajęcia sylabusy",
        "payments" to "Płatności opłaty czesne subkonta",
        "registrations" to "Rejestracje zapisy na zajęcia",
        "exams" to "Zapisy na egzaminy",
        "examrep" to "Protokoły egzaminacyjne",
        "examrep2" to "Protokoły egzaminacyjne",
        "crstests" to "Sprawdziany kolokwia punkty",
        "groups" to "Grupy zajęciowe",
        "progs" to "Programy studiów kierunki",
        "theses" to "Prace dyplomowe dyplomy",
        "terms" to "Semestry terminy akademickie",
        "news" to "Komunikaty aktualności ogłoszenia",
        "surveys" to "Ankiety ocena zajęć",
        "attendance" to "Obecności nieobecności",
        "calendar" to "Kalendarz uczelni dni wolne",
        "credits" to "Zaliczenia etapy studiów",
        "housing" to "Akademiki zakwaterowanie",
        "internships" to "Praktyki staże",
        "mobility" to "Mobilność Erasmus wymiana",
        "slips" to "Obiegówki",
        "statements" to "Oświadczenia podpisy",
        "guide" to "Informator uczelni",
        "fac" to "Wydziały jednostki uczelni",
        "photos" to "Zdjęcia profilowe",
        "phones" to "Numery telefonów",
        "pit" to "Dokumenty podatkowe PIT",
        "blobbox" to "Pliki dokumenty binarne",
        "fileshare" to "Udostępnianie plików",
        "events" to "Zdarzenia powiadomienia",
        "events2" to "Zdarzenia ustawienia powiadomień",
        "mailclient" to "Wiadomości poczta",
        "mailing" to "Wysyłanie wiadomości",
        "meetings" to "Spotkania",
        "geo" to "Lokalizacje sale budynki",
        "uprefs" to "Preferencje użytkownika"
    )

    private val CREDENTIAL_METHODS = setOf(
        "services/cards/mcard",
        "services/cards/mcard2",
        "services/cards/mcard_order",
        "services/cards/mcard_order2"
    )

    private val LECTURER_WRITE_METHODS = setOf(
        "services/crstests/update_grades",
        "services/crstests/update_points",
        "services/crstests/change_grade_node_properties",
        "services/crstests/change_task_node_properties",
        "services/crstests/create_grade_node",
        "services/grades/update_grade"
    )

    @JvmStatic
    fun lecturerWriteMethod(method: String): Boolean = LECTURER_WRITE_METHODS.contains(method)

    @JvmStatic
    fun visibleForAccount(method: String, state: JSONObject?): Boolean {
        if (!lecturerWriteMethod(method)) return true
        return state != null && AccountRole.lecturer(state)
    }

    @JvmStatic
    fun module(method: String): String {
        val parts = method.split("/")
        return if (parts.size >= 3 && parts[0] == "services") parts[1] else ""
    }

    @JvmStatic
    fun label(module: String): String = LABELS.getOrDefault(module, module)

    @JvmStatic
    fun modules(catalog: JSONArray): Map<String, Int> {
        val modules = TreeMap<String, Int>()
        for (i in 0 until catalog.length()) {
            val entry = catalog.optJSONObject(i) ?: continue
            val mod = module(entry.optString("name"))
            if (mod.isNotEmpty()) modules[mod] = modules.getOrDefault(mod, 0) + 1
        }
        return modules
    }

    @JvmStatic
    fun matches(entry: JSONObject, selectedModule: String, query: String): Boolean {
        val name = entry.optString("name")
        val mod = module(name)
        return (selectedModule.isEmpty() || selectedModule == mod) &&
            StudentSearch.matches(query, name, entry.optString("brief_description"), label(mod))
    }

    @JvmStatic
    fun missingScopes(metadata: JSONObject, grantedScopes: String?): List<String> {
        val missing = ArrayList<String>()
        val auth = metadata.optJSONObject("auth_options")
        val scopes = auth?.optJSONArray("scopes")
        if (scopes != null) {
            for (i in 0 until scopes.length()) {
                val scope = scopes.optString(i)
                if (scope.isNotEmpty() && !StudentServices.hasScope(grantedScopes, scope)) {
                    missing.add(scope)
                }
            }
        }
        return missing
    }

    @JvmStatic
    fun sensitiveParameter(key: String): Boolean {
        val lower = key.lowercase(Locale.ROOT)
        return lower.contains("secret") || lower.contains("token") || lower.contains("password") ||
            lower.contains("pass") || lower == "code" || lower == "pin" ||
            lower == "key" || lower.endsWith("_key") || lower == "certificate" || lower == "structure"
    }

    @JvmStatic
    fun credentialResponse(method: String): Boolean {
        val mod = module(method)
        return "oauth" == mod || "oauth2" == mod || CREDENTIAL_METHODS.contains(method)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun restorableDrafts(drafts: JSONObject): JSONObject {
        val safe = JSONObject()
        val methods = drafts.keys()
        while (methods.hasNext()) {
            val method = methods.next()
            val source = drafts.optJSONObject(method) ?: continue
            val values = JSONObject()
            val keys = source.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!sensitiveParameter(key)) values.put(key, source.opt(key))
            }
            safe.put(method, values)
        }
        return safe
    }
}
