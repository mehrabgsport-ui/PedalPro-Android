package ir.pedalpro.app.nativeapp;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public final class NativeSafetyActivity extends NativeScreenBase {
    public static final String EXTRA_SECTION="section";
    private static final int REQ_LOCATION=4201;

    private String section="safety";
    private JSONObject boot;

    @Override protected String screenTitle(){
        if("privacy".equals(section))return "Privacy Zones";
        if("beacon".equals(section))return "Beacon";
        return "ایمنی / SOS";
    }

    @Override protected void onScreenReady(){
        String requested=getIntent().getStringExtra(EXTRA_SECTION);
        if(requested!=null&&!requested.isBlank())section=requested;
        load();
    }

    private void load(){
        content.removeAllViews();
        setSubtitle("در حال دریافت اطلاعات…",MUTED);
        io.execute(()->{
            try{
                boot=api.bootstrap();
                if("privacy".equals(section)){
                    JSONObject zones=api.get("pp_privacy_zones");
                    main.post(()->renderPrivacy(zones.optJSONArray("items")));
                }else if("beacon".equals(section)){
                    main.post(this::renderBeacon);
                }else{
                    JSONObject contacts=api.get("pp_safety_contacts");
                    main.post(()->renderSafety(contacts.optJSONArray("items")));
                }
            }catch(Exception e){
                main.post(()->{setSubtitle("خطا",RED);addCard("خطا",e.getMessage());});
            }
        });
    }

    private void renderSafety(JSONArray contacts){
        content.removeAllViews();
        setSubtitle("",MUTED);

        LinearLayout emergency=card();
        emergency.addView(text("مخاطبین اضطراری",18,true,TEXT));

        EditText name=input("نام");
        EditText phone=input("شماره تماس");
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        EditText relation=input("نسبت / توضیح");
        CheckBox primaryBox=new CheckBox(this);
        primaryBox.setText("مخاطب اصلی");
        primaryBox.setTextColor(MUTED);
        Button add=primary("افزودن");
        add.setOnClickListener(v->saveContact(name,phone,relation,primaryBox,add));
        emergency.addView(name);
        emergency.addView(phone);
        emergency.addView(relation);
        emergency.addView(primaryBox);
        emergency.addView(add);

        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp=new LinearLayout.LayoutParams(-1,-2);
        listLp.setMargins(0,dp(10),0,0);
        emergency.addView(list,listLp);

        if(contacts==null||contacts.length()==0){
            list.addView(emptyText("مخاطب اضطراری ثبت نشده است."));
        }else{
            for(int i=0;i<contacts.length();i++){
                JSONObject c=contacts.optJSONObject(i);if(c==null)continue;
                list.addView(contactRow(c),cardLp());
            }
        }
        content.addView(emergency,cardLp());

        LinearLayout sos=card();
        sos.addView(text("SOS Live",18,true,TEXT));
        TextView desc=text("یک لینک اضطراری ۲۴ ساعته از آخرین موقعیت قابل اشتراک می‌سازد.",12,false,MUTED);
        desc.setPadding(0,dp(6),0,0);
        sos.addView(desc);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button start=danger("ساخت لینک SOS");
        Button stop=normal("توقف SOS");
        actions.addView(start,new LinearLayout.LayoutParams(0,dp(50),1f));
        LinearLayout.LayoutParams stopLp=new LinearLayout.LayoutParams(0,dp(50),1f);
        stopLp.setMargins(dp(8),0,0,0);
        actions.addView(stop,stopLp);
        sos.addView(actions);

        LinearLayout result=new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setVisibility(View.GONE);
        LinearLayout.LayoutParams resultLp=new LinearLayout.LayoutParams(-1,-2);
        resultLp.setMargins(0,dp(10),0,0);
        sos.addView(result,resultLp);

        start.setOnClickListener(v->startSos(start,result));
        stop.setOnClickListener(v->stopSos(stop,result));
        content.addView(sos,cardLp());
        VazirmatnLoader.apply(this,content);
    }

    private View contactRow(JSONObject c){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(10),dp(10),dp(10),dp(10));
        row.setBackground(round(android.graphics.Color.argb(9,255,255,255),14,android.graphics.Color.argb(26,255,255,255)));

        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.END);
        copy.addView(text(c.optString("name","مخاطب"),14,true,TEXT));
        String meta=c.optString("phone","");
        if(!c.optString("relation_label","").isBlank())meta+=" · "+c.optString("relation_label");
        if(c.optInt("is_primary",0)==1)meta+=" · اصلی";
        copy.addView(text(meta,11,false,MUTED));
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button call=normal("تماس");
        String phone=c.optString("phone","");
        call.setOnClickListener(v->{
            try{startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+phone)));}catch(Exception e){toast(e.getMessage());}
        });
        Button del=danger("حذف");
        int id=c.optInt("id",0);
        del.setOnClickListener(v->deleteContact(id,del));
        actions.addView(call,new LinearLayout.LayoutParams(dp(74),dp(42)));
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(74),dp(42));dl.setMargins(dp(6),0,0,0);actions.addView(del,dl);
        row.addView(actions);
        return row;
    }

    private void renderPrivacy(JSONArray zones){
        content.removeAllViews();
        setSubtitle("",MUTED);

        LinearLayout card=card();
        card.addView(text("Privacy Zones",18,true,TEXT));
        TextView description=text("شروع/پایان مسیر برای دیگران داخل این شعاع مخفی می‌شود؛ خود شما و مدیر همچنان مسیر کامل را می‌بینید.",12,false,MUTED);
        description.setPadding(0,dp(6),0,0);
        card.addView(description);

        EditText label=input("مثلاً خانه");
        label.setText("خانه");
        EditText radius=input("شعاع به متر");
        radius.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        radius.setText("300");
        Button here=primary("ثبت موقعیت فعلی");
        here.setOnClickListener(v->savePrivacyAtCurrent(label,radius,here));
        card.addView(label);card.addView(radius);card.addView(here);
        content.addView(card,cardLp());

        if(zones==null||zones.length()==0){
            content.addView(emptyText("هنوز Privacy Zone ندارید."),cardLp());
        }else{
            for(int i=0;i<zones.length();i++){
                JSONObject z=zones.optJSONObject(i);if(z==null)continue;
                LinearLayout row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                row.setPadding(dp(12),dp(12),dp(12),dp(12));
                row.setBackground(round(android.graphics.Color.argb(9,255,255,255),15,android.graphics.Color.argb(23,255,255,255)));

                View stripe=new View(this);
                stripe.setBackgroundColor(android.graphics.Color.rgb(255,178,77));
                row.addView(stripe,new LinearLayout.LayoutParams(dp(4),dp(48)));

                LinearLayout copy=new LinearLayout(this);
                copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.END);
                copy.addView(text(z.optString("label","حریم خصوصی"),14,true,TEXT));
                copy.addView(text("شعاع "+z.optInt("radius_m",300)+" متر",11,false,MUTED));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1f);cp.setMargins(dp(10),0,dp(10),0);row.addView(copy,cp);

                Button del=danger("حذف");
                int id=z.optInt("id",0);
                del.setOnClickListener(v->deleteZone(id,del));
                row.addView(del,new LinearLayout.LayoutParams(dp(82),dp(42)));
                content.addView(row,cardLp());
            }
        }
        VazirmatnLoader.apply(this,content);
    }

    private void renderBeacon(){
        content.removeAllViews();
        setSubtitle("",MUTED);

        LinearLayout card=card();
        card.addView(text("Beacon عمومی",18,true,TEXT));
        TextView description=text("یک لینک موقت ۱۲ ساعته بسازید؛ خانواده یا دوست شما بدون ورود به حساب می‌تواند آخرین موقعیت و زمان بروزرسانی را ببیند.",12,false,MUTED);
        description.setPadding(0,dp(6),0,0);
        card.addView(description);

        Spinner rides=activeRideSpinner();
        Button create=primary("ساخت لینک زنده");
        Button stop=normal("پایان اشتراک");
        card.addView(rides);card.addView(create);card.addView(stop);
        if(activeRideId(rides)<=0){
            create.setEnabled(false);
            create.setAlpha(.55f);
            TextView noRide=text("برای ساخت لینک Beacon ابتدا یک رکاب‌زنی فعال شروع کنید.",12,false,MUTED);
            noRide.setPadding(0,dp(8),0,0);
            card.addView(noRide);
        }

        LinearLayout result=new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);rlp.setMargins(0,dp(10),0,0);card.addView(result,rlp);

        create.setOnClickListener(v->createBeacon(activeRideId(rides),create,result));
        stop.setOnClickListener(v->stopBeacon(stop,result));
        content.addView(card,cardLp());
        VazirmatnLoader.apply(this,content);
    }

    private Spinner activeRideSpinner(){
        Spinner spinner=new Spinner(this);
        ArrayList<String> labels=new ArrayList<>();
        JSONArray ids=new JSONArray();
        JSONArray rides=boot==null?null:boot.optJSONArray("rides");
        JSONObject user=boot==null?null:boot.optJSONObject("user");
        int uid=user==null?0:user.optInt("id",0);
        if(rides!=null){
            for(int i=0;i<rides.length();i++){
                JSONObject r=rides.optJSONObject(i);if(r==null)continue;
                if(!"active".equals(r.optString("status"))||r.optInt("owner_user_id",0)!=uid)continue;
                labels.add(r.optString("title","رکاب‌زنی"));
                ids.put(r.optInt("id",0));
            }
        }
        if(labels.isEmpty()){labels.add("رکاب‌زنی فعال ندارید");ids.put(0);}
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){
                TextView v=(TextView)super.getView(position,convertView,parent);
                v.setTextColor(TEXT);v.setTextSize(12);v.setPadding(dp(12),0,dp(12),0);v.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);return v;
            }
            @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){
                TextView v=(TextView)super.getDropDownView(position,convertView,parent);
                v.setTextColor(TEXT);v.setBackgroundColor(android.graphics.Color.rgb(9,26,41));v.setPadding(dp(12),dp(12),dp(12),dp(12));return v;
            }
        };
        spinner.setAdapter(adapter);spinner.setTag(ids);
        spinner.setBackground(round(android.graphics.Color.argb(122,5,25,40),12,android.graphics.Color.argb(46,255,255,255)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,dp(10),0,dp(5));spinner.setLayoutParams(lp);
        return spinner;
    }

    private int activeRideId(Spinner spinner){
        Object tag=spinner.getTag();return tag instanceof JSONArray?((JSONArray)tag).optInt(spinner.getSelectedItemPosition(),0):0;
    }

    private TextView emptyText(String value){
        TextView t=text(value,12,false,MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12),dp(22),dp(12),dp(22));
        t.setBackground(round(android.graphics.Color.argb(7,255,255,255),14,android.graphics.Color.argb(18,255,255,255)));
        return t;
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

    private void startSos(Button b,LinearLayout result){
        b.setEnabled(false);
        io.execute(()->{
            try{
                JSONObject d=api.post("pp_sos_start",new JSONObject());
                String url=d.optString("url","");
                main.post(()->{
                    b.setEnabled(true);
                    if(url.isBlank()){toast("لینک SOS دریافت نشد.");return;}
                    showLink(result,"لینک اضطراری:",url,"کپی لینک");
                });
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void stopSos(Button b,LinearLayout result){
        b.setEnabled(false);
        io.execute(()->{
            try{
                api.post("pp_sos_stop",new JSONObject());
                main.post(()->{b.setEnabled(true);result.removeAllViews();result.setVisibility(View.GONE);toast("SOS متوقف شد");});
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void createBeacon(int rideId,Button b,LinearLayout result){
        b.setEnabled(false);
        io.execute(()->{
            try{
                JSONObject d=api.post("pp_beacon_create",new JSONObject().put("ride_id",rideId));
                String url=d.optString("url","");
                main.post(()->{
                    b.setEnabled(true);
                    if(url.isBlank()){toast("لینک Beacon دریافت نشد.");return;}
                    showLink(result,"لینک زنده:",url,"کپی لینک");
                });
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void stopBeacon(Button b,LinearLayout result){
        b.setEnabled(false);
        io.execute(()->{
            try{
                api.post("pp_beacon_stop",new JSONObject());
                main.post(()->{b.setEnabled(true);result.removeAllViews();toast("اشتراک زنده پایان یافت");});
            }catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    private void showLink(LinearLayout result,String title,String url,String copyLabel){
        result.removeAllViews();result.setVisibility(View.VISIBLE);
        result.setPadding(dp(12),dp(11),dp(12),dp(11));
        result.setBackground(round(android.graphics.Color.argb(8,255,255,255),14,android.graphics.Color.argb(28,255,255,255)));
        result.addView(text(title,12,true,TEXT));
        TextView value=text(url,11,false,TEXT);value.setTextDirection(View.TEXT_DIRECTION_LTR);value.setGravity(Gravity.START);
        result.addView(value);
        Button copy=normal(copyLabel);
        copy.setOnClickListener(v->{
            ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null){cm.setPrimaryClip(ClipData.newPlainText("PedalPro",url));toast("لینک کپی شد");}
        });
        result.addView(copy);
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
                    if(loc==null){b.setEnabled(true);b.setText("ثبت موقعیت فعلی");toast("موقعیت دریافت نشد.");return;}
                    int rad=300;try{rad=Integer.parseInt(radius.getText().toString().trim());}catch(Exception ignored){}
                    rad=Math.max(100,Math.min(2000,rad));
                    String l=label.getText().toString().trim();if(l.isBlank())l="حریم خصوصی";
                    saveZone(loc,l,rad,b);
                })
                .addOnFailureListener(this,e->{b.setEnabled(true);b.setText("ثبت موقعیت فعلی");toast(e.getMessage());});
    }

    private void saveZone(Location loc,String label,int radius,Button b){
        io.execute(()->{
            try{
                api.post("pp_privacy_save",new JSONObject()
                        .put("label",label).put("lat",loc.getLatitude()).put("lng",loc.getLongitude()).put("radius_m",radius));
                main.post(()->{toast("Privacy Zone ثبت شد");load();});
            }catch(Exception e){main.post(()->{b.setEnabled(true);b.setText("ثبت موقعیت فعلی");toast(e.getMessage());});}
        });
    }

    private void deleteZone(int id,Button b){
        if(id<=0)return;b.setEnabled(false);
        io.execute(()->{
            try{api.post("pp_privacy_delete",new JSONObject().put("id",id));main.post(()->{toast("Privacy Zone حذف شد");load();});}
            catch(Exception e){main.post(()->{b.setEnabled(true);toast(e.getMessage());});}
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_LOCATION)toast("بعد از فعال‌شدن Location دوباره ثبت موقعیت فعلی را بزنید.");
    }
}
