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
    public static final String ACTION_DISCARD = "ir.pedalpro.app.DISCARD_TRACKING";
    public static final String ACTION_PAUSE = "ir.pedalpro.app.PAUSE_TRACKING";
    public static final String ACTION_RESUME = "ir.pedalpro.app.RESUME_TRACKING";
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
    private long lastGpsFixAt = 0L;
    private long trackingStartedAt = 0L;
    private long resumeCutoffAt = 0L;
    private boolean segmentBreakPending = false;
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

        if (intent != null && ACTION_DISCARD.equals(intent.getAction())) {
            discardTracking();
            return START_NOT_STICKY;
        }

        if (intent != null && ACTION_PAUSE.equals(intent.getAction())) {
            pauseTracking();
            return START_STICKY;
        }

        if (intent != null && ACTION_RESUME.equals(intent.getAction())) {
            resumeTracking();
            return START_STICKY;
        }

        if (intent != null && ACTION_START.equals(intent.getAction())) {
            int rideId = intent.getIntExtra("ride_id", 0);
            int previousRideId = prefs.getInt("ride_id", 0);
            if (rideId <= 0) {
                broadcastStatus("شناسه رکاب معتبر نیست", true);
                stopSelf();
                return START_NOT_STICKY;
            }
            prefs.edit()
                    .putBoolean("active", true)
                    .putBoolean("paused", false)
                    .putInt("ride_id", rideId)
                    .putString("csrf", intent.getStringExtra("csrf"))
                    .putFloat("max_accuracy", (float) intent.getDoubleExtra("max_accuracy", 25))
                    .putFloat("max_speed_kmh", (float) intent.getDoubleExtra("max_speed_kmh", 100))
                    .putLong("interval_ms", Math.max(1000L, intent.getLongExtra("interval_ms", 2500L)))
                    .apply();
            if (previousRideId != rideId) {
                prefs.edit()
                        .putFloat("distance_m", 0f)
                        .putLong("elapsed_ms", 0L)
                        .putFloat("last_speed_mps", 0f)
                        .apply();
            }
        }

        if (!prefs.getBoolean("active", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        boolean paused = prefs.getBoolean("paused", false);
        startForeground(NOTIFICATION_ID, buildNotification(paused ? "ثبت موقتاً متوقف است" : "ثبت مسیر در حال انجام است"));
        if (paused) {
            releaseWakeLock();
            broadcastTrackingState("ثبت موقتاً متوقف است", false);
            return START_STICKY;
        }
        acquireWakeLock();
        trackingStartedAt = System.currentTimeMillis();
        resumeCutoffAt = 0L;
        segmentBreakPending = false;
        prefs.edit().putLong("segment_started_at", trackingStartedAt).apply();
        rawWindow.clear();
        acceptedWindow.clear();
        lastAcceptedLocation = null;
        lastAcceptedAt = 0L;
        lastGpsFixAt = 0L;
        beginLocationUpdates();
        flushPending();
        broadcastTrackingState("GPS اندروید فعال — ثبت پس‌زمینه", false);
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

        // Do not seed a ride from cached/network fixes. Like Strava, distance is
        // based on real recorded GPS points; a wrong first anchor pollutes the whole ride.
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
        if (!prefs.getBoolean("active", false) || prefs.getBoolean("paused", false) || loc == null) return;

        long now = System.currentTimeMillis();
        if (resumeCutoffAt > 0L) {
            long fixTime = loc.getTime() > 0L ? loc.getTime() : now;
            if (fixTime < resumeCutoffAt) return;
        }

        long fixTime = loc.getTime() > 0L ? loc.getTime() : now;
        long age = Math.abs(now - fixTime);
        if (age > 8000L) return;

        String provider = loc.getProvider() == null ? "" : loc.getProvider();
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            lastGpsFixAt = now;
        } else if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
            // Network fixes are useful as a fallback, but they must never replace a
            // recent satellite fix or become the first distance anchor.
            if (lastAcceptedLocation == null || now - lastGpsFixAt < 10000L) return;
            if (!loc.hasAccuracy() || loc.getAccuracy() > 18f) return;
        }

        float requestedAccuracy = prefs.getFloat("max_accuracy", 25f);
        float normalMax = Math.min(30f, Math.max(18f, requestedAccuracy));
        float firstFixMax = Math.min(25f, normalMax);
        float effectiveMax = lastAcceptedLocation == null ? firstFixMax : normalMax;

        if (!loc.hasAccuracy() || loc.getAccuracy() > effectiveMax) {
            broadcastStatus("GPS ضعیف ±" + (loc.hasAccuracy() ? Math.round(loc.getAccuracy()) : "?") + "m — در انتظار نمونه دقیق", false);
            return;
        }

        // Preserve the measured coordinate. Do not median/EMA the latitude/longitude:
        // those filters cut corners and systematically under-count distance.
        Location filtered = new Location(loc);

        if (lastAcceptedLocation == null) {
            filtered.setSpeed(0f);
            acceptStravaStylePoint(filtered, now, 0.0, 0.0);
            return;
        }

        long dtMs = filtered.getTime() - lastAcceptedLocation.getTime();
        if (dtMs <= 0L) return;

        double segmentMeters = lastAcceptedLocation.distanceTo(filtered);
        double dtSec = Math.max(0.35, dtMs / 1000.0);
        double segmentKmh = segmentMeters / dtSec * 3.6;

        if (!isPlausible(filtered)) {
            broadcastStatus("پرش GPS حذف شد", false);
            return;
        }

        float prevAcc = lastAcceptedLocation.hasAccuracy() ? lastAcceptedLocation.getAccuracy() : 8f;
        float curAcc = filtered.getAccuracy();
        double uncertainty = Math.sqrt(prevAcc * prevAcc + curAcc * curAcc);
        double noiseRadius = Math.max(1.2, Math.min(4.5, uncertainty * 0.22));

        boolean reliableOsSpeed = filtered.hasSpeed();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && filtered.hasSpeedAccuracy()) {
            reliableOsSpeed = filtered.getSpeedAccuracyMetersPerSecond() <= 2.0f;
        }
        double osMps = reliableOsSpeed ? Math.max(0.0, filtered.getSpeed()) : 0.0;

        // Stationary drift must not add distance. Genuine slow cycling is retained when
        // Android Doppler speed confirms movement.
        if (segmentMeters < noiseRadius && (!reliableOsSpeed || osMps < 0.75)) {
            broadcastStatus("GPS پایدار — Drift حذف شد", false);
            return;
        }

        double robustKmh = robustSpeedKmh(filtered, segmentKmh);
        filtered.setSpeed((float)(Math.max(0.0, robustKmh) / 3.6));

        acceptStravaStylePoint(filtered, now, segmentMeters, robustKmh);
    }

    private void acceptStravaStylePoint(Location point, long now, double segmentMeters, double robustKmh) {
        float totalDistance = prefs.getFloat("distance_m", 0f) + (float)Math.max(0.0, segmentMeters);
        prefs.edit()
                .putFloat("distance_m", totalDistance)
                .putFloat("last_speed_mps", (float)Math.max(0.0, robustKmh / 3.6))
                .apply();

        lastAcceptedAt = now;
        lastAcceptedLocation = new Location(point);
        acceptedWindow.addLast(new Location(point));
        while (acceptedWindow.size() > 10) acceptedWindow.removeFirst();

        try {
            JSONObject p = new JSONObject();
            p.put("lat", point.getLatitude());
            p.put("lng", point.getLongitude());
            if (point.hasAltitude()) p.put("altitude", point.getAltitude()); else p.put("altitude", JSONObject.NULL);
            p.put("speed_kmh", Math.max(0.0, robustKmh));
            p.put("accuracy", point.getAccuracy());
            p.put("timestamp", point.getTime() > 0L ? point.getTime() : now);
            if (point.hasBearing()) p.put("bearing", point.getBearing());
            p.put("distance_m", prefs.getFloat("distance_m", 0f));
            p.put("elapsed_ms", currentElapsedMs());
            p.put("paused", false);
            p.put("segment_break", segmentBreakPending);
            p.put("gps_engine", "v6-strava-style");
            appendPending(p);
            segmentBreakPending = false;
            resumeCutoffAt = 0L;
            broadcastPoint(p, null);
            flushPending();
            broadcastStatus("GPS دقیق ±" + Math.round(point.getAccuracy()) + "m — فیلتر V6", false);
        } catch (Exception ignored) { }
    }

    private double robustSpeedKmh(Location current, double segmentKmh) {
        List<Double> values = new ArrayList<>();
        List<Location> rows = new ArrayList<>(acceptedWindow);
        for (int i = Math.max(1, rows.size() - 4); i < rows.size(); i++) {
            Location a = rows.get(i - 1);
            Location b = rows.get(i);
            long dt = b.getTime() - a.getTime();
            if (dt <= 0L || dt > 15000L) continue;
            values.add(a.distanceTo(b) / (dt / 1000.0) * 3.6);
        }
        values.add(Math.max(0.0, segmentKmh));

        double geometric = medianDouble(values);

        boolean reliableOsSpeed = current.hasSpeed();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && current.hasSpeedAccuracy()) {
            reliableOsSpeed = current.getSpeedAccuracyMetersPerSecond() <= 1.8f;
        }
        if (reliableOsSpeed) {
            double osKmh = Math.max(0.0, current.getSpeed() * 3.6);
            if (Math.abs(osKmh - geometric) <= Math.max(10.0, geometric * 0.45)) {
                geometric = geometric * 0.78 + osKmh * 0.22;
            }
        }
        return geometric;
    }

    private boolean isPlausible(Location current) {
        if (lastAcceptedLocation == null) return true;

        long dtMs = current.getTime() - lastAcceptedLocation.getTime();
        if (dtMs <= 0L) return false;
        if (dtMs > 120000L) return true;

        double dtSec = Math.max(0.35, dtMs / 1000.0);
        double meters = lastAcceptedLocation.distanceTo(current);
        double kmh = meters / dtSec * 3.6;

        float prevAcc = lastAcceptedLocation.hasAccuracy() ? lastAcceptedLocation.getAccuracy() : 8f;
        float curAcc = current.hasAccuracy() ? current.getAccuracy() : 8f;
        double uncertainty = Math.max(5.0, Math.sqrt(prevAcc * prevAcc + curAcc * curAcc));

        double configuredMax = Math.max(80.0, prefs.getFloat("max_speed_kmh", 100f));
        double recent = medianRecentSpeedKmh();
        double hardLimit = Math.max(110.0, configuredMax * 1.10);
        if (kmh > hardLimit && meters > uncertainty) return false;

        if (recent > 0.0) {
            double dynamicLimit = Math.max(62.0, recent + 34.0);
            if (kmh > dynamicLimit && dtSec <= 6.0 && meters > Math.max(12.0, uncertainty * 1.15)) return false;

            double accel = Math.abs(kmh - recent) / 3.6 / dtSec;
            if (accel > 4.5 && kmh > 45.0 && meters > Math.max(10.0, uncertainty)) return false;
        } else if (kmh > 72.0 && dtSec <= 5.0 && meters > Math.max(15.0, uncertainty * 1.2)) {
            // An isolated 70–100 km/h first jump is almost always an acquisition spike.
            return false;
        }

        return true;
    }

    private double medianRecentSpeedKmh() {
        if (acceptedWindow.size() < 2) return 0.0;
        List<Location> rows = new ArrayList<>(acceptedWindow);
        List<Double> values = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            Location a = rows.get(i - 1), b = rows.get(i);
            long dt = b.getTime() - a.getTime();
            if (dt <= 0 || dt > 30000L) continue;
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
            boolean paused = prefs.getBoolean("paused", false);
            if (paused) out.put("speed_mps", 0.0);
            else if (kmh instanceof Number) out.put("speed_mps", ((Number) kmh).doubleValue() / 3.6);
            else out.put("speed_mps", JSONObject.NULL);
            out.put("accuracy", p.has("accuracy") ? p.opt("accuracy") : JSONObject.NULL);
            out.put("bearing", p.has("bearing") ? p.opt("bearing") : JSONObject.NULL);
            out.put("timestamp", p.optLong("timestamp", System.currentTimeMillis()));
            out.put("paused", prefs.getBoolean("paused", false));
            out.put("distance_m", prefs.getFloat("distance_m", 0f));
            out.put("elapsed_ms", currentElapsedMs());
            if (response != null) out.put("response", response);
            Intent i = new Intent(ACTION_UPDATE);
            i.setPackage(getPackageName());
            i.putExtra(EXTRA_PAYLOAD, out.toString());
            sendBroadcast(i);
        } catch (Exception ignored) { }
    }

    private void broadcastStatus(String message, boolean error) {
        broadcastTrackingState(message, error);
    }

    private void broadcastTrackingState(String message, boolean error) {
        try {
            JSONObject out = new JSONObject();
            if (error) out.put("error", message); else out.put("message", message);
            out.put("paused", prefs.getBoolean("paused", false));
            out.put("speed_mps", prefs.getBoolean("paused", false) ? 0.0 : prefs.getFloat("last_speed_mps", 0f));
            out.put("distance_m", prefs.getFloat("distance_m", 0f));
            out.put("elapsed_ms", currentElapsedMs());
            Intent i = new Intent(ACTION_STATUS);
            i.setPackage(getPackageName());
            i.putExtra(EXTRA_PAYLOAD, out.toString());
            sendBroadcast(i);
        } catch (Exception ignored) { }
    }

    private long currentElapsedMs() {
        long elapsed = prefs.getLong("elapsed_ms", 0L);
        if (prefs.getBoolean("active", false) && !prefs.getBoolean("paused", false) && trackingStartedAt > 0L) {
            elapsed += Math.max(0L, System.currentTimeMillis() - trackingStartedAt);
        }
        return elapsed;
    }

    private void persistElapsedNow() {
        if (!prefs.getBoolean("paused", false) && trackingStartedAt > 0L) {
            prefs.edit().putLong("elapsed_ms", currentElapsedMs()).putLong("segment_started_at", 0L).apply();
            trackingStartedAt = 0L;
        }
    }

    private void pauseTracking() {
        if (!prefs.getBoolean("active", false) || prefs.getBoolean("paused", false)) return;
        persistElapsedNow();
        prefs.edit().putBoolean("paused", true).putFloat("last_speed_mps", 0f).apply();
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        rawWindow.clear();
        acceptedWindow.clear();
        lastAcceptedLocation = null;
        lastAcceptedAt = 0L;
        releaseWakeLock();
        startForeground(NOTIFICATION_ID, buildNotification("ثبت موقتاً متوقف است — سرعت 0"));
        broadcastTrackingState("ثبت موقتاً متوقف شد", false);
    }

    private void resumeTracking() {
        if (!prefs.getBoolean("active", false) || !prefs.getBoolean("paused", false)) return;
        prefs.edit().putBoolean("paused", false).putFloat("last_speed_mps", 0f).apply();
        rawWindow.clear();
        acceptedWindow.clear();
        lastAcceptedLocation = null;
        lastAcceptedAt = 0L;
        lastGpsFixAt = 0L;
        trackingStartedAt = System.currentTimeMillis();
        resumeCutoffAt = trackingStartedAt;
        segmentBreakPending = true;
        prefs.edit().putLong("segment_started_at", trackingStartedAt).apply();
        acquireWakeLock();
        beginLocationUpdates();
        startForeground(NOTIFICATION_ID, buildNotification("ثبت مسیر در حال انجام است"));
        broadcastTrackingState("ثبت ادامه پیدا کرد", false);
    }

    private void stopTracking() {
        persistElapsedNow();
        prefs.edit().putBoolean("active", false).putBoolean("paused", false).putFloat("last_speed_mps", 0f).putLong("segment_started_at", 0L).apply();
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void discardTracking() {
        prefs.edit()
                .putBoolean("active", false)
                .putBoolean("paused", false)
                .putFloat("last_speed_mps", 0f)
                .putLong("segment_started_at", 0L)
                .remove("pending")
                .remove("ride_id")
                .remove("csrf")
                .apply();
        rawWindow.clear();
        acceptedWindow.clear();
        lastAcceptedLocation = null;
        lastAcceptedAt = 0L;
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

        boolean paused = prefs != null && prefs.getBoolean("paused", false);
        Intent toggle = new Intent(this, TrackingService.class).setAction(paused ? ACTION_RESUME : ACTION_PAUSE);
        PendingIntent togglePi = PendingIntent.getService(
                this, 3, toggle, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("PedalPro — رکاب‌زنی فعال")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                        paused ? "ادامه" : "توقف", togglePi).build())
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
