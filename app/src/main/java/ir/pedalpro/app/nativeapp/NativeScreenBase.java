package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.pedalpro.app.R;

/**
 * Shared native shell that mirrors the current 4.7.46 mobile web chrome.
 * Secondary screens keep their page heading in the scrolling content area.
 */
abstract class NativeScreenBase extends Activity {
    protected static final int BG=Color.rgb(6,17,30);
    protected static final int CARD=Color.rgb(13,28,44);
    protected static final int CARD2=Color.rgb(16,35,56);
    protected static final int TEXT=Color.rgb(245,247,251);
    protected static final int MUTED=Color.rgb(159,178,200);
    protected static final int ACCENT=Color.rgb(46,230,166);
    protected static final int GREEN=Color.rgb(46,230,166);
    protected static final int ORANGE=Color.rgb(255,124,70);
    protected static final int RED=Color.rgb(255,92,108);
    protected static final int STROKE=Color.rgb(33,54,77);

    protected final ExecutorService io=Executors.newSingleThreadExecutor();
    protected final Handler main=new Handler(Looper.getMainLooper());
    protected LinearLayout content;
    protected PedalProApiClient api;
    protected SessionStore session;

    private TextView subtitle;
    private LinearLayout webHeader;
    private PedalActionView headerAccount;
    private View headerAdminSettings;
    private TextView rideDockLabel;
    private boolean signedIn=false;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        api=new PedalProApiClient(this);
        session=new SessionStore(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(BG);
        buildShell();
    }

