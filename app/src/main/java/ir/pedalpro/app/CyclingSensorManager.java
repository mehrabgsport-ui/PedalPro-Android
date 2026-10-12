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
import android.bluetooth.le.ScanRecord;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CyclingSensorManager {
    private static final UUID CSC_SERVICE = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");
    private static final UUID CSC_MEASUREMENT = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb");
    private static final UUID POWER_SERVICE = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb");
    private static final UUID POWER_MEASUREMENT = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Activity activity;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, DeviceItem> devices = new LinkedHashMap<>();
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private boolean scanning;
    private boolean connected;
    private String deviceName = "";
    private String deviceAddress = "";
    private double cadenceRpm = 0;
    private int powerW = 0;
    private long lastSampleAt = 0L;
    private int prevCrankRevs = -1;
    private int prevCrankEvent = -1;

    public CyclingSensorManager(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public void startScan() {
        if (!hasPermissions()) return;
        BluetoothManager bm = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            emitStatus("Bluetooth خاموش است.");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            emitStatus("جستجوی سنسور در دسترس نیست.");
            return;
        }
        stopScan();
        devices.clear();
        scanning = true;
        try {
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanner.startScan(null, settings, scanCallback);
            emitStatus("در حال جستجوی Cadence / Power...");
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
        try { if (scanner != null && hasPermissions()) scanner.stopScan(scanCallback); } catch (Throwable ignored) { }
        emitStatus(null);
    }

    @SuppressLint("MissingPermission")
    public void connect(String address) {
        if (!hasPermissions() || address == null || address.isEmpty()) return;
        stopScan();
        BluetoothManager bm = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null) return;
        try {
            BluetoothDevice d = adapter.getRemoteDevice(address);
            disconnect();
            deviceAddress = address;
            deviceName = safeName(d);
            prevCrankRevs = -1;
            prevCrankEvent = -1;
            gatt = d.connectGatt(activity, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            emitStatus("در حال اتصال به " + (deviceName.isEmpty() ? "سنسور" : deviceName) + "...");
        } catch (Throwable e) {
            emitStatus("اتصال به سنسور ممکن نشد.");
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        connected = false;
        try { if (gatt != null) { gatt.disconnect(); gatt.close(); } } catch (Throwable ignored) { }
        gatt = null;
        emitStatus(null);
    }

    public String devicesJson() {
        JSONArray a = new JSONArray();
        synchronized (devices) {
            for (DeviceItem d : devices.values()) {
                JSONObject o = new JSONObject();
                try {
                    o.put("name", d.name);
                    o.put("address", d.address);
                    o.put("rssi", d.rssi);
                    o.put("cadence", d.hasCsc);
                    o.put("power", d.hasPower);
                    a.put(o);
                } catch (Throwable ignored) { }
            }
        }
        return a.toString();
    }

    public String statusJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("supported", true);
            o.put("scanning", scanning);
            o.put("connected", connected);
            o.put("deviceName", deviceName);
            o.put("deviceAddress", deviceAddress);
            o.put("cadenceRpm", cadenceRpm);
            o.put("powerW", powerW);
            o.put("lastSampleAt", lastSampleAt);
        } catch (Throwable ignored) { }
        return o.toString();
    }

    @SuppressLint("MissingPermission")
    private String safeName(BluetoothDevice d) {
        try { String n = d.getName(); return n == null ? "" : n; } catch (Throwable e) { return ""; }
    }

    private boolean hasService(ScanRecord record, UUID uuid) {
        if (record == null) return false;
        List<ParcelUuid> uuids = record.getServiceUuids();
        if (uuids == null) return false;
        for (ParcelUuid u : uuids) if (uuid.equals(u.getUuid())) return true;
        return false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) return;
            boolean csc = hasService(result.getScanRecord(), CSC_SERVICE);
            boolean power = hasService(result.getScanRecord(), POWER_SERVICE);
            if (!csc && !power) return;
            BluetoothDevice d = result.getDevice();
            String address;
            try { address = d.getAddress(); } catch (Throwable e) { return; }
            if (address == null) return;
            synchronized (devices) {
                devices.put(address, new DeviceItem(address, safeName(d), result.getRssi(), csc, power));
            }
        }
        @Override public void onBatchScanResults(List<ScanResult> results) {
            if (results != null) for (ScanResult r : results) onScanResult(0, r);
        }
        @Override public void onScanFailed(int errorCode) {
            scanning = false;
            emitStatus("خطای جستجوی سنسور: " + errorCode);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true;
                try {
                    BluetoothDevice d = g.getDevice();
                    deviceAddress = d.getAddress();
                    String n = safeName(d);
                    if (!n.isEmpty()) deviceName = n;
                    g.discoverServices();
                } catch (Throwable ignored) { }
                emitStatus("سنسور متصل شد.");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                emitStatus("اتصال سنسور قطع شد.");
            }
        }

        @SuppressLint("MissingPermission")
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            enable(g, CSC_SERVICE, CSC_MEASUREMENT);
            enable(g, POWER_SERVICE, POWER_MEASUREMENT);
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            if (ch != null) parse(ch.getUuid(), ch.getValue());
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch, byte[] value) {
            if (ch != null) parse(ch.getUuid(), value);
        }
    };

    @SuppressLint("MissingPermission")
    private void enable(BluetoothGatt g, UUID serviceId, UUID characteristicId) {
        try {
            BluetoothGattService service = g.getService(serviceId);
            if (service == null) return;
            BluetoothGattCharacteristic ch = service.getCharacteristic(characteristicId);
            if (ch == null) return;
            g.setCharacteristicNotification(ch, true);
            BluetoothGattDescriptor d = ch.getDescriptor(CCCD);
            if (d != null) {
                d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(d);
            }
        } catch (Throwable ignored) { }
    }

    private void parse(UUID uuid, byte[] value) {
        if (value == null) return;
        if (CSC_MEASUREMENT.equals(uuid)) parseCsc(value);
        else if (POWER_MEASUREMENT.equals(uuid)) parsePower(value);
    }

    private void parseCsc(byte[] v) {
        if (v.length < 1) return;
        int flags = v[0] & 0xff;
        int i = 1;
        if ((flags & 0x01) != 0) {
            if (v.length < i + 6) return;
            i += 6; // cumulative wheel revs + last wheel event
        }
        if ((flags & 0x02) == 0 || v.length < i + 4) return;
        int crankRevs = u16(v, i);
        int crankEvent = u16(v, i + 2);
        if (prevCrankRevs >= 0 && prevCrankEvent >= 0) {
            int dRev = (crankRevs - prevCrankRevs) & 0xffff;
            int dEvt = (crankEvent - prevCrankEvent) & 0xffff;
            if (dEvt > 0 && dRev >= 0 && dRev < 40) {
                double seconds = dEvt / 1024.0;
                double rpm = (dRev / seconds) * 60.0;
                if (rpm >= 0 && rpm <= 250) cadenceRpm = rpm;
            }
        }
        prevCrankRevs = crankRevs;
        prevCrankEvent = crankEvent;
        lastSampleAt = System.currentTimeMillis();
        emitMeasurement();
    }

    private void parsePower(byte[] v) {
        if (v.length < 4) return;
        int raw = (v[2] & 0xff) | ((v[3] & 0xff) << 8);
        if ((raw & 0x8000) != 0) raw -= 0x10000;
        if (raw < -100 || raw > 3000) return;
        powerW = Math.max(0, raw);
        lastSampleAt = System.currentTimeMillis();
        emitMeasurement();
    }

    private int u16(byte[] v, int i) {
        return (v[i] & 0xff) | ((v[i + 1] & 0xff) << 8);
    }

    private void emitMeasurement() {
        JSONObject o = new JSONObject();
        try {
            o.put("cadence_rpm", Math.round(cadenceRpm * 10.0) / 10.0);
            o.put("power_w", powerW);
            o.put("device_name", deviceName);
            o.put("device_address", deviceAddress);
            o.put("source", "ble_cycling");
            o.put("timestamp", lastSampleAt);
        } catch (Throwable ignored) { }
        final String payload = o.toString();
        main.post(() -> {
            if (webView == null) return;
            try {
                webView.evaluateJavascript("window.PedalProCyclingSensorNative&&window.PedalProCyclingSensorNative(" + JSONObject.quote(payload) + ");", null);
            } catch (Throwable ignored) { }
        });
    }

    private void emitStatus(String message) {
        JSONObject o = new JSONObject();
        try {
            o.put("connected", connected);
            o.put("scanning", scanning);
            o.put("deviceName", deviceName);
            o.put("deviceAddress", deviceAddress);
            o.put("cadenceRpm", cadenceRpm);
            o.put("powerW", powerW);
            o.put("lastSampleAt", lastSampleAt);
            if (message != null) o.put("message", message);
        } catch (Throwable ignored) { }
        final String payload = o.toString();
        main.post(() -> {
            if (webView == null) return;
            try {
                webView.evaluateJavascript("window.PedalProCyclingSensorStatus&&window.PedalProCyclingSensorStatus(" + JSONObject.quote(payload) + ");", null);
            } catch (Throwable ignored) { }
        });
    }

    private static final class DeviceItem {
        final String address, name;
        final int rssi;
        final boolean hasCsc, hasPower;
        DeviceItem(String address, String name, int rssi, boolean hasCsc, boolean hasPower) {
            this.address = address; this.name = name == null ? "" : name; this.rssi = rssi; this.hasCsc = hasCsc; this.hasPower = hasPower;
        }
    }
}
