package ir.pedalpro.app.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NativeSegmentLeaderboardActivity extends NativeScreenBase {
    public static final String EXTRA_SEGMENT_ID="segment_id";
    public static final String EXTRA_TITLE="title";
    private int segmentId;

    @Override protected String screenTitle(){return getIntent().getStringExtra(EXTRA_TITLE)==null?"Segment Leaderboard":getIntent().getStringExtra(EXTRA_TITLE);}

    @Override protected void onScreenReady(){
        segmentId=getIntent().getIntExtra(EXTRA_SEGMENT_ID,0);
        if(segmentId<=0){addCard("خطا","Segment معتبر نیست.");return;}
        load();
    }

    private void load(){
        setSubtitle("در حال دریافت رکوردها…",MUTED);
        io.execute(()->{
            try{
                Map<String,Object> q=new HashMap<>();q.put("segment_id",segmentId);
                JSONObject d=api.get("pp_segment_leaderboard",q);
                main.post(()->render(d.optJSONArray("items")));
            }catch(Exception e){main.post(()->{setSubtitle("خطا",RED);addCard("خطا",e.getMessage());});}
        });
    }

    private void render(JSONArray items){
        content.removeAllViews();
        setSubtitle((items==null?0:items.length())+" رکورد",GREEN);
        if(items==null||items.length()==0){addCard("Leaderboard","هنوز رکوردی وجود ندارد.");return;}
        for(int i=0;i<items.length();i++){
            JSONObject x=items.optJSONObject(i);if(x==null)continue;
            String name=x.optString("display_name",x.optString("user_name","عضو"));
            long sec=x.optLong("elapsed_seconds",x.optLong("duration_seconds",0));
            String t=String.format(Locale.US,"%02d:%02d",sec/60,sec%60);
            addCard((i+1)+". "+name,t+" · "+String.format(Locale.US,"%.1f km/h",x.optDouble("avg_speed_kmh",0)));
        }
    }
}
