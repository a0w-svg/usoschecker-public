package pl.vistula.checker.core;

import java.text.Normalizer;
import java.util.Locale;

/** Word search accepts accents or plain keyboard equivalents. */
public final class StudentSearch {
    private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}+");
    private StudentSearch() {}
    private static String normalize(String text) {
        return MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("").replace('ł', 'l');
    }
    public static boolean matches(String query, String... values) {
        String text = normalize(String.join(" ", values));
        for (String word : normalize(query).trim().split("\\s+")) if (!text.contains(word)) return false;
        return true;
    }
}
