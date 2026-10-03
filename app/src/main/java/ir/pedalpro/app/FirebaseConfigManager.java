package ir.pedalpro.app;

import android.content.Context;
import android.provider.Settings;
import android.webkit.CookieManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FirebaseConfigManager {
    private static final String CONFIG_URL = "https://pedalpro.ir/api.php?action=app_firebase_public_config";
    private static final String REGISTER_URL = "https://pedalpro.ir/api.php?action=app_device_register";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean SYNCING = new AtomicBoolean(false);

    private FirebaseConfigManager() {}

    public static void sync(Context context) {
        if (!SYNCING.compareAndSet(false, true)) return;
        IO.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection)new URL(CONFIG_URL).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(12000);
                c.setRequestProperty("Accept","application/json");
                int code = c.getResponseCode();
                String body = NetUtil.read(code >= 400 ? c.getErrorStream() : c.getInputStream());
                c.disconnect();
                if (code < 200 || code >= 300) return;
                JSONObject root = new JSONObject(body);
                JSONObject cfg = root.optJSONObject("config");
                if (cfg == null || !cfg.optBoolean("enabled", false)) return;

                String projectId = cfg.optString("project_id","");
                String appId = cfg.optString("app_id","");
                String apiKey = cfg.optString("api_key","");
                String senderId = cfg.optString("sender_id","");
                if (projectId.isEmpty() || appId.isEmpty() || apiKey.isEmpty() || senderId.isEmpty()) return;

                FirebaseApp firebase;
                try { firebase = FirebaseApp.getInstance(); }
                catch (Exception ex) {
                    FirebaseOptions options = new FirebaseOptions.Builder()
                            .setProjectId(projectId)
                            .setApplicationId(appId)
                            .setApiKey(apiKey)
                            .setGcmSenderId(senderId)
                            .build();
                    firebase = FirebaseApp.initializeApp(context, options);
                }
                if (firebase == null) return;
                FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        registerToken(context, task.getResult());
                    }
                });
            } catch (Exception ignored) {
            } finally {
                SYNCING.set(false);
            }
        });
    }

    public static void registerToken(Context context, String token) {
        if (token == null || token.length() < 40) return;
        IO.execute(() -> {
            HttpURLConnection c = null;
            try {
                JSONObject p = new JSONObject();
                p.put("token", token);
                p.put("device_id", Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));
                p.put("app_version", BuildConfig.VERSION_NAME);
                c = (HttpURLConnection)new URL(REGISTER_URL).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(12000);
                c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                c.setRequestProperty("Accept","application/json");
                String cookie = CookieManager.getInstance().getCookie("https://pedalpro.ir/");
                if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
                byte[] data = p.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(data.length);
                try (OutputStream os = c.getOutputStream()) { os.write(data); }
                c.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }
}
