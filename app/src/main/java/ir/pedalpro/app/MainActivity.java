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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    private String rideHudCssCache;
    private String rideHudJsCache;

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
                installWebPerformanceGuard(view);
                installRouteLazyGuard(view);
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
                    Intent intent = fileChooserParams.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
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
                "function thinTo(a,max){if(!Array.isArray(a)||a.length<=max)return a;" +
                "var out=[],step=(a.length-1)/(max-1);for(var i=0;i<max;i++)out.push(a[Math.min(a.length-1,Math.round(i*step))]);" +
                "out[0]=a[0];out[out.length-1]=a[a.length-1];return out;}" +
                "var OF=window.fetch;" +
                "if(OF){var IF=new Map(),CA=new Map();" +
                "window.fetch=function(input,init){try{" +
                "var method=((init&&init.method)||((input&&input.method)||'GET')).toUpperCase();" +
                "var url=(typeof input==='string')?input:((input&&input.url)||'');" +
                "if(method==='GET'&&/(?:^|\\/)api\\.php(?:\\?|$)/i.test(url)){" +
                "var now=Date.now(),c=CA.get(url);if(c&&now-c.t<350){return Promise.resolve(c.r.clone());}" +
                "var a=IF.get(url);if(a){return a.then(function(r){return r.clone();});}" +
                "var p=OF.apply(this,arguments).then(function(r){try{CA.set(url,{t:Date.now(),r:r.clone()});}catch(e){}return r;})" +
                ".finally(function(){IF.delete(url);});IF.set(url,p);return p;}" +
                "}catch(e){}return OF.apply(this,arguments);};}" +
                "var OSI=window.setInterval;window.setInterval=function(fn,delay){" +
                "var d=Number(delay)||0;try{var src=(typeof fn==='function')?Function.prototype.toString.call(fn):String(fn);" +
                "if(d<800&&/(api\\.php|fetch\\(|XMLHttpRequest|live|track|setLatLngs|polyline)/i.test(src))d=800;}catch(e){}" +
                "var args=Array.prototype.slice.call(arguments,2);return OSI.apply(window,[fn,d].concat(args));};" +
                "function patchLeaflet(){try{if(!window.L||!L.Polyline||L.Polyline.prototype.__pp)return;" +
                "var p=L.Polyline.prototype,orig=p.setLatLngs;p.setLatLngs=function(a){try{a=thinTo(a,600);}catch(e){}return orig.call(this,a);};p.__pp=1;" +
                "if(L.Map&&L.Map.prototype&&!L.Map.prototype.__ppfit){var fb=L.Map.prototype.fitBounds,last=0;" +
                "L.Map.prototype.fitBounds=function(){var n=Date.now();if(n-last<250)return this;last=n;" +
                "try{if(arguments[1])arguments[1].animate=false;}catch(e){}return fb.apply(this,arguments);};L.Map.prototype.__ppfit=1;}" +
                "}catch(e){}}" +
                "function patchRideDetails(){try{" +
                "if(window.speedChart&&!window.speedChart.__ppLite){var SC=window.speedChart;" +
                "var SW=function(points){return SC.call(this,thinTo(points,260));};SW.__ppLite=1;window.speedChart=SW;}" +
                "if(window.drawRide&&!window.drawRide.__ppLite){var DR=window.drawRide;" +
                "var DW=function(id,ride,fit){try{" +
                "if(id==='rideLiveMap'&&window.L){" +
                "var m=null;try{m=(typeof maps!=='undefined'&&maps[id])?maps[id]:null;}catch(e){}" +
                "if(!m||m.__neshan){try{if(m&&m.remove)m.remove();}catch(e){}try{if(typeof maps!=='undefined')delete maps[id];}catch(e){}" +
                "try{m=(typeof initMap==='function')?initMap(id):null;}catch(e){m=null;}}" +
                "if(m&&m.addLayer){" +
                "try{if(m.__ppRideLayer)m.removeLayer(m.__ppRideLayer);}catch(e){}" +
                "var g=L.featureGroup().addTo(m);m.__ppRideLayer=g;" +
                "var src=thinTo((ride&&ride.points)||[],520),pts=[];" +
                "for(var i=0;i<src.length;i++){var p=src[i]||{},lat=+(p.lat!=null?p.lat:p.latitude),lng=+(p.lng!=null?p.lng:p.longitude);" +
                "if(Number.isFinite(lat)&&Number.isFinite(lng))pts.push([lat,lng]);}" +
                "if(pts.length>1){var line=L.polyline(pts,{color:'#2ee6a6',weight:5,opacity:.9,smoothFactor:2}).addTo(g);" +
                "L.circleMarker(pts[0],{radius:6,color:'#2ee6a6'}).addTo(g);" +
                "L.circleMarker(pts[pts.length-1],{radius:7,color:'#ff557e',weight:2}).addTo(g);" +
                "if(fit!==false)try{m.fitBounds(line.getBounds(),{padding:[20,20],maxZoom:17,animate:false});}catch(e){}" +
                "}else if(pts.length===1){L.circleMarker(pts[0],{radius:8,color:'#2ee6a6',weight:2}).addTo(g);try{m.setView(pts[0],17,{animate:false});}catch(e){}}" +
                "else if(ride&&ride.last_lat&&ride.last_lng){var ll=[+ride.last_lat,+ride.last_lng];L.circleMarker(ll,{radius:8,color:'#2ee6a6',weight:2}).addTo(g);try{m.setView(ll,17,{animate:false});}catch(e){}}" +
                "return;}" +
                "}" +
                "var rr=ride;if(ride&&Array.isArray(ride.points)&&ride.points.length>600)rr=Object.assign({},ride,{points:thinTo(ride.points,600)});" +
                "return DR.call(this,id,rr,fit);" +
                "}catch(e){return DR.apply(this,arguments);}};DW.__ppLite=1;window.drawRide=DW;}" +
                "if(window.openRide&&!window.openRide.__ppLite){var OR=window.openRide;" +
                "var OW=function(id,fromPoll){var self=this,args=arguments;" +
                "if(window.__PP_RIDE_DETAIL_BUSY__){return window.__PP_RIDE_DETAIL_BUSY__;}" +
                "var task=new Promise(function(resolve){requestAnimationFrame(function(){resolve();});})" +
                ".then(function(){return OR.apply(self,args);})" +
                ".finally(function(){setTimeout(function(){if(window.__PP_RIDE_DETAIL_BUSY__===task)window.__PP_RIDE_DETAIL_BUSY__=null;},80);});" +
                "window.__PP_RIDE_DETAIL_BUSY__=task;return task;};OW.__ppLite=1;window.openRide=OW;}" +
                "}catch(e){}}" +
                "patchLeaflet();patchRideDetails();OSI(patchLeaflet,2000);OSI(patchRideDetails,1200);" +
                "}catch(e){}})();";
        try { view.evaluateJavascript(js, null); } catch (Throwable ignored) { }
    }

    private void installRouteLazyGuard(WebView view) {
        if (view == null) return;
        String js =
                "(function(){try{" +
                "if(window.__PP_ROUTE_LAZY__)return;window.__PP_ROUTE_LAZY__=1;" +
                "function patch(){try{if(typeof window.drawRide!=='function'||window.drawRide.__ppLazy)return;" +
                "var DR=window.drawRide;" +
                "var DW=function(id,ride,fit){try{" +
                "var el=document.getElementById(id);var key=String(id||'').toLowerCase();" +
                "var isRideMap=(id==='rideLiveMap'||(key.indexOf('ride')>=0&&key.indexOf('map')>=0));" +
                "if(isRideMap&&el&&!el.dataset.ppMapEnabled){" +
                "el.__ppPendingRide={id:id,ride:ride,fit:fit};el.style.minHeight='150px';el.style.display='flex';el.style.alignItems='center';el.style.justifyContent='center';" +
                "if(!el.querySelector('.pp-show-route-map')){el.innerHTML='<button type=\"button\" class=\"pp-show-route-map\" style=\"border:0;border-radius:16px;padding:12px 20px;font-weight:800;background:#101820;color:#fff\">نمایش نقشه مسیر</button>';" +
                "el.querySelector('.pp-show-route-map').onclick=function(){var p=el.__ppPendingRide;if(!p)return;el.dataset.ppMapEnabled='1';el.dataset.ppMapFitted='0';el.innerHTML='';" +
                "try{DR.call(window,p.id,p.ride,true);el.dataset.ppMapFitted='1';}catch(e){};};}" +
                "return;}" +
                "if(isRideMap&&el&&el.dataset.ppMapEnabled==='1'){var doFit=el.dataset.ppMapFitted!=='1'&&fit!==false;var r=DR.call(this,id,ride,doFit);el.dataset.ppMapFitted='1';return r;}" +
                "return DR.apply(this,arguments);}catch(e){return DR.apply(this,arguments);}};" +
                "DW.__ppLazy=1;window.drawRide=DW;}catch(e){}}" +
                "patch();setInterval(patch,900);" +
                "}catch(e){}})();";
        try { view.evaluateJavascript(js, null); } catch (Throwable ignored) { }
    }

    public class NativeBridge {
        @JavascriptInterface public void startTracking(String json) {
            runOnUiThread(() -> {
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
                Intent i = new Intent(MainActivity.this, TrackingService.class);
                i.setAction(TrackingService.ACTION_STOP);
                startService(i);
            });
        }
        @JavascriptInterface public void pauseTracking() {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, TrackingService.class);
                i.setAction(TrackingService.ACTION_PAUSE);
                startService(i);
            });
        }
        @JavascriptInterface public void resumeTracking() {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, TrackingService.class);
                i.setAction(TrackingService.ACTION_RESUME);
                startService(i);
            });
        }
        @JavascriptInterface public boolean isTrackingPaused() {
            return getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE).getBoolean("paused", false);
        }
        @JavascriptInterface public void openLowPowerMode() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, LowPowerRideActivity.class)));
        }
        @JavascriptInterface public void discardTracking() {
            runOnUiThread(() -> {
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
            runOnUiThread(() -> StoreUpdateManager.openUpdate(MainActivity.this));
        }
        @JavascriptInterface public void requestNotifications() {
            runOnUiThread(MainActivity.this::showNotificationPermissionDialog);
        }
        @JavascriptInterface public String storeChannel() { return BuildConfig.STORE_CHANNEL; }
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
        NotificationJobService.fetchNow(getApplicationContext());
        FirebaseConfigManager.sync(getApplicationContext());
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
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
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
