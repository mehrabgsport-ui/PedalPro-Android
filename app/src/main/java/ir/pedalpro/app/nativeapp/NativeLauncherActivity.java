package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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

public final class NativeLauncherActivity extends Activity {
    private static final int BG=Color.rgb(4,17,27);
    private static final int PANEL=Color.argb(184,6,31,45);
    private static final int PANEL2=Color.argb(178,7,38,54);
    private static final int TEXT=Color.rgb(247,250,252);
    private static final int MUTED=Color.rgb(188,205,214);
    private static final int CYAN=Color.rgb(31,231,242);
    private static final int BLUE=Color.rgb(43,178,255);
    private static final int GREEN=Color.rgb(44,224,159);
    private static final int ORANGE=Color.rgb(255,187,68);
    private static final int RED=Color.rgb(245,70,88);
    private static final int STROKE=Color.argb(105,242,249,255);
    private static final int REQ_LOCATION=6301;

    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private PedalProApiClient api;
    private SessionStore session;
    private JSONObject data;
    private JSONObject activeRide;
    private LinearLayout content;
    private TextView status;
    private Button navHome,navActivities,navRide,navRoutes,navMe;
    private Location pendingFix;
    private String page="dashboard";

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
        FrameLayout frame=new FrameLayout(this);
        frame.setBackgroundColor(BG);

        ImageView bg=new ImageView(this);
        bg.setImageResource(R.drawable.site_bg_reference);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setAlpha(.98f);
        frame.addView(bg,new FrameLayout.LayoutParams(-1,-1));

