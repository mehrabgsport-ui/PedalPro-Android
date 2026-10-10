package ir.pedalpro.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DiagnosticLogger {
    private static final Object LOCK = new Object();
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final String DIR = "diagnostics";
    private static final String FILE = "pedalpro.log";
    private static final String OLD_FILE = "pedalpro.old.log";

    private DiagnosticLogger() {}

    private static File dir(Context context) {
        File d = new File(context.getFilesDir(), DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File file(Context context) {
        return new File(dir(context), FILE);
    }

    public static void log(Context context, String category, String message) {
        log(context, category, message, null);
    }

    public static void log(Context context, String category, String message, Throwable error) {
        if (context == null) return;
        synchronized (LOCK) {
            try {
                File f = file(context);
                if (f.exists() && f.length() >= MAX_BYTES) {
                    File old = new File(dir(context), OLD_FILE);
                    if (old.exists()) old.delete();
                    f.renameTo(old);
                }

                JSONObject row = new JSONObject();
                row.put("time", System.currentTimeMillis());
                row.put("iso", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()));
                row.put("category", category == null ? "app" : category);
                row.put("message", message == null ? "" : message);
                row.put("thread", Thread.currentThread().getName());
                if (error != null) {
                    row.put("error", error.getClass().getName());
                    row.put("error_message", String.valueOf(error.getMessage()));
                    StackTraceElement[] stack = error.getStackTrace();
                    StringBuilder sb = new StringBuilder();
                    int max = Math.min(stack == null ? 0 : stack.length, 24);
                    for (int i = 0; i < max; i++) sb.append(stack[i]).append("\n");
                    row.put("stack", sb.toString());
                }

                try (FileWriter out = new FileWriter(f, true)) {
                    out.write(row.toString());
                    out.write("\n");
                }
            } catch (Throwable ignored) { }
        }
    }

    public static String dump(Context context) {
        if (context == null) return "";
        synchronized (LOCK) {
            StringBuilder sb = new StringBuilder();
            sb.append("PedalPro Diagnostic\n");
            sb.append("version=").append(BuildConfig.VERSION_NAME)
                    .append(" code=").append(BuildConfig.VERSION_CODE).append("\n");
            sb.append("sdk=").append(Build.VERSION.SDK_INT)
                    .append(" device=").append(Build.MANUFACTURER).append(' ')
                    .append(Build.MODEL).append("\n");

            try {
                SharedPreferences p = context.getSharedPreferences(TrackingService.PREFS, Context.MODE_PRIVATE);
                int rideId = p.getInt("ride_id", 0);
                sb.append("tracking.active=").append(p.getBoolean("active", false))
                        .append(" paused=").append(p.getBoolean("paused", false))
                        .append(" ride_id=").append(rideId)
                        .append(" distance_m=").append(p.getFloat("distance_m", 0f))
                        .append(" final_distance_m=").append(p.getFloat("final_distance_m", 0f))
                        .append(" final_max_speed_kmh=").append(p.getFloat("final_max_speed_kmh", 0f))
                        .append(" speed_mps=").append(p.getFloat("last_speed_mps", 0f))
                        .append(" elapsed_ms=").append(p.getLong("elapsed_ms", 0L))
                        .append(" distance_source=").append(p.getString("distance_source", "gps"))
                        .append("\n");
                if (rideId > 0) {
                    String summary = RidePostProcessor.summaryJson(context, rideId);
                    if (summary != null && !summary.isEmpty()) {
                        sb.append("postprocess=").append(summary).append("\n");
                    }
                }
                sb.append("bike_sensor.connected=").append(BikeSensorManager.get().isConnected())
                        .append(" speed_mps=").append(BikeSensorManager.get().getSpeedMps())
                        .append(" ride_distance_m=").append(BikeSensorManager.get().getRideDistanceMeters())
                        .append("\n");
            } catch (Throwable ignored) { }

            appendFile(sb, new File(dir(context), OLD_FILE));
            appendFile(sb, file(context));
            return sb.toString();
        }
    }

    private static void appendFile(StringBuilder sb, File f) {
        if (f == null || !f.exists()) return;
        sb.append("\n--- ").append(f.getName()).append(" ---\n");
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } catch (Throwable e) {
            sb.append("read_error=").append(e).append('\n');
        }
    }

    public static void clear(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            try { file(context).delete(); } catch (Throwable ignored) { }
            try { new File(dir(context), OLD_FILE).delete(); } catch (Throwable ignored) { }
        }
    }
}
