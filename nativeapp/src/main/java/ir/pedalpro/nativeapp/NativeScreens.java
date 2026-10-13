package ir.pedalpro.nativeapp;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class NativeScreens {
    static View home(Context c, Runnable onStartRide) {
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        LinearLayout root = NativeUi.column(c,0);
        root.setPadding(NativeUi.dp(c,18), NativeUi.dp(c,18), NativeUi.dp(c,18), NativeUi.dp(c,110));
        root.addView(header(c, "PedalPro", "سلام، رکاب امروز آماده‌ست"));
        NativeUi.addGap(root,c,20);

        TextView start = NativeUi.button(c,"🚴  شروع رکاب",true);
        start.setOnClickListener(v -> onStartRide.run());
        root.addView(start, NativeUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,c,0));

        NativeUi.addGap(root,c,18);
        root.addView(sectionTitle(c,"خلاصه امروز"));
        root.addView(statsRow(c,new String[][]{
                {"42.5","کیلومتر"},{"2:15","زمان"},{"980","کالری"},{"4","رکاب"}
        }));

        NativeUi.addGap(root,c,22);
        root.addView(sectionTitle(c,"برنامه بعدی"));
        LinearLayout program = NativeUi.card(c);
        program.addView(NativeUi.text(c,"رکاب غروب طرقبه",20,true));
        program.addView(NativeUi.muted(c,"جمعه ۲۴ مهر · ۱۶:۰۰",14));
        NativeUi.addGap(program,c,10);
        program.addView(NativeUi.muted(c,"📍 طرقبه   •   ۴۵ کیلومتر   •   ۱۸ نفر",14));
        TextView details = NativeUi.button(c,"جزئیات برنامه",false);
        program.addView(details, NativeUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,c,14));
        root.addView(program);

        NativeUi.addGap(root,c,22);
        root.addView(sectionTitle(c,"وضعیت هوا"));
        LinearLayout weather = NativeUi.card(c);
        LinearLayout wRow = NativeUi.row(c);
        wRow.addView(NativeUi.text(c,"☀️ 20°",26,true), new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        wRow.addView(NativeUi.muted(c,"مشهد · صاف · باد 12 km/h",14), new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,2));
        weather.addView(wRow);
        root.addView(weather);

        scroll.addView(root);
        return scroll;
    }

    static View programs(Context c) {
        ScrollView scroll = new ScrollView(c);
        LinearLayout root = NativeUi.column(c,0);
        root.setPadding(NativeUi.dp(c,18), NativeUi.dp(c,18), NativeUi.dp(c,18), NativeUi.dp(c,110));
        root.addView(header(c,"برنامه‌ها","برنامه‌های گروه و رکاب‌های آینده"));
        NativeUi.addGap(root,c,16);
        root.addView(NativeUi.button(c,"+ برنامه جدید",true));
        NativeUi.addGap(root,c,16);

        String[][] items = {
                {"#34","رکاب غروب طرقبه","جمعه ۲۴ مهر · ۱۶:۰۰","۴۵ کیلومتر · ۱۸ نفر"},
                {"#33","صعود به شاندیز","شنبه ۲۵ مهر · ۰۶:۰۰","۴۵ کیلومتر · ۲۴ نفر"},
                {"#32","رکاب خانوادگی پارک ملت","پنجشنبه ۸ آبان · ۱۷:۰۰","۳۰ کیلومتر · ۴۴ نفر"}
        };
        for (String[] x: items) {
            LinearLayout card = NativeUi.card(c);
            LinearLayout title = NativeUi.row(c);
            title.addView(NativeUi.text(c,x[1],18,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            title.addView(NativeUi.muted(c,x[0],13));
            card.addView(title);
            card.addView(NativeUi.muted(c,x[2],14));
            card.addView(NativeUi.muted(c,x[3],14));
            LinearLayout actions = NativeUi.row(c);
            TextView d = NativeUi.button(c,"جزئیات",false);
            TextView r = NativeUi.button(c,"ثبت‌نام",true);
            actions.addView(d,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            actions.addView(r,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            card.addView(actions, NativeUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,c,14));
            root.addView(card,NativeUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,c,12));
        }
        scroll.addView(root);
        return scroll;
    }

    static View routes(Context c) {
        ScrollView scroll = new ScrollView(c);
        LinearLayout root = NativeUi.column(c,0);
        root.setPadding(NativeUi.dp(c,18),NativeUi.dp(c,18),NativeUi.dp(c,18),NativeUi.dp(c,110));
        root.addView(header(c,"مسیرها","مسیرهای ذخیره‌شده و عمومی"));
        NativeUi.addGap(root,c,16);
        String[][] routes = {
                {"مسیر کوهسنگی – طرقبه","۴۵ کیلومتر","۸۵۰ متر"},
                {"شاندیز – بینالود","۶۵ کیلومتر","۱۴۵۰ متر"},
                {"مسیر شهری مشهد","۲۵ کیلومتر","۳۵۰ متر"}
        };
        for (String[] x: routes) {
            LinearLayout card = NativeUi.card(c);
            card.addView(NativeUi.text(c,x[0],18,true));
            card.addView(NativeUi.muted(c,"📍 "+x[1]+"   •   ↗ "+x[2]+" صعود",14));
            TextView play = NativeUi.button(c,"نمایش مسیر",false);
            card.addView(play,NativeUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,c,12));
            root.addView(card,NativeUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,c,12));
        }
        scroll.addView(root);
        return scroll;
    }

    static View profile(Context c) {
        ScrollView scroll = new ScrollView(c);
        LinearLayout root = NativeUi.column(c,0);
        root.setPadding(NativeUi.dp(c,18),NativeUi.dp(c,18),NativeUi.dp(c,18),NativeUi.dp(c,110));
        root.addView(header(c,"حساب کاربری","علی رضایی · @alireza"));
        NativeUi.addGap(root,c,18);
        root.addView(statsRow(c,new String[][]{{"213","رکاب"},{"4,850","کیلومتر"},{"325","ساعت"},{"12","مدال"}}));
        NativeUi.addGap(root,c,18);
        String[] rows = {"اطلاعات شخصی","دوچرخه‌های من","سنسورها و دستگاه‌ها","تنظیمات","حریم خصوصی","راهنما و پشتیبانی"};
        for (String s: rows) {
            TextView row = NativeUi.button(c,s+"   ‹",false);
            row.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            root.addView(row,NativeUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,c,8));
        }
        scroll.addView(root);
        return scroll;
    }

    private static View header(Context c,String title,String sub) {
        LinearLayout box = NativeUi.column(c,0);
        box.addView(NativeUi.text(c,title,28,true));
        box.addView(NativeUi.muted(c,sub,14));
        return box;
    }

    private static View sectionTitle(Context c,String s) {
        TextView t = NativeUi.text(c,s,18,true);
        t.setPadding(0,0,0,NativeUi.dp(c,10));
        return t;
    }

    private static View statsRow(Context c,String[][] stats) {
        LinearLayout row = NativeUi.row(c);
        for (String[] s: stats) {
            LinearLayout box = NativeUi.card(c);
            box.setGravity(Gravity.CENTER);
            TextView n = NativeUi.text(c,s[0],20,true); n.setGravity(Gravity.CENTER);
            TextView l = NativeUi.muted(c,s[1],12); l.setGravity(Gravity.CENTER);
            box.addView(n); box.addView(l);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
            p.setMargins(NativeUi.dp(c,3),0,NativeUi.dp(c,3),0);
            row.addView(box,p);
        }
        return row;
    }
}
