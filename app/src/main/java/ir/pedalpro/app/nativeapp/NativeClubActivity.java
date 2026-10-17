package ir.pedalpro.app.nativeapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

public final class NativeClubActivity extends NativeScreenBase {
    @Override protected String screenTitle(){return "باشگاه و چالش‌ها";}

    @Override protected void onScreenReady(){
        load();
    }

    private void load(){
        setSubtitle("در حال دریافت اطلاعات باشگاه…",MUTED);
        io.execute(()->{
            try{
                JSONObject d=api.bootstrap();
                main.post(()->render(d));
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا در دریافت اطلاعات",RED);addCard("خطا",e.getMessage());});
            }
        });
    }

    private void render(JSONObject d){
        content.removeAllViews();
        JSONArray members=d.optJSONArray("members");
        JSONArray challenges=d.optJSONArray("challenges");
        JSONArray events=d.optJSONArray("events");
        setSubtitle((members==null?0:members.length())+" عضو · "+(challenges==null?0:challenges.length())+" چالش",GREEN);

        LinearLayout summary=card();
        summary.addView(text("باشگاه PedalPro",18,true,TEXT));
        summary.addView(text("اعضا: "+(members==null?0:members.length())+"   ·   برنامه‌ها: "+(events==null?0:events.length()),12,false,MUTED));
        content.addView(summary,cardLp());

        if(challenges==null||challenges.length()==0){
            addCard("چالش‌ها","چالش فعالی وجود ندارد.");
        }else{
            for(int i=0;i<challenges.length();i++){
                JSONObject c=challenges.optJSONObject(i);if(c==null)continue;
                addChallenge(c);
            }
        }

        if(members!=null&&members.length()>0){
            LinearLayout title=card();title.addView(text("اعضای باشگاه",17,true,TEXT));content.addView(title,cardLp());
            for(int i=0;i<Math.min(60,members.length());i++){
                JSONObject m=members.optJSONObject(i);if(m==null)continue;
                String name=m.optString("display_name",m.optString("username","عضو"));
                String meta="@"+m.optString("username","")+" · "+m.optString("role","member");
                LinearLayout row=card();
                row.addView(text(name,14,true,TEXT));
                row.addView(text(meta,11,false,MUTED));
                content.addView(row,cardLp());
            }
        }
    }

    private void addChallenge(JSONObject c){
        LinearLayout box=card();
        boolean joined=c.optBoolean("joined_by_me",false)||c.optInt("joined_by_me",0)==1;
        String status=c.optString("status","planned");
        String metric=c.optString("metric","distance");
        String metricFa="distance".equals(metric)?"مسافت":"elevation".equals(metric)?"ارتفاع":"تعداد رکاب";
        String title=(c.optInt("is_official",0)==1?"⭐ ":"")+c.optString("title","چالش");
        box.addView(text(title,16,true,TEXT));
        String meta="وضعیت: "+status+" · هدف: "+metricFa;
        if(c.has("target_value")&&!c.isNull("target_value"))meta+=" "+c.optString("target_value","");
        meta+=" · همراهان: "+c.optInt("joiners_count",0);
        box.addView(text(meta,11,false,MUTED));
        String desc=c.optString("description","");
        if(!desc.isBlank())box.addView(text(desc,12,false,MUTED));

        int id=c.optInt("id",0);
        boolean allow=c.optBoolean("allow_join",true)||c.optInt("allow_join",1)==1;
        if(id>0&&allow){
            Button b=primary(joined?"لغو همراهی":"همراه شدن");
            b.setOnClickListener(v->join(id,!joined,b));
            box.addView(b);
        }
        content.addView(box,cardLp());
    }

    private void join(int id,boolean join,Button b){
        b.setEnabled(false);
        io.execute(()->{
            try{
                api.post("join_challenge",new JSONObject().put("challenge_id",id).put("join",join));
                JSONObject d=api.bootstrap();
                main.post(()->{toast(join?"به چالش ملحق شدید.":"همراهی لغو شد.");render(d);});
            }catch(Exception e){
                main.post(()->{b.setEnabled(true);toast(e.getMessage());});
            }
        });
    }
}
