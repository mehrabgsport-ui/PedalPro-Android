package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WeatherClient {
    static final class Day {
        final String date; final int code; final int minC,maxC,rainPct,windKmh,gustKmh,direction;
        Day(String date,int code,int minC,int maxC,int rainPct,int windKmh,int gustKmh,int direction){
            this.date=date;this.code=code;this.minC=minC;this.maxC=maxC;this.rainPct=rainPct;this.windKmh=windKmh;this.gustKmh=gustKmh;this.direction=direction;
        }
    }
    private static final String PREFS="pedalpro_weather";
    private static final long TTL=20*60*1000L;

    List<Day> forecast(Context context,double lat,double lng,boolean force)throws Exception{
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        String cached=p.getString("json","");
        if(!force&&!cached.isBlank()&&now-p.getLong("at",0)<TTL&&Math.abs(lat-p.getFloat("lat",0))<.08&&Math.abs(lng-p.getFloat("lng",0))<.08){
            try{return parse(new JSONObject(cached));}catch(Exception ignored){}
        }
        String q=String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&timezone=auto&forecast_days=7&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant",
                lat,lng);
        HttpURLConnection c=(HttpURLConnection)new URL(q).openConnection();
        c.setConnectTimeout(6500);c.setReadTimeout(8000);c.setRequestMethod("GET");c.setRequestProperty("Accept","application/json");
        int status=c.getResponseCode();
        if(status<200||status>=300)throw new IllegalStateException("هواشناسی HTTP "+status);
        StringBuilder raw=new StringBuilder();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)raw.append(line);}
        finally{c.disconnect();}
        JSONObject root=new JSONObject(raw.toString());
        p.edit().putString("json",root.toString()).putLong("at",now).putFloat("lat",(float)lat).putFloat("lng",(float)lng).apply();
        return parse(root);
    }

    private List<Day> parse(JSONObject root){
        JSONObject d=root.optJSONObject("daily");
        if(d==null)return java.util.Collections.emptyList();
        JSONArray time=d.optJSONArray("time"),code=d.optJSONArray("weather_code"),max=d.optJSONArray("temperature_2m_max"),min=d.optJSONArray("temperature_2m_min"),
                rain=d.optJSONArray("precipitation_probability_max"),wind=d.optJSONArray("wind_speed_10m_max"),gust=d.optJSONArray("wind_gusts_10m_max"),dir=d.optJSONArray("wind_direction_10m_dominant");
        List<Day> out=new ArrayList<>();
        int n=Math.min(7,time==null?0:time.length());
        for(int i=0;i<n;i++)out.add(new Day(time.optString(i,""),code==null?0:code.optInt(i,0),
                (int)Math.round(min==null?0:min.optDouble(i,0)),(int)Math.round(max==null?0:max.optDouble(i,0)),
                rain==null?0:rain.optInt(i,0),(int)Math.round(wind==null?0:wind.optDouble(i,0)),
                (int)Math.round(gust==null?0:gust.optDouble(i,0)),dir==null?0:dir.optInt(i,0)));
        return out;
    }

    static PedalIconView.Icon icon(int code){
        if(code==0||code==1)return PedalIconView.Icon.SUN;
        if(code==2||code==3||code==45||code==48)return PedalIconView.Icon.CLOUD;
        if((code>=51&&code<=67)||(code>=80&&code<=82)||code>=95)return PedalIconView.Icon.RAIN;
        return PedalIconView.Icon.CLOUD;
    }
    static String condition(int code){
        if(code==0)return "آفتابی"; if(code<=2)return "نیمه‌ابری"; if(code==3)return "ابری"; if(code==45||code==48)return "مه";
        if((code>=51&&code<=67)||(code>=80&&code<=82))return "بارانی"; if(code>=71&&code<=77)return "برفی"; if(code>=95)return "رعدوبرق"; return "ابری";
    }
}
