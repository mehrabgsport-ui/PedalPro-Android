package ir.pedalpro.app.nativeapp;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** Presentation-only time interpretation for historical and active GPS profiles. */
public final class RideProfileTime {
    private RideProfileTime(){}
    public static double resolve(long timestamp,String recordedAt){
        // PHP may send epoch seconds; Android recorders normally send epoch millis.
        if(timestamp>=1_000_000_000_000L)return timestamp/1000.0;
        if(timestamp>0L)return timestamp;
        if(recordedAt==null||recordedAt.length()<19)return Double.NaN;
        try{
            SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);
            format.setLenient(false);
            format.setTimeZone(TimeZone.getTimeZone("Asia/Tehran"));
            String date=recordedAt.substring(0,19).replace('T',' ');
            return format.parse(date).getTime()/1000.0;
        }catch(Exception ignored){return Double.NaN;}
    }
    public static String formatElapsed(double seconds){
        if(!Double.isFinite(seconds)||seconds<0)return "—";
        long s=Math.round(seconds);
        long h=s/3600,m=(s%3600)/60,sec=s%60;
        return h>0?String.format(Locale.US,"%d:%02d:%02d",h,m,sec)
                :String.format(Locale.US,"%02d:%02d",m,sec);
    }
}
