package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.pedalpro.app.R;
import ir.pedalpro.app.TrackingService;

/**
 * Polished Native 2.0 launcher.
 * No WebView is created or loaded.
 */
public final class NativeLauncherActivity extends Activity {
    private static final int BG=Color.rgb(5,17,28);
    private static final int CARD=Color.rgb(11,38,54);
    private static final int CARD2=Color.rgb(9,31,45);
    private static final int TEXT=Color.rgb(240,248,251);
    private static final int MUTED=Color.rgb(147,174,187);
    private static final int ACCENT=Color.rgb(37,215,220);
    private static final int GREEN=Color.rgb(45,223,158);
    private static final int ORANGE=Color.rgb(255,182,67);
    private static final int STROKE=Color.rgb(26,69,87);
    private static final int REQ_LOCATION=5201;

    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private PedalProApiClient api;
    private SessionStore session;
    private JSONObject data;
    private JSONObject activeRide;
    private LinearLayout content;
    private TextView status;
    private Button navHome,navActivities,navRide,navRoutes,navMe;
    private Location pendingFix;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        api=new PedalProApiClient(this);
        session=new SessionStore(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BG);
        buildShell();
        load(true);
    }

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16),dp(7),dp(16),dp(7));

        LinearLayout ht=new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setGravity(Gravity.END);
        TextView brand=txt("PedalPro",19,true,TEXT);
        status=txt("در حال اتصال…",10,false,MUTED);
        ht.addView(brand);ht.addView(status);

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        header.addView(ht,new LinearLayout.LayoutParams(0,dp(54),1f));
        header.addView(logo,new LinearLayout.LayoutParams(dp(50),dp(50)));
        root.addView(header);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(5),dp(16),dp(24));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(5),dp(5),dp(5),dp(5));
        nav.setBackground(round(Color.rgb(6,27,40),0,0));

        navHome=nav("خانه",this::home);
        navActivities=nav("فعالیت‌ها",this::activities);
        navRide=nav("رکاب‌زنی",this::ride);
        navRoutes=nav("مسیرها",this::routes);
        navMe=nav("من",this::me);
        nav.addView(navHome,weight());
        nav.addView(navActivities,weight());
        nav.addView(navRide,weight());
        nav.addView(navRoutes,weight());
        nav.addView(navMe,weight());
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(62)));
        setContentView(root);
    }

    private void load(boolean first){
        status.setText("در حال همگام‌سازی…");
        io.execute(()->{
            try{
                JSONObject d=api.bootstrap();
                data=d;activeRide=d.optJSONObject("my_active_ride");
                runOnUiThread(()->{
                    JSONObject u=d.optJSONObject("user");
                    if(u==null){status.setText("ورود لازم است");login();return;}
                    status.setText("متصل · "+u.optString("display_name",u.optString("username","PedalPro")));
                    status.setTextColor(GREEN);
                    if(first)home();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    status.setText("ارتباط با سرور برقرار نشد");
                    status.setTextColor(ORANGE);
                    error("خطای اتصال",e.getMessage());
                });
            }
        });
    }

    private void login(){
        select(null);content.removeAllViews();
        hero("ورود به PedalPro","نسخه مستقل Android · بدون WebView");
        LinearLayout box=card();
        EditText user=input("نام کاربری");
        EditText pass=input("رمز عبور");
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button go=primary("ورود");
        ProgressBar p=new ProgressBar(this);p.setVisibility(View.GONE);
        box.addView(user);box.addView(pass);box.addView(go);box.addView(p);
        content.addView(box,cardLp());
        go.setOnClickListener(v->{
            String u=user.getText().toString().trim(),pw=pass.getText().toString();
            if(u.isBlank()||pw.isBlank()){toast("نام کاربری و رمز عبور را وارد کنید.");return;}
            go.setEnabled(false);p.setVisibility(View.VISIBLE);
            io.execute(()->{
                try{
                    api.login(u,pw,true);
                    data=api.bootstrap();activeRide=data.optJSONObject("my_active_ride");
                    runOnUiThread(()->{go.setEnabled(true);p.setVisibility(View.GONE);home();});
                }catch(Exception e){
                    runOnUiThread(()->{go.setEnabled(true);p.setVisibility(View.GONE);toast(e.getMessage());});
                }
            });
        });
    }

    private void home(){
        if(!signed()){login();return;}
        select(navHome);content.removeAllViews();
        JSONObject u=data.optJSONObject("user");
        String name=u==null?"رکاب‌زن":u.optString("display_name","رکاب‌زن");

        LinearLayout h=card();
        h.addView(txt("سلام "+name,22,true,TEXT));
        TextView sub=txt(activeRide==null?"برای یک رکاب‌زنی دقیق آماده‌ای.":"رکاب‌زنی فعال داری؛ ادامه‌اش بده.",12,false,MUTED);
        sub.setPadding(0,dp(5),0,0);h.addView(sub);
        content.addView(h,cardLp());

        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("فعالیت‌ها",completedCount()),metricLp());
        metrics.addView(metric("اعضا",arrayCount("members")),metricLp());
        metrics.addView(metric("زنده",liveCount()),metricLp());
        content.addView(metrics,cardLp());

        LinearLayout rc=card();
        rc.addView(txt(activeRide==null?"شروع رکاب‌زنی":"رکاب‌زنی فعال",18,true,TEXT));
        TextView rm=txt(activeRide==null?
                "GPS ابتدا بررسی می‌شود؛ تایمر و مسیر فقط بعد از تأیید شما شروع می‌شوند.":
                String.format(Locale.US,"%.2f km  ·  %s",activeRide.optDouble("distance_km",0),duration(activeRide.optLong("duration_seconds",0))),
                11,false,MUTED);
        rm.setPadding(0,dp(6),0,0);rc.addView(rm);
        Button go=primary(activeRide==null?"آماده‌سازی GPS":"بازگشت به رکاب‌زنی");
        go.setOnClickListener(v->ride());rc.addView(go);content.addView(rc,cardLp());

        section("رکاب‌زنی زنده");
        JSONArray rides=data.optJSONArray("rides");int shown=0;int uid=u==null?0:u.optInt("id",0);
        if(rides!=null)for(int i=0;i<rides.length()&&shown<3;i++){
            JSONObject r=rides.optJSONObject(i);
            if(r==null||!"active".equals(r.optString("status"))||r.optInt("owner_user_id",0)==uid)continue;
            rideCard(r,true);shown++;
        }
        if(shown==0)smallCard("فعلاً رکاب‌زن زنده دیگری دیده نمی‌شود.");

        section("امکانات");
        quickRow("باشگاه","چالش‌ها",
                ()->open(NativeClubActivity.class),()->open(NativeClubActivity.class));
        quickRow("سنسورها","عملکرد من",
                ()->open(NativeSensorsActivity.class),()->open(NativePerformanceActivity.class));
        quickRow("تحلیل حرفه‌ای","هوش مصنوعی",
                ()->open(NativeAdvancedActivity.class),()->open(NativeAiActivity.class));
        quickRow("چت گروهی","فید اجتماعی",
                ()->open(NativeChatActivity.class),()->open(NativeSocialActivity.class));
        quickRow("ایمنی و SOS","Heatmap",
                ()->open(NativeSafetyActivity.class),()->open(NativeHeatmapActivity.class));
    }

    private void activities(){
        if(!signed()){login();return;}
        select(navActivities);content.removeAllViews();hero("فعالیت‌ها","رکاب‌زنی‌های ثبت‌شده شما");
        JSONArray rides=data.optJSONArray("my_completed_rides");if(rides==null)rides=data.optJSONArray("rides");
        JSONObject u=data.optJSONObject("user");int uid=u==null?0:u.optInt("id",0),shown=0;
        if(rides!=null)for(int i=0;i<rides.length()&&shown<50;i++){
            JSONObject r=rides.optJSONObject(i);if(r==null)continue;
            if(!"completed".equals(r.optString("status","completed")))continue;
            int owner=r.optInt("owner_user_id",uid);if(uid>0&&owner>0&&owner!=uid)continue;
            rideCard(r,false);shown++;
        }
        if(shown==0)smallCard("هنوز فعالیت ثبت‌شده‌ای وجود ندارد.");
    }

    private void routes(){
        if(!signed()){login();return;}
        select(navRoutes);content.removeAllViews();hero("مسیرها","مسیرها و رکاب‌زنی‌های زنده PedalPro");
        JSONArray rides=data.optJSONArray("rides");int shown=0;
        if(rides!=null)for(int i=0;i<rides.length()&&shown<50;i++){
            JSONObject r=rides.optJSONObject(i);if(r==null)continue;
            rideCard(r,"active".equals(r.optString("status")));shown++;
        }
        if(shown==0)smallCard("مسیر قابل نمایشی وجود ندارد.");
    }

    private void ride(){
        if(!signed()){login();return;}
        select(navRide);content.removeAllViews();
        if(activeRide==null){
            hero("شروع رکاب‌زنی","ابتدا GPS تازه و دقیق بررسی می‌شود.");
            LinearLayout box=card();
            TextView state=txt("در حال بررسی GPS…",15,true,TEXT);
            TextView accuracy=txt("دقت: —",11,false,MUTED);
            Button check=normal("بررسی دوباره GPS");
            Button start=primary("شروع ثبت مسیر");
            start.setEnabled(false);
            box.addView(state);box.addView(accuracy);box.addView(check);box.addView(start);
            content.addView(box,cardLp());
            check.setOnClickListener(v->preflight(state,accuracy,start));
            start.setOnClickListener(v->startRide(start,state));
            preflight(state,accuracy,start);
            return;
        }

        hero("رکاب‌زنی فعال",activeRide.optString("title","PedalPro"));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metricText("زمان",duration(activeRide.optLong("duration_seconds",0))),metricLp());
        row.addView(metricText("مسافت",String.format(Locale.US,"%.2f km",activeRide.optDouble("distance_km",0))),metricLp());
        row.addView(metricText("میانگین",String.format(Locale.US,"%.1f",activeRide.optDouble("avg_speed_kmh",0))),metricLp());
        content.addView(row,cardLp());

        Button navGroup=normal("مسیریابی تا گروه در حال حرکت");
        navGroup.setOnClickListener(v->{
            Intent x=new Intent(this,NativeLiveNavigationActivity.class);
            x.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"group");
            x.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID,activeRide.optInt("id",0));
            startActivity(x);
        });
        content.addView(navGroup);

        Button sensors=normal("سنسورها و داده زنده");
        sensors.setOnClickListener(v->open(NativeSensorsActivity.class));content.addView(sensors);

        Button stop=danger("پایان رکاب‌زنی");
        stop.setOnClickListener(v->finishRide(stop));content.addView(stop);
    }

    private void me(){
        if(!signed()){login();return;}
        select(navMe);content.removeAllViews();
        JSONObject u=data.optJSONObject("user");
        hero(u==null?"حساب من":u.optString("display_name","حساب من"),u==null?"":"@"+u.optString("username",""));
        quickRow("باشگاه","عملکرد",()->open(NativeClubActivity.class),()->open(NativePerformanceActivity.class));
        quickRow("سنسورها","تحلیل حرفه‌ای",()->open(NativeSensorsActivity.class),()->open(NativeAdvancedActivity.class));
        quickRow("ایمنی","اجتماعی",()->open(NativeSafetyActivity.class),()->open(NativeSocialActivity.class));
        Button refresh=normal("بروزرسانی اطلاعات");refresh.setOnClickListener(v->load(true));content.addView(refresh);
        Button logout=danger("خروج از حساب");logout.setOnClickListener(v->io.execute(()->{
            try{api.logout();}catch(Exception ignored){session.clear();}
            runOnUiThread(this::login);
        }));content.addView(logout);
    }

    private void rideCard(JSONObject r,boolean live){
        LinearLayout box=card();
        box.addView(txt((live?"● ":"")+r.optString("owner_name","PedalPro")+" · "+r.optString("title","رکاب‌زنی"),15,true,live?ORANGE:TEXT));
        box.addView(txt(String.format(Locale.US,"%.2f km  ·  %s  ·  %.1f km/h",
                r.optDouble("distance_km",r.optDouble("distance_m",0)/1000.0),
                duration(r.optLong("duration_seconds",0)),r.optDouble("avg_speed_kmh",0)),11,false,MUTED));
        int id=r.optInt("id",0);
        if(id>0){
            Button detail=normal("جزئیات مسیر");
            detail.setOnClickListener(v->{Intent x=new Intent(this,NativeRideDetailActivity.class);x.putExtra(NativeRideDetailActivity.EXTRA_RIDE_ID,id);startActivity(x);});
            box.addView(detail);
        }
        if(live&&id>0){
            Button track=primary("مشاهده و مسیریابی زنده");
            track.setOnClickListener(v->{
                Intent x=new Intent(this,NativeLiveNavigationActivity.class);
                x.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"live");
                x.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID,id);
                x.putExtra(NativeLiveNavigationActivity.EXTRA_TITLE,r.optString("owner_name","رکاب‌زن"));
                startActivity(x);
            });
            box.addView(track);
        }
        content.addView(box,cardLp());
    }

    private void preflight(TextView state,TextView accuracy,Button start){
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ_LOCATION);
            state.setText("دسترسی دقیق Location لازم است.");return;
        }
        pendingFix=null;start.setEnabled(false);state.setText("در حال دریافت موقعیت تازه و دقیق…");
        CancellationTokenSource token=new CancellationTokenSource();
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,token.getToken())
                .addOnSuccessListener(this,loc->{
                    if(loc==null){state.setText("موقعیت دریافت نشد.");return;}
                    long age=Math.max(0,System.currentTimeMillis()-loc.getTime());
                    float acc=loc.hasAccuracy()?loc.getAccuracy():999f;
                    accuracy.setText("دقت ±"+Math.round(acc)+" متر · تازگی "+(age/1000)+" ثانیه");
                    if(age<=12000&&acc<=35f){pendingFix=loc;state.setText("GPS آماده است.");state.setTextColor(GREEN);start.setEnabled(true);}
                    else{state.setText("GPS هنوز به دقت مناسب نرسیده است.");state.setTextColor(ORANGE);}
                })
                .addOnFailureListener(this,e->{state.setText("خطای GPS");toast(e.getMessage());});
    }

    private void startRide(Button b,TextView state){
        if(pendingFix==null)return;b.setEnabled(false);state.setText("در حال شروع…");Location fix=pendingFix;
        io.execute(()->{
            try{
                JSONObject created=api.post("start_ride",new JSONObject()
                        .put("challenge_id",0).put("event_id",0).put("title","رکاب‌زنی من")
                        .put("public_tracking",false).put("viewer_user_ids",new JSONArray()).put("bike_id",0));
                int rideId=created.optInt("ride_id",0);if(rideId<=0)throw new IllegalStateException("شناسه رکاب دریافت نشد.");
                try{api.post("track_point",new JSONObject().put("ride_id",rideId).put("lat",fix.getLatitude()).put("lng",fix.getLongitude())
                        .put("altitude",fix.hasAltitude()?fix.getAltitude():JSONObject.NULL)
                        .put("speed_kmh",fix.hasSpeed()?fix.getSpeed()*3.6:0).put("accuracy",fix.getAccuracy()).put("timestamp",System.currentTimeMillis()));}catch(Exception ignored){}
                data=api.bootstrap();activeRide=data.optJSONObject("my_active_ride");
                Intent i=new Intent(this,TrackingService.class);i.setAction(TrackingService.ACTION_START);
                i.putExtra("ride_id",rideId);i.putExtra("csrf",session.csrf());i.putExtra("max_accuracy",25d);i.putExtra("max_speed_kmh",100d);
                ContextCompat.startForegroundService(this,i);
                runOnUiThread(this::ride);
            }catch(Exception e){runOnUiThread(()->{b.setEnabled(true);state.setText("شروع انجام نشد.");toast(e.getMessage());});}
        });
    }

    private void finishRide(Button b){
        int rideId=activeRide==null?0:activeRide.optInt("id",0);if(rideId<=0)return;b.setEnabled(false);
        io.execute(()->{
            try{
                Intent s=new Intent(this,TrackingService.class);s.setAction(TrackingService.ACTION_STOP);startService(s);
                api.post("stop_ride",new JSONObject().put("ride_id",rideId).put("notes",""));
                data=api.bootstrap();activeRide=data.optJSONObject("my_active_ride");
                runOnUiThread(this::activities);
            }catch(Exception e){runOnUiThread(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private boolean signed(){return data!=null&&data.optJSONObject("user")!=null;}
    private int arrayCount(String k){JSONArray a=data==null?null:data.optJSONArray(k);return a==null?0:a.length();}
    private int completedCount(){
        JSONArray a=data==null?null:data.optJSONArray("my_completed_rides");if(a!=null)return a.length();
        JSONArray rides=data==null?null:data.optJSONArray("rides");int n=0;if(rides!=null)for(int i=0;i<rides.length();i++){JSONObject r=rides.optJSONObject(i);if(r!=null&&"completed".equals(r.optString("status")))n++;}return n;
    }
    private int liveCount(){JSONArray a=data==null?null:data.optJSONArray("rides");int n=0;if(a!=null)for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&"active".equals(r.optString("status")))n++;}return n;}

    private void open(Class<? extends Activity> cls){startActivity(new Intent(this,cls));}
    private void error(String title,String body){content.removeAllViews();hero(title,body==null?"":body);Button b=primary("تلاش دوباره");b.setOnClickListener(v->load(true));content.addView(b);}
    private void hero(String title,String sub){LinearLayout x=card();x.addView(txt(title,21,true,TEXT));TextView s=txt(sub,12,false,MUTED);s.setPadding(0,dp(5),0,0);x.addView(s);content.addView(x,cardLp());}
    private void section(String s){TextView t=txt(s,15,true,TEXT);t.setPadding(dp(2),dp(10),dp(2),dp(8));content.addView(t);}
    private void smallCard(String s){LinearLayout x=card();x.addView(txt(s,12,false,MUTED));content.addView(x,cardLp());}

    private void quickRow(String a,String b,Runnable ra,Runnable rb){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(quick(a,ra),quickLp());row.addView(quick(b,rb),quickLp());content.addView(row);
    }
    private Button quick(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(TEXT);b.setGravity(Gravity.CENTER);b.setBackground(round(CARD2,16,STROKE));b.setOnClickListener(v->r.run());return b;}
    private LinearLayout.LayoutParams quickLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(62),1f);p.setMargins(dp(4),dp(4),dp(4),dp(4));return p;}

    private View metric(String label,int value){return metricText(label,String.valueOf(value));}
    private View metricText(String label,String value){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER);x.setBackground(round(CARD2,16,STROKE));TextView v=txt(value,18,true,ACCENT);v.setGravity(Gravity.CENTER);TextView l=txt(label,10,false,MUTED);l.setGravity(Gravity.CENTER);l.setPadding(0,dp(3),0,0);x.addView(v);x.addView(l);return x;}
    private LinearLayout.LayoutParams metricLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(84),1f);p.setMargins(dp(4),0,dp(4),0);return p;}

    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(15),dp(14),dp(15),dp(14));x.setBackground(round(CARD,18,STROKE));x.setElevation(dp(1));return x;}
    private LinearLayout.LayoutParams cardLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));return p;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-1,1f);}
    private Button nav(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextSize(10);b.setAllCaps(false);b.setTextColor(TEXT);b.setGravity(Gravity.CENTER);b.setBackgroundColor(Color.TRANSPARENT);b.setOnClickListener(v->r.run());return b;}
    private void select(Button selected){Button[] all={navHome,navActivities,navRide,navRoutes,navMe};for(Button b:all){if(b==null)continue;b.setTextColor(b==selected?ACCENT:TEXT);b.setTypeface(Typeface.DEFAULT,b==selected?Typeface.BOLD:Typeface.NORMAL);b.setBackground(b==selected?round(Color.rgb(10,45,61),14,0):round(Color.TRANSPARENT,14,0));}}
    private TextView txt(String s,int sp,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.END);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setSingleLine(true);e.setPadding(dp(10),dp(10),dp(10),dp(10));e.setBackground(round(CARD2,12,STROKE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(5),0,dp(5));e.setLayoutParams(p);return e;}
    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(Color.rgb(2,31,38));b.setGravity(Gravity.CENTER);b.setBackground(round(ACCENT,15,0));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.setMargins(0,dp(9),0,0);b.setLayoutParams(p);return b;}
    private Button normal(String s){Button b=primary(s);b.setTextColor(TEXT);b.setBackground(round(Color.rgb(21,57,75),15,STROKE));return b;}
    private Button danger(String s){Button b=primary(s);b.setTextColor(Color.WHITE);b.setBackground(round(Color.rgb(145,49,61),15,0));return b;}
    private GradientDrawable round(int fill,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}
    private String duration(long s){s=Math.max(0,s);return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);}
    private void toast(String s){Toast.makeText(this,s==null?"خطا":s,Toast.LENGTH_LONG).show();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    @Override protected void onResume(){super.onResume();if(data!=null&&signed())load(false);}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
