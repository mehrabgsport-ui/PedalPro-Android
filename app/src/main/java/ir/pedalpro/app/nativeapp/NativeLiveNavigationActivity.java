package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NativeLiveNavigationActivity extends NativeScreenBase {
    public static final String EXTRA_MODE="mode";
    public static final String EXTRA_RIDE_ID="ride_id";
    public static final String EXTRA_TITLE="title";

    private String mode="live";
    private int rideId=0;
    private String targetName="رکاب‌زن";
    private long lastPointId=0;
    private double targetLat=Double.NaN,targetLng=Double.NaN;
    private double currentLat=Double.NaN,currentLng=Double.NaN;
    private PedalNeshanMapSurface routeView;
    private TextView distance,bearing,status;
    private PedalIconView arrow;
    private boolean busy=false;

    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(!isFinishing())refresh();
            main.postDelayed(this,3000);
        }
    };

    @Override protected String screenTitle(){return "مسیریابی زنده";}

    @Override protected void onScreenReady(){
        mode=getIntent().getStringExtra(EXTRA_MODE);
        if(mode==null)mode="live";
        rideId=getIntent().getIntExtra(EXTRA_RIDE_ID,0);
        targetName=getIntent().getStringExtra(EXTRA_TITLE);
        if(targetName==null||targetName.isBlank())targetName="هدف زنده";

        setSubtitle("live".equals(mode)?"تا "+targetName:"تا گروه در حال حرکت",GREEN);

        LinearLayout hud=card();
        arrow=new PedalIconView(this,PedalIconView.Icon.HEADING,ORANGE);
        arrow.setContentDescription("جهت هدف زنده");
        distance=text("— km",28,true,TEXT);distance.setGravity(android.view.Gravity.CENTER);
        bearing=text("جهت: —",13,false,MUTED);bearing.setGravity(android.view.Gravity.CENTER);
        status=text("در حال دریافت موقعیت…",12,false,MUTED);status.setGravity(android.view.Gravity.CENTER);
        hud.addView(arrow,new LinearLayout.LayoutParams(-1,dp(78)));
        hud.addView(distance);hud.addView(bearing);hud.addView(status);
        content.addView(hud,cardLp());

        routeView=new PedalNeshanMapSurface(this);
        content.addView(routeView,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(360)));

        addCard("راهنما","خط زرد فاصله مستقیم شما تا موقعیت زنده هدف را نشان می‌دهد. موقعیت هدف هر ۳ ثانیه بروزرسانی می‌شود.");
        main.post(tick);
    }

    private void refresh(){
        if(busy)return;
        busy=true;
        updateCurrentLocation();
        io.execute(()->{
            try{
                if("group".equals(mode)){
                    JSONObject d=api.get("group_nav_state");
                    JSONObject g=d.optJSONObject("group");
                    if(g==null||!g.optBoolean("has_location",false)){
                        main.post(()->status.setText("موقعیت زنده گروه در دسترس نیست."));
                    }else{
                        double lat=g.optDouble("last_lat",Double.NaN),lng=g.optDouble("last_lng",Double.NaN);
                        String leader=g.optString("leader_name","سرگروه");
                        main.post(()->applyTarget(lat,lng,"گروه · "+leader,null));
                    }
                }else{
                    Map<String,Object> q=new HashMap<>();
                    q.put("ride_id",rideId);
                    q.put("after_id",lastPointId);
                    q.put("limit",180);
                    JSONObject d=api.get("ride_live_points",q);
                    JSONObject r=d.optJSONObject("ride");
                    JSONArray pts=d.optJSONArray("points");
                    long newLast=d.optLong("last_id",lastPointId);
                    if(r==null||!"active".equals(r.optString("status"))){
                        main.post(()->status.setText("انتشار زنده این رکاب‌زن متوقف شده است."));
                    }else{
                        double lat=r.optDouble("last_lat",Double.NaN),lng=r.optDouble("last_lng",Double.NaN);
                        lastPointId=Math.max(lastPointId,newLast);
                        main.post(()->applyTarget(lat,lng,r.optString("owner_name",targetName),pts));
                    }
                }
            }catch(Exception e){
                main.post(()->status.setText("خطای موقت دریافت موقعیت: "+e.getMessage()));
            }finally{busy=false;}
        });
    }

    private void updateCurrentLocation(){
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            status.setText("دسترسی Location لازم است.");
            return;
        }
        CancellationTokenSource cts=new CancellationTokenSource();
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,cts.getToken())
                .addOnSuccessListener(this,loc->{
                    if(loc==null)return;
                    currentLat=loc.getLatitude();currentLng=loc.getLongitude();
                    routeView.setCurrent(currentLat,currentLng);
                    updateHud();
                });
    }

    private void applyTarget(double lat,double lng,String name,JSONArray pts){
        if(!Double.isFinite(lat)||!Double.isFinite(lng)){
            status.setText("هدف هنوز موقعیت GPS معتبر ندارد.");
            return;
        }
        targetLat=lat;targetLng=lng;
        targetName=name;
        if(pts!=null)routeView.appendPoints(pts);
        routeView.setTarget(lat,lng);
        status.setText("هدف زنده: "+name+" · آخرین بروزرسانی همین حالا");
        updateHud();
    }

    private void updateHud(){
        if(!Double.isFinite(currentLat)||!Double.isFinite(targetLat))return;
        float[] r=new float[2];
        Location.distanceBetween(currentLat,currentLng,targetLat,targetLng,r);
        double km=r[0]/1000.0;
        float deg=(r.length>1?r[1]:0);
        distance.setText(String.format(Locale.US,km<1?"%.2f km":"%.1f km",km));
        bearing.setText("جهت جغرافیایی: "+Math.round((deg+360)%360)+"°");
        arrow.setRotation(deg);
        arrow.setContentDescription("جهت جغرافیایی هدف: "+Math.round((deg+360)%360)+" درجه");
    }

    @Override protected void onDestroy(){
        main.removeCallbacks(tick);
        super.onDestroy();
    }
}
