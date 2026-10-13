package ir.pedalpro.app.nativeapp;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Native PedalPro API transport with persistent PHP session/remember cookies and CSRF.
 * This client never renders website HTML.
 */
public final class PedalProApiClient {
    public static final String BASE_URL = "https://pedalpro.ir/api.php";

    private final SessionStore session;

    public PedalProApiClient(Context context) {
        session = new SessionStore(context.getApplicationContext());
    }

    public JSONObject get(String action) throws Exception {
        return request("GET", action, null, true);
    }

    public JSONObject get(String action, java.util.Map<String, ?> params) throws Exception {
        StringBuilder q = new StringBuilder(action);
        if (params != null && !params.isEmpty()) {
            boolean first = true;
            for (java.util.Map.Entry<String, ?> e : params.entrySet()) {
                if (e.getValue() == null) continue;
                q.append(first ? "&" : "&")
                 .append(java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                 .append("=")
                 .append(java.net.URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
                first = false;
            }
        }
        return request("GET", q.toString(), null, true, true);
    }

    public JSONObject post(String action, JSONObject body) throws Exception {
        return request("POST", action, body == null ? new JSONObject() : body, true);
    }

    public JSONObject bootstrap() throws Exception {
        JSONObject data = get("bootstrap");
        String csrf = data.optString("csrf", "");
        if (!csrf.isBlank()) session.saveCsrf(csrf);
        JSONObject user = data.optJSONObject("user");
        session.saveUserJson(user == null ? "" : user.toString());
        return data;
    }

    public JSONObject login(String username, String password, boolean remember) throws Exception {
        // Obtain a fresh PHP session + CSRF before auth_login.
        bootstrap();
        JSONObject body = new JSONObject()
                .put("username", username == null ? "" : username.trim())
                .put("password", password == null ? "" : password)
                .put("remember", remember);
        JSONObject result = post("auth_login", body);
        bootstrap();
        return result;
    }

    public void logout() throws Exception {
        try { post("auth_logout", new JSONObject()); }
        finally { session.clear(); }
    }

    private JSONObject request(String method, String action, JSONObject body, boolean retry419) throws Exception {
        return request(method, action, body, retry419, false);
    }

    private JSONObject request(String method, String action, JSONObject body, boolean retry419, boolean actionContainsQuery) throws Exception {
        HttpURLConnection c = null;
        try {
            String query;
            if (actionContainsQuery) {
                int amp = action.indexOf('&');
                String name = amp >= 0 ? action.substring(0, amp) : action;
                String rest = amp >= 0 ? action.substring(amp) : "";
                query = "action=" + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8) + rest;
            } else {
                query = "action=" + java.net.URLEncoder.encode(action, StandardCharsets.UTF_8);
            }
            URL url = new URL(BASE_URL + "?" + query);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(18000);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Cache-Control", "no-cache");

            String cookie = session.cookie();
            if (!cookie.isBlank()) c.setRequestProperty("Cookie", cookie);

            if (!"GET".equals(method)) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                String csrf = session.csrf();
                if (!csrf.isBlank()) c.setRequestProperty("X-CSRF-Token", csrf);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
            }

            int status = c.getResponseCode();
            captureCookies(c);
            String raw = readBody(status >= 400 ? c.getErrorStream() : c.getInputStream());
            JSONObject json = new JSONObject(raw == null || raw.isBlank() ? "{}" : raw);

            String csrf = json.optString("csrf", "");
            if (!csrf.isBlank()) session.saveCsrf(csrf);

            if (status == 419 && retry419) {
                bootstrap();
                return request(method, action, body, false, actionContainsQuery);
            }
            if (status < 200 || status >= 300 || json.optBoolean("ok", true) == false) {
                throw new IllegalStateException(json.optString("error", "HTTP " + status));
            }
            return json;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void captureCookies(HttpURLConnection c) {
        Map<String, List<String>> headers = c.getHeaderFields();
        if (headers == null) return;
        StringBuilder merged = new StringBuilder();
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() == null || !"set-cookie".equalsIgnoreCase(e.getKey())) continue;
            for (String line : e.getValue()) {
                if (line == null || line.isBlank()) continue;
                String pair = line.split(";", 2)[0].trim();
                if (pair.isBlank()) continue;
                if (merged.length() > 0) merged.append("; ");
                merged.append(pair);
            }
        }
        if (merged.length() > 0) {
            String previous = session.cookie();
            session.saveCookie(mergeCookiePairs(previous, merged.toString()));
        }
    }

    private String mergeCookiePairs(String oldCookie, String incoming) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        addCookieString(map, oldCookie);
        addCookieString(map, incoming);
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (out.length() > 0) out.append("; ");
            out.append(e.getKey()).append("=").append(e.getValue());
        }
        return out.toString();
    }

    private void addCookieString(java.util.LinkedHashMap<String, String> map, String cookie) {
        if (cookie == null) return;
        for (String part : cookie.split(";")) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            map.put(p.substring(0, eq).trim(), p.substring(eq + 1).trim());
        }
    }

    private String readBody(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line);
        }
        return out.toString();
    }
}
