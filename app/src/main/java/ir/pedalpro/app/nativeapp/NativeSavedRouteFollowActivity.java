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
    private double[] trackLat=new double[0],trackLng=new double[0],trackMeters=new double[0];
    private int lastProgressSegment=-1;
    private long lastCyclingAttempt,approachRevision;
    private boolean cyclingInFlight;
    private long arrivalCandidateElapsed,arrivalCandidateFixTime;
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
        intro.addView(text("مسیریابی اختصاصی دوچرخه تا ابتدا یا انتهای مسیر روی نقشه PedalPro نمایش داده می‌شود، پس از فعال‌سازی سرویس دوچرخه در سرور. ثبت GPS مستقل ادامه دارد.",11,false,MUTED));
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
                    indexTrack(points);
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
                    phase=PHASE_TO_TARGET;arrived=false;lastProgressSegment=-1;
                    arrivalCandidateElapsed=0L;arrivalCandidateFixTime=0L;
                    lastCyclingAttempt=0L;approachRevision++;
                    mapView.setNavigationMode(true);
                    mapView.clearNavigationRoute();
                    mapView.setTarget(goalLat,goalLng);
                    status.setText("مقصد: "+(toBeginning?"ابتدای":"انتهای")+
                            " مسیر؛ در انتظار GPS و دریافت مسیر ویژه دوچرخه…");
                    updateLocation();
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
        if(!Double.isFinite(goalLat)){chooseDestination();return;}
        // Never skip the bicycle approach to the selected endpoint.
        phase=PHASE_TO_TARGET;
        arrived=false;arrivalCandidateElapsed=0L;arrivalCandidateFixTime=0L;
        mapView.setNavigationMode(true);
        if(!recording()){
            new AlertDialog.Builder(this).setTitle("ثبت مسیر GPS")
                    .setMessage("برای ذخیره حرکت تا مسیر اصلی ابتدا GPS را روشن کن.")
                    .setPositiveButton("شروع GPS و مسیریابی",(d,w)->
                            beginTrackingThen(this::updateLocation))
                    .setNeutralButton("مسیریابی بدون ثبت",(d,w)->updateLocation())
                    .setNegativeButton("انصراف",null).show();
            return;
        }
        updateLocation();
    }

    private void activateFollowing(){
        phase=PHASE_FOLLOW;arrived=true;
        approachRevision++;
        mapView.clearNavigationRoute();
        JSONObject finish=endpoint(false);
        if(finish!=null)mapView.setTarget(finish.optDouble("lat"),finish.optDouble("lng"));
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
                if(phase==PHASE_FOLLOW){updateFollowProgress(loc);return;}
                if(phase!=PHASE_TO_TARGET||!Double.isFinite(goalLat))return;
                if(!loc.hasAccuracy()||loc.getAccuracy()>50f||
                        Math.abs(System.currentTimeMillis()-loc.getTime())>12000L){
                    status.setText("GPS قدیمی یا کم‌دقت است؛ مسیریابی دوچرخه متوقف شده.");
                    return;
                }
                fetchCyclingApproach(loc);
                float[] dist=new float[1];
                Location.distanceBetween(loc.getLatitude(),loc.getLongitude(),goalLat,goalLng,dist);
                status.setText(String.format(Locale.US,"تا %s مسیر %.0f متر · GPS ±%.0f متر",
                        toBeginning?"ابتدا":"انتها",dist[0],loc.hasAccuracy()?loc.getAccuracy():999f));
                if(arrived||loc.getAccuracy()>30f||
                    dist[0]>Math.max(35f,loc.getAccuracy()*1.5f)){
                    arrivalCandidateElapsed=0L;arrivalCandidateFixTime=0L;
                    return;
                }
                if(arrivalCandidateElapsed==0L){
                    arrivalCandidateElapsed=SystemClock.elapsedRealtime();
                    arrivalCandidateFixTime=loc.getTime();
                    status.setText("نزدیک مقصد؛ در حال تأیید رسیدن با GPS دوم…");
                    return;
                }
                if(loc.getTime()<=arrivalCandidateFixTime ||
                        SystemClock.elapsedRealtime()-arrivalCandidateElapsed<6000L)return;
                arrived=true;
                if(toBeginning){
                    // Switch to the recorded polyline after an accurate start fix.
                    // The existing TrackingService continues the same ride.
                    if(recording()){
                        activateFollowing();
                        android.widget.Toast.makeText(this,
                                "به ابتدای مسیر رسیدی؛ دنبال‌کردن مسیر اصلی فعال شد.",
                                android.widget.Toast.LENGTH_LONG).show();
                    }else{
                        status.setText("به ابتدای مسیر رسیدی. برای ثبت ادامه مسیر GPS را فعال کن.");
                        new AlertDialog.Builder(this).setTitle("به ابتدای مسیر رسیدی")
                            .setMessage("مسیر اصلی آماده است. ثبت ادامه رکاب‌زنی نیازمند روشن‌شدن GPS است.")
                            .setPositiveButton("شروع GPS و دنبال‌کردن مسیر",(d,w)->
                                    beginTrackingThen(this::activateFollowing))
                            .setNeutralButton("نمایش بدون ثبت",(d,w)->activateFollowing())
                            .setNegativeButton("بعداً",null).show();
                    }
                } else status.setText("به انتهای مسیر رسیدی.");
            });
    }


    private void fetchCyclingApproach(Location position){
        if(cyclingInFlight||!Double.isFinite(goalLat)||phase!=PHASE_TO_TARGET)return;
        long now=SystemClock.elapsedRealtime();
        if(lastCyclingAttempt>0&&now-lastCyclingAttempt<45000L)return;
        lastCyclingAttempt=now;cyclingInFlight=true;
        final long revision=approachRevision;
        final double fromLat=position.getLatitude(),fromLng=position.getLongitude();
        final double toLat=goalLat,toLng=goalLng;
        io.execute(()->{
            try{
                CyclingDirectionsClient.Route route=CyclingDirectionsClient.fetch(api,fromLat,fromLng,toLat,toLng);
                main.post(()->{
                    cyclingInFlight=false;
                    if(isFinishing()||phase!=PHASE_TO_TARGET||revision!=approachRevision)return;
                    mapView.setNavigationRoute(route.points);
                    status.setText(String.format(Locale.US,
                            "مسیر مخصوص دوچرخه تا %s: %.2f km · حدود %d دقیقه",
                            toBeginning?"ابتدا":"انتها",route.distanceMeters/1000.0,
                            Math.max(1,(route.durationSeconds+59)/60)));
                });
            }catch(Exception e){
                main.post(()->{
                    cyclingInFlight=false;
                    if(isFinishing()||phase!=PHASE_TO_TARGET||revision!=approachRevision)return;
                    mapView.clearNavigationRoute();
                    status.setText("مسیر دوچرخه روی سرور فعال نیست یا در دسترس نیست: "+
                            (e.getMessage()==null?"خطای دریافت":e.getMessage())+
                            " · مسیر خودرو جایگزین نمی‌شود.");
                });
            }
        });
    }

    /** Read-only index of the original GPS track, used only for navigation feedback. */
    private void indexTrack(JSONArray points){
        int size=points==null?0:points.length();
        double[] la=new double[size],lo=new double[size],metres=new double[size];
        int count=0;
        for(int i=0;i<size;i++){
            JSONObject p=points.optJSONObject(i);
            if(p==null)continue;
            double lat=p.optDouble("lat",Double.NaN),lng=p.optDouble("lng",Double.NaN);
            if(!Double.isFinite(lat)||!Double.isFinite(lng)||
                    Math.abs(lat)>=85||Math.abs(lng)>=180)continue;
            if(count>0){
                float[] step=new float[1];
                Location.distanceBetween(la[count-1],lo[count-1],lat,lng,step);
                if(step[0]<.5f)continue;
                metres[count]=metres[count-1]+Math.max(0d,step[0]);
            }
            la[count]=lat;lo[count]=lng;count++;
        }
        trackLat=java.util.Arrays.copyOf(la,count);
        trackLng=java.util.Arrays.copyOf(lo,count);
        trackMeters=java.util.Arrays.copyOf(metres,count);
        lastProgressSegment=-1;
    }

    /** Project a fresh rider fix onto a recorded segment; never alters recorded GPS. */
    private void updateFollowProgress(Location fix){
        if(trackLat.length<2||!fix.hasAccuracy()||fix.getAccuracy()>60f||
                Math.abs(System.currentTimeMillis()-fix.getTime())>15000L){
            status.setText("در انتظار موقعیت GPS معتبر برای دنبال‌کردن مسیر…");
            return;
        }
        int from=lastProgressSegment<0?0:Math.max(0,lastProgressSegment-4);
        int to=lastProgressSegment<0?trackLat.length-1:
                Math.min(trackLat.length-1,lastProgressSegment+120);
        double latScale=111195d;
        double lngScale=latScale*Math.max(.01d,Math.cos(Math.toRadians(fix.getLatitude())));
        double best=Double.POSITIVE_INFINITY,along=0d;
        int matched=-1;
        for(int i=from;i<to;i++){
            double x=(trackLng[i]-fix.getLongitude())*lngScale;
            double y=(trackLat[i]-fix.getLatitude())*latScale;
            double dx=(trackLng[i+1]-trackLng[i])*lngScale;
            double dy=(trackLat[i+1]-trackLat[i])*latScale;
            double len=dx*dx+dy*dy;
            if(len<.01d)continue;
            double fraction=Math.max(0d,Math.min(1d,-(x*dx+y*dy)/len));
            double gap=Math.hypot(x+fraction*dx,y+fraction*dy);
            if(gap<best){
                best=gap;matched=i;
                along=trackMeters[i]+fraction*(trackMeters[i+1]-trackMeters[i]);
            }
        }
        if(matched<0)return;
        if(best<=Math.max(80d,fix.getAccuracy()*2d))lastProgressSegment=matched;
        double remaining=Math.max(0d,trackMeters[trackMeters.length-1]-along);
        String message="تا انتهای مسیر "+
                String.format(Locale.US,"%.2f",remaining/1000d)+" کیلومتر";
        if(best>Math.max(75d,fix.getAccuracy()*2d))
            message+=" · فاصله از مسیر "+Math.round(best)+" متر";
        else message+=" · روی مسیر اصلی";
        status.setText(message+(recording()?" · ثبت GPS فعال":" · ثبت GPS غیرفعال"));
    }
    @Override protected void onResume(){
        super.onResume();
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        visible=true;
        main.removeCallbacks(tick);main.post(tick);
    }
    @Override protected void onPause(){
        visible=false;main.removeCallbacks(tick);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }
}
