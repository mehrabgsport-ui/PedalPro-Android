package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DisplayFontLoader {
    private static final String ANTON="https://raw.githubusercontent.com/google/fonts/main/ofl/anton/Anton-Regular.ttf";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile Typeface anton;
    private DisplayFontLoader(){}

    static void applyAnton(Context c, TextView... views){
        if(anton!=null){for(TextView v:views)if(v!=null)v.setTypeface(anton);return;}
        for(TextView v:views)if(v!=null)v.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        IO.execute(()->{
            try{
                File f=new File(c.getFilesDir(),"anton-regular.ttf");
                if(!f.exists())download(f);
                anton=Typeface.createFromFile(f);
                MAIN.post(()->{for(TextView v:views)if(v!=null)v.setTypeface(anton);});
            }catch(Throwable ignored){}
        });
    }
    private static void download(File f)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(ANTON).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(12000);
        if(c.getResponseCode()<200||c.getResponseCode()>=300)throw new IllegalStateException("font HTTP");
        File t=new File(f.getAbsolutePath()+".tmp");
        try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(t)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}
        finally{c.disconnect();}
        if(!t.renameTo(f))throw new IllegalStateException("font write");
    }
}
