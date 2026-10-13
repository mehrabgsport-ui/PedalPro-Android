package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.pedalpro.app.R;
import ir.pedalpro.app.TrackingService;

/**
 * PedalPro Native 2.0 launcher styled to match the existing web application.
 * No WebView is used.
 */
public final class NativeLauncherActivity extends Activity {
    private static final int BG=Color.rgb(4,17,27);
    private static final int PANEL=Color.rgb(8,31,45);
    private static final int PANEL2=Color.rgb(10,39,55);
    private static final int TEXT=Color.rgb(244,249,251);
    private static final int MUTED=Color.rgb(166,188,198);
    private static final int CYAN=Color.rgb(31,213,227);
    private static final int BLUE=Color.rgb(31,159,235);
    private static final int GREEN=Color.rgb(49,219,161);
    private static final int ORANGE=Color.rgb(255,187,68);
    private static final int RED=Color.rgb(170,51,62);
    private static final int STROKE=Color.rgb(49,82,98);
    private static final int REQ_LOCATION=6301;

    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private PedalProApiClient api;
    private SessionStore session;
    private JSONObject data;
    private JSONObject activeRide;
    private LinearLayout content;
    private LinearLayout header;
    private TextView status;
    private Button navHome,navActivities,navRide,navRoutes,navMe;
    private Location pendingFix;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        api=new PedalProApiClient(this);
        session=new SessionStore(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BG);
        buildShell();
        load(true);
    }

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });

        header=buildHeader();
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(112)));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(14),dp(16),dp(30));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        root.addView(buildBottomNav(),new LinearLayout.LayoutParams(-1,dp(92)));
        setContentView(root);
    }

    private LinearLayout buildHeader(){
        LinearLayout h=new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(20),dp(12),dp(16),dp(12));
        h.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        h.setBackground(headerGradient());

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        h.addView(logo,new LinearLayout.LayoutParams(dp(105),dp(72)));

        LinearLayout spacer=new LinearLayout(this);
        h.addView(spacer,new LinearLayout.LayoutParams(0,1,1f));

        Button account=outline("حساب کاربری");
        account.setOnClickListener(v->me());
        h.addView(account,new LinearLayout.LayoutParams(dp(126),dp(54)));

        Button settings=square("⚙");
        settings.setOnClickListener(v->me());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(58),dp(54));sp.setMargins(dp(8),0,0,0);
        h.addView(settings,sp);

        Button menu=square("☰");
        menu.setTextSize(28);
        menu.setOnClickListener(v->menu());
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(58),dp(54));mp.setMargins(dp(8),0,0,0);
        h.addView(menu,mp);

        status=new TextView(this);
        status.setVisibility(View.GONE);
        return h;
    }

    private LinearLayout buildBottomNav(){
        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        nav.setPadding(dp(6),dp(6),dp(6),dp(7));
        nav.setBackground(bottomGradient());

        navHome=bottom("⌂\nخانه",this::home);
        navActivities=bottom("▣\nبرنامه‌ها",this::activities);
        navRide=centerRideButton();
        navRide.setOnClickListener(v->ride());
        navRoutes=bottom("⌖\nمسیرها",this::routes);
        navMe=bottom("●\nمن",this::me);

        nav.addView(navHome,bottomLp(1f,60));
        nav.addView(navActivities,bottomLp(1f,60));

        LinearLayout centerWrap=new LinearLayout(this);
        centerWrap.setGravity(Gravity.CENTER);
        centerWrap.addView(navRide,new LinearLayout.LayoutParams(dp(88),dp(88)));
        nav.addView(centerWrap,new LinearLayout.LayoutParams(0,dp(88),1.35f));

        nav.addView(navRoutes,bottomLp(1f,60));
        nav.addView(navMe,bottomLp(1f,60));
        return nav;
    }

    private Button centerRideButton(){
        Button b=new Button(this);
        b.setText("🚴\nشروع\nرکاب");
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(Color.rgb(2,34,42));
        b.setGravity(Gravity.CENTER);
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(34,238,239),Color.rgb(33,178,237)});
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(dp(2),Color.rgb(93,237,244));
        b.setBackground(g);
        b.setElevation(dp(8));
        return b;
    }

    private void load(boolean first){
        io.execute(()->{
            try{
                JSONObject d=api.bootstrap();
                data=d;activeRide=d.optJSONObject("my_active_ride");
                runOnUiThread(()->{
                    JSONObject u=d.optJSONObject("user");
                    if(u==null){login();return;}
                    if(first)home();
                });
            }catch(Exception e){
                runOnUiThread(()->error("خطای اتصال",e.getMessage()));
            }
        });
    }

    private void login(){
        select(null);content.removeAllViews();
        heroSimple("ورود به PedalPro","اپلیکیشن مستقل اندروید");
        LinearLayout box=glassCard();
        EditText user=input("نام کاربری");
        EditText pass=input("رمز عبور");
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button go=primary("ورود");
        ProgressBar p=new ProgressBar(this);p.setVisibility(View.GONE);
        box.addView(user);box.addView(pass);box.addView(go);box.addView(p);
        content.addView(box,cardLp());
        go.setOnClickListener(v->{
            String u=user.getText().toString().trim(),pw=pass.getText().toString();
            if(u.isBlank()||pw.isBlank()){toast("نام کاربری و رمز عبور را وارد کنید.");return;}
            go.setEnabled(false);p.setVisibility(View.VISIBLE);
            io.execute(()->{
                try{
                    api.login(u,pw,true);
                    data=api.bootstrap();activeRide=data.optJSONObject("my_active_ride");
                    runOnUiThread(this::home);
                }catch(Exception e){runOnUiThread(()->{go.setEnabled(true);p.setVisibility(View.GONE);toast(e.getMessage());});}
            });
        });
    }

    private void home(){
        if(!signed()){login();return;}
        select(navHome);content.removeAllViews();

        LinearLayout hero=glassCard();
        TextView kicker=txt("گروه دوچرخه سواری",17,true,TEXT);
        TextView name=txt("پدال پرو",38,true,TEXT);
        name.setTextColor(TEXT);
        TextView cyan=txt("پرو",38,true,CYAN);
        LinearLayout titleRow=new LinearLayout(this);titleRow.setOrientation(LinearLayout.HORIZONTAL);titleRow.setGravity(Gravity.END);
        titleRow.addView(cyan);titleRow.addView(name);
        TextView desc=txt("رکاب بزن، طبیعت، مسیرهای جدید",13,false,TEXT);
        desc.setPadding(0,dp(2),0,dp(10));
        hero.addView(kicker);
        hero.addView(titleRow);
        hero.addView(desc);

        Button join=primary("▣   ثبت نام برنامه");
        join.setOnClickListener(v->activities());
        Button see=outline("⌖   مشاهده مسیرها");
        see.setOnClickListener(v->routes());
        hero.addView(join);
        hero.addView(see);
        content.addView(hero,cardLp());

        sectionTitle("برنامه پیش رو  ▣");
        JSONObject upcoming=firstUpcoming();
        if(upcoming!=null) programPreview(upcoming);
        else smallCard("برنامه پیش رو ثبت نشده است.");

        sectionTitle("رکاب‌زنی زنده");
        JSONObject u=data.optJSONObject("user");int uid=u==null?0:u.optInt("id",0),shown=0;
        JSONArray rides=data.optJSONArray("rides");
        if(rides!=null)for(int i=0;i<rides.length()&&shown<2;i++){
            JSONObject r=rides.optJSONObject(i);
            if(r==null||!"active".equals(r.optString("status"))||r.optInt("owner_user_id",0)==uid)continue;
            rideCard(r,true);shown++;
        }
        if(shown==0)smallCard("در حال حاضر رکاب‌زن زنده دیگری دیده نمی‌شود.");

        sectionTitle("دسترسی سریع");
        quickRow("باشگاه","عملکرد من",()->open(NativeClubActivity.class),()->open(NativePerformanceActivity.class));
        quickRow("تحلیل حرفه‌ای","سنسورها",()->open(NativeAdvancedActivity.class),()->open(NativeSensorsActivity.class));
        quickRow("ایمنی و حریم خصوصی","فید اجتماعی",()->open(NativeSafetyActivity.class),()->open(NativeSocialActivity.class));
    }

    private void activities(){
        if(!signed()){login();return;}
        select(navActivities);content.removeAllViews();

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button add=primary("+ برنامه جدید");
        add.setEnabled(false);
        Button refresh=outline("↻ بروزرسانی");
        refresh.setOnClickListener(v->loadAnd(this::activities));
        top.addView(add,new LinearLayout.LayoutParams(0,dp(52),1f));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(52),1f);rp.setMargins(dp(8),0,0,0);top.addView(refresh,rp);
        content.addView(top,cardLp());

        LinearLayout title=glassCard();
        title.addView(txt("برنامه‌ها و فعالیت‌ها",28,true,TEXT));
        title.addView(txt("تنها اعضایی که شماره تلفن تایید شده داشته باشند امکان ثبت نام دارند.",12,false,MUTED));
        content.addView(title,cardLp());

        JSONArray events=data.optJSONArray("events");
        if(events==null)events=data.optJSONArray("programs");
        int shown=0;
        if(events!=null)for(int i=0;i<events.length();i++){
            JSONObject e=events.optJSONObject(i);if(e==null)continue;
            programCard(e,i==0?GREEN:BLUE);shown++;
        }
        if(shown==0){
            JSONArray rides=data.optJSONArray("rides");
            if(rides!=null)for(int i=0;i<rides.length()&&shown<20;i++){
                JSONObject r=rides.optJSONObject(i);if(r==null||!"planned".equals(r.optString("status")))continue;
                programCard(r,shown==0?GREEN:BLUE);shown++;
            }
        }
        if(shown==0)smallCard("برنامه فعالی برای نمایش وجود ندارد.");
    }

    private void routes(){
        if(!signed()){login();return;}
        select(navRoutes);content.removeAllViews();
        heroSimple("مسیرهای GPS","مسیرها و رکاب‌زنی‌های زنده");
        JSONArray rides=data.optJSONArray("rides");int shown=0;
        if(rides!=null)for(int i=0;i<rides.length()&&shown<50;i++){
            JSONObject r=rides.optJSONObject(i);if(r==null)continue;
            rideCard(r,"active".equals(r.optString("status")));shown++;
        }
        if(shown==0)smallCard("مسیر قابل نمایشی وجود ندارد.");
    }

    private void ride(){
        if(!signed()){login();return;}
        select(navRide);content.removeAllViews();
        if(activeRide==null){
            heroSimple("شروع رکاب‌زنی","ابتدا GPS تازه و دقیق بررسی می‌شود.");
            LinearLayout box=glassCard();
            TextView state=txt("در حال بررسی GPS…",17,true,TEXT);
            TextView accuracy=txt("دقت: —",12,false,MUTED);
            Button check=outline("بررسی دوباره GPS");
            Button start=primary("▶ شروع ثبت مسیر");
            start.setEnabled(false);
            box.addView(state);box.addView(accuracy);box.addView(check);box.addView(start);
            content.addView(box,cardLp());
            check.setOnClickListener(v->preflight(state,accuracy,start));
            start.setOnClickListener(v->startRide(start,state));
            preflight(state,accuracy,start);
            return;
        }

        heroSimple("رکاب‌زنی فعال",activeRide.optString("title","PedalPro"));
        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(metricBox("زمان",duration(activeRide.optLong("duration_seconds",0))),metricLp());
        stats.addView(metricBox("مسافت",String.format(Locale.US,"%.2f km",activeRide.optDouble("distance_km",0))),metricLp());
        stats.addView(metricBox("میانگین",String.format(Locale.US,"%.1f",activeRide.optDouble("avg_speed_kmh",0))),metricLp());
        content.addView(stats,cardLp());

        Button group=outline("◎ رسیدن به گروه در حال حرکت");
        group.setOnClickListener(v->{
            Intent x=new Intent(this,NativeLiveNavigationActivity.class);
            x.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"group");
            x.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID,activeRide.optInt("id",0));
            startActivity(x);
        });
        content.addView(group);
        Button sensors=outline("⌁ سنسورهای Cadence / Power");
        sensors.setOnClickListener(v->open(NativeSensorsActivity.class));
        content.addView(sensors);
        Button stop=danger("■ پایان رکاب‌زنی");
        stop.setOnClickListener(v->finishRide(stop));
        content.addView(stop);
    }

    private void me(){
        if(!signed()){login();return;}
        select(navMe);content.removeAllViews();
        JSONObject u=data.optJSONObject("user");
        heroSimple(u==null?"حساب من":u.optString("display_name","حساب من"),u==null?"":"@"+u.optString("username",""));
        quickRow("باشگاه","عملکرد",()->open(NativeClubActivity.class),()->open(NativePerformanceActivity.class));
        quickRow("سنسورها","تحلیل حرفه‌ای",()->open(NativeSensorsActivity.class),()->open(NativeAdvancedActivity.class));
        quickRow("ایمنی","اجتماعی",()->open(NativeSafetyActivity.class),()->open(NativeSocialActivity.class));
        Button refresh=outline("↻ بروزرسانی اطلاعات");refresh.setOnClickListener(v->loadAnd(this::me));content.addView(refresh);
        Button logout=danger("خروج از حساب");logout.setOnClickListener(v->io.execute(()->{
            try{api.logout();}catch(Exception ignored){session.clear();}
            runOnUiThread(this::login);
        }));content.addView(logout);
    }

    private void menu(){
        final Dialog d=new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setGravity(Gravity.END);
        shell.setBackgroundColor(Color.TRANSPARENT);
        shell.setPadding(dp(8),dp(8),dp(8),dp(8));

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16),dp(14),dp(16),dp(14));
        panel.setBackground(round(Color.rgb(6,35,49),28,Color.rgb(50,94,111)));
        panel.setElevation(dp(12));

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        panel.addView(logo,new LinearLayout.LayoutParams(-1,dp(138)));

        panel.addView(drawerRow("⌂  صفحه اصلی",()->{d.dismiss();home();},false));
        panel.addView(drawerRow("▣  فعالیت‌ها و برنامه‌ها",()->{d.dismiss();activities();},true));
        panel.addView(drawerRow("⌖  مسیرهای GPS",()->{d.dismiss();routes();},false));
        panel.addView(drawerRow("🚴  رکاب‌زنی",()->{d.dismiss();ride();},false));

        panel.addView(drawerSection("🧭  مسیرها و اکتشاف"));
        panel.addView(drawerSub("Heatmap و Segments",()->{d.dismiss();open(NativeAdvancedActivity.class);}));
        panel.addView(drawerSub("مسیرهای پیشنهادی",()->{d.dismiss();open(NativeSocialActivity.class);}));

        panel.addView(drawerSection("👥  باشگاه"));
        panel.addView(drawerSub("باشگاه و اعضا",()->{d.dismiss();open(NativeClubActivity.class);}));
        panel.addView(drawerSub("چالش‌ها",()->{d.dismiss();open(NativeClubActivity.class);}));

        panel.addView(drawerSection("💬  ارتباطات"));
        panel.addView(drawerSub("چت گروهی",()->{d.dismiss();open(NativeChatActivity.class);}));
        panel.addView(drawerSub("مشاور هوش مصنوعی",()->{d.dismiss();open(NativeAiActivity.class);}));

        panel.addView(drawerSection("📈  عملکرد من"));
        panel.addView(drawerSub("عملکرد و تمرین",()->{d.dismiss();open(NativePerformanceActivity.class);}));
        panel.addView(drawerSub("سنسورها",()->{d.dismiss();open(NativeSensorsActivity.class);}));

        panel.addView(drawerSection("🛡  ایمنی و حریم خصوصی"));
        panel.addView(drawerSub("SOS / Beacon / Privacy",()->{d.dismiss();open(NativeSafetyActivity.class);}));

        panel.addView(drawerRow("👤  حساب من",()->{d.dismiss();me();},false));
        panel.addView(drawerRow("⚙  مدیریت",()->{d.dismiss();me();},false));

        LinearLayout server=new LinearLayout(this);
        server.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        TextView dot=txt("●",16,true,GREEN);
        TextView st=txt("متصل به سرور",11,false,TEXT);
        server.addView(st);
        LinearLayout.LayoutParams dotLp=new LinearLayout.LayoutParams(-2,-2);dotLp.setMargins(dp(8),0,0,0);
        server.addView(dot,dotLp);
        server.setPadding(dp(10),dp(12),dp(10),dp(4));
        panel.addView(server);

        shell.addView(panel,new LinearLayout.LayoutParams(dp(360),-1));
        d.setContentView(shell);

        Window w=d.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width=WindowManager.LayoutParams.MATCH_PARENT;
            lp.height=WindowManager.LayoutParams.MATCH_PARENT;
            lp.gravity=Gravity.END;
            lp.dimAmount=.72f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.show();
    }

    private View drawerRow(String label,Runnable action,boolean active){
        Button b=new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(active?CYAN:TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        b.setPadding(dp(14),0,dp(14),0);
        b.setBackground(round(active?Color.rgb(7,71,86):PANEL2,16,active?CYAN:STROKE));
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));
        lp.setMargins(0,dp(4),0,0);
        b.setLayoutParams(lp);
        return b;
    }

    private View drawerSection(String label){
        TextView t=txt(label,16,true,TEXT);
        t.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        t.setPadding(dp(14),0,dp(14),0);
        t.setBackground(round(PANEL2,16,STROKE));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));
        lp.setMargins(0,dp(4),0,0);
        t.setLayoutParams(lp);
        return t;
    }

    private View drawerSub(String label,Runnable action){
        Button b=new Button(this);
        b.setText("‹   "+label);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        b.setPadding(dp(20),0,dp(20),0);
        b.setBackground(round(Color.rgb(8,42,57),13,Color.rgb(38,74,91)));
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));
        lp.setMargins(dp(10),dp(3),dp(10),0);
        b.setLayoutParams(lp);
        return b;
    }

    private JSONObject firstUpcoming(){
        JSONArray a=data==null?null:data.optJSONArray("events");
        if(a==null)a=data==null?null:data.optJSONArray("programs");
        return a!=null&&a.length()>0?a.optJSONObject(0):null;
    }

    private void programPreview(JSONObject e){
        LinearLayout box=glassCard();
        box.addView(txt(e.optString("title","برنامه شماره "+e.optInt("id",0)),20,true,TEXT));
        String desc=e.optString("description",e.optString("notes",""));
        if(!desc.isBlank())box.addView(txt(desc,12,false,MUTED));
        LinearLayout meta=new LinearLayout(this);meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.addView(chip("⌖ "+e.optString("location_name",e.optString("meeting_place","مسیر"))),chipLp());
        meta.addView(chip("↔ "+String.format(Locale.US,"%.0f km",e.optDouble("distance_km",0))),chipLp());
        box.addView(meta);
        content.addView(box,cardLp());
    }

    private void programCard(JSONObject e,int accent){
        LinearLayout box=glassCard();
        GradientDrawable g=round(PANEL2,22,STROKE);g.setStroke(dp(2),accent);box.setBackground(g);
        box.addView(txt(e.optString("title","برنامه شماره "+e.optInt("id",0)),21,true,TEXT));
        String info=e.optString("date",e.optString("starts_at",""))+"  ·  "+e.optString("location_name",e.optString("meeting_place",""));
        box.addView(txt(info,11,false,MUTED));
        String desc=e.optString("description",e.optString("notes",""));
        if(!desc.isBlank()){TextView d=txt(desc,12,false,TEXT);d.setPadding(0,dp(8),0,0);box.addView(d);}
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button details=outline("جزئیات");
        Button share=outline("اشتراک‌گذاری");
        Button nav=outline("مسیریابی تا شروع");
        actions.addView(details,actionLp());actions.addView(share,actionLp());actions.addView(nav,actionLp());
        box.addView(actions);
        content.addView(box,cardLp());
    }

    private void rideCard(JSONObject r,boolean live){
        LinearLayout box=glassCard();
        box.addView(txt((live?"● ":"")+r.optString("owner_name","PedalPro")+" · "+r.optString("title","رکاب‌زنی"),16,true,live?ORANGE:TEXT));
        box.addView(txt(String.format(Locale.US,"%.2f km  ·  %s  ·  %.1f km/h",
                r.optDouble("distance_km",r.optDouble("distance_m",0)/1000.0),
                duration(r.optLong("duration_seconds",0)),r.optDouble("avg_speed_kmh",0)),11,false,MUTED));
        int id=r.optInt("id",0);
        if(id>0){
            Button detail=outline("جزئیات");
            detail.setOnClickListener(v->{Intent x=new Intent(this,NativeRideDetailActivity.class);x.putExtra(NativeRideDetailActivity.EXTRA_RIDE_ID,id);startActivity(x);});
            box.addView(detail);
        }
        if(live&&id>0){
            Button track=primary("مشاهده و مسیریابی زنده");
            track.setOnClickListener(v->{
                Intent x=new Intent(this,NativeLiveNavigationActivity.class);
                x.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"live");
                x.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID,id);
                x.putExtra(NativeLiveNavigationActivity.EXTRA_TITLE,r.optString("owner_name","رکاب‌زن"));
                startActivity(x);
            });
            box.addView(track);
        }
        content.addView(box,cardLp());
    }

    private void preflight(TextView state,TextView accuracy,Button start){
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ_LOCATION);
            state.setText("دسترسی دقیق Location لازم است.");return;
        }
        pendingFix=null;start.setEnabled(false);state.setText("در حال دریافت موقعیت تازه و دقیق…");
        CancellationTokenSource token=new CancellationTokenSource();
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,token.getToken())
                .addOnSuccessListener(this,loc->{
                    if(loc==null){state.setText("موقعیت دریافت نشد.");return;}
                    long age=Math.max(0,System.currentTimeMillis()-loc.getTime());
                    float acc=loc.hasAccuracy()?loc.getAccuracy():999f;
                    accuracy.setText("دقت ±"+Math.round(acc)+" متر · تازگی "+(age/1000)+" ثانیه");
                    if(age<=12000&&acc<=35f){pendingFix=loc;state.setText("GPS آماده است.");state.setTextColor(GREEN);start.setEnabled(true);}
                    else{state.setText("GPS هنوز به دقت مناسب نرسیده است.");state.setTextColor(ORANGE);}
                })
                .addOnFailureListener(this,e->{state.setText("خطای GPS");toast(e.getMessage());});
    }

    private void startRide(Button b,TextView state){
        if(pendingFix==null)return;b.setEnabled(false);state.setText("در حال شروع…");Location fix=pendingFix;
        io.execute(()->{
            try{
                JSONObject created=api.post("start_ride",new JSONObject()
                        .put("challenge_id",0).put("event_id",0).put("title","رکاب‌زنی من")
                        .put("public_tracking",false).put("viewer_user_ids",new JSONArray()).put("bike_id",0));
                int rideId=created.optInt("ride_id",0);if(rideId<=0)throw new IllegalStateException("شناسه رکاب دریافت نشد.");
                try{api.post("track_point",new JSONObject().put("ride_id",rideId).put("lat",fix.getLatitude()).put("lng",fix.getLongitude())
                        .put("altitude",fix.hasAltitude()?fix.getAltitude():JSONObject.NULL)
                        .put("speed_kmh",fix.hasSpeed()?fix.getSpeed()*3.6:0).put("accuracy",fix.getAccuracy()).put("timestamp",System.currentTimeMillis()));}catch(Exception ignored){}
                data=api.bootstrap();activeRide=data.optJSONObject("my_active_ride");
                Intent i=new Intent(this,TrackingService.class);i.setAction(TrackingService.ACTION_START);
                i.putExtra("ride_id",rideId);i.putExtra("csrf",session.csrf());i.putExtra("max_accuracy",25d);i.putExtra("max_speed_kmh",100d);
                ContextCompat.startForegroundService(this,i);
                runOnUiThread(this::ride);
            }catch(Exception e){runOnUiThread(()->{b.setEnabled(true);state.setText("شروع انجام نشد.");toast(e.getMessage());});}
        });
    }

    private void finishRide(Button b){
        int rideId=activeRide==null?0:activeRide.optInt("id",0);if(rideId<=0)return;b.setEnabled(false);
        io.execute(()->{
            try{
                Intent s=new Intent(this,TrackingService.class);s.setAction(TrackingService.ACTION_STOP);startService(s);
                api.post("stop_ride",new JSONObject().put("ride_id",rideId).put("notes",""));
                data=api.bootstrap();activeRide=data.optJSONObject("my_active_ride");
                runOnUiThread(this::activities);
            }catch(Exception e){runOnUiThread(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void loadAnd(Runnable page){
        io.execute(()->{
            try{data=api.bootstrap();activeRide=data.optJSONObject("my_active_ride");runOnUiThread(page);}
            catch(Exception e){runOnUiThread(()->toast(e.getMessage()));}
        });
    }

    private boolean signed(){return data!=null&&data.optJSONObject("user")!=null;}
    private int liveCount(){JSONArray a=data==null?null:data.optJSONArray("rides");int n=0;if(a!=null)for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&"active".equals(r.optString("status")))n++;}return n;}

    private void open(Class<? extends Activity> cls){startActivity(new Intent(this,cls));}
    private void error(String title,String body){content.removeAllViews();heroSimple(title,body==null?"":body);Button b=primary("تلاش دوباره");b.setOnClickListener(v->load(true));content.addView(b);}
    private void heroSimple(String title,String sub){LinearLayout x=glassCard();x.addView(txt(title,24,true,TEXT));TextView s=txt(sub,12,false,MUTED);s.setPadding(0,dp(5),0,0);x.addView(s);content.addView(x,cardLp());}
    private void sectionTitle(String s){TextView t=txt(s,18,true,TEXT);t.setPadding(dp(4),dp(12),dp(4),dp(8));content.addView(t);}
    private void smallCard(String s){LinearLayout x=glassCard();x.addView(txt(s,12,false,MUTED));content.addView(x,cardLp());}

    private LinearLayout glassCard(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(16),dp(16),dp(16),dp(16));x.setBackground(round(PANEL,22,STROKE));x.setElevation(dp(2));return x;}
    private LinearLayout.LayoutParams cardLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(12));return p;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-1,1f);}
    private LinearLayout.LayoutParams bottomLp(float weight,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(h),weight);p.setMargins(dp(3),0,dp(3),0);return p;}
    private LinearLayout.LayoutParams actionLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1f);p.setMargins(dp(3),dp(8),dp(3),0);return p;}
    private LinearLayout.LayoutParams chipLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1f);p.setMargins(dp(3),dp(8),dp(3),0);return p;}
    private LinearLayout.LayoutParams metricLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(84),1f);p.setMargins(dp(4),0,dp(4),0);return p;}

    private Button bottom(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextSize(11);b.setAllCaps(false);b.setTextColor(TEXT);b.setGravity(Gravity.CENTER);b.setBackground(round(PANEL2,24,0));b.setOnClickListener(v->r.run());return b;}
    private void select(Button selected){Button[] all={navHome,navActivities,navRide,navRoutes,navMe};for(Button b:all){if(b==null)continue;b.setTextColor(b==selected?CYAN:TEXT);b.setTypeface(Typeface.DEFAULT,b==selected?Typeface.BOLD:Typeface.NORMAL);b.setBackground(b==selected?round(Color.rgb(8,58,76),24,CYAN):round(PANEL2,24,0));}}

    private View metricBox(String label,String value){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER);x.setBackground(round(PANEL2,16,STROKE));TextView v=txt(value,17,true,CYAN);v.setGravity(Gravity.CENTER);TextView l=txt(label,10,false,MUTED);l.setGravity(Gravity.CENTER);l.setPadding(0,dp(3),0,0);x.addView(v);x.addView(l);return x;}
    private View chip(String s){TextView t=txt(s,11,true,TEXT);t.setGravity(Gravity.CENTER);t.setBackground(round(PANEL2,14,STROKE));return t;}

    private void quickRow(String a,String b,Runnable ra,Runnable rb){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button x=outline(a);x.setOnClickListener(v->ra.run());Button y=outline(b);y.setOnClickListener(v->rb.run());
        row.addView(x,quickLp());row.addView(y,quickLp());content.addView(row);
    }
    private LinearLayout.LayoutParams quickLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(58),1f);p.setMargins(dp(4),dp(4),dp(4),dp(4));return p;}

    private TextView txt(String s,int sp,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.END);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setSingleLine(true);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(round(PANEL2,14,STROKE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(6),0,dp(6));e.setLayoutParams(p);return e;}

    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(Color.rgb(3,34,42));b.setGravity(Gravity.CENTER);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{CYAN,Color.rgb(27,181,246)});g.setCornerRadius(dp(16));b.setBackground(g);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(0,dp(10),0,0);b.setLayoutParams(p);return b;}
    private Button outline(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(TEXT);b.setGravity(Gravity.CENTER);b.setBackground(round(PANEL2,16,STROKE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(8),0,0);b.setLayoutParams(p);return b;}
    private Button square(String s){Button b=outline(s);b.setPadding(0,0,0,0);return b;}
    private Button danger(String s){Button b=primary(s);b.setTextColor(Color.WHITE);b.setBackground(round(RED,16,0));return b;}

    private GradientDrawable round(int fill,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}
    private GradientDrawable headerGradient(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(34,38,40),Color.rgb(4,45,63),Color.rgb(3,83,105)});g.setCornerRadii(new float[]{0,0,0,0,dp(30),dp(30),dp(30),dp(30)});g.setStroke(dp(1),STROKE);return g;}
    private GradientDrawable bottomGradient(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(8,42,58),Color.rgb(4,28,42)});g.setCornerRadii(new float[]{dp(28),dp(28),dp(28),dp(28),0,0,0,0});g.setStroke(dp(1),STROKE);return g;}
    private String duration(long s){s=Math.max(0,s);return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);}
    private void toast(String s){Toast.makeText(this,s==null?"خطا":s,Toast.LENGTH_LONG).show();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    @Override protected void onResume(){super.onResume();if(data!=null&&signed())load(false);}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
