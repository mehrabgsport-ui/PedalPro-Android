package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.carto.styles.LineStyleBuilder;
import com.carto.styles.MarkerStyleBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.neshan.common.model.LatLng;
import org.neshan.mapsdk.MapView;
import org.neshan.mapsdk.model.Marker;
import org.neshan.mapsdk.model.Polyline;
import org.neshan.mapsdk.style.NeshanMapStyle;

import java.util.ArrayList;
import java.util.List;

/** Compiled ONLY when pedalproNeshanAndroidSdk=true with a valid neshan.licence. */
public final class LicensedNeshanMapBridge implements NeshanMapBridge {
    private final MapView map;
    private final com.carto.styles.LineStyle recordedStyle;
    private final com.carto.styles.LineStyle suggestedStyle;
    private Polyline recordedLine, suggestedLine;
    private Marker userMarker, goalMarker;
    private double userLat=Double.NaN,userLng=Double.NaN;
    private double targetLat=Double.NaN,targetLng=Double.NaN;
    private double cameraLat=Double.NaN,cameraLng=Double.NaN;
    private boolean follow=true, headingUp=true, courseValid;
    private float heading;
    private int zoom=17;
    private boolean tilted;
    private float appliedBearing=Float.NaN;
    private long lastCameraMs, lastMarkerMs;
    private float touchStartX,touchStartY;
    private boolean fingerDown;

