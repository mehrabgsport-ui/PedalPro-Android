package ir.pedalpro.app;

import android.content.Context;
import android.os.Build;
import org.json.JSONObject;
import java.util.concurrent.Executors;

public class DiagnosticClient {
    private static final String ENDPOINT = "https://pedalpro.ir/api.php?action=diagnostic_event";
    private static volatile String lastPage = "";
    private static volatile String lastAction = "";

    public static void page(String page) {
        lastPage = page == null ? "" : page;
        send("page");
    }

    public static void event(String action) {
        lastAction = action == null ? "" : action;
        send("event");
    }

    public static void error(String message) {
        lastAction = "ERROR: " + message;
        send("error");
    }

    private static void send(String type) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("type", type);
                body.put("page", lastPage);
                body.put("action", lastAction);
                body.put("device", Build.MODEL);
                body.put("android", Build.VERSION.RELEASE);
                // Network transport will be connected to Diagnostic API during integration.
            } catch (Exception ignored) { }
        });
    }
}
