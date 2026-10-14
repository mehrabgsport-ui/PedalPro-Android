package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Polyline;
import org.maplibre.android.annotations.PolylineOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.gestures.MoveGestureDetector;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Neshan's CURRENT SDK (MapLibre Native 13.4.1).
 * A MOBILE map API key is supplied via AndroidManifest meta-data, restricted in Neshan
 * to the package/signing fingerprint. No old neshan.license or WebView is used.
 * TrackingService and recorded ride data are deliberately not modified.
 */
public final class NeshanMapLibreBridge implements NeshanMapBridge {
    private static final String STYLES="https://static.neshan.org/sdk/maplibre/styles/";
    private final MapView view;
    private final Activity activity;
    private final Application.ActivityLifecycleCallbacks lifecycle;
    private MapLibreMap map;
    private boolean ready, destroyed, night, navigation, headingUp=true, validHeading;
    private boolean manualPan;
    private boolean firstCamera=true;
    private float heading;
    private double lat=Double.NaN,lng=Double.NaN,goalLat=Double.NaN,goalLng=Double.NaN;
    private int zoom=17;
    private long lastCameraMs,lastMarkerMs,lastLineMs;
    private double lastCameraLat=Double.NaN,lastCameraLng=Double.NaN;
    private Marker rider,goal;
    private org.maplibre.android.annotations.Icon riderIcon;
    private Polyline recorded,suggested;
    private JSONArray recordedPoints,suggestedPoints;
    private boolean started,resumed;
    private final Runnable delayedGeometry=()->applyGeometry();

