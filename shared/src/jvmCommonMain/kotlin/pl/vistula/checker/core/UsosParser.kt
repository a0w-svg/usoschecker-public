package pl.vistula.checker.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.TreeMap
import java.util.TreeSet
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Validates response shape before a snapshot can be committed. */
object UsosParser {
    @JvmField val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun optionalText(o: JSONObject, field: String): String =
        if (o.isNull(field)) "" else o.opt(field).let { if (it is String || it is Number || it is Boolean) it.toString() else "" }

    private fun finiteAmount(o: JSONObject, field: String, fallback: Double = 0.0): Double {
        if (!o.has(field) || o.isNull(field)) return fallback
        val value = o.optDouble(field, Double.NaN)
        if (!value.isFinite()) throw JSONException("Invalid payment amount")
        return value
    }

    @JvmStatic
    fun language(o: JSONObject?): String = ApiLanguage.text(o)

    @JvmStatic
    fun courseNames(courses: JSONObject): Map<String, String> {
        val names = TreeMap<String, String>()
        for (term in sortedKeys(courses)) {
            val rows = courses.optJSONArray(term) ?: continue
            for (i in 0 until rows.length()) {
                val c = rows.optJSONObject(i) ?: continue
                val id = c.optString("course_id")
                val courseNameObj = c.optJSONObject("course_name")
                if (id.isEmpty() || courseNameObj == null) continue
                names[id] = language(courseNameObj)
            }
        }
        return names
    }

