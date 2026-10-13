package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.util.Locale;

import ir.pedalpro.app.TrackingService;

public final class LowPowerRideActivity extends Activity {
    public static final String EXTRA_RIDE_ID="ride_id";
    public static final String EXTRA_BASE_SECONDS="base_seconds";
    public static final String EXTRA_DISTANCE_KM="distance_km";

    private final Handler main=new Handler(Looper.getMainLooper());
    private int rideId;
    private long baseSeconds;
    private long anchor;
    private double distanceKm;
    private boolean paused;
    private TextView time,speed,distance,pauseLabel;
    private SessionStore session;

    private final Runnable tick=new Runnable(){
        @Override public void run(){
            long sec=baseSeconds;
            if(!paused&&anchor>0)sec+=Math.max(0,(SystemClock.elapsedRealtime()-anchor)/1000L);
            time.setText(format(sec));
            main.postDelayed(this,1000);
        }
    };

    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(i==null||!TrackingService.ACTION_UPDATE.equals(i.getAction()))return;
            String p=i.getStringExtra(TrackingService.EXTRA_PAYLOAD);if(p==null)return;
            try{
                JSONObject j=new JSONObject(p);
                speed.setText(String.format(Locale.US,"%.1f",Math.max(0,j.optDouble("speed_mps",0)*3.6)));
                String response=j.optString("response","");
                if(!response.isBlank()){
                    JSONObject r=new JSONObject(response).optJSONObject("ride");
                    if(r!=null){distanceKm=r.optDouble("distance_km",distanceKm);distance.setText(String.format(Locale.US,"%.2f",distanceKm));}
                }
            }catch(Exception ignored){}
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        session=new SessionStore(this);
        rideId=getIntent().getIntExtra(EXTRA_RIDE_ID,0);
        baseSeconds=getIntent().getLongExtra(EXTRA_BASE_SECONDS,0);
        distanceKm=getIntent().getDoubleExtra(EXTRA_DISTANCE_KM,0);
        anchor=SystemClock.elapsedRealtime();
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        build();
        register();
        main.post(tick);
    }

    private void build(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{androidx.core.graphics.Insets b=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(0,b.top,0,b.bottom);return insets;});

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setGravity(Gravity.CENTER);body.setPadding(dp(22),dp(30),dp(22),dp(30));
        TextView low=new TextView(this);low.setText("حالت کم‌مصرف");low.setTextColor(Color.rgb(115,130,135));low.setTextSize(12);low.setGravity(Gravity.CENTER);body.addView(low);

        time=metric("00:00:00",54);speed=metric("0.0",76);distance=metric(String.format(Locale.US,"%.2f",distanceKm),58);
        body.addView(label("زمان"));body.addView(time);
        body.addView(label("سرعت  km/h"));body.addView(speed);
        body.addView(label("مسافت  km"));body.addView(distance);

        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setGravity(Gravity.CENTER);controls.setPadding(0,dp(28),0,0);
        LinearLayout pause=control(PedalIconView.Icon.PAUSE,"توقف");pauseLabel=(TextView)pause.getChildAt(1);pause.setOnClickListener(v->togglePause());
        LinearLayout back=control(PedalIconView.Icon.BACK,"بازگشت");back.setOnClickListener(v->finish());
        controls.addView(pause,new LinearLayout.LayoutParams(dp(118),dp(82)));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(118),dp(82));bp.setMargins(dp(24),0,0,0);controls.addView(back,bp);
        body.addView(controls);

        root.addView(body,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        DisplayFontLoader.applyAnton(this,time,speed,distance);VazirmatnLoader.apply(this,root);
    }

    private TextView metric(String value,int sp){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(false);return t;}
    private TextView label(String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.rgb(132,148,152));t.setTextSize(11);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(16),0,0);return t;}
    private LinearLayout control(PedalIconView.Icon icon,String title){LinearLayout h=new LinearLayout(this);h.setOrientation(LinearLayout.VERTICAL);h.setGravity(Gravity.CENTER);PedalIconView i=new PedalIconView(this,icon,Color.WHITE);TextView l=label(title);l.setPadding(0,dp(2),0,0);h.addView(i,new LinearLayout.LayoutParams(dp(38),dp(38)));h.addView(l);h.setClickable(true);h.setContentDescription(title);return h;}

    private void togglePause(){
        if(rideId<=0)return;
        if(!paused){
            Intent stop=new Intent(this,TrackingService.class);stop.setAction(TrackingService.ACTION_STOP);startService(stop);
            long elapsed=anchor>0?Math.max(0,(SystemClock.elapsedRealtime()-anchor)/1000L):0;baseSeconds+=elapsed;anchor=0;paused=true;speed.setText("0.0");
            PedalIconView icon=(PedalIconView)((LinearLayout)pauseLabel.getParent()).getChildAt(0);icon.setIcon(PedalIconView.Icon.PLAY);pauseLabel.setText("ادامه");
        }else{
            Intent start=new Intent(this,TrackingService.class);start.setAction(TrackingService.ACTION_START);start.putExtra("ride_id",rideId);start.putExtra("csrf",session.csrf());start.putExtra("max_accuracy",25d);start.putExtra("max_speed_kmh",100d);
            ContextCompat.startForegroundService(this,start);paused=false;anchor=SystemClock.elapsedRealtime();
            PedalIconView icon=(PedalIconView)((LinearLayout)pauseLabel.getParent()).getChildAt(0);icon.setIcon(PedalIconView.Icon.PAUSE);pauseLabel.setText("توقف");
        }
    }

    private void register(){IntentFilter f=new IntentFilter(TrackingService.ACTION_UPDATE);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);}
    private String format(long sec){return String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec%3600)/60,sec%60);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){main.removeCallbacks(tick);try{unregisterReceiver(receiver);}catch(Exception ignored){}super.onDestroy();}
}
