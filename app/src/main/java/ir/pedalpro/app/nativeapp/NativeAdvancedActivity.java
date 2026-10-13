package ir.pedalpro.app.nativeapp;

import android.content.Intent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class NativeAdvancedActivity extends NativeScreenBase {
    private JSONObject training;

    @Override protected String screenTitle(){return "تحلیل حرفه‌ای";}

    @Override protected void onScreenReady(){load();}

    private void load(){
        setSubtitle("در حال دریافت Training / Segments / Leaderboard…",MUTED);
        io.execute(()->{
            try{
                JSONObject t=api.get("pp_training_summary");
                JSONObject club=api.get("pp_club_weekly");
                JSONObject seg=api.get("pp_segments_list");
                JSONObject pro=api.get("pp_pro_summary");
                main.post(()->render(t,club,seg,pro));
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا در دریافت تحلیل",RED);addCard("خطا",e.getMessage());});
            }
        });
    }

    private void render(JSONObject t,JSONObject club,JSONObject seg,JSONObject pro){
        content.removeAllViews();
        training=t.optJSONObject("training");
        if(training==null)training=t;
        setSubtitle("Training Load · Segments · Club Weekly",GREEN);

        LinearLayout load=card();
        load.addView(text("Training Load",18,true,TEXT));
        String loadText=String.format(Locale.US,
                "Acute 7d: %.1f\nChronic 28d: %.1f\nForm: %.1f\nA:C Ratio: %.2f\nStatus: %s",
                training.optDouble("acute_7d",0),
                training.optDouble("chronic_28d_weekly",0),
                training.optDouble("form",0),
                training.optDouble("acute_chronic_ratio",0),
                training.optString("status","—"));
        load.addView(text(loadText,13,false,MUTED));
        JSONObject sensors=training.optJSONObject("sensors");
        if(sensors!=null)load.addView(text(String.format(Locale.US,
                "Cadence %.0f rpm · Power %.0f W · Max %.0f W",
                sensors.optDouble("avg_cadence_rpm",0),sensors.optDouble("avg_power_w",0),sensors.optDouble("max_power_w",0)),12,false,ACCENT));
        content.addView(load,cardLp());

        JSONObject settings=training.optJSONObject("settings");
        LinearLayout set=card();
        set.addView(text("تنظیمات فیزیولوژیک",16,true,TEXT));
        EditText ftp=input("FTP (W)");
        EditText maxHr=input("Max HR");
        if(settings!=null){
            if(settings.optInt("ftp_w",0)>0)ftp.setText(String.valueOf(settings.optInt("ftp_w")));
            if(settings.optInt("max_hr",0)>0)maxHr.setText(String.valueOf(settings.optInt("max_hr")));
        }
        Button save=primary("ذخیره FTP / Max HR");
        save.setOnClickListener(v->saveSettings(ftp,maxHr,save));
        set.addView(ftp);set.addView(maxHr);set.addView(save);
        content.addView(set,cardLp());

        JSONArray weekly=club.optJSONArray("items");
        LinearLayout wh=card();wh.addView(text("رتبه‌بندی هفتگی باشگاه",17,true,TEXT));
        wh.addView(text("هفته شنبه تا جمعه",11,false,MUTED));content.addView(wh,cardLp());
        if(weekly!=null){
            for(int i=0;i<Math.min(20,weekly.length());i++){
                JSONObject x=weekly.optJSONObject(i);if(x==null)continue;
                LinearLayout row=card();
                row.addView(text((i+1)+". "+x.optString("display_name","عضو"),14,true,TEXT));
                row.addView(text(String.format(Locale.US,"%d رکاب · %.1f km · %.0f m صعود",
                        x.optInt("rides",0),x.optDouble("distance_m",0)/1000.0,x.optDouble("ascent_m",0)),11,false,MUTED));
                content.addView(row,cardLp());
            }
        }

        JSONArray segments=seg.optJSONArray("items");
        if(segments==null)segments=seg.optJSONArray("segments");
        LinearLayout sh=card();sh.addView(text("Segments",17,true,TEXT));content.addView(sh,cardLp());
        if(segments==null||segments.length()==0){
            addCard("Segments","هنوز Segment فعالی وجود ندارد.");
        }else{
            for(int i=0;i<segments.length();i++){
                JSONObject s=segments.optJSONObject(i);if(s==null)continue;
                LinearLayout row=card();
                row.addView(text(s.optString("name","Segment"),15,true,TEXT));
                row.addView(text(String.format(Locale.US,"%.2f km",s.optDouble("distance_m",0)/1000.0),11,false,MUTED));
                int id=s.optInt("id",0);
                Button leaderboard=primary("Leaderboard");
                leaderboard.setOnClickListener(v->{
                    Intent x=new Intent(this,NativeSegmentLeaderboardActivity.class);
                    x.putExtra(NativeSegmentLeaderboardActivity.EXTRA_SEGMENT_ID,id);
                    x.putExtra(NativeSegmentLeaderboardActivity.EXTRA_TITLE,s.optString("name","Segment"));
                    startActivity(x);
                });
                row.addView(leaderboard);
                content.addView(row,cardLp());
            }
        }

        Button heatmap=primary("Heatmap مسیرها");
        heatmap.setOnClickListener(v->startActivity(new Intent(this,NativeHeatmapActivity.class)));
        content.addView(heatmap);
    }

    private void saveSettings(EditText ftp,EditText hr,Button b){
        int ftpW=0,maxHr=0;
        try{ftpW=Integer.parseInt(ftp.getText().toString().trim());}catch(Exception ignored){}
        try{maxHr=Integer.parseInt(hr.getText().toString().trim());}catch(Exception ignored){}
        b.setEnabled(false);
        int finalFtp=ftpW,finalHr=maxHr;
        io.execute(()->{
            try{
                api.post("pp_training_settings_save",new JSONObject().put("ftp_w",finalFtp).put("max_hr",finalHr));
                main.post(()->{toast("تنظیمات ذخیره شد.");load();});
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }
}
