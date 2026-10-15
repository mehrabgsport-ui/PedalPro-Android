package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Interactive ride profile. Scrubbing is presentation-only, never edits GPS. */
public final class NativeRideProfileView extends View {
    public enum Mode { ELEVATION, SPEED, TIME }
    public interface Listener { void onSample(int index,JSONObject point); }
    private JSONArray points;
    private final ArrayList<JSONObject> valid=new ArrayList<>();
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Mode mode=Mode.ELEVATION;
    private Listener listener;
    private int selected=-1;
    private double[] cumulativeKm=new double[0];
    public NativeRideProfileView(Context context){super(context);setBackgroundColor(Color.rgb(9,29,45));}
    public void setPoints(JSONArray data){
        points=data;valid.clear();
        if(data!=null)for(int i=0;i<data.length();i++){
            JSONObject p=data.optJSONObject(i);
            if(p==null)continue;
            if(Double.isFinite(p.optDouble("lat",Double.NaN))&&Double.isFinite(p.optDouble("lng",Double.NaN)))
                valid.add(p);
        }
        cumulativeKm=new double[valid.size()];
        float[] meters=new float[1];
        for(int i=1;i<valid.size();i++){
            JSONObject a=valid.get(i-1),b=valid.get(i);
            android.location.Location.distanceBetween(
                    a.optDouble("lat"),a.optDouble("lng"),
                    b.optDouble("lat"),b.optDouble("lng"),meters);
            cumulativeKm[i]=cumulativeKm[i-1]+Math.max(0,meters[0])/1000.0;
        }
        invalidate();
    }
    public double distanceAt(int i){
        return i>=0&&i<cumulativeKm.length?cumulativeKm[i]:0.0;
    }
    public void setMode(Mode next){mode=next;invalidate();}
    public void setListener(Listener callback){listener=callback;}
    public void clearSelection(){selected=-1;invalidate();}
    private double value(JSONObject p,int i){
        if(mode==Mode.ELEVATION)return p.optDouble("altitude",p.optDouble("alt",Double.NaN));
        if(mode==Mode.SPEED)return p.optDouble("speed_kmh",p.optDouble("speed",Double.NaN)*3.6);
        long ts=p.optLong("timestamp",0);
        if(ts>0)return ts/1000.0;
        String recorded=p.optString("recorded_at",p.optString("created_at",""));
        try{
            if(recorded.length()>=19){
                java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);
                fmt.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Tehran"));
                java.util.Date parsed=fmt.parse(recorded.substring(0,19));
                if(parsed!=null)return parsed.getTime()/1000.0;
            }
        }catch(Exception ignored){}
        return i;
    }
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(valid.size()<2){
            pen.setColor(Color.LTGRAY);pen.setTextSize(dp(12));
            canvas.drawText("برای نمودار داده کافی وجود ندارد.",dp(15),getHeight()/2f,pen);
            return;
        }
        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(int i=0;i<valid.size();i++){
            double v=value(valid.get(i),i);
            if(Double.isFinite(v)){lo=Math.min(lo,v);hi=Math.max(hi,v);}
        }
        if(!Double.isFinite(lo)){lo=0;hi=1;}
        if(hi-lo<1e-3)hi=lo+1;
        float left=dp(14),right=getWidth()-dp(14),top=dp(25),bottom=getHeight()-dp(25);
        pen.setStrokeWidth(dp(1));pen.setColor(Color.rgb(35,71,88));
        canvas.drawLine(left,bottom,right,bottom,pen);
        canvas.drawLine(left,(top+bottom)/2,right,(top+bottom)/2,pen);
        Path curve=new Path();boolean first=true;
        for(int i=0;i<valid.size();i++){
            double v=value(valid.get(i),i);if(!Double.isFinite(v))continue;
            float x=left+(right-left)*i/(valid.size()-1f);
            float y=bottom-(float)((v-lo)/(hi-lo))*(bottom-top);
            if(first){curve.moveTo(x,y);first=false;}else curve.lineTo(x,y);
        }
        pen.setColor(Color.rgb(45,204,239));pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(dp(2.6f));canvas.drawPath(curve,pen);pen.setStyle(Paint.Style.FILL);
        if(selected>=0&&selected<valid.size()){
            float x=left+(right-left)*selected/(valid.size()-1f);
            pen.setColor(Color.rgb(255,182,67));pen.setStrokeWidth(dp(1.5f));
            canvas.drawLine(x,top,x,bottom,pen);
            double v=value(valid.get(selected),selected);
            if(Double.isFinite(v)){
                float y=bottom-(float)((v-lo)/(hi-lo))*(bottom-top);
                canvas.drawCircle(x,y,dp(5),pen);
            }
        }
        pen.setColor(Color.rgb(175,197,208));pen.setTextSize(dp(11));
        String unit=mode==Mode.ELEVATION?"m":mode==Mode.SPEED?"km/h":"زمان";
        canvas.drawText(String.format(Locale.US,"%s %.1f – %.1f",unit,lo,hi),left,dp(16),pen);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()!=MotionEvent.ACTION_DOWN &&
                event.getActionMasked()!=MotionEvent.ACTION_MOVE &&
                event.getActionMasked()!=MotionEvent.ACTION_UP)return super.onTouchEvent(event);
        if(valid.isEmpty())return false;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
        float left=dp(14),span=Math.max(1f,getWidth()-dp(28));
        int index=Math.max(0,Math.min(valid.size()-1,Math.round((event.getX()-left)/span*(valid.size()-1))));
        selected=index;
        invalidate();
        if(listener!=null)listener.onSample(index,valid.get(index));
        if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public static String describe(JSONObject p,int index,JSONArray points){
        double altitude=p.optDouble("altitude",p.optDouble("alt",Double.NaN));
        double speed=p.optDouble("speed_kmh",p.optDouble("speed",Double.NaN)*3.6);
        String timestamp=p.optString("recorded_at",p.optString("created_at",""));
        if(timestamp.isBlank()&&p.has("timestamp")){
            timestamp=java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM)
                    .format(new java.util.Date(p.optLong("timestamp")));
        }
        return String.format(Locale.US,"نقطه %d · ارتفاع %s متر · سرعت %s km/h · زمان %s",
                index+1,Double.isFinite(altitude)?String.format(Locale.US,"%.1f",altitude):"—",
                Double.isFinite(speed)?String.format(Locale.US,"%.1f",speed):"—",
                timestamp.isBlank()?"—":timestamp);
    }
}
