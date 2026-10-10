package ir.pedalpro.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;
import java.util.UUID;

public final class BikeSensorManager {
    private static final String PREFS = "pedalpro_bike_sensor";
    private static final UUID CSC_SERVICE = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");
    private static final UUID CSC_MEASUREMENT = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final BikeSensorManager INSTANCE = new BikeSensorManager();

    private BluetoothGatt gatt;
    private Context appContext;
    private volatile boolean connected;
    private volatile long lastSampleAt;
    private volatile double totalDistanceM;
    private volatile double rideBaseDistanceM;
    private volatile double speedMps;
    private volatile double maxRideSpeedMps;
    private long lastWheelRevs = -1L;
    private int lastWheelEvent = -1;
    private double circumferenceM = 2.105;

    private BikeSensorManager() {}

    public static BikeSensorManager get() {
        return INSTANCE;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isFresh() {
        return connected && System.currentTimeMillis() - lastSampleAt <= 4500L;
    }

    public double getSpeedMps() {
        return isFresh() ? Math.max(0.0, speedMps) : 0.0;
    }

    public double getRideDistanceMeters() {
        return Math.max(0.0, totalDistanceM - rideBaseDistanceM);
    }

    public double getMaxRideSpeedKmh() {
        return Math.max(0.0, maxRideSpeedMps * 3.6);
    }

    public void markRideStart() {
        rideBaseDistanceM = totalDistanceM;
        maxRideSpeedMps = 0.0;
    }

    public void markRideResume() {
        // CSC cumulative wheel revolutions continue across pauses. Rebase the next
        // measurement so movement while manually paused cannot leak into ride distance.
        lastWheelRevs = -1L;
        lastWheelEvent = -1;
    }

    public void markRidePaused() {
        speedMps = 0.0;
        lastWheelRevs = -1L;
        lastWheelEvent = -1;
    }

    public void setCircumferenceMm(Context context, int mm) {
        int safe = Math.max(1000, Math.min(3000, mm));
        circumferenceM = safe / 1000.0;
        if (context != null) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt("circumference_mm", safe).apply();
    }

    public int getCircumferenceMm(Context context) {
        if (context != null) {
            int mm = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt("circumference_mm", 2105);
            circumferenceM = Math.max(1000, Math.min(3000, mm)) / 1000.0;
        }
        return (int)Math.round(circumferenceM * 1000.0);
    }

    public String savedAddress(Context context) {
        if (context == null) return "";
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("address", "");
    }

    public JSONArray bondedDevices(Context context) {
        JSONArray out = new JSONArray();
        if (context == null || !hasConnectPermission(context)) return out;
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            Set<BluetoothDevice> devices = adapter == null ? null : adapter.getBondedDevices();
            if (devices == null) return out;
            for (BluetoothDevice d : devices) {
                JSONObject row = new JSONObject();
                row.put("name", d.getName() == null ? "Bluetooth" : d.getName());
                row.put("address", d.getAddress());
                out.put(row);
            }
        } catch (Throwable e) {
            DiagnosticLogger.log(context, "bike_sensor_error", "bonded list", e);
        }
        return out;
    }

    public void autoConnect(Context context) {
        if (context == null || !hasConnectPermission(context)) return;
        String address = savedAddress(context);
        if (address == null || address.isEmpty()) return;
        connect(context, address, getCircumferenceMm(context));
    }

    @SuppressLint("MissingPermission")
    public synchronized boolean connect(Context context, String address, int circumferenceMm) {
        if (context == null || address == null || address.trim().isEmpty()) return false;
        if (!hasConnectPermission(context)) return false;

        appContext = context.getApplicationContext();
        setCircumferenceMm(appContext, circumferenceMm);
        disconnect();

        try {
            BluetoothManager manager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) return false;
            BluetoothDevice device = adapter.getRemoteDevice(address.trim());
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("address", device.getAddress()).apply();
            gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE);
            DiagnosticLogger.log(appContext, "bike_sensor", "connecting " + device.getAddress());
            return gatt != null;
        } catch (Throwable e) {
            DiagnosticLogger.log(appContext, "bike_sensor_error", "connect " + address, e);
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    public synchronized void disconnect() {
        connected = false;
        speedMps = 0.0;
        lastWheelRevs = -1L;
        lastWheelEvent = -1;
        BluetoothGatt x = gatt;
        gatt = null;
        if (x != null) {
            try { x.disconnect(); } catch (Throwable ignored) { }
            try { x.close(); } catch (Throwable ignored) { }
        }
    }

    private boolean hasConnectPermission(Context context) {
        return Build.VERSION.SDK_INT < 31 ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected = true;
                lastSampleAt = System.currentTimeMillis();
                DiagnosticLogger.log(appContext, "bike_sensor", "connected");
                try { g.discoverServices(); } catch (Throwable ignored) { }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                speedMps = 0.0;
                DiagnosticLogger.log(appContext, "bike_sensor", "disconnected status=" + status);
            }
        }

        @Override @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            try {
                BluetoothGattService service = g.getService(CSC_SERVICE);
                BluetoothGattCharacteristic characteristic =
                        service == null ? null : service.getCharacteristic(CSC_MEASUREMENT);
                if (characteristic == null) {
                    DiagnosticLogger.log(appContext, "bike_sensor", "CSC service not found");
                    return;
                }
                g.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(descriptor);
                }
                DiagnosticLogger.log(appContext, "bike_sensor", "CSC notifications enabled");
            } catch (Throwable e) {
                DiagnosticLogger.log(appContext, "bike_sensor_error", "enable CSC", e);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            if (characteristic == null || !CSC_MEASUREMENT.equals(characteristic.getUuid())) return;
            parseMeasurement(characteristic.getValue());
        }
    };

    private synchronized void parseMeasurement(byte[] value) {
        if (value == null || value.length < 1) return;
        try {
            int flags = value[0] & 0xff;
            int index = 1;
            boolean wheelPresent = (flags & 0x01) != 0;
            if (!wheelPresent || value.length < index + 6) return;

            long revs = ((long)value[index] & 0xffL) |
                    (((long)value[index + 1] & 0xffL) << 8) |
                    (((long)value[index + 2] & 0xffL) << 16) |
                    (((long)value[index + 3] & 0xffL) << 24);
            index += 4;
            int event = (value[index] & 0xff) | ((value[index + 1] & 0xff) << 8);

            long now = System.currentTimeMillis();
            if (lastWheelRevs >= 0L && lastWheelEvent >= 0) {
                long deltaRevs = (revs - lastWheelRevs) & 0xffffffffL;
                int deltaEvent = (event - lastWheelEvent) & 0xffff;
                double dt = deltaEvent / 1024.0;
                if (deltaRevs >= 0L && deltaRevs <= 128L && dt > 0.02 && dt <= 20.0) {
                    double deltaM = deltaRevs * circumferenceM;
                    double speed = deltaM / dt;
                    if (speed >= 0.0 && speed <= 40.0) {
                        totalDistanceM += deltaM;
                        speedMps = speed;
                        maxRideSpeedMps = Math.max(maxRideSpeedMps, speed);
                        lastSampleAt = now;
                    }
                }
            }

            lastWheelRevs = revs;
            lastWheelEvent = event;
            lastSampleAt = now;
        } catch (Throwable e) {
            DiagnosticLogger.log(appContext, "bike_sensor_error", "parse CSC", e);
        }
    }
}
