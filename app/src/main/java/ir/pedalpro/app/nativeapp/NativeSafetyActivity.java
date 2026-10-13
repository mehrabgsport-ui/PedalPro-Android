package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

public final class NativeSafetyActivity extends NativeScreenBase {
    private static final int REQ_LOCATION=4201;

    @Override protected String screenTitle(){return "ایمنی و حریم خصوصی";}

    @Override protected void onScreenReady(){load();}

    private void load(){
        setSubtitle("در حال دریافت تنظیمات ایمنی…",MUTED);
        io.execute(()->{
            try{
                JSONObject contacts=api.get("pp_safety_contacts");
                JSONObject zones=api.get("pp_privacy_zones");
                main.post(()->render(contacts.optJSONArray("items"),zones.optJSONArray("items")));
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا",RED);addCard("خطا",e.getMessage());});
            }
        });
    }

    private void render(JSONArray contacts,JSONArray zones){
        content.removeAllViews();
        setSubtitle("SOS · مخاطب اضطراری · Privacy Zone",GREEN);

        LinearLayout sos=card();
        sos.addView(text("SOS",18,true,RED));
        sos.addView(text("یک لینک ۲۴ ساعته برای نمایش موقعیت زنده شما ساخته می‌شود.",12,false,MUTED));
        Button start=primary("فعال‌کردن SOS و اشتراک لینک");
        start.setOnClickListener(v->startSos(start));
        Button stop=normal("پایان SOS فعال");
        stop.setOnClickListener(v->stopSos(stop));
        sos.addView(start);sos.addView(stop);
        content.addView(sos,cardLp());

        LinearLayout add=card();
        add.addView(text("افزودن مخاطب اضطراری",16,true,TEXT));
        EditText name=input("نام");
        EditText phone=input("شماره تماس");
        EditText relation=input("نسبت / توضیح");
        CheckBox primary=new CheckBox(this);primary.setText("مخاطب اصلی");primary.setTextColor(TEXT);
        Button save=primary("ذخیره مخاطب");
        save.setOnClickListener(v->saveContact(name,phone,relation,primary,save));
        add.addView(name);add.addView(phone);add.addView(relation);add.addView(primary);add.addView(save);
        content.addView(add,cardLp());

        if(contacts!=null&&contacts.length()>0){
            for(int i=0;i<contacts.length();i++){
                JSONObject c=contacts.optJSONObject(i);if(c==null)continue;
                LinearLayout box=card();
                box.addView(text((c.optInt("is_primary",0)==1?"★ ":"")+c.optString("name","مخاطب"),15,true,TEXT));
                box.addView(text(c.optString("phone","")+" · "+c.optString("relation_label",""),11,false,MUTED));
                Button del=normal("حذف");
                int id=c.optInt("id",0);
                del.setOnClickListener(v->deleteContact(id,del));
                box.addView(del);
                content.addView(box,cardLp());
            }
        }else addCard("مخاطب اضطراری","هنوز مخاطبی ثبت نشده است.");

        LinearLayout zone=card();
        zone.addView(text("Privacy Zone",17,true,TEXT));
        zone.addView(text("برای مخفی‌کردن ابتدا/انتهای مسیر عمومی در اطراف مکان‌های حساس، یک محدوده بر اساس موقعیت فعلی بسازید.",12,false,MUTED));
        EditText label=input("نام محدوده؛ مثال: خانه");
        EditText radius=input("شعاع به متر (100 تا 2000)");
        radius.setText("300");
        Button current=primary("ساخت محدوده از موقعیت فعلی");
        current.setOnClickListener(v->savePrivacyAtCurrent(label,radius,current));
        zone.addView(label);zone.addView(radius);zone.addView(current);
        content.addView(zone,cardLp());

        if(zones!=null&&zones.length()>0){
            for(int i=0;i<zones.length();i++){
                JSONObject z=zones.optJSONObject(i);if(z==null)continue;
                LinearLayout box=card();
                box.addView(text(z.optString("label","حریم خصوصی"),15,true,TEXT));
                box.addView(text("شعاع "+z.optInt("radius_m",300)+" متر",11,false,MUTED));
                Button del=normal("حذف محدوده");
                int id=z.optInt("id",0);
                del.setOnClickListener(v->deleteZone(id,del));
                box.addView(del);
                content.addView(box,cardLp());
            }
        }
    }

