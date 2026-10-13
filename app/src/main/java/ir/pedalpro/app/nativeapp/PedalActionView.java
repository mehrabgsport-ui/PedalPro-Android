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
    private int accent=Color.rgb(31,231,242);

    public PedalActionView(Context c, PedalIconView.Icon type, String text, Style style){
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        int pad=dp(12);
        setPadding(pad,0,pad,0);
        icon=new PedalIconView(c,type,style==Style.PRIMARY?Color.rgb(2,30,38):Color.WHITE);
        label=new TextView(c);
        label.setText(text);
        label.setTextSize(style==Style.BOTTOM?11:13);
        label.setTextColor(style==Style.PRIMARY?Color.rgb(2,30,38):Color.WHITE);
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
            g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(29,239,244),Color.rgb(21,209,238),Color.rgb(43,178,255)});
        } else {
            g=new GradientDrawable(); g.setColor(style==Style.GHOST?Color.TRANSPARENT:Color.argb(112,7,39,54));
            if(style!=Style.GHOST)g.setStroke(dp(1),Color.argb(105,242,249,255));
        }
        g.setCornerRadius(dp(style==Style.BOTTOM?18:17));
        return g;
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
