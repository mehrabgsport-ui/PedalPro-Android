package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.pedalpro.app.R;
import ir.pedalpro.app.TrackingService;
import ir.pedalpro.app.CyclingSensorManager;
import ir.pedalpro.app.HeartRateBleManager;

/**
 * PedalPro 2.0 native launcher.
 * No WebView is created or loaded in this Activity.
 */
public final class NativeMainActivity extends Activity {
    private static final int REQ_LOCATION = 2201;
    private static final int REQ_BLE = 2202;
    private static final int REQ_EVENT_PHOTO = 2301;
    private static final int REQ_EVENT_COVER = 2302;
    private static final int REQ_PROFILE_AVATAR = 2303;
    private static final int REQ_BIKE_PHOTO = 2304;

    private static final int BG = Color.rgb(6, 17, 30);
    private static final int CARD = Color.rgb(13, 28, 44);
    private static final int TEXT = Color.rgb(245, 247, 251);
    private static final int MUTED = Color.rgb(159, 178, 200);
    private static final int ACCENT = Color.rgb(46, 230, 166);
    private static final int GREEN = Color.rgb(46, 230, 166);
    private static final int ORANGE = Color.rgb(255, 124, 70);
    private static final int RED = Color.rgb(255, 92, 108);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SessionStore session;
    private PedalProApiClient api;
    private JSONObject bootstrap;
    private JSONObject activeRide;
    private CyclingSensorManager cyclingSensors;
    private HeartRateBleManager heartRateSensors;
    private TextView sensorStatus;
    private TextView cadenceValue;
    private TextView powerValue;
    private TextView heartRateValue;
    private LinearLayout sensorDeviceList;
    private LinearLayout chatMessagesHost;
    private int chatRoomId = 0;
    private int chatLastId = 0;
    private boolean chatLoading = false;
    private final Runnable chatPoll = new Runnable() {
        @Override public void run() {
            if (chatMessagesHost == null || !chatMessagesHost.isAttachedToWindow() || chatRoomId <= 0) return;
            loadGroupChatMessages(false);
            main.postDelayed(this, 4500L);
        }
    };

    private LinearLayout content;
    private TextView topStatus;
    private View headerAdminSettings;
    private PedalActionView headerAccount;
    private TextView rideTimer;
    private TextView rideGps;
    private TextView rideSpeed;
    private TextView rideDistance;
    private View navHome;
    private View navActivities;
    private View navRide;
    private View navRoutes;
    private View navMe;
    private FrameLayout stageRoot;
    private ImageView siteBackground;
    private LinearLayout appRoot;
    private LinearLayout webHeader;
    private TextView rideDockLabel;
    private ScrollView mainScroll;
    private String currentPage = "dashboard";
    private boolean manualPaused = false;
    private boolean serverOnline = false;
    private TextView serverStatusLabel;
    private View serverStatusDot;
    private String launchPage = "";
    private int launchEventId = 0;
    private NativeRouteView activeRideRouteView;
    private int pendingEventPhotoId = 0;
    private android.net.Uri pendingEditorCoverUri;
    private android.net.Uri pendingProfileAvatarUri;
    private android.net.Uri pendingBikePhotoUri;
    private int pendingBikePhotoId;
    private LinearLayout weatherDaysHost;
    private TextView weatherStatus;

