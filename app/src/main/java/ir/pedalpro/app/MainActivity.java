package ir.pedalpro.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.view.WindowManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME = "https://pedalpro.ir/";
    private static final int REQ_LOCATION = 1101;
    private static final int REQ_CAMERA = 1102;
    private static final int REQ_FILE = 1103;
    private static final int REQ_NOTIFICATIONS = 1104;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private PermissionRequest pendingWebPermission;
    private String pendingTrackingJson;
    private volatile boolean updateCheckRunning = false;
    private long lastUpdateCheckMs = 0L;
    private android.app.AlertDialog forcedUpdateDialog;
    private boolean backDispatching = false;

    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra(TrackingService.EXTRA_PAYLOAD);
            if (payload == null || webView == null) return;
            String fn = TrackingService.ACTION_UPDATE.equals(intent.getAction())
                    ? "PedalProNativeLocation" : "PedalProNativeStatus";
            String script = "window." + fn + " && window." + fn + "(" + JSONObject.quote(payload) + ");";
            webView.post(() -> webView.evaluateJavascript(script, null));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        setupWebView();
        registerTrackingReceiver();
        requestNotificationIfNeeded();
        scheduleNotificationJob();
        FirebaseConfigManager.sync(getApplicationContext());
        String initial = resolveInitialUrl(getIntent());
        if (savedInstanceState == null) webView.loadUrl(initial);
        else webView.restoreState(savedInstanceState);
        webView.postDelayed(() -> checkReleasePolicy(false), 1400L);
    }

    private String resolveInitialUrl(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data != null) {
            String host = data.getHost() == null ? "" : data.getHost().toLowerCase(Locale.ROOT);
            if ("pedalpro.ir".equals(host) || "www.pedalpro.ir".equals(host)) return data.toString();
        }
        return HOME;
    }

    private void registerTrackingReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TrackingService.ACTION_UPDATE);
        filter.addAction(TrackingService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(trackingReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(trackingReceiver, filter);
    }

    private void scheduleNotificationJob() {
        try {
            JobScheduler js = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            JobInfo info = new JobInfo.Builder(NotificationJobService.JOB_ID,
                    new ComponentName(this, NotificationJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build();
            js.schedule(info);
            NotificationJobService.fetchNow(getApplicationContext());
        } catch (Exception ignored) { }
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " PedalProAndroid/1.4");

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new NativeBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUri(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUri(Uri.parse(url));
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                view.evaluateJavascript("document.documentElement.classList.add('pedalpro-native-app');", null);
                NotificationJobService.fetchNow(getApplicationContext());
                FirebaseConfigManager.sync(getApplicationContext());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!isPedalProOrigin(origin)) { callback.invoke(origin, false, false); return; }
                if (hasLocation()) callback.invoke(origin, true, false);
                else {
                    geoOrigin = origin; geoCallback = callback;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                }
            }

            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean needsCamera = false;
                    for (String r : request.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) needsCamera = true;
                    if (needsCamera && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        pendingWebPermission = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                        return;
                    }
                    request.grant(request.getResources());
                });
            }

            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = filePathCallback;
                try {
                    boolean ppup = false;
                    String[] accepts = fileChooserParams.getAcceptTypes();
                    if (accepts != null) {
                        for (String a : accepts) {
                            if (a != null && a.toLowerCase(Locale.ROOT).contains("ppup")) { ppup = true; break; }
                        }
                    }
                    Intent intent;
                    if (ppup) {
                        // .ppup is a ZIP container with a custom extension. Android file pickers
                        // often hide it when MIME filtering is enabled, so show all openable files
                        // and let PedalPro validate the package on the server.
                        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                    } else {
                        intent = fileChooserParams.createIntent();
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                    }
                    startActivityForResult(intent, REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "انتخاب فایل در دسترس نیست", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { Toast.makeText(this, "باز کردن فایل ممکن نیست", Toast.LENGTH_SHORT).show(); }
        });
    }

    public class NativeBridge {
        @JavascriptInterface public void startTracking(String json) {
            runOnUiThread(() -> {
                setRideScreenAwake(true);
                if (!hasFineLocation()) {
                    pendingTrackingJson = json;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                    return;
                }
                startNativeTracking(json);
            });
        }
        @JavascriptInterface public void stopTracking() {
            runOnUiThread(() -> {
                setRideScreenAwake(false);
                Intent i = new Intent(MainActivity.this, TrackingService.class);
                i.setAction(TrackingService.ACTION_STOP);
                startService(i);
            });
        }
        @JavascriptInterface public void discardTracking() {
            runOnUiThread(() -> {
                setRideScreenAwake(false);
                Intent i = new Intent(MainActivity.this, TrackingService.class);
                i.setAction(TrackingService.ACTION_DISCARD);
                startService(i);
            });
        }
        @JavascriptInterface public boolean isTracking() {
            return getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE).getBoolean("active", false);
        }
        @JavascriptInterface public String appVersion() { return BuildConfig.VERSION_NAME; }
        @JavascriptInterface public int appVersionCode() { return BuildConfig.VERSION_CODE; }
        @JavascriptInterface public void checkForUpdate() {
            runOnUiThread(() -> checkReleasePolicy(true));
        }
        @JavascriptInterface public void requestNotifications() {
            runOnUiThread(MainActivity.this::showNotificationPermissionDialog);
        }
        @JavascriptInterface public String storeChannel() { return BuildConfig.STORE_CHANNEL; }
    }

    private void checkReleasePolicy(boolean manual) {
        long now = System.currentTimeMillis();
        if (updateCheckRunning) return;
        if (!manual && now - lastUpdateCheckMs < 25000L) return;
        updateCheckRunning = true;
        lastUpdateCheckMs = now;
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL u = new URL("https://pedalpro.ir/api.php?action=app_latest_release");
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(12000);
                c.setRequestProperty("Accept", "application/json");
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = br.readLine()) != null) sb.append(line);
                }
                JSONObject root = new JSONObject(sb.toString());
                JSONObject rel = root.optJSONObject("release");
                if (rel == null) throw new Exception("release missing");
                int latest = rel.optInt("version_code", 0);
                String version = rel.optString("version_name", String.valueOf(latest));
                String notes = rel.optString("release_notes", "");
                String apkUrl = rel.optString("apk_url", "");
                boolean mandatory = rel.optBoolean("is_mandatory", false);
                runOnUiThread(() -> {
                    if (latest > BuildConfig.VERSION_CODE) showReleaseDialog(version, notes, apkUrl, mandatory);
                    else {
                        if (forcedUpdateDialog != null && forcedUpdateDialog.isShowing()) forcedUpdateDialog.dismiss();
                        if (webView != null) webView.setEnabled(true);
                        if (manual) Toast.makeText(MainActivity.this, "PedalPro به‌روز است.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (manual) runOnUiThread(() -> Toast.makeText(MainActivity.this, "بررسی نسخه انجام نشد؛ اتصال اینترنت را بررسی کنید.", Toast.LENGTH_LONG).show());
            } finally {
                if (c != null) c.disconnect();
                updateCheckRunning = false;
            }
        }, "PedalProUpdateCheck").start();
    }

    private void showReleaseDialog(String version, String notes, String apkUrl, boolean mandatory) {
        if (isFinishing() || isDestroyed()) return;
        if (forcedUpdateDialog != null && forcedUpdateDialog.isShowing()) forcedUpdateDialog.dismiss();
        String msg = "نسخه " + version + " آماده است." + (notes == null || notes.trim().isEmpty() ? "" : "\n\n" + notes.trim());
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                .setTitle(mandatory ? "بروزرسانی اجباری PedalPro" : "نسخه جدید PedalPro")
                .setMessage(msg)
                .setPositiveButton(mandatory ? "بروزرسانی الزامی" : "بروزرسانی", (d, w) ->
                        StoreUpdateManager.openUpdate(MainActivity.this, apkUrl));
        if (!mandatory) b.setNegativeButton("بعداً", null);
        forcedUpdateDialog = b.create();
        forcedUpdateDialog.setCancelable(!mandatory);
        forcedUpdateDialog.setCanceledOnTouchOutside(!mandatory);
        forcedUpdateDialog.setOnShowListener(x -> {
            if (mandatory && webView != null) webView.setEnabled(false);
        });
        forcedUpdateDialog.setOnDismissListener(x -> {
            if (!mandatory && webView != null) webView.setEnabled(true);
        });
        forcedUpdateDialog.show();
    }

    private void setRideScreenAwake(boolean keepAwake) {
        try {
            if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Throwable ignored) { }
    }

    private void startNativeTracking(String json) {
        try {
            JSONObject o = new JSONObject(json);
            Intent i = new Intent(this, TrackingService.class);
            i.setAction(TrackingService.ACTION_START);
            i.putExtra("ride_id", o.optInt("ride_id", 0));
            i.putExtra("csrf", o.optString("csrf", ""));
            i.putExtra("max_accuracy", o.optDouble("max_accuracy", 25));
            i.putExtra("max_speed_kmh", o.optDouble("max_speed_kmh", 100));
            i.putExtra("interval_ms", o.optLong("interval_ms", 2500));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
        } catch (Exception e) {
            Toast.makeText(this, "شروع GPS اندروید ناموفق بود", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isPedalProOrigin(String origin) {
        try {
            Uri u = Uri.parse(origin);
            String h = u.getHost();
            return "https".equalsIgnoreCase(u.getScheme()) && h != null &&
                    (h.equalsIgnoreCase("pedalpro.ir") || h.equalsIgnoreCase("www.pedalpro.ir"));
        } catch (Throwable e) { return false; }
    }

    private boolean handleUri(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (("https".equals(scheme) || "http".equals(scheme)) &&
                ("pedalpro.ir".equals(host) || "www.pedalpro.ir".equals(host))) return false;
        if ("http".equals(scheme) || "https".equals(scheme) || "geo".equals(scheme) ||
                "market".equals(scheme) || "tel".equals(scheme) || "mailto".equals(scheme)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (Exception e) { Toast.makeText(this, "برنامه مناسب برای باز کردن این لینک پیدا نشد", Toast.LENGTH_SHORT).show(); }
            return true;
        }
        if ("intent".equals(scheme)) {
            try { startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)); } catch (Exception ignored) { }
            return true;
        }
        return false;
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasFineLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void showLocationSettingsHelp() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("دقت GPS PedalPro")
                .setMessage("برای ثبت دقیق مسیر، دسترسی Location را روی «Precise / دقیق» و «Allow while using the app» بگذارید و GPS گوشی را روشن کنید.")
                .setPositiveButton("تنظیمات برنامه", (d, w) -> {
                    try {
                        Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception ignored) { }
                })
                .setNegativeButton("بعداً", null)
                .show();
    }

    private void requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences prefs = getSharedPreferences("pedalpro_permissions", MODE_PRIVATE);
        if (prefs.getBoolean("notification_prompt_shown", false)) return;
        prefs.edit().putBoolean("notification_prompt_shown", true).apply();
        showNotificationPermissionDialog();
    }

    private void showNotificationPermissionDialog() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("اعلان‌های PedalPro")
                .setMessage("برای دریافت پیام‌های چت، چالش‌های زنده و هشدارهای PedalPro اجازه اعلان را فعال کنید.")
                .setPositiveButton("فعال کردن", (d, w) -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS))
                .setNegativeButton("بعداً", null)
                .show();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean tracking = getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE).getBoolean("active", false);
        setRideScreenAwake(tracking);
        NotificationJobService.fetchNow(getApplicationContext());
        FirebaseConfigManager.sync(getApplicationContext());
        webView.postDelayed(() -> checkReleasePolicy(false), 650L);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = resolveInitialUrl(intent);
        if (webView != null && !HOME.equals(url)) webView.loadUrl(url);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean ok = hasLocation();
            boolean fine = hasFineLocation();
            if (geoCallback != null) {
                geoCallback.invoke(geoOrigin, ok, false);
                geoCallback = null; geoOrigin = null;
            }
            if (fine && pendingTrackingJson != null) {
                String json = pendingTrackingJson; pendingTrackingJson = null; startNativeTracking(json);
            } else if (pendingTrackingJson != null) {
                pendingTrackingJson = null;
                showLocationSettingsHelp();
                Toast.makeText(this, "برای ثبت مسیر دقیق، Location را روی Precise قرار دهید.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_CAMERA && pendingWebPermission != null) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                pendingWebPermission.grant(pendingWebPermission.getResources());
            else pendingWebPermission.deny();
            pendingWebPermission = null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result); fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        if (backDispatching) return;
        backDispatching = true;
        String js = "(function(){try{" +
                "if(window.PedalProHandleAndroidBack){return !!window.PedalProHandleAndroidBack();}" +
                "return false;}catch(e){return false;}})();";
        webView.evaluateJavascript(js, value -> {
            backDispatching = false;
            boolean handled = "true".equalsIgnoreCase(value) ||
                    "\"true\"".equalsIgnoreCase(value) ||
                    "1".equals(value) || "\"1\"".equals(value);
            if (!handled) fallbackNativeBack();
        });
    }

    private void fallbackNativeBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState); super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(trackingReceiver); } catch (Exception ignored) { }
        if (webView != null) {
            webView.stopLoading(); webView.setWebChromeClient(null); webView.setWebViewClient(null); webView.destroy();
        }
        super.onDestroy();
    }
}