    public LicensedNeshanMapBridge(Context context) {
        map=new MapView(context);
        map.setMapStyle(NeshanMapStyle.NESHAN);
        map.setTrafficEnabled(false); // Do not download extra traffic layers during cycling.
        map.getSettings().setZoomControlsEnabled(false);
        map.setZoom(zoom,0f);
        recordedStyle=lineStyle((short)23,(short)213,(short)232,(short)225,8f);
        suggestedStyle=lineStyle((short)255,(short)201,(short)55,(short)235,10f);
        // Do not block native map gestures, and suspend follow after a deliberate pan.
        map.setOnTouchListener((View view,MotionEvent e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                fingerDown=true;touchStartX=e.getX();touchStartY=e.getY();
            } else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&fingerDown&&e.getPointerCount()==1) {
                if(Math.hypot(e.getX()-touchStartX,e.getY()-touchStartY)>24f)
                    follow=false;
            } else if(e.getActionMasked()==MotionEvent.ACTION_UP||
                    e.getActionMasked()==MotionEvent.ACTION_CANCEL)fingerDown=false;
            return false;
        });
    }

    @Override public View view(){return map;}

    private static com.carto.styles.LineStyle lineStyle(short r,short g,short b,short a,float width) {
        LineStyleBuilder builder=new LineStyleBuilder();
        builder.setColor(new com.carto.graphics.Color(r,g,b,a));
        builder.setWidth(width);
        builder.setStretchFactor(0f);
        return builder.buildStyle();
    }

    private ArrayList<LatLng> coords(JSONArray rows){
        ArrayList<LatLng> out=new ArrayList<>();
        if(rows==null)return out;
        // Geometry is display-only. Keep native SDK layers bounded under long rides.
        int stride=Math.max(1,rows.length()/4500);
        for(int i=0;i<rows.length();i+=stride){
            JSONObject p=rows.optJSONObject(i);
            if(p==null)continue;
            double lat=p.optDouble("lat",Double.NaN),lng=p.optDouble("lng",Double.NaN);
            if(Double.isFinite(lat)&&Double.isFinite(lng)&&Math.abs(lat)<=85&&Math.abs(lng)<=180)
                out.add(new LatLng(lat,lng));
        }
        return out;
    }

    @Override public void setRecordedTrack(JSONArray rows){
        if(recordedLine!=null){map.removePolyline(recordedLine);recordedLine=null;}
        ArrayList<LatLng> coords=coords(rows);
        if(coords.size()>=2){
            recordedLine=new Polyline(coords,recordedStyle);
            map.addPolyline(recordedLine);
        }
        if(!Double.isFinite(userLat)&&!coords.isEmpty()){
            LatLng first=coords.get(coords.size()-1);
            // Native map camera is initially seeded from the actual track.
            setCurrent(first.getLatitude(),first.getLongitude());
        }
    }

    @Override public void setSuggestedRoute(JSONArray rows){
        if(suggestedLine!=null){map.removePolyline(suggestedLine);suggestedLine=null;}
        ArrayList<LatLng> coords=coords(rows);
        if(coords.size()>=2){
            suggestedLine=new Polyline(coords,suggestedStyle);
            map.addPolyline(suggestedLine);
        }
    }

    private static Bitmap markerBitmap(boolean target){
        Bitmap bmp=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bmp);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF092B43);c.drawCircle(32,32,24,p);
        p.setColor(target?0xFFFFC840:0xFF10DFF2);c.drawCircle(32,32,18,p);
        p.setColor(0xFFFFFFFF);
        Path arrow=new Path();
        arrow.moveTo(32,11);arrow.lineTo(20,42);arrow.lineTo(32,35);
        arrow.lineTo(44,42);arrow.close();c.drawPath(arrow,p);
        return bmp;
    }

    private Marker newMarker(double lat,double lng,boolean target){
        MarkerStyleBuilder builder=new MarkerStyleBuilder();
        builder.setSize(target?24f:30f);
        builder.setBitmap(com.carto.utils.BitmapUtils.createBitmapFromAndroidBitmap(
                markerBitmap(target)));
        return new Marker(new LatLng(lat,lng),builder.buildStyle());
    }

    @Override public void setCurrent(double lat,double lng){
        if(!Double.isFinite(lat)||!Double.isFinite(lng)||Math.abs(lat)>85||Math.abs(lng)>180)return;
        float moved=Float.POSITIVE_INFINITY;
        if(Double.isFinite(userLat)){
            float[] result=new float[1];
            Location.distanceBetween(userLat,userLng,lat,lng,result);
            moved=result[0];
        }
        userLat=lat;userLng=lng;
        long now=SystemClock.elapsedRealtime();
        if(moved>=2.5f && now-lastMarkerMs>350L){
            if(userMarker!=null)map.removeMarker(userMarker);
            userMarker=newMarker(lat,lng,false);
            map.addMarker(userMarker);
            lastMarkerMs=now;
        }
        if(follow&&(moved>=3f||!Double.isFinite(cameraLat)))
            updateCamera(false);
    }

    @Override public void setNavigation(boolean enabled,boolean headingUp,float courseDegrees,boolean valid){
        this.follow=enabled;this.headingUp=headingUp;
        this.courseValid=valid&&Float.isFinite(courseDegrees);
        if(courseValid)this.heading=courseDegrees;
        updateCamera(true);
    }

    private void updateCamera(boolean forced){
        if(follow&&!Double.isFinite(userLat))return;
        boolean needsPitch=follow;
        if(needsPitch!=tilted||forced){
            // Neshan official SDK: 90 degrees overhead, approx 55 degrees pitched.
            map.setTilt(needsPitch?55f:90f,.35f);
            tilted=needsPitch;
        }
        float desired=follow&&headingUp&&courseValid?heading:0f;
        if(!Float.isFinite(appliedBearing)||
           Math.abs(((desired-appliedBearing+540f)%360f)-180f)>2f){
            map.setBearing(desired,.35f);
            appliedBearing=desired;
        }
        if(!follow)return;
        long now=SystemClock.elapsedRealtime();
        if(!forced&&now-lastCameraMs<350L)return;
        double lat=userLat,lng=userLng;
        // Forward look-ahead (~40 m) moves the rider into the lower viewport.
        if(headingUp&&courseValid){
            double ahead=40d;
            lat+=ahead*Math.cos(Math.toRadians(heading))/111320d;
            lng+=ahead*Math.sin(Math.toRadians(heading))/
                    (111320d*Math.max(.1,Math.cos(Math.toRadians(userLat))));
        }
        if(forced||!Double.isFinite(cameraLat)||
                Math.abs(lat-cameraLat)+Math.abs(lng-cameraLng)>.000025){
            map.moveCamera(new LatLng(lat,lng),.35f);
            cameraLat=lat;cameraLng=lng;
            lastCameraMs=now;
        }
    }

    @Override public void zoomBy(int step){
        zoom=Math.max(5,Math.min(19,zoom+step));
        map.setZoom(zoom,.25f);
    }

    @Override public void recenter(){
        follow=true;
        updateCamera(true);
    }

    @Override public void setNight(boolean enabled){
        map.setMapStyle(enabled?NeshanMapStyle.NESHAN_NIGHT:NeshanMapStyle.NESHAN);
    }

    @Override public void setTraffic(boolean enabled){map.setTrafficEnabled(enabled);}

    @Override public void setTarget(double lat,double lng){
        if(!Double.isFinite(lat)||!Double.isFinite(lng))return;
        if(Double.isFinite(targetLat)){
            float[] distance=new float[1];
            Location.distanceBetween(targetLat,targetLng,lat,lng,distance);
            if(distance[0]<5f)return;
        }
        targetLat=lat;targetLng=lng;
        if(goalMarker!=null)map.removeMarker(goalMarker);
        goalMarker=newMarker(lat,lng,true);
        map.addMarker(goalMarker);
    }

    @Override public void clearTarget(){
        if(goalMarker!=null){map.removeMarker(goalMarker);goalMarker=null;}
        targetLat=Double.NaN;targetLng=Double.NaN;
    }
}