    public NeshanMapLibreBridge(Context context) {
        if(!(context instanceof Activity))throw new IllegalArgumentException("Activity required");
        activity=(Activity)context;
        MapLibre.getInstance(context);
        view=new MapView(context);
        view.onCreate(null);
        view.getMapAsync(m->{
            if(destroyed)return;
            map=m;
            map.getUiSettings().setCompassEnabled(false);
            map.addOnMoveListener(new MapLibreMap.OnMoveListener(){
                @Override public void onMoveBegin(MoveGestureDetector detector){manualPan=true;}
                @Override public void onMove(MoveGestureDetector detector){}
                @Override public void onMoveEnd(MoveGestureDetector detector){}
            });
            setNight(true);
        });
        lifecycle=new Application.ActivityLifecycleCallbacks(){
            @Override public void onActivityCreated(Activity a,Bundle b){}
            @Override public void onActivityStarted(Activity a){if(a==activity&&!started){view.onStart();started=true;}}
            @Override public void onActivityResumed(Activity a){if(a==activity&&!resumed){view.onResume();resumed=true;}}
            @Override public void onActivityPaused(Activity a){if(a==activity&&resumed){view.onPause();resumed=false;}}
            @Override public void onActivityStopped(Activity a){if(a==activity&&started){view.onStop();started=false;}}
            @Override public void onActivitySaveInstanceState(Activity a,Bundle b){
                if(a==activity&&!destroyed)view.onSaveInstanceState(b);
            }
            @Override public void onActivityDestroyed(Activity a){
                if(a!=activity||destroyed)return;
                destroyed=true;
                view.removeCallbacks(delayedGeometry);
                if(resumed)view.onPause();
                if(started)view.onStop();
                view.onDestroy();
                activity.getApplication().unregisterActivityLifecycleCallbacks(this);
            }
        };
        activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);
        // This screen is created after Activity.onResume, so forward the initial
        // lifecycle manually; subsequent background transitions use callbacks.
        view.onStart(); started=true;
        view.onResume(); resumed=true;
    }

    @Override public View view(){return view;}

    @Override public void setNight(boolean enabled){
        if(map==null||destroyed)return;
        if(ready&&night==enabled)return;
        night=enabled;
        ready=false;
        firstCamera=true;
        map.setStyle(new Style.Builder().fromUri(
            STYLES+(night?"dark.json":"light.json")),style->{
                if(destroyed)return;
                ready=true;
                // Rebuild annotations because changing a vector style may clear layers.
                rider=null;goal=null;recorded=null;suggested=null;
                applyGeometry();
                updateRiderMarker(true);
                updateTargetMarker();
                updateCamera(true);
            });
    }

    @Override public void setTraffic(boolean enabled){
        // The Neshan MapLibre style does not document a separate live traffic toggle.
        // Do not guess a traffic vector URL or mix a car traffic route with cycling.
    }

    @Override public void setNavigation(boolean enabled,boolean up,float course,boolean valid){
        boolean wasNavigation=navigation;
        navigation=enabled;
        headingUp=up;
        validHeading=valid&&Float.isFinite(course);
        if(validHeading)heading=((course%360f)+360f)%360f;
        if(!enabled)manualPan=false;
        if(enabled&&!wasNavigation)manualPan=false;
        updateCamera(true);
    }

    @Override public void setCurrent(double latitude,double longitude){
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||
                Math.abs(latitude)>85||Math.abs(longitude)>180)return;
        lat=latitude;lng=longitude;
        updateRiderMarker(false);
        if(firstCamera && ready && !navigation){
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat,lng),zoom));
            firstCamera=false;
        }else updateCamera(false);
    }


    /** Isometric painted bicycle; bottom wheel contacts the GPS route anchor. */
    private org.maplibre.android.annotations.Icon bikeMapIcon(){
        if(riderIcon!=null)return riderIcon;
        int side=(int)(88*activity.getResources().getDisplayMetrics().density);
        side=Math.max(88,Math.min(side,220));
        Bitmap bitmap=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        c.save();c.scale(side/100f,side/100f);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(92,0,12,24));
        c.drawOval(35,91,66,99,p);
        drawWheel(c,p,50,89,16,8);
        drawWheel(c,p,50,26,11,6);
        android.graphics.Path frame=new android.graphics.Path();
        frame.moveTo(50,89);frame.lineTo(37,50);frame.lineTo(50,27);
        frame.moveTo(37,50);frame.lineTo(64,52);frame.lineTo(50,89);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(6);p.setColor(Color.rgb(4,35,51));c.drawPath(frame,p);
        p.setStrokeWidth(3.4f);p.setColor(Color.rgb(26,217,233));c.drawPath(frame,p);
        p.setStrokeWidth(1.3f);p.setColor(Color.WHITE);c.drawLine(37,50,50,27,p);
        p.setStrokeWidth(4);p.setColor(Color.rgb(8,50,65));
        c.drawLine(32,30,68,30,p);c.drawLine(37,43,55,43,p);
        p.setColor(Color.rgb(254,185,91));c.drawLine(34,34,44,51,p);c.drawLine(65,34,55,51,p);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(249,194,109));c.drawOval(42,14,59,31,p);
        p.setColor(Color.rgb(7,66,91));c.drawOval(40,33,60,57,p);
        p.setColor(Color.rgb(24,205,230));c.drawOval(43,35,54,48,p);
        c.restore();
        riderIcon=IconFactory.getInstance(activity).fromBitmap(bitmap);
        return riderIcon;
    }

    private static void drawWheel(Canvas c,Paint p,float x,float y,float rx,float ry){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5.5f);p.setColor(Color.rgb(5,33,48));
        c.drawOval(x-rx,y-ry,x+rx,y+ry,p);
        p.setStrokeWidth(2.0f);p.setColor(Color.rgb(180,228,234));
        c.drawOval(x-rx+2,y-ry+2,x+rx-2,y+ry-2,p);
        p.setStrokeWidth(1f);c.drawLine(x-rx+3,y,x+rx-3,y,p);
    }

    private void updateRiderMarker(boolean force){
        if(!ready||!Double.isFinite(lat))return;
        long now=SystemClock.elapsedRealtime();
        if(!force&&now-lastMarkerMs<450)return;
        if(rider==null)rider=map.addMarker(new MarkerOptions()
                .position(new LatLng(lat,lng)).icon(bikeMapIcon()).title("رکاب‌زن"));
        else {rider.setPosition(new LatLng(lat,lng));map.updateMarker(rider);}
        lastMarkerMs=now;
    }

    @Override public void setRecordedTrack(JSONArray data){
        recordedPoints=data;
        scheduleGeometry();
    }
    @Override public void setSuggestedRoute(JSONArray data){
        suggestedPoints=data;
        scheduleGeometry();
    }

    private void scheduleGeometry(){
        if(!ready)return;
        long remain=1800-(SystemClock.elapsedRealtime()-lastLineMs);
        view.removeCallbacks(delayedGeometry);
        if(remain>0)view.postDelayed(delayedGeometry,remain);
        else applyGeometry();
    }
    private static List<LatLng> parse(JSONArray pts){
        ArrayList<LatLng> result=new ArrayList<>();
        if(pts==null)return result;
        int skip=Math.max(1,pts.length()/3500);
        for(int i=0;i<pts.length();i+=skip){
            JSONObject p=pts.optJSONObject(i);
            if(p==null)continue;
            double a=p.optDouble("lat",Double.NaN),b=p.optDouble("lng",Double.NaN);
            if(Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a)<85&&Math.abs(b)<=180)
                result.add(new LatLng(a,b));
        }
        if(pts.length()>0&&(pts.length()-1)%skip!=0){
            JSONObject last=pts.optJSONObject(pts.length()-1);
            if(last!=null){
                double a=last.optDouble("lat",Double.NaN),b=last.optDouble("lng",Double.NaN);
                if(Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a)<85&&Math.abs(b)<=180)
                    result.add(new LatLng(a,b));
            }
        }
        return result;
    }
    private void applyGeometry(){
        if(destroyed||!ready||map==null)return;
        if(recorded!=null){map.removePolyline(recorded);recorded=null;}
        if(suggested!=null){map.removePolyline(suggested);suggested=null;}
        List<LatLng> history=parse(recordedPoints),route=parse(suggestedPoints);
        if(history.size()>1){
            recorded=map.addPolyline(new PolylineOptions()
                    .addAll(history).color(Color.rgb(28,215,230)).width(5f));
            if(firstCamera&&!navigation){
                LatLngBounds.Builder bounds=new LatLngBounds.Builder();
                for(LatLng point:history)bounds.include(point);
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),44));
                firstCamera=false;
            }
        }
        if(route.size()>1)suggested=map.addPolyline(new PolylineOptions()
                .addAll(route).color(Color.rgb(255,194,43)).width(8f));
        lastLineMs=SystemClock.elapsedRealtime();
    }

    @Override public void setTarget(double latitude,double longitude){
        goalLat=latitude;goalLng=longitude;updateTargetMarker();
        if(ready&&firstCamera&&!navigation&&Double.isFinite(latitude)&&Double.isFinite(longitude)){
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(latitude,longitude),Math.min(zoom,15)));
            firstCamera=false;
        }
    }
    @Override public void clearTarget(){
        goalLat=Double.NaN;goalLng=Double.NaN;
        if(ready&&goal!=null){map.removeMarker(goal);goal=null;}
    }
    private void updateTargetMarker(){
        if(!ready||!Double.isFinite(goalLat)||!Double.isFinite(goalLng))return;
        if(goal==null)goal=map.addMarker(new MarkerOptions()
                .position(new LatLng(goalLat,goalLng)).title("مقصد"));
        else {goal.setPosition(new LatLng(goalLat,goalLng));map.updateMarker(goal);}
    }

    @Override public void zoomBy(int amount){
        zoom=Math.max(5,Math.min(19,zoom+amount));
        updateCamera(true);
    }
    @Override public void recenter(){
        manualPan=false;navigation=true;updateCamera(true);
    }
    private void updateCamera(boolean force){
        if(!ready||!Double.isFinite(lat)||manualPan||!navigation)return;
        long now=SystemClock.elapsedRealtime();
        if(!force&&now-lastCameraMs<450)return;
        float bearing=headingUp&&validHeading?heading:0f;
        // Focus ahead, not on the rider. This leaves navigation road space ahead.
        double ahead=headingUp&&validHeading?35d:0d;
        double camLat=lat+ahead*Math.cos(Math.toRadians(bearing))/111320d;
        double camLng=lng+ahead*Math.sin(Math.toRadians(bearing))/
                (111320d*Math.max(.1d,Math.cos(Math.toRadians(lat))));
        if(!force&&Double.isFinite(lastCameraLat)){
            float[] delta=new float[1];
            Location.distanceBetween(lastCameraLat,lastCameraLng,camLat,camLng,delta);
            if(delta[0]<3d)return;
        }
        CameraPosition pos=new CameraPosition.Builder().target(new LatLng(camLat,camLng))
                .zoom(zoom).tilt(57d).bearing(bearing).build();
        if(force)map.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        else map.animateCamera(CameraUpdateFactory.newCameraPosition(pos),650);
        lastCameraLat=camLat;lastCameraLng=camLng;lastCameraMs=now;
    }
}
