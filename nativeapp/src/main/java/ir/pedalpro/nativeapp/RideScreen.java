package ir.pedalpro.nativeapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

final class RideScreen {
    static View normal(Context c, Runnable lowPower, Runnable finishRide) {
        LinearLayout root = NativeUi.column(c,0);
        root.setPadding(NativeUi.dp(c,18),NativeUi.dp(c,18),NativeUi.dp(c,18),NativeUi.dp(c,110));

        LinearLayout top = NativeUi.row(c);
        top.addView(NativeUi.text(c,"رکاب",26,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView gps = NativeUi.text(c,"● GPS",14,true);
        gps.setTextColor(NativeUi.SUCCESS);
        top.addView(gps);
        root.addView(top);

        NativeUi.addGap(root,c,16);
        LinearLayout map = NativeUi.card(c);
        map.setGravity(Gravity.CENTER);
        map.setBackground(NativeUi.bg(Color.rgb(10,45,59),22,c));
        TextView route = NativeUi.text(c,"🗺️\\n\\nمسیر زنده\\n\\nطرقبه  ━━━━━━━●  مشهد",22,true);
        route.setGravity(Gravity.CENTER);
        route.setTextColor(NativeUi.PRIMARY);
        map.addView(route,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,NativeUi.dp(c,260)));
        root.addView(map);

        NativeUi.addGap(root,c,14);
        LinearLayout time = NativeUi.card(c);
        TextView t1 = NativeUi.muted(c,"زمان",13); t1.setGravity(Gravity.CENTER);
        TextView t2 = NativeUi.text(c,"01:24:17",34,true); t2.setGravity(Gravity.CENTER);
        time.addView(t1); time.addView(t2);
        root.addView(time);

        NativeUi.addGap(root,c,10);
        LinearLayout stats = NativeUi.row(c);
        stats.addView(metric(c,"32.5","km","مسافت"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        stats.addView(metric(c,"28.4","km/h","سرعت لحظه‌ای"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        root.addView(stats);

        NativeUi.addGap(root,c,14);
        LinearLayout actions = NativeUi.row(c);
        TextView eco = NativeUi.button(c,"کم‌مصرف",false); eco.setOnClickListener(v->lowPower.run());
        TextView pause = NativeUi.button(c,"⏸ توقف",true);
        TextView finish = NativeUi.button(c,"■ پایان",false); finish.setTextColor(NativeUi.DANGER); finish.setOnClickListener(v->finishRide.run());
        actions.addView(eco,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(pause,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        actions.addView(finish,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        root.addView(actions);
        return root;
    }

    static View lowPower(Context c, Runnable back) {
        LinearLayout root = NativeUi.column(c,0);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(NativeUi.dp(c,24),NativeUi.dp(c,24),NativeUi.dp(c,24),NativeUi.dp(c,24));

        root.addView(bigMetric(c,"01:24:17","زمان"));
        divider(root,c);
        root.addView(bigMetric(c,"28.4","سرعت  km/h"));
        divider(root,c);
        root.addView(bigMetric(c,"32.5","مسافت  km"));
        NativeUi.addGap(root,c,24);

        TextView pause = NativeUi.button(c,"⏸",false);
        pause.setTextSize(34);
        root.addView(pause,new LinearLayout.LayoutParams(NativeUi.dp(c,92),NativeUi.dp(c,72)));
        TextView b = NativeUi.button(c,"↩ بازگشت",false);
        b.setOnClickListener(v->back.run());
        root.addView(b,NativeUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,c,20));
        return root;
    }

    private static View metric(Context c,String n,String unit,String label) {
        LinearLayout box = NativeUi.card(c);
        TextView l = NativeUi.muted(c,label,12); l.setGravity(Gravity.CENTER);
        TextView x = NativeUi.text(c,n,26,true); x.setGravity(Gravity.CENTER);
        TextView u = NativeUi.muted(c,unit,12); u.setGravity(Gravity.CENTER);
        box.addView(l); box.addView(x); box.addView(u);
        return box;
    }

    private static View bigMetric(Context c,String value,String label) {
        LinearLayout box = NativeUi.column(c,0); box.setGravity(Gravity.CENTER);
        TextView l = NativeUi.muted(c,label,15); l.setGravity(Gravity.CENTER);
        TextView v = NativeUi.text(c,value,48,true); v.setGravity(Gravity.CENTER); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(l); box.addView(v);
        return box;
    }

    private static void divider(LinearLayout root,Context c) {
        View d = new View(c); d.setBackgroundColor(Color.rgb(70,70,70));
        root.addView(d,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,NativeUi.dp(c,1)));
    }
}
