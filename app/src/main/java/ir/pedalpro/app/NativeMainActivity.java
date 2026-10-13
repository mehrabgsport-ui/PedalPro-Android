package ir.pedalpro.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NativeMainActivity extends Activity {
    private static final int REQ_LOCATION = 2201;
    private static final int REQ_NOTIFICATIONS = 2202;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private NativeApiClient api;
    private JSONObject bootstrap = new JSONObject();
    private FrameLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private TextView title;
    private ProgressBar loading;
    private FusedLocationProviderClient fused;
    private String currentTab = "home";
    private boolean gpsStarting = false;

    private int navy() { return Color.rgb(5, 23, 36); }
    private int panel() { return Color.rgb(9, 42, 61); }
    private int cyan() { return Color.rgb(28, 217, 232); }
    private int text() { return Color.rgb(238, 249, 255); }
    private int muted() { return Color.rgb(155, 181, 198); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new NativeApiClient(this);
        fused = LocationServices.getFusedLocationProviderClient(this);
        buildShell();
        requestNotificationPermissionIfNeeded();
        refreshBootstrap();
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(navy());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(14), dp(10), dp(14), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = new TextView(this);
        brand.setText("PedalPro");
        brand.setTextColor(cyan());
        brand.setTextSize(24);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(56), 1));

        Button refresh = button("↻");
        refresh.setOnClickListener(v -> refreshBootstrap());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(52), dp(44)));
        column.addView(top);

        title = new TextView(this);
        title.setTextColor(text());
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(6), 0, dp(8));
        column.addView(title);

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        column.addView(loading, new LinearLayout.LayoutParams(-1, dp(3)));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(88));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(7));
        nav.setBackgroundColor(Color.rgb(7, 32, 48));
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(-1, dp(74), Gravity.BOTTOM);
        root.addView(nav, navLp);

        setContentView(root);
        rebuildNav();
    }

    private void rebuildNav() {
        nav.removeAllViews();
        addNav("home", "خانه", "⌂");
        addNav("activities", "فعالیت‌ها", "▦");
        addNav("ride", "رکاب", "▶");
        addNav("routes", "مسیرها", "⌖");
        addNav("me", "من", "●");
    }

    private void addNav(String id, String label, String icon) {
        Button b = button(icon + "\n" + label);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(id.equals(currentTab) ? cyan() : text());
        b.setOnClickListener(v -> showTab(id));
        nav.addView(b, new LinearLayout.LayoutParams(0, dp(60), 1));
    }

    private void showTab(String tab) {
        currentTab = tab;
        rebuildNav();
        if (bootstrap.optJSONObject("user") == null) {
            renderLogin();
            return;
        }
        switch (tab) {
            case "activities": renderActivities(); break;
            case "ride": renderRide(); break;
            case "routes": renderRoutes(); break;
            case "me": renderMe(); break;
            default: renderHome();
        }
    }

    private void refreshBootstrap() {
        setBusy(true);
        io.execute(() -> {
            try {
                JSONObject d = api.bootstrap();
                main.post(() -> {
                    bootstrap = d;
                    setBusy(false);
                    showTab(currentTab);
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false);
                    renderError(e.getMessage());
                });
            }
        });
    }

    private void renderLogin() {
        title.setText("ورود به PedalPro");
        clear();
        cardHeader("اپ مستقل Android", "این صفحه Native است و هیچ WebView یا صفحه وبی داخل اپ لود نمی‌شود.");
        EditText u = input("نام کاربری");
        EditText p = input("رمز عبور");
        p.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(u);
        content.addView(space(8));
        content.addView(p);
        content.addView(space(10));
        Button login = primary("ورود");
        login.setOnClickListener(v -> {
            String user = u.getText().toString().trim();
            String pass = p.getText().toString();
            if (user.isEmpty() || pass.isEmpty()) {
                toast("نام کاربری و رمز عبور را وارد کنید.");
                return;
            }
            setBusy(true);
            io.execute(() -> {
                try {
                    api.login(user, pass, true);
                    JSONObject d = api.bootstrap();
                    main.post(() -> {
                        bootstrap = d;
                        setBusy(false);
                        currentTab = "home";
                        showTab("home");
                    });
                } catch (Exception e) {
                    main.post(() -> { setBusy(false); toast(e.getMessage()); });
                }
            });
        });
        content.addView(login);
    }

    private void renderHome() {
        title.setText("خانه");
        clear();
        JSONObject u = bootstrap.optJSONObject("user");
        String name = u == null ? "کاربر" : u.optString("display_name", u.optString("username", "کاربر"));
        cardHeader("سلام " + name, "داشبورد Native PedalPro");

        JSONObject active = bootstrap.optJSONObject("my_active_ride");
        if (active != null) {
            LinearLayout c = card();
            addText(c, "رکاب‌زنی زنده من", 16, true, Color.rgb(61, 235, 174));
            addText(c, active.optString("title", "رکاب‌زنی"), 14, true, text());
            addText(c, String.format(Locale.US, "%.2f km  ·  %s", active.optDouble("distance_km", 0), formatSeconds(active.optInt("duration_seconds", 0))), 12, false, muted());
            Button go = primary("باز کردن حالت رکاب‌زنی");
            go.setOnClickListener(v -> showTab("ride"));
            c.addView(go);
            content.addView(c);
        }

        JSONArray rides = bootstrap.optJSONArray("rides");
        int liveOthers = 0;
        if (rides != null && u != null) {
            int uid = u.optInt("id");
            for (int i=0;i<rides.length();i++) {
                JSONObject r = rides.optJSONObject(i);
                if (r != null && "active".equals(r.optString("status")) && r.optInt("owner_user_id") != uid && r.optInt("public_tracking") == 1) liveOthers++;
            }
        }
        LinearLayout live = card();
        addText(live, "رکاب‌زنی‌های زنده دیگران", 15, true, Color.rgb(255, 180, 64));
        addText(live, liveOthers + " نفر در حال رکاب‌زنی", 13, false, text());
        content.addView(live);

        JSONArray events = bootstrap.optJSONArray("events");
        LinearLayout upcoming = card();
        addText(upcoming, "برنامه‌های پیش رو", 15, true, cyan());
        int shown = 0;
        if (events != null) {
            for (int i=0;i<events.length() && shown<3;i++) {
                JSONObject e = events.optJSONObject(i);
                if (e == null) continue;
                addText(upcoming, "• " + e.optString("title", "برنامه"), 13, false, text());
                shown++;
            }
        }
        if (shown == 0) addText(upcoming, "برنامه‌ای ثبت نشده است.", 12, false, muted());
        content.addView(upcoming);
    }

    private void renderActivities() {
        title.setText("فعالیت‌ها و برنامه‌ها");
        clear();
        JSONArray events = bootstrap.optJSONArray("events");
        if (events == null || events.length() == 0) {
            empty("برنامه‌ای برای نمایش وجود ندارد.");
            return;
        }
        for (int i=0;i<events.length();i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            LinearLayout c = card();
            addText(c, e.optString("title", "برنامه"), 15, true, text());
            addText(c, e.optString("event_date", e.optString("starts_at", "")), 12, false, muted());
            addText(c, e.optString("meeting_location", ""), 12, false, muted());
            content.addView(c);
        }
    }

    private void renderRoutes() {
        title.setText("مسیرها");
        clear();
        JSONArray rides = bootstrap.optJSONArray("rides");
        if (rides == null) { empty("مسیر ثبت‌شده‌ای وجود ندارد."); return; }
        int shown=0;
        JSONObject u=bootstrap.optJSONObject("user");
        int uid=u==null?0:u.optInt("id");
        for (int i=0;i<rides.length() && shown<30;i++) {
            JSONObject r=rides.optJSONObject(i);
            if (r==null || !"completed".equals(r.optString("status")) || (uid>0 && r.optInt("owner_user_id")!=uid)) continue;
            LinearLayout c=card();
            addText(c,r.optString("title","رکاب‌زنی"),15,true,text());
            addText(c,String.format(Locale.US,"%.2f km · %s",r.optDouble("distance_km",0),formatSeconds(r.optInt("duration_seconds",0))),12,false,muted());
            content.addView(c);shown++;
        }
        if(shown==0) empty("هنوز مسیر تکمیل‌شده‌ای ندارید.");
    }

    private void renderMe() {
        title.setText("من");
        clear();
        JSONObject u = bootstrap.optJSONObject("user");
        LinearLayout p = card();
        addText(p, u == null ? "کاربر" : u.optString("display_name", u.optString("username","کاربر")), 18, true, text());
        addText(p, u == null ? "" : "@" + u.optString("username",""), 12, false, muted());
        content.addView(p);

        addMenuCard("عملکرد من", "Training Load، رکوردها و تحلیل");
        addMenuCard("دوچرخه‌های من", "کارکرد و سرویس دوره‌ای");
        addMenuCard("ایمنی و حریم خصوصی", "Beacon، SOS و Privacy Zones");
        addMenuCard("تنظیمات گوشی", "باتری، GPS و Background");

        Button logout = button("خروج از حساب");
        logout.setOnClickListener(v -> io.execute(() -> {
            try { api.logout(); } catch (Exception ignored) {}
            main.post(() -> { bootstrap = new JSONObject(); showTab("home"); });
        }));
        content.addView(logout);
    }

    private void renderRide() {
        title.setText("رکاب‌زنی");
        clear();
        JSONObject active = bootstrap.optJSONObject("my_active_ride");
        if (active != null) {
            LinearLayout c = card();
            addText(c, "رکاب‌زنی فعال", 16, true, Color.rgb(55, 230, 165));
            addText(c, active.optString("title","رکاب‌زنی"), 14, true, text());
            addText(c, String.format(Locale.US,"%.2f km · %s",active.optDouble("distance_km",0),formatSeconds(active.optInt("duration_seconds",0))), 13, false, muted());
            Button resume = primary("ادامه ثبت GPS");
            resume.setOnClickListener(v -> startTrackingService(active.optInt("id"), bootstrap.optString("csrf","")));
            c.addView(resume);
            Button stop = danger("پایان رکاب‌زنی");
            stop.setOnClickListener(v -> confirmStop(active.optInt("id")));
            c.addView(stop);
            content.addView(c);
            return;
        }

        LinearLayout c = card();
        addText(c, "شروع رکاب‌زنی Native", 17, true, cyan());
        addText(c, "ابتدا GPS دقیق و تازه بررسی می‌شود. تا قبل از تأیید شما هیچ رکاب و تایمری شروع نمی‌شود.", 12, false, muted());
        Button start = primary("بررسی GPS و شروع");
        start.setOnClickListener(v -> gpsPreflight());
        c.addView(start);
        content.addView(c);
    }

    private void gpsPreflight() {
        if (gpsStarting) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        gpsStarting = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("در حال بررسی GPS")
                .setMessage("در حال دریافت موقعیت تازه و دقیق…")
                .setNegativeButton("انصراف", (d,w) -> gpsStarting=false)
                .create();
        dialog.show();

        CancellationTokenSource token = new CancellationTokenSource();
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.getToken())
                .addOnSuccessListener(loc -> {
                    gpsStarting = false;
                    if (loc == null) {
                        dialog.setMessage("موقعیت دریافت نشد. دوباره تلاش کنید.");
                        return;
                    }
                    float acc = loc.hasAccuracy() ? loc.getAccuracy() : 999f;
                    if (acc > 35f) {
                        dialog.setMessage("دقت GPS فعلی ±" + Math.round(acc) + " متر است. چند لحظه در فضای باز بمانید و دوباره تلاش کنید.");
                        return;
                    }
                    dialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("GPS آماده است")
                            .setMessage("دقت فعلی ±" + Math.round(acc) + " متر\nثبت مسیر و تایمر فقط با زدن دکمه زیر شروع می‌شود.")
                            .setNegativeButton("انصراف", null)
                            .setPositiveButton("شروع ثبت مسیر", (d,w) -> startRideAt(loc))
                            .show();
                })
                .addOnFailureListener(e -> {
                    gpsStarting=false;
                    dialog.setMessage("دریافت GPS انجام نشد: " + e.getMessage());
                });
    }

    private void startRideAt(Location loc) {
        setBusy(true);
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("challenge_id",0);
                body.put("event_id",0);
                body.put("title","رکاب‌زنی");
                body.put("public_tracking",false);
                body.put("bike_id",0);
                JSONObject d = api.post("start_ride", body);
                int rideId = d.optInt("ride_id");
                if (rideId <= 0) throw new Exception("شناسه رکاب دریافت نشد.");

                JSONObject point = new JSONObject();
                point.put("ride_id", rideId);
                point.put("lat", loc.getLatitude());
                point.put("lng", loc.getLongitude());
                point.put("altitude", loc.hasAltitude()?loc.getAltitude():JSONObject.NULL);
                point.put("speed_kmh", loc.hasSpeed()?loc.getSpeed()*3.6:0);
                point.put("accuracy", loc.hasAccuracy()?loc.getAccuracy():JSONObject.NULL);
                point.put("timestamp", System.currentTimeMillis());
                try { api.post("track_point", point); } catch (Exception ignored) {}

                JSONObject b = api.bootstrap();
                main.post(() -> {
                    bootstrap=b;
                    setBusy(false);
                    startTrackingService(rideId, b.optString("csrf",""));
                    renderRide();
                    toast("ثبت مسیر شروع شد.");
                });
            } catch (Exception e) {
                main.post(() -> { setBusy(false); toast(e.getMessage()); });
            }
        });
    }

    private void startTrackingService(int rideId, String csrf) {
        if (rideId <= 0) return;
        Intent i = new Intent(this, TrackingService.class);
        i.setAction(TrackingService.ACTION_START);
        i.putExtra("ride_id", rideId);
        i.putExtra("csrf", csrf);
        i.putExtra("max_accuracy", 25d);
        i.putExtra("max_speed_kmh", 100d);
        i.putExtra("interval_ms", 1000L);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void confirmStop(int rideId) {
        new AlertDialog.Builder(this)
                .setTitle("پایان رکاب‌زنی")
                .setMessage("ثبت GPS و رکاب‌زنی پایان یابد؟")
                .setNegativeButton("انصراف",null)
                .setPositiveButton("پایان", (d,w) -> stopRide(rideId))
                .show();
    }

    private void stopRide(int rideId) {
        setBusy(true);
        io.execute(() -> {
            try {
                JSONObject body=new JSONObject();
                body.put("ride_id",rideId);
                body.put("notes","");
                api.post("stop_ride",body);
                Intent i=new Intent(this,TrackingService.class);
                i.setAction(TrackingService.ACTION_STOP);
                startService(i);
                JSONObject b=api.bootstrap();
                main.post(() -> { bootstrap=b;setBusy(false);currentTab="routes";showTab("routes"); });
            } catch(Exception e) {
                main.post(() -> { setBusy(false);toast(e.getMessage()); });
            }
        });
    }

    private void renderError(String msg) {
        title.setText("PedalPro");
        clear();
        cardHeader("خطای اتصال", msg == null ? "ارتباط با سرور برقرار نشد." : msg);
        Button retry=primary("تلاش دوباره");
        retry.setOnClickListener(v->refreshBootstrap());
        content.addView(retry);
    }

    private void addMenuCard(String h,String s) {
        LinearLayout c=card();
        addText(c,h,15,true,text());
        addText(c,s,12,false,muted());
        content.addView(c);
    }

    private void cardHeader(String h,String s) {
        LinearLayout c=card();
        addText(c,h,18,true,text());
        addText(c,s,12,false,muted());
        content.addView(c);
    }

    private LinearLayout card() {
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14),dp(13),dp(14),dp(13));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(panel());bg.setCornerRadius(dp(20));bg.setStroke(dp(1),Color.rgb(31,75,96));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(10));
        c.setLayoutParams(lp);
        return c;
    }

    private void addText(LinearLayout parent,String value,int sp,boolean bold,int color) {
        TextView v=new TextView(this);
        v.setText(value);v.setTextColor(color);v.setTextSize(sp);
        v.setGravity(Gravity.RIGHT);
        if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);
        v.setPadding(0,dp(3),0,dp(3));
        parent.addView(v);
    }

    private EditText input(String hint) {
        EditText e=new EditText(this);
        e.setHint(hint);e.setHintTextColor(muted());e.setTextColor(text());e.setTextSize(14);
        e.setBackgroundColor(Color.rgb(12,50,70));e.setPadding(dp(12),dp(10),dp(12),dp(10));
        return e;
    }

    private Button button(String label) {
        Button b=new Button(this);
        b.setText(label);b.setTextColor(text());b.setTextSize(13);b.setAllCaps(false);
        b.setBackgroundColor(Color.rgb(13,55,76));
        return b;
    }

    private Button primary(String label) {
        Button b=button(label);b.setTextColor(Color.rgb(2,32,40));b.setBackgroundColor(cyan());return b;
    }

    private Button danger(String label) {
        Button b=button(label);b.setBackgroundColor(Color.rgb(140,42,55));return b;
    }

    private View space(int h) { View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v; }
    private void empty(String s) { LinearLayout c=card();addText(c,s,13,false,muted());content.addView(c); }
    private void clear() { content.removeAllViews(); }
    private void setBusy(boolean on) { loading.setVisibility(on?View.VISIBLE:View.GONE); }

    private String formatSeconds(int sec) {
        sec=Math.max(0,sec);int h=sec/3600,m=(sec%3600)/60,s=sec%60;
        return String.format(Locale.US,"%02d:%02d:%02d",h,m,s);
    }

    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==REQ_LOCATION && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
            gpsPreflight();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
