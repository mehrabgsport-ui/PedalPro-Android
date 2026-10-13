package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class PedalActionView extends LinearLayout {
    public enum Style { PRIMARY, OUTLINE, GHOST, BOTTOM }
    private final PedalIconView icon;
    private final TextView label;
    private int accent=Color.rgb(46,230,166);

    public PedalActionView(Context c, PedalIconView.Icon type, String text, Style style){
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        int pad=dp(12);
        setPadding(pad,0,pad,0);
        icon=new PedalIconView(c,type,style==Style.PRIMARY?Color.rgb(4,36,24):Color.WHITE);
        label=new TextView(c);
        label.setText(text);
        label.setTextSize(style==Style.BOTTOM?11:13);
        label.setTextColor(style==Style.PRIMARY?Color.rgb(4,36,24):Color.WHITE);
        label.setTypeface(Typeface.DEFAULT,style==Style.GHOST?Typeface.NORMAL:Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        LayoutParams ip=new LayoutParams(dp(style==Style.BOTTOM?24:22),dp(style==Style.BOTTOM?24:22));
        ip.setMargins(0,0,dp(8),0);
        addView(icon,ip);
        addView(label,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        setBackground(background(style));
        setClickable(true); setFocusable(true);
    }

    public void setAccent(int value){ accent=value; icon.setColor(value); label.setTextColor(value); }
    public void setLabel(String value){ label.setText(value); }
    public TextView labelView(){ return label; }

    private GradientDrawable background(Style style){
        GradientDrawable g;
        if(style==Style.PRIMARY){
            g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(23,201,145),Color.rgb(46,230,166)});
        } else {
            g=new GradientDrawable(); g.setColor(style==Style.GHOST?Color.TRANSPARENT:Color.rgb(16,35,56));
            if(style!=Style.GHOST)g.setStroke(dp(1),Color.rgb(42,66,92));
        }
        g.setCornerRadius(dp(style==Style.BOTTOM?18:12));
        return g;
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
