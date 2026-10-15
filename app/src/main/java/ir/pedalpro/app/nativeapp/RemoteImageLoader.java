package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
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
    private static final int MAX_IMAGE_BYTES=6*1024*1024;
    private RemoteImageLoader(){}

    static void load(Context c,ImageView view,String rawUrl,int fallbackRes){
        if(rawUrl==null||rawUrl.isBlank()){view.setImageResource(fallbackRes);return;}
        if(rawUrl.startsWith("data:image/")){
            try{
                int comma=rawUrl.indexOf(',');
                if(comma>0&&rawUrl.length()-comma-1<MAX_IMAGE_BYTES*4/3){
                    byte[] bytes=android.util.Base64.decode(rawUrl.substring(comma+1),android.util.Base64.DEFAULT);
                    Bitmap bmp=decodeBounded(bytes);
                    if(bmp!=null){view.setImageBitmap(bmp);return;}
                }
            }catch(Throwable ignored){}
            view.setImageResource(fallbackRes);return;
        }
        String absolute=absolute(rawUrl);
        final URL parsed;
        try{
            parsed=new URL(absolute);
            if(!"https".equalsIgnoreCase(parsed.getProtocol()))throw new IllegalArgumentException();
        }catch(Exception invalid){view.setImageResource(fallbackRes);return;}
        view.setTag(absolute);
        Bitmap cached=CACHE.get(absolute);
        if(cached!=null){view.setImageBitmap(cached);return;}
        view.setImageResource(fallbackRes);
        // Never attach PHP authentication cookies to arbitrary user-controlled
        // remote image URLs. Redirects are disabled to prevent cookie forwarding.
        boolean sameOrigin="pedalpro.ir".equalsIgnoreCase(parsed.getHost())
                && (parsed.getPort()==-1||parsed.getPort()==443);
        String cookie=sameOrigin?new SessionStore(c).cookie():"";
        IO.execute(()->{
            HttpURLConnection conn=null;
            try{
                conn=(HttpURLConnection)parsed.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(7000);conn.setReadTimeout(12000);
                if(!cookie.isBlank())conn.setRequestProperty("Cookie",cookie);
                conn.setRequestProperty("Accept","image/*");
                int status=conn.getResponseCode();
                if(status<200||status>=300)return;
                String mime=conn.getContentType();
                if(mime==null||!mime.toLowerCase(java.util.Locale.ROOT).startsWith("image/"))return;
                if(conn.getContentLengthLong()>MAX_IMAGE_BYTES)return;
                byte[] bytes;
                try(InputStream in=conn.getInputStream();
                    ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int total=0,n;
                    while((n=in.read(buffer))>0){
                        total+=n;if(total>MAX_IMAGE_BYTES)return;
                        out.write(buffer,0,n);
                    }
                    bytes=out.toByteArray();
                }
                Bitmap bmp=decodeBounded(bytes);
                if(bmp!=null){CACHE.put(absolute,bmp);MAIN.post(()->{
                    if(absolute.equals(view.getTag()))
                        view.setImageBitmap(bmp);
                });}
            }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}
        });
    }
    private static Bitmap decodeBounded(byte[] bytes){
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
        if(opts.outWidth<=0||opts.outHeight<=0)return null;
        opts.inSampleSize=1;
        while((long)opts.outWidth/opts.inSampleSize*
                (long)(opts.outHeight/opts.inSampleSize)>4_000_000L)
            opts.inSampleSize*=2;
        opts.inJustDecodeBounds=false;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
    }
    static String absolute(String value){
        if(value.startsWith("http://")||value.startsWith("https://"))return value;
        if(value.startsWith("/"))return "https://pedalpro.ir"+value;
        return "https://pedalpro.ir/"+value;
    }
}
