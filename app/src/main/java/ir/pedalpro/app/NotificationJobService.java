package ir.pedalpro.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationJobService extends JobService {
    public static final int JOB_ID = 42120;
    private static final String CHANNEL = "pedalpro_member_notifications";
    private static final String PREFS = "pedalpro_member_notifications";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    @Override public boolean onStartJob(JobParameters params) {
        IO.execute(() -> { fetchAndNotify(getApplicationContext()); jobFinished(params, false); });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }

    public static void fetchNow(Context context) { IO.execute(() -> fetchAndNotify(context)); }

    private static void fetchAndNotify(Context context) {
        try {
            String cookie = CookieManager.getInstance().getCookie("https://pedalpro.ir/");
            if (cookie == null || cookie.isEmpty()) return;
            SharedPreferences sp = context.getSharedPreferences(PREFS, MODE_PRIVATE);
            long after = sp.getLong("last_id", 0L);
            HttpURLConnection c = (HttpURLConnection) new URL("https://pedalpro.ir/api.php?action=app_notifications&after=" + after + "&limit=50").openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(12000);
            c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("Cookie", cookie);
            int code = c.getResponseCode(); String body = NetUtil.read(code >= 400 ? c.getErrorStream() : c.getInputStream()); c.disconnect();
            if (code < 200 || code >= 300) return;
            JSONObject root = new JSONObject(body); JSONArray items = root.optJSONArray("items"); if (items == null) return;
            ensureChannel(context);
            long max = after;
            for (int i=0;i<items.length();i++) {
                JSONObject n = items.optJSONObject(i); if (n == null) continue;
                long id = n.optLong("id",0); max = Math.max(max,id);
                post(context, n, (int)(id % Integer.MAX_VALUE));
            }
            if (max > after) sp.edit().putLong("last_id", max).apply();
        } catch (Exception ignored) { }
    }

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "اعلان‌های PedalPro", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("اعلان‌های مدیر گروه، برنامه‌ها و بروزرسانی‌های PedalPro");
            c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private static void post(Context c, JSONObject n, int id) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        String url = n.optString("action_url", "");
        Intent open = new Intent(c, MainActivity.class);
        if (!url.isEmpty()) {
            if (url.startsWith("/")) url = "https://pedalpro.ir" + url;
            open.setData(Uri.parse(url));
        }
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        Notification no = b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(n.optString("title","PedalPro"))
                .setContentText(n.optString("body",""))
                .setStyle(new Notification.BigTextStyle().bigText(n.optString("body","")))
                .setAutoCancel(true).setContentIntent(pi).build();
        c.getSystemService(NotificationManager.class).notify(id, no);
    }
}
