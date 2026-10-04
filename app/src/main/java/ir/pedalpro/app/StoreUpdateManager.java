package ir.pedalpro.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

final class StoreUpdateManager {
    private StoreUpdateManager() {}

    static void openUpdate(Activity activity) {
        String id = activity.getPackageName();
        if ("bazaar".equals(BuildConfig.STORE_CHANNEL)) {
            if (open(activity, "bazaar://details?id=" + id)) return;
            open(activity, "https://cafebazaar.ir/app/" + id);
            return;
        }
        if ("googlePlay".equals(BuildConfig.STORE_CHANNEL)) {
            if (open(activity, "market://details?id=" + id)) return;
            open(activity, "https://play.google.com/store/apps/details?id=" + id);
            return;
        }
        Toast.makeText(activity, "مسیر بروزرسانی این نسخه تعریف نشده است.", Toast.LENGTH_LONG).show();
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
