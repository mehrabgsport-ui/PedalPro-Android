package ir.pedalpro.app.nativeapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import ir.pedalpro.app.R;

final class NativeRidePhotoController {
    static final int REQ_GALLERY=5101;
    static final int REQ_CAMERA=5102;

    interface Listener { void onUploaded(JSONObject refreshedBootstrap); }

    private final NativeRideDetailActivity activity;
    private final PedalProApiClient api;
    private final ExecutorService io;
    private final Listener listener;
    private int rideId;
    private JSONObject boot;
    private Uri pendingCameraUri;
    private EditText caption;

    NativeRidePhotoController(
            NativeRideDetailActivity activity,
            PedalProApiClient api,
            ExecutorService io,
            Listener listener) {
        this.activity=activity;
        this.api=api;
        this.io=io;
        this.listener=listener;
    }

    void render(LinearLayout target,JSONObject bootstrap,int targetRideId,boolean canUpload){
        rideId=targetRideId;
        boot=bootstrap;
        JSONArray photos=ridePhotos();

        LinearLayout gallery=new LinearLayout(activity);
        gallery.setOrientation(LinearLayout.VERTICAL);
        if(photos.length()==0){
            gallery.addView(empty("عکسی ثبت نشده است."));
        }else{
            for(int i=0;i<photos.length();i++){
                JSONObject p=photos.optJSONObject(i);
                if(p==null)continue;
                LinearLayout card=activity.card();
                card.setPadding(activity.dp(8),activity.dp(8),activity.dp(8),activity.dp(8));

                ImageView image=new ImageView(activity);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setClipToOutline(true);
                image.setBackground(activity.round(
                        android.graphics.Color.TRANSPARENT,14,
                        android.graphics.Color.argb(35,255,255,255)));
                RemoteImageLoader.load(activity,image,p.optString("file_name",""),R.drawable.program_reference);
                card.addView(image,new LinearLayout.LayoutParams(-1,activity.dp(210)));

                String text=p.optString("caption","");
                if(!text.isBlank()){
                    TextView c=activity.text(text,11,false,NativeScreenBase.MUTED);
                    c.setPadding(0,activity.dp(7),0,0);
                    card.addView(c);
                }
                gallery.addView(card,activity.cardLp());
            }
        }
        target.addView(gallery,activity.cardLp());

        if(!canUpload)return;

        caption=activity.input("توضیح عکس");
        caption.setSingleLine(true);
        target.addView(caption,activity.cardLp());

        LinearLayout actions=new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button galleryButton=activity.normal("🖼 انتخاب از گوشی");
        galleryButton.setOnClickListener(v->pickPhoto());
        Button cameraButton=activity.primary("📷 باز کردن دوربین");
        cameraButton.setOnClickListener(v->capturePhoto());

        actions.addView(galleryButton,new LinearLayout.LayoutParams(0,activity.dp(50),1f));
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,activity.dp(50),1f);
        clp.setMargins(activity.dp(8),0,0,0);
        actions.addView(cameraButton,clp);
        target.addView(actions,activity.cardLp());

        JSONObject config=bootstrap==null?null:bootstrap.optJSONObject("ride_config");
        int limit=config==null?20:config.optInt("photo_limit",20);
        int used=myPhotoCount(photos);
        target.addView(activity.text(
                used+" از "+limit+" عکس استفاده شده. فایل اصلی بدون کاهش عمدی کیفیت در سرور ذخیره می‌شود.",
                11,false,NativeScreenBase.MUTED),activity.cardLp());
    }

    boolean onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=REQ_GALLERY&&requestCode!=REQ_CAMERA)return false;
        if(resultCode!=Activity.RESULT_OK)return true;
        Uri uri=requestCode==REQ_CAMERA?pendingCameraUri:(data==null?null:data.getData());
        if(uri!=null)upload(uri);
        return true;
    }

    private void pickPhoto(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        activity.startActivityForResult(i,REQ_GALLERY);
    }

    private void capturePhoto(){
        try{
            File dir=new File(activity.getCacheDir(),"camera");
            if(!dir.exists())dir.mkdirs();
            File file=new File(dir,"ride_"+rideId+"_"+System.currentTimeMillis()+".jpg");
            pendingCameraUri=FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName()+".fileprovider",
                    file);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT,pendingCameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(i,REQ_CAMERA);
        }catch(Exception e){
            activity.toast(e.getMessage());
        }
    }

    private void upload(Uri uri){
        String text=caption==null?"":caption.getText().toString();
        activity.toast("در حال ذخیره عکس…");
        io.execute(()->{
            try{
                Map<String,String> fields=new HashMap<>();
                fields.put("ride_id",String.valueOf(rideId));
                fields.put("caption",text);
                String mime=activity.getContentResolver().getType(uri);
                api.postMultipart(
                        "upload_ride_photo",
                        fields,
                        uri,
                        "photo",
                        "ride-photo.jpg",
                        mime);
                JSONObject refreshed=api.bootstrap();
                activity.runOnUiThread(()->{
                    activity.toast("عکس ذخیره شد");
                    if(listener!=null)listener.onUploaded(refreshed);
                });
            }catch(Exception e){
                activity.runOnUiThread(()->activity.toast(e.getMessage()));
            }
        });
    }

    private JSONArray ridePhotos(){
        JSONArray out=new JSONArray();
        JSONArray rows=boot==null?null:boot.optJSONArray("ride_photos");
        if(rows==null)return out;
        for(int i=0;i<rows.length();i++){
            JSONObject p=rows.optJSONObject(i);
            if(p!=null&&p.optInt("ride_id",0)==rideId)out.put(p);
        }
        return out;
    }

    private int myPhotoCount(JSONArray photos){
        JSONObject user=boot==null?null:boot.optJSONObject("user");
        int uid=user==null?0:user.optInt("id",0),count=0;
        for(int i=0;i<photos.length();i++){
            JSONObject p=photos.optJSONObject(i);
            if(p!=null&&p.optInt("uploaded_by",0)==uid)count++;
        }
        return count;
    }

    private TextView empty(String value){
        TextView t=activity.text(value,12,false,NativeScreenBase.MUTED);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(activity.dp(12),activity.dp(20),activity.dp(12),activity.dp(20));
        t.setBackground(activity.round(
                android.graphics.Color.argb(7,255,255,255),
                14,
                android.graphics.Color.argb(18,255,255,255)));
        return t;
    }
}
