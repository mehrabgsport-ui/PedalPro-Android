package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
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
    private final Paint roadRoute = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadRouteBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint target = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint current = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface CameraListener { void onCameraChanged(double lat, double lng, int zoom); }
    public interface MapTapListener { void onMapTap(double latitude,double longitude); }
    private CameraListener cameraListener;
    private MapTapListener mapTapListener;
    private boolean externalBasemap;
    private boolean amoledBatterySaver;
    private final List<P> points = new ArrayList<>();
    private final List<P> navigationPoints = new ArrayList<>();
    private P currentPoint;
    private P targetPoint;
    private JSONArray nearbyRiders=new JSONArray();
    private boolean showTargetLine = false;
    private boolean navigationMode;
    private boolean headingUpEnabled = true;
    private int navigationZoom = 16;
    private float navigationBearing;
    private boolean hasNavigationBearing;
    private final Matrix viewTransform = new Matrix();
    private final Matrix inverseViewTransform = new Matrix();
    private int cameraZoom = 15;
    private double cameraLat = Double.NaN;
    private double cameraLng = Double.NaN;
    private boolean userCamera = false;
    private double userLat, userLng;
    private int userZoom = 15;
    private float lastTouchX, lastTouchY;
    private float pinchAccum = 1f;
    private GestureDetector tapDetector;
    private ScaleGestureDetector scaleDetector;

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
        roadRouteBorder.setColor(Color.rgb(229,254,255));
        roadRouteBorder.setStrokeWidth(dp(11));
        roadRouteBorder.setStyle(Paint.Style.STROKE);
        roadRouteBorder.setStrokeCap(Paint.Cap.ROUND);
        roadRouteBorder.setStrokeJoin(Paint.Join.ROUND);
        roadRoute.setColor(Color.rgb(13,201,219));
        roadRoute.setStrokeWidth(dp(7));
        roadRoute.setStyle(Paint.Style.STROKE);
        roadRoute.setStrokeCap(Paint.Cap.ROUND);
        roadRoute.setStrokeJoin(Paint.Join.ROUND);
        live.setColor(Color.rgb(255, 205, 64));
        live.setStrokeWidth(dp(3));
        live.setStyle(Paint.Style.STROKE);
        live.setStrokeCap(Paint.Cap.ROUND);
        target.setColor(Color.rgb(255, 188, 53));
        current.setColor(Color.rgb(47, 228, 159));
        text.setColor(Color.rgb(165, 191, 204));
        text.setTextSize(dp(11));
        setClickable(true);
        setFocusable(true);
        setContentDescription("نقشه مسیر؛ حرکت با کشیدن انگشت، بزرگ‌نمایی با دو انگشت، بازگشت به مسیر با لمس طولانی");
        tapDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if(mapTapListener!=null&&Double.isFinite(cameraLat)&&Double.isFinite(cameraLng)){
                    // Map selection is done on the north-up raster canvas.
                    double world=PedalMapTiles.TILE*(double)(1<<cameraZoom);
                    double px=PedalMapTiles.worldX(cameraLng,cameraZoom)+e.getX()-getWidth()*.5;
                    double py=PedalMapTiles.worldY(cameraLat,cameraZoom)+e.getY()-getHeight()*.5;
                    double longitude=px/world*360d-180d;
                    double mercator=Math.PI*(1d-2d*Math.max(0d,Math.min(world,py))/world);
                    double latitude=Math.toDegrees(Math.atan(Math.sinh(mercator)));
                    if(Math.abs(latitude)<=85.05112878&&Math.abs(longitude)<=180)
                        mapTapListener.onMapTap(latitude,longitude);
                    return true;
                }
                return false;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                zoomBy(1);
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                userCamera = false;
                invalidate();
            }
        });
        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                ensureUserCamera();
                pinchAccum *= detector.getScaleFactor();
                if (pinchAccum >= 1.4f) {
                    userZoom = Math.min(18, userZoom + 1);
                    pinchAccum = 1f;
                    invalidate();
                } else if (pinchAccum <= .72f) {
                    userZoom = Math.max(3, userZoom - 1);
                    pinchAccum = 1f;
                    invalidate();
                }
                return true;
            }
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                pinchAccum = 1f;
                return true;
            }
        });
    }

    private void ensureUserCamera() {
        if (userCamera || !Double.isFinite(cameraLat) || !Double.isFinite(cameraLng)) return;
        userLat = cameraLat;
        userLng = cameraLng;
        userZoom = cameraZoom;
        userCamera = true;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (scaleDetector != null) scaleDetector.onTouchEvent(e);
        if (tapDetector != null) tapDetector.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = e.getX();
                lastTouchY = e.getY();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() == 1 && (scaleDetector == null || !scaleDetector.isInProgress())) {
                    float dx = e.getX() - lastTouchX, dy = e.getY() - lastTouchY;
                    if (Math.abs(dx) + Math.abs(dy) > dp(1)) {
                        // Unproject BOTH touch points: true projective tilt has
                        // nonlinear scale, so rotation-only deltas drift on drag.
                        if(navigationMode && !userCamera && !externalBasemap) {
                            float[] before={lastTouchX,lastTouchY},after={e.getX(),e.getY()};
                            inverseViewTransform.mapPoints(before);
                            inverseViewTransform.mapPoints(after);
                            dx=after[0]-before[0];dy=after[1]-before[1];
                        }
                        ensureUserCamera();
                        if (userCamera) {
                            double wx = PedalMapTiles.worldX(userLng, userZoom) - dx;
                            double wy = PedalMapTiles.worldY(userLat, userZoom) - dy;
                            double world = PedalMapTiles.TILE * (1 << userZoom);
                            userLng = wx / world * 360.0 - 180.0;
                            double mercator = Math.PI * (1 - 2 * Math.max(0, Math.min(world, wy)) / world);
                            userLat = Math.toDegrees(Math.atan(Math.sinh(mercator)));
                            invalidate();
                        }
                    }
                    lastTouchX = e.getX();
                    lastTouchY = e.getY();
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                lastTouchX = e.getX(0);
                lastTouchY = e.getY(0);
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    public void setNearbyRiders(JSONArray riders){
        nearbyRiders=riders==null?new JSONArray():riders;
        invalidate();
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

    public void focusOn(double lat,double lng){
        if(!Double.isFinite(lat)||!Double.isFinite(lng))return;
        userCamera=true;userLat=lat;userLng=lng;userZoom=Math.max(13,cameraZoom);
        invalidate();
    }

    public void setTarget(double lat, double lng) {
        targetPoint = new P(lat, lng);
        showTargetLine = true;
        invalidate();
    }

    /** Display-only suggested Neshan road route; never part of recorded GPS samples. */
    public void setNavigationRoute(JSONArray rows) {
        navigationPoints.clear();
        if (rows != null) for (int i = 0; i < rows.length() && i < 12000; i++) {
            JSONObject o = rows.optJSONObject(i);
            if (o == null) continue;
            double lat = o.optDouble("lat", Double.NaN);
            double lng = o.optDouble("lng", Double.NaN);
            if (Double.isFinite(lat) && Double.isFinite(lng) && Math.abs(lat) <= 85.0 && Math.abs(lng) <= 180.0)
                navigationPoints.add(new P(lat, lng));
        }
        invalidate();
    }

    public void clearNavigationRoute() { navigationPoints.clear(); invalidate(); }

    public void clearTarget() {
        targetPoint = null;
        showTargetLine = false;
        invalidate();
    }

    public int pointCount() { return points.size(); }

    /** Presentation-only export for SDK polyline; original tracked GPS remains untouched. */
    public JSONArray exportPoints() {
        JSONArray out=new JSONArray();
        int stride=Math.max(1,points.size()/4500);
        for(int i=0;i<points.size();i+=stride){
            P v=points.get(i);
            JSONObject coordinate=new JSONObject();
            try{coordinate.put("lat",v.lat);coordinate.put("lng",v.lng);}
            catch(Exception ignored){continue;}
            out.put(coordinate);
        }
        return out;
    }

    public void setAmoledBatterySaver(boolean enabled){
        amoledBatterySaver=enabled;
        setBackgroundColor(enabled?Color.BLACK:Color.rgb(6,19,31));
        invalidate();
    }

    public void setExternalBasemap(boolean useNativeProvider) {
        externalBasemap = useNativeProvider;
        setBackgroundColor(useNativeProvider ? Color.TRANSPARENT : Color.rgb(6, 19, 31));
        invalidate();
    }

    public void setCameraListener(CameraListener listener) { cameraListener = listener; }
    public void setOnMapTapListener(MapTapListener listener){ mapTapListener=listener; }

    public void zoomBy(int amount) {
        if(navigationMode && !userCamera) {
            navigationZoom = Math.max(3, Math.min(18, navigationZoom + amount));
            invalidate();
            return;
        }
        ensureUserCamera();
        if (userCamera) { userZoom = Math.max(3, Math.min(18, userZoom + amount)); invalidate(); }
    }

    public void recenter() { userCamera = false; invalidate(); }

    /** UI-only navigation camera: heading-up map, without changing recorded GPS samples. */
    public void setNavigationMode(boolean enabled) {
        navigationMode=enabled;
        if(enabled)userCamera=false;
        invalidate();
    }

    public boolean isNavigationMode() { return navigationMode; }

    /** Switch North-Up / Heading-Up without disabling rider following. */
    public void setHeadingUpEnabled(boolean enabled) {
        headingUpEnabled = enabled;
        invalidate();
    }

    public boolean isHeadingUpEnabled() { return headingUpEnabled; }

    public boolean hasHeadingDegrees(){return hasNavigationBearing;}
    public float getHeadingDegrees(){return navigationBearing;}

    /** Android Location bearing: 0=north, 90=east. Ignore invalid/unknown fixes. */
    public void setHeadingDegrees(float bearing) {
        if(!Float.isFinite(bearing))return;
        bearing=((bearing%360f)+360f)%360f;
        if(!hasNavigationBearing) { navigationBearing=bearing;hasNavigationBearing=true; }
        else {
            float delta=((bearing-navigationBearing+540f)%360f)-180f;
            if(Math.abs(delta)>1.5f)navigationBearing=((navigationBearing+delta*.30f)+360f)%360f;
        }
        if(navigationMode && !userCamera)invalidate();
    }

    /**
     * Raster presentation-only projective camera. Unlike RC6's flat Y squeeze,
     * the 4-point homography makes streets converge toward the distant horizon.
     * The licensed provider SDK must supply its own native 3D camera.
     */
    private void calculateTransform(float w,float h,boolean tilted,boolean bearingUp) {
        viewTransform.reset();
        if(tilted) {
            float[] source={0,0,w,0,w,h,0,h};
            float[] dest={w*.12f,-h*.06f,w*.88f,-h*.06f,
                    w*1.08f,h*1.08f,-w*.08f,h*1.08f};
            Matrix pitch=new Matrix();
            pitch.setPolyToPoly(source,0,dest,0,4);
            if(bearingUp) {
                Matrix rotate=new Matrix();
                rotate.setRotate(-navigationBearing,w*.5f,h*.5f);
                viewTransform.setConcat(pitch,rotate);
            }else viewTransform.set(pitch);
        }
        if(!viewTransform.invert(inverseViewTransform))
            inverseViewTransform.reset();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth(), h = getHeight();
        if (w <= 1 || h <= 1) return;

        Bounds b = bounds();
        if (b == null) {
            canvas.drawText("در انتظار موقعیت معتبر برای نمایش نقشه…", dp(16), h / 2f, text);
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
        boolean follow=navigationMode && currentPoint!=null && !userCamera;
        boolean pitched=follow && !externalBasemap;
        boolean headingUp=pitched && headingUpEnabled && hasNavigationBearing;
        calculateTransform(w,h,pitched,headingUp);
        if(follow) {
            cameraZoom=navigationZoom;
            float[] riderAt={w*.5f,h*.68f};
            if(pitched)inverseViewTransform.mapPoints(riderAt);
            double world=PedalMapTiles.TILE*(double)(1<<cameraZoom);
            double centerX=PedalMapTiles.worldX(currentPoint.lng,cameraZoom)
                    -(riderAt[0]-w*.5f);
            double centerY=PedalMapTiles.worldY(currentPoint.lat,cameraZoom)
                    -(riderAt[1]-h*.5f);
            cameraLng=centerX/world*360d-180d;
            double mercator=Math.PI*(1d-2d*Math.max(0d,Math.min(world,centerY))/world);
            cameraLat=Math.toDegrees(Math.atan(Math.sinh(mercator)));
        }
        if (userCamera) {
            cameraLat = userLat;
            cameraLng = userLng;
            cameraZoom = userZoom;
        }
        // The XYZ fallback has a lightweight visual 2.5D pitch; the external SDK
        // supplies its own transform and must not be distorted by Canvas rotation.
        int mapSave=canvas.save();
        if(pitched)canvas.concat(viewTransform);
        if (externalBasemap) {
            if (cameraListener != null) cameraListener.onCameraChanged(cameraLat,cameraLng,cameraZoom);
        } else {
            if(amoledBatterySaver)canvas.drawColor(Color.BLACK);
            else PedalMapTiles.draw(this, canvas, cameraLat, cameraLng, cameraZoom,
                    pitched ? 1.50f : 1.0f, headingUp ? navigationBearing : 0f,
                    pitched ? inverseViewTransform : null);
        }
        Path p = new Path();
        boolean first = true;
        for (P x : points) {
            float sx = projectX(x.lng, b, w, pad);
            float sy = projectY(x.lat, b, h, pad);
            if (first) { p.moveTo(sx, sy); first = false; }
            else p.lineTo(sx, sy);
        }
        if (!first) canvas.drawPath(p, route);

        if (!navigationPoints.isEmpty()) {
            Path road = new Path();
            boolean firstRoad = true;
            for (P r : navigationPoints) {
                float x = projectX(r.lng, b, w, pad), y = projectY(r.lat, b, h, pad);
                if (firstRoad) { road.moveTo(x,y); firstRoad = false; }
                else road.lineTo(x,y);
            }
            canvas.drawPath(road, roadRouteBorder);
            canvas.drawPath(road, roadRoute);
        }

        if (showTargetLine && currentPoint != null && targetPoint != null) {
            canvas.drawLine(
                    projectX(currentPoint.lng, b, w, pad),
                    projectY(currentPoint.lat, b, h, pad),
                    projectX(targetPoint.lng, b, w, pad),
                    projectY(targetPoint.lat, b, h, pad),
                    live);
        }

        if (currentPoint != null && (!navigationMode || userCamera)) {
            canvas.drawCircle(projectX(currentPoint.lng, b, w, pad),
                    projectY(currentPoint.lat, b, h, pad), dp(7), current);
        }
        // Fallback renderer also shows the authorized nearby active riders.
        for(int i=0;i<nearbyRiders.length()&&i<80;i++){
            JSONObject other=nearbyRiders.optJSONObject(i);
            if(other==null||other.optInt("age_seconds",99)>45)continue;
            double latitude=other.optDouble("lat",Double.NaN);
            double longitude=other.optDouble("lng",Double.NaN);
            if(!Double.isFinite(latitude)||!Double.isFinite(longitude))continue;
            float ox=projectX(longitude,b,w,pad),oy=projectY(latitude,b,h,pad);
            Paint otherPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            otherPaint.setColor(Color.rgb(255,157,52));
            canvas.drawCircle(ox,oy,dp(7),otherPaint);
            otherPaint.setColor(Color.WHITE);otherPaint.setTextSize(dp(10));
            String name=other.optString("display_name","دوچرخه‌سوار");
            canvas.drawText(name,ox+dp(10),oy-dp(8),otherPaint);
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

        canvas.restoreToCount(mapSave);
        if(!externalBasemap&&!amoledBatterySaver)PedalMapTiles.drawAttribution(this,canvas);
        if(navigationMode && !userCamera && currentPoint!=null && !externalBasemap) {
            // The marker stays upright in Heading-Up and reflects travel direction
            // in North-Up. No fake heading is shown before a valid bearing arrives.
            float riderX=w*.5f, riderY=h*.68f;
            Paint bike=new Paint(Paint.ANTI_ALIAS_FLAG);
            bike.setColor(Color.rgb(9,46,66));
            canvas.drawCircle(riderX,riderY,dp(18),bike);
            bike.setColor(Color.rgb(21,227,239));
            if(hasNavigationBearing) {
                int markerSave=canvas.save();
                if(!headingUpEnabled)canvas.rotate(navigationBearing,riderX,riderY);
                Path heading=new Path();
                heading.moveTo(riderX,riderY-dp(18));
                heading.lineTo(riderX-dp(10),riderY+dp(12));
                heading.lineTo(riderX,riderY+dp(6));
                heading.lineTo(riderX+dp(10),riderY+dp(12));
                heading.close();
                canvas.drawPath(heading,bike);
                canvas.restoreToCount(markerSave);
            } else canvas.drawCircle(riderX,riderY,dp(7),bike);
        }
        canvas.drawText(points.size() + " نقطه GPS", dp(10), h - dp(10), text);
    }

    private Bounds bounds() {
        if (points.isEmpty() && navigationPoints.isEmpty() && currentPoint == null && targetPoint == null) return null;
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLng = Double.POSITIVE_INFINITY, maxLng = Double.NEGATIVE_INFINITY;
        for (P p : points) {
            minLat = Math.min(minLat, p.lat); maxLat = Math.max(maxLat, p.lat);
            minLng = Math.min(minLng, p.lng); maxLng = Math.max(maxLng, p.lng);
        }
        for (P nav : navigationPoints) {
            minLat = Math.min(minLat, nav.lat); maxLat = Math.max(maxLat, nav.lat);
            minLng = Math.min(minLng, nav.lng); maxLng = Math.max(maxLng, nav.lng);
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
