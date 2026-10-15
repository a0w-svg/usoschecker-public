package pl.vistula.checker.core;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Validates response shape before a snapshot can be committed. */
public final class UsosParser {
    private UsosParser() {}
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static String optionalText(JSONObject o, String field) {
        return o.isNull(field) ? "" : o.optString(field, "");
    }
    public static String language(JSONObject o) {
        String pl = optionalText(o, "pl");
        if (!pl.isEmpty()) return pl;
        String en = optionalText(o, "en");
        if (!en.isEmpty()) return en;
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) { String value = optionalText(o, keys.next()); if (!value.isEmpty()) return value; }
        return "";
    }
    public static Map<String, String> courseNames(JSONObject courses) {
        Map<String, String> names = new TreeMap<>();
        for (String term : sortedKeys(courses)) {
            JSONArray rows = courses.optJSONArray(term);
            if (rows == null) continue;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject c = rows.optJSONObject(i);
                if (c == null) continue;
                String id = c.optString("course_id");
                if (id.isEmpty() || c.optJSONObject("course_name") == null) continue;
                names.put(id, language(c.optJSONObject("course_name")));
            }
        }
        return names;
    }
    public static List<String> sortedKeys(JSONObject object) {
        List<String> keys = new ArrayList<>();
        object.keys().forEachRemaining(keys::add);
        Collections.sort(keys); return keys;
    }

    public static Map<String, Double> ectsPoints(JSONObject response) {
        Map<String, Double> map = new HashMap<>();
        if (response == null) return map;
        Iterator<String> termKeys = response.keys();
        while (termKeys.hasNext()) {
            String term = termKeys.next();
            if (response.isNull(term)) continue;
            JSONObject termObj = response.optJSONObject(term);
            if (termObj == null) continue;
            Iterator<String> courseKeys = termObj.keys();
            while (courseKeys.hasNext()) {
                String course = courseKeys.next();
                if (termObj.isNull(course)) continue;
                Object raw = termObj.opt(course);
                if (raw == null) continue;
                try {
                    String clean = raw.toString().trim().replace(',', '.');
                    double value = Double.parseDouble(clean);
                    if (Double.isFinite(value) && value >= 0) {
                        map.put(term + "\0" + course, value);
                        map.putIfAbsent(course, value);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    public static final class UserProfile {
        public final String name;
        public final String studentNumber;
        public final String email;
        public final String programme;
        public final String studentStatus;
        public final String stage;
        public final String admissionDate;
        public final String photoUrl;

        public UserProfile(String name, String studentNumber, String email, String programme, String studentStatus) {
            this(name, studentNumber, email, programme, studentStatus, "", "", "");
        }

        public UserProfile(String name, String studentNumber, String email, String programme, String studentStatus,
                           String stage, String admissionDate, String photoUrl) {
            this.name = name;
            this.studentNumber = studentNumber;
            this.email = email;
            this.programme = programme;
            this.studentStatus = studentStatus;
            this.stage = stage;
            this.admissionDate = admissionDate;
            this.photoUrl = photoUrl;
        }
    }

    public static UserProfile userProfile(JSONObject user) {
        if (user == null) return new UserProfile("", "", "", "", "");
        String first = optionalText(user, "first_name").trim();
        String last = optionalText(user, "last_name").trim();
        String name = (first + " " + last).trim();
        String studentNumber = optionalText(user, "student_number").trim();
        String email = optionalText(user, "email").trim();

        String programme = "";
        String stage = "";
        String admissionDate = "";
        JSONArray progs = user.optJSONArray("student_programmes");
        if (progs != null) {
            for (int i = 0; i < progs.length(); i++) {
                JSONObject sp = progs.optJSONObject(i);
                if (sp == null) continue;
                if (admissionDate.isEmpty()) admissionDate = optionalText(sp, "admission_date");
                JSONArray stages = sp.optJSONArray("stages");
                if (stages != null && stages.length() > 0) {
                    JSONObject lastStage = stages.optJSONObject(stages.length() - 1);
                    if (lastStage != null) {
                        String stId = optionalText(lastStage, "stage_id");
                        if (!stId.isEmpty()) stage = stId;
                    }
                }
                JSONObject p = sp.optJSONObject("programme");
                if (p != null) {
                    JSONObject pName = p.optJSONObject("name");
                    if (pName != null) {
                        String candidate = language(pName);
                        if (!candidate.isEmpty()) {
                            programme = candidate;
                            if ("active".equals(sp.optString("status", ""))) break;
                        }
                    }
                }
            }
        }

        String studentStatus = "";
        if (user.has("student_status") && !user.isNull("student_status")) {
            int status = user.optInt("student_status", -1);
            if (status == 2) studentStatus = "Aktywny student";
            else if (status == 1) studentStatus = "Były student";
            else if (status == 0) studentStatus = "Nie jest studentem";
        }

        String photoUrl = "";
        JSONObject photos = user.optJSONObject("photo_urls");
        if (photos != null) {
            photoUrl = optionalText(photos, "200x200");
            if (photoUrl.isEmpty()) photoUrl = optionalText(photos, "100x100");
        }

        return new UserProfile(name, studentNumber, email, programme, studentStatus, stage, admissionDate, photoUrl);
    }

    public static void grades(Snapshot s, JSONObject grades, Map<String, String> names) throws JSONException {
        grades(s, grades, names, Collections.emptyMap());
    }

    public static void grades(Snapshot s, JSONObject grades, Map<String, String> names, Map<String, Double> ectsPoints) throws JSONException {
        for (String term : sortedKeys(grades)) {
            JSONObject courses = grades.getJSONObject(term);
            if (courses == null) continue;
            for (String course : sortedKeys(courses)) {
                JSONObject edition = courses.getJSONObject(course);
                if (edition == null) continue;
                if (!edition.has("course_grades") || !edition.has("course_units_grades")) throw new JSONException("Niepełne dane ocen.");
                if (!edition.isNull("course_grades"))
                    readExams(s, edition.getJSONArray("course_grades"), course, names, term, ectsPoints, null);
                JSONObject units = edition.isNull("course_units_grades") ? null : edition.getJSONObject("course_units_grades");
                if (units != null) {
                    for (String unit : sortedKeys(units)) {
                        if (!units.isNull(unit))
                            readExams(s, units.getJSONArray(unit), course, names, term, ectsPoints, unit);
                    }
                }
            }
        }
    }
    static String detectClassType(String unitId, String gradeTypeId, String comment, String description, String symbol, Snapshot s) {
        if (s != null && unitId != null && !unitId.isEmpty()) {
            String fromPlan = s.findClassTypeForUnit(unitId);
            if (!fromPlan.isEmpty()) return fromPlan;
        }
        // grade_type_id identifies the grading scale, not the class type.
        return "";
    }
    private static void readExams(Snapshot s, JSONArray exams, String course, Map<String, String> names, String term, Map<String, Double> ectsPoints, String unitId) throws JSONException {
        Double ectsVal = ectsPoints.get(term + "\0" + course);
        if (ectsVal == null) ectsVal = ectsPoints.get(course);
        double ects = ectsVal != null ? ectsVal : Double.NaN;

        for (int i = 0; i < exams.length(); i++) {
            JSONObject sessions = exams.getJSONObject(i);
            if (sessions == null) continue;
            for (String session : sortedKeys(sessions)) {
                if (sessions.isNull(session)) continue;
                JSONObject g = sessions.getJSONObject(session);
                if (g == null) continue;
                String exam = g.get("exam_id").toString();
                String number = g.get("exam_session_number").toString();
                String symbol = g.getString("value_symbol");
                String modified = optionalText(g, "date_modified");
                if (!modified.isEmpty()) {
                    LocalDateTime.parse(modified, TIME);
                }
                String comment = optionalText(g, "comment");
                String value = symbol + " · termin " + number + (comment.isEmpty() ? "" : "\n" + comment);

                JSONObject meta = new JSONObject();
                meta.put("rawGrade", new JSONObject(g.toString()));
                meta.put("courseId", course);
                meta.put("examId", exam);
                meta.put("sessionNumber", number);
                meta.put("term", term);
                meta.put("valueSymbol", symbol);
                if (unitId != null && !unitId.isEmpty()) {
                    meta.put("unitId", unitId);
                    meta.put("gradeScope", "unit");
                } else {
                    meta.put("gradeScope", "course");
                }
                if (!modified.isEmpty()) meta.put("dateModified", modified);
                if (g.has("passes") && !g.isNull("passes")) meta.put("passes", g.optBoolean("passes"));
                JSONObject valDesc = g.optJSONObject("value_description");
                String desc = valDesc != null ? language(valDesc) : optionalText(g, "value_description");
                if (!desc.isEmpty()) meta.put("valueDescription", desc);
                if (g.has("counts_into_average") && !g.isNull("counts_into_average")) meta.put("countsIntoAverage", g.optBoolean("counts_into_average"));
                String gradeTypeId = optionalText(g, "grade_type_id");
                if (!gradeTypeId.isEmpty()) meta.put("gradeTypeId", gradeTypeId);
                String dateAcq = optionalText(g, "date_acquisition");
                if (!dateAcq.isEmpty()) meta.put("dateAcquisition", dateAcq);
                String modAuthor = optionalText(g, "modification_author");
                if (!modAuthor.isEmpty()) meta.put("modificationAuthor", modAuthor);
                if (!comment.isEmpty()) meta.put("comment", comment);
                if (!Double.isNaN(ects)) meta.put("ects", ects);

                String inferredType = detectClassType(unitId, gradeTypeId, comment, desc, symbol, s);
                if (!inferredType.isEmpty()) meta.put("classType", inferredType).put("classTypeSource", "timetable");

                Record r = new Record("grade:" + exam + ":" + number, "ocena", names.getOrDefault(course, course), value, modified, "", term, "", "", ects, meta);
                // The same exam can be referenced by a course and its unit.
                Record existing = s.records.get(r.id);
                if (existing == null) {
                    s.add(r);
                } else {
                    if (!existing.same(r)) throw new IllegalArgumentException("Sprzeczne dane oceny: " + r.id);
                    if (unitId != null && !unitId.isEmpty() && existing.optMeta("unitId").isEmpty()) {
                        try {
                            existing.metadata.put("alsoCourseProtocol", "course".equals(existing.optMeta("gradeScope")));
                            existing.metadata.put("unitId", unitId);
                            existing.metadata.put("gradeScope", "unit");
                            if (!inferredType.isEmpty() && existing.optMeta("classType").isEmpty()) {
                                existing.metadata.put("classType", inferredType);
                            }
                        } catch (JSONException ignored) {}
                    }
                }
            }
        }
    }
    public static Set<String> lecturerIds(JSONArray activities) throws JSONException {
        Set<String> ids = new TreeSet<>();
        for (int i = 0; i < activities.length(); i++) {
            JSONArray lecturers = activities.getJSONObject(i).optJSONArray("lecturer_ids");
            if (lecturers == null) continue;
            for (int j = 0; j < lecturers.length(); j++) if (!lecturers.isNull(j)) {
                String id = lecturers.get(j).toString();
                if (!id.matches("[0-9]+")) throw new JSONException("Nieprawidłowy identyfikator prowadzącego.");
                ids.add(id);
            }
        }
        return ids;
    }
    public static Map<String, String> lecturerNames(JSONObject users) throws JSONException {
        Map<String, String> names = new TreeMap<>();
        for (String id : sortedKeys(users)) {
            if (users.isNull(id)) continue;
            JSONObject person = users.getJSONObject(id);
            String first = optionalText(person, "first_name").trim();
            String last = optionalText(person, "last_name").trim();
            String name = (first + " " + last).trim();
            if (!name.isEmpty()) names.put(id, name);
        }
        return names;
    }
    public static void timetable(Snapshot s, JSONArray activities) {
        timetable(s, activities, java.util.Collections.emptyMap());
    }
    public static void timetable(Snapshot s, JSONArray activities, Map<String, String> lecturerNames) {
        for (int i = 0; i < activities.length(); i++) {
            JSONObject a = activities.optJSONObject(i);
            if (a == null) throw new IllegalArgumentException("Niepełne dane planu.");
            String type = a.optString("type", "");
            String start = a.optString("start_time", ""), end = a.optString("end_time", "");
            if (start.isEmpty() || end.isEmpty()) throw new IllegalArgumentException("Brak godzin zajęć.");
            try {
                LocalDateTime parsedStart = LocalDateTime.parse(start, TIME);
                if (!LocalDateTime.parse(end, TIME).isAfter(parsedStart)) throw new IllegalArgumentException("Nieprawidłowe godziny zajęć.");
                String date = parsedStart.toLocalDate().toString();
                if (!s.covers(date)) continue;

                String genericName = a.optJSONObject("name") == null ? "Brak nazwy" : language(a.getJSONObject("name"));
                String courseName = a.optJSONObject("course_name") == null ? genericName : language(a.getJSONObject("course_name"));

                String stable;
                if (type.equals("classgroup2") && !a.isNull("sm_id")) stable = "sm:" + a.optString("sm_id", "");
                else if (type.equals("classgroup")) stable = "cg:" + a.optString("unit_id", "") + ":" + a.optString("group_number", "") + ":" + a.optString("cgwm_id", "") + ":" + date;
                else stable = type + ":" + a.optString("unit_id", "") + ":" + a.optString("group_number", "") + ":" + courseName + ":" + start;
                String room = optionalText(a, "room_number");
                String building = a.optJSONObject("building_name") == null ? "" : language(a.getJSONObject("building_name"));
                String classType = a.optJSONObject("classtype_name") == null ? "" : language(a.getJSONObject("classtype_name"));
                JSONArray lecturers = a.optJSONArray("lecturer_ids");
                List<String> ids = new ArrayList<>();
                if (lecturers != null) {
                    for (int j = 0; j < lecturers.length(); j++) {
                        if (lecturers.isNull(j)) continue;
                        String lid = String.valueOf(lecturers.opt(j));
                        ids.add(lecturerNames.getOrDefault(lid, "ID: " + lid));
                    }
                }
                Collections.sort(ids);
                String value = start.substring(11, 16) + " – " + end.substring(11, 16)
                    + (classType.isEmpty() ? "" : "\n" + classType)
                    + (room.isEmpty() ? "" : "\nSala " + room)
                    + (building.isEmpty() ? "" : " · " + building)
                    + (ids.isEmpty() ? "" : "\nProwadzący: " + String.join(", ", ids))
                    + (a.optString("frequency", "").equals("other") || a.optString("frequency", "").equals("once") ? "\nTermin orientacyjny" : "");

                JSONObject meta = new JSONObject();
                meta.put("rawLesson", new JSONObject(a.toString()));
                String courseId = optionalText(a, "course_id");
                if (!courseId.isEmpty()) meta.put("courseId", courseId);
                if (!room.isEmpty()) meta.put("room", room);
                String roomId = optionalText(a, "room_id");
                if (!roomId.isEmpty()) meta.put("roomId", roomId);
                if (!building.isEmpty()) meta.put("building", building);
                String buildingId = optionalText(a, "building_id");
                if (!buildingId.isEmpty()) meta.put("buildingId", buildingId);
                if (!classType.isEmpty()) meta.put("classType", classType);
                String classTypeId = optionalText(a, "classtype_id");
                if (!classTypeId.isEmpty()) meta.put("classTypeId", classTypeId);
                String groupNumber = optionalText(a, "group_number");
                if (!groupNumber.isEmpty()) meta.put("groupNumber", groupNumber);
                if (!ids.isEmpty()) meta.put("lecturers", String.join(", ", ids));
                String freq = a.optString("frequency", "");
                if (!freq.isEmpty()) meta.put("frequency", freq);
                if (!type.isEmpty()) meta.put("type", type);
                String unitId = optionalText(a, "unit_id");
                if (!unitId.isEmpty()) meta.put("unitId", unitId);
                String cgwmId = optionalText(a, "cgwm_id");
                if (!cgwmId.isEmpty()) meta.put("cgwmId", cgwmId);
                String smId = optionalText(a, "sm_id");
                if (!smId.isEmpty()) meta.put("smId", smId);
                String url = optionalText(a, "url");
                if (!url.isEmpty()) meta.put("url", url);
                String profileUrl = optionalText(a, "classgroup_profile_url");
                if (!profileUrl.isEmpty()) meta.put("profileUrl", profileUrl);
                meta.put("startTime", start);
                meta.put("endTime", end);
                meta.put("date", date);

                s.add(new Record("plan:" + stable, "plan", courseName, value, "", date, "", start, end, Double.NaN, meta));
            } catch (Exception e) { throw new IllegalArgumentException("Nieprawidłowe dane planu.", e); }
        }
    }

    public static List<PaymentInfo.Account> accounts(Object accountsRaw) {
        if (accountsRaw == null) return Collections.emptyList();
        List<PaymentInfo.Account> results = new ArrayList<>();
        JSONArray list = null;
        if (accountsRaw instanceof JSONArray) {
            list = (JSONArray) accountsRaw;
        } else if (accountsRaw instanceof JSONObject) {
            JSONObject obj = (JSONObject) accountsRaw;
            list = obj.optJSONArray("user_accounts");
            if (list == null) list = obj.optJSONArray("accounts");
        }
        if (list == null) return results;

        for (int i = 0; i < list.length(); i++) {
            JSONObject a = list.optJSONObject(i);
            if (a == null) continue;
            String accNum = optionalText(a, "account_number");
            if (accNum.isEmpty()) accNum = optionalText(a, "bank_account_number");
            String id = optionalText(a, "id");
            if (id.isEmpty()) id = !accNum.isEmpty() ? accNum : ("acc:" + (i + 1));

            JSONObject nameObj = a.optJSONObject("name");
            String name = nameObj != null ? language(nameObj) : optionalText(a, "name");
            if (name.isEmpty()) name = "Subkonto studenckie";

            String currency = optionalText(a, "currency");
            if (currency.isEmpty()) currency = "PLN";

            double balance = a.optDouble("balance", 0.0);
            String owner = optionalText(a, "account_owner");
            String swift = optionalText(a, "swift");

            String faculty = "";
            JSONObject facObj = a.optJSONObject("faculty");
            if (facObj != null) {
                JSONObject facName = facObj.optJSONObject("name");
                faculty = facName != null ? language(facName) : optionalText(facObj, "id");
            } else {
                faculty = optionalText(a, "faculty");
            }

            results.add(new PaymentInfo.Account(id, name, currency, accNum, balance, faculty, owner, swift));
        }
        return results;
    }

    public static List<PaymentInfo.Installment> installments(Object paymentsRaw) {
        if (paymentsRaw == null) return Collections.emptyList();
        List<PaymentInfo.Installment> results = new ArrayList<>();
        JSONArray list = null;
        if (paymentsRaw instanceof JSONArray) {
            list = (JSONArray) paymentsRaw;
        } else if (paymentsRaw instanceof JSONObject) {
            JSONObject obj = (JSONObject) paymentsRaw;
            list = obj.optJSONArray("user_payments");
            if (list == null) list = obj.optJSONArray("payments");
            if (list == null) list = obj.optJSONArray("installments");
        }
        if (list == null) return results;

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;

            // Direct legacy installment format
            if (item.has("amount") && !item.has("chosen_installment_plan") && !item.has("total_amount") && !item.has("type")) {
                String id = String.valueOf(item.opt("id"));
                JSONObject nameObj = item.optJSONObject("name");
                String name = nameObj != null ? language(nameObj) : optionalText(item, "name");
                if (name.isEmpty()) name = "Należność";
                double amount = item.optDouble("amount", 0.0);
                String currency = optionalText(item, "currency");
                if (currency.isEmpty()) currency = "PLN";
                String deadline = optionalText(item, "deadline");
                String status = optionalText(item, "status");
                String paymentDate = optionalText(item, "payment_date");
                results.add(new PaymentInfo.Installment(id, name, amount, currency, deadline, status, paymentDate));
                continue;
            }

            // Real USOS payment object format
            String payId = String.valueOf(item.opt("id"));
            JSONObject descObj = item.optJSONObject("description");
            String payDesc = descObj != null ? language(descObj) : optionalText(item, "description");
            String payType = optionalText(item, "type");
            String payState = optionalText(item, "state");
            String payAcc = optionalText(item, "account_number");
            String payDeadline = optionalText(item, "payment_deadline");
            if (payDeadline.isEmpty()) payDeadline = optionalText(item, "deadline");
            String payCurrency = optionalText(item, "currency");
            if (payCurrency.isEmpty()) payCurrency = "PLN";
            double totalAmount = item.optDouble("total_amount", item.optDouble("amount", 0.0));

            JSONObject plan = item.optJSONObject("chosen_installment_plan");
            JSONArray planInsts = plan != null ? plan.optJSONArray("installments") : null;

            if (planInsts != null && planInsts.length() > 0) {
                for (int j = 0; j < planInsts.length(); j++) {
                    JSONObject pi = planInsts.optJSONObject(j);
                    if (pi == null) continue;
                    int num = pi.optInt("number", j + 1);
                    String instId = payId.isEmpty() ? ("inst:" + (i + 1) + ":" + num) : (payId + ":rata:" + num);
                    JSONObject piDescObj = pi.optJSONObject("description");
                    String piDesc = piDescObj != null ? language(piDescObj) : optionalText(pi, "description");
                    String fullName = !piDesc.isEmpty()
                        ? (!payDesc.isEmpty() ? (payDesc + " – " + piDesc) : piDesc)
                        : (!payDesc.isEmpty() ? (payDesc + " (rata " + num + ")") : ("Rata " + num));
                    double instAmount = pi.optDouble("amount", 0.0);
                    String instCurrency = optionalText(pi, "currency");
                    if (instCurrency.isEmpty() && plan != null) instCurrency = plan.optString("currency", payCurrency);
                    if (instCurrency.isEmpty()) instCurrency = payCurrency;
                    String deadline = optionalText(pi, "payment_date");
                    if (deadline.isEmpty()) deadline = payDeadline;

                    String status = optionalText(pi, "status");
                    if (status.isEmpty()) {
                        if ("paid".equalsIgnoreCase(payState)) {
                            status = "Opłacona";
                        } else if ("cancelled".equalsIgnoreCase(payState)) {
                            status = "Anulowana";
                        } else {
                            boolean overdue = false;
                            if (!deadline.isEmpty()) {
                                try {
                                    String d = deadline.length() > 10 ? deadline.substring(0, 10) : deadline;
                                    overdue = LocalDate.parse(d).isBefore(LocalDate.now());
                                } catch (Exception ignored) {}
                            }
                            status = overdue ? "Po terminie" : "Do zapłaty";
                        }
                    }
                    String paymentDate = optionalText(pi, "payment_date_settled");
                    if (paymentDate.isEmpty() && "paid".equalsIgnoreCase(payState)) {
                        paymentDate = optionalText(item, "date_of_plan_choice");
                    }
                    results.add(new PaymentInfo.Installment(instId, fullName, instAmount, instCurrency, deadline, status, paymentDate, payAcc, payType));
                }
            } else {
                String instId = payId.isEmpty() ? ("pay:" + (i + 1)) : payId;
                String fullName = !payDesc.isEmpty() ? payDesc : "Należność USOS";
                String status = optionalText(item, "status");
                if (status.isEmpty()) status = payState;
                if ("paid".equalsIgnoreCase(status)) status = "Opłacona";
                else if ("unsettled".equalsIgnoreCase(status)) status = "Do zapłaty";
                else if ("overdue".equalsIgnoreCase(status)) status = "Po terminie";
                else if ("underpaid".equalsIgnoreCase(status)) status = "Niedopłata";
                else if ("cancelled".equalsIgnoreCase(status)) status = "Anulowana";
                else if (status.isEmpty()) {
                    boolean overdue = false;
                    if (!payDeadline.isEmpty()) {
                        try {
                            String d = payDeadline.length() > 10 ? payDeadline.substring(0, 10) : payDeadline;
                            overdue = LocalDate.parse(d).isBefore(LocalDate.now());
                        } catch (Exception ignored) {}
                    }
                    status = overdue ? "Po terminie" : "Do zapłaty";
                }
                String paymentDate = optionalText(item, "payment_date");
                results.add(new PaymentInfo.Installment(instId, fullName, totalAmount, payCurrency, payDeadline, status, paymentDate, payAcc, payType));
            }
        }
        return results;
    }

    public static PaymentInfo payments(Object accountsRaw, Object paymentsRaw) {
        return payments(accountsRaw, paymentsRaw, false, "");
    }

    public static PaymentInfo payments(Object accountsRaw, Object paymentsRaw, boolean scopeMissing, String statusNotice) {
        List<PaymentInfo.Account> accounts = new ArrayList<>(accounts(accountsRaw));
        List<PaymentInfo.Installment> installments = installments(paymentsRaw);

        Set<String> knownAccounts = new HashSet<>();
        for (PaymentInfo.Account a : accounts) {
            if (!a.bankAccountNumber.isEmpty()) knownAccounts.add(a.bankAccountNumber.replaceAll("\\s+", ""));
        }
        for (PaymentInfo.Installment inst : installments) {
            if (!inst.bankAccountNumber.isEmpty()) {
                String clean = inst.bankAccountNumber.replaceAll("\\s+", "");
                if (!clean.isEmpty() && !knownAccounts.contains(clean)) {
                    knownAccounts.add(clean);
                    String accName = "Subkonto do wpłat: " + inst.name;
                    accounts.add(new PaymentInfo.Account("acc:pay:" + knownAccounts.size(), accName,
                        inst.currency, inst.bankAccountNumber, 0.0));
                }
            }
        }

        return new PaymentInfo(accounts, installments, scopeMissing, statusNotice);
    }

    public static PaymentInfo payments(JSONObject paymentsJson) {
        if (paymentsJson == null) return new PaymentInfo(Collections.emptyList(), Collections.emptyList());
        Object accObj = paymentsJson.opt("user_accounts");
        if (accObj == null) accObj = paymentsJson.opt("accounts");
        Object payObj = paymentsJson.opt("installments");
        if (payObj == null) payObj = paymentsJson.opt("user_payments");
        if (payObj == null) payObj = paymentsJson.opt("payments");
        return payments(accObj, payObj, paymentsJson.optBoolean("scopeMissing", false), paymentsJson.optString("statusNotice", ""));
    }

    public static Map<String, AcademicTerm> academicTerms(JSONObject termsJson) {
        if (termsJson == null) return Collections.emptyMap();
        Map<String, AcademicTerm> terms = new LinkedHashMap<>();
        for (String id : sortedKeys(termsJson)) {
            JSONObject t = termsJson.optJSONObject(id);
            if (t == null) continue;
            JSONObject nameObj = t.optJSONObject("name");
            String name = nameObj != null ? language(nameObj) : optionalText(t, "name");
            if (name.isEmpty()) name = id;
            String start = optionalText(t, "start_date");
            String end = optionalText(t, "end_date");
            String finish = optionalText(t, "finish_date");
            terms.put(id, new AcademicTerm(id, name, start, end, finish));
        }
        return terms;
    }

    public static ThesisInfo thesis(Object thesesRaw) {
        if (thesesRaw == null) return new ThesisInfo("", "", "", "");
        JSONObject tObj = null;
        if (thesesRaw instanceof JSONArray) {
            JSONArray arr = (JSONArray) thesesRaw;
            if (arr.length() > 0) tObj = arr.optJSONObject(0);
        } else if (thesesRaw instanceof JSONObject) {
            JSONObject obj = (JSONObject) thesesRaw;
            JSONArray arr = obj.optJSONArray("theses");
            if (arr != null && arr.length() > 0) tObj = arr.optJSONObject(0);
            else tObj = obj;
        }
        if (tObj == null) return new ThesisInfo("", "", "", "");
        JSONObject titleObj = tObj.optJSONObject("title");
        String title = titleObj != null ? language(titleObj) : optionalText(tObj, "title");
        JSONObject statusObj = tObj.optJSONObject("status");
        String status = statusObj != null ? language(statusObj) : optionalText(tObj, "status");

        List<String> supervisors = new ArrayList<>();
        JSONArray supArr = tObj.optJSONArray("supervisors");
        if (supArr != null) {
            for (int i = 0; i < supArr.length(); i++) {
                JSONObject sup = supArr.optJSONObject(i);
                if (sup == null) continue;
                String first = optionalText(sup, "first_name").trim();
                String last = optionalText(sup, "last_name").trim();
                String name = (first + " " + last).trim();
                if (!name.isEmpty()) supervisors.add(name);
            }
        }

        List<String> reviewers = new ArrayList<>();
        JSONArray revArr = tObj.optJSONArray("reviewers");
        if (revArr != null) {
            for (int i = 0; i < revArr.length(); i++) {
                JSONObject rev = revArr.optJSONObject(i);
                if (rev == null) continue;
                String first = optionalText(rev, "first_name").trim();
                String last = optionalText(rev, "last_name").trim();
                String name = (first + " " + last).trim();
                if (!name.isEmpty()) reviewers.add(name);
            }
        }

        return new ThesisInfo(title, status, String.join(", ", supervisors), String.join(", ", reviewers));
    }

    public static List<CourseTestRecord> courseTests(Object testsRaw, Map<String, String> courseNames) {
        if (testsRaw == null) return Collections.emptyList();
        List<CourseTestRecord> results = new ArrayList<>();
        JSONArray list = null;
        if (testsRaw instanceof JSONArray) {
            list = (JSONArray) testsRaw;
        } else if (testsRaw instanceof JSONObject) {
            list = ((JSONObject) testsRaw).optJSONArray("tests");
        }
        if (list == null) return results;

        for (int i = 0; i < list.length(); i++) {
            JSONObject test = list.optJSONObject(i);
            if (test == null) continue;
            String id = String.valueOf(test.opt("id"));
            String courseId = optionalText(test, "course_id");
            String courseName = courseNames != null ? courseNames.getOrDefault(courseId, courseId) : courseId;
            JSONObject nameObj = test.optJSONObject("name");
            String testName = nameObj != null ? language(nameObj) : optionalText(test, "name");
            if (testName.isEmpty()) testName = "Sprawdzian";
            double points = test.optDouble("points", 0.0);
            double maxPoints = test.optDouble("max_points", 0.0);
            String grade = optionalText(test, "grade");
            String date = optionalText(test, "date");
            results.add(new CourseTestRecord(id, courseId, courseName, testName, points, maxPoints, grade, date));
        }
        return results;
    }

    public static List<NewsArticle> newsArticles(Object raw) {
        if (raw == null) return Collections.emptyList();
        List<NewsArticle> results = new ArrayList<>();
        JSONArray items = null;
        if (raw instanceof JSONObject) {
            JSONObject obj = (JSONObject) raw;
            items = obj.optJSONArray("items");
            if (items == null) items = obj.optJSONArray("articles");
        } else if (raw instanceof JSONArray) {
            items = (JSONArray) raw;
        }
        if (items == null) return results;

        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            JSONObject a = it.optJSONObject("article");
            if (a == null) a = it;

            String id = String.valueOf(a.opt("id"));
            JSONObject titleObj = a.optJSONObject("title");
            String title = titleObj != null ? language(titleObj) : optionalText(a, "title");
            if (title.isEmpty()) continue;

            String pubDate = optionalText(a, "publication_date");
            if (pubDate.length() > 10 && (pubDate.charAt(10) == ' ' || pubDate.charAt(10) == 'T')) {
                pubDate = pubDate.substring(0, 10);
            }
            JSONObject headlineObj = a.optJSONObject("headline_html");
            if (headlineObj == null) headlineObj = a.optJSONObject("headline");
            String headline = headlineObj != null ? language(headlineObj) : optionalText(a, "headline_html");
            if (headline.isEmpty()) headline = optionalText(a, "headline");
            String cleanHeadline = headline.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();

            String authorName = "";
            JSONObject authorObj = a.optJSONObject("author");
            if (authorObj != null) {
                String first = optionalText(authorObj, "first_name");
                String last = optionalText(authorObj, "last_name");
                authorName = (first + " " + last).trim();
            }
            if (authorName.isEmpty()) authorName = optionalText(a, "author_name");
            if (authorName.isEmpty()) authorName = optionalText(a, "author");

            String url = optionalText(a, "url");
            results.add(new NewsArticle(id, title, pubDate, authorName, cleanHeadline, url));
        }
        return results;
    }
}
