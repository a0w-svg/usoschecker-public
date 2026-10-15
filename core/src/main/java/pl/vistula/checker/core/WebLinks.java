package pl.vistula.checker.core;

import java.net.URI;
import java.net.URISyntaxException;

/** Limits server-supplied browser links to ordinary web addresses. */
public final class WebLinks {
    private WebLinks() {}

    public static boolean isWebUrl(String address) {
        if (address == null) return false;
        try {
            URI uri = new URI(address);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && !uri.getHost().isEmpty() && uri.getRawUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
