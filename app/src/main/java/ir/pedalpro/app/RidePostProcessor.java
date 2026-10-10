package ir.pedalpro.app;

import android.content.Context;
import android.location.Location;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RidePostProcessor {
    public interface Callback {
        void onDone(Result result);
    }

    public static final class Result {
        public final int rideId;
        public final int rawPoints;
        public final int acceptedPoints;
        public final int rejectedPoints;
        public final double distanceM;
        public final double maxSpeedKmh;
        public final double averageMovingKmh;
        public final long movingTimeMs;

        Result(int rideId, int rawPoints, int acceptedPoints, int rejectedPoints,
               double distanceM, double maxSpeedKmh, double averageMovingKmh, long movingTimeMs) {
            this.rideId = rideId;
            this.rawPoints = rawPoints;
            this.acceptedPoints = acceptedPoints;
            this.rejectedPoints = rejectedPoints;
            this.distanceM = distanceM;
            this.maxSpeedKmh = maxSpeedKmh;
            this.averageMovingKmh = averageMovingKmh;
            this.movingTimeMs = movingTimeMs;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("ride_id", rideId);
                o.put("raw_points", rawPoints);
                o.put("accepted_points", acceptedPoints);
                o.put("rejected_points", rejectedPoints);
                o.put("final_distance_m", Math.round(distanceM * 10.0) / 10.0);
                o.put("final_max_speed_kmh", Math.round(maxSpeedKmh * 10.0) / 10.0);
                o.put("final_average_moving_kmh", Math.round(averageMovingKmh * 10.0) / 10.0);
                o.put("final_moving_time_ms", movingTimeMs);
                o.put("engine", "v7-final-postprocess");
            } catch (Throwable ignored) { }
            return o;
        }
    }

    private static final class Fix {
        double lat;
        double lng;
        long time;
        float accuracy;
        float speedMps;
        float speedAccuracy;
        boolean hasSpeed;
        boolean hasSpeedAccuracy;
        String provider;
        int segment;

        Location location() {
            Location l = new Location(provider == null ? "gps" : provider);
            l.setLatitude(lat);
            l.setLongitude(lng);
            l.setTime(time);
            l.setAccuracy(accuracy);
            if (hasSpeed) l.setSpeed(speedMps);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasSpeedAccuracy) {
                l.setSpeedAccuracyMetersPerSecond(speedAccuracy);
            }
            return l;
        }
    }

    private static final Object WRITE_LOCK = new Object();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private RidePostProcessor() {}

    private static File dir(Context context) {
        File d = new File(context.getFilesDir(), "ride_gps");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File rawFile(Context context, int rideId) {
        return new File(dir(context), "ride_" + rideId + "_raw.jsonl");
    }

    private static File filteredFile(Context context, int rideId) {
        return new File(dir(context), "ride_" + rideId + "_filtered.jsonl");
    }

    private static File summaryFile(Context context, int rideId) {
        return new File(dir(context), "ride_" + rideId + "_summary.json");
    }

    public static void resetRide(Context context, int rideId) {
        if (context == null || rideId <= 0) return;
        synchronized (WRITE_LOCK) {
            rawFile(context, rideId).delete();
            filteredFile(context, rideId).delete();
            summaryFile(context, rideId).delete();
        }
        DiagnosticLogger.log(context, "gps_raw", "reset ride=" + rideId);
    }

    public static void deleteRide(Context context, int rideId) {
        resetRide(context, rideId);
    }

    public static void appendRawFix(Context context, int rideId, int segment, Location loc, String source) {
        if (context == null || rideId <= 0 || loc == null) return;
        synchronized (WRITE_LOCK) {
            try (BufferedWriter out = new BufferedWriter(new FileWriter(rawFile(context, rideId), true))) {
                JSONObject o = new JSONObject();
                o.put("lat", loc.getLatitude());
                o.put("lng", loc.getLongitude());
                o.put("time", loc.getTime() > 0L ? loc.getTime() : System.currentTimeMillis());
                o.put("accuracy", loc.hasAccuracy() ? loc.getAccuracy() : JSONObject.NULL);
                o.put("speed_mps", loc.hasSpeed() ? loc.getSpeed() : JSONObject.NULL);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    o.put("speed_accuracy_mps",
                            loc.hasSpeedAccuracy() ? loc.getSpeedAccuracyMetersPerSecond() : JSONObject.NULL);
                }
                o.put("provider", loc.getProvider() == null ? JSONObject.NULL : loc.getProvider());
                o.put("source", source == null ? JSONObject.NULL : source);
                o.put("segment", segment);
                out.write(o.toString());
                out.newLine();
            } catch (Throwable e) {
                DiagnosticLogger.log(context, "gps_raw_error", "append ride=" + rideId, e);
            }
        }
    }

    public static void processAsync(Context context, int rideId, Callback callback) {
        Context app = context == null ? null : context.getApplicationContext();
        if (app == null || rideId <= 0) return;
        IO.execute(() -> {
            Result result = process(app, rideId);
            if (callback != null) {
                try { callback.onDone(result); } catch (Throwable ignored) { }
            }
        });
    }

    public static Result process(Context context, int rideId) {
        List<Fix> raw = readRaw(context, rideId);
        if (raw.isEmpty()) {
            Result empty = new Result(rideId, 0, 0, 0, 0, 0, 0, 0);
            writeSummary(context, empty);
            return empty;
        }

        raw.sort(Comparator.comparingLong(a -> a.time));

        List<Fix> prelim = new ArrayList<>();
        List<Long> gpsTimes = new ArrayList<>();
        for (Fix f : raw) {
            if ("gps".equalsIgnoreCase(f.provider)) gpsTimes.add(f.time);
        }

        int rejected = 0;
        Fix previous = null;
        for (Fix f : raw) {
            if (!validCoordinate(f) || f.accuracy <= 0f || f.accuracy > 30f) {
                rejected++;
                continue;
            }
            if ("network".equalsIgnoreCase(f.provider) && hasNearbyGps(gpsTimes, f.time, 10000L)) {
                rejected++;
                continue;
            }
            if (previous != null && previous.segment == f.segment) {
                long dt = f.time - previous.time;
                if (dt <= 0L) {
                    rejected++;
                    continue;
                }
                if (dt < 400L && distance(previous, f) < 3.0) {
                    rejected++;
                    continue;
                }
            }
            prelim.add(f);
            previous = f;
        }

        boolean[] spike = new boolean[prelim.size()];
        for (int i = 1; i + 1 < prelim.size(); i++) {
            Fix a = prelim.get(i - 1);
            Fix b = prelim.get(i);
            Fix d = prelim.get(i + 1);
            if (a.segment != b.segment || b.segment != d.segment) continue;

            long dt1 = b.time - a.time;
            long dt2 = d.time - b.time;
            if (dt1 <= 0L || dt2 <= 0L || dt1 > 15000L || dt2 > 15000L) continue;

            double ab = distance(a, b);
            double bd = distance(b, d);
            double ad = distance(a, d);
            double uncertainty = Math.max(8.0, Math.max(a.accuracy, Math.max(b.accuracy, d.accuracy)) * 1.25);
            double detour = Math.max(0.0, ab + bd - ad);
            double ratio = (ab + bd) / Math.max(3.0, ad);
            double midKmh = Math.max(ab / (dt1 / 1000.0), bd / (dt2 / 1000.0)) * 3.6;

            if (ab >= Math.max(18.0, uncertainty * 1.2) &&
                    bd >= Math.max(18.0, uncertainty * 1.2) &&
                    detour >= Math.max(18.0, uncertainty * 1.3) &&
                    ratio >= 2.1 && midKmh > 55.0) {
                spike[i] = true;
            }
        }

        List<Fix> accepted = new ArrayList<>();
        List<Double> recentSpeeds = new ArrayList<>();
        for (int i = 0; i < prelim.size(); i++) {
            if (spike[i]) {
                rejected++;
                continue;
            }
            Fix f = prelim.get(i);
            if (accepted.isEmpty()) {
                if (f.accuracy > 25f) {
                    rejected++;
                    continue;
                }
                accepted.add(f);
                continue;
            }

            Fix last = accepted.get(accepted.size() - 1);
            if (last.segment != f.segment) {
                accepted.add(f);
                recentSpeeds.clear();
                continue;
            }

            long dtMs = f.time - last.time;
            if (dtMs <= 0L) {
                rejected++;
                continue;
            }

            double meters = distance(last, f);
            double dtSec = dtMs / 1000.0;
            double kmh = meters / Math.max(0.35, dtSec) * 3.6;
            double uncertainty = Math.max(5.0,
                    Math.sqrt(last.accuracy * last.accuracy + f.accuracy * f.accuracy));
            double recent = median(recentSpeeds);

            boolean impossible = kmh > 110.0 && meters > uncertainty;
            boolean dynamicJump = recent > 0.0 && dtSec <= 6.0 &&
                    kmh > Math.max(62.0, recent + 34.0) &&
                    meters > Math.max(12.0, uncertainty * 1.15);
            double accel = recent > 0.0 ? Math.abs(kmh - recent) / 3.6 / Math.max(0.35, dtSec) : 0.0;
            boolean accelJump = recent > 0.0 && accel > 4.5 && kmh > 45.0 &&
                    meters > Math.max(10.0, uncertainty);

            if (impossible || dynamicJump || accelJump) {
                rejected++;
                continue;
            }

            boolean reliableOsSpeed = f.hasSpeed &&
                    (!f.hasSpeedAccuracy || f.speedAccuracy <= 2.0f);
            double noiseRadius = Math.max(1.2, Math.min(4.5, uncertainty * 0.22));
            if (meters < noiseRadius && (!reliableOsSpeed || f.speedMps < 0.75f)) {
                rejected++;
                continue;
            }

            accepted.add(f);
            recentSpeeds.add(kmh);
            while (recentSpeeds.size() > 7) recentSpeeds.remove(0);
        }

        double distance = 0.0;
        long movingMs = 0L;
        double cumulativeWindowDistance = 0.0;
        double maxRolling = 0.0;
        List<Double> segmentDistances = new ArrayList<>();
        List<Long> segmentTimes = new ArrayList<>();

        for (int i = 1; i < accepted.size(); i++) {
            Fix a = accepted.get(i - 1);
            Fix b = accepted.get(i);
            if (a.segment != b.segment) {
                segmentDistances.add(0.0);
                segmentTimes.add(0L);
                continue;
            }
            long dt = b.time - a.time;
            if (dt <= 0L || dt > 120000L) {
                segmentDistances.add(0.0);
                segmentTimes.add(0L);
                continue;
            }
            double d = distance(a, b);
            distance += d;
            double kmh = d / (dt / 1000.0) * 3.6;
            if (kmh >= 1.0 && kmh <= 110.0) movingMs += dt;
            segmentDistances.add(d);
            segmentTimes.add(dt);
        }

        for (int end = 1; end < accepted.size(); end++) {
            Fix endFix = accepted.get(end);
            double winDistance = 0.0;
            long winTime = 0L;
            for (int i = end - 1; i >= 0; i--) {
                Fix a = accepted.get(i);
                Fix b = accepted.get(i + 1);
                if (a.segment != endFix.segment || b.segment != endFix.segment) break;
                long dt = b.time - a.time;
                if (dt <= 0L || dt > 15000L) break;
                winDistance += distance(a, b);
                winTime += dt;
                if (winTime >= 3000L) {
                    double speed = winDistance / (winTime / 1000.0) * 3.6;
                    if (winTime <= 6500L && speed <= 110.0) maxRolling = Math.max(maxRolling, speed);
                    break;
                }
            }
        }

        double avgMoving = movingMs > 0L ? distance / (movingMs / 1000.0) * 3.6 : 0.0;
        Result result = new Result(
                rideId,
                raw.size(),
                accepted.size(),
                Math.max(rejected, raw.size() - accepted.size()),
                distance,
                maxRolling,
                avgMoving,
                movingMs);

        writeFiltered(context, rideId, accepted);
        writeSummary(context, result);
        DiagnosticLogger.log(context, "postprocess", result.toJson().toString());
        return result;
    }

    public static String summaryJson(Context context, int rideId) {
        File f = summaryFile(context, rideId);
        if (!f.exists()) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        } catch (Throwable ignored) { }
        return sb.toString();
    }

    private static List<Fix> readRaw(Context context, int rideId) {
        List<Fix> out = new ArrayList<>();
        File f = rawFile(context, rideId);
        if (!f.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    JSONObject o = new JSONObject(line);
                    Fix x = new Fix();
                    x.lat = o.optDouble("lat", Double.NaN);
                    x.lng = o.optDouble("lng", Double.NaN);
                    x.time = o.optLong("time", 0L);
                    x.accuracy = (float)o.optDouble("accuracy", -1.0);
                    x.hasSpeed = !o.isNull("speed_mps");
                    x.speedMps = (float)o.optDouble("speed_mps", 0.0);
                    x.hasSpeedAccuracy = !o.isNull("speed_accuracy_mps");
                    x.speedAccuracy = (float)o.optDouble("speed_accuracy_mps", 999.0);
                    x.provider = o.optString("provider", "");
                    x.segment = o.optInt("segment", 0);
                    out.add(x);
                } catch (Throwable ignored) { }
            }
        } catch (Throwable e) {
            DiagnosticLogger.log(context, "postprocess_error", "read raw ride=" + rideId, e);
        }
        return out;
    }

    private static void writeFiltered(Context context, int rideId, List<Fix> rows) {
        synchronized (WRITE_LOCK) {
            try (BufferedWriter out = new BufferedWriter(new FileWriter(filteredFile(context, rideId), false))) {
                for (Fix f : rows) {
                    JSONObject o = new JSONObject();
                    o.put("lat", f.lat);
                    o.put("lng", f.lng);
                    o.put("time", f.time);
                    o.put("accuracy", f.accuracy);
                    o.put("segment", f.segment);
                    out.write(o.toString());
                    out.newLine();
                }
            } catch (Throwable e) {
                DiagnosticLogger.log(context, "postprocess_error", "write filtered ride=" + rideId, e);
            }
        }
    }

    private static void writeSummary(Context context, Result result) {
        synchronized (WRITE_LOCK) {
            try (BufferedWriter out = new BufferedWriter(new FileWriter(summaryFile(context, result.rideId), false))) {
                out.write(result.toJson().toString());
            } catch (Throwable e) {
                DiagnosticLogger.log(context, "postprocess_error", "write summary ride=" + result.rideId, e);
            }
        }
    }

    private static boolean validCoordinate(Fix f) {
        return f != null && Double.isFinite(f.lat) && Double.isFinite(f.lng) &&
                f.lat >= -90.0 && f.lat <= 90.0 && f.lng >= -180.0 && f.lng <= 180.0 &&
                f.time > 0L;
    }

    private static boolean hasNearbyGps(List<Long> times, long target, long windowMs) {
        int idx = Collections.binarySearch(times, target);
        if (idx >= 0) return true;
        int insert = -idx - 1;
        if (insert < times.size() && Math.abs(times.get(insert) - target) <= windowMs) return true;
        return insert > 0 && Math.abs(times.get(insert - 1) - target) <= windowMs;
    }

    private static double distance(Fix a, Fix b) {
        return a.location().distanceTo(b.location());
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> x = new ArrayList<>(values);
        Collections.sort(x);
        int n = x.size();
        int m = n / 2;
        return n % 2 == 1 ? x.get(m) : (x.get(m - 1) + x.get(m)) / 2.0;
    }
}
