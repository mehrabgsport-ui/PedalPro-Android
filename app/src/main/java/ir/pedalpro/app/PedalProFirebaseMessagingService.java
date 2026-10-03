package ir.pedalpro.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class PedalProFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL = "pedalpro_push";

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        FirebaseConfigManager.registerToken(getApplicationContext(), token);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Map<String,String> data = message.getData();
        String title = data.get("title");
        String body = data.get("body");
        String actionUrl = data.get("action_url");
        if ((title == null || title.isEmpty()) && message.getNotification() != null)
            title = message.getNotification().getTitle();
        if ((body == null || body.isEmpty()) && message.getNotification() != null)
            body = message.getNotification().getBody();
        if (title == null || title.isEmpty()) title = "PedalPro";
        if (body == null) body = "";
        post(title, body, actionUrl);
    }

    private void post(String title, String body, String actionUrl) {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "اعلان‌های فوری PedalPro", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("پیام‌ها، اعلان‌های مدیر و رویدادهای فوری PedalPro");
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(this, MainActivity.class);
        if (actionUrl != null && !actionUrl.isEmpty()) {
            String url = actionUrl.startsWith("/") ? "https://pedalpro.ir" + actionUrl : actionUrl;
            open.setData(Uri.parse(url));
        }
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, (int)(System.currentTimeMillis() & 0x7fffffff), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify((int)(System.currentTimeMillis() & 0x7fffffff), n);
    }
}
