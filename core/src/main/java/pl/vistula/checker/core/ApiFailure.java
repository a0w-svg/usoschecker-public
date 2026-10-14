package pl.vistula.checker.core;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.json.JSONObject;

/** Safe API diagnostics: never include raw server bodies or request credentials. */
public final class ApiFailure {
    public final String code;
    public final boolean authorization, retryable;
    public final long retryAt;

    public ApiFailure(int status, String body, String retryAfter, long now) {
        code = errorCode(body);
        authorization = code.equals("token_expired") || code.equals("token_rejected")
            || code.equals("invalid_token") || (status == 401 && code.equals("api_error"));
        retryable = !authorization && (status == 408 || status == 429 || status >= 500);
        retryAt = retryable ? retryTime(retryAfter, now) : 0;
    }

    private static String errorCode(String body) {
        String value = "api_error";
        try {
            JSONObject json = new JSONObject(body);
            value = json.optString("code", json.optString("oauth_problem", value));
        } catch (Exception ignored) {
            for (String part : body.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2 && (pair[0].equals("oauth_problem") || pair[0].equals("code"))) {
                    try { value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()); }
                    catch (Exception malformed) { value = "api_error"; }
                    break;
                }
            }
        }
        return value.matches("[a-zA-Z0-9_]{1,80}") ? value : "api_error";
    }

    private static long retryTime(String header, long now) {
        if (header == null) return 0;
        try {
            long seconds = Long.parseLong(header.trim());
            return seconds < 0 ? 0 : Math.addExact(now, Math.multiplyExact(seconds, 1000));
        } catch (Exception ignored) {
            try {
                long date = ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
                return Math.max(now, date);
            } catch (Exception malformed) { return 0; }
        }
    }
}
