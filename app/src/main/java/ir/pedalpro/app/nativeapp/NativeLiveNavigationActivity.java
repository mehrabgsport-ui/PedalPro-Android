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
    private JSONArray viewedRiderTrack=new JSONArray();
    private double targetLat=Double.NaN,targetLng=Double.NaN;
    private double currentLat=Double.NaN,currentLng=Double.NaN;
    private PedalNeshanMapSurface routeView;
    private TextView distance,bearing,status;
    private PedalIconView arrow;
    private boolean busy=false;
    private boolean groupArrivalNotified;
    private long arrivalCandidateTime;
    private long arrivalCandidateFixTime;
    private float currentAccuracy=Float.MAX_VALUE;
    private Location recentFix;
    private boolean startingGroupTracking;
    private boolean autoGroupTrackingAttempted;
    private Button groupTrackingButton;
    private boolean screenVisible;
    private long lastNearbyRefresh;
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
            if(SystemClock.elapsedRealtime()-lastNearbyRefresh>=10000L){
                lastNearbyRefresh=SystemClock.elapsedRealtime();
                refreshNearbyRiders();
            }
            main.postDelayed(this,"group".equals(mode)?10000L:3000L);
        }
    };

    @Override protected String screenTitle(){return "مسیریابی زنده";}

    @Override protected void onScreenReady(){
        mode=getIntent().getStringExtra(EXTRA_MODE);
        if(mode==null)mode="live";
        // Reaching a moving group is a navigation request, not just a bearing HUD.
        roadEnabled="group".equals(mode);
        if(roadEnabled)getSharedPreferences(NativeAppSettingsActivity.PREFS,MODE_PRIVATE)
                .edit().putBoolean(NativeAppSettingsActivity.PREF_PREFER_ROADS,true).apply();
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
            groupTrackingButton=primary("بررسی / راه‌اندازی دوباره ثبت GPS");
            groupTrackingButton.setOnClickListener(v->beginGroupRideIfNeeded(groupTrackingButton));
            content.addView(groupTrackingButton,cardLp());
            addCard("ثبت رکاب‌زنی","به‌محض دریافت موقعیت GPS معتبر، ثبت رکاب‌زنی هنگام رسیدن به گروه خودکار شروع می‌شود. اگر رکورد فعالی در سرور وجود داشته باشد همان ادامه می‌یابد. با دکمه بالا نیز می‌توانید وضعیت GPS را بررسی کنید.");
        }

        LinearLayout routing=card();
        routing.addView(text("مسیریابی مخصوص دوچرخه",17,true,TEXT));
        roadStatus=text("مسیر مخصوص دوچرخه از API سرور دریافت می‌شود؛ در صورت نبود سرویس، مسیر خودرو جایگزین نخواهد شد.",12,false,MUTED);
        roadStatus.setPadding(0,dp(8),0,dp(8));
        routing.addView(roadStatus);
        roadToggle=normal("فعال‌سازی مسیر دوچرخه");
        roadToggle.setOnClickListener(v->{
            roadEnabled=!roadEnabled;
            getSharedPreferences(NativeAppSettingsActivity.PREFS,MODE_PRIVATE).edit()
                    .putBoolean(NativeAppSettingsActivity.PREF_PREFER_ROADS,roadEnabled).apply();
            roadToggle.setText(roadEnabled?"غیرفعال‌سازی مسیر دوچرخه":"فعال‌سازی مسیر دوچرخه");
            if(!roadEnabled){
                routeView.clearNavigationRoute();
                invalidateDirectionCache();
                roadStatus.setText("نمایش فاصله مستقیم؛ مسیر دوچرخه خاموش است.");
            } else {
                invalidateDirectionCache();
                maybeFetchDirections();
            }
        });
        routing.addView(roadToggle);
        // Admin map credentials live only behind the account-adjacent settings gear.
        content.addView(routing,cardLp());
        roadStatus.setText(roadEnabled?"در حال دریافت مسیر خیابانی دوچرخه تا مقصد متحرک…":
                "برای مسیریابی دوچرخه، سرویس cycling-regular باید در Backend فعال باشد؛ نقشه نشان حفظ می‌شود.");
        addCard("راهنما","خط مستقیم فقط فاصله را نشان می‌دهد؛ خط ناوبری ویژه دوچرخه پس از پاسخ معتبر Backend ترسیم می‌شود.");
    }

    @Override protected void onResume(){
        super.onResume();
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screenVisible=true;
        boolean preference=getSharedPreferences(NativeAppSettingsActivity.PREFS,MODE_PRIVATE)
                .getBoolean(NativeAppSettingsActivity.PREF_PREFER_ROADS,"group".equals(mode));
        if(preference!=roadEnabled){
            roadEnabled=preference;
            invalidateDirectionCache();
            if(!roadEnabled)routeView.clearNavigationRoute();
        }
        if(roadToggle!=null)roadToggle.setText(roadEnabled?
                "غیرفعال‌سازی مسیر دوچرخه":"فعال‌سازی مسیر دوچرخه");

        main.removeCallbacks(tick);
        main.post(tick);
        if(roadEnabled)maybeFetchDirections();
    }

    @Override protected void onPause(){
        screenVisible=false;
        main.removeCallbacks(tick);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    private void refreshNearbyRiders(){
        io.execute(()->{
            try{
                JSONArray others=api.get("pp_live_riders").optJSONArray("riders");
                int ownRide=getSharedPreferences(TrackingService.PREFS,MODE_PRIVATE).getInt("ride_id",0);
                JSONArray permitted=new JSONArray();
                if(others!=null)for(int i=0;i<others.length();i++){
                    JSONObject person=others.optJSONObject(i);
                    if(person==null)continue;
                    int id=person.optInt("ride_id",0);
                    if(id<=0||id==ownRide||person.optInt("age_seconds",99)>45)continue;
                    permitted.put(person);
                }
                main.post(()->{
                    if(!screenVisible||routeView==null)return;
                    if("live".equals(mode)&&rideId>0&&rideId!=ownRide&&
                            Double.isFinite(targetLat)&&Double.isFinite(targetLng)){
                        JSONObject selected=new JSONObject();
                        try{
                            selected.put("ride_id",rideId).put("lat",targetLat)
                                    .put("lng",targetLng).put("display_name",targetName)
                                    .put("age_seconds",0).put("track_points",viewedRiderTrack);
                            // Selected target replaces a basic marker, keeping the route orange.
                            JSONArray combined=new JSONArray().put(selected);
                            for(int i=0;i<permitted.length();i++){
                                JSONObject p=permitted.optJSONObject(i);
                                if(p!=null&&p.optInt("ride_id",0)!=rideId)combined.put(p);
                            }
                            routeView.setNearbyRiders(combined);
                        }catch(org.json.JSONException ignored){routeView.setNearbyRiders(permitted);}
                    }else routeView.setNearbyRiders(permitted);
                });
            }catch(Exception e){
                main.post(()->{
                    if(screenVisible&&routeView!=null)routeView.setNearbyRiders(null);
                });
            }
        });
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
                    if(g==null||!g.optBoolean("has_location",false)||!groupTargetFresh(g)){
                        main.post(()->{
                            // Never continue navigating to a stale leader fix.
                            targetLat=Double.NaN;
                            targetLng=Double.NaN;
                            routeView.clearTarget();
                            routeView.clearNavigationRoute();
                            invalidateDirectionCache();
                            distance.setText("— km");
                            bearing.setText("جهت: —");
                            status.setText("موقعیت گروه قدیمی یا نامعتبر است؛ مسیریابی متوقف شد ولی ثبت GPS شما ادامه دارد.");
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
                    if(r==null||!"active".equals(r.optString("status")) ||
                            (r.optInt("id",0)!=rideId)){
                        main.post(()->{
                            status.setText("انتشار زنده این رکاب‌زن متوقف شده است.");
                            if(!"group".equals(mode)){
                                viewedRiderTrack=new JSONArray();
                                routeView.setNearbyRiders(null);
                                targetLat=Double.NaN;targetLng=Double.NaN;
                                routeView.clearTarget();
                            }
                            routeView.clearNavigationRoute();
                        });
                    }else{
                        double lat=r.optDouble("last_lat",Double.NaN),lng=r.optDouble("last_lng",Double.NaN);
                        lastPointId=Math.max(lastPointId,newLast);
                        if(pts!=null)for(int i=0;i<pts.length();i++){
                            JSONObject p=pts.optJSONObject(i);
                            if(p!=null)viewedRiderTrack.put(p);
                        }
                        JSONArray shown=new JSONArray();
                        for(int i=Math.max(0,viewedRiderTrack.length()-1500);i<viewedRiderTrack.length();i++)
                            shown.put(viewedRiderTrack.opt(i));
                        viewedRiderTrack=shown;
                        JSONObject peer=new JSONObject();
                        peer.put("ride_id",rideId).put("lat",lat).put("lng",lng)
                                .put("display_name",r.optString("owner_name",targetName))
                                .put("age_seconds",r.optInt("location_age_seconds",0))
                                .put("track_points",shown);
                        main.post(()->{
                            routeView.setNearbyRiders(new JSONArray().put(peer));
                            applyTarget(lat,lng,r.optString("owner_name",targetName),null);
                        });
                    }
                }
            }catch(Exception e){
                main.post(()->{
                    targetLat=Double.NaN;targetLng=Double.NaN;
                    routeView.clearTarget();routeView.clearNavigationRoute();
                    invalidateDirectionCache();arrivalCandidateTime=0L;arrivalCandidateFixTime=0L;
                    if(!"group".equals(mode)){
                        viewedRiderTrack=new JSONArray();
                        routeView.setNearbyRiders(null);
                    }
                    status.setText("موقعیت زنده معتبر یا دسترسی مشاهده در دسترس نیست؛ مسیر قبلی حذف شد.");
                });
            }finally{busy=false;}
        });
    }


    private static boolean groupTargetFresh(JSONObject group){
        if(group.has("location_age_seconds")&&!group.isNull("location_age_seconds")){
            double age=group.optDouble("location_age_seconds",Double.POSITIVE_INFINITY);
            return Double.isFinite(age)&&age>=0d&&age<=30d;
        }
        // Legacy 4.7.46 response: timestamp is in server Asia/Tehran timezone.
        String date=group.optString("recorded_at","");
        if(date.isBlank()||"null".equalsIgnoreCase(date))return false;
        try{
            java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US);
            format.setLenient(false);
            format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Tehran"));
            java.util.Date stamped=format.parse(date);
            if(stamped==null)return false;
            long age=System.currentTimeMillis()-stamped.getTime();
            return age>=-3000L&&age<=30000L;
        }catch(Exception e){
            try{
                java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);
                format.setLenient(false);
                format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Tehran"));
                java.util.Date stamped=format.parse(date);
                return stamped!=null && System.currentTimeMillis()-stamped.getTime()>=-3000L
                    &&System.currentTimeMillis()-stamped.getTime()<=30000L;
            }catch(Exception bad){return false;}
        }
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
                    long fixAge=Math.max(0L,System.currentTimeMillis()-loc.getTime());
                    if(fixAge>12000L){
                        currentAccuracy=Float.MAX_VALUE;
                        return; // Never announce arrival using a cached GPS fix.
                    }
                    currentLat=loc.getLatitude();currentLng=loc.getLongitude();
                    currentAccuracy=loc.hasAccuracy()?loc.getAccuracy():Float.MAX_VALUE;
                    recentFix=loc;
                    if("group".equals(mode)&&!autoGroupTrackingAttempted&&
                            !startingGroupTracking&&currentAccuracy<=35f&&
                            groupTrackingButton!=null&&
                            !getSharedPreferences(TrackingService.PREFS,MODE_PRIVATE)
                                .getBoolean("active",false)){
                        autoGroupTrackingAttempted=true;
                        beginGroupRideIfNeeded(groupTrackingButton);
                    }
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
                int id=active==null?0:active.optInt("id",0);
                if(id<=0){
                    JSONObject created=api.post("start_ride",new JSONObject()
                            .put("title","رسیدن به گروه")
                            .put("event_id",0)
                            .put("challenge_id",0)
                            .put("bike_id",0)
                            .put("public_tracking",false)
                            .put("viewer_user_ids",new JSONArray()));
                    id=created.optInt("ride_id",0);
                }
                if(id<=0)throw new IllegalStateException("شناسه معتبر از سرور دریافت نشد");
                // TrackingService owns track-point writes and de-duplication. Reuse
                // the authenticated active ride instead of writing a duplicate first point.
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
                    status.setText("ثبت GPS برای رسیدن به گروه فعال شد؛ همان رکاب‌زنی در پس‌زمینه ادامه دارد.");
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
        if(pts!=null && "group".equals(mode))routeView.appendPoints(pts);
        routeView.setTarget(lat,lng);
        status.setText("هدف زنده: "+name+" · آخرین بروزرسانی همین حالا");
        updateHud();
        maybeFetchDirections();
    }

    private void maybeFetchDirections() {
        if(!roadEnabled || directionInFlight || !Double.isFinite(currentLat) ||
                !Double.isFinite(currentLng)||!Double.isFinite(targetLat)||
                !Double.isFinite(targetLng)||isFinishing())return;
        // Cycling credential is server-side only; never request a Neshan car route.
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
        roadStatus.setText("در حال دریافت مسیر مخصوص دوچرخه…");
        directionsIo.execute(()->{
            try {
                CyclingDirectionsClient.Route result=CyclingDirectionsClient.fetch(api,
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
                    String next=result.instruction==null?"":result.instruction.trim();
                    roadStatus.setText("مسیر مخصوص دوچرخه: "+km+
                            (next.isEmpty()?"":" · "+next));
                });
            } catch(Exception e) {
                String reason=e.getMessage()==null?"پاسخ معتبر دوچرخه دریافت نشد":e.getMessage();
                main.post(()->{
                    directionInFlight=false;
                    if(isFinishing()||!roadEnabled)return;
                    routeView.clearNavigationRoute();
                    roadStatus.setText("مسیریابی دوچرخه: "+reason+
                            " · مسیر خودرو جایگزین نمی‌شود.");
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
        boolean fresh=recentFix!=null && recentFix.hasAccuracy() &&
                Math.max(0L,System.currentTimeMillis()-recentFix.getTime())<=12000L;
        boolean nearGroup="group".equals(mode)&&fresh&&currentAccuracy<=30f&&
                r[0]<=Math.max(35f,currentAccuracy*1.5f);
        // Require two distinct accurate GPS fixes, at least six seconds apart.
        long fixTime=recentFix==null?0L:recentFix.getTime();
        if(!nearGroup){
            arrivalCandidateTime=0L;
            arrivalCandidateFixTime=0L;
        }else if(!groupArrivalNotified && arrivalCandidateTime==0L){
            arrivalCandidateTime=SystemClock.elapsedRealtime();
            arrivalCandidateFixTime=fixTime;
            status.setText("نزدیک گروه هستی؛ در حال تأیید موقعیت…");
        }else if(!groupArrivalNotified &&
                fixTime>arrivalCandidateFixTime &&
                SystemClock.elapsedRealtime()-arrivalCandidateTime>=6000L){
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
