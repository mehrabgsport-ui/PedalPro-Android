package ir.pedalpro.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AppUpdateManager {
    private static final String CHECK_URL = "https://pedalpro.ir/api.php?action=app_latest_release";
    private static final String PREFS = "pedalpro_updates";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static volatile boolean dialogShowing = false;

    private AppUpdateManager() {}

    public static void check(Activity a, boolean manual) {
        if (!CHECKING.compareAndSet(false, true)) return;
        IO.execute(() -> {
            Release r = null; String error = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(CHECK_URL).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(12000);
                c.setRequestProperty("Accept", "application/json");
                int code = c.getResponseCode();
                String body = NetUtil.read(code >= 400 ? c.getErrorStream() : c.getInputStream());
                c.disconnect();
                JSONObject root = new JSONObject(body);
                JSONObject j = root.optJSONObject("release");
                if (code >= 200 && code < 300 && j != null) r = Release.from(j);
                else if (code >= 400) error = root.optString("error", "خطا در بررسی نسخه");
            } catch (Exception e) { error = "اتصال برای بررسی نسخه برقرار نشد"; }
            Release rr = r; String ee = error;
            a.runOnUiThread(() -> {
                CHECKING.set(false);
                if (a.isFinishing()) return;
                if (rr != null && rr.versionCode > BuildConfig.VERSION_CODE) showUpdate(a, rr);
                else if (manual) Toast.makeText(a, ee != null ? ee : "آخرین نسخه نصب است: " + BuildConfig.VERSION_NAME, Toast.LENGTH_LONG).show();
            });
        });
    }

    private static void showUpdate(Activity a, Release r) {
        if (dialogShowing || a.isFinishing()) return;
        dialogShowing = true;
        String msg = "نسخه جدید " + r.versionName + " منتشر شده است.\n\n" +
                (r.notes == null || r.notes.isEmpty() ? "برای دریافت و نصب بروزرسانی روی دکمه زیر بزنید." : r.notes);
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("نسخه جدید PedalPro")
                .setMessage(msg)
                .setPositiveButton("دانلود و نصب", null)
                .setNegativeButton(r.mandatory ? null : "بعداً", (x,w)-> dialogShowing=false)
                .setCancelable(!r.mandatory)
                .create();
        d.setOnDismissListener(x -> dialogShowing=false);
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            d.dismiss();
            download(a, r);
        }));
        d.show();
    }

    private static void download(Activity a, Release r) {
        ProgressDialog pd = new ProgressDialog(a);
        pd.setTitle("دانلود بروزرسانی PedalPro");
        pd.setMessage("در حال دریافت نسخه " + r.versionName + " ...");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setIndeterminate(false); pd.setMax(100); pd.setCancelable(false); pd.show();

        IO.execute(() -> {
            try {
                File dir = new File(a.getCacheDir(), "updates"); if (!dir.exists()) dir.mkdirs();
                File apk = new File(dir, "PedalPro-" + r.versionCode + ".apk");
                HttpURLConnection c = (HttpURLConnection) new URL(r.apkUrl).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(30000);
                int total = c.getContentLength();
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = new BufferedInputStream(c.getInputStream()); FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buf = new byte[32768]; int n; long done = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n); md.update(buf, 0, n); done += n;
                        if (total > 0) {
                            int pct = (int)Math.min(100, done * 100 / total);
                            a.runOnUiThread(() -> pd.setProgress(pct));
                        }
                    }
                } finally { c.disconnect(); }
                String hash = hex(md.digest());
                if (r.sha256 != null && !r.sha256.isEmpty() && !hash.equalsIgnoreCase(r.sha256))
                    throw new Exception("امضای فایل دانلودشده با سرور یکسان نیست.");
                PackageInfo pi = a.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
                if (pi == null || !a.getPackageName().equals(pi.packageName)) throw new Exception("فایل بروزرسانی متعلق به PedalPro نیست.");
                long vc = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
                if (vc < r.versionCode) throw new Exception("کد نسخه APK معتبر نیست.");
                a.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().putString("pending_apk", apk.getAbsolutePath()).apply();
                a.runOnUiThread(() -> { pd.dismiss(); installOrRequestPermission(a, apk); });
            } catch (Exception e) {
                String m = e.getMessage() == null ? "دانلود بروزرسانی ناموفق بود." : e.getMessage();
                a.runOnUiThread(() -> { pd.dismiss(); Toast.makeText(a, m, Toast.LENGTH_LONG).show(); });
            }
        });
    }

    private static void installOrRequestPermission(Activity a, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "برای نصب مستقیم، اجازه «نصب برنامه‌های ناشناس» را برای PedalPro فعال کنید.", Toast.LENGTH_LONG).show();
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()));
            a.startActivity(s); return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".provider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, "باز کردن نصب‌کننده Android ممکن نشد.", Toast.LENGTH_LONG).show();
        }
    }

    public static void resumePendingInstall(Activity a) {
        String p = a.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).getString("pending_apk", "");
        if (p.isEmpty()) return; File f = new File(p);
        if (!f.isFile()) { a.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().remove("pending_apk").apply(); return; }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || a.getPackageManager().canRequestPackageInstalls()) installOrRequestPermission(a, f);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format(Locale.US, "%02x", x)); return sb.toString();
    }

    private static class Release {
        int versionCode; String versionName, apkUrl, sha256, notes; boolean mandatory;
        static Release from(JSONObject j) {
            Release r = new Release(); r.versionCode = j.optInt("version_code",0);
            r.versionName=j.optString("version_name",""); r.apkUrl=j.optString("apk_url","");
            r.sha256=j.optString("sha256",""); r.notes=j.optString("release_notes","");
            r.mandatory=j.optBoolean("is_mandatory",false); return r;
        }
    }
}
