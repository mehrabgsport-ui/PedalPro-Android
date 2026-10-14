package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RemoteImageLoader {
    private static final ExecutorService IO=Executors.newFixedThreadPool(3);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(24*1024){
        @Override protected int sizeOf(String k,Bitmap v){return v.getByteCount()/1024;}
    };
    private RemoteImageLoader(){}

    static void load(Context c, ImageView view, String rawUrl, int fallbackRes){
        if(rawUrl==null||rawUrl.isBlank()){view.setImageResource(fallbackRes);return;}
        if(rawUrl.startsWith("data:image/")){
            try{
                int comma=rawUrl.indexOf(',');
                if(comma>0){
                    byte[] bytes=android.util.Base64.decode(rawUrl.substring(comma+1),android.util.Base64.DEFAULT);
                    Bitmap bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                    if(bmp!=null){view.setImageBitmap(bmp);return;}
                }
            }catch(Throwable ignored){}
            view.setImageResource(fallbackRes);
            return;
        }
        String url=absolute(rawUrl);
        Bitmap cached=CACHE.get(url);
        if(cached!=null){view.setImageBitmap(cached);return;}
        view.setImageResource(fallbackRes);
        String cookie=new SessionStore(c).cookie();
        IO.execute(()->{
            HttpURLConnection conn=null;
            try{
                conn=(HttpURLConnection)new URL(url).openConnection();
                conn.setConnectTimeout(7000);conn.setReadTimeout(12000);
                if(cookie!=null&&!cookie.isBlank())conn.setRequestProperty("Cookie",cookie);
                conn.setRequestProperty("Accept","image/*");
                if(conn.getResponseCode()<200||conn.getResponseCode()>=300)return;
                try(InputStream in=conn.getInputStream()){
                    Bitmap bmp=BitmapFactory.decodeStream(in);
                    if(bmp!=null){CACHE.put(url,bmp);MAIN.post(()->view.setImageBitmap(bmp));}
                }
            }catch(Throwable ignored){}finally{if(conn!=null)conn.disconnect();}
        });
    }
    static String absolute(String value){
        if(value.startsWith("http://")||value.startsWith("https://"))return value;
        if(value.startsWith("/"))return "https://pedalpro.ir"+value;
        return "https://pedalpro.ir/"+value;
    }
}
