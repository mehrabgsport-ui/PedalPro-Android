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

/**
 * Shared web-parity visual shell for all native feature pages.
 * Native views only; no WebView.
 */
abstract class NativeScreenBase extends Activity {
    protected static final int BG=Color.rgb(4,17,27);
    protected static final int CARD=Color.rgb(8,31,45);
    protected static final int CARD2=Color.rgb(10,39,55);
    protected static final int TEXT=Color.rgb(244,249,251);
    protected static final int MUTED=Color.rgb(166,188,198);
    protected static final int ACCENT=Color.rgb(31,213,227);
    protected static final int GREEN=Color.rgb(49,219,161);
    protected static final int ORANGE=Color.rgb(255,187,68);
    protected static final int RED=Color.rgb(255,91,103);
    protected static final int STROKE=Color.rgb(49,82,98);

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
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);
            return insets;
        });

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16),dp(10),dp(16),dp(10));
        head.setBackground(headerGradient());

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.pedalpro_app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        TextView title=text(screenTitle(),21,true,TEXT);
        subtitle=text("",11,false,MUTED);
        titles.addView(title);
        titles.addView(subtitle);

        Button back=new Button(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setTextColor(TEXT);
        back.setAllCaps(false);
        back.setPadding(0,0,0,0);
        back.setBackground(round(CARD2,15,STROKE));
        back.setOnClickListener(v->finish());

        head.addView(logo,new LinearLayout.LayoutParams(dp(80),dp(58)));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(58),1f);tp.setMargins(dp(8),0,dp(8),0);
        head.addView(titles,tp);
        head.addView(back,new LinearLayout.LayoutParams(dp(52),dp(50)));
        root.addView(head,new LinearLayout.LayoutParams(-1,dp(86)));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14),dp(14),dp(14),dp(24));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        setContentView(root);
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
        x.setBackground(round(CARD,20,STROKE));
        x.setElevation(dp(2));
        return x;
    }

    protected LinearLayout accentCard(int accent){
        LinearLayout x=card();
        GradientDrawable g=round(CARD2,20,STROKE);
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
        Button b=new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(Color.rgb(3,34,42));
        b.setGravity(Gravity.CENTER);
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT,Color.rgb(27,181,246)});
        g.setCornerRadius(dp(16));
        b.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));
        lp.setMargins(0,dp(9),0,0);
        b.setLayoutParams(lp);
        return b;
    }

    protected Button normal(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(CARD2,16,STROKE));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));
        lp.setMargins(0,dp(8),0,0);
        b.setLayoutParams(lp);
        return b;
    }

    protected Button danger(String s){
        Button b=normal(s);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(Color.rgb(152,50,62),16,0));
        return b;
    }

    protected TextView chip(String s){
        TextView t=text(s,11,true,TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10),dp(7),dp(10),dp(7));
        t.setBackground(round(CARD2,13,STROKE));
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

    private GradientDrawable headerGradient(){
        GradientDrawable g=new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(35,39,41),Color.rgb(4,47,65),Color.rgb(3,81,104)});
        g.setCornerRadii(new float[]{0,0,0,0,dp(28),dp(28),dp(28),dp(28)});
        g.setStroke(dp(1),STROKE);
        return g;
    }

    protected int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    protected void toast(String s){Toast.makeText(this,s==null?"خطا":s,Toast.LENGTH_LONG).show();}

    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
