package ir.pedalpro.app;

import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Thin adapter around Neshan's official Map Matching service.
 *
 * The Android Services SDK published by Neshan uses:
 *   GET https://api.neshan.org/v1/map-matching?path=lat,lng|...
 *   Header: Api-Key
 *
 * PedalPro keeps this adapter dependency-free so the tracking foreground service
 * stays light. The result is treated as a suggestion only: strict geometry and
 * distance checks decide whether a snapped point is safe to use.
 */
final class NeshanRoadMatcher {
    private static final String ENDPOINT = "https://api.neshan.org/v1/map-matching?path=";
    private static final int CONNECT_TIMEOUT_MS = 2800;
    private static final int READ_TIMEOUT_MS = 3200;

    private NeshanRoadMatcher() { }

    static MatchResult match(String apiKey, List<Location> rawPoints) {
        if (!validKey(apiKey) || rawPoints == null || rawPoints.size() < 2) {
            return MatchResult.rejected();
        }

        HttpURLConnection connection = null;
        try {
            StringBuilder path = new StringBuilder();
            for (Location p : rawPoints) {
                if (p == null) continue;
                if (path.length() > 0) path.append('|');
                path.append(p.getLatitude()).append(',').append(p.getLongitude());
            }
            if (path.length() == 0) return MatchResult.rejected();

            String encoded = URLEncoder.encode(path.toString(), StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
            connection = (HttpURLConnection) new URL(ENDPOINT + encoded).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Api-Key", apiKey);

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return MatchResult.rejected();

            String body = readBody(connection.getInputStream());
            JSONObject root = new JSONObject(body);
            JSONArray points = root.optJSONArray("snappedPoints");
            if (points == null) points = root.optJSONArray("snapped_points");
            if (points == null || points.length() < 2) return MatchResult.rejected();

            Snapped[] snapped = new Snapped[rawPoints.size()];
            List<Double> offsets = new ArrayList<>();
            int matched = 0;

            for (int i = 0; i < points.length(); i++) {
                JSONObject row = points.optJSONObject(i);
                if (row == null) continue;
                int originalIndex = row.has("originalIndex")
                        ? row.optInt("originalIndex", -1)
                        : row.optInt("original_index", -1);
                if (originalIndex < 0 || originalIndex >= rawPoints.size()) continue;

                JSONObject loc = row.optJSONObject("location");
                if (loc == null) continue;
                double lat = loc.optDouble("latitude", Double.NaN);
                double lng = loc.optDouble("longitude", Double.NaN);
                if (!Double.isFinite(lat) || !Double.isFinite(lng)) continue;

                Location raw = rawPoints.get(originalIndex);
                double offset = distanceMeters(raw.getLatitude(), raw.getLongitude(), lat, lng);
                snapped[originalIndex] = new Snapped(lat, lng, offset);
                offsets.add(offset);
                matched++;
            }

            if (matched < Math.max(2, (int)Math.ceil(rawPoints.size() * 0.65))) {
                return MatchResult.rejected();
            }

            double medianOffset = median(offsets);
            double coverage = matched / (double) rawPoints.size();

            int last = lastMatchedIndex(snapped);
            int prev = previousMatchedIndex(snapped, last);
            if (last < 0 || prev < 0) return MatchResult.rejected();

            Location rawPrev = rawPoints.get(prev);
            Location rawLast = rawPoints.get(last);
            double rawBearing = bearingDegrees(
                    rawPrev.getLatitude(), rawPrev.getLongitude(),
                    rawLast.getLatitude(), rawLast.getLongitude());
            double snappedBearing = bearingDegrees(
                    snapped[prev].latitude, snapped[prev].longitude,
                    snapped[last].latitude, snapped[last].longitude);
            double headingDiff = angleDifference(rawBearing, snappedBearing);

            double rawLength = pathLengthRaw(rawPoints);
            double snappedLength = pathLengthSnapped(snapped);
            double ratio = rawLength > 3.0 ? snappedLength / rawLength : 1.0;
            double continuityScore = clamp01(1.0 - Math.abs(1.0 - ratio) / 0.85);
            double distanceScore = clamp01(1.0 - medianOffset / 38.0);
            double headingScore = clamp01(1.0 - headingDiff / 75.0);

            double confidence =
                    distanceScore * 0.45 +
                    headingScore * 0.25 +
                    coverage * 0.15 +
                    continuityScore * 0.15;

            // Conservative gates: roads are optional for PedalPro because riders also
            // use trails. A far-away road or a route with a conflicting heading must
            // never pull a valid off-road ride onto asphalt.
            if (medianOffset > 28.0) return MatchResult.rejected();
            if (headingDiff > 65.0 && rawLength > 15.0) return MatchResult.rejected();
            if (ratio < 0.48 || ratio > 1.95) return MatchResult.rejected();
            if (confidence < 0.76) return MatchResult.rejected();

            return new MatchResult(true, confidence, medianOffset, snapped);
        } catch (Throwable ignored) {
            return MatchResult.rejected();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static boolean validKey(String key) {
        if (key == null) return false;
        String x = key.trim();
        return x.length() >= 12 && x.length() <= 256 && x.indexOf(' ') < 0;
    }

    private static int lastMatchedIndex(Snapped[] points) {
        for (int i = points.length - 1; i >= 0; i--) if (points[i] != null) return i;
        return -1;
    }

    private static int previousMatchedIndex(Snapped[] points, int before) {
        for (int i = before - 1; i >= 0; i--) if (points[i] != null) return i;
        return -1;
    }

    private static double pathLengthRaw(List<Location> points) {
        double sum = 0.0;
        for (int i = 1; i < points.size(); i++) {
            Location a = points.get(i - 1), b = points.get(i);
            sum += distanceMeters(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        }
        return sum;
    }

    private static double pathLengthSnapped(Snapped[] points) {
        double sum = 0.0;
        Snapped previous = null;
        for (Snapped p : points) {
            if (p == null) continue;
            if (previous != null) {
                sum += distanceMeters(previous.latitude, previous.longitude, p.latitude, p.longitude);
            }
            previous = p;
        }
        return sum;
    }

    private static String readBody(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return Double.POSITIVE_INFINITY;
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int n = copy.size();
        return (n % 2 == 1)
                ? copy.get(n / 2)
                : (copy.get(n / 2 - 1) + copy.get(n / 2)) / 2.0;
    }

    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, result);
        return result[0];
    }

    private static double bearingDegrees(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLon) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) -
                Math.sin(p1) * Math.cos(p2) * Math.cos(dLon);
        double b = Math.toDegrees(Math.atan2(y, x));
        return (b + 360.0) % 360.0;
    }

    private static double angleDifference(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }

    private static double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    static final class Snapped {
        final double latitude;
        final double longitude;
        final double offsetMeters;

        Snapped(double latitude, double longitude, double offsetMeters) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.offsetMeters = offsetMeters;
        }
    }

    static final class MatchResult {
        final boolean accepted;
        final double confidence;
        final double medianOffsetMeters;
        private final Snapped[] snapped;

        MatchResult(boolean accepted, double confidence, double medianOffsetMeters, Snapped[] snapped) {
            this.accepted = accepted;
            this.confidence = confidence;
            this.medianOffsetMeters = medianOffsetMeters;
            this.snapped = snapped;
        }

        static MatchResult rejected() {
            return new MatchResult(false, 0.0, Double.POSITIVE_INFINITY, new Snapped[0]);
        }

        Snapped get(int index) {
            if (!accepted || index < 0 || index >= snapped.length) return null;
            return snapped[index];
        }
    }
}