    private Location pendingFreshFix;
    private boolean gpsChecking = false;
    private long rideTimerAnchorElapsed = 0L;
    private long rideBaseSeconds = 0L;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (rideTimer != null && activeRide != null) {
                long sec = rideBaseSeconds;
                if (!manualPaused && rideTimerAnchorElapsed > 0L) {
                    sec += Math.max(0L, (SystemClock.elapsedRealtime() - rideTimerAnchorElapsed) / 1000L);
                }
                rideTimer.setText(formatDuration(sec));
            }
            main.postDelayed(this, 1000L);
        }
    };

    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String payload = intent.getStringExtra(TrackingService.EXTRA_PAYLOAD);
            if (payload == null) return;
            try {
                JSONObject j = new JSONObject(payload);
                if (TrackingService.ACTION_STATUS.equals(intent.getAction())) {
                    String error = j.optString("error", "");
                    String message = j.optString("message", "");
                    if (rideGps != null) {
                        rideGps.setText(error.isBlank() ? message : error);
                        rideGps.setTextColor(error.isBlank() ? MUTED : RED);
                    }
                } else if (TrackingService.ACTION_UPDATE.equals(intent.getAction())) {
                    double mps = j.optDouble("speed_mps", 0);
                    double acc = j.optDouble("accuracy", -1);
                    if (rideSpeed != null) rideSpeed.setText(String.format(Locale.US, "%.1f", Math.max(0, mps * 3.6)));
                    if (activeRideRouteView != null) {
                        double lat = j.optDouble("lat", Double.NaN), lng = j.optDouble("lng", Double.NaN);
                        if (Double.isFinite(lat) && Double.isFinite(lng)) activeRideRouteView.setCurrent(lat, lng);
                    }
                    if (rideGps != null) {
                        rideGps.setText(acc > 0 ? "GPS فعال · دقت ±" + Math.round(acc) + " متر" : "GPS فعال");
                        rideGps.setTextColor(GREEN);
                    }
                    String response = j.optString("response", "");
                    if (!response.isBlank()) {
                        try {
                            JSONObject r = new JSONObject(response).optJSONObject("ride");
                            if (r != null) {
                                activeRide = r;
                                if (rideDistance != null) rideDistance.setText(String.format(Locale.US, "%.2f", r.optDouble("distance_km", 0)));
                            }
                        } catch (Exception ignored) { }
                    }
                }
            } catch (Exception ignored) { }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = new SessionStore(this);
        api = new PedalProApiClient(this);
        consumeLaunchIntent(getIntent());
        cyclingSensors = new CyclingSensorManager(this, new CyclingSensorManager.Listener() {
            @Override public void onStatus(String json) { renderCyclingStatus(json); }
            @Override public void onMeasurement(String json) { renderCyclingMeasurement(json); }
        });
        heartRateSensors = new HeartRateBleManager(this, new HeartRateBleManager.Listener() {
            @Override public void onStatus(String json) { renderHeartRateStatus(json); }
            @Override public void onHeartRate(String json) { renderHeartRateMeasurement(json); }
        });
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        renderShell();
        registerTrackingReceiver();
        main.post(clockTick);
        refreshBootstrap(true);
    }

    private void renderShell() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        stageRoot = new FrameLayout(this);
        stageRoot.setBackgroundColor(BG);

        siteBackground = new ImageView(this);
        siteBackground.setImageResource(R.drawable.site_bg_reference);
        siteBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        siteBackground.setAlpha(.94f);
        if (Build.VERSION.SDK_INT >= 31) {
            siteBackground.setRenderEffect(RenderEffect.createBlurEffect(dp(2), dp(2), Shader.TileMode.CLAMP));
        }
        stageRoot.addView(siteBackground, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View shade = new View(this);
        shade.setBackgroundColor(Color.argb(72, 2, 10, 18));
        stageRoot.addView(shade, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        appRoot = new LinearLayout(this);
        appRoot.setOrientation(LinearLayout.VERTICAL);
        appRoot.setBackgroundColor(Color.TRANSPARENT);
        appRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        appRoot.setClipChildren(false);

        ViewCompat.setOnApplyWindowInsetsListener(appRoot, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        appRoot.addView(buildWebHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mainScroll = new ScrollView(this);
        mainScroll.setFillViewport(true);
        mainScroll.setClipToPadding(false);
        mainScroll.setBackgroundColor(Color.TRANSPARENT);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(32));
        content.setClipChildren(false);
        mainScroll.addView(content);
        appRoot.addView(mainScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        appRoot.addView(buildWebBottomNav(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(116)));

        stageRoot.addView(appRoot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams serverLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(36), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        serverLp.bottomMargin = dp(122);
        stageRoot.addView(buildViewportServerStatus(), serverLp);

        setContentView(stageRoot);
        VazirmatnLoader.apply(this, stageRoot);
    }


    private void consumeLaunchIntent(Intent intent) {
        if (intent == null) return;
        launchPage = intent.getStringExtra("page");
        if (launchPage == null) launchPage = "";
        launchEventId = intent.getIntExtra("event_id", 0);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeLaunchIntent(intent);
        if (bootstrap != null) handleLaunchPage();
        else refreshBootstrap(true);
    }

    private void handleLaunchPage() {
        if (launchEventId > 0) {
            JSONObject requested = findEvent(launchEventId);
            if (requested != null) showEventDetail(requested);
            else showActivities();
            launchEventId = 0;
            launchPage = "";
            return;
        }

        String target = launchPage == null ? "" : launchPage;
        launchPage = "";
        switch (target) {
            case "activities": showActivities(); break;
            case "routes": showRoutes(); break;
            case "ride": showRide(); break;
            case "me": showMe(); break;
            case "club": showClub(); break;
            case "challenges": showChallenges(); break;
            case "gallery": showGalleryPage(); break;
            case "chat": showGroupChat(); break;
            case "ai": showAiAdvisor(); break;
            case "performance": showPerformance(); break;
            case "fitness": showFitness(); break;
            case "bikes": showBikes(); break;
            case "profile": showProfileEditor(); break;
            case "phone-settings": showPhoneSettingsPage(); break;
            case "app": showAppVersionPage(); break;
            case "about": showAboutGroupPage(); break;
            case "guide": showGuidePage(); break;
            case "sms": showSmsPage(); break;
            case "login":
                showHome();
                main.postDelayed(() -> showAuthDialog("login"), 120L);
                break;
            case "menu":
                showHome();
                main.postDelayed(this::showDrawer, 120L);
                break;
            case "dashboard":
            default: showHome(); break;
        }
    }

    private void refreshBootstrap(boolean firstLaunch) {
        setTopStatus("در حال دریافت اطلاعات…", MUTED);
        io.execute(() -> {
            try {
                JSONObject d = api.bootstrap();
                bootstrap = d;
                serverOnline = true;
                activeRide = d.optJSONObject("my_active_ride");
                if (activeRide != null) {
                    rideBaseSeconds = activeRide.optLong("duration_seconds", 0);
                    rideTimerAnchorElapsed = SystemClock.elapsedRealtime();
                } else {
                    rideBaseSeconds = 0;
                    rideTimerAnchorElapsed = 0;
                }
                main.post(() -> {
                    updateHeaderAccess(d);
                    applyUiSettings(d);
                    JSONObject user = d.optJSONObject("user");
                    if (user == null) {
                        setTopStatus("ورود به PedalPro", MUTED);
                    } else {
                        setTopStatus("متصل · " + user.optString("display_name", user.optString("username", "PedalPro")), GREEN);
                    }
                    updateServerStatusPill();
                    if (firstLaunch) handleLaunchPage();
                    else refreshVisibleScreen();
                });
            } catch (Exception e) {
                main.post(() -> {
                    serverOnline = false;
                    setTopStatus("خطا در اتصال به سرور", RED);
                    updateServerStatusPill();
                    if (firstLaunch) showError("ارتباط با PedalPro برقرار نشد.", e.getMessage(), () -> refreshBootstrap(true));
                });
            }
        });
    }

    private void showLogin() {
        showAuthDialog("login");
    }

    private void showAuthDialog(String initialTab) {
        if (signedIn()) {
            showMe();
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout stage = new FrameLayout(this);
        stage.setPadding(dp(8), dp(8), dp(8), dp(8));
        stage.setBackgroundColor(Color.TRANSPARENT);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setClipToPadding(false);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(0, dp(10), 0, dp(10));
        scroller.addView(center, new ScrollView.LayoutParams(-1, -1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(15), dp(15), dp(15), dp(15));
        GradientDrawable boxBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(247, 16, 35, 56), Color.argb(247, 10, 27, 43)});
        boxBg.setCornerRadius(dp(17));
        boxBg.setStroke(dp(1), Color.rgb(36, 60, 85));
        box.setBackground(boxBg);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title = text("حساب کاربری", 20, true, TEXT);
        Button close = plainButton("×", 26, TEXT);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        box.addView(head);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tabs.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        tabLp.setMargins(dp(3), dp(4), dp(3), dp(14));

        Button loginTab = authTabButton("ورود");
        Button signupTab = authTabButton("ثبت‌نام");
        Button forgotTab = authTabButton("فراموشی رمز");
        tabs.addView(loginTab, new LinearLayout.LayoutParams(tabLp));
        tabs.addView(signupTab, new LinearLayout.LayoutParams(tabLp));
        tabs.addView(forgotTab, new LinearLayout.LayoutParams(tabLp));
        box.addView(tabs);

        LinearLayout loginPane = new LinearLayout(this);
        loginPane.setOrientation(LinearLayout.VERTICAL);
        EditText username = input("نام کاربری");
        EditText password = input("رمز عبور");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox remember = new CheckBox(this);
        remember.setText("ورود من روی این دستگاه حفظ شود");
        remember.setTextColor(TEXT);
        remember.setChecked(true);
        TextView rememberNote = text("تا ۱۸۰ روز؛ خروج دستی این دسترسی را لغو می‌کند.", 11, false, MUTED);
        Button doLogin = primaryButton("ورود");
        ProgressBar loginProgress = new ProgressBar(this);
        loginProgress.setVisibility(View.GONE);
        loginPane.addView(username);
        loginPane.addView(password);
        loginPane.addView(remember);
        loginPane.addView(rememberNote);
        loginPane.addView(doLogin);
        loginPane.addView(loginProgress);

        LinearLayout signupPane = new LinearLayout(this);
        signupPane.setOrientation(LinearLayout.VERTICAL);
        LinearLayout signupStep1 = new LinearLayout(this);
        signupStep1.setOrientation(LinearLayout.VERTICAL);
        TextView signupNote = text("برای ثبت‌نام، شماره موبایل فقط یک‌بار با پیامک تأیید می‌شود.", 12, false, MUTED);
        EditText signupPhone = input("شماره موبایل");
        signupPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        Button signupSend = primaryButton("ارسال کد تأیید");
        signupStep1.addView(signupNote);
        signupStep1.addView(signupPhone);
        signupStep1.addView(signupSend);

        LinearLayout signupStep2 = new LinearLayout(this);
        signupStep2.setOrientation(LinearLayout.VERTICAL);
        signupStep2.setVisibility(View.GONE);
        EditText signupCode = input("کد ۶ رقمی");
        signupCode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Button signupVerify = primaryButton("تأیید کد");
        signupStep2.addView(signupCode);
        signupStep2.addView(signupVerify);

        LinearLayout signupStep3 = new LinearLayout(this);
        signupStep3.setOrientation(LinearLayout.VERTICAL);
        signupStep3.setVisibility(View.GONE);
        EditText signupName = input("نام نمایشی");
        EditText signupUsername = input("نام کاربری");
        EditText signupPassword = input("رمز عبور");
        signupPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button signupFinish = primaryButton("ساخت حساب");
        signupStep3.addView(signupName);
        signupStep3.addView(signupUsername);
        signupStep3.addView(signupPassword);
        signupStep3.addView(signupFinish);
        signupPane.addView(signupStep1);
        signupPane.addView(signupStep2);
        signupPane.addView(signupStep3);

        LinearLayout forgotPane = new LinearLayout(this);
        forgotPane.setOrientation(LinearLayout.VERTICAL);
        LinearLayout forgotStep1 = new LinearLayout(this);
        forgotStep1.setOrientation(LinearLayout.VERTICAL);
        EditText forgotPhone = input("شماره موبایل تأییدشده");
        forgotPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        Button forgotSend = primaryButton("ارسال کد بازیابی");
        forgotStep1.addView(forgotPhone);
        forgotStep1.addView(forgotSend);

        LinearLayout forgotStep2 = new LinearLayout(this);
        forgotStep2.setOrientation(LinearLayout.VERTICAL);
        forgotStep2.setVisibility(View.GONE);
        EditText forgotCode = input("کد پیامکی");
        forgotCode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Button forgotVerify = primaryButton("تأیید کد");
        forgotStep2.addView(forgotCode);
        forgotStep2.addView(forgotVerify);

        LinearLayout forgotStep3 = new LinearLayout(this);
        forgotStep3.setOrientation(LinearLayout.VERTICAL);
        forgotStep3.setVisibility(View.GONE);
        EditText forgotPassword = input("رمز جدید");
        forgotPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button forgotFinish = primaryButton("ثبت رمز جدید");
        forgotStep3.addView(forgotPassword);
        forgotStep3.addView(forgotFinish);
        forgotPane.addView(forgotStep1);
        forgotPane.addView(forgotStep2);
        forgotPane.addView(forgotStep3);

        box.addView(loginPane);
        box.addView(signupPane);
        box.addView(forgotPane);

        Runnable showLoginPane = () -> {
            loginPane.setVisibility(View.VISIBLE);
            signupPane.setVisibility(View.GONE);
            forgotPane.setVisibility(View.GONE);
            styleAuthTab(loginTab, true);
            styleAuthTab(signupTab, false);
            styleAuthTab(forgotTab, false);
        };
        Runnable showSignupPane = () -> {
            loginPane.setVisibility(View.GONE);
            signupPane.setVisibility(View.VISIBLE);
            forgotPane.setVisibility(View.GONE);
            styleAuthTab(loginTab, false);
            styleAuthTab(signupTab, true);
            styleAuthTab(forgotTab, false);
        };
        Runnable showForgotPane = () -> {
            loginPane.setVisibility(View.GONE);
            signupPane.setVisibility(View.GONE);
            forgotPane.setVisibility(View.VISIBLE);
            styleAuthTab(loginTab, false);
            styleAuthTab(signupTab, false);
            styleAuthTab(forgotTab, true);
        };
        loginTab.setOnClickListener(v -> showLoginPane.run());
        signupTab.setOnClickListener(v -> showSignupPane.run());
        forgotTab.setOnClickListener(v -> showForgotPane.run());

        doLogin.setOnClickListener(v -> {
            String u = username.getText().toString().trim();
            String p = password.getText().toString();
            if (u.isBlank() || p.isBlank()) {
                toast("نام کاربری و رمز عبور را وارد کنید.");
                return;
            }
            hideKeyboard();
            doLogin.setEnabled(false);
            loginProgress.setVisibility(View.VISIBLE);
            io.execute(() -> {
                try {
                    api.login(u, p, remember.isChecked());
                    JSONObject data = api.bootstrap();
                    bootstrap = data;
                    activeRide = data.optJSONObject("my_active_ride");
                    main.post(() -> {
                        doLogin.setEnabled(true);
                        loginProgress.setVisibility(View.GONE);
                        if (data.optJSONObject("user") == null) {
                            toast("ورود تأیید نشد.");
                            return;
                        }
                        updateHeaderAccess(data);
                        applyUiSettings(data);
                        dialog.dismiss();
                        showHome();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        doLogin.setEnabled(true);
                        loginProgress.setVisibility(View.GONE);
                        toast(e.getMessage());
                    });
                }
            });
        });

        signupSend.setOnClickListener(v -> {
            String phone = signupPhone.getText().toString().trim();
            signupSend.setEnabled(false);
            io.execute(() -> {
                try {
                    api.post("auth_signup_send", new JSONObject().put("phone", phone));
                    main.post(() -> {
                        signupSend.setEnabled(true);
                        signupStep1.setVisibility(View.GONE);
                        signupStep2.setVisibility(View.VISIBLE);
                        signupCode.requestFocus();
                        toast("کد تأیید ارسال شد؛ در مرورگرهای پشتیبانی‌شده کد خودکار پیشنهاد/وارد می‌شود");
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        signupSend.setEnabled(true);
                        toast(e.getMessage());
                    });
                }
            });
        });

        signupVerify.setOnClickListener(v -> {
            String phone = signupPhone.getText().toString().trim();
            String code = signupCode.getText().toString().trim();
            signupVerify.setEnabled(false);
            io.execute(() -> {
                try {
                    api.post("auth_signup_verify", new JSONObject().put("phone", phone).put("code", code));
                    main.post(() -> {
                        signupVerify.setEnabled(true);
                        signupStep2.setVisibility(View.GONE);
                        signupStep3.setVisibility(View.VISIBLE);
                        toast("شماره تأیید شد");
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        signupVerify.setEnabled(true);
                        toast(e.getMessage());
                    });
                }
            });
        });

        signupFinish.setOnClickListener(v -> {
            signupFinish.setEnabled(false);
            JSONObject body = new JSONObject();
            try {
                body.put("display_name", signupName.getText().toString());
                body.put("username", signupUsername.getText().toString());
                body.put("password", signupPassword.getText().toString());
            } catch (Exception ignored) {}
            io.execute(() -> {
                try {
                    JSONObject result = api.post("auth_signup_finish", body);
                    JSONObject data = api.bootstrap();
                    bootstrap = data;
                    activeRide = data.optJSONObject("my_active_ride");
                    main.post(() -> {
                        signupFinish.setEnabled(true);
                        updateHeaderAccess(data);
                        applyUiSettings(data);
                        dialog.dismiss();
                        JSONObject invite = result.optJSONObject("bale_invite");
                        String url = invite == null ? "" : invite.optString("url", "");
                        if (!url.isBlank() && invite.optBoolean("enabled", false) && !invite.optBoolean("connected", false)) {
                            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); } catch (Exception ignored) {}
                        }
                        showHome();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        signupFinish.setEnabled(true);
                        toast(e.getMessage());
                    });
                }
            });
        });

        forgotSend.setOnClickListener(v -> {
            forgotSend.setEnabled(false);
            io.execute(() -> {
                try {
                    api.post("auth_reset_send", new JSONObject().put("phone", forgotPhone.getText().toString().trim()));
                    main.post(() -> {
                        forgotSend.setEnabled(true);
                        forgotStep1.setVisibility(View.GONE);
                        forgotStep2.setVisibility(View.VISIBLE);
                        forgotCode.requestFocus();
                        toast("اگر شماره عضو باشد، کد ارسال می‌شود؛ AutoFill فعال است");
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        forgotSend.setEnabled(true);
                        toast(e.getMessage());
                    });
                }
            });
        });

        forgotVerify.setOnClickListener(v -> {
            forgotVerify.setEnabled(false);
            io.execute(() -> {
                try {
                    api.post("auth_reset_verify", new JSONObject()
                            .put("phone", forgotPhone.getText().toString().trim())
                            .put("code", forgotCode.getText().toString().trim()));
                    main.post(() -> {
                        forgotVerify.setEnabled(true);
                        forgotStep2.setVisibility(View.GONE);
                        forgotStep3.setVisibility(View.VISIBLE);
                        toast("کد تأیید شد");
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        forgotVerify.setEnabled(true);
                        toast(e.getMessage());
                    });
                }
            });
        });

        forgotFinish.setOnClickListener(v -> {
            forgotFinish.setEnabled(false);
            io.execute(() -> {
                try {
                    api.post("auth_reset_finish", new JSONObject().put("password", forgotPassword.getText().toString()));
                    main.post(() -> {
                        forgotFinish.setEnabled(true);
                        toast("رمز جدید ثبت شد");
                        forgotStep1.setVisibility(View.VISIBLE);
                        forgotStep2.setVisibility(View.GONE);
                        forgotStep3.setVisibility(View.GONE);
                        showLoginPane.run();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        forgotFinish.setEnabled(true);
                        toast(e.getMessage());
                    });
                }
            });
        });

        if ("signup".equals(initialTab)) showSignupPane.run();
        else if ("forgot".equals(initialTab)) showForgotPane.run();
        else showLoginPane.run();

        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                Math.min(dp(760), getResources().getDisplayMetrics().widthPixels - dp(16)),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        center.addView(box, boxLp);
        stage.addView(scroller, new FrameLayout.LayoutParams(-1, -1));
        dialog.setContentView(stage);
        dialog.show();

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            lp.dimAmount = .91f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        VazirmatnLoader.apply(this, box);
    }

    private Button authTabButton(String label) {
        Button b = plainButton(label, 12, TEXT);
        b.setAllCaps(false);
        return b;
    }

    private void styleAuthTab(Button b, boolean active) {
        b.setTextColor(active ? Color.rgb(5, 40, 55) : TEXT);
        GradientDrawable g;
        if (active) {
            g = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(19, 220, 232), Color.rgb(37, 200, 244)});
        } else {
            g = new GradientDrawable();
            g.setColor(Color.argb(18, 255, 255, 255));
            g.setStroke(dp(1), Color.argb(46, 255, 255, 255));
        }
        g.setCornerRadius(dp(17));
        b.setBackground(g);
    }


    private View buildHomeQuickGrid() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(4), 0, dp(12));

        View[] cards = new View[]{
                homeQuickCard(PedalIconView.Icon.CALENDAR, "برنامه‌ها", "رویدادهای هیجان‌انگیز", this::showActivities),
                homeQuickCard(PedalIconView.Icon.ROUTE, "مسیرها", "نقشه و جزئیات", this::showRoutes),
                homeQuickCard(PedalIconView.Icon.HOME_MEMBERS, "اعضا", "آشنایی با ما", this::showClub),
                homeQuickCard(PedalIconView.Icon.GALLERY, "گالری", "عکس‌ها و ویدیوها", this::showGalleryPage)
        };
        for (int i = 0; i < cards.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(150), 1f);
            if (i > 0) lp.setMargins(dp(8), 0, 0, 0);
            row.addView(cards[i], lp);
        }
        return row;
    }

    private View homeQuickCard(PedalIconView.Icon icon, String title, String subtitle, Runnable run) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(5), dp(14), dp(5), dp(14));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(title);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(41, 23, 54, 77), Color.argb(56, 8, 28, 45)});
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), Color.argb(54, 255, 255, 255));
        card.setBackground(bg);

        PedalIconView iv = new PedalIconView(this, icon, Color.rgb(32, 228, 241));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(64), dp(64));
        card.addView(iv, iconLp);

        TextView name = text(title, 18, true, TEXT);
        name.setGravity(Gravity.CENTER);
        name.setIncludeFontPadding(false);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-2, -2);
        nameLp.setMargins(0, dp(4), 0, 0);
        card.addView(name, nameLp);

        TextView sub = text(subtitle, 10, false, Color.rgb(224, 237, 246));
        sub.setTextSize(10.5f);
        sub.setGravity(Gravity.CENTER);
        sub.setIncludeFontPadding(false);
        sub.setMaxLines(2);
        sub.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(5), 0, 0);
        card.addView(sub, subLp);

        card.setOnClickListener(v -> run.run());
        return card;
    }

    private View buildHomeLiveHub() {
        LinearLayout hub = glassCard(28);
        hub.setPadding(dp(14), dp(14), dp(14), dp(14));

        JSONArray lives = otherVisibleLiveRides();
        int activeCount = (activeRide == null ? 0 : 1) + lives.length();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView liveDot = text("●", 12, false, Color.rgb(52, 231, 177));
        TextView title = text("رکاب‌زنی‌های زنده", 19, true, TEXT);
        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.HORIZONTAL);
        titleWrap.setGravity(Gravity.CENTER_VERTICAL);
        titleWrap.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        titleWrap.addView(liveDot);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.setMargins(dp(8), 0, 0, 0);
        titleWrap.addView(title, titleLp);

        TextView summary = text(JalaliDate.fa(activeCount) + " رکاب‌زنی فعال", 11, false, Color.rgb(199, 217, 231));
        head.addView(titleWrap, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(summary);
        hub.addView(head);

        View mine = homeLiveCard(true, activeRide, lives.length());
        LinearLayout.LayoutParams mineLp = new LinearLayout.LayoutParams(-1, dp(94));
        mineLp.setMargins(0, dp(10), 0, 0);
        hub.addView(mine, mineLp);

        View others = homeLiveCard(false, null, lives.length());
        LinearLayout.LayoutParams othersLp = new LinearLayout.LayoutParams(-1, dp(94));
        othersLp.setMargins(0, dp(10), 0, 0);
        hub.addView(others, othersLp);
        return hub;
    }

    private View homeLiveCard(boolean mine, JSONObject ride, int othersCount) {
        boolean active = mine ? ride != null : othersCount > 0;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setClickable(true);
        card.setFocusable(true);
        card.setAlpha(active ? 1f : .74f);

        int[] colors = mine
                ? new int[]{Color.argb(235, 9, 104, 77), Color.argb(242, 6, 56, 57)}
                : new int[]{Color.argb(240, 139, 77, 12), Color.argb(245, 75, 42, 10)};
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), mine ? Color.argb(122, 58, 238, 174) : Color.argb(143, 255, 177, 67));
        card.setBackground(bg);

        TextView icon = text(mine ? "🚴" : "◉", 26, false, TEXT);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(Color.argb(26, 255, 255, 255), 17, 0));
        card.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.END);

        String name;
        String meta;
        if (mine) {
            if (ride != null) {
                name = ride.optString("title", "").trim();
                if (name.isBlank()) name = "رکاب‌زنی زنده من";
                meta = String.format(Locale.US, "%.2f km · %s · لمس کنید تا برگردید",
                        ride.optDouble("distance_km", 0), formatDuration(ride.optLong("duration_seconds", 0)));
            } else {
                name = "رکاب‌زنی من";
                meta = "فعال نیست · برای شروع لمس کنید";
            }
        } else {
            name = "رکاب‌زنی زنده دیگران";
            JSONArray rides = otherVisibleLiveRides();
            if (othersCount == 0) {
                meta = "فعلاً کسی در حال رکاب‌زنی نیست";
            } else if (othersCount == 1) {
                JSONObject one = rides.optJSONObject(0);
                String riderName = one == null ? "یک عضو" : one.optString("owner_name", one.optString("display_name", "یک عضو"));
                meta = riderName + " در حال رکاب‌زنی است";
            } else {
                meta = JalaliDate.fa(othersCount) + " نفر در حال رکاب‌زنی هستند · مشاهده";
            }
        }

        TextView t = text(name, 16, true, TEXT);
        t.setMaxLines(1);
        TextView m = text(meta, 11, false, Color.rgb(224, 237, 245));
        m.setMaxLines(2);
        m.setLineSpacing(0, 1.7f);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
        metaLp.setMargins(0, dp(6), 0, 0);
        copy.addView(t);
        copy.addView(m, metaLp);

        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1f);
        copyLp.setMargins(dp(11), 0, dp(11), 0);
        card.addView(copy, copyLp);

        if (mine) {
            TextView go = text("‹", 23, false, TEXT);
            go.setGravity(Gravity.CENTER);
            card.addView(go, new LinearLayout.LayoutParams(dp(30), dp(36)));
            card.setOnClickListener(v -> showRide());
        } else {
            TextView count = text(String.valueOf(othersCount), 14, true, TEXT);
            count.setGravity(Gravity.CENTER);
            count.setBackground(roundRect(Color.argb(36, 255, 255, 255), 12, 0));
            card.addView(count, new LinearLayout.LayoutParams(dp(36), dp(36)));
            card.setOnClickListener(v -> showLiveRidesSheet());
        }
        return card;
    }

    private JSONArray otherVisibleLiveRides() {
        JSONArray out = new JSONArray();
        JSONArray rides = bootstrap == null ? null : bootstrap.optJSONArray("rides");
        JSONObject me = bootstrap == null ? null : bootstrap.optJSONObject("user");
        int uid = me == null ? 0 : me.optInt("id", 0);
        boolean admin = bootstrap != null && (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1);
        if (rides == null) return out;
        for (int i = 0; i < rides.length(); i++) {
            JSONObject r = rides.optJSONObject(i);
            if (r == null || !"active".equals(r.optString("status")) || r.optInt("owner_user_id", 0) == uid) continue;
            boolean visible = r.optInt("public_tracking", 0) == 1 || admin || r.optInt("viewer_can_live", 0) == 1;
            if (visible) out.put(r);
        }
        return out;
    }

    private View buildHomeReport() {
        JSONArray events = bootstrap == null ? null : bootstrap.optJSONArray("events");
        int completedEvents = 0;
        int totalEvents = events == null ? 0 : events.length();
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (event != null && "completed".equals(event.optString("status"))) completedEvents++;
            }
        }
        int programs = completedEvents > 0 ? completedEvents : totalEvents;

        JSONArray members = bootstrap == null ? null : bootstrap.optJSONArray("members");
        int memberCount = members == null ? 0 : members.length();
        double totalKm = 0;
        if (members != null) {
            for (int i = 0; i < members.length(); i++) {
                JSONObject member = members.optJSONObject(i);
                if (member != null) totalKm += member.optDouble("total_distance_m", 0) / 1000.0;
            }
        }

        LinearLayout report = new LinearLayout(this);
        report.setOrientation(LinearLayout.HORIZONTAL);
        report.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        report.setGravity(Gravity.CENTER_VERTICAL);
        report.setMinimumHeight(dp(124));
        report.setPadding(dp(8), dp(14), dp(8), dp(14));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(184, 16, 61, 84), Color.argb(184, 10, 43, 64)});
        bg.setCornerRadius(dp(34));
        bg.setStroke(dp(2), Color.argb(56, 255, 255, 255));
        report.setBackground(bg);

        report.addView(homeReportItem(PedalIconView.Icon.REPORT_PROGRAMS,
                JalaliDate.fa(programs), "برنامه برگزار شده"), new LinearLayout.LayoutParams(0, -1, 1f));
        report.addView(homeReportSeparator());
        report.addView(homeReportItem(PedalIconView.Icon.REPORT_MEMBERS,
                JalaliDate.fa(memberCount), "عضو فعال"), new LinearLayout.LayoutParams(0, -1, 1f));
        report.addView(homeReportSeparator());
        report.addView(homeReportItem(PedalIconView.Icon.REPORT_DISTANCE,
                JalaliDate.fa(Math.round(totalKm)), "کیلومتر رکاب زده"), new LinearLayout.LayoutParams(0, -1, 1f));
        return report;
    }

    private View homeReportItem(PedalIconView.Icon icon, String value, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(7), dp(6), dp(7), dp(6));

        PedalIconView iv = new PedalIconView(this, icon, Color.rgb(19, 231, 239));
        item.addView(iv, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.END);
        copy.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView number = text(value, 26, true, TEXT);
        number.setIncludeFontPadding(false);
        TextView caption = text(label, 11, true, Color.rgb(237, 245, 250));
        caption.setIncludeFontPadding(false);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(-2, -2);
        captionLp.setMargins(0, dp(7), 0, 0);
        copy.addView(number);
        copy.addView(caption, captionLp);

        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(-2, -2);
        copyLp.setMargins(dp(7), 0, 0, 0);
        item.addView(copy, copyLp);
        return item;
    }

    private View homeReportSeparator() {
        View line = new View(this);
        line.setBackgroundColor(Color.argb(43, 255, 255, 255));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), dp(72));
        line.setLayoutParams(lp);
        return line;
    }

    private View buildHomeTopRiders() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(16), dp(12), dp(16));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(61,24,62,86),Color.argb(77,9,29,45)});
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1),Color.argb(46,255,255,255));
        box.setBackground(bg);

        java.util.ArrayList<JSONObject> rows = new java.util.ArrayList<>();
        JSONArray members = bootstrap == null ? null : bootstrap.optJSONArray("members");
        boolean hasAnyRide = false;
        if (members != null) {
            for (int i=0;i<members.length();i++) {
                JSONObject m=members.optJSONObject(i);
                if (m==null) continue;
                rows.add(m);
                if (m.optDouble("total_distance_m",0)>0 || m.optInt("rides_count",0)>0) hasAnyRide=true;
            }
        }
        final boolean sortByRide=hasAnyRide;
        rows.sort((a,b)->{
            if(sortByRide){
                int c=Double.compare(b.optDouble("total_distance_m",0),a.optDouble("total_distance_m",0));
                if(c!=0)return c;
                c=Integer.compare(b.optInt("rides_count",0),a.optInt("rides_count",0));
                if(c!=0)return c;
            }
            return Integer.compare(a.optInt("id",0),b.optInt("id",0));
        });

        TextView title=text("سه رکاب‌زن برتر",18,true,TEXT);
        box.addView(title);
        TextView subtitle=text(hasAnyRide ? "بیشترین مسافت رکاب‌زده تا این لحظه" : "تا ثبت اولین رکاب، ترتیب بر اساس زمان عضویت است",
                12,false,MUTED);
        subtitle.setPadding(0,dp(4),0,dp(12));
        box.addView(subtitle);

        if(rows.isEmpty()){
            TextView empty=text("هنوز عضوی ثبت نشده است.",12,false,Color.rgb(214,230,242));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12),dp(18),dp(12),dp(18));
            empty.setBackground(roundRect(Color.argb(10,255,255,255),20,Color.argb(36,255,255,255)));
            box.addView(empty);
            return box;
        }

        int count=Math.min(3,rows.size());
        for(int i=0;i<count;i++){
            JSONObject m=rows.get(i);
            View row=homeLeaderRow(m,i);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,0,0,dp(10));
            box.addView(row,lp);
        }
        return box;
    }

    private View homeLeaderRow(JSONObject member,int rank) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(10),dp(10),dp(10),dp(10));
        row.setClickable(true);
        row.setFocusable(true);
        GradientDrawable bg=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(20,255,255,255),Color.argb(8,255,255,255)});
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1),Color.argb(26,255,255,255));
        row.setBackground(bg);

        TextView medal=text(rank==0?"🥇":rank==1?"🥈":"🥉",20,false,Color.rgb(16,32,44));
        medal.setGravity(Gravity.CENTER);
        GradientDrawable medalBg;
        if(rank==0){
            medalBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(255,241,167),Color.rgb(249,214,79),Color.rgb(174,109,8)});
        }else if(rank==1){
            medalBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.WHITE,Color.rgb(231,237,244),Color.rgb(127,139,151)});
        }else{
            medalBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(255,224,187),Color.rgb(217,154,97),Color.rgb(113,64,31)});
        }
        medalBg.setCornerRadius(dp(16));
        medal.setBackground(medalBg);
        row.addView(medal,new LinearLayout.LayoutParams(dp(48),dp(48)));

        String name=member.optString("display_name",member.optString("username","عضو "+member.optInt("id",0)));
        String initial=name.trim().isEmpty()?"؟":name.trim().substring(0,1);
        TextView avatar=text(initial,18,true,TEXT);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(roundRect(Color.argb(20,255,255,255),14,Color.argb(31,255,255,255)));
        LinearLayout.LayoutParams avatarLp=new LinearLayout.LayoutParams(dp(42),dp(42));
        avatarLp.setMargins(dp(10),0,0,0);
        row.addView(avatar,avatarLp);

        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.END);
        TextView who=text(name,14,true,TEXT);who.setMaxLines(1);
        int rides=member.optInt("rides_count",0);
        TextView meta=text(rides>0?JalaliDate.fa(rides)+" رکاب‌زنی ثبت‌شده":"هنوز رکابی ثبت نکرده",11,false,Color.rgb(207,225,238));
        meta.setPadding(0,dp(5),0,0);
        copy.addView(who);copy.addView(meta);
        LinearLayout.LayoutParams copyLp=new LinearLayout.LayoutParams(0,-2,1f);
        copyLp.setMargins(dp(10),0,0,0);
        row.addView(copy,copyLp);

        LinearLayout score=new LinearLayout(this);
        score.setOrientation(LinearLayout.VERTICAL);
        score.setGravity(Gravity.START);
        double km=member.optDouble("total_distance_m",0)/1000.0;
        String kmText=km>=100?String.format(Locale.US,"%.0f",km):String.format(Locale.US,"%.1f",km);
        TextView kmv=text(JalaliDate.fa(kmText),18,true,TEXT);
        TextView unit=text("کیلومتر",11,false,Color.rgb(205,224,237));unit.setPadding(0,dp(6),0,0);
        score.addView(kmv);score.addView(unit);
        row.addView(score,new LinearLayout.LayoutParams(dp(72),-2));

        row.setOnClickListener(v->showMemberDialog(member));
        return row;
    }

    private View buildHomeFeed() {
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(18),dp(18),dp(18));
        GradientDrawable bg=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(46,23,54,77),Color.argb(64,8,28,45)});
        bg.setCornerRadius(dp(30));
        bg.setStroke(dp(1),Color.argb(46,255,255,255));
        box.setBackground(bg);

        box.addView(text("رویدادها",19,true,TEXT));
        TextView sub=text("آخرین اتفاق‌های پدال پرو",12,false,MUTED);
        sub.setPadding(0,dp(4),0,dp(10));
        box.addView(sub);

        JSONArray feed=bootstrap==null?null:bootstrap.optJSONArray("latest_feed");
        int count=feed==null?0:Math.min(5,feed.length());
        if(count==0){
            TextView empty=text("هنوز رویدادی ثبت نشده است.",12,false,MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0,dp(16),0,dp(16));
            box.addView(empty);
        }else{
            for(int i=0;i<count;i++){
                JSONObject item=feed.optJSONObject(i);
                if(item==null)continue;
                View row=feedRow(item,false);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                lp.setMargins(0,0,0,dp(10));
                box.addView(row,lp);
            }
        }

        Button more=normalButton("ادامه رویدادها");
        more.setOnClickListener(v->showFeedPage());
        LinearLayout.LayoutParams moreLp=new LinearLayout.LayoutParams(-1,dp(48));
        moreLp.setMargins(0,dp(2),0,0);
        box.addView(more,moreLp);
        return box;
    }

    private View feedRow(JSONObject item, boolean full) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(13),dp(12),dp(13),dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setAlpha(item.optInt("hidden",0)==1?.52f:1f);
        row.setBackground(roundRect(Color.argb(14,255,255,255),18,Color.argb(23,255,255,255)));

        View dot=new View(this);
        GradientDrawable dotBg=new GradientDrawable();dotBg.setShape(GradientDrawable.OVAL);dotBg.setColor(Color.rgb(33,227,239));dot.setBackground(dotBg);
        row.addView(dot,new LinearLayout.LayoutParams(dp(10),dp(10)));

        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.END);
        String title=item.optString("title","رویداد");
        String detail=item.optString("detail","");
        if(detail.isBlank())detail=feedTypeLabel(item.optString("event_type",""));
        if(full)detail=detail+" · "+feedTypeLabel(item.optString("event_type",""));
        copy.addView(text(title,14,true,TEXT));
        TextView d=text(detail,11,false,Color.rgb(203,220,232));d.setPadding(0,dp(4),0,0);d.setLineSpacing(0,1.7f);copy.addView(d);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1f);cp.setMargins(dp(11),0,dp(11),0);row.addView(copy,cp);

        TextView time=text(JalaliDate.format(item.optString("created_at",""),true),10,false,Color.rgb(184,202,215));
        row.addView(time);
        row.setOnClickListener(v->showFeedDetail(item));
        return row;
    }

    private String feedTypeLabel(String type){
        switch(type){
            case "member_joined": return "عضویت";
            case "event_registered": return "ثبت‌نام برنامه";
            case "challenge_joined": return "چالش";
            case "ride_completed": return "رکاب‌زنی";
            case "event_completed": return "برنامه برگزارشده";
            default: return "رویداد";
        }
    }

    private void showFeedPage(){
        currentPage="feed";
        selectNav(null);
        content.removeAllViews();

        LinearLayout head=glassCard(26);
        head.addView(text("رویدادها",22,true,TEXT));
        head.addView(text("تاریخچه فعالیت‌های سایت از ابتدای ثبت داده‌ها",12,false,MUTED));
        PedalActionView refresh=action(PedalIconView.Icon.REFRESH,"↻ بروزرسانی",PedalActionView.Style.OUTLINE);
        head.addView(refresh,fullActionLp(48));
        content.addView(head,cardLp());

        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        TextView loading=text("در حال بارگذاری...",13,false,MUTED);loading.setGravity(Gravity.CENTER);list.addView(loading);
        content.addView(list,cardLp());
        Runnable load=()->{
            list.removeAllViews();
            list.addView(text("در حال بارگذاری...",13,false,MUTED));
            io.execute(()->{
                try{
                    java.util.HashMap<String,Object> q=new java.util.HashMap<>();
                    q.put("limit",5000);
                    boolean admin=bootstrap!=null&&(bootstrap.optBoolean("is_admin",false)||bootstrap.optInt("is_admin",0)==1);
                    if(admin)q.put("include_hidden",1);
                    JSONObject data=api.get("site_feed_list",q);
                    JSONArray items=data.optJSONArray("items");
                    main.post(()->{
                        list.removeAllViews();
                        if(items==null||items.length()==0){
                            TextView empty=text("رویدادی ثبت نشده است.",13,false,MUTED);empty.setGravity(Gravity.CENTER);list.addView(empty);return;
                        }
                        for(int i=0;i<items.length();i++){
                            JSONObject item=items.optJSONObject(i);if(item==null)continue;
                            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));
                            list.addView(feedRow(item,true),lp);
                        }
                        VazirmatnLoader.apply(this,list);
                    });
                }catch(Exception e){
                    main.post(()->{list.removeAllViews();TextView err=text(e.getMessage(),13,false,RED);err.setGravity(Gravity.CENTER);list.addView(err);});
                }
            });
        };
        refresh.setOnClickListener(v->load.run());
        load.run();
        VazirmatnLoader.apply(this,content);
        animatePageIn();
    }

    private void showFeedDetail(JSONObject item){
        Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=glassCard(22);box.setPadding(dp(16),dp(16),dp(16),dp(16));
        box.addView(text(item.optString("title","رویداد"),20,true,TEXT));
        String body=(item.optString("detail",""))+"\nزمان: "+JalaliDate.format(item.optString("created_at",""),true)+"\nنوع: "+feedTypeLabel(item.optString("event_type",""));
        TextView copy=text(body,12,false,Color.rgb(212,229,239));copy.setPadding(0,dp(10),0,0);copy.setLineSpacing(0,1.8f);box.addView(copy);

        String entity=item.optString("entity_type","");
        int entityId=item.optInt("entity_id",0);
        if(entityId>0){
            String label="";
            if("event".equals(entity))label="مشاهده برنامه";
            else if("challenge".equals(entity))label="مشاهده چالش";
            else if("ride".equals(entity))label="مشاهده رکاب‌زنی";
            else if("user".equals(entity))label="مشاهده عضو";
            if(!label.isBlank()){
                Button go=normalButton(label);
                go.setOnClickListener(v->{
                    d.dismiss();
                    if("event".equals(entity)){JSONObject e=findEvent(entityId);if(e!=null)showEventDetail(e);else showActivities();}
                    else if("challenge".equals(entity))showChallenges();
                    else if("ride".equals(entity)){Intent i=new Intent(this,NativeRideDetailActivity.class);i.putExtra(NativeRideDetailActivity.EXTRA_RIDE_ID,entityId);startActivity(i);}
                    else if("user".equals(entity)){JSONObject m=findMember(entityId);if(m!=null)showMemberDialog(m);else showClub();}
                });
                box.addView(go,fullActionLp(48));
            }
        }

        boolean admin=bootstrap!=null&&(bootstrap.optBoolean("is_admin",false)||bootstrap.optInt("is_admin",0)==1);
        if(admin){
            Button hide=normalButton(item.optInt("hidden",0)==1?"نمایش دوباره":"مخفی کردن این رویداد");
            hide.setOnClickListener(v->{
                hide.setEnabled(false);
                io.execute(()->{
                    try{
                        api.post("site_feed_hide",new JSONObject().put("id",item.optInt("id",0)).put("hidden",item.optInt("hidden",0)!=1));
                        bootstrap=api.bootstrap();
                        main.post(()->{d.dismiss();toast(item.optInt("hidden",0)==1?"رویداد دوباره نمایش داده شد":"رویداد مخفی شد");showFeedPage();});
                    }catch(Exception e){main.post(()->{hide.setEnabled(true);toast(e.getMessage());});}
                });
            });
            box.addView(hide,fullActionLp(48));
        }

        d.setContentView(box);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));WindowManager.LayoutParams lp=new WindowManager.LayoutParams();lp.copyFrom(w.getAttributes());lp.width=Math.min(dp(760),getResources().getDisplayMetrics().widthPixels-dp(16));lp.height=-2;lp.gravity=Gravity.CENTER;lp.dimAmount=.72f;w.setAttributes(lp);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}VazirmatnLoader.apply(this,box);
    }

    private JSONObject findMember(int id){
        JSONArray members=bootstrap==null?null:bootstrap.optJSONArray("members");
        if(members!=null)for(int i=0;i<members.length();i++){JSONObject m=members.optJSONObject(i);if(m!=null&&m.optInt("id",0)==id)return m;}
        return null;
    }

    private void showMemberDialog(JSONObject member){
        if(member==null)return;
        Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView sc=new ScrollView(this);
        LinearLayout box=glassCard(24);box.setPadding(dp(16),dp(16),dp(16),dp(16));

        String name=member.optString("display_name",member.optString("username","عضو"));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView avatar=text(name.trim().isEmpty()?"؟":name.trim().substring(0,1),20,true,TEXT);avatar.setGravity(Gravity.CENTER);avatar.setBackground(roundRect(Color.argb(20,255,255,255),16,Color.argb(31,255,255,255)));top.addView(avatar,new LinearLayout.LayoutParams(dp(56),dp(56)));
        LinearLayout who=new LinearLayout(this);who.setOrientation(LinearLayout.VERTICAL);who.setGravity(Gravity.END);who.addView(text(name,16,true,TEXT));TextView role=text(member.optString("role","member"),11,false,MUTED);role.setPadding(0,dp(4),0,0);who.addView(role);LinearLayout.LayoutParams whoLp=new LinearLayout.LayoutParams(0,-2,1f);whoLp.setMargins(dp(14),0,0,0);top.addView(who,whoLp);box.addView(top);

        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);stats.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);stats.setGravity(Gravity.CENTER);
        stats.addView(memberStat("مسافت",String.format(Locale.US,"%.1f km",member.optDouble("total_distance_m",0)/1000d)),new LinearLayout.LayoutParams(0,-2,1f));
        stats.addView(memberStat("رکاب‌زنی",JalaliDate.fa(member.optInt("rides_count",0))),new LinearLayout.LayoutParams(0,-2,1f));
        stats.addView(memberStat("چالش",JalaliDate.fa(member.optInt("challenges_count",0))),new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout.LayoutParams statLp=new LinearLayout.LayoutParams(-1,-2);statLp.setMargins(0,dp(14),0,0);box.addView(stats,statLp);

        String bike="🚲 "+member.optString("bike_brand_model","مدل ثبت نشده")+"\nنوع: "+member.optString("bike_type","—")+" · سایز: "+member.optString("bike_size","—")+" · رنگ: "+member.optString("bike_color","—");
        TextView bikeView=text(bike,12,false,Color.rgb(212,229,239));bikeView.setPadding(dp(12),dp(10),dp(12),dp(10));bikeView.setLineSpacing(0,1.8f);bikeView.setBackground(roundRect(Color.argb(14,255,255,255),16,Color.argb(31,255,255,255)));box.addView(bikeView,fullWrapLp(10));

        box.addView(text("چالش‌های این عضو",16,true,TEXT),fullWrapLp(12));
        JSONArray challenges=bootstrap==null?null:bootstrap.optJSONArray("challenges");int cc=0;
        if(challenges!=null)for(int i=0;i<challenges.length()&&cc<6;i++){JSONObject c=challenges.optJSONObject(i);if(c==null||c.optInt("creator_user_id",0)!=member.optInt("id",0)||!"public".equals(c.optString("visibility")))continue;Button b=normalButton(c.optString("title","چالش"));b.setOnClickListener(v->{d.dismiss();showChallenges();});box.addView(b,fullActionLp(44));cc++;}
        if(cc==0)box.addView(text("چالش عمومی ندارد.",11,false,MUTED));

        box.addView(text("رکاب‌زنی‌های اخیر",16,true,TEXT),fullWrapLp(12));
        JSONArray rides=bootstrap==null?null:bootstrap.optJSONArray("rides");int rc=0;
        if(rides!=null)for(int i=0;i<rides.length()&&rc<6;i++){JSONObject r=rides.optJSONObject(i);if(r==null||r.optInt("owner_user_id",0)!=member.optInt("id",0)||!"completed".equals(r.optString("status")))continue;int rideId=r.optInt("id",0);Button b=normalButton(r.optString("title","رکاب‌زنی")+" · "+String.format(Locale.US,"%.1f km",r.optDouble("distance_km",0)));b.setOnClickListener(v->{d.dismiss();Intent x=new Intent(this,NativeRideDetailActivity.class);x.putExtra(NativeRideDetailActivity.EXTRA_RIDE_ID,rideId);startActivity(x);});box.addView(b,fullActionLp(44));rc++;}
        if(rc==0)box.addView(text("رکاب‌زنی عمومی ثبت‌شده‌ای ندارد.",11,false,MUTED));

        sc.addView(box);
        d.setContentView(sc);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));WindowManager.LayoutParams lp=new WindowManager.LayoutParams();lp.copyFrom(w.getAttributes());lp.width=Math.min(dp(760),getResources().getDisplayMetrics().widthPixels-dp(16));lp.height=Math.min(dp(760),Math.round(getResources().getDisplayMetrics().heightPixels*.84f));lp.gravity=Gravity.CENTER;lp.dimAmount=.72f;w.setAttributes(lp);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}VazirmatnLoader.apply(this,box);
    }

    private View memberStat(String label,String value){
        LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER);x.setPadding(dp(6),dp(9),dp(6),dp(9));x.setBackground(roundRect(Color.argb(14,255,255,255),15,Color.argb(31,255,255,255)));TextView l=text(label,10,false,MUTED);l.setGravity(Gravity.CENTER);TextView v=text(value,14,true,TEXT);v.setGravity(Gravity.CENTER);v.setPadding(0,dp(5),0,0);x.addView(l);x.addView(v);return x;
    }

    private void showHome() {
        currentPage = "dashboard";
        selectNav(navHome);
        content.removeAllViews();

        LinearLayout hero = glassCard(38);
        hero.setPadding(dp(18), dp(28), dp(18), dp(42));

        TextView eyebrow = text(uiString("home_eyebrow", "گروه دوچرخه سواری"), 17, true, TEXT);
        hero.addView(eyebrow);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        brand.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int brandSize = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density <= 430 ? 37 : 41;
        TextView pedal = text("پدال", brandSize, true, TEXT);
        TextView pro = text("پرو", brandSize, true, Color.rgb(31, 231, 242));
        LinearLayout.LayoutParams proLp = new LinearLayout.LayoutParams(-2, -2);
        proLp.setMargins(dp(12), 0, 0, 0);
        brand.addView(pedal);
        brand.addView(pro, proLp);
        hero.addView(brand);

        TextView tagline = text(uiString("home_tagline", "رکاب بزن، طبیعت، مسیرهای جدید"), 12, true, Color.rgb(237, 248, 255));
        tagline.setPadding(0, dp(16), 0, dp(4));
        hero.addView(tagline);

        PedalActionView register = action(PedalIconView.Icon.CALENDAR,
                uiString("home_primary_button_text", "ثبت نام برنامه"), PedalActionView.Style.PRIMARY);
        register.setOnClickListener(v -> homeRegisterNextEventNative());
        LinearLayout.LayoutParams registerLp = fullActionLp(70);
        registerLp.setMargins(0, dp(16), 0, 0);
        hero.addView(register, registerLp);

        PedalActionView routes = action(PedalIconView.Icon.ROUTE,
                uiString("home_secondary_button_text", "مشاهده مسیرها"), PedalActionView.Style.OUTLINE);
        routes.setOnClickListener(v -> showRoutes());
        LinearLayout.LayoutParams routesLp = fullActionLp(70);
        routesLp.setMargins(0, dp(8), 0, 0);
        hero.addView(routes, routesLp);
        content.addView(hero, cardLp());

        content.addView(buildHomeQuickGrid(), fullWrapLp(4));

        JSONObject event = nextUpcomingEvent();
        content.addView(buildUpcomingCard(event), cardLp());

        content.addView(buildHomeLiveHub(), cardLp());

        content.addView(buildWeatherCard(), cardLp());
        loadWeather(false);

        content.addView(buildHomeReport(), fullWrapLp(2));
        content.addView(buildHomeTopRiders(), fullWrapLp(0));
        content.addView(buildHomeFeed(), fullWrapLp(0));

        VazirmatnLoader.apply(this, content);
        animatePageIn();
        if (mainScroll != null) mainScroll.post(() -> mainScroll.scrollTo(0, 0));
    }


    private void showRide() {
        currentPage = "ride";
        if (!signedIn()) { showLogin(); return; }
        selectNav(navRide);
        content.removeAllViews();
        activeRideRouteView=null;

        if (activeRide == null) {
            addHero("شروع رکاب‌زنی", "ثبت مسیر فقط بعد از دریافت موقعیت تازه و دقیق شروع می‌شود.");
            LinearLayout gate = glassCard(28);
            LinearLayout statusRow=new LinearLayout(this);statusRow.setOrientation(LinearLayout.HORIZONTAL);statusRow.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
            PedalIconView gpsIcon=new PedalIconView(this,PedalIconView.Icon.LOCATION,Color.rgb(31,231,242));
            TextView state = text("در حال بررسی GPS…", 16, true, TEXT);
            statusRow.addView(state,new LinearLayout.LayoutParams(0,-2,1f));statusRow.addView(gpsIcon,new LinearLayout.LayoutParams(dp(34),dp(34)));
            TextView accuracy = text("دقت: —", 12, false, MUTED);
            accuracy.setPadding(0,dp(7),0,dp(4));
            PedalActionView check = action(PedalIconView.Icon.REFRESH,"بررسی دوباره GPS",PedalActionView.Style.OUTLINE);
            PedalActionView start = action(PedalIconView.Icon.PLAY,"شروع ثبت مسیر",PedalActionView.Style.PRIMARY);
            start.setEnabled(false);start.setAlpha(.5f);
            gate.addView(statusRow);gate.addView(accuracy);gate.addView(check,fullActionLp(52));gate.addView(start,fullActionLp(58));
            content.addView(gate, cardLp());

            Button checkProxy=hiddenButtonProxy(check), startProxy=hiddenButtonProxy(start);
            check.setOnClickListener(v->{check.setEnabled(false);startGpsPreflight(state,accuracy,startProxy,checkProxy);main.postDelayed(()->check.setEnabled(true),1600);});
            start.setOnClickListener(v->{if(pendingFreshFix!=null)createRideAndStartTracking(startProxy,state);});
            startGpsPreflight(state, accuracy, startProxy, checkProxy);
            main.postDelayed(()->{if(pendingFreshFix!=null){start.setEnabled(true);start.setAlpha(1f);}},1200);
            VazirmatnLoader.apply(this,content);animatePageIn();
            return;
        }

        addHero("رکاب‌زنی فعال", activeRide.optString("title", "PedalPro"));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        rideTimer = metricValue("زمان", formatDuration(activeRide.optLong("duration_seconds", 0)));
        rideDistance = metricValue("مسافت", String.format(Locale.US, "%.2f", activeRide.optDouble("distance_km", 0)));
        rideSpeed = metricValue("سرعت", String.format(Locale.US, "%.1f", activeRide.optDouble("avg_speed_kmh", 0)));
        stats.addView(wrapMetric(rideTimer, "زمان"), weightHeightLp(dp(100)));
        stats.addView(wrapMetric(rideDistance, "km"), weightHeightLp(dp(100)));
        stats.addView(wrapMetric(rideSpeed, "km/h"), weightHeightLp(dp(100)));
        content.addView(stats, cardLp());

        activeRideRouteView=new NativeRouteView(this);
        content.addView(activeRideRouteView,new LinearLayout.LayoutParams(-1,dp(280)));
        loadActiveRideRoute();

        LinearLayout gps = glassCard(20);
        rideGps = text(isTrackingServiceActive() ? "GPS فعال · ثبت مسیر در پس‌زمینه" : "GPS موقتاً متوقف است", 13, true,
                isTrackingServiceActive() ? GREEN : ORANGE);
        gps.addView(rideGps);
        content.addView(gps, cardLp());

        PedalActionView pause=action(manualPaused?PedalIconView.Icon.PLAY:PedalIconView.Icon.PAUSE,manualPaused?"ادامه ثبت":"توقف ثبت",PedalActionView.Style.OUTLINE);
        pause.setOnClickListener(v->toggleManualPause(pause));
        content.addView(pause,fullActionLp(54));

        PedalActionView groupNav=action(PedalIconView.Icon.BEACON,"رسیدن به گروه در حال حرکت",PedalActionView.Style.OUTLINE);
        groupNav.setOnClickListener(v->openGroupNavigation());content.addView(groupNav,fullActionLp(52));

        PedalActionView sensors=action(PedalIconView.Icon.SENSOR,"سنسورهای Cadence / Power / HR",PedalActionView.Style.OUTLINE);
        sensors.setOnClickListener(v->showSensors());content.addView(sensors,fullActionLp(52));

        PedalActionView beacon=action(PedalIconView.Icon.BEACON,"اشتراک زنده Beacon و SOS",PedalActionView.Style.OUTLINE);
        beacon.setOnClickListener(v->startActivity(new Intent(this,NativeSafetyActivity.class)));content.addView(beacon,fullActionLp(52));

        PedalActionView low=action(PedalIconView.Icon.SUN,"حالت کم‌مصرف OLED",PedalActionView.Style.OUTLINE);
        low.setOnClickListener(v->{
            Intent i=new Intent(this,LowPowerRideActivity.class);
            i.putExtra(LowPowerRideActivity.EXTRA_RIDE_ID,activeRide.optInt("id",0));
            i.putExtra(LowPowerRideActivity.EXTRA_BASE_SECONDS,activeRide.optLong("duration_seconds",0));
            i.putExtra(LowPowerRideActivity.EXTRA_DISTANCE_KM,activeRide.optDouble("distance_km",0));
            startActivity(i);
        });
        content.addView(low,fullActionLp(52));

        PedalActionView stop=action(PedalIconView.Icon.CLOSE,"پایان رکاب‌زنی",PedalActionView.Style.OUTLINE);
        stop.setAccent(RED);stop.setOnClickListener(v->confirmFinishRide(stop));
        content.addView(stop,fullActionLp(54));

        rideBaseSeconds = activeRide.optLong("duration_seconds", 0);
        rideTimerAnchorElapsed = SystemClock.elapsedRealtime();
        VazirmatnLoader.apply(this,content);animatePageIn();
    }


    private void showActivities() {
        currentPage = "activities";
        selectNav(navActivities);
        content.removeAllViews();

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        boolean admin = bootstrap != null && (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1);
        if (admin) {
            PedalActionView add = action(PedalIconView.Icon.PLUS, "برنامه جدید", PedalActionView.Style.PRIMARY);
            add.setOnClickListener(v -> showEventEditor(null));
            tools.addView(add, rowWeightLp());
        }
        PedalActionView refresh = action(PedalIconView.Icon.REFRESH, "بروزرسانی", PedalActionView.Style.OUTLINE);
        refresh.setOnClickListener(v -> refreshAndRender(this::showActivities));
        LinearLayout.LayoutParams refreshLp = rowWeightLp();
        if (admin) refreshLp.setMargins(dp(8),0,0,0);
        tools.addView(refresh, refreshLp);
        content.addView(tools, cardLp());

        LinearLayout intro = glassCard(28);
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.TOP);
        line.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView heading = text("فعالیت‌ها و برنامه‌ها", 27, true, TEXT);
        TextView note = text("تنها اعضایی که شماره تلفن تایید شده داشته باشند امکان ثبت نام دارند.", 13, false, TEXT);
        note.setGravity(Gravity.END);
        line.addView(heading, new LinearLayout.LayoutParams(dp(155), LinearLayout.LayoutParams.WRAP_CONTENT));
        line.addView(note, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        intro.addView(line);
        content.addView(intro, cardLp());

        JSONArray events = bootstrap.optJSONArray("events");
        int eventCount = 0;
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (event == null || "cancelled".equals(event.optString("status"))) continue;
                addEventCard(event);
                eventCount++;
            }
        }
        if (eventCount == 0) addEmpty("فعلاً برنامه‌ای ثبت نشده است.");

        TextView ridesTitle = text("رکاب‌زنی‌های ثبت‌شده", 19, true, TEXT);
        ridesTitle.setPadding(dp(4), dp(12), dp(4), dp(8));
        content.addView(ridesTitle);

        JSONArray rides = bootstrap.optJSONArray("my_completed_rides");
        if (rides == null) rides = bootstrap.optJSONArray("rides");
        JSONObject me = bootstrap.optJSONObject("user");
        int uid = me == null ? 0 : me.optInt("id", 0);
        int shown = 0;
        if (rides != null) {
            for (int i = 0; i < rides.length() && shown < 30; i++) {
                JSONObject r = rides.optJSONObject(i);
                if (r == null || !"completed".equals(r.optString("status", "completed"))) continue;
                int owner = r.optInt("owner_user_id", uid);
                if (uid > 0 && owner > 0 && owner != uid) continue;
                addRideSummaryCard(r, false);
                shown++;
            }
        }
        if (shown == 0) addEmpty("هنوز فعالیت تکمیل‌شده‌ای وجود ندارد.");
        VazirmatnLoader.apply(this, content);
        animatePageIn();
    }


    private void showRoutes() {
        currentPage = "routes";
        selectNav(navRoutes);
        content.removeAllViews();
        addHero("مسیرها و رکاب‌زنی‌ها", "");
        addLiveRidesSection(20);

        JSONArray rides = bootstrap.optJSONArray("rides");
        int added = 0;
        if (rides != null) {
            for (int i = 0; i < rides.length() && added < 30; i++) {
                JSONObject r = rides.optJSONObject(i);
                if (r == null || "active".equals(r.optString("status"))) continue;
                addRideSummaryCard(r, true);
                added++;
            }
        }
        if (added == 0 && liveCount() == 0) addEmpty("مسیر قابل نمایشی وجود ندارد.");
    }


    private void showClub() {
        currentPage = "club";
        selectNav(null);
        content.removeAllViews();
        addHero("اعضای باشگاه", "");

        JSONArray members = bootstrap.optJSONArray("members");
        if (members == null || members.length() == 0) {
            addEmpty("عضوی برای نمایش دریافت نشد.");
            return;
        }
        for (int i = 0; i < members.length(); i++) {
            JSONObject m = members.optJSONObject(i);
            if (m == null) continue;
            LinearLayout box = card();
            String name = m.optString("display_name", m.optString("username", "عضو"));
            box.addView(text(name, 15, true, TEXT));
            String username = m.optString("username", "");
            String role = m.optString("role", "");
            box.addView(text((username.isBlank() ? "" : "@" + username) + (role.isBlank() ? "" : " · " + role), 11, false, MUTED));
            content.addView(box, cardLp());
        }
    }

    private void showChallenges() {
        currentPage = "challenges";
        selectNav(null);
        content.removeAllViews();
        addHero("چالش‌ها", "");

        boolean admin = bootstrap != null && (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1);
        if (signedIn()) {
            LinearLayout create = new LinearLayout(this);
            create.setOrientation(LinearLayout.HORIZONTAL);
            create.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            PedalActionView add = action(PedalIconView.Icon.PLUS, "چالش جدید", PedalActionView.Style.PRIMARY);
            add.setOnClickListener(v -> showChallengeEditor(null, false));
            create.addView(add, new LinearLayout.LayoutParams(0, dp(52), 1f));
            if (admin) {
                PedalActionView official = action(PedalIconView.Icon.PLUS, "چالش رسمی جدید", PedalActionView.Style.OUTLINE);
                official.setOnClickListener(v -> showChallengeEditor(null, true));
                LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, dp(52), 1f);
                op.setMargins(dp(8), 0, 0, 0);
                create.addView(official, op);
            }
            content.addView(create, cardLp());
        }

        JSONArray challenges = bootstrap == null ? null : bootstrap.optJSONArray("challenges");
        if (challenges == null) challenges = new JSONArray();

        addChallengeSectionTitle("چالش‌های زنده", "");
        int liveShown = 0;
        for (int i = 0; i < challenges.length(); i++) {
            JSONObject ch = challenges.optJSONObject(i);
            if (ch == null) continue;
            JSONObject live = challengeLiveRide(ch);
            if (live == null || !challengeLiveVisible(live, ch)) continue;
            content.addView(buildChallengeCard(ch), cardLp());
            liveShown++;
        }
        if (liveShown == 0) addEmpty("چالش زنده عمومی وجود ندارد.");

        addChallengeSectionTitle("چالش‌ها", "");
        if (challenges.length() == 0) {
            addEmpty("هنوز چالشی ساخته نشده است.");
        } else {
            for (int i = 0; i < challenges.length(); i++) {
                JSONObject ch = challenges.optJSONObject(i);
                if (ch != null) content.addView(buildChallengeCard(ch), cardLp());
            }
        }

        if (signedIn()) {
            addChallengeSectionTitle("چالش‌های من", "چالش‌هایی که ساخته‌اید یا در آن‌ها شرکت کرده‌اید");
            JSONObject me = bootstrap.optJSONObject("user");
            int uid = me == null ? 0 : me.optInt("id", 0);
            int mine = 0;
            for (int i = 0; i < challenges.length(); i++) {
                JSONObject ch = challenges.optJSONObject(i);
                if (ch == null) continue;
                if (ch.optInt("creator_user_id", 0) == uid || ch.optInt("joined_by_me", 0) == 1) {
                    content.addView(buildChallengeCard(ch), cardLp());
                    mine++;
                }
            }
            if (mine == 0) addEmpty("هنوز در هیچ چالشی عضو نشده‌اید.");
        }

        VazirmatnLoader.apply(this, content);
        animatePageIn();
    }


    private void addChallengeSectionTitle(String title, String sub) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.END);
        TextView t = text(title, 19, true, TEXT);
        head.addView(t);
        if (sub != null && !sub.isBlank()) {
            TextView st = text(sub, 11, false, MUTED);
            st.setPadding(0, dp(3), 0, 0);
            head.addView(st);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(3), dp(10), dp(3), dp(8));
        content.addView(head, lp);
    }

    private View buildChallengeCard(JSONObject ch) {
        boolean official = ch.optInt("is_official", 0) == 1;
        JSONObject me = bootstrap == null ? null : bootstrap.optJSONObject("user");
        int uid = me == null ? 0 : me.optInt("id", 0);
        boolean mine = me != null && (ch.optInt("creator_user_id", 0) == uid || ch.optInt("joined_by_me", 0) == 1);
        boolean admin = bootstrap != null && (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1);
        JSONArray participants = challengeParticipants(ch);
        JSONObject live = challengeLiveRide(ch);
        double progress = ch.optDouble("target_value", 0) > 0 ? challengeProgressValue(ch) : 0;
        double target = ch.optDouble("target_value", 0);
        double pct = target > 0 ? Math.min(100, progress / Math.max(.0001, target) * 100.0) : 0;

        LinearLayout box = glassCard(24);
        if (mine) {
            GradientDrawable selected = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.argb(56, 19, 64, 93), Color.argb(71, 8, 30, 46)});
            selected.setCornerRadius(dp(24));
            selected.setStroke(dp(1), Color.argb(92, 46, 230, 166));
            box.setBackground(selected);
        }

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.END);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title = text((official ? "⭐ " : "") + ch.optString("title", "چالش"), 17, true, TEXT);
        titleRow.addView(title);
        if (mine) {
            TextView badge = challengeBadge("در این چالش هستم", GREEN);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
            bp.setMargins(dp(7), 0, 0, 0);
            titleRow.addView(badge, bp);
        }
        copy.addView(titleRow);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        meta.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        meta.setPadding(0, dp(7), 0, 0);
        meta.addView(challengeMeta("👤 " + ch.optString("creator_name", "عضو")));
        if (!ch.optString("starts_at", "").isBlank()) {
            String date = "📅 " + JalaliDate.format(ch.optString("starts_at"), false);
            if (!ch.optString("ends_at", "").isBlank()) date += " تا " + JalaliDate.format(ch.optString("ends_at"), false);
            meta.addView(challengeMeta(date));
        }
        if (official) meta.addView(challengeBadge("رسمی", Color.rgb(255, 217, 74)));
        if (!official && "private".equals(ch.optString("visibility"))) meta.addView(challengeBadge("خصوصی", Color.rgb(220, 188, 255)));
        if (live != null && live.optInt("public_tracking", 0) == 1) meta.addView(challengeBadge("زنده", GREEN));
        copy.addView(meta);

        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView participantsCount = challengeBadge("شرکت‌کنندگان " + JalaliDate.fa(participants.length()), Color.rgb(210, 229, 241));
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
        countLp.setMargins(dp(8), 0, 0, 0);
        top.addView(participantsCount, countLp);
        box.addView(top);

        String desc = ch.optString("description", "");
        if (!desc.isBlank()) {
            TextView d = text(desc, 12, false, MUTED);
            d.setPadding(0, dp(10), 0, 0);
            box.addView(d);
        }

        LinearLayout extras = new LinearLayout(this);
        extras.setOrientation(LinearLayout.HORIZONTAL);
        extras.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        extras.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        extras.setPadding(0, dp(9), 0, 0);
        extras.addView(challengeBadge("نوع هدف: " + challengeMetricLabel(ch.optString("metric", "distance")), Color.rgb(213, 232, 244)));
        if (target > 0) {
            String v = String.format(Locale.US, "پیشرفت: %." + ("rides".equals(ch.optString("metric")) ? "0" : "1") + "f / %.1f", progress, target);
            if ("rides".equals(ch.optString("metric"))) v = "پیشرفت: " + Math.round(progress) + " / " + Math.round(target);
            TextView pb = challengeBadge(v, Color.rgb(213, 232, 244));
            LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(-2, -2);
            pblp.setMargins(dp(7), 0, 0, 0);
            extras.addView(pb, pblp);
        }
        box.addView(extras);

        if (target > 0) box.addView(challengeProgressBar(pct));

        if (participants.length() > 0) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            chips.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            chips.setPadding(0, dp(10), 0, 0);
            int show = Math.min(5, participants.length());
            for (int i = 0; i < show; i++) {
                JSONObject p = participants.optJSONObject(i);
                if (p == null) continue;
                boolean self = me != null && p.optInt("user_id", 0) == uid;
                TextView chip = challengeMemberChip(p.optString("display_name", "عضو"), self);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
                if (i > 0) cp.setMargins(dp(5), 0, 0, 0);
                chips.addView(chip, cp);
            }
            if (participants.length() > 5) {
                TextView more = challengeMemberChip("+" + (participants.length() - 5), false);
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
                mp.setMargins(dp(5), 0, 0, 0);
                chips.addView(more, mp);
            }
            box.addView(chips);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(10), 0, 0);

        if (live != null && challengeLiveVisible(live, ch)) {
            Button liveBtn = primaryButton("مشاهده زنده");
            int rideId = live.optInt("id", 0);
            liveBtn.setOnClickListener(v -> openRideDetailNative(rideId));
            actions.addView(liveBtn, challengeActionLp());
        }

        boolean canJoin = me != null
                && ch.optInt("allow_join", 1) == 1
                && ("public".equals(ch.optString("visibility", "public")) || official)
                && ch.optInt("creator_user_id", 0) != uid;
        if (canJoin) {
            boolean joined = ch.optInt("joined_by_me", 0) == 1;
            Button join = normalButton(joined ? "خروج از چالش" : "شرکت در چالش");
            int challengeId = ch.optInt("id", 0);
            join.setOnClickListener(v -> toggleChallengeJoin(challengeId, !joined, join));
            actions.addView(join, challengeActionLp());
        }

        Button details = normalButton("جزئیات");
        details.setOnClickListener(v -> showChallengeDetail(ch));
        actions.addView(details, challengeActionLp());

        if (mine || admin) {
            Button edit = normalButton("ویرایش");
            edit.setOnClickListener(v -> showChallengeEditor(ch, official));
            actions.addView(edit, challengeActionLp());
        }
        box.addView(actions);
        return box;
    }

    private LinearLayout.LayoutParams challengeActionLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.setMargins(dp(5), 0, 0, 0);
        return lp;
    }

    private TextView challengeMeta(String value) {
        TextView t = text(value, 10, false, MUTED);
        t.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(7), 0, 0, 0);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView challengeBadge(String value, int color) {
        TextView t = text(value, 10, true, color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(5), dp(8), dp(5));
        t.setBackground(roundRect(Color.argb(14, 255, 255, 255), 11, Color.argb(25, 255, 255, 255)));
        return t;
    }

    private TextView challengeMemberChip(String value, boolean self) {
        TextView t = text(value, 10, true, self ? Color.rgb(4, 36, 24) : TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(9), dp(6), dp(9), dp(6));
        t.setBackground(roundRect(self ? Color.rgb(46, 230, 166) : Color.argb(18, 255, 255, 255), 13, Color.argb(32, 255, 255, 255)));
        return t;
    }

    private View challengeProgressBar(double percent) {
        FrameLayout track = new FrameLayout(this);
        track.setBackground(roundRect(Color.argb(22, 255, 255, 255), 999, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(8));
        lp.setMargins(0, dp(10), 0, 0);
        track.setLayoutParams(lp);
        View fill = new View(this);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(46, 230, 166), Color.rgb(43, 146, 255)});
        bg.setCornerRadius(dp(999));
        fill.setBackground(bg);
        int width = Math.max(dp(2), Math.round((getResources().getDisplayMetrics().widthPixels - dp(64)) * (float)Math.max(0, Math.min(100, percent)) / 100f));
        track.addView(fill, new FrameLayout.LayoutParams(width, -1));
        return track;
    }

    private JSONArray challengeParticipants(JSONObject ch) {
        JSONArray out = new JSONArray();
        JSONArray members = bootstrap == null ? null : bootstrap.optJSONArray("challenge_members");
        int challengeId = ch.optInt("id", 0);
        int creatorId = ch.optInt("creator_user_id", 0);
        boolean creatorFound = false;
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        if (members != null) {
            for (int i = 0; i < members.length(); i++) {
                JSONObject m = members.optJSONObject(i);
                if (m == null || m.optInt("challenge_id", 0) != challengeId) continue;
                int uid = m.optInt("user_id", 0);
                if (seen.add(uid)) {
                    out.put(m);
                    if (uid == creatorId) creatorFound = true;
                }
            }
        }
        if (!creatorFound && creatorId > 0) {
            JSONObject creator = new JSONObject();
            try {
                creator.put("challenge_id", challengeId);
                creator.put("user_id", creatorId);
                creator.put("display_name", ch.optString("creator_name", "سازنده"));
            } catch (Exception ignored) {}
            JSONArray withCreator = new JSONArray();
            withCreator.put(creator);
            for (int i = 0; i < out.length(); i++) withCreator.put(out.optJSONObject(i));
            return withCreator;
        }
        return out;
    }

    private JSONArray challengeProgressRides(JSONObject ch) {
        JSONArray all = bootstrap == null ? null : bootstrap.optJSONArray("rides");
        JSONArray out = new JSONArray();
        if (all == null) return out;

        String metric = ch.optString("metric", "distance");
        int challengeId = ch.optInt("id", 0);
        java.util.HashSet<Integer> participantIds = new java.util.HashSet<>();
        JSONArray participants = challengeParticipants(ch);
        for (int i = 0; i < participants.length(); i++) {
            JSONObject p = participants.optJSONObject(i);
            if (p != null) participantIds.add(p.optInt("user_id", 0));
        }
        String start = ch.optString("starts_at", "");
        String end = ch.optString("ends_at", "");
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();

        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < all.length(); i++) {
                JSONObject r = all.optJSONObject(i);
                if (r == null || !"completed".equals(r.optString("status"))) continue;
                boolean direct = r.optInt("challenge_id", 0) == challengeId;
                if ("rides".equals(metric) && !direct) continue;
                if (pass == 0 && !direct) continue;
                if (pass == 1) {
                    if (direct || !participantIds.contains(r.optInt("owner_user_id", 0))) continue;
                    String raw = r.optString("ended_at", r.optString("started_at", ""));
                    String date = raw.length() >= 10 ? raw.substring(0, 10) : raw;
                    if (!start.isBlank() && !date.isBlank() && date.compareTo(start.substring(0, Math.min(10, start.length()))) < 0) continue;
                    if (!end.isBlank() && !date.isBlank() && date.compareTo(end.substring(0, Math.min(10, end.length()))) > 0) continue;
                }
                int id = r.optInt("id", i + 1);
                if (seen.add(id)) out.put(r);
            }
            if ("rides".equals(metric)) break;
        }
        return out;
    }

    private double challengeProgressValue(JSONObject ch) {
        JSONArray rides = challengeProgressRides(ch);
        String metric = ch.optString("metric", "distance");
        if ("distance".equals(metric)) {
            double total = 0;
            for (int i = 0; i < rides.length(); i++) total += rides.optJSONObject(i).optDouble("distance_km", 0);
            return total;
        }
        if ("elevation".equals(metric)) {
            double total = 0;
            for (int i = 0; i < rides.length(); i++) total += rides.optJSONObject(i).optDouble("ascent_m", 0);
            return total;
        }
        return rides.length();
    }

    private JSONObject challengeLiveRide(JSONObject ch) {
        JSONArray rides = bootstrap == null ? null : bootstrap.optJSONArray("rides");
        if (rides == null) return null;
        int challengeId = ch.optInt("id", 0);
        for (int i = 0; i < rides.length(); i++) {
            JSONObject r = rides.optJSONObject(i);
            if (r != null && r.optInt("challenge_id", 0) == challengeId && "active".equals(r.optString("status"))) return r;
        }
        return null;
    }

    private boolean challengeLiveVisible(JSONObject live, JSONObject ch) {
        if (live == null) return false;
        JSONObject me = bootstrap == null ? null : bootstrap.optJSONObject("user");
        int uid = me == null ? 0 : me.optInt("id", 0);
        boolean mine = me != null && (ch.optInt("creator_user_id", 0) == uid || ch.optInt("joined_by_me", 0) == 1);
        boolean admin = bootstrap != null && (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1);
        return live.optInt("public_tracking", 0) == 1 || mine || admin;
    }

    private String challengeMetricLabel(String metric) {
        if ("elevation".equals(metric)) return "صعود";
        if ("rides".equals(metric)) return "تعداد رکاب‌زنی";
        return "مسافت";
    }

    private void openRideDetailNative(int rideId) {
        if (rideId <= 0) return;
        Intent i = new Intent(this, NativeRideDetailActivity.class);
        i.putExtra(NativeRideDetailActivity.EXTRA_RIDE_ID, rideId);
        startActivity(i);
    }

    private void showChallengeDetail(JSONObject ch) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = glassCard(22);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        head.addView(text(ch.optString("title", "چالش"), 20, true, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
        Button close = normalButton("×");
        close.setOnClickListener(v -> d.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        box.addView(head);

        double val = challengeProgressValue(ch);
        String metric = ch.optString("metric", "distance");
        String unit = "distance".equals(metric) ? " km" : "elevation".equals(metric) ? " m" : "";
        LinearLayout notice = glassCard(15);
        notice.addView(text(ch.optString("title", "چالش"), 14, true, TEXT));
        if (!ch.optString("description", "").isBlank()) notice.addView(text(ch.optString("description"), 12, false, TEXT));
        notice.addView(text("هدف: " + challengeMetricLabel(metric) + " " + (ch.optString("target_value", "").isBlank() ? "—" : ch.optString("target_value")), 12, false, TEXT));
        notice.addView(text("پیشرفت فعلی: " + ("rides".equals(metric) ? Math.round(val) : String.format(Locale.US, "%.1f", val)) + unit, 12, false, TEXT));
        notice.addView(text("وضعیت: " + ch.optString("status", ""), 12, false, TEXT));
        box.addView(notice, cardLp());

        box.addView(text("شرکت‌کنندگان", 17, true, TEXT));
        JSONArray participants = challengeParticipants(ch);
        if (participants.length() == 0) {
            box.addView(text("هنوز کسی در این چالش شرکت نکرده است.", 12, false, MUTED));
        } else {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.VERTICAL);
            JSONObject me = bootstrap == null ? null : bootstrap.optJSONObject("user");
            int uid = me == null ? 0 : me.optInt("id", 0);
            for (int i = 0; i < participants.length(); i++) {
                JSONObject p = participants.optJSONObject(i);
                if (p == null) continue;
                TextView c = challengeMemberChip(p.optString("display_name", "عضو"), me != null && p.optInt("user_id", 0) == uid);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
                clp.setMargins(0, dp(4), 0, 0);
                chips.addView(c, clp);
            }
            box.addView(chips);
        }

        TextView ridesTitle = text("رکاب‌زنی‌های مرتبط", 17, true, TEXT);
        ridesTitle.setPadding(0, dp(16), 0, dp(6));
        box.addView(ridesTitle);
        JSONArray rides = challengeProgressRides(ch);
        if (rides.length() == 0) {
            box.addView(text("هنوز رکاب‌زنی‌ای ثبت نشده است.", 12, false, MUTED));
        } else {
            for (int i = 0; i < rides.length(); i++) {
                JSONObject r = rides.optJSONObject(i);
                if (r == null) continue;
                Button b = normalButton(r.optString("owner_name", "") + " · " + String.format(Locale.US, "%.1f km", r.optDouble("distance_km", 0)) + ("active".equals(r.optString("status")) ? " 🟢" : ""));
                int rideId = r.optInt("id", 0);
                b.setOnClickListener(v -> { d.dismiss(); openRideDetailNative(rideId); });
                box.addView(b);
            }
        }

        scroll.addView(box);
        d.setContentView(scroll);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = Math.min(getResources().getDisplayMetrics().heightPixels - dp(48), dp(720));
            lp.gravity = Gravity.CENTER;
            lp.dimAmount = .66f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        VazirmatnLoader.apply(this, box);
    }

    private void showChallengeEditor(JSONObject ch, boolean official) {
        if (!signedIn()) { showAuthDialog("login"); return; }
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = glassCard(18);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        head.addView(text("چالش", 20, true, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
        Button close = normalButton("×");
        close.setOnClickListener(v -> d.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        box.addView(head);

        EditText title = input("عنوان");
        title.setText(ch == null ? "" : ch.optString("title", ""));
        android.widget.Spinner metric = challengeSpinner(new String[]{"مسافت (km)", "صعود (m)", "تعداد رکاب‌زنی"}, ch == null ? 0 : "elevation".equals(ch.optString("metric")) ? 1 : "rides".equals(ch.optString("metric")) ? 2 : 0);
        EditText target = input("مقدار هدف");
        target.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        target.setText(ch == null ? "" : ch.optString("target_value", ""));
        EditText start = input("شروع · تاریخ شمسی");
        start.setText(ch == null || ch.optString("starts_at", "").isBlank() ? "" : jalaliDateForInput(ch.optString("starts_at")));
        EditText end = input("پایان · تاریخ شمسی");
        end.setText(ch == null || ch.optString("ends_at", "").isBlank() ? "" : jalaliDateForInput(ch.optString("ends_at")));
        android.widget.Spinner status = challengeSpinner(new String[]{"برنامه‌ریزی", "در حال اجرا", "تمام‌شده"}, ch == null ? 0 : "active".equals(ch.optString("status")) ? 1 : "completed".equals(ch.optString("status")) ? 2 : 0);
        android.widget.Spinner visibility = challengeSpinner(new String[]{"عمومی", "خصوصی"}, ch != null && "private".equals(ch.optString("visibility")) ? 1 : 0);
        android.widget.Spinner allowJoin = challengeSpinner(new String[]{"بله", "خیر"}, ch != null && ch.optInt("allow_join", 1) == 0 ? 1 : 0);
        EditText desc = input("توضیحات");
        desc.setSingleLine(false);
        desc.setMinLines(3);
        desc.setText(ch == null ? "" : ch.optString("description", ""));

        box.addView(title);
        box.addView(metric);
        box.addView(target);
        box.addView(start);
        box.addView(end);
        box.addView(status);
        box.addView(visibility);
        box.addView(allowJoin);
        box.addView(desc);

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button save = primaryButton("ذخیره");
        foot.addView(save, new LinearLayout.LayoutParams(0, dp(50), 1f));

        boolean canDelete = ch != null && (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1
                || ch.optInt("creator_user_id", 0) == bootstrap.optJSONObject("user").optInt("id", 0));
        if (canDelete) {
            Button delete = normalButton("حذف چالش");
            delete.setTextColor(RED);
            delete.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                    .setMessage("این چالش حذف شود؟ رکاب‌زنی‌های ثبت‌شده حذف نمی‌شوند و فقط از چالش جدا می‌شوند.")
                    .setPositiveButton("حذف", (dialog, which) -> deleteChallenge(ch.optInt("id", 0), d, delete))
                    .setNegativeButton("انصراف", null)
                    .show());
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(50), 1f);
            dlp.setMargins(dp(8), 0, 0, 0);
            foot.addView(delete, dlp);
        }
        box.addView(foot);

        save.setOnClickListener(v -> {
            String titleText = title.getText().toString().trim();
            if (titleText.isBlank()) { toast("عنوان چالش را وارد کنید."); return; }
            String startIso = start.getText().toString().trim().isBlank() ? "" : JalaliDate.toGregorianIso(start.getText().toString().trim());
            String endIso = end.getText().toString().trim().isBlank() ? "" : JalaliDate.toGregorianIso(end.getText().toString().trim());
            if (!start.getText().toString().trim().isBlank() && startIso.isBlank()) { toast("تاریخ شروع نامعتبر است."); return; }
            if (!end.getText().toString().trim().isBlank() && endIso.isBlank()) { toast("تاریخ پایان نامعتبر است."); return; }
            String[] metrics = {"distance", "elevation", "rides"};
            String[] statuses = {"planned", "active", "completed"};
            save.setEnabled(false);
            JSONObject payload = new JSONObject();
            try {
                payload.put("id", ch == null ? 0 : ch.optInt("id", 0));
                payload.put("title", titleText);
                payload.put("metric", metrics[Math.max(0, Math.min(2, metric.getSelectedItemPosition()))]);
                payload.put("target_value", JalaliDate.en(target.getText().toString().trim()));
                payload.put("starts_at", startIso);
                payload.put("ends_at", endIso);
                payload.put("status", statuses[Math.max(0, Math.min(2, status.getSelectedItemPosition()))]);
                payload.put("visibility", visibility.getSelectedItemPosition() == 1 ? "private" : "public");
                payload.put("allow_join", allowJoin.getSelectedItemPosition() == 0);
                payload.put("description", desc.getText().toString().trim());
                payload.put("is_official", official || (ch != null && ch.optInt("is_official", 0) == 1));
            } catch (Exception ignored) {}
            io.execute(() -> {
                try {
                    api.post("save_challenge", payload);
                    bootstrap = api.bootstrap();
                    activeRide = bootstrap.optJSONObject("my_active_ride");
                    main.post(() -> { d.dismiss(); toast("چالش ذخیره شد"); showChallenges(); });
                } catch (Exception e) {
                    main.post(() -> { save.setEnabled(true); toast(e.getMessage()); });
                }
            });
        });

        scroll.addView(box);
        d.setContentView(scroll);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = Math.min(getResources().getDisplayMetrics().heightPixels - dp(40), dp(760));
            lp.gravity = Gravity.CENTER;
            lp.dimAmount = .66f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        VazirmatnLoader.apply(this, box);
    }

    private android.widget.Spinner challengeSpinner(String[] labels, int selected) {
        android.widget.Spinner spinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(TEXT); v.setTextSize(12); v.setGravity(Gravity.CENTER_VERTICAL | Gravity.END); v.setPadding(dp(12), 0, dp(12), 0);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(TEXT); v.setBackgroundColor(Color.rgb(9, 26, 41)); v.setPadding(dp(12), dp(12), dp(12), dp(12)); v.setGravity(Gravity.END);
                return v;
            }
        };
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(labels.length - 1, selected)));
        spinner.setBackground(roundRect(Color.argb(122, 5, 25, 40), 15, Color.argb(46, 255, 255, 255)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.setMargins(0, dp(6), 0, dp(6));
        spinner.setLayoutParams(lp);
        return spinner;
    }

    private void deleteChallenge(int id, Dialog modal, Button button) {
        if (id <= 0) return;
        button.setEnabled(false);
        io.execute(() -> {
            try {
                api.post("delete_challenge", new JSONObject().put("challenge_id", id));
                bootstrap = api.bootstrap();
                activeRide = bootstrap.optJSONObject("my_active_ride");
                main.post(() -> { modal.dismiss(); toast("چالش حذف شد"); showChallenges(); });
            } catch (Exception e) {
                main.post(() -> { button.setEnabled(true); toast(e.getMessage()); });
            }
        });
    }

    private String jalaliDateForInput(String iso) {
        if (iso == null || iso.isBlank()) return "";
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(iso.substring(0, 10));
            int[] j = JalaliDate.toJalali(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
            return JalaliDate.fa(String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]));
        } catch (Exception e) { return iso; }
    }

    private void toggleChallengeJoin(int challengeId, boolean join, Button button) {
        if (challengeId <= 0) return;
        if (!signedIn()) {
            showLogin();
            return;
        }
        button.setEnabled(false);
        io.execute(() -> {
            try {
                api.post("join_challenge", new JSONObject().put("challenge_id", challengeId).put("join", join));
                bootstrap = api.bootstrap();
                activeRide = bootstrap.optJSONObject("my_active_ride");
                main.post(() -> {
                    updateHeaderAccess(bootstrap);
                    applyUiSettings(bootstrap);
                    toast(join ? "به چالش ملحق شدید." : "همراهی لغو شد.");
                    showChallenges();
                });
            } catch (Exception e) {
                main.post(() -> {
                    button.setEnabled(true);
                    toast(e.getMessage());
                });
            }
        });
    }


    private void showGroupChat() {
        currentPage = "chat";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("گفتگوی گروهی", "");

        chatMessagesHost = new LinearLayout(this);
        chatMessagesHost.setOrientation(LinearLayout.VERTICAL);
        content.addView(chatMessagesHost);

        LinearLayout composer = card();
        EditText input = input("پیام…");
        input.setSingleLine(false);
        input.setMinLines(2);
        Button send = primaryButton("ارسال");
        composer.addView(input);
        composer.addView(send);
        content.addView(composer, cardLp());

        send.setOnClickListener(v -> {
            String body = input.getText().toString().trim();
            if (body.isBlank() || chatRoomId <= 0) return;
            hideKeyboard();
            send.setEnabled(false);
            io.execute(() -> {
                try {
                    JSONObject d = api.post("chat_send", new JSONObject()
                            .put("room_id", chatRoomId)
                            .put("body", body)
                            .put("reply_to_id", 0)
                            .put("client_token", java.util.UUID.randomUUID().toString()));
                    main.post(() -> {
                        input.setText("");
                        send.setEnabled(true);
                        loadGroupChatMessages(true);
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        send.setEnabled(true);
                        toast(e.getMessage());
                    });
                }
            });
        });

        io.execute(() -> {
            try {
                JSONObject boot = api.get("chat_bootstrap");
                JSONObject group = boot.optJSONObject("group");
                if (group == null || group.optInt("id", 0) <= 0) throw new IllegalStateException("چت گروهی در دسترس نیست.");
                chatRoomId = group.optInt("id");
                main.post(() -> {
                    loadGroupChatMessages(true);
                    main.removeCallbacks(chatPoll);
                    main.postDelayed(chatPoll, 4500L);
                });
            } catch (Exception e) {
                main.post(() -> {
                    LinearLayout box = card();
                    box.addView(text("چت دریافت نشد: " + e.getMessage(), 12, false, MUTED));
                    chatMessagesHost.addView(box, cardLp());
                });
            }
        });
    }

    private void loadGroupChatMessages(boolean full) {
        if (chatLoading || chatRoomId <= 0 || chatMessagesHost == null) return;
        chatLoading = true;
        final int after = full ? 0 : chatLastId;
        io.execute(() -> {
            try {
                java.util.LinkedHashMap<String, String> q = new java.util.LinkedHashMap<>();
                q.put("room_id", String.valueOf(chatRoomId));
                q.put("after", String.valueOf(after));
                q.put("limit", full ? "120" : "60");
                JSONObject d = api.get("chat_messages", q);
                JSONArray items = d.optJSONArray("items");
                chatLastId = Math.max(chatLastId, d.optInt("last_id", 0));
                main.post(() -> {
                    if (chatMessagesHost == null) return;
                    if (full) chatMessagesHost.removeAllViews();
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject m = items.optJSONObject(i);
                            if (m == null) continue;
                            chatLastId = Math.max(chatLastId, m.optInt("id", 0));
                            addChatMessage(chatMessagesHost, m);
                        }
                    }
                    if (full && (items == null || items.length() == 0)) {
                        LinearLayout e = card();
                        e.addView(text("هنوز پیامی ارسال نشده است.", 12, false, MUTED));
                        chatMessagesHost.addView(e, cardLp());
                    }
                });
            } catch (Exception e) {
                if (full) main.post(() -> toast(e.getMessage()));
            } finally {
                chatLoading = false;
            }
        });
    }

    private void addChatMessage(LinearLayout host, JSONObject m) {
        JSONObject user = bootstrap == null ? null : bootstrap.optJSONObject("user");
        int uid = user == null ? 0 : user.optInt("id", 0);
        boolean mine = m.optInt("sender_user_id", 0) == uid;
        LinearLayout box = card();
        box.setBackgroundColor(mine ? Color.rgb(17, 67, 75) : Color.rgb(20, 40, 58));
        box.addView(text(mine ? "شما" : m.optString("sender_name", "عضو"), 11, true, mine ? ACCENT : ORANGE));
        box.addView(text(m.optString("body", ""), 13, false, TEXT));
        String created = m.optString("created_at", "");
        if (!created.isBlank()) box.addView(text(created, 9, false, MUTED));
        host.addView(box, cardLp());
    }

    private void showAiAdvisor() {
        currentPage = "ai";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("مشاور هوش مصنوعی", "گفتگوی Native مستقیم با سرویس PedalPro");

        LinearLayout messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        content.addView(messages);

        LinearLayout composer = card();
        EditText prompt = input("پیام خود را بنویسید…");
        prompt.setSingleLine(false);
        prompt.setMinLines(3);
        Button send = primaryButton("ارسال");
        composer.addView(prompt);
        composer.addView(send);
        content.addView(composer, cardLp());

        loadAiMessages(messages);

        send.setOnClickListener(v -> {
            String message = prompt.getText().toString().trim();
            if (message.isBlank()) return;
            hideKeyboard();
            send.setEnabled(false);
            send.setText("در حال دریافت پاسخ…");
            addAiBubble(messages, "شما", message, false);
            prompt.setText("");
            io.execute(() -> {
                try {
                    JSONObject d = api.post("ai_chat", new JSONObject()
                            .put("message", message)
                            .put("mode", "coach")
                            .put("use_profile_context", true));
                    String answer = d.optString("answer", "پاسخی دریافت نشد.");
                    main.post(() -> {
                        addAiBubble(messages, "PedalPro AI", answer, true);
                        send.setEnabled(true);
                        send.setText("ارسال");
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        send.setEnabled(true);
                        send.setText("ارسال");
                        toast(e.getMessage());
                    });
                }
            });
        });
    }

    private void loadAiMessages(LinearLayout host) {
        io.execute(() -> {
            try {
                JSONObject d = api.get("ai_history");
                JSONArray rows = d.optJSONArray("messages");
                main.post(() -> {
                    host.removeAllViews();
                    if (rows == null || rows.length() == 0) {
                        LinearLayout e = card();
                        e.addView(text("گفتگو هنوز شروع نشده است.", 12, false, MUTED));
                        host.addView(e, cardLp());
                        return;
                    }
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject m = rows.optJSONObject(i);
                        if (m == null) continue;
                        boolean assistant = "assistant".equals(m.optString("role"));
                        addAiBubble(host, assistant ? "PedalPro AI" : "شما", m.optString("body", ""), assistant);
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    LinearLayout box = card();
                    box.addView(text("تاریخچه AI دریافت نشد: " + e.getMessage(), 12, false, MUTED));
                    host.addView(box, cardLp());
                });
            }
        });
    }

    private void addAiBubble(LinearLayout host, String sender, String body, boolean assistant) {
        LinearLayout box = card();
        box.setBackgroundColor(assistant ? Color.rgb(10, 54, 67) : Color.rgb(24, 45, 66));
        box.addView(text(sender, 11, true, assistant ? ACCENT : ORANGE));
        TextView msg = text(body, 13, false, TEXT);
        msg.setPadding(0, dp(6), 0, 0);
        box.addView(msg);
        host.addView(box, cardLp());
    }

    private void showPerformance() {
        currentPage = "performance";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        showLoadingState("در حال محاسبه عملکرد…");
        io.execute(() -> {
            try {
                JSONObject report = api.get("user_performance_report");
                JSONObject training;
                try { training = api.get("pp_training_summary"); } catch (Exception ignored) { training = new JSONObject(); }
                JSONObject finalTraining = training;
                main.post(() -> renderPerformanceReport(report, finalTraining));
            } catch (Exception e) {
                main.post(() -> {
                    content.removeAllViews();
                    addHero("عملکرد من", "گزارش شخصی");
                    showInlineMessage("گزارش در دسترس نیست", e.getMessage(), RED);
                });
            }
        });
    }


    private void renderPerformanceReport(JSONObject report, JSONObject training) {
        content.removeAllViews();
        addHero("عملکرد من", "هفته از شنبه تا جمعه");

        JSONObject root = report == null ? new JSONObject() : report;
        JSONObject summary = root.optJSONObject("summary");
        if (summary == null) summary = root.optJSONObject("totals");
        if (summary == null) summary = root;

        double distance = summary.optDouble("distance_km",
                summary.optDouble("total_distance_km", summary.optDouble("distance", 0)));
        long duration = summary.optLong("duration_seconds",
                summary.optLong("total_duration_seconds", summary.optLong("duration", 0)));
        double ascent = summary.optDouble("ascent_m",
                summary.optDouble("total_ascent_m", summary.optDouble("ascent", 0)));
        int rides = summary.optInt("rides_count",
                summary.optInt("ride_count", summary.optInt("activities_count", 0)));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(wrapMetric(metricValue("رکاب", JalaliDate.fa(rides)), "رکاب"), weightHeightLp(dp(96)));
        metrics.addView(wrapMetric(metricValue("مسافت", JalaliDate.fa(String.format(Locale.US, "%.1f", distance))), "km"), weightHeightLp(dp(96)));
        metrics.addView(wrapMetric(metricValue("زمان", formatDuration(duration)), "زمان"), weightHeightLp(dp(96)));
        content.addView(metrics, cardLp());

        LinearLayout metrics2 = new LinearLayout(this);
        metrics2.setOrientation(LinearLayout.HORIZONTAL);
        double avg = summary.optDouble("avg_speed_kmh", summary.optDouble("average_speed_kmh", 0));
        double max = summary.optDouble("max_speed_kmh", summary.optDouble("best_speed_kmh", 0));
        metrics2.addView(wrapMetric(metricValue("صعود", JalaliDate.fa(String.format(Locale.US, "%.0f", ascent))), "m"), weightHeightLp(dp(96)));
        metrics2.addView(wrapMetric(metricValue("میانگین", JalaliDate.fa(String.format(Locale.US, "%.1f", avg))), "km/h"), weightHeightLp(dp(96)));
        metrics2.addView(wrapMetric(metricValue("بیشینه", JalaliDate.fa(String.format(Locale.US, "%.1f", max))), "km/h"), weightHeightLp(dp(96)));
        content.addView(metrics2, cardLp());

        JSONObject week = root.optJSONObject("week");
        if (week == null) week = root.optJSONObject("weekly");
        JSONObject month = root.optJSONObject("month");
        if (month == null) month = root.optJSONObject("monthly");
        if (week != null) addPerformancePeriod("این هفته", week);
        if (month != null) addPerformancePeriod("این ماه", month);

        if (training != null && training.length() > 0) {
            LinearLayout t = glassCard(24);
            t.addView(text("بار تمرینی", 17, true, TEXT));
            double load = training.optDouble("training_load", training.optDouble("load", 0));
            double fitness = training.optDouble("fitness", training.optDouble("ctl", 0));
            double fatigue = training.optDouble("fatigue", training.optDouble("atl", 0));
            double form = training.optDouble("form", training.optDouble("tsb", fitness - fatigue));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(wrapMetric(metricValue("Load", JalaliDate.fa(String.format(Locale.US, "%.0f", load))), "Load"), weightHeightLp(dp(90)));
            row.addView(wrapMetric(metricValue("Fitness", JalaliDate.fa(String.format(Locale.US, "%.0f", fitness))), "Fitness"), weightHeightLp(dp(90)));
            row.addView(wrapMetric(metricValue("Form", JalaliDate.fa(String.format(Locale.US, "%.0f", form))), "Form"), weightHeightLp(dp(90)));
            t.addView(row);
            TextView fatigueText = text("Fatigue: " + JalaliDate.fa(String.format(Locale.US, "%.0f", fatigue)), 11, false, MUTED);
            fatigueText.setPadding(0, dp(6), 0, 0);
            t.addView(fatigueText);
            content.addView(t, cardLp());
        }

        JSONArray best = root.optJSONArray("best_efforts");
        if (best == null) best = root.optJSONArray("prs");
        if (best != null && best.length() > 0) {
            LinearLayout b = glassCard(24);
            b.addView(text("بهترین رکوردها", 17, true, TEXT));
            for (int i = 0; i < Math.min(8, best.length()); i++) {
                JSONObject x = best.optJSONObject(i);
                if (x == null) continue;
                String name = x.optString("name", x.optString("label", "PR"));
                String value = x.optString("value", "");
                if (value.isBlank()) value = x.optString("display_value", x.optString("result", ""));
                b.addView(text(name + (value.isBlank() ? "" : " · " + value), 12, false, i == 0 ? ORANGE : TEXT));
            }
            content.addView(b, cardLp());
        }

        PedalActionView advanced = action(PedalIconView.Icon.CHART, "تحلیل حرفه‌ای، Segments و Heatmap", PedalActionView.Style.OUTLINE);
        advanced.setOnClickListener(v -> startActivity(new Intent(this, NativeAdvancedActivity.class)));
        content.addView(advanced, fullActionLp(54));

        VazirmatnLoader.apply(this, content);
        animatePageIn();
    }

    private void addPerformancePeriod(String title, JSONObject period) {
        LinearLayout box = glassCard(22);
        box.addView(text(title, 16, true, TEXT));
        double km = period.optDouble("distance_km", period.optDouble("total_distance_km", 0));
        long sec = period.optLong("duration_seconds", period.optLong("total_duration_seconds", 0));
        double climb = period.optDouble("ascent_m", period.optDouble("total_ascent_m", 0));
        int count = period.optInt("rides_count", period.optInt("ride_count", period.optInt("activities_count", 0)));
        box.addView(text(JalaliDate.fa(count) + " رکاب · " +
                JalaliDate.fa(String.format(Locale.US, "%.1f", km)) + " km · " +
                formatDuration(sec) + " · " +
                JalaliDate.fa(String.format(Locale.US, "%.0f", climb)) + " m صعود", 12, false, MUTED));
        content.addView(box, cardLp());
    }

    private void showSensors() {
        currentPage = "sensors";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("سنسورها", "اتصال مستقیم Bluetooth LE بدون JavaScript Bridge");

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        cadenceValue = metricValue("Cadence", "0");
        powerValue = metricValue("Power", "0");
        heartRateValue = metricValue("HR", "—");
        metrics.addView(wrapMetric(cadenceValue, "rpm"), weightHeightLp(dp(96)));
        metrics.addView(wrapMetric(powerValue, "W"), weightHeightLp(dp(96)));
        metrics.addView(wrapMetric(heartRateValue, "bpm"), weightHeightLp(dp(96)));
        content.addView(metrics, cardLp());

        LinearLayout status = card();
        sensorStatus = text("آماده جستجوی سنسور", 12, true, MUTED);
        status.addView(sensorStatus);
        Button scan = primaryButton("جستجوی Cadence / Power / Heart Rate");
        scan.setOnClickListener(v -> startSensorScan());
        status.addView(scan);
        content.addView(status, cardLp());

        sensorDeviceList = new LinearLayout(this);
        sensorDeviceList.setOrientation(LinearLayout.VERTICAL);
        content.addView(sensorDeviceList);

        renderSensorDevices();
    }

    private void startSensorScan() {
        if (!hasBlePermissions()) {
            if (Build.VERSION.SDK_INT >= 31) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLE);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_BLE);
            }
            return;
        }
        if (sensorStatus != null) {
            sensorStatus.setText("در حال جستجوی سنسورها…");
            sensorStatus.setTextColor(ACCENT);
        }
        cyclingSensors.startScan();
        heartRateSensors.startScan();
        main.postDelayed(this::renderSensorDevices, 2200);
        main.postDelayed(this::renderSensorDevices, 5000);
        main.postDelayed(this::renderSensorDevices, 9500);
    }

    private void renderSensorDevices() {
        if (sensorDeviceList == null) return;
        sensorDeviceList.removeAllViews();
        int found = 0;
        try {
            JSONArray arr = new JSONArray(cyclingSensors.devicesJson());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject d = arr.optJSONObject(i);
                if (d == null) continue;
                addSensorDevice(d.optString("name", "Cycling Sensor"),
                        d.optString("address", ""),
                        (d.optBoolean("cadence") ? "Cadence " : "") + (d.optBoolean("power") ? "Power" : ""),
                        true);
                found++;
            }
        } catch (Exception ignored) { }

        try {
            JSONArray arr = new JSONArray(heartRateSensors.devicesJson());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject d = arr.optJSONObject(i);
                if (d == null) continue;
                addSensorDevice(d.optString("name", "Heart Rate Sensor"),
                        d.optString("address", ""), "Heart Rate", false);
                found++;
            }
        } catch (Exception ignored) { }

        if (found == 0) {
            LinearLayout box = card();
            box.addView(text("هنوز سنسور استاندارد BLE پیدا نشده است.", 12, false, MUTED));
            sensorDeviceList.addView(box, cardLp());
        }
    }

    private void addSensorDevice(String name, String address, String type, boolean cycling) {
        LinearLayout box = card();
        box.addView(text(name == null || name.isBlank() ? type : name, 14, true, TEXT));
        box.addView(text(type + (address.isBlank() ? "" : " · " + address), 10, false, MUTED));
        Button connect = primaryButton("اتصال");
        connect.setOnClickListener(v -> {
            if (cycling) cyclingSensors.connect(address);
            else heartRateSensors.connect(address);
            if (sensorStatus != null) sensorStatus.setText("در حال اتصال…");
        });
        box.addView(connect);
        sensorDeviceList.addView(box, cardLp());
    }

    private void renderCyclingStatus(String json) {
        main.post(() -> {
            try {
                JSONObject o = new JSONObject(json);
                if (sensorStatus != null && o.has("message")) sensorStatus.setText(o.optString("message"));
                if (cadenceValue != null) cadenceValue.setText(String.format(Locale.US, "%.0f", o.optDouble("cadenceRpm", 0)));
                if (powerValue != null) powerValue.setText(String.valueOf(o.optInt("powerW", 0)));
            } catch (Exception ignored) { }
        });
    }

    private void renderCyclingMeasurement(String json) {
        main.post(() -> {
            try {
                JSONObject o = new JSONObject(json);
                if (cadenceValue != null) cadenceValue.setText(String.format(Locale.US, "%.0f", o.optDouble("cadence_rpm", 0)));
                if (powerValue != null) powerValue.setText(String.valueOf(o.optInt("power_w", 0)));
                if (sensorStatus != null) {
                    sensorStatus.setText("سنسور دوچرخه متصل · داده زنده");
                    sensorStatus.setTextColor(GREEN);
                }
            } catch (Exception ignored) { }
        });
    }

    private void renderHeartRateStatus(String json) {
        main.post(() -> {
            try {
                JSONObject o = new JSONObject(json);
                if (heartRateValue != null && o.optInt("bpm", 0) > 0) heartRateValue.setText(String.valueOf(o.optInt("bpm")));
                if (sensorStatus != null && o.has("message")) sensorStatus.setText(o.optString("message"));
            } catch (Exception ignored) { }
        });
    }

    private void renderHeartRateMeasurement(String json) {
        main.post(() -> {
            try {
                JSONObject o = new JSONObject(json);
                int bpm = o.optInt("bpm", 0);
                if (heartRateValue != null && bpm > 0) heartRateValue.setText(String.valueOf(bpm));
                if (sensorStatus != null) {
                    sensorStatus.setText("Heart Rate متصل · داده زنده");
                    sensorStatus.setTextColor(GREEN);
                }
            } catch (Exception ignored) { }
        });
    }

    private boolean hasBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return hasFineLocation();
    }


    private void showProfileEditor() {
        currentPage = "profile";
        if (!signedIn()) { showAuthDialog("login"); return; }
        selectNav(null);
        content.removeAllViews();

        JSONObject user = bootstrap.optJSONObject("user");
        if (user == null) return;
        boolean admin = bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1;

        addHero("پروفایل من", "");

        LinearLayout identity = glassCard(24);
        LinearLayout profileHead = new LinearLayout(this);
        profileHead.setOrientation(LinearLayout.HORIZONTAL);
        profileHead.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        profileHead.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setClipToOutline(true);
        avatar.setBackground(roundRect(Color.TRANSPARENT, 44, Color.argb(100, 245, 249, 252)));
        RemoteImageLoader.load(this, avatar, user.optString("avatar_path", ""), R.drawable.pedalpro_app_icon);
        profileHead.addView(avatar, new LinearLayout.LayoutParams(dp(88), dp(88)));

        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setGravity(Gravity.END);
        who.addView(text(user.optString("display_name", "عضو"), 19, true, TEXT));
        if (!user.optString("username", "").isBlank()) who.addView(text("@" + user.optString("username"), 11, false, MUTED));
        TextView verification = text(
                (!user.optString("phone_verified_at", "").isBlank() || user.optInt("phone_verified", 0) == 1) ? "شماره تلفن تأیید شده" : "شماره تلفن تأیید نشده",
                11, true,
                (!user.optString("phone_verified_at", "").isBlank() || user.optInt("phone_verified", 0) == 1) ? GREEN : ORANGE);
        verification.setPadding(0, dp(4), 0, 0);
        who.addView(verification);
        LinearLayout.LayoutParams whoLp = new LinearLayout.LayoutParams(0, -2, 1f);
        whoLp.setMargins(dp(14), 0, 0, 0);
        profileHead.addView(who, whoLp);
        identity.addView(profileHead);

        PedalActionView pickAvatar = action(PedalIconView.Icon.EDIT, "تغییر تصویر پروفایل", PedalActionView.Style.OUTLINE);
        pickAvatar.setOnClickListener(v -> pickImage(REQ_PROFILE_AVATAR));
        identity.addView(pickAvatar, fullActionLp(50));
        content.addView(identity, cardLp());

        LinearLayout phoneCard = glassCard(24);
        phoneCard.addView(text("تأیید شماره تلفن", 17, true, TEXT));
        String savedPhone = user.optString("phone", "");
        boolean verified = !user.optString("phone_verified_at", "").isBlank() || user.optInt("phone_verified", 0) == 1;
        TextView phoneState = text(
                verified ? "شماره تأیید شده: " + savedPhone : "شماره هنوز تأیید نشده است.",
                11, true, verified ? GREEN : ORANGE);
        phoneState.setPadding(0, dp(5), 0, dp(4));
        phoneCard.addView(phoneState);

        EditText phone = input("شماره موبایل");
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        phone.setText(savedPhone);
        EditText code = input("کد تأیید");
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        phoneCard.addView(phone);
        phoneCard.addView(code);

        LinearLayout phoneActions = new LinearLayout(this);
        phoneActions.setOrientation(LinearLayout.HORIZONTAL);
        phoneActions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        PedalActionView sendCode = action(PedalIconView.Icon.CHAT, "ارسال کد", PedalActionView.Style.OUTLINE);
        PedalActionView verify = action(PedalIconView.Icon.DETAILS, "تأیید کد", PedalActionView.Style.PRIMARY);
        phoneActions.addView(sendCode, new LinearLayout.LayoutParams(0, dp(50), 1f));
        LinearLayout.LayoutParams verifyLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        verifyLp.setMargins(dp(8), 0, 0, 0);
        phoneActions.addView(verify, verifyLp);
        phoneCard.addView(phoneActions);

        if (admin) {
            PedalActionView manual = action(PedalIconView.Icon.EDIT, "ثبت دستی شماره مدیر", PedalActionView.Style.OUTLINE);
            manual.setOnClickListener(v -> saveProfilePhoneManual(phone, manual));
            phoneCard.addView(manual, fullActionLp(48));
        }

        TextView phoneNote = text("شماره موبایل برای کاربران عادی قابل مشاهده نیست.", 11, false, MUTED);
        phoneNote.setPadding(0, dp(8), 0, 0);
        phoneCard.addView(phoneNote);
        content.addView(phoneCard, cardLp());

        sendCode.setOnClickListener(v -> {
            String p = JalaliDate.en(phone.getText().toString().trim());
            if (p.isBlank()) { toast("شماره موبایل را وارد کنید."); return; }
            sendCode.setEnabled(false);
            io.execute(() -> {
                try {
                    api.post("profile_phone_send", new JSONObject().put("phone", p));
                    main.post(() -> {
                        sendCode.setEnabled(true);
                        code.requestFocus();
                        toast("کد تأیید ارسال شد؛ AutoFill فعال است");
                    });
                } catch (Exception e) {
                    main.post(() -> { sendCode.setEnabled(true); toast(e.getMessage()); });
                }
            });
        });

        verify.setOnClickListener(v -> {
            String p = JalaliDate.en(phone.getText().toString().trim());
            String otp = JalaliDate.en(code.getText().toString().trim());
            if (p.isBlank() || otp.isBlank()) { toast("شماره و کد را وارد کنید."); return; }
            verify.setEnabled(false);
            io.execute(() -> {
                try {
                    JSONObject result = api.post("profile_phone_verify", new JSONObject().put("phone", p).put("code", otp));
                    bootstrap = api.bootstrap();
                    activeRide = bootstrap.optJSONObject("my_active_ride");
                    main.post(() -> {
                        toast("شماره تأیید شد");
                        JSONObject invite = result.optJSONObject("bale_invite");
                        if (invite != null && invite.optBoolean("enabled", false)
                                && !invite.optBoolean("connected", false)
                                && !invite.optBoolean("opted_out", false)
                                && !invite.optString("url", "").isBlank()) {
                            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(invite.optString("url")))); }
                            catch (Exception ignored) {}
                        }
                        showProfileEditor();
                    });
                } catch (Exception e) {
                    main.post(() -> { verify.setEnabled(true); toast(e.getMessage()); });
                }
            });
        });

        LinearLayout profile = glassCard(24);
        profile.addView(text("اطلاعات پروفایل", 17, true, TEXT));
        EditText name = input("نام نمایشی"); name.setText(user.optString("display_name", ""));
        EditText age = input("سن"); age.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); age.setText(user.optString("age", ""));
        EditText height = input("قد (cm)"); height.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL); height.setText(user.optString("height_cm", ""));
        EditText weight = input("وزن (kg)"); weight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL); weight.setText(user.optString("weight_kg", ""));
        EditText bio = input("درباره من"); bio.setSingleLine(false); bio.setMinLines(4); bio.setText(user.optString("bio", ""));
        android.widget.Spinner visibility = challengeSpinner(new String[]{"عمومی", "خصوصی"}, user.optInt("profile_public", 1) == 1 ? 0 : 1);
        profile.addView(name); profile.addView(age); profile.addView(height); profile.addView(weight); profile.addView(bio);
        TextView publicLabel = text("نمایش پروفایل", 11, false, MUTED);
        publicLabel.setPadding(0, dp(5), 0, 0);
        profile.addView(publicLabel);
        profile.addView(visibility);
        content.addView(profile, cardLp());

        LinearLayout social = glassCard(24);
        social.addView(text("شبکه‌های اجتماعی و ورزشی", 17, true, TEXT));
        TextView socialSub = text("لینک یا نام کاربری هر سرویس را وارد کنید تا در پروفایل شما نمایش داده شود.", 11, false, MUTED);
        socialSub.setPadding(0, dp(4), 0, dp(5));
        social.addView(socialSub);

        EditText instagram = input("اینستاگرام"); instagram.setHint("نام کاربری یا لینک کامل"); instagram.setText(user.optString("instagram_profile", ""));
        EditText telegram = input("تلگرام"); telegram.setHint("نام کاربری یا لینک کامل"); telegram.setText(user.optString("telegram_profile", ""));
        EditText eitaa = input("ایتا"); eitaa.setHint("نام کاربری یا لینک کامل"); eitaa.setText(user.optString("eitaa_profile", ""));
        EditText bale = input("بله"); bale.setHint("نام کاربری یا لینک کامل"); bale.setText(user.optString("bale_profile", ""));
        EditText strava = input("Strava"); strava.setHint("لینک کامل پروفایل Strava"); strava.setText(user.optString("strava_profile", ""));
        EditText relive = input("Relive"); relive.setHint("لینک کامل پروفایل Relive"); relive.setText(user.optString("relive_profile", ""));
        social.addView(instagram); social.addView(telegram); social.addView(eitaa); social.addView(bale); social.addView(strava); social.addView(relive);
        social.addView(text("پس از وارد کردن لینک‌ها، «ذخیره تغییرات» را بزنید.", 11, false, MUTED));
        content.addView(social, cardLp());

        PedalActionView save = action(PedalIconView.Icon.DETAILS, "ذخیره تغییرات", PedalActionView.Style.PRIMARY);
        save.setOnClickListener(v -> saveProfileNative(
                user, name, age, height, weight, bio, visibility,
                instagram, telegram, eitaa, bale, strava, relive, save));
        content.addView(save, fullActionLp(54));

        TextView privacy = text("سن، قد و وزن عمومی نمایش داده نمی‌شوند. شماره موبایل نیز برای کاربران عادی قابل مشاهده نیست.", 11, false, MUTED);
        privacy.setPadding(dp(12), dp(11), dp(12), dp(11));
        privacy.setBackground(roundRect(Color.argb(8, 255, 255, 255), 14, Color.argb(28, 255, 255, 255)));
        content.addView(privacy, cardLp());

        renderProfileMedals(user);
        renderProfileChallenges(user);

        LinearLayout account = glassCard(24);
        account.addView(text("حریم خصوصی و مدیریت حساب", 17, true, TEXT));
        TextView accountInfo = text("سیاست حریم خصوصی و مسیر حذف کامل حساب برای نسخه فروشگاهی PedalPro در دسترس است.", 11, false, MUTED);
        accountInfo.setPadding(0, dp(5), 0, dp(7));
        account.addView(accountInfo);

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button policy = normalButton("سیاست حریم خصوصی");
        policy.setOnClickListener(v -> openExternalUrl("https://pedalpro.ir/privacy.php"));
        Button deleteWeb = normalButton("حذف حساب از وب");
        deleteWeb.setOnClickListener(v -> openExternalUrl("https://pedalpro.ir/account-delete.php"));
        links.addView(policy, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams deleteWebLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        deleteWebLp.setMargins(dp(8), 0, 0, 0);
        links.addView(deleteWeb, deleteWebLp);
        account.addView(links);

        EditText deletePassword = input("رمز عبور برای حذف حساب");
        deletePassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        deletePassword.setHint("برای تأیید حذف وارد کنید");
        account.addView(deletePassword);

        LinearLayout destructive = new LinearLayout(this);
        destructive.setOrientation(LinearLayout.HORIZONTAL);
        destructive.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button deleteAccount = normalButton("حذف دائمی حساب و داده‌ها");
        deleteAccount.setTextColor(RED);
        deleteAccount.setOnClickListener(v -> confirmDeleteOwnAccount(deletePassword, deleteAccount));
        Button logout = normalButton("خروج از حساب");
        logout.setTextColor(RED);
        logout.setOnClickListener(v -> logoutFromProfile(logout));
        destructive.addView(deleteAccount, new LinearLayout.LayoutParams(0, dp(50), 1f));
        LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        logoutLp.setMargins(dp(8), 0, 0, 0);
        destructive.addView(logout, logoutLp);
        account.addView(destructive);
        content.addView(account, cardLp());

        VazirmatnLoader.apply(this, content);
        animatePageIn();
    }


    private void saveProfileNative(
            JSONObject user,
            EditText name,
            EditText age,
            EditText height,
            EditText weight,
            EditText bio,
            android.widget.Spinner visibility,
            EditText instagram,
            EditText telegram,
            EditText eitaa,
            EditText bale,
            EditText strava,
            EditText relive,
            PedalActionView save) {
        save.setEnabled(false);
        io.execute(() -> {
            try {
                api.post("profile_save", new JSONObject()
                        .put("display_name", name.getText().toString().trim())
                        .put("age", JalaliDate.en(age.getText().toString().trim()))
                        .put("height_cm", JalaliDate.en(height.getText().toString().trim()))
                        .put("weight_kg", JalaliDate.en(weight.getText().toString().trim()))
                        .put("bio", bio.getText().toString().trim())
                        .put("profile_public", visibility.getSelectedItemPosition() == 0)
                        .put("instagram_profile", instagram.getText().toString().trim())
                        .put("telegram_profile", telegram.getText().toString().trim())
                        .put("eitaa_profile", eitaa.getText().toString().trim())
                        .put("bale_profile", bale.getText().toString().trim())
                        .put("strava_profile", strava.getText().toString().trim())
                        .put("relive_profile", relive.getText().toString().trim()));

                if (pendingProfileAvatarUri != null) {
                    java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
                    fields.put("kind", "avatar");
                    api.postMultipart(
                            "profile_image_upload",
                            fields,
                            pendingProfileAvatarUri,
                            "photo",
                            "avatar.jpg",
                            getContentResolver().getType(pendingProfileAvatarUri));
                    pendingProfileAvatarUri = null;
                }

                bootstrap = api.bootstrap();
                activeRide = bootstrap.optJSONObject("my_active_ride");
                main.post(() -> { toast("پروفایل ذخیره شد"); showProfileEditor(); });
            } catch (Exception e) {
                main.post(() -> { save.setEnabled(true); toast(e.getMessage()); });
            }
        });
    }

    private void saveProfilePhoneManual(EditText phone, PedalActionView button) {
        String p = JalaliDate.en(phone.getText().toString().trim());
        button.setEnabled(false);
        io.execute(() -> {
            try {
                api.post("profile_phone_manual", new JSONObject().put("phone", p));
                bootstrap = api.bootstrap();
                activeRide = bootstrap.optJSONObject("my_active_ride");
                main.post(() -> {
                    toast(p.isBlank() ? "شماره مدیر حذف شد" : "شماره مدیر به‌صورت دستی ثبت شد؛ هنوز تأیید پیامکی نشده است.");
                    showProfileEditor();
                });
            } catch (Exception e) {
                main.post(() -> { button.setEnabled(true); toast(e.getMessage()); });
            }
        });
    }

    private void renderProfileMedals(JSONObject user) {
        JSONArray defs = bootstrap == null ? null : bootstrap.optJSONArray("medal_definitions");
        JSONArray awards = bootstrap == null ? null : bootstrap.optJSONArray("medal_awards");
        LinearLayout section = glassCard(24);
        section.addView(text("مدال‌ها", 17, true, TEXT));
        section.addView(text("مدال‌های کسب‌شده اول نمایش داده می‌شوند؛ مدال‌های قفل‌شده خاکستری‌اند.", 11, false, MUTED));

        if (defs == null || defs.length() == 0) {
            section.addView(text("مدالی تعریف نشده است.", 12, false, MUTED));
            content.addView(section, cardLp());
            return;
        }

        int uid = user.optInt("id", 0);
        java.util.ArrayList<JSONObject> sorted = new java.util.ArrayList<>();
        for (int i = 0; i < defs.length(); i++) {
            JSONObject def = defs.optJSONObject(i);
            if (def != null) sorted.add(def);
        }
        sorted.sort((a, b) -> {
            JSONObject aa = medalAwardFor(uid, a.optInt("id", 0), awards);
            JSONObject ba = medalAwardFor(uid, b.optInt("id", 0), awards);
            if ((aa != null) != (ba != null)) return aa != null ? -1 : 1;
            if (aa != null && ba != null) return ba.optString("awarded_at", "").compareTo(aa.optString("awarded_at", ""));
            int ao = a.optInt("sort_order", 100), bo = b.optInt("sort_order", 100);
            return ao != bo ? Integer.compare(ao, bo) : Integer.compare(a.optInt("id", 0), b.optInt("id", 0));
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int limit = Math.min(4, sorted.size());
        for (int i = 0; i < limit; i++) {
            JSONObject def = sorted.get(i);
            JSONObject award = medalAwardFor(uid, def.optInt("id", 0), awards);
            LinearLayout medal = new LinearLayout(this);
            medal.setOrientation(LinearLayout.VERTICAL);
            medal.setGravity(Gravity.CENTER);
            medal.setPadding(dp(7), dp(8), dp(7), dp(8));
            medal.setBackground(roundRect(Color.argb(8, 255, 255, 255), 15, Color.argb(24, 255, 255, 255)));
            medal.setAlpha(award != null ? 1f : .46f);
            medal.setClickable(true);
            medal.setOnClickListener(v -> showMedalDetail(def, award));

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            String imagePath = def.optString("image_path", "");
            if (imagePath.isBlank()) imagePath = "assets/medals/30km.png";
            RemoteImageLoader.load(this, image, imagePath, R.drawable.pedalpro_app_icon);
            medal.addView(image, new LinearLayout.LayoutParams(dp(64), dp(64)));

            TextView title = text(def.optString("title", "مدال"), 10, true, TEXT);
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(2);
            medal.addView(title);
            TextView state = text(
                    award != null ? "دریافت: " + JalaliDate.format(award.optString("awarded_at", ""), true)
                            : def.optInt("is_active", 1) == 1 ? "هنوز کسب نشده" : "غیرفعال",
                    9, false, MUTED);
            state.setGravity(Gravity.CENTER);
            state.setMaxLines(2);
            medal.addView(state);

            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) mlp.setMargins(dp(5), 0, 0, 0);
            row.addView(medal, mlp);
        }
        section.addView(row);
        content.addView(section, cardLp());
    }

    private JSONObject medalAwardFor(int userId, int medalId, JSONArray awards) {
        if (awards == null) return null;
        for (int i = 0; i < awards.length(); i++) {
            JSONObject a = awards.optJSONObject(i);
            if (a != null && a.optInt("user_id", 0) == userId && a.optInt("medal_id", 0) == medalId) return a;
        }
        return null;
    }

    private void showMedalDetail(JSONObject medal, JSONObject award) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = glassCard(20);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.addView(text(medal.optString("title", "مدال"), 20, true, TEXT));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        String path = medal.optString("image_path", "");
        if (path.isBlank()) path = "assets/medals/30km.png";
        RemoteImageLoader.load(this, image, path, R.drawable.pedalpro_app_icon);
        image.setAlpha(award != null ? 1f : .38f);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, dp(160));
        ilp.setMargins(0, dp(10), 0, dp(10));
        box.addView(image, ilp);

        box.addView(text(medal.optString("description", ""), 12, false, TEXT));
        String criteria = medalCriteriaText(medal);
        TextView detail = text(
                "شرط مدال: " + criteria
                        + "\nوضعیت: " + (award != null ? "کسب شده" : "هنوز کسب نشده")
                        + "\nتاریخ و ساعت دریافت: " + (award != null ? JalaliDate.format(award.optString("awarded_at", ""), true) : "—")
                        + (award != null && !award.isNull("metric_value") ? "\nمقدار ثبت‌شده هنگام دریافت: " + award.optString("metric_value") : ""),
                12, false, TEXT);
        detail.setPadding(dp(12), dp(11), dp(12), dp(11));
        detail.setBackground(roundRect(Color.argb(8, 255, 255, 255), 14, Color.argb(28, 255, 255, 255)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.setMargins(0, dp(10), 0, 0);
        box.addView(detail, dlp);

        Button close = normalButton("بستن");
        close.setOnClickListener(v -> d.dismiss());
        box.addView(close);

        d.setContentView(box);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            lp.dimAmount = .66f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        VazirmatnLoader.apply(this, box);
    }

    private String medalCriteriaText(JSONObject medal) {
        String metric = medal.optString("metric_type", "");
        String label;
        switch (metric) {
            case "distance_km": label = "مسافت رکاب‌زنی (km)"; break;
            case "calories_kcal": label = "کالری سوزانده‌شده (kcal)"; break;
            case "ascent_m": label = "صعود تجمعی (m)"; break;
            case "ride_count": label = "تعداد رکاب‌زنی"; break;
            case "weight_loss_kg": label = "کاهش وزن (kg)"; break;
            case "weight_gain_kg": label = "افزایش وزن (kg)"; break;
            case "event_count": label = "تعداد حضور در فعالیت‌ها"; break;
            case "challenge_count": label = "تعداد مشارکت در چالش‌ها"; break;
            case "participation_count": label = "مجموع مشارکت فعالیت + چالش"; break;
            default: label = metric;
        }
        String out = label + ": " + medal.optString("target_value", "—") + " · "
                + (medal.optInt("window_days", 0) > 0 ? "در بازه " + medal.optInt("window_days", 0) + " روزه" : "بدون محدودیت زمانی");
        if (!medal.optString("match_text", "").isBlank()) out += " · فیلتر: " + medal.optString("match_text");
        return out;
    }

    private void renderProfileChallenges(JSONObject user) {
        JSONArray challenges = bootstrap == null ? null : bootstrap.optJSONArray("challenges");
        LinearLayout section = glassCard(24);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        summary.addView(text("🏆 چالش‌های من", 16, true, TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = text("⌄", 16, false, MUTED);
        arrow.setGravity(Gravity.CENTER);
        summary.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(30)));
        section.addView(summary);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        int uid = user.optInt("id", 0);
        int count = 0;
        if (challenges != null) {
            for (int i = 0; i < challenges.length(); i++) {
                JSONObject ch = challenges.optJSONObject(i);
                if (ch == null) continue;
                if (ch.optInt("creator_user_id", 0) != uid && ch.optInt("joined_by_me", 0) != 1) continue;
                Button b = normalButton(ch.optString("title", "چالش"));
                b.setOnClickListener(v -> showChallengeDetail(ch));
                body.addView(b);
                count++;
            }
        }
        if (count == 0) body.addView(text("هنوز در هیچ چالشی عضو نشده‌اید.", 12, false, MUTED));
        summary.setClickable(true);
        summary.setOnClickListener(v -> {
            boolean open = body.getVisibility() != View.VISIBLE;
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            arrow.setText(open ? "⌃" : "⌄");
        });
        section.addView(body);
        content.addView(section, cardLp());
    }

    private void openExternalUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast(e.getMessage()); }
    }

    private void confirmDeleteOwnAccount(EditText password, Button button) {
        String p = password.getText().toString();
        if (p.isBlank()) { toast("رمز عبور را برای تأیید حذف حساب وارد کنید."); return; }
        new android.app.AlertDialog.Builder(this)
                .setMessage("هشدار: حساب، مسیرهای GPS، تصاویر، پیام‌ها و اطلاعات شخصی شما حذف می‌شوند و قابل بازگشت نیستند. ادامه می‌دهید؟")
                .setPositiveButton("حذف دائمی", (dialog, which) -> deleteOwnAccount(p, button))
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void deleteOwnAccount(String password, Button button) {
        button.setEnabled(false);
        io.execute(() -> {
            try {
                api.post("account_delete_self", new JSONObject().put("password", password));
                session.clear();
                bootstrap = null;
                activeRide = null;
                main.post(() -> {
                    toast("حساب و داده‌های شخصی شما حذف شد.");
                    refreshBootstrap(true);
                });
            } catch (Exception e) {
                main.post(() -> { button.setEnabled(true); toast(e.getMessage()); });
            }
        });
    }

    private void logoutFromProfile(Button button) {
        button.setEnabled(false);
        io.execute(() -> {
            try { api.logout(); }
            catch (Exception ignored) { session.clear(); }
            main.post(() -> {
                bootstrap = null;
                activeRide = null;
                refreshBootstrap(true);
            });
        });
    }

    private void showBikes() {
        currentPage = "bikes";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("دوچرخه‌های من", "مدیریت دوچرخه‌ها و عکس هر دوچرخه");

        PedalActionView add = action(PedalIconView.Icon.PLUS,"افزودن دوچرخه",PedalActionView.Style.PRIMARY);
        add.setOnClickListener(v -> showBikeEditor(null));
        content.addView(add,fullActionLp(54));

        JSONArray bikes = bootstrap.optJSONArray("my_bikes");
        if (bikes == null || bikes.length() == 0) { addEmpty("هنوز دوچرخه‌ای ثبت نشده است."); return; }
        for (int i = 0; i < bikes.length(); i++) {
            JSONObject bike = bikes.optJSONObject(i); if (bike == null) continue;
            LinearLayout box = glassCard(24);
            String photo=bike.optString("photo_path","");
            if(!photo.isBlank()){ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setClipToOutline(true);RemoteImageLoader.load(this,im,photo,R.drawable.pedalpro_app_icon);box.addView(im,new LinearLayout.LayoutParams(-1,dp(160)));}
            String title = bike.optString("brand_model", bike.optString("bike_type", "دوچرخه"));
            if (bike.optInt("is_primary", 0) == 1) title = "★ " + title;
            box.addView(text(title, 16, true, TEXT));
            String meta = bike.optString("bike_type", "");
            if (!bike.optString("wheel_size", "").isBlank()) meta += " · چرخ " + bike.optString("wheel_size");
            if (bike.optDouble("weight_kg", 0) > 0) meta += " · " + JalaliDate.fa(bike.optString("weight_kg")) + " kg";
            box.addView(text(meta, 11, false, MUTED));
            PedalActionView edit = action(PedalIconView.Icon.EDIT,"ویرایش",PedalActionView.Style.OUTLINE);
            JSONObject captured = bike; edit.setOnClickListener(v -> showBikeEditor(captured));
            box.addView(edit,fullActionLp(50));
            content.addView(box, cardLp());
        }
        VazirmatnLoader.apply(this,content);animatePageIn();
    }


    private void showBikeEditor(JSONObject bike) {
        currentPage="bike_editor";selectNav(null);content.removeAllViews();
        boolean editing = bike != null;
        pendingBikePhotoUri=null;
        addHero(editing ? "ویرایش دوچرخه" : "افزودن دوچرخه", "اطلاعات برای محاسبات رکاب‌زنی و نگهداری");

        LinearLayout box = glassCard(24);
        EditText brand = input("برند / مدل");
        EditText type = input("نوع دوچرخه");
        EditText frame = input("سایز فریم");
        EditText wheel = input("سایز چرخ");
        EditText color = input("رنگ");
        EditText weight = input("وزن دوچرخه (kg)");
        weight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox primary = new CheckBox(this);primary.setText("دوچرخه اصلی");primary.setTextColor(TEXT);

        if (bike != null) {
            brand.setText(bike.optString("brand_model", ""));type.setText(bike.optString("bike_type", ""));frame.setText(bike.optString("frame_size", ""));wheel.setText(bike.optString("wheel_size", ""));color.setText(bike.optString("color", ""));weight.setText(bike.optString("weight_kg", ""));primary.setChecked(bike.optInt("is_primary", 0) == 1);
            String photo=bike.optString("photo_path","");if(!photo.isBlank()){ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);RemoteImageLoader.load(this,im,photo,R.drawable.pedalpro_app_icon);box.addView(im,new LinearLayout.LayoutParams(-1,dp(170)));}
        }
        box.addView(brand);box.addView(type);box.addView(frame);box.addView(wheel);box.addView(color);box.addView(weight);box.addView(primary);
        PedalActionView photo=action(PedalIconView.Icon.PLUS,"انتخاب عکس دوچرخه",PedalActionView.Style.OUTLINE);photo.setOnClickListener(v->pickImage(REQ_BIKE_PHOTO));box.addView(photo,fullActionLp(50));
        PedalActionView save=action(PedalIconView.Icon.DETAILS,"ذخیره دوچرخه",PedalActionView.Style.PRIMARY);box.addView(save,fullActionLp(54));

        if (editing) {
            PedalActionView delete=action(PedalIconView.Icon.CLOSE,"حذف دوچرخه",PedalActionView.Style.OUTLINE);delete.setAccent(RED);
            delete.setOnClickListener(v -> deleteBike(bike.optInt("id", 0), hiddenButtonProxy(delete)));box.addView(delete,fullActionLp(50));
        }
        content.addView(box,cardLp());

        save.setOnClickListener(v -> {
            save.setEnabled(false);int id = bike == null ? 0 : bike.optInt("id", 0);
            io.execute(() -> {
                try {
                    JSONObject saved=api.post("bike_save",new JSONObject().put("id",id).put("brand_model",brand.getText().toString().trim()).put("bike_type",type.getText().toString().trim()).put("frame_size",frame.getText().toString().trim()).put("wheel_size",wheel.getText().toString().trim()).put("color",color.getText().toString().trim()).put("weight_kg",JalaliDate.en(weight.getText().toString().trim())).put("is_primary",primary.isChecked()));
                    int bikeId=saved.optInt("id",id);
                    if(pendingBikePhotoUri!=null&&bikeId>0){
                        java.util.LinkedHashMap<String,String> fields=new java.util.LinkedHashMap<>();fields.put("bike_id",String.valueOf(bikeId));
                        api.postMultipart("bike_photo_upload",fields,pendingBikePhotoUri,"photo","bike.jpg",getContentResolver().getType(pendingBikePhotoUri));
                        pendingBikePhotoUri=null;
                    }
                    bootstrap=api.bootstrap();activeRide=bootstrap.optJSONObject("my_active_ride");
                    main.post(()->{toast("دوچرخه ذخیره شد.");showBikes();});
                }catch(Exception e){main.post(()->{save.setEnabled(true);showInlineMessage("ذخیره دوچرخه انجام نشد",e.getMessage(),RED);});}
            });
        });
        VazirmatnLoader.apply(this,content);
    }


    private void deleteBike(int id, Button button) {
        if (id <= 0) return;
        button.setEnabled(false);
        io.execute(() -> {
            try {
                api.post("bike_delete", new JSONObject().put("id", id));
                bootstrap = api.bootstrap();
                activeRide = bootstrap.optJSONObject("my_active_ride");
                main.post(() -> {
                    toast("دوچرخه حذف شد.");
                    showBikes();
                });
            } catch (Exception e) {
                main.post(() -> {
                    button.setEnabled(true);
                    toast(e.getMessage());
                });
            }
        });
    }

    private void showFitness() {
        currentPage = "fitness";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("وضعیت بدن", "وزن و شاخص‌های بدنی · تاریخ شمسی");

        LinearLayout add = card();
        EditText date = input("تاریخ شمسی YYYY/MM/DD");
        date.setText(JalaliDate.todayForInput());
        EditText weight = input("وزن (kg)");
        weight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText fat = input("درصد چربی");
        fat.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText note = input("یادداشت");
        Button save = primaryButton("ثبت وضعیت");
        add.addView(date); add.addView(weight); add.addView(fat); add.addView(note); add.addView(save);
        content.addView(add, cardLp());

        save.setOnClickListener(v -> {
            if (weight.getText().toString().trim().isBlank()) { showInlineMessage("اطلاعات ناقص","وزن را وارد کنید.",RED); return; }
            String gregorian=JalaliDate.toGregorianIso(date.getText().toString().trim());
            if(gregorian.isBlank()){showInlineMessage("تاریخ نامعتبر","تاریخ را به شکل ۱۴۰۵/۰۷/۲۱ وارد کنید.",RED);return;}
            save.setEnabled(false);
            io.execute(() -> {
                try {
                    api.post("body_metric_add", new JSONObject()
                            .put("measured_on", gregorian)
                            .put("weight_kg", JalaliDate.en(weight.getText().toString().trim()))
                            .put("body_fat_pct", JalaliDate.en(fat.getText().toString().trim()))
                            .put("note", note.getText().toString().trim()));
                    bootstrap = api.bootstrap();
                    activeRide = bootstrap.optJSONObject("my_active_ride");
                    main.post(() -> { toast("وضعیت بدن ثبت شد."); showFitness(); });
                } catch (Exception e) {
                    main.post(() -> { save.setEnabled(true); showInlineMessage("ثبت انجام نشد",e.getMessage(),RED); });
                }
            });
        });

        JSONArray metrics = bootstrap.optJSONArray("body_metrics");
        if (metrics == null || metrics.length() == 0) { addEmpty("هنوز داده بدنی ثبت نشده است."); return; }
        for (int i = metrics.length() - 1; i >= 0; i--) {
            JSONObject m = metrics.optJSONObject(i);
            if (m == null) continue;
            LinearLayout box = card();
            box.addView(text(JalaliDate.format(m.optString("measured_on", ""),false), 11, true, MUTED));
            box.addView(text(JalaliDate.fa(m.optString("weight_kg", "—")) + " kg", 20, true, Color.rgb(31,231,242)));
            if (!m.optString("body_fat_pct", "").isBlank()) box.addView(text("چربی بدن: " + JalaliDate.fa(m.optString("body_fat_pct")) + "%", 11, false, MUTED));
            if (!m.optString("note", "").isBlank()) box.addView(text(m.optString("note"), 11, false, MUTED));
            content.addView(box, cardLp());
        }
        VazirmatnLoader.apply(this,content);animatePageIn();
    }


    private void showMe() {
        currentPage = "me";
        if (!signedIn()) { showLogin(); return; }
        selectNav(navMe);
        content.removeAllViews();
        JSONObject user = bootstrap.optJSONObject("user");

        LinearLayout profile=glassCard(28);
        ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setClipToOutline(true);avatar.setBackground(roundRect(Color.TRANSPARENT,50,Color.argb(105,245,249,252)));
        RemoteImageLoader.load(this,avatar,user==null?"":user.optString("avatar_path",""),R.drawable.pedalpro_app_icon);
        LinearLayout avw=new LinearLayout(this);avw.setGravity(Gravity.CENTER);avw.addView(avatar,new LinearLayout.LayoutParams(dp(96),dp(96)));profile.addView(avw);
        TextView name=text(user==null?"حساب من":user.optString("display_name","حساب من"),22,true,TEXT);name.setGravity(Gravity.CENTER);profile.addView(name);
        if(user!=null&&!user.optString("username","").isBlank()){TextView username=text("@"+user.optString("username"),11,false,MUTED);username.setGravity(Gravity.CENTER);profile.addView(username);}
        boolean verified=user!=null&&(!user.optString("phone_verified_at","").isBlank()||user.optInt("phone_verified",0)==1);
        TextView phone=text(verified?"شماره تلفن تأیید شده":"شماره تلفن نیاز به تأیید دارد",11,true,verified?GREEN:ORANGE);phone.setGravity(Gravity.CENTER);phone.setPadding(0,dp(5),0,0);profile.addView(phone);
        content.addView(profile,cardLp());

        PedalActionView edit=action(PedalIconView.Icon.EDIT,"ویرایش پروفایل و شماره تماس",PedalActionView.Style.PRIMARY);edit.setOnClickListener(v->showProfileEditor());content.addView(edit,fullActionLp(54));
        PedalActionView bikes=action(PedalIconView.Icon.RIDE,"دوچرخه‌های من",PedalActionView.Style.OUTLINE);bikes.setOnClickListener(v->showBikes());content.addView(bikes,fullActionLp(52));
        PedalActionView fitness=action(PedalIconView.Icon.CHART,"وضعیت بدن",PedalActionView.Style.OUTLINE);fitness.setOnClickListener(v->showFitness());content.addView(fitness,fullActionLp(52));
        PedalActionView safety=action(PedalIconView.Icon.SHIELD,"ایمنی و حریم خصوصی",PedalActionView.Style.OUTLINE);safety.setOnClickListener(v->startActivity(new Intent(this,NativeSafetyActivity.class)));content.addView(safety,fullActionLp(52));
        PedalActionView refresh=action(PedalIconView.Icon.REFRESH,"بروزرسانی اطلاعات",PedalActionView.Style.OUTLINE);refresh.setOnClickListener(v->refreshAndRender(this::showMe));content.addView(refresh,fullActionLp(50));
        PedalActionView logout=action(PedalIconView.Icon.CLOSE,"خروج از حساب",PedalActionView.Style.OUTLINE);logout.setAccent(RED);logout.setOnClickListener(v->io.execute(()->{try{api.logout();}catch(Exception ignored){session.clear();}main.post(()->{bootstrap=null;activeRide=null;showLogin();});}));content.addView(logout,fullActionLp(50));
        VazirmatnLoader.apply(this,content);animatePageIn();
    }


    private void addLiveRidesSection(int limit) {
        JSONArray rides = bootstrap.optJSONArray("rides");
        JSONObject user = bootstrap.optJSONObject("user");
        int uid = user == null ? 0 : user.optInt("id", 0);
        int count = 0;
        if (rides != null) {
            for (int i = 0; i < rides.length() && count < limit; i++) {
                JSONObject r = rides.optJSONObject(i);
                if (r == null || !"active".equals(r.optString("status")) || r.optInt("owner_user_id", 0) == uid) continue;
                addRideSummaryCard(r, true);
                count++;
            }
        }
        if (count == 0) addCard("رکاب‌زنی زنده دیگران", "در حال حاضر رکاب‌زن زنده دیگری دیده نمی‌شود.");
    }

    private void addRideSummaryCard(JSONObject r, boolean navigation) {
        LinearLayout box = card();
        boolean live = "active".equals(r.optString("status"));
        int rideId = r.optInt("id", 0);
        TextView title = text((live ? "● " : "") + r.optString("owner_name", "PedalPro") + " · " + r.optString("title", "رکاب‌زنی"),
                15, true, live ? ORANGE : TEXT);
        String meta = String.format(Locale.US, "%.2f km · %s · %.1f km/h",
                r.optDouble("distance_km", r.optDouble("distance_m", 0) / 1000.0),
                formatDuration(r.optLong("duration_seconds", 0)),
                r.optDouble("avg_speed_kmh", 0));
        box.addView(title);
        box.addView(text(meta, 11, false, MUTED));

        if (rideId > 0) {
            Button details = normalButton("جزئیات و مسیر Native");
            details.setOnClickListener(v -> {
                Intent i = new Intent(this, NativeRideDetailActivity.class);
                i.putExtra(NativeRideDetailActivity.EXTRA_RIDE_ID, rideId);
                startActivity(i);
            });
            box.addView(details);
        }

        if (navigation && live) {
            Button nav = primaryButton("مسیریابی زنده تا این رکاب‌زن");
            nav.setOnClickListener(v -> {
                Intent i = new Intent(this, NativeLiveNavigationActivity.class);
                i.putExtra(NativeLiveNavigationActivity.EXTRA_MODE, "live");
                i.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID, rideId);
                i.putExtra(NativeLiveNavigationActivity.EXTRA_TITLE, r.optString("owner_name", "رکاب‌زن"));
                startActivity(i);
            });
            box.addView(nav);
        }
        content.addView(box, cardLp());
    }


    private void addEventCard(JSONObject event) {
        FrameLayout shell = new FrameLayout(this);
        GradientDrawable outer = roundRect(Color.argb(188, 7, 31, 45), 25, Color.argb(100, 245, 249, 252));
        shell.setBackground(outer);
        shell.setElevation(dp(3));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(15), dp(17), dp(15));

        String title = event.optString("title", "برنامه");
        box.addView(text(title, 20, true, TEXT));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        meta.setGravity(Gravity.CENTER);
        String date = JalaliDate.format(event.optString("event_date", ""), true);
        meta.addView(metaTile(PedalIconView.Icon.CALENDAR, date), rowWeightLp());
        meta.addView(metaTile(PedalIconView.Icon.LOCATION, event.optString("meeting_location", "محل قرار")), rowWeightLp());
        double planned = event.optDouble("planned_distance", 0);
        meta.addView(metaTile(PedalIconView.Icon.DISTANCE, planned > 0 ? JalaliDate.fa(Math.round(planned)) + " کیلومتر" : "مسافت آزاد"), rowWeightLp());
        box.addView(meta);

        int regs = eventRegistrationCount(event.optInt("id", 0));
        TextView badge = text("اعضا: " + JalaliDate.fa(regs), 11, true, Color.rgb(140, 205, 255));
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundRect(Color.argb(120, 4, 65, 105), 18, 0));
        LinearLayout badgeWrap = new LinearLayout(this); badgeWrap.setGravity(Gravity.END);
        badgeWrap.addView(badge, new LinearLayout.LayoutParams(dp(74), dp(34)));
        box.addView(badgeWrap);

        String desc = event.optString("description", "");
        if (!desc.isBlank()) {
            TextView d = text(desc, 13, false, TEXT);
            d.setPadding(0, dp(8), 0, dp(5));
            box.addView(d);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        PedalActionView details = action(PedalIconView.Icon.DETAILS, "جزئیات", PedalActionView.Style.OUTLINE);
        details.setOnClickListener(v -> showEventDetail(event));
        PedalActionView share = action(PedalIconView.Icon.SHARE, "اشتراک‌گذاری", PedalActionView.Style.OUTLINE);
        share.setOnClickListener(v -> shareEvent(event));
        PedalActionView nav = action(PedalIconView.Icon.ROUTE, "مسیریابی تا شروع", PedalActionView.Style.OUTLINE);
        nav.setOnClickListener(v -> {
            double lat = event.optDouble("meeting_lat", Double.NaN), lng = event.optDouble("meeting_lng", Double.NaN);
            if (Double.isFinite(lat) && Double.isFinite(lng)) openNeshanPoint(lat, lng);
            else showInlineMessage("مسیریابی", "مختصات محل شروع برای این برنامه ثبت نشده است.", ORANGE);
        });
        actions.addView(details, actionWeightLp());
        actions.addView(share, actionWeightLp());
        actions.addView(nav, actionWeightLp());
        box.addView(actions);

        boolean registered = isRegisteredForEvent(event.optInt("id", 0));
        if (event.optInt("registration_open", 1) == 1 && "planned".equals(event.optString("status", "planned"))) {
            PedalActionView reg = action(registered ? PedalIconView.Icon.CLOSE : PedalIconView.Icon.PLUS,
                    registered ? "لغو ثبت‌نام" : "ثبت‌نام", registered ? PedalActionView.Style.OUTLINE : PedalActionView.Style.PRIMARY);
            int eventId = event.optInt("id", 0);
            reg.setOnClickListener(v -> toggleEventRegistration(eventId, !registered, hiddenButtonProxy(reg)));
            box.addView(reg, fullActionLp(52));
        }

        if (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1) {
            PedalActionView edit = action(PedalIconView.Icon.EDIT, "ویرایش برنامه", PedalActionView.Style.OUTLINE);
            edit.setOnClickListener(v -> showEventEditor(event));
            box.addView(edit, fullActionLp(50));
        }

        shell.addView(box, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        View stripe = new View(this);
        stripe.setBackgroundColor(event.optInt("id", 0) % 2 == 0 ? GREEN : Color.rgb(43,146,255));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(5), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END);
        slp.setMargins(0, dp(10), 0, dp(10));
        shell.addView(stripe, slp);
        content.addView(shell, cardLp());
    }


    private boolean isRegisteredForEvent(int eventId) {
        if (eventId <= 0 || bootstrap == null) return false;
        JSONArray regs = bootstrap.optJSONArray("registrations");
        JSONObject user = bootstrap.optJSONObject("user");
        int uid = user == null ? 0 : user.optInt("id", 0);
        if (regs == null || uid <= 0) return false;
        for (int i = 0; i < regs.length(); i++) {
            JSONObject r = regs.optJSONObject(i);
            if (r != null && r.optInt("event_id", 0) == eventId && r.optInt("user_id", 0) == uid) return true;
        }
        return false;
    }

    private void toggleEventRegistration(int eventId, boolean join, Button button) {
        if (eventId <= 0) return;
        if (!signedIn()) {
            showLogin();
            return;
        }
        setProxyEnabled(button, false);
        io.execute(() -> {
            try {
                if (join) api.post("register_event", new JSONObject().put("event_id", eventId));
                else api.post("unregister_event", new JSONObject().put("event_id", eventId));
                bootstrap = api.bootstrap();
                activeRide = bootstrap.optJSONObject("my_active_ride");
                main.post(() -> {
                    updateHeaderAccess(bootstrap);
                    applyUiSettings(bootstrap);
                    toast(join ? "ثبت‌نام انجام شد." : "ثبت‌نام لغو شد.");
                    showActivities();
                });
            } catch (Exception e) {
                main.post(() -> {
                    setProxyEnabled(button, true);
                    toast(e.getMessage());
                });
            }
        });
    }

    private void openNeshanPoint(double lat, double lng) {
        try {
            Intent neshan = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://nshn.ir?lat=" + lat + "&lng=" + lng));
            neshan.setPackage("org.rajman.neshan.traffic.tehran.navigator");
            startActivity(neshan);
            return;
        } catch (Exception ignored) { }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng)));
        } catch (Exception e) {
            toast("مسیریاب نشان یا برنامه نقشه روی گوشی پیدا نشد.");
        }
    }

    private void startGpsPreflight(TextView state, TextView accuracy, Button start, Button check) {
        if (gpsChecking) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            state.setText("برای ادامه دسترسی دقیق Location را فعال کنید.");
            state.setTextColor(ORANGE);
            return;
        }

        gpsChecking = true;
        pendingFreshFix = null;
        setProxyEnabled(start,false);
        setProxyEnabled(check,false);
        state.setText("در حال دریافت موقعیت تازه و دقیق…");
        state.setTextColor(ACCENT);
        accuracy.setText("دقت: در حال بررسی");

        CancellationTokenSource token = new CancellationTokenSource();
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.getToken())
                .addOnSuccessListener(this, loc -> {
                    gpsChecking = false;
                    setProxyEnabled(check,true);
                    if (loc == null) {
                        state.setText("موقعیت دریافت نشد. دوباره بررسی کنید.");
                        state.setTextColor(RED);
                        return;
                    }
                    long age = Math.max(0, System.currentTimeMillis() - loc.getTime());
                    float acc = loc.hasAccuracy() ? loc.getAccuracy() : 999f;
                    accuracy.setText("دقت: ±" + Math.round(acc) + " متر · تازگی " + (age / 1000) + " ثانیه");
                    if (age <= 12000 && acc <= 35f) {
                        pendingFreshFix = loc;
                        state.setText("GPS آماده است. ثبت مسیر از همین موقعیت شروع می‌شود.");
                        state.setTextColor(GREEN);
                        setProxyEnabled(start,true);
                    } else {
                        state.setText("GPS هنوز به دقت مناسب نرسیده است. چند لحظه در فضای باز بمانید.");
                        state.setTextColor(ORANGE);
                    }
                })
                .addOnFailureListener(this, e -> {
                    gpsChecking = false;
                    setProxyEnabled(check,true);
                    state.setText("خطای GPS: " + e.getMessage());
                    state.setTextColor(RED);
                });
    }

    private void createRideAndStartTracking(Button start, TextView state) {
        if (pendingFreshFix == null) return;
        setProxyEnabled(start,false);
        state.setText("در حال شروع رکاب‌زنی…");
        Location fix = pendingFreshFix;

        io.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("challenge_id", 0)
                        .put("event_id", 0)
                        .put("title", "رکاب‌زنی من")
                        .put("public_tracking", false)
                        .put("viewer_user_ids", new JSONArray())
                        .put("bike_id", 0);

                JSONObject created = api.post("start_ride", body);
                int rideId = created.optInt("ride_id", 0);
                if (rideId <= 0) throw new IllegalStateException("شناسه رکاب از سرور دریافت نشد.");

                JSONObject point = new JSONObject()
                        .put("ride_id", rideId)
                        .put("lat", fix.getLatitude())
                        .put("lng", fix.getLongitude())
                        .put("altitude", fix.hasAltitude() ? fix.getAltitude() : JSONObject.NULL)
                        .put("speed_kmh", fix.hasSpeed() ? fix.getSpeed() * 3.6 : 0)
                        .put("accuracy", fix.hasAccuracy() ? fix.getAccuracy() : JSONObject.NULL)
                        .put("timestamp", System.currentTimeMillis());
                try { api.post("track_point", point); } catch (Exception ignored) { }

                JSONObject d = api.bootstrap();
                bootstrap = d;
                activeRide = d.optJSONObject("my_active_ride");
                if (activeRide == null) {
                    activeRide = new JSONObject().put("id", rideId).put("title", "رکاب‌زنی من").put("duration_seconds", 0);
                }
                rideBaseSeconds = activeRide.optLong("duration_seconds", 0);
                rideTimerAnchorElapsed = SystemClock.elapsedRealtime();
                startTrackingService(rideId);

                main.post(() -> {
                    pendingFreshFix = null;
                    showRide();
                    toast("ثبت مسیر شروع شد.");
                });
            } catch (Exception e) {
                main.post(() -> {
                    setProxyEnabled(start,true);
                    state.setText("شروع انجام نشد: " + e.getMessage());
                    state.setTextColor(RED);
                });
            }
        });
    }

    private void toggleManualPause(PedalActionView button){
        if(activeRide==null)return;
        int rideId=activeRide.optInt("id",0);if(rideId<=0)return;
        if(!manualPaused){
            long elapsed=rideTimerAnchorElapsed>0?Math.max(0,(SystemClock.elapsedRealtime()-rideTimerAnchorElapsed)/1000L):0;
            rideBaseSeconds+=elapsed;rideTimerAnchorElapsed=0;manualPaused=true;
            Intent stop=new Intent(this,TrackingService.class);stop.setAction(TrackingService.ACTION_STOP);startService(stop);
            if(rideSpeed!=null)rideSpeed.setText("0.0");if(rideGps!=null){rideGps.setText("توقف دستی · GPS موقتاً متوقف");rideGps.setTextColor(ORANGE);}
            button.setLabel("ادامه ثبت");
        }else{
            manualPaused=false;rideTimerAnchorElapsed=SystemClock.elapsedRealtime();startTrackingService(rideId);
            if(rideGps!=null){rideGps.setText("ثبت GPS دوباره فعال شد");rideGps.setTextColor(GREEN);}button.setLabel("توقف ثبت");
        }
    }

    private void loadActiveRideRoute(){
        if(activeRideRouteView==null||activeRide==null)return;
        int id=activeRide.optInt("id",0);if(id<=0)return;
        io.execute(()->{
            try{
                java.util.LinkedHashMap<String,Object> q=new java.util.LinkedHashMap<>();q.put("ride_id",id);q.put("point_mode","nav");
                JSONObject d=api.get("ride_details",q);JSONObject r=d.optJSONObject("ride");JSONArray pts=r==null?null:r.optJSONArray("points");
                main.post(()->{if(activeRideRouteView!=null)activeRideRouteView.setPoints(pts);});
            }catch(Exception ignored){}
        });
    }

    private void confirmFinishRide(View trigger){
        Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=glassCard(28);box.addView(text("پایان رکاب‌زنی؟",20,true,TEXT));box.addView(text("مسیر و آمار نهایی ذخیره می‌شود.",12,false,MUTED));
        PedalActionView yes=action(PedalIconView.Icon.DETAILS,"ذخیره و پایان",PedalActionView.Style.PRIMARY);
        PedalActionView no=action(PedalIconView.Icon.BACK,"ادامه رکاب‌زنی",PedalActionView.Style.OUTLINE);
        yes.setOnClickListener(v->{d.dismiss();finishRide(hiddenButtonProxy(trigger));});no.setOnClickListener(v->d.dismiss());
        box.addView(yes,fullActionLp(54));box.addView(no,fullActionLp(50));d.setContentView(box);d.show();
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));WindowManager.LayoutParams lp=new WindowManager.LayoutParams();lp.copyFrom(w.getAttributes());lp.width=-1;lp.height=-2;lp.gravity=Gravity.BOTTOM;w.setAttributes(lp);}
        VazirmatnLoader.apply(this,box);
    }

    private void startTrackingService(int rideId) {
        if (rideId <= 0) return;
        Intent i = new Intent(this, TrackingService.class);
        i.setAction(TrackingService.ACTION_START);
        i.putExtra("ride_id", rideId);
        i.putExtra("csrf", session.csrf());
        i.putExtra("max_accuracy", 25d);
        i.putExtra("max_speed_kmh", 100d);
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(this, i);
        else startService(i);
    }

    private void finishRide(Button button) {
        if (activeRide == null) return;
        int rideId = activeRide.optInt("id", 0);
        if (rideId <= 0) return;
        setProxyEnabled(button,false);
        button.setText("در حال پایان…");

        io.execute(() -> {
            try {
                Intent stop = new Intent(this, TrackingService.class);
                stop.setAction(TrackingService.ACTION_STOP);
                startService(stop);
                api.post("stop_ride", new JSONObject().put("ride_id", rideId).put("notes", ""));
                JSONObject d = api.bootstrap();
                bootstrap = d;
                activeRide = d.optJSONObject("my_active_ride");
                rideTimerAnchorElapsed = 0;
                rideBaseSeconds = 0;
                main.post(() -> {
                    toast("رکاب‌زنی ذخیره شد.");
                    showActivities();
                });
            } catch (Exception e) {
                main.post(() -> {
                    setProxyEnabled(button,true);
                    button.setText("پایان رکاب‌زنی");
                    toast(e.getMessage());
                });
            }
        });
    }

    private boolean isTrackingServiceActive() {
        return getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE).getBoolean("active", false);
    }

    private String uiString(String key, String fallback) {
        JSONObject ui = bootstrap == null ? null : bootstrap.optJSONObject("ui");
        if (ui == null) return fallback;
        String value = ui.optString(key, "").trim();
        return value.isBlank() ? fallback : value;
    }

    private int uiInt(String key, int fallback, int min, int max) {
        JSONObject ui = bootstrap == null ? null : bootstrap.optJSONObject("ui");
        int value = ui == null ? fallback : ui.optInt(key, fallback);
        return Math.max(min, Math.min(max, value));
    }

    private void applyUiSettings(JSONObject data) {
        int topGap = uiInt("mobile_header_top_gap_px", 20, 6, 50);
        if (webHeader != null) webHeader.setPadding(dp(10), dp(topGap), dp(10), dp(14));
        if (rideDockLabel != null) {
            String label = uiString("ride_button_text", "شروع").trim();
            rideDockLabel.setText(label + "\nرکاب");
        }
    }

    private void homeRegisterNextEventNative() {
        JSONObject event = nextUpcomingEvent();
        if (event == null) {
            showActivities();
            return;
        }
        if (!signedIn()) {
            showAuthDialog("signup");
            return;
        }
        int eventId = event.optInt("id", 0);
        if (isRegisteredForEvent(eventId)) {
            toast("شما قبلاً در این برنامه ثبت‌نام کرده‌اید.");
            showEventDetail(event);
            return;
        }
        io.execute(() -> {
            try {
                api.post("register_event", new JSONObject().put("event_id", eventId));
                bootstrap = api.bootstrap();
                activeRide = bootstrap.optJSONObject("my_active_ride");
                main.post(() -> {
                    updateHeaderAccess(bootstrap);
                    applyUiSettings(bootstrap);
                    toast("ثبت‌نام انجام شد.");
                    showHome();
                });
            } catch (Exception e) {
                main.post(() -> toast(e.getMessage()));
            }
        });
    }

    private boolean signedIn() {
        return bootstrap != null && bootstrap.optJSONObject("user") != null;
    }

    private int liveCount() {
        JSONArray rides = bootstrap == null ? null : bootstrap.optJSONArray("rides");
        JSONObject user = bootstrap == null ? null : bootstrap.optJSONObject("user");
        int uid = user == null ? 0 : user.optInt("id", 0);
        int count = 0;
        if (rides != null) {
            for (int i = 0; i < rides.length(); i++) {
                JSONObject r = rides.optJSONObject(i);
                if (r != null && "active".equals(r.optString("status")) && r.optInt("owner_user_id", 0) != uid) count++;
            }
        }
        return count;
    }

    private int countArray(String key) {
        JSONArray a = bootstrap == null ? null : bootstrap.optJSONArray(key);
        return a == null ? 0 : a.length();
    }

    private int countMyCompletedRides() {
        if (bootstrap == null) return 0;
        JSONArray direct = bootstrap.optJSONArray("my_completed_rides");
        if (direct != null) return direct.length();
        JSONArray rides = bootstrap.optJSONArray("rides");
        JSONObject me = bootstrap.optJSONObject("user");
        int uid = me == null ? 0 : me.optInt("id", 0);
        int count = 0;
        if (rides != null) {
            for (int i = 0; i < rides.length(); i++) {
                JSONObject r = rides.optJSONObject(i);
                if (r == null || !"completed".equals(r.optString("status"))) continue;
                int owner = r.optInt("owner_user_id", uid);
                if (uid == 0 || owner == 0 || owner == uid) count++;
            }
        }
        return count;
    }

    private void refreshVisibleScreen() {
        switch (currentPage) {
            case "ride": showRide(); break;
            case "activities": showActivities(); break;
            case "routes": showRoutes(); break;
            case "club": showClub(); break;
            case "challenges": showChallenges(); break;
            case "chat": showGroupChat(); break;
            case "ai": showAiAdvisor(); break;
            case "performance": showPerformance(); break;
            case "sensors": showSensors(); break;
            case "profile": showProfileEditor(); break;
            case "bikes": showBikes(); break;
            case "fitness": showFitness(); break;
            case "me": showMe(); break;
            default: showHome(); break;
        }
    }


    private void setTopStatus(String value, int color) {
        main.post(() -> {
            if (topStatus != null) {
                topStatus.setText(value);
                topStatus.setTextColor(color);
            }
        });
    }

    private void showError(String title, String body, Runnable retry) {
        selectNav(null);
        content.removeAllViews();
        addHero(title, body == null ? "" : body);
        Button b = primaryButton("تلاش دوباره");
        b.setOnClickListener(v -> retry.run());
        content.addView(b);
    }

    private void addEmpty(String value) {
        addCard("PedalPro", value);
    }

    private void addHero(String title, String sub) {
        LinearLayout box = glassCard(26);
        box.addView(text(title, 22, true, TEXT));
        TextView s = text(sub, 13, false, MUTED);
        s.setPadding(0, dp(7), 0, 0);
        box.addView(s);
        content.addView(box, cardLp());
    }


    private void addCard(String title, String body) {
        LinearLayout box = glassCard(22);
        box.addView(text(title, 16, true, TEXT));
        TextView b = text(body, 12, false, MUTED);
        b.setPadding(0, dp(7), 0, 0);
        box.addView(b);
        content.addView(box, cardLp());
    }


    private LinearLayout card() {
        return glassCard(22);
    }


    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        return lp;
    }

    private LinearLayout.LayoutParams weightLp() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightHeightLp(int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private Button navButton(String label, Runnable action) {
        Button b = plainButton(label, 11, TEXT);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v -> action.run());
        return b;
    }


    private void tintBottomItem(View view, boolean active) {
        if (view == null) return;
        int color = active ? Color.rgb(31, 231, 242) : Color.rgb(238, 248, 255);
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        if (view instanceof PedalIconView) {
            ((PedalIconView) view).setColor(color);
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof LinearLayout) {
            LinearLayout group = (LinearLayout) view;
            for (int i = 0; i < group.getChildCount(); i++) tintBottomItem(group.getChildAt(i), active);
        } else if (view instanceof FrameLayout) {
            FrameLayout group = (FrameLayout) view;
            for (int i = 0; i < group.getChildCount(); i++) tintBottomItem(group.getChildAt(i), active);
        }
    }

    private void selectNav(View selected) {
        tintBottomItem(navHome, selected == navHome);
        tintBottomItem(navActivities, selected == navActivities);
        tintBottomItem(navRoutes, selected == navRoutes);
        tintBottomItem(navMe, selected == navMe);
        if (navRide != null) {
            navRide.setAlpha(1f);
            navRide.setScaleX(1f);
            navRide.setScaleY(1f);
        }
    }


    private TextView metric(String label, int value) {
        return metricValue(label, String.valueOf(value));
    }

    private TextView metricValue(String label, String value) {
        TextView t = text(value, 18, true, ACCENT);
        t.setTag(label);
        return t;
    }

    private View wrapMetric(TextView value, String label) {
        LinearLayout box = card();
        box.setGravity(Gravity.CENTER);
        box.addView(value);
        box.addView(text(label, 10, false, MUTED));
        return box;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setGravity(Gravity.END);
        v.setLineSpacing(dp(2),1f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }


    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(12), dp(12), dp(12));
        return e;
    }

    private Button primaryButton(String label) {
        Button b = plainButton(label, 13, Color.rgb(3,31,40));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(31,231,242),Color.rgb(33,205,240),Color.rgb(43,178,255)});
        g.setCornerRadius(dp(17));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(9), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }


    private Button dangerButton(String label) {
        Button b = plainButton(label, 13, Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(roundRect(Color.argb(225,130,37,51),17,Color.argb(130,255,130,150)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(9), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }


    private Button normalButton(String label) {
        Button b = plainButton(label, 13, TEXT);
        b.setBackground(roundRect(Color.argb(120,10,39,55),17,Color.argb(100,245,249,252)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }


    private LinearLayout buildWebHeader() {
        webHeader = new LinearLayout(this);
        webHeader.setOrientation(LinearLayout.HORIZONTAL);
        webHeader.setGravity(Gravity.CENTER_VERTICAL);
        webHeader.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        webHeader.setPadding(dp(10), dp(20), dp(10), dp(14));
        webHeader.setMinimumHeight(dp(72));
        webHeader.setBackground(headerGradient());

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setContentDescription("PedalPro");
        webHeader.addView(logo, new LinearLayout.LayoutParams(dp(108), dp(54)));

        webHeader.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        headerAccount = action(PedalIconView.Icon.USER, "ورود", PedalActionView.Style.OUTLINE);
        headerAccount.setOnClickListener(v -> {
            if (signedIn()) showMe();
            else showLogin();
        });
        webHeader.addView(headerAccount, new LinearLayout.LayoutParams(dp(105), dp(46)));

        headerAdminSettings = iconSquare(PedalIconView.Icon.SETTINGS, this::showSiteSettingsPage, "تنظیمات سایت");
        headerAdminSettings.setVisibility(View.GONE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(46), dp(46));
        sp.setMargins(dp(6), 0, 0, 0);
        webHeader.addView(headerAdminSettings, sp);

        FrameLayout menu = iconSquare(PedalIconView.Icon.MENU, this::showDrawer, "منو");
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(46), dp(46));
        mp.setMargins(dp(6), 0, 0, 0);
        webHeader.addView(menu, mp);

        topStatus = new TextView(this);
        topStatus.setVisibility(View.GONE);
        return webHeader;
    }

    private LinearLayout buildWebBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        nav.setClipChildren(false);
        nav.setPadding(dp(10), dp(16), dp(10), dp(10));
        nav.setBackground(bottomGradient());

        navHome = bottomIconItem(PedalIconView.Icon.HOME, "خانه", this::showHome);
        navActivities = bottomIconItem(PedalIconView.Icon.CALENDAR, "فعالیت‌ها", this::showActivities);
        navRide = centerRideItem();
        navRoutes = bottomIconItem(PedalIconView.Icon.ROUTE, "مسیرها", this::showRoutes);
        navMe = bottomIconItem(PedalIconView.Icon.USER, "من", this::showMe);

        nav.addView(navHome, bottomWeightLp(1f, 90));
        nav.addView(navActivities, bottomWeightLp(1f, 90));

        LinearLayout center = new LinearLayout(this);
        center.setGravity(Gravity.CENTER);
        center.setClipChildren(false);
        navRide.setTranslationY(-dp(38));
        center.addView(navRide, new LinearLayout.LayoutParams(dp(106), dp(106)));
        nav.addView(center, new LinearLayout.LayoutParams(0, dp(106), 1.12f));

        nav.addView(navRoutes, bottomWeightLp(1f, 90));
        nav.addView(navMe, bottomWeightLp(1f, 90));
        return nav;
    }

    

    private View bottomIconItem(PedalIconView.Icon iconType, String label, Runnable run) {
        LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setGravity(Gravity.CENTER);
        host.setClickable(true);
        host.setFocusable(true);
        host.setPadding(dp(6), dp(10), dp(6), dp(8));
        host.setContentDescription(label);

        PedalIconView icon = new PedalIconView(this, iconType, Color.rgb(238, 248, 255));
        host.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView tv = text(label, 11, true, Color.rgb(238, 248, 255));
        tv.setTextSize(11.5f);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.setMargins(0, dp(9), 0, 0);
        host.addView(tv, tlp);

        host.setOnClickListener(v -> run.run());
        return host;
    }

    private View centerRideItem() {
        FrameLayout ring = new FrameLayout(this);
        GradientDrawable outer = new GradientDrawable();
        outer.setShape(GradientDrawable.OVAL);
        outer.setColor(Color.argb(199, 16, 49, 71));
        ring.setBackground(outer);
        ring.setElevation(dp(12));
        ring.setClickable(true);
        ring.setFocusable(true);
        ring.setContentDescription("شروع رکاب");

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        inner.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable cyan = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(29, 239, 244), Color.rgb(21, 209, 238), Color.rgb(43, 178, 255)});
        cyan.setShape(GradientDrawable.OVAL);
        inner.setBackground(cyan);

        rideDockLabel = text("شروع\nرکاب", 16, true, Color.rgb(6, 39, 55));
        rideDockLabel.setGravity(Gravity.CENTER);
        rideDockLabel.setIncludeFontPadding(false);
        rideDockLabel.setLineSpacing(0, 1.08f);
        inner.addView(rideDockLabel, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(86), dp(86), Gravity.CENTER);
        ring.addView(inner, ilp);
        ring.setOnClickListener(v -> showRide());
        return ring;
    }

    private FrameLayout iconSquare(PedalIconView.Icon icon,Runnable run,String description) {
        FrameLayout f = new FrameLayout(this); f.setBackground(roundRect(Color.argb(100,6,38,54),17,Color.argb(100,245,249,252)));
        PedalIconView iv = new PedalIconView(this,icon,TEXT); f.addView(iv,new FrameLayout.LayoutParams(dp(29),dp(29),Gravity.CENTER));
        f.setClickable(true); f.setFocusable(true); f.setContentDescription(description); f.setOnClickListener(v -> run.run()); return f;
    }

    private PedalActionView action(PedalIconView.Icon icon,String label,PedalActionView.Style style) {
        PedalActionView v = new PedalActionView(this,icon,label,style); v.setContentDescription(label); return v;
    }

    private LinearLayout glassCard(int radius) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(15), dp(16), dp(15));
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(41, 23, 54, 77), Color.argb(56, 8, 28, 45)});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), Color.argb(54, 255, 255, 255));
        box.setBackground(g);
        box.setElevation(0);
        return box;
    }

    private GradientDrawable roundRect(int fill,int radius,int stroke) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius));
        if(stroke!=0) g.setStroke(dp(1),stroke); return g;
    }
    private GradientDrawable headerGradient() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(87, 24, 59, 82), Color.argb(107, 8, 31, 49)});
        g.setCornerRadii(new float[]{0,0,0,0,dp(26),dp(26),dp(26),dp(26)});
        g.setStroke(dp(1), Color.argb(51, 255, 255, 255));
        return g;
    }
    private GradientDrawable bottomGradient() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(102, 24, 59, 82), Color.argb(122, 8, 31, 49)});
        g.setCornerRadii(new float[]{dp(36),dp(36),dp(36),dp(36),0,0,0,0});
        g.setStroke(dp(1), Color.argb(61, 255, 255, 255));
        return g;
    }
    private Button plainButton(String label,int sp,int color) {
        Button b = new Button(this); b.setText(label); b.setTextSize(sp); b.setAllCaps(false); b.setTextColor(color);
        b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setMinHeight(0); b.setMinWidth(0); b.setStateListAnimator(null); return b;
    }
    private LinearLayout.LayoutParams fullActionLp(int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(h));p.setMargins(0,dp(9),0,0);return p;}
    private LinearLayout.LayoutParams rowWeightLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1f);p.setMargins(dp(3),0,dp(3),0);return p;}
    private LinearLayout.LayoutParams actionWeightLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1f);p.setMargins(dp(3),dp(7),dp(3),0);return p;}
    private LinearLayout.LayoutParams bottomWeightLp(float w,int h){return new LinearLayout.LayoutParams(0,dp(h),w);}

    private View metaTile(PedalIconView.Icon icon,String label) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.HORIZONTAL); tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(6),dp(5),dp(6),dp(5)); tile.setBackground(roundRect(Color.argb(105,5,35,49),14,Color.argb(90,245,249,252)));
        PedalIconView iv = new PedalIconView(this,icon,Color.rgb(31,231,242));
        TextView tv = text(label,10,true,TEXT); tv.setGravity(Gravity.CENTER); tv.setMaxLines(2);
        tile.addView(iv,new LinearLayout.LayoutParams(dp(18),dp(18)));
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,-2,1f);tlp.setMargins(dp(4),0,0,0);tile.addView(tv,tlp);
        return tile;
    }

    private JSONObject nextUpcomingEvent() {
        JSONArray events=bootstrap==null?null:bootstrap.optJSONArray("events"); JSONObject best=null; long bestTs=Long.MAX_VALUE;
        if(events==null)return null;
        for(int i=0;i<events.length();i++){
            JSONObject e=events.optJSONObject(i); if(e==null)continue;
            String st=e.optString("status","planned"); if("cancelled".equals(st)||"completed".equals(st))continue;
            long ts=parseServerDate(e.optString("event_date",""));
            if(ts==0){if(best==null)best=e;} else if(ts<bestTs){bestTs=ts;best=e;}
        }
        return best;
    }

    private View buildUpcomingCard(JSONObject event) {
        LinearLayout outer=glassCard(32);
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        PedalIconView icon=new PedalIconView(this,PedalIconView.Icon.CALENDAR,Color.rgb(31,231,242));
        TextView title=text("برنامه پیش رو",20,true,TEXT);
        heading.addView(title);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(24),dp(24));ip.setMargins(dp(8),0,0,0);heading.addView(icon,ip);outer.addView(heading);
        if(event==null){TextView empty=text("فعلاً برنامه آینده‌ای ثبت نشده است.",13,false,MUTED);empty.setPadding(0,dp(18),0,dp(18));empty.setGravity(Gravity.CENTER);outer.addView(empty);return outer;}

        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,0);
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setClipToOutline(true);cover.setBackground(roundRect(Color.TRANSPARENT,21,Color.argb(100,245,249,252)));
        RemoteImageLoader.load(this,cover,eventCover(event),R.drawable.program_reference);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(148),dp(165));cp.setMargins(dp(11),0,0,0);row.addView(cover,cp);

        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.END);
        info.addView(text(event.optString("title","برنامه"),20,true,TEXT));
        String route=event.optString("route_name",event.optString("meeting_location","مسیر گروهی"));
        String desc=event.optString("description","");
        TextView d=text(route+(desc.isBlank()?"":"، "+desc),12,false,TEXT);d.setMaxLines(3);d.setPadding(0,dp(5),0,dp(5));info.addView(d);
        LinearLayout r1=new LinearLayout(this);r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(metaTile(PedalIconView.Icon.DISTANCE,event.optDouble("planned_distance",0)>0?JalaliDate.fa(Math.round(event.optDouble("planned_distance",0)))+" کیلومتر":"مسافت آزاد"),rowWeightLp());
        r1.addView(metaTile(PedalIconView.Icon.LOCATION,event.optString("meeting_location","محل قرار")),rowWeightLp());info.addView(r1);
        LinearLayout r2=new LinearLayout(this);r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(metaTile(PedalIconView.Icon.ROUTE,event.optString("finish_location","پایان اختیاری")),rowWeightLp());
        r2.addView(metaTile(PedalIconView.Icon.CLOCK,JalaliDate.format(event.optString("event_date",""),true)),rowWeightLp());info.addView(r2);
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));outer.addView(row);
        outer.setClickable(true);outer.setOnClickListener(v->showEventDetail(event));return outer;
    }

    private String eventCover(JSONObject event) {
        String cover=event.optString("cover_image_path","");if(!cover.isBlank())return cover;
        JSONArray photos=bootstrap==null?null:bootstrap.optJSONArray("event_photos");
        if(photos!=null)for(int i=0;i<photos.length();i++){JSONObject p=photos.optJSONObject(i);if(p!=null&&p.optInt("event_id",0)==event.optInt("id",0)){String f=p.optString("file_name","");if(!f.isBlank())return f;}}
        return "";
    }

    private View buildWeatherCard() {
        LinearLayout box=glassCard(32);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        PedalIconView cloud=new PedalIconView(this,PedalIconView.Icon.CLOUD,Color.rgb(205,229,255));
        TextView title=text("پیش‌بینی ۷ روزه هوا",20,true,TEXT);
        LinearLayout titleHost=new LinearLayout(this);titleHost.setOrientation(LinearLayout.HORIZONTAL);titleHost.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);titleHost.addView(title);LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(dp(26),dp(26));clp.setMargins(dp(7),0,0,0);titleHost.addView(cloud,clp);
        head.addView(titleHost,new LinearLayout.LayoutParams(0,-2,1f));
        PedalActionView refresh=action(PedalIconView.Icon.REFRESH,"بروزرسانی",PedalActionView.Style.OUTLINE);refresh.setOnClickListener(v->loadWeather(true));head.addView(refresh,new LinearLayout.LayoutParams(dp(112),dp(50)));
        box.addView(head);
        TextView sub=text("باد، جهت باد، تندباد و بارندگی برای برنامه‌ریزی رکاب",12,false,MUTED);sub.setPadding(0,dp(3),0,dp(8));box.addView(sub);
        weatherStatus=text("در حال دریافت پیش‌بینی…",10,false,MUTED);box.addView(weatherStatus);
        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);
        weatherDaysHost=new LinearLayout(this);weatherDaysHost.setOrientation(LinearLayout.HORIZONTAL);weatherDaysHost.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);hsv.addView(weatherDaysHost);
        box.addView(hsv,new LinearLayout.LayoutParams(-1,dp(155)));return box;
    }

    private void loadWeather(boolean force) {
        if(weatherDaysHost==null)return;
        weatherStatus.setText("در حال دریافت پیش‌بینی…");
        resolveWeatherPoint((lat,lng)->io.execute(()->{
            try{
                java.util.List<WeatherClient.Day> days=new WeatherClient().forecast(this,lat,lng,force);
                main.post(()->renderWeather(days));
            }catch(Exception e){main.post(()->{weatherStatus.setText("پیش‌بینی هوا در دسترس نیست؛ برای تلاش دوباره «بروزرسانی» را بزنید.");});}
        }));
    }

    private void resolveWeatherPoint(WeatherPointCallback callback) {
        JSONObject event=nextUpcomingEvent();
        double elat=event==null?Double.NaN:event.optDouble("meeting_lat",Double.NaN),elng=event==null?Double.NaN:event.optDouble("meeting_lng",Double.NaN);
        if(Double.isFinite(elat)&&Double.isFinite(elng)){callback.ready(elat,elng);return;}
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            CancellationTokenSource token=new CancellationTokenSource();
            LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY,token.getToken())
                    .addOnSuccessListener(this,loc->{if(loc!=null)callback.ready(loc.getLatitude(),loc.getLongitude());else callback.ready(36.2605,59.6168);})
                    .addOnFailureListener(this,e->callback.ready(36.2605,59.6168));
        } else callback.ready(36.2605,59.6168);
    }
    private interface WeatherPointCallback{void ready(double lat,double lng);}

    private void renderWeather(java.util.List<WeatherClient.Day> days) {
        if(weatherDaysHost==null)return;weatherDaysHost.removeAllViews();
        if(days==null||days.isEmpty()){weatherStatus.setText("داده هواشناسی دریافت نشد.");return;}
        weatherStatus.setText("بروزرسانی شد · "+JalaliDate.fa(days.size())+" روز");
        for(WeatherClient.Day d:days){
            LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(7),dp(7),dp(7),dp(7));tile.setBackground(roundRect(Color.argb(105,5,34,49),16,Color.argb(90,245,249,252)));
            TextView date=text(JalaliDate.weekdayAndDate(d.date),10,true,TEXT);date.setGravity(Gravity.CENTER);date.setMaxLines(2);
            PedalIconView wi=new PedalIconView(this,WeatherClient.icon(d.code),d.code<=1?Color.rgb(255,217,74):Color.rgb(190,224,255));
            TextView max=text(JalaliDate.fa(d.maxC)+"°",18,true,TEXT);max.setGravity(Gravity.CENTER);
            TextView min=text(JalaliDate.fa(d.minC)+"°",10,false,MUTED);min.setGravity(Gravity.CENTER);
            TextView wind=text("باد "+JalaliDate.fa(d.windKmh)+" · تندباد "+JalaliDate.fa(d.gustKmh),8,false,MUTED);wind.setGravity(Gravity.CENTER);wind.setMaxLines(2);
            TextView rain=text("بارش "+JalaliDate.fa(d.rainPct)+"٪",8,false,MUTED);rain.setGravity(Gravity.CENTER);
            tile.addView(date);tile.addView(wi,new LinearLayout.LayoutParams(dp(36),dp(36)));tile.addView(max);tile.addView(min);tile.addView(wind);tile.addView(rain);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(112),dp(150));p.setMargins(dp(4),0,dp(4),0);weatherDaysHost.addView(tile,p);
        }
        VazirmatnLoader.apply(this,weatherDaysHost);
    }

    private void showDrawer() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(18), dp(12), dp(12));
        GradientDrawable drawerBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(240, 11, 41, 64), Color.argb(247, 4, 22, 37)});
        drawerBg.setCornerRadii(new float[]{dp(28),dp(28),0,0,0,0,dp(28),dp(28)});
        drawerBg.setStroke(dp(1), Color.argb(51, 255, 255, 255));
        panel.setBackground(drawerBg);
        panel.setElevation(dp(20));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setContentDescription("PedalPro");
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(-1, dp(92));
        logoLp.setMargins(0, 0, 0, dp(10));
        panel.addView(logo, logoLp);

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);
        sc.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        body.setPadding(dp(6), 0, dp(6), dp(18));

        body.addView(drawerItem("⌂", "صفحه اصلی", () -> { d.dismiss(); showHome(); }, "dashboard".equals(currentPage)));
        body.addView(drawerItem("🗓️", "فعالیت‌ها و برنامه‌ها", () -> { d.dismiss(); showActivities(); }, "activities".equals(currentPage)));
        body.addView(drawerItem("📍", "مسیرهای GPS", () -> { d.dismiss(); showRoutes(); }, "routes".equals(currentPage)));

        body.addView(drawerSection("رکاب‌زنی", "🚴",
                new String[]{"▶", "⌁", "◎", "◉"},
                new String[]{"شروع / ادامه رکاب‌زنی", "سنسورهای Cadence / Power", "رسیدن به گروه در حال حرکت", "اشتراک زنده Beacon"},
                new Runnable[]{
                        () -> { d.dismiss(); showRide(); },
                        () -> { d.dismiss(); openAdvancedSection("sensors"); },
                        () -> { d.dismiss(); openAdvancedSection("group"); },
                        () -> { d.dismiss(); openAdvancedSection("beacon"); }
                }, true));

        body.addView(drawerSection("مسیرها و اکتشاف", "🧭",
                new String[]{"⌖", "✦", "▰", "◫", "◩", "⬇", "◈"},
                new String[]{"مسیرهای من", "مسیرهای پیشنهادی", "Segments", "Heatmap شخصی / باشگاه", "Heatmap عمومی PedalPro", "مسیرهای آفلاین", "Flyover"},
                new Runnable[]{
                        () -> { d.dismiss(); showRoutes(); },
                        () -> { d.dismiss(); openAdvancedSection("recommend"); },
                        () -> { d.dismiss(); openAdvancedSection("segments"); },
                        () -> { d.dismiss(); openAdvancedSection("heatmap"); },
                        () -> { d.dismiss(); openAdvancedSection("globalheat"); },
                        () -> { d.dismiss(); openAdvancedSection("offline"); },
                        () -> { d.dismiss(); openAdvancedSection("flyover"); }
                }, false));

        body.addView(drawerSection("باشگاه", "👥",
                new String[]{"👤", "🏆", "🥇", "⌕", "▧"},
                new String[]{"اعضا", "چالش‌ها", "رتبه‌بندی هفتگی", "کشف برنامه‌ها", "گالری"},
                new Runnable[]{
                        () -> { d.dismiss(); showClub(); },
                        () -> { d.dismiss(); showChallenges(); },
                        () -> { d.dismiss(); openAdvancedSection("club"); },
                        () -> { d.dismiss(); openAdvancedSection("discover"); },
                        () -> { d.dismiss(); showGalleryPage(); }
                }, false));

        if (signedIn()) {
            body.addView(drawerSection("ارتباطات", "💬",
                    new String[]{"💬", "♥", "✦"},
                    new String[]{"چت‌روم", "فید اجتماعی / Follow / Kudos", "مشاور هوشمند"},
                    new Runnable[]{
                            () -> { d.dismiss(); showGroupChat(); },
                            () -> { d.dismiss(); openAdvancedSection("social"); },
                            () -> { d.dismiss(); showAiAdvisor(); }
                    }, false));
        } else {
            body.addView(drawerSection("ارتباطات", "💬",
                    new String[]{"♥"},
                    new String[]{"فید اجتماعی / Follow / Kudos"},
                    new Runnable[]{() -> { d.dismiss(); openAdvancedSection("social"); }}, false));
        }

        if (signedIn()) {
            body.addView(drawerSection("عملکرد من", "📈",
                    new String[]{"▥", "↗", "▶", "⌁", "♥", "🚲", "⚒"},
                    new String[]{"آمار و تحلیل", "Training Load / Fitness", "Replay فعالیت", "ارتفاع و آب‌وهوا", "وضعیت جسمانی", "دوچرخه‌های من", "سرویس و نگهداری"},
                    new Runnable[]{
                            () -> { d.dismiss(); showPerformance(); },
                            () -> { d.dismiss(); openAdvancedSection("training"); },
                            () -> { d.dismiss(); openAdvancedSection("replay"); },
                            () -> { d.dismiss(); openAdvancedSection("analysis"); },
                            () -> { d.dismiss(); showFitness(); },
                            () -> { d.dismiss(); showBikes(); },
                            () -> { d.dismiss(); openAdvancedSection("maintenance"); }
                    }, false));
        } else {
            body.addView(drawerSection("عملکرد من", "📈",
                    new String[]{"↗", "▶", "⌁", "⚒"},
                    new String[]{"Training Load / Fitness", "Replay فعالیت", "ارتفاع و آب‌وهوا", "سرویس و نگهداری"},
                    new Runnable[]{
                            () -> { d.dismiss(); openAdvancedSection("training"); },
                            () -> { d.dismiss(); openAdvancedSection("replay"); },
                            () -> { d.dismiss(); openAdvancedSection("analysis"); },
                            () -> { d.dismiss(); openAdvancedSection("maintenance"); }
                    }, false));
        }

        body.addView(drawerSection("ایمنی و حریم خصوصی", "🛡️",
                new String[]{"SOS", "◉", "◌"},
                new String[]{"SOS و مخاطبین اضطراری", "Beacon", "Privacy Zones"},
                new Runnable[]{
                        () -> { d.dismiss(); openAdvancedSection("safety"); },
                        () -> { d.dismiss(); openAdvancedSection("beacon"); },
                        () -> { d.dismiss(); openAdvancedSection("privacy"); }
                }, false));

        if (signedIn()) {
            body.addView(drawerSection("حساب من", "👤",
                    new String[]{"👤", "⚙", "⬆", "ⓘ", "📘"},
                    new String[]{"پروفایل من", "تنظیمات تلفن همراه", "نسخه اپلیکیشن", "درباره ما", "راهنمای استفاده"},
                    new Runnable[]{
                            () -> { d.dismiss(); showProfileEditor(); },
                            () -> { d.dismiss(); showPhoneSettingsPage(); },
                            () -> { d.dismiss(); showAppVersionPage(); },
                            () -> { d.dismiss(); showAboutGroupPage(); },
                            () -> { d.dismiss(); showGuidePage(); }
                    }, false));
        } else {
            body.addView(drawerSection("حساب من", "👤",
                    new String[]{"ⓘ", "📘"},
                    new String[]{"درباره ما", "راهنمای استفاده"},
                    new Runnable[]{
                            () -> { d.dismiss(); showAboutGroupPage(); },
                            () -> { d.dismiss(); showGuidePage(); }
                    }, false));
        }

        boolean admin = bootstrap != null && (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1);
        if (admin) {
            body.addView(drawerSection("مدیریت", "⚙",
                    new String[]{"⚙", "✉"},
                    new String[]{"تنظیمات سایت", "پیامک"},
                    new Runnable[]{
                            () -> { d.dismiss(); showSiteSettingsPage(); },
                            () -> { d.dismiss(); showSmsPage(); }
                    }, false));
        }

        sc.addView(body);
        panel.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                Math.min(dp(350), (int) (getResources().getDisplayMetrics().widthPixels * .86f)),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END);
        stage.addView(panel, pp);
        d.setContentView(stage);

        if (Build.VERSION.SDK_INT >= 31 && siteBackground != null) {
            siteBackground.setRenderEffect(RenderEffect.createBlurEffect(dp(10), dp(10), Shader.TileMode.CLAMP));
        }
        d.setOnDismissListener(x -> {
            if (Build.VERSION.SDK_INT >= 31 && siteBackground != null) {
                siteBackground.setRenderEffect(RenderEffect.createBlurEffect(dp(2), dp(2), Shader.TileMode.CLAMP));
            }
        });

        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.END;
            lp.dimAmount = .48f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        panel.setTranslationX(Math.min(dp(350), (int) (getResources().getDisplayMetrics().widthPixels * .86f)));
        panel.animate().translationX(0).setDuration(250).start();
        VazirmatnLoader.apply(this, panel);
    }

    private View buildViewportServerStatus() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(232, 7, 31, 48), Color.argb(238, 5, 23, 36)});
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(1), Color.argb(77, 108, 179, 209));
        pill.setBackground(bg);
        pill.setElevation(dp(8));

        serverStatusLabel = text("در حال بررسی سرور", 11, false, Color.rgb(213, 226, 238));
        serverStatusDot = new View(this);
        pill.addView(serverStatusLabel);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.setMargins(dp(8), 0, 0, 0);
        pill.addView(serverStatusDot, dotLp);
        updateServerStatusPill();
        return pill;
    }

    private void updateServerStatusPill() {
        if (serverStatusLabel == null || serverStatusDot == null) return;
        serverStatusLabel.setText(serverOnline ? "متصل به سرور" : "اتصال سرور قطع — ذخیره محلی");
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(serverOnline ? GREEN : RED);
        serverStatusDot.setBackground(dotBg);
    }

    private View drawerItem(String glyph, String label, Runnable run, boolean active) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setBackground(roundRect(
                active ? Color.argb(23, 32, 228, 241) : Color.TRANSPARENT,
                14,
                0));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(label);

        int fg = active ? Color.rgb(85, 239, 247) : Color.rgb(220, 234, 243);
        TextView icon = text(glyph, 16, false, fg);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView tv = text(label, 13, true, fg);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMargins(dp(10), 0, 0, 0);
        row.addView(tv, tlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, dp(3), 0, 0);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> run.run());
        return row;
    }

    private View drawerSection(String title, String groupGlyph, String[] itemGlyphs, String[] labels, Runnable[] actions, boolean startOpen) {
        LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setBackground(roundRect(Color.argb(6, 255, 255, 255), 15, Color.argb(18, 255, 255, 255)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(5), 0, 0);
        host.setLayoutParams(hp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        head.setPadding(dp(12), 0, dp(12), 0);

        TextView sectionIcon = text(groupGlyph, 16, false, Color.rgb(220, 234, 243));
        sectionIcon.setGravity(Gravity.CENTER);
        TextView titleView = text(title, 13, true, Color.rgb(220, 234, 243));
        TextView chevron = text(startOpen ? "⌃" : "⌄", 15, false, Color.rgb(128, 164, 187));
        chevron.setGravity(Gravity.CENTER);

        head.addView(sectionIcon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.setMargins(dp(9), 0, 0, 0);
        head.addView(titleView, titleLp);
        head.addView(chevron, new LinearLayout.LayoutParams(dp(22), dp(22)));
        host.addView(head, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout subs = new LinearLayout(this);
        subs.setOrientation(LinearLayout.VERTICAL);
        subs.setPadding(dp(7), 0, dp(7), dp(8));
        subs.setVisibility(startOpen ? View.VISIBLE : View.GONE);

        for (int i = 0; i < labels.length; i++) {
            final Runnable action = actions[i];
            LinearLayout sub = new LinearLayout(this);
            sub.setOrientation(LinearLayout.HORIZONTAL);
            sub.setGravity(Gravity.CENTER_VERTICAL);
            sub.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            sub.setPadding(dp(10), 0, dp(10), 0);
            sub.setBackground(roundRect(Color.TRANSPARENT, 11, 0));
            sub.setClickable(true);
            sub.setFocusable(true);

            String glyph = itemGlyphs != null && i < itemGlyphs.length ? itemGlyphs[i] : "";
            TextView gi = text(glyph, 16, false, Color.rgb(220, 234, 243));
            gi.setGravity(Gravity.CENTER);
            sub.addView(gi, new LinearLayout.LayoutParams(dp(24), dp(24)));

            TextView label = text(labels[i], 12, false, Color.rgb(220, 234, 243));
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1f);
            labelLp.setMargins(dp(9), 0, 0, 0);
            sub.addView(label, labelLp);

            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(42));
            sp.setMargins(0, dp(3), 0, 0);
            subs.addView(sub, sp);
            sub.setOnClickListener(v -> action.run());
        }

        head.setOnClickListener(v -> {
            boolean open = subs.getVisibility() != View.VISIBLE;
            if (open) {
                subs.setVisibility(View.VISIBLE);
                subs.setAlpha(0f);
                subs.animate().alpha(1f).setDuration(120).start();
            } else {
                subs.setVisibility(View.GONE);
            }
            chevron.setText(open ? "⌃" : "⌄");
        });

        host.addView(subs);
        return host;
    }

    private void updateHeaderAccess(JSONObject data) {
        JSONObject user = data == null ? null : data.optJSONObject("user");
        if (headerAccount != null) headerAccount.setLabel(user == null ? "ورود" : "حساب کاربری");
        boolean admin = data != null && (data.optBoolean("is_admin", false) || data.optInt("is_admin", 0) == 1);
        if (headerAdminSettings != null) headerAdminSettings.setVisibility(admin ? View.VISIBLE : View.GONE);
    }

    private void openAdvancedSection(String section) {
        if (!signedIn()) {
            showAuthDialog("login");
            return;
        }
        Intent i = new Intent(this, NativeAdvancedActivity.class);
        i.putExtra("section", section == null ? "" : section);
        startActivity(i);
    }

    private void showAboutGroupPage() {
        currentPage = "about";
        selectNav(null);
        content.removeAllViews();
        JSONObject site = bootstrap == null ? null : bootstrap.optJSONObject("site");
        String title = site == null ? "درباره ما" : site.optString("about_title", "درباره ما");
        String body = site == null ? "" : site.optString("about_body", "");
        addHero(title, "");
        LinearLayout box = glassCard(24);
        if (!body.isBlank()) box.addView(text(body, 13, false, TEXT));
        else box.addView(text("PedalPro", 18, true, TEXT));
        content.addView(box, cardLp());
        VazirmatnLoader.apply(this, content);
        animatePageIn();
    }

    private void showSiteSettingsPage() {
        if (!signedIn()) { showLogin(); return; }
        boolean admin = bootstrap != null && (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1);
        if (!admin) { showHome(); return; }
        Intent i = new Intent(this, NativeSiteSettingsActivity.class);
        startActivity(i);
    }

    private void showGalleryPage() {
        currentPage = "gallery";
        selectNav(null);
        content.removeAllViews();
        addHero("گالری", "");
        JSONArray photos = bootstrap == null ? null : bootstrap.optJSONArray("event_photos");
        int shown = 0;
        if (photos != null) {
            for (int i = 0; i < photos.length(); i++) {
                JSONObject p = photos.optJSONObject(i);
                if (p == null) continue;
                LinearLayout box = glassCard(20);
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setClipToOutline(true);
                image.setBackground(roundRect(Color.TRANSPARENT, 16, Color.argb(70, 245, 249, 252)));
                RemoteImageLoader.load(this, image, p.optString("file_name", ""), R.drawable.program_reference);
                box.addView(image, new LinearLayout.LayoutParams(-1, dp(190)));
                String caption = p.optString("caption", p.optString("uploader_name", ""));
                if (!caption.isBlank()) box.addView(text(caption, 11, false, MUTED));
                content.addView(box, cardLp());
                shown++;
            }
        }
        if (shown == 0) addEmpty("هنوز تصویری در گالری ثبت نشده است.");
        VazirmatnLoader.apply(this, content);
        animatePageIn();
    }

    private String[][] phonePowerGuide(String manufacturer, String model) {
        String key = ((manufacturer == null ? "" : manufacturer) + " " + (model == null ? "" : model)).toLowerCase(Locale.ROOT);
        if (key.matches(".*(xiaomi|redmi|poco).*")) {
            return new String[][]{
                    {"باتری", "Settings → Apps → Manage apps → PedalPro → Battery saver → No restrictions"},
                    {"اجرای خودکار", "Autostart را برای PedalPro روشن کنید."},
                    {"برنامه‌های اخیر", "در صورت وجود گزینه Lock، PedalPro را در Recent apps قفل کنید تا MIUI آن را نبندد."}
            };
        }
        if (key.contains("samsung")) {
            return new String[][]{
                    {"باتری برنامه", "Settings → Apps → PedalPro → Battery → Unrestricted"},
                    {"Sleeping apps", "Battery and device care → Battery → Background usage limits؛ PedalPro را از Sleeping و Deep sleeping apps خارج کنید."},
                    {"Location", "Location permission را روی Precise و Allow while using the app نگه دارید."}
            };
        }
        if (key.matches(".*(huawei|honor).*")) {
            return new String[][]{
                    {"App launch", "Settings → Battery → App launch → PedalPro → Manage manually"},
                    {"سه مجوز اجرا", "Auto-launch، Secondary launch و Run in background را فعال کنید."},
                    {"Battery optimization", "PedalPro را از Battery optimization محدود نکنید."}
            };
        }
        if (key.matches(".*(oppo|realme|oneplus).*")) {
            return new String[][]{
                    {"مصرف باتری", "App management → PedalPro → Battery usage → Allow background activity / No restrictions"},
                    {"Auto launch", "Auto launch یا Startup manager را برای PedalPro فعال کنید."},
                    {"قفل برنامه", "در صورت وجود App lock/Keep alive در Recent apps، PedalPro را نگه دارید."}
            };
        }
        if (key.matches(".*(vivo|iqoo).*")) {
            return new String[][]{
                    {"Background power", "Battery → Background power consumption management → PedalPro → Don’t restrict"},
                    {"Auto start", "Permission management / iManager → Autostart → PedalPro را فعال کنید."},
                    {"Location", "دسترسی مکان دقیق را فعال نگه دارید."}
            };
        }
        if (key.contains("asus")) {
            return new String[][]{
                    {"PowerMaster", "Mobile Manager / PowerMaster → Auto-start manager → PedalPro را مجاز کنید."},
                    {"باتری", "Battery optimization را برای PedalPro محدود نکنید."}
            };
        }
        if (key.matches(".*(pixel|google).*")) {
            return new String[][]{
                    {"App battery usage", "Settings → Apps → PedalPro → App battery usage → Unrestricted"},
                    {"Location", "Location را روی Precise نگه دارید."}
            };
        }
        return new String[][]{
                {"باتری برنامه", "Settings → Apps → PedalPro → Battery → Unrestricted / No restrictions"},
                {"پس‌زمینه", "Background activity را فعال و Battery optimization را برای PedalPro غیرفعال کنید."},
                {"اجرای خودکار", "اگر گوشی بخش Auto-start دارد، PedalPro را در آن فعال کنید."}
        };
    }

    private void openAppSettingsNative() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast("تنظیمات PedalPro قابل باز شدن نیست.");
        }
    }

    private void openBatterySettingsNative() {
        try {
            Intent specific = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(specific);
        } catch (Exception ignored) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                openAppSettingsNative();
            }
        }
    }

    private void openAutostartSettingsNative() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        Intent intent = new Intent();
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) {
            intent.setComponent(new android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        } else if (m.contains("huawei") || m.contains("honor")) {
            intent.setComponent(new android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
        } else if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) {
            intent.setComponent(new android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
        } else if (m.contains("vivo") || m.contains("iqoo")) {
            intent.setComponent(new android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
        } else {
            openAppSettingsNative();
            return;
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            openAppSettingsNative();
        }
    }

    private void showPhoneSettingsPage() {
        currentPage = "phone-settings";
        if (!signedIn()) { showAuthDialog("login"); return; }
        selectNav(null);
        content.removeAllViews();

        LinearLayout shell = glassCard(26);
        shell.setPadding(dp(14), dp(14), dp(14), dp(14));

        shell.addView(text("تنظیمات تلفن همراه", 22, true, TEXT));
        TextView subtitle = text("برای جلوگیری از متوقف‌شدن GPS، رکاب‌زنی زنده و موقعیت گروه در پس‌زمینه، محدودیت باتری PedalPro را بردارید.", 12, false, MUTED);
        subtitle.setLineSpacing(0, 1.55f);
        subtitle.setPadding(0, dp(5), 0, 0);
        shell.addView(subtitle);

        LinearLayout device = new LinearLayout(this);
        device.setOrientation(LinearLayout.HORIZONTAL);
        device.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        device.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        device.setPadding(dp(16), dp(16), dp(16), dp(16));
        device.setBackground(roundRect(Color.argb(184, 9, 27, 42), 18, Color.argb(56, 46, 230, 166)));
        LinearLayout.LayoutParams deviceLp = new LinearLayout.LayoutParams(-1, -2);
        deviceLp.setMargins(0, dp(14), 0, dp(14));

        TextView phoneIcon = text("📱", 25, false, TEXT);
        phoneIcon.setGravity(Gravity.CENTER);
        GradientDrawable phoneIconBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(46, 46, 230, 166), Color.argb(36, 32, 187, 232)});
        phoneIconBg.setCornerRadius(dp(16));
        phoneIcon.setBackground(phoneIconBg);

        LinearLayout deviceText = new LinearLayout(this);
        deviceText.setOrientation(LinearLayout.VERTICAL);
        deviceText.setGravity(Gravity.END);
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String deviceName = (manufacturer + " " + model).trim();
        if (deviceName.isBlank()) deviceName = "گوشی Android";
        deviceText.addView(text(deviceName, 14, true, TEXT));
        TextView androidVersion = text("Android " + Build.VERSION.RELEASE, 11, false, MUTED);
        androidVersion.setPadding(0, dp(3), 0, 0);
        deviceText.addView(androidVersion);

        device.addView(phoneIcon, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout.LayoutParams dtLp = new LinearLayout.LayoutParams(0, -2, 1f);
        dtLp.setMargins(dp(14), 0, 0, 0);
        device.addView(deviceText, dtLp);
        shell.addView(device, deviceLp);

        String[][] guide = phonePowerGuide(manufacturer, model);
        for (int i = 0; i < guide.length; i++) {
            LinearLayout step = new LinearLayout(this);
            step.setOrientation(LinearLayout.VERTICAL);
            step.setPadding(dp(14), dp(13), dp(14), dp(13));
            step.setBackground(roundRect(Color.argb(9, 255, 255, 255), 15, Color.argb(26, 255, 255, 255)));
            TextView stepTitle = text(JalaliDate.fa(i + 1) + ". " + guide[i][0], 13, true, Color.rgb(233, 251, 255));
            TextView stepBody = text(guide[i][1], 11, false, Color.rgb(159, 181, 200));
            stepBody.setLineSpacing(0, 1.75f);
            stepBody.setPadding(0, dp(4), 0, 0);
            step.addView(stepTitle);
            step.addView(stepBody);
            LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(-1, -2);
            stepLp.setMargins(0, 0, 0, dp(10));
            shell.addView(step, stepLp);
        }

        PedalActionView battery = action(PedalIconView.Icon.SETTINGS, "باز کردن تنظیمات باتری", PedalActionView.Style.PRIMARY);
        battery.setOnClickListener(v -> openBatterySettingsNative());
        shell.addView(battery, fullActionLp(52));

        PedalActionView autostart = action(PedalIconView.Icon.SETTINGS, "تنظیمات اجرای خودکار", PedalActionView.Style.OUTLINE);
        autostart.setOnClickListener(v -> openAutostartSettingsNative());
        shell.addView(autostart, fullActionLp(50));

        PedalActionView app = action(PedalIconView.Icon.SETTINGS, "تنظیمات PedalPro", PedalActionView.Style.OUTLINE);
        app.setOnClickListener(v -> openAppSettingsNative());
        shell.addView(app, fullActionLp(50));

        LinearLayout notice = new LinearLayout(this);
        notice.setOrientation(LinearLayout.VERTICAL);
        notice.setPadding(dp(13), dp(12), dp(13), dp(12));
        notice.setBackground(roundRect(Color.argb(32, 255, 217, 74), 15, Color.argb(76, 255, 217, 74)));
        TextView note = text("بعد از انجام تنظیمات، PedalPro را در فهرست برنامه‌های Sleeping / Deep Sleeping / Battery Saver / Background Restriction قرار ندهید. برای رکاب‌زنی طولانی، Location را روی حالت دقیق (Precise) نگه دارید.", 11, false, TEXT);
        note.setLineSpacing(0, 1.65f);
        notice.addView(note);
        LinearLayout.LayoutParams noticeLp = new LinearLayout.LayoutParams(-1, -2);
        noticeLp.setMargins(0, dp(14), 0, 0);
        shell.addView(notice, noticeLp);

        content.addView(shell, cardLp());
        VazirmatnLoader.apply(this, content);
        animatePageIn();
    }

    private void showAppVersionPage() {
        currentPage = "app";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("نسخه اپلیکیشن", "");
        LinearLayout box = glassCard(24);
        box.addView(text("PedalPro Android", 18, true, TEXT));
        box.addView(text("نسخه " + ir.pedalpro.app.BuildConfig.VERSION_NAME + " · کد " + ir.pedalpro.app.BuildConfig.VERSION_CODE, 12, false, MUTED));
        PedalActionView update = action(PedalIconView.Icon.REFRESH, "بررسی بروزرسانی", PedalActionView.Style.PRIMARY);
        update.setOnClickListener(v -> openStoreUpdatePage());
        box.addView(update, fullActionLp(54));
        content.addView(box, cardLp());
        VazirmatnLoader.apply(this, content);
        animatePageIn();
    }

    private void openStoreUpdatePage() {
        String channel = ir.pedalpro.app.BuildConfig.STORE_CHANNEL;
        Uri uri = "myket".equals(channel)
                ? Uri.parse("myket://details?id=ir.pedalpro.app")
                : Uri.parse("bazaar://details?id=ir.pedalpro.app");
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception e) {
            Uri web = "myket".equals(channel)
                    ? Uri.parse("https://myket.ir/app/ir.pedalpro.app")
                    : Uri.parse("https://cafebazaar.ir/app/ir.pedalpro.app");
            startActivity(new Intent(Intent.ACTION_VIEW, web));
        }
    }

    private LinearLayout guideJumpCard(String title, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.END);
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        card.setClickable(true);
        card.setFocusable(true);
        card.setBackground(roundRect(Color.argb(9, 255, 255, 255), 17, Color.argb(31, 255, 255, 255)));
        TextView t = text(title, 14, true, Color.rgb(238, 249, 255));
        TextView sub = text(subtitle, 11, false, Color.rgb(157, 182, 199));
        sub.setPadding(0, dp(5), 0, 0);
        sub.setLineSpacing(0, 1.7f);
        card.addView(t);
        card.addView(sub);
        return card;
    }

    private LinearLayout guideArticleSection(String title, String body) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(13), dp(13), dp(13), dp(13));
        section.setBackground(roundRect(Color.argb(153, 4, 24, 38), 18, Color.argb(26, 255, 255, 255)));
        TextView heading = text(title, 16, true, Color.rgb(234, 250, 255));
        section.addView(heading);
        if (body != null && !body.isBlank()) {
            TextView copy = text(body, 12, false, Color.rgb(212, 229, 239));
            copy.setPadding(0, dp(9), 0, 0);
            copy.setLineSpacing(0, 2f);
            section.addView(copy);
        }
        return section;
    }

    private LinearLayout.LayoutParams guideArticleLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(11));
        return lp;
    }

    private LinearLayout.LayoutParams fullWrapLp(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(topMargin), 0, 0);
        return lp;
    }

    private View guideFaq(String question, String answer) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        item.setBackground(roundRect(Color.argb(8, 255, 255, 255), 13, Color.argb(23, 255, 255, 255)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, 0);
        item.setLayoutParams(lp);

        TextView summary = text(question, 12, true, TEXT);
        TextView copy = text(answer, 12, false, Color.rgb(212, 229, 239));
        copy.setPadding(0, dp(8), 0, 0);
        copy.setLineSpacing(0, 1.8f);
        copy.setVisibility(View.GONE);
        item.addView(summary);
        item.addView(copy);
        item.setClickable(true);
        item.setOnClickListener(v -> copy.setVisibility(copy.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        return item;
    }

    private void showGuidePage() {
        currentPage = "guide";
        selectNav(null);
        content.removeAllViews();

        LinearLayout shell = glassCard(26);
        shell.setPadding(dp(14), dp(14), dp(14), dp(14));

        shell.addView(text("راهنمای کامل استفاده از PedalPro", 22, true, TEXT));
        TextView subtitle = text("راهنمای وب و اپلیکیشن Android، از شروع رکاب تا تحلیل عملکرد و ایمنی", 12, false, MUTED);
        subtitle.setPadding(0, dp(4), 0, 0);
        subtitle.setLineSpacing(0, 1.5f);
        shell.addView(subtitle);

        PedalActionView pdf = action(PedalIconView.Icon.DETAILS, "دانلود راهنمای PDF", PedalActionView.Style.PRIMARY);
        pdf.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://pedalpro.ir/docs/PedalPro_User_Guide_4.7.40.pdf")));
            } catch (Exception e) {
                toast(e.getMessage());
            }
        });
        shell.addView(pdf, fullActionLp(50));

        final View[] jumpTargets = new View[8];
        String[][] cards = {
                {"شروع کار", "ورود، مجوزها و منوی اصلی"},
                {"رکاب‌زنی", "GPS، حالت کم‌مصرف و رکاب گروهی"},
                {"مسیرها", "Segments، Heatmap، Replay و Flyover"},
                {"عملکرد", "Training Load، Power و تحلیل"},
                {"باشگاه و ارتباطات", "برنامه، چالش، چت و فید"},
                {"ایمنی", "Beacon، SOS و Privacy Zones"},
                {"تنظیمات گوشی", "باتری، GPS و Background"},
                {"رفع اشکال", "خطاهای متداول و راه‌حل"}
        };
        LinearLayout quickGrid = new LinearLayout(this);
        quickGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(-1, -2);
        quickLp.setMargins(0, dp(12), 0, dp(20));
        for (int row = 0; row < 4; row++) {
            LinearLayout pair = new LinearLayout(this);
            pair.setOrientation(LinearLayout.HORIZONTAL);
            pair.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            pair.setGravity(Gravity.TOP);
            for (int col = 0; col < 2; col++) {
                final int idx = row * 2 + col;
                LinearLayout card = guideJumpCard(cards[idx][0], cards[idx][1]);
                card.setOnClickListener(v -> {
                    View target = jumpTargets[idx];
                    if (target != null && mainScroll != null) {
                        mainScroll.post(() -> mainScroll.smoothScrollTo(0, Math.max(0, target.getTop() - dp(95))));
                    }
                });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
                if (col == 0) cp.setMargins(dp(5), 0, 0, dp(9));
                else cp.setMargins(0, 0, dp(5), dp(9));
                pair.addView(card, cp);
            }
            quickGrid.addView(pair, new LinearLayout.LayoutParams(-1, -2));
        }
        shell.addView(quickGrid, quickLp);

        LinearLayout start = guideArticleSection("۱. شروع کار",
                "از بالای صفحه وارد «حساب کاربری» شوید. برای ثبت فعالیت، دسترسی Location را فعال کنید. اگر از Cadence یا Power Meter استفاده می‌کنید Bluetooth و مجوزهای آن نیز لازم است.\n\n" +
                "• خانه: داشبورد و فعالیت‌های زنده\n• فعالیت‌ها: برنامه‌ها و رویدادهای باشگاه\n• شروع رکاب: شروع یا ادامه رکاب‌زنی\n• مسیرها: مسیرهای GPS و ابزارهای اکتشاف\n• من: پروفایل و بخش‌های شخصی");
        jumpTargets[0] = start; shell.addView(start, guideArticleLp());

        LinearLayout home = guideArticleSection("۲. صفحه خانه",
                "برنامه بعدی، خلاصه‌ها، فید فعالیت‌ها و رکاب‌زنی‌های زنده در خانه نمایش داده می‌شوند. کارت سبز مربوط به رکاب‌زنی فعال خود شماست و کارت نارنجی افراد دیگری را که در حال رکاب‌زدن هستند نشان می‌دهد.");
        shell.addView(home, guideArticleLp());

        LinearLayout ride = guideArticleSection("۳. شروع و ثبت رکاب‌زنی",
                "۱. دکمه «شروع رکاب» را بزنید.\n۲. عنوان، دوچرخه و حریم خصوصی را انتخاب کنید.\n۳. رکاب را شروع کنید؛ زمان، سرعت، مسافت و GPS ثبت می‌شوند.\n۴. برای توقف موقت Pause و برای ادامه Resume را بزنید.\n۵. در پایان، فعالیت را خاتمه دهید تا در تاریخچه ثبت شود.\n\n" +
                "حالت کم‌مصرف: صفحه تیره و ساده می‌شود و فقط اطلاعات ضروری رکاب نمایش داده می‌شود.\n\n" +
                "رکاب گروهی: موقعیت گروه به‌صورت زنده به‌روزرسانی می‌شود. «رسیدن به گروه» برای مسیریابی به مقصد متحرک گروه استفاده می‌شود.");
        jumpTargets[1] = ride; shell.addView(ride, guideArticleLp());

        LinearLayout routes = guideArticleSection("۴. مسیرها و اکتشاف",
                "• Segments: ساخت بخش رقابتی از مسیر و ثبت Effort.\n• Leaderboard: مقایسه عملکرد اعضا.\n• Personal / Club / Global Heatmap: نمایش مسیرهای پرتکرار.\n• مسیر پیشنهادی: پیشنهاد بر اساس محبوبیت، صعود و مسافت معمول شما.\n• Offline Routes: ذخیره اطلاعات مسیر روی دستگاه.\n• Replay: بازپخش مسیر با Timeline و سرعت 1×، 2× و 4×.\n• Flyover: نمای متحرک شبه‌سه‌بعدی مسیر و ارتفاع.");
        jumpTargets[2] = routes; shell.addView(routes, guideArticleLp());

        LinearLayout performance = guideArticleSection("۵. عملکرد و تمرین",
                "• Training Load هفت‌روزه و پایه ۲۸ روزه\n• Fitness / Form و Acute/Chronic Ratio\n• Best Efforts و PR\n• Power و FTP در صورت وجود Power Meter\n• Cadence به RPM\n• Heart Rate Zones در صورت وجود منبع HR\n• تحلیل آب‌وهوا، ارتفاع، صعود، نزول و شیب");
        LinearLayout performanceNotice = new LinearLayout(this);
        performanceNotice.setOrientation(LinearLayout.VERTICAL);
        performanceNotice.setPadding(dp(12), dp(10), dp(12), dp(10));
        performanceNotice.setBackground(roundRect(Color.argb(24, 255, 217, 74), 13, Color.argb(62, 255, 217, 74)));
        performanceNotice.addView(text("تحلیل‌های Training Load و Fitness ابزار ورزشی هستند و جایگزین ارزیابی پزشکی نیستند.", 11, false, TEXT));
        performance.addView(performanceNotice, fullWrapLp(8));
        jumpTargets[3] = performance; shell.addView(performance, guideArticleLp());

        LinearLayout social = guideArticleSection("۶. باشگاه و ارتباطات",
                "• مشاهده و ثبت‌نام برنامه‌های باشگاه\n• چالش‌ها و رتبه‌بندی هفتگی از شنبه تا جمعه\n• اعضا و گالری\n• چت‌روم\n• فید اجتماعی، Follow / Unfollow و Kudos\n• مشاور هوشمند");
        jumpTargets[4] = social; shell.addView(social, guideArticleLp());

        LinearLayout safety = guideArticleSection("۷. ایمنی و حریم خصوصی",
                "• Beacon: لینک موقت برای مشاهده آخرین موقعیت رکاب‌زن.\n• SOS: مخاطب اضطراری و لینک زنده ۲۴ ساعته.\n• Privacy Zones: مخفی‌کردن مکان‌های حساس و نقاط شروع/پایان مسیر برای دیگران.");
        jumpTargets[5] = safety; shell.addView(safety, guideArticleLp());

        LinearLayout bike = guideArticleSection("۸. دوچرخه و نگهداری",
                "دوچرخه‌ها را ثبت کنید، کیلومتر کارکرد را پیگیری کنید و برای زنجیر، لاستیک، لنت و سرویس عمومی دوره نگهداری تعریف کنید.");
        shell.addView(bike, guideArticleLp());

        LinearLayout settings = guideArticleSection("۹. تنظیمات تلفن همراه",
                "• Battery Optimization برای PedalPro خاموش باشد.\n• Background Activity / Autostart در صورت وجود فعال باشد.\n• Location روی Precise قرار گیرد.\n• PedalPro در Sleeping / Deep Sleeping Apps قرار نگیرد.");
        PedalActionView openPhone = action(PedalIconView.Icon.SETTINGS, "باز کردن تنظیمات تلفن همراه", PedalActionView.Style.OUTLINE);
        openPhone.setOnClickListener(v -> showPhoneSettingsPage());
        settings.addView(openPhone, fullActionLp(48));
        jumpTargets[6] = settings; shell.addView(settings, guideArticleLp());

        LinearLayout update = guideArticleSection("۱۰. نسخه و بروزرسانی",
                "در بخش «نسخه اپلیکیشن» نسخه نصب‌شده و نسخه جدید بررسی می‌شود. بروزرسانی سایت با PPUP و بکاپ/Restore انجام می‌شود.");
        PedalActionView openApp = action(PedalIconView.Icon.DETAILS, "رفتن به نسخه اپلیکیشن", PedalActionView.Style.OUTLINE);
        openApp.setOnClickListener(v -> showAppVersionPage());
        update.addView(openApp, fullActionLp(48));
        shell.addView(update, guideArticleLp());

        LinearLayout faq = guideArticleSection("۱۱. رفع اشکال سریع", "");
        faq.addView(guideFaq("GPS در پس‌زمینه قطع می‌شود", "محدودیت باتری، Sleeping Apps و Background Restriction را غیرفعال کنید."));
        faq.addView(guideFaq("مسافت یا سرعت غیرعادی است", "در فضای باز شروع کنید، Location دقیق را فعال نگه دارید و چند ثانیه برای تثبیت GPS صبر کنید."));
        faq.addView(guideFaq("پیش‌بینی هوا دیر می‌آید", "داده هوا Cache می‌شود. اگر اینترنت یا Location کند باشد، بروزرسانی دستی را بزنید."));
        faq.addView(guideFaq("صفحه عملکرد اطلاعات ندارد", "نسخه‌های جدید هنگام بازشدن صفحه اطلاعات را خودکار دریافت می‌کنند؛ اتصال سرور را بررسی کنید."));
        faq.addView(guideFaq("سنسور پیدا نمی‌شود", "Bluetooth و مجوزها را بررسی کنید. سنسور باید پروتکل استاندارد BLE Cycling Speed/Cadence یا Cycling Power را ارائه کند."));
        jumpTargets[7] = faq; shell.addView(faq, guideArticleLp());

        LinearLayout check = guideArticleSection("۱۲. چک‌لیست قبل از رکاب",
                "• شارژ کافی گوشی\n• GPS و Location دقیق\n• خروج PedalPro از محدودیت باتری\n• اینترنت برای Live/Group/Weather\n• Bluetooth در صورت استفاده از سنسور\n• انتخاب دوچرخه و حریم خصوصی مناسب");
        shell.addView(check, guideArticleLp());

        content.addView(shell, cardLp());
        VazirmatnLoader.apply(this, content);
        animatePageIn();
        if (mainScroll != null) mainScroll.post(() -> mainScroll.scrollTo(0, 0));
    }

    private void showInstallPage() {
        currentPage = "install";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("نصب PedalPro", "");
        addCard("PedalPro Android", "نسخه Native روی این دستگاه نصب است.");
        showAppVersionPage();
    }

    private void showSmsPage() {
        currentPage = "sms";
        if (!signedIn()) { showLogin(); return; }
        boolean admin = bootstrap != null && (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1);
        if (!admin) { showHome(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("پیامک", "");
        LinearLayout box = glassCard(24);
        JSONObject status = bootstrap == null ? null : bootstrap.optJSONObject("sms_status");
        if (status != null) {
            box.addView(text("ارائه‌دهنده: " + status.optString("provider", "FarazSMS"), 13, true, TEXT));
            box.addView(text(status.optBoolean("configured", false) ? "API تنظیم شده" : "API ناقص", 12, false, status.optBoolean("configured", false) ? GREEN : ORANGE));
            box.addView(text("خط فعال: " + status.optString("sender", "ثبت نشده"), 12, false, MUTED));
        } else {
            box.addView(text("وضعیت پیامک از سرور دریافت نشده است.", 12, false, MUTED));
        }
        content.addView(box, cardLp());
        VazirmatnLoader.apply(this, content);
        animatePageIn();
    }

    private View liveRiderCard(JSONObject ride, Dialog sheet) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(89,126,70,14), Color.argb(9,255,255,255)});
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.argb(61,255,190,86));
        card.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        String name = ride == null ? "عضو پدال‌پرو" :
                ride.optString("owner_name", ride.optString("display_name", "عضو پدال‌پرو"));
        String initial = name.trim().isEmpty() ? "پ" : name.trim().substring(0, 1);

        FrameLayout avatarFrame = new FrameLayout(this);
        TextView avatar = text(initial, 17, true, Color.rgb(46,27,3));
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(239,155,47), Color.rgb(255,189,85)});
        avatarBg.setCornerRadius(dp(15));
        avatar.setBackground(avatarBg);
        avatarFrame.addView(avatar, new FrameLayout.LayoutParams(-1, -1));

        View online = new View(this);
        GradientDrawable onlineBg = new GradientDrawable();
        onlineBg.setShape(GradientDrawable.OVAL);
        onlineBg.setColor(GREEN);
        onlineBg.setStroke(dp(2), Color.rgb(11,33,49));
        online.setBackground(onlineBg);
        FrameLayout.LayoutParams onlineLp = new FrameLayout.LayoutParams(dp(13), dp(13), Gravity.BOTTOM | Gravity.START);
        onlineLp.leftMargin = -dp(2);
        onlineLp.bottomMargin = -dp(2);
        avatarFrame.addView(online, onlineLp);
        top.addView(avatarFrame, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.END);
        TextView who = text(name, 13, true, TEXT);
        who.setMaxLines(1);
        String rideTitle = ride == null ? "رکاب‌زنی زنده" :
                ride.optString("challenge_title", ride.optString("event_title", ride.optString("title", "رکاب‌زنی زنده")));
        String meta = rideTitle + "\n" +
                String.format(Locale.US, "%.2f km · %s",
                        ride == null ? 0 : ride.optDouble("distance_km", 0),
                        formatDuration(ride == null ? 0 : ride.optLong("duration_seconds", 0)));
        TextView details = text(meta, 10, false, Color.rgb(199,219,233));
        details.setPadding(0, dp(4), 0, 0);
        details.setLineSpacing(0, 1.6f);
        copy.addView(who);
        copy.addView(details);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1f);
        copyLp.setMargins(dp(8), 0, 0, 0);
        top.addView(copy, copyLp);
        card.addView(top);

        Button watch = plainButton("مشاهده زنده", 12, Color.rgb(255,214,152));
        watch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        watch.setBackground(roundRect(Color.argb(33,255,164,45), 12, Color.argb(102,255,190,86)));
        LinearLayout.LayoutParams watchLp = new LinearLayout.LayoutParams(-1, dp(40));
        watchLp.setMargins(0, dp(9), 0, 0);
        card.addView(watch, watchLp);

        int rideId = ride == null ? 0 : ride.optInt("id", 0);
        watch.setOnClickListener(v -> {
            sheet.dismiss();
            Intent x = new Intent(this, NativeLiveNavigationActivity.class);
            x.putExtra(NativeLiveNavigationActivity.EXTRA_MODE, "live");
            x.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID, rideId);
            x.putExtra(NativeLiveNavigationActivity.EXTRA_TITLE, name);
            startActivity(x);
        });
        return card;
    }

    private void showLiveRidesSheet() {
        JSONArray lives = otherVisibleLiveRides();

        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shell.setPadding(dp(14), dp(10), dp(14), dp(16));
        GradientDrawable shellBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(16,45,64), Color.rgb(7,24,39)});
        shellBg.setCornerRadii(new float[]{dp(28),dp(28),dp(28),dp(28),0,0,0,0});
        shellBg.setStroke(dp(1), Color.argb(51,255,255,255));
        shell.setBackground(shellBg);

        View handle = new View(this);
        handle.setBackground(roundRect(Color.argb(77,255,255,255), 999, 0));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(54), dp(5));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.setMargins(0, dp(2), 0, dp(12));
        shell.addView(handle, handleLp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout headCopy = new LinearLayout(this);
        headCopy.setOrientation(LinearLayout.VERTICAL);
        headCopy.setGravity(Gravity.END);
        headCopy.addView(text("رکاب‌زنی‌های زنده دیگران", 20, true, TEXT));
        String sub = lives.length() > 0
                ? JalaliDate.fa(lives.length()) + " نفر همین حالا فعال هستند"
                : "رکاب‌زن فعالی وجود ندارد";
        TextView subView = text(sub, 11, false, Color.rgb(159,185,201));
        subView.setPadding(0, dp(4), 0, 0);
        headCopy.addView(subView);

        Button close = plainButton("×", 24, TEXT);
        close.setAllCaps(false);
        close.setBackground(roundRect(Color.argb(23,255,255,255), 13, 0));
        close.setOnClickListener(v -> d.dismiss());

        head.addView(headCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        shell.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(10), 0, dp(8));

        if (lives.length() == 0) {
            TextView empty = text("در حال حاضر رکاب‌زنی زنده دیگری وجود ندارد.", 13, false, Color.rgb(199,217,231));
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(90)));
        } else {
            boolean singleColumn = getResources().getDisplayMetrics().widthPixels /
                    getResources().getDisplayMetrics().density <= 390;
            if (singleColumn) {
                for (int i = 0; i < lives.length(); i++) {
                    JSONObject ride = lives.optJSONObject(i);
                    if (ride == null) continue;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.setMargins(0, 0, 0, dp(8));
                    list.addView(liveRiderCard(ride, d), lp);
                }
            } else {
                for (int i = 0; i < lives.length(); i += 2) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                    for (int j = 0; j < 2; j++) {
                        int idx = i + j;
                        if (idx >= lives.length()) {
                            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                            continue;
                        }
                        JSONObject ride = lives.optJSONObject(idx);
                        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
                        if (j == 0) cp.setMargins(dp(4), 0, 0, dp(8));
                        else cp.setMargins(0, 0, dp(4), dp(8));
                        row.addView(liveRiderCard(ride, d), cp);
                    }
                    list.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
            }
        }

        scroll.addView(list);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        d.setContentView(shell);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            int maxHeight = Math.min(dp(680), Math.round(getResources().getDisplayMetrics().heightPixels * .78f));
            lp.height = maxHeight;
            lp.gravity = Gravity.BOTTOM;
            lp.dimAmount = .58f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        VazirmatnLoader.apply(this, shell);
    }

    private void showEventDetail(JSONObject event) {
        content.removeAllViews();
        currentPage="event_detail";
        selectNav(navActivities);
        addHero(event.optString("title","جزئیات برنامه"),JalaliDate.format(event.optString("event_date",""),true));

        String cover=eventCover(event);
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setClipToOutline(true);
        image.setBackground(roundRect(Color.TRANSPARENT,24,Color.argb(100,245,249,252)));
        RemoteImageLoader.load(this,image,cover,R.drawable.program_reference);
        content.addView(image,new LinearLayout.LayoutParams(-1,dp(220)));

        LinearLayout card=glassCard(24);
        card.addView(text("محل شروع: "+event.optString("meeting_location","—"),13,true,TEXT));
        if(!event.optString("finish_location","").isBlank())card.addView(text("پایان: "+event.optString("finish_location"),12,false,MUTED));
        if(!event.optString("route_name","").isBlank())card.addView(text("مسیر: "+event.optString("route_name"),12,false,MUTED));
        if(event.optDouble("planned_distance",0)>0)card.addView(text("مسافت برنامه: "+JalaliDate.fa(Math.round(event.optDouble("planned_distance",0)))+" کیلومتر",12,false,MUTED));
        if(!event.optString("description","").isBlank())card.addView(text(event.optString("description"),13,false,TEXT));
        content.addView(card,cardLp());

        LinearLayout participants=glassCard(24);
        participants.addView(text("شرکت‌کنندگان",17,true,TEXT));
        JSONArray regs=bootstrap.optJSONArray("registrations");
        int count=0; boolean mine=false; JSONObject me=bootstrap.optJSONObject("user"); int uid=me==null?0:me.optInt("id",0);
        if(regs!=null)for(int i=0;i<regs.length();i++){
            JSONObject r=regs.optJSONObject(i);
            if(r!=null&&r.optInt("event_id",0)==event.optInt("id",0)){
                count++; if(r.optInt("user_id",0)==uid)mine=true;
                participants.addView(text("• "+r.optString("display_name","عضو")+(r.optInt("attended",0)==1?" · حاضر":""),12,false,TEXT));
            }
        }
        if(count==0)participants.addView(text("هنوز کسی ثبت‌نام نکرده است.",12,false,MUTED));
        content.addView(participants,cardLp());

        LinearLayout gallery=glassCard(24);
        gallery.addView(text("آلبوم تصاویر",17,true,TEXT));
        JSONArray photos=bootstrap.optJSONArray("event_photos");
        int pc=0;
        if(photos!=null)for(int i=0;i<photos.length()&&pc<20;i++){
            JSONObject p=photos.optJSONObject(i);
            if(p!=null&&p.optInt("event_id",0)==event.optInt("id",0)){
                ImageView im=new ImageView(this);
                im.setScaleType(ImageView.ScaleType.CENTER_CROP);
                im.setClipToOutline(true);
                im.setBackground(roundRect(Color.TRANSPARENT,18,Color.argb(80,245,249,252)));
                RemoteImageLoader.load(this,im,p.optString("file_name",""),R.drawable.program_reference);
                LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(-1,dp(170));ilp.setMargins(0,dp(8),0,0);gallery.addView(im,ilp);
                String cap=p.optString("caption",p.optString("uploader_name",""));
                if(!cap.isBlank()){TextView ct=text(cap,10,false,MUTED);ct.setPadding(0,dp(3),0,0);gallery.addView(ct);}
                pc++;
            }
        }
        if(pc==0)gallery.addView(text("هنوز تصویری بارگذاری نشده است.",12,false,MUTED));
        boolean canUpload=bootstrap.optBoolean("is_admin",false)||bootstrap.optInt("is_admin",0)==1||mine;
        if(canUpload){
            PedalActionView upload=action(PedalIconView.Icon.PLUS,"افزودن عکس به آلبوم",PedalActionView.Style.OUTLINE);
            upload.setOnClickListener(v->{pendingEventPhotoId=event.optInt("id",0);pickImage(REQ_EVENT_PHOTO);});
            gallery.addView(upload,fullActionLp(50));
        }
        content.addView(gallery,cardLp());

        boolean registered=isRegisteredForEvent(event.optInt("id",0));
        if(event.optInt("registration_open",1)==1&&"planned".equals(event.optString("status","planned"))){
            PedalActionView reg=action(registered?PedalIconView.Icon.CLOSE:PedalIconView.Icon.PLUS,registered?"لغو ثبت‌نام":"ثبت‌نام",registered?PedalActionView.Style.OUTLINE:PedalActionView.Style.PRIMARY);
            reg.setOnClickListener(v->toggleEventRegistration(event.optInt("id",0),!registered,hiddenButtonProxy(reg)));
            content.addView(reg,fullActionLp(54));
        }
        PedalActionView share=action(PedalIconView.Icon.SHARE,"اشتراک‌گذاری",PedalActionView.Style.OUTLINE);
        share.setOnClickListener(v->shareEvent(event));content.addView(share,fullActionLp(54));

        double lat=event.optDouble("meeting_lat",Double.NaN),lng=event.optDouble("meeting_lng",Double.NaN);
        if(Double.isFinite(lat)&&Double.isFinite(lng)){
            PedalActionView nav=action(PedalIconView.Icon.ROUTE,"مسیریابی تا ابتدای مسیر",PedalActionView.Style.PRIMARY);
            nav.setOnClickListener(v->openNeshanPoint(lat,lng));content.addView(nav,fullActionLp(56));
        }
        if(bootstrap.optBoolean("is_admin",false)||bootstrap.optInt("is_admin",0)==1){
            PedalActionView edit=action(PedalIconView.Icon.EDIT,"ویرایش برنامه",PedalActionView.Style.OUTLINE);
            edit.setOnClickListener(v->showEventEditor(event));content.addView(edit,fullActionLp(52));
        }
        PedalActionView back=action(PedalIconView.Icon.BACK,"بازگشت به برنامه‌ها",PedalActionView.Style.OUTLINE);
        back.setOnClickListener(v->showActivities());content.addView(back,fullActionLp(52));
        VazirmatnLoader.apply(this,content);animatePageIn();
    }


    private void showEventEditor(JSONObject event) {
        boolean editing=event!=null;
        pendingEditorCoverUri=null;
        content.removeAllViews();currentPage="event_editor";selectNav(navActivities);
        addHero(editing?"ویرایش برنامه":"برنامه جدید","اطلاعات برنامه باشگاه");

        LinearLayout form=glassCard(24);
        EditText title=input("عنوان");
        EditText date=input("تاریخ/زمان: YYYY-MM-DD HH:mm:ss");
        EditText distance=input("مسافت برنامه (km)");
        EditText meeting=input("محل شروع");
        EditText meetingLat=input("عرض جغرافیایی شروع");
        EditText meetingLng=input("طول جغرافیایی شروع");
        EditText route=input("نام مسیر");
        EditText finish=input("محل پایان");
        EditText finishLat=input("عرض جغرافیایی پایان");
        EditText finishLng=input("طول جغرافیایی پایان");
        EditText desc=input("توضیحات");desc.setSingleLine(false);desc.setMinLines(3);

        if(editing){
            title.setText(event.optString("title",""));date.setText(event.optString("event_date",""));distance.setText(event.optString("planned_distance",""));
            meeting.setText(event.optString("meeting_location",""));meetingLat.setText(event.optString("meeting_lat",""));meetingLng.setText(event.optString("meeting_lng",""));
            route.setText(event.optString("route_name",""));finish.setText(event.optString("finish_location",""));finishLat.setText(event.optString("finish_lat",""));finishLng.setText(event.optString("finish_lng",""));desc.setText(event.optString("description",""));
        }
        TextView jalali=text("نمایش: "+(date.getText().length()>0?JalaliDate.format(date.getText().toString(),true):"—"),11,false,MUTED);
        date.setOnFocusChangeListener((v,has)->{if(!has)jalali.setText("نمایش: "+JalaliDate.format(date.getText().toString(),true));});
        form.addView(title);form.addView(date);form.addView(jalali);form.addView(distance);form.addView(meeting);form.addView(meetingLat);form.addView(meetingLng);form.addView(route);form.addView(finish);form.addView(finishLat);form.addView(finishLng);form.addView(desc);

        PedalActionView cover=action(PedalIconView.Icon.PLUS,"انتخاب تصویر کاور",PedalActionView.Style.OUTLINE);
        cover.setOnClickListener(v->pickImage(REQ_EVENT_COVER));
        form.addView(cover,fullActionLp(50));

        PedalActionView save=action(PedalIconView.Icon.DETAILS,"ذخیره برنامه",PedalActionView.Style.PRIMARY);
        form.addView(save,fullActionLp(56));content.addView(form,cardLp());

        save.setOnClickListener(v->{
            if(title.getText().toString().trim().isBlank()||date.getText().toString().trim().isBlank()){showInlineMessage("اطلاعات ناقص","عنوان و تاریخ برنامه الزامی است.",RED);return;}
            save.setEnabled(false);
            io.execute(()->{
                try{
                    JSONObject payload=new JSONObject()
                            .put("id",editing?event.optInt("id",0):0)
                            .put("title",title.getText().toString().trim())
                            .put("event_date",date.getText().toString().trim())
                            .put("planned_distance",distance.getText().toString().trim())
                            .put("meeting_location",meeting.getText().toString().trim())
                            .put("meeting_lat",meetingLat.getText().toString().trim())
                            .put("meeting_lng",meetingLng.getText().toString().trim())
                            .put("finish_location",finish.getText().toString().trim())
                            .put("finish_lat",finishLat.getText().toString().trim())
                            .put("finish_lng",finishLng.getText().toString().trim())
                            .put("route_name",route.getText().toString().trim())
                            .put("status",editing?event.optString("status","planned"):"planned")
                            .put("registration_open",true)
                            .put("description",desc.getText().toString().trim())
                            .put("cover_image_path",editing?event.optString("cover_image_path",""):"");
                    JSONObject saved=api.post("save_event",payload);
                    int eventId=saved.optInt("id",editing?event.optInt("id",0):0);
                    if(pendingEditorCoverUri!=null&&eventId>0){
                        java.util.LinkedHashMap<String,String> fields=new java.util.LinkedHashMap<>();fields.put("event_id",String.valueOf(eventId));
                        api.postMultipart("upload_event_cover",fields,pendingEditorCoverUri,"photo","event-cover.jpg",getContentResolver().getType(pendingEditorCoverUri));
                    }
                    bootstrap=api.bootstrap();activeRide=bootstrap.optJSONObject("my_active_ride");pendingEditorCoverUri=null;
                    main.post(()->{toast("برنامه ذخیره شد.");showActivities();});
                }catch(Exception e){main.post(()->{save.setEnabled(true);showInlineMessage("ذخیره انجام نشد",e.getMessage(),RED);});}
            });
        });
        PedalActionView cancel=action(PedalIconView.Icon.BACK,"انصراف",PedalActionView.Style.OUTLINE);cancel.setOnClickListener(v->showActivities());content.addView(cancel,fullActionLp(52));
        VazirmatnLoader.apply(this,content);
    }


    private void shareEvent(JSONObject event) {
        Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");String text=event.optString("title","برنامه PedalPro")+"\n"+JalaliDate.format(event.optString("event_date",""),true)+"\n"+event.optString("meeting_location","")+(event.optString("description","").isBlank()?"":"\n"+event.optString("description",""));share.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(share,"اشتراک‌گذاری برنامه"));
    }

    private int eventRegistrationCount(int eventId){JSONArray a=bootstrap==null?null:bootstrap.optJSONArray("registrations");int n=0;if(a!=null)for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&r.optInt("event_id",0)==eventId)n++;}return n;}
    private Button hiddenButtonProxy(View source){Button b=new Button(this);b.setVisibility(View.GONE);b.setTag(source);source.setTag(b);return b;}
    private void setProxyEnabled(Button proxy,boolean enabled){if(proxy==null)return;proxy.setEnabled(enabled);Object src=proxy.getTag();if(src instanceof View){((View)src).setEnabled(enabled);((View)src).setAlpha(enabled?1f:.5f);}}

    private String eventDateForUi(JSONObject e){return JalaliDate.format(e.optString("event_date",""),true);}
    private long parseServerDate(String value){if(value==null||value.isBlank())return 0;try{return java.time.LocalDateTime.parse(value.trim().replace('T',' ').substring(0,19),java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();}catch(Exception e){return 0;}}
    private void openGroupNavigation(){Intent i=new Intent(this,NativeLiveNavigationActivity.class);i.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"group");i.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID,activeRide==null?0:activeRide.optInt("id",0));startActivity(i);}
    private void refreshAndRender(Runnable renderer){showLoadingState("در حال بروزرسانی…");io.execute(()->{try{bootstrap=api.bootstrap();activeRide=bootstrap.optJSONObject("my_active_ride");main.post(renderer);}catch(Exception e){main.post(()->showInlineMessage("خطای بروزرسانی",e.getMessage(),RED));}});}
    private void showLoadingState(String message){content.removeAllViews();LinearLayout box=glassCard(24);ProgressBar p=new ProgressBar(this);TextView t=text(message,13,false,MUTED);t.setGravity(Gravity.CENTER);box.setGravity(Gravity.CENTER);box.addView(p,new LinearLayout.LayoutParams(dp(42),dp(42)));box.addView(t);content.addView(box,cardLp());}
    private void showInlineMessage(String title,String message,int color){LinearLayout box=glassCard(20);TextView h=text(title,15,true,color);TextView b=text(message==null?"":message,12,false,TEXT);b.setPadding(0,dp(5),0,0);box.addView(h);box.addView(b);content.addView(box,0,cardLp());VazirmatnLoader.apply(this,box);}
    private void animatePageIn(){content.setAlpha(0f);content.setTranslationY(dp(8));content.animate().alpha(1f).translationY(0).setDuration(180).start();}

    private void pickImage(int requestCode){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i,requestCode);
    }

    private void uploadEventPhoto(android.net.Uri uri,int eventId){
        if(uri==null||eventId<=0)return;
        showLoadingState("در حال بارگذاری تصویر…");
        io.execute(()->{
            try{
                java.util.LinkedHashMap<String,String> fields=new java.util.LinkedHashMap<>();
                fields.put("event_id",String.valueOf(eventId));fields.put("caption","");
                api.postMultipart("upload_event_photo",fields,uri,"photo","event-photo.jpg",getContentResolver().getType(uri));
                bootstrap=api.bootstrap();activeRide=bootstrap.optJSONObject("my_active_ride");
                JSONObject event=findEvent(eventId);
                main.post(()->{toast("تصویر اضافه شد.");if(event!=null)showEventDetail(event);else showActivities();});
            }catch(Exception e){main.post(()->{showInlineMessage("بارگذاری انجام نشد",e.getMessage(),RED);});}
        });
    }

    private JSONObject findEvent(int eventId){
        JSONArray events=bootstrap==null?null:bootstrap.optJSONArray("events");
        if(events!=null)for(int i=0;i<events.length();i++){JSONObject e=events.optJSONObject(i);if(e!=null&&e.optInt("id",0)==eventId)return e;}
        return null;
    }

    private boolean hasFineLocation() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void toast(String value) {
        Toast.makeText(this, value == null || value.isBlank() ? "خطا" : value, Toast.LENGTH_LONG).show();
    }

    private String formatDuration(long seconds) {
        long s = Math.max(0, seconds);
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void registerTrackingReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TrackingService.ACTION_UPDATE);
        filter.addAction(TrackingService.ACTION_STATUS);
        ContextCompat.registerReceiver(this, trackingReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        android.net.Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}
        if(requestCode==REQ_EVENT_COVER){pendingEditorCoverUri=uri;toast("تصویر کاور انتخاب شد.");}
        else if(requestCode==REQ_EVENT_PHOTO){int id=pendingEventPhotoId;pendingEventPhotoId=0;uploadEventPhoto(uri,id);}
        else if(requestCode==REQ_PROFILE_AVATAR){pendingProfileAvatarUri=uri;toast("تصویر پروفایل انتخاب شد؛ با ذخیره پروفایل ارسال می‌شود.");}
        else if(requestCode==REQ_BIKE_PHOTO){pendingBikePhotoUri=uri;toast("عکس دوچرخه انتخاب شد؛ با ذخیره ارسال می‌شود.");}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (hasFineLocation()) {
                toast("دسترسی GPS فعال شد. دوباره «بررسی GPS» را بزنید.");
            } else {
                toast("برای ثبت دقیق مسیر، دسترسی Precise Location لازم است.");
            }
        } else if (requestCode == REQ_BLE) {
            if (hasBlePermissions()) {
                startSensorScan();
            } else {
                toast("برای اتصال سنسورها اجازه Bluetooth لازم است.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bootstrap != null && signedIn()) refreshBootstrap(false);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(clockTick);
        main.removeCallbacks(chatPoll);
        try { unregisterReceiver(trackingReceiver); } catch (Exception ignored) { }
        try { if (cyclingSensors != null) { cyclingSensors.stopScan(); cyclingSensors.disconnect(); } } catch (Throwable ignored) { }
        try { if (heartRateSensors != null) { heartRateSensors.stopScan(); heartRateSensors.disconnect(); } } catch (Throwable ignored) { }
        io.shutdownNow();
        super.onDestroy();
    }
}
