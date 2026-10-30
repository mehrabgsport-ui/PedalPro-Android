package ir.pedalpro.app.nativeapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Native mirror of the current web ride-detail modal.
 * Existing tracking and map algorithms are not changed by this screen.
 */
public final class NativeRideDetailActivity extends NativeScreenBase {
    public static final String EXTRA_RIDE_ID="ride_id";

    private int rideId;
    private JSONObject ride;
    private JSONObject boot;
    private boolean active;
    private boolean owner;
    private boolean admin;
    private boolean canEdit;
    private boolean destroyed;
    private LinearLayout commentsHost;
    private NativeRidePhotoController photos;
    private PedalNeshanMapSurface profileMap;
    private NativeRideProfileView profile;
    private TextView profileDetails;
    private int selectedProfileIndex=-1;
    private NativeRideProfileView.Mode profileMode=NativeRideProfileView.Mode.ELEVATION;
    private long lastScrubAt;
    private long lastProfileMapFocusAt;
    private View liveMetricGrid;
    private int drawnPointCount=-1;
    private long drawnLastPointId=-1L;

    private final Runnable livePoll=new Runnable(){
        @Override public void run(){
            if(destroyed||!active)return;
            load(true); // Schedule the next poll only after this one completes.
        }
    };

    @Override protected String screenTitle(){return "جزئیات رکاب‌زنی";}

    @Override protected void onScreenReady(){
        rideId=getIntent().getIntExtra(EXTRA_RIDE_ID,0);
        if(rideId<=0){
            renderLoadError("شناسه رکاب معتبر نیست.");
            return;
        }
        photos=new NativeRidePhotoController(this,api,io,refreshed->{
            boot=refreshed;
            renderRide();
        });
        renderLoading();
        load(false);
    }

    private void renderLoading(){
        content.removeAllViews();
        setSubtitle("جزئیات رکاب‌زنی "+rideId,MUTED);
        LinearLayout notice=noticeCard();
        notice.addView(text("در حال دریافت جزئیات رکاب‌زنی…",15,true,TEXT));
        TextView body=text("در حال بررسی اطلاعات مسیر و نمودار سرعت. شماره فعالیت: "+rideId,12,false,MUTED);
        body.setPadding(0,dp(7),0,0);
        notice.addView(body);
        Button close=normal("بستن");
        close.setOnClickListener(v->finish());
        notice.addView(close);
        content.addView(notice,cardLp());
    }

    private void load(boolean fromPoll){
        io.execute(()->{
            try{
                Map<String,Object> q=new HashMap<>();
                q.put("ride_id",rideId);
                // Server provides a <=900-point downsampled preview, not the full
                // long-ride GPS dataset. Every stored GPS point remains untouched.
                q.put("point_mode","live");
                JSONObject details=api.get("ride_details",q);
                JSONObject r=details.optJSONObject("ride");
                // Repeated bootstrap includes whole-site histories and is expensive.
                // The already-authenticated detail poll needs only ride_details.
                JSONObject b=fromPoll?null:api.bootstrap();
                main.post(()->{
                    if(destroyed)return;
                    if(r==null){
                        if(fromPoll&&active){
                            toast("انتشار زنده این مسیر متوقف شده است.");
                            finish();
                        }else renderLoadError("مسیر خصوصی است یا اطلاعات آن در دسترس نیست.");
                        return;
                    }
                    ride=r;
                    if(b!=null)boot=b;
                    if(fromPoll && active && "active".equals(r.optString("status"))) {
                        if(android.os.SystemClock.elapsedRealtime()-lastScrubAt>=6000L)
                            updateLiveRide();
                    } else {
                        renderRide();
                    }
                    if(fromPoll && !destroyed && active)main.postDelayed(livePoll,8000L);
                });
            }catch(Exception e){
                main.post(()->{
                    if(!destroyed&&!fromPoll)renderLoadError(e.getMessage());
                    if(!destroyed&&fromPoll&&active)main.postDelayed(livePoll,8000L);
                });
            }
        });
    }