    @JvmStatic
    fun sortedKeys(obj: JSONObject): List<String> {
        val keys = ArrayList<String>()
        val it = obj.keys()
        while (it.hasNext()) keys.add(it.next())
        keys.sort()
        return keys
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun courseEditions(editions: JSONObject, ectsPoints: Map<String, Double>): List<Record> {
        val result = ArrayList<Record>()
        for (term in sortedKeys(editions)) {
            val rows = editions.optJSONArray(term) ?: continue
            val seen = HashSet<String>()
            for (i in 0 until rows.length()) {
                val course = rows.optJSONObject(i) ?: continue
                val id = optionalText(course, "course_id")
                if (id.isEmpty() || !seen.add(id)) continue
                val translated = course.optJSONObject("course_name")
                var title = if (translated == null) id else language(translated)
                if (title.isEmpty()) title = id
                val ects = ectsPoints.getOrDefault("$term\u0000$id", Double.NaN)
                result.add(
                    Record(
                        "course:$term:$id", "przedmiot", title, "", "", "", term,
                        "", "", ects, JSONObject().put("courseId", id)
                    )
                )
            }
        }
        return result
    }

    @JvmStatic
    fun ectsPoints(response: JSONObject?): Map<String, Double> {
        val map = HashMap<String, Double>()
        if (response == null) return map
        val termKeys = response.keys()
        while (termKeys.hasNext()) {
            val term = termKeys.next()
            if (response.isNull(term)) continue
            val termObj = response.optJSONObject(term) ?: continue
            val courseKeys = termObj.keys()
            while (courseKeys.hasNext()) {
                val course = courseKeys.next()
                if (termObj.isNull(course)) continue
                val raw = termObj.opt(course) ?: continue
                try {
                    val clean = raw.toString().trim().replace(',', '.')
                    val value = clean.toDouble()
                    if (value.isFinite() && value >= 0) {
                        map["$term\u0000$course"] = value
                    }
                } catch (ignored: NumberFormatException) {}
            }
        }
        return map
    }

    class UserProfile @JvmOverloads constructor(
        @JvmField val name: String,
        @JvmField val studentNumber: String,
        @JvmField val email: String,
        @JvmField val programme: String,
        @JvmField val studentStatus: String,
        @JvmField val stage: String = "",
        @JvmField val admissionDate: String = "",
        @JvmField val photoUrl: String = ""
    )

    @JvmStatic
    fun userProfile(user: JSONObject?): UserProfile {
        if (user == null) return UserProfile("", "", "", "", "")
        val first = optionalText(user, "first_name").trim()
        val last = optionalText(user, "last_name").trim()
        val name = "$first $last".trim()
        val studentNumber = optionalText(user, "student_number").trim()
        val email = optionalText(user, "email").trim()

        var programme = ""
        var stage = ""
        var admissionDate = ""
        val progs = user.optJSONArray("student_programmes")
        if (progs != null) {
            var selected: JSONObject? = null
            for (i in 0 until progs.length()) {
                val sp = progs.optJSONObject(i) ?: continue
                selected = sp
                if ("active" == sp.optString("status", "")) break
            }
            if (selected != null) {
                val pName = selected.optJSONObject("programme")?.optJSONObject("name")
                if (pName != null) programme = language(pName)
                admissionDate = optionalText(selected, "admission_date")
                val stages = selected.optJSONArray("stages")
                val lastStage = if (stages != null && stages.length() > 0) stages.optJSONObject(stages.length() - 1) else null
                stage = if (lastStage == null) "" else optionalText(lastStage, "stage_id")
            }
        }

        var studentStatus = ""
        if (user.has("student_status") && !user.isNull("student_status")) {
            val status = user.optInt("student_status", -1)
            when (status) {
                2 -> studentStatus = ApiLanguage.label("Aktywny student", "Active student", "Активний студент")
                1 -> studentStatus = ApiLanguage.label("Były student", "Former student", "Колишній студент")
                0 -> studentStatus = ApiLanguage.label("Nie jest studentem", "Not a student", "Не є студентом")
            }
        }

        var photoUrl = ""
        val photos = user.optJSONObject("photo_urls")
        if (photos != null) {
            photoUrl = optionalText(photos, "200x200")
            if (photoUrl.isEmpty()) photoUrl = optionalText(photos, "100x100")
        }

        return UserProfile(name, studentNumber, email, programme, studentStatus, stage, admissionDate, photoUrl)
    }

    @JvmStatic
    @JvmOverloads
    @Throws(JSONException::class)
    fun grades(
        s: Snapshot,
        grades: JSONObject,
        names: Map<String, String>,
        ectsPoints: Map<String, Double> = emptyMap()
    ) {
        for (term in sortedKeys(grades)) {
            val courses = grades.getJSONObject(term) ?: continue
            for (course in sortedKeys(courses)) {
                val edition = courses.getJSONObject(course) ?: continue
                if (!edition.has("course_grades") || !edition.has("course_units_grades")) {
                    throw JSONException("Niepełne dane ocen.")
                }
                if (!edition.isNull("course_grades")) {
                    readExams(s, edition.getJSONArray("course_grades"), course, names, term, ectsPoints, null)
                }
                val units = if (edition.isNull("course_units_grades")) null else edition.getJSONObject("course_units_grades")
                if (units != null) {
                    for (unit in sortedKeys(units)) {
                        if (!units.isNull(unit)) {
                            readExams(s, units.getJSONArray(unit), course, names, term, ectsPoints, unit)
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun detectClassType(
        unitId: String?,
        gradeTypeId: String?,
        comment: String?,
        description: String?,
        symbol: String?,
        s: Snapshot?
    ): String {
        if (s != null && !unitId.isNullOrEmpty()) {
            val fromPlan = s.findClassTypeForUnit(unitId)
            if (fromPlan.isNotEmpty()) return fromPlan
        }
        // grade_type_id identifies the grading scale, not the class type.
        return ""
    }

    @Throws(JSONException::class)
    private fun readExams(
        s: Snapshot,
        exams: JSONArray,
        course: String,
        names: Map<String, String>,
        term: String,
        ectsPoints: Map<String, Double>,
        unitId: String?
    ) {
        val ects = ectsPoints["$term\u0000$course"] ?: Double.NaN

        for (i in 0 until exams.length()) {
            val sessions = exams.getJSONObject(i) ?: continue
            for (session in sortedKeys(sessions)) {
                if (sessions.isNull(session)) continue
                val g = sessions.getJSONObject(session) ?: continue
                val exam = g.get("exam_id").toString()
                val number = g.get("exam_session_number").toString()
                val symbol = g.getString("value_symbol")
                val modified = optionalText(g, "date_modified")
                if (modified.isNotEmpty()) {
                    LocalDateTime.parse(modified, TIME)
                }
                val comment = optionalText(g, "comment")
                val value = "$symbol · ${ApiLanguage.label("termin", "attempt", "спроба")} $number" +
                    (if (comment.isEmpty()) "" else "\n$comment")

                val meta = JSONObject()
                meta.put("rawGrade", JSONObject(g.toString()))
                meta.put("courseId", course)
                meta.put("examId", exam)
                meta.put("sessionNumber", number)
                meta.put("term", term)
                meta.put("valueSymbol", symbol)
                if (!unitId.isNullOrEmpty()) {
                    meta.put("unitId", unitId)
                    meta.put("gradeScope", "unit")
                } else {
                    meta.put("gradeScope", "course")
                }
                if (modified.isNotEmpty()) meta.put("dateModified", modified)
                if (g.has("passes") && !g.isNull("passes")) meta.put("passes", g.optBoolean("passes"))
                val valDesc = g.optJSONObject("value_description")
                val desc = if (valDesc != null) language(valDesc) else optionalText(g, "value_description")
                if (desc.isNotEmpty()) meta.put("valueDescription", desc)
                if (g.has("counts_into_average") && !g.isNull("counts_into_average")) {
                    meta.put("countsIntoAverage", g.optBoolean("counts_into_average"))
                }
                val gradeTypeId = optionalText(g, "grade_type_id")
                if (gradeTypeId.isNotEmpty()) meta.put("gradeTypeId", gradeTypeId)
                val dateAcq = optionalText(g, "date_acquisition")
                if (dateAcq.isNotEmpty()) meta.put("dateAcquisition", dateAcq)
                val modAuthor = optionalText(g, "modification_author")
                if (modAuthor.isNotEmpty()) meta.put("modificationAuthor", modAuthor)
                if (comment.isNotEmpty()) meta.put("comment", comment)
                if (!ects.isNaN()) meta.put("ects", ects)

                val inferredType = detectClassType(unitId, gradeTypeId, comment, desc, symbol, s)
                if (inferredType.isNotEmpty()) {
                    meta.put("classType", inferredType).put("classTypeSource", "timetable")
                }
                var gradeDate = ""
                if (modified.length >= 10) {
                    gradeDate = modified.substring(0, 10)
                } else if (dateAcq.length >= 10) {
                    gradeDate = dateAcq.substring(0, 10)
                }
                val r = Record(
                    "grade:$exam:$number", "ocena", names.getOrDefault(course, course),
                    value, modified, gradeDate, term, "", "", ects, meta
                )
                // The same exam can be referenced by a course and its unit.
                val existing = s.records[r.id]
                if (existing == null) {
                    s.add(r)
                } else {
                    if (!existing.same(r)) throw IllegalArgumentException("Sprzeczne dane oceny: " + r.id)
                    if (!unitId.isNullOrEmpty() && existing.optMeta("unitId").isEmpty()) {
                        try {
                            existing.metadata.put("alsoCourseProtocol", "course" == existing.optMeta("gradeScope"))
                            existing.metadata.put("unitId", unitId)
                            existing.metadata.put("gradeScope", "unit")
                            if (inferredType.isNotEmpty() && existing.optMeta("classType").isEmpty()) {
                                existing.metadata.put("classType", inferredType)
                            }
                        } catch (ignored: JSONException) {}
                    }
                }
            }
        }
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun lecturerIds(activities: JSONArray): Set<String> {
        val ids = TreeSet<String>()
        for (i in 0 until activities.length()) {
            val lecturers = activities.getJSONObject(i).optJSONArray("lecturer_ids") ?: continue
            for (j in 0 until lecturers.length()) {
                if (!lecturers.isNull(j)) {
                    val id = lecturers.get(j).toString()
                    if (!id.matches(Regex("[0-9]+"))) {
                        throw JSONException("Nieprawidłowy identyfikator prowadzącego.")
                    }
                    ids.add(id)
                }
            }
        }
        return ids
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun lecturerNames(users: JSONObject): Map<String, String> {
        val names = TreeMap<String, String>()
        for (id in sortedKeys(users)) {
            if (users.isNull(id)) continue
            val person = users.getJSONObject(id)
            val first = optionalText(person, "first_name").trim()
            val last = optionalText(person, "last_name").trim()
            val name = "$first $last".trim()
            if (name.isNotEmpty()) names[id] = name
        }
        return names
    }

    @JvmStatic
    @JvmOverloads
    fun timetable(
        s: Snapshot,
        activities: JSONArray,
        lecturerNames: Map<String, String> = emptyMap()
    ) {
        for (i in 0 until activities.length()) {
            val a = activities.optJSONObject(i) ?: throw IllegalArgumentException("Niepełne dane planu.")
            val type = a.optString("type", "")
            val start = a.optString("start_time", "")
            val end = a.optString("end_time", "")
            if (start.isEmpty() || end.isEmpty()) throw IllegalArgumentException("Brak godzin zajęć.")
            try {
                val parsedStart = LocalDateTime.parse(start, TIME)
                if (!LocalDateTime.parse(end, TIME).isAfter(parsedStart)) {
                    throw IllegalArgumentException("Nieprawidłowe godziny zajęć.")
                }
                val date = parsedStart.toLocalDate().toString()
                if (!s.covers(date)) continue

                val genericName = if (a.optJSONObject("name") == null) {
                    ApiLanguage.label("Brak nazwy", "Unnamed", "Без назви")
                } else {
                    language(a.getJSONObject("name"))
                }
                val courseName = if (a.optJSONObject("course_name") == null) {
                    genericName
                } else {
                    language(a.getJSONObject("course_name"))
                }

                val stable = when {
                    type == "classgroup2" && !a.isNull("sm_id") -> "sm:" + a.optString("sm_id", "")
                    type == "classgroup" -> "cg:" + a.optString("unit_id", "") + ":" + a.optString("group_number", "") + ":" + a.optString("cgwm_id", "") + ":" + date
                    else -> {
                        val stableName = a.optJSONObject("course_name") ?: a.optJSONObject("name")
                        val canonicalName = if (stableName == null) "Brak nazwy" else ApiLanguage.text(stableName, "pl")
                        type + ":" + a.optString("unit_id", "") + ":" + a.optString("group_number", "") + ":" + canonicalName + ":" + start
                    }
                }
                val room = optionalText(a, "room_number")
                val building = if (a.optJSONObject("building_name") == null) "" else language(a.getJSONObject("building_name"))
                val classType = if (a.optJSONObject("classtype_name") == null) "" else language(a.getJSONObject("classtype_name"))
                val lecturers = a.optJSONArray("lecturer_ids")
                val ids = ArrayList<String>()
                if (lecturers != null) {
                    for (j in 0 until lecturers.length()) {
                        if (lecturers.isNull(j)) continue
                        val lid = lecturers.opt(j).toString()
                        ids.add(lecturerNames.getOrDefault(lid, "ID: $lid"))
                    }
                }
                ids.sort()
                val freq = a.optString("frequency", "")
                val value = start.substring(11, 16) + " – " + end.substring(11, 16) +
                    (if (classType.isEmpty()) "" else "\n$classType") +
                    (if (room.isEmpty()) "" else "\n${ApiLanguage.label("Sala", "Room", "Аудиторія")} $room") +
                    (if (building.isEmpty()) "" else " · $building") +
                    (if (ids.isEmpty()) "" else "\n${ApiLanguage.label("Prowadzący", "Lecturers", "Викладачі")}: " + ids.joinToString(", ")) +
                    (if (freq == "other" || freq == "once") "\n" + ApiLanguage.label("Termin orientacyjny", "Tentative date", "Орієнтовна дата") else "")

                val meta = JSONObject()
                meta.put("rawLesson", JSONObject(a.toString()))
                val courseId = optionalText(a, "course_id")
                if (courseId.isNotEmpty()) meta.put("courseId", courseId)
                if (room.isNotEmpty()) meta.put("room", room)
                val roomId = optionalText(a, "room_id")
                if (roomId.isNotEmpty()) meta.put("roomId", roomId)
                if (building.isNotEmpty()) meta.put("building", building)
                val buildingId = optionalText(a, "building_id")
                if (buildingId.isNotEmpty()) meta.put("buildingId", buildingId)
                if (classType.isNotEmpty()) meta.put("classType", classType)
                val classTypeId = optionalText(a, "classtype_id")
                if (classTypeId.isNotEmpty()) meta.put("classTypeId", classTypeId)
                val groupNumber = optionalText(a, "group_number")
                if (groupNumber.isNotEmpty()) meta.put("groupNumber", groupNumber)
                if (ids.isNotEmpty()) meta.put("lecturers", ids.joinToString(", "))
                if (freq.isNotEmpty()) meta.put("frequency", freq)
                if (type.isNotEmpty()) meta.put("type", type)
                val unitId = optionalText(a, "unit_id")
                if (unitId.isNotEmpty()) meta.put("unitId", unitId)
                val cgwmId = optionalText(a, "cgwm_id")
                if (cgwmId.isNotEmpty()) meta.put("cgwmId", cgwmId)
                val smId = optionalText(a, "sm_id")
                if (smId.isNotEmpty()) meta.put("smId", smId)
                val url = optionalText(a, "url")
                if (url.isNotEmpty()) meta.put("url", url)
                val profileUrl = optionalText(a, "classgroup_profile_url")
                if (profileUrl.isNotEmpty()) meta.put("profileUrl", profileUrl)
                meta.put("startTime", start)
                meta.put("endTime", end)
                meta.put("date", date)

                s.add(Record("plan:$stable", "plan", courseName, value, "", date, "", start, end, Double.NaN, meta))
            } catch (e: Exception) {
                throw IllegalArgumentException("Nieprawidłowe dane planu.", e)
            }
        }
    }

    @JvmStatic
    fun accounts(accountsRaw: Any?): List<PaymentInfo.Account> {
        if (accountsRaw == null) return emptyList()
        val results = ArrayList<PaymentInfo.Account>()
        val list: JSONArray? = when (accountsRaw) {
            is JSONArray -> accountsRaw
            is JSONObject -> accountsRaw.optJSONArray("user_accounts") ?: accountsRaw.optJSONArray("accounts")
            else -> null
        }
        if (list == null) return results

        for (i in 0 until list.length()) {
            val a = list.optJSONObject(i) ?: continue
            var accNum = optionalText(a, "account_number")
            if (accNum.isEmpty()) accNum = optionalText(a, "bank_account_number")
            var id = optionalText(a, "id")
            if (id.isEmpty()) id = accNum.ifEmpty { "acc:${i + 1}" }

            val nameObj = a.optJSONObject("name")
            var name = if (nameObj != null) language(nameObj) else optionalText(a, "name")
            if (name.isEmpty()) name = "Subkonto studenckie"

            var currency = PaymentAmounts.currencyCode(a.opt("currency"))
            if (currency.isEmpty()) currency = "PLN"

            val balance = finiteAmount(a, "balance")
            val owner = optionalText(a, "account_owner")
            val swift = optionalText(a, "swift")

            val facObj = a.optJSONObject("faculty")
            val faculty = if (facObj != null) {
                val facName = facObj.optJSONObject("name")
                if (facName != null) language(facName) else optionalText(facObj, "id")
            } else {
                optionalText(a, "faculty")
            }

            results.add(PaymentInfo.Account(id, name, currency, accNum, balance, faculty, owner, swift))
        }
        return results
    }

    @JvmStatic
    fun installments(paymentsRaw: Any?): List<PaymentInfo.Installment> {
        if (paymentsRaw == null) return emptyList()
        val results = ArrayList<PaymentInfo.Installment>()
        val list: JSONArray? = when (paymentsRaw) {
            is JSONArray -> paymentsRaw
            is JSONObject -> paymentsRaw.optJSONArray("user_payments")
                ?: paymentsRaw.optJSONArray("payments")
                ?: paymentsRaw.optJSONArray("installments")
            else -> null
        }
        if (list == null) return results

        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue

            // Direct legacy installment format
            if (item.has("amount") && !item.has("chosen_installment_plan") && !item.has("total_amount") && !item.has("type")) {
                val id = optionalText(item, "id")
                val nameObj = item.optJSONObject("name")
                var name = if (nameObj != null) language(nameObj) else optionalText(item, "name")
                if (name.isEmpty()) name = ApiLanguage.label("Należność", "Payment", "Платіж")
                val amount = finiteAmount(item, "amount")
                var currency = PaymentAmounts.currencyCode(item.opt("currency"))
                if (currency.isEmpty()) currency = "PLN"
                val deadline = optionalText(item, "deadline")
                val status = optionalText(item, "status")
                val paymentDate = optionalText(item, "payment_date")
                results.add(PaymentInfo.Installment(id, name, amount, currency, deadline, status, paymentDate))
                continue
            }

            // Real USOS payment object format
            val payId = optionalText(item, "id")
            val descObj = item.optJSONObject("description")
            val payDesc = if (descObj != null) language(descObj) else optionalText(item, "description")
            val payType = optionalText(item, "type")
            val payState = optionalText(item, "state")
            val payAcc = optionalText(item, "account_number")
            var payDeadline = optionalText(item, "payment_deadline")
            if (payDeadline.isEmpty()) payDeadline = optionalText(item, "deadline")
            var payCurrency = PaymentAmounts.currencyCode(item.opt("currency"))
            if (payCurrency.isEmpty()) payCurrency = "PLN"
            val totalAmount = when {
                item.has("total_amount") && !item.isNull("total_amount") -> {
                    val t = finiteAmount(item, "total_amount")
                    if (t != 0.0) t
                    else if (item.has("amount") && !item.isNull("amount")) finiteAmount(item, "amount")
                    else if (item.has("saldo_amount") && !item.isNull("saldo_amount")) kotlin.math.abs(finiteAmount(item, "saldo_amount"))
                    else 0.0
                }
                item.has("amount") && !item.isNull("amount") -> finiteAmount(item, "amount")
                item.has("saldo_amount") && !item.isNull("saldo_amount") -> kotlin.math.abs(finiteAmount(item, "saldo_amount"))
                else -> 0.0
            }

            val plan = item.optJSONObject("chosen_installment_plan")
            val planInsts = plan?.optJSONArray("installments")

            if (planInsts != null && planInsts.length() > 0) {
                for (j in 0 until planInsts.length()) {
                    val pi = planInsts.optJSONObject(j) ?: continue
                    val num = pi.optInt("number", j + 1)
                    val instId = if (payId.isEmpty()) "inst:${i + 1}:$num" else "$payId:rata:$num"
                    val piDescObj = pi.optJSONObject("description")
                    val piDesc = if (piDescObj != null) language(piDescObj) else optionalText(pi, "description")
                    val fullName = if (piDesc.isNotEmpty()) {
                        if (payDesc.isNotEmpty()) "$payDesc – $piDesc" else piDesc
                    } else {
                        if (payDesc.isNotEmpty()) "$payDesc (${ApiLanguage.label("rata", "installment", "внесок")} $num)" else "${ApiLanguage.label("Rata", "Installment", "Внесок")} $num"
                    }
                    val instAmount = finiteAmount(pi, "amount")
                    var instCurrency = PaymentAmounts.currencyCode(pi.opt("currency"))
                    if (instCurrency.isEmpty()) instCurrency = PaymentAmounts.currencyCode(plan.opt("currency"))
                    if (instCurrency.isEmpty()) instCurrency = payCurrency
                    var deadline = optionalText(pi, "payment_date")
                    if (deadline.isEmpty()) deadline = payDeadline

                    val status = when {
                        "paid".equals(payState, ignoreCase = true) -> ApiLanguage.label("Opłacona", "Paid", "Сплачено")
                        "cancelled".equals(payState, ignoreCase = true) -> ApiLanguage.label("Anulowana", "Cancelled", "Скасовано")
                        else -> "scheduled"
                    }
                    val paymentDate = optionalText(pi, "payment_date_settled")
                    results.add(PaymentInfo.Installment(instId, fullName, instAmount, instCurrency, deadline, status, paymentDate, payAcc, payType))
                }
            } else {
                val instId = payId.ifEmpty { "pay:${i + 1}" }
                val fullName = payDesc.ifEmpty { ApiLanguage.label("Należność USOS", "USOS payment", "Платіж USOS") }
                var status = optionalText(item, "status")
                if (status.isEmpty()) status = payState
                status = when {
                    "paid".equals(status, ignoreCase = true) -> ApiLanguage.label("Opłacona", "Paid", "Сплачено")
                    "cancelled".equals(status, ignoreCase = true) -> ApiLanguage.label("Anulowana", "Cancelled", "Скасовано")
                    else -> "settlement_unknown"
                }
                val paymentDate = optionalText(item, "payment_date")
                results.add(PaymentInfo.Installment(instId, fullName, totalAmount, payCurrency, payDeadline, status, paymentDate, payAcc, payType))
            }
        }
        return results
    }

    @JvmStatic
    @JvmOverloads
    fun payments(
        accountsRaw: Any?,
        paymentsRaw: Any?,
        scopeMissing: Boolean = false,
        statusNotice: String = ""
    ): PaymentInfo {
        val accounts = ArrayList(accounts(accountsRaw))
        val installments = installments(paymentsRaw)

        val knownAccounts = HashSet<String>()
        for (a in accounts) {
            if (a.bankAccountNumber.isNotEmpty()) {
                knownAccounts.add(a.bankAccountNumber.replace(Regex("\\s+"), ""))
            }
        }
        for (inst in installments) {
            if (inst.bankAccountNumber.isNotEmpty()) {
                val clean = inst.bankAccountNumber.replace(Regex("\\s+"), "")
                if (clean.isNotEmpty() && !knownAccounts.contains(clean)) {
                    knownAccounts.add(clean)
                    val accName = ApiLanguage.label("Subkonto do wpłat: ", "Payment account: ", "Рахунок для платежів: ") + inst.name
                    accounts.add(
                        PaymentInfo.Account(
                            "acc:pay:${knownAccounts.size}", accName,
                            inst.currency, inst.bankAccountNumber, 0.0
                        )
                    )
                }
            }
        }

        return PaymentInfo(accounts, installments, scopeMissing, statusNotice)
    }

    @JvmStatic
    fun payments(paymentsJson: JSONObject?): PaymentInfo {
        if (paymentsJson == null) return PaymentInfo(emptyList(), emptyList())
        val accObj = paymentsJson.opt("user_accounts") ?: paymentsJson.opt("accounts")
        val payObj = paymentsJson.opt("installments")
            ?: paymentsJson.opt("user_payments")
            ?: paymentsJson.opt("payments")
        return payments(
            accObj, payObj,
            paymentsJson.optBoolean("scopeMissing", false),
            paymentsJson.optString("statusNotice", "")
        )
    }

    @JvmStatic
    fun academicTerms(termsJson: JSONObject?): Map<String, AcademicTerm> {
        if (termsJson == null) return emptyMap()
        val terms = LinkedHashMap<String, AcademicTerm>()
        for (id in sortedKeys(termsJson)) {
            val t = termsJson.optJSONObject(id) ?: continue
            val nameObj = t.optJSONObject("name")
            var name = if (nameObj != null) language(nameObj) else optionalText(t, "name")
            if (name.isEmpty()) name = id
            val start = optionalText(t, "start_date")
            val end = optionalText(t, "end_date")
            val finish = optionalText(t, "finish_date")
            terms[id] = AcademicTerm(id, name, start, end, finish)
        }
        return terms
    }

    @JvmStatic
    fun thesis(thesesRaw: Any?): ThesisInfo {
        if (thesesRaw == null) return ThesisInfo("", "", "", "")
        var tObj: JSONObject? = null
        if (thesesRaw is JSONArray) {
            if (thesesRaw.length() > 0) tObj = thesesRaw.optJSONObject(0)
        } else if (thesesRaw is JSONObject) {
            val arr = thesesRaw.optJSONArray("authored_theses") ?: thesesRaw.optJSONArray("theses")
            tObj = if (arr != null && arr.length() > 0) arr.optJSONObject(0) else thesesRaw
        }
        if (tObj == null) return ThesisInfo("", "", "", "")
        val titleObj = tObj.optJSONObject("titles") ?: tObj.optJSONObject("title")
        val translatedTitle = if (titleObj != null) language(titleObj) else ""
        val title = translatedTitle.ifEmpty { optionalText(tObj, "title") }
        val statusObj = tObj.optJSONObject("status")
        val status = if (statusObj != null) language(statusObj) else optionalText(tObj, "status")

        val supervisors = ArrayList<String>()
        val supArr = tObj.optJSONArray("supervisors")
        if (supArr != null) {
            for (i in 0 until supArr.length()) {
                val sup = supArr.optJSONObject(i) ?: continue
                val first = optionalText(sup, "first_name").trim()
                val last = optionalText(sup, "last_name").trim()
                val name = "$first $last".trim()
                if (name.isNotEmpty()) supervisors.add(name)
            }
        }

        val reviewers = ArrayList<String>()
        val revArr = tObj.optJSONArray("reviewers")
        if (revArr != null) {
            for (i in 0 until revArr.length()) {
                val rev = revArr.optJSONObject(i) ?: continue
                val first = optionalText(rev, "first_name").trim()
                val last = optionalText(rev, "last_name").trim()
                val name = "$first $last".trim()
                if (name.isNotEmpty()) reviewers.add(name)
            }
        }

        return ThesisInfo(title, status, supervisors.joinToString(", "), reviewers.joinToString(", "))
    }

    @JvmStatic
    fun courseTests(testsRaw: Any?, courseNames: Map<String, String>?): List<CourseTestRecord> {
        if (testsRaw == null) return emptyList()
        val results = ArrayList<CourseTestRecord>()
        val list: JSONArray? = when (testsRaw) {
            is JSONArray -> testsRaw
            is JSONObject -> testsRaw.optJSONArray("tests")
            else -> null
        }
        if (list == null) return results

        for (i in 0 until list.length()) {
            val test = list.optJSONObject(i) ?: continue
            val id = optionalText(test, "id")
            val courseId = optionalText(test, "course_id")
            val courseName = courseNames?.getOrDefault(courseId, courseId) ?: courseId
            val nameObj = test.optJSONObject("name")
            var testName = if (nameObj != null) language(nameObj) else optionalText(test, "name")
            if (testName.isEmpty()) testName = ApiLanguage.label("Sprawdzian", "Test", "Тест")
            val points = test.optDouble("points", 0.0)
            val maxPoints = test.optDouble("max_points", 0.0)
            val grade = optionalText(test, "grade")
            val date = optionalText(test, "date")
            results.add(CourseTestRecord(id, courseId, courseName, testName, points, maxPoints, grade, date))
        }
        return results
    }

    private fun localizedText(value: Any?): String = when {
        value is JSONObject -> language(value)
        value == null || value === JSONObject.NULL -> ""
        else -> value.toString()
    }

    /** participant2 returns course-test trees, rather than a flat list of exam grades. */
    @JvmStatic
    fun courseTestTrees(tests: JSONArray, names: Map<String, String>): List<CourseTestRecord> {
        val result = ArrayList<CourseTestRecord>()
        val seen = HashSet<String>()
        for (i in 0 until tests.length()) {
            val test = tests.optJSONObject(i) ?: continue
            val edition = test.optJSONObject("course_edition")
            val course = edition?.optJSONObject("course")
            val term = edition?.optJSONObject("term")
            val id = if (course == null) "" else optionalText(course, "id")
            var title = if (course == null) "" else localizedText(course.opt("name"))
            if (title.isEmpty()) title = names.getOrDefault(id, id)
            val termId = if (term == null) "" else optionalText(term, "id")
            collectTestNodes(test.optJSONObject("root"), id, title, termId, result, seen)
        }
        return result
    }

    private fun collectTestNodes(
        node: JSONObject?,
        courseId: String,
        courseName: String,
        term: String,
        result: MutableList<CourseTestRecord>,
        seen: MutableSet<String>
    ) {
        if (node == null || !node.optBoolean("visible_for_students", true)) return
        val id = optionalText(node, "id")
        if (id.isEmpty() || !seen.add("$term:$id")) return
        val task = node.optJSONObject("task_node_details")
        val grading = node.optJSONObject("grade_node_details")
        val points = task?.optJSONObject("students_points") ?: node.optJSONObject("students_points")
        val grade = grading?.optJSONObject("students_grade")
        val value = grade?.optJSONObject("grade_value")
        val hasPoints = points != null && !points.isNull("points") && points.has("points")
        val hasGrade = value != null && optionalText(value, "symbol").isNotEmpty()
        val pointValue = if (hasPoints) points.optDouble("points", Double.NaN) else 0.0
        val maximum = if (task != null && task.has("points_max") && !task.isNull("points_max")) task.optDouble("points_max", Double.NaN) else 0.0
        if (!pointValue.isFinite() || !maximum.isFinite()) throw JSONException("Invalid course-test points")
        if (hasPoints || hasGrade) {
            result.add(
                CourseTestRecord(
                    id, courseId, courseName,
                    localizedText(node.opt("name")),
                    pointValue,
                    maximum,
                    if (hasGrade) optionalText(value, "symbol") else "",
                    if (hasGrade) optionalText(grade, "last_changed") else (if (points != null) optionalText(points, "last_changed") else ""),
                    term, hasPoints
                )
            )
        }
        for (key in arrayOf("subnodes_deep", "subnodes")) {
            val children = node.optJSONArray(key)
            if (children != null) {
                for (i in 0 until children.length()) {
                    collectTestNodes(children.optJSONObject(i), courseId, courseName, term, result, seen)
                }
            }
        }
    }

    @JvmStatic
    fun newsArticles(raw: Any?): List<NewsArticle> {
        if (raw == null) return emptyList()
        val results = ArrayList<NewsArticle>()
        val items: JSONArray? = when (raw) {
            is JSONObject -> raw.optJSONArray("items") ?: raw.optJSONArray("articles")
            is JSONArray -> raw
            else -> null
        }
        if (items == null) return results

        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            val a = it.optJSONObject("article") ?: it

            val id = optionalText(a, "id")
            val titleObj = a.optJSONObject("title")
            val title = if (titleObj != null) language(titleObj) else optionalText(a, "title")
            if (title.isEmpty()) continue

            var pubDate = optionalText(a, "publication_date")
            if (pubDate.length > 10 && (pubDate[10] == ' ' || pubDate[10] == 'T')) {
                pubDate = pubDate.substring(0, 10)
            }
            val headlineObj = a.optJSONObject("headline_html") ?: a.optJSONObject("headline")
            var headline = if (headlineObj != null) language(headlineObj) else optionalText(a, "headline_html")
            if (headline.isEmpty()) headline = optionalText(a, "headline")
            val cleanHeadline = headline.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

            var authorName = ""
            val authorObj = a.optJSONObject("author")
            if (authorObj != null) {
                val first = optionalText(authorObj, "first_name")
                val last = optionalText(authorObj, "last_name")
                authorName = "$first $last".trim()
                if (authorName.isEmpty()) authorName = language(authorObj)
            }
            if (authorName.isEmpty()) authorName = optionalText(a, "author_name")
            if (authorName.isEmpty()) authorName = optionalText(a, "author")

            val url = optionalText(a, "url")
            results.add(NewsArticle(id, title, pubDate, authorName, cleanHeadline, url, localizedText(a.opt("content_html"))))
        }
        return results
    }
}

