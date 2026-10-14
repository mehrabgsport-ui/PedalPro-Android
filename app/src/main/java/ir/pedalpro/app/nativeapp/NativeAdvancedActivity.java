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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NativeAdvancedActivity extends NativeScreenBase {
    private JSONObject boot;
    private String tab="overview";

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
            case "globalheat": openScreen(NativeHeatmapActivity.class); break;
            case "flyover": renderRideData("Flyover 3D","pp_flyover"); break;
            case "discover": renderDiscover(); break;
            case "safety": openScreen(NativeSafetyActivity.class); break;
            case "training": renderTraining(); break;
            case "privacy": openScreen(NativeSafetyActivity.class); break;
            case "replay": renderRideData("Replay فعالیت","pp_activity_replay"); break;
            case "club": renderClub(); break;
            case "analysis": renderAnalysis(); break;
            case "segments": renderSegments(); break;
            case "heatmap": openScreen(NativeHeatmapActivity.class); break;
            case "group": openGroup(); break;
            case "beacon": openScreen(NativeSafetyActivity.class); break;
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
        LinearLayout loading=card();TextView status=text("در حال آماده‌سازی خلاصه عملکرد…",12,false,MUTED);loading.addView(status);content.addView(loading,cardLp());
        io.execute(()->{
            try{JSONObject d=api.get("pp_pro_summary");main.post(()->{content.removeView(loading);JSONObject best=d.optJSONObject("best_efforts");LinearLayout grid=card();grid.addView(text("خلاصه عملکرد",18,true,TEXT));if(best!=null){addBest(grid,"بیشترین مسافت",best.optJSONObject("distance"),"distance_km","km");addBest(grid,"بیشترین صعود",best.optJSONObject("ascent"),"ascent_m","m");addBest(grid,"بیشترین سرعت",best.optJSONObject("speed"),"max_speed_kmh","km/h");addBest(grid,"طولانی‌ترین زمان",best.optJSONObject("duration"),"duration_seconds","s");}grid.addView(text("سگمنت‌ها: "+d.optInt("segments",0)+" · Effortهای شما: "+d.optInt("efforts",0),12,false,MUTED));content.addView(grid,cardLp());VazirmatnLoader.apply(this,content);});}
            catch(Exception e){main.post(()->status.setText(e.getMessage()));}
        });
    }

    private void addBest(LinearLayout root,String label,JSONObject x,String key,String unit){
        if(x==null)return;double value=x.optDouble(key,0);String val="s".equals(unit)?formatSeconds((long)value):String.format(Locale.US,"%.1f %s",value,unit);root.addView(text(label+" · "+val+" · "+x.optString("title",""),12,false,TEXT));
    }

    private void renderTraining(){
        LinearLayout box=card();TextView status=text("در حال دریافت Training Load…",12,false,MUTED);box.addView(status);content.addView(box,cardLp());
        io.execute(()->{try{JSONObject d=api.get("pp_training_summary");main.post(()->{
            content.removeView(box);JSONObject t=d.optJSONObject("training");if(t==null)t=d;
            LinearLayout c=card();c.addView(text("Training Load / Fitness",18,true,TEXT));
            c.addView(text(String.format(Locale.US,"Acute 7d: %.1f\nChronic 28d: %.1f\nForm: %.1f\nA:C Ratio: %.2f\nStatus: %s",t.optDouble("acute_7d",0),t.optDouble("chronic_28d_weekly",0),t.optDouble("form",0),t.optDouble("acute_chronic_ratio",0),t.optString("status","—")),12,false,MUTED));
            JSONObject settings=t.optJSONObject("settings");EditText ftp=input("FTP (W)"),hr=input("Max HR");if(settings!=null){ftp.setText(String.valueOf(settings.optInt("ftp_w",0)));hr.setText(String.valueOf(settings.optInt("max_hr",0)));}Button save=primary("ذخیره FTP / Max HR");save.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("ftp_w",iv(ftp,200));p.put("max_hr",iv(hr,180));}catch(Exception ignored){}post("pp_training_settings_save",p,save,"تنظیمات تمرین ذخیره شد",this::renderTraining);});c.addView(ftp);c.addView(hr);c.addView(save);content.addView(c,cardLp());VazirmatnLoader.apply(this,content);
        });}catch(Exception e){main.post(()->status.setText(e.getMessage()));}});
    }

    private void renderClub(){
        LinearLayout box=card();TextView status=text("در حال دریافت رتبه‌بندی هفتگی…",12,false,MUTED);box.addView(status);content.addView(box,cardLp());
        io.execute(()->{try{JSONObject d=api.get("pp_club_weekly");JSONArray a=d.optJSONArray("items");main.post(()->{content.removeView(box);LinearLayout h=card();h.addView(text("رتبه‌بندی هفتگی باشگاه",18,true,TEXT));h.addView(text("هفته شنبه تا جمعه",11,false,MUTED));content.addView(h,cardLp());if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;LinearLayout r=card();r.addView(text((i+1)+". "+x.optString("display_name","عضو"),14,true,TEXT));r.addView(text(String.format(Locale.US,"%d رکاب · %.1f km · %.0f m صعود",x.optInt("rides",0),x.optDouble("distance_m",0)/1000.0,x.optDouble("ascent_m",0)),11,false,MUTED));content.addView(r,cardLp());}VazirmatnLoader.apply(this,content);});}catch(Exception e){main.post(()->status.setText(e.getMessage()));}});
    }

    private void renderSegments(){
        LinearLayout form=card();form.addView(text("سگمنت‌ها",18,true,TEXT));EditText rideId=input("شناسه رکاب‌زنی"),name=input("نام سگمنت");Button create=primary("ساخت سگمنت از این رکاب‌زنی");create.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("ride_id",iv(rideId,0));p.put("name",name.getText().toString().trim());}catch(Exception ignored){}post("pp_segment_create_from_ride",p,create,"سگمنت ساخته شد",this::render);});form.addView(rideId);form.addView(name);form.addView(create);content.addView(form,cardLp());
        io.execute(()->{try{JSONObject d=api.get("pp_segments_list");JSONArray a=d.optJSONArray("items");main.post(()->{if(a==null||a.length()==0){addCard("سگمنت‌ها","هنوز سگمنتی ساخته نشده است.");return;}for(int i=0;i<a.length();i++){JSONObject s=a.optJSONObject(i);if(s==null)continue;LinearLayout r=card();r.addView(text(s.optString("name","Segment"),15,true,TEXT));r.addView(text(String.format(Locale.US,"%.2f km · %d تلاش",s.optDouble("distance_m",0)/1000.0,s.optInt("efforts",0)),11,false,MUTED));int id=s.optInt("id",0);Button board=normal("رتبه‌بندی");board.setOnClickListener(v->{Intent x=new Intent(this,NativeSegmentLeaderboardActivity.class);x.putExtra(NativeSegmentLeaderboardActivity.EXTRA_SEGMENT_ID,id);x.putExtra(NativeSegmentLeaderboardActivity.EXTRA_TITLE,s.optString("name","Segment"));startActivity(x);});r.addView(board);content.addView(r,cardLp());}VazirmatnLoader.apply(this,content);});}catch(Exception e){main.post(()->addCard("خطا",e.getMessage()));}});
    }

    private void renderDiscover(){
        LinearLayout status=card();status.addView(text("برنامه‌های آینده PedalPro به ترتیب زمان",14,true,TEXT));content.addView(status,cardLp());
        io.execute(()->{try{JSONObject d=api.get("pp_event_discovery");JSONArray a=d.optJSONArray("items");main.post(()->{if(a==null||a.length()==0){addCard("کشف برنامه‌ها","برنامه آینده‌ای ثبت نشده است.");return;}for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;LinearLayout r=card();r.addView(text(x.optString("title","برنامه"),15,true,TEXT));r.addView(text(x.optString("event_date","")+" · "+x.optString("meeting_location","مکان اعلام نشده")+" · "+x.optInt("registrations_count",0)+" ثبت‌نام",11,false,MUTED));content.addView(r,cardLp());}VazirmatnLoader.apply(this,content);});}catch(Exception e){main.post(()->addCard("خطا",e.getMessage()));}});
    }

    private void renderRideData(String title,String action){
        LinearLayout c=card();c.addView(text(title,18,true,TEXT));EditText ride=input("شناسه رکاب‌زنی");Button load=primary("بارگذاری");TextView out=text("",11,false,MUTED);load.setOnClickListener(v->{load.setEnabled(false);io.execute(()->{try{Map<String,Object> q=new HashMap<>();q.put("ride_id",iv(ride,0));JSONObject d=api.get(action,q);JSONArray pts=d.optJSONArray("points");main.post(()->{load.setEnabled(true);out.setText("نقاط مسیر: "+(pts==null?0:pts.length())+(d.has("distance_km")?" · "+d.optDouble("distance_km",0)+" km":""));});}catch(Exception e){main.post(()->{load.setEnabled(true);out.setText(e.getMessage());});}});});c.addView(ride);c.addView(load);c.addView(out);content.addView(c,cardLp());
    }

    private void renderAnalysis(){
        LinearLayout c=card();c.addView(text("هوا و ارتفاع",18,true,TEXT));EditText ride=input("شناسه رکاب‌زنی");Button load=primary("تحلیل فعالیت");TextView out=text("",11,false,MUTED);load.setOnClickListener(v->{load.setEnabled(false);io.execute(()->{try{Map<String,Object> q=new HashMap<>();q.put("ride_id",iv(ride,0));JSONObject d=api.get("pp_ride_analysis",q);main.post(()->{load.setEnabled(true);JSONObject x=d.optJSONObject("analysis");if(x==null)x=d;out.setText(x.toString());});}catch(Exception e){main.post(()->{load.setEnabled(true);out.setText(e.getMessage());});}});});c.addView(ride);c.addView(load);c.addView(out);content.addView(c,cardLp());
    }

    private void renderOffline(){
        LinearLayout c=card();c.addView(text("مسیرهای آفلاین",18,true,TEXT));c.addView(text("داده مسیر بدون اینترنت روی این دستگاه باقی می‌ماند.",11,false,MUTED));EditText ride=input("شناسه رکاب‌زنی");Button save=primary("ذخیره مسیر روی گوشی");TextView saved=text("",11,false,MUTED);
        SharedPreferences p=getSharedPreferences("pedalpro_offline_routes",MODE_PRIVATE);saved.setText("ذخیره‌شده: "+p.getAll().size()+" مسیر");
        save.setOnClickListener(v->{save.setEnabled(false);io.execute(()->{try{Map<String,Object> q=new HashMap<>();q.put("ride_id",iv(ride,0));JSONObject d=api.get("pp_route_pack",q);JSONObject route=d.optJSONObject("route");if(route==null)throw new IllegalStateException("مسیر دریافت نشد.");p.edit().putString(String.valueOf(route.optInt("id",0)),route.toString()).apply();main.post(()->{save.setEnabled(true);saved.setText("ذخیره‌شده: "+p.getAll().size()+" مسیر");toast("مسیر روی این گوشی ذخیره شد");});}catch(Exception e){main.post(()->{save.setEnabled(true);toast(e.getMessage());});}});});Button clear=danger("پاک‌کردن همه");clear.setOnClickListener(v->{p.edit().clear().apply();saved.setText("ذخیره‌شده: 0 مسیر");});c.addView(ride);c.addView(save);c.addView(clear);c.addView(saved);content.addView(c,cardLp());
    }

    private void renderMaintenance(){
        LinearLayout form=card();form.addView(text("سرویس و نگهداری",18,true,TEXT));EditText bike=input("شناسه دوچرخه"),title=input("مثلاً تعویض زنجیر"),km=input("دوره سرویس km");km.setText("500");Button save=primary("افزودن یادآور");save.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("bike_id",iv(bike,0));p.put("title",title.getText().toString());p.put("interval_km",iv(km,500));}catch(Exception ignored){}post("pp_maintenance_save",p,save,"یادآور سرویس ثبت شد",this::render);});form.addView(bike);form.addView(title);form.addView(km);form.addView(save);content.addView(form,cardLp());
        io.execute(()->{try{JSONObject d=api.get("pp_maintenance_list");JSONArray items=d.optJSONArray("items");main.post(()->{if(items==null||items.length()==0){addCard("سرویس","یادآور سرویس ندارید.");return;}for(int i=0;i<items.length();i++){JSONObject x=items.optJSONObject(i);if(x==null)continue;LinearLayout r=card();r.addView(text(x.optString("title","سرویس"),14,true,TEXT));r.addView(text("سرویس هر "+x.optInt("interval_km",0)+" km · موعد "+x.optDouble("next_due_km",0)+" km",11,false,MUTED));int id=x.optInt("id",0);Button done=normal("سرویس انجام شد");done.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("id",id);}catch(Exception ignored){}post("pp_maintenance_complete",p,done,"کیلومتر سرویس بروزرسانی شد",this::render);});r.addView(done);content.addView(r,cardLp());}VazirmatnLoader.apply(this,content);});}catch(Exception e){main.post(()->addCard("خطا",e.getMessage()));}});
    }

    private void openGroup(){Intent i=new Intent(this,NativeLiveNavigationActivity.class);i.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"group");startActivity(i);}
    private void openSocial(String section){Intent i=new Intent(this,NativeSocialActivity.class);i.putExtra(NativeSocialActivity.EXTRA_SECTION,section);startActivity(i);}

    private void openScreen(Class<?> cls){startActivity(new Intent(this,cls));}

    private void post(String action,JSONObject p,Button b,String ok,Runnable after){b.setEnabled(false);io.execute(()->{try{api.post(action,p);main.post(()->{b.setEnabled(true);toast(ok);after.run();});}catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}});}
    private int iv(EditText e,int fallback){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return fallback;}}
    private String formatSeconds(long v){long s=Math.max(0,v);return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);}
}