    private void renderLoadError(String message){
        content.removeAllViews();
        setSubtitle("بارگذاری جزئیات انجام نشد",RED);
        LinearLayout notice=noticeCard();
        notice.addView(text("بارگذاری جزئیات انجام نشد.",15,true,TEXT));
        TextView error=text(message==null?"خطای نامشخص در دریافت جزئیات":message,12,false,MUTED);
        error.setPadding(0,dp(7),0,0);
        notice.addView(error);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button retry=primary("تلاش دوباره");
        retry.setOnClickListener(v->{renderLoading();load(false);});
        Button close=normal("بستن");
        close.setOnClickListener(v->finish());
        actions.addView(retry,new LinearLayout.LayoutParams(0,dp(50),1f));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(50),1f);
        cp.setMargins(dp(8),0,0,0);
        actions.addView(close,cp);
        notice.addView(actions);
        content.addView(notice,cardLp());
        VazirmatnLoader.apply(this,content);
    }

    private void renderRide(){
        main.removeCallbacks(livePoll);
        content.removeAllViews();

        active="active".equals(ride.optString("status"));
        JSONObject user=boot==null?null:boot.optJSONObject("user");
        int uid=user==null?0:user.optInt("id",0);
        admin=boot!=null&&(boot.optBoolean("is_admin",false)||boot.optInt("is_admin",0)==1);
        owner=user!=null&&ride.optInt("owner_user_id",0)==uid;
        canEdit=owner||admin;

        setSubtitle(active?"مسیر زنده":"رکاب‌زنی ثبت‌شده",active?GREEN:MUTED);

        profileMap=new PedalNeshanMapSurface(this);
        profileMap.setPoints(ride.optJSONArray("points"));
        JSONArray initialPoints=ride.optJSONArray("points");
        drawnPointCount=initialPoints==null?0:initialPoints.length();
        JSONObject initialTail=drawnPointCount>0?initialPoints.optJSONObject(drawnPointCount-1):null;
        drawnLastPointId=initialTail==null?0L:initialTail.optLong("id",0L);
        double markerLat=ride.optDouble("last_lat",Double.NaN);
        double markerLng=ride.optDouble("last_lng",Double.NaN);
        if(Double.isFinite(markerLat)&&Double.isFinite(markerLng))
            profileMap.setTarget(markerLat,markerLng);

        LinearLayout notice=noticeCard();
        notice.addView(text(ride.optString("title","رکاب‌زنی"),15,true,TEXT));
        notice.addView(detailMetaRow(PedalIconView.Icon.USER,ride.optString("owner_name",""),MUTED));
        notice.addView(detailMetaRow(PedalIconView.Icon.RIDE,bikeText(findBike(ride.optInt("bike_id",0))),MUTED));
        if(!ride.optString("challenge_title","").isBlank()){
            notice.addView(detailMetaRow(PedalIconView.Icon.DETAILS,ride.optString("challenge_title"),MUTED));
        }
        notice.addView(detailMetaRow(active?PedalIconView.Icon.PLAY:PedalIconView.Icon.DETAILS,
                active?"در حال اجرا":"پایان یافته",active?GREEN:MUTED));
        content.addView(notice,cardLp());

        liveMetricGrid=metricGrid();
        content.addView(liveMetricGrid,cardLp());

        if(ride.has("met_value")&&!ride.isNull("met_value")){
            TextView energy=text("MET تقریبی: "+ride.optString("met_value")+". "+ride.optString("energy_basis",""),11,false,MUTED);
            energy.setPadding(dp(11),dp(10),dp(11),dp(10));
            energy.setBackground(round(Color.argb(9,255,255,255),13,Color.argb(26,255,255,255)));
            content.addView(energy,cardLp());
        }

        if(canEdit&&active){
            Button toggle=normal(ride.optInt("public_tracking",0)==1?"خصوصی کردن":"انتشار زنده");
            toggle.setOnClickListener(v->toggleLiveVisibility(toggle));
            content.addView(toggle,cardLp());
        }

        renderPrivacyAndLiveActions();

        // Keep the map directly above the interactive scrubber on all ride details.
        content.addView(profileMap,new LinearLayout.LayoutParams(-1,dp(310)));
        sectionTitle("پیمایش تعاملی مسیر");
        LinearLayout modes=new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button elevation=normal("ارتفاع");
        Button velocity=normal("سرعت");
        Button timeMode=normal("زمان");
        modes.addView(elevation,new LinearLayout.LayoutParams(0,dp(44),1));
        modes.addView(velocity,new LinearLayout.LayoutParams(0,dp(44),1));
        modes.addView(timeMode,new LinearLayout.LayoutParams(0,dp(44),1));
        content.addView(modes,cardLp());
        profile=new NativeRideProfileView(this);
        profile.setPoints(ride.optJSONArray("points"));
        profile.setMode(profileMode);
        profile.setListener((index,point)->{
            selectedProfileIndex=index;
            lastScrubAt=android.os.SystemClock.elapsedRealtime();
            double latitude=point.optDouble("lat",Double.NaN);
            double longitude=point.optDouble("lng",Double.NaN);
            long scrubNow=android.os.SystemClock.elapsedRealtime();
            // Do not enqueue hundreds of camera animations per second on long rides.
            // ACTION_UP always applies the final selected GPS position.
            if(profileMap!=null&&Double.isFinite(latitude)&&Double.isFinite(longitude)
                    &&(profile.isTouchFinished()||scrubNow-lastProfileMapFocusAt>=110L)){
                lastProfileMapFocusAt=scrubNow;
                profileMap.setTarget(latitude,longitude);
                profileMap.focusOn(latitude,longitude);
            }
            if(profileDetails!=null)
                profileDetails.setText(NativeRideProfileView.describe(point,index,ride.optJSONArray("points"))+
                        String.format(Locale.US," · مسافت %.2f km",profile.distanceAt(index)));
        });
        elevation.setOnClickListener(v->{profileMode=NativeRideProfileView.Mode.ELEVATION;profile.setMode(profileMode);});
        velocity.setOnClickListener(v->{profileMode=NativeRideProfileView.Mode.SPEED;profile.setMode(profileMode);});
        timeMode.setOnClickListener(v->{profileMode=NativeRideProfileView.Mode.TIME;profile.setMode(profileMode);});
        content.addView(profile,new LinearLayout.LayoutParams(-1,dp(202)));
        profileDetails=text("انگشت را روی نمودار بکشید تا محل همان نقطه روی نقشه نمایش داده شود.",12,false,MUTED);
        content.addView(profileDetails,cardLp());
        Button backToLive=normal(active?"بازگشت به موقعیت زنده":"نمایش انتهای مسیر");
        backToLive.setOnClickListener(v->{
            selectedProfileIndex=-1;
            if(profileMap!=null){
                double latitude=ride.optDouble("last_lat",Double.NaN);
                double longitude=ride.optDouble("last_lng",Double.NaN);
                if(Double.isFinite(latitude)&&Double.isFinite(longitude)){
                    profileMap.setTarget(latitude,longitude);
                    profileMap.focusOn(latitude,longitude);
                }
            }
            if(profile!=null)profile.clearSelection();
            profileDetails.setText("موقعیت انتهای مسیر نمایش داده شد.");
        });
        content.addView(backToLive,cardLp());
        double navLat=ride.optDouble("last_lat",Double.NaN);
        double navLng=ride.optDouble("last_lng",Double.NaN);
        if(Double.isFinite(navLat)&&Double.isFinite(navLng)){
            Button directions=normal("مسیریابی تا موقعیت فعلی");
            directions.setOnClickListener(v->openRouteFollower());
            content.addView(directions,cardLp());
        }

        sectionTitle("توضیحات فعالیت");
        renderNotes();

        sectionTitle("عکس‌های این رکاب‌زنی");
        photos.render(content,boot,rideId,canEdit);

        sectionTitle("دیدگاه‌ها");
        renderComments();

        // Match the website: activity owners can remove completed test rides too.
        if(owner&&!active){
            Button deleteActivity=danger("حذف این فعالیت");
            deleteActivity.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("حذف فعالیت")
                    .setMessage("این رکاب‌زنی، عکس‌ها، دیدگاه‌ها و مسیر GPS آن برای همیشه حذف شود؟")
                    .setNegativeButton("انصراف",null)
                    .setPositiveButton("حذف فعالیت",(dialog,which)->{
                        deleteActivity.setEnabled(false);
                        io.execute(()->{
                            try{
                                api.post("ride_delete",new JSONObject().put("ride_id",rideId));
                                main.post(()->{toast("فعالیت حذف شد.");finish();});
                            }catch(Exception err){
                                main.post(()->{deleteActivity.setEnabled(true);toast("حذف انجام نشد: "+err.getMessage());});
                            }
                        });
                    }).show());
            content.addView(deleteActivity,cardLp());
        }

        VazirmatnLoader.apply(this,content);
        if(active)main.postDelayed(livePoll,8000L);
    }

    /** Refresh an active ride in place: no Neshan renderer recreation, image reload,
     *  repeated comments fetch or loss of scroll/scrubber position every 8 seconds. */
    private void updateLiveRide(){
        if(profileMap==null||profile==null||liveMetricGrid==null){renderRide();return;}
        int at=content.indexOfChild(liveMetricGrid);
        if(at>=0){
            content.removeViewAt(at);
            liveMetricGrid=metricGrid();
            content.addView(liveMetricGrid,at,cardLp());
        }
        JSONArray points=ride.optJSONArray("points");
        int count=points==null?0:points.length();
        JSONObject tail=count>0?points.optJSONObject(count-1):null;
        long lastId=tail==null?0L:tail.optLong("id",0L);
        if(count!=drawnPointCount||lastId!=drawnLastPointId){
            drawnPointCount=count;
            drawnLastPointId=lastId;
            profileMap.setPoints(points);
            profile.setPoints(points);
            double latitude=ride.optDouble("last_lat",Double.NaN);
            double longitude=ride.optDouble("last_lng",Double.NaN);
            if(selectedProfileIndex<0&&Double.isFinite(latitude)&&Double.isFinite(longitude))
                profileMap.setTarget(latitude,longitude);
        }
    }

    private LinearLayout deferredMap(){
        LinearLayout host=new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setGravity(Gravity.CENTER);
        host.setPadding(dp(20),dp(20),dp(20),dp(20));
        host.setBackground(round(Color.rgb(8,24,38),13,Color.rgb(40,68,94)));

        TextView title=text("نقشه مسیر",18,true,Color.rgb(220,234,245));
        title.setGravity(Gravity.CENTER);
        host.addView(title);

        TextView note=text("برای جلوگیری از سنگین شدن اپ، نقشه فقط در صورت نیاز باز می‌شود.",12,false,Color.rgb(145,168,186));
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,-2);
        nlp.setMargins(0,dp(8),0,dp(10));
        host.addView(note,nlp);

        Button show=primary("نمایش نقشه مسیر");
        show.setOnClickListener(v->{
            host.removeAllViews();
            host.setPadding(0,0,0,0);
            PedalNeshanMapSurface route=new PedalNeshanMapSurface(this);
            route.setPoints(ride.optJSONArray("points"));
            double lat=ride.optDouble("last_lat",Double.NaN);
            double lng=ride.optDouble("last_lng",Double.NaN);
            if(Double.isFinite(lat)&&Double.isFinite(lng))route.setTarget(lat,lng);
            host.addView(route,new LinearLayout.LayoutParams(-1,-1));
        });
        host.addView(show,new LinearLayout.LayoutParams(dp(190),dp(50)));
        return host;
    }

    private View detailMetaRow(PedalIconView.Icon icon,String value,int color){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(0,dp(5),0,0);
        PedalIconView iv=new PedalIconView(this,icon,color);
        row.addView(iv,new LinearLayout.LayoutParams(dp(20),dp(20)));
        TextView label=text(value==null?"":value,12,false,color);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);
        lp.setMargins(dp(7),0,0,0);
        row.addView(label,lp);
        return row;
    }

    private View metricGrid(){
        View[] cells=new View[]{
                metric("مسافت",String.format(Locale.US,"%.2f km",ride.optDouble("distance_km",ride.optDouble("distance_m",0)/1000.0))),
                metric("زمان",formatDuration(ride.optLong("duration_seconds",0))),
                metric("میانگین",String.format(Locale.US,"%.1f km/h",ride.optDouble("avg_speed_kmh",0))),
                metric("صعود",Math.round(ride.optDouble("ascent_m",0))+" m"),
                metric("کالری تقریبی",ride.optDouble("estimated_calories",0)>0?Math.round(ride.optDouble("estimated_calories",0))+" kcal":"—"),
                metric("کار مکانیکی تقریبی",ride.optDouble("estimated_work_kj",0)>0?Math.round(ride.optDouble("estimated_work_kj",0))+" kJ":"—")
        };
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<cells.length;i+=2){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1f);
            a.setMargins(dp(4),dp(4),0,dp(4));
            LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,-2,1f);
            b.setMargins(0,dp(4),dp(4),dp(4));
            row.addView(cells[i],a);
            row.addView(cells[i+1],b);
            root.addView(row);
        }
        return root;
    }

    private View metric(String label,String value){
        LinearLayout stat=new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setGravity(Gravity.CENTER);
        stat.setPadding(dp(8),dp(11),dp(8),dp(11));
        stat.setBackground(round(Color.argb(9,255,255,255),13,Color.argb(24,255,255,255)));

        TextView small=text(label,10,false,MUTED);
        small.setGravity(Gravity.CENTER);
        TextView big=text(value,15,true,TEXT);
        big.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-1,-2);
        blp.setMargins(0,dp(5),0,0);
        stat.addView(small);
        stat.addView(big,blp);
        return stat;
    }

    private void renderPrivacyAndLiveActions(){
        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        boolean any=false;

        if(canEdit){
            PedalActionView privacy=new PedalActionView(this,PedalIconView.Icon.SHIELD,
                    "ویرایش انتشار و حریم مسیر",PedalActionView.Style.OUTLINE);
            privacy.setOnClickListener(v->NativeRidePrivacyDialog.show(
                    this,api,io,ride,boot,
                    isPublic->load(false)));
            actions.addView(privacy,new LinearLayout.LayoutParams(0,dp(50),1f));
            any=true;
        }
        if(active&&!owner){
            PedalActionView nav=new PedalActionView(this,PedalIconView.Icon.ROUTE,
                    "مسیریابی دوچرخه تا این رکاب‌زن",PedalActionView.Style.PRIMARY);
            nav.setOnClickListener(v->{
                Intent i=new Intent(this,NativeLiveNavigationActivity.class);
                i.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"live");
                i.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID,rideId);
                i.putExtra(NativeLiveNavigationActivity.EXTRA_TITLE,ride.optString("owner_name","رکاب‌زن"));
                startActivity(i);
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1f);
            if(any)lp.setMargins(dp(8),0,0,0);
            actions.addView(nav,lp);
            any=true;
        }
        if(any)content.addView(actions,cardLp());
    }

    private void renderNotes(){
        if(canEdit){
            EditText notes=input("توضیحات مسیر و تجربه رکاب‌زنی...");
            notes.setSingleLine(false);
            notes.setMinLines(4);
            notes.setText(ride.optString("notes",""));
            content.addView(notes,cardLp());

            Button save=primary("ذخیره توضیحات");
            save.setOnClickListener(v->{
                save.setEnabled(false);
                io.execute(()->{
                    try{
                        api.post("ride_notes_save",new JSONObject()
                                .put("ride_id",rideId)
                                .put("notes",notes.getText().toString()));
                        main.post(()->{save.setEnabled(true);toast("توضیحات ذخیره شد");});
                    }catch(Exception e){
                        main.post(()->{save.setEnabled(true);toast(e.getMessage());});
                    }
                });
            });
            content.addView(save,cardLp());
        }else{
            String body=ride.optString("notes","");
            TextView note=text(body.isBlank()?"توضیحی ثبت نشده است.":body,12,false,TEXT);
            note.setPadding(dp(12),dp(12),dp(12),dp(12));
            note.setBackground(round(Color.argb(8,255,255,255),14,Color.argb(28,255,255,255)));
            content.addView(note,cardLp());
        }
    }

    private void renderComments(){
        commentsHost=new LinearLayout(this);
        commentsHost.setOrientation(LinearLayout.VERTICAL);
        commentsHost.addView(text("در حال دریافت...",11,false,MUTED));
        content.addView(commentsHost,cardLp());

        JSONObject user=boot==null?null:boot.optJSONObject("user");
        if(user!=null){
            EditText comment=input("دیدگاه خود را بنویسید...");
            comment.setSingleLine(false);
            comment.setMinLines(3);
            Button send=primary("ارسال");
            send.setOnClickListener(v->sendComment(comment,send));
            content.addView(comment,cardLp());
            content.addView(send,cardLp());
        }else{
            TextView note=text("برای ثبت دیدگاه باید وارد حساب کاربری شوید. دیدگاه‌ها برای همه قابل مشاهده‌اند.",12,false,TEXT);
            note.setPadding(dp(12),dp(11),dp(12),dp(11));
            note.setBackground(round(Color.argb(8,255,255,255),14,Color.argb(28,255,255,255)));
            content.addView(note,cardLp());
        }
        loadComments();
    }

    private void loadComments(){
        io.execute(()->{
            try{
                Map<String,Object> q=new HashMap<>();
                q.put("target_type","ride");
                q.put("target_id",rideId);
                JSONObject d=api.get("comments_list",q);
                JSONArray rows=d.optJSONArray("comments");
                main.post(()->paintComments(rows));
            }catch(Exception e){
                main.post(()->{
                    if(commentsHost!=null){
                        commentsHost.removeAllViews();
                        commentsHost.addView(text("نمایش دیدگاه‌ها ممکن نشد.",11,false,RED));
                    }
                });
            }
        });
    }

    private void paintComments(JSONArray rows){
        if(commentsHost==null)return;
        commentsHost.removeAllViews();
        if(rows==null||rows.length()==0){
            commentsHost.addView(empty("هنوز دیدگاهی ثبت نشده است."));
            return;
        }

        JSONObject user=boot==null?null:boot.optJSONObject("user");
        int uid=user==null?0:user.optInt("id",0);
        for(int i=0;i<rows.length();i++){
            JSONObject c=rows.optJSONObject(i);
            if(c==null)continue;
            LinearLayout box=card();
            box.setPadding(dp(12),dp(12),dp(12),dp(12));

            LinearLayout head=new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            head.addView(text(c.optString("display_name","عضو"),11,true,TEXT),new LinearLayout.LayoutParams(0,-2,1f));
            head.addView(text(JalaliDate.format(c.optString("created_at",""),false),10,false,MUTED));
            box.addView(head);

            TextView body=text(c.optString("body",""),13,false,TEXT);
            body.setPadding(0,dp(7),0,0);
            box.addView(body);

            if(user!=null&&(admin||c.optInt("user_id",0)==uid)){
                Button del=danger("حذف");
                int commentId=c.optInt("id",0);
                del.setOnClickListener(v->new AlertDialog.Builder(this)
                        .setMessage("دیدگاه حذف شود؟")
                        .setPositiveButton("حذف",(dialog,which)->deleteComment(commentId,del))
                        .setNegativeButton("انصراف",null)
                        .show());
                LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(dp(84),dp(42));
                dp.setMargins(0,dp(8),0,0);
                box.addView(del,dp);
            }
            commentsHost.addView(box,cardLp());
        }
        VazirmatnLoader.apply(this,commentsHost);
    }

    private void sendComment(EditText input,Button send){
        String body=input.getText().toString().trim();
        if(body.isBlank())return;
        send.setEnabled(false);
        io.execute(()->{
            try{
                api.post("comment_add",new JSONObject()
                        .put("target_type","ride")
                        .put("target_id",rideId)
                        .put("body",body));
                main.post(()->{send.setEnabled(true);input.setText("");loadComments();});
            }catch(Exception e){
                main.post(()->{send.setEnabled(true);toast(e.getMessage());});
            }
        });
    }

    private void deleteComment(int id,Button button){
        button.setEnabled(false);
        io.execute(()->{
            try{
                api.post("comment_delete",new JSONObject().put("id",id));
                main.post(this::loadComments);
            }catch(Exception e){
                main.post(()->{button.setEnabled(true);toast(e.getMessage());});
            }
        });
    }

    private void toggleLiveVisibility(Button button){
        boolean next=ride.optInt("public_tracking",0)!=1;
        button.setEnabled(false);
        io.execute(()->{
            try{
                api.post("toggle_tracking",new JSONObject()
                        .put("ride_id",rideId)
                        .put("public_tracking",next));
                main.post(()->{
                    button.setEnabled(true);
                    toast(next?"انتشار زنده روشن شد":"انتشار زنده خاموش شد");
                    load(false);
                });
            }catch(Exception e){
                main.post(()->{button.setEnabled(true);toast(e.getMessage());});
            }
        });
    }

    private void openRouteFollower(){
        Intent i=new Intent(this,NativeSavedRouteFollowActivity.class);
        i.putExtra(NativeSavedRouteFollowActivity.EXTRA_RIDE_ID,rideId);
        startActivity(i);
    }

    private void openCurrentPosition(double lat,double lng){
        try{
            Uri uri=Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+lat+","+lng);
            startActivity(new Intent(Intent.ACTION_VIEW,uri));
        }catch(Exception e){
            toast("برنامه مسیریابی پیدا نشد.");
        }
    }

    private JSONObject findBike(int id){
        JSONArray bikes=boot==null?null:boot.optJSONArray("member_bikes");
        if(bikes==null)return null;
        for(int i=0;i<bikes.length();i++){
            JSONObject b=bikes.optJSONObject(i);
            if(b!=null&&b.optInt("id",0)==id)return b;
        }
        return null;
    }

    private String bikeText(JSONObject b){
        if(b==null)return "دوچرخه ثبت نشده";
        StringBuilder out=new StringBuilder();
        append(out,b.optString("brand_model",""));
        append(out,b.optString("bike_type",""));
        if(!b.optString("frame_size","").isBlank())append(out,"فریم "+b.optString("frame_size"));
        if(!b.optString("wheel_size","").isBlank())append(out,"چرخ "+b.optString("wheel_size"));
        append(out,b.optString("color",""));
        return out.length()==0?"دوچرخه بدون مشخصات":out.toString();
    }

    private void append(StringBuilder out,String value){
        if(value==null||value.isBlank())return;
        if(out.length()>0)out.append(" · ");
        out.append(value);
    }

    private LinearLayout noticeCard(){
        LinearLayout n=card();
        n.setPadding(dp(12),dp(12),dp(12),dp(12));
        return n;
    }

    private void sectionTitle(String value){
        TextView t=text(value,17,true,TEXT);
        t.setPadding(0,dp(12),0,dp(8));
        content.addView(t);
    }

    private TextView empty(String value){
        TextView t=text(value,12,false,MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12),dp(20),dp(12),dp(20));
        t.setBackground(round(Color.argb(7,255,255,255),14,Color.argb(18,255,255,255)));
        return t;
    }

    private String formatDuration(long seconds){
        long s=Math.max(0,seconds);
        return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(photos!=null&&photos.onActivityResult(requestCode,resultCode,data))return;
    }

    @Override protected void onDestroy(){
        destroyed=true;
        main.removeCallbacks(livePoll);
        super.onDestroy();
    }
}
