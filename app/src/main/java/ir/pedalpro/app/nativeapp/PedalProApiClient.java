package ir.pedalpro.app.nativeapp;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal native API transport.
 * The native app talks to the PedalPro backend directly and does not render website HTML.
 */
public final class PedalProApiClient {
    public static final String BASE_URL = "https://pedalpro.ir/api.php";

    public JSONObject get(String action, String bearerToken) throws Exception {
        URL url = new URL(BASE_URL + "?action=" + action);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        if (bearerToken != null && !bearerToken.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        return readJson(c);
    }

    public JSONObject post(String action, JSONObject body, String bearerToken) throws Exception {
        URL url = new URL(BASE_URL + "?action=" + action);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setDoOutput(true);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (bearerToken != null && !bearerToken.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) {
            os.write(data);
        }
        return readJson(c);
    }

    private JSONObject readJson(HttpURLConnection c) throws Exception {
        int status = c.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line);
        }
        JSONObject json = new JSONObject(out.toString().isEmpty() ? "{}" : out.toString());
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(json.optString("error", "HTTP " + status));
        }
        return json;
    }
}
