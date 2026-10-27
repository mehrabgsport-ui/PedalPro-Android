package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;

/** Lightweight native report charts. Pure rendering: no WebView, no GPS changes. */
public final class NativePerformanceCharts extends View {
    public enum Mode { GOAL, SPEED_ELEVATION, WEEKLY, HEATMAP, RADAR }
    private static final int AQUA=Color.rgb(12,214,239);
    private static final int GREEN=Color.rgb(76,230,122);
    private static final int MUTED=Color.rgb(149,174,195);
    private final Mode mode;
    private final JSONObject data;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public NativePerformanceCharts(Context context,Mode mode,JSONObject report){
        super(context);this.mode=mode;this.data=report==null?new JSONObject():report;
        density=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setContentDescription("نمودار "+mode.name());
    }
    private void stroke(int color,float width){
        paint.reset();paint.setAntiAlias(true);paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width*density);
        paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
    }
    private void fill(int color){paint.reset();paint.setAntiAlias(true);paint.setColor(color);paint.setStyle(Paint.Style.FILL);}
    private void label(Canvas c,String s,float x,float y,int color,float size){
        fill(color);paint.setTextSize(size*density);paint.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,paint);
    }
    private float clamp(double n,float min,float max){return (float)Math.max(min,Math.min(max,n));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();if(w<30||h<30)return;
        switch(mode){
            case GOAL:goal(canvas,w,h);break;
            case SPEED_ELEVATION:profile(canvas,w,h);break;
            case WEEKLY:weekly(canvas,w,h);break;
            case HEATMAP:heatmap(canvas,w,h);break;
            case RADAR:radar(canvas,w,h);break;
        }
    }
    private void goal(Canvas c,float w,float h){
        JSONObject obj=data.optJSONObject("weekly_goal");if(obj==null)obj=new JSONObject();
        float pct=clamp(obj.optDouble("percent",0),0,100);
        float radius=Math.min(w*.30f,h*.34f),cx=w*.5f,cy=h*.49f;
        stroke(Color.argb(100,70,120,145),13);c.drawCircle(cx,cy,radius,paint);
        stroke(AQUA,13);c.drawArc(cx-radius,cy-radius,cx+radius,cy+radius,-90,3.6f*pct,false,paint);
        label(c,Math.round(pct)+"%",cx,cy+7*density,Color.WHITE,25);
        double left=Math.max(0,obj.optDouble("target_km",0)-obj.optDouble("current_km",0));
        label(c,String.format(java.util.Locale.US,"%.1f km remaining",left),cx,cy+radius+24*density,MUTED,11);
    }
    private void profile(Canvas c,float w,float h){
        JSONObject profile=data.optJSONObject("profile");JSONArray pts=profile==null?null:profile.optJSONArray("points");
        if(pts==null||pts.length()<2){label(c,"No recorded speed/elevation profile",w/2,h/2,MUTED,12);return;}
        int n=pts.length();float top=25*density,bottom=h-32*density,left=25*density,right=w-16*density;
        if(right<=left||bottom<=top)return;
        double speedMax=1,altMin=Double.POSITIVE_INFINITY,altMax=Double.NEGATIVE_INFINITY;
        for(int i=0;i<n;i++){JSONObject p=pts.optJSONObject(i);if(p==null)continue;
            speedMax=Math.max(speedMax,p.optDouble("speed_kmh",0));
            double a=p.optDouble("altitude",Double.NaN);if(Double.isFinite(a)){altMin=Math.min(altMin,a);altMax=Math.max(altMax,a);}
        }
        if(!Double.isFinite(altMin)){altMin=0;altMax=1;}if(altMax-altMin<1)altMax=altMin+1;
        for(int k=0;k<4;k++){float y=top+(bottom-top)*k/3f;stroke(Color.argb(52,140,181,198),1);c.drawLine(left,y,right,y,paint);}
        Path sp=new Path(),ap=new Path();boolean first=true;
        for(int i=0;i<n;i++){JSONObject p=pts.optJSONObject(i);if(p==null)continue;
            float x=left+(right-left)*i/(n-1f);
            float sy=bottom-clamp(p.optDouble("speed_kmh",0)/speedMax,0,1)*(bottom-top);
            float ay=bottom-clamp((p.optDouble("altitude",altMin)-altMin)/(altMax-altMin),0,1)*(bottom-top);
            if(first){sp.moveTo(x,sy);ap.moveTo(x,ay);first=false;}else{sp.lineTo(x,sy);ap.lineTo(x,ay);}
        }
        stroke(AQUA,2.8f);c.drawPath(sp,paint);stroke(GREEN,2.8f);c.drawPath(ap,paint);
        label(c,"Speed",left+34*density,15*density,AQUA,10);
        label(c,"Elevation",right-44*density,15*density,GREEN,10);
    }
    private void weekly(Canvas c,float w,float h){
        JSONArray arr=data.optJSONArray("week_days");if(arr==null||arr.length()==0){label(c,"No weekly activity",w/2,h/2,MUTED,12);return;}
        int n=Math.min(7,arr.length());float margin=21*density;float baseline=h-32*density;float top=25*density;
        float cell=(w-2*margin)/n,bar=cell*.54f;double max=1;
        for(int i=0;i<n;i++){JSONObject v=arr.optJSONObject(i);if(v!=null)max=Math.max(max,v.optDouble("km",0));}
        for(int i=0;i<n;i++){JSONObject v=arr.optJSONObject(i);if(v==null)continue;
            double km=v.optDouble("km",0);float bh=Math.max(km>0?5*density:0,(float)(km/max)*(baseline-top));
            float x=margin+(i+.5f)*cell,y=baseline-bh;
            fill(AQUA);paint.setShader(new LinearGradient(x,y,x,baseline,GREEN,AQUA,Shader.TileMode.CLAMP));
            c.drawRoundRect(x-bar/2,y,x+bar/2,baseline,8*density,8*density,paint);paint.setShader(null);
            label(c,v.optString("label",""),x,baseline+19*density,MUTED,11);
            if(km>0)label(c,String.format(java.util.Locale.US,"%.0f",km),x,Math.max(14*density,y-7*density),Color.WHITE,10);
        }
    }
    private void heatmap(Canvas c,float w,float h){
        JSONArray arr=data.optJSONArray("heatmap");
        if(arr==null||arr.length()==0){label(c,"No activity days",w/2,h/2,MUTED,12);return;}
        int n=Math.min(84,arr.length()),cols=12,rows=7;
        float gap=3*density,size=Math.min((w-32*density)/cols-gap,(h-26*density)/rows-gap);
        float used=cols*(size+gap)-gap,startX=(w-used)/2,startY=(h-rows*(size+gap)+gap)/2;
        double max=1;for(int i=0;i<n;i++){JSONObject d=arr.optJSONObject(i);if(d!=null)max=Math.max(max,d.optDouble("km",0));}
        for(int i=0;i<n;i++){JSONObject d=arr.optJSONObject(i);double km=d==null?0:d.optDouble("km",0);
            float weight=clamp(km/max,0,1);
            int color=km<=0?Color.rgb(20,50,70):Color.rgb((int)(40+95*weight),(int)(140+103*weight),(int)(108+30*weight));
            fill(color);float x=startX+(i/rows)*(size+gap),y=startY+(i%rows)*(size+gap);
            c.drawRoundRect(x,y,x+size,y+size,3*density,3*density,paint);
        }
    }
    private void radar(Canvas c,float w,float h){
        JSONObject scores=data.optJSONObject("scores");if(scores==null)scores=new JSONObject();
        float cx=w*.5f,cy=h*.50f,radius=Math.min(w*.34f,h*.35f);
        for(int ring=1;ring<=4;ring++){float r=radius*ring/4f;Path frame=new Path();
            for(int k=0;k<4;k++){double angle=k*Math.PI/2-Math.PI/2;float x=cx+(float)Math.cos(angle)*r,y=cy+(float)Math.sin(angle)*r;
                if(k==0)frame.moveTo(x,y);else frame.lineTo(x,y);
            }frame.close();stroke(Color.argb(75,150,184,197),1);c.drawPath(frame,paint);}
        String[] names={"speed","endurance","climbing","consistency"};Path actual=new Path();
        for(int k=0;k<4;k++){double angle=k*Math.PI/2-Math.PI/2;
            float a=radius*clamp(scores.optDouble(names[k],0)/10,0,1);
            float x=cx+(float)Math.cos(angle)*a,y=cy+(float)Math.sin(angle)*a;
            if(k==0)actual.moveTo(x,y);else actual.lineTo(x,y);
        }
        actual.close();fill(Color.argb(77,70,232,117));c.drawPath(actual,paint);
        stroke(GREEN,2.5f);c.drawPath(actual,paint);
        label(c,"Speed",cx,cy-radius-9*density,MUTED,11);
        label(c,"Endurance",cx+radius+23*density,cy+3*density,MUTED,10);
        label(c,"Climb",cx,cy+radius+17*density,MUTED,11);
        label(c,"Consistency",cx-radius-27*density,cy+3*density,MUTED,10);
    }
}
