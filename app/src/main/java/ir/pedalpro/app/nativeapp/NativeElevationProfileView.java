package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

final class NativeElevationProfileView extends View {
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint base=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);
    private JSONArray profile=new JSONArray();

    NativeElevationProfileView(Context context){
        super(context);
        setBackground(round(Color.argb(6,255,255,255),16,Color.argb(23,255,255,255)));
        line.setColor(Color.rgb(34,215,232));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(4));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        base.setColor(Color.argb(46,255,255,255));
        base.setStrokeWidth(dp(1));
        label.setColor(Color.rgb(159,180,196));
        label.setTextSize(dp(11));
    }

    void setProfile(JSONArray value){profile=value==null?new JSONArray():value;invalidate();}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight(),pad=dp(10);
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,dmax=0;
        int count=0;
        for(int i=0;i<profile.length();i++){
            JSONObject x=profile.optJSONObject(i);if(x==null||x.isNull("altitude"))continue;
            double a=x.optDouble("altitude",Double.NaN),d=x.optDouble("distance_km",0);
            if(!Double.isFinite(a))continue;
            min=Math.min(min,a);max=Math.max(max,a);dmax=Math.max(dmax,d);count++;
        }
        if(count<2||!Double.isFinite(min)){
            c.drawText("داده ارتفاع کافی نیست.",pad,h/2f,label);return;
        }
        Path p=new Path();boolean first=true;
        for(int i=0;i<profile.length();i++){
            JSONObject x=profile.optJSONObject(i);if(x==null||x.isNull("altitude"))continue;
            double a=x.optDouble("altitude",Double.NaN),d=x.optDouble("distance_km",0);
            if(!Double.isFinite(a))continue;
            float px=pad+(float)(d/Math.max(.001,dmax))*(w-pad*2);
            float py=h-pad-(float)((a-min)/Math.max(1,max-min))*(h-pad*2);
            if(first){p.moveTo(px,py);first=false;}else p.lineTo(px,py);
        }
        c.drawPath(p,line);
        c.drawLine(pad,h-pad,w-pad,h-pad,base);
    }

    private GradientDrawable round(int fill,int radius,int stroke){
        GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));
        if(stroke!=0)g.setStroke(Math.max(1,Math.round(dp(1))),stroke);return g;
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
