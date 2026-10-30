package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

final class NativeTrainingLoadBarsView extends View {
    private final Paint topPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bottomPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private JSONArray days=new JSONArray();
    private double maxLoad=1;

    NativeTrainingLoadBarsView(Context context){
        super(context);
        setBackground(round(Color.argb(6,255,255,255),16,Color.argb(23,255,255,255)));
        topPaint.setColor(Color.rgb(36,223,240));
        bottomPaint.setColor(Color.rgb(26,191,146));
        gridPaint.setColor(Color.argb(18,255,255,255));
        gridPaint.setStrokeWidth(dp(1));
    }

    void setDays(JSONArray value){
        days=value==null?new JSONArray():value;
        maxLoad=1;
        for(int i=0;i<days.length();i++){
            JSONObject x=days.optJSONObject(i);
            if(x!=null)maxLoad=Math.max(maxLoad,x.optDouble("load",0));
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<=1||h<=1)return;
        float pad=dp(10);
        for(int i=1;i<4;i++){
            float y=pad+(h-pad*2)*i/4f;
            c.drawLine(pad,y,w-pad,y,gridPaint);
        }
        int n=Math.max(1,days.length());
        float gap=dp(3);
        float bw=Math.max(dp(3),(w-pad*2-gap*(n-1))/n);
        float x=pad;
        for(int i=0;i<days.length();i++){
            JSONObject d=days.optJSONObject(i);
            double v=d==null?0:d.optDouble("load",0);
            float bh=(float)Math.max(dp(3),(h-pad*2)*(v/maxLoad));
            float top=h-pad-bh;
            float mid=top+bh*.48f;
            c.drawRoundRect(x,top,x+bw,mid,dp(4),dp(4),topPaint);
            c.drawRect(x,mid,x+bw,h-pad,bottomPaint);
            x+=bw+gap;
        }
    }

    private GradientDrawable round(int fill,int radius,int stroke){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if(stroke!=0)g.setStroke(Math.max(1,Math.round(dp(1))),stroke);
        return g;
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
