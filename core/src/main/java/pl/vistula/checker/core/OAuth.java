package pl.vistula.checker.core;

import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** OAuth 1.0a, RFC 5849. Parameters are transmitted in the query, never logged. */
public final class OAuth {
    private OAuth() {}
    public static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
                .replace("*", "%2A").replace("%7E", "~");
        } catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
    public static String query(Map<String, String> params) {
        List<String> pairs = new ArrayList<>();
        params.forEach((k, v) -> pairs.add(encode(k) + "=" + encode(v)));
        Collections.sort(pairs);
        return String.join("&", pairs);
    }
    public static String signature(String method, String url, Map<String, String> params,
                                   String consumerSecret, String tokenSecret) throws Exception {
        TreeMap<String, String> unsigned = new TreeMap<>(params);
        unsigned.remove("oauth_signature");
        String base = method.toUpperCase(Locale.ROOT) + "&" + encode(url) + "&" + encode(query(unsigned));
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((encode(consumerSecret) + "&" + encode(tokenSecret))
            .getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
    }
    public static Map<String, String> sign(String url, Map<String, String> params, String key,
                                         String secret, String token, String tokenSecret) throws Exception {
        return sign("GET", url, params, key, secret, token, tokenSecret);
    }
    public static Map<String, String> sign(String method, String url, Map<String, String> params, String key,
                                         String secret, String token, String tokenSecret) throws Exception {
        Map<String, String> signed = new TreeMap<>(params);
        signed.put("oauth_consumer_key", key);
        signed.put("oauth_signature_method", "HMAC-SHA1");
        signed.put("oauth_timestamp", Long.toString(System.currentTimeMillis() / 1000));
        signed.put("oauth_nonce", UUID.randomUUID().toString());
        signed.put("oauth_version", "1.0");
        if (!token.isEmpty()) signed.put("oauth_token", token);
        signed.put("oauth_signature", signature(method, url, signed, secret, tokenSecret));
        return signed;
    }
    public static Map<String, String> form(String body) {
        Map<String, String> result = new HashMap<>();
        for (String part : body.split("&")) {
            String[] p = part.split("=", 2);
            if (p.length == 2) {
                try { result.put(URLDecoder.decode(p[0], "UTF-8"), URLDecoder.decode(p[1], "UTF-8")); }
                catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
            }
        }
        if (!result.containsKey("oauth_token") || !result.containsKey("oauth_token_secret")
            || result.get("oauth_token").isEmpty() || result.get("oauth_token_secret").isEmpty())
            throw new IllegalArgumentException("Niepełna odpowiedź autoryzacji USOS.");
        return result;
    }
}
