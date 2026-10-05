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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.webkit.CookieManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
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

public class TrackingService extends Service implements LocationListener, SensorEventListener {
    public static final String PREFS = "pedalpro_native_tracking";
    public static final String ACTION_START = "ir.pedalpro.app.START_TRACKING";
    public static final String ACTION_STOP = "ir.pedalpro.app.STOP_TRACKING";
    public static final String ACTION_DISCARD = "ir.pedalpro.app.DISCARD_TRACKING";
    public static final String ACTION_UPDATE = "ir.pedalpro.app.TRACK_UPDATE";
    public static final String ACTION_STATUS = "ir.pedalpro.app.TRACK_STATUS";
    public static final String EXTRA_PAYLOAD = "payload";

    private static final String API = "https://pedalpro.ir/api.php";
    private static final String CHANNEL_ID = "pedalpro_ride_tracking";
    private static final int NOTIFICATION_ID = 4107;
    private static final float FIRST_LOCK_ACCURACY_M = 18f;
    private static final long MAX_FIX_AGE_MS = 8000L;
    private static final long MIN_DISTINCT_FIX_MS = 450L;
    private static final long IMU_STILL_ENTER_MS = 2500L;
    private static final long IMU_STARTUP_GUARD_MS = 3500L;
    private static final long IMU_FRESH_MS = 1800L;
    private static final long IMU_MOTION_RECENT_MS = 1800L;
    private static final double LINEAR_ACCEL_STILL_MPS2 = 0.18;
    private static final double LINEAR_ACCEL_MOTION_MPS2 = 0.42;
    private static final double GYRO_STILL_RADPS = 0.040;
    private static final double GYRO_MOTION_RADPS = 0.090;

    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor accelerationSensor;
    private Sensor gyroscopeSensor;
    private boolean accelerationIsLinear = false;
    private boolean imuAvailable = false;
    private boolean imuStationary = false;
    private long imuStillSince = 0L;
    private long lastImuSampleAt = 0L;
    private long lastImuMotionAt = 0L;
    private long imuGpsMotionOverrideUntil = 0L;
    private double accelEma = 0.0;
    private double gyroEma = 0.0;
    private boolean accelEmaReady = false;
    private boolean gyroEmaReady = false;
    private final float[] gravityEstimate = new float[3];
    private boolean gravityReady = false;
    private GnssStatus.Callback gnssStatusCallback;
    private int gnssSatellitesUsed = 0;
    private float gnssAverageCn0 = 0f;
    private long lastGnssStatusAt = 0L;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback fusedCallback;
    private boolean usingFused = false;
    private boolean legacyFallbackStarted = false;
    private BufferedWriter rawWriter;
    private int rawWriterRideId = 0;
    private SharedPreferences prefs;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private PowerManager.WakeLock wakeLock;
    private long lastAcceptedAt = 0L;
    private long lastRawFixAt = 0L;
    private long trackingStartedAt = 0L;
    private long lastGapFallbackAt = 0L;
    private Location lastAcceptedLocation;
    // One-fix quarantine: a point is persisted only after the following fix confirms
    // that it was not an isolated GNSS teleport. This intentionally adds ~1s latency.
    private Location pendingCandidate;
    private long pendingCandidateReceivedAt = 0L;
    private int stationaryFixes = 0;
    private int movementConfirmFixes = 0;
    private int rejectedFixes = 0;
    private long lastFilterStatusAt = 0L;
    private final ArrayDeque<Location> rawWindow = new ArrayDeque<>();
    private final ArrayDeque<Location> acceptedWindow = new ArrayDeque<>();
    private final Handler gpsWatchdogHandler = new Handler(Looper.getMainLooper());
    private final Runnable gpsWatchdog = new Runnable() {
        @Override public void run() {
            try {
                if (!prefs.getBoolean("active", false)) return;
                long now = System.currentTimeMillis();
                long reference = lastRawFixAt > 0L ? lastRawFixAt : trackingStartedAt;
                long gap = reference > 0L ? now - reference : 0L;

                if (usingFused && gap >= 6000L && !legacyFallbackStarted &&
                        now - lastGapFallbackAt >= 10000L) {
                    lastGapFallbackAt = now;
                    broadcastStatus("گپ GPS تشخیص داده شد؛ فعال‌سازی GPS مستقیم اندروید", false);
                    startLegacyLocationFallback();
                }
            } catch (Throwable ignored) {
            } finally {
                if (prefs != null && prefs.getBoolean("active", false)) {
                    gpsWatchdogHandler.postDelayed(this, 3000L);
                }
            }
        }
    };

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

