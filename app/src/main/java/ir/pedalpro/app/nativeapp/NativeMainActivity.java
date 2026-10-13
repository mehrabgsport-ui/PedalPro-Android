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
    private LinearLayout appRoot;\n    private LinearLayout webHeader;\n    private TextView rideDockLabel;
    private String currentPage = "dashboard";
    private boolean manualPaused = false;
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

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(32));
        content.setClipChildren(false);
        scroll.addView(content);
        appRoot.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        appRoot.addView(buildWebBottomNav(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(116)));

        stageRoot.addView(appRoot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(stageRoot);
        VazirmatnLoader.apply(this, stageRoot);
    }


    private void refreshBootstrap(boolean firstLaunch) {
        setTopStatus("در حال دریافت اطلاعات…", MUTED);
        io.execute(() -> {
            try {
                JSONObject d = api.bootstrap();
                bootstrap = d;
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
                    JSONObject user = d.optJSONObject("user");
                    if (user == null) {
                        setTopStatus("ورود به PedalPro", MUTED);
                        showLogin();
                    } else {
                        setTopStatus("متصل · " + user.optString("display_name", user.optString("username", "PedalPro")), GREEN);
                        if (firstLaunch) showHome();
                        else refreshVisibleScreen();
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    setTopStatus("خطا در اتصال به سرور", RED);
                    if (firstLaunch) showError("ارتباط با PedalPro برقرار نشد.", e.getMessage(), () -> refreshBootstrap(true));
                });
            }
        });
    }

    private void showLogin() {
        selectNav(null);
        content.removeAllViews();
        addHero("ورود به PedalPro", "ورود به حساب کاربری");

        LinearLayout box = card();
        EditText username = input("نام کاربری");
        EditText password = input("رمز عبور");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox remember = new CheckBox(this);
        remember.setText("ورود من روی این دستگاه حفظ شود");
        remember.setTextColor(TEXT);
        remember.setChecked(true);

        Button login = primaryButton("ورود");
        ProgressBar progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);

        box.addView(username);
        box.addView(password);
        box.addView(remember);
        box.addView(login);
        box.addView(progress);
        content.addView(box, cardLp());

        login.setOnClickListener(v -> {
            String u = username.getText().toString().trim();
            String p = password.getText().toString();
            if (u.isBlank() || p.isBlank()) {
                toast("نام کاربری و رمز عبور را وارد کنید.");
                return;
            }
            hideKeyboard();
            login.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            io.execute(() -> {
                try {
                    api.login(u, p, remember.isChecked());
                    JSONObject d = api.bootstrap();
                    bootstrap = d;
                    activeRide = d.optJSONObject("my_active_ride");
                    main.post(() -> {
                        login.setEnabled(true);
                        progress.setVisibility(View.GONE);
                        JSONObject user = d.optJSONObject("user");
                        if (user == null) {
                            toast("ورود تأیید نشد.");
                            return;
                        }
                        setTopStatus("متصل · " + user.optString("display_name", u), GREEN);
                        showHome();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        login.setEnabled(true);
                        progress.setVisibility(View.GONE);
                        toast(e.getMessage());
                    });
                }
            });
        });
    }

    private void showHome() {
        if (!signedIn()) { showLogin(); return; }
        currentPage = "dashboard";
        selectNav(navHome);
        content.removeAllViews();

        LinearLayout hero = glassCard(32);
        hero.setPadding(dp(20), dp(22), dp(20), dp(24));
        TextView eyebrow = text("گروه دوچرخه سواری", 18, true, TEXT);
        hero.addView(eyebrow);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        brand.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView pedal = text("پدال ", 38, true, TEXT);
        TextView pro = text("پرو", 38, true, Color.rgb(31, 231, 242));
        brand.addView(pedal);
        brand.addView(pro);
        hero.addView(brand);

        TextView tagline = text("رکاب بزن، طبیعت، مسیرهای جدید", 13, false, TEXT);
        tagline.setPadding(0, 0, 0, dp(8));
        hero.addView(tagline);

        PedalActionView register = action(PedalIconView.Icon.PLUS, "ثبت نام برنامه", PedalActionView.Style.PRIMARY);
        register.setOnClickListener(v -> showActivities());
        hero.addView(register, fullActionLp(60));

        PedalActionView routes = action(PedalIconView.Icon.ROUTE, "مشاهده مسیرها", PedalActionView.Style.OUTLINE);
        routes.setOnClickListener(v -> showRoutes());
        hero.addView(routes, fullActionLp(58));
        content.addView(hero, cardLp());

        JSONObject event = nextUpcomingEvent();
        content.addView(buildUpcomingCard(event), cardLp());

        content.addView(buildWeatherCard(), cardLp());
        loadWeather(false);

        JSONArray rides = bootstrap.optJSONArray("rides");
        int live = 0;
        if (rides != null) {
            JSONObject me = bootstrap.optJSONObject("user");
            int uid = me == null ? 0 : me.optInt("id", 0);
            for (int i = 0; i < rides.length(); i++) {
                JSONObject r = rides.optJSONObject(i);
                if (r != null && "active".equals(r.optString("status")) && r.optInt("owner_user_id", 0) != uid) live++;
            }
        }
        if (live > 0) {
            PedalActionView liveBtn = action(PedalIconView.Icon.BEACON, "رکاب‌زنی زنده · " + JalaliDate.fa(live), PedalActionView.Style.OUTLINE);
            liveBtn.setOnClickListener(v -> showLiveRidesSheet());
            content.addView(liveBtn, cardLp());
        }
        VazirmatnLoader.apply(this, content);
        animatePageIn();
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
        if (!signedIn()) { showLogin(); return; }
        currentPage = "activities";
        selectNav(navActivities);
        content.removeAllViews();

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        PedalActionView add = action(PedalIconView.Icon.PLUS, "برنامه جدید", PedalActionView.Style.PRIMARY);
        add.setOnClickListener(v -> {
            if (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1) showEventEditor(null);
            else showInlineMessage("دسترسی مدیریت", "ایجاد برنامه فقط برای مدیر گروه فعال است.", ORANGE);
        });
        PedalActionView refresh = action(PedalIconView.Icon.REFRESH, "بروزرسانی", PedalActionView.Style.OUTLINE);
        refresh.setOnClickListener(v -> refreshAndRender(this::showActivities));
        tools.addView(add, rowWeightLp());
        LinearLayout.LayoutParams refreshLp = rowWeightLp(); refreshLp.setMargins(dp(8),0,0,0);
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
        if (!signedIn()) { showLogin(); return; }
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
        if (!signedIn()) { showLogin(); return; }
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
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("چالش‌ها", "");

        JSONArray challenges = bootstrap.optJSONArray("challenges");
        if (challenges == null || challenges.length() == 0) {
            addEmpty("چالشی برای نمایش وجود ندارد.");
            return;
        }
        for (int i = 0; i < challenges.length(); i++) {
            JSONObject ch = challenges.optJSONObject(i);
            if (ch == null) continue;
            LinearLayout box = card();
            boolean official = ch.optInt("is_official", 0) == 1;
            box.addView(text((official ? "★ " : "") + ch.optString("title", "چالش"), 16, true, official ? ORANGE : TEXT));
            String status = ch.optString("status", "");
            int joined = ch.optInt("joiners_count", ch.optInt("participants_count", 0));
            box.addView(text("وضعیت: " + status + " · همراهان: " + joined, 11, false, MUTED));
            String desc = ch.optString("description", "");
            if (!desc.isBlank()) box.addView(text(desc, 12, false, MUTED));
            boolean joinedByMe = ch.optInt("joined_by_me", 0) == 1;
            Button join = primaryButton(joinedByMe ? "لغو همراهی" : "پیوستن به چالش");
            int challengeId = ch.optInt("id", 0);
            join.setOnClickListener(v -> toggleChallengeJoin(challengeId, !joinedByMe, join));
            box.addView(join);
            content.addView(box, cardLp());
        }
    }


    private void toggleChallengeJoin(int challengeId, boolean join, Button button) {
        if (challengeId <= 0) return;
        button.setEnabled(false);
        io.execute(() -> {
            try {
                api.post("join_challenge", new JSONObject().put("challenge_id", challengeId).put("join", join));
                bootstrap = api.bootstrap();
                activeRide = bootstrap.optJSONObject("my_active_ride");
                main.post(() -> {
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
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        JSONObject user = bootstrap.optJSONObject("user");
        if (user == null) return;

        addHero("ویرایش پروفایل", "اطلاعات حساب و تأیید شماره تماس");

        LinearLayout identity=glassCard(24);
        ImageView avatar=new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setClipToOutline(true);
        avatar.setBackground(roundRect(Color.TRANSPARENT,44,Color.argb(100,245,249,252)));
        RemoteImageLoader.load(this,avatar,user.optString("avatar_path",""),R.drawable.pedalpro_app_icon);
        LinearLayout avWrap=new LinearLayout(this);avWrap.setGravity(Gravity.CENTER);avWrap.addView(avatar,new LinearLayout.LayoutParams(dp(88),dp(88)));
        identity.addView(avWrap);
        PedalActionView pickAvatar=action(PedalIconView.Icon.EDIT,"تغییر تصویر پروفایل",PedalActionView.Style.OUTLINE);
        pickAvatar.setOnClickListener(v->pickImage(REQ_PROFILE_AVATAR));
        identity.addView(pickAvatar,fullActionLp(50));
        content.addView(identity,cardLp());

        LinearLayout box = glassCard(24);
        EditText name = input("نام نمایشی"); name.setText(user.optString("display_name", ""));
        EditText age = input("سن"); age.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); age.setText(user.optString("age", ""));
        EditText height = input("قد (cm)"); height.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL); height.setText(user.optString("height_cm", ""));
        EditText weight = input("وزن (kg)"); weight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL); weight.setText(user.optString("weight_kg", ""));
        EditText bio = input("درباره من"); bio.setSingleLine(false); bio.setMinLines(3); bio.setText(user.optString("bio", ""));
        EditText instagram=input("Instagram");instagram.setText(user.optString("instagram_profile",""));
        EditText telegram=input("Telegram");telegram.setText(user.optString("telegram_profile",""));
        EditText eitaa=input("Eitaa");eitaa.setText(user.optString("eitaa_profile",""));
        EditText bale=input("Bale");bale.setText(user.optString("bale_profile",""));
        EditText strava=input("Strava");strava.setText(user.optString("strava_profile",""));
        EditText relive=input("Relive");relive.setText(user.optString("relive_profile",""));
        CheckBox publicProfile = new CheckBox(this);
        publicProfile.setText("پروفایل عمومی"); publicProfile.setTextColor(TEXT); publicProfile.setChecked(user.optInt("profile_public", 1) == 1);

        PedalActionView save = action(PedalIconView.Icon.DETAILS,"ذخیره پروفایل",PedalActionView.Style.PRIMARY);
        box.addView(name);box.addView(age);box.addView(height);box.addView(weight);box.addView(bio);box.addView(instagram);box.addView(telegram);box.addView(eitaa);box.addView(bale);box.addView(strava);box.addView(relive);box.addView(publicProfile);box.addView(save,fullActionLp(54));
        content.addView(box, cardLp());

        save.setOnClickListener(v -> {
            save.setEnabled(false);
            io.execute(() -> {
                try {
                    api.post("profile_save", new JSONObject()
                            .put("display_name", name.getText().toString().trim())
                            .put("age", JalaliDate.en(age.getText().toString().trim()))
                            .put("height_cm", JalaliDate.en(height.getText().toString().trim()))
                            .put("weight_kg", JalaliDate.en(weight.getText().toString().trim()))
                            .put("bio", bio.getText().toString().trim())
                            .put("profile_public", publicProfile.isChecked())
                            .put("instagram_profile", instagram.getText().toString().trim())
                            .put("telegram_profile", telegram.getText().toString().trim())
                            .put("eitaa_profile", eitaa.getText().toString().trim())
                            .put("bale_profile", bale.getText().toString().trim())
                            .put("strava_profile", strava.getText().toString().trim())
                            .put("relive_profile", relive.getText().toString().trim()));
                    if(pendingProfileAvatarUri!=null){
                        java.util.LinkedHashMap<String,String> fields=new java.util.LinkedHashMap<>();fields.put("kind","avatar");
                        api.postMultipart("profile_image_upload",fields,pendingProfileAvatarUri,"photo","avatar.jpg",getContentResolver().getType(pendingProfileAvatarUri));
                        pendingProfileAvatarUri=null;
                    }
                    bootstrap = api.bootstrap(); activeRide = bootstrap.optJSONObject("my_active_ride");
                    main.post(() -> { toast("پروفایل ذخیره شد."); showMe(); });
                } catch (Exception e) {
                    main.post(() -> { save.setEnabled(true); showInlineMessage("ذخیره پروفایل انجام نشد",e.getMessage(),RED); });
                }
            });
        });

        LinearLayout phoneCard=glassCard(24);
        phoneCard.addView(text("تأیید شماره تلفن",17,true,TEXT));
        String savedPhone=user.optString("phone","");
        boolean verified=!user.optString("phone_verified_at","").isBlank()||user.optInt("phone_verified",0)==1;
        TextView phoneState=text(verified?"شماره تأیید شده":"شماره هنوز تأیید نشده است.",11,true,verified?GREEN:ORANGE);
        phoneCard.addView(phoneState);
        EditText phone=input("شماره موبایل");phone.setText(savedPhone);
        EditText code=input("کد تأیید");code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        PedalActionView sendCode=action(PedalIconView.Icon.CHAT,"ارسال کد",PedalActionView.Style.OUTLINE);
        PedalActionView verify=action(PedalIconView.Icon.DETAILS,"تأیید کد",PedalActionView.Style.PRIMARY);
        phoneCard.addView(phone);phoneCard.addView(code);phoneCard.addView(sendCode,fullActionLp(50));phoneCard.addView(verify,fullActionLp(52));
        content.addView(phoneCard,cardLp());

        sendCode.setOnClickListener(v->{
            String p=JalaliDate.en(phone.getText().toString().trim()); if(p.isBlank()){showInlineMessage("شماره نامعتبر","شماره موبایل را وارد کنید.",RED);return;}
            sendCode.setEnabled(false);io.execute(()->{
                try{api.post("profile_phone_send",new JSONObject().put("phone",p));main.post(()->{sendCode.setEnabled(true);showInlineMessage("کد ارسال شد","کد تأیید به شماره واردشده ارسال شد.",GREEN);});}
                catch(Exception e){main.post(()->{sendCode.setEnabled(true);showInlineMessage("ارسال انجام نشد",e.getMessage(),RED);});}
            });
        });
        verify.setOnClickListener(v->{
            String p=JalaliDate.en(phone.getText().toString().trim()),otp=JalaliDate.en(code.getText().toString().trim());if(p.isBlank()||otp.isBlank()){showInlineMessage("اطلاعات ناقص","شماره و کد را وارد کنید.",RED);return;}
            verify.setEnabled(false);io.execute(()->{
                try{api.post("profile_phone_verify",new JSONObject().put("phone",p).put("code",otp));bootstrap=api.bootstrap();main.post(()->{toast("شماره تلفن تأیید شد.");showProfileEditor();});}
                catch(Exception e){main.post(()->{verify.setEnabled(true);showInlineMessage("تأیید انجام نشد",e.getMessage(),RED);});}
            });
        });

        VazirmatnLoader.apply(this,content);animatePageIn();
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
        setProxyEnabled(button,false);
        io.execute(() -> {
            try {
                if (join) api.post("register_event", new JSONObject().put("event_id", eventId));
                else api.post("unregister_event", new JSONObject().put("event_id", eventId));
                bootstrap = api.bootstrap();
                activeRide = bootstrap.optJSONObject("my_active_ride");
                main.post(() -> {
                    toast(join ? "ثبت‌نام انجام شد." : "ثبت‌نام لغو شد.");
                    showActivities();
                });
            } catch (Exception e) {
                main.post(() -> {
                    setProxyEnabled(button,true);
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
        logo.setImageResource(R.drawable.pedalpro_app_icon);
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

        navHome = bottomItem(PedalIconView.Icon.HOME, "خانه", this::showHome);
        navActivities = bottomItem(PedalIconView.Icon.CALENDAR, "فعالیت‌ها", this::showActivities);
        navRide = centerRideItem();
        navRoutes = bottomItem(PedalIconView.Icon.ROUTE, "مسیرها", this::showRoutes);
        navMe = bottomItem(PedalIconView.Icon.USER, "من", this::showMe);

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

    private View bottomItem(PedalIconView.Icon icon, String label, Runnable run) {
        LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setGravity(Gravity.CENTER);
        host.setClickable(true);
        host.setFocusable(true);
        host.setPadding(dp(6), dp(10), dp(6), dp(8));

        PedalIconView iv = new PedalIconView(this, icon, Color.rgb(238, 248, 255));
        TextView tv = text(label, 11, true, Color.rgb(238, 248, 255));
        tv.setTextSize(11.5f);
        tv.setGravity(Gravity.CENTER);
        host.addView(iv, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.setMargins(0, dp(9), 0, 0);
        host.addView(tv, tlp);
        host.setContentDescription(label);
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
        GradientDrawable cyan = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(29, 239, 244), Color.rgb(21, 209, 238), Color.rgb(43, 178, 255)});
        cyan.setShape(GradientDrawable.OVAL);
        inner.setBackground(cyan);

        PedalIconView bike = new PedalIconView(this, PedalIconView.Icon.RIDE, Color.rgb(6, 39, 55));
        rideDockLabel = text("شروع\nرکاب", 15, true, Color.rgb(6, 39, 55));
        rideDockLabel.setGravity(Gravity.CENTER);
        inner.addView(bike, new LinearLayout.LayoutParams(dp(32), dp(32)));
        inner.addView(rideDockLabel);

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
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(roundRect(Color.rgb(7, 20, 32), 0, Color.rgb(23, 40, 58)));

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        body.addView(logo, new LinearLayout.LayoutParams(-1, dp(116)));

        body.addView(drawerItem(PedalIconView.Icon.HOME, "صفحه اصلی", () -> { d.dismiss(); showHome(); }, "dashboard".equals(currentPage)));
        body.addView(drawerItem(PedalIconView.Icon.CALENDAR, "فعالیت‌ها و برنامه‌ها", () -> { d.dismiss(); showActivities(); }, "activities".equals(currentPage)));
        body.addView(drawerItem(PedalIconView.Icon.LOCATION, "مسیرهای GPS", () -> { d.dismiss(); showRoutes(); }, "routes".equals(currentPage)));

        body.addView(drawerSection("رکاب‌زنی", PedalIconView.Icon.RIDE,
                new String[]{"شروع / ادامه رکاب‌زنی", "سنسورهای Cadence / Power", "رسیدن به گروه در حال حرکت", "اشتراک زنده Beacon"},
                new Runnable[]{
                        () -> { d.dismiss(); showRide(); },
                        () -> { d.dismiss(); showSensors(); },
                        () -> { d.dismiss(); openGroupNavigation(); },
                        () -> { d.dismiss(); startActivity(new Intent(this, NativeSafetyActivity.class)); }
                }, true));

        body.addView(drawerSection("مسیرها و اکتشاف", PedalIconView.Icon.ROUTE,
                new String[]{"مسیرهای من", "مسیرهای پیشنهادی", "Segments", "Heatmap شخصی / باشگاه", "Heatmap عمومی PedalPro", "مسیرهای آفلاین", "Flyover"},
                new Runnable[]{
                        () -> { d.dismiss(); showRoutes(); },
                        () -> { d.dismiss(); startActivity(new Intent(this, NativeSocialActivity.class)); },
                        () -> { d.dismiss(); openAdvancedSection("segments"); },
                        () -> { d.dismiss(); startActivity(new Intent(this, NativeHeatmapActivity.class)); },
                        () -> { d.dismiss(); startActivity(new Intent(this, NativeHeatmapActivity.class)); },
                        () -> { d.dismiss(); openAdvancedSection("offline"); },
                        () -> { d.dismiss(); openAdvancedSection("flyover"); }
                }, false));

        body.addView(drawerSection("باشگاه", PedalIconView.Icon.GROUP,
                new String[]{"اعضا", "چالش‌ها", "رتبه‌بندی هفتگی", "کشف برنامه‌ها", "گالری"},
                new Runnable[]{
                        () -> { d.dismiss(); showClub(); },
                        () -> { d.dismiss(); showChallenges(); },
                        () -> { d.dismiss(); openAdvancedSection("club"); },
                        () -> { d.dismiss(); showActivities(); },
                        () -> { d.dismiss(); showGalleryPage(); }
                }, false));

        body.addView(drawerSection("ارتباطات", PedalIconView.Icon.CHAT,
                new String[]{"چت‌روم", "فید اجتماعی / Follow / Kudos", "مشاور هوشمند"},
                new Runnable[]{
                        () -> { d.dismiss(); showGroupChat(); },
                        () -> { d.dismiss(); startActivity(new Intent(this, NativeSocialActivity.class)); },
                        () -> { d.dismiss(); showAiAdvisor(); }
                }, false));

        body.addView(drawerSection("عملکرد من", PedalIconView.Icon.CHART,
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

        body.addView(drawerSection("ایمنی و حریم خصوصی", PedalIconView.Icon.SHIELD,
                new String[]{"SOS و مخاطبین اضطراری", "Beacon", "Privacy Zones"},
                new Runnable[]{
                        () -> { d.dismiss(); startActivity(new Intent(this, NativeSafetyActivity.class)); },
                        () -> { d.dismiss(); startActivity(new Intent(this, NativeSafetyActivity.class)); },
                        () -> { d.dismiss(); startActivity(new Intent(this, NativeSafetyActivity.class)); }
                }, false));

        body.addView(drawerSection("حساب من", PedalIconView.Icon.USER,
                new String[]{"پروفایل من", "تنظیمات تلفن همراه", "نسخه اپلیکیشن", "درباره ما", "راهنمای استفاده", "نصب PedalPro"},
                new Runnable[]{
                        () -> { d.dismiss(); showProfileEditor(); },
                        () -> { d.dismiss(); showPhoneSettingsPage(); },
                        () -> { d.dismiss(); showAppVersionPage(); },
                        () -> { d.dismiss(); showAboutGroupPage(); },
                        () -> { d.dismiss(); showGuidePage(); },
                        () -> { d.dismiss(); showInstallPage(); }
                }, false));

        boolean admin = bootstrap != null && (bootstrap.optBoolean("is_admin", false) || bootstrap.optInt("is_admin", 0) == 1);
        if (admin) {
            body.addView(drawerSection("مدیریت", PedalIconView.Icon.SETTINGS,
                    new String[]{"تنظیمات سایت", "پیامک"},
                    new Runnable[]{
                            () -> { d.dismiss(); showSiteSettingsPage(); },
                            () -> { d.dismiss(); showSmsPage(); }
                    }, false));
        }

        TextView sideBottom = text("RIDE\nEXPLORE\nTOGETHER", 10, true, Color.rgb(120, 144, 170));
        sideBottom.setGravity(Gravity.CENTER);
        sideBottom.setPadding(0, dp(16), 0, dp(20));
        body.addView(sideBottom);

        sc.addView(body);
        panel.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                Math.min(dp(320), (int) (getResources().getDisplayMetrics().widthPixels * .82f)),
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
            lp.dimAmount = .62f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        panel.setTranslationX(dp(320));
        panel.animate().translationX(0).setDuration(180).start();
        VazirmatnLoader.apply(this, panel);
    }

    private View drawerItem(PedalIconView.Icon icon, String label, Runnable run, boolean active) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setBackground(roundRect(
                active ? Color.argb(42, 46, 230, 166) : Color.TRANSPARENT,
                14,
                active ? Color.argb(130, 46, 230, 166) : 0));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(label);

        TextView tv = text(label, 13, true, active ? Color.rgb(46, 230, 166) : Color.rgb(220, 234, 243));
        row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        PedalIconView iv = new PedalIconView(this, icon, active ? Color.rgb(46, 230, 166) : Color.rgb(220, 234, 243));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
        ip.setMargins(dp(9), 0, 0, 0);
        row.addView(iv, ip);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, dp(3), 0, 0);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> run.run());
        return row;
    }

    private View drawerSection(String title, PedalIconView.Icon icon, String[] labels, Runnable[] actions, boolean startOpen) {
        LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setBackground(roundRect(Color.argb(10, 255, 255, 255), 15, Color.argb(18, 255, 255, 255)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(5), 0, 0);
        host.setLayoutParams(hp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        head.setPadding(dp(12), 0, dp(12), 0);
        PedalIconView sectionIcon = new PedalIconView(this, icon, Color.rgb(220, 234, 243));
        TextView titleView = text(title, 13, true, Color.rgb(220, 234, 243));
        PedalIconView chevron = new PedalIconView(this, PedalIconView.Icon.CHEVRON, Color.rgb(128, 164, 187));
        head.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        head.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(sectionIcon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        host.addView(head, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout subs = new LinearLayout(this);
        subs.setOrientation(LinearLayout.VERTICAL);
        subs.setPadding(dp(7), 0, dp(7), dp(8));
        subs.setVisibility(startOpen ? View.VISIBLE : View.GONE);
        chevron.setRotation(startOpen ? 90 : 0);

        for (int i = 0; i < labels.length; i++) {
            final Runnable action = actions[i];
            TextView sub = text(labels[i], 12, false, Color.rgb(220, 234, 243));
            sub.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            sub.setPadding(dp(10), 0, dp(10), 0);
            sub.setBackground(roundRect(Color.TRANSPARENT, 11, 0));
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
            chevron.animate().rotation(open ? 90 : 0).setDuration(120).start();
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
        Intent i = new Intent(this, NativeAdvancedActivity.class);
        i.putExtra("section", section == null ? "" : section);
        startActivity(i);
    }

    private void showAboutGroupPage() {
        currentPage = "about";
        if (!signedIn()) { showLogin(); return; }
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
        if (!signedIn()) { showLogin(); return; }
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

    private void showPhoneSettingsPage() {
        currentPage = "phone-settings";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("تنظیمات تلفن همراه", "");
        LinearLayout box = glassCard(24);
        box.addView(text("برای جلوگیری از متوقف‌شدن GPS، رکاب‌زنی زنده و موقعیت گروه در پس‌زمینه، محدودیت باتری PedalPro را بردارید.", 12, false, MUTED));
        PedalActionView battery = action(PedalIconView.Icon.SETTINGS, "باز کردن تنظیمات باتری", PedalActionView.Style.PRIMARY);
        battery.setOnClickListener(v -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        });
        PedalActionView app = action(PedalIconView.Icon.SETTINGS, "تنظیمات PedalPro", PedalActionView.Style.OUTLINE);
        app.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        box.addView(battery, fullActionLp(54));
        box.addView(app, fullActionLp(52));
        content.addView(box, cardLp());
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

    private void showGuidePage() {
        currentPage = "guide";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("راهنمای کامل استفاده از PedalPro", "");
        String[] sections = {
                "۱. شروع کار", "۲. صفحه خانه", "۳. شروع و ثبت رکاب‌زنی", "۴. مسیرها و اکتشاف",
                "۵. عملکرد و تمرین", "۶. باشگاه و ارتباطات", "۷. ایمنی و حریم خصوصی",
                "۸. دوچرخه و نگهداری", "۹. تنظیمات تلفن همراه", "۱۰. نسخه و بروزرسانی"
        };
        for (String s : sections) {
            LinearLayout box = glassCard(18);
            box.addView(text(s, 15, true, TEXT));
            content.addView(box, cardLp());
        }
        VazirmatnLoader.apply(this, content);
        animatePageIn();
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

    private void showLiveRidesSheet() {
        JSONArray rides=bootstrap.optJSONArray("rides");JSONObject me=bootstrap.optJSONObject("user");int uid=me==null?0:me.optInt("id",0);
        Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout box=glassCard(30);box.setPadding(dp(16),dp(16),dp(16),dp(20));box.addView(text("رکاب‌زنی‌های زنده",20,true,TEXT));
        int count=0;if(rides!=null)for(int i=0;i<rides.length();i++){JSONObject r=rides.optJSONObject(i);if(r==null||!"active".equals(r.optString("status"))||r.optInt("owner_user_id",0)==uid)continue;count++;LinearLayout row=glassCard(18);row.addView(text(r.optString("owner_name","رکاب‌زن"),15,true,ORANGE));row.addView(text(String.format(Locale.US,"%.2f km · %.1f km/h",r.optDouble("distance_km",0),r.optDouble("avg_speed_kmh",0)),11,false,MUTED));int rideId=r.optInt("id",0);PedalActionView go=action(PedalIconView.Icon.BEACON,"مشاهده و مسیریابی",PedalActionView.Style.PRIMARY);go.setOnClickListener(v->{d.dismiss();Intent x=new Intent(this,NativeLiveNavigationActivity.class);x.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"live");x.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID,rideId);x.putExtra(NativeLiveNavigationActivity.EXTRA_TITLE,r.optString("owner_name","رکاب‌زن"));startActivity(x);});row.addView(go,fullActionLp(50));box.addView(row,cardLp());}
        if(count==0)box.addView(text("رکاب‌زن زنده دیگری دیده نمی‌شود.",13,false,MUTED));
        d.setContentView(box);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));WindowManager.LayoutParams lp=new WindowManager.LayoutParams();lp.copyFrom(w.getAttributes());lp.width=-1;lp.height=-2;lp.gravity=Gravity.BOTTOM;w.setAttributes(lp);}VazirmatnLoader.apply(this,box);
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
