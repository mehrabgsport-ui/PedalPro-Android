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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/** Native equivalent of Web 4.7.46 speedChart()/ppSmoothSpeedPoints(). */
final class NativeSpeedChartView extends View {
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Double> values=new ArrayList<>();
    private double chartMax=10d;

    NativeSpeedChartView(Context context){
        super(context);
        line.setColor(Color.rgb(43,146,255));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(4));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);

        fill.setColor(Color.argb(72,43,146,255));
        fill.setStyle(Paint.Style.FILL);

        label.setColor(Color.rgb(159,178,200));
        label.setTextSize(dp(11));

        GradientDrawable bg=new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(84,9,28,43),Color.argb(104,6,21,34)});
        bg.setCornerRadius(dp(16));
        bg.setStroke(Math.max(1,Math.round(dp(1))),Color.argb(28,255,255,255));
        setBackground(bg);
    }

    void setPoints(JSONArray points){
        values.clear();
        ArrayList<Double> raw=new ArrayList<>();
        if(points!=null){
            for(int i=0;i<points.length();i++){
                JSONObject p=points.optJSONObject(i);
                if(p==null)continue;
                double v=p.has("speed_kmh")
                        ?p.optDouble("speed_kmh",Double.NaN)
                        :p.optDouble("speed",Double.NaN)*3.6d;
                if(Double.isFinite(v)&&v>=0&&v<100)raw.add(Math.max(0d,v));
            }
        }
        ArrayList<Double> smoothed=smooth(raw);
        if(smoothed.size()>320){
            ArrayList<Double> slim=new ArrayList<>();
            int last=-1;
            for(int i=0;i<320;i++){
                int j=Math.round(i*(smoothed.size()-1)/319f);
                if(j!=last){slim.add(smoothed.get(j));last=j;}
            }
            smoothed=slim;
        }
        values.addAll(smoothed);
        chartMax=10d;
        for(Double v:values)if(v!=null&&Double.isFinite(v))chartMax=Math.max(chartMax,v);
        invalidate();
    }

    private ArrayList<Double> smooth(ArrayList<Double> raw){
        int n=raw.size();
        if(n<3)return new ArrayList<>(raw);

        double[] stage=new double[n];
        for(int i=0;i<n;i++){
            int lo=Math.max(0,i-3),hi=Math.min(n-1,i+3);
            ArrayList<Double> positive=new ArrayList<>();
            for(int j=lo;j<=hi;j++)if(raw.get(j)>1.5)positive.add(raw.get(j));
            double med=positive.isEmpty()?0:median(positive);

            int zlo=Math.max(0,i-1),zhi=Math.min(n-1,i+1);
            boolean sustainedStop=(zhi-zlo+1)>=3;
            if(sustainedStop){
                for(int j=zlo;j<=zhi;j++){
                    if(raw.get(j)>1.5){sustainedStop=false;break;}
                }
            }

            double v=raw.get(i);
            if(v<=1.5&&positive.size()>=4&&!sustainedStop)stage[i]=med;
            else if(v>1.5&&positive.size()>=4&&med>0&&v>med*1.55+6)stage[i]=med;
            else stage[i]=v;
        }

        ArrayList<Double> out=new ArrayList<>(n);
        double ema=stage[0],alpha=.28;
        for(int i=0;i<n;i++){
            int zlo=Math.max(0,i-1),zhi=Math.min(n-1,i+1);
            boolean sustainedStop=(zhi-zlo+1)>=3;
            if(sustainedStop){
                for(int j=zlo;j<=zhi;j++){
                    if(raw.get(j)>1.5){sustainedStop=false;break;}
                }
            }
            if(sustainedStop){
                ema=0;
                out.add(0d);
                continue;
            }
            if(i==0)ema=stage[i];
            else ema=alpha*stage[i]+(1-alpha)*ema;
            out.add(Math.max(0d,ema));
        }
        return out;
    }

    private double median(ArrayList<Double> input){
        ArrayList<Double> x=new ArrayList<>(input);
        Collections.sort(x);
        int m=x.size()/2;
        return x.size()%2==1?x.get(m):(x.get(m-1)+x.get(m))/2d;
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        float pad=dp(18);
        if(w<=pad*2||h<=pad*2)return;

        if(values.size()<2){
            c.drawText("برای نمودار سرعت هنوز داده کافی ثبت نشده است.",pad,h/2f,label);
            return;
        }

        Path stroke=new Path();
        Path area=new Path();
        float lastX=pad;

        for(int i=0;i<values.size();i++){
            double v=Math.max(0,values.get(i));
            float x=pad+i*(w-2*pad)/Math.max(1f,values.size()-1f);
            float y=h-pad-(float)(v/chartMax)*(h-2*pad);
            if(i==0){
                stroke.moveTo(x,y);
                area.moveTo(x,h-pad);
                area.lineTo(x,y);
            }else{
                stroke.lineTo(x,y);
                area.lineTo(x,y);
            }
            lastX=x;
        }

        area.lineTo(lastX,h-pad);
        area.lineTo(pad,h-pad);
        area.close();
        c.drawPath(area,fill);
        c.drawPath(stroke,line);

        c.drawText(String.format(Locale.US,"حداکثر نمایشی %.1f km/h",chartMax),dp(20),dp(22),label);
    }

    private float dp(float v){
        return v*getResources().getDisplayMetrics().density;
    }
}