        View shade=new View(this);
        shade.setBackgroundColor(Color.argb(58,2,10,17));
        frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setClipChildren(false);

        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });

        root.addView(buildHeader(),new LinearLayout.LayoutParams(-1,dp(90)));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),dp(14),dp(16),dp(32));
        content.setClipChildren(false);
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        root.addView(buildBottomNav(),new LinearLayout.LayoutParams(-1,dp(102)));
        frame.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
    }

    private LinearLayout buildHeader(){
        LinearLayout h=new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        h.setPadding(dp(18),dp(9),dp(14),dp(9));
        h.setBackground(headerGradient());

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        h.addView(logo,new LinearLayout.LayoutParams(dp(105),dp(68)));

        h.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));

        Button account=outlineCompact("حساب کاربری");
        account.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        account.setOnClickListener(v->me());
        h.addView(account,new LinearLayout.LayoutParams(dp(112),dp(50)));

        Button settings=outlineCompact("⚙");
        settings.setTextSize(24);
        settings.setOnClickListener(v->me());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(52),dp(50));sp.setMargins(dp(7),0,0,0);
        h.addView(settings,sp);

        Button menu=outlineCompact("☰");
        menu.setTextSize(26);
        menu.setOnClickListener(v->menu());
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(52),dp(50));mp.setMargins(dp(7),0,0,0);
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
        nav.setClipChildren(false);
        nav.setPadding(dp(8),dp(10),dp(8),dp(7));
        nav.setBackground(bottomGradient());

        navHome=bottom("⌂\nخانه",this::home);
        navActivities=bottom("▣\nبرنامه‌ها",this::activities);
        navRide=centerRideButton();
        navRide.setOnClickListener(v->ride());
        navRoutes=bottom("⌖\nمسیرها",this::routes);
        navMe=bottom("●\nمن",this::me);

        nav.addView(navHome,bottomLp(1f,68));
        nav.addView(navActivities,bottomLp(1f,68));

        LinearLayout center=new LinearLayout(this);
        center.setGravity(Gravity.CENTER);
        center.setClipChildren(false);
        navRide.setTranslationY(-dp(16));
        center.addView(navRide,new LinearLayout.LayoutParams(dp(88),dp(88)));
        nav.addView(center,new LinearLayout.LayoutParams(0,dp(92),1.25f));

        nav.addView(navRoutes,bottomLp(1f,68));
        nav.addView(navMe,bottomLp(1f,68));
        return nav;
    }

    private Button centerRideButton(){
        Button b=plainButton("🚴\nشروع\nرکاب",12,TEXT);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(Color.rgb(2,30,39));
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(33,240,244),Color.rgb(20,211,238),Color.rgb(43,178,255)});
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(dp(2),Color.rgb(111,245,249));
        b.setBackground(g);
        b.setElevation(dp(12));
        return b;
    }

    private void load(boolean first){
        io.execute(()->{
            try{
                JSONObject d=api.bootstrap();
                data=d;
                activeRide=d.optJSONObject("my_active_ride");
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
        page="login";
        select(null);
        content.removeAllViews();
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
            String u=user.getText().toString().trim();
            String pw=pass.getText().toString();
            if(u.isBlank()||pw.isBlank()){toast("نام کاربری و رمز عبور را وارد کنید.");return;}
            go.setEnabled(false);p.setVisibility(View.VISIBLE);
            io.execute(()->{
                try{
                    api.login(u,pw,true);
                    data=api.bootstrap();
                    activeRide=data.optJSONObject("my_active_ride");
                    runOnUiThread(this::home);
                }catch(Exception e){
                    runOnUiThread(()->{go.setEnabled(true);p.setVisibility(View.GONE);toast(e.getMessage());});
                }
            });
        });
    }

    private void home(){
        if(!signed()){login();return;}
        page="dashboard";
        select(navHome);
        content.removeAllViews();

        content.addView(homeHero(),cardLp());

        JSONObject upcoming=firstUpcoming();
        content.addView(upcomingCard(upcoming),cardLp());

        content.addView(weatherCard(),cardLp());
    }

    private View homeHero(){
        FrameLayout shell=new FrameLayout(this);
        shell.setBackground(round(PANEL,30,STROKE));
        shell.setElevation(dp(4));

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setPadding(dp(18),dp(20),dp(18),dp(20));

        View reveal=new View(this);
        row.addView(reveal,new LinearLayout.LayoutParams(0,dp(205),.72f));

        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        copy.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView kicker=txt("گروه دوچرخه سواری",16,true,TEXT);
        copy.addView(kicker);

        LinearLayout brand=new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.END);
        brand.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView first=txt("پدال ",37,true,TEXT);
        TextView second=txt("پرو",37,true,CYAN);
        brand.addView(first);
        brand.addView(second);
        copy.addView(brand);

        TextView tag=txt("رکاب بزن، طبیعت، مسیرهای جدید",13,false,TEXT);
        tag.setPadding(0,dp(1),0,dp(6));
        copy.addView(tag);

        Button join=primary("⊞    ثبت نام برنامه        ‹");
        join.setOnClickListener(v->activities());
        copy.addView(join);

        Button routes=outline("⌖    مشاهده مسیرها        ‹");
        routes.setOnClickListener(v->routes());
        copy.addView(routes);

        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1.28f));
        shell.addView(row,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(238));
        shell.setLayoutParams(lp);
        return shell;
    }

    private View upcomingCard(JSONObject event){
        JSONObject e=event==null?new JSONObject():event;
        LinearLayout outer=glassCard();
        outer.setPadding(dp(16),dp(15),dp(16),dp(16));

        TextView section=txt("برنامه پیش رو  ▣",20,true,TEXT);
        outer.addView(section);

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(8),0,0);

        ImageView photo=new ImageView(this);
        photo.setImageResource(R.drawable.program_reference);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackground(round(Color.TRANSPARENT,20,STROKE));
        photo.setClipToOutline(true);
        LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(dp(145),dp(150));
        imageLp.setMargins(dp(10),0,0,0);
        row.addView(photo,imageLp);

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.END);

        int id=e.optInt("id",34);
        TextView title=txt(e.optString("title","برنامه شماره "+id),19,true,TEXT);
        info.addView(title);

        String desc=e.optString("description",e.optString("notes","انشالله میریم صبحونه داخل مسیر خاکی جاغرق اگر هوا مساعد بود"));
        if(desc.isBlank())desc="انشالله میریم صبحونه داخل مسیر خاکی جاغرق اگر هوا مساعد بود";
        TextView d=txt(desc,12,false,TEXT);
        d.setMaxLines(3);
        d.setPadding(0,dp(6),0,dp(4));
        info.addView(d);

        String location=e.optString("location_name",e.optString("meeting_place","درب پارکملت"));
        double km=e.optDouble("distance_km",40);
        LinearLayout r1=new LinearLayout(this);r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(metaTile("⌖  "+String.format(Locale.US,"%.0f کیلومتر",km)),new LinearLayout.LayoutParams(0,dp(52),1f));
        r1.addView(metaTile("♟  "+location),new LinearLayout.LayoutParams(0,dp(52),1f));
        info.addView(r1);

        LinearLayout r2=new LinearLayout(this);r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(metaTile("◉  پایان اختیاری"),new LinearLayout.LayoutParams(0,dp(52),1f));
        r2.addView(metaTile("◷  "+eventDate(e)),new LinearLayout.LayoutParams(0,dp(52),1f));
        info.addView(r2);

        row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
        outer.addView(row);
        return outer;
    }

    private View weatherCard(){
        LinearLayout box=glassCard();

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title=txt("پیش‌بینی ۷ روزه هوا  ☁",20,true,TEXT);
        titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
        Button refresh=outlineCompact("↻\nبروزرسانی");
        refresh.setOnClickListener(v->toast("پیش‌بینی هوا بروزرسانی شد."));
        titleRow.addView(refresh,new LinearLayout.LayoutParams(dp(88),dp(62)));
        box.addView(titleRow);

        TextView sub=txt("باد، جهت باد، تندباد و بارندگی برای برنامه‌ریزی رکاب",12,false,MUTED);
        sub.setPadding(0,dp(2),0,dp(8));
        box.addView(sub);

        HorizontalScrollView hsv=new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout days=new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        days.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        for(int i=0;i<WeatherUiModel.DAYS.length;i++){
            LinearLayout day=new LinearLayout(this);
            day.setOrientation(LinearLayout.VERTICAL);
            day.setGravity(Gravity.CENTER);
            day.setPadding(dp(7),dp(8),dp(7),dp(8));
            day.setBackground(round(Color.argb(110,5,34,49),16,STROKE));

            TextView date=txt(WeatherUiModel.DAYS[i],11,true,TEXT);
            date.setGravity(Gravity.CENTER);
            TextView icon=txt(WeatherUiModel.ICONS[i],27,false,
                    "☀".equals(WeatherUiModel.ICONS[i])?ORANGE:CYAN);
            icon.setGravity(Gravity.CENTER);
            TextView max=txt(WeatherUiModel.MAX[i]+"°",18,true,TEXT);max.setGravity(Gravity.CENTER);
            TextView min=txt(WeatherUiModel.MIN[i]+"°",10,false,MUTED);min.setGravity(Gravity.CENTER);
            day.addView(date);day.addView(icon);day.addView(max);day.addView(min);

            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(104),dp(142));
            p.setMargins(dp(4),0,dp(4),0);
            days.addView(day,p);
        }
        hsv.addView(days);
        box.addView(hsv,new LinearLayout.LayoutParams(-1,dp(146)));
        return box;
    }

    private void activities(){
        if(!signed()){login();return;}
        page="activities";
        select(navActivities);
        content.removeAllViews();

        LinearLayout tools=new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button add=primary("+ برنامه جدید");
        add.setOnClickListener(v->toast("ایجاد برنامه از بخش مدیریت انجام می‌شود."));
        Button refresh=outline("↻  بروزرسانی");
        refresh.setOnClickListener(v->loadAnd(this::activities));
        tools.addView(add,new LinearLayout.LayoutParams(0,dp(54),1f));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(54),1f);
        rp.setMargins(dp(10),0,0,0);
        tools.addView(refresh,rp);
        content.addView(tools,cardLp());

        LinearLayout heading=glassCard();
        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.TOP);
        titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title=txt("برنامه‌ها و\nفعالیت‌ها",27,true,TEXT);
        titleRow.addView(title,new LinearLayout.LayoutParams(dp(150),-2));
        TextView desc=txt("تنها اعضایی که شماره تلفن تایید شده داشته باشند امکان ثبت نام دارند.",13,false,TEXT);
        desc.setGravity(Gravity.END);
        titleRow.addView(desc,new LinearLayout.LayoutParams(0,-2,1f));
        heading.addView(titleRow);
        content.addView(heading,cardLp());

        JSONArray events=data.optJSONArray("events");
        if(events==null)events=data.optJSONArray("programs");
        int shown=0;
        if(events!=null){
            for(int i=0;i<events.length();i++){
                JSONObject e=events.optJSONObject(i);
                if(e==null)continue;
                programCard(e,i==0?GREEN:BLUE,i==0);
                shown++;
            }
        }
        if(shown==0){
            JSONObject sample1=new JSONObject();
            try{
                sample1.put("id",34).put("title","برنامه شماره ۳۴")
                        .put("description","انشالله میریم صبحونه داخل مسیر خاکی جاغرق اگر هوا مساعد بود")
                        .put("location_name","درب پارکملت").put("distance_km",40).put("joiners_count",3);
                JSONObject sample2=new JSONObject();
                sample2.put("id",33).put("title","برنامه شماره ۳۳")
                        .put("location_name","ابتدای پارک ملت").put("distance_km",30).put("joiners_count",4);
                programCard(sample1,GREEN,true);
                programCard(sample2,BLUE,false);
            }catch(Exception ignored){}
        }
    }

    private void programCard(JSONObject e,int accent,boolean expanded){
        FrameLayout shell=new FrameLayout(this);
        shell.setBackground(round(PANEL,24,STROKE));
        shell.setElevation(dp(3));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16),dp(15),dp(16),dp(15));

        int id=e.optInt("id",0);
        String title=e.optString("title",id>0?"برنامه شماره "+id:"برنامه");
        box.addView(txt(title,20,true,TEXT));

        LinearLayout meta=new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER);
        meta.setPadding(0,dp(8),0,0);
        meta.addView(metaPlain("▣  "+eventDate(e)),new LinearLayout.LayoutParams(0,dp(44),1f));
        meta.addView(metaPlain("◷  "+e.optString("time","۰۵:۴۵")),new LinearLayout.LayoutParams(0,dp(44),1f));
        meta.addView(metaPlain("⌖  "+e.optString("location_name",e.optString("meeting_place","درب پارکملت"))),new LinearLayout.LayoutParams(0,dp(44),1f));
        meta.addView(metaPlain("↔  "+String.format(Locale.US,"%.0f کیلومتر",e.optDouble("distance_km",40))),new LinearLayout.LayoutParams(0,dp(44),1f));
        box.addView(meta);

        TextView count=txt("👥  "+e.optInt("joiners_count",expanded?3:4),11,true,Color.rgb(133,200,255));
        count.setGravity(Gravity.CENTER);
        count.setBackground(round(Color.argb(120,4,65,105),18,0));
        LinearLayout countWrap=new LinearLayout(this);countWrap.setGravity(Gravity.END);
        countWrap.addView(count,new LinearLayout.LayoutParams(dp(64),dp(34)));
        box.addView(countWrap);

        if(expanded){
            Button quick=outlineCompact("➤  اشتراک‌گذاری سریع");
            quick.setTextColor(GREEN);
            quick.setBackground(round(Color.argb(90,10,79,67),18,Color.argb(150,49,219,161)));
            quick.setOnClickListener(v->shareEvent(e));
            LinearLayout qWrap=new LinearLayout(this);qWrap.setGravity(Gravity.END);
            qWrap.addView(quick,new LinearLayout.LayoutParams(dp(150),dp(42)));
            box.addView(qWrap);

            String desc=e.optString("description",e.optString("notes","انشالله میریم صبحونه داخل مسیر خاکی جاغرق اگر هوا مساعد بود"));
            if(!desc.isBlank()){
                TextView d=txt(desc,13,false,TEXT);
                d.setPadding(0,dp(8),0,dp(6));
                box.addView(d);
            }
        }

        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        Button details=smallAction("▤  جزئیات");
        details.setOnClickListener(v->toast(title));
        Button share=smallAction("⌯  اشتراک‌گذاری");
        share.setOnClickListener(v->shareEvent(e));
        Button nav=smallAction("➤  مسیریابی تا شروع");
        nav.setOnClickListener(v->navigateEvent(e));
        row1.addView(details,actionLp());row1.addView(share,actionLp());row1.addView(nav,actionLp());
        box.addView(row1);

        if(expanded){
            LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
            Button cancel=smallAction("⊗  لغو ثبت‌نام");
            cancel.setTextColor(Color.rgb(255,132,145));
            Button edit=smallAction("✎  ویرایش");
            edit.setOnClickListener(v->toast("ویرایش برنامه از بخش مدیریت انجام می‌شود."));
            row2.addView(cancel,new LinearLayout.LayoutParams(0,dp(48),1f));
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(48),1f);ep.setMargins(dp(8),0,0,0);row2.addView(edit,ep);
            box.addView(row2);
        }

        shell.addView(box,new FrameLayout.LayoutParams(-1,-2));
        View stripe=new View(this);stripe.setBackgroundColor(accent);
        FrameLayout.LayoutParams slp=new FrameLayout.LayoutParams(dp(5),-1,Gravity.END);
        slp.setMargins(0,dp(10),0,dp(10));
        shell.addView(stripe,slp);

        content.addView(shell,cardLp());
    }

    private void routes(){
        if(!signed()){login();return;}
        page="routes";
        select(navRoutes);
        content.removeAllViews();
        heroSimple("مسیرهای GPS","مسیرها و رکاب‌زنی‌های زنده");
        JSONArray rides=data.optJSONArray("rides");
        int shown=0;
        if(rides!=null){
            for(int i=0;i<rides.length()&&shown<50;i++){
                JSONObject r=rides.optJSONObject(i);
                if(r==null)continue;
                rideCard(r,"active".equals(r.optString("status")));
                shown++;
            }
        }
        if(shown==0)smallCard("مسیر قابل نمایشی وجود ندارد.");
    }

    private void ride(){
        if(!signed()){login();return;}
        page="ride";
        select(navRide);
        content.removeAllViews();

        if(activeRide==null){
            heroSimple("شروع رکاب‌زنی","ابتدا GPS تازه و دقیق بررسی می‌شود.");
            LinearLayout box=glassCard();
            TextView state=txt("در حال بررسی GPS…",17,true,TEXT);
            TextView accuracy=txt("دقت: —",12,false,MUTED);
            Button check=outline("↻  بررسی دوباره GPS");
            Button start=primary("▶  شروع ثبت مسیر");
            start.setEnabled(false);
            box.addView(state);box.addView(accuracy);box.addView(check);box.addView(start);
            content.addView(box,cardLp());
            check.setOnClickListener(v->preflight(state,accuracy,start));
            start.setOnClickListener(v->startRide(start,state));
            preflight(state,accuracy,start);
            return;
        }

        heroSimple("رکاب‌زنی فعال",activeRide.optString("title","PedalPro"));

        LinearLayout stats=new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(metricBox("زمان",duration(activeRide.optLong("duration_seconds",0))),metricLp());
        stats.addView(metricBox("مسافت",String.format(Locale.US,"%.2f km",activeRide.optDouble("distance_km",0))),metricLp());
        stats.addView(metricBox("میانگین",String.format(Locale.US,"%.1f",activeRide.optDouble("avg_speed_kmh",0))),metricLp());
        content.addView(stats,cardLp());

        Button group=outline("◎  رسیدن به گروه در حال حرکت");
        group.setOnClickListener(v->{
            Intent x=new Intent(this,NativeLiveNavigationActivity.class);
            x.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"group");
            x.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID,activeRide.optInt("id",0));
            startActivity(x);
        });
        content.addView(group);

        Button sensors=outline("⌁  سنسورهای Cadence / Power");
        sensors.setOnClickListener(v->open(NativeSensorsActivity.class));
        content.addView(sensors);

        Button beacon=outline("◉  اشتراک زنده Beacon");
        beacon.setOnClickListener(v->open(NativeSafetyActivity.class));
        content.addView(beacon);

        Button stop=danger("■  پایان رکاب‌زنی");
        stop.setOnClickListener(v->finishRide(stop));
        content.addView(stop);
    }

    private void me(){
        if(!signed()){login();return;}
        page="me";
        select(navMe);
        content.removeAllViews();

        JSONObject u=data.optJSONObject("user");
        heroSimple(u==null?"حساب من":u.optString("display_name","حساب من"),u==null?"":"@"+u.optString("username",""));

        menuLikeLink("👥  باشگاه",()->open(NativeClubActivity.class));
        menuLikeLink("📈  عملکرد من",()->open(NativePerformanceActivity.class));
        menuLikeLink("⌁  سنسورها",()->open(NativeSensorsActivity.class));
        menuLikeLink("🧭  تحلیل حرفه‌ای و Heatmap",()->open(NativeAdvancedActivity.class));
        menuLikeLink("🛡  ایمنی و حریم خصوصی",()->open(NativeSafetyActivity.class));
        menuLikeLink("💬  ارتباطات",()->open(NativeChatActivity.class));

        Button refresh=outline("↻  بروزرسانی اطلاعات");
        refresh.setOnClickListener(v->loadAnd(this::me));
        content.addView(refresh);

        Button logout=danger("خروج از حساب");
        logout.setOnClickListener(v->io.execute(()->{
            try{api.logout();}catch(Exception ignored){session.clear();}
            runOnUiThread(this::login);
        }));
        content.addView(logout);
    }

    private void menu(){
        final Dialog d=new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout stage=new FrameLayout(this);
        stage.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14),dp(10),dp(14),dp(10));
        panel.setBackground(round(Color.argb(245,4,35,49),30,STROKE));
        panel.setElevation(dp(14));

        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(3),0,dp(3),dp(8));

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        body.addView(logo,new LinearLayout.LayoutParams(-1,dp(145)));

        body.addView(drawerRow("⌂  صفحه اصلی",()->{d.dismiss();home();},"dashboard".equals(page)));
        body.addView(drawerRow("▣  فعالیت‌ها و برنامه‌ها",()->{d.dismiss();activities();},"activities".equals(page)));
        body.addView(drawerRow("📍  مسیرهای GPS",()->{d.dismiss();routes();},"routes".equals(page)));

        body.addView(drawerSection("🚴  رکاب‌زنی                  ˄"));
        LinearLayout rideSubs=new LinearLayout(this);rideSubs.setOrientation(LinearLayout.VERTICAL);
        rideSubs.addView(drawerSub("▶  شروع / ادامه رکاب‌زنی",()->{d.dismiss();ride();}));
        rideSubs.addView(drawerSub("⌁  سنسورهای Cadence / Power",()->{d.dismiss();open(NativeSensorsActivity.class);}));
        rideSubs.addView(drawerSub("◎  رسیدن به گروه در حال حرکت",()->{d.dismiss();openGroupNav();}));
        rideSubs.addView(drawerSub("◉  اشتراک زنده Beacon",()->{d.dismiss();open(NativeSafetyActivity.class);}));
        body.addView(rideSubs);

        addCollapsed(body,"🧭  مسیرها و اکتشاف",new String[]{"Heatmap و Segments","مسیرهای پیشنهادی"},new Runnable[]{
                ()->{d.dismiss();open(NativeAdvancedActivity.class);},
                ()->{d.dismiss();open(NativeSocialActivity.class);}
        });
        addCollapsed(body,"👥  باشگاه",new String[]{"باشگاه و اعضا","چالش‌ها"},new Runnable[]{
                ()->{d.dismiss();open(NativeClubActivity.class);},
                ()->{d.dismiss();open(NativeClubActivity.class);}
        });
        addCollapsed(body,"💬  ارتباطات",new String[]{"چت گروهی","مشاور هوش مصنوعی"},new Runnable[]{
                ()->{d.dismiss();open(NativeChatActivity.class);},
                ()->{d.dismiss();open(NativeAiActivity.class);}
        });
        addCollapsed(body,"📈  عملکرد من",new String[]{"عملکرد و تمرین","سنسورها"},new Runnable[]{
                ()->{d.dismiss();open(NativePerformanceActivity.class);},
                ()->{d.dismiss();open(NativeSensorsActivity.class);}
        });
        addCollapsed(body,"🛡  ایمنی و حریم خصوصی",new String[]{"SOS و Beacon","Privacy Zones"},new Runnable[]{
                ()->{d.dismiss();open(NativeSafetyActivity.class);},
                ()->{d.dismiss();open(NativeSafetyActivity.class);}
        });
        addCollapsed(body,"👤  حساب من",new String[]{"پروفایل","دوچرخه‌های من"},new Runnable[]{
                ()->{d.dismiss();me();},
                ()->{d.dismiss();me();}
        });

        body.addView(drawerRow("⚙  مدیریت",()->{d.dismiss();me();},false));

        LinearLayout server=new LinearLayout(this);
        server.setGravity(Gravity.CENTER);
        server.setPadding(dp(8),dp(12),dp(8),dp(4));
        TextView pill=txt("●  متصل به سرور",11,true,TEXT);
        pill.setTextColor(GREEN);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(round(Color.argb(120,5,40,52),18,STROKE));
        server.addView(pill,new LinearLayout.LayoutParams(dp(150),dp(40)));
        body.addView(server);

        sc.addView(body);
        panel.addView(sc,new LinearLayout.LayoutParams(-1,0,1f));

        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(360),-1,Gravity.END);
        pp.setMargins(dp(8),dp(8),dp(8),dp(8));
        stage.addView(panel,pp);
        d.setContentView(stage);
        d.show();

        Window w=d.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width=WindowManager.LayoutParams.MATCH_PARENT;
            lp.height=WindowManager.LayoutParams.MATCH_PARENT;
            lp.gravity=Gravity.END;
            lp.dimAmount=.70f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private void addCollapsed(LinearLayout parent,String title,String[] labels,Runnable[] actions){
        TextView head=drawerSection(title+"                  ˅");
        LinearLayout subs=new LinearLayout(this);
        subs.setOrientation(LinearLayout.VERTICAL);
        subs.setVisibility(View.GONE);
        for(int i=0;i<labels.length;i++)subs.addView(drawerSub(labels[i],actions[i]));
        head.setOnClickListener(v->{
            boolean open=subs.getVisibility()!=View.VISIBLE;
            subs.setVisibility(open?View.VISIBLE:View.GONE);
            head.setText(title+(open?"                  ˄":"                  ˅"));
        });
        parent.addView(head);
        parent.addView(subs);
    }

    private View drawerRow(String label,Runnable action,boolean active){
        Button b=plainButton(label,15,active?CYAN:TEXT);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        b.setPadding(dp(14),0,dp(14),0);
        b.setBackground(round(active?Color.argb(150,4,92,106):PANEL2,17,active?CYAN:STROKE));
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));
        lp.setMargins(0,dp(4),0,0);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView drawerSection(String label){
        TextView t=txt(label,16,true,TEXT);
        t.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        t.setPadding(dp(14),0,dp(14),0);
        t.setBackground(round(PANEL2,17,STROKE));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));
        lp.setMargins(0,dp(4),0,0);
        t.setLayoutParams(lp);
        return t;
    }

    private View drawerSub(String label,Runnable action){
        Button b=plainButton("   "+label,13,TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        b.setPadding(dp(18),0,dp(18),0);
        b.setBackground(round(Color.argb(135,8,42,57),14,Color.argb(85,235,248,255)));
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
        lp.setMargins(dp(10),dp(3),dp(10),0);
        b.setLayoutParams(lp);
        return b;
    }

    private void openGroupNav(){
        Intent x=new Intent(this,NativeLiveNavigationActivity.class);
        x.putExtra(NativeLiveNavigationActivity.EXTRA_MODE,"group");
        x.putExtra(NativeLiveNavigationActivity.EXTRA_RIDE_ID,activeRide==null?0:activeRide.optInt("id",0));
        startActivity(x);
    }

    private JSONObject firstUpcoming(){
        JSONArray a=data==null?null:data.optJSONArray("events");
        if(a==null)a=data==null?null:data.optJSONArray("programs");
        return a!=null&&a.length()>0?a.optJSONObject(0):null;
    }

    private void rideCard(JSONObject r,boolean live){
        LinearLayout box=glassCard();
        box.addView(txt((live?"● ":"")+r.optString("owner_name","PedalPro")+" · "+r.optString("title","رکاب‌زنی"),16,true,live?ORANGE:TEXT));
        box.addView(txt(String.format(Locale.US,"%.2f km  ·  %s  ·  %.1f km/h",
                r.optDouble("distance_km",r.optDouble("distance_m",0)/1000.0),
                duration(r.optLong("duration_seconds",0)),
                r.optDouble("avg_speed_kmh",0)),11,false,MUTED));
        int id=r.optInt("id",0);
        if(id>0){
            Button detail=outline("▤  جزئیات مسیر");
            detail.setOnClickListener(v->{
                Intent x=new Intent(this,NativeRideDetailActivity.class);
                x.putExtra(NativeRideDetailActivity.EXTRA_RIDE_ID,id);
                startActivity(x);
            });
            box.addView(detail);
        }
        if(live&&id>0){
            Button track=primary("◎  مشاهده و مسیریابی زنده");
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

    private void menuLikeLink(String label,Runnable action){
        Button b=outline(label);
        b.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        b.setOnClickListener(v->action.run());
        content.addView(b);
    }

    private void shareEvent(JSONObject e){
        Intent share=new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        String title=e.optString("title","برنامه PedalPro");
        share.putExtra(Intent.EXTRA_TEXT,title+"\n"+e.optString("description",""));
        startActivity(Intent.createChooser(share,"اشتراک‌گذاری برنامه"));
    }

    private void navigateEvent(JSONObject e){
        double lat=e.optDouble("lat",e.optDouble("start_lat",Double.NaN));
        double lng=e.optDouble("lng",e.optDouble("start_lng",Double.NaN));
        if(!Double.isFinite(lat)||!Double.isFinite(lng)){
            toast("موقعیت شروع برنامه ثبت نشده است.");
            return;
        }
        try{
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:"+lat+","+lng+"?q="+lat+","+lng)));
        }catch(Exception ex){toast("برنامه مسیریابی پیدا نشد.");}
    }

    private String eventDate(JSONObject e){
        String v=e.optString("jalali_date",e.optString("date",""));
        if(v.isBlank())v=e.optString("starts_at","");
        if(v.isBlank())return "۲۳ مهر ۱۴۰۵\nساعت ۰۵:۴۵";
        return v;
    }

    private void preflight(TextView state,TextView accuracy,Button start){
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ_LOCATION);
            state.setText("دسترسی دقیق Location لازم است.");
            return;
        }
        pendingFix=null;
        start.setEnabled(false);
        state.setText("در حال دریافت موقعیت تازه و دقیق…");
        CancellationTokenSource token=new CancellationTokenSource();
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,token.getToken())
                .addOnSuccessListener(this,loc->{
                    if(loc==null){state.setText("موقعیت دریافت نشد.");return;}
                    long age=Math.max(0,System.currentTimeMillis()-loc.getTime());
                    float acc=loc.hasAccuracy()?loc.getAccuracy():999f;
                    accuracy.setText("دقت ±"+Math.round(acc)+" متر · تازگی "+(age/1000)+" ثانیه");
                    if(age<=12000&&acc<=35f){
                        pendingFix=loc;
                        state.setText("GPS آماده است.");
                        state.setTextColor(GREEN);
                        start.setEnabled(true);
                    }else{
                        state.setText("GPS هنوز به دقت مناسب نرسیده است.");
                        state.setTextColor(ORANGE);
                    }
                })
                .addOnFailureListener(this,e->{state.setText("خطای GPS");toast(e.getMessage());});
    }

    private void startRide(Button b,TextView state){
        if(pendingFix==null)return;
        b.setEnabled(false);
        state.setText("در حال شروع…");
        Location fix=pendingFix;

        io.execute(()->{
            try{
                JSONObject created=api.post("start_ride",new JSONObject()
                        .put("challenge_id",0)
                        .put("event_id",0)
                        .put("title","رکاب‌زنی من")
                        .put("public_tracking",false)
                        .put("viewer_user_ids",new JSONArray())
                        .put("bike_id",0));

                int rideId=created.optInt("ride_id",0);
                if(rideId<=0)throw new IllegalStateException("شناسه رکاب دریافت نشد.");

                try{
                    api.post("track_point",new JSONObject()
                            .put("ride_id",rideId)
                            .put("lat",fix.getLatitude())
                            .put("lng",fix.getLongitude())
                            .put("altitude",fix.hasAltitude()?fix.getAltitude():JSONObject.NULL)
                            .put("speed_kmh",fix.hasSpeed()?fix.getSpeed()*3.6:0)
                            .put("accuracy",fix.getAccuracy())
                            .put("timestamp",System.currentTimeMillis()));
                }catch(Exception ignored){}

                data=api.bootstrap();
                activeRide=data.optJSONObject("my_active_ride");

                Intent i=new Intent(this,TrackingService.class);
                i.setAction(TrackingService.ACTION_START);
                i.putExtra("ride_id",rideId);
                i.putExtra("csrf",session.csrf());
                i.putExtra("max_accuracy",25d);
                i.putExtra("max_speed_kmh",100d);
                ContextCompat.startForegroundService(this,i);

                runOnUiThread(this::ride);
            }catch(Exception e){
                runOnUiThread(()->{b.setEnabled(true);state.setText("شروع انجام نشد.");toast(e.getMessage());});
            }
        });
    }

    private void finishRide(Button b){
        int rideId=activeRide==null?0:activeRide.optInt("id",0);
        if(rideId<=0)return;
        b.setEnabled(false);

        io.execute(()->{
            try{
                Intent s=new Intent(this,TrackingService.class);
                s.setAction(TrackingService.ACTION_STOP);
                startService(s);
                api.post("stop_ride",new JSONObject().put("ride_id",rideId).put("notes",""));
                data=api.bootstrap();
                activeRide=data.optJSONObject("my_active_ride");
                runOnUiThread(this::activities);
            }catch(Exception e){
                runOnUiThread(()->{b.setEnabled(true);toast(e.getMessage());});
            }
        });
    }

    private void loadAnd(Runnable pageRenderer){
        io.execute(()->{
            try{
                data=api.bootstrap();
                activeRide=data.optJSONObject("my_active_ride");
                runOnUiThread(pageRenderer);
            }catch(Exception e){
                runOnUiThread(()->toast(e.getMessage()));
            }
        });
    }

    private boolean signed(){return data!=null&&data.optJSONObject("user")!=null;}

    private void open(Class<? extends Activity> cls){startActivity(new Intent(this,cls));}

    private void error(String title,String body){
        content.removeAllViews();
        heroSimple(title,body==null?"":body);
        Button b=primary("تلاش دوباره");
        b.setOnClickListener(v->load(true));
        content.addView(b);
    }

    private void heroSimple(String title,String sub){
        LinearLayout x=glassCard();
        x.addView(txt(title,24,true,TEXT));
        TextView s=txt(sub,12,false,MUTED);
        s.setPadding(0,dp(5),0,0);
        x.addView(s);
        content.addView(x,cardLp());
    }

    private void smallCard(String s){
        LinearLayout x=glassCard();
        x.addView(txt(s,12,false,MUTED));
        content.addView(x,cardLp());
    }

    private LinearLayout glassCard(){
        LinearLayout x=new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(dp(16),dp(16),dp(16),dp(16));
        x.setBackground(round(PANEL,24,STROKE));
        x.setElevation(dp(4));
        return x;
    }

    private View metaTile(String s){
        TextView t=txt(s,11,true,TEXT);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(Color.argb(104,5,35,49),14,STROKE));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1f);
        p.setMargins(dp(3),dp(3),dp(3),dp(3));
        t.setLayoutParams(p);
        return t;
    }

    private TextView metaPlain(String s){
        TextView t=txt(s,10,false,TEXT);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private View metricBox(String label,String value){
        LinearLayout x=new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setGravity(Gravity.CENTER);
        x.setBackground(round(PANEL2,16,STROKE));
        TextView v=txt(value,17,true,CYAN);v.setGravity(Gravity.CENTER);
        TextView l=txt(label,10,false,MUTED);l.setGravity(Gravity.CENTER);l.setPadding(0,dp(3),0,0);
        x.addView(v);x.addView(l);
        return x;
    }

    private Button primary(String s){
        Button b=plainButton(s,14,Color.rgb(2,29,38));
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(29,239,244),Color.rgb(21,209,238),Color.rgb(43,178,255)});
        g.setCornerRadius(dp(18));
        b.setBackground(g);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));
        p.setMargins(0,dp(9),0,0);
        b.setLayoutParams(p);
        return b;
    }

    private Button outline(String s){
        Button b=plainButton(s,13,TEXT);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(round(Color.argb(110,6,38,54),18,STROKE));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
        p.setMargins(0,dp(8),0,0);
        b.setLayoutParams(p);
        return b;
    }

    private Button outlineCompact(String s){
        Button b=plainButton(s,13,TEXT);
        b.setBackground(round(Color.argb(105,5,37,53),17,STROKE));
        return b;
    }

    private Button smallAction(String s){
        Button b=plainButton(s,11,TEXT);
        b.setBackground(round(Color.argb(100,7,39,54),15,STROKE));
        return b;
    }

    private Button danger(String s){
        Button b=primary(s);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(Color.argb(220,130,37,51),18,Color.argb(140,255,120,145)));
        return b;
    }

    private Button bottom(String s,Runnable action){
        Button b=plainButton(s,11,TEXT);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v->action.run());
        return b;
    }

    private Button plainButton(String s,int sp,int color){
        Button b=new Button(this);
        b.setText(s);
        b.setTextSize(sp);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0,0,0,0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setStateListAnimator(null);
        return b;
    }

    private EditText input(String hint){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setPadding(dp(12),dp(10),dp(12),dp(10));
        e.setBackground(round(PANEL2,14,STROKE));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));
        p.setMargins(0,dp(6),0,dp(6));
        e.setLayoutParams(p);
        return e;
    }

    private TextView txt(String s,int sp,boolean bold,int color){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.END);
        t.setLineSpacing(dp(2),1f);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    private void select(Button selected){
        Button[] all={navHome,navActivities,navRoutes,navMe};
        for(Button b:all){
            if(b==null)continue;
            b.setTextColor(b==selected?CYAN:TEXT);
            b.setTypeface(Typeface.DEFAULT,b==selected?Typeface.BOLD:Typeface.NORMAL);
            b.setAlpha(b==selected?1f:.86f);
        }
        if(navRide!=null){
            boolean rideSelected=selected==navRide;
            navRide.setScaleX(rideSelected?1.06f:1f);
            navRide.setScaleY(rideSelected?1.06f:1f);
        }
    }

    private GradientDrawable round(int fill,int radius,int stroke){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if(stroke!=0)g.setStroke(dp(1),stroke);
        return g;
    }

    private GradientDrawable headerGradient(){
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(205,44,52,53),
                        Color.argb(211,4,53,72),
                        Color.argb(220,3,82,105)});
        g.setCornerRadii(new float[]{0,0,0,0,dp(28),dp(28),dp(28),dp(28)});
        g.setStroke(dp(1),STROKE);
        return g;
    }

    private GradientDrawable bottomGradient(){
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(232,20,61,82),Color.argb(241,5,31,46)});
        g.setCornerRadii(new float[]{dp(29),dp(29),dp(29),dp(29),0,0,0,0});
        g.setStroke(dp(1),STROKE);
        return g;
    }

    private LinearLayout.LayoutParams cardLp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(12));
        return p;
    }

    private LinearLayout.LayoutParams bottomLp(float weight,int h){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(h),weight);
        p.setMargins(dp(2),0,dp(2),0);
        return p;
    }

    private LinearLayout.LayoutParams actionLp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1f);
        p.setMargins(dp(3),dp(7),dp(3),0);
        return p;
    }

    private LinearLayout.LayoutParams metricLp(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(84),1f);
        p.setMargins(dp(4),0,dp(4),0);
        return p;
    }

    private String duration(long sec){
        long s=Math.max(0,sec);
        return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);
    }

    private void toast(String s){
        Toast.makeText(this,s==null?"خطا":s,Toast.LENGTH_LONG).show();
    }

    private int dp(int v){
        return Math.round(v*getResources().getDisplayMetrics().density);
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_LOCATION&&ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            toast("دسترسی GPS فعال شد.");
        }
    }

    @Override protected void onResume(){
        super.onResume();
        if(data!=null&&signed())load(false);
    }

    @Override protected void onDestroy(){
        io.shutdownNow();
        super.onDestroy();
    }
}
