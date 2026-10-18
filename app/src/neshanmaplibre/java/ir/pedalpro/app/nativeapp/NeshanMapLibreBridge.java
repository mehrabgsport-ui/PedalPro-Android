package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
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
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.BackgroundLayer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.layers.PropertyFactory;

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
    private boolean amoled;
    private Style currentStyle;
    private boolean firstCamera=true;
    private float heading;
    private double lat=Double.NaN,lng=Double.NaN,goalLat=Double.NaN,goalLng=Double.NaN;
    private int zoom=17;
    private long lastCameraMs,lastMarkerMs,lastLineMs;
    private double lastCameraLat=Double.NaN,lastCameraLng=Double.NaN;
    private Marker rider,goal;
    private JSONArray nearbyRiders=new JSONArray();
    private final java.util.HashMap<Integer,Marker> otherRiderMarkers=new java.util.HashMap<>();
    private final android.util.SparseArray<org.maplibre.android.annotations.Icon> riderIcons = new android.util.SparseArray<>();
    private int currentRiderDirectionBucket = Integer.MIN_VALUE;
    private Polyline recorded,recordedGlow,recordedHalo,suggested;
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
                currentStyle=style;
                if(amoled)applyAmoledStyle(style);
                // Rebuild annotations because changing a vector style may clear layers.
                // Old annotations may persist across a style reload in MapLibre.
                // Remove the previous rider before recreating the layer to prevent ghosts.
                if(rider!=null){try{map.removeMarker(rider);}catch(Exception ignored){}}
                if(goal!=null){try{map.removeMarker(goal);}catch(Exception ignored){}}
                if(recorded!=null){try{map.removePolyline(recorded);}catch(Exception ignored){}}
                if(recordedGlow!=null){try{map.removePolyline(recordedGlow);}catch(Exception ignored){}}
                if(recordedHalo!=null){try{map.removePolyline(recordedHalo);}catch(Exception ignored){}}
                if(suggested!=null){try{map.removePolyline(suggested);}catch(Exception ignored){}}
                for(Marker marker:otherRiderMarkers.values()){
                    try{map.removeMarker(marker);}catch(Exception ignored){}
                }
                otherRiderMarkers.clear();
                rider=null;goal=null;recorded=null;recordedGlow=null;recordedHalo=null;suggested=null;
                currentRiderDirectionBucket=Integer.MIN_VALUE;
                applyGeometry();
                updateRiderMarker(true);
                updateTargetMarker();
                updateNearbyRiderMarkers();
                updateCamera(true);
            });
    }

    @Override public void setAmoled(boolean enabled){
        if(amoled==enabled)return;
        amoled=enabled;
        if(!ready||destroyed)return;
        if(enabled){
            applyAmoledStyle(currentStyle);
            scheduleGeometry(); // change display layers, never the persisted GPS track
        } else { ready=false;setNight(night); }
    }
    private void applyAmoledStyle(Style style){
        if(style==null)return;
        // Recolor vector layers: black land/water, bright roads, muted labels.
        // Does not modify Neshan SDK's underlying raster/vector data or GPS.
        for(Layer layer:style.getLayers()){
            String id=layer.getId().toLowerCase(java.util.Locale.ROOT);
            try{
                if(layer instanceof BackgroundLayer){
                    layer.setProperties(PropertyFactory.backgroundColor(Color.BLACK));
                }else if(layer instanceof FillLayer){
                    layer.setProperties(PropertyFactory.fillColor(Color.BLACK));
                }else if(layer instanceof LineLayer){
                    boolean road=id.contains("road")||id.contains("street")
                            ||id.contains("highway")||id.contains("motorway");
                    layer.setProperties(PropertyFactory.lineColor(
                            road?Color.rgb(222,241,247):Color.rgb(24,39,45)));
                }else if(layer instanceof SymbolLayer){
                    layer.setProperties(PropertyFactory.textColor(Color.rgb(138,157,165)),
                            PropertyFactory.textHaloColor(Color.BLACK));
                }
            }catch(RuntimeException ignored){
                // Some provider layers have immutable/unsupported paint expressions.
            }
        }
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
        updateRiderMarker(true);
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


    /**
     * Reference-style 2D overhead rider. The bike wheels share the same vertical
     * axis, and the transparent sprite is centered on the accepted GPS fix.
     */
    private org.maplibre.android.annotations.Icon bikeMapIcon(int bucket){
        org.maplibre.android.annotations.Icon previous=riderIcons.get(bucket);
        if(previous!=null)return previous;
        int side=(int)(68*activity.getResources().getDisplayMetrics().density);
        side=Math.max(68,Math.min(side,192));
        Bitmap bitmap=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Drawable drawable=ContextCompat.getDrawable(activity,ir.pedalpro.app.R.drawable.pedal_cyclist_topdown);
        if(drawable==null)throw new IllegalStateException("Cyclist marker drawable missing");
        drawable=drawable.mutate();
        drawable.setBounds(Math.round(side*.24f),Math.round(side*.09f),
                Math.round(side*.76f),Math.round(side*.91f));
        canvas.save();
        canvas.rotate(bucket*10f,side/2f,side/2f);
        drawable.draw(canvas);
        canvas.restore();
        org.maplibre.android.annotations.Icon icon=IconFactory.getInstance(activity).fromBitmap(bitmap);
        riderIcons.put(bucket,icon);
        return icon;
    }

    private void updateRiderMarker(boolean force){
        if(!ready||map==null||!Double.isFinite(lat))return;
        long now=SystemClock.elapsedRealtime();
        if(!force&&now-lastMarkerMs<450)return;
        double mapBearing=map.getCameraPosition()==null?0:map.getCameraPosition().bearing;
        double rotation=validHeading?heading-mapBearing:0d;
        int bucket=((int)Math.round(rotation/10.0)%36+36)%36;
        org.maplibre.android.annotations.Icon icon=bikeMapIcon(bucket);
        if(rider==null)rider=map.addMarker(new MarkerOptions()
                .position(new LatLng(lat,lng)).icon(icon).title("رکاب‌زن"));
        else {
            rider.setPosition(new LatLng(lat,lng));
            if(bucket!=currentRiderDirectionBucket)rider.setIcon(icon);
            map.updateMarker(rider);
        }
        currentRiderDirectionBucket=bucket;
        lastMarkerMs=now;
    }

    @Override public void setNearbyRiders(JSONArray riders){
        nearbyRiders=riders==null?new JSONArray():riders;
        updateNearbyRiderMarkers();
    }
    private void updateNearbyRiderMarkers(){
        if(!ready||map==null)return;
        java.util.HashSet<Integer> found=new java.util.HashSet<>();
        for(int i=0;i<nearbyRiders.length();i++){
            JSONObject p=nearbyRiders.optJSONObject(i);if(p==null)continue;
            int id=p.optInt("ride_id",0);
            double a=p.optDouble("lat",Double.NaN),b=p.optDouble("lng",Double.NaN);
            if(id<=0||!Double.isFinite(a)||!Double.isFinite(b)||Math.abs(a)>85||Math.abs(b)>180
                    ||p.optInt("age_seconds",99)>45)continue;
            found.add(id);
            String title=p.optString("display_name","رکاب‌زن")+" · "+
                    String.format(java.util.Locale.US,"%.1f km/h · %d ثانیه قبل",
                            p.optDouble("speed_kmh",0d),p.optInt("age_seconds",0));
            Marker marker=otherRiderMarkers.get(id);
            if(marker==null){
                marker=map.addMarker(new MarkerOptions().position(new LatLng(a,b))
                        .title(title).icon(bikeMapIcon(0)));
                otherRiderMarkers.put(id,marker);
            }else {
                marker.setPosition(new LatLng(a,b));
                marker.setTitle(title);
                map.updateMarker(marker);
            }
        }
        java.util.Iterator<java.util.Map.Entry<Integer,Marker>> it=otherRiderMarkers.entrySet().iterator();
        while(it.hasNext()){
            java.util.Map.Entry<Integer,Marker> e=it.next();
            if(found.contains(e.getKey()))continue;
            try{map.removeMarker(e.getValue());}catch(Exception ignored){}
            it.remove();
        }
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
    /** Presentation-only suppression of obvious old GPS rebounds; server history unchanged. */
    private static List<LatLng> cleanRecordedGeometry(List<LatLng> original){
        if(original.size()<3)return original;
        ArrayList<LatLng> keep=new ArrayList<>();
        for(int i=0;i<original.size();i++){
            LatLng here=original.get(i);
            if(!keep.isEmpty()){
                LatLng previous=keep.get(keep.size()-1);
                float[] move=new float[1];
                Location.distanceBetween(previous.getLatitude(),previous.getLongitude(),
                        here.getLatitude(),here.getLongitude(),move);
                if(move[0]<4f)continue;
                if(i+1<original.size()){
                    LatLng next=original.get(i+1);
                    float[] forward=new float[1],direct=new float[1];
                    Location.distanceBetween(here.getLatitude(),here.getLongitude(),
                            next.getLatitude(),next.getLongitude(),forward);
                    Location.distanceBetween(previous.getLatitude(),previous.getLongitude(),
                            next.getLatitude(),next.getLongitude(),direct);
                    // Out-and-back GPS bounces are not plausible path corners.
                    if(move[0]>22f && forward[0]>22f &&
                            direct[0] < Math.max(14f,Math.min(move[0],forward[0])*.40f))
                        continue;
                }
            }
            keep.add(here);
        }
        return keep;
    }

    private void applyGeometry(){
        if(destroyed||!ready||map==null)return;
        if(recorded!=null){map.removePolyline(recorded);recorded=null;}
        if(recordedGlow!=null){map.removePolyline(recordedGlow);recordedGlow=null;}
        if(recordedHalo!=null){map.removePolyline(recordedHalo);recordedHalo=null;}
        if(suggested!=null){map.removePolyline(suggested);suggested=null;}
        List<LatLng> history=cleanRecordedGeometry(parse(recordedPoints)),route=parse(suggestedPoints);
        if(history.size()>1){
            if(amoled){
                // Three translucent native strokes approximate a neon bloom without
                // blurring the map or changing stored GPS geometry.
                recordedGlow=map.addPolyline(new PolylineOptions()
                        .addAll(history).color(Color.argb(54,18,225,252)).width(22f));
                recordedHalo=map.addPolyline(new PolylineOptions()
                        .addAll(history).color(Color.argb(118,0,231,255)).width(13f));
            }
            recorded=map.addPolyline(new PolylineOptions()
                    .addAll(history).color(amoled?Color.rgb(87,247,255):Color.rgb(28,215,230))
                    .width(amoled?7.5f:5f));
            if(firstCamera&&!navigation){
                LatLngBounds.Builder bounds=new LatLngBounds.Builder();
                for(LatLng point:history)bounds.include(point);
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),44));
                firstCamera=false;
            }
        }
        if(route.size()>1)suggested=map.addPolyline(new PolylineOptions()
                .addAll(route).color(Color.rgb(13,201,219)).width(9f));
        lastLineMs=SystemClock.elapsedRealtime();
    }

    @Override public void focusOn(double latitude,double longitude){
        if(!ready||map==null||!Double.isFinite(latitude)||!Double.isFinite(longitude))return;
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude,longitude),
                Math.max(13,Math.min(zoom,17))),250);
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
