package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.location.Location;
import android.os.SystemClock;
import android.os.Build;
import androidx.core.content.ContextCompat;
import ir.pedalpro.app.TrackingService;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.app.AlertDialog;
import android.widget.Toast;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private boolean groupArrivalNotified;
    private float currentAccuracy=Float.MAX_VALUE;
    private Location recentFix;
    private boolean startingGroupTracking;
    private boolean screenVisible;
    private TextView roadStatus;
    private Button roadToggle;
    private boolean roadEnabled;
    private boolean directionInFlight;
    private long lastDirectionAttempt;
    private double lastRouteFromLat=Double.NaN,lastRouteFromLng=Double.NaN;
    private double lastRouteToLat=Double.NaN,lastRouteToLng=Double.NaN;
    private final ExecutorService directionsIo=Executors.newSingleThreadExecutor();

    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(isFinishing()||!screenVisible)return;
            refresh();
            main.postDelayed(this,"group".equals(mode)?10000L:3000L);
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
        routeView.setNavigationMode(true);
        content.addView(routeView,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(360)));
        if("group".equals(mode)){
            Button cancel=normal("لغو رسیدن به گروه · ادامه رکاب‌زنی عادی");
            cancel.setOnClickListener(v->{
                roadEnabled=false;
                routeView.clearNavigationRoute();
                Intent ride=new Intent(this,NativeMainActivity.class);
                ride.putExtra("page","ride");
                ride.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(ride);
                finish();
            });
            content.addView(cancel,cardLp());
            Button tracking=primary("شروع / بررسی ثبت GPS هنگام رسیدن به گروه");
            tracking.setOnClickListener(v->beginGroupRideIfNeeded(tracking));
            content.addView(tracking,cardLp());
            addCard("ثبت رکاب‌زنی","اگر رکاب‌زنی فعال داشته باشید همان رکورد ادامه می‌یابد. در غیر این صورت، با دکمه بالا ثبت GPS جدید شروع می‌شود؛ خروج از مسیریابی آن را متوقف نمی‌کند.");
        }

        LinearLayout routing=card();
        routing.addView(text("مسیریابی خیابانی نشان",17,true,TEXT));
        roadStatus=text("مسیر خیابانی پیشنهاد خودرو است؛ برای دوچرخه از نظر عبورپذیری و زمان سفر تأیید نشده.",12,false,MUTED);
        roadStatus.setPadding(0,dp(8),0,dp(8));
        routing.addView(roadStatus);
        roadToggle=normal("فعال‌سازی مسیر خیابانی");
        roadToggle.setOnClickListener(v->{
            roadEnabled=!roadEnabled;
            getSharedPreferences(NativeAppSettingsActivity.PREFS,MODE_PRIVATE).edit()
                    .putBoolean(NativeAppSettingsActivity.PREF_PREFER_ROADS,roadEnabled).apply();
            roadToggle.setText(roadEnabled?"غیرفعال‌سازی مسیر خیابانی":"فعال‌سازی مسیر خیابانی");
            if(!roadEnabled){
                routeView.clearNavigationRoute();
                invalidateDirectionCache();
                roadStatus.setText("نمایش فاصله مستقیم؛ مسیر خیابانی خاموش است.");
            } else {
                invalidateDirectionCache();
                maybeFetchDirections();
            }
        });
        routing.addView(roadToggle);
        // Admin map credentials live only behind the account-adjacent settings gear.
        content.addView(routing,cardLp());
        if(!NeshanServiceCredentials.hasKey(this))
            roadStatus.setText("برای مسیریابی خیابانی، کلید «سرویس‌ها»ی نشان را روی این دستگاه وارد کنید. مسیر مستقیم همچنان فعال است.");
        addCard("راهنما","خط مستقیم موقعیت هدف را نشان می‌دهد. مسیر خیابانی تنها پس از فعال‌کردن و دریافت پاسخ معتبر نشان نمایش داده می‌شود.");
    }

    @Override protected void onResume(){
        super.onResume();
        screenVisible=true;
        boolean preference=getSharedPreferences(NativeAppSettingsActivity.PREFS,MODE_PRIVATE)
                .getBoolean(NativeAppSettingsActivity.PREF_PREFER_ROADS,false);
        if(preference!=roadEnabled){
            roadEnabled=preference;
            invalidateDirectionCache();
            if(!roadEnabled)routeView.clearNavigationRoute();
        }
        if(roadToggle!=null)roadToggle.setText(roadEnabled?
                "غیرفعال‌سازی مسیر خیابانی":"فعال‌سازی مسیر خیابانی");
        if(roadStatus!=null&&!NeshanServiceCredentials.hasKey(this))
            roadStatus.setText("کلید مسیریابی نشان در تنظیمات مدیر ثبت نشده است.");
        main.removeCallbacks(tick);
        main.post(tick);
        if(roadEnabled)maybeFetchDirections();
    }

    @Override protected void onPause(){
        screenVisible=false;
        main.removeCallbacks(tick);
        super.onPause();
    }

    private void invalidateDirectionCache(){
        lastDirectionAttempt=0L;
        lastRouteFromLat=Double.NaN;
        lastRouteToLat=Double.NaN;
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
                        main.post(()->{
                            status.setText("موقعیت زنده گروه در دسترس نیست.");
                            routeView.clearNavigationRoute();
                        });
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
                        main.post(()->{
                            status.setText("انتشار زنده این رکاب‌زن متوقف شده است.");
                            routeView.clearNavigationRoute();
                        });
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
                    currentAccuracy=loc.hasAccuracy()?loc.getAccuracy():Float.MAX_VALUE;
                    recentFix=loc;
                    routeView.setCurrent(currentLat,currentLng);
                    if(loc.hasBearing() && loc.hasSpeed() && loc.getSpeed()>1.3f)
                        routeView.setHeadingDegrees(loc.getBearing());
                    updateHud();
                    maybeFetchDirections();
                });
    }

    private void beginGroupRideIfNeeded(Button button){
        if(startingGroupTracking)return;
        if(getSharedPreferences(TrackingService.PREFS,MODE_PRIVATE).getBoolean("active",false)){
            Toast.makeText(this,"ثبت GPS فعلی فعال است و ادامه خواهد یافت.",Toast.LENGTH_LONG).show();
            return;
        }
        Location fix=recentFix;
        long age=fix==null?Long.MAX_VALUE:
                Math.max(0L,System.currentTimeMillis()-fix.getTime());
        if(fix==null||age>12000L||!fix.hasAccuracy()||fix.getAccuracy()>35f){
            status.setText("برای شروع ثبت رکاب‌زنی، GPS تازه با دقت ±۳۵ متر یا بهتر لازم است.");
            updateCurrentLocation();
            return;
        }
        startingGroupTracking=true;button.setEnabled(false);
        status.setText("در حال شروع رکاب‌زنی برای رسیدن به گروه…");
        io.execute(()->{
            try{
                JSONObject b=api.bootstrap();
                JSONObject active=b.optJSONObject("my_active_ride");
                if(active!=null&&active.optInt("id",0)>0){
                    main.post(()->{
                        startingGroupTracking=false;button.setEnabled(true);
                        status.setText("رکاب‌زنی قبلی شما هنوز فعال است؛ ادامه یا توقف آن را در صفحه رکاب‌زنی مدیریت کنید.");
                    });
                    return;
                }
                JSONObject created=api.post("start_ride",new JSONObject()
                        .put("title","رسیدن به گروه")
                        .put("event_id",0)
                        .put("challenge_id",0)
                        .put("bike_id",0)
                        .put("public_tracking",false)
                        .put("viewer_user_ids",new JSONArray()));
                int id=created.optInt("ride_id",0);
                if(id<=0)throw new IllegalStateException("شناسه معتبر از سرور دریافت نشد");
                try{
                    api.post("track_point",new JSONObject().put("ride_id",id)
                        .put("lat",fix.getLatitude()).put("lng",fix.getLongitude())
                        .put("altitude",fix.hasAltitude()?fix.getAltitude():JSONObject.NULL)
                        .put("speed_kmh",fix.hasSpeed()?fix.getSpeed()*3.6d:0d)
                        .put("accuracy",fix.getAccuracy())
                        .put("timestamp",System.currentTimeMillis()));
                }catch(Exception ignored){}
                Intent start=new Intent(this,TrackingService.class);
                start.setAction(TrackingService.ACTION_START);
                start.putExtra("ride_id",id);
                start.putExtra("csrf",session.csrf());
                start.putExtra("max_accuracy",25d);
                start.putExtra("max_speed_kmh",100d);
                if(Build.VERSION.SDK_INT>=26)ContextCompat.startForegroundService(this,start);
                else startService(start);
                main.post(()->{
                    startingGroupTracking=false;button.setEnabled(true);
                    status.setText("ثبت رکاب‌زنی GPS فعال شد؛ مسیریابی گروهی ادامه دارد.");
                });
            }catch(Exception e){
                main.post(()->{
                    startingGroupTracking=false;button.setEnabled(true);
                    status.setText("شروع GPS انجام نشد: "+e.getMessage());
                });
            }
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
        maybeFetchDirections();
    }

    private void maybeFetchDirections() {
        if(!roadEnabled || directionInFlight || !Double.isFinite(currentLat) ||
                !Double.isFinite(currentLng)||!Double.isFinite(targetLat)||
                !Double.isFinite(targetLng)||isFinishing())return;
        String secret=NeshanServiceCredentials.read(this);
        if(secret.isEmpty()) {
            roadStatus.setText("کلید سرویس نشان در این گوشی تنظیم نشده؛ فاصله مستقیم فعال است.");
            return;
        }
        long now=SystemClock.elapsedRealtime();
        if(now-lastDirectionAttempt<("group".equals(mode)?10_000L:45_000L)
                && lastDirectionAttempt>0)return;
        boolean haveRoute=Double.isFinite(lastRouteFromLat)&&Double.isFinite(lastRouteToLat);
        if(haveRoute) {
            float[] from=new float[1],to=new float[1];
            Location.distanceBetween(lastRouteFromLat,lastRouteFromLng,currentLat,currentLng,from);
            Location.distanceBetween(lastRouteToLat,lastRouteToLng,targetLat,targetLng,to);
            if(from[0]<("group".equals(mode)?25f:80f) &&
               to[0]<("group".equals(mode)?20f:80f))return;
        }
        lastDirectionAttempt=now;
        directionInFlight=true;
        final double fromLat=currentLat,fromLng=currentLng,toLat=targetLat,toLng=targetLng;
        roadStatus.setText("در حال دریافت مسیر خیابانی پیشنهاد خودرو از نشان…");
        directionsIo.execute(()->{
            try {
                NeshanDirectionsClient.Route result=NeshanDirectionsClient.fetch(secret,
                        fromLat,fromLng,toLat,toLng);
                main.post(()->{
                    directionInFlight=false;
                    if(isFinishing()||!roadEnabled)return;
                    float[] drift=new float[1];
                    if(Double.isFinite(targetLat))
                        Location.distanceBetween(toLat,toLng,targetLat,targetLng,drift);
                    if(drift[0]>150f){maybeFetchDirections();return;}
                    routeView.setNavigationRoute(result.points);
                    lastRouteFromLat=fromLat;lastRouteFromLng=fromLng;
                    lastRouteToLat=toLat;lastRouteToLng=toLng;
                    String km=result.distanceMeters>0?
                            String.format(Locale.US,"%.1f کیلومتر",result.distanceMeters/1000.0):
                            "مسیر موجود";
                    String next=result.nextInstruction==null?"":result.nextInstruction.trim();
                    roadStatus.setText("مسیر خیابانی پیشنهادی خودرو: "+km+
                            (next.isEmpty()?"":" · "+next)+
                            " · زمان و عبورپذیری دوچرخه تأیید نشده است.");
                });
            } catch(Exception e) {
                String reason=e.getMessage()==null?"پاسخ نامعتبر از نشان":e.getMessage();
                main.post(()->{
                    directionInFlight=false;
                    if(isFinishing()||!roadEnabled)return;
                    routeView.clearNavigationRoute();
                    roadStatus.setText("نشان: "+reason+" · فاصله مستقیم همچنان در دسترس است.");
                });
            }
        });
    }

    private void updateHud(){
        if(!Double.isFinite(currentLat)||!Double.isFinite(targetLat))return;
        float[] r=new float[2];
        Location.distanceBetween(currentLat,currentLng,targetLat,targetLng,r);
        double km=r[0]/1000.0;
        float deg=(r.length>1?r[1]:0);
        distance.setText(String.format(Locale.US,km<1?"%.2f km":"%.1f km",km));
        if("group".equals(mode)&&!groupArrivalNotified
                && currentAccuracy<=30f&&r[0]<=Math.max(35f,currentAccuracy*1.5f)){
            groupArrivalNotified=true;
            status.setText("به گروه رسیدی؛ رکاب‌زنی شما همچنان ثبت می‌شود.");
            new AlertDialog.Builder(this)
                .setTitle("به گروه رسیدی")
                .setMessage("می‌توانی تا پایان رکاب‌زنی کنار گروه ادامه بدهی؛ مسافت ثبت‌شده از بین نمی‌رود.")
                .setPositiveButton("همراه گروه می‌مانم",(dialog,which)->{})
                .setNegativeButton("لغو رسیدن به گروه",(dialog,which)->{
                    Intent ride=new Intent(this,NativeMainActivity.class);
                    ride.putExtra("page","ride");
                    ride.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(ride);finish();
                }).show();
        }
        bearing.setText("جهت جغرافیایی: "+Math.round((deg+360)%360)+"°");
        arrow.setRotation(deg);
        arrow.setContentDescription("جهت جغرافیایی هدف: "+Math.round((deg+360)%360)+" درجه");
    }

    @Override protected void onDestroy(){
        main.removeCallbacks(tick);
        directionsIo.shutdownNow();
        super.onDestroy();
    }
}
