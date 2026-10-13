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

    private static final int BG = Color.rgb(6, 17, 30);
    private static final int CARD = Color.argb(188, 13, 28, 44);
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
    private String currentPage = "dashboard";
    private boolean manualPaused = false;
    private NativeRouteView activeRideRouteView;
    private int pendingEventPhotoId = 0;
    private android.net.Uri pendingEditorCoverUri;
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
                LinearLayout.LayoutParams.MATCH_PARENT, dp(88)));

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
                LinearLayout.LayoutParams.MATCH_PARENT, dp(108)));

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
        TextView heading = text("برنامه‌ها و\nفعالیت‌ها", 27, true, TEXT);
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
        addHero("مسیرها و Live", "مسیرهای قابل مشاهده و رکاب‌زنی‌های زنده اعضا");
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
        addHero("باشگاه", "اعضا و وضعیت کلی گروه");

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
        addHero("چالش‌ها", "چالش‌های فعال و برنامه‌ریزی‌شده");

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
        addHero("چت گروهی PedalPro", "پیام‌رسان Native مستقیم به سرور");

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

        addHero("ویرایش پروفایل", "اطلاعات حساب PedalPro");

        LinearLayout box = card();
        EditText name = input("نام نمایشی");
        name.setText(user.optString("display_name", ""));
        EditText age = input("سن");
        age.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        age.setText(user.optString("age", ""));
        EditText height = input("قد (cm)");
        height.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        height.setText(user.optString("height_cm", ""));
        EditText weight = input("وزن (kg)");
        weight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weight.setText(user.optString("weight_kg", ""));
        EditText bio = input("درباره من");
        bio.setSingleLine(false);
        bio.setMinLines(3);
        bio.setText(user.optString("bio", ""));
        CheckBox publicProfile = new CheckBox(this);
        publicProfile.setText("پروفایل عمومی");
        publicProfile.setTextColor(TEXT);
        publicProfile.setChecked(user.optInt("profile_public", 1) == 1);

        Button save = primaryButton("ذخیره پروفایل");
        box.addView(name); box.addView(age); box.addView(height); box.addView(weight); box.addView(bio); box.addView(publicProfile); box.addView(save);
        content.addView(box, cardLp());

        save.setOnClickListener(v -> {
            save.setEnabled(false);
            io.execute(() -> {
                try {
                    api.post("profile_save", new JSONObject()
                            .put("display_name", name.getText().toString().trim())
                            .put("age", age.getText().toString().trim())
                            .put("height_cm", height.getText().toString().trim())
                            .put("weight_kg", weight.getText().toString().trim())
                            .put("bio", bio.getText().toString().trim())
                            .put("profile_public", publicProfile.isChecked())
                            .put("instagram_profile", user.optString("instagram_profile", ""))
                            .put("telegram_profile", user.optString("telegram_profile", ""))
                            .put("eitaa_profile", user.optString("eitaa_profile", ""))
                            .put("bale_profile", user.optString("bale_profile", ""))
                            .put("strava_profile", user.optString("strava_profile", ""))
                            .put("relive_profile", user.optString("relive_profile", "")));
                    bootstrap = api.bootstrap();
                    activeRide = bootstrap.optJSONObject("my_active_ride");
                    main.post(() -> {
                        toast("پروفایل ذخیره شد.");
                        showMe();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        save.setEnabled(true);
                        toast(e.getMessage());
                    });
                }
            });
        });
    }

    private void showBikes() {
        currentPage = "bikes";
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("دوچرخه‌های من", "مدیریت دوچرخه‌های PedalPro");

        Button add = primaryButton("افزودن دوچرخه");
        add.setOnClickListener(v -> showBikeEditor(null));
        content.addView(add);

        JSONArray bikes = bootstrap.optJSONArray("my_bikes");
        if (bikes == null || bikes.length() == 0) {
            addEmpty("هنوز دوچرخه‌ای ثبت نشده است.");
            return;
        }
        for (int i = 0; i < bikes.length(); i++) {
            JSONObject bike = bikes.optJSONObject(i);
            if (bike == null) continue;
            LinearLayout box = card();
            String title = bike.optString("brand_model", bike.optString("bike_type", "دوچرخه"));
            if (bike.optInt("is_primary", 0) == 1) title = "★ " + title;
            box.addView(text(title, 15, true, TEXT));
            String meta = bike.optString("bike_type", "");
            if (!bike.optString("wheel_size", "").isBlank()) meta += " · چرخ " + bike.optString("wheel_size");
            if (bike.optDouble("weight_kg", 0) > 0) meta += " · " + bike.optString("weight_kg") + " kg";
            box.addView(text(meta, 11, false, MUTED));
            Button edit = primaryButton("ویرایش");
            JSONObject captured = bike;
            edit.setOnClickListener(v -> showBikeEditor(captured));
            box.addView(edit);
            content.addView(box, cardLp());
        }
    }

    private void showBikeEditor(JSONObject bike) {
        selectNav(null);
        content.removeAllViews();
        boolean editing = bike != null;
        addHero(editing ? "ویرایش دوچرخه" : "افزودن دوچرخه", "اطلاعات برای محاسبات رکاب‌زنی و نگهداری");

        LinearLayout box = card();
        EditText brand = input("برند / مدل");
        EditText type = input("نوع دوچرخه");
        EditText frame = input("سایز فریم");
        EditText wheel = input("سایز چرخ");
        EditText color = input("رنگ");
        EditText weight = input("وزن دوچرخه (kg)");
        weight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox primary = new CheckBox(this);
        primary.setText("دوچرخه اصلی");
        primary.setTextColor(TEXT);

        if (bike != null) {
            brand.setText(bike.optString("brand_model", ""));
            type.setText(bike.optString("bike_type", ""));
            frame.setText(bike.optString("frame_size", ""));
            wheel.setText(bike.optString("wheel_size", ""));
            color.setText(bike.optString("color", ""));
            weight.setText(bike.optString("weight_kg", ""));
            primary.setChecked(bike.optInt("is_primary", 0) == 1);
        }

        Button save = primaryButton("ذخیره");
        box.addView(brand); box.addView(type); box.addView(frame); box.addView(wheel); box.addView(color); box.addView(weight); box.addView(primary); box.addView(save);

        if (editing) {
            Button delete = dangerButton("حذف دوچرخه");
            delete.setOnClickListener(v -> deleteBike(bike.optInt("id", 0), delete));
            box.addView(delete);
        }
        content.addView(box, cardLp());

        save.setOnClickListener(v -> {
            save.setEnabled(false);
            int id = bike == null ? 0 : bike.optInt("id", 0);
            io.execute(() -> {
                try {
                    api.post("bike_save", new JSONObject()
                            .put("id", id)
                            .put("brand_model", brand.getText().toString().trim())
                            .put("bike_type", type.getText().toString().trim())
                            .put("frame_size", frame.getText().toString().trim())
                            .put("wheel_size", wheel.getText().toString().trim())
                            .put("color", color.getText().toString().trim())
                            .put("weight_kg", weight.getText().toString().trim())
                            .put("is_primary", primary.isChecked()));
                    bootstrap = api.bootstrap();
                    activeRide = bootstrap.optJSONObject("my_active_ride");
                    main.post(() -> {
                        toast("دوچرخه ذخیره شد.");
                        showBikes();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        save.setEnabled(true);
                        toast(e.getMessage());
                    });
                }
            });
        });
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
        addHero(user == null ? "حساب من" : user.optString("display_name", "حساب من"),
                user == null ? "" : "@" + user.optString("username", ""));

        addCard("نسخه اپ", "PedalPro Native 2.0.0-dev · رابط اصلی بدون WebView");
        addCard("GPS", "Foreground TrackingService، Fused GPS و فیلتر مسیر Native فعال است.");
        addCard("BLE", "هسته Cadence / Power / Heart Rate موجود از پروژه قبلی حفظ شده است.");

        Button profile = primaryButton("ویرایش پروفایل");
        profile.setOnClickListener(v -> showProfileEditor());
        content.addView(profile);

        Button bikes = primaryButton("دوچرخه‌های من");
        bikes.setOnClickListener(v -> showBikes());
        content.addView(bikes);

        Button fitness = primaryButton("وضعیت بدن");
        fitness.setOnClickListener(v -> showFitness());
        content.addView(fitness);

        Button refresh = primaryButton("بروزرسانی اطلاعات");
        refresh.setOnClickListener(v -> refreshBootstrap(false));
        content.addView(refresh);

        Button logout = dangerButton("خروج از حساب");
        logout.setOnClickListener(v -> io.execute(() -> {
            try { api.logout(); } catch (Exception ignored) { session.clear(); }
            main.post(() -> {
                bootstrap = null;
                activeRide = null;
                showLogin();
            });
        }));
        content.addView(logout);
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
        button.setEnabled(false);
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
                    button.setEnabled(true);
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
        if (!hasFineLocation()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            state.setText("برای ادامه دسترسی دقیق Location را فعال کنید.");
            state.setTextColor(ORANGE);
            return;
        }

        gpsChecking = true;
        pendingFreshFix = null;
        start.setEnabled(false);
        check.setEnabled(false);
        state.setText("در حال دریافت موقعیت تازه و دقیق…");
        state.setTextColor(ACCENT);
        accuracy.setText("دقت: در حال بررسی");

        CancellationTokenSource token = new CancellationTokenSource();
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.getToken())
                .addOnSuccessListener(this, loc -> {
                    gpsChecking = false;
                    check.setEnabled(true);
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
                        start.setEnabled(true);
                    } else {
                        state.setText("GPS هنوز به دقت مناسب نرسیده است. چند لحظه در فضای باز بمانید.");
                        state.setTextColor(ORANGE);
                    }
                })
                .addOnFailureListener(this, e -> {
                    gpsChecking = false;
                    check.setEnabled(true);
                    state.setText("خطای GPS: " + e.getMessage());
                    state.setTextColor(RED);
                });
    }

    private void createRideAndStartTracking(Button start, TextView state) {
        if (pendingFreshFix == null) return;
        start.setEnabled(false);
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
                    start.setEnabled(true);
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
        button.setEnabled(false);
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
                    button.setEnabled(true);
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


    private void selectNav(View selected) {
        View[] all = {navHome, navActivities, navRide, navRoutes, navMe};
        for (View v : all) {
            if (v == null) continue;
            v.setAlpha(v == selected ? 1f : .78f);
            v.setScaleX(v == selected ? 1.04f : 1f);
            v.setScaleY(v == selected ? 1.04f : 1f);
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
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        h.setPadding(dp(18), dp(8), dp(14), dp(8));
        h.setBackground(headerGradient());

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setContentDescription("PedalPro");
        h.addView(logo, new LinearLayout.LayoutParams(dp(108), dp(66)));
        h.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        PedalActionView account = action(PedalIconView.Icon.USER, "حساب کاربری", PedalActionView.Style.OUTLINE);
        account.setOnClickListener(v -> showMe());
        h.addView(account, new LinearLayout.LayoutParams(dp(128), dp(52)));

        FrameLayout settings = iconSquare(PedalIconView.Icon.SETTINGS, () -> showMe(), "تنظیمات");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(54),dp(52)); sp.setMargins(dp(7),0,0,0);
        h.addView(settings, sp);

        FrameLayout menu = iconSquare(PedalIconView.Icon.MENU, this::showDrawer, "منو");
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(54),dp(52)); mp.setMargins(dp(7),0,0,0);
        h.addView(menu, mp);

        topStatus = new TextView(this); topStatus.setVisibility(View.GONE);
        return h;
    }

    private LinearLayout buildWebBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        nav.setClipChildren(false);
        nav.setPadding(dp(7),dp(9),dp(7),dp(7));
        nav.setBackground(bottomGradient());

        navHome = bottomItem(PedalIconView.Icon.HOME,"خانه",this::showHome);
        navActivities = bottomItem(PedalIconView.Icon.CALENDAR,"برنامه‌ها",this::showActivities);
        navRide = centerRideItem();
        navRoutes = bottomItem(PedalIconView.Icon.ROUTE,"مسیرها",this::showRoutes);
        navMe = bottomItem(PedalIconView.Icon.USER,"من",this::showMe);

        nav.addView(navHome,bottomWeightLp(1f,70));
        nav.addView(navActivities,bottomWeightLp(1f,70));
        LinearLayout center = new LinearLayout(this); center.setGravity(Gravity.CENTER); center.setClipChildren(false);
        navRide.setTranslationY(-dp(17));
        center.addView(navRide,new LinearLayout.LayoutParams(dp(90),dp(90)));
        nav.addView(center,new LinearLayout.LayoutParams(0,dp(96),1.28f));
        nav.addView(navRoutes,bottomWeightLp(1f,70));
        nav.addView(navMe,bottomWeightLp(1f,70));
        return nav;
    }

    private View bottomItem(PedalIconView.Icon icon,String label,Runnable run) {
        LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setGravity(Gravity.CENTER);
        host.setClickable(true); host.setFocusable(true);
        PedalIconView iv = new PedalIconView(this, icon, TEXT);
        TextView tv = text(label, 10, true, TEXT); tv.setGravity(Gravity.CENTER);
        host.addView(iv,new LinearLayout.LayoutParams(dp(28),dp(28)));
        host.addView(tv);
        host.setContentDescription(label);
        host.setOnClickListener(v -> run.run());
        return host;
    }

    private View centerRideItem() {
        FrameLayout ring = new FrameLayout(this);
        GradientDrawable outer = new GradientDrawable(); outer.setShape(GradientDrawable.OVAL);
        outer.setColor(Color.argb(220,16,49,71)); outer.setStroke(dp(1),Color.argb(100,255,255,255));
        ring.setBackground(outer); ring.setElevation(dp(12)); ring.setClickable(true); ring.setContentDescription("شروع رکاب");
        LinearLayout inner = new LinearLayout(this); inner.setOrientation(LinearLayout.VERTICAL); inner.setGravity(Gravity.CENTER);
        GradientDrawable cyan = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(36,239,244),Color.rgb(22,210,239),Color.rgb(43,178,255)});
        cyan.setShape(GradientDrawable.OVAL); cyan.setStroke(dp(2),Color.rgb(116,246,250)); inner.setBackground(cyan);
        PedalIconView bike = new PedalIconView(this,PedalIconView.Icon.RIDE,Color.rgb(2,30,39));
        TextView label = text("شروع\nرکاب",11,true,Color.rgb(2,30,39)); label.setGravity(Gravity.CENTER);
        inner.addView(bike,new LinearLayout.LayoutParams(dp(30),dp(30))); inner.addView(label);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(74),dp(74),Gravity.CENTER); ring.addView(inner,ilp);
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
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16),dp(15),dp(16),dp(15));
        box.setBackground(roundRect(Color.argb(182,8,28,45),radius,Color.argb(100,245,249,252)));
        box.setElevation(dp(3)); return box;
    }

    private GradientDrawable roundRect(int fill,int radius,int stroke) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius));
        if(stroke!=0) g.setStroke(dp(1),stroke); return g;
    }
    private GradientDrawable headerGradient() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(205,51,108,142),Color.argb(214,16,53,75)});
        g.setCornerRadii(new float[]{0,0,0,0,dp(32),dp(32),dp(32),dp(32)});
        g.setStroke(dp(1),Color.argb(105,245,249,252)); return g;
    }
    private GradientDrawable bottomGradient() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(230,24,59,82),Color.argb(240,8,31,49)});
        g.setCornerRadii(new float[]{dp(36),dp(36),dp(36),dp(36),0,0,0,0});
        g.setStroke(dp(1),Color.argb(105,245,249,252)); return g;
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
        if(hasFineLocation()){
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
        final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout panel=glassCard(30);panel.setPadding(dp(14),dp(10),dp(14),dp(10));
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(2),0,dp(2),dp(8));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.pedalpro_app_icon);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);body.addView(logo,new LinearLayout.LayoutParams(-1,dp(140)));
        body.addView(drawerItem(PedalIconView.Icon.HOME,"صفحه اصلی",()->{d.dismiss();showHome();},"dashboard".equals(currentPage)));
        body.addView(drawerItem(PedalIconView.Icon.CALENDAR,"فعالیت‌ها و برنامه‌ها",()->{d.dismiss();showActivities();},"activities".equals(currentPage)));
        body.addView(drawerItem(PedalIconView.Icon.LOCATION,"مسیرهای GPS",()->{d.dismiss();showRoutes();},"routes".equals(currentPage)));
        body.addView(drawerSection("رکاب‌زنی",PedalIconView.Icon.RIDE,new String[]{"شروع / ادامه رکاب‌زنی","سنسورهای Cadence / Power","رسیدن به گروه در حال حرکت","اشتراک زنده Beacon"},new Runnable[]{()->{d.dismiss();showRide();},()->{d.dismiss();showSensors();},()->{d.dismiss();openGroupNavigation();},()->{d.dismiss();startActivity(new Intent(this,NativeSafetyActivity.class));}},true));
        body.addView(drawerSection("مسیرها و اکتشاف",PedalIconView.Icon.ROUTE,new String[]{"Heatmap و Segments","مسیرهای پیشنهادی"},new Runnable[]{()->{d.dismiss();startActivity(new Intent(this,NativeAdvancedActivity.class));},()->{d.dismiss();startActivity(new Intent(this,NativeSocialActivity.class));}},false));
        body.addView(drawerSection("باشگاه",PedalIconView.Icon.GROUP,new String[]{"باشگاه و اعضا","چالش‌ها"},new Runnable[]{()->{d.dismiss();showClub();},()->{d.dismiss();showChallenges();}},false));
        body.addView(drawerSection("ارتباطات",PedalIconView.Icon.CHAT,new String[]{"چت گروهی","مشاور هوش مصنوعی"},new Runnable[]{()->{d.dismiss();showGroupChat();},()->{d.dismiss();showAiAdvisor();}},false));
        body.addView(drawerSection("عملکرد من",PedalIconView.Icon.CHART,new String[]{"عملکرد و تمرین","تحلیل حرفه‌ای","سنسورها"},new Runnable[]{()->{d.dismiss();showPerformance();},()->{d.dismiss();startActivity(new Intent(this,NativeAdvancedActivity.class));},()->{d.dismiss();showSensors();}},false));
        body.addView(drawerSection("ایمنی و حریم خصوصی",PedalIconView.Icon.SHIELD,new String[]{"SOS / Beacon / Privacy"},new Runnable[]{()->{d.dismiss();startActivity(new Intent(this,NativeSafetyActivity.class));}},false));
        body.addView(drawerSection("حساب من",PedalIconView.Icon.USER,new String[]{"پروفایل","دوچرخه‌های من","وضعیت بدن"},new Runnable[]{()->{d.dismiss();showProfileEditor();},()->{d.dismiss();showBikes();},()->{d.dismiss();showFitness();}},false));
        if(bootstrap.optBoolean("is_admin",false)||bootstrap.optInt("is_admin",0)==1)body.addView(drawerItem(PedalIconView.Icon.SETTINGS,"مدیریت",()->{d.dismiss();showActivities();},false));
        TextView server=text("●  متصل به سرور",11,true,GREEN);server.setGravity(Gravity.CENTER);server.setBackground(roundRect(Color.argb(110,5,40,52),18,Color.argb(95,245,249,252)));LinearLayout serverWrap=new LinearLayout(this);serverWrap.setGravity(Gravity.CENTER);serverWrap.setPadding(0,dp(12),0,dp(4));serverWrap.addView(server,new LinearLayout.LayoutParams(dp(150),dp(40)));body.addView(serverWrap);
        sc.addView(body);panel.addView(sc,new LinearLayout.LayoutParams(-1,0,1f));FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(Math.min(dp(380),(int)(getResources().getDisplayMetrics().widthPixels*.88f)),-1,Gravity.END);pp.setMargins(dp(8),dp(8),dp(8),dp(8));stage.addView(panel,pp);d.setContentView(stage);
        if(Build.VERSION.SDK_INT>=31&&siteBackground!=null)siteBackground.setRenderEffect(RenderEffect.createBlurEffect(dp(12),dp(12),Shader.TileMode.CLAMP));
        d.setOnDismissListener(x->{if(Build.VERSION.SDK_INT>=31&&siteBackground!=null)siteBackground.setRenderEffect(RenderEffect.createBlurEffect(dp(2),dp(2),Shader.TileMode.CLAMP));});
        d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));WindowManager.LayoutParams lp=new WindowManager.LayoutParams();lp.copyFrom(w.getAttributes());lp.width=-1;lp.height=-1;lp.gravity=Gravity.END;lp.dimAmount=.68f;w.setAttributes(lp);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
        panel.setTranslationX(dp(360));panel.setAlpha(.2f);panel.animate().translationX(0).alpha(1f).setDuration(220).start();VazirmatnLoader.apply(this,panel);
    }

    private View drawerItem(PedalIconView.Icon icon,String label,Runnable run,boolean active) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);row.setPadding(dp(14),0,dp(14),0);row.setBackground(roundRect(active?Color.argb(150,4,92,106):Color.argb(130,8,42,57),17,active?Color.rgb(31,231,242):Color.argb(80,245,249,252)));row.setClickable(true);row.setContentDescription(label);
        TextView tv=text(label,15,true,active?Color.rgb(31,231,242):TEXT);row.addView(tv,new LinearLayout.LayoutParams(0,-2,1f));PedalIconView iv=new PedalIconView(this,icon,active?Color.rgb(31,231,242):TEXT);row.addView(iv,new LinearLayout.LayoutParams(dp(27),dp(27)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(4),0,0);row.setLayoutParams(lp);row.setOnClickListener(v->run.run());return row;
    }

    private View drawerSection(String title,PedalIconView.Icon icon,String[] labels,Runnable[] actions,boolean startOpen) {
        LinearLayout host=new LinearLayout(this);host.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head=(LinearLayout)drawerItem(icon,title,()->{},false);PedalIconView chevron=new PedalIconView(this,PedalIconView.Icon.CHEVRON,MUTED);head.addView(chevron,0,new LinearLayout.LayoutParams(dp(20),dp(20)));
        LinearLayout subs=new LinearLayout(this);subs.setOrientation(LinearLayout.VERTICAL);subs.setVisibility(startOpen?View.VISIBLE:View.GONE);chevron.setRotation(startOpen?90:0);
        for(int i=0;i<labels.length;i++){final Runnable r=actions[i];TextView sub=text(labels[i],13,false,TEXT);sub.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);sub.setPadding(dp(18),0,dp(18),0);sub.setBackground(roundRect(Color.argb(105,8,42,57),14,Color.argb(70,245,249,252)));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));sp.setMargins(dp(10),dp(3),dp(10),0);subs.addView(sub,sp);sub.setOnClickListener(v->r.run());}
        head.setOnClickListener(v->{boolean open=subs.getVisibility()!=View.VISIBLE;if(open){subs.setVisibility(View.VISIBLE);subs.setAlpha(0);subs.setTranslationY(-dp(6));subs.animate().alpha(1).translationY(0).setDuration(160).start();}else{subs.animate().alpha(0).translationY(-dp(6)).setDuration(120).withEndAction(()->subs.setVisibility(View.GONE)).start();}chevron.animate().rotation(open?90:0).setDuration(160).start();});
        host.addView(head);host.addView(subs);return host;
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
    private Button hiddenButtonProxy(View source){Button b=new Button(this);b.setVisibility(View.GONE);source.setTag(b);return b;}
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
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(trackingReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(trackingReceiver, filter);
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        android.net.Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}
        if(requestCode==REQ_EVENT_COVER){pendingEditorCoverUri=uri;toast("تصویر کاور انتخاب شد.");}
        else if(requestCode==REQ_EVENT_PHOTO){int id=pendingEventPhotoId;pendingEventPhotoId=0;uploadEventPhoto(uri,id);}
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
