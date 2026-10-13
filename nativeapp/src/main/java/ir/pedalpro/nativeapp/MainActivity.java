package ir.pedalpro.nativeapp;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private FrameLayout content;
    private LinearLayout nav;
    private int current = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(NativeUi.BG);
        getWindow().setNavigationBarColor(NativeUi.BG);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(NativeUi.BG);
        content = new FrameLayout(this);
        root.addView(content,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        nav = buildNav();
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,NativeUi.dp(this,82),Gravity.BOTTOM);
        np.setMargins(NativeUi.dp(this,14),0,NativeUi.dp(this,14),NativeUi.dp(this,10));
        root.addView(nav,np);
        setContentView(root);
        show(0);
    }

    private LinearLayout buildNav() {
        LinearLayout bar = NativeUi.row(this);
        bar.setPadding(NativeUi.dp(this,8),NativeUi.dp(this,8),NativeUi.dp(this,8),NativeUi.dp(this,8));
        bar.setBackground(NativeUi.stroke(NativeUi.SURFACE,Color.rgb(31,65,88),22,this));

        String[] labels = {"خانه","برنامه‌ها","شروع","مسیرها","من"};
        String[] icons = {"⌂","▣","🚴","⌖","●"};
        for (int i=0;i<labels.length;i++) {
            final int index=i;
            TextView item = NativeUi.text(this,icons[i]+"\n"+labels[i],12,i==2);
            item.setGravity(Gravity.CENTER);
            item.setTag(i);
            item.setOnClickListener(v -> {
                if(index==2) showRide();
                else show(index);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1);
            bar.addView(item,p);
        }
        return bar;
    }

    private void show(int index) {
        current=index;
        View screen;
        if(index==0) screen=NativeScreens.home(this,this::showRide);
        else if(index==1) screen=NativeScreens.programs(this);
        else if(index==3) screen=NativeScreens.routes(this);
        else screen=NativeScreens.profile(this);
        content.removeAllViews();
        content.addView(screen,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        nav.setVisibility(View.VISIBLE);
    }

    private void showRide() {
        content.removeAllViews();
        content.addView(RideScreen.normal(this,this::showLowPower,()->show(0)),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        nav.setVisibility(View.VISIBLE);
    }

    private void showLowPower() {
        content.removeAllViews();
        content.addView(RideScreen.lowPower(this,this::showRide),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        nav.setVisibility(View.GONE);
    }

    @Override public void onBackPressed() {
        if(nav.getVisibility()==View.GONE){ showRide(); return; }
        if(current!=0){ show(0); return; }
        super.onBackPressed();
    }
}