    private void buildShell(){
        FrameLayout frame=new FrameLayout(this);
        frame.setBackgroundColor(BG);

        ImageView bg=new ImageView(this);
        bg.setImageResource(R.drawable.site_bg_reference);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setAlpha(.82f);
        frame.addView(bg,new FrameLayout.LayoutParams(-1,-1));

        View shade=new View(this);
        shade.setBackgroundColor(Color.argb(58,2,12,20));
        frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });

        root.addView(buildWebHeader(),new LinearLayout.LayoutParams(-1,-2));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout scroller=new LinearLayout(this);
        scroller.setOrientation(LinearLayout.VERTICAL);
        scroller.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroller.setPadding(dp(14),dp(12),dp(14),dp(112));

        LinearLayout pageHead=new LinearLayout(this);
        pageHead.setOrientation(LinearLayout.VERTICAL);
        pageHead.setGravity(Gravity.END);
        TextView title=text(screenTitle(),22,true,TEXT);
        subtitle=text("",11,false,MUTED);
        pageHead.addView(title);
        LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2);
        subLp.setMargins(0,dp(4),0,0);
        pageHead.addView(subtitle,subLp);
        LinearLayout.LayoutParams headLp=new LinearLayout.LayoutParams(-1,-2);
        headLp.setMargins(dp(2),dp(3),dp(2),dp(12));
        scroller.addView(pageHead,headLp);

        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(content,new LinearLayout.LayoutParams(-1,-2));

        scroll.addView(scroller);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        root.addView(buildWebBottomNav(),new LinearLayout.LayoutParams(-1,dp(128)));

        frame.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
        onScreenReady();
        refreshChrome();
        VazirmatnLoader.apply(this,frame);
    }

    private LinearLayout buildWebHeader(){
        webHeader=new LinearLayout(this);
        webHeader.setOrientation(LinearLayout.HORIZONTAL);
        webHeader.setGravity(Gravity.CENTER_VERTICAL);
        webHeader.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        webHeader.setPadding(dp(10),dp(20),dp(10),dp(14));
        webHeader.setMinimumHeight(dp(72));
        webHeader.setBackground(headerGradient());

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setContentDescription("PedalPro");
        logo.setOnClickListener(v->openMain("dashboard"));
        webHeader.addView(logo,new LinearLayout.LayoutParams(dp(108),dp(54)));

        webHeader.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));

        headerAccount=new PedalActionView(this,PedalIconView.Icon.USER,"ورود",PedalActionView.Style.OUTLINE);
        headerAccount.setOnClickListener(v->openMain(signedIn?"me":"login"));
        webHeader.addView(headerAccount,new LinearLayout.LayoutParams(dp(105),dp(46)));

        headerAdminSettings=iconSquare(PedalIconView.Icon.SETTINGS,()->startActivity(new Intent(this,NativeSiteSettingsActivity.class)),"تنظیمات سایت");
        headerAdminSettings.setVisibility(View.GONE);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(46),dp(46));
        sp.setMargins(dp(6),0,0,0);
        webHeader.addView(headerAdminSettings,sp);

        View menu=iconSquare(PedalIconView.Icon.MENU,()->openMain("menu"),"منو");
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(46),dp(46));
        mp.setMargins(dp(6),0,0,0);
        webHeader.addView(menu,mp);
        return webHeader;
    }

    private FrameLayout buildWebBottomNav(){
        FrameLayout nav=new FrameLayout(this);
        nav.setClipChildren(false);
        nav.setClipToPadding(false);
        nav.setBackground(bottomGradient());

        LinearLayout links=new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setGravity(Gravity.CENTER_VERTICAL);
        links.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        links.setPadding(dp(8),0,dp(8),dp(3));
        links.addView(bottomItem(PedalIconView.Icon.HOME,"خانه",()->openMain("dashboard")),bottomWeight(1f,82));
        links.addView(bottomItem(PedalIconView.Icon.CALENDAR,"فعالیت‌ها",()->openMain("activities")),bottomWeight(1f,82));
        links.addView(new View(this),bottomWeight(1.12f,82));
        links.addView(bottomItem(PedalIconView.Icon.ROUTE,"مسیرها",()->openMain("routes")),bottomWeight(1f,82));
        links.addView(bottomItem(PedalIconView.Icon.USER,"من",()->openMain("me")),bottomWeight(1f,82));
        nav.addView(links,new FrameLayout.LayoutParams(-1,dp(84),Gravity.BOTTOM));

        View ride=centerRide();
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(108),dp(108),
                Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        rp.topMargin=dp(1);
        nav.addView(ride,rp);
        return nav;
    }

    private View bottomItem(PedalIconView.Icon iconType,String label,Runnable run){
        LinearLayout host=new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setGravity(Gravity.CENTER);
        host.setPadding(dp(6),dp(10),dp(6),dp(8));
        host.setClickable(true);
        host.setFocusable(true);
        host.setContentDescription(label);

        PedalIconView icon=new PedalIconView(this,iconType,Color.rgb(238,248,255));
        host.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));

        TextView name=text(label,11,true,Color.rgb(238,248,255));
        name.setTextSize(11.5f);
        name.setGravity(Gravity.CENTER);
        name.setIncludeFontPadding(false);
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-2,-2);
        nlp.setMargins(0,dp(9),0,0);
        host.addView(name,nlp);

        host.setOnClickListener(v->run.run());
        return host;
    }

    private View centerRide(){
        FrameLayout ring=new FrameLayout(this);
        GradientDrawable outer=new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(5,67,88),Color.rgb(5,30,53)});
        outer.setShape(GradientDrawable.OVAL);
        outer.setStroke(dp(2),Color.argb(220,39,233,245));
        ring.setBackground(outer);
        ring.setElevation(dp(12));
        ring.setClickable(true);
        ring.setFocusable(true);
        ring.setContentDescription("شروع یا ادامه رکاب‌زنی");
        ring.setOnClickListener(v->openMain("ride"));
        ring.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)
                v.animate().scaleX(.94f).scaleY(.94f).setDuration(110).start();
            else if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP ||
                    e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            return false;
        });

        LinearLayout inner=new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        GradientDrawable cyan=new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(33,234,244),Color.rgb(25,202,232),Color.rgb(34,157,235)});
        cyan.setShape(GradientDrawable.OVAL);
        inner.setBackground(cyan);
        PedalIconView icon=new PedalIconView(this,PedalIconView.Icon.RIDE,Color.rgb(2,51,72));
        inner.addView(icon,new LinearLayout.LayoutParams(dp(28),dp(28)));
        rideDockLabel=text("شروع رکاب",13,true,Color.rgb(4,45,65));
        rideDockLabel.setGravity(Gravity.CENTER);
        rideDockLabel.setIncludeFontPadding(false);
        inner.addView(rideDockLabel);
        ring.addView(inner,new FrameLayout.LayoutParams(dp(91),dp(91),Gravity.CENTER));
        return ring;
    }

    private View iconSquare(PedalIconView.Icon icon,Runnable run,String description){
        FrameLayout box=new FrameLayout(this);
        box.setBackground(round(Color.argb(20,255,255,255),14,Color.argb(50,255,255,255)));
        PedalIconView iv=new PedalIconView(this,icon,Color.WHITE);
        box.addView(iv,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
        box.setClickable(true);
        box.setFocusable(true);
        box.setContentDescription(description);
        box.setOnClickListener(v->run.run());
        return box;
    }

    private void refreshChrome(){
        io.execute(()->{
            try{
                JSONObject b=api.bootstrap();
                JSONObject user=b.optJSONObject("user");
                JSONObject ui=b.optJSONObject("ui");
                boolean isAdmin=b.optBoolean("is_admin",false)||b.optInt("is_admin",0)==1;
                int gap=ui==null?20:Math.max(6,Math.min(50,ui.optInt("mobile_header_top_gap_px",20)));
                String ride=ui==null?"شروع":ui.optString("ride_button_text","شروع").trim();
                if(ride.isBlank())ride="شروع";
                String finalRide=ride;
                main.post(()->{
                    signedIn=user!=null;
                    headerAccount.setLabel(signedIn?"حساب کاربری":"ورود");
                    headerAccount.iconView().setVisibility(signedIn?View.GONE:View.VISIBLE);
                    headerAdminSettings.setVisibility(isAdmin?View.VISIBLE:View.GONE);
                    webHeader.setPadding(dp(10),dp(gap),dp(10),dp(14));
                    rideDockLabel.setText(finalRide+"\nرکاب");
                });
            }catch(Exception ignored){}
        });
    }

    private void openMain(String page){
        Intent i=new Intent(this,NativeMainActivity.class);
        i.putExtra("page",page);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    protected abstract String screenTitle();
    protected abstract void onScreenReady();

    protected void setSubtitle(String s,int color){
        main.post(()->{
            subtitle.setText(s==null?"":s);
            subtitle.setTextColor(color);
        });
    }

    protected LinearLayout card(){
        LinearLayout x=new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(dp(16),dp(15),dp(16),dp(15));
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(245,15,42,61),Color.argb(251,6,27,45)});
        g.setCornerRadius(dp(26));
        g.setStroke(dp(1),Color.argb(54,255,255,255));
        x.setBackground(g);
        x.setElevation(0);
        return x;
    }

    protected LinearLayout accentCard(int accent){
        LinearLayout x=card();
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(239,16,82,96),Color.argb(248,8,42,59)});
        g.setCornerRadius(dp(26));
        g.setStroke(dp(1),accent);
        x.setBackground(g);
        return x;
    }

    protected void addCard(String title,String body){
        LinearLayout x=card();
        x.addView(text(title,17,true,TEXT));
        TextView b=text(body,12,false,MUTED);
        b.setPadding(0,dp(7),0,0);
        x.addView(b);
        content.addView(x,cardLp());
    }

    protected TextView text(String s,int sp,boolean bold,int color){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.END);
        t.setLineSpacing(dp(2),1.0f);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    protected EditText input(String hint){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(13);
        e.setSingleLine(false);
        e.setPadding(dp(12),dp(11),dp(12),dp(11));
        e.setBackground(round(Color.argb(247,5,25,40),15,Color.argb(46,255,255,255)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(6),0,dp(6));
        e.setLayoutParams(lp);
        return e;
    }

    protected Button primary(String s){
        Button b=plainButton(s,13);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(Color.rgb(5,40,55));
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(19,220,232),Color.rgb(37,200,244)});
        g.setCornerRadius(dp(17));
        b.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));
        lp.setMargins(0,dp(9),0,0);
        b.setLayoutParams(lp);
        return b;
    }

    protected Button normal(String s){
        Button b=plainButton(s,13);
        b.setBackground(round(Color.argb(236,14,38,54),17,Color.argb(46,255,255,255)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));
        lp.setMargins(0,dp(8),0,0);
        b.setLayoutParams(lp);
        return b;
    }

    protected Button danger(String s){
        Button b=normal(s);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(Color.rgb(120,36,50),12,Color.rgb(190,70,90)));
        return b;
    }

    protected TextView chip(String s){
        TextView t=text(s,11,true,TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10),dp(7),dp(10),dp(7));
        t.setBackground(round(CARD2,14,STROKE));
        return t;
    }

    protected LinearLayout.LayoutParams cardLp(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(11));
        return lp;
    }

    protected GradientDrawable round(int fill,int radius,int stroke){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if(stroke!=0)g.setStroke(dp(1),stroke);
        return g;
    }

    private Button plainButton(String s,int sp){
        Button b=new Button(this);
        b.setText(s);
        b.setTextSize(sp);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0,0,0,0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams bottomWeight(float w,int h){
        return new LinearLayout.LayoutParams(0,dp(h),w);
    }

    private GradientDrawable headerGradient(){
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(251,19,49,71),Color.argb(253,8,31,50)});
        g.setCornerRadii(new float[]{0,0,0,0,dp(26),dp(26),dp(26),dp(26)});
        g.setStroke(dp(1),Color.argb(51,255,255,255));
        return g;
    }

    private GradientDrawable bottomGradient(){
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(251,15,47,68),Color.argb(253,5,27,44)});
        g.setCornerRadii(new float[]{dp(36),dp(36),dp(36),dp(36),0,0,0,0});
        g.setStroke(dp(1),Color.argb(61,255,255,255));
        return g;
    }

    protected int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    protected void toast(String s){Toast.makeText(this,s==null?"خطا":s,Toast.LENGTH_LONG).show();}

    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
