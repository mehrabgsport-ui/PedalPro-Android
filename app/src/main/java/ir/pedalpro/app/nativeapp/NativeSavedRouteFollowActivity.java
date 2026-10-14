package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
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
import ir.pedalpro.app.TrackingService;

/** Reuses the recorded GPS polyline; never writes or duplicates tracking points. */
public final class NativeSavedRouteFollowActivity extends NativeScreenBase {
    public static final String EXTRA_RIDE_ID="ride_id";
    private static final int PHASE_TO_TARGET=0, PHASE_FOLLOW=1;
    private int rideId,phase=PHASE_TO_TARGET;
    private boolean toBeginning=true,visible,arrived,startingTracking;
    private JSONArray recorded=new JSONArray();
    private PedalNeshanMapSurface mapView;
    private TextView status;
    private double goalLat=Double.NaN,goalLng=Double.NaN;
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(!visible||isFinishing())return;
            updateLocation();
            main.postDelayed(this,8000L);
        }
    };

    @Override protected String screenTitle(){return "دنبال‌کردن مسیر GPS";}
    @Override protected void onScreenReady(){
        rideId=getIntent().getIntExtra(EXTRA_RIDE_ID,0);
        if(rideId<=0){addCard("خطا","شناسه مسیر نامعتبر است.");return;}
        LinearLayout intro=card();
        intro.addView(text("دو مرحله: رسیدن به مسیر، سپس دنبال‌کردن خط GPS اصلی",15,true,TEXT));
        intro.addView(text("مسیریاب نشان مقصد انتخابی را باز می‌کند. ثبت رکاب‌زنی در PedalPro فقط در صورت فعال بودن رکاب‌زنی GPS ادامه دارد.",11,false,MUTED));
        content.addView(intro,cardLp());

        mapView=new PedalNeshanMapSurface(this);
        content.addView(mapView,new LinearLayout.LayoutParams(-1,dp(440)));
        status=text("در حال دریافت نقاط مسیر…",13,true,TEXT);
        LinearLayout card=card();card.addView(status);
        Button choose=primary("انتخاب ابتدا یا انتهای مسیر");
        choose.setOnClickListener(v->chooseDestination());
        card.addView(choose);
        Button external=normal("بازکردن مقصد انتخابی در نشان");
        external.setOnClickListener(v->openNeshanDestination());
        card.addView(external);
        Button follow=normal("دنبال‌کردن مسیر اصلی در PedalPro");
        follow.setOnClickListener(v->startFollow());
        card.addView(follow);
        Button startGps=primary("شروع / ادامه ثبت رکاب‌زنی GPS");
        startGps.setOnClickListener(v->beginTrackingThen(()->
                status.setText("ثبت GPS فعال است؛ برای مسیریابی مقصد را انتخاب کنید.")));
        card.addView(startGps);
        Button backToRide=normal("رفتن به ثبت رکاب‌زنی");
        backToRide.setOnClickListener(v->{
            Intent i=new Intent(this,NativeMainActivity.class);
            i.putExtra("page","ride");
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        });
        card.addView(backToRide);
        content.addView(card,cardLp());
        loadTrack();
    }
    private void loadTrack(){
        status.setText("در حال دریافت نقاط مسیر ثبت‌شده…");
        io.execute(()->{
            try{
                Map<String,Object> q=new HashMap<>();
                q.put("ride_id",rideId);q.put("point_mode","nav");
                JSONObject payload=api.get("ride_details",q);
                JSONObject ride=payload.optJSONObject("ride");
                JSONArray points=ride==null?null:ride.optJSONArray("points");
                if(points==null||points.length()<2)
                    throw new IllegalStateException("این رکاب‌زنی نقاط معتبر برای دنبال‌کردن ندارد.");
                main.post(()->{
                    recorded=points;
                    mapView.setPoints(recorded);
                    status.setText("مسیر دریافت شد ("+recorded.length()+" نقطه). مقصد را انتخاب کنید.");
                    chooseDestination();
                });
            }catch(Exception e){main.post(()->status.setText(
                    "خطای دریافت مسیر: "+(e.getMessage()==null?"نامشخص":e.getMessage())));}
        });
    }
    private JSONObject endpoint(boolean first){
        if(recorded==null)return null;
        int i=first?0:recorded.length()-1, step=first?1:-1;
        while(i>=0&&i<recorded.length()){
            JSONObject p=recorded.optJSONObject(i);
            if(p!=null){
                double la=p.optDouble("lat",Double.NaN),lo=p.optDouble("lng",Double.NaN);
                if(Double.isFinite(la)&&Double.isFinite(lo)&&Math.abs(la)<85&&Math.abs(lo)<180)
                    return p;
            }
            i+=step;
        }
        return null;
    }
    private void chooseDestination(){
        if(recorded.length()<2){status.setText("ابتدا باید مسیر GPS بارگذاری شود.");return;}
        new AlertDialog.Builder(this).setTitle("می‌خواهید به کدام نقطه بروید؟")
                .setItems(new String[]{"ابتدای مسیر","انتهای مسیر"},(d,choice)->{
                    toBeginning=choice==0;
                    JSONObject point=endpoint(toBeginning);
                    if(point==null){status.setText("نقطه مقصد معتبر نیست.");return;}
                    goalLat=point.optDouble("lat",Double.NaN);
                    goalLng=point.optDouble("lng",Double.NaN);
                    phase=PHASE_TO_TARGET;arrived=false;
                    mapView.setNavigationMode(false);
                    mapView.setTarget(goalLat,goalLng);
                    status.setText("مقصد: "+(toBeginning?"ابتدای":"انتهای")+
                            " مسیر. نشان را باز کنید و از داخل نشان مسیریابی را شروع کنید.");
                }).show();
    }
    private boolean recording(){
        return getSharedPreferences(TrackingService.PREFS,MODE_PRIVATE)
                .getBoolean("active",false);
    }
    private void ensureTrackingNotice(){
        if(!recording())new AlertDialog.Builder(this)
            .setTitle("ثبت رکاب‌زنی فعال نیست")
            .setMessage("برای ذخیره مسیر طی‌شده ابتدا رکاب‌زنی GPS را در PedalPro شروع کنید. بازکردن نشان به‌تنهایی رکاب‌زنی را ثبت نمی‌کند.")
            .setPositiveButton("متوجه شدم",null).show();
    }
    private void openNeshanDestination(){
        if(!Double.isFinite(goalLat)){chooseDestination();return;}
        if(!recording()){
            new AlertDialog.Builder(this)
                .setTitle("ثبت رکاب‌زنی هنوز شروع نشده")
                .setMessage("برای اینکه مسیر تا نشان و ادامه حرکت در PedalPro ثبت شود، ابتدا GPS را فعال کنید. ثبت مسیر مستقل از باز بودن نشان در پس‌زمینه ادامه پیدا می‌کند.")
                .setPositiveButton("شروع GPS و بازکردن نشان",(dialog,which)->
                        beginTrackingThen(this::launchNeshanDestination))
                .setNeutralButton("فقط نمایش نشان، بدون ثبت",(dialog,which)->
                        launchNeshanDestination())
                .setNegativeButton("انصراف",null).show();
            return;
        }
        launchNeshanDestination();
    }

    private void launchNeshanDestination(){
        // The official point link selects the destination in Neshan; starting
        // turn-by-turn is controlled by its own app. Arbitrary GPX import is
        // NOT assumed to be supported by Neshan.
        try{
            Uri uri=Uri.parse("https://nshn.ir?lat="+
                    String.format(Locale.US,"%.7f",goalLat)+"&lng="+
                    String.format(Locale.US,"%.7f",goalLng));
            Intent i=new Intent(Intent.ACTION_VIEW,uri);
            i.setPackage("org.rajman.neshan.traffic.tehran.navigator");
            startActivity(i);
        }catch(Exception e){
            try{
                Uri geo=Uri.parse("geo:"+goalLat+","+goalLng+"?q="+goalLat+","+goalLng);
                startActivity(new Intent(Intent.ACTION_VIEW,geo));
            }catch(Exception missing){status.setText("مسیریاب نشان روی دستگاه در دسترس نیست.");}
        }
    }
    private void startFollow(){
        if(recorded.length()<2)return;
        if(!recording()){
            new AlertDialog.Builder(this).setTitle("ثبت مسیر GPS")
                    .setMessage("برای ثبت رکاب‌زنی هنگام دنبال‌کردن مسیر اصلی، GPS را فعال کنید.")
                    .setPositiveButton("شروع GPS و ادامه",(d,w)->
                            beginTrackingThen(this::activateFollowing))
                    .setNeutralButton("مشاهده بدون ثبت",(d,w)->activateFollowing())
                    .setNegativeButton("انصراف",null).show();
            return;
        }
        activateFollowing();
    }

    private void activateFollowing(){
        phase=PHASE_FOLLOW;arrived=true;
        mapView.clearTarget();
        mapView.setPoints(recorded);
        mapView.setNavigationMode(true);
        status.setText(recording()?"دنبال‌کردن مسیر اصلی و ثبت GPS فعال است.":
                "دنبال‌کردن مسیر فعال است؛ GPS ثبت نمی‌شود.");
    }

    /**
     * The existing site start_ride action and TrackingService are reused.
     * Do not create a second ride if the authenticated server already has one.
     * No distance/calorie/filter/route-writing rules are modified.
     */
    private void beginTrackingThen(Runnable after){
        if(startingTracking)return;
        if(recording()){
            after.run();
            return;
        }
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED){
            status.setText("برای ثبت رکاب‌زنی اجازه Location دقیق لازم است.");
            return;
        }
        startingTracking=true;
        status.setText("در حال دریافت GPS دقیق برای آغاز رکاب‌زنی…");
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(this,fix->{
                    long age=fix==null?Long.MAX_VALUE:
                            Math.max(0L,System.currentTimeMillis()-fix.getTime());
                    if(fix==null||age>12000||!fix.hasAccuracy()||fix.getAccuracy()>35f){
                        startingTracking=false;
                        status.setText("شروع GPS نیازمند موقعیت تازه با دقت ۳۵ متر یا بهتر است.");
                        return;
                    }
                    io.execute(()->{
                        try{
                            JSONObject b=api.bootstrap();
                            JSONObject active=b.optJSONObject("my_active_ride");
                            int id=active==null?0:active.optInt("id",0);
                            if(id<=0){
                                JSONObject created=api.post("start_ride",new JSONObject()
                                        .put("title","دنبال‌کردن مسیر GPS")
                                        .put("event_id",0).put("challenge_id",0)
                                        .put("bike_id",0).put("public_tracking",false)
                                        .put("viewer_user_ids",new JSONArray()));
                                id=created.optInt("ride_id",0);
                            }
                            if(id<=0)throw new IllegalStateException("شناسه رکاب‌زنی دریافت نشد.");
                            Intent service=new Intent(this,TrackingService.class);
                            service.setAction(TrackingService.ACTION_START);
                            service.putExtra("ride_id",id);
                            service.putExtra("csrf",session.csrf());
                            service.putExtra("max_accuracy",25d);
                            service.putExtra("max_speed_kmh",100d);
                            if(android.os.Build.VERSION.SDK_INT>=26)
                                androidx.core.content.ContextCompat.startForegroundService(this,service);
                            else startService(service);
                            main.post(()->{
                                startingTracking=false;
                                if(isFinishing())return;
                                status.setText("ثبت GPS آغاز شد و در پس‌زمینه ادامه دارد.");
                                after.run();
                            });
                        }catch(Exception ex){
                            main.post(()->{
                                startingTracking=false;
                                status.setText("شروع ثبت رکاب‌زنی ممکن نشد: "+
                                        (ex.getMessage()==null?"خطای سرور":ex.getMessage()));
                            });
                        }
                    });
                }).addOnFailureListener(this,e->{
                    startingTracking=false;
                    status.setText("موقعیت GPS دریافت نشد؛ ثبت رکاب‌زنی شروع نشده است.");
                });
    }
    private void updateLocation(){
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED){
            status.setText("اجازه Location دقیق برای دنبال‌کردن مسیر لازم است.");
            return;
        }
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,new CancellationTokenSource().getToken())
            .addOnSuccessListener(this,loc->{
                if(loc==null)return;
                mapView.setCurrent(loc.getLatitude(),loc.getLongitude());
                if(loc.hasSpeed()&&loc.hasBearing()&&loc.getSpeed()>1.3f)
                    mapView.setHeadingDegrees(loc.getBearing());
                if(phase!=PHASE_TO_TARGET||!Double.isFinite(goalLat))return;
                float[] dist=new float[1];
                Location.distanceBetween(loc.getLatitude(),loc.getLongitude(),goalLat,goalLng,dist);
                status.setText(String.format(Locale.US,"تا %s مسیر %.0f متر · GPS ±%.0f متر",
                        toBeginning?"ابتدا":"انتها",dist[0],loc.hasAccuracy()?loc.getAccuracy():999f));
                if(arrived||!loc.hasAccuracy()||loc.getAccuracy()>30f||
                    dist[0]>Math.max(35f,loc.getAccuracy()*1.5f))return;
                arrived=true;
                if(toBeginning){
                    new AlertDialog.Builder(this).setTitle("به ابتدای مسیر رسیدی")
                        .setMessage("اکنون مسیر GPS رکاب‌زن قبلی روی نقشه نمایش داده می‌شود.")
                        .setPositiveButton("دنبال‌کردن مسیر", (d,w)->startFollow())
                        .setNegativeButton("بعداً",null).show();
                } else status.setText("به انتهای مسیر رسیدی.");
            });
    }
    @Override protected void onResume(){
        super.onResume();visible=true;
        main.removeCallbacks(tick);main.post(tick);
    }
    @Override protected void onPause(){
        visible=false;main.removeCallbacks(tick);super.onPause();
    }
}
