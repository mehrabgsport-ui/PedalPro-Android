package ir.pedalpro.app.nativeapp;

import android.app.Activity;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.pedalpro.app.R;

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
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.TRANSPARENT);

        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(15),dp(9),dp(15),dp(9));
        head.setBackground(headerGradient());

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        TextView title=text(screenTitle(),20,true,TEXT);
        subtitle=text("",10,false,MUTED);
        titles.addView(title);
        titles.addView(subtitle);

        Button back=plainButton("‹",28);
        back.setBackground(round(Color.argb(110,7,31,45),16,STROKE));
        back.setOnClickListener(v->finish());

        head.addView(logo,new LinearLayout.LayoutParams(dp(78),dp(56)));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(56),1f);
        tp.setMargins(dp(8),0,dp(8),0);
        head.addView(titles,tp);
        head.addView(back,new LinearLayout.LayoutParams(dp(50),dp(48)));
        root.addView(head,new LinearLayout.LayoutParams(-1,dp(80)));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14),dp(14),dp(14),dp(26));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        frame.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
        onScreenReady();
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
        x.setBackground(round(CARD,22,STROKE));
        x.setElevation(dp(3));
        return x;
    }

    protected LinearLayout accentCard(int accent){
        LinearLayout x=card();
        GradientDrawable g=round(CARD2,22,STROKE);
        g.setStroke(dp(2),accent);
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
        e.setBackground(round(CARD2,14,STROKE));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(6),0,dp(6));
        e.setLayoutParams(lp);
        return e;
    }

    protected Button primary(String s){
        Button b=plainButton(s,13);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(Color.rgb(4,36,24));
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(23,201,145),Color.rgb(46,230,166)});
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));
        lp.setMargins(0,dp(9),0,0);
        b.setLayoutParams(lp);
        return b;
    }

    protected Button normal(String s){
        Button b=plainButton(s,13);
        b.setBackground(round(CARD2,12,Color.rgb(42,66,92)));
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

    private GradientDrawable headerGradient(){
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(235,7,20,32),Color.argb(235,8,22,37)});
        g.setCornerRadii(new float[]{0,0,0,0,dp(26),dp(26),dp(26),dp(26)});
        g.setStroke(dp(1),Color.rgb(23,40,58));
        return g;
    }

    protected int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    protected void toast(String s){Toast.makeText(this,s==null?"خطا":s,Toast.LENGTH_LONG).show();}

    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