    private void startSos(Button b){
        b.setEnabled(false);
        io.execute(()->{
            try{
                JSONObject boot=api.bootstrap();
                JSONObject ride=boot.optJSONObject("my_active_ride");
                int rideId=ride==null?0:ride.optInt("id",0);
                JSONObject d=api.post("pp_sos_start",new JSONObject().put("ride_id",rideId));
                String url=d.optString("url","");
                main.post(()->{
                    b.setEnabled(true);
                    if(url.isBlank()){toast("لینک SOS دریافت نشد.");return;}
                    Intent share=new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT,"لینک SOS PedalPro:\n"+url);
                    startActivity(Intent.createChooser(share,"اشتراک لینک SOS"));
                });
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void stopSos(Button b){
        b.setEnabled(false);
        io.execute(()->{
            try{
                api.post("pp_sos_stop",new JSONObject());
                main.post(()->{b.setEnabled(true);toast("SOS پایان یافت.");});
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void saveContact(EditText name,EditText phone,EditText relation,CheckBox primary,Button b){
        String n=name.getText().toString().trim(),p=phone.getText().toString().trim(),r=relation.getText().toString().trim();
        if(n.isBlank()||p.length()<7){toast("نام و شماره تماس معتبر وارد کنید.");return;}
        b.setEnabled(false);
        io.execute(()->{
            try{
                api.post("pp_safety_contact_save",new JSONObject()
                        .put("name",n).put("phone",p).put("relation_label",r).put("is_primary",primary.isChecked()));
                main.post(()->{toast("مخاطب ذخیره شد.");load();});
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void deleteContact(int id,Button b){
        if(id<=0)return;b.setEnabled(false);
        io.execute(()->{
            try{api.post("pp_safety_contact_delete",new JSONObject().put("id",id));main.post(this::load);}
            catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void savePrivacyAtCurrent(EditText label,EditText radius,Button b){
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ_LOCATION);
            return;
        }
        b.setEnabled(false);b.setText("در حال دریافت GPS…");
        CancellationTokenSource cts=new CancellationTokenSource();
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,cts.getToken())
                .addOnSuccessListener(this,loc->{
                    if(loc==null){b.setEnabled(true);b.setText("ساخت محدوده از موقعیت فعلی");toast("موقعیت دریافت نشد.");return;}
                    int rad=300;try{rad=Integer.parseInt(radius.getText().toString().trim());}catch(Exception ignored){}
                    rad=Math.max(100,Math.min(2000,rad));
                    String l=label.getText().toString().trim();if(l.isBlank())l="حریم خصوصی";
                    saveZone(loc,l,rad,b);
                })
                .addOnFailureListener(this,e->{b.setEnabled(true);b.setText("ساخت محدوده از موقعیت فعلی");toast(e.getMessage());});
    }

    private void saveZone(Location loc,String label,int radius,Button b){
        io.execute(()->{
            try{
                api.post("pp_privacy_save",new JSONObject()
                        .put("label",label).put("lat",loc.getLatitude()).put("lng",loc.getLongitude()).put("radius_m",radius));
                main.post(()->{toast("Privacy Zone ذخیره شد.");load();});
            }catch(Exception e){main.post(()->{b.setEnabled(true);b.setText("ساخت محدوده از موقعیت فعلی");toast(e.getMessage());});}
        });
    }

    private void deleteZone(int id,Button b){
        if(id<=0)return;b.setEnabled(false);
        io.execute(()->{
            try{api.post("pp_privacy_delete",new JSONObject().put("id",id));main.post(this::load);}
            catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_LOCATION)toast("بعد از فعال‌شدن Location دوباره ساخت Privacy Zone را بزنید.");
    }
}
