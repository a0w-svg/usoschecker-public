package pl.vistula.checker.core;

import java.util.Locale;
import org.json.JSONObject;

/** Language selection for API dictionaries; scoped parsing never changes the process locale. */
public final class ApiLanguage {
    private ApiLanguage() {}
    public static final String[] SUPPORTED = {"pl", "en", "uk"};
    private static final ThreadLocal<String> PARSING = new ThreadLocal<>();
    public interface Read<T> { T get() throws Exception; }
    public static <T> T in(String language, Read<T> read) throws Exception {
        String previous = PARSING.get();
        PARSING.set(normalize(language));
        try { return read.get(); }
        finally { if (previous == null) PARSING.remove(); else PARSING.set(previous); }
    }
    public static String label(String polish, String english, String ukrainian) {
        return "uk".equals(current()) ? ukrainian : "en".equals(current()) ? english : polish;
    }
    public static String current() { return PARSING.get() == null ? "pl" : PARSING.get(); }
    public static String normalize(String tag) {
        String language = Locale.forLanguageTag(tag == null ? "" : tag.replace('_', '-')).getLanguage();
        return "en".equals(language) || "uk".equals(language) ? language : "pl";
    }
    public static String text(Object value) { return text(value, current()); }
    public static String text(Object value, String tag) {
        if (value instanceof String) return (String) value;
        if (!(value instanceof JSONObject)) return "";
        JSONObject dictionary = (JSONObject) value;
        String requested = normalize(tag);
        // Ukrainian is often unavailable in USOS; prefer English before Polish in that case.
        String[] order = "pl".equals(requested) ? new String[]{"pl", "en", "uk"}
            : new String[]{requested, "en", "pl"};
        for (String key : order) {
            Object candidate = dictionary.opt(key);
            if (candidate instanceof String && !((String) candidate).trim().isEmpty()) return (String) candidate;
        }
        for (String key : UsosParser.sortedKeys(dictionary)) {
            Object candidate = dictionary.opt(key);
            if (candidate instanceof String && !((String) candidate).trim().isEmpty()) return (String) candidate;
        }
        return "";
    }
    /** Keep only text translations, without retaining unrelated fields from an API object. */
    public static Object safe(Object value) throws org.json.JSONException {
        if (value instanceof String) return value;
        JSONObject safe = new JSONObject();
        if (value instanceof JSONObject) for (String key : UsosParser.sortedKeys((JSONObject) value)) {
            Object text = ((JSONObject) value).opt(key);
            if (key.matches("[a-z]{2}(?:-[A-Za-z]{2})?") && text instanceof String) safe.put(key, text);
        }
        return safe;
    }
}
