package ir.pedalpro.app.nativeapp;

import android.widget.Button;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class NativeHeatmapActivity extends NativeScreenBase {
    private NativeHeatmapView map;
    private LinearLayout buttons;

    @Override protected String screenTitle(){return "Heatmap مسیرها";}

    @Override protected void onScreenReady(){
        setSubtitle("Personal · Club · Global",GREEN);
        buttons=new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button personal=normal("شخصی");
        Button club=normal("باشگاه");
        Button global=normal("عمومی");
        personal.setOnClickListener(v->load("personal"));
        club.setOnClickListener(v->load("club"));
        global.setOnClickListener(v->load("global"));
        buttons.addView(personal,new LinearLayout.LayoutParams(0,dp(48),1f));
        buttons.addView(club,new LinearLayout.LayoutParams(0,dp(48),1f));
        buttons.addView(global,new LinearLayout.LayoutParams(0,dp(48),1f));
        content.addView(buttons,cardLp());

        map=new NativeHeatmapView(this);
        content.addView(map,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(430)));
        addCard("Heatmap Native","رندر مسیرها داخل Android انجام می‌شود و هیچ WebView/Leafletی در این صفحه وجود ندارد.");
        load("personal");
    }

    private void load(String scope){
        setSubtitle("در حال دریافت Heatmap "+scope+"…",MUTED);
        io.execute(()->{
            try{
                JSONObject d;
                if("global".equals(scope)){
                    d=api.get("pp_global_heatmap");
                }else{
                    Map<String,Object> q=new HashMap<>();
                    q.put("scope",scope);
                    d=api.get("pp_heatmap",q);
                }
                JSONArray rows=d.optJSONArray("items");
                if(rows==null)rows=d.optJSONArray("routes");
                if(rows==null)rows=d.optJSONArray("data");
                JSONArray finalRows=rows;
                main.post(()->{
                    map.setRoutes(finalRows);
                    setSubtitle(("personal".equals(scope)?"شخصی":"club".equals(scope)?"باشگاه":"عمومی")+" · "+(finalRows==null?0:finalRows.length())+" مسیر",GREEN);
                });
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا در دریافت Heatmap",RED);toast(e.getMessage());});
            }
        });
    }
}
