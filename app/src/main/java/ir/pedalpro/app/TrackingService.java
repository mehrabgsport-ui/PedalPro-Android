package ir.pedalpro.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrackingService extends Service implements LocationListener {
    public static final String PREFS = "pedalpro_native_tracking";
    public static final String ACTION_START = "ir.pedalpro.app.START_TRACKING";
    public static final String ACTION_STOP = "ir.pedalpro.app.STOP_TRACKING";
    public static final String ACTION_UPDATE = "ir.pedalpro.app.TRACK_UPDATE";
    public static final String ACTION_STATUS = "ir.pedalpro.app.TRACK_STATUS";
    public static final String EXTRA_PAYLOAD = "payload";

    private static final String API = "https://pedalpro.ir/api.php";
    private static final String CHANNEL_ID = "pedalpro_ride_tracking";
    private static final int NOTIFICATION_ID = 4107;

    private LocationManager locationManager;
    private SharedPreferences prefs;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private PowerManager.WakeLock wakeLock;
    private long lastAcceptedAt = 0L;
    private long trackingStartedAt = 0L;
    private Location lastAcceptedLocation;
    private final ArrayDeque<Location> rawWindow = new ArrayDeque<>();
    private final ArrayDeque<Location> acceptedWindow = new ArrayDeque<>();

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopTracking();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_START.equals(intent.getAction())) {
            int rideId = intent.getIntExtra("ride_id", 0);
            if (rideId <= 0) {
                broadcastStatus("شناسه رکاب معتبر نیست", true);
                stopSelf();
                return START_NOT_STICKY;
            }
            prefs.edit()
                    .putBoolean("active", true)
                    .putInt("ride_id", rideId)
                    .putString("csrf", intent.getStringExtra("csrf"))
                    .putFloat("max_accuracy", (float) intent.getDoubleExtra("max_accuracy", 25))
                    .putFloat("max_speed_kmh", (float) intent.getDoubleExtra("max_speed_kmh", 100))
                    .putLong("interval_ms", Math.max(1000L, intent.getLongExtra("interval_ms", 2500L)))
                    .apply();
        }

        if (!prefs.getBoolean("active", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("ثبت مسیر در حال انجام است"));
        acquireWakeLock();
        trackingStartedAt = System.currentTimeMillis();
        rawWindow.clear();
        acceptedWindow.clear();
        lastAcceptedLocation = null;
        lastAcceptedAt = 0L;
        beginLocationUpdates();
        flushPending();
        broadcastStatus("GPS اندروید فعال — ثبت پس‌زمینه", false);
        return START_STICKY;
    }

    private void beginLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            broadcastStatus("دسترسی موقعیت مکانی داده نشده است", true);
            stopTracking();
            return;
        }
        if (locationManager == null) locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        long interval = prefs.getLong("interval_ms", 2500L);

        // Warm start: a fresh last-known fix prevents waiting minutes for the first map position.
        try {
            Location best = null;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                Location x = locationManager.getLastKnownLocation(provider);
                if (x == null) continue;
                long age = Math.abs(System.currentTimeMillis() - x.getTime());
                if (age > 30000L) continue;
                if (best == null || (!x.hasAccuracy() || !best.hasAccuracy()) ||
                        (x.hasAccuracy() && best.hasAccuracy() && x.getAccuracy() < best.getAccuracy())) best = x;
            }
            if (best != null && (!best.hasAccuracy() || best.getAccuracy() <= 70f)) onLocationChanged(best);
        } catch (Exception ignored) { }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
        } catch (Exception ignored) { }
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, Math.max(2000L, interval), 0f, this, Looper.getMainLooper());
        } catch (Exception ignored) { }
        broadcastStatus("در حال دریافت چند نمونه GPS برای قفل دقیق موقعیت…", false);
    }

    @Override public void onLocationChanged(Location loc) {
        if (!prefs.getBoolean("active", false) || loc == null) return;

        long now = System.currentTimeMillis();
        long age = loc.getTime() > 0 ? Math.abs(now - loc.getTime()) : 0L;
        if (age > 30000L) return;

        float configuredMax = prefs.getFloat("max_accuracy", 25f);
        float normalMax = Math.max(configuredMax, 35f);
        float firstFixMax = Math.max(normalMax, 60f);
        float effectiveMax = lastAcceptedLocation == null ? firstFixMax : normalMax;

        if (loc.hasAccuracy() && loc.getAccuracy() > effectiveMax) {
            broadcastStatus("GPS ضعیف ±" + Math.round(loc.getAccuracy()) + "m — در حال دقیق‌تر شدن", false);
            return;
        }

        rawWindow.addLast(new Location(loc));
        while (rawWindow.size() > 5) rawWindow.removeFirst();

        Location filtered = medianLocation(rawWindow, loc);
        if (filtered == null) return;

        if (!isPlausible(filtered)) {
            broadcastStatus("پرش غیرمنطقی GPS حذف شد", false);
            return;
        }

        long interval = prefs.getLong("interval_ms", 2500L);
        if (lastAcceptedAt > 0 && now - lastAcceptedAt < interval) return;

        if (lastAcceptedLocation != null) {
            double meters = lastAcceptedLocation.distanceTo(filtered);
            float prevAcc = lastAcceptedLocation.hasAccuracy() ? lastAcceptedLocation.getAccuracy() : 12f;
            float curAcc = filtered.hasAccuracy() ? filtered.getAccuracy() : 12f;
            double noiseRadius = Math.max(1.8, Math.min(6.0, Math.sqrt(Math.max(1.0, prevAcc * curAcc)) * 0.24));
            if (meters < noiseRadius && now - lastAcceptedAt < 15000L) {
                broadcastStatus("GPS پایدار — حرکت کاذب حذف شد", false);
                return;
            }

            long dtMs = filtered.getTime() - lastAcceptedLocation.getTime();
            if (dtMs > 0 && dtMs < 120000L) {
                double kmh = meters / (dtMs / 1000.0) * 3.6;
                float alpha = kmh < 5 ? 0.40f : (kmh < 20 ? 0.64f : 0.84f);
                if (filtered.hasAccuracy() && lastAcceptedLocation.hasAccuracy() &&
                        filtered.getAccuracy() > lastAcceptedLocation.getAccuracy() * 1.4f) alpha *= 0.75f;
                Location smooth = new Location(filtered);
                smooth.setLatitude(lastAcceptedLocation.getLatitude() +
                        alpha * (filtered.getLatitude() - lastAcceptedLocation.getLatitude()));
                smooth.setLongitude(lastAcceptedLocation.getLongitude() +
                        alpha * (filtered.getLongitude() - lastAcceptedLocation.getLongitude()));
                filtered = smooth;
            }
        }

        lastAcceptedAt = now;
        lastAcceptedLocation = new Location(filtered);
        acceptedWindow.addLast(new Location(filtered));
        while (acceptedWindow.size() > 8) acceptedWindow.removeFirst();

        try {
            JSONObject p = new JSONObject();
            p.put("lat", filtered.getLatitude());
            p.put("lng", filtered.getLongitude());
            if (filtered.hasAltitude()) p.put("altitude", filtered.getAltitude()); else p.put("altitude", JSONObject.NULL);
            if (filtered.hasSpeed()) p.put("speed_kmh", filtered.getSpeed() * 3.6); else p.put("speed_kmh", JSONObject.NULL);
            if (filtered.hasAccuracy()) p.put("accuracy", filtered.getAccuracy()); else p.put("accuracy", JSONObject.NULL);
            p.put("timestamp", filtered.getTime() > 0 ? filtered.getTime() : now);
            if (filtered.hasBearing()) p.put("bearing", filtered.getBearing());
            appendPending(p);
            broadcastPoint(p, null);
            flushPending();
            broadcastStatus("GPS پایدار ±" + Math.round(filtered.hasAccuracy() ? filtered.getAccuracy() : 0f) + "m — فیلتر پرش فعال", false);
        } catch (Exception ignored) { }
    }

    private Location medianLocation(ArrayDeque<Location> source, Location newest) {
        if (source.isEmpty()) return null;
        if (source.size() < 3) return new Location(newest);
        List<Double> lats = new ArrayList<>();
        List<Double> lngs = new ArrayList<>();
        List<Float> accuracies = new ArrayList<>();
        List<Double> altitudes = new ArrayList<>();
        List<Float> speeds = new ArrayList<>();
        for (Location x : source) {
            lats.add(x.getLatitude());
            lngs.add(x.getLongitude());
            if (x.hasAccuracy()) accuracies.add(x.getAccuracy());
            if (x.hasAltitude()) altitudes.add(x.getAltitude());
            if (x.hasSpeed()) speeds.add(x.getSpeed());
        }
        Location out = new Location(newest);
        out.setLatitude(medianDouble(lats));
        out.setLongitude(medianDouble(lngs));
        if (!accuracies.isEmpty()) out.setAccuracy(medianFloat(accuracies));
        if (!altitudes.isEmpty()) out.setAltitude(medianDouble(altitudes));
        if (!speeds.isEmpty()) out.setSpeed(medianFloat(speeds));
        return out;
    }

    private boolean isPlausible(Location current) {
        if (lastAcceptedLocation == null) return true;
        long dtMs = current.getTime() - lastAcceptedLocation.getTime();
        if (dtMs <= 0 || dtMs > 120000L) return true;
        double meters = lastAcceptedLocation.distanceTo(current);
        double kmh = meters / (dtMs / 1000.0) * 3.6;
        double configuredMax = Math.max(30.0, prefs.getFloat("max_speed_kmh", 100f));
        double medianRecent = medianRecentSpeedKmh();
        double dynamicLimit = Math.max(55.0, medianRecent * 3.0 + 18.0);
        return kmh <= configuredMax * 1.08 && !(meters > 25.0 && kmh > dynamicLimit);
    }

    private double medianRecentSpeedKmh() {
        if (acceptedWindow.size() < 2) return 0.0;
        List<Location> rows = new ArrayList<>(acceptedWindow);
        List<Double> values = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            Location a = rows.get(i - 1), b = rows.get(i);
            long dt = b.getTime() - a.getTime();
            if (dt <= 0 || dt > 60000L) continue;
            values.add(a.distanceTo(b) / (dt / 1000.0) * 3.6);
        }
        return values.isEmpty() ? 0.0 : medianDouble(values);
    }

    private static double medianDouble(List<Double> values) {
        List<Double> x = new ArrayList<>(values);
        Collections.sort(x);
        int n = x.size(), m = n / 2;
        return n % 2 == 1 ? x.get(m) : (x.get(m - 1) + x.get(m)) / 2.0;
    }

    private static float medianFloat(List<Float> values) {
        List<Float> x = new ArrayList<>(values);
        Collections.sort(x);
        int n = x.size(), m = n / 2;
        return n % 2 == 1 ? x.get(m) : (x.get(m - 1) + x.get(m)) / 2f;
    }

    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private synchronized void appendPending(JSONObject point) {
        try {
            JSONArray arr = new JSONArray(prefs.getString("pending", "[]"));
            arr.put(point);
            if (arr.length() > 3000) {
                JSONArray trimmed = new JSONArray();
                for (int i = arr.length() - 3000; i < arr.length(); i++) trimmed.put(arr.get(i));
                arr = trimmed;
            }
            prefs.edit().putString("pending", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private synchronized JSONObject peekPending() {
        try {
            JSONArray arr = new JSONArray(prefs.getString("pending", "[]"));
            if (arr.length() == 0) return null;
            return arr.getJSONObject(0);
        } catch (Exception e) { return null; }
    }

    private synchronized void removeFirstPending() {
        try {
            JSONArray arr = new JSONArray(prefs.getString("pending", "[]"));
            JSONArray next = new JSONArray();
            for (int i = 1; i < arr.length(); i++) next.put(arr.get(i));
            prefs.edit().putString("pending", next.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void flushPending() {
        if (!flushing.compareAndSet(false, true)) return;
        network.execute(() -> {
            try {
                while (prefs.getBoolean("active", false)) {
                    JSONObject p = peekPending();
                    if (p == null) break;
                    SendResult result = sendPoint(p, false);
                    if (!result.ok) break;
                    removeFirstPending();
                    broadcastPoint(p, result.body);
                }
            } finally {
                flushing.set(false);
            }
        });
    }

    private SendResult sendPoint(JSONObject point, boolean retried) {
        HttpURLConnection c = null;
        try {
            int rideId = prefs.getInt("ride_id", 0);
            JSONObject payload = new JSONObject(point.toString());
            payload.put("ride_id", rideId);

            URL url = new URL(API + "?action=track_point");
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("X-CSRF-Token", prefs.getString("csrf", ""));
            String cookie = CookieManager.getInstance().getCookie("https://pedalpro.ir/");
            if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);

            byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(data.length);
            try (OutputStream os = c.getOutputStream()) { os.write(data); }

            int code = c.getResponseCode();
            String body = readBody(code >= 400 ? c.getErrorStream() : c.getInputStream());

            if (code == 419 && !retried && refreshCsrf()) return sendPoint(point, true);
            if (code >= 200 && code < 300) return new SendResult(true, body);

            String msg = "ارسال GPS به سرور ناموفق بود (" + code + ")";
            try {
                JSONObject j = new JSONObject(body);
                if (j.has("error")) msg = j.optString("error", msg);
            } catch (Exception ignored) { }
            broadcastStatus(msg, true);
            return new SendResult(false, body);
        } catch (Exception e) {
            broadcastStatus("اینترنت در دسترس نیست؛ نقاط GPS روی گوشی نگه‌داری می‌شوند", false);
            return new SendResult(false, null);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private boolean refreshCsrf() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(API + "?action=bootstrap").openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(12000);
            c.setRequestProperty("Accept", "application/json");
            String cookie = CookieManager.getInstance().getCookie("https://pedalpro.ir/");
            if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return false;
            JSONObject j = new JSONObject(readBody(c.getInputStream()));
            String token = j.optString("csrf", "");
            if (token.isEmpty()) return false;
            prefs.edit().putString("csrf", token).apply();
            return true;
        } catch (Exception e) { return false; }
        finally { if (c != null) c.disconnect(); }
    }

    private static String readBody(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void broadcastPoint(JSONObject p, String response) {
        try {
            JSONObject out = new JSONObject();
            out.put("lat", p.optDouble("lat"));
            out.put("lng", p.optDouble("lng"));
            out.put("altitude", p.has("altitude") ? p.opt("altitude") : JSONObject.NULL);
            Object kmh = p.opt("speed_kmh");
            if (kmh instanceof Number) out.put("speed_mps", ((Number) kmh).doubleValue() / 3.6);
            else out.put("speed_mps", JSONObject.NULL);
            out.put("accuracy", p.has("accuracy") ? p.opt("accuracy") : JSONObject.NULL);
            out.put("bearing", p.has("bearing") ? p.opt("bearing") : JSONObject.NULL);
            if (response != null) out.put("response", response);
            Intent i = new Intent(ACTION_UPDATE);
            i.setPackage(getPackageName());
            i.putExtra(EXTRA_PAYLOAD, out.toString());
            sendBroadcast(i);
        } catch (Exception ignored) { }
    }

    private void broadcastStatus(String message, boolean error) {
        try {
            JSONObject out = new JSONObject();
            if (error) out.put("error", message); else out.put("message", message);
            Intent i = new Intent(ACTION_STATUS);
            i.setPackage(getPackageName());
            i.putExtra(EXTRA_PAYLOAD, out.toString());
            sendBroadcast(i);
        } catch (Exception ignored) { }
    }

    private void stopTracking() {
        prefs.edit().putBoolean("active", false).apply();
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "ثبت رکاب‌زنی PedalPro", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("ثبت GPS هنگام رکاب‌زنی در پس‌زمینه");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, TrackingService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("PedalPro — رکاب‌زنی فعال")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "پایان ثبت", stopPi).build())
                .build();
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PedalPro:RideTracking");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    @Override public void onDestroy() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        releaseWakeLock();
        network.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static class SendResult {
        final boolean ok; final String body;
        SendResult(boolean ok, String body) { this.ok = ok; this.body = body; }
    }
}
