package pl.vistula.checker.core;

import java.net.URI;

/** Links to Vistula's Platon only; credentials and session parameters are never task links. */
public final class PlatonLinks {
    public static final String HOME = "https://platon.vistula.edu.pl/";
    private PlatonLinks() {}
    public static String normalize(String address) {
        if (address == null || address.trim().isEmpty()) return "";
        String value = address.trim();
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"platon.vistula.edu.pl".equalsIgnoreCase(uri.getHost())
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || value.length() > 2048 || uri.getRawPath().contains("\\")) throw new IllegalArgumentException();
            String query = uri.getRawQuery();
            String parameters = String.valueOf(uri.getQuery()) + "&" + String.valueOf(uri.getFragment());
            if (parameters.toLowerCase(java.util.Locale.ROOT).matches("(?s).*(?:token|password|secret|ticket|session|code|sesskey|sid|sessionid|authorization)=.*"))
                throw new IllegalArgumentException();
            return "https://platon.vistula.edu.pl" + (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (query == null ? "" : "?" + query) + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
        } catch (Exception e) {
            throw new IllegalArgumentException("Wklej link HTTPS do zadania na platon.vistula.edu.pl, bez danych logowania.");
        }
    }
    public static boolean isTaskLink(String address) {
        try { return !normalize(address).isEmpty(); }
        catch (IllegalArgumentException e) { return false; }
    }
}
