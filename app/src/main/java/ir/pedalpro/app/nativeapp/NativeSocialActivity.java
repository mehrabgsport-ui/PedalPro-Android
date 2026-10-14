package ir.pedalpro.app.nativeapp;

import android.content.Intent;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class NativeSocialActivity extends NativeScreenBase {
    public static final String EXTRA_SECTION="section";
    private String section="social";

    @Override protected String screenTitle(){
        return "recommend".equals(section)?"مسیرهای پیشنهادی":"اجتماعی";
    }

    @Override protected void onScreenReady(){
        String requested=getIntent().getStringExtra(EXTRA_SECTION);
        if(requested!=null&&!requested.isBlank())section=requested;
        load();
    }

    private void load(){
        content.removeAllViews();
        if("recommend".equals(section))loadRecommendations();
        else loadFeed();
    }

    private void loadFeed(){
        setSubtitle("در حال دریافت فید اجتماعی…",MUTED);
        io.execute(()->{
            try{
                JSONObject d=api.get("pp_social_feed");
                JSONArray rows=d.optJSONArray("items");
                main.post(()->renderFeed(rows));
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا",RED);addCard("خطا",e.getMessage());});
            }
        });
    }

    private void renderFeed(JSONArray rows){
        content.removeAllViews();
        setSubtitle("",MUTED);
        if(rows==null||rows.length()==0){
            addCard("فید اجتماعی","هنوز فعالیتی برای فید وجود ندارد.");
            return;
        }
        for(int i=0;i<rows.length();i++){
            JSONObject x=rows.optJSONObject(i);
            if(x!=null)content.addView(socialCard(x),cardLp());
        }
        VazirmatnLoader.apply(this,content);
    }

    private LinearLayout socialCard(JSONObject x){
        LinearLayout card=card();
        card.setPadding(dp(14),dp(14),dp(14),dp(14));

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);

        LinearLayout person=new LinearLayout(this);
        person.setOrientation(LinearLayout.HORIZONTAL);
        person.setGravity(android.view.Gravity.CENTER_VERTICAL);
        person.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);

        String who=x.optString("display_name","پ");
        TextView avatar=text(who.isBlank()?"پ":who.substring(0,1),16,true,TEXT);
        avatar.setGravity(android.view.Gravity.CENTER);
        avatar.setBackground(round(Color.rgb(17,34,51),14,0));
        person.addView(avatar,new LinearLayout.LayoutParams(dp(42),dp(42)));

        LinearLayout personText=new LinearLayout(this);
        personText.setOrientation(LinearLayout.VERTICAL);
        personText.setGravity(android.view.Gravity.END);
        personText.addView(text(who,13,true,TEXT));
        String rawDate=x.optString("ended_at",x.optString("started_at",""));
        personText.addView(text(JalaliDate.format(rawDate,true),10,false,MUTED));
        LinearLayout.LayoutParams pt=new LinearLayout.LayoutParams(-2,-2);
        pt.setMargins(dp(10),0,0,0);
        person.addView(personText,pt);

        head.addView(person,new LinearLayout.LayoutParams(0,-2,1f));

        JSONObject bootUser=null;
        int me=0;
        try{
            String userJson=session.userJson();
            if(userJson!=null&&!userJson.isBlank())bootUser=new JSONObject(userJson);
            me=bootUser==null?0:bootUser.optInt("id",0);
        }catch(Exception ignored){}
        int owner=x.optInt("owner_user_id",0);
        if(me>0&&owner>0&&owner!=me){
            Button follow=normal(x.optBoolean("viewer_follows",false)?"دنبال می‌کنید":"دنبال کردن");
            follow.setOnClickListener(v->toggleFollow(owner,follow));
            head.addView(follow,new LinearLayout.LayoutParams(dp(118),dp(42)));
        }
        card.addView(head);

        TextView title=text(x.optString("title","رکاب‌زنی"),16,true,TEXT);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
        titleLp.setMargins(0,dp(12),0,dp(6));
        card.addView(title,titleLp);

        LinearLayout stats=new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        stats.setGravity(android.view.Gravity.CENTER);
        stats.addView(stat("مسافت",String.format(Locale.US,"%.1f km",x.optDouble("distance_km",0))),new LinearLayout.LayoutParams(0,-2,1f));
        stats.addView(stat("صعود",Math.round(x.optDouble("ascent_m",0))+" m"),new LinearLayout.LayoutParams(0,-2,1f));
        stats.addView(stat("حداکثر سرعت",String.format(Locale.US,"%.1f",x.optDouble("max_speed_kmh",0))),new LinearLayout.LayoutParams(0,-2,1f));
        stats.addView(stat("Kudos",String.valueOf(x.optInt("kudos",0))),new LinearLayout.LayoutParams(0,-2,1f));
        card.addView(stats);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        int rideId=x.optInt("id",0);
        Button kudos=normal("👍 Kudos");
        if(x.optBoolean("viewer_kudos",false)){
            kudos.setTextColor(Color.rgb(5,40,55));
            kudos.setBackground(round(Color.rgb(20,223,237),17,0));
        }
        kudos.setOnClickListener(v->toggleKudos(rideId,kudos));
        Button detail=normal("مسیر و دیدگاه‌ها");
        detail.setOnClickListener(v->openRide(rideId));
        actions.addView(kudos,new LinearLayout.LayoutParams(0,dp(46),1f));
        LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(0,dp(46),1f);
        detailLp.setMargins(dp(8),0,0,0);
        actions.addView(detail,detailLp);
        LinearLayout.LayoutParams actionsLp=new LinearLayout.LayoutParams(-1,-2);
        actionsLp.setMargins(0,dp(10),0,0);
        card.addView(actions,actionsLp);
        return card;
    }

    private LinearLayout stat(String label,String value){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        box.setPadding(dp(5),dp(9),dp(5),dp(9));
        box.setBackground(round(Color.argb(10,255,255,255),12,0));
        TextView l=text(label,9,false,Color.rgb(142,166,184));l.setGravity(android.view.Gravity.CENTER);
        TextView v=text(value,12,true,TEXT);v.setGravity(android.view.Gravity.CENTER);
        box.addView(l);box.addView(v);
        return box;
    }

    private void loadRecommendations(){
        setSubtitle("در حال دریافت مسیرهای پیشنهادی…",MUTED);
        io.execute(()->{
            try{
                JSONObject d=api.get("pp_route_recommendations");
                main.post(()->renderRecommendations(d));
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا",RED);addCard("خطا",e.getMessage());});
            }
        });
    }

    private void renderRecommendations(JSONObject d){
        content.removeAllViews();
        setSubtitle("",MUTED);
        LinearLayout notice=card();
        notice.addView(text("پیشنهادها با توجه به رکاب‌زنی‌های عمومی PedalPro، محبوبیت، صعود و مسافت معمول شما رتبه‌بندی شده‌اند. مسافت معمول شما: "+
                String.format(Locale.US,"%.1f km",d.optDouble("median_km",0)),12,false,TEXT));
        content.addView(notice,cardLp());

        JSONArray rows=d.optJSONArray("items");
        if(rows==null||rows.length()==0){
            addCard("مسیرهای پیشنهادی","مسیر عمومی کافی برای پیشنهاد وجود ندارد.");
            return;
        }
        for(int i=0;i<rows.length();i++){
            JSONObject x=rows.optJSONObject(i);if(x==null)continue;
            LinearLayout row=card();
            row.setPadding(dp(12),dp(12),dp(12),dp(12));
            row.addView(text((i+1)+". "+x.optString("title","مسیر"),14,true,TEXT));
            row.addView(text(x.optString("owner_name","")+" · "+
                    String.format(Locale.US,"%.1f km · صعود %.0f m · 👍 %d",
                            x.optDouble("distance_km",0),x.optDouble("ascent_m",0),x.optInt("kudos",0)),11,false,MUTED));
            Button open=primary("مشاهده مسیر");
            int rideId=x.optInt("id",0);
            open.setOnClickListener(v->openRide(rideId));
            row.addView(open);
            content.addView(row,cardLp());
        }
        VazirmatnLoader.apply(this,content);
    }

    private void openRide(int rideId){
        Intent x=new Intent(this,NativeRideDetailActivity.class);
        x.putExtra(NativeRideDetailActivity.EXTRA_RIDE_ID,rideId);
        startActivity(x);
    }

    private void toggleKudos(int rideId,Button b){
        b.setEnabled(false);
        io.execute(()->{
            try{
                JSONObject d=api.post("pp_kudos_toggle",new JSONObject().put("ride_id",rideId));
                main.post(()->{
                    b.setEnabled(true);
                    b.setText("👍 Kudos");
                    if(d.optBoolean("active")){
                        b.setTextColor(Color.rgb(5,40,55));
                        b.setBackground(round(Color.rgb(20,223,237),17,0));
                    }else{
                        b.setTextColor(TEXT);
                        b.setBackground(round(Color.argb(18,255,255,255),17,Color.argb(46,255,255,255)));
                    }
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
                    b.setText(d.optBoolean("following")?"دنبال می‌کنید":"دنبال کردن");
                });
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }
}
