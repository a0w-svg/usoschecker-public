package pl.vistula.checker.core;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Read models for the documented cards and administration-document endpoints. */
public final class StudentServices {
    private StudentServices() {}
    public static String text(JSONObject object, String key) {
        if (object == null || object.isNull(key)) return "";
        Object value = object.opt(key);
        return value instanceof String || value instanceof Number ? String.valueOf(value) : "";
    }
    public static String localized(Object value) {
        return ApiLanguage.text(value);
    }
    public static String localized(Object value, String language) {
        return ApiLanguage.text(value, language);
    }
    public static LocalDate date(String value) {
        try { return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value); }
        catch (DateTimeParseException e) { return null; }
    }
    public static boolean hasScope(String scopes, String scope) {
        if (scopes == null) return false;
        for (String value : scopes.split("\\|")) if (scope.equals(value.trim())) return true;
        return false;
    }
    public static String normalizeScopes(String scopes) {
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        if (scopes != null) for (String value : scopes.split("\\|")) {
            if (!value.trim().isEmpty()) values.add(value.trim());
        }
        return String.join("|", values);
    }
    public static final class Card {
        public final String id, type, studentNumber, barcode, issued, expires;
        public Card(JSONObject value) {
            id = text(value, "id"); type = text(value, "type"); studentNumber = text(value, "student_number");
            barcode = text(value, "barcode_number"); issued = text(value, "date_of_issue"); expires = text(value, "expiration_date");
        }
        public boolean expired(LocalDate today) { LocalDate until = date(expires); return until != null && until.isBefore(today); }
        public boolean expiresSoon(LocalDate today) {
            LocalDate until = date(expires);
            return until != null && !until.isBefore(today) && !until.isAfter(today.plusDays(30));
        }
        public JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("type", type).put("student_number", studentNumber)
                .put("barcode_number", barcode).put("date_of_issue", issued).put("expiration_date", expires);
        }
    }
    public static List<Card> cards(JSONArray rows) throws JSONException {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) cards.add(new Card(rows.getJSONObject(i)));
        return cards;
    }
    public static JSONArray safeCards(JSONArray rows) throws JSONException {
        JSONArray result = new JSONArray();
        for (Card card : cards(rows)) result.put(card.json());
        return result;
    }
    /** Cache only status information, never mobile activation tokens or passwords. */
    public static JSONObject safeMobileCard(JSONObject source) throws JSONException {
        JSONObject result = new JSONObject().put("why_cannot_order", ApiLanguage.safe(source.opt("why_cannot_order")))
            .put("why_cannot_revoke", ApiLanguage.safe(source.opt("why_cannot_revoke")))
            .put("orderEligibilityKnown", source.has("why_cannot_order"))
            .put("revokeEligibilityKnown", source.has("why_cannot_revoke"))
            .put("expected_expiration_date", text(source, "expected_expiration_date"));
        JSONObject latest = source.optJSONObject("latest_order");
        if (latest != null) result.put("latest_order", safeOrder(latest));
        JSONArray orders = source.optJSONArray("orders"), safe = new JSONArray();
        if (orders != null) for (int i = 0; i < orders.length(); i++) safe.put(safeOrder(orders.getJSONObject(i)));
        result.put("orders", safe);
        return result;
    }
    private static JSONObject safeOrder(JSONObject source) throws JSONException {
        JSONObject result = new JSONObject();
        for (String key : new String[]{"id", "order_date", "status", "student_number", "expiration_date"}) result.put(key, text(source, key));
        return result;
    }
    public static final class Document {
        public final String id, name, status, typeName, published, delivered, blobId;
        public final List<String> actions = new ArrayList<>();
        public Document(JSONObject source) { this(source, ApiLanguage.current()); }
        public Document(JSONObject source, String language) {
            id = text(source, "id"); name = localized(source.opt("name"), language); status = localized(source.opt("status"), language);
            typeName = localized(source.opt("type_name"), language); published = text(source, "published_date");
            delivered = text(source, "delivered_date"); blobId = text(source, "id_blobbox");
            JSONArray values = source.optJSONArray("actions");
            if (values != null) for (int i = 0; i < values.length(); i++) {
                Object value = values.opt(i); if (value instanceof String) actions.add((String) value);
            }
        }
        public boolean downloadable() { return actions.contains("GET_DOCUMENT") && !blobId.isEmpty(); }
        public boolean matches(String query) { return StudentSearch.matches(query, name, status, typeName, published, id); }
        public JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("status", status).put("type_name", typeName)
                .put("published_date", published).put("delivered_date", delivered).put("id_blobbox", blobId).put("actions", new JSONArray(actions));
        }
    }
    public static JSONArray safeDocuments(JSONArray source) throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject raw = source.getJSONObject(i);
            JSONObject safe = new Document(raw).json();
            for (String key : new String[]{"name", "status", "type_name"}) safe.put(key, ApiLanguage.safe(raw.opt(key)));
            result.put(safe);
        }
        return result;
    }
}
