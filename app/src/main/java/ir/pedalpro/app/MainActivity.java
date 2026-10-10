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
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
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
    private boolean lowPowerModeRequested = false;

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
                captureNeshanKey(view);
                injectNeshanKey(view);
                installWebPerformanceGuard(view);
                installRouteLazyGuard(view);
                installRideHud(view);
                installDiagnosticButton(view);
                NotificationJobService.fetchNow(getApplicationContext());
                FirebaseConfigManager.sync(getApplicationContext());
                view.postDelayed(() -> {
                    captureNeshanKey(view);
                    injectNeshanKey(view);
                }, 1500L);
                view.postDelayed(() -> {
                    captureNeshanKey(view);
                    injectNeshanKey(view);
                }, 4500L);
                DiagnosticLogger.log(MainActivity.this, "web", "page_finished " + url);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    DiagnosticLogger.log(MainActivity.this, "web_error",
                            "main_frame code=" + (error == null ? "?" : error.getErrorCode()) +
                                    " desc=" + (error == null ? "" : error.getDescription()) +
                                    " url=" + request.getUrl());
                }
                super.onReceivedError(view, request, error);
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request != null && request.isForMainFrame()) {
                    DiagnosticLogger.log(MainActivity.this, "http_error",
                            "status=" + (errorResponse == null ? "?" : errorResponse.getStatusCode()) +
                                    " url=" + request.getUrl());
                }
                super.onReceivedHttpError(view, request, errorResponse);
            }

            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                DiagnosticLogger.log(MainActivity.this, "renderer",
                        "gone crashed=" + (detail != null && detail.didCrash()) +
                                " priority=" + (detail == null ? "?" : detail.rendererPriorityAtExit()));
                try {
                    if (view != null) {
                        ((FrameLayout)view.getParent()).removeView(view);
                        view.destroy();
                    }
                } catch (Throwable ignored) { }
                Toast.makeText(MainActivity.this, "صفحه وب ریست شد؛ گزارش خطا ذخیره شد", Toast.LENGTH_LONG).show();
                recreate();
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                if (message != null) {
                    DiagnosticLogger.log(MainActivity.this, "js",
                            message.messageLevel() + " " + message.sourceId() + ":" +
                                    message.lineNumber() + " " + message.message());
                }
                return true;
            }

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

    private void installDiagnosticButton(WebView view) {
        if (view == null) return;
        String js =
                "(function(){try{" +
                "if(document.getElementById('pp-native-diag'))return;" +
                "var b=document.createElement('button');b.id='pp-native-diag';b.type='button';b.textContent='LOG';" +
                "b.style.cssText='position:fixed;left:8px;top:45%;z-index:2147483646;border:1px solid rgba(255,255,255,.24);border-radius:10px;background:rgba(5,13,20,.72);color:#b8f4ff;font:700 10px sans-serif;padding:7px 8px;opacity:.72';" +
                "b.onclick=function(){try{if(window.AndroidBridge&&AndroidBridge.openDiagnostics){AndroidBridge.openDiagnostics();}}catch(e){}};" +
                "document.body.appendChild(b);" +
                "window.addEventListener('error',function(e){try{AndroidBridge.diagnosticLog('js_error',String(e.message)+' @ '+String(e.filename)+':'+String(e.lineno));}catch(x){}});" +
                "window.addEventListener('unhandledrejection',function(e){try{AndroidBridge.diagnosticLog('promise_rejection',String(e.reason));}catch(x){}});" +
                "}catch(e){}})();";
        try { view.evaluateJavascript(js, null); } catch (Throwable ignored) { }
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
                "function log(c,m){try{if(window.AndroidBridge&&AndroidBridge.diagnosticLog)AndroidBridge.diagnosticLog(c,String(m));}catch(e){}}" +
                "function thinTo(a,max){if(!Array.isArray(a)||a.length<=max)return a;" +
                "var out=[],step=(a.length-1)/(max-1);for(var i=0;i<max;i++)out.push(a[Math.min(a.length-1,Math.round(i*step))]);" +
                "out[0]=a[0];out[out.length-1]=a[a.length-1];return out;}" +
                "var OF=window.fetch;" +
                "if(OF){var IF=new Map(),CA=new Map();" +
                "window.fetch=function(input,init){var started=Date.now(),url=(typeof input==='string')?input:((input&&input.url)||'');try{" +
                "var method=((init&&init.method)||((input&&input.method)||'GET')).toUpperCase();" +
                "if(method==='GET'&&/(?:^|\\/)api\\.php(?:\\?|$)/i.test(url)){" +
                "var now=Date.now(),cc=CA.get(url);if(cc&&now-cc.t<350)return Promise.resolve(cc.r.clone());" +
                "var aa=IF.get(url);if(aa)return aa.then(function(r){return r.clone();});" +
                "var p=OF.apply(this,arguments).then(function(r){var ms=Date.now()-started;if(ms>1400)log('api_slow',ms+'ms '+url);" +
                "try{CA.set(url,{t:Date.now(),r:r.clone()});}catch(e){}return r;},function(e){log('api_error',url+' '+e);throw e;})" +
                ".finally(function(){IF.delete(url);});IF.set(url,p);return p;}" +
                "}catch(e){}" +
                "return OF.apply(this,arguments).then(function(r){var ms=Date.now()-started;if(ms>1800)log('fetch_slow',ms+'ms '+url);return r;});};}" +
                "function patchLeaflet(){try{if(!window.L||!L.Polyline||L.Polyline.prototype.__ppThin)return;" +
                "var orig=L.Polyline.prototype.setLatLngs;L.Polyline.prototype.setLatLngs=function(a){try{a=thinTo(a,900);}catch(e){}return orig.call(this,a);};" +
                "L.Polyline.prototype.__ppThin=1;}catch(e){}}" +
                "function patchDetails(){try{" +
                "if(typeof window.speedChart==='function'&&!window.speedChart.__ppLite){var SC=window.speedChart;" +
                "var SW=function(points){var p=thinTo(points,240);if(points&&points.length>240)log('detail_opt','speedChart '+points.length+'->'+p.length);return SC.call(this,p);};SW.__ppLite=1;window.speedChart=SW;}" +
                "if(typeof window.drawRide==='function'&&!window.drawRide.__ppPerf){var DR=window.drawRide;" +
                "var DW=function(id,ride,fit){try{var rr=ride;if(ride&&Array.isArray(ride.points)&&ride.points.length>700){" +
                "rr=Object.assign({},ride,{points:thinTo(ride.points,700)});log('detail_opt','drawRide '+ride.points.length+'->700 id='+id);}" +
                "return DR.call(this,id,rr,fit);}catch(e){log('detail_error','drawRide '+e);return DR.apply(this,arguments);}};" +
                "DW.__ppPerf=1;window.drawRide=DW;}" +
                "if(typeof window.openRide==='function'&&!window.openRide.__ppPerf){var OR=window.openRide;" +
                "var OW=function(){var self=this,args=arguments;if(window.__PP_OPEN_RIDE_BUSY__){log('detail_busy','duplicate openRide blocked');return window.__PP_OPEN_RIDE_BUSY__;}" +
                "var started=Date.now();var task=Promise.resolve().then(function(){return OR.apply(self,args);})" +
                ".then(function(v){var ms=Date.now()-started;if(ms>1200)log('detail_slow','openRide '+ms+'ms id='+String(args[0]));return v;})" +
                ".catch(function(e){log('detail_error','openRide '+e);throw e;})" +
                ".finally(function(){setTimeout(function(){if(window.__PP_OPEN_RIDE_BUSY__===task)window.__PP_OPEN_RIDE_BUSY__=null;},120);});" +
                "window.__PP_OPEN_RIDE_BUSY__=task;return task;};OW.__ppPerf=1;window.openRide=OW;}" +
                "}catch(e){log('detail_patch_error',e);}}" +
                "patchLeaflet();patchDetails();setInterval(function(){patchLeaflet();patchDetails();},1000);" +
                "}catch(e){try{AndroidBridge.diagnosticLog('perf_guard_error',String(e));}catch(x){}}})();";
        try { view.evaluateJavascript(js, null); } catch (Throwable e) {
            DiagnosticLogger.log(this, "perf_guard", "inject failed", e);
        }
    }

    private void installRouteLazyGuard(WebView view) {
        if (view == null) return;
        String js =
                "(function(){try{" +
                "if(window.__PP_ROUTE_LAZY__)return;window.__PP_ROUTE_LAZY__=1;" +
                "function cardOf(el){while(el&&el!==document.body){var id=(el.id||'').toLowerCase(),cl=String(el.className||'').toLowerCase();" +
                "if(el.dataset&&(el.dataset.rideId||el.dataset.routeId))return el;if(/ride-card|route-card|activity-card|ride-row|route-row/.test(cl+' '+id))return el;el=el.parentElement;}return null;}" +
                "function enhanceHistory(){try{" +
                "var cards=[].slice.call(document.querySelectorAll('[data-ride-id],[data-route-id],.ride-card,.route-card,.activity-card,.ride-row,.route-row'));" +
                "if(cards.length<2)return;" +
                "if(!document.getElementById('pp-route-date-filter')){" +
                "var host=document.querySelector('[data-route-history],.route-history,.rides-history,.ride-history')||cards[0].parentElement;" +
                "if(host){var box=document.createElement('div');box.id='pp-route-date-filter';box.style.cssText='display:flex;gap:8px;align-items:center;margin:10px 0;direction:rtl';" +
                "box.innerHTML='<input type=\"text\" inputmode=\"numeric\" placeholder=\"جستجوی تاریخ\" id=\"pp-route-date\" style=\"min-height:42px;border:1px solid #ccd3da;border-radius:12px;padding:6px 10px;background:#fff;color:#111\"><button type=\"button\" id=\"pp-route-date-clear\" style=\"min-height:42px;border:0;border-radius:12px;padding:6px 12px\">همه تاریخ‌ها</button>';" +
                "host.insertBefore(box,host.firstChild);var inp=box.querySelector('#pp-route-date');var clear=box.querySelector('#pp-route-date-clear');" +
                "function nd(s){return String(s||'').replace(/[۰-۹]/g,function(ch){return '۰۱۲۳۴۵۶۷۸۹'.indexOf(ch);}).replace(/[٠-٩]/g,function(ch){return '٠١٢٣٤٥٦٧٨٩'.indexOf(ch);}).replace(/[-.]/g,'/').replace(/\\s+/g,'');}" +
                "function apply(){var v=nd(inp.value);cards.forEach(function(card){if(!v){card.style.removeProperty('display');return;}" +
                "var t=nd(card.getAttribute('data-date')||card.getAttribute('data-created-at')||card.innerText||'');card.style.display=t.indexOf(v)>=0?'':'none';});}" +
                "inp.onchange=apply;clear.onclick=function(){inp.value='';apply();};}}" +
                "var role=((document.body&&document.body.getAttribute('data-role'))||'').toLowerCase();" +
                "var admin=(role==='admin'||role==='administrator'||document.body.classList.contains('admin')||document.documentElement.classList.contains('admin'));" +
                "if(admin){cards.forEach(function(card){if(card.querySelector('.pp-admin-delete'))return;var id=card.getAttribute('data-ride-id')||card.getAttribute('data-route-id');if(!id)return;" +
                "if(typeof window.deleteRide!=='function'&&typeof window.deleteRoute!=='function')return;var b=document.createElement('button');b.type='button';b.className='pp-admin-delete';b.textContent='حذف';" +
                "b.style.cssText='margin-inline-start:8px;border:0;border-radius:10px;padding:7px 12px;background:#b42318;color:#fff;font-weight:800';" +
                "b.onclick=function(ev){ev.preventDefault();ev.stopPropagation();if(!confirm('این مسیر حذف شود؟'))return;var fn=typeof window.deleteRide==='function'?window.deleteRide:window.deleteRoute;Promise.resolve(fn(id)).then(function(){card.remove();}).catch(function(){});};card.appendChild(b);});}" +
                "}catch(e){}}" +
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
                "patch();enhanceHistory();setInterval(function(){patch();enhanceHistory();},1200);" +
                "}catch(e){}})();";
        try { view.evaluateJavascript(js, null); } catch (Throwable ignored) { }
    }

    private void captureNeshanKey(WebView view) {
        if (view == null) return;
        String js = "(function(){try{" +
                "var k=window.NESHAN_API_KEY||window.NESHAN_SERVICE_KEY||window.PedalProNeshanServiceKey||" +
                "localStorage.getItem('pedalpro_neshan_service_key')||'';" +
                "if(k&&window.AndroidBridge&&AndroidBridge.setNeshanServiceKey)AndroidBridge.setNeshanServiceKey(String(k));" +
                "}catch(e){}})();";
        try { view.evaluateJavascript(js, null); } catch (Throwable e) {
            DiagnosticLogger.log(this, "neshan_error", "capture key failed", e);
        }
    }

    private void injectNeshanKey(WebView view) {
        if (view == null) return;
        String key = getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE)
                .getString("neshan_service_key", "");
        if (!NeshanRoadMatcher.validKey(key)) {
            DiagnosticLogger.log(this, "neshan", "native key missing");
            return;
        }
        String q = JSONObject.quote(key);
        String js = "try{" +
                "window.PedalProNeshanServiceKey=" + q + ";" +
                "window.NESHAN_API_KEY=" + q + ";" +
                "localStorage.setItem('pedalpro_neshan_service_key'," + q + ");" +
                "window.dispatchEvent(new CustomEvent('pedalpro:neshan-ready',{detail:{key:" + q + "}}));" +
                "}catch(e){}";
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
            runOnUiThread(MainActivity.this::openLowPowerRide);
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
        @JavascriptInterface public String neshanServiceKey() {
            String key = getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE).getString("neshan_service_key", "");
            return NeshanRoadMatcher.validKey(key) ? key : "";
        }
        @JavascriptInterface public void setNeshanServiceKey(String key) {
            if (!NeshanRoadMatcher.validKey(key)) return;
            getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE)
                    .edit().putString("neshan_service_key", key.trim()).apply();
            DiagnosticLogger.log(MainActivity.this, "neshan", "web key captured");
        }
        @JavascriptInterface public void openDiagnostics() {
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, DiagnosticActivity.class)));
        }
        @JavascriptInterface public void diagnosticLog(String category, String message) {
            DiagnosticLogger.log(MainActivity.this, category, message);
        }
        @JavascriptInterface public String ridePostProcessSummary(int rideId) {
            return RidePostProcessor.summaryJson(MainActivity.this, rideId);
        }
        @JavascriptInterface public void recalculateRide(int rideId) {
            if (rideId <= 0) return;
            DiagnosticLogger.log(MainActivity.this, "postprocess", "manual recalculate ride=" + rideId);
            RidePostProcessor.processAsync(getApplicationContext(), rideId, result -> {
                if (result == null || webView == null) return;
                String payload = result.toJson().toString();
                webView.post(() -> {
                    String js = "try{window.dispatchEvent(new CustomEvent('pedalpro:ride-recalculated',{detail:" +
                            payload + "}));}catch(e){}";
                    webView.evaluateJavascript(js, null);
                });
            });
        }
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
                neshanKey = getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE).getString("neshan_service_key", "");
            }
            if (NeshanRoadMatcher.validKey(neshanKey)) i.putExtra("neshan_service_key", neshanKey);
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

    private void openLowPowerRide() {
        try {
            boolean active = getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE)
                    .getBoolean("active", false);
            DiagnosticLogger.log(this, "low_power", "open requested active=" + active);
            lowPowerModeRequested = true;
            if (webView != null) {
                try { webView.onPause(); } catch (Throwable ignored) { }
                try { webView.pauseTimers(); } catch (Throwable ignored) { }
            }
            Intent i = new Intent(this, LowPowerRideActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
            overridePendingTransition(0, 0);
        } catch (Throwable e) {
            lowPowerModeRequested = false;
            if (webView != null) {
                try { webView.resumeTimers(); webView.onResume(); } catch (Throwable ignored) { }
            }
            DiagnosticLogger.log(this, "low_power_error", "open failed", e);
            Toast.makeText(this, "حالت کم‌مصرف باز نشد", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean handleUri(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if ("pedalpro".equals(scheme) && ("low-power".equals(host) || "lowpower".equals(host))) {
            openLowPowerRide();
            return true;
        }
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
        if (webView != null) {
            try { webView.resumeTimers(); } catch (Throwable ignored) { }
            try { webView.onResume(); } catch (Throwable ignored) { }
        }
        if (lowPowerModeRequested) {
            DiagnosticLogger.log(this, "low_power", "returned to main WebView");
            lowPowerModeRequested = false;
        }
        NotificationJobService.fetchNow(getApplicationContext());
        FirebaseConfigManager.sync(getApplicationContext());
    }

    @Override protected void onPause() {
        if (webView != null) {
            try { webView.onPause(); } catch (Throwable ignored) { }
            if (lowPowerModeRequested) {
                try { webView.pauseTimers(); } catch (Throwable ignored) { }
            }
        }
        super.onPause();
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