        if (intent != null && ACTION_START.equals(intent.getAction())) {
            int rideId = intent.getIntExtra("ride_id", 0);
            int previousRideId = prefs.getInt("ride_id", 0);
            if (rideId <= 0) {
                broadcastStatus("شناسه رکاب معتبر نیست", true);
                stopSelf();
                return START_NOT_STICKY;
            }
            // GPS Engine V2 owns the safe operating envelope. Legacy server/admin
            // values that are too strict (e.g. 10m / 2000ms / 50kmh) must not
            // degrade tracking quality or delete legitimate downhill fixes.
            float requestedAccuracy = (float) intent.getDoubleExtra("max_accuracy", 25);
            float requestedMaxSpeed = (float) intent.getDoubleExtra("max_speed_kmh", 80);
            prefs.edit()
                    .putBoolean("active", true)
                    .putInt("ride_id", rideId)
                    .putString("csrf", intent.getStringExtra("csrf"))
                    .putFloat("max_accuracy", Math.max(25f, requestedAccuracy))
                    .putFloat("max_speed_kmh", Math.max(80f, requestedMaxSpeed))
                    .putLong("interval_ms", 1000L)
                    .apply();
            if (previousRideId != rideId) resetRawLog(rideId);
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
        pendingCandidate = null;
        pendingCandidateReceivedAt = 0L;
        lastAcceptedAt = 0L;
        lastRawFixAt = 0L;
        lastGapFallbackAt = 0L;
        stationaryFixes = 0;
        movementConfirmFixes = 0;
        rejectedFixes = 0;
        lastFilterStatusAt = 0L;
        resetSensorFusionState();
        beginLocationUpdates();
        gpsWatchdogHandler.removeCallbacks(gpsWatchdog);
        gpsWatchdogHandler.postDelayed(gpsWatchdog, 3000L);
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

        legacyFallbackStarted = false;
        usingFused = false;
        ensureRawWriter();
        startSensorFusion();
        startGnssQualityMonitor();

        if (!startFusedLocationUpdates()) {
            startLegacyLocationFallback();
        }
    }

    private void resetSensorFusionState() {
        imuAvailable = false;
        imuStationary = false;
        imuStillSince = 0L;
        lastImuSampleAt = 0L;
        lastImuMotionAt = 0L;
        imuGpsMotionOverrideUntil = 0L;
        accelEma = 0.0;
        gyroEma = 0.0;
        accelEmaReady = false;
        gyroEmaReady = false;
        gravityReady = false;
        gravityEstimate[0] = gravityEstimate[1] = gravityEstimate[2] = 0f;
        gnssSatellitesUsed = 0;
        gnssAverageCn0 = 0f;
        lastGnssStatusAt = 0L;
    }

