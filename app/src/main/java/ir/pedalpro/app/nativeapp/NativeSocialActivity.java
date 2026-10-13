package ir.pedalpro.app.nativeapp;

import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NativeSocialActivity extends NativeScreenBase {
    @Override protected String screenTitle(){return "اجتماعی و مسیرهای پیشنهادی";}

    @Override protected void onScreenReady(){load();}

    private void load(){
        setSubtitle("در حال دریافت فید اجتماعی…",MUTED);
        io.execute(()->{
            try{
                Map<String,Object> p=new HashMap<>();p.put("limit",40);
                JSONObject feed=api.get("pp_social_feed",p);
                JSONObject rec=api.get("pp_route_recommendations");
                main.post(()->render(feed.optJSONArray("items"),rec.optJSONArray("items")));
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا",RED);addCard("خطا",e.getMessage());});
            }
        });
    }

    private void render(JSONArray feed,JSONArray rec){
        content.removeAllViews();
        setSubtitle("Follow · Kudos · Route discovery",GREEN);

        if(rec!=null&&rec.length()>0){
            LinearLayout h=card();h.addView(text("مسیرهای پیشنهادی",17,true,TEXT));content.addView(h,cardLp());
            for(int i=0;i<Math.min(8,rec.length());i++){
                JSONObject r=rec.optJSONObject(i);if(r==null)continue;
                LinearLayout box=card();
                box.addView(text(r.optString("title","مسیر پیشنهادی"),15,true,TEXT));
                box.addView(text(r.optString("owner_name","")+" · "+
                        String.format(Locale.US,"%.1f km · %.0f m صعود · %d kudos",
                                r.optDouble("distance_km",0),r.optDouble("ascent_m",0),r.optInt("kudos",0)),11,false,MUTED));
                int id=r.optInt("id",0);
                Button open=primary("نمایش مسیر");
                open.setOnClickListener(v->{
                    Intent x=new Intent(this,NativeRideDetailActivity.class);
                    x.putExtra(NativeRideDetailActivity.EXTRA_RIDE_ID,id);
                    startActivity(x);
                });
                box.addView(open);
                content.addView(box,cardLp());
            }
        }

        LinearLayout fh=card();fh.addView(text("فید فعالیت‌ها",17,true,TEXT));content.addView(fh,cardLp());
        if(feed==null||feed.length()==0){addCard("فید","فعلاً فعالیت عمومی‌ای برای نمایش وجود ندارد.");return;}
        for(int i=0;i<feed.length();i++){
            JSONObject r=feed.optJSONObject(i);if(r==null)continue;
            addFeedCard(r);
        }
    }

    private void addFeedCard(JSONObject r){
        LinearLayout box=card();
        String who=r.optString("display_name","عضو");
        box.addView(text(who,13,true,ACCENT));
        box.addView(text(r.optString("title","رکاب‌زنی"),16,true,TEXT));
        box.addView(text(String.format(Locale.US,"%.2f km · %.0f m صعود · %.1f km/h",
                r.optDouble("distance_km",0),r.optDouble("ascent_m",0),r.optDouble("max_speed_kmh",0)),11,false,MUTED));

        int rideId=r.optInt("id",0),userId=r.optInt("owner_user_id",0);
        boolean liked=r.optBoolean("viewer_kudos",false);
        Button kudos=normal((liked?"★ ":"☆ ")+"Kudos · "+r.optInt("kudos",0));
        kudos.setOnClickListener(v->toggleKudos(rideId,kudos));
        box.addView(kudos);

        if(userId>0){
            boolean following=r.optBoolean("viewer_follows",false);
            Button follow=normal(following?"لغو دنبال‌کردن":"دنبال‌کردن");
            follow.setOnClickListener(v->toggleFollow(userId,follow));
            box.addView(follow);
        }

        Button detail=primary("جزئیات مسیر");
        detail.setOnClickListener(v->{
            Intent x=new Intent(this,NativeRideDetailActivity.class);
            x.putExtra(NativeRideDetailActivity.EXTRA_RIDE_ID,rideId);
            startActivity(x);
        });
        box.addView(detail);
        content.addView(box,cardLp());
    }

    private void toggleKudos(int rideId,Button b){
        b.setEnabled(false);
        io.execute(()->{
            try{
                JSONObject d=api.post("pp_kudos_toggle",new JSONObject().put("ride_id",rideId));
                main.post(()->{
                    b.setEnabled(true);
                    b.setText((d.optBoolean("active")?"★ ":"☆ ")+"Kudos · "+d.optInt("count",0));
                });
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void toggleFollow(int userId,Button b){
        b.setEnabled(false);
        io.execute(()->{
            try{
                JSONObject d=api.post("pp_follow_toggle",new JSONObject().put("user_id",userId));
                main.post(()->{
                    b.setEnabled(true);
                    b.setText(d.optBoolean("following")?"لغو دنبال‌کردن":"دنبال‌کردن");
                });
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }
}
