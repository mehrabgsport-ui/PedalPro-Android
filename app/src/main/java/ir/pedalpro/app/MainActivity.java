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
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
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
import android.view.WindowManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME = "https://pedalpro.ir/";
    private static final int REQ_LOCATION = 1101;
    private static final int REQ_CAMERA = 1102;
    private static final int REQ_FILE = 1103;
    private static final int REQ_NOTIFICATIONS = 1104;
    private static final int REQ_AUDIO = 1105;
    private static final String WALKIE_LOG_TAG = "PedalProWalkie";

    private static final String WALKIE_DIAGNOSTICS_JS = """
(function(){
  try {
    if (window.__PP_WALKIE_PROBE_INSTALLED__) return;
    window.__PP_WALKIE_PROBE_INSTALLED__ = true;

    const MAX_BODY = 1200;
    const probeVersion = "1.4.12-audio-source-fix";

    function errInfo(e) {
      if (!e) return null;
      return {
        name: String(e.name || ""),
        message: String(e.message || e),
        stack: String(e.stack || "").split("\\n").slice(0, 8).join(" | ")
      };
    }

    function emit(stage, data) {
      const payload = {
        t: new Date().toISOString(),
        stage: stage,
        page: location.href,
        data: data == null ? null : data
      };
      let encoded;
      try { encoded = JSON.stringify(payload); }
      catch (e) { encoded = JSON.stringify({t:new Date().toISOString(), stage:stage, data:{encodeError:String(e)}}); }
      try { console.log("[PP_WALKIE] " + encoded); } catch (_) {}
      try {
        if (window.AndroidBridge && typeof window.AndroidBridge.walkieDebug === "function") {
          window.AndroidBridge.walkieDebug(encoded);
        }
      } catch (_) {}
    }

    function targetInfo(t) {
      if (!t) return null;
      let text = "";
      try { text = String((t.innerText || t.textContent || "")).trim().replace(/\\s+/g, " ").slice(0, 100); } catch (_) {}
      return {
        tag: String(t.tagName || ""),
        id: String(t.id || ""),
        cls: String(t.className || "").slice(0, 160),
        role: String((t.getAttribute && t.getAttribute("role")) || ""),
        aria: String((t.getAttribute && t.getAttribute("aria-label")) || ""),
        title: String((t.getAttribute && t.getAttribute("title")) || ""),
        text: text
      };
    }

    window.__PP_WALKIE_DEBUG__ = { emit: emit, version: probeVersion };
    emit("PROBE_READY", {
      version: probeVersion,
      secureContext: !!window.isSecureContext,
      hasMediaDevices: !!(navigator.mediaDevices),
      hasGetUserMedia: !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia),
      hasMediaRecorder: !!window.MediaRecorder,
      visibility: document.visibilityState,
      ua: navigator.userAgent
    });

    ["pointerdown","pointerup","pointercancel","touchstart","touchend","touchcancel","mousedown","mouseup"].forEach(function(name){
      document.addEventListener(name, function(e){
        const d = { target: targetInfo(e.target), trusted: !!e.isTrusted, defaultPrevented: !!e.defaultPrevented };
        if ("pointerId" in e) {
          d.pointerId = e.pointerId;
          d.pointerType = e.pointerType;
          d.buttons = e.buttons;
          d.pressure = e.pressure;
        }
        if (e.changedTouches) d.changedTouches = e.changedTouches.length;
        emit("INPUT_" + name.toUpperCase(), d);
      }, {capture:true, passive:true});
    });

    document.addEventListener("visibilitychange", function(){
      emit("PAGE_VISIBILITY", {visibility: document.visibilityState, hidden: document.hidden});
    }, true);
    window.addEventListener("pagehide", function(e){ emit("PAGE_HIDE", {persisted:!!e.persisted}); }, true);
    window.addEventListener("pageshow", function(e){ emit("PAGE_SHOW", {persisted:!!e.persisted}); }, true);

    if (navigator.mediaDevices && typeof navigator.mediaDevices.getUserMedia === "function") {
      try {
        const originalGum = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
        navigator.mediaDevices.getUserMedia = function(constraints) {
          emit("GUM_REQUEST", {constraints: constraints});
          try {
            if (window.AndroidBridge && typeof window.AndroidBridge.prepareWalkieAudio === "function") {
              window.AndroidBridge.prepareWalkieAudio();
              emit("ANDROID_AUDIO_PREPARE_CALL", {});
            }
          } catch (e) {
            emit("ANDROID_AUDIO_PREPARE_FAIL", errInfo(e));
          }
          let p;
          try { p = originalGum(constraints); }
          catch (e) {
            try {
              if (window.AndroidBridge && typeof window.AndroidBridge.releaseWalkieAudio === "function") {
                window.AndroidBridge.releaseWalkieAudio();
              }
            } catch (_) {}
            emit("GUM_THROW", errInfo(e));
            throw e;
          }
          return p.then(function(stream){
            let tracks = [];
            try {
              tracks = stream.getTracks().map(function(t){
                let settings = {};
                try { settings = t.getSettings ? t.getSettings() : {}; } catch (_) {}
                return {kind:t.kind, label:t.label, enabled:t.enabled, muted:t.muted, readyState:t.readyState, settings:settings};
              });
            } catch (_) {}
            emit("GUM_OK", {active:stream.active, tracks:tracks});
            return stream;
          }, function(e){
            try {
              if (window.AndroidBridge && typeof window.AndroidBridge.releaseWalkieAudio === "function") {
                window.AndroidBridge.releaseWalkieAudio();
              }
            } catch (_) {}
            emit("GUM_FAIL", errInfo(e));
            throw e;
          });
        };
      } catch (e) {
        emit("GUM_PATCH_FAIL", errInfo(e));
      }
    }

    if (window.MediaRecorder && window.MediaRecorder.prototype) {
      try {
        const MR = window.MediaRecorder;
        const hooked = new WeakSet();
        function hookRecorder(rec) {
          if (hooked.has(rec)) return;
          hooked.add(rec);
          ["start","pause","resume"].forEach(function(name){
            rec.addEventListener(name, function(){ emit("REC_EVENT_" + name.toUpperCase(), {state:rec.state, mimeType:rec.mimeType}); });
          });
          rec.addEventListener("stop", function(){
            emit("REC_EVENT_STOP", {state:rec.state, mimeType:rec.mimeType});
            try {
              if (window.AndroidBridge && typeof window.AndroidBridge.releaseWalkieAudio === "function") {
                window.AndroidBridge.releaseWalkieAudio();
                emit("ANDROID_AUDIO_RELEASE_CALL", {reason:"recorder-stop"});
              }
            } catch (_) {}
          });
          rec.addEventListener("dataavailable", function(e){
            emit("REC_DATA", {state:rec.state, size:e.data ? e.data.size : 0, type:e.data ? e.data.type : ""});
          });
          rec.addEventListener("error", function(e){
            emit("REC_ERROR", errInfo(e && (e.error || e)));
          });
        }

        const originalStart = MR.prototype.start;
        MR.prototype.start = function(timeslice) {
          hookRecorder(this);
          emit("REC_START_CALL", {state:this.state, mimeType:this.mimeType, timeslice:timeslice == null ? null : timeslice});
          try {
            const out = originalStart.apply(this, arguments);
            this.__ppStartAt = Date.now();
            emit("REC_START_RETURN", {state:this.state});
            return out;
          } catch (e) {
            emit("REC_START_THROW", errInfo(e));
            throw e;
          }
        };

        const originalStop = MR.prototype.stop;
        MR.prototype.stop = function() {
          hookRecorder(this);
          const durationMs = this.__ppStartAt ? Math.max(0, Date.now() - this.__ppStartAt) : null;
          emit("REC_STOP_CALL", {state:this.state, durationMs:durationMs});
          if (durationMs != null && durationMs < 4500) {
            emit("REC_STOP_EARLY", {durationMs:durationMs, state:this.state});
          }
          try {
            const out = originalStop.apply(this, arguments);
            emit("REC_STOP_RETURN", {state:this.state, durationMs:durationMs});
            return out;
          } catch (e) {
            emit("REC_STOP_THROW", errInfo(e));
            throw e;
          }
        };
      } catch (e) {
        emit("REC_PATCH_FAIL", errInfo(e));
      }
    }

    function inspectBody(body) {
      const out = {type: body == null ? "none" : (body.constructor && body.constructor.name) || typeof body, action:""};
      try {
        if (window.FormData && body instanceof FormData) {
          const keys = [];
          body.forEach(function(v,k){
            keys.push(k);
            if (!out.action && String(k).toLowerCase() === "action") out.action = String(v);
            if (v && typeof Blob !== "undefined" && v instanceof Blob) {
              out[k] = {blobSize:v.size, blobType:v.type};
            } else if (String(k).toLowerCase() !== "csrf") {
              out[k] = String(v).slice(0, 160);
            }
          });
          out.keys = keys;
        } else if (window.URLSearchParams && body instanceof URLSearchParams) {
          out.action = String(body.get("action") || "");
          out.text = body.toString().slice(0, 500);
        } else if (typeof body === "string") {
          out.text = body.slice(0, 500);
          try { out.action = String(new URLSearchParams(body).get("action") || ""); } catch (_) {}
          if (!out.action) {
            try { const j = JSON.parse(body); out.action = String(j.action || ""); } catch (_) {}
          }
        } else if (typeof Blob !== "undefined" && body instanceof Blob) {
          out.blobSize = body.size;
          out.blobType = body.type;
        }
      } catch (e) {
        out.inspectError = String(e);
      }
      return out;
    }

    function requestMeta(input, init) {
      let url = "";
      let method = "GET";
      try {
        if (typeof input === "string") url = input;
        else if (input && input.url) { url = input.url; method = input.method || method; }
      } catch (_) {}
      if (init && init.method) method = init.method;
      const bodyInfo = inspectBody(init && init.body);
      const hay = (url + " " + (bodyInfo.action || "") + " " + (bodyInfo.text || "")).toLowerCase();
      return {
        interesting: hay.indexOf("walkie") >= 0,
        url:url,
        method:String(method || "GET").toUpperCase(),
        body:bodyInfo,
        stack:String((new Error()).stack || "").split("\\n").slice(2,7).join(" | ")
      };
    }

    if (typeof window.fetch === "function") {
      const originalFetch = window.fetch;
      window.fetch = function(input, init) {
        const meta = requestMeta(input, init);
        if (meta.interesting) emit("FETCH_REQUEST", meta);
        let p;
        try { p = originalFetch.apply(this, arguments); }
        catch (e) {
          if (meta.interesting) emit("FETCH_THROW", {request:meta, error:errInfo(e)});
          throw e;
        }
        if (!meta.interesting) return p;
        return p.then(function(resp){
          emit("FETCH_RESPONSE", {url:meta.url, status:resp.status, ok:resp.ok, redirected:resp.redirected, type:resp.type});
          if (!resp.ok) emit("FETCH_HTTP_FAIL", {url:meta.url, status:resp.status});
          try {
            resp.clone().text().then(function(t){
              const bodyText = String(t);
              emit("FETCH_BODY", {url:meta.url, status:resp.status, body:bodyText.slice(0, MAX_BODY)});
              try {
                JSON.parse(bodyText);
                emit("FETCH_JSON_OK", {url:meta.url, status:resp.status});
              } catch (e) {
                emit("FETCH_JSON_INVALID", {url:meta.url, status:resp.status, error:errInfo(e), body:bodyText.slice(0, 500)});
              }
            }).catch(function(e){ emit("FETCH_BODY_FAIL", errInfo(e)); });
          } catch (e) { emit("FETCH_BODY_FAIL", errInfo(e)); }
          return resp;
        }, function(e){
          emit("FETCH_ERROR", {request:meta, error:errInfo(e)});
          throw e;
        });
      };
    }

    if (window.XMLHttpRequest && window.XMLHttpRequest.prototype) {
      try {
        const XP = window.XMLHttpRequest.prototype;
        const originalOpen = XP.open;
        const originalSend = XP.send;

        XP.open = function(method, url) {
          this.__ppMethod = String(method || "GET").toUpperCase();
          this.__ppUrl = String(url || "");
          return originalOpen.apply(this, arguments);
        };

        XP.send = function(body) {
          const bodyInfo = inspectBody(body);
          const hay = (String(this.__ppUrl || "") + " " + (bodyInfo.action || "") + " " + (bodyInfo.text || "")).toLowerCase();
          this.__ppWalkie = hay.indexOf("walkie") >= 0;
          if (this.__ppWalkie) {
            emit("XHR_REQUEST", {
              url:this.__ppUrl,
              method:this.__ppMethod,
              body:bodyInfo,
              stack:String((new Error()).stack || "").split("\\n").slice(2,7).join(" | ")
            });
            if (!this.__ppListeners) {
              this.__ppListeners = true;
              this.addEventListener("loadend", function(){
                let response = "";
                try {
                  if (!this.responseType || this.responseType === "text") response = String(this.responseText || "").slice(0, MAX_BODY);
                } catch (_) {}
                emit("XHR_RESPONSE", {url:this.__ppUrl, method:this.__ppMethod, status:this.status, responseType:this.responseType, body:response});
                if (this.status < 200 || this.status >= 300) {
                  emit("XHR_HTTP_FAIL", {url:this.__ppUrl, method:this.__ppMethod, status:this.status, body:response.slice(0, 500)});
                }
                if (response) {
                  try {
                    JSON.parse(response);
                    emit("XHR_JSON_OK", {url:this.__ppUrl, status:this.status});
                  } catch (e) {
                    emit("XHR_JSON_INVALID", {url:this.__ppUrl, status:this.status, error:errInfo(e), body:response.slice(0, 500)});
                  }
                }
              });
              this.addEventListener("error", function(){ emit("XHR_ERROR", {url:this.__ppUrl, status:this.status}); });
              this.addEventListener("timeout", function(){ emit("XHR_TIMEOUT", {url:this.__ppUrl, status:this.status}); });
              this.addEventListener("abort", function(){ emit("XHR_ABORT", {url:this.__ppUrl, status:this.status}); });
            }
          }
          return originalSend.apply(this, arguments);
        };
      } catch (e) {
        emit("XHR_PATCH_FAIL", errInfo(e));
      }
    }

    window.addEventListener("error", function(e){
      emit("JS_ERROR", {message:String(e.message || ""), file:String(e.filename || ""), line:e.lineno || 0, col:e.colno || 0, error:errInfo(e.error)});
    }, true);
    window.addEventListener("unhandledrejection", function(e){
      emit("UNHANDLED_REJECTION", errInfo(e.reason));
    }, true);

    let reconnectShown = false;
    let observerTimer = 0;
    function scanReconnect() {
      observerTimer = 0;
      let text = "";
      try { text = document.body ? String(document.body.innerText || "") : ""; } catch (_) {}
      const now = text.indexOf("در حال اتصال مجدد واکی") >= 0 ||
                  text.indexOf("اتصال مجدد واکی") >= 0 ||
                  /walkie.{0,40}reconnect|reconnect.{0,40}walkie/i.test(text);
      if (now !== reconnectShown) {
        reconnectShown = now;
        emit(now ? "UI_RECONNECT_SHOWN" : "UI_RECONNECT_HIDDEN", {});
      }
    }
    if (document.body && window.MutationObserver) {
      new MutationObserver(function(){
        if (!observerTimer) observerTimer = setTimeout(scanReconnect, 80);
      }).observe(document.body, {subtree:true, childList:true, characterData:true, attributes:true});
      scanReconnect();
    }
  } catch (fatal) {
    try {
      const encoded = JSON.stringify({t:new Date().toISOString(),stage:"PROBE_FATAL",data:{name:fatal && fatal.name,message:String(fatal && (fatal.message || fatal))}});
      console.log("[PP_WALKIE] " + encoded);
      if (window.AndroidBridge && typeof window.AndroidBridge.walkieDebug === "function") window.AndroidBridge.walkieDebug(encoded);
    } catch (_) {}
  }
})();
""";

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private PermissionRequest pendingWebPermission;
    private String pendingTrackingJson;
    private volatile boolean updateCheckRunning = false;
    private long lastUpdateCheckMs = 0L;
    private android.app.AlertDialog forcedUpdateDialog;
    private android.app.AlertDialog walkieDebugDialog;
    private final ArrayDeque<String> walkieRecentLogs = new ArrayDeque<>();
    private long lastWalkieDebugDialogMs = 0L;
    private AudioManager walkieAudioManager;
    private boolean walkieAudioModeOwned = false;
    private int walkieAudioPreviousMode = AudioManager.MODE_NORMAL;
    private final android.os.Handler walkieAudioHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable walkieAudioReleaseRunnable = () -> releaseWalkieAudioMode("timeout");
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
                if (isPedalProOrigin(url)) {
                    walkieLog("PAGE_FINISHED", url);
                    view.evaluateJavascript(WALKIE_DIAGNOSTICS_JS, value -> walkieLog("PROBE_INJECTED", String.valueOf(value)));
                }
                NotificationJobService.fetchNow(getApplicationContext());
                FirebaseConfigManager.sync(getApplicationContext());
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    String url = request.getUrl() == null ? "" : request.getUrl().toString();
                    if (isWalkieUrl(url)) walkieLog("NATIVE_NET_REQUEST", request.getMethod() + " " + url);
                } catch (Throwable ignored) { }
                return super.shouldInterceptRequest(view, request);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                try {
                    String url = request.getUrl() == null ? "" : request.getUrl().toString();
                    if (request.isForMainFrame() || isWalkieUrl(url)) {
                        walkieLog("WEB_RESOURCE_ERROR", "code=" + error.getErrorCode() + " url=" + url + " desc=" + error.getDescription());
                    }
                } catch (Throwable ignored) { }
                super.onReceivedError(view, request, error);
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                try {
                    String url = request.getUrl() == null ? "" : request.getUrl().toString();
                    if (isWalkieUrl(url)) {
                        walkieLog("WEB_HTTP_ERROR", "status=" + errorResponse.getStatusCode() + " url=" + url);
                    }
                } catch (Throwable ignored) { }
                super.onReceivedHttpError(view, request, errorResponse);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                try {
                    String message = consoleMessage == null ? "" : consoleMessage.message();
                    if (message != null && message.contains("[PP_WALKIE]")) {
                        walkieLog("JS_CONSOLE", message);
                    }
                } catch (Throwable ignored) { }
                return super.onConsoleMessage(consoleMessage);
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
                    try {
                        String origin = request.getOrigin() == null ? "" : request.getOrigin().toString();
                        String resources = java.util.Arrays.toString(request.getResources());
                        walkieLog("WEB_PERMISSION_REQUEST", "origin=" + origin + " resources=" + resources +
                                " androidAudioGranted=" + (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED));

                        if (request.getOrigin() == null || !isPedalProOrigin(origin)) {
                            walkieLog("WEB_PERMISSION_DENY_ORIGIN", origin);
                            request.deny();
                            return;
                        }
                        boolean needsCamera = false;
                        boolean needsAudio = false;
                        for (String r : request.getResources()) {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) needsCamera = true;
                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) needsAudio = true;
                        }
                        if (needsAudio) {
                            prepareWalkieAudioMode("web-permission");
                        }
                        if (needsAudio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            walkieLog("AUDIO_ANDROID_PERMISSION_PROMPT", origin);
                            pendingWebPermission = request;
                            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
                            return;
                        }
                        if (needsCamera && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            pendingWebPermission = request;
                            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                            return;
                        }
                        request.grant(request.getResources());
                        walkieLog("WEB_PERMISSION_GRANTED", "origin=" + origin + " resources=" + resources);
                    } catch (Throwable e) {
                        walkieLog("WEB_PERMISSION_EXCEPTION", e.getClass().getSimpleName() + ": " + e.getMessage());
                        request.deny();
                    }
                });
            }

            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                String origin = request != null && request.getOrigin() != null ? request.getOrigin().toString() : "";
                walkieLog("WEB_PERMISSION_CANCELED", origin);
                super.onPermissionRequestCanceled(request);
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
        @JavascriptInterface public void prepareWalkieAudio() {
            prepareWalkieAudioMode("js-getUserMedia");
        }
        @JavascriptInterface public void releaseWalkieAudio() {
            releaseWalkieAudioMode("js-release");
        }
        @JavascriptInterface public void walkieDebug(String json) {
            String raw = json == null ? "null" : json;
            String stage = "JS_EVENT";
            try {
                JSONObject o = new JSONObject(raw);
                String jsStage = o.optString("stage", "EVENT");
                if (jsStage != null && !jsStage.trim().isEmpty()) stage = "JS_" + jsStage.trim();
            } catch (Throwable ignored) { }
            walkieLog(stage, raw);
            boolean showReport =
                    "JS_UI_RECONNECT_SHOWN".equals(stage) ||
                    "JS_REC_STOP_EARLY".equals(stage) ||
                    "JS_GUM_FAIL".equals(stage) ||
                    "JS_GUM_THROW".equals(stage) ||
                    "JS_REC_START_THROW".equals(stage) ||
                    "JS_REC_ERROR".equals(stage) ||
                    "JS_FETCH_ERROR".equals(stage) ||
                    "JS_FETCH_THROW".equals(stage) ||
                    "JS_FETCH_HTTP_FAIL".equals(stage) ||
                    "JS_FETCH_JSON_INVALID".equals(stage) ||
                    "JS_XHR_ERROR".equals(stage) ||
                    "JS_XHR_TIMEOUT".equals(stage) ||
                    "JS_XHR_HTTP_FAIL".equals(stage) ||
                    "JS_XHR_JSON_INVALID".equals(stage);
            if (showReport) {
                runOnUiThread(MainActivity.this::showWalkieDebugReport);
            }
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

    private void prepareWalkieAudioMode(String reason) {
        try {
            if (walkieAudioManager == null) {
                walkieAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            }
            if (walkieAudioManager == null) {
                walkieLog("AUDIO_MODE_PREPARE_FAIL", "reason=" + reason + " audioManager=null");
                return;
            }
            walkieAudioHandler.removeCallbacks(walkieAudioReleaseRunnable);
            if (!walkieAudioModeOwned) {
                walkieAudioPreviousMode = walkieAudioManager.getMode();
            }
            int before = walkieAudioManager.getMode();
            walkieAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            int after = walkieAudioManager.getMode();
            walkieAudioModeOwned = true;
            walkieAudioHandler.postDelayed(walkieAudioReleaseRunnable, 8000L);
            walkieLog("AUDIO_MODE_PREPARED", "reason=" + reason + " before=" + before +
                    " after=" + after + " previous=" + walkieAudioPreviousMode);
        } catch (Throwable e) {
            walkieLog("AUDIO_MODE_PREPARE_FAIL", "reason=" + reason + " " +
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void releaseWalkieAudioMode(String reason) {
        try {
            walkieAudioHandler.removeCallbacks(walkieAudioReleaseRunnable);
            if (walkieAudioManager == null || !walkieAudioModeOwned) return;
            int before = walkieAudioManager.getMode();
            if (before == AudioManager.MODE_IN_COMMUNICATION) {
                int restore = walkieAudioPreviousMode;
                if (restore == AudioManager.MODE_INVALID || restore == AudioManager.MODE_CURRENT) {
                    restore = AudioManager.MODE_NORMAL;
                }
                walkieAudioManager.setMode(restore);
            }
            int after = walkieAudioManager.getMode();
            walkieAudioModeOwned = false;
            walkieLog("AUDIO_MODE_RELEASED", "reason=" + reason + " before=" + before + " after=" + after);
        } catch (Throwable e) {
            walkieAudioModeOwned = false;
            walkieLog("AUDIO_MODE_RELEASE_FAIL", "reason=" + reason + " " +
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void walkieLog(String stage, String detail) {
        try {
            String d = detail == null ? "" : detail;
            if (d.length() > 1400) d = d.substring(0, 1400) + "…";
            String line = System.currentTimeMillis() + " [" + stage + "] " + d;
            Log.i(WALKIE_LOG_TAG, line);
            synchronized (walkieRecentLogs) {
                walkieRecentLogs.addLast(line);
                while (walkieRecentLogs.size() > 60) walkieRecentLogs.removeFirst();
            }
        } catch (Throwable ignored) { }
    }

    private String walkieDebugReportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("PedalPro Android ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("Walkie E2E diagnostic\n\n");
        synchronized (walkieRecentLogs) {
            int skip = Math.max(0, walkieRecentLogs.size() - 24);
            int i = 0;
            for (String line : walkieRecentLogs) {
                if (i++ < skip) continue;
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private void showWalkieDebugReport() {
        try {
            long now = System.currentTimeMillis();
            if (now - lastWalkieDebugDialogMs < 2500L) return;
            lastWalkieDebugDialogMs = now;
            if (isFinishing() || isDestroyed()) return;
            if (walkieDebugDialog != null && walkieDebugDialog.isShowing()) return;

            String report = walkieDebugReportText();
            walkieDebugDialog = new android.app.AlertDialog.Builder(this)
                    .setTitle("گزارش تشخیصی واکی‌تاکی")
                    .setMessage(report)
                    .setPositiveButton("کپی گزارش", (d, w) -> {
                        try {
                            android.content.ClipboardManager cm =
                                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("PedalPro Walkie Debug", report));
                                Toast.makeText(this, "گزارش واکی‌تاکی کپی شد", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Throwable ignored) { }
                    })
                    .setNegativeButton("بستن", null)
                    .create();
            walkieDebugDialog.setOnDismissListener(d -> walkieDebugDialog = null);
            walkieDebugDialog.show();
        } catch (Throwable e) {
            walkieLog("DEBUG_DIALOG_ERROR", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private boolean isWalkieUrl(String url) {
        if (url == null) return false;
        String s = url.toLowerCase(Locale.ROOT);
        return s.contains("walkie") || (s.contains("api.php") && s.contains("action=walkie"));
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
        } else if (requestCode == REQ_AUDIO && pendingWebPermission != null) {
            boolean granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            walkieLog("AUDIO_ANDROID_PERMISSION_RESULT", "granted=" + granted);
            if (granted) {
                pendingWebPermission.grant(pendingWebPermission.getResources());
                walkieLog("WEB_PERMISSION_GRANTED_AFTER_PROMPT", java.util.Arrays.toString(pendingWebPermission.getResources()));
            } else {
                pendingWebPermission.deny();
                walkieLog("WEB_PERMISSION_DENIED_AFTER_PROMPT", "");
            }
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
        if (walkieDebugDialog != null) {
            try { walkieDebugDialog.dismiss(); } catch (Throwable ignored) { }
            walkieDebugDialog = null;
        }
        releaseWalkieAudioMode("activity-destroy");
        if (webView != null) {
            webView.stopLoading(); webView.setWebChromeClient(null); webView.setWebViewClient(null); webView.destroy();
        }
        super.onDestroy();
    }
}
