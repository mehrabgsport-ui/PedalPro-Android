package ir.pedalpro.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HeartRateBleManager {
    private static final UUID HR_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HR_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Activity activity;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, DeviceItem> devices = new LinkedHashMap<>();
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private boolean scanning;
    private boolean connected;
    private int bpm;
    private String deviceName = "";
    private String deviceAddress = "";
    private long lastSampleAt;
    private String lastSource = "ble";

    public HeartRateBleManager(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public boolean hasBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public void startScan() {
        if (!hasBlePermissions()) return;
        BluetoothManager bm = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { emitStatus("Bluetooth خاموش است."); return; }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { emitStatus("جستجوی Bluetooth در دسترس نیست."); return; }
        stopScan();
        devices.clear();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(HR_SERVICE)).build());
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanning = true;
        try {
            scanner.startScan(filters, settings, scanCallback);
            emitStatus("در حال جستجوی سنسور ضربان...");
            main.postDelayed(this::stopScan, 10000L);
        } catch (Throwable e) {
            scanning = false;
            emitStatus("شروع جستجوی سنسور ممکن نشد.");
        }
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (!scanning) return;
        scanning = false;
        try { if (scanner != null && hasBlePermissions()) scanner.stopScan(scanCallback); } catch (Throwable ignored) { }
        emitStatus(null);
    }

    @SuppressLint("MissingPermission")
    public void connect(String address) {
        if (!hasBlePermissions() || address == null || address.isEmpty()) return;
        stopScan();
        BluetoothManager bm = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null) return;
        try {
            BluetoothDevice d = adapter.getRemoteDevice(address);
            if (gatt != null) { try { gatt.close(); } catch (Throwable ignored) { } gatt = null; }
            deviceAddress = address;
            deviceName = safeName(d);
            connected = false;
            gatt = d.connectGatt(activity, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            emitStatus("در حال اتصال به " + (deviceName.isEmpty() ? "سنسور" : deviceName) + "...");
        } catch (Throwable e) { emitStatus("اتصال به سنسور ممکن نشد."); }
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        connected = false;
        try { if (gatt != null) { gatt.disconnect(); gatt.close(); } } catch (Throwable ignored) { }
        gatt = null;
        emitStatus(null);
    }

    public void acceptExternalHeartRate(int value, String source, String name, long timestampMs) {
        if (value < 30 || value > 240) return;
        bpm = value;
        lastSource = source == null || source.isEmpty() ? "health_connect" : source;
        deviceName = name == null ? "" : name;
        lastSampleAt = timestampMs > 0 ? timestampMs : System.currentTimeMillis();
        emitHeartRate();
    }

    public String devicesJson() {
        JSONArray a = new JSONArray();
        synchronized (devices) {
            for (DeviceItem d : devices.values()) {
                JSONObject o = new JSONObject();
                try { o.put("name", d.name); o.put("address", d.address); o.put("rssi", d.rssi); a.put(o); } catch (Throwable ignored) { }
            }
        }
        return a.toString();
    }

    public String statusJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("supported", true); o.put("scanning", scanning); o.put("connected", connected); o.put("bpm", bpm);
            o.put("deviceName", deviceName); o.put("deviceAddress", deviceAddress); o.put("source", lastSource); o.put("lastSampleAt", lastSampleAt);
        } catch (Throwable ignored) { }
        return o.toString();
    }

    @SuppressLint("MissingPermission")
    private String safeName(BluetoothDevice d) {
        try { String n = d.getName(); return n == null ? "" : n; } catch (Throwable ignored) { return ""; }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) return;
            BluetoothDevice d = result.getDevice();
            String address;
            try { address = d.getAddress(); } catch (Throwable e) { return; }
            if (address == null) return;
            synchronized (devices) { devices.put(address, new DeviceItem(address, safeName(d), result.getRssi())); }
        }
        @Override public void onBatchScanResults(List<ScanResult> results) {
            if (results != null) for (ScanResult r : results) onScanResult(0, r);
        }
        @Override public void onScanFailed(int errorCode) { scanning = false; emitStatus("خطای جستجوی Bluetooth: " + errorCode); }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true;
                try {
                    BluetoothDevice d = g.getDevice(); deviceAddress = d.getAddress(); String n = safeName(d); if (!n.isEmpty()) deviceName = n;
                } catch (Throwable ignored) { }
                try { g.discoverServices(); } catch (Throwable ignored) { }
                emitStatus("سنسور متصل شد.");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false; emitStatus("اتصال سنسور قطع شد.");
            }
        }

        @SuppressLint("MissingPermission")
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            BluetoothGattService service = g.getService(HR_SERVICE);
            if (service == null) { emitStatus("این دستگاه سرویس استاندارد Heart Rate را ارائه نمی‌کند."); return; }
            BluetoothGattCharacteristic ch = service.getCharacteristic(HR_MEASUREMENT);
            if (ch == null) { emitStatus("Heart Rate Measurement روی این دستگاه پیدا نشد."); return; }
            try {
                g.setCharacteristicNotification(ch, true);
                BluetoothGattDescriptor cccd = ch.getDescriptor(CCCD);
                if (cccd != null) { cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); g.writeDescriptor(cccd); }
            } catch (Throwable e) { emitStatus("فعال‌سازی دریافت ضربان ناموفق بود."); }
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) { handleHeartRate(ch == null ? null : ch.getValue()); }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch, byte[] value) { handleHeartRate(value); }
    };

    private void handleHeartRate(byte[] value) {
        if (value == null || value.length < 2) return;
        int flags = value[0] & 0xff;
        int v = ((flags & 0x01) == 0) ? (value[1] & 0xff) : (value.length >= 3 ? ((value[1] & 0xff) | ((value[2] & 0xff) << 8)) : 0);
        if (v < 30 || v > 240) return;
        bpm = v; lastSource = "ble"; lastSampleAt = System.currentTimeMillis(); emitHeartRate();
    }

    private void emitStatus(String message) {
        JSONObject o = new JSONObject();
        try {
            o.put("supported", true); o.put("scanning", scanning); o.put("connected", connected); o.put("bpm", bpm);
            o.put("deviceName", deviceName); o.put("deviceAddress", deviceAddress); o.put("source", lastSource); o.put("lastSampleAt", lastSampleAt);
            if (message != null) o.put("message", message);
        } catch (Throwable ignored) { }
        String payload = o.toString();
        main.post(() -> { if (webView != null) try { webView.evaluateJavascript("window.PedalProHeartRateStatus&&window.PedalProHeartRateStatus(" + JSONObject.quote(payload) + ");", null); } catch (Throwable ignored) { } });
    }

    private void emitHeartRate() {
        JSONObject o = new JSONObject();
        try {
            o.put("bpm", bpm); o.put("deviceName", deviceName); o.put("deviceAddress", deviceAddress); o.put("source", lastSource); o.put("timestamp", lastSampleAt);
        } catch (Throwable ignored) { }
        String payload = o.toString();
        main.post(() -> { if (webView != null) try { webView.evaluateJavascript("window.PedalProHeartRateNative&&window.PedalProHeartRateNative(" + JSONObject.quote(payload) + ");", null); } catch (Throwable ignored) { } });
    }

    private static final class DeviceItem {
        final String address; final String name; final int rssi;
        DeviceItem(String address, String name, int rssi) { this.address = address; this.name = name == null ? "" : name; this.rssi = rssi; }
    }
}
