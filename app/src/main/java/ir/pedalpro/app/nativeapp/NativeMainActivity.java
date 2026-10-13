package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Toast;

import ir.pedalpro.app.R;

/**
 * PedalPro 2.0 native shell.
 * This Activity intentionally contains no WebView and never loads pedalpro.ir UI.
 * All screens will progressively move to native Android views/components.
 */
public final class NativeMainActivity extends Activity {
    private static final int BG = Color.rgb(6, 17, 30);
    private static final int CARD = Color.rgb(13, 39, 57);
    private static final int TEXT = Color.rgb(236, 248, 252);
    private static final int MUTED = Color.rgb(153, 181, 196);
    private static final int ACCENT = Color.rgb(38, 222, 224);

    private LinearLayout content;
    private final SessionStore sessionStore bySessionStore = null;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        renderShell();
    }

    private void renderShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView top = text("PedalPro", 24, true, TEXT);
        top.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        top.setPadding(dp(20), dp(18), dp(20), dp(14));
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(18));
        scroll.addView(content);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollLp);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(7), dp(8), dp(9));

        addNav(nav, "خانه", () -> showHome());
        addNav(nav, "فعالیت‌ها", () -> showPlaceholder("فعالیت‌ها"));
        addNav(nav, "رکاب‌زنی", () -> showRide());
        addNav(nav, "مسیرها", () -> showPlaceholder("مسیرها"));
        addNav(nav, "من", () -> showPlaceholder("حساب من"));

        root.addView(nav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        showHome();
    }

    private void showHome() {
        content.removeAllViews();
        addHero("PedalPro Native 2.0", "نسخه مستقل اندروید — بدون WebView");
        addMetricRow("GPS", "Native", "BLE", "Native", "Backend", "API");
        addCard("رکاب‌زنی", "ثبت مسیر، GPS پس‌زمینه و حالت کم‌مصرف در لایه Native قرار می‌گیرد.");
        addCard("رکاب‌زنی زنده", "نمایش موقعیت اعضا و مسیریابی گروهی مستقیماً از API.");
        addCard("سنسورها", "Cadence / Power / Heart Rate بدون JavaScript Bridge.");
    }

    private void showRide() {
        content.removeAllViews();
        addHero("رکاب‌زنی", "هسته GPS فعلی PedalPro حفظ می‌شود و رابط WebView حذف خواهد شد.");
        Button start = new Button(this);
        start.setText("آماده‌سازی GPS");
        start.setOnClickListener(v -> Toast.makeText(this,
                "مرحله Native GPS UI در حال مهاجرت است.", Toast.LENGTH_SHORT).show());
        content.addView(start);
    }

    private void showPlaceholder(String title) {
        content.removeAllViews();
        addHero(title, "این بخش در شاخه Native 2.0 به رابط مستقل Android منتقل می‌شود.");
    }

    private void addHero(String title, String sub) {
        LinearLayout box = card();
        TextView t = text(title, 22, true, TEXT);
        TextView s = text(sub, 13, false, MUTED);
        s.setPadding(0, dp(7), 0, 0);
        box.addView(t);
        box.addView(s);
        content.addView(box, cardLp());
    }

    private void addCard(String title, String body) {
        LinearLayout box = card();
        box.addView(text(title, 17, true, TEXT));
        TextView b = text(body, 13, false, MUTED);
        b.setPadding(0, dp(7), 0, 0);
        box.addView(b);
        content.addView(box, cardLp());
    }

    private void addMetricRow(String a, String av, String b, String bv, String c, String cv) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric(a, av), new LinearLayout.LayoutParams(0, dp(86), 1f));
        row.addView(metric(b, bv), new LinearLayout.LayoutParams(0, dp(86), 1f));
        row.addView(metric(c, cv), new LinearLayout.LayoutParams(0, dp(86), 1f));
        content.addView(row, cardLp());
    }

    private View metric(String label, String value) {
        LinearLayout box = card();
        box.setGravity(Gravity.CENTER);
        box.addView(text(value, 16, true, ACCENT));
        box.addView(text(label, 11, false, MUTED));
        return box;
    }

    private void addNav(LinearLayout nav, String label, Runnable action) {
        TextView b = text(label, 12, true, TEXT);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        b.setOnClickListener(v -> action.run());
        nav.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(14), dp(15), dp(14));
        box.setBackgroundColor(CARD);
        return box;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        return lp;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.END);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
