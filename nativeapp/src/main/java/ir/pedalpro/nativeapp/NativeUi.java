package ir.pedalpro.nativeapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

final class NativeUi {
    static final int BG = Color.rgb(7,17,27);
    static final int SURFACE = Color.rgb(14,28,42);
    static final int SURFACE_2 = Color.rgb(19,38,56);
    static final int PRIMARY = Color.rgb(18,215,255);
    static final int TEXT = Color.rgb(244,247,251);
    static final int MUTED = Color.rgb(148,168,184);
    static final int SUCCESS = Color.rgb(34,197,94);
    static final int DANGER = Color.rgb(239,68,68);

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable bg(int color, int radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable stroke(int color, int strokeColor, int radiusDp, Context c) {
        GradientDrawable g = bg(color, radiusDp, c);
        g.setStroke(dp(c, 1), strokeColor);
        return g;
    }

    static TextView text(Context c, String value, int sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextColor(TEXT);
        t.setTextSize(sp);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    static TextView muted(Context c, String value, int sp) {
        TextView t = text(c, value, sp, false);
        t.setTextColor(MUTED);
        return t;
    }

    static LinearLayout column(Context c, int gapDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.RIGHT);
        if (gapDp > 0) l.setPadding(0, 0, 0, dp(c, gapDp));
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = column(c, 0);
        l.setPadding(dp(c,16), dp(c,16), dp(c,16), dp(c,16));
        l.setBackground(stroke(SURFACE, Color.rgb(31,65,88), 18, c));
        return l;
    }

    static TextView button(Context c, String label, boolean primary) {
        TextView b = text(c, label, 16, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c,16), dp(c,14), dp(c,16), dp(c,14));
        b.setBackground(primary ? bg(PRIMARY, 16, c) : stroke(SURFACE_2, Color.rgb(42,78,101), 16, c));
        b.setTextColor(primary ? Color.rgb(1,31,42) : TEXT);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    static LinearLayout.LayoutParams lp(int w, int h, Context c, int marginTop) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(c, marginTop);
        return p;
    }

    static void addGap(ViewGroup parent, Context c, int hDp) {
        View v = new View(c);
        parent.addView(v, new ViewGroup.LayoutParams(1, dp(c,hDp)));
    }
}
