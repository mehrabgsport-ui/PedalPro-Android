package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runtime loader for the same Vazirmatn family used by the PedalPro website.
 * Fonts live only in app-private cache and are not bundled into the APK.
 */
final class VazirmatnLoader {
    private static final String REGULAR="https://cdn.jsdelivr.net/gh/rastikerdar/vazirmatn@v33.003/fonts/ttf/Vazirmatn-Regular.ttf";
    private static final String BOLD="https://cdn.jsdelivr.net/gh/rastikerdar/vazirmatn@v33.003/fonts/ttf/Vazirmatn-Bold.ttf";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile Typeface regular,bold;
    private static volatile boolean loading;

    private VazirmatnLoader(){}

    static void apply(Context c, View root){
        if(root==null)return;
        if(regular!=null){ applyNow(root); return; }
        applyFallback(root);
        if(loading)return;
        loading=true;
        IO.execute(()->{
            try{
                File dir=new File(c.getFilesDir(),"fonts"); if(!dir.exists())dir.mkdirs();
                File rf=new File(dir,"vazirmatn-regular.ttf");
                File bf=new File(dir,"vazirmatn-bold.ttf");
                if(!rf.exists())download(REGULAR,rf);
                if(!bf.exists())download(BOLD,bf);
                regular=Typeface.createFromFile(rf);
                bold=Typeface.createFromFile(bf);
            }catch(Throwable ignored){}
            loading=false;
            MAIN.post(()->applyNow(root));
        });
    }

    private static void applyNow(View v){
        if(v instanceof TextView){
            TextView t=(TextView)v;
            boolean isBold=t.getTypeface()!=null && t.getTypeface().isBold();
            Typeface use=isBold?(bold!=null?bold:Typeface.DEFAULT_BOLD):(regular!=null?regular:Typeface.DEFAULT);
            t.setTypeface(use);
        }
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++)applyNow(g.getChildAt(i));
        }
    }

    private static void applyFallback(View v){
        if(v instanceof TextView){
            TextView t=(TextView)v;
            boolean isBold=t.getTypeface()!=null&&t.getTypeface().isBold();
            t.setTypeface(Typeface.create("sans-serif",isBold?Typeface.BOLD:Typeface.NORMAL));
        }
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++)applyFallback(g.getChildAt(i));
        }
    }

    private static void download(String url,File file)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(7000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","PedalPro/2.0");
        if(c.getResponseCode()<200||c.getResponseCode()>=300)throw new IllegalStateException("font HTTP "+c.getResponseCode());
        File tmp=new File(file.getAbsolutePath()+".tmp");
        try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);
        }finally{c.disconnect();}
        if(!tmp.renameTo(file))throw new IllegalStateException("font cache write failed");
    }
}
