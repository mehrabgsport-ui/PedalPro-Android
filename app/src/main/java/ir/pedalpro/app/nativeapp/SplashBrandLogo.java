package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import ir.pedalpro.app.R;

/**
 * Retains the original PedalPro artwork, using its existing 192px brand source
 * rather than the old 96px splash thumbnail or an AI-generated replacement.
 * The square icon's solid black matte is converted to a transparent alpha.
 */
public final class SplashBrandLogo {
    private SplashBrandLogo() {}

    public static Drawable load(Context context) {
        Bitmap source=BitmapFactory.decodeResource(context.getResources(),R.drawable.pedalpro_app_icon);
        if(source==null)return context.getDrawable(R.drawable.pedalpro_logo_transparent);

        final int width=source.getWidth(),height=source.getHeight();
        int[] pixels=new int[width*height];
        source.getPixels(pixels,0,width,0,0,width,height);
        for(int i=0;i<pixels.length;i++){
            int pixel=pixels[i],r=Color.red(pixel),g=Color.green(pixel),b=Color.blue(pixel);
            int peak=Math.max(r,Math.max(g,b));
            if(peak<=27){
                pixels[i]=Color.TRANSPARENT;
            }else if(peak<75){
                int a=Math.max(1,Math.min(255,(peak-27)*255/48));
                // Undo the black matte's premultiplication for smooth alpha edges.
                pixels[i]=Color.argb(a,Math.min(255,r*255/a),
                        Math.min(255,g*255/a),Math.min(255,b*255/a));
            }else{
                pixels[i]=Color.argb(255,r,g,b);
            }
        }
        Bitmap transparent=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        transparent.setPixels(pixels,0,width,0,0,width,height);
        BitmapDrawable drawable=new BitmapDrawable(context.getResources(),transparent);
        drawable.setFilterBitmap(true);
        drawable.setDither(true);
        return drawable;
    }
}
