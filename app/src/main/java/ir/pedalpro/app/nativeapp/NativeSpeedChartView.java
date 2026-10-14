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

/** Native equivalent of the web route-line-chart used in ride detail. */
final class NativeSpeedChartView extends View {
    private final Paint grid=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);
    private JSONArray points=new JSONArray();
    private double maxSpeed=1;

    NativeSpeedChartView(Context context){
        super(context);
        grid.setColor(Color.argb(28,255,255,255));
        grid.setStrokeWidth(dp(1));
        line.setColor(Color.rgb(22,228,239));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(3));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setColor(Color.argb(30,22,228,239));
        fill.setStyle(Paint.Style.FILL);
        label.setColor(Color.rgb(145,168,186));
        label.setTextSize(dp(10));

        GradientDrawable bg=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(132,9,28,43),Color.argb(150,6,21,34)});
        bg.setCornerRadius(dp(16));
        bg.setStroke(Math.max(1,Math.round(dp(1))),Color.argb(35,255,255,255));
        setBackground(bg);
    }

    void setPoints(JSONArray value){
        points=value==null?new JSONArray():value;
        maxSpeed=1;
        for(int i=0;i<points.length();i++){
            JSONObject p=points.optJSONObject(i);
            if(p==null)continue;
            double v=p.has("speed_kmh")?p.optDouble("speed_kmh",0):p.optDouble("speed",0)*3.6;
            if(Double.isFinite(v)&&v>=0&&v<100)maxSpeed=Math.max(maxSpeed,v);
        }
        maxSpeed=Math.max(10,Math.ceil(maxSpeed/10.0)*10.0);
        invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        float left=dp(12),right=dp(8),top=dp(12),bottom=dp(24);
        if(w<=left+right||h<=top+bottom)return;

        for(int i=0;i<=3;i++){
            float y=top+(h-top-bottom)*i/3f;
            c.drawLine(left,y,w-right,y,grid);
        }

        if(points.length()<2){
            c.drawText("داده سرعت کافی نیست.",left,h/2f,label);
            return;
        }

        Path strokePath=new Path();
        Path fillPath=new Path();
        boolean first=true;
        float lastX=left,lastY=h-bottom;
        int plotted=0;
        for(int i=0;i<points.length();i++){
            JSONObject p=points.optJSONObject(i);
            if(p==null)continue;
            double speed=p.has("speed_kmh")?p.optDouble("speed_kmh",Double.NaN):p.optDouble("speed",Double.NaN)*3.6;
            if(!Double.isFinite(speed)||speed<0||speed>=100)continue;
            float x=left+(w-left-right)*(i/(float)Math.max(1,points.length()-1));
            float y=h-bottom-(float)(speed/maxSpeed)*(h-top-bottom);
            if(first){
                strokePath.moveTo(x,y);
                fillPath.moveTo(x,h-bottom);
                fillPath.lineTo(x,y);
                first=false;
            }else{
                strokePath.lineTo(x,y);
                fillPath.lineTo(x,y);
            }
            lastX=x;lastY=y;plotted++;
        }
        if(plotted<2){
            c.drawText("داده سرعت کافی نیست.",left,h/2f,label);
            return;
        }
        fillPath.lineTo(lastX,h-bottom);
        fillPath.close();
        c.drawPath(fillPath,fill);
        c.drawPath(strokePath,line);
        c.drawText("0",left,h-dp(7),label);
        c.drawText(((int)maxSpeed)+" km/h",left,top+dp(10),label);
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
