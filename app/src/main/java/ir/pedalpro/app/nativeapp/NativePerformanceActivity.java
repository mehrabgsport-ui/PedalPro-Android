package ir.pedalpro.app.nativeapp;

import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public final class NativePerformanceActivity extends NativeScreenBase {
    @Override protected String screenTitle(){return "عملکرد من";}

    @Override protected void onScreenReady(){load();}

    private void load(){
        setSubtitle("در حال محاسبه عملکرد…",MUTED);
        io.execute(()->{
            try{
                JSONObject d=api.bootstrap();
                main.post(()->render(d));
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا",RED);addCard("خطا",e.getMessage());});
            }
        });
    }

    private void render(JSONObject d){
        content.removeAllViews();
        JSONObject user=d.optJSONObject("user");
        int uid=user==null?0:user.optInt("id",0);
        JSONArray rides=d.optJSONArray("rides");
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime weekStart=now.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).toLocalDate().atStartOfDay();
        LocalDateTime monthStart=now.withDayOfMonth(1).toLocalDate().atStartOfDay();

        Stats all=new Stats(),week=new Stats(),month=new Stats();
        if(rides!=null){
            for(int i=0;i<rides.length();i++){
                JSONObject r=rides.optJSONObject(i);if(r==null)continue;
                if(!"completed".equals(r.optString("status")))continue;
                if(uid>0&&r.optInt("owner_user_id",uid)!=uid)continue;
                LocalDateTime at=parseDate(r.optString("started_at",""));
                accumulate(all,r);
                if(at!=null&&!at.isBefore(monthStart))accumulate(month,r);
                if(at!=null&&!at.isBefore(weekStart))accumulate(week,r);
            }
        }

        setSubtitle("هفته: شنبه تا جمعه",GREEN);
        addStats("این هفته",week);
        addStats("این ماه",month);
        addStats("کل فعالیت‌ها",all);

        if(all.rides>0){
            addCard("میانگین هر رکاب",
                    String.format(Locale.US,"%.1f km · %s · %.0f m صعود",
                            all.km/all.rides,formatSec((long)(all.sec/(double)all.rides)),all.ascent/all.rides));
            addCard("بهترین رکورد سرعت",
                    String.format(Locale.US,"%.1f km/h",all.maxSpeed));
        }else addCard("عملکرد","هنوز رکاب‌زنی تکمیل‌شده‌ای برای تحلیل وجود ندارد.");
    }

    private void addStats(String title,Stats s){
        LinearLayout box=card();
        box.addView(text(title,17,true,TEXT));
        box.addView(text(
                String.format(Locale.US,"%d رکاب · %.1f km · %s · %.0f m صعود",
                        s.rides,s.km,formatSec(s.sec),s.ascent),
                12,false,MUTED));
        content.addView(box,cardLp());
    }

    private void accumulate(Stats s,JSONObject r){
        s.rides++;
        s.km+=r.optDouble("distance_km",r.optDouble("distance_m",0)/1000.0);
        s.sec+=r.optLong("duration_seconds",0);
        s.ascent+=r.optDouble("ascent_m",0);
        s.maxSpeed=Math.max(s.maxSpeed,r.optDouble("max_speed_kmh",0));
    }

    private LocalDateTime parseDate(String x){
        if(x==null||x.isBlank())return null;
        String v=x.trim().replace('T',' ');
        if(v.endsWith("Z"))v=v.substring(0,v.length()-1);
        String[] patterns={"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm","yyyy-MM-dd"};
        for(String p:patterns){
            try{
                if("yyyy-MM-dd".equals(p))return java.time.LocalDate.parse(v,DateTimeFormatter.ofPattern(p)).atStartOfDay();
                return LocalDateTime.parse(v,DateTimeFormatter.ofPattern(p));
            }catch(Exception ignored){}
        }
        return null;
    }

    private String formatSec(long sec){
        long s=Math.max(0,sec),h=s/3600,m=(s%3600)/60;
        return h>0?h+"h "+m+"m":m+"m";
    }

    private static final class Stats{
        int rides;double km,ascent,maxSpeed;long sec;
    }
}
