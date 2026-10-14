package ir.pedalpro.app.nativeapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NativeAdvancedActivity extends NativeScreenBase {
    private JSONObject boot;
    private String tab="overview";
    private JSONArray replayPoints=new JSONArray();
    private int replayIndex=0;
    private boolean replayRunning=false;
    private long replayDelayMs=650L;
    private NativeRouteView replayMap;
    private TextView replayMeta;
    private Button replayPlay;
    private android.widget.SeekBar replaySlider;

    private JSONArray flyPoints=new JSONArray();
    private int flyIndex=0;
    private boolean flyRunning=false;
    private int flyStep=1;
    private NativeRouteView flyMap;
    private TextView flyMeta;
    private Button flyPlay;

    private final Runnable replayTick=new Runnable(){
        @Override public void run(){
            if(!replayRunning||replayPoints==null||replayIndex>=replayPoints.length()){stopReplayNative();return;}
            replayPaintNative(replayIndex++);
            main.postDelayed(this,replayDelayMs);
        }
    };

    private final Runnable flyTick=new Runnable(){
        @Override public void run(){
            if(!flyRunning||flyPoints==null||flyIndex>=flyPoints.length()){stopFlyNative();return;}
            flyPaintNative(flyIndex);
            flyIndex+=Math.max(1,flyStep);
            main.postDelayed(this,80L);
        }
    };

    @Override protected String screenTitle(){return "PedalPro Pro";}

    @Override protected void onScreenReady(){
        String requested=getIntent().getStringExtra("section");
        if(requested!=null&&!requested.isBlank())tab=requested;
        loadBase();
    }

    private void loadBase(){
        setSubtitle("در حال دریافت اطلاعات…",MUTED);
        io.execute(()->{
            try{
                boot=api.bootstrap();
                main.post(this::render);
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا",RED);addCard("خطا",e.getMessage());});
            }
        });
    }

    private void render(){
        content.removeAllViews();
        setSubtitle("سگمنت، Heatmap، Training Load، Privacy Zones، Replay، رتبه‌بندی باشگاه، تحلیل هوا/ارتفاع و سنسورهای دوچرخه",MUTED);
        renderTabs();
        switch(tab){
            case "social": openSocial("social"); break;
            case "recommend": openSocial("recommend"); break;
            case "globalheat": openHeatmap("global"); break;
            case "flyover": renderRideData("Flyover 3D","pp_flyover"); break;
            case "discover": renderDiscover(); break;
            case "safety": openSafety("safety"); break;
            case "training": renderTraining(); break;
            case "privacy": openSafety("privacy"); break;
            case "replay": renderRideData("Replay فعالیت","pp_activity_replay"); break;
            case "club": renderClub(); break;
            case "analysis": renderAnalysis(); break;
            case "segments": renderSegments(); break;
            case "heatmap": openHeatmap("personal"); break;
            case "group": openGroup(); break;
            case "beacon": openSafety("beacon"); break;
            case "offline": renderOffline(); break;
            case "maintenance": renderMaintenance(); break;
            case "sensors": openScreen(NativeSensorsActivity.class); break;
            default: renderOverview(); break;
        }
        VazirmatnLoader.apply(this,content);
    }

    private void renderTabs(){
        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        addTab(row,"overview","خلاصه");addTab(row,"social","اجتماعی");addTab(row,"recommend","مسیر پیشنهادی");
        addTab(row,"globalheat","Heatmap عمومی");addTab(row,"flyover","Flyover 3D");addTab(row,"discover","کشف برنامه‌ها");
        addTab(row,"safety","ایمنی / SOS");addTab(row,"training","Training Load");addTab(row,"privacy","Privacy Zones");
        addTab(row,"replay","Replay");addTab(row,"club","رتبه هفتگی");addTab(row,"analysis","هوا و ارتفاع");
        addTab(row,"segments","سگمنت‌ها");addTab(row,"heatmap","Heatmap");addTab(row,"group","رسیدن به گروه");
        addTab(row,"beacon","Beacon");addTab(row,"offline","آفلاین");addTab(row,"maintenance","سرویس");addTab(row,"sensors","Cadence / Power");
        hsv.addView(row);content.addView(hsv,new LinearLayout.LayoutParams(-1,dp(58)));
    }

    private void addTab(LinearLayout row,String key,String label){
        Button b=normal(label);b.setOnClickListener(v->{tab=key;render();});
        if(key.equals(tab)){b.setTextColor(android.graphics.Color.rgb(5,34,53));android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{android.graphics.Color.rgb(20,223,237),android.graphics.Color.rgb(36,185,237)});g.setCornerRadius(dp(999));b.setBackground(g);}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(label.length()>11?150:118),dp(44));lp.setMargins(dp(4),dp(5),dp(4),dp(5));row.addView(b,lp);
    }

    private void renderOverview(){
        LinearLayout loading=card();
        TextView status=text("در حال آماده‌سازی خلاصه عملکرد…",12,false,MUTED);
        loading.addView(status);
        content.addView(loading,cardLp());
        io.execute(()->{
            try{
                JSONObject d=api.get("pp_pro_summary");
                JSONObject hr=null;
                try{hr=api.get("pp_hr_latest");}catch(Exception ignored){}
                JSONObject finalHr=hr;
                main.post(()->{
                    content.removeView(loading);
                    JSONObject best=d.optJSONObject("best_efforts");
                    if(best==null)best=new JSONObject();
                    addProGrid(
                            bestEffortCard("بیشترین مسافت",best.optJSONObject("distance"),"distance_km","distance"),
                            bestEffortCard("بیشترین صعود",best.optJSONObject("ascent"),"ascent_m","ascent"),
                            bestEffortCard("بیشترین سرعت",best.optJSONObject("speed"),"max_speed_kmh","speed"),
                            bestEffortCard("طولانی‌ترین زمان",best.optJSONObject("duration"),"duration_seconds","duration")
                    );

                    SharedPreferences offline=getSharedPreferences("pedalpro_offline_routes",MODE_PRIVATE);
                    String hrValue="—";
                    if(finalHr!=null){
                        JSONObject sample=finalHr.optJSONObject("sample");
                        if(sample!=null)hrValue=sample.optInt("bpm",0)+" bpm";
                    }
                    addProGrid(
                            proValueCard("سگمنت‌ها",String.valueOf(d.optInt("segments",0)),"سگمنت عمومی"),
                            proValueCard("Effortهای شما",String.valueOf(d.optInt("efforts",0)),"عبور ثبت‌شده"),
                            proValueCard("مسیرهای آفلاین",String.valueOf(offline.getAll().size()),"ذخیره روی این دستگاه"),
                            proValueCard("سنسور",hrValue,"آخرین ضربان")
                    );
                    VazirmatnLoader.apply(this,content);
                });
            }catch(Exception e){
                main.post(()->status.setText(e.getMessage()));
            }
        });
    }

    private void addBest(LinearLayout root,String label,JSONObject x,String key,String unit){
        if(x==null)return;double value=x.optDouble(key,0);String val="s".equals(unit)?formatSeconds((long)value):String.format(Locale.US,"%.1f %s",value,unit);root.addView(text(label+" · "+val+" · "+x.optString("title",""),12,false,TEXT));
    }

    private void renderTraining(){
        LinearLayout loading=card();
        TextView status=text("در حال دریافت Training Load…",12,false,MUTED);
        loading.addView(status);
        content.addView(loading,cardLp());
        io.execute(()->{
            try{
                JSONObject d=api.get("pp_training_summary");
                main.post(()->{
                    content.removeView(loading);
                    JSONObject t=d.optJSONObject("training");
                    if(t==null)t=d;

                    addProGrid(
                            proValueCard("بار ۷ روز",String.format(Locale.US,"%.0f",t.optDouble("acute_7d",0)),"PedalPro Load"),
                            proValueCard("میانگین هفتگی ۲۸ روز",String.format(Locale.US,"%.0f",t.optDouble("chronic_28d_weekly",0)),"بار پایه"),
                            proValueCard("Form",String.format(Locale.US,"%.0f",t.optDouble("form",0)),t.optString("status","")),
                            proValueCard("Acute / Chronic",String.format(Locale.US,"%.2f",t.optDouble("acute_chronic_ratio",0)),"برآورد تمرینی غیرپزشکی")
                    );

                    LinearLayout trend=proSectionCard("روند ۴۲ روزه");
                    NativeTrainingLoadBarsView bars=new NativeTrainingLoadBarsView(this);
                    bars.setDays(t.optJSONArray("daily"));
                    trend.addView(bars,new LinearLayout.LayoutParams(-1,dp(180)));
                    content.addView(trend,cardLp());

                    JSONObject sensors=t.optJSONObject("sensors");
                    if(sensors==null)sensors=new JSONObject();
                    addProGrid(
                            proValueCard("Power",Math.round(sensors.optDouble("avg_power_w",0))+" W",
                                    "میانگین ۲۸ روز · بیشینه "+Math.round(sensors.optDouble("max_power_w",0))+" W"),
                            proValueCard("Cadence",String.format(Locale.US,"%.0f rpm",sensors.optDouble("avg_cadence_rpm",0)),"میانگین ۲۸ روز")
                    );

                    JSONObject settings=t.optJSONObject("settings");
                    if(settings==null)settings=new JSONObject();
                    LinearLayout cfg=proSectionCard("تنظیمات Training Load");
                    EditText ftp=input("FTP (W)");
                    ftp.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                    ftp.setText(String.valueOf(settings.optInt("ftp_w",200)));
                    EditText hr=input("Max HR");
                    hr.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                    hr.setText(String.valueOf(settings.optInt("max_hr",180)));
                    Button save=primary("ذخیره تنظیمات");
                    save.setOnClickListener(v->{
                        JSONObject p=new JSONObject();
                        try{p.put("ftp_w",iv(ftp,200));p.put("max_hr",iv(hr,180));}catch(Exception ignored){}
                        post("pp_training_settings_save",p,save,"تنظیمات تمرین ذخیره شد",this::rerenderCurrent);
                    });
                    cfg.addView(ftp);cfg.addView(hr);cfg.addView(save);
                    TextView note=text("اگر Power برای یک رکاب‌زنی ثبت شده باشد، بار تمرین با FTP محاسبه می‌شود؛ در غیر این صورت مدل زمان/سرعت/مسافت/صعود استفاده می‌شود.",11,false,MUTED);
                    note.setPadding(0,dp(8),0,0);cfg.addView(note);
                    content.addView(cfg,cardLp());

                    LinearLayout zones=proSectionCard("Heart Rate Zones");
                    JSONObject hrData=t.optJSONObject("hr");
                    if(hrData!=null&&hrData.optBoolean("available",false)){
                        JSONObject zs=hrData.optJSONObject("zones_seconds");
                        if(zs==null)zs=new JSONObject();
                        LinearLayout zoneRows=new LinearLayout(this);
                        zoneRows.setOrientation(LinearLayout.VERTICAL);
                        for(int base=1;base<=5;base+=2){
                            LinearLayout row=new LinearLayout(this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                            for(int j=0;j<2;j++){
                                int zone=base+j;
                                if(zone>5){row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));continue;}
                                LinearLayout z=smallMetricCard("Zone "+zone,formatSeconds(zs.optLong(String.valueOf(zone),0)));
                                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
                                if(j==0)lp.setMargins(dp(4),dp(4),0,dp(4));else lp.setMargins(0,dp(4),dp(4),dp(4));
                                row.addView(z,lp);
                            }
                            zoneRows.addView(row);
                        }
                        zones.addView(zoneRows);
                        TextView maxHr=text("Max HR: "+hrData.optInt("max_hr",180)+" bpm",11,false,MUTED);
                        maxHr.setPadding(0,dp(8),0,0);zones.addView(maxHr);
                    }else{
                        zones.addView(noticeView("Heart Rate فعلاً اختیاری است. تحلیل Power و سایر داده‌های رکاب‌زنی مستقل از آن کار می‌کنند."));
                    }
                    content.addView(zones,cardLp());
                    VazirmatnLoader.apply(this,content);
                });
            }catch(Exception e){main.post(()->status.setText(e.getMessage()));}
        });
    }

    private void renderClub(){
        LinearLayout loading=card();
        TextView status=text("در حال دریافت رتبه‌بندی هفتگی…",12,false,MUTED);
        loading.addView(status);content.addView(loading,cardLp());
        io.execute(()->{
            try{
                JSONObject d=api.get("pp_club_weekly");
                JSONArray a=d.optJSONArray("items");
                main.post(()->{
                    content.removeView(loading);
                    content.addView(noticeView("رتبه‌بندی هفته جاری از شنبه · شروع: "+JalaliDate.format(d.optString("week_start",""),false)),cardLp());
                    if(a==null||a.length()==0){content.addView(noticeView("این هفته هنوز رکاب‌زنی ثبت نشده است."),cardLp());return;}
                    for(int i=0;i<a.length();i++){
                        JSONObject x=a.optJSONObject(i);if(x==null)continue;
                        LinearLayout row=proRow();
                        TextView rank=text(String.valueOf(i+1),13,true,TEXT);
                        rank.setGravity(android.view.Gravity.CENTER);
                        rank.setBackground(round(android.graphics.Color.argb(33,36,223,240),11,0));
                        row.addView(rank,new LinearLayout.LayoutParams(dp(34),dp(34)));

                        LinearLayout copy=new LinearLayout(this);
                        copy.setOrientation(LinearLayout.VERTICAL);
                        copy.setGravity(android.view.Gravity.END);
                        copy.addView(text(x.optString("display_name","عضو"),14,true,TEXT));
                        copy.addView(text(x.optInt("rides",0)+" رکاب · "+
                                String.format(Locale.US,"%.1f km",x.optDouble("distance_m",0)/1000.0)+" · "+
                                Math.round(x.optDouble("ascent_m",0))+" m صعود",11,false,MUTED));
                        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1f);cp.setMargins(dp(10),0,dp(10),0);
                        row.addView(copy,cp);
                        row.addView(text(formatSeconds(x.optLong("moving_seconds",0)),12,true,TEXT));
                        content.addView(row,cardLp());
                    }
                    VazirmatnLoader.apply(this,content);
                });
            }catch(Exception e){main.post(()->status.setText(e.getMessage()));}
        });
    }

    private void renderSegments(){
        LinearLayout form=card();
        form.addView(text("سگمنت‌ها",18,true,TEXT));
        android.widget.Spinner ride=rideSpinner();
        EditText name=input("نام سگمنت");
        Button create=primary("ساخت سگمنت از این رکاب‌زنی");
        create.setOnClickListener(v->{
            int rideId=rideId(ride);
            if(rideId<=0){toast("ابتدا یک رکاب‌زنی معتبر انتخاب کنید.");return;}
            if(name.getText().toString().trim().isEmpty()){toast("نام سگمنت را وارد کنید.");return;}
            JSONObject p=new JSONObject();
            try{p.put("ride_id",rideId);p.put("name",name.getText().toString().trim());}catch(Exception ignored){}
            post("pp_segment_create_from_ride",p,create,"سگمنت ساخته شد",this::rerenderCurrent);
        });
        form.addView(ride);form.addView(name);form.addView(create);
        content.addView(form,cardLp());

        io.execute(()->{
            try{
                JSONObject d=api.get("pp_segments_list");
                JSONArray a=d.optJSONArray("items");
                main.post(()->{
                    if(a==null||a.length()==0){content.addView(noticeView("هنوز سگمنتی ساخته نشده است."),cardLp());return;}
                    for(int i=0;i<a.length();i++){
                        JSONObject seg=a.optJSONObject(i);if(seg==null)continue;
                        LinearLayout row=proRow();
                        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(android.view.Gravity.END);
                        copy.addView(text(seg.optString("name","Segment"),14,true,TEXT));
                        copy.addView(text(String.format(Locale.US,"%.2f km · %d تلاش",seg.optDouble("distance_m",0)/1000.0,seg.optInt("efforts",0)),11,false,MUTED));
                        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));

                        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);
                        Button refresh=normal("ثبت تلاش‌های من");
                        Button board=normal("رتبه‌بندی");
                        int id=seg.optInt("id",0);
                        refresh.setOnClickListener(v->{
                            refresh.setEnabled(false);
                            io.execute(()->{try{
                                JSONObject out=api.post("pp_segment_refresh",new JSONObject().put("segment_id",id));
                                main.post(()->{refresh.setEnabled(true);toast(out.optInt("matched",0)+" تلاش شما با این سگمنت تطبیق داده شد");rerenderCurrent();});
                            }catch(Exception e){main.post(()->{refresh.setEnabled(true);toast(e.getMessage());});}});
                        });
                        board.setOnClickListener(v->{
                            Intent x=new Intent(this,NativeSegmentLeaderboardActivity.class);
                            x.putExtra(NativeSegmentLeaderboardActivity.EXTRA_SEGMENT_ID,id);
                            x.putExtra(NativeSegmentLeaderboardActivity.EXTRA_TITLE,seg.optString("name","Segment"));
                            startActivity(x);
                        });
                        actions.addView(refresh,new LinearLayout.LayoutParams(dp(132),dp(42)));
                        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(dp(132),dp(42));blp.setMargins(0,dp(5),0,0);
                        actions.addView(board,blp);
                        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-2,-2);alp.setMargins(dp(8),0,0,0);
                        row.addView(actions,alp);
                        content.addView(row,cardLp());
                    }
                    VazirmatnLoader.apply(this,content);
                });
            }catch(Exception e){main.post(()->content.addView(noticeView(e.getMessage()),cardLp()));}
        });
    }

    private void renderDiscover(){
        content.addView(noticeView("برنامه‌های آینده PedalPro به ترتیب زمان"),cardLp());
        io.execute(()->{
            try{
                JSONObject d=api.get("pp_event_discovery");
                JSONArray a=d.optJSONArray("items");
                main.post(()->{
                    if(a==null||a.length()==0){content.addView(noticeView("برنامه آینده‌ای ثبت نشده است."),cardLp());return;}
                    for(int i=0;i<a.length();i++){
                        JSONObject x=a.optJSONObject(i);if(x==null)continue;
                        LinearLayout row=proRow();
                        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(android.view.Gravity.END);
                        copy.addView(text(x.optString("title","برنامه"),14,true,TEXT));
                        copy.addView(text(JalaliDate.format(x.optString("event_date",""),true)+" · "+
                                x.optString("meeting_location","مکان اعلام نشده")+" · "+
                                x.optInt("registrations_count",0)+" ثبت‌نام",11,false,MUTED));
                        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
                        Button open=primary("مشاهده");
                        int eventId=x.optInt("id",0);
                        open.setOnClickListener(v->openEvent(eventId));
                        row.addView(open,new LinearLayout.LayoutParams(dp(92),dp(44)));
                        content.addView(row,cardLp());
                    }
                    VazirmatnLoader.apply(this,content);
                });
            }catch(Exception e){main.post(()->content.addView(noticeView(e.getMessage()),cardLp()));}
        });
    }

    private void renderRideData(String title,String action){
        if("pp_activity_replay".equals(action)){renderReplayNative();return;}
        if("pp_flyover".equals(action)){renderFlyoverNative();return;}
    }

    private void renderReplayNative(){
        stopReplayNative();
        LinearLayout form=card();
        android.widget.Spinner ride=rideSpinner();
        Button load=primary("بارگذاری Replay");
        form.addView(ride);form.addView(load);content.addView(form,cardLp());

        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(android.view.Gravity.CENTER_VERTICAL);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        replayPlay=normal("▶ پخش");
        replaySlider=new android.widget.SeekBar(this);
        android.widget.Spinner speed=simpleSpinner(new String[]{"1×","2×","4×"});
        controls.addView(replayPlay,new LinearLayout.LayoutParams(dp(92),dp(46)));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(46),1f);slp.setMargins(dp(7),0,dp(7),0);controls.addView(replaySlider,slp);
        controls.addView(speed,new LinearLayout.LayoutParams(dp(82),dp(46)));
        content.addView(controls,cardLp());

        replayMeta=noticeView("یک رکاب‌زنی را انتخاب کنید.");
        content.addView(replayMeta,cardLp());
        replayMap=new NativeRouteView(this);
        content.addView(replayMap,new LinearLayout.LayoutParams(-1,dp(360)));

        load.setOnClickListener(v->loadReplayNative(rideId(ride),load));
        replayPlay.setOnClickListener(v->{
            if(replayRunning){stopReplayNative();return;}
            if(replayPoints==null||replayPoints.length()==0){loadReplayNative(rideId(ride),load);return;}
            replayRunning=true;replayPlay.setText("⏸ مکث");main.removeCallbacks(replayTick);main.post(replayTick);
        });
        replaySlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(android.widget.SeekBar seekBar,int progress,boolean fromUser){if(fromUser){stopReplayNative();replayPaintNative(progress);}}
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar){}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar){}
        });
        speed.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){replayDelayMs=position==0?650L:position==1?350L:170L;}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
    }

    private void loadReplayNative(int rideId,Button load){
        stopReplayNative();load.setEnabled(false);
        io.execute(()->{
            try{
                Map<String,Object> q=new HashMap<>();q.put("ride_id",rideId);
                JSONObject d=api.get("pp_activity_replay",q);
                JSONArray pts=d.optJSONArray("points");if(pts==null)pts=new JSONArray();
                JSONArray finalPts=pts;
                main.post(()->{
                    load.setEnabled(true);replayPoints=finalPts;replayIndex=0;
                    if(replayMap!=null)replayMap.setPoints(finalPts);
                    if(replaySlider!=null){replaySlider.setMax(Math.max(0,finalPts.length()-1));replaySlider.setProgress(0);}
                    replayPaintNative(0);
                });
            }catch(Exception e){main.post(()->{load.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void replayPaintNative(int i){
        if(replayPoints==null||replayPoints.length()==0)return;
        i=Math.max(0,Math.min(replayPoints.length()-1,i));replayIndex=i;
        JSONObject p=replayPoints.optJSONObject(i);if(p==null)return;
        double lat=p.optDouble("lat",Double.NaN),lng=p.optDouble("lng",Double.NaN);
        if(replayMap!=null&&Double.isFinite(lat)&&Double.isFinite(lng))replayMap.setCurrent(lat,lng);
        if(replaySlider!=null)replaySlider.setProgress(i);
        if(replayMeta!=null)replayMeta.setText((i+1)+"/"+replayPoints.length()+" · "+
                String.format(Locale.US,"%.1f km/h",p.optDouble("speed_kmh",0))+" · "+
                (p.isNull("altitude")?"—":Math.round(p.optDouble("altitude",0))+" m")+" · "+p.optString("recorded_at",""));
    }

    private void stopReplayNative(){
        replayRunning=false;main.removeCallbacks(replayTick);
        if(replayPlay!=null)replayPlay.setText("▶ پخش");
    }

    private void renderFlyoverNative(){
        stopFlyNative();
        LinearLayout form=card();
        android.widget.Spinner ride=rideSpinner();
        Button load=primary("بارگذاری Flyover");
        form.addView(ride);form.addView(load);content.addView(form,cardLp());

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        flyPlay=normal("▶ پخش");
        android.widget.Spinner speed=simpleSpinner(new String[]{"1×","2×","4×"});
        actions.addView(flyPlay,new LinearLayout.LayoutParams(0,dp(46),1f));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(92),dp(46));sp.setMargins(dp(8),0,0,0);actions.addView(speed,sp);
        content.addView(actions,cardLp());

        flyMap=new NativeRouteView(this);
        flyMap.setBackgroundColor(android.graphics.Color.rgb(7,19,31));
        content.addView(flyMap,new LinearLayout.LayoutParams(-1,dp(300)));
        flyMeta=text("",11,false,MUTED);flyMeta.setPadding(0,dp(8),0,0);content.addView(flyMeta,cardLp());

        load.setOnClickListener(v->loadFlyNative(rideId(ride),load));
        flyPlay.setOnClickListener(v->{
            if(flyRunning){stopFlyNative();return;}
            if(flyPoints==null||flyPoints.length()==0){loadFlyNative(rideId(ride),load);return;}
            flyRunning=true;flyPlay.setText("⏸ توقف");main.removeCallbacks(flyTick);main.post(flyTick);
        });
        speed.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){flyStep=position==0?1:position==1?2:4;}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
    }

    private void loadFlyNative(int rideId,Button load){
        stopFlyNative();load.setEnabled(false);
        io.execute(()->{
            try{
                Map<String,Object> q=new HashMap<>();q.put("ride_id",rideId);
                JSONObject d=api.get("pp_flyover",q);
                JSONArray pts=d.optJSONArray("points");if(pts==null)pts=new JSONArray();
                JSONArray finalPts=pts;
                main.post(()->{
                    load.setEnabled(true);flyPoints=finalPts;flyIndex=0;
                    if(flyMap!=null)flyMap.setPoints(finalPts);
                    flyPaintNative(0);
                });
            }catch(Exception e){main.post(()->{load.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void flyPaintNative(int i){
        if(flyPoints==null||flyPoints.length()==0)return;
        i=Math.max(0,Math.min(flyPoints.length()-1,i));flyIndex=i;
        JSONObject p=flyPoints.optJSONObject(i);if(p==null)return;
        double lat=p.optDouble("lat",Double.NaN),lng=p.optDouble("lng",Double.NaN);
        if(flyMap!=null&&Double.isFinite(lat)&&Double.isFinite(lng))flyMap.setCurrent(lat,lng);
        if(flyMeta!=null)flyMeta.setText("نقطه "+(i+1)+"/"+flyPoints.length()+" · سرعت "+
                String.format(Locale.US,"%.1f km/h",p.optDouble("speed_kmh",0))+" · ارتفاع "+
                Math.round(p.optDouble("altitude",0))+" m");
    }

    private void stopFlyNative(){
        flyRunning=false;main.removeCallbacks(flyTick);if(flyPlay!=null)flyPlay.setText("▶ پخش");
    }

    private void renderAnalysis(){
        LinearLayout form=card();
        android.widget.Spinner ride=rideSpinner();
        Button load=primary("تحلیل رکاب‌زنی");
        form.addView(ride);form.addView(load);
        content.addView(form,cardLp());
        LinearLayout result=new LinearLayout(this);result.setOrientation(LinearLayout.VERTICAL);
        result.addView(text("رکاب‌زنی را انتخاب کنید و «تحلیل رکاب‌زنی» را بزنید.",12,false,MUTED));
        content.addView(result,cardLp());

        load.setOnClickListener(v->{
            int id=rideId(ride);
            load.setEnabled(false);
            io.execute(()->{
                try{
                    Map<String,Object> q=new HashMap<>();q.put("ride_id",id);
                    JSONObject d=api.get("pp_ride_analysis",q);
                    main.post(()->{
                        load.setEnabled(true);result.removeAllViews();
                        JSONObject e=d.optJSONObject("elevation");if(e==null)e=new JSONObject();
                        JSONObject w=d.optJSONObject("weather");if(w==null)w=new JSONObject();
                        addProGridTo(result,
                                proValueCard("صعود",Math.round(e.optDouble("gain_m",0))+" m","نزول "+Math.round(e.optDouble("descent_m",0))+" m"),
                                proValueCard("ارتفاع",e.isNull("max_m")?"—":Math.round(e.optDouble("max_m",0))+" m",
                                        "کمینه "+(e.isNull("min_m")?"—":Math.round(e.optDouble("min_m",0))+" m")),
                                proValueCard("بیشترین شیب نمونه‌برداری‌شده",String.format(Locale.US,"%.1f%%",e.optDouble("max_grade_pct",0)),"با حذف جهش‌های ارتفاعی"),
                                proValueCard("آب‌وهوا",w.isNull("avg_temperature_c")?"—":String.format(Locale.US,"%.1f°",w.optDouble("avg_temperature_c",0)),
                                        "باد "+(w.isNull("avg_wind_kmh")?"—":String.format(Locale.US,"%.1f km/h",w.optDouble("avg_wind_kmh",0)))+
                                                " · تندباد "+(w.isNull("max_gust_kmh")?"—":String.format(Locale.US,"%.1f",w.optDouble("max_gust_kmh",0))))
                        );
                        NativeElevationProfileView chart=new NativeElevationProfileView(this);
                        chart.setProfile(e.optJSONArray("profile"));
                        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,dp(210));clp.setMargins(0,dp(10),0,0);
                        result.addView(chart,clp);
                        TextView note=text("نمونه‌های هواشناسی: "+w.optInt("samples",0)+" · بارش ثبت‌شده: "+
                                String.format(Locale.US,"%.1f mm",w.optDouble("precipitation_mm",0))+
                                ". نوع نقشه و مسیر تغییری نکرده است.",11,false,TEXT);
                        note.setBackground(round(android.graphics.Color.argb(8,255,255,255),14,android.graphics.Color.argb(28,255,255,255)));
                        note.setPadding(dp(12),dp(11),dp(12),dp(11));
                        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,-2);nlp.setMargins(0,dp(10),0,0);
                        result.addView(note,nlp);
                        VazirmatnLoader.apply(this,result);
                    });
                }catch(Exception e){main.post(()->{load.setEnabled(true);toast(e.getMessage());});}
            });
        });
    }

    private void renderOffline(){
        SharedPreferences prefs=getSharedPreferences("pedalpro_offline_routes",MODE_PRIVATE);
        LinearLayout form=card();
        android.widget.Spinner ride=rideSpinner();
        Button save=primary("ذخیره مسیر روی گوشی");
        Button clear=normal("پاک‌کردن همه");
        form.addView(ride);form.addView(save);form.addView(clear);
        content.addView(form,cardLp());

        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list,cardLp());
        Runnable paint=()->renderOfflineSaved(list,prefs);
        paint.run();

        save.setOnClickListener(v->{
            int id=rideId(ride);
            if(id<=0){toast("برای ذخیره، ابتدا یک مسیر معتبر انتخاب کنید.");return;}
            save.setEnabled(false);
            io.execute(()->{
                try{
                    Map<String,Object> q=new HashMap<>();q.put("ride_id",id);
                    JSONObject d=api.get("pp_route_pack",q);
                    JSONObject route=d.optJSONObject("route");
                    if(route==null)throw new IllegalStateException("مسیر دریافت نشد.");
                    prefs.edit().putString(String.valueOf(route.optInt("id",0)),route.toString()).apply();
                    main.post(()->{save.setEnabled(true);toast("مسیر روی این گوشی ذخیره شد");paint.run();});
                }catch(Exception e){main.post(()->{save.setEnabled(true);toast(e.getMessage());});}
            });
        });
        clear.setOnClickListener(v->{
            if(prefs.getAll().isEmpty()){toast("مسیر آفلاینی برای پاک‌کردن وجود ندارد.");return;}
            new android.app.AlertDialog.Builder(this)
                    .setTitle("حذف مسیرهای آفلاین")
                    .setMessage("همه مسیرهای ذخیره‌شده روی همین گوشی پاک شوند؟ این کار اطلاعات رکاب‌زنی روی سرور را حذف نمی‌کند.")
                    .setNegativeButton("انصراف",null)
                    .setPositiveButton("حذف از گوشی",(dialog,which)->{
                        prefs.edit().clear().apply();
                        paint.run();
                        toast("مسیرهای آفلاین از گوشی حذف شدند.");
                    })
                    .show();
        });
        TextView note=text("داده مسیر بدون اینترنت باقی می‌ماند. نمایش پس‌زمینه نقشه به Tileهای قبلاً Cache‌شده دستگاه وابسته است.",11,false,MUTED);
        content.addView(note,cardLp());
    }

    private void renderMaintenance(){
        LinearLayout loading=card();TextView status=text("در حال دریافت سرویس و نگهداری…",12,false,MUTED);loading.addView(status);content.addView(loading,cardLp());
        io.execute(()->{
            try{
                JSONObject d=api.get("pp_maintenance_list");
                JSONArray bikes=d.optJSONArray("bikes"),items=d.optJSONArray("items");
                main.post(()->{
                    content.removeView(loading);
                    LinearLayout form=card();
                    android.widget.Spinner bike=bikeSpinner(bikes);
                    EditText title=input("مثلاً تعویض زنجیر");
                    EditText km=input("دوره سرویس km");km.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);km.setText("500");
                    Button save=primary("افزودن یادآور");
                    save.setOnClickListener(v->{
                        JSONObject p=new JSONObject();
                        try{p.put("bike_id",bikeId(bike));p.put("title",title.getText().toString());p.put("interval_km",iv(km,500));}catch(Exception ignored){}
                        post("pp_maintenance_save",p,save,"یادآور سرویس ثبت شد",this::rerenderCurrent);
                    });
                    form.addView(bike);form.addView(title);form.addView(km);form.addView(save);content.addView(form,cardLp());

                    if(items==null||items.length()==0){content.addView(noticeView("یادآور سرویس ندارید."),cardLp());return;}
                    for(int i=0;i<items.length();i++){
                        JSONObject x=items.optJSONObject(i);if(x==null)continue;
                        JSONObject found=findBike(bikes,x.optInt("bike_id",0));
                        double odo=found==null?0:found.optDouble("odometer_km",0);
                        double left=x.optDouble("next_due_km",0)-odo;
                        int color=left<=0?RED:left<100?android.graphics.Color.rgb(255,209,102):android.graphics.Color.rgb(85,230,155);
                        LinearLayout row=proRow();
                        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(android.view.Gravity.END);
                        copy.addView(text(x.optString("title","سرویس"),14,true,TEXT));
                        copy.addView(text((found==null?"دوچرخه":found.optString("brand_model","دوچرخه"))+" · سرویس هر "+
                                Math.round(x.optDouble("interval_km",0))+" km",11,false,MUTED));
                        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
                        LinearLayout due=new LinearLayout(this);due.setOrientation(LinearLayout.VERTICAL);due.setGravity(android.view.Gravity.END);
                        due.addView(text(left<=0?"موعد سرویس رسیده":Math.round(left)+" km مانده",12,true,color));
                        Button done=normal("سرویس انجام شد");
                        int itemId=x.optInt("id",0);
                        done.setOnClickListener(v->{
                            JSONObject p=new JSONObject();try{p.put("id",itemId);}catch(Exception ignored){}
                            post("pp_maintenance_complete",p,done,"کیلومتر سرویس بروزرسانی شد",this::rerenderCurrent);
                        });
                        due.addView(done,new LinearLayout.LayoutParams(dp(126),dp(42)));
                        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-2,-2);dlp.setMargins(dp(8),0,0,0);row.addView(due,dlp);
                        content.addView(row,cardLp());
                    }
                    VazirmatnLoader.apply(this,content);
                });
            }catch(Exception e){main.post(()->status.setText(e.getMessage()));}
        });
    }

    private void rerenderCurrent(){content.removeAllViews();render();}

    private LinearLayout proSectionCard(String title){
        LinearLayout c=card();
        c.setPadding(dp(15),dp(15),dp(15),dp(15));
        c.addView(text(title,16,true,TEXT));
        return c;
    }

    private LinearLayout proValueCard(String title,String value,String sub){
        LinearLayout c=card();
        c.setPadding(dp(15),dp(15),dp(15),dp(15));
        c.addView(text(title,14,true,TEXT));
        TextView v=text(value==null?"—":value,27,true,android.graphics.Color.rgb(233,251,255));
        LinearLayout.LayoutParams vlp=new LinearLayout.LayoutParams(-1,-2);vlp.setMargins(0,dp(7),0,0);c.addView(v,vlp);
        TextView m=text(sub==null?"":sub,12,false,MUTED);
        LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(-1,-2);mlp.setMargins(0,dp(4),0,0);c.addView(m,mlp);
        return c;
    }

    private LinearLayout bestEffortCard(String title,JSONObject x,String field,String kind){
        if(x==null)return proValueCard(title,"—","هنوز رکوردی ثبت نشده");
        double value=x.optDouble(field,0);
        String v;
        if("distance".equals(kind))v=String.format(Locale.US,"%.1f km",value);
        else if("ascent".equals(kind))v=Math.round(value)+" m";
        else if("speed".equals(kind))v=String.format(Locale.US,"%.1f km/h",value);
        else v=formatSeconds((long)value);
        return proValueCard(title,v,x.optString("title",""));
    }

    private void addProGrid(android.view.View... views){addProGridTo(content,views);}

    private void addProGridTo(LinearLayout host,android.view.View... views){
        for(int i=0;i<views.length;i+=2){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            android.view.View a=views[i];
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1f);ap.setMargins(dp(5),0,0,dp(10));row.addView(a,ap);
            if(i+1<views.length){
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1f);bp.setMargins(0,0,dp(5),dp(10));row.addView(views[i+1],bp);
            }else row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));
            host.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private LinearLayout smallMetricCard(String label,String value){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(android.view.Gravity.CENTER);
        c.setPadding(dp(10),dp(10),dp(10),dp(10));
        c.setBackground(round(android.graphics.Color.argb(10,255,255,255),14,android.graphics.Color.argb(23,255,255,255)));
        TextView l=text(label,10,false,MUTED);l.setGravity(android.view.Gravity.CENTER);
        TextView v=text(value,18,true,TEXT);v.setGravity(android.view.Gravity.CENTER);
        c.addView(l);c.addView(v);return c;
    }

    private TextView noticeView(String body){
        TextView t=text(body==null?"":body,12,false,TEXT);
        t.setPadding(dp(12),dp(11),dp(12),dp(11));
        t.setBackground(round(android.graphics.Color.argb(8,255,255,255),14,android.graphics.Color.argb(28,255,255,255)));
        return t;
    }

    private LinearLayout proRow(){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(12),dp(12),dp(12),dp(12));
        row.setBackground(round(android.graphics.Color.argb(9,255,255,255),15,android.graphics.Color.argb(23,255,255,255)));
        return row;
    }

    private android.widget.Spinner simpleSpinner(String[] values){
        android.widget.Spinner spinner=new android.widget.Spinner(this);
        ArrayList<String> labels=new ArrayList<>();
        java.util.Collections.addAll(labels,values);
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){
            @Override public android.view.View getView(int position,android.view.View convertView,android.view.ViewGroup parent){TextView v=(TextView)super.getView(position,convertView,parent);styleSpinnerText(v);return v;}
            @Override public android.view.View getDropDownView(int position,android.view.View convertView,android.view.ViewGroup parent){TextView v=(TextView)super.getDropDownView(position,convertView,parent);v.setTextColor(TEXT);v.setBackgroundColor(android.graphics.Color.rgb(9,26,41));v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setGravity(android.view.Gravity.CENTER);return v;}
        };
        spinner.setAdapter(adapter);spinner.setBackground(round(android.graphics.Color.argb(122,5,25,40),12,android.graphics.Color.argb(46,255,255,255)));return spinner;
    }

    private android.widget.Spinner rideSpinner(){
        android.widget.Spinner spinner=new android.widget.Spinner(this);
        ArrayList<String> labels=new ArrayList<>();
        JSONArray ids=new JSONArray();
        JSONArray rides=boot==null?null:boot.optJSONArray("my_completed_rides");
        if(rides==null)rides=boot==null?null:boot.optJSONArray("rides");
        if(rides!=null){
            JSONObject me=boot.optJSONObject("user");int uid=me==null?0:me.optInt("id",0);
            for(int i=rides.length()-1;i>=0;i--){
                JSONObject r=rides.optJSONObject(i);if(r==null)continue;
                if(!"completed".equals(r.optString("status","completed")))continue;
                int owner=r.optInt("owner_user_id",uid);if(uid>0&&owner>0&&owner!=uid)continue;
                labels.add(r.optString("title","رکاب "+r.optInt("id",0))+" — "+String.format(Locale.US,"%.1f km",r.optDouble("distance_m",0)/1000.0));
                ids.put(r.optInt("id",0));
                if(labels.size()>=80)break;
            }
        }
        if(labels.isEmpty()){labels.add("رکاب‌زنی ثبت‌شده‌ای وجود ندارد");ids.put(0);}
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){
            @Override public android.view.View getView(int position,android.view.View convertView,android.view.ViewGroup parent){
                TextView v=(TextView)super.getView(position,convertView,parent);styleSpinnerText(v);return v;
            }
            @Override public android.view.View getDropDownView(int position,android.view.View convertView,android.view.ViewGroup parent){
                TextView v=(TextView)super.getDropDownView(position,convertView,parent);v.setTextColor(TEXT);v.setBackgroundColor(android.graphics.Color.rgb(9,26,41));v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setGravity(android.view.Gravity.END);return v;
            }
        };
        spinner.setAdapter(adapter);spinner.setTag(ids);spinner.setBackground(round(android.graphics.Color.argb(122,5,25,40),12,android.graphics.Color.argb(46,255,255,255)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,dp(6),0,dp(6));spinner.setLayoutParams(lp);
        return spinner;
    }

    private void styleSpinnerText(TextView v){v.setTextColor(TEXT);v.setTextSize(12);v.setPadding(dp(12),0,dp(12),0);v.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.END);}

    private int rideId(android.widget.Spinner spinner){
        Object tag=spinner.getTag();if(!(tag instanceof JSONArray))return 0;
        return ((JSONArray)tag).optInt(spinner.getSelectedItemPosition(),0);
    }

    private android.widget.Spinner bikeSpinner(JSONArray bikes){
        android.widget.Spinner spinner=new android.widget.Spinner(this);
        ArrayList<String> labels=new ArrayList<>();JSONArray ids=new JSONArray();
        if(bikes!=null)for(int i=0;i<bikes.length();i++){
            JSONObject b=bikes.optJSONObject(i);if(b==null)continue;
            labels.add(b.optString("brand_model","دوچرخه "+b.optInt("id",0))+" — "+String.format(Locale.US,"%.1f km",b.optDouble("odometer_km",0)));
            ids.put(b.optInt("id",0));
        }
        if(labels.isEmpty()){labels.add("دوچرخه‌ای ثبت نشده است");ids.put(0);}
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){
            @Override public android.view.View getView(int position,android.view.View convertView,android.view.ViewGroup parent){TextView v=(TextView)super.getView(position,convertView,parent);styleSpinnerText(v);return v;}
            @Override public android.view.View getDropDownView(int position,android.view.View convertView,android.view.ViewGroup parent){TextView v=(TextView)super.getDropDownView(position,convertView,parent);v.setTextColor(TEXT);v.setBackgroundColor(android.graphics.Color.rgb(9,26,41));v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setGravity(android.view.Gravity.END);return v;}
        };
        spinner.setAdapter(adapter);spinner.setTag(ids);spinner.setBackground(round(android.graphics.Color.argb(122,5,25,40),12,android.graphics.Color.argb(46,255,255,255)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,dp(6),0,dp(6));spinner.setLayoutParams(lp);return spinner;
    }

    private int bikeId(android.widget.Spinner spinner){Object tag=spinner.getTag();return tag instanceof JSONArray?((JSONArray)tag).optInt(spinner.getSelectedItemPosition(),0):0;}

    private JSONObject findBike(JSONArray bikes,int id){
        if(bikes==null)return null;
        for(int i=0;i<bikes.length();i++){JSONObject b=bikes.optJSONObject(i);if(b!=null&&b.optInt("id",0)==id)return b;}
        return null;
    }

    private void renderOfflineSaved(LinearLayout host,SharedPreferences prefs){
        host.removeAllViews();
        if(prefs.getAll().isEmpty()){host.addView(noticeView("مسیر آفلاینی روی این دستگاه ذخیره نشده است."));return;}
        for(Map.Entry<String,?> e:prefs.getAll().entrySet()){
            try{
                JSONObject r=new JSONObject(String.valueOf(e.getValue()));
                LinearLayout row=proRow();
                LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(android.view.Gravity.END);
                copy.addView(text(r.optString("title","مسیر"),14,true,TEXT));
                JSONArray pts=r.optJSONArray("points");
                copy.addView(text(String.format(Locale.US,"%.1f km · %d نقطه",r.optDouble("distance_km",0),pts==null?0:pts.length()),11,false,MUTED));
                row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
                Button show=normal("نمایش");
                show.setOnClickListener(v->showOfflineRoute(r));
                row.addView(show,new LinearLayout.LayoutParams(dp(82),dp(42)));
                host.addView(row,cardLp());
            }catch(Exception ignored){}
        }
    }

    private void showOfflineRoute(JSONObject route){
        JSONArray source=route.optJSONArray("points");
        JSONArray points=new JSONArray();
        if(source!=null)for(int i=0;i<source.length();i++){
            JSONArray p=source.optJSONArray(i);if(p==null||p.length()<2)continue;
            JSONObject o=new JSONObject();try{o.put("lat",p.optDouble(0));o.put("lng",p.optDouble(1));points.put(o);}catch(Exception ignored){}
        }
        NativeRouteView map=new NativeRouteView(this);map.setPoints(points);
        content.addView(map,new LinearLayout.LayoutParams(-1,dp(360)));
    }

    private void openEvent(int eventId){
        Intent i=new Intent(this,NativeMainActivity.class);
        i.putExtra("page","activities");i.putExtra("event_id",eventId);
        startActivity(i);
    }

    private void openGroup(){Intent i=new Intent(this,NativeLiveNavigationActivity.class);i.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"group");startActivity(i);}
    private void openSafety(String section){Intent i=new Intent(this,NativeSafetyActivity.class);i.putExtra(NativeSafetyActivity.EXTRA_SECTION,section);startActivity(i);}
    private void openHeatmap(String scope){Intent i=new Intent(this,NativeHeatmapActivity.class);i.putExtra(NativeHeatmapActivity.EXTRA_SCOPE,scope);startActivity(i);}
    private void openSocial(String section){Intent i=new Intent(this,NativeSocialActivity.class);i.putExtra(NativeSocialActivity.EXTRA_SECTION,section);startActivity(i);}

    private void openScreen(Class<?> cls){startActivity(new Intent(this,cls));}

    private void post(String action,JSONObject p,Button b,String ok,Runnable after){b.setEnabled(false);io.execute(()->{try{api.post(action,p);main.post(()->{b.setEnabled(true);toast(ok);after.run();});}catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}});}
    private int iv(EditText e,int fallback){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return fallback;}}
    private String formatSeconds(long v){long s=Math.max(0,v);return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);}
    @Override protected void onDestroy(){
        stopReplayNative();
        stopFlyNative();
        super.onDestroy();
    }

}
