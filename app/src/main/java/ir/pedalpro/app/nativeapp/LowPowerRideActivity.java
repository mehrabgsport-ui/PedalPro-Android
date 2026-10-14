package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
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

/**
 * OLED low-power ride surface.
 * Presentation only: tracking, GPS filtering and distance computation remain owned by TrackingService/server.
 */
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
    private TextView time;
    private TextView speed;
    private TextView distance;
    private PedalIconView pauseIcon;
    private SessionStore session;

    private final Runnable tick=new Runnable(){
        @Override public void run(){
            long sec=baseSeconds;
            if(!paused&&anchor>0){
                sec+=Math.max(0,(SystemClock.elapsedRealtime()-anchor)/1000L);
            }
            if(time!=null)time.setText(format(sec));
            main.postDelayed(this,1000L);
        }
    };

    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(i==null||!TrackingService.ACTION_UPDATE.equals(i.getAction()))return;
            String payload=i.getStringExtra(TrackingService.EXTRA_PAYLOAD);
            if(payload==null)return;
            try{
                JSONObject j=new JSONObject(payload);
                if(!paused&&speed!=null){
                    speed.setText(String.format(Locale.US,"%.1f",Math.max(0,j.optDouble("speed_mps",0)*3.6)));
                }
                String response=j.optString("response","");
                if(!response.isBlank()){
                    JSONObject r=new JSONObject(response).optJSONObject("ride");
                    if(r!=null){
                        distanceKm=r.optDouble("distance_km",distanceKm);
                        if(distance!=null)distance.setText(String.format(Locale.US,"%.2f",distanceKm));
                    }
                }
            }catch(Exception ignored){}
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        session=new SessionStore(this);
        rideId=getIntent().getIntExtra(EXTRA_RIDE_ID,0);
        baseSeconds=Math.max(0,getIntent().getLongExtra(EXTRA_BASE_SECONDS,0));
        distanceKm=Math.max(0,getIntent().getDoubleExtra(EXTRA_DISTANCE_KM,0));
        anchor=SystemClock.elapsedRealtime();

        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        build();
        register();
        main.post(tick);
    }

    private void build(){
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setKeepScreenOn(true);

        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });

        TextView back=new TextView(this);
        back.setText("↩");
        back.setTextColor(Color.WHITE);
        back.setTextSize(32);
        back.setGravity(Gravity.CENTER);
        back.setIncludeFontPadding(false);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("بازگشت");
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v->finish());
        FrameLayout.LayoutParams backLp=new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.TOP|Gravity.END);
        backLp.setMargins(0,dp(8),dp(10),0);
        root.addView(back,backLp);

        LinearLayout metrics=new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);
        metrics.setGravity(Gravity.CENTER_HORIZONTAL);
        metrics.setPadding(dp(18),0,dp(18),0);

        metrics.addView(metricLabel("زمان"));
        time=metric("00:00:00",58);
        metrics.addView(time,new LinearLayout.LayoutParams(-1,dp(82)));

        metrics.addView(metricLabel("سرعت  km/h"));
        speed=metric("0.0",86);
        metrics.addView(speed,new LinearLayout.LayoutParams(-1,dp(110)));

        metrics.addView(metricLabel("مسافت  km"));
        distance=metric(String.format(Locale.US,"%.2f",distanceKm),66);
        metrics.addView(distance,new LinearLayout.LayoutParams(-1,dp(94)));

        FrameLayout.LayoutParams metricsLp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        metricsLp.topMargin=dp(70);
        root.addView(metrics,metricsLp);

        FrameLayout pause=new FrameLayout(this);
        pause.setBackgroundColor(Color.TRANSPARENT);
        pause.setClickable(true);
        pause.setFocusable(true);
        pause.setContentDescription("توقف");
        pauseIcon=new PedalIconView(this,PedalIconView.Icon.PAUSE,Color.WHITE);
        pause.addView(pauseIcon,new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.CENTER));
        pause.setOnClickListener(v->togglePause());
        FrameLayout.LayoutParams pauseLp=new FrameLayout.LayoutParams(dp(86),dp(72),Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);
        pauseLp.bottomMargin=dp(28);
        root.addView(pause,pauseLp);

        setContentView(root);
        DisplayFontLoader.applyAnton(this,time,speed,distance);
    }

    private TextView metric(String value,int sp){
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setLetterSpacing(.01f);
        return t;
    }

    private TextView metricLabel(String value){
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextColor(Color.rgb(142,150,154));
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void togglePause(){
        if(rideId<=0)return;

        if(!paused){
            Intent stop=new Intent(this,TrackingService.class);
            stop.setAction(TrackingService.ACTION_STOP);
            startService(stop);

            long elapsed=anchor>0?Math.max(0,(SystemClock.elapsedRealtime()-anchor)/1000L):0;
            baseSeconds+=elapsed;
            anchor=0;
            paused=true;
            speed.setText("0.0");
            pauseIcon.setIcon(PedalIconView.Icon.PLAY);
            pauseIcon.setContentDescription("ادامه");
        }else{
            Intent start=new Intent(this,TrackingService.class);
            start.setAction(TrackingService.ACTION_START);
            start.putExtra("ride_id",rideId);
            start.putExtra("csrf",session.csrf());
            start.putExtra("max_accuracy",25d);
            start.putExtra("max_speed_kmh",100d);
            ContextCompat.startForegroundService(this,start);

            paused=false;
            anchor=SystemClock.elapsedRealtime();
            pauseIcon.setIcon(PedalIconView.Icon.PAUSE);
            pauseIcon.setContentDescription("توقف");
        }
    }

    private void register(){
        IntentFilter f=new IntentFilter(TrackingService.ACTION_UPDATE);
        ContextCompat.registerReceiver(this,receiver,f,ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private String format(long sec){
        return String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec%3600)/60,sec%60);
    }

    private int dp(int v){
        return Math.round(v*getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy(){
        main.removeCallbacks(tick);
        try{unregisterReceiver(receiver);}catch(Exception ignored){}
        super.onDestroy();
    }
}
