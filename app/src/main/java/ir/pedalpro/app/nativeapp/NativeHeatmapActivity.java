package ir.pedalpro.app.nativeapp;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class NativeHeatmapActivity extends NativeScreenBase {
    public static final String EXTRA_SCOPE="scope";
    private NativeHeatmapView map;
    private TextView info;
    private Button personal;
    private Button club;
    private String scope="personal";

    @Override protected String screenTitle(){
        return "global".equals(scope)?"Heatmap عمومی PedalPro":"Heatmap";
    }

    @Override protected void onScreenReady(){
        String requested=getIntent().getStringExtra(EXTRA_SCOPE);
        if(requested!=null&&!requested.isBlank())scope=requested;
        renderShell();
    }

    private void renderShell(){
        content.removeAllViews();
        setSubtitle("",MUTED);

        if("global".equals(scope)){
            TextView notice=notice("Heatmap عمومی PedalPro فقط از رکاب‌زنی‌های عمومی کاربران ساخته می‌شود.");
            content.addView(notice,cardLp());
        }else{
            LinearLayout actions=new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
            actions.setGravity(Gravity.CENTER);
            personal=tabButton("Heatmap شخصی","personal");
            club=tabButton("Heatmap باشگاه","club");
            actions.addView(personal,new LinearLayout.LayoutParams(0,dp(46),1f));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1f);cp.setMargins(dp(8),0,0,0);
            actions.addView(club,cp);
            content.addView(actions,cardLp());
            applyTabState();
        }

        map=new NativeHeatmapView(this);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,Math.max(dp(360),Math.round(getResources().getDisplayMetrics().heightPixels*.54f)));
        content.addView(map,mp);

        info=text("در حال آماده‌سازی…",11,false,MUTED);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.setMargins(0,dp(8),0,0);
        content.addView(info,ip);
        load(scope);
    }

    private Button tabButton(String label,String target){
        Button b=normal(label);
        b.setOnClickListener(v->{scope=target;applyTabState();load(scope);});
        return b;
    }

    private void applyTabState(){
        styleTab(personal,"personal".equals(scope));
        styleTab(club,"club".equals(scope));
    }

    private void styleTab(Button b,boolean active){
        if(b==null)return;
        if(active){
            b.setTextColor(Color.rgb(5,34,53));
            android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(20,223,237),Color.rgb(36,185,237)});
            g.setCornerRadius(dp(17));
            b.setBackground(g);
        }else{
            b.setTextColor(TEXT);
            b.setBackground(round(Color.argb(18,255,255,255),17,Color.argb(46,255,255,255)));
        }
    }

    private void load(String targetScope){
        if(info!=null)info.setText("در حال آماده‌سازی…");
        io.execute(()->{
            try{
                JSONObject d;
                JSONArray rows;
                if("global".equals(targetScope)){
                    d=api.get("pp_global_heatmap");
                    rows=d.optJSONArray("routes");
                }else{
                    Map<String,Object> q=new HashMap<>();q.put("scope",targetScope);
                    d=api.get("pp_heatmap",q);
                    rows=d.optJSONArray("routes");
                }
                JSONArray finalRows=rows==null?new JSONArray():rows;
                main.post(()->{
                    if(map!=null)map.setRoutes(finalRows);
                    if(info!=null){
                        if("global".equals(targetScope)){
                            info.setText(finalRows.length()+" رکاب‌زنی عمومی PedalPro روی Heatmap نمایش داده شد.");
                        }else{
                            info.setText(finalRows.length()+" رکاب‌زنی در "+("club".equals(targetScope)?"Heatmap باشگاه":"Heatmap شخصی")+" نمایش داده شد. نوع و Provider نقشه بدون تغییر است.");
                        }
                    }
                });
            }catch(Exception e){
                main.post(()->{if(info!=null)info.setText(e.getMessage());});
            }
        });
    }

    private TextView notice(String body){
        TextView t=text(body,12,false,TEXT);
        t.setPadding(dp(12),dp(11),dp(12),dp(11));
        t.setBackground(round(Color.argb(8,255,255,255),14,Color.argb(28,255,255,255)));
        return t;
    }
}
