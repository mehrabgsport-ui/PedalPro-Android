package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private static final int BG = Color.rgb(6, 17, 30);
    private static final int CARD = Color.rgb(13, 39, 57);
    private static final int TEXT = Color.rgb(236, 248, 252);
    private static final int MUTED = Color.rgb(153, 181, 196);
    private static final int ACCENT = Color.rgb(38, 222, 224);
    private static final int GREEN = Color.rgb(44, 224, 159);
    private static final int ORANGE = Color.rgb(255, 177, 67);
    private static final int RED = Color.rgb(255, 91, 103);

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
    private Button navHome;
    private Button navActivities;
    private Button navRide;
    private Button navRoutes;
    private Button navMe;

    private Location pendingFreshFix;
    private boolean gpsChecking = false;
    private long rideTimerAnchorElapsed = 0L;
    private long rideBaseSeconds = 0L;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (rideTimer != null && activeRide != null) {
                long sec = rideBaseSeconds;
                if (rideTimerAnchorElapsed > 0L) {
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(10));
        TextView title = text("PedalPro", 24, true, TEXT);
        topStatus = text("در حال اتصال…", 11, false, MUTED);
        header.addView(title);
        header.addView(topStatus);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(6), dp(14), dp(18));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(6), dp(6), dp(6), dp(8));
        nav.setBackgroundColor(Color.rgb(7, 28, 43));

        navHome = navButton("خانه", this::showHome);
        navActivities = navButton("فعالیت‌ها", this::showActivities);
        navRide = navButton("رکاب‌زنی", this::showRide);
        navRoutes = navButton("مسیرها", this::showRoutes);
        navMe = navButton("من", this::showMe);

        nav.addView(navHome, weightLp());
        nav.addView(navActivities, weightLp());
        nav.addView(navRide, weightLp());
        nav.addView(navRoutes, weightLp());
        nav.addView(navMe, weightLp());

        root.addView(nav);
        setContentView(root);
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
        addHero("ورود به PedalPro", "نسخه مستقل Android · بدون WebView");

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
        selectNav(navHome);
        content.removeAllViews();

        JSONObject user = bootstrap.optJSONObject("user");
        String name = user == null ? "رکاب‌زن" : user.optString("display_name", "رکاب‌زن");
        addHero("سلام " + name, activeRide == null ? "برای شروع رکاب‌زنی آماده‌ای." : "رکاب‌زنی فعال داری.");

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("فعالیت‌ها", countArray("my_completed_rides")), weightHeightLp(dp(86)));
        metrics.addView(metric("اعضا", countArray("members")), weightHeightLp(dp(86)));
        metrics.addView(metric("Live", liveCount()), weightHeightLp(dp(86)));
        content.addView(metrics, cardLp());

        LinearLayout rideCard = card();
        TextView rideTitle = text(activeRide == null ? "شروع رکاب‌زنی" : activeRide.optString("title", "رکاب‌زنی فعال"), 18, true, TEXT);
        TextView rideMeta = text(activeRide == null
                ? "GPS، زمان و مسیر بعد از تأیید موقعیت تازه شروع می‌شوند."
                : String.format(Locale.US, "%.2f km · %s", activeRide.optDouble("distance_km", 0), formatDuration(activeRide.optLong("duration_seconds", 0))),
                12, false, MUTED);
        Button open = primaryButton(activeRide == null ? "آماده‌سازی GPS" : "بازگشت به رکاب‌زنی");
        open.setOnClickListener(v -> showRide());
        rideCard.addView(rideTitle);
        rideCard.addView(rideMeta);
        rideCard.addView(open);
        content.addView(rideCard, cardLp());

        addLiveRidesSection(3);

        LinearLayout tools = card();
        tools.addView(text("بخش‌های Native", 16, true, TEXT));
        Button club = primaryButton("باشگاه و اعضا");
        club.setOnClickListener(v -> showClub());
        Button challenges = primaryButton("چالش‌ها");
        challenges.setOnClickListener(v -> showChallenges());
        Button sensors = primaryButton("سنسورها");
        sensors.setOnClickListener(v -> showSensors());
        Button perf = primaryButton("عملکرد من");
        perf.setOnClickListener(v -> showPerformance());
        Button ai = primaryButton("مشاور هوش مصنوعی");
        ai.setOnClickListener(v -> showAiAdvisor());
        Button chat = primaryButton("چت گروهی");
        chat.setOnClickListener(v -> showGroupChat());
        tools.addView(club); tools.addView(challenges); tools.addView(sensors); tools.addView(perf); tools.addView(ai); tools.addView(chat);
        content.addView(tools, cardLp());
    }

    private void showRide() {
        if (!signedIn()) { showLogin(); return; }
        selectNav(navRide);
        content.removeAllViews();

        if (activeRide == null) {
            addHero("شروع رکاب‌زنی", "ابتدا GPS تازه و دقیق بررسی می‌شود؛ ثبت مسیر و تایمر فقط بعد از تأیید شما شروع می‌شود.");
            LinearLayout gate = card();
            TextView state = text("GPS هنوز بررسی نشده است.", 15, true, TEXT);
            TextView accuracy = text("دقت: —", 12, false, MUTED);
            Button check = primaryButton("بررسی GPS");
            Button start = primaryButton("شروع ثبت مسیر");
            start.setEnabled(false);

            gate.addView(state);
            gate.addView(accuracy);
            gate.addView(check);
            gate.addView(start);
            content.addView(gate, cardLp());

            check.setOnClickListener(v -> startGpsPreflight(state, accuracy, start, check));
            start.setOnClickListener(v -> createRideAndStartTracking(start, state));
            startGpsPreflight(state, accuracy, start, check);
            return;
        }

        addHero("رکاب‌زنی فعال", activeRide.optString("title", "PedalPro"));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        rideTimer = metricValue("زمان", formatDuration(activeRide.optLong("duration_seconds", 0)));
        rideDistance = metricValue("مسافت km", String.format(Locale.US, "%.2f", activeRide.optDouble("distance_km", 0)));
        rideSpeed = metricValue("سرعت km/h", String.format(Locale.US, "%.1f", activeRide.optDouble("avg_speed_kmh", 0)));
        stats.addView(wrapMetric(rideTimer, "زمان"), weightHeightLp(dp(96)));
        stats.addView(wrapMetric(rideDistance, "مسافت km"), weightHeightLp(dp(96)));
        stats.addView(wrapMetric(rideSpeed, "سرعت km/h"), weightHeightLp(dp(96)));
        content.addView(stats, cardLp());

        LinearLayout gps = card();
        rideGps = text(isTrackingServiceActive() ? "GPS اندروید فعال" : "GPS متوقف است", 13, true,
                isTrackingServiceActive() ? GREEN : ORANGE);
        gps.addView(rideGps);
        content.addView(gps, cardLp());

        Button resume = primaryButton(isTrackingServiceActive() ? "GPS در حال ثبت است" : "ادامه ثبت GPS");
        resume.setEnabled(!isTrackingServiceActive());
        resume.setOnClickListener(v -> startTrackingService(activeRide.optInt("id", 0)));

        Button stop = dangerButton("پایان رکاب‌زنی");
        stop.setOnClickListener(v -> finishRide(stop));

        content.addView(resume);
        content.addView(stop);

        rideBaseSeconds = activeRide.optLong("duration_seconds", 0);
        rideTimerAnchorElapsed = SystemClock.elapsedRealtime();
    }

    private void showActivities() {
        if (!signedIn()) { showLogin(); return; }
        selectNav(navActivities);
        content.removeAllViews();
        addHero("فعالیت‌ها", "آخرین رکاب‌زنی‌های ثبت‌شده");

        JSONArray rides = bootstrap.optJSONArray("my_completed_rides");
        if (rides == null || rides.length() == 0) {
            addEmpty("هنوز فعالیت ثبت‌شده‌ای وجود ندارد.");
            return;
        }
        for (int i = 0; i < Math.min(40, rides.length()); i++) {
            JSONObject r = rides.optJSONObject(i);
            if (r == null) continue;
            addRideSummaryCard(r, false);
        }
    }

    private void showRoutes() {
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
        if (!signedIn()) { showLogin(); return; }
        selectNav(null);
        content.removeAllViews();
        addHero("عملکرد من", "خلاصه Native بر اساس فعالیت‌های ثبت‌شده");

        JSONArray rides = bootstrap.optJSONArray("my_completed_rides");
        if (rides == null) rides = bootstrap.optJSONArray("rides");

        int count = 0;
        double distance = 0;
        double ascent = 0;
        long seconds = 0;
        double weightedSpeedDistance = 0;

        if (rides != null) {
            JSONObject user = bootstrap.optJSONObject("user");
            int uid = user == null ? 0 : user.optInt("id", 0);
            for (int i = 0; i < rides.length(); i++) {
                JSONObject r = rides.optJSONObject(i);
                if (r == null) continue;
                if (!"completed".equals(r.optString("status", "completed"))) continue;
                int owner = r.optInt("owner_user_id", uid);
                if (uid > 0 && owner > 0 && owner != uid) continue;
                double d = Math.max(0, r.optDouble("distance_km", 0));
                distance += d;
                ascent += Math.max(0, r.optDouble("ascent_m", 0));
                seconds += Math.max(0, r.optLong("duration_seconds", 0));
                weightedSpeedDistance += Math.max(0, r.optDouble("avg_speed_kmh", 0)) * d;
                count++;
            }
        }

        LinearLayout a = new LinearLayout(this);
        a.setOrientation(LinearLayout.HORIZONTAL);
        a.addView(wrapMetric(metricValue("رکاب", String.valueOf(count)), "رکاب"), weightHeightLp(dp(96)));
        a.addView(wrapMetric(metricValue("مسافت", String.format(Locale.US, "%.1f", distance)), "km"), weightHeightLp(dp(96)));
        a.addView(wrapMetric(metricValue("صعود", String.format(Locale.US, "%.0f", ascent)), "m"), weightHeightLp(dp(96)));
        content.addView(a, cardLp());

        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        double avg = distance > 0 ? weightedSpeedDistance / distance : 0;
        b.addView(wrapMetric(metricValue("میانگین", String.format(Locale.US, "%.1f", avg)), "km/h"), weightHeightLp(dp(96)));
        b.addView(wrapMetric(metricValue("زمان", formatDuration(seconds)), "کل"), weightHeightLp(dp(96)));
        content.addView(b, cardLp());

        addCard("Training / Sensors", "Cadence و Power از BLE Native خوانده می‌شوند و آماده اتصال به محاسبات Training Load هستند.");
    }

    private void showSensors() {
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

    private void showMe() {
        if (!signedIn()) { showLogin(); return; }
        selectNav(navMe);
        content.removeAllViews();
        JSONObject user = bootstrap.optJSONObject("user");
        addHero(user == null ? "حساب من" : user.optString("display_name", "حساب من"),
                user == null ? "" : "@" + user.optString("username", ""));

        addCard("نسخه اپ", "PedalPro Native 2.0.0-dev · رابط اصلی بدون WebView");
        addCard("GPS", "Foreground TrackingService، Fused GPS و فیلتر مسیر Native فعال است.");
        addCard("BLE", "هسته Cadence / Power / Heart Rate موجود از پروژه قبلی حفظ شده است.");

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
        TextView title = text((live ? "● " : "") + r.optString("owner_name", "PedalPro") + " · " + r.optString("title", "رکاب‌زنی"),
                15, true, live ? ORANGE : TEXT);
        String meta = String.format(Locale.US, "%.2f km · %s · %.1f km/h",
                r.optDouble("distance_km", 0),
                formatDuration(r.optLong("duration_seconds", 0)),
                r.optDouble("avg_speed_kmh", 0));
        box.addView(title);
        box.addView(text(meta, 11, false, MUTED));

        if (navigation && live && Double.isFinite(r.optDouble("last_lat", Double.NaN)) && Double.isFinite(r.optDouble("last_lng", Double.NaN))) {
            Button nav = primaryButton("مسیریابی تا موقعیت فعلی");
            double lat = r.optDouble("last_lat");
            double lng = r.optDouble("last_lng");
            nav.setOnClickListener(v -> {
                Uri uri = Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng);
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception e) { toast("برنامه مسیریابی روی گوشی پیدا نشد."); }
            });
            box.addView(nav);
        }
        content.addView(box, cardLp());
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

    private void refreshVisibleScreen() {
        if (navRide != null && !navRide.isEnabled()) showRide();
        else showHome();
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
        LinearLayout box = card();
        box.addView(text(title, 22, true, TEXT));
        TextView s = text(sub, 13, false, MUTED);
        s.setPadding(0, dp(7), 0, 0);
        box.addView(s);
        content.addView(box, cardLp());
    }

    private void addCard(String title, String body) {
        LinearLayout box = card();
        box.addView(text(title, 16, true, TEXT));
        TextView b = text(body, 12, false, MUTED);
        b.setPadding(0, dp(7), 0, 0);
        box.addView(b);
        content.addView(box, cardLp());
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(14), dp(15), dp(14));
        box.setBackgroundColor(CARD);
        return box;
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
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(TEXT);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void selectNav(Button selected) {
        Button[] all = {navHome, navActivities, navRide, navRoutes, navMe};
        for (Button b : all) {
            if (b == null) continue;
            b.setEnabled(b != selected);
            b.setTextColor(b == selected ? ACCENT : TEXT);
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
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.END);
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
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.rgb(3, 31, 40));
        b.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(10), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button dangerButton(String label) {
        Button b = primaryButton(label);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(157, 48, 61));
        return b;
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
