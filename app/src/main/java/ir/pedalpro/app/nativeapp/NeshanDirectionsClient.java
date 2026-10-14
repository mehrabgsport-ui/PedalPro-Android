package ir.pedalpro.app.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Visual guidance only. The Neshan v2/direction result describes a road route;
 * no bicycle-specific road eligibility, travel time, or safety is claimed.
 * Never sends or changes TrackingService GPS samples.
 */
final class NeshanDirectionsClient {
    private static final String ENDPOINT="https://api.neshan.org/v2/direction";

    static final class Route {
        final JSONArray points;
        final double distanceMeters;
        final long motorVehicleEtaSeconds;
        final String nextInstruction;
        Route(JSONArray points,double distanceMeters,long eta,String instruction) {
            this.points=points;this.distanceMeters=distanceMeters;
            this.motorVehicleEtaSeconds=eta;this.nextInstruction=instruction;
        }
    }

    private NeshanDirectionsClient() {}

    static Route fetch(String key,double fromLat,double fromLng,double toLat,double toLng) throws Exception {
        if(key==null||key.length()<12)throw new IllegalArgumentException("کلید سرویس نشان تنظیم نشده است.");
        if(!valid(fromLat,fromLng)||!valid(toLat,toLng))
            throw new IllegalArgumentException("موقعیت مبدا یا مقصد معتبر نیست.");
        String url=ENDPOINT+"?origin="+point(fromLat,fromLng)+
                "&destination="+point(toLat,toLng)+
                "&alternative=false&avoidTrafficZone=false&avoidOddEvenZone=false";
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6500);
        conn.setReadTimeout(9000);
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("Api-Key",key);
        conn.setRequestProperty("User-Agent","PedalPro-Android/2.0");
        try {
            int status=conn.getResponseCode();
            if(status==401||status==403)
                throw new IllegalStateException("دسترسی کلید سرویس نشان رد شد؛ نوع کلید و محدودیت IP را بررسی کنید.");
            if(status==429)
                throw new IllegalStateException("سقف درخواست‌های سرویس نشان پر شده است.");
            if(status<200||status>=300)
                throw new IllegalStateException("مسیریابی نشان در دسترس نیست (HTTP "+status+").");
            String json=readBody(conn.getInputStream(),1_500_000);
            JSONObject root=new JSONObject(json);
            JSONArray routes=root.optJSONArray("routes");
            JSONObject route=routes==null?null:routes.optJSONObject(0);
            if(route==null)throw new IllegalStateException("نشان مسیر خیابانی بین این نقاط پیدا نکرد.");
            JSONObject overview=route.optJSONObject("overview_polyline");
            String encoded=overview==null?"":overview.optString("points","");
            JSONArray geometry=decodePolyline(encoded);
            if(geometry.length()<2)throw new IllegalStateException("هندسه مسیر پاسخ نشان معتبر نیست.");
            JSONArray legs=route.optJSONArray("legs");
            JSONObject first=legs==null?null:legs.optJSONObject(0);
            JSONObject metres=first==null?null:first.optJSONObject("distance");
            JSONObject eta=first==null?null:first.optJSONObject("duration");
            JSONArray steps=first==null?null:first.optJSONArray("steps");
            JSONObject next=steps==null?null:steps.optJSONObject(0);
            String instruction=next==null?"":next.optString("instruction","");
            return new Route(geometry,metres==null?-1:metres.optDouble("value",-1),
                    eta==null?-1:eta.optLong("value",-1),instruction);
        } finally {conn.disconnect();}
    }

    static JSONArray decodePolyline(String encoded) {
        JSONArray out=new JSONArray();
        if(encoded==null||encoded.length()>200000)return out;
        long lat=0,lng=0;
        int index=0;
        while(index<encoded.length()&&out.length()<12000) {
            long[] first=decodeComponent(encoded,index);
            if(first==null)break;
            index=(int)first[1];
            long[] second=decodeComponent(encoded,index);
            if(second==null)break;
            index=(int)second[1];
            lat+=first[0];lng+=second[0];
            double a=lat/1e5,b=lng/1e5;
            if(!valid(a,b))return new JSONArray();
            JSONObject point=new JSONObject();
            try{point.put("lat",a);point.put("lng",b);}catch(Exception ignored){}
            out.put(point);
        }
        return out;
    }

    private static long[] decodeComponent(String s,int start) {
        long result=0;int shift=0,i=start;
        while(i<s.length()&&shift<40) {
            int next=s.charAt(i++)-63;
            if(next<0||next>63)return null;
            result|=(long)(next&0x1f)<<shift;
            if(next<0x20) {
                long delta=(result&1L)!=0?~(result>>1):(result>>1);
                return new long[]{delta,i};
            }
            shift+=5;
        }
        return null;
    }

    private static boolean valid(double lat,double lng) {
        return Double.isFinite(lat)&&Double.isFinite(lng)&&Math.abs(lat)<=85&&Math.abs(lng)<=180;
    }

    private static String point(double lat,double lng) {
        return String.format(Locale.US,"%.6f,%.6f",lat,lng);
    }

    private static String readBody(InputStream stream,int limit) throws Exception {
        StringBuilder out=new StringBuilder();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            char[] buf=new char[4096];int read;
            while((read=reader.read(buf))!=-1) {
                if(out.length()+read>limit)throw new IllegalStateException("پاسخ سرویس نشان بیش‌ازحد بزرگ است.");
                out.append(buf,0,read);
            }
        }
        return out.toString();
    }
}
