package ir.pedalpro.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

final class StoreUpdateManager {
    private StoreUpdateManager() {}

    static void openUpdate(Activity activity) {
        openUpdate(activity, null);
    }

    static void openUpdate(Activity activity, String directApkUrl) {
        String id = activity.getPackageName();
        if ("bazaar".equals(BuildConfig.STORE_CHANNEL)) {
            if (open(activity, "bazaar://details?id=" + id)) return;
            if (open(activity, "https://cafebazaar.ir/app/" + id)) return;
        } else if ("myket".equals(BuildConfig.STORE_CHANNEL)) {
            if (open(activity, "myket://details?id=" + id)) return;
            if (open(activity, "https://myket.ir/app/" + id)) return;
        } else if ("googlePlay".equals(BuildConfig.STORE_CHANNEL)) {
            if (open(activity, "market://details?id=" + id)) return;
            if (open(activity, "https://play.google.com/store/apps/details?id=" + id)) return;
        }
        if (directApkUrl != null && !directApkUrl.trim().isEmpty()) {
            if (open(activity, directApkUrl.trim())) return;
        }
        Toast.makeText(activity, "مسیر بروزرسانی این نسخه در دسترس نیست.", Toast.LENGTH_LONG).show();
    }

    private static boolean open(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
