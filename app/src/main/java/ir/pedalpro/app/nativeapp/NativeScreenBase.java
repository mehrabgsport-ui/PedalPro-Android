package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class NativeScreenBase extends Activity {
    protected static final int BG=Color.rgb(6,17,30), CARD=Color.rgb(13,39,57), TEXT=Color.rgb(236,248,252),
            MUTED=Color.rgb(153,181,196), ACCENT=Color.rgb(38,222,224), GREEN=Color.rgb(44,224,159),
            ORANGE=Color.rgb(255,177,67), RED=Color.rgb(255,91,103);

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
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildShell();
    }

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16),dp(14),dp(16),dp(10));
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button back=new Button(this);
        back.setText("‹");
        back.setTextSize(22);
        back.setTextColor(TEXT);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v->finish());
        TextView title=text(screenTitle(),21,true,TEXT);
        title.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        row.addView(title,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        row.addView(back,new LinearLayout.LayoutParams(dp(56),dp(48)));
        subtitle=text("",11,false,MUTED);
        head.addView(row);head.addView(subtitle);
        root.addView(head);

        ScrollView scroll=new ScrollView(this);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14),dp(4),dp(14),dp(20));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(root);
        onScreenReady();
    }

    protected abstract String screenTitle();
    protected abstract void onScreenReady();

    protected void setSubtitle(String s,int color){main.post(()->{subtitle.setText(s);subtitle.setTextColor(color);});}

    protected LinearLayout card(){
        LinearLayout x=new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(dp(14),dp(13),dp(14),dp(13));
        x.setBackgroundColor(CARD);
        return x;
    }

    protected void addCard(String title,String body){
        LinearLayout x=card();
        x.addView(text(title,16,true,TEXT));
        TextView b=text(body,12,false,MUTED);b.setPadding(0,dp(6),0,0);x.addView(b);
        content.addView(x,cardLp());
    }

    protected TextView text(String s,int sp,boolean bold,int color){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.END);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    protected EditText input(String hint){
        EditText e=new EditText(this);
        e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setSingleLine(false);
        e.setPadding(dp(11),dp(10),dp(11),dp(10));
        return e;
    }

    protected Button primary(String s){
        Button b=new Button(this);b.setText(s);b.setTextColor(Color.rgb(3,31,40));b.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(50));
        lp.setMargins(0,dp(9),0,0);b.setLayoutParams(lp);return b;
    }

    protected Button normal(String s){
        Button b=primary(s);b.setTextColor(TEXT);b.setBackgroundColor(Color.rgb(23,58,77));return b;
    }

    protected LinearLayout.LayoutParams cardLp(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(10));return lp;
    }

    protected int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    protected void toast(String s){Toast.makeText(this,s==null?"خطا":s,Toast.LENGTH_LONG).show();}

    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
