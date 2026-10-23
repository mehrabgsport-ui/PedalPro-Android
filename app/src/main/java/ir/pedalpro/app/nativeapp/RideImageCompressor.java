package ir.pedalpro.app.nativeapp;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
/** Optimizes user photos before upload; preserves original files and normal EXIF rotation. */
final class RideImageCompressor {
    private RideImageCompressor(){}
    static File prepare(Context context,Uri source)throws Exception{
        BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(source)){
            if(in==null)throw new IllegalArgumentException("Unreadable image");
            BitmapFactory.decodeStream(in,null,dimensions);
        }
        if(dimensions.outWidth<=0||dimensions.outHeight<=0)throw new IllegalArgumentException("Unsupported image");
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
        while(Math.max(dimensions.outWidth/opts.inSampleSize,dimensions.outHeight/opts.inSampleSize)>1800)opts.inSampleSize*=2;
        Bitmap bitmap;
        try(InputStream in=context.getContentResolver().openInputStream(source)){
            if(in==null)throw new IllegalArgumentException("Unreadable image");
            bitmap=BitmapFactory.decodeStream(in,null,opts);
        }
        if(bitmap==null)throw new IllegalArgumentException("Image decode failed");
        try{
            int degrees=0;
            try(InputStream in=context.getContentResolver().openInputStream(source)){
                ExifInterface e=new ExifInterface(in);
                int o=e.getAttributeInt(ExifInterface.TAG_ORIENTATION,1);
                if(o==ExifInterface.ORIENTATION_ROTATE_90)degrees=90;
                if(o==ExifInterface.ORIENTATION_ROTATE_180)degrees=180;
                if(o==ExifInterface.ORIENTATION_ROTATE_270)degrees=270;
            }catch(Exception ignored){}
            if(degrees!=0){
                Matrix matrix=new Matrix();matrix.postRotate(degrees);
                Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}
            }
            byte[] bytes=null;
            for(int i=0;i<24;i++){
                int q=Math.max(32,88-(i%8)*8);
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,q,out))throw new IllegalStateException("Encoding failed");
                bytes=out.toByteArray();if(bytes.length<=500*1024)break;
                if(i%8==7){
                    Bitmap scaled=Bitmap.createScaledBitmap(bitmap,Math.max(96,(int)(bitmap.getWidth()*.65)),
                            Math.max(96,(int)(bitmap.getHeight()*.65)),true);
                    if(scaled!=bitmap){bitmap.recycle();bitmap=scaled;}
                }
            }
            if(bytes==null||bytes.length>=1024*1024)throw new IllegalStateException("Photo too large");
            File dir=new File(context.getCacheDir(),"uploads");
            if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cache unavailable");
            File file=File.createTempFile("photo_",".jpg",dir);
            try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
            return file;
        }finally{if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();}
    }
}
