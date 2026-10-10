package ir.pedalpro.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class BikeSensorActivity extends Activity {
    private static final int REQ_BLUETOOTH = 2201;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout deviceList;
    private TextView status;
    private EditText circumference;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            renderStatus();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackgroundColor(Color.rgb(9, 17, 23));

        TextView title = text("سنسور سرعت/مسافت دوچرخه (Bluetooth CSC)", 18f);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        circumference = new EditText(this);
        circumference.setText(String.valueOf(BikeSensorManager.get().getCircumferenceMm(this)));
        circumference.setHint("محیط چرخ به میلی‌متر؛ مثال 2105");
        circumference.setInputType(InputType.TYPE_CLASS_NUMBER);
        circumference.setTextColor(Color.WHITE);
        circumference.setHintTextColor(Color.GRAY);
        root.addView(circumference);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = button("نمایش دستگاه‌های Pair شده");
        Button disconnect = button("قطع اتصال");
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(52), 1f));
        actions.addView(disconnect, new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(actions);

        status = text("", 14f);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(deviceList);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        refresh.setOnClickListener(v -> ensurePermissionAndLoad());
        disconnect.setOnClickListener(v -> {
            BikeSensorManager.get().disconnect();
            DiagnosticLogger.log(this, "bike_sensor", "manual disconnect");
            renderStatus();
        });

        ensurePermissionAndLoad();
    }

    @Override protected void onStart() {
        super.onStart();
        handler.post(ticker);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(ticker);
        super.onStop();
    }

    private void ensurePermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, REQ_BLUETOOTH);
            return;
        }
        loadDevices();
    }

    private void loadDevices() {
        deviceList.removeAllViews();
        JSONArray rows = BikeSensorManager.get().bondedDevices(this);
        if (rows.length() == 0) {
            TextView empty = text("دستگاه Pair شده‌ای پیدا نشد. ابتدا سنسور را از تنظیمات Bluetooth گوشی Pair کنید.", 14f);
            deviceList.addView(empty);
            return;
        }

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String name = row.optString("name", "Bluetooth");
            String address = row.optString("address", "");
            Button b = button(name + "\n" + address);
            b.setAllCaps(false);
            b.setOnClickListener(v -> connect(address));
            deviceList.addView(b, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));
        }
    }

    private void connect(String address) {
        int mm = 2105;
        try { mm = Integer.parseInt(circumference.getText().toString().trim()); }
        catch (Throwable ignored) { }
        boolean ok = BikeSensorManager.get().connect(this, address, mm);
        Toast.makeText(this, ok ? "در حال اتصال به سنسور…" : "اتصال شروع نشد", Toast.LENGTH_SHORT).show();
        renderStatus();
    }

    private void renderStatus() {
        BikeSensorManager m = BikeSensorManager.get();
        String state = m.isConnected() ? "متصل" : "قطع";
        status.setText("وضعیت: " + state +
                "\nسرعت سنسور: " + String.format(java.util.Locale.US, "%.1f km/h", m.getSpeedMps() * 3.6) +
                "\nمسافت این رکاب: " + String.format(java.util.Locale.US, "%.3f km", m.getRideDistanceMeters() / 1000.0));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLUETOOTH) loadDevices();
    }

    private TextView text(String value, float size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