    private void startSensorFusion() {
        try {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager == null) return;
            accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            accelerationIsLinear = accelerationSensor != null;
            if (accelerationSensor == null) {
                accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                accelerationIsLinear = false;
            }
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            boolean accelOk = accelerationSensor != null &&
                    sensorManager.registerListener(this, accelerationSensor, SensorManager.SENSOR_DELAY_GAME);
            boolean gyroOk = gyroscopeSensor != null &&
                    sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
            imuAvailable = accelOk || gyroOk;
        } catch (Throwable ignored) {
            imuAvailable = false;
        }
    }

    private void stopSensorFusion() {
        if (sensorManager != null) {
            try { sensorManager.unregisterListener(this); } catch (Throwable ignored) { }
        }
        accelerationSensor = null;
        gyroscopeSensor = null;
        sensorManager = null;
        imuAvailable = false;
        imuStationary = false;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null || event.values == null || event.values.length < 3) return;
        long now = System.currentTimeMillis();
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_LINEAR_ACCELERATION || type == Sensor.TYPE_ACCELEROMETER) {
            double x = event.values[0], y = event.values[1], z = event.values[2];
            if (type == Sensor.TYPE_ACCELEROMETER && !accelerationIsLinear) {
                if (!gravityReady) {
                    gravityEstimate[0] = event.values[0];
                    gravityEstimate[1] = event.values[1];
                    gravityEstimate[2] = event.values[2];
                    gravityReady = true;
                } else {
                    final float alpha = 0.92f;
                    gravityEstimate[0] = alpha * gravityEstimate[0] + (1f - alpha) * event.values[0];
                    gravityEstimate[1] = alpha * gravityEstimate[1] + (1f - alpha) * event.values[1];
                    gravityEstimate[2] = alpha * gravityEstimate[2] + (1f - alpha) * event.values[2];
                }
                x -= gravityEstimate[0];
                y -= gravityEstimate[1];
                z -= gravityEstimate[2];
            }
            double mag = Math.sqrt(x * x + y * y + z * z);
            accelEma = accelEmaReady ? accelEma * 0.84 + mag * 0.16 : mag;
            accelEmaReady = true;
        } else if (type == Sensor.TYPE_GYROSCOPE) {
            double x = event.values[0], y = event.values[1], z = event.values[2];
            double mag = Math.sqrt(x * x + y * y + z * z);
            gyroEma = gyroEmaReady ? gyroEma * 0.84 + mag * 0.16 : mag;
            gyroEmaReady = true;
        } else {
            return;
        }
        lastImuSampleAt = now;
        updateImuMotionState(now);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void updateImuMotionState(long now) {
        boolean accelStill = !accelEmaReady || accelEma <= LINEAR_ACCEL_STILL_MPS2;
        boolean gyroStill = !gyroEmaReady || gyroEma <= GYRO_STILL_RADPS;
        boolean still = accelStill && gyroStill && (accelEmaReady || gyroEmaReady);
        boolean motion = (accelEmaReady && accelEma >= LINEAR_ACCEL_MOTION_MPS2) ||
                (gyroEmaReady && gyroEma >= GYRO_MOTION_RADPS);

        if (motion) {
            lastImuMotionAt = now;
            imuStillSince = 0L;
            imuStationary = false;
            return;
        }
        if (still) {
            if (imuStillSince == 0L) imuStillSince = now;
            if (now - imuStillSince >= IMU_STILL_ENTER_MS && now >= imuGpsMotionOverrideUntil) {
                imuStationary = true;
            }
        } else {
            imuStillSince = 0L;
        }
    }

    private boolean isImuFresh(long now) {
        return imuAvailable && lastImuSampleAt > 0L && now - lastImuSampleAt <= IMU_FRESH_MS;
    }

    private boolean hasRecentImuMotion(long now) {
        return isImuFresh(now) && lastImuMotionAt > 0L && now - lastImuMotionAt <= IMU_MOTION_RECENT_MS;
    }

    private boolean isImuStationaryStrong(long now) {
        return isImuFresh(now) && imuStationary && now >= imuGpsMotionOverrideUntil;
    }

    private boolean isImuStartupGuard(long now) {
        return isImuFresh(now) && now - trackingStartedAt < IMU_STARTUP_GUARD_MS && !hasRecentImuMotion(now);
    }

    private void startGnssQualityMonitor() {
        try {
            if (locationManager == null) locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager == null || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            gnssStatusCallback = new GnssStatus.Callback() {
                @Override public void onSatelliteStatusChanged(GnssStatus status) {
                    if (status == null) return;
                    int used = 0;
                    float total = 0f;
                    for (int i = 0; i < status.getSatelliteCount(); i++) {
                        if (status.usedInFix(i)) {
                            used++;
                            total += status.getCn0DbHz(i);
                        }
                    }
                    gnssSatellitesUsed = used;
                    gnssAverageCn0 = used > 0 ? total / used : 0f;
                    lastGnssStatusAt = System.currentTimeMillis();
                }
            };
            locationManager.registerGnssStatusCallback(gnssStatusCallback, new Handler(Looper.getMainLooper()));
        } catch (Throwable ignored) {
            gnssStatusCallback = null;
        }
    }

    private void stopGnssQualityMonitor() {
        if (locationManager != null && gnssStatusCallback != null) {
            try { locationManager.unregisterGnssStatusCallback(gnssStatusCallback); } catch (Throwable ignored) { }
        }
        gnssStatusCallback = null;
    }

    private int gnssQualityScore(long now) {
        if (lastGnssStatusAt <= 0L || now - lastGnssStatusAt > 5000L || gnssSatellitesUsed <= 0) return -1;
        int satScore = Math.min(50, gnssSatellitesUsed * 7);
        int cn0Score;
        if (gnssAverageCn0 >= 35f) cn0Score = 50;
        else if (gnssAverageCn0 >= 30f) cn0Score = 42;
        else if (gnssAverageCn0 >= 25f) cn0Score = 32;
        else if (gnssAverageCn0 >= 20f) cn0Score = 20;
        else cn0Score = 8;
        return Math.min(100, satScore + cn0Score);
    }

    private boolean startFusedLocationUpdates() {
        try {
            int availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
            if (availability != ConnectionResult.SUCCESS) {
                broadcastStatus("Fused GPS در این گوشی در دسترس نیست؛ استفاده از GPS مستقیم اندروید", false);
                return false;
            }

            fusedClient = LocationServices.getFusedLocationProviderClient(this);
            LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                    .setGranularity(Granularity.GRANULARITY_FINE)
                    .setMinUpdateIntervalMillis(900L)
                    .setMaxUpdateDelayMillis(0L)
                    .setMaxUpdateAgeMillis(0L)
                    .setMinUpdateDistanceMeters(0f)
                    .setWaitForAccurateLocation(true)
                    .build();

            fusedCallback = new LocationCallback() {
                @Override public void onLocationResult(LocationResult result) {
                    if (result == null) return;
                    for (Location loc : result.getLocations()) {
                        if (loc != null) handleLocation(loc, "fused");
                    }
                }
            };

            fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                    .addOnSuccessListener(v -> {
                        usingFused = true;
                        broadcastStatus("Fused GPS با دقت بالا فعال — نمونه‌گیری حدود ۱ ثانیه", false);
                    })
                    .addOnFailureListener(e -> {
                        usingFused = false;
                        broadcastStatus("Fused GPS فعال نشد؛ انتقال به GPS مستقیم اندروید", false);
                        startLegacyLocationFallback();
                    });
            return true;
        } catch (Throwable e) {
            usingFused = false;
            return false;
        }
    }

    private synchronized void startLegacyLocationFallback() {
        if (legacyFallbackStarted || !prefs.getBoolean("active", false)) return;
        legacyFallbackStarted = true;
        if (locationManager == null) locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        long interval = Math.max(1000L, prefs.getLong("interval_ms", 1000L));

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
            }
        } catch (Exception ignored) { }
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, Math.max(2000L, interval), 0f, this, Looper.getMainLooper());
            }
        } catch (Exception ignored) { }
        broadcastStatus("GPS مستقیم اندروید فعال — حالت سازگار", false);
    }

    @Override public void onLocationChanged(Location loc) {
        handleLocation(loc, "legacy:" + (loc == null ? "unknown" : String.valueOf(loc.getProvider())));
    }

    private void handleLocation(Location loc, String source) {
        if (!prefs.getBoolean("active", false) || loc == null) return;

        // Preserve the untouched sensor fix for diagnostics before filtering.
        appendRawPoint(loc, source);

        long now = System.currentTimeMillis();
        lastRawFixAt = now;
        long fixTime = loc.getTime() > 0 ? loc.getTime() : now;
        long age = Math.abs(now - fixTime);
        if (age > MAX_FIX_AGE_MS) {
            noteRejected("نمونه قدیمی GPS حذف شد");
            return;
        }

        // Never seed a ride from a cached/coarse fix. The first anchor controls every
        // distance that follows, so it must be materially tighter than normal tracking.
        if (lastAcceptedLocation == null) {
            int quality = gnssQualityScore(now);
            if (!loc.hasAccuracy() || loc.getAccuracy() > FIRST_LOCK_ACCURACY_M ||
                    (quality >= 0 && quality < 28 && loc.getAccuracy() > 12f)) {
                noteRejected("در انتظار قفل دقیق GPS");
                return;
            }
            Location first = new Location(loc);
            first.setSpeed(0f);
            acceptCandidate(first, now, false);
            return;
        }

        float accuracyLimit = adaptiveAccuracyLimit(loc);
        if (!loc.hasAccuracy() || loc.getAccuracy() > accuracyLimit) {
            noteRejected("GPS ضعیف ±" + (loc.hasAccuracy() ? Math.round(loc.getAccuracy()) : "?") + "m");
            return;
        }

        Location candidate = new Location(loc);
        if (isDuplicateFix(candidate)) return;

        // Keep a short quality window for telemetry and dynamic thresholds.
        rawWindow.addLast(new Location(candidate));
        while (rawWindow.size() > 6) rawWindow.removeFirst();

        // One-fix look-ahead quarantine. A bad point is not allowed to reach the server
        // until the next GNSS fix proves that the route genuinely continued through it.
        if (pendingCandidate == null) {
            pendingCandidate = candidate;
            pendingCandidateReceivedAt = now;
            return;
        }

        Location evaluating = pendingCandidate;
        long evaluatingReceivedAt = pendingCandidateReceivedAt;
        pendingCandidate = candidate;
        pendingCandidateReceivedAt = now;

        if (isPendingSpike(lastAcceptedLocation, evaluating, candidate)) {
            noteRejected("پرش GPS با تأیید نقطه بعدی حذف شد");
            return;
        }

        if (!isPlausibleV2(evaluating)) {
            noteRejected("پرش غیرمنطقی GPS حذف شد");
            return;
        }

        processCandidate(evaluating, candidate,
                evaluatingReceivedAt > 0L ? evaluatingReceivedAt : now);
    }

    private void processCandidate(Location candidate, Location lookAhead, long receivedAt) {
        if (lastAcceptedLocation == null) return;

        double meters = lastAcceptedLocation.distanceTo(candidate);
        long dtMs = candidate.getTime() - lastAcceptedLocation.getTime();
        if (dtMs <= 0L) return;

        float prevAcc = lastAcceptedLocation.hasAccuracy() ? lastAcceptedLocation.getAccuracy() : 8f;
        float curAcc = candidate.hasAccuracy() ? candidate.getAccuracy() : 8f;
        double noiseRadius = stationaryNoiseRadius(prevAcc, curAcc);

        double derivedMps = meters / Math.max(0.35, dtMs / 1000.0);
        boolean reliableSpeed = hasReliableSpeed(candidate);
        double osMps = reliableSpeed ? Math.max(0.0, candidate.getSpeed()) : 0.0;

        double forwardMeters = lookAhead == null ? 0.0 : candidate.distanceTo(lookAhead);
        double netMeters = lookAhead == null ? meters : lastAcceptedLocation.distanceTo(lookAhead);
        long totalDtMs = lookAhead == null ? dtMs : lookAhead.getTime() - lastAcceptedLocation.getTime();
        if (totalDtMs <= 0L) totalDtMs = dtMs;

        boolean forwardSupport = lookAhead == null ||
                (forwardMeters >= 1.2 && netMeters >= Math.max(3.0, noiseRadius * 0.85));
        boolean dopplerMovement = reliableSpeed && osMps >= 0.65 &&
                (meters >= 1.4 || netMeters >= 2.5);
        boolean geometricMovement = meters >= Math.max(4.0, noiseRadius * 1.10) &&
                derivedMps >= 0.50 && forwardSupport &&
                netMeters >= Math.max(5.0, noiseRadius * 1.30);
        boolean accumulatedSlowMovement = totalDtMs >= 2500L &&
                netMeters >= Math.max(6.0, noiseRadius * 1.55);
        boolean moving = dopplerMovement || geometricMovement || accumulatedSlowMovement;

        long now = Math.max(receivedAt, System.currentTimeMillis());
        boolean imuStill = isImuStationaryStrong(now) || isImuStartupGuard(now);
        boolean imuMotion = hasRecentImuMotion(now);

        // Zero-Velocity Update (ZUPT): when the phone's inertial sensors say it is
        // physically still, GNSS wander is never allowed to add distance. Unlocking
        // requires either recent inertial motion or three consecutive coherent fixes
        // with reliable Doppler speed. This catches slow multi-fix drift, not only jumps.
        if (imuStill && !imuMotion) {
            boolean strongGpsMotion = reliableSpeed && osMps >= 1.35 && forwardSupport &&
                    netMeters >= Math.max(7.0, noiseRadius * 1.45) &&
                    derivedMps >= 0.75;
            if (strongGpsMotion) movementConfirmFixes++;
            else movementConfirmFixes = 0;

            if (movementConfirmFixes < 3) {
                freezeAtStationaryAnchor(candidate, receivedAt, "Sensor Fusion — سکون قفل شد");
                return;
            }

            // Coherent GNSS/Doppler evidence overrides a falsely quiet IMU for 5s.
            imuGpsMotionOverrideUntil = now + 5000L;
            imuStationary = false;
            imuStillSince = 0L;
            movementConfirmFixes = 0;
            moving = true;
        }

        // If GNSS quality is poor and the IMU does not independently show motion,
        // require stronger geometry before accepting movement.
        int quality = gnssQualityScore(now);
        if (moving && quality >= 0 && quality < 25 && !imuMotion) {
            boolean poorGnssOverride = reliableSpeed && osMps >= 1.5 && forwardSupport &&
                    netMeters >= Math.max(9.0, noiseRadius * 1.8);
            if (!poorGnssOverride) moving = false;
        }

        if (moving && stationaryFixes >= 2 && !imuMotion) {
            double unlockDistance = Math.max(4.0, noiseRadius * 1.20);
            boolean strongEvidence =
                    (reliableSpeed && osMps >= 0.95 && netMeters >= unlockDistance) ||
                    netMeters >= Math.max(7.0, noiseRadius * 1.65) ||
                    (derivedMps >= 1.15 && meters >= unlockDistance && forwardSupport);
            if (strongEvidence) movementConfirmFixes++;
            else movementConfirmFixes = 0;
            if (movementConfirmFixes < 2) moving = false;
        } else if (!moving) {
            movementConfirmFixes = 0;
        }

        if (!moving) {
            freezeAtStationaryAnchor(candidate, receivedAt, "Sensor Fusion — Drift حذف شد");
            return;
        }

        stationaryFixes = 0;
        movementConfirmFixes = 0;
        double maxMps = prefs.getFloat("max_speed_kmh", 100f) / 3.6;
        double chosenMps = derivedMps;
        if (reliableSpeed && Math.abs(osMps - derivedMps) <= Math.max(1.8, derivedMps * 0.55)) {
            chosenMps = derivedMps * 0.64 + osMps * 0.36;
        }
        candidate.setSpeed((float)Math.min(Math.max(0.0, chosenMps), maxMps));
        acceptCandidate(candidate, receivedAt, true);
    }

    private void freezeAtStationaryAnchor(Location candidate, long receivedAt, String status) {
        stationaryFixes++;
        Location frozen = new Location(lastAcceptedLocation);
        frozen.setTime(candidate.getTime());
        frozen.setSpeed(0f);
        try { broadcastPoint(locationJson(frozen), null); } catch (Exception ignored) { }
        if (receivedAt - lastFilterStatusAt >= 3500L) {
            lastFilterStatusAt = receivedAt;
            broadcastStatus(status, false);
        }
    }

    private boolean hasReliableSpeed(Location loc) {
        if (loc == null || !loc.hasSpeed()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && loc.hasSpeedAccuracy()) {
            return loc.getSpeedAccuracyMetersPerSecond() <= 1.8f;
        }
        return true;
    }

    private boolean isDuplicateFix(Location candidate) {
        Location ref = pendingCandidate != null ? pendingCandidate : lastAcceptedLocation;
        if (ref == null) return false;
        long dt = Math.abs(candidate.getTime() - ref.getTime());
        return dt < MIN_DISTINCT_FIX_MS && ref.distanceTo(candidate) < 2.0f;
    }

    private boolean isPendingSpike(Location previous, Location middle, Location next) {
        if (previous == null || middle == null || next == null) return false;
        long dt1 = middle.getTime() - previous.getTime();
        long dt2 = next.getTime() - middle.getTime();
        if (dt1 <= 0L || dt2 <= 0L || dt1 > 15000L || dt2 > 15000L) return false;

        double d1 = previous.distanceTo(middle);
        double d2 = middle.distanceTo(next);
        double direct = previous.distanceTo(next);
        float pAcc = previous.hasAccuracy() ? previous.getAccuracy() : 8f;
        float mAcc = middle.hasAccuracy() ? middle.getAccuracy() : 8f;
        float nAcc = next.hasAccuracy() ? next.getAccuracy() : 8f;
        double uncertainty = Math.max(8.0, Math.max(pAcc, Math.max(mAcc, nAcc)) * 1.25);

        double detour = Math.max(0.0, d1 + d2 - direct);
        double ratio = (d1 + d2) / Math.max(3.0, direct);
        double middleKmh = Math.max(d1 / (dt1 / 1000.0), d2 / (dt2 / 1000.0)) * 3.6;
        double directKmh = direct / ((dt1 + dt2) / 1000.0) * 3.6;
        double recent = medianRecentSpeedKmh();
        double dynamicLimit = Math.max(60.0, recent * 3.0 + 25.0);

        boolean geometricSpike = d1 >= Math.max(18.0, uncertainty * 1.25) &&
                d2 >= Math.max(18.0, uncertainty * 1.25) &&
                detour >= Math.max(18.0, uncertainty * 1.35) && ratio >= 2.15;
        boolean speedSpike = middleKmh > dynamicLimit &&
                directKmh <= Math.max(55.0, recent * 2.5 + 22.0) &&
                detour >= Math.max(15.0, uncertainty);
        return geometricSpike || speedSpike;
    }

    private void acceptCandidate(Location candidate, long now, boolean moving) {
        if (lastAcceptedLocation != null) {
            long fixDt = candidate.getTime() - lastAcceptedLocation.getTime();
            if (fixDt <= 0L) return;
            // Prevent duplicate Fused/GPS fallback fixes from double-counting distance.
            if (fixDt < 700L && lastAcceptedLocation.distanceTo(candidate) < 3.0f) return;
        }

        lastAcceptedAt = now;
        lastAcceptedLocation = new Location(candidate);
        acceptedWindow.addLast(new Location(candidate));
        while (acceptedWindow.size() > 12) acceptedWindow.removeFirst();

        try {
            JSONObject p = locationJson(candidate);
            appendPending(p);
            broadcastPoint(p, null);
            flushPending();
            if (now - lastFilterStatusAt >= 3500L) {
                lastFilterStatusAt = now;
                String quality = candidate.hasAccuracy()
                        ? " ±" + Math.round(candidate.getAccuracy()) + "m" : "";
                broadcastStatus((moving ? "GPS V4 Sensor Fusion" : "GPS V4 قفل دقیق") + quality, false);
            }
        } catch (Exception ignored) { }
    }

    private float adaptiveAccuracyLimit(Location loc) {
        float configured = prefs.getFloat("max_accuracy", 25f);
        configured = Math.max(20f, Math.min(30f, configured));

        if (lastAcceptedLocation == null) return FIRST_LOCK_ACCURACY_M;

        double recentKmh = medianRecentSpeedKmh();
        double osMps = hasReliableSpeed(loc) ? Math.max(0.0, loc.getSpeed()) : 0.0;
        int quality = gnssQualityScore(System.currentTimeMillis());
        float penalty = quality >= 0 && quality < 25 ? 3f : 0f;
        if (recentKmh >= 12.0 || osMps >= 3.3) return Math.max(24f, Math.max(28f, configured) - penalty);
        if (recentKmh >= 3.0 || osMps >= 0.85) return Math.max(22f, Math.max(25f, configured) - penalty);
        return Math.max(18f, Math.min(22f, configured) - penalty);
    }

    private double stationaryNoiseRadius(float prevAcc, float curAcc) {
        double a = Math.max(1.0, Math.max(prevAcc, curAcc));
        return Math.max(3.5, Math.min(9.0, a * 0.55));
    }

    private boolean isIsolatedSpike(ArrayDeque<Location> source, Location newest) {
        if (source.size() < 3 || lastAcceptedLocation == null) return false;
        List<Location> rows = new ArrayList<>(source);
        Location a = rows.get(rows.size() - 3);
        Location b = rows.get(rows.size() - 2);

        double priorSpread = a.distanceTo(b);
        double jump = b.distanceTo(newest);
        long dt = newest.getTime() - b.getTime();
        if (dt <= 0L || dt > 15000L) return false;

        float aAcc = a.hasAccuracy() ? a.getAccuracy() : 8f;
        float bAcc = b.hasAccuracy() ? b.getAccuracy() : 8f;
        float nAcc = newest.hasAccuracy() ? newest.getAccuracy() : 8f;
        double uncertainty = Math.max(10.0, (aAcc + bAcc + nAcc) * 0.55);
        double kmh = jump / (dt / 1000.0) * 3.6;
        double recent = medianRecentSpeedKmh();
        double dynamic = Math.max(65.0, recent * 3.2 + 28.0);

        return priorSpread <= Math.max(8.0, uncertainty * 0.45) &&
                jump >= Math.max(24.0, uncertainty * 1.15) &&
                kmh > dynamic;
    }

    private boolean isPlausibleV2(Location current) {
        if (lastAcceptedLocation == null) return true;

        long dtMs = current.getTime() - lastAcceptedLocation.getTime();
        if (dtMs <= 0L) return false;
        if (dtMs > 120000L) return true;

        double meters = lastAcceptedLocation.distanceTo(current);
        double kmh = meters / (dtMs / 1000.0) * 3.6;

        float prevAcc = lastAcceptedLocation.hasAccuracy() ? lastAcceptedLocation.getAccuracy() : 8f;
        float curAcc = current.hasAccuracy() ? current.getAccuracy() : 8f;
        double uncertainty = Math.max(6.0, (prevAcc + curAcc) * 0.75);

        double configuredMax = Math.max(45.0, prefs.getFloat("max_speed_kmh", 100f));
        double recent = medianRecentSpeedKmh();
        double hardLimit = configuredMax * 1.15;
        double dynamicLimit = Math.max(65.0, recent * 3.2 + 28.0);

        if (kmh > hardLimit && meters > uncertainty) return false;
        if (kmh > dynamicLimit && meters > Math.max(25.0, uncertainty * 1.4)) return false;

        // A huge one-second acceleration with poor geometric support is usually drift.
        if (recent >= 5.0 && kmh > recent + 55.0 &&
                meters > Math.max(30.0, uncertainty * 1.5)) return false;

        return true;
    }

    private void noteRejected(String message) {
        rejectedFixes++;
        long now = System.currentTimeMillis();
        if (now - lastFilterStatusAt >= 3500L) {
            lastFilterStatusAt = now;
            broadcastStatus(message + " • فیلتر V4", false);
        }
    }

    private synchronized void resetRawLog(int rideId) {
        closeRawWriter();
        rawWriterRideId = 0;
        if (rideId <= 0) return;
        try {
            File dir = new File(getFilesDir(), "raw_gps");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "ride_" + rideId + ".jsonl");
            if (file.exists()) file.delete();
        } catch (Throwable ignored) { }
    }

    private synchronized void ensureRawWriter() {
        int rideId = prefs.getInt("ride_id", 0);
        if (rideId <= 0) return;
        if (rawWriter != null && rawWriterRideId == rideId) return;
        closeRawWriter();
        try {
            File dir = new File(getFilesDir(), "raw_gps");
            if (!dir.exists() && !dir.mkdirs()) return;
            File file = new File(dir, "ride_" + rideId + ".jsonl");
            rawWriter = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file, true), StandardCharsets.UTF_8));
            rawWriterRideId = rideId;
        } catch (Throwable ignored) {
            rawWriter = null;
            rawWriterRideId = 0;
        }
    }

    private synchronized void appendRawPoint(Location loc, String source) {
        try {
            ensureRawWriter();
            if (rawWriter == null) return;
            JSONObject p = new JSONObject();
            p.put("lat", loc.getLatitude());
            p.put("lng", loc.getLongitude());
            p.put("timestamp", loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis());
            p.put("source", source == null ? "unknown" : source);
            p.put("provider", loc.getProvider() == null ? JSONObject.NULL : loc.getProvider());
            p.put("accuracy", loc.hasAccuracy() ? loc.getAccuracy() : JSONObject.NULL);
            p.put("speed_mps", loc.hasSpeed() ? loc.getSpeed() : JSONObject.NULL);
            p.put("bearing", loc.hasBearing() ? loc.getBearing() : JSONObject.NULL);
            p.put("altitude", loc.hasAltitude() ? loc.getAltitude() : JSONObject.NULL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                p.put("vertical_accuracy", loc.hasVerticalAccuracy() ? loc.getVerticalAccuracyMeters() : JSONObject.NULL);
                p.put("speed_accuracy", loc.hasSpeedAccuracy() ? loc.getSpeedAccuracyMetersPerSecond() : JSONObject.NULL);
                p.put("bearing_accuracy", loc.hasBearingAccuracy() ? loc.getBearingAccuracyDegrees() : JSONObject.NULL);
            }
            p.put("imu_available", imuAvailable);
            p.put("imu_stationary", isImuStationaryStrong(System.currentTimeMillis()));
            p.put("imu_accel_ema", accelEmaReady ? accelEma : JSONObject.NULL);
            p.put("imu_gyro_ema", gyroEmaReady ? gyroEma : JSONObject.NULL);
            p.put("gnss_used", gnssSatellitesUsed);
            p.put("gnss_cn0_avg", gnssSatellitesUsed > 0 ? gnssAverageCn0 : JSONObject.NULL);
            rawWriter.write(p.toString());
            rawWriter.newLine();
            rawWriter.flush();
        } catch (Throwable ignored) { }
    }

    private synchronized void closeRawWriter() {
        if (rawWriter != null) {
            try { rawWriter.flush(); } catch (Throwable ignored) { }
            try { rawWriter.close(); } catch (Throwable ignored) { }
        }
        rawWriter = null;
        rawWriterRideId = 0;
    }

    private synchronized void deleteRawLogForCurrentRide() {
        int rideId = prefs.getInt("ride_id", 0);
        closeRawWriter();
        if (rideId <= 0) return;
        try {
            File file = new File(new File(getFilesDir(), "raw_gps"), "ride_" + rideId + ".jsonl");
            if (file.exists()) file.delete();
        } catch (Throwable ignored) { }
    }

    private JSONObject locationJson(Location loc) throws Exception {
        JSONObject p = new JSONObject();
        p.put("lat", loc.getLatitude());
        p.put("lng", loc.getLongitude());
        if (loc.hasAltitude()) p.put("altitude", loc.getAltitude()); else p.put("altitude", JSONObject.NULL);
        if (loc.hasSpeed()) p.put("speed_kmh", loc.getSpeed() * 3.6); else p.put("speed_kmh", JSONObject.NULL);
        if (loc.hasAccuracy()) p.put("accuracy", loc.getAccuracy()); else p.put("accuracy", JSONObject.NULL);
        p.put("timestamp", loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis());
        if (loc.hasBearing()) p.put("bearing", loc.getBearing());
        p.put("gps_engine", "v4-sensor-fusion");
        p.put("filter_rejected", rejectedFixes);
        return p;
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
        gpsWatchdogHandler.removeCallbacks(gpsWatchdog);
        stopAllLocationUpdates();
        stopSensorFusion();
        stopGnssQualityMonitor();
        closeRawWriter();
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void discardTracking() {
        deleteRawLogForCurrentRide();
        prefs.edit()
                .putBoolean("active", false)
                .remove("pending")
                .remove("ride_id")
                .remove("csrf")
                .apply();
        rawWindow.clear();
        acceptedWindow.clear();
        lastAcceptedLocation = null;
        pendingCandidate = null;
        pendingCandidateReceivedAt = 0L;
        lastAcceptedAt = 0L;
        lastRawFixAt = 0L;
        lastGapFallbackAt = 0L;
        stationaryFixes = 0;
        movementConfirmFixes = 0;
        rejectedFixes = 0;
        lastFilterStatusAt = 0L;
        resetSensorFusionState();
        gpsWatchdogHandler.removeCallbacks(gpsWatchdog);
        stopAllLocationUpdates();
        stopSensorFusion();
        stopGnssQualityMonitor();
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void stopAllLocationUpdates() {
        if (fusedClient != null && fusedCallback != null) {
            try { fusedClient.removeLocationUpdates(fusedCallback); } catch (Throwable ignored) { }
        }
        fusedCallback = null;
        fusedClient = null;
        usingFused = false;
        legacyFallbackStarted = false;
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
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
        gpsWatchdogHandler.removeCallbacks(gpsWatchdog);
        stopAllLocationUpdates();
        stopSensorFusion();
        stopGnssQualityMonitor();
        closeRawWriter();
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
