package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject

/** Public USOS faculty identifiers, verified against fac/search; never infer a branch from a course name. */
object CampusAffiliation {
    val campuses = linkedMapOf("0000" to "Warszawa", "C0000" to "Ciechanów", "G0000" to "Gdańsk",
        "GB000" to "Braniewo", "GT000" to "Tczew", "O0000" to "Olsztyn", "OK000" to "Kętrzyn", "P0000" to "Pułtusk")

    fun detect(programmes: JSONArray): JSONObject {
        val found = linkedSetOf<String>()
        val units = JSONArray()
        for (i in 0 until programmes.length()) {
            val programme = programmes.getJSONObject(i)
            val status = StudentServices.text(programme, "status")
            if (status.isNotEmpty() && status != "active") continue
            val faculty = programme.optJSONObject("programme")?.optJSONObject("faculty") ?: continue
            val path = faculty.optJSONArray("path") ?: JSONArray()
            val ancestry = (0 until path.length()).map { StudentServices.text(path.optJSONObject(it), "id") } + StudentServices.text(faculty, "id")
            var campus = ancestry.lastOrNull { it != "0000" && campuses.containsKey(it) }.orEmpty()
            if (campus.isEmpty() && (StudentServices.text(faculty, "id") == "0000" ||
                Regex("\\bWarszawa\\b", RegexOption.IGNORE_CASE).containsMatchIn(StudentServices.text(faculty, "postal_address")))) campus = "0000"
            if (campus.isNotEmpty()) found.add(campus)
            units.put(JSONObject().put("id", StudentServices.text(faculty, "id"))
                .put("name", ApiLanguage.safe(faculty.opt("name"))).put("campus", campus)
                .put("address", StudentServices.text(faculty, "postal_address"))
                .put("phones", JSONArray().apply {
                    val phones = faculty.optJSONArray("phone_numbers") ?: JSONArray()
                    for (n in 0 until phones.length()) {
                        val phone = phones.opt(n)
                        if (phone is String && phone.isNotBlank()) put(phone)
                    }
                })
                .put("email", StudentServices.text(faculty, "email"))
                .put("homepage", StudentServices.text(faculty, "homepage_url")))
        }
        return JSONObject().put("candidates", JSONArray(found.toList())).put("units", units)
            .put("detected", if (found.size == 1) found.first() else "")
    }

    fun selected(state: JSONObject): String {
        val manual = state.optString("campusOverride")
        if (campuses.containsKey(manual)) return manual
        val detected = state.optJSONObject("studentServices")?.optJSONObject("affiliation")?.optJSONObject("data")?.optString("detected").orEmpty()
        return if (campuses.containsKey(detected)) detected else ""
    }

    /** Central public posts are not campus-tagged; show them only for the Warsaw campus. */
    fun canShowPublicNews(state: JSONObject): Boolean = selected(state) == "0000"
    fun newsMatches(data: JSONObject, state: JSONObject): Boolean = data.optString("campus") == selected(state)

    fun website(campus: String): String? = when (campus) {
        "0000" -> "https://vistula.edu.pl/"
        "G0000" -> "https://gdansk.vistula.edu.pl/"
        "GB000" -> "https://gdansk.vistula.edu.pl/pl/braniewo"
        "GT000" -> "https://gdansk.vistula.edu.pl/pl/node/286"
        "O0000", "OK000" -> "https://olsztyn.vistula.edu.pl/"
        "P0000" -> "https://pultusk.vistula.edu.pl/"
        else -> null // Unverified links are not guessed from a city name.
    }
}
