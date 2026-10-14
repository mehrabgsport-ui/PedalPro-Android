package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight fully-native route renderer.
 * It renders PedalPro route geometry without WebView/Leaflet.
 * Base-map integration is intentionally separate from route geometry.
 */
public final class NativeRouteView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint route = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint live = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint target = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint current = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<P> points = new ArrayList<>();
    private P currentPoint;
    private P targetPoint;
    private boolean showTargetLine = false;
    private int cameraZoom = 15;
    private double cameraLat = Double.NaN;
    private double cameraLng = Double.NaN;

    public NativeRouteView(Context context) { super(context); init(); }
    public NativeRouteView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setBackgroundColor(Color.rgb(6, 19, 31));
        grid.setColor(Color.rgb(18, 48, 65));
        grid.setStrokeWidth(dp(1));
        route.setColor(Color.rgb(44, 220, 225));
        route.setStrokeWidth(dp(4));
        route.setStyle(Paint.Style.STROKE);
        route.setStrokeCap(Paint.Cap.ROUND);
        route.setStrokeJoin(Paint.Join.ROUND);
        live.setColor(Color.rgb(255, 205, 64));
        live.setStrokeWidth(dp(3));
        live.setStyle(Paint.Style.STROKE);
        live.setStrokeCap(Paint.Cap.ROUND);
        target.setColor(Color.rgb(255, 188, 53));
        current.setColor(Color.rgb(47, 228, 159));
        text.setColor(Color.rgb(165, 191, 204));
        text.setTextSize(dp(11));
    }

    public void setPoints(JSONArray rows) {
        points.clear();
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject o = rows.optJSONObject(i);
                if (o == null) continue;
                double lat = o.optDouble("lat", Double.NaN);
                double lng = o.optDouble("lng", Double.NaN);
                if (Double.isFinite(lat) && Double.isFinite(lng)) points.add(new P(lat, lng));
            }
        }
        invalidate();
    }

    public void appendPoints(JSONArray rows) {
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject o = rows.optJSONObject(i);
                if (o == null) continue;
                double lat = o.optDouble("lat", Double.NaN);
                double lng = o.optDouble("lng", Double.NaN);
                if (Double.isFinite(lat) && Double.isFinite(lng)) {
                    if (points.isEmpty() || distance(points.get(points.size()-1), new P(lat,lng)) > 0.5f) {
                        points.add(new P(lat, lng));
                    }
                }
            }
        }
        invalidate();
    }

    public void setCurrent(double lat, double lng) {
        currentPoint = new P(lat, lng);
        invalidate();
    }

    public void setTarget(double lat, double lng) {
        targetPoint = new P(lat, lng);
        showTargetLine = true;
        invalidate();
    }

    public void clearTarget() {
        targetPoint = null;
        showTargetLine = false;
        invalidate();
    }

    public int pointCount() { return points.size(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth(), h = getHeight();
        if (w <= 1 || h <= 1) return;

        Bounds b = bounds();
        if (b == null) {
            canvas.drawText("در انتظار دریافت اولین نقطه GPS…", dp(16), h / 2f, text);
            return;
        }

        float pad = dp(22);
        cameraLat = (b.minLat + b.maxLat) * .5;
        cameraLng = (b.minLng + b.maxLng) * .5;
        for (cameraZoom = 17; cameraZoom > 3; cameraZoom--) {
            double spanX = PedalMapTiles.worldX(b.maxLng, cameraZoom)
                    - PedalMapTiles.worldX(b.minLng, cameraZoom);
            double spanY = PedalMapTiles.worldY(b.minLat, cameraZoom)
                    - PedalMapTiles.worldY(b.maxLat, cameraZoom);
            if (spanX <= w - pad * 2 && spanY <= h - pad * 2) break;
        }
        PedalMapTiles.draw(this, canvas, cameraLat, cameraLng, cameraZoom);
        Path p = new Path();
        boolean first = true;
        for (P x : points) {
            float sx = projectX(x.lng, b, w, pad);
            float sy = projectY(x.lat, b, h, pad);
            if (first) { p.moveTo(sx, sy); first = false; }
            else p.lineTo(sx, sy);
        }
        if (!first) canvas.drawPath(p, route);

        if (showTargetLine && currentPoint != null && targetPoint != null) {
            canvas.drawLine(
                    projectX(currentPoint.lng, b, w, pad),
                    projectY(currentPoint.lat, b, h, pad),
                    projectX(targetPoint.lng, b, w, pad),
                    projectY(targetPoint.lat, b, h, pad),
                    live);
        }

        if (currentPoint != null) {
            canvas.drawCircle(projectX(currentPoint.lng, b, w, pad),
                    projectY(currentPoint.lat, b, h, pad), dp(7), current);
        }
        if (targetPoint != null) {
            canvas.drawCircle(projectX(targetPoint.lng, b, w, pad),
                    projectY(targetPoint.lat, b, h, pad), dp(8), target);
            Paint ring = new Paint(target);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(2));
            canvas.drawCircle(projectX(targetPoint.lng, b, w, pad),
                    projectY(targetPoint.lat, b, h, pad), dp(13), ring);
        }

        canvas.drawText(points.size() + " نقطه GPS", dp(10), h - dp(10), text);
    }

    private Bounds bounds() {
        if (points.isEmpty() && currentPoint == null && targetPoint == null) return null;
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLng = Double.POSITIVE_INFINITY, maxLng = Double.NEGATIVE_INFINITY;
        for (P p : points) {
            minLat = Math.min(minLat, p.lat); maxLat = Math.max(maxLat, p.lat);
            minLng = Math.min(minLng, p.lng); maxLng = Math.max(maxLng, p.lng);
        }
        if (currentPoint != null) {
            minLat = Math.min(minLat, currentPoint.lat); maxLat = Math.max(maxLat, currentPoint.lat);
            minLng = Math.min(minLng, currentPoint.lng); maxLng = Math.max(maxLng, currentPoint.lng);
        }
        if (targetPoint != null) {
            minLat = Math.min(minLat, targetPoint.lat); maxLat = Math.max(maxLat, targetPoint.lat);
            minLng = Math.min(minLng, targetPoint.lng); maxLng = Math.max(maxLng, targetPoint.lng);
        }
        if (!Double.isFinite(minLat)) return null;
        if (maxLat - minLat < 0.0003) { maxLat += 0.00015; minLat -= 0.00015; }
        if (maxLng - minLng < 0.0003) { maxLng += 0.00015; minLng -= 0.00015; }
        return new Bounds(minLat,maxLat,minLng,maxLng);
    }

    private float projectX(double lng, Bounds b, float w, float pad) {
        return w * .5f + (float)(PedalMapTiles.worldX(lng,cameraZoom)
                - PedalMapTiles.worldX(cameraLng,cameraZoom));
    }
    private float projectY(double lat, Bounds b, float h, float pad) {
        return h * .5f + (float)(PedalMapTiles.worldY(lat,cameraZoom)
                - PedalMapTiles.worldY(cameraLat,cameraZoom));
    }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    public static float distance(P a, P b) {
        float[] r = new float[1];
        Location.distanceBetween(a.lat,a.lng,b.lat,b.lng,r);
        return r[0];
    }

    public static final class P {
        public final double lat, lng;
        public P(double lat, double lng) { this.lat=lat; this.lng=lng; }
    }
    private static final class Bounds {
        final double minLat,maxLat,minLng,maxLng;
        Bounds(double a,double b,double c,double d){minLat=a;maxLat=b;minLng=c;maxLng=d;}
    }
}
