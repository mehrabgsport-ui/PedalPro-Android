package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class NativeHeatmapView extends View {
    private final List<List<P>> routes=new ArrayList<>();
    private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);

    public NativeHeatmapView(Context context){
        super(context);
        setBackgroundColor(Color.rgb(6,19,31));
        line.setColor(Color.argb(105,44,220,225));
        line.setStrokeWidth(dp(2.3f));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        grid.setColor(Color.rgb(17,47,64));
        grid.setStrokeWidth(dp(1));
        text.setColor(Color.rgb(160,188,201));
        text.setTextSize(dp(11));
    }

    public void setRoutes(JSONArray rows){
        routes.clear();
        if(rows!=null){
            for(int i=0;i<rows.length();i++){
                JSONObject r=rows.optJSONObject(i);if(r==null)continue;
                JSONArray pts=r.optJSONArray("points");if(pts==null)continue;
                List<P> path=new ArrayList<>();
                for(int j=0;j<pts.length();j++){
                    JSONArray a=pts.optJSONArray(j);
                    if(a!=null&&a.length()>=2){
                        double lat=a.optDouble(0,Double.NaN),lng=a.optDouble(1,Double.NaN);
                        if(Double.isFinite(lat)&&Double.isFinite(lng))path.add(new P(lat,lng));
                    }else{
                        JSONObject p=pts.optJSONObject(j);
                        if(p!=null){
                            double lat=p.optDouble("lat",Double.NaN),lng=p.optDouble("lng",Double.NaN);
                            if(Double.isFinite(lat)&&Double.isFinite(lng))path.add(new P(lat,lng));
                        }
                    }
                }
                if(path.size()>1)routes.add(path);
            }
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();if(w<2||h<2)return;
        for(int i=1;i<6;i++){float x=w*i/6f,y=h*i/6f;c.drawLine(x,0,x,h,grid);c.drawLine(0,y,w,y,grid);}
        B b=bounds();if(b==null){c.drawText("داده Heatmap موجود نیست",dp(16),h/2,text);return;}
        float pad=dp(18);
        for(List<P> route:routes){
            Path p=new Path();boolean first=true;
            for(P x:route){
                float sx=pad+(float)((x.lng-b.minLng)/(b.maxLng-b.minLng))*Math.max(1,w-pad*2);
                float sy=h-pad-(float)((x.lat-b.minLat)/(b.maxLat-b.minLat))*Math.max(1,h-pad*2);
                if(first){p.moveTo(sx,sy);first=false;}else p.lineTo(sx,sy);
            }
            c.drawPath(p,line);
        }
        c.drawText(routes.size()+" مسیر",dp(10),h-dp(10),text);
    }

    private B bounds(){
        if(routes.isEmpty())return null;
        double minLat=Double.POSITIVE_INFINITY,maxLat=Double.NEGATIVE_INFINITY,minLng=Double.POSITIVE_INFINITY,maxLng=Double.NEGATIVE_INFINITY;
        for(List<P> r:routes)for(P p:r){minLat=Math.min(minLat,p.lat);maxLat=Math.max(maxLat,p.lat);minLng=Math.min(minLng,p.lng);maxLng=Math.max(maxLng,p.lng);}
        if(!Double.isFinite(minLat))return null;
        if(maxLat-minLat<.001){maxLat+=.0005;minLat-=.0005;}
        if(maxLng-minLng<.001){maxLng+=.0005;minLng-=.0005;}
        return new B(minLat,maxLat,minLng,maxLng);
    }

    private float dp(float x){return x*getResources().getDisplayMetrics().density;}
    private static final class P{final double lat,lng;P(double a,double b){lat=a;lng=b;}}
    private static final class B{final double minLat,maxLat,minLng,maxLng;B(double a,double b,double c,double d){minLat=a;maxLat=b;minLng=c;maxLng=d;}}
}
