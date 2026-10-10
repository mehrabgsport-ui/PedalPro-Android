package ir.pedalpro.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Locale;

public class LowPowerRideActivity extends Activity {
    private SharedPreferences prefs;
    private TextView timeValue;
    private TextView speedValue;
    private TextView distanceValue;
    private TextView pauseButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered = false;

    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra(TrackingService.EXTRA_PAYLOAD);
            if (payload == null) return;
            try {
                JSONObject j = new JSONObject(payload);
                if (j.has("speed_mps")) {
                    double mps = j.optDouble("speed_mps", 0.0);
                    if (j.optBoolean("paused", prefs.getBoolean("paused", false))) mps = 0.0;
                    speedValue.setText(String.format(Locale.US, "%.1f", Math.max(0.0, mps * 3.6)));
                }
                if (j.has("distance_m")) {
                    distanceValue.setText(String.format(Locale.US, "%.2f", Math.max(0.0, j.optDouble("distance_m", 0.0) / 1000.0)));
                }
            } catch (Throwable ignored) { }
            render();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(TrackingService.PREFS, MODE_PRIVATE);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.screenBrightness = 0.08f;
        getWindow().setAttributes(attrs);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        TextView back = button("↩", 34f);
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(72), dp(72));
        backLp.gravity = Gravity.TOP | Gravity.START;
        backLp.topMargin = dp(14);
        backLp.leftMargin = dp(10);
        root.addView(back, backLp);
        back.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);
        metrics.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams metricsLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        metricsLp.gravity = Gravity.CENTER;
        metricsLp.leftMargin = dp(28);
        metricsLp.rightMargin = dp(28);
        metricsLp.bottomMargin = dp(80);
        root.addView(metrics, metricsLp);

        timeValue = stat(metrics, "زمان", "00:00:00", 58f);
        speedValue = stat(metrics, "سرعت  km/h", "0.0", 94f);
        distanceValue = stat(metrics, "مسافت  km", "0.00", 58f);

        pauseButton = button("⏸", 44f);
        FrameLayout.LayoutParams pauseLp = new FrameLayout.LayoutParams(dp(116), dp(82));
        pauseLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        pauseLp.bottomMargin = dp(42);
        root.addView(pauseButton, pauseLp);
        pauseButton.setOnClickListener(v -> togglePause());

        setContentView(root);
        render();
    }

    @Override protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(TrackingService.ACTION_UPDATE);
            filter.addAction(TrackingService.ACTION_STATUS);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(trackingReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(trackingReceiver, filter);
            }
            receiverRegistered = true;
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(ticker);
        if (receiverRegistered) {
            try { unregisterReceiver(trackingReceiver); } catch (Throwable ignored) { }
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void togglePause() {
        boolean paused = prefs.getBoolean("paused", false);
        Intent i = new Intent(this, TrackingService.class);
        i.setAction(paused ? TrackingService.ACTION_RESUME : TrackingService.ACTION_PAUSE);
        startService(i);
        if (!paused) speedValue.setText("0.0");
        pauseButton.setText(paused ? "⏸" : "▶");
    }

    private void render() {
        if (prefs == null) return;
        boolean paused = prefs.getBoolean("paused", false);
        long elapsed = Math.max(0L, prefs.getLong("elapsed_ms", 0L));
        if (prefs.getBoolean("active", false) && !paused) {
            long segmentStarted = prefs.getLong("segment_started_at", 0L);
            if (segmentStarted > 0L) elapsed += Math.max(0L, System.currentTimeMillis() - segmentStarted);
        }

        long totalSeconds = elapsed / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        timeValue.setText(String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));

        float distanceM = Math.max(0f, prefs.getFloat("distance_m", 0f));
        distanceValue.setText(String.format(Locale.US, "%.2f", distanceM / 1000.0));

        float speedMps = paused ? 0f : Math.max(0f, prefs.getFloat("last_speed_mps", 0f));
        speedValue.setText(String.format(Locale.US, "%.1f", speedMps * 3.6f));
        pauseButton.setText(paused ? "▶" : "⏸");
    }

    private TextView stat(LinearLayout parent, String label, String value, float valueSize) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        boxLp.bottomMargin = dp(18);
        parent.addView(box, boxLp);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(title);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(valueSize);
        valueView.setGravity(Gravity.CENTER);
        valueView.setIncludeFontPadding(false);
        valueView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        box.addView(valueView);
        return valueView;
    }

    private TextView button(String text, float size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(Color.TRANSPARENT);
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
