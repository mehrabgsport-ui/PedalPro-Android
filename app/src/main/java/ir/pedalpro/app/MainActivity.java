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
import android.provider.MediaStore;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.File;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.HeartRateRecord;
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
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.time.Instant;

public class MainActivity extends Activity {
    private static final String HOME = "https://pedalpro.ir/";
    private static final int REQ_LOCATION = 1101;
    private static final int REQ_CAMERA = 1102;
    private static final int REQ_FILE = 1103;
    private static final int REQ_NOTIFICATIONS = 1104;
    private static final int REQ_CAMERA_FILE = 1105;
    private static final int REQ_BLE_HR = 1106;
    private static final int REQ_HEALTH_HEART_RATE = 1107;


    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraCaptureUri;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private PermissionRequest pendingWebPermission;
    private String pendingTrackingJson;
    private volatile boolean updateCheckRunning = false;
    private long lastUpdateCheckMs = 0L;
    private android.app.AlertDialog forcedUpdateDialog;
    private boolean backDispatching = false;
    private String rideHudCssCache;
    private String rideHudJsCache;
    private HeartRateBleManager heartRateManager;
    private HuaweiHealthManager huaweiHealthManager;

    // Back-pressure for native -> WebView GPS events. A busy live map must never build
    // an unbounded evaluateJavascript queue; only the newest point is retained.
    private final Handler nativeLocationHandler = new Handler(Looper.getMainLooper());
    private String pendingNativeLocationPayload = null;
    private long pendingNativeLocationTimestamp = 0L;
    private boolean nativeLocationDispatchBusy = false;
    private long lastNativeLocationDispatchAt = 0L;
    private static final long NATIVE_LOCATION_MIN_INTERVAL_MS = 450L;

    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra(TrackingService.EXTRA_PAYLOAD);
            if (payload == null || webView == null) return;
            if (TrackingService.ACTION_UPDATE.equals(intent.getAction())) {
                queueNativeLocation(payload);
                return;
            }
            String script = "window.PedalProNativeStatus && window.PedalProNativeStatus(" +
                    JSONObject.quote(payload) + ");";
            webView.post(() -> {
                try { webView.evaluateJavascript(script, null); } catch (Throwable ignored) { }
            });
        }
    };

    private void queueNativeLocation(String payload) {
        webView.post(() -> {
            long ts = System.currentTimeMillis();
            try {
                JSONObject o = new JSONObject(payload);
                ts = o.optLong("timestamp", ts);
            } catch (Throwable ignored) { }

            // Server acknowledgements for old queued fixes can arrive later. Never let
            // an older point replace a newer point that is already waiting for WebView.
            if (pendingNativeLocationPayload != null && ts < pendingNativeLocationTimestamp) return;
            pendingNativeLocationPayload = payload;
            pendingNativeLocationTimestamp = ts;
            pumpNativeLocation();
        });
    }

    private void pumpNativeLocation() {
        if (webView == null || nativeLocationDispatchBusy || pendingNativeLocationPayload == null) return;
        long now = System.currentTimeMillis();
        long wait = NATIVE_LOCATION_MIN_INTERVAL_MS - (now - lastNativeLocationDispatchAt);
        if (wait > 0L) {
            nativeLocationHandler.removeCallbacks(nativeLocationPumpRunnable);
            nativeLocationHandler.postDelayed(nativeLocationPumpRunnable, wait);
            return;
        }

        String payload = pendingNativeLocationPayload;
        pendingNativeLocationPayload = null;
        pendingNativeLocationTimestamp = 0L;
        nativeLocationDispatchBusy = true;
        lastNativeLocationDispatchAt = now;
        String script = "window.PedalProNativeLocation && window.PedalProNativeLocation(" +
                JSONObject.quote(payload) + ");";
        try {
            webView.evaluateJavascript(script, value -> {
                nativeLocationDispatchBusy = false;
                // If several GPS/server events arrived while JS was busy, only the newest
                // one remains and is dispatched next.
                nativeLocationHandler.postDelayed(nativeLocationPumpRunnable, 40L);
            });
        } catch (Throwable ignored) {
            nativeLocationDispatchBusy = false;
            nativeLocationHandler.postDelayed(nativeLocationPumpRunnable, 100L);
        }
    }

    private final Runnable nativeLocationPumpRunnable = this::pumpNativeLocation;

    private void resetNativeLocationBridge() {
        nativeLocationHandler.removeCallbacks(nativeLocationPumpRunnable);
        pendingNativeLocationPayload = null;
        pendingNativeLocationTimestamp = 0L;
        nativeLocationDispatchBusy = false;
        lastNativeLocationDispatchAt = 0L;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        heartRateManager = new HeartRateBleManager(this, webView);
        huaweiHealthManager = new HuaweiHealthManager(this);
        setupWebView();
        registerPredictiveBackHandler();
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true); } catch (Throwable ignored) { }
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUri(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUri(Uri.parse(url));
            }
            @Override public void onPageFinished(WebView view, String url) {
                resetNativeLocationBridge();
                CookieManager.getInstance().flush();
                view.evaluateJavascript("document.documentElement.classList.add('pedalpro-native-app');", null);
                installWebPerformanceGuard(view);
                installRideHud(view);
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
                    try {
                        String origin = request.getOrigin() == null ? "" : request.getOrigin().toString();
                        if (request.getOrigin() == null || !isPedalProOrigin(origin)) {
                            request.deny();
                            return;
                        }
                        boolean needsCamera = false;
                        boolean hasUnsupportedCapture = false;
                        for (String r : request.getResources()) {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) needsCamera = true;
                            else hasUnsupportedCapture = true;
                        }
                        if (hasUnsupportedCapture) {
                            request.deny();
                            return;
                        }
                        if (needsCamera && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            pendingWebPermission = request;
                            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                            return;
                        }
                        request.grant(request.getResources());
                    } catch (Throwable e) {
                        request.deny();
                    }
                });
            }

            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = filePathCallback;
                cameraCaptureUri = null;
                try {
                    boolean ppup = false;
                    String[] accepts = fileChooserParams.getAcceptTypes();
                    if (accepts != null) {
                        for (String a : accepts) {
                            if (a != null && a.toLowerCase(Locale.ROOT).contains("ppup")) { ppup = true; break; }
                        }
                    }
                    boolean imageInput = false;
                    if (accepts != null) {
                        for (String type : accepts) {
                            if (type != null && type.toLowerCase(Locale.ROOT).startsWith("image/")) {
                                imageInput = true; break;
                            }
                        }
                    }
                    if (!ppup && imageInput && fileChooserParams.isCaptureEnabled()) {
                        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_FILE);
                        } else {
                            launchCameraCapture();
                        }
                        return true;
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
                    if (fileCallback != null) fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "انتخاب فایل در دسترس نیست", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            // Myket releases cannot initiate an APK download from the embedded website.
            String path = Uri.parse(url).getPath();
            boolean apk = (path != null && path.toLowerCase(Locale.ROOT).endsWith(".apk")) ||
                    "application/vnd.android.package-archive".equalsIgnoreCase(mimetype);
            if (("myket".equals(BuildConfig.STORE_CHANNEL) || "bazaar".equals(BuildConfig.STORE_CHANNEL)) && apk) {
                StoreUpdateManager.openUpdate(MainActivity.this);
                return;
            }
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { Toast.makeText(this, "باز کردن فایل ممکن نیست", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void launchCameraCapture() {
        try {
            File directory = new File(getCacheDir(), "camera");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("camera cache unavailable");
            File photo = File.createTempFile("pedalpro_photo_", ".jpg", directory);
            cameraCaptureUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraCaptureUri);
            intent.setClipData(android.content.ClipData.newRawUri("photo", cameraCaptureUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQ_FILE);
        } catch (Exception e) {
            cameraCaptureUri = null;
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = null;
            Toast.makeText(this, "دوربین گوشی در دسترس نیست", Toast.LENGTH_LONG).show();
        }
    }

    private void installRideHud(WebView view) {
        if (view == null) return;
        try {
            if (rideHudCssCache == null) rideHudCssCache = readAssetText("pedalpro_ride_hud_v4.css");
            if (rideHudJsCache == null) rideHudJsCache = readAssetText("pedalpro_ride_hud_v4.js");
            String css = rideHudCssCache == null ? "" : rideHudCssCache;
            String js = rideHudJsCache == null ? "" : rideHudJsCache;
            if (!css.isEmpty()) {
                String injectCss = "(function(){try{if(document.getElementById('pp-hud4-style'))return;" +
                        "var s=document.createElement('style');s.id='pp-hud4-style';s.textContent=" +
                        JSONObject.quote(css) + ";document.head.appendChild(s);}catch(e){}})();";
                view.evaluateJavascript(injectCss, null);
            }
            if (!js.isEmpty()) view.evaluateJavascript(js, null);
        } catch (Throwable ignored) { }
    }

    private String readAssetText(String name) {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getAssets().open(name);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } catch (Throwable ignored) { }
        return sb.toString();
    }

    private void installWebPerformanceGuard(WebView view) {
        if (view == null) return;
        String js =
                "(function(){try{" +
                "if(window.__PP_PERF_GUARD__)return;window.__PP_PERF_GUARD__=1;" +
                "var OF=window.fetch;" +
                "if(OF){var IF=new Map(),CA=new Map();" +
                "window.fetch=function(input,init){try{" +
                "var method=((init&&init.method)||((input&&input.method)||'GET')).toUpperCase();" +
                "var url=(typeof input==='string')?input:((input&&input.url)||'');" +
                "if(method==='GET'&&/(?:^|\\\\/)api\\\\.php(?:\\\\?|$)/i.test(url)){" +
                "var now=Date.now(),c=CA.get(url);if(c&&now-c.t<350){return Promise.resolve(c.r.clone());}" +
                "var a=IF.get(url);if(a){return a.then(function(r){return r.clone();});}" +
                "var p=OF.apply(this,arguments).then(function(r){try{CA.set(url,{t:Date.now(),r:r.clone()});}catch(e){}return r;})" +
                ".finally(function(){IF.delete(url);});IF.set(url,p);return p;}" +
                "}catch(e){}return OF.apply(this,arguments);};}" +
                "var OSI=window.setInterval;window.setInterval=function(fn,delay){" +
                "var d=Number(delay)||0;try{var src=(typeof fn==='function')?Function.prototype.toString.call(fn):String(fn);" +
                "if(d<800&&/(api\\\\.php|fetch\\\\(|XMLHttpRequest|live|track|setLatLngs|polyline)/i.test(src))d=800;}catch(e){}" +
                "var args=Array.prototype.slice.call(arguments,2);return OSI.apply(window,[fn,d].concat(args));};" +
                "function thin(a){if(!Array.isArray(a)||a.length<=1200)return a;var st=Math.ceil(a.length/1200),o=[];" +
                "for(var i=0;i<a.length;i+=st)o.push(a[i]);if(o[o.length-1]!==a[a.length-1])o.push(a[a.length-1]);return o;}" +
                "function patchLeaflet(){try{if(!window.L||!L.Polyline||L.Polyline.prototype.__pp)return;" +
                "var p=L.Polyline.prototype,orig=p.setLatLngs;p.setLatLngs=function(a){try{a=thin(a);}catch(e){}return orig.call(this,a);};p.__pp=1;" +
                "if(L.Map&&L.Map.prototype&&!L.Map.prototype.__ppfit){var fb=L.Map.prototype.fitBounds,last=0;" +
                "L.Map.prototype.fitBounds=function(){var n=Date.now();if(n-last<250)return this;last=n;" +
                "try{if(arguments[1])arguments[1].animate=false;}catch(e){}return fb.apply(this,arguments);};L.Map.prototype.__ppfit=1;}" +
                "}catch(e){}}" +
                "patchLeaflet();OSI(patchLeaflet,2000);" +
                "}catch(e){}})();";
        try { view.evaluateJavascript(js, null); } catch (Throwable ignored) { }
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
        @JavascriptInterface public void startHeartRateScan() {
            runOnUiThread(() -> {
                if (heartRateManager == null) return;
                if (heartRateManager.hasBlePermissions()) {
                    heartRateManager.startScan();
                    return;
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLE_HR);
                } else {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_BLE_HR);
                }
            });
        }
        @JavascriptInterface public String heartRateDevices() {
            return heartRateManager == null ? "[]" : heartRateManager.devicesJson();
        }
        @JavascriptInterface public String heartRateStatus() {
            return heartRateManager == null ? "{\"supported\":false}" : heartRateManager.statusJson();
        }
        @JavascriptInterface public void connectHeartRateDevice(String address) {
            runOnUiThread(() -> { if (heartRateManager != null) heartRateManager.connect(address); });
        }
        @JavascriptInterface public void disconnectHeartRateDevice() {
            runOnUiThread(() -> { if (heartRateManager != null) heartRateManager.disconnect(); });
        }
        @JavascriptInterface public void openHealthConnectSettings() {
            runOnUiThread(MainActivity.this::openHealthConnectSettingsNative);
        }
        @JavascriptInterface public void readHealthConnectHeartRate() {
            runOnUiThread(MainActivity.this::requestHealthConnectHeartRate);
        }

        @JavascriptInterface public void connectHuaweiHealth() {
            runOnUiThread(() -> { if (huaweiHealthManager != null) huaweiHealthManager.connectAndStart(); });
        }
        @JavascriptInterface public void disconnectHuaweiHealth() {
            runOnUiThread(() -> { if (huaweiHealthManager != null) huaweiHealthManager.stop(); });
        }
        @JavascriptInterface public void openHuaweiHealth() {
            runOnUiThread(() -> { if (huaweiHealthManager != null) huaweiHealthManager.openHuaweiHealth(); });
        }
        @JavascriptInterface public void syncHuaweiHealth() {
            runOnUiThread(() -> { if (huaweiHealthManager != null) huaweiHealthManager.openHuaweiHealthSync(); });
        }
        @JavascriptInterface public String huaweiHealthStatus() {
            return huaweiHealthManager == null ? "{\"configured\":false}" : huaweiHealthManager.statusJson();
        }

        @JavascriptInterface public String devicePowerInfo() {
            try {
                JSONObject o = new JSONObject();
                o.put("manufacturer", Build.MANUFACTURER == null ? "" : Build.MANUFACTURER);
                o.put("brand", Build.BRAND == null ? "" : Build.BRAND);
                o.put("model", Build.MODEL == null ? "" : Build.MODEL);
                o.put("android", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE);
                o.put("sdk", Build.VERSION.SDK_INT);
                return o.toString();
            } catch (Throwable e) { return "{}"; }
        }

        @JavascriptInterface public void openBatterySettings() {
            runOnUiThread(() -> openBatterySettingsForDevice());
        }

        @JavascriptInterface public void openAppSettings() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Throwable ignored) { }
            });
        }

        @JavascriptInterface public void openAutostartSettings() {
            runOnUiThread(() -> openAutostartSettingsForDevice());
        }

        @JavascriptInterface public void setNeshanServiceKey(String key) {
            if (!NeshanRoadMatcher.validKey(key)) return;
            getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE)
                    .edit().putString("neshan_service_key", key.trim()).apply();
        }
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
                // The website's APK release may precede store approval.
                // Never block Myket users for an unpublished web-only release.
                boolean mandatory = rel.optBoolean("is_mandatory", false) &&
                        !"myket".equals(BuildConfig.STORE_CHANNEL) &&
                        !"bazaar".equals(BuildConfig.STORE_CHANNEL);
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
            String neshanKey = o.optString("neshan_service_key", "");
            if (!NeshanRoadMatcher.validKey(neshanKey)) {
                neshanKey = getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE)
                        .getString("neshan_service_key", "");
            }
            if (NeshanRoadMatcher.validKey(neshanKey)) i.putExtra("neshan_service_key", neshanKey);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
        } catch (Exception e) {
            Toast.makeText(this, "شروع GPS اندروید ناموفق بود", Toast.LENGTH_SHORT).show();
        }
    }


    private void openHealthConnectSettingsNative() {
        if (Build.VERSION.SDK_INT < 34) {
            Toast.makeText(this, "Health Connect روی Android 14 به بالا به‌صورت سیستمی پشتیبانی می‌شود.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent i = new Intent(HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS);
            i.putExtra(Intent.EXTRA_PACKAGE_NAME, getPackageName());
            startActivity(i);
        } catch (Throwable e) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable ignored) { }
        }
    }

    private void requestHealthConnectHeartRate() {
        if (Build.VERSION.SDK_INT < 34) {
            Toast.makeText(this, "برای Health Connect سیستمی، Android 14 یا جدیدتر لازم است. اتصال BLE همچنان قابل استفاده است.", Toast.LENGTH_LONG).show();
            return;
        }
        if (checkSelfPermission(HealthPermissions.READ_HEART_RATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{HealthPermissions.READ_HEART_RATE}, REQ_HEALTH_HEART_RATE);
            return;
        }
        readLatestHealthConnectHeartRate();
    }

    @SuppressLint("NewApi")
    private void readLatestHealthConnectHeartRate() {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            HealthConnectManager manager = getSystemService(HealthConnectManager.class);
            if (manager == null) {
                Toast.makeText(this, "Health Connect در این گوشی در دسترس نیست.", Toast.LENGTH_LONG).show();
                return;
            }
            Instant end = Instant.now();
            Instant start = end.minusSeconds(6L * 60L * 60L);
            TimeInstantRangeFilter range = new TimeInstantRangeFilter.Builder()
                    .setStartTime(start)
                    .setEndTime(end)
                    .build();
            ReadRecordsRequestUsingFilters<HeartRateRecord> request =
                    new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                            .setTimeRangeFilter(range)
                            .setAscending(false)
                            .setPageSize(50)
                            .build();
            manager.readRecords(request, getMainExecutor(),
                    new OutcomeReceiver<ReadRecordsResponse<HeartRateRecord>, HealthConnectException>() {
                        @Override public void onResult(ReadRecordsResponse<HeartRateRecord> response) {
                            HeartRateRecord.HeartRateSample newest = null;
                            for (HeartRateRecord record : response.getRecords()) {
                                for (HeartRateRecord.HeartRateSample sample : record.getSamples()) {
                                    if (newest == null || sample.getTime().isAfter(newest.getTime())) newest = sample;
                                }
                            }
                            if (newest == null) {
                                Toast.makeText(MainActivity.this, "داده ضربان جدیدی در Health Connect پیدا نشد.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            int value = (int)newest.getBeatsPerMinute();
                            long ts = newest.getTime().toEpochMilli();
                            if (heartRateManager != null) heartRateManager.acceptExternalHeartRate(value, "health_connect", "Health Connect", ts);
                            Toast.makeText(MainActivity.this, "ضربان از Health Connect خوانده شد: " + value, Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(HealthConnectException error) {
                            Toast.makeText(MainActivity.this, "خواندن Health Connect انجام نشد.", Toast.LENGTH_LONG).show();
                        }
                    });
        } catch (SecurityException e) {
            openHealthConnectSettingsNative();
        } catch (Throwable e) {
            Toast.makeText(this, "Health Connect روی این دستگاه آماده نیست.", Toast.LENGTH_LONG).show();
        }
    }

    public void onHuaweiHeartRate(int bpm, long timestampMs, int confidence) {
        if (heartRateManager != null) heartRateManager.acceptExternalHeartRate(bpm, "huawei_health", "Huawei Health", timestampMs);
        JSONObject o = new JSONObject();
        try {
            o.put("bpm", bpm); o.put("timestamp", timestampMs); o.put("confidence", confidence);
            o.put("source", "huawei_health"); o.put("deviceName", "Huawei Health");
        } catch (Throwable ignored) { }
        final String payload = o.toString();
        if (webView != null) webView.post(() -> {
            try { webView.evaluateJavascript("window.PedalProHeartRateNative&&window.PedalProHeartRateNative(" + JSONObject.quote(payload) + ");", null); }
            catch (Throwable ignored) { }
        });
    }

    public void emitHuaweiHealthStatus(String state, String message, boolean running) {
        JSONObject o = new JSONObject();
        try {
            o.put("state", state); o.put("message", message); o.put("running", running);
            o.put("configured", huaweiHealthManager != null && huaweiHealthManager.isConfigured());
        } catch (Throwable ignored) { }
        final String payload = o.toString();
        if (webView != null) webView.post(() -> {
            try { webView.evaluateJavascript("window.PedalProHuaweiHealthStatus&&window.PedalProHuaweiHealthStatus(" + JSONObject.quote(payload) + ");", null); }
            catch (Throwable ignored) { }
        });
    }

    private boolean launchComponent(String pkg, String cls) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName(pkg, cls));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private void openAutostartSettingsForDevice() {
        String m = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER).toLowerCase(Locale.ROOT);
        boolean opened = false;
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            opened = launchComponent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity");
        } else if (m.contains("huawei") || m.contains("honor")) {
            opened = launchComponent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
        } else if (m.contains("vivo") || m.contains("iqoo")) {
            opened = launchComponent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity");
        } else if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) {
            opened = launchComponent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity");
            if (!opened) opened = launchComponent("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity");
        } else if (m.contains("asus")) {
            opened = launchComponent("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity");
        }
        if (!opened) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable ignored) { }
        }
    }

    private void openBatterySettingsForDevice() {
        String m = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER).toLowerCase(Locale.ROOT);
        boolean opened = false;

        // Prefer the vendor's own battery-management screen when it is available.
        if (m.contains("samsung")) {
            opened = launchComponent("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity");
            if (!opened) opened = launchComponent("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity");
        } else if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            opened = launchComponent("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity");
        } else if (m.contains("huawei") || m.contains("honor")) {
            opened = launchComponent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity");
        } else if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) {
            opened = launchComponent("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity");
        } else if (m.contains("vivo") || m.contains("iqoo")) {
            opened = launchComponent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager");
        }

        if (!opened) {
            try {
                Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(i);
                opened = true;
            } catch (Throwable ignored) { }
        }

        if (!opened) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable ignored) { }
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
        if ("myket".equals(BuildConfig.STORE_CHANNEL) || "bazaar".equals(BuildConfig.STORE_CHANNEL)) {
            boolean isMyket = "myket".equals(scheme) || "myket.ir".equals(host) || host.endsWith(".myket.ir");
            boolean isBazaar = "bazaar".equals(scheme) || "cafebazaar.ir".equals(host) || host.endsWith(".cafebazaar.ir");
            boolean wrongMarket = ("myket".equals(BuildConfig.STORE_CHANNEL) && isBazaar) ||
                    ("bazaar".equals(BuildConfig.STORE_CHANNEL) && isMyket);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            boolean isExternalApk = ("http".equals(scheme) || "https".equals(scheme)) && path.endsWith(".apk");
            if (wrongMarket || isExternalApk) {
                StoreUpdateManager.openUpdate(this);
                return true;
            }
        }
        if (("https".equals(scheme) || "http".equals(scheme)) &&
                ("pedalpro.ir".equals(host) || "www.pedalpro.ir".equals(host))) return false;
        if ("http".equals(scheme) || "https".equals(scheme) || "geo".equals(scheme) ||
                "market".equals(scheme) || "myket".equals(scheme) || "bazaar".equals(scheme) ||
                "tel".equals(scheme) || "mailto".equals(scheme)) {
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
        } else if (requestCode == REQ_CAMERA_FILE) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                launchCameraCapture();
            else {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = null;
                Toast.makeText(this, "برای عکس گرفتن، دسترسی دوربین لازم است", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_CAMERA && pendingWebPermission != null) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                pendingWebPermission.grant(pendingWebPermission.getResources());
            else pendingWebPermission.deny();
            pendingWebPermission = null;
        } else if (requestCode == REQ_BLE_HR) {
            if (heartRateManager != null && heartRateManager.hasBlePermissions()) heartRateManager.startScan();
            else Toast.makeText(this, "برای اتصال ساعت/سنسور، اجازه Bluetooth لازم است.", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQ_HEALTH_HEART_RATE) {
            if (Build.VERSION.SDK_INT >= 34 && checkSelfPermission(HealthPermissions.READ_HEART_RATE) == PackageManager.PERMISSION_GRANTED)
                readLatestHealthConnectHeartRate();
            else Toast.makeText(this, "اجازه خواندن ضربان Health Connect داده نشد.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (huaweiHealthManager != null && requestCode == HuaweiHealthManager.REQ_HUAWEI_SIGN_IN) {
            huaweiHealthManager.handleActivityResult(requestCode, data);
            return;
        }
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK) {
                result = cameraCaptureUri != null ? new Uri[]{cameraCaptureUri} :
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            cameraCaptureUri = null;
        }
    }

    private void registerPredictiveBackHandler() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBackNavigation);
        }
    }

    private void handleBackNavigation() {
        if (webView == null) {
            finishAfterTransition();
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

    @Override public void onBackPressed() {
        handleBackNavigation();
    }

    private void fallbackNativeBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finishAfterTransition();
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState); super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        resetNativeLocationBridge();
        if (heartRateManager != null) {
            try { heartRateManager.stopScan(); } catch (Throwable ignored) { }
            try { heartRateManager.disconnect(); } catch (Throwable ignored) { }
        }
        if (huaweiHealthManager != null) {
            try { huaweiHealthManager.stop(); } catch (Throwable ignored) { }
        }
        try { unregisterReceiver(trackingReceiver); } catch (Exception ignored) { }
        if (webView != null) {
            webView.stopLoading(); webView.setWebChromeClient(null); webView.setWebViewClient(null); webView.destroy();
        }
        super.onDestroy();
    }
}
