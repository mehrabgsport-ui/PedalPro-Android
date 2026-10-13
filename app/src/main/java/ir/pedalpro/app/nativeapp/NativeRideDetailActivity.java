package ir.pedalpro.app.nativeapp;

import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NativeRideDetailActivity extends NativeScreenBase {
    public static final String EXTRA_RIDE_ID="ride_id";
    private int rideId;

    @Override protected String screenTitle(){return "جزئیات رکاب‌زنی";}

    @Override protected void onScreenReady(){
        rideId=getIntent().getIntExtra(EXTRA_RIDE_ID,0);
        if(rideId<=0){addCard("خطا","شناسه رکاب معتبر نیست.");return;}
        load();
    }

    private void load(){
        setSubtitle("در حال دریافت مسیر…",MUTED);
        io.execute(()->{
            try{
                Map<String,Object> q=new HashMap<>();
                q.put("ride_id",rideId);
                q.put("point_mode","nav");
                JSONObject d=api.get("ride_details",q);
                JSONObject r=d.optJSONObject("ride");
                main.post(()->render(r));
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا",RED);addCard("خطا",e.getMessage());});
            }
        });
    }

    private void render(JSONObject r){
        content.removeAllViews();
        if(r==null){addCard("مسیر","این رکاب‌زنی خصوصی است یا در دسترس نیست.");return;}
        boolean live="active".equals(r.optString("status"));
        setSubtitle(live?"رکاب‌زنی زنده":"رکاب‌زنی ثبت‌شده",live?GREEN:MUTED);

        LinearLayout head=card();
        head.addView(text(r.optString("title","رکاب‌زنی"),18,true,TEXT));
        head.addView(text("👤 "+r.optString("owner_name",""),12,false,MUTED));
        head.addView(text(String.format(Locale.US,"%.2f km · %s · %.1f km/h · %.0f m صعود",
                r.optDouble("distance_km",r.optDouble("distance_m",0)/1000.0),
                formatDuration(r.optLong("duration_seconds",0)),
                r.optDouble("avg_speed_kmh",0),
                r.optDouble("ascent_m",0)),12,false,MUTED));
        content.addView(head,cardLp());

        NativeRouteView map=new NativeRouteView(this);
        map.setPoints(r.optJSONArray("points"));
        double lat=r.optDouble("last_lat",Double.NaN),lng=r.optDouble("last_lng",Double.NaN);
        if(Double.isFinite(lat)&&Double.isFinite(lng))map.setTarget(lat,lng);
        content.addView(map,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(300)));

        LinearLayout actions=card();
        if(live){
            Button nav=primary("مسیریابی زنده تا این رکاب‌زن");
            nav.setOnClickListener(v->{
                Intent i=new Intent(this,NativeLiveNavigationActivity.class);
                i.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"live");
                i.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID,rideId);
                i.putExtra(NativeLiveNavigationActivity.EXTRA_TITLE,r.optString("owner_name","رکاب‌زن"));
                startActivity(i);
            });
            actions.addView(nav);
        }

        if(Double.isFinite(lat)&&Double.isFinite(lng)){
            Button external=normal("باز کردن موقعیت در برنامه مسیریابی گوشی");
            external.setOnClickListener(v->{
                try{
                    Uri uri=Uri.parse("geo:"+lat+","+lng+"?q="+lat+","+lng);
                    startActivity(new Intent(Intent.ACTION_VIEW,uri));
                }catch(Exception e){toast("برنامه مسیریابی پیدا نشد.");}
            });
            actions.addView(external);
        }
        content.addView(actions,cardLp());

        JSONArray pts=r.optJSONArray("points");
        addCard("داده مسیر",(pts==null?0:pts.length())+" نقطه دریافت‌شده · نمایش کاملاً Native و بدون WebView");
    }

    private String formatDuration(long seconds){
        long s=Math.max(0,seconds);
        return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);
    }
}
