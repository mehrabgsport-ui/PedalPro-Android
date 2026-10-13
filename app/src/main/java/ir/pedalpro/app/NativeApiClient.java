package ir.pedalpro.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class NativeApiClient {
    private static final String API = "https://pedalpro.ir/api.php";
    private final NativeSessionStore session;

    public NativeApiClient(Context context) {
        session = new NativeSessionStore(context);
    }

    public JSONObject bootstrap() throws Exception {
        JSONObject d = request("bootstrap", "GET", null);
        session.setCsrf(d.optString("csrf", session.csrf()));
        return d;
    }

    public JSONObject login(String username, String password, boolean remember) throws Exception {
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);
        body.put("remember", remember);
        JSONObject d = request("auth_login", "POST", body);
        return d;
    }

    public JSONObject logout() throws Exception {
        JSONObject d = request("auth_logout", "POST", new JSONObject());
        session.clear();
        return d;
    }

    public JSONObject post(String action, JSONObject body) throws Exception {
        return request(action, "POST", body == null ? new JSONObject() : body);
    }

    public JSONObject get(String action) throws Exception {
        return request(action, "GET", null);
    }

    private JSONObject request(String action, String method, JSONObject body) throws Exception {
        URL url = new URL(API + "?action=" + java.net.URLEncoder.encode(action, StandardCharsets.UTF_8));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(18000);
        c.setUseCaches(false);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "PedalProNative/2.0 Android");
        String cookie = session.cookie();
        if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        String csrf = session.csrf();
        if (!csrf.isEmpty()) c.setRequestProperty("X-CSRF-Token", csrf);

        if ("POST".equals(method)) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] data = (body == null ? "{}" : body.toString()).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(data); }
        }

        int code = c.getResponseCode();
        String setCookie = c.getHeaderField("Set-Cookie");
        if (setCookie != null) session.setCookie(setCookie);
        InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
        }
        JSONObject json;
        try { json = new JSONObject(sb.length() == 0 ? "{}" : sb.toString()); }
        catch (Exception e) { throw new Exception("پاسخ نامعتبر از سرور (" + code + ")"); }
        if (json.has("csrf")) session.setCsrf(json.optString("csrf", session.csrf()));
        if (code < 200 || code >= 400 || json.optBoolean("ok", true) == false) {
            throw new Exception(json.optString("error", "خطای ارتباط با سرور (" + code + ")"));
        }
        return json;
    }
}
