package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Web 4.7.46 button skin rendered with native Android views. */
public final class PedalActionView extends LinearLayout {
    public enum Style { PRIMARY, OUTLINE, GHOST, BOTTOM }

    private static final int WEB_TEXT = Color.rgb(247, 252, 255);
    private static final int WEB_PRIMARY_TEXT = Color.rgb(5, 40, 55);
    private static final int WEB_CYAN = Color.rgb(22, 228, 239);

    private final PedalIconView icon;
    private final TextView label;
    private final Style style;
    private int accent = WEB_CYAN;

    public PedalActionView(Context c, PedalIconView.Icon type, String text, Style style) {
        super(c);
        this.style = style;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        setPadding(dp(12), 0, dp(12), 0);

        int fg = style == Style.PRIMARY ? WEB_PRIMARY_TEXT : WEB_TEXT;
        icon = new PedalIconView(c, type, fg);
        label = new TextView(c);
        label.setText(text);
        label.setTextSize(style == Style.BOTTOM ? 11.5f : 13f);
        label.setTextColor(fg);
        label.setTypeface(Typeface.DEFAULT, style == Style.GHOST ? Typeface.NORMAL : Typeface.BOLD);
        label.setGravity(Gravity.CENTER);

        LayoutParams ip = new LayoutParams(dp(style == Style.BOTTOM ? 24 : 22), dp(style == Style.BOTTOM ? 24 : 22));
        ip.setMargins(0, 0, dp(8), 0);
        addView(icon, ip);
        addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        setBackground(background(style));
        setClickable(true);
        setFocusable(true);
        setMinimumHeight(dp(44));
        setStateListAnimator(null);
    }

    public void setAccent(int value) {
        accent = value;
        if (style != Style.PRIMARY) {
            icon.setColor(value);
            label.setTextColor(value);
        }
    }

    public void setLabel(String value) { label.setText(value); }
    public TextView labelView() { return label; }
    public PedalIconView iconView() { return icon; }

    private GradientDrawable background(Style style) {
        GradientDrawable g;
        if (style == Style.PRIMARY) {
            g = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(19, 220, 232), Color.rgb(37, 200, 244)});
            g.setCornerRadius(dp(17));
        } else {
            g = new GradientDrawable();
            g.setColor(style == Style.GHOST ? Color.TRANSPARENT : Color.argb(18, 255, 255, 255));
            if (style != Style.GHOST) g.setStroke(dp(1), Color.argb(46, 255, 255, 255));
            g.setCornerRadius(dp(style == Style.BOTTOM ? 21 : 17));
        }
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
